//! The compositor: smithay's wayland frontend on its own calloop thread.
//!
//! Presentation is a CPU copy of each toplevel's root wl_shm buffer into that toplevel's
//! ANativeWindow. Subsurfaces, popups and cursor surfaces are acknowledged (buffers released,
//! frame callbacks answered) but never shown.

use std::os::unix::net::UnixStream;
use std::path::PathBuf;
use std::sync::Arc;
use std::time::{Duration, Instant};

use smithay::delegate_compositor;
use smithay::delegate_data_device;
use smithay::delegate_output;
use smithay::delegate_seat;
use smithay::delegate_shm;
use smithay::delegate_xdg_decoration;
use smithay::delegate_xdg_shell;
use smithay::input::keyboard::XkbConfig;
use smithay::input::pointer::{AxisFrame, ButtonEvent, MotionEvent as PointerMotion};
use smithay::input::touch::{DownEvent, MotionEvent as TouchMotion, UpEvent};
use smithay::input::{Seat, SeatHandler, SeatState};
use smithay::output::{Mode as OutputMode, Output, PhysicalProperties, Scale, Subpixel};
use smithay::reexports::calloop::channel::{self, Channel, Sender};
use smithay::reexports::calloop::generic::Generic;
use smithay::reexports::calloop::timer::{TimeoutAction, Timer};
use smithay::reexports::calloop::{EventLoop, Interest, LoopHandle, LoopSignal, Mode, PostAction};
use smithay::reexports::wayland_protocols::xdg::decoration::zv1::server::zxdg_toplevel_decoration_v1::Mode as DecorationMode;
use smithay::reexports::wayland_protocols::xdg::shell::server::xdg_toplevel;
use smithay::reexports::wayland_server::backend::{ClientData, ClientId, DisconnectReason};
use smithay::reexports::wayland_server::protocol::wl_buffer::WlBuffer;
use smithay::reexports::wayland_server::protocol::wl_callback::WlCallback;
use smithay::reexports::wayland_server::protocol::wl_seat::WlSeat;
use smithay::reexports::wayland_server::protocol::wl_shm;
use smithay::reexports::wayland_server::protocol::wl_surface::WlSurface;
use smithay::reexports::wayland_server::{Client, Display, DisplayHandle, ListeningSocket, Resource};
use smithay::utils::{Serial, Transform, SERIAL_COUNTER};
use smithay::wayland::buffer::BufferHandler;
use smithay::wayland::compositor::{
    get_parent, with_states, with_surface_tree_downward, BufferAssignment, CompositorClientState, CompositorHandler,
    CompositorState, SurfaceAttributes, TraversalAction,
};
use smithay::wayland::output::{OutputHandler, OutputManagerState};
use smithay::wayland::selection::data_device::{
    ClientDndGrabHandler, DataDeviceHandler, DataDeviceState, ServerDndGrabHandler,
};
use smithay::wayland::selection::SelectionHandler;
use smithay::wayland::shell::xdg::decoration::{XdgDecorationHandler, XdgDecorationState};
use smithay::wayland::shell::xdg::{
    PopupSurface, PositionerState, ToplevelSurface, XdgShellHandler, XdgShellState, XdgToplevelSurfaceData,
};
use smithay::wayland::shm::{with_buffer_contents, ShmHandler, ShmState};

use super::java::HostEvents;
use super::log::{lerror, linfo, lwarn};
use super::window::NativeWindow;

/// Frame callbacks are answered at most this often.
const FRAME_INTERVAL: Duration = Duration::from_millis(16);
/// At most one copy-time line per toplevel per this interval.
const LOG_INTERVAL: Duration = Duration::from_secs(1);

pub const TOUCH_DOWN: i32 = 0;
pub const TOUCH_UP: i32 = 1;
pub const TOUCH_MOTION: i32 = 2;
pub const TOUCH_CANCEL: i32 = 3;

pub const POINTER_MOTION: i32 = 0;
pub const POINTER_BUTTON_DOWN: i32 = 1;
pub const POINTER_BUTTON_UP: i32 = 2;
pub const POINTER_AXIS: i32 = 3;
pub const POINTER_LEAVE: i32 = 4;

/// What Java asks of the compositor thread.
pub enum Command {
    SetWindow { id: i64, window: Option<NativeWindow> },
    Configure { id: i64, width: i32, height: i32 },
    Touch { id: i64, action: i32, slot: i32, x: f32, y: f32 },
    Pointer { id: i64, action: i32, button: i32, x: f32, y: f32 },
}

