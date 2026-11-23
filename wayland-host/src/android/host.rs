//! The compositor: smithay's wayland frontend on its own calloop thread.
//!
//! Presentation is a CPU copy of each toplevel's root wl_shm buffer into that toplevel's
//! ANativeWindow, paced by one frame tick (`paced.rs` has the rules): a commit only holds the
//! buffer, the tick copies each shown dirty toplevel once and then releases replaced buffers and
//! answers frame callbacks. Hidden toplevels (not visible, or no Surface) copy nothing and are
//! let go only on a slow keep-alive. Subsurfaces, popups and cursor surfaces are acknowledged on
//! the tick (buffers released, frame callbacks answered) but never shown.

use std::os::unix::net::UnixStream;
use std::path::PathBuf;
use std::sync::atomic::{AtomicBool, AtomicI64, Ordering};
use std::sync::Arc;
use std::time::{Duration, Instant};

use smithay::delegate_compositor;
use smithay::delegate_data_device;
use smithay::delegate_fractional_scale;
use smithay::delegate_output;
use smithay::delegate_seat;
use smithay::delegate_shm;
use smithay::delegate_viewporter;
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
use smithay::reexports::calloop::{EventLoop, Interest, LoopHandle, LoopSignal, Mode, PostAction, RegistrationToken};
use smithay::reexports::wayland_protocols::xdg::decoration::zv1::server::zxdg_toplevel_decoration_v1::Mode as DecorationMode;
use smithay::reexports::wayland_protocols::xdg::shell::server::xdg_toplevel;
use smithay::reexports::wayland_server::backend::{ClientData, ClientId, DisconnectReason};
use smithay::reexports::wayland_server::protocol::wl_buffer::WlBuffer;
use smithay::reexports::wayland_server::protocol::wl_callback::WlCallback;
use smithay::reexports::wayland_server::protocol::wl_seat::WlSeat;
use smithay::reexports::wayland_server::protocol::wl_shm;
use smithay::reexports::wayland_server::protocol::wl_surface::WlSurface;
use smithay::reexports::wayland_server::{Client, Display, DisplayHandle, ListeningSocket, Resource};
use smithay::utils::{Rectangle, Serial, Transform, SERIAL_COUNTER};
use smithay::wayland::buffer::BufferHandler;
use smithay::wayland::compositor::{
    get_parent, send_surface_state, with_states, with_surface_tree_downward, BufferAssignment, CompositorClientState,
    CompositorHandler, CompositorState, SurfaceAttributes, TraversalAction,
};
use smithay::wayland::fractional_scale::{with_fractional_scale, FractionalScaleHandler, FractionalScaleManagerState};
use smithay::wayland::output::{OutputHandler, OutputManagerState};
use smithay::wayland::selection::data_device::{
    ClientDndGrabHandler, DataDeviceHandler, DataDeviceState, ServerDndGrabHandler,
};
use smithay::wayland::selection::SelectionHandler;
use smithay::wayland::shell::xdg::decoration::{XdgDecorationHandler, XdgDecorationState};
use smithay::wayland::shell::xdg::{
    PopupSurface, PositionerState, SurfaceCachedState, ToplevelSurface, XdgShellHandler, XdgShellState,
    XdgToplevelSurfaceData,
};
use smithay::wayland::shm::{with_buffer_contents, ShmHandler, ShmState};
use smithay::wayland::viewporter::{ViewportCachedState, ViewporterState};
use smithay::wayland::xwayland_shell::XWaylandShellState;
use smithay::xwayland::{X11Surface, XWaylandClientData};

use smithay::backend::input::KeyState;
use smithay::input::keyboard::{FilterResult, Keycode};

use crate::geometry::{
    clamp_to_limit, content_span, integer_scale, logical_size, post_limit, surface_map, view_to_surface, Rect, RectF,
};
use crate::keymap::android_to_evdev;
use crate::paced::Paced;
use crate::protocol::*;

use super::focus::KeyboardFocus;
use super::java::HostEvents;
use super::log::{lerror, linfo, lwarn};
use super::window::NativeWindow;

/// A hidden toplevel's waiting frame callbacks and buffers are let go this often.
const KEEPALIVE: Duration = Duration::from_secs(1);
/// At most one copy-time line per toplevel per this interval, while the copy log is on.
const LOG_INTERVAL: Duration = Duration::from_secs(1);
/// Titles and app ids longer than this are cut before they cross to Java.
const MAX_NAME_CHARS: usize = 256;

/// Window ids are unique for the life of the process, across stop and start, so a late call
/// for a gone window can never reach a new one.
static NEXT_ID: AtomicI64 = AtomicI64::new(1);
/// The per-toplevel copy-time log line (debug interface).
pub static COPY_LOG: AtomicBool = AtomicBool::new(false);

/// Touch points one MotionEvent can carry here; more are dropped.
pub const MAX_TOUCH_POINTS: usize = MAX_TOUCH_POINTERS as usize;

/// One MotionEvent's pointers: (pointer id, x, y) in view pixels.
#[derive(Clone, Copy)]
pub struct TouchPoints {
    pub count: usize,
    pub points: [(i32, f32, f32); MAX_TOUCH_POINTS],
}

/// What Java asks of the compositor thread. Times are Android uptime milliseconds.
pub enum Command {
    SetWindow { id: i64, window: Option<NativeWindow> },
    Configure { id: i64, width: i32, height: i32, scale: f64 },
    SetVisible { id: i64, visible: bool },
    SetFocus { id: i64 },
    Touch { id: i64, action: i32, action_index: i32, time: u32, points: TouchPoints },
    Pointer { id: i64, action: i32, button: i32, x: f32, y: f32, time: u32 },
    /// An Android key code; the host maps it to evdev.
    Key { id: i64, android_keycode: i32, down: bool, time: u32 },
    Close { id: i64 },
}