pub struct StartConfig {
    pub socket_path: PathBuf,
    pub output_width: i32,
    pub output_height: i32,
    pub xwayland: Option<super::xwayland::XwaylandConfig>,
}

/// The running compositor as Java's thread sees it.
pub struct HostHandle {
    pub sender: Sender<Command>,
    pub signal: LoopSignal,
    pub thread: std::thread::JoinHandle<()>,
}

#[derive(Default)]
pub struct ClientState {
    pub compositor_state: CompositorClientState,
}

impl ClientData for ClientState {
    fn initialized(&self, _client_id: ClientId) {}
    fn disconnected(&self, _client_id: ClientId, _reason: DisconnectReason) {}
}

#[derive(Default)]
struct CopyStats {
    frames: u32,
    copy_us_sum: u64,
    copy_us_max: u64,
    total_us_sum: u64,
    last_copy_us: u64,
}

pub struct Toplevel {
    pub id: i64,
    pub surface: ToplevelSurface,
    announced: bool,
    window: Option<NativeWindow>,
    view_size: Option<(i32, i32)>,
    buffer_size: (i32, i32),
    /// The newest buffer, kept only while there is no window to post it to.
    waiting_buffer: Option<WlBuffer>,
    /// Frame callbacks held while the toplevel has no window.
    held_frames: Vec<WlCallback>,
    stats: CopyStats,
    last_log: Instant,
}

pub struct State {
    pub display_handle: DisplayHandle,
    pub loop_handle: LoopHandle<'static, State>,
    pub compositor_state: CompositorState,
    pub xdg_shell_state: XdgShellState,
    pub shm_state: ShmState,
    pub output_manager_state: OutputManagerState,
    pub seat_state: SeatState<State>,
    pub data_device_state: DataDeviceState,
    pub decoration_state: XdgDecorationState,
    pub seat: Seat<State>,
    pub output: Output,
    pub toplevels: Vec<Toplevel>,
    next_id: i64,
    popups: Vec<PopupSurface>,
    pending_frames: Vec<WlCallback>,
    frame_timer_armed: bool,
    last_frame_tick: Instant,
    start: Instant,
    events: Box<dyn HostEvents>,
    pub xwayland: Option<super::xwayland::XwaylandState>,
}

pub fn spawn(config: StartConfig, events: impl FnOnce() -> Option<Box<dyn HostEvents>> + Send + 'static) -> std::io::Result<HostHandle> {
    let (sender, channel) = channel::channel::<Command>();
    let (signal_tx, signal_rx) = std::sync::mpsc::channel::<Result<LoopSignal, String>>();
    let thread = std::thread::Builder::new().name("tl-wayland".into()).spawn(move || {
        let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(move || {
            let Some(events) = events() else {
                let _ = signal_tx.send(Err("cannot attach to the VM".into()));
                return;
            };
            match setup(config, channel, events) {
                Ok((mut event_loop, mut state)) => {
                    let _ = signal_tx.send(Ok(event_loop.get_signal()));
                    linfo!("compositor running");
                    let run = event_loop.run(None, &mut state, |state| {
                        if let Err(e) = state.display_handle.flush_clients() {
                            lwarn!("flush_clients: {e}");
                        }
                    });
                    if let Err(e) = run {
                        lerror!("event loop ended with an error: {e}");
                    }
                    state.shutdown();
                    linfo!("compositor stopped");
                }
                Err(e) => {
                    let _ = signal_tx.send(Err(e.to_string()));
                }
            }
        }));
        if result.is_err() {
            lerror!("compositor thread panicked; the host is down");
        }
    })?;
    match signal_rx.recv() {
        Ok(Ok(signal)) => Ok(HostHandle { sender, signal, thread }),
        Ok(Err(e)) => {
            let _ = thread.join();
            Err(std::io::Error::other(e))
        }
        Err(_) => {
            let _ = thread.join();
            Err(std::io::Error::other("compositor thread died during start"))
        }
    }
}