pub struct StartConfig {
    pub socket_path: PathBuf,
    pub output_width: i32,
    pub output_height: i32,
    pub scale: f64,
    pub frame_period: Duration,
    pub xwayland: Option<super::xwayland::XwaylandConfig>,
}

/// The running compositor as Java's thread sees it.
pub struct HostHandle {
    pub sender: Sender<Command>,
    pub signal: LoopSignal,
    pub thread: std::thread::JoinHandle<()>,
    /// Set before a requested stop, so the thread's end is not reported as a death.
    pub stopping: Arc<AtomicBool>,
}

/// Reports the compositor thread's unrequested end to Java; `true` when it panicked.
pub type EndReporter = Box<dyn FnOnce(bool)>;

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

/// What a toplevel is to the protocol: an xdg_toplevel, or an X11 window from Xwayland.
pub enum Shell {
    Xdg(ToplevelSurface),
    X11(X11Surface),
}

impl Shell {
    /// An X11 window has a wl_surface only once Xwayland has associated one.
    pub fn wl_surface(&self) -> Option<WlSurface> {
        match self {
            Shell::Xdg(t) => Some(t.wl_surface().clone()),
            Shell::X11(w) => w.wl_surface(),
        }
    }

    fn keyboard_focus(&self) -> KeyboardFocus {
        match self {
            Shell::Xdg(t) => KeyboardFocus::Wl(t.wl_surface().clone()),
            Shell::X11(w) => KeyboardFocus::X11(w.clone()),
        }
    }

    /// The committed xdg window geometry; X11 windows have none.
    fn geometry(&self) -> Option<Rect> {
        match self {
            Shell::Xdg(t) => with_states(t.wl_surface(), |states| {
                states
                    .cached_state
                    .get::<SurfaceCachedState>()
                    .current()
                    .geometry
                    .map(|g| (g.loc.x, g.loc.y, g.size.w, g.size.h))
            }),
            Shell::X11(_) => None,
        }
    }

    fn is(&self, surface: &WlSurface) -> bool {
        match self {
            Shell::Xdg(t) => t.wl_surface() == surface,
            Shell::X11(w) => w.wl_surface().as_ref() == Some(surface),
        }
    }

    fn is_x11(&self) -> bool {
        matches!(self, Shell::X11(_))
    }

    fn names(&self) -> (String, String) {
        let (title, app_id) = match self {
            Shell::Xdg(t) => toplevel_names(t),
            Shell::X11(w) => (w.title(), w.class()),
        };
        (capped(title), capped(app_id))
    }

    /// The client's process id, 0 when unknown.
    fn client_pid(&self, dh: &DisplayHandle) -> i32 {
        match self {
            Shell::Xdg(t) => t
                .wl_surface()
                .client()
                .and_then(|c| c.get_credentials(dh).ok())
                .map(|c| c.pid)
                .unwrap_or(0),
            Shell::X11(w) => w.pid().and_then(|p| i32::try_from(p).ok()).unwrap_or(0),
        }
    }

    fn is_initial_configure_sent(&self) -> bool {
        match self {
            Shell::Xdg(t) => t.is_initial_configure_sent(),
            Shell::X11(_) => true,
        }
    }

    fn send_configure(&self) {
        match self {
            Shell::Xdg(t) => {
                t.send_configure();
            }
            Shell::X11(w) => {
                let _ = w.configure(None);
            }
        }
    }

    /// Asks for a window of `size`: logical units for xdg, pixels for X11.
    fn configure(&self, size: (i32, i32), activated: bool) {
        match self {
            Shell::Xdg(t) => {
                t.with_pending_state(|s| {
                    s.size = Some(size.into());
                    if activated {
                        s.states.set(xdg_toplevel::State::Activated);
                    } else {
                        s.states.unset(xdg_toplevel::State::Activated);
                    }
                    s.states.set(xdg_toplevel::State::TiledLeft);
                    s.states.set(xdg_toplevel::State::TiledRight);
                    s.states.set(xdg_toplevel::State::TiledTop);
                    s.states.set(xdg_toplevel::State::TiledBottom);
                });
                if t.is_initial_configure_sent() {
                    t.send_pending_configure();
                }
            }
            Shell::X11(w) => {
                let loc = w.geometry().loc;
                let _ = w.configure(Rectangle::new(loc, size.into()));
            }
        }
    }

    /// Only the keyboard-focus window is told it is active (focused chrome, caret).
    fn set_activated(&self, activated: bool) {
        match self {
            Shell::Xdg(t) => {
                t.with_pending_state(|s| {
                    if activated {
                        s.states.set(xdg_toplevel::State::Activated);
                    } else {
                        s.states.unset(xdg_toplevel::State::Activated);
                    }
                });
                if t.is_initial_configure_sent() {
                    t.send_pending_configure();
                }
            }
            Shell::X11(w) => {
                let _ = w.set_activated(activated);
            }
        }
    }
}

fn capped(mut s: String) -> String {
    if let Some((cut, _)) = s.char_indices().nth(MAX_NAME_CHARS) {
        s.truncate(cut);
    }
    s
}