fn setup(
    config: StartConfig,
    channel: Channel<Command>,
    events: Box<dyn HostEvents>,
) -> Result<(EventLoop<'static, State>, State), Box<dyn std::error::Error>> {
    let event_loop: EventLoop<'static, State> = EventLoop::try_new()?;
    let loop_handle = event_loop.handle();
    let display: Display<State> = Display::new()?;
    let dh = display.handle();

    let compositor_state = CompositorState::new::<State>(&dh);
    let xdg_shell_state = XdgShellState::new::<State>(&dh);
    // ARGB8888 and XRGB8888 are always advertised.
    let shm_state = ShmState::new::<State>(&dh, vec![]);
    let output_manager_state = OutputManagerState::new_with_xdg_output::<State>(&dh);
    let mut seat_state = SeatState::new();
    let data_device_state = DataDeviceState::new::<State>(&dh);
    let decoration_state = XdgDecorationState::new::<State>(&dh);

    let mut seat = seat_state.new_wl_seat(&dh, "seat0");
    seat.add_pointer();
    seat.add_touch();
    match seat.add_keyboard(XkbConfig::default(), 400, 25) {
        Ok(_) => linfo!("keyboard on the seat (XKB_CONFIG_ROOT={:?})", std::env::var("XKB_CONFIG_ROOT").ok()),
        Err(e) => lwarn!("no keyboard on the seat: {e:?} (XKB_CONFIG_ROOT={:?})", std::env::var("XKB_CONFIG_ROOT").ok()),
    }

    let output = Output::new(
        "TL-1".into(),
        PhysicalProperties {
            size: (0, 0).into(),
            subpixel: Subpixel::Unknown,
            make: "Termux Launcher".into(),
            model: "Wayland spike".into(),
        },
    );
    output.create_global::<State>(&dh);
    let mode = OutputMode { size: (config.output_width.max(1), config.output_height.max(1)).into(), refresh: 60_000 };
    output.change_current_state(Some(mode), Some(Transform::Normal), Some(Scale::Integer(1)), Some((0, 0).into()));
    output.set_preferred(mode);

    // bind_absolute takes `<path>.lock` and removes a stale socket left by a dead host; a live
    // host keeps its lock and the bind fails instead of stealing its socket.
    let listener = ListeningSocket::bind_absolute(config.socket_path.clone())?;
    linfo!("listening on {}", config.socket_path.display());
    loop_handle.insert_source(Generic::new(listener, Interest::READ, Mode::Level), |_, listener, state| {
        loop {
            match listener.accept() {
                Ok(Some(stream)) => state.insert_client(stream),
                Ok(None) => break,
                Err(e) => {
                    lwarn!("accept: {e}");
                    break;
                }
            }
        }
        Ok(PostAction::Continue)
    })?;

    loop_handle.insert_source(Generic::new(display, Interest::READ, Mode::Level), |_, display, state| {
        // SAFETY: the display is not dropped while the source is registered.
        let display = unsafe { display.get_mut() };
        if let Err(e) = display.dispatch_clients(state) {
            lwarn!("dispatch_clients: {e}");
        }
        Ok(PostAction::Continue)
    })?;

    loop_handle.insert_source(channel, |event, _, state| {
        if let channel::Event::Msg(cmd) = event {
            state.handle_command(cmd);
        }
    })?;

    let mut state = State {
        display_handle: dh,
        loop_handle,
        compositor_state,
        xdg_shell_state,
        shm_state,
        output_manager_state,
        seat_state,
        data_device_state,
        decoration_state,
        seat,
        output,
        toplevels: Vec::new(),
        next_id: 1,
        popups: Vec::new(),
        pending_frames: Vec::new(),
        frame_timer_armed: false,
        last_frame_tick: Instant::now(),
        start: Instant::now(),
        events,
        xwayland: None,
    };
    if let Some(xw) = config.xwayland {
        super::xwayland::start(&mut state, xw);
    }
    Ok((event_loop, state))
}

impl State {
    pub fn insert_client(&mut self, stream: UnixStream) {
        if let Err(e) = self.display_handle.insert_client(stream, Arc::new(ClientState::default())) {
            lwarn!("insert_client: {e}");
        }
    }

    fn shutdown(&mut self) {
        super::xwayland::stop(self);
        for t in self.toplevels.drain(..) {
            if let Some(b) = t.waiting_buffer {
                b.release();
            }
        }
    }

    pub fn now_ms(&self) -> u32 {
        self.start.elapsed().as_millis() as u32
    }

    fn toplevel_index(&self, id: i64) -> Option<usize> {
        self.toplevels.iter().position(|t| t.id == id)
    }

    fn toplevel_index_for(&self, surface: &WlSurface) -> Option<usize> {
        self.toplevels.iter().position(|t| t.surface.wl_surface() == surface)
    }