pub struct Toplevel {
    /// 0 until the toplevel is announced to Java; a new id each time it is (re)mapped.
    pub id: i64,
    pub shell: Shell,
    announced: bool,
    window: Option<NativeWindow>,
    /// The launcher's setVisible; drawn only while visible and with a window.
    visible: bool,
    view_size: Option<(i32, i32)>,
    /// The display density the launcher shows this toplevel at.
    scale: f64,
    /// The last root commit's surface state, for mapping logical to buffer pixels.
    buffer_scale: i32,
    viewport_src: Option<RectF>,
    viewport_dst: Option<(i32, i32)>,
    /// xdg_surface.set_window_geometry as last committed (None for X11 windows).
    geometry: Option<Rect>,
    /// The part of the buffer shown, in logical coordinates, for input.
    content: RectF,
    /// The whole buffer and the posted rectangle of the last copy, for the log.
    buffer_size: (i32, i32),
    shown: Rect,
    /// The size last reported to Java.
    reported_size: (i32, i32),
    /// The size limit was hit; logged once per toplevel.
    clamp_logged: bool,
    paced: Paced<WlBuffer, WlCallback>,
    stats: CopyStats,
    last_log: Instant,
}

impl Toplevel {
    fn is_shown(&self) -> bool {
        self.visible && self.window.is_some()
    }
}

pub struct State {
    pub display_handle: DisplayHandle,
    pub loop_handle: LoopHandle<'static, State>,
    pub compositor_state: CompositorState,
    pub xdg_shell_state: XdgShellState,
    pub shm_state: ShmState,
    /// Held only to keep their globals alive.
    #[allow(dead_code)]
    pub output_manager_state: OutputManagerState,
    #[allow(dead_code)]
    pub fractional_scale_state: FractionalScaleManagerState,
    #[allow(dead_code)]
    pub viewporter_state: ViewporterState,
    pub seat_state: SeatState<State>,
    pub data_device_state: DataDeviceState,
    #[allow(dead_code)]
    pub decoration_state: XdgDecorationState,
    pub xwayland_shell_state: XWaylandShellState,
    pub seat: Seat<State>,
    pub output: Output,
    output_size: (i32, i32),
    output_scale: f64,
    pub toplevels: Vec<Toplevel>,
    /// The toplevel with keyboard focus, as the launcher set it.
    focused: Option<i64>,
    popups: Vec<PopupSurface>,
    /// Buffers and frame callbacks of surfaces that are not shown (subsurfaces, popups,
    /// cursors) and of removed toplevels, let go on the next frame tick.
    loose_releases: Vec<WlBuffer>,
    loose_frames: Vec<WlCallback>,
    /// Reused by every commit and tick so neither allocates once grown.
    scratch_frames: Vec<WlCallback>,
    tick_releases: Vec<WlBuffer>,
    tick_frames: Vec<WlCallback>,
    frame_period: Duration,
    /// The one frame timer and its deadline, while armed.
    timer: Option<(RegistrationToken, Instant)>,
    last_tick: Instant,
    last_keepalive: Instant,
    events: Box<dyn HostEvents>,
    pub xwayland: Option<super::xwayland::XwaylandState>,
}

pub fn spawn(
    config: StartConfig,
    events: impl FnOnce() -> Option<(Box<dyn HostEvents>, EndReporter)> + Send + 'static,
) -> std::io::Result<HostHandle> {
    let (sender, channel) = channel::channel::<Command>();
    let (signal_tx, signal_rx) = std::sync::mpsc::channel::<Result<LoopSignal, String>>();
    let stopping = Arc::new(AtomicBool::new(false));
    let stop_requested = stopping.clone();
    let thread = std::thread::Builder::new().name("tl-wayland".into()).spawn(move || {
        let Some((events, report_end)) = events() else {
            let _ = signal_tx.send(Err("cannot attach to the VM".into()));
            return;
        };
        let running = AtomicBool::new(false);
        let result = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| match setup(config, channel, events) {
            Ok((mut event_loop, mut state)) => {
                running.store(true, Ordering::SeqCst);
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
        }));
        let panicked = result.is_err();
        if panicked {
            lerror!("compositor thread panicked; the host is down");
        }
        // Whatever ended a running host unasked (a panic, a loop error) is reported, so Java
        // clears its windows and a later start works.
        if running.load(Ordering::SeqCst) && !stop_requested.load(Ordering::SeqCst) {
            report_end(panicked);
        }
    })?;
    match signal_rx.recv() {
        Ok(Ok(signal)) => Ok(HostHandle { sender, signal, thread, stopping }),
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
    let fractional_scale_state = FractionalScaleManagerState::new::<State>(&dh);
    let viewporter_state = ViewporterState::new::<State>(&dh);
    let mut seat_state = SeatState::new();
    let data_device_state = DataDeviceState::new::<State>(&dh);
    let decoration_state = XdgDecorationState::new::<State>(&dh);
    // Only Xwayland's client sees this global.
    let xwayland_shell_state = XWaylandShellState::new::<State>(&dh);

    let mut seat = seat_state.new_wl_seat(&dh, "seat0");
    seat.add_pointer();
    seat.add_touch();
    // XKB_CONFIG_ROOT was set by nativeStart before this thread existed.
    match seat.add_keyboard(XkbConfig::default(), 400, 25) {
        Ok(_) => linfo!("keyboard on the seat"),
        Err(e) => lwarn!("no keyboard on the seat: {e:?}"),
    }

    let output = Output::new(
        "TL-1".into(),
        PhysicalProperties {
            size: (0, 0).into(),
            subpixel: Subpixel::Unknown,
            make: "Termux Launcher".into(),
            model: "Linux windows".into(),
        },
    );
    output.create_global::<State>(&dh);
    let output_size = (config.output_width.max(1), config.output_height.max(1));
    let refresh_mhz = (1_000_000_000_000u128 / config.frame_period.as_nanos().max(1)) as i32;
    let mode = OutputMode { size: output_size.into(), refresh: refresh_mhz };
    output.change_current_state(Some(mode), Some(Transform::Normal), Some(output_scale(config.scale)), Some((0, 0).into()));
    output.set_preferred(mode);

    // bind_absolute takes `<path>.lock` and removes a stale socket left by a dead host; a live
    // host keeps its lock and the bind fails instead of stealing its socket.
    let listener = ListeningSocket::bind_absolute(config.socket_path.clone())?;
    linfo!("listening on {} (scale {}, frame {:?})", config.socket_path.display(), config.scale, config.frame_period);
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

    let now = Instant::now();
    let mut state = State {
        display_handle: dh,
        loop_handle,
        compositor_state,
        xdg_shell_state,
        shm_state,
        output_manager_state,
        fractional_scale_state,
        viewporter_state,
        seat_state,
        data_device_state,
        decoration_state,
        xwayland_shell_state,
        seat,
        output,
        output_size,
        output_scale: config.scale,
        toplevels: Vec::new(),
        focused: None,
        popups: Vec::new(),
        loose_releases: Vec::new(),
        loose_frames: Vec::new(),
        scratch_frames: Vec::new(),
        tick_releases: Vec::new(),
        tick_frames: Vec::new(),
        frame_period: config.frame_period,
        timer: None,
        last_tick: now,
        last_keepalive: now,
        events,
        xwayland: None,
    };
    if let Some(xw) = config.xwayland {
        super::xwayland::start(&mut state, xw);
    }
    Ok((event_loop, state))
}