    fn handle_command(&mut self, cmd: Command) {
        match cmd {
            Command::SetWindow { id, window } => self.set_window(id, window),
            Command::Configure { id, width, height } => self.configure(id, width, height),
            Command::Touch { id, action, slot, x, y } => self.touch(id, action, slot, x, y),
            Command::Pointer { id, action, button, x, y } => self.pointer(id, action, button, x, y),
        }
    }

    fn set_window(&mut self, id: i64, window: Option<NativeWindow>) {
        let Some(i) = self.toplevel_index(id) else { return };
        let has_window = window.is_some();
        self.toplevels[i].window = window;
        if !has_window {
            return;
        }
        if let Some(buffer) = self.toplevels[i].waiting_buffer.take() {
            post_buffer(&mut self.toplevels[i], &buffer);
            buffer.release();
        } else if self.toplevels[i].surface.is_initial_configure_sent() {
            // Nothing to show yet in the new window: a configure makes most clients draw again.
            self.toplevels[i].surface.send_configure();
        }
        let held = std::mem::take(&mut self.toplevels[i].held_frames);
        self.pending_frames.extend(held);
        self.schedule_frames();
    }

    fn configure(&mut self, id: i64, width: i32, height: i32) {
        let Some(i) = self.toplevel_index(id) else { return };
        let t = &mut self.toplevels[i];
        if width <= 0 || height <= 0 || t.view_size == Some((width, height)) {
            return;
        }
        t.view_size = Some((width, height));
        t.surface.with_pending_state(|s| {
            s.size = Some((width, height).into());
            s.states.set(xdg_toplevel::State::Activated);
            s.states.set(xdg_toplevel::State::TiledLeft);
            s.states.set(xdg_toplevel::State::TiledRight);
            s.states.set(xdg_toplevel::State::TiledTop);
            s.states.set(xdg_toplevel::State::TiledBottom);
        });
        if t.surface.is_initial_configure_sent() {
            t.surface.send_pending_configure();
        }
    }

    /// View pixels to surface coordinates: the buffer is scaled to fill the view.
    fn surface_point(t: &Toplevel, x: f32, y: f32) -> (f64, f64) {
        match t.view_size {
            Some((vw, vh)) if t.buffer_size.0 > 0 && t.buffer_size.1 > 0 => (
                x as f64 * t.buffer_size.0 as f64 / vw as f64,
                y as f64 * t.buffer_size.1 as f64 / vh as f64,
            ),
            _ => (x as f64, y as f64),
        }
    }

    fn touch(&mut self, id: i64, action: i32, slot: i32, x: f32, y: f32) {
        let Some(i) = self.toplevel_index(id) else { return };
        let Some(touch) = self.seat.get_touch() else { return };
        let surface = self.toplevels[i].surface.wl_surface().clone();
        let (sx, sy) = Self::surface_point(&self.toplevels[i], x, y);
        let time = self.now_ms();
        let slot = Some(slot.max(0) as u32).into();
        let focus = Some((surface.clone(), (0.0, 0.0).into()));
        match action {
            TOUCH_DOWN => {
                let serial = SERIAL_COUNTER.next_serial();
                if let Some(keyboard) = self.seat.get_keyboard() {
                    keyboard.set_focus(self, Some(surface), serial);
                }
                touch.down(self, focus, &DownEvent { slot, location: (sx, sy).into(), serial, time });
            }
            TOUCH_UP => touch.up(self, &UpEvent { slot, serial: SERIAL_COUNTER.next_serial(), time }),
            TOUCH_MOTION => touch.motion(self, focus, &TouchMotion { slot, location: (sx, sy).into(), time }),
            TOUCH_CANCEL => touch.cancel(self),
            _ => return,
        }
        touch.frame(self);
    }

    fn pointer(&mut self, id: i64, action: i32, button: i32, x: f32, y: f32) {
        let Some(i) = self.toplevel_index(id) else { return };
        let Some(pointer) = self.seat.get_pointer() else { return };
        let surface = self.toplevels[i].surface.wl_surface().clone();
        let time = self.now_ms();
        let serial = SERIAL_COUNTER.next_serial();
        match action {
            POINTER_MOTION => {
                let (sx, sy) = Self::surface_point(&self.toplevels[i], x, y);
                pointer.motion(self, Some((surface, (0.0, 0.0).into())), &PointerMotion { location: (sx, sy).into(), serial, time });
            }
            POINTER_BUTTON_DOWN | POINTER_BUTTON_UP => {
                if action == POINTER_BUTTON_DOWN {
                    if let Some(keyboard) = self.seat.get_keyboard() {
                        keyboard.set_focus(self, Some(surface), serial);
                    }
                }
                let state = if action == POINTER_BUTTON_DOWN {
                    smithay::backend::input::ButtonState::Pressed
                } else {
                    smithay::backend::input::ButtonState::Released
                };
                pointer.button(self, &ButtonEvent { serial, time, button: button as u32, state });
            }
            POINTER_AXIS => {
                // x/y carry scroll distances in surface pixels.
                let mut frame = AxisFrame::new(time).source(smithay::backend::input::AxisSource::Wheel);
                if x != 0.0 {
                    frame = frame.value(smithay::backend::input::Axis::Horizontal, x as f64);
                }
                if y != 0.0 {
                    frame = frame.value(smithay::backend::input::Axis::Vertical, y as f64);
                }
                pointer.axis(self, frame);
            }
            POINTER_LEAVE => {
                pointer.motion(self, None, &PointerMotion { location: (0.0, 0.0).into(), serial, time });
            }
            _ => return,
        }
        pointer.frame(self);
    }

    /// Answers held frame callbacks on the next ~16 ms tick; no timer runs while nothing waits.
    fn schedule_frames(&mut self) {
        if self.frame_timer_armed || self.pending_frames.is_empty() {
            return;
        }
        self.frame_timer_armed = true;
        let due = self.last_frame_tick + FRAME_INTERVAL;
        let delay = due.saturating_duration_since(Instant::now());
        let res = self.loop_handle.insert_source(Timer::from_duration(delay), |_, _, state| {
            state.frame_timer_armed = false;
            state.last_frame_tick = Instant::now();
            let time = state.now_ms();
            for cb in state.pending_frames.drain(..) {
                cb.done(time);
            }
            TimeoutAction::Drop
        });
        if res.is_err() {
            self.frame_timer_armed = false;
        }
    }

    pub fn announce(&mut self, i: usize) {
        let (title, app_id) = toplevel_names(&self.toplevels[i].surface);
        let id = self.toplevels[i].id;
        self.toplevels[i].announced = true;
        self.output.enter(self.toplevels[i].surface.wl_surface());
        self.events.toplevel_created(id, &title, &app_id);
    }

    pub fn add_toplevel(&mut self, surface: ToplevelSurface) -> i64 {
        let id = self.next_id;
        self.next_id += 1;
        surface.with_pending_state(|s| {
            s.decoration_mode = Some(DecorationMode::ServerSide);
        });
        self.toplevels.push(Toplevel {
            id,
            surface,
            announced: false,
            window: None,
            view_size: None,
            buffer_size: (0, 0),
            waiting_buffer: None,
            held_frames: Vec::new(),
            stats: CopyStats::default(),
            last_log: Instant::now(),
        });
        id
    }

    pub fn remove_toplevel(&mut self, surface: &WlSurface) {
        if let Some(i) = self.toplevel_index_for(surface) {
            let t = self.toplevels.remove(i);
            if let Some(b) = t.waiting_buffer {
                if b.is_alive() {
                    b.release();
                }
            }
            if t.announced {
                self.events.toplevel_destroyed(t.id);
            }
        }
    }

    pub fn toplevel_renamed(&mut self, surface: &WlSurface) {
        if let Some(i) = self.toplevel_index_for(surface) {
            if self.toplevels[i].announced {
                let (title, app_id) = toplevel_names(&self.toplevels[i].surface);
                let id = self.toplevels[i].id;
                self.events.toplevel_title(id, &title, &app_id);
            }
        }
    }
}

fn toplevel_names(surface: &ToplevelSurface) -> (String, String) {
    with_states(surface.wl_surface(), |states| {
        states
            .data_map
            .get::<XdgToplevelSurfaceData>()
            .map(|d| {
                let d = d.lock().unwrap();
                (d.title.clone().unwrap_or_default(), d.app_id.clone().unwrap_or_default())
            })
            .unwrap_or_default()
    })
}