fn output_scale(scale: f64) -> Scale {
    Scale::Custom { advertised_integer: integer_scale(scale), fractional: scale }
}

/// CLOCK_MONOTONIC in milliseconds, the clock of Android's event times (uptimeMillis), cut to
/// the protocol's 32 bits.
pub fn now_ms() -> u32 {
    let mut ts = libc::timespec { tv_sec: 0, tv_nsec: 0 };
    // SAFETY: a valid out pointer to a timespec on this stack.
    unsafe { libc::clock_gettime(libc::CLOCK_MONOTONIC, &mut ts) };
    (ts.tv_sec as u64).wrapping_mul(1000).wrapping_add(ts.tv_nsec as u64 / 1_000_000) as u32
}

impl State {
    pub fn insert_client(&mut self, stream: UnixStream) {
        if let Err(e) = self.display_handle.insert_client(stream, Arc::new(ClientState::default())) {
            lwarn!("insert_client: {e}");
        }
    }

    fn shutdown(&mut self) {
        super::xwayland::stop(self);
        let mut releases = std::mem::take(&mut self.tick_releases);
        let mut frames = std::mem::take(&mut self.tick_frames);
        for mut t in self.toplevels.drain(..) {
            t.paced.take_all(&mut releases, &mut frames);
        }
        releases.append(&mut self.loose_releases);
        for b in releases {
            if b.is_alive() {
                b.release();
            }
        }
    }

    pub fn toplevel_index(&self, id: i64) -> Option<usize> {
        if id <= 0 {
            return None;
        }
        self.toplevels.iter().position(|t| t.id == id && t.announced)
    }

    pub fn toplevel_index_for(&self, surface: &WlSurface) -> Option<usize> {
        self.toplevels.iter().position(|t| t.shell.is(surface))
    }

    pub fn view_size(&self, i: usize) -> Option<(i32, i32)> {
        self.toplevels[i].view_size
    }

    fn handle_command(&mut self, cmd: Command) {
        match cmd {
            Command::SetWindow { id, window } => self.set_window(id, window),
            Command::Configure { id, width, height, scale } => self.configure(id, width, height, scale),
            Command::SetVisible { id, visible } => self.set_visible(id, visible),
            Command::SetFocus { id } => self.set_focus(id),
            Command::Touch { id, action, action_index, time, points } => self.touch(id, action, action_index, time, &points),
            Command::Pointer { id, action, button, x, y, time } => self.pointer(id, action, button, x, y, time),
            Command::Key { id, android_keycode, down, time } => self.key(id, android_keycode, down, time),
            Command::Close { id } => self.close(id),
        }
    }

    fn set_window(&mut self, id: i64, window: Option<NativeWindow>) {
        let Some(i) = self.toplevel_index(id) else { return };
        let has_window = window.is_some();
        self.toplevels[i].window = window;
        if has_window {
            // A fresh Surface is blank: show the held buffer on the next tick.
            if !self.toplevels[i].paced.repaint() && self.toplevels[i].shell.is_initial_configure_sent() {
                // Nothing held yet: a configure makes most clients draw.
                self.toplevels[i].shell.send_configure();
            }
        }
        self.schedule();
    }

    fn set_visible(&mut self, id: i64, visible: bool) {
        let Some(i) = self.toplevel_index(id) else { return };
        self.toplevels[i].visible = visible;
        // Shown again: what was committed while hidden is still dirty and is copied now.
        self.schedule();
    }

    fn configure(&mut self, id: i64, width: i32, height: i32, scale: f64) {
        let Some(i) = self.toplevel_index(id) else { return };
        if width <= 0 || height <= 0 {
            return;
        }
        let t = &mut self.toplevels[i];
        if t.view_size == Some((width, height)) && t.scale == scale {
            return;
        }
        t.view_size = Some((width, height));
        let scale_changed = t.scale != scale;
        t.scale = scale;
        let activated = self.focused == Some(t.id);
        let size = if t.shell.is_x11() { (width, height) } else { logical_size((width, height), scale) };
        t.shell.configure(size, activated);
        if scale_changed {
            self.apply_scale(i);
        }
        // Until the client answers with a buffer of the new size, the view shows the old
        // contents stretched rather than whatever the resized texture holds.
        self.toplevels[i].paced.repaint();
        self.schedule();
    }