/// Copies `buffer` into the toplevel's window, if it has one, and logs the copy time at most
/// once a second. Returns the buffer's size when it could be read.
fn post_buffer(t: &mut Toplevel, buffer: &WlBuffer) -> Option<(i32, i32)> {
    let id = t.id;
    let window = &mut t.window;
    let stats = &mut t.stats;
    let result = with_buffer_contents(buffer, |ptr, len, data| {
        let (w, h, stride, offset) = (data.width, data.height, data.stride, data.offset);
        if w <= 0 || h <= 0 || stride < w * 4 || offset < 0 {
            return None;
        }
        let needed = offset as usize + stride as usize * (h as usize - 1) + w as usize * 4;
        if needed > len {
            return None;
        }
        let opaque = match data.format {
            wl_shm::Format::Argb8888 => false,
            wl_shm::Format::Xrgb8888 => true,
            _ => return Some((w, h)),
        };
        if let Some(win) = window.as_mut() {
            // SAFETY: smithay holds the pool mapping for the duration of this closure and the
            // range was checked against its length above.
            let src = unsafe { std::slice::from_raw_parts(ptr.add(offset as usize), needed - offset as usize) };
            match win.post(src, stride as usize, w, h, opaque) {
                Ok(timing) => {
                    stats.frames += 1;
                    stats.copy_us_sum += timing.copy_us;
                    stats.copy_us_max = stats.copy_us_max.max(timing.copy_us);
                    stats.total_us_sum += timing.total_us;
                    stats.last_copy_us = timing.copy_us;
                }
                Err(rc) => lwarn!("toplevel {id}: post failed ({rc})"),
            }
        }
        Some((w, h))
    });
    if t.last_log.elapsed() >= LOG_INTERVAL && t.stats.frames > 0 {
        let s = &t.stats;
        linfo!(
            "copy toplevel={} buffer={}x{} bytes={} frames={} copy_us_last={} copy_us_avg={} copy_us_max={} post_us_avg={}",
            id,
            t.buffer_size.0,
            t.buffer_size.1,
            t.buffer_size.0 as i64 * t.buffer_size.1 as i64 * 4,
            s.frames,
            s.last_copy_us,
            s.copy_us_sum / s.frames as u64,
            s.copy_us_max,
            s.total_us_sum / s.frames as u64
        );
        t.stats = CopyStats::default();
        t.last_log = Instant::now();
    }
    match result {
        Ok(size) => size,
        Err(e) => {
            lwarn!("toplevel {id}: unreadable buffer: {e:?}");
            None
        }
    }
}

impl CompositorHandler for State {
    fn compositor_state(&mut self) -> &mut CompositorState {
        &mut self.compositor_state
    }

    fn client_compositor_state<'a>(&self, client: &'a Client) -> &'a CompositorClientState {
        if let Some(state) = client.get_data::<ClientState>() {
            return &state.compositor_state;
        }
        if let Some(state) = client.get_data::<super::xwayland::XwaylandClientData>() {
            return &state.compositor_state;
        }
        panic!("client without compositor state")
    }

    fn commit(&mut self, surface: &WlSurface) {
        super::xwayland::commit_hook(self, surface);

        let mut root = surface.clone();
        while let Some(parent) = get_parent(&root) {
            root = parent;
        }

        // Popups need their first configure to proceed; they are never shown.
        if let Some(popup) = self.popups.iter().find(|p| p.wl_surface() == surface) {
            if !popup.is_initial_configure_sent() {
                let _ = popup.send_configure();
            }
        }
        self.popups.retain(|p| p.alive());

        let index = self.toplevel_index_for(&root);
        if let Some(i) = index {
            let t = &mut self.toplevels[i];
            if !t.surface.is_initial_configure_sent() {
                t.surface.send_configure();
            }
        }

        // Walk the whole tree: synced subsurfaces apply their state on the parent's commit.
        let mut root_buffer: Option<BufferAssignment> = None;
        let mut frames: Vec<WlCallback> = Vec::new();
        with_surface_tree_downward(
            &root,
            (),
            |_, _, _| TraversalAction::DoChildren(()),
            |s, states, _| {
                let mut guard = states.cached_state.get::<SurfaceAttributes>();
                let attrs = guard.current();
                frames.append(&mut attrs.frame_callbacks);
                if let Some(assignment) = attrs.buffer.take() {
                    if *s == root && index.is_some() {
                        root_buffer = Some(assignment);
                    } else if let BufferAssignment::NewBuffer(b) = assignment {
                        // Subsurfaces, popups and cursors are not shown: release at once.
                        b.release();
                    }
                }
            },
            |_, _, _| true,
        );

        let Some(i) = index else {
            self.pending_frames.append(&mut frames);
            self.schedule_frames();
            return;
        };

        if let Some(BufferAssignment::NewBuffer(buffer)) = root_buffer {
            let size = if self.toplevels[i].window.is_some() {
                let size = post_buffer(&mut self.toplevels[i], &buffer);
                buffer.release();
                size
            } else {
                let size = with_buffer_contents(&buffer, |_, _, d| (d.width, d.height)).ok();
                if let Some(old) = self.toplevels[i].waiting_buffer.replace(buffer) {
                    old.release();
                }
                size
            };
            if !self.toplevels[i].announced {
                self.announce(i);
            }
            if let Some(size) = size {
                if size != self.toplevels[i].buffer_size {
                    self.toplevels[i].buffer_size = size;
                    let id = self.toplevels[i].id;
                    self.events.toplevel_buffer_size(id, size.0, size.1);
                }
            }
        } else if !self.toplevels[i].announced && self.toplevels[i].surface.is_initial_configure_sent() {
            // The initial (bufferless) commit: title and app id are set by now.
            self.announce(i);
        }

        if self.toplevels[i].window.is_some() {
            self.pending_frames.append(&mut frames);
            self.schedule_frames();
        } else {
            self.toplevels[i].held_frames.append(&mut frames);
        }
    }
}