    /// Tells the toplevel's surfaces their preferred scale (fractional and integer), and the
    /// output its scale: one display, so the newest density wins.
    fn apply_scale(&mut self, i: usize) {
        let scale = self.toplevels[i].scale;
        if scale != self.output_scale {
            self.output_scale = scale;
            self.output.change_current_state(None, None, Some(output_scale(scale)), None);
        }
        if self.toplevels[i].shell.is_x11() {
            return;
        }
        let Some(root) = self.toplevels[i].shell.wl_surface() else { return };
        with_surface_tree_downward(
            &root,
            (),
            |_, _, _| TraversalAction::DoChildren(()),
            |s, states, _| {
                with_fractional_scale(states, |f| f.set_preferred_scale(scale));
                send_surface_state(s, states, integer_scale(scale), Transform::Normal);
            },
            |_, _, _| true,
        );
    }

    /// View pixels to surface coordinates: the shown content fills the view.
    fn surface_point(t: &Toplevel, x: f32, y: f32) -> (f64, f64) {
        view_to_surface(x, y, t.view_size, t.content)
    }

    /// Moves keyboard focus. Keys held at the change are released first, so neither the old
    /// window nor the new one sees a key stuck down.
    fn set_focus(&mut self, id: i64) {
        let new = self.toplevel_index(id).map(|i| self.toplevels[i].id);
        if new == self.focused {
            return;
        }
        self.release_all_keys();
        let old = std::mem::replace(&mut self.focused, new);
        if let Some(i) = old.and_then(|id| self.toplevel_index(id)) {
            self.toplevels[i].shell.set_activated(false);
        }
        let target = new.and_then(|id| self.toplevel_index(id)).map(|i| {
            self.toplevels[i].shell.set_activated(true);
            self.toplevels[i].shell.keyboard_focus()
        });
        if let Some(keyboard) = self.seat.get_keyboard() {
            keyboard.set_focus(self, target, SERIAL_COUNTER.next_serial());
        }
    }

    /// Clears the seat's pressed keys without telling the client; it gets wl_keyboard.leave
    /// next, which releases them on its side.
    fn release_all_keys(&mut self) {
        let Some(keyboard) = self.seat.get_keyboard() else { return };
        for key in keyboard.pressed_keys() {
            keyboard.input_intercept::<(), _>(self, key, KeyState::Released, |_, _, _| ());
        }
    }

    fn touch(&mut self, id: i64, action: i32, action_index: i32, time: u32, points: &TouchPoints) {
        if !matches!(action, TOUCH_DOWN | TOUCH_UP | TOUCH_MOVE | TOUCH_CANCEL | TOUCH_POINTER_DOWN | TOUCH_POINTER_UP) {
            return;
        }
        let Some(i) = self.toplevel_index(id) else { return };
        let Some(touch) = self.seat.get_touch() else { return };
        let Some(surface) = self.toplevels[i].shell.wl_surface() else { return };
        if action == TOUCH_CANCEL {
            touch.cancel(self);
            return;
        }
        // One MotionEvent is one wl_touch frame: the pointer at action_index goes down or up,
        // every other pointer moves.
        for (k, &(pointer_id, x, y)) in points.points[..points.count.min(MAX_TOUCH_POINTS)].iter().enumerate() {
            let (sx, sy) = Self::surface_point(&self.toplevels[i], x, y);
            let slot = Some(pointer_id.max(0) as u32).into();
            let acting = k as i32 == action_index;
            match action {
                TOUCH_DOWN | TOUCH_POINTER_DOWN if acting => {
                    let focus = Some((surface.clone(), (0.0, 0.0).into()));
                    let serial = SERIAL_COUNTER.next_serial();
                    touch.down(self, focus, &DownEvent { slot, location: (sx, sy).into(), serial, time });
                }
                TOUCH_UP | TOUCH_POINTER_UP if acting => {
                    touch.up(self, &UpEvent { slot, serial: SERIAL_COUNTER.next_serial(), time });
                }
                TOUCH_MOVE | TOUCH_POINTER_DOWN | TOUCH_POINTER_UP => {
                    let focus = Some((surface.clone(), (0.0, 0.0).into()));
                    touch.motion(self, focus, &TouchMotion { slot, location: (sx, sy).into(), time });
                }
                _ => {}
            }
        }
        touch.frame(self);
    }

    fn key(&mut self, id: i64, android_keycode: i32, down: bool, time: u32) {
        // Keys go only to the window the launcher focused; a late key for another is dropped.
        if id <= 0 || self.focused != Some(id) {
            return;
        }
        let Some(evdev) = android_to_evdev(android_keycode) else { return };
        let Some(keyboard) = self.seat.get_keyboard() else { return };
        let state = if down { KeyState::Pressed } else { KeyState::Released };
        // xkb key codes are evdev codes + 8.
        keyboard.input::<(), _>(self, Keycode::new(evdev + 8), state, SERIAL_COUNTER.next_serial(), time, |_, _, _| {
            FilterResult::Forward
        });
    }

    fn close(&mut self, id: i64) {
        let Some(i) = self.toplevel_index(id) else { return };
        match &self.toplevels[i].shell {
            Shell::Xdg(t) => t.send_close(),
            Shell::X11(w) => {
                if let Err(e) = w.close() {
                    lwarn!("X11 close: {e}");
                }
            }
        }
    }

    fn pointer(&mut self, id: i64, action: i32, button: i32, x: f32, y: f32, time: u32) {
        let Some(i) = self.toplevel_index(id) else { return };
        let Some(pointer) = self.seat.get_pointer() else { return };
        let Some(surface) = self.toplevels[i].shell.wl_surface() else { return };
        let serial = SERIAL_COUNTER.next_serial();
        match action {
            POINTER_MOTION => {
                let (sx, sy) = Self::surface_point(&self.toplevels[i], x, y);
                pointer.motion(self, Some((surface, (0.0, 0.0).into())), &PointerMotion { location: (sx, sy).into(), serial, time });
            }
            POINTER_BUTTON_DOWN | POINTER_BUTTON_UP => {
                let state = if action == POINTER_BUTTON_DOWN {
                    smithay::backend::input::ButtonState::Pressed
                } else {
                    smithay::backend::input::ButtonState::Released
                };
                pointer.button(self, &ButtonEvent { serial, time, button: button.max(0) as u32, state });
            }
            POINTER_SCROLL => {
                // View pixels to surface units, like a motion's distance.
                let (ox, oy) = Self::surface_point(&self.toplevels[i], 0.0, 0.0);
                let (sx, sy) = Self::surface_point(&self.toplevels[i], x, y);
                let (dx, dy) = (sx - ox, sy - oy);
                let mut frame = AxisFrame::new(time).source(smithay::backend::input::AxisSource::Wheel);
                if dx != 0.0 {
                    frame = frame.value(smithay::backend::input::Axis::Horizontal, dx);
                }
                if dy != 0.0 {
                    frame = frame.value(smithay::backend::input::Axis::Vertical, dy);
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

    /// When the next tick is due: a frame period after the last one while a shown toplevel or
    /// a loose surface waits, else the next keep-alive while only hidden ones do, else never.
    fn next_deadline(&self) -> Option<Instant> {
        let mut hidden_waits = false;
        let mut frame_due = !self.loose_releases.is_empty() || !self.loose_frames.is_empty();
        for t in &self.toplevels {
            if t.is_shown() {
                frame_due |= t.paced.has_work();
            } else {
                hidden_waits |= t.paced.has_waiting();
            }
        }
        if frame_due {
            Some(self.last_tick + self.frame_period)
        } else if hidden_waits {
            Some(self.last_keepalive + KEEPALIVE)
        } else {
            None
        }
    }

    /// Arms the one frame timer for the next deadline, unless it already fires by then. The
    /// timer re-arms itself while work remains, so a running animation inserts nothing.
    fn schedule(&mut self) {
        let Some(deadline) = self.next_deadline() else { return };
        if let Some((token, armed)) = self.timer {
            if armed <= deadline {
                return;
            }
            self.loop_handle.remove(token);
            self.timer = None;
        }
        let res = self.loop_handle.insert_source(Timer::from_deadline(deadline), |_, _, state| match state.tick() {
            Some(next) => {
                if let Some(timer) = state.timer.as_mut() {
                    timer.1 = next;
                }
                TimeoutAction::ToInstant(next)
            }
            None => {
                state.timer = None;
                TimeoutAction::Drop
            }
        });
        match res {
            Ok(token) => self.timer = Some((token, deadline)),
            Err(e) => lwarn!("frame timer: {e}"),
        }
    }

    /// One frame tick: copy each shown dirty toplevel once, then release what waited and answer
    /// frame callbacks. Returns the next deadline.
    fn tick(&mut self) -> Option<Instant> {
        let now = Instant::now();
        self.last_tick = now;
        let keepalive = now.duration_since(self.last_keepalive) >= KEEPALIVE;
        if keepalive {
            self.last_keepalive = now;
        }
        let mut releases = std::mem::take(&mut self.tick_releases);
        let mut frames = std::mem::take(&mut self.tick_frames);
        let mut copied = false;
        for i in 0..self.toplevels.len() {
            let shown = self.toplevels[i].is_shown();
            if let Some(buffer) = self.toplevels[i].paced.tick(shown, keepalive, &mut releases, &mut frames) {
                self.copy(i, &buffer);
                copied = true;
            }
        }
        releases.append(&mut self.loose_releases);
        frames.append(&mut self.loose_frames);
        // A client answered now usually commits again within the period: keep the timer armed
        // for one more tick, so a running animation re-arms instead of inserting a timer per frame.
        let worked = copied || !releases.is_empty() || !frames.is_empty();
        for b in releases.drain(..) {
            // A buffer another toplevel still shows is released when that one lets it go.
            if b.is_alive() && !self.toplevels.iter().any(|t| t.paced.current() == Some(&b)) {
                b.release();
            }
        }
        let time = now_ms();
        for cb in frames.drain(..) {
            if cb.is_alive() {
                cb.done(time);
            }
        }
        self.tick_releases = releases;
        self.tick_frames = frames;
        if worked {
            Some(now + self.frame_period)
        } else {
            self.next_deadline()
        }
    }

    /// Copies `buffer` into toplevel `i`'s window and reports a new shown size. A window whose
    /// post fails (its consumer is gone) is dropped, as if detached, so nothing is drawn into it
    /// again until the launcher hands a new Surface.
    fn copy(&mut self, i: usize, buffer: &WlBuffer) {
        if !buffer.is_alive() {
            return;
        }
        let output = self.output_size;
        let t = &mut self.toplevels[i];
        match post_buffer(t, buffer, output) {
            Ok(()) => {}
            Err(rc) => {
                lwarn!("toplevel {}: post failed ({rc}); the Surface is dropped until a new one comes", t.id);
                t.window = None;
            }
        }
        let size = (t.shown.2, t.shown.3);
        if size != t.reported_size && t.announced {
            t.reported_size = size;
            let (title, app_id) = t.shell.names();
            let id = t.id;
            self.events.window_changed(id, &title, &app_id, size.0, size.1);
        }
    }

    pub fn announce(&mut self, i: usize) {
        let id = NEXT_ID.fetch_add(1, Ordering::Relaxed);
        let (title, app_id) = self.toplevels[i].shell.names();
        let pid = self.toplevels[i].shell.client_pid(&self.display_handle);
        let flags = if self.toplevels[i].shell.is_x11() { WINDOW_X11 } else { 0 };
        let t = &mut self.toplevels[i];
        t.id = id;
        t.announced = true;
        t.reported_size = (0, 0);
        if let Some(surface) = t.shell.wl_surface() {
            self.output.enter(&surface);
        }
        self.apply_scale(i);
        self.events.window_created(id, &title, &app_id, pid, flags);
    }

    pub fn add_toplevel(&mut self, shell: Shell) {
        self.toplevels.push(Toplevel {
            id: 0,
            shell,
            announced: false,
            window: None,
            visible: true,
            view_size: None,
            scale: self.output_scale,
            buffer_scale: 1,
            viewport_src: None,
            viewport_dst: None,
            geometry: None,
            content: (0.0, 0.0, 0.0, 0.0),
            buffer_size: (0, 0),
            shown: (0, 0, 0, 0),
            reported_size: (0, 0),
            clamp_logged: false,
            paced: Paced::new(),
            stats: CopyStats::default(),
            last_log: Instant::now(),
        });
    }

    /// Takes the toplevel off the launcher's screen: its id ends, its window and view go. The
    /// toplevel itself may stay (an unmapped xdg_toplevel maps again with a new id).
    fn withdraw(&mut self, i: usize) {
        let t = &mut self.toplevels[i];
        let id = t.id;
        let was_announced = t.announced;
        t.announced = false;
        t.id = 0;
        t.window = None;
        t.visible = true;
        t.view_size = None;
        t.clamp_logged = false;
        if self.focused == Some(id) && id > 0 {
            self.release_all_keys();
            self.focused = None;
        }
        if was_announced {
            self.events.window_destroyed(id);
        }
    }

    pub fn remove_toplevel_at(&mut self, i: usize) {
        self.withdraw(i);
        let mut t = self.toplevels.remove(i);
        t.paced.take_all(&mut self.loose_releases, &mut self.loose_frames);
        self.schedule();
    }

    pub fn rename_at(&mut self, i: usize) {
        let t = &self.toplevels[i];
        if t.announced {
            let (title, app_id) = t.shell.names();
            let size = t.reported_size;
            let id = t.id;
            self.events.window_changed(id, &title, &app_id, size.0, size.1);
        }
    }
}

fn toplevel_names(surface: &ToplevelSurface) -> (String, String) {
    with_states(surface.wl_surface(), |states| {
        states
            .data_map
            .get::<XdgToplevelSurfaceData>()
            .and_then(|d| d.lock().ok().map(|d| (d.title.clone().unwrap_or_default(), d.app_id.clone().unwrap_or_default())))
            .unwrap_or_default()
    })
}

/// Copies the shown part of `buffer` into the toplevel's window: the window geometry, mapped
/// from logical to buffer pixels, clipped to the buffer and cut to the size limit, so a client
/// never decides how much the launcher allocates. Err with the platform's code when the post
/// failed; Ok also when the buffer could not be read (logged, nothing posted).
fn post_buffer(t: &mut Toplevel, buffer: &WlBuffer, output: (i32, i32)) -> Result<(), i32> {
    let id = t.id;
    let (geometry, bs, src, dst) = (t.geometry, t.buffer_scale, t.viewport_src, t.viewport_dst);
    let limit = post_limit(t.view_size, output);
    let Some(window) = t.window.as_mut() else { return Ok(()) };
    let stats = &mut t.stats;
    let result = with_buffer_contents(buffer, |ptr, len, data| {
        let (w, h, stride, offset) = (data.width, data.height, data.stride, data.offset);
        if w <= 0 || h <= 0 || stride < w * 4 || offset < 0 {
            return None;
        }
        let map = surface_map(w, h, bs, src, dst);
        let logical = map.logical_content(geometry);
        let full = map.to_buffer(logical, w, h);
        let shown = clamp_to_limit(full, limit);
        let (start, bytes) = content_span(offset as usize, stride as usize, shown, len)?;
        let opaque = match data.format {
            wl_shm::Format::Argb8888 => false,
            wl_shm::Format::Xrgb8888 => true,
            _ => return None,
        };
        // SAFETY: smithay holds the pool mapping for the duration of this closure and
        // content_span checked the range against its length.
        let src = unsafe { std::slice::from_raw_parts(ptr.add(start), bytes) };
        let posted = window.post(src, stride as usize, shown.2, shown.3, opaque).map(|timing| {
            stats.frames += 1;
            stats.copy_us_sum += timing.copy_us;
            stats.copy_us_max = stats.copy_us_max.max(timing.copy_us);
            stats.total_us_sum += timing.total_us;
            stats.last_copy_us = timing.copy_us;
        });
        Some((posted, (w, h), shown, full, map.to_logical(shown)))
    });
    let (posted, size, shown, full, logical) = match result {
        Ok(Some(v)) => v,
        Ok(None) => return Ok(()),
        Err(e) => {
            lwarn!("toplevel {id}: unreadable buffer: {e:?}");
            return Ok(());
        }
    };
    if shown != full && !t.clamp_logged {
        t.clamp_logged = true;
        lwarn!("toplevel {id}: frame {}x{} is over the limit {}x{}; showing its top-left part", full.2, full.3, limit.0, limit.1);
    }
    t.buffer_size = size;
    t.shown = shown;
    t.content = logical;
    if COPY_LOG.load(Ordering::Relaxed) && t.last_log.elapsed() >= LOG_INTERVAL && t.stats.frames > 0 {
        let s = &t.stats;
        linfo!(
            "copy toplevel={} buffer={}x{} shown={}x{}+{}+{} bytes={} frames={} copy_us_last={} copy_us_avg={} copy_us_max={} post_us_avg={}",
            id,
            t.buffer_size.0,
            t.buffer_size.1,
            shown.2,
            shown.3,
            shown.0,
            shown.1,
            shown.2 as i64 * shown.3 as i64 * 4,
            s.frames,
            s.last_copy_us,
            s.copy_us_sum / s.frames as u64,
            s.copy_us_max,
            s.total_us_sum / s.frames as u64
        );
        t.stats = CopyStats::default();
        t.last_log = Instant::now();
    }
    posted
}

impl CompositorHandler for State {
    fn compositor_state(&mut self) -> &mut CompositorState {
        &mut self.compositor_state
    }

    fn client_compositor_state<'a>(&self, client: &'a Client) -> &'a CompositorClientState {
        if let Some(state) = client.get_data::<ClientState>() {
            return &state.compositor_state;
        }
        if let Some(state) = client.get_data::<XWaylandClientData>() {
            return &state.compositor_state;
        }
        // Every client is inserted with one of the two above; unreachable.
        panic!("client without compositor state")
    }

    fn commit(&mut self, surface: &WlSurface) {
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
            if !t.shell.is_initial_configure_sent() {
                t.shell.send_configure();
            }
        }

        // Walk the whole tree: synced subsurfaces apply their state on the parent's commit.
        let mut root_buffer: Option<BufferAssignment> = None;
        let mut root_scale = 1;
        let mut root_viewport: (Option<RectF>, Option<(i32, i32)>) = (None, None);
        let mut frames = std::mem::take(&mut self.scratch_frames);
        let loose = &mut self.loose_releases;
        with_surface_tree_downward(
            &root,
            (),
            |_, _, _| TraversalAction::DoChildren(()),
            |s, states, _| {
                let is_root = *s == root && index.is_some();
                {
                    let mut guard = states.cached_state.get::<SurfaceAttributes>();
                    let attrs = guard.current();
                    frames.append(&mut attrs.frame_callbacks);
                    if is_root {
                        root_scale = attrs.buffer_scale;
                    }
                    if let Some(assignment) = attrs.buffer.take() {
                        if is_root {
                            root_buffer = Some(assignment);
                        } else if let BufferAssignment::NewBuffer(b) = assignment {
                            // Subsurfaces, popups and cursors are not shown; released on the tick.
                            loose.push(b);
                        }
                    }
                }
                if is_root {
                    let mut guard = states.cached_state.get::<ViewportCachedState>();
                    let v = guard.current();
                    root_viewport = (
                        v.src.map(|r| (r.loc.x, r.loc.y, r.size.w, r.size.h)),
                        v.dst.map(|d| (d.w, d.h)),
                    );
                }
            },
            |_, _, _| true,
        );

        let Some(i) = index else {
            self.loose_frames.append(&mut frames);
            self.scratch_frames = frames;
            self.schedule();
            return;
        };

        // Window geometry, scale and viewport are double-buffered with the buffer.
        self.toplevels[i].geometry = self.toplevels[i].shell.geometry();
        match root_buffer {
            Some(BufferAssignment::Removed) => {
                self.toplevels[i].paced.remove_buffer();
                if let Shell::Xdg(t) = &self.toplevels[i].shell {
                    // An unmapped xdg_toplevel starts over: its next bufferless commit must get
                    // an initial configure again, and it maps as a new window.
                    t.reset_initial_configure_sent();
                    self.withdraw(i);
                }
            }
            Some(BufferAssignment::NewBuffer(buffer)) => {
                let t = &mut self.toplevels[i];
                t.buffer_scale = root_scale;
                t.viewport_src = root_viewport.0;
                t.viewport_dst = root_viewport.1;
                t.paced.commit_buffer(buffer);
                if !t.announced {
                    self.announce(i);
                }
            }
            None => {
                if !self.toplevels[i].announced && self.toplevels[i].shell.is_initial_configure_sent() {
                    // The initial (bufferless) commit: title and app id are set by now.
                    self.announce(i);
                }
            }
        }
        self.toplevels[i].paced.add_frames(&mut frames);
        self.scratch_frames = frames;
        self.schedule();
    }
}

impl BufferHandler for State {
    fn buffer_destroyed(&mut self, buffer: &WlBuffer) {
        for t in &mut self.toplevels {
            t.paced.forget(buffer);
        }
        self.loose_releases.retain(|b| b != buffer);
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
        surface.with_pending_state(|s| s.decoration_mode = Some(DecorationMode::ServerSide));
        self.add_toplevel(Shell::Xdg(surface));
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
        if let Some(i) = self.toplevel_index_for(surface.wl_surface()) {
            self.remove_toplevel_at(i);
        }
    }

    fn title_changed(&mut self, surface: ToplevelSurface) {
        if let Some(i) = self.toplevel_index_for(surface.wl_surface()) {
            self.rename_at(i);
        }
    }

    fn app_id_changed(&mut self, surface: ToplevelSurface) {
        if let Some(i) = self.toplevel_index_for(surface.wl_surface()) {
            self.rename_at(i);
        }
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

impl FractionalScaleHandler for State {
    /// A surface asked for its fractional scale: its toplevel's, else the output's.
    fn new_fractional_scale(&mut self, surface: WlSurface) {
        let mut root = surface.clone();
        while let Some(parent) = get_parent(&root) {
            root = parent;
        }
        let scale = self.toplevel_index_for(&root).map(|i| self.toplevels[i].scale).unwrap_or(self.output_scale);
        with_states(&surface, |states| with_fractional_scale(states, |f| f.set_preferred_scale(scale)));
    }
}

impl OutputHandler for State {}

impl SeatHandler for State {
    type KeyboardFocus = KeyboardFocus;
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
delegate_fractional_scale!(State);
delegate_viewporter!(State);