impl BufferHandler for State {
    fn buffer_destroyed(&mut self, buffer: &WlBuffer) {
        for t in &mut self.toplevels {
            if t.waiting_buffer.as_ref() == Some(buffer) {
                t.waiting_buffer = None;
            }
        }
    }
}

impl ShmHandler for State {
    fn shm_state(&self) -> &ShmState {
        &self.shm_state
    }
}

impl XdgShellHandler for State {
    fn xdg_shell_state(&mut self) -> &mut XdgShellState {
        &mut self.xdg_shell_state
    }

    fn new_toplevel(&mut self, surface: ToplevelSurface) {
        self.add_toplevel(surface);
    }

    fn new_popup(&mut self, surface: PopupSurface, _positioner: PositionerState) {
        self.popups.push(surface);
    }

    fn grab(&mut self, _surface: PopupSurface, _seat: WlSeat, _serial: Serial) {}

    fn reposition_request(&mut self, surface: PopupSurface, positioner: PositionerState, token: u32) {
        surface.with_pending_state(|s| {
            s.geometry = positioner.get_geometry();
            s.positioner = positioner;
        });
        surface.send_repositioned(token);
    }

    fn toplevel_destroyed(&mut self, surface: ToplevelSurface) {
        self.remove_toplevel(surface.wl_surface());
    }

    fn title_changed(&mut self, surface: ToplevelSurface) {
        self.toplevel_renamed(surface.wl_surface());
    }

    fn app_id_changed(&mut self, surface: ToplevelSurface) {
        self.toplevel_renamed(surface.wl_surface());
    }
}

impl XdgDecorationHandler for State {
    fn new_decoration(&mut self, toplevel: ToplevelSurface) {
        toplevel.with_pending_state(|s| s.decoration_mode = Some(DecorationMode::ServerSide));
    }

    fn request_mode(&mut self, toplevel: ToplevelSurface, _mode: DecorationMode) {
        toplevel.with_pending_state(|s| s.decoration_mode = Some(DecorationMode::ServerSide));
        if toplevel.is_initial_configure_sent() {
            toplevel.send_pending_configure();
        }
    }

    fn unset_mode(&mut self, toplevel: ToplevelSurface) {
        self.request_mode(toplevel, DecorationMode::ServerSide);
    }
}

impl OutputHandler for State {}

impl SeatHandler for State {
    type KeyboardFocus = WlSurface;
    type PointerFocus = WlSurface;
    type TouchFocus = WlSurface;

    fn seat_state(&mut self) -> &mut SeatState<State> {
        &mut self.seat_state
    }
}

impl SelectionHandler for State {
    type SelectionUserData = ();
}

impl DataDeviceHandler for State {
    fn data_device_state(&self) -> &DataDeviceState {
        &self.data_device_state
    }
}

impl ClientDndGrabHandler for State {}
impl ServerDndGrabHandler for State {}

delegate_compositor!(State);
delegate_shm!(State);
delegate_xdg_shell!(State);
delegate_xdg_decoration!(State);
delegate_output!(State);
delegate_seat!(State);
delegate_data_device!(State);
