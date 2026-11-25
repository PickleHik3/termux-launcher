//! The compositor: smithay's wayland frontend on its own calloop thread.
//!
//! Presentation is a CPU copy of each toplevel's view into that toplevel's ANativeWindow, paced
//! by one frame tick (`paced.rs` has the rules): a commit only holds the buffer, the tick copies
//! each shown dirty toplevel once and then releases replaced buffers and answers frame callbacks.
//! Hidden toplevels (not visible, or no Surface) copy nothing and are let go only on a slow
//! keep-alive.
//!
//! A view is the toplevel's surface tree (its subsurfaces, below and above, nested), then the
//! override-redirect X windows it owns, then its xdg popups in stacking order, each with its own
//! subsurface tree (`compose.rs` places, cuts and blends them). Every surface of a view holds its
//! buffer like the toplevel does (`Part`), and releases and frame callbacks go on the same tick.
//! A toplevel with no other surface keeps the single-copy path. Input goes to the surface under
//! the point; cursor and drag icons are acknowledged on the tick but never shown.

use std::os::unix::net::UnixStream;
use std::path::PathBuf;
use std::sync::atomic::{AtomicBool, AtomicI64, Ordering};
use std::sync::Arc;
use std::time::{Duration, Instant};

use smithay::delegate_compositor;
use smithay::delegate_data_device;
use smithay::delegate_dmabuf;
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
use smithay::reexports::wayland_server::protocol::wl_output::WlOutput;
use smithay::reexports::wayland_server::protocol::wl_seat::WlSeat;
use smithay::reexports::wayland_server::protocol::wl_surface::WlSurface;
use smithay::reexports::wayland_server::{Client, Display, DisplayHandle, ListeningSocket, Resource};
use smithay::utils::{Logical, Point, Rectangle, Serial, Transform, SERIAL_COUNTER};
use smithay::backend::allocator::dmabuf::Dmabuf;
use smithay::wayland::buffer::BufferHandler;
use smithay::wayland::dmabuf::{DmabufGlobal, DmabufHandler, DmabufState, ImportNotifier};
use smithay::wayland::compositor::{
    get_parent, send_surface_state, with_states, with_surface_tree_downward, with_surface_tree_upward, BufferAssignment,
    CompositorClientState, CompositorHandler, CompositorState, RectangleKind, SubsurfaceCachedState, SurfaceAttributes,
    SurfaceData, TraversalAction,
};
use smithay::wayland::fractional_scale::{with_fractional_scale, FractionalScaleHandler, FractionalScaleManagerState};
use smithay::wayland::output::{OutputHandler, OutputManagerState};
use smithay::wayland::selection::data_device::{
    ClientDndGrabHandler, DataDeviceHandler, DataDeviceState, ServerDndGrabHandler,
};
use smithay::wayland::selection::{SelectionHandler, SelectionSource, SelectionTarget};
use std::os::fd::OwnedFd;
use smithay::wayland::shell::xdg::decoration::{XdgDecorationHandler, XdgDecorationState};
use smithay::wayland::shell::xdg::{
    PopupSurface, PositionerState, SurfaceCachedState, ToplevelSurface, XdgPopupSurfaceData, XdgShellHandler,
    XdgShellState, XdgToplevelSurfaceData,
};
use smithay::wayland::shm::{ShmHandler, ShmState};
use smithay::wayland::viewporter::{ViewportCachedState, ViewporterState};
use smithay::wayland::xwayland_shell::XWaylandShellState;
use smithay::xwayland::xwm::WmWindowType;
use smithay::xwayland::{X11Surface, XWaylandClientData};

use smithay::backend::input::KeyState;
use smithay::input::keyboard::{FilterResult, Keycode};

use crate::compose::{self, HitLayer};
use crate::geometry::{
    clamp_to_limit, content_span, integer_scale, integer_scale_view, logical_size, post_limit, surface_map, view_to_surface, Rect, RectF,
};
use crate::keymap::android_to_evdev;
use crate::paced::Paced;
use crate::pixels;
use crate::png;
use crate::window_class::{WindowClass, WindowHints, WindowKind};
use crate::protocol::*;

use super::focus::KeyboardFocus;
use super::clipboard::ClipSource;
use super::java::HostEvents;
use super::log::{lerror, linfo, lwarn};
use super::source::{self, fourcc_name, Desc, DmabufMaps, Kind};
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
/// One line for the first refused dmabuf import; a client can retry in a loop.
static DMABUF_REJECT_LOGGED: AtomicBool = AtomicBool::new(false);
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
    /// The size, in px, and scale a new full-page window gets: its first configure uses it
    /// while it has no view yet.
    PageSize { width: i32, height: i32, scale: f64 },
    SetVisible { id: i64, visible: bool },
    SetFocus { id: i64 },
    /// The launcher gave the window the whole screen, or took it back: the Fullscreen state (xdg)
    /// or `_NET_WM_STATE_FULLSCREEN` (X11); the size follows in the next Configure.
    SetFullscreen { id: i64, on: bool },
    Touch { id: i64, action: i32, action_index: i32, time: u32, points: TouchPoints },
    Pointer { id: i64, action: i32, button: i32, x: f32, y: f32, time: u32 },
    /// An Android key code; the host maps it to evdev.
    Key { id: i64, android_keycode: i32, down: bool, time: u32 },
    Close { id: i64 },
    /// Android's clipboard text, for Linux clients.
    SetClipboardText { text: String },
    /// Debug: write the window's shown pixels as a PNG to `path` (checked by the service).
    Dump { id: i64, path: String },
}

pub struct StartConfig {
    pub socket_path: PathBuf,
    pub output_width: i32,
    pub output_height: i32,
    pub scale: f64,
    pub frame_period: Duration,
    pub xwayland: Option<super::xwayland::XwaylandConfig>,
    /// A socket the launcher already listens on at `socket_path`, handed over to be accepted on;
    /// None binds `socket_path` here. An adopted socket is left in place on stop: the launcher
    /// keeps listening on it.
    pub listen_fd: Option<OwnedFd>,
}

/// Where clients connect: a socket bound here (its drop removes it and its lock), or the
/// launcher's, adopted.
enum Listener {
    Bound(ListeningSocket),
    Adopted(std::os::unix::net::UnixListener),
}

impl Listener {
    fn accept(&self) -> std::io::Result<Option<UnixStream>> {
        match self {
            Listener::Bound(l) => l.accept(),
            Listener::Adopted(l) => match l.accept() {
                Ok((stream, _)) => Ok(Some(stream)),
                Err(e) if e.kind() == std::io::ErrorKind::WouldBlock => Ok(None),
                Err(e) => Err(e),
            },
        }
    }
}

impl std::os::fd::AsFd for Listener {
    fn as_fd(&self) -> std::os::fd::BorrowedFd<'_> {
        match self {
            Listener::Bound(l) => l.as_fd(),
            Listener::Adopted(l) => l.as_fd(),
        }
    }
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

    /// Asks for a window of `size` (None: the client picks): logical units for xdg, pixels for
    /// X11, which has no "client picks" and keeps its size. `bounds` is the pane, xdg only.
    fn configure(&self, size: Option<(i32, i32)>, bounds: (i32, i32), activated: bool) {
        match self {
            Shell::Xdg(t) => {
                t.with_pending_state(|s| {
                    s.size = size.map(Into::into);
                    s.bounds = Some(bounds.into());
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
                if let Some(size) = size {
                    let loc = w.geometry().loc;
                    let _ = w.configure(Rectangle::new(loc, size.into()));
                }
            }
        }
    }

    /// The window's size limits in logical px (xdg) or px (X11); 0 on an axis is unset.
    fn size_limits(&self) -> ((i32, i32), (i32, i32)) {
        match self {
            Shell::Xdg(t) => with_states(t.wl_surface(), |states| {
                let mut guard = states.cached_state.get::<SurfaceCachedState>();
                let c = guard.current();
                ((c.min_size.w, c.min_size.h), (c.max_size.w, c.max_size.h))
            }),
            Shell::X11(w) => {
                let h = w.size_hints();
                let get = |f: fn(&smithay::reexports::x11rb::properties::WmSizeHints) -> Option<(i32, i32)>| {
                    h.as_ref().and_then(f).unwrap_or((0, 0))
                };
                (get(|h| h.min_size), get(|h| h.max_size))
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
    /// Decided once, before the first configure (xdg) or at the map request (X11).
    class: WindowClass,
    /// The launcher shows it full screen (its SetFullscreen).
    fullscreen: bool,
    /// It asked for full screen before it was announced; the launcher hears it at announce.
    asked_fullscreen: bool,
    /// The scale the launcher shows this toplevel at: its app's own, else the screen's density.
    scale: f64,
    /// The output's integer scale, which a client that reads only `wl_output` draws at.
    output_integer: i32,
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
    /// The kind of the last buffer copied, so a change is logged once.
    last_kind: Option<Kind>,
    paced: Paced<WlBuffer, WlCallback>,
    /// The root buffer's logical size and density, for composing a view of many surfaces.
    root_draw: DrawState,
    /// Every other surface of the view that holds a buffer. Empty means the single-copy path.
    parts: Vec<Part>,
    /// The surfaces the last composed view had, so a change is logged once.
    layer_count: usize,
    /// A tap outside a grabbing popup dismissed it: the rest of that touch sequence, or that
    /// button's release, goes nowhere.
    swallow_touch: bool,
    swallow_button: bool,
    /// The pointer's last position in the root surface's logical coordinates.
    pointer_at: (f64, f64),
    stats: CopyStats,
    last_log: Instant,
}

impl Toplevel {
    /// The class decided before the first configure; the X11 resize path reads it.
    pub fn class(&self) -> WindowClass {
        self.class
    }

    /// The size the client last gave itself (xdg window geometry, logical; X11 geometry, px),
    /// or None while it has none. A non-Main window keeps it when the pane changes.
    fn own_size(&self) -> Option<(i32, i32)> {
        match &self.shell {
            Shell::Xdg(_) => self.geometry.map(|g| (g.2, g.3)),
            Shell::X11(w) => {
                let s = w.geometry().size;
                (s.w > 0 && s.h > 0).then_some((s.w, s.h))
            }
        }
    }

    fn is_shown(&self) -> bool {
        self.visible && self.window.is_some()
    }

    /// The buffer and placement of `surface` when it is part of this view and holds one.
    fn draw_of(&self, surface: &WlSurface) -> Option<(WlBuffer, DrawState)> {
        if self.shell.is(surface) {
            return self.paced.current().map(|b| (b.clone(), self.root_draw));
        }
        self.parts.iter().find(|p| p.surface == *surface).map(|p| (p.buffer.clone(), p.draw))
    }

    fn holds(&self, buffer: &WlBuffer) -> bool {
        self.paced.current() == Some(buffer) || self.parts.iter().any(|p| p.buffer == *buffer)
    }
}

/// What places a surface's buffer in its view: its logical size and how buffer pixels map to it.
#[derive(Clone, Copy, Default)]
struct DrawState {
    size: (f64, f64),
    buffer_scale: i32,
    viewport_src: Option<RectF>,
    viewport_dst: Option<(i32, i32)>,
}

impl DrawState {
    fn of(buffer: &WlBuffer, buffer_scale: i32, viewport: (Option<RectF>, Option<(i32, i32)>)) -> DrawState {
        let (w, h) = source::buffer_size(buffer).unwrap_or((0, 0));
        let map = surface_map(w, h, buffer_scale, viewport.0, viewport.1);
        DrawState { size: (map.width, map.height), buffer_scale, viewport_src: viewport.0, viewport_dst: viewport.1 }
    }
}

/// A surface of a view other than the toplevel's own (a subsurface, a popup or one of its
/// subsurfaces, an override-redirect X window), holding its buffer until the client replaces it.
struct Part {
    surface: WlSurface,
    buffer: WlBuffer,
    draw: DrawState,
}

/// One mapped surface of a view, back to front, with its origin in the root surface's logical
/// coordinates.
struct Layer {
    surface: WlSurface,
    buffer: WlBuffer,
    origin: (f64, f64),
    draw: DrawState,
    /// Drawn over the window rather than part of it (popups, override-redirect X windows), so
    /// not in the content's bounding box.
    overlay: bool,
    popup: bool,
    /// Its input region in `State::hit_rects`, when asked for.
    region: Option<(usize, usize)>,
}

struct Popup {
    surface: PopupSurface,
    /// The client asked for a grab: a tap outside the popups dismisses them.
    grab: bool,
    /// popup_done was sent; it is not shown again, and goes when the client destroys it.
    dismissed: bool,
}

/// An override-redirect X window (menu, tooltip) shown in the view of the X toplevel it belongs to.
pub struct OrWindow {
    pub window: X11Surface,
    /// The owner's window id.
    pub owner: i64,
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
    pub dmabuf_state: DmabufState,
    #[allow(dead_code)]
    dmabuf_global: DmabufGlobal,
    /// Read-only mappings of the dmabufs clients committed, per wl_buffer.
    maps: DmabufMaps,
    pub seat_state: SeatState<State>,
    pub data_device_state: DataDeviceState,
    #[allow(dead_code)]
    pub decoration_state: XdgDecorationState,
    pub xwayland_shell_state: XWaylandShellState,
    pub seat: Seat<State>,
    pub output: Output,
    output_size: (i32, i32),
    output_scale: f64,
    /// The launcher's page slot (px) and the scale a new window there gets; None until told.
    page: Option<((i32, i32), f64)>,
    /// The scale X apps are told (XSETTINGS, `Xft.dpi`): the start scale, the screen's density.
    /// One X server serves every X app, so no window's own scale moves it.
    toolkit_scale: f64,
    pub toplevels: Vec<Toplevel>,
    /// The toplevel with keyboard focus, as the launcher set it.
    pub focused: Option<i64>,
    /// xdg popups in creation order, which is their stacking order within a toplevel.
    popups: Vec<Popup>,
    pub or_windows: Vec<OrWindow>,
    /// Reused by every composed copy and hit test.
    scene: Vec<Layer>,
    hit_layers: Vec<HitLayer>,
    hit_rects: Vec<(bool, Rect)>,
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
    pub events: Box<dyn HostEvents>,
    pub xwayland: Option<super::xwayland::XwaylandState>,
    pub clip: super::clipboard::ClipState,
    /// Removes the listening socket and the display from the loop at stop (see `Teardown`).
    teardown: crate::teardown::Teardown,
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
    mut config: StartConfig,
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
    // zwp_linux_dmabuf_v1 version 3: format + modifier events, which Mesa's Vulkan WSI needs.
    // Linear only, single plane, shown by copying the mapped pixels.
    let mut dmabuf_state = DmabufState::new();
    let dmabuf_global = dmabuf_state.create_global::<State>(&dh, source::advertised_formats());
    let mut seat_state = SeatState::new();
    let data_device_state = DataDeviceState::new::<State>(&dh);
    let decoration_state = XdgDecorationState::new::<State>(&dh);
    // Only Xwayland's client sees this global.
    let xwayland_shell_state = XWaylandShellState::new::<State>(&dh);

    let mut seat = seat_state.new_wl_seat(&dh, "seat0");
    seat.add_pointer();
    seat.add_touch();
    // XKB_CONFIG_ROOT was set by nativeStart before this thread existed.
    // Without usable data libxkbcommon's context is NULL and add_keyboard crashes (see xkbroot),
    // so the seat then runs with pointer and touch only; key events find no keyboard and drop.
    let xkb_root = std::env::var_os("XKB_CONFIG_ROOT").map(std::path::PathBuf::from);
    match xkb_root.as_deref().filter(|root| crate::xkbroot::usable(root)) {
        Some(_) => match seat.add_keyboard(XkbConfig::default(), 400, 25) {
            Ok(_) => linfo!("keyboard on the seat"),
            Err(e) => lwarn!("no keyboard on the seat: {e:?}"),
        },
        None => lwarn!("no keyboard on the seat: keymap data missing at {}", xkb_root.as_deref().map_or("(XKB_CONFIG_ROOT unset)".into(), |r| r.display().to_string())),
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
    let listener = match config.listen_fd.take() {
        Some(fd) => {
            let l = std::os::unix::net::UnixListener::from(fd);
            l.set_nonblocking(true)?;
            linfo!("adopted the launcher's socket at {}", config.socket_path.display());
            Listener::Adopted(l)
        }
        None => Listener::Bound(ListeningSocket::bind_absolute(config.socket_path.clone())?),
    };
    linfo!("listening on {} (scale {}, frame {:?})", config.socket_path.display(), config.scale, config.frame_period);
    let listener_token = loop_handle.insert_source(Generic::new(listener, Interest::READ, Mode::Level), |_, listener, state| {
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

    let display_token = loop_handle.insert_source(Generic::new(display, Interest::READ, Mode::Level), |_, display, state| {
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

    // Texts the clipboard reader threads read come back to the compositor thread here.
    let (clip_tx, clip_rx) = channel::channel::<String>();
    loop_handle.insert_source(clip_rx, |event, _, state| {
        if let channel::Event::Msg(text) = event {
            state.linux_text(text);
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
        dmabuf_state,
        dmabuf_global,
        maps: DmabufMaps::default(),
        seat_state,
        data_device_state,
        decoration_state,
        xwayland_shell_state,
        seat,
        output,
        output_size,
        page: None,
        output_scale: config.scale,
        toolkit_scale: config.scale,
        toplevels: Vec::new(),
        focused: None,
        popups: Vec::new(),
        or_windows: Vec::new(),
        scene: Vec::new(),
        hit_layers: Vec::new(),
        hit_rects: Vec::new(),
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
        clip: super::clipboard::ClipState::new(clip_tx),
        teardown: Default::default(),
    };
    // Last in, first out: the listening socket goes (and its files with it), then the display.
    let h = state.loop_handle.clone();
    state.teardown.add(move || h.remove(display_token));
    let h = state.loop_handle.clone();
    state.teardown.add(move || h.remove(listener_token));
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
    /// The scale X apps under Xwayland are told; it never follows a window.
    pub fn toolkit_scale(&self) -> f64 {
        self.toolkit_scale
    }

    /// The one output's scale, which clients that know only `wl_output` draw at. Each toplevel
    /// learns its integer, so its post limit leaves room for such a client's buffer.
    fn set_output_scale(&mut self, scale: f64) {
        if scale == self.output_scale {
            return;
        }
        self.output_scale = scale;
        self.output.change_current_state(None, None, Some(output_scale(scale)), None);
        let integer = integer_scale(scale);
        for t in &mut self.toplevels {
            t.output_integer = integer;
        }
    }

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
            releases.extend(t.parts.drain(..).map(|p| p.buffer));
        }
        releases.append(&mut self.loose_releases);
        for b in releases {
            if b.is_alive() {
                b.release();
            }
        }
        self.maps.clear();
        self.teardown.run();
    }

    /// Debug: writes what toplevel `id` shows (its held buffer, cropped and cut like a copy,
    /// before the Surface) to `path` as a PNG, and logs the path and size.
    fn dump(&mut self, id: i64, path: &str) {
        let Some(i) = self.toplevel_index(id) else {
            lwarn!("dump: no window {id}");
            return;
        };
        let Some(buffer) = self.toplevels[i].paced.current().cloned() else {
            lwarn!("dump: window {id} has no frame");
            return;
        };
        if !self.toplevels[i].parts.is_empty() {
            self.dump_tree(i, id, path);
            return;
        }
        let layout = Layout::of(&self.toplevels[i], self.output_size);
        let result = source::with_pixels(&buffer, &mut self.maps, |ptr, len, desc| {
            let (shown, _, _) = shown_rect(layout, desc)?;
            let (start, bytes) = content_span(usize::try_from(desc.offset).ok()?, desc.stride as usize, shown, len)?;
            // SAFETY: the mapping lives for this closure and content_span checked the range.
            let src = unsafe { std::slice::from_raw_parts(ptr.add(start), bytes) };
            let (w, h) = (shown.2 as usize, shown.3 as usize);
            let mut rgba = vec![0u8; w * h * 4];
            pixels::convert(src, desc.stride as usize, &mut rgba, w * 4, w, h, desc.format);
            Some((png::encode_rgba(w as u32, h as u32, &rgba)?, shown, desc.kind))
        });
        match result {
            Ok(Some((png, shown, kind))) => match std::fs::write(path, &png) {
                Ok(()) => linfo!("dump window={id} kind={} size={}x{} bytes={} path={path}", kind.name(), shown.2, shown.3, png.len()),
                Err(e) => lwarn!("dump window={id}: cannot write {path}: {e}"),
            },
            Ok(None) => lwarn!("dump window={id}: its buffer cannot be read"),
            Err(e) => lwarn!("dump window={id}: {e}"),
        }
    }

    /// `dump` for a view of many surfaces: the composed canvas, exactly as a copy draws it.
    fn dump_tree(&mut self, i: usize, id: i64, path: &str) {
        self.build_scene(i, false);
        let Some((canvas, origin, k, _)) = self.canvas(i) else {
            lwarn!("dump window={id}: nothing to show");
            return;
        };
        let (w, h) = (canvas.0 as usize, canvas.1 as usize);
        let mut rgba = vec![0u8; w * h * 4];
        let scene = std::mem::take(&mut self.scene);
        compose_into(&scene, &mut self.maps, &mut rgba, w * 4, canvas, origin, k);
        let (layers, over) = (scene.len(), scene.iter().filter(|l| l.overlay).count());
        self.scene = scene;
        match png::encode_rgba(w as u32, h as u32, &rgba) {
            Some(png) => match std::fs::write(path, &png) {
                Ok(()) => linfo!("dump window={id} kind=tree surfaces={layers} over={over} size={w}x{h} bytes={} path={path}", png.len()),
                Err(e) => lwarn!("dump window={id}: cannot write {path}: {e}"),
            },
            None => lwarn!("dump window={id}: cannot encode {w}x{h}"),
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
            Command::PageSize { width, height, scale } => {
                self.page = (width > 0 && height > 0).then_some(((width, height), scale));
            }
            Command::SetVisible { id, visible } => self.set_visible(id, visible),
            Command::SetFocus { id } => self.set_focus(id),
            Command::SetFullscreen { id, on } => self.set_fullscreen(id, on),
            Command::Touch { id, action, action_index, time, points } => self.touch(id, action, action_index, time, &points),
            Command::Pointer { id, action, button, x, y, time } => self.pointer(id, action, button, x, y, time),
            Command::Key { id, android_keycode, down, time } => self.key(id, android_keycode, down, time),
            Command::Close { id } => self.close(id),
            Command::SetClipboardText { text } => self.set_android_text(text),
            Command::Dump { id, path } => self.dump(id, &path),
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
        if !visible {
            self.dismiss_popups(i);
        } else if !self.toplevels[i].shell.is_x11() {
            // The shown window's scale is the output's, for its clients that read only that.
            self.set_output_scale(self.toplevels[i].scale);
        }
        // Shown again: what was committed while hidden is still dirty and is copied now.
        self.schedule();
    }

    /// Sets or clears the full-screen state the launcher decided; the size comes with the view's
    /// next configure.
    fn set_fullscreen(&mut self, id: i64, on: bool) {
        let Some(i) = self.toplevel_index(id) else { return };
        let t = &mut self.toplevels[i];
        if t.fullscreen == on {
            return;
        }
        t.fullscreen = on;
        match &t.shell {
            Shell::Xdg(x) => {
                x.with_pending_state(|s| {
                    if on {
                        s.states.set(xdg_toplevel::State::Fullscreen);
                    } else {
                        s.states.unset(xdg_toplevel::State::Fullscreen);
                    }
                });
                if x.is_initial_configure_sent() {
                    x.send_pending_configure();
                }
            }
            Shell::X11(w) => {
                let _ = w.set_fullscreen(on);
            }
        }
    }

    /// A client asked to be full screen or to leave it: the launcher decides (`set_fullscreen`).
    /// A window not yet announced is heard at its announce.
    pub fn request_fullscreen(&mut self, i: usize, on: bool) {
        let t = &mut self.toplevels[i];
        if !t.announced {
            t.asked_fullscreen = on;
            return;
        }
        let id = t.id;
        self.events.fullscreen_request(id, on);
    }

    /// A client asked to be moved (a drag on its own title bar): told to the launcher, which
    /// lifts the window under the finger.
    pub fn request_move(&mut self, i: usize) {
        let t = &self.toplevels[i];
        if t.announced {
            let id = t.id;
            self.events.move_request(id);
        }
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
        // Only a Main window is told the pane; the others keep their own size, cut to the pane.
        let own = if t.class == WindowClass::Main { Some(size) } else { t.own_size().map(|o| (o.0.min(size.0), o.1.min(size.1))) };
        t.shell.configure(own, size, activated);
        if scale_changed {
            self.apply_scale(i);
        }
        // Until the client answers with a buffer of the new size, the view shows the old
        // contents stretched rather than whatever the resized texture holds.
        self.toplevels[i].paced.repaint();
        self.schedule();
    }

    /// Tells a Wayland toplevel's surfaces their preferred scale (fractional, and integer through
    /// `wl_surface.preferred_buffer_scale`), and the output its scale: one output, so the window
    /// configured last wins it. X windows keep the X server's scale (`toolkit_scale`).
    fn apply_scale(&mut self, i: usize) {
        if self.toplevels[i].shell.is_x11() {
            return;
        }
        let scale = self.toplevels[i].scale;
        self.set_output_scale(scale);
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
            // A grab ends when its window loses the keyboard.
            if self.has_grab(i) {
                self.dismiss_popups(i);
            }
        }
        let target = match new.and_then(|id| self.toplevel_index(id)) {
            Some(i) => {
                self.toplevels[i].shell.set_activated(true);
                Some(self.keyboard_target(i))
            }
            None => None,
        };
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
        if action == TOUCH_CANCEL {
            self.toplevels[i].swallow_touch = false;
            touch.cancel(self);
            return;
        }
        let count = points.count.min(MAX_TOUCH_POINTS);
        if action == TOUCH_DOWN {
            // The first finger: outside a grabbing popup it only dismisses the popups.
            if let Some(&(_, x, y)) = usize::try_from(action_index).ok().filter(|&k| k < count).and_then(|k| points.points.get(k)) {
                let at = Self::surface_point(&self.toplevels[i], x, y);
                self.toplevels[i].swallow_touch = self.press_dismisses(i, at);
            }
        }
        if self.toplevels[i].swallow_touch {
            if action == TOUCH_UP {
                self.toplevels[i].swallow_touch = false;
            }
            return;
        }
        // One MotionEvent is one wl_touch frame: the pointer at action_index goes down or up,
        // every other pointer moves. A point stays with the surface it went down on (smithay
        // keeps each slot's focus from its down).
        for (k, &(pointer_id, x, y)) in points.points[..count].iter().enumerate() {
            let (sx, sy) = Self::surface_point(&self.toplevels[i], x, y);
            let slot = Some(pointer_id.max(0) as u32).into();
            let acting = k as i32 == action_index;
            match action {
                TOUCH_DOWN | TOUCH_POINTER_DOWN if acting => {
                    let Some((surface, origin)) = self.input_target(i, (sx, sy)) else { continue };
                    let serial = SERIAL_COUNTER.next_serial();
                    touch.down(self, Some((surface, origin.into())), &DownEvent { slot, location: (sx, sy).into(), serial, time });
                }
                TOUCH_UP | TOUCH_POINTER_UP if acting => {
                    touch.up(self, &UpEvent { slot, serial: SERIAL_COUNTER.next_serial(), time });
                }
                TOUCH_MOVE | TOUCH_POINTER_DOWN | TOUCH_POINTER_UP => {
                    touch.motion(self, None, &TouchMotion { slot, location: (sx, sy).into(), time });
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
        let serial = SERIAL_COUNTER.next_serial();
        match action {
            POINTER_MOTION => {
                let at = Self::surface_point(&self.toplevels[i], x, y);
                self.toplevels[i].pointer_at = at;
                let Some((surface, origin)) = self.input_target(i, at) else { return };
                pointer.motion(self, Some((surface, origin.into())), &PointerMotion { location: at.into(), serial, time });
            }
            POINTER_BUTTON_DOWN | POINTER_BUTTON_UP => {
                // Outside a grabbing popup a press only dismisses the popups, and its release
                // goes nowhere either.
                if action == POINTER_BUTTON_DOWN {
                    let at = self.toplevels[i].pointer_at;
                    if self.press_dismisses(i, at) {
                        self.toplevels[i].swallow_button = true;
                        return;
                    }
                } else if std::mem::take(&mut self.toplevels[i].swallow_button) {
                    return;
                }
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
                // The hash is taken before the copy: a write landing in between shows as a change
                // on the next read, never as a missed one.
                let hash = self.settle_hash(i, &buffer);
                // A dmabuf still holding what it last posted has not been written yet: it is not
                // posted, the window keeps the previous frame and the settle posts the change.
                // Its release and the frame callbacks below still go out on this tick.
                if self.toplevels[i].paced.first_read(hash) {
                    self.copy(i, &buffer);
                    copied = true;
                }
            } else if let Some(buffer) = self.toplevels[i].paced.settle_due(shown, now) {
                // The dmabuf copy may have run before the client's GPU finished (no fence on
                // KGSL): copy again only when its pixels changed since.
                let hash = self.settle_hash(i, &buffer);
                if self.toplevels[i].paced.settled(hash) {
                    self.copy(i, &buffer);
                    copied = true;
                }
            }
        }
        releases.append(&mut self.loose_releases);
        frames.append(&mut self.loose_frames);
        // A client answered now usually commits again within the period: keep the timer armed
        // for one more tick, so a running animation re-arms instead of inserting a timer per frame.
        let worked = copied || !releases.is_empty() || !frames.is_empty();
        for b in releases.drain(..) {
            // A buffer another toplevel still shows is released when that one lets it go.
            if b.is_alive() && !self.toplevels.iter().any(|t| t.holds(&b)) {
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

    /// The content hash of a dmabuf toplevel's held buffer, for the settle reads (see
    /// `Paced::copied`). None for shm, which is complete at commit, and for a window with
    /// subsurfaces or popups, whose copy is a tree the root alone does not describe.
    fn settle_hash(&mut self, i: usize, buffer: &WlBuffer) -> Option<u64> {
        if !buffer.is_alive() || !self.toplevels[i].parts.is_empty() {
            return None;
        }
        let output = self.output_size;
        content_hash(&self.toplevels[i], buffer, output, &mut self.maps)
    }

    /// Copies `buffer` into toplevel `i`'s window and reports a new shown size. A window whose
    /// post fails (its consumer is gone) is dropped, as if detached, so nothing is drawn into it
    /// again until the launcher hands a new Surface.
    fn copy(&mut self, i: usize, buffer: &WlBuffer) {
        if !buffer.is_alive() {
            return;
        }
        let posted = if self.toplevels[i].parts.is_empty() {
            // One surface: the client's pixels straight into the window, as always.
            self.toplevels[i].layer_count = 1;
            let output = self.output_size;
            post_buffer(&mut self.toplevels[i], buffer, output, &mut self.maps)
        } else {
            self.post_tree(i)
        };
        let t = &mut self.toplevels[i];
        if let Err(rc) = posted {
            lwarn!("toplevel {}: post failed ({rc}); the Surface is dropped until a new one comes", t.id);
            t.window = None;
        }
        let size = (t.shown.2, t.shown.3);
        if size != t.reported_size && t.announced {
            t.reported_size = size;
            let (title, app_id) = t.shell.names();
            let id = t.id;
            self.events.window_changed(id, &title, &app_id, size.0, size.1);
        }
    }

    /// The toplevel's own surface committed.
    fn commit_root(
        &mut self,
        i: usize,
        assignment: Option<BufferAssignment>,
        scale: i32,
        viewport: (Option<RectF>, Option<(i32, i32)>),
        frames: &mut Vec<WlCallback>,
    ) {
        // Window geometry, scale and viewport are double-buffered with the buffer.
        self.toplevels[i].geometry = self.toplevels[i].shell.geometry();
        match assignment {
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
                t.buffer_scale = scale;
                t.viewport_src = viewport.0;
                t.viewport_dst = viewport.1;
                t.root_draw = DrawState::of(&buffer, scale, viewport);
                t.paced.commit_buffer(buffer);
                if !t.announced {
                    self.announce(i);
                }
            }
            None => {
                let t = &mut self.toplevels[i];
                if !t.parts.is_empty() {
                    // Subsurface positions and order apply with this commit.
                    t.paced.repaint();
                }
                if !t.announced && t.shell.is_initial_configure_sent() {
                    // The initial (bufferless) commit: title and app id are set by now.
                    self.announce(i);
                }
            }
        }
        self.toplevels[i].paced.add_frames(frames);
    }

    /// Another surface of toplevel `i`'s view committed: it holds its new buffer, the replaced
    /// one waits for the tick, and the view is composed again on the next tick.
    fn commit_part(
        &mut self,
        i: usize,
        surface: &WlSurface,
        assignment: Option<BufferAssignment>,
        scale: i32,
        viewport: (Option<RectF>, Option<(i32, i32)>),
        frames: &mut Vec<WlCallback>,
    ) {
        let t = &mut self.toplevels[i];
        let at = t.parts.iter().position(|p| p.surface == *surface);
        match assignment {
            Some(BufferAssignment::NewBuffer(buffer)) => {
                let draw = DrawState::of(&buffer, scale, viewport);
                match at {
                    Some(k) => {
                        let p = &mut t.parts[k];
                        p.draw = draw;
                        if p.buffer != buffer {
                            let old = std::mem::replace(&mut p.buffer, buffer);
                            t.paced.release_later(old);
                        }
                    }
                    None => {
                        self.output.enter(surface);
                        t.parts.push(Part { surface: surface.clone(), buffer, draw });
                    }
                }
            }
            Some(BufferAssignment::Removed) => {
                if let Some(k) = at {
                    let p = t.parts.swap_remove(k);
                    t.paced.release_later(p.buffer);
                }
            }
            None => {
                if let Some(k) = at {
                    let p = &mut t.parts[k];
                    p.draw = DrawState::of(&p.buffer, scale, viewport);
                }
            }
        }
        t.paced.repaint();
        t.paced.add_frames(frames);
    }

    /// The toplevel whose view shows `surface`: its own tree, a popup's (through the popup's
    /// parents) or an override-redirect X window's. A dismissed popup is in no view.
    fn owner_of(&self, surface: &WlSurface) -> Option<usize> {
        let mut root = root_of(surface)?;
        for _ in 0..=self.popups.len() {
            if let Some(i) = self.toplevel_index_for(&root) {
                return Some(i);
            }
            if let Some(p) = self.popups.iter().find(|p| p.surface.wl_surface() == &root) {
                if p.dismissed || !p.surface.alive() {
                    return None;
                }
                root = root_of(&p.surface.get_parent_surface()?)?;
                continue;
            }
            let owner = self.or_windows.iter().find(|o| o.window.wl_surface().as_ref() == Some(&root))?.owner;
            return self.toplevel_index(owner);
        }
        None
    }

    /// Where `surface` (toplevel `i`'s own, or one of its popups') has its origin, in the
    /// root surface's logical coordinates.
    fn view_origin(&self, i: usize, surface: &WlSurface, depth: usize) -> Option<(f64, f64)> {
        if !surface.is_alive() {
            return None;
        }
        if self.toplevels[i].shell.is(surface) {
            return Some((0.0, 0.0));
        }
        if depth > self.popups.len() {
            return None;
        }
        let p = self.popups.iter().find(|p| p.surface.wl_surface() == surface && p.surface.alive())?;
        let parent = p.surface.get_parent_surface().filter(|s| s.is_alive())?;
        let parent_origin = self.view_origin(i, &parent, depth + 1)?;
        Some(compose::popup_origin(parent_origin, geometry_origin(&parent), popup_position(&p.surface), geometry_origin(surface)))
    }

    /// Toplevel `i`'s window in its root surface's logical coordinates: what the view shows,
    /// else its window geometry, else the view's logical size.
    fn window_area(&self, i: usize) -> RectF {
        let t = &self.toplevels[i];
        if t.content.2 > 0.0 && t.content.3 > 0.0 {
            return t.content;
        }
        if let Some((x, y, w, h)) = t.geometry.filter(|g| g.2 > 0 && g.3 > 0) {
            return (x as f64, y as f64, w as f64, h as f64);
        }
        let (w, h) = t.view_size.map(|v| logical_size(v, t.scale)).unwrap_or(self.output_size);
        (0.0, 0.0, w as f64, h as f64)
    }

    /// The popup's geometry from `positioner`, kept inside its window (flip, slide and resize as
    /// the positioner allows; smithay computes it).
    fn constrained(&self, popup: &PopupSurface, positioner: PositionerState) -> Rectangle<i32, Logical> {
        let fallback = positioner.get_geometry();
        if !popup.alive() {
            return fallback;
        }
        let Some(parent) = popup.get_parent_surface().filter(|s| s.is_alive()) else { return fallback };
        let Some(i) = self.owner_of(&parent) else { return fallback };
        let Some(parent_origin) = self.view_origin(i, &parent, 0) else { return fallback };
        let (gx, gy) = geometry_origin(&parent);
        let area = self.window_area(i);
        let bound = |v: f64| v.round().clamp(-compose::MAX_COORD, compose::MAX_COORD) as i32;
        let target = Rectangle::new(
            (bound(area.0 - parent_origin.0 - gx as f64), bound(area.1 - parent_origin.1 - gy as f64)).into(),
            (bound(area.2).max(1), bound(area.3).max(1)).into(),
        );
        positioner.get_unconstrained_geometry(target)
    }

    /// Sends popup_done to every popup of toplevel `i`, topmost first, and gives the keyboard back.
    fn dismiss_popups(&mut self, i: usize) {
        let mut any = false;
        for k in (0..self.popups.len()).rev() {
            if self.popups[k].dismissed || !self.popups[k].surface.alive() {
                continue;
            }
            if self.owner_of(self.popups[k].surface.wl_surface()) == Some(i) {
                self.popups[k].surface.send_popup_done();
                self.popups[k].dismissed = true;
                any = true;
            }
        }
        if any {
            self.toplevels[i].paced.repaint();
            self.refocus();
            self.schedule();
        }
    }

    fn has_grab(&self, i: usize) -> bool {
        self.popups.iter().any(|p| p.grab && !p.dismissed && p.surface.alive() && self.owner_of(p.surface.wl_surface()) == Some(i))
    }

    /// The keyboard target of toplevel `i`: its topmost grabbing popup, else itself.
    fn keyboard_target(&self, i: usize) -> KeyboardFocus {
        for p in self.popups.iter().rev() {
            if p.grab && !p.dismissed && p.surface.alive() && self.owner_of(p.surface.wl_surface()) == Some(i) {
                return KeyboardFocus::Wl(p.surface.wl_surface().clone());
            }
        }
        self.toplevels[i].shell.keyboard_focus()
    }

    /// Moves the keyboard to the focused toplevel's target after a popup grab began or ended.
    fn refocus(&mut self) {
        let Some(i) = self.focused.and_then(|id| self.toplevel_index(id)) else { return };
        let target = self.keyboard_target(i);
        let Some(keyboard) = self.seat.get_keyboard() else { return };
        if keyboard.current_focus().as_ref() != Some(&target) {
            keyboard.set_focus(self, Some(target), SERIAL_COUNTER.next_serial());
        }
    }

    /// A press at `at` (root logical) in toplevel `i`: with a grabbing popup open and the press
    /// on none of its popups, the popups are dismissed and the press is consumed (true).
    fn press_dismisses(&mut self, i: usize, at: (f64, f64)) -> bool {
        if !self.has_grab(i) {
            return false;
        }
        if self.hit(i, at).is_some_and(|h| h.2) {
            return false;
        }
        self.dismiss_popups(i);
        true
    }

    /// The surface of toplevel `i`'s view under `at`, its origin, and whether it is a popup's.
    fn hit(&mut self, i: usize, at: (f64, f64)) -> Option<(WlSurface, (f64, f64), bool)> {
        self.build_scene(i, true);
        self.hit_layers.clear();
        for l in &self.scene {
            self.hit_layers.push(HitLayer { rect: (l.origin.0, l.origin.1, l.draw.size.0, l.draw.size.1), region: l.region });
        }
        let (k, _) = compose::hit_test(&self.hit_layers, &self.hit_rects, at.0, at.1)?;
        let l = self.scene.get(k)?;
        Some((l.surface.clone(), l.origin, l.popup))
    }

    /// Where input at `at` goes: the surface under it and that surface's origin; the root
    /// surface when it is on none (a margin), as for a lone surface.
    fn input_target(&mut self, i: usize, at: (f64, f64)) -> Option<(WlSurface, (f64, f64))> {
        if !self.toplevels[i].parts.is_empty() {
            if let Some((surface, origin, _)) = self.hit(i, at) {
                return Some((surface, origin));
            }
        }
        self.toplevels[i].shell.wl_surface().map(|s| (s, (0.0, 0.0)))
    }

    /// Fills `self.scene` with toplevel `i`'s view, back to front: its tree, the
    /// override-redirect X windows it owns, its popups. With `regions`, input regions go to
    /// `self.hit_rects`.
    fn build_scene(&mut self, i: usize, regions: bool) {
        let mut scene = std::mem::take(&mut self.scene);
        let mut rects = std::mem::take(&mut self.hit_rects);
        scene.clear();
        rects.clear();
        let t = &self.toplevels[i];
        if let Some(root) = t.shell.wl_surface().filter(|s| s.is_alive()) {
            push_tree(t, &root, (0.0, 0.0), false, false, &mut scene, regions.then_some(&mut rects));
        }
        if let Shell::X11(w) = &t.shell {
            let base = w.geometry().loc;
            for o in self.or_windows.iter().filter(|o| o.owner == t.id && t.id > 0) {
                let Some(surface) = o.window.wl_surface().filter(|s| s.is_alive()) else { continue };
                let loc = o.window.geometry().loc;
                let origin = (loc.x as f64 - base.x as f64, loc.y as f64 - base.y as f64);
                push_tree(t, &surface, origin, true, false, &mut scene, regions.then_some(&mut rects));
            }
        }
        for p in &self.popups {
            if p.dismissed || !p.surface.alive() {
                continue;
            }
            let surface = p.surface.wl_surface();
            if self.owner_of(surface) != Some(i) {
                continue;
            }
            let Some(origin) = self.view_origin(i, surface, 0) else { continue };
            push_tree(t, surface, origin, true, true, &mut scene, regions.then_some(&mut rects));
        }
        self.scene = scene;
        self.hit_rects = rects;
    }

    /// Lets go of parts no longer in toplevel `i`'s view (the surface is gone, left the tree,
    /// or its popup was dismissed); their buffers are released at the end of this tick.
    fn prune_parts(&mut self, i: usize) {
        let mut k = 0;
        while k < self.toplevels[i].parts.len() {
            let surface = &self.toplevels[i].parts[k].surface;
            if surface.is_alive() && self.owner_of(surface) == Some(i) {
                k += 1;
            } else {
                let p = self.toplevels[i].parts.swap_remove(k);
                self.loose_releases.push(p.buffer);
            }
        }
    }

    /// The canvas for `self.scene` of toplevel `i`: its size (cut to the post limit), the
    /// logical origin of the content, the density, and the uncut size. None when empty.
    fn canvas(&self, i: usize) -> Option<((i32, i32), (f64, f64), (f64, f64), (i32, i32))> {
        let t = &self.toplevels[i];
        let (w, h) = source::buffer_size(t.paced.current()?)?;
        let d = t.root_draw;
        let k = surface_map(w, h, d.buffer_scale, d.viewport_src, d.viewport_dst).density();
        let tree = compose::bounds(self.scene.iter().filter(|l| !l.overlay).map(|l| (l.origin.0, l.origin.1, l.draw.size.0, l.draw.size.1)))?;
        let content = compose::window_content(t.geometry, tree);
        let full = compose::canvas_size(content, k);
        let shown = clamp_to_limit((0, 0, full.0, full.1), post_limit(post_view(t), self.output_size));
        if shown.2 <= 0 || shown.3 <= 0 {
            return None;
        }
        Some(((shown.2, shown.3), (content.0, content.1), k, full))
    }

    /// Composes toplevel `i`'s view of many surfaces into its window. Err with the platform's
    /// code when the post failed.
    fn post_tree(&mut self, i: usize) -> Result<(), i32> {
        self.prune_parts(i);
        self.build_scene(i, false);
        let Some((canvas, origin, k, full)) = self.canvas(i) else { return Ok(()) };
        let output = self.output_size;
        let scene = std::mem::take(&mut self.scene);
        let maps = &mut self.maps;
        let t = &mut self.toplevels[i];
        let id = t.id;
        if scene.len() != t.layer_count {
            t.layer_count = scene.len();
            linfo!("toplevel {id}: view of {} surfaces, {} over the window", scene.len(), scene.iter().filter(|l| l.overlay).count());
        }
        if (full.0 > canvas.0 || full.1 > canvas.1) && !t.clamp_logged {
            t.clamp_logged = true;
            let limit = post_limit(post_view(t), output);
            lwarn!("toplevel {id}: view {}x{} is over the limit {}x{}; showing its top-left part", full.0, full.1, limit.0, limit.1);
        }
        t.content = (origin.0, origin.1, canvas.0 as f64 / k.0, canvas.1 as f64 / k.1);
        t.shown = ((origin.0 * k.0).round() as i32, (origin.1 * k.1).round() as i32, canvas.0, canvas.1);
        let result = match t.window.as_mut() {
            Some(window) => window
                .post_with(canvas.0, canvas.1, |dst, dst_stride, w, h| {
                    compose_into(&scene, maps, dst, dst_stride, (w as i32, h as i32), origin, k)
                })
                .map(|timing| {
                    let s = &mut t.stats;
                    s.frames += 1;
                    s.copy_us_sum += timing.copy_us;
                    s.copy_us_max = s.copy_us_max.max(timing.copy_us);
                    s.total_us_sum += timing.total_us;
                    s.last_copy_us = timing.copy_us;
                }),
            None => Ok(()),
        };
        if COPY_LOG.load(Ordering::Relaxed) && t.last_log.elapsed() >= LOG_INTERVAL && t.stats.frames > 0 {
            let s = &t.stats;
            linfo!(
                "copy toplevel={id} kind=tree surfaces={} shown={}x{} frames={} copy_us_last={} copy_us_avg={} copy_us_max={} post_us_avg={}",
                scene.len(),
                canvas.0,
                canvas.1,
                s.frames,
                s.last_copy_us,
                s.copy_us_sum / s.frames as u64,
                s.copy_us_max,
                s.total_us_sum / s.frames as u64
            );
            t.stats = CopyStats::default();
            t.last_log = Instant::now();
        }
        self.scene = scene;
        result
    }

    /// Marks the view of window `id` for a new copy (an override-redirect X window moved, came
    /// or went).
    pub fn repaint_id(&mut self, id: i64) {
        if let Some(i) = self.toplevel_index(id) {
            self.toplevels[i].paced.repaint();
            self.schedule();
        }
    }

    /// The X toplevel an override-redirect X window is shown with: the window it is transient
    /// for, else the focused X window of the same client, else the newest X window of the same
    /// client, else the focused or the newest X window.
    pub fn or_owner(&self, w: &X11Surface) -> Option<i64> {
        let x11 = |t: &&Toplevel| t.announced && t.shell.is_x11();
        if let Some(parent) = w.is_transient_for() {
            let owner = self.toplevels.iter().filter(x11).find(|t| matches!(&t.shell, Shell::X11(s) if s.window_id() == parent));
            if let Some(t) = owner {
                return Some(t.id);
            }
            if let Some(o) = self.or_windows.iter().find(|o| o.window.window_id() == parent) {
                return Some(o.owner);
            }
        }
        let pid = w.pid();
        let same_client = |t: &&Toplevel| pid.is_some() && matches!(&t.shell, Shell::X11(s) if s.pid() == pid);
        let focused = self.focused.and_then(|id| self.toplevel_index(id)).map(|i| &self.toplevels[i]).filter(x11);
        if let Some(t) = focused.filter(|t| pid.is_none() || same_client(t)) {
            return Some(t.id);
        }
        if let Some(t) = self.toplevels.iter().filter(x11).filter(same_client).last() {
            return Some(t.id);
        }
        focused.or_else(|| self.toplevels.iter().filter(x11).last()).map(|t| t.id)
    }

    pub fn announce(&mut self, i: usize) {
        let id = NEXT_ID.fetch_add(1, Ordering::Relaxed);
        let (title, app_id) = self.toplevels[i].shell.names();
        let pid = self.toplevels[i].shell.client_pid(&self.display_handle);
        let flags = if self.toplevels[i].shell.is_x11() { WINDOW_X11 } else { 0 } | self.toplevels[i].class.flags();
        let parent = self.parent_of(i);
        let t = &mut self.toplevels[i];
        t.id = id;
        t.announced = true;
        t.reported_size = (0, 0);
        if let Some(surface) = t.shell.wl_surface() {
            self.output.enter(&surface);
        }
        self.apply_scale(i);
        self.events.window_created(id, &title, &app_id, pid, flags, parent);
        if std::mem::take(&mut self.toplevels[i].asked_fullscreen) {
            self.events.fullscreen_request(id, true);
        }
    }

    /// The pane toplevel `i` is shown in, in the units its client is configured in: the view
    /// (logical for xdg, px for X11), else the page a new window gets, else the output when
    /// nothing is known yet.
    fn pane_of(&self, i: usize) -> (i32, i32) {
        let t = &self.toplevels[i];
        match (t.view_size, self.page) {
            (Some(v), _) if t.shell.is_x11() => v,
            (Some(v), _) => logical_size(v, t.scale),
            (None, Some((p, _))) if t.shell.is_x11() => p,
            (None, Some((p, scale))) => logical_size(p, scale),
            (None, None) => self.output_size,
        }
    }

    /// Classifies toplevel `i` from what its client has said so far.
    pub fn classify(&self, i: usize) -> WindowClass {
        let t = &self.toplevels[i];
        let (min, max) = t.shell.size_limits();
        let (has_parent, kind, modal) = match &t.shell {
            Shell::Xdg(s) => (s.parent().is_some(), WindowKind::Normal, false),
            Shell::X11(w) => (
                w.is_transient_for().is_some(),
                match w.window_type() {
                    None | Some(WmWindowType::Normal) => WindowKind::Normal,
                    Some(WmWindowType::Dialog) => WindowKind::Dialog,
                    Some(WmWindowType::Utility) => WindowKind::Utility,
                    Some(WmWindowType::Splash) => WindowKind::Splash,
                    Some(WmWindowType::Toolbar) => WindowKind::Toolbar,
                    Some(WmWindowType::Notification) => WindowKind::Notification,
                    Some(_) => WindowKind::Unknown,
                },
                w.is_popup(),
            ),
        };
        WindowClass::of(&WindowHints { has_parent, kind, modal, min, max, pane: self.pane_of(i) })
    }

    /// Sets toplevel `i`'s class from its hints; the X11 path calls it at the map request.
    pub fn set_class(&mut self, i: usize) {
        self.toplevels[i].class = self.classify(i);
    }

    /// The first xdg configure: classifies, then tells Main the pane (the view's, else the page
    /// a new window gets) and every class the pane as bounds; Dialog, Fixed and Transient pick
    /// their own size. A window sized from the page takes the page's scale with it.
    fn send_first_configure(&mut self, i: usize) {
        self.set_class(i);
        let pane = self.pane_of(i);
        if self.toplevels[i].view_size.is_none() {
            if let Some((_, scale)) = self.page {
                self.toplevels[i].scale = scale;
            }
        }
        let known = self.toplevels[i].view_size.is_some() || self.page.is_some();
        let t = &self.toplevels[i];
        if let Shell::Xdg(x) = &t.shell {
            let size = if t.class == WindowClass::Main && known { Some(pane) } else { None };
            x.with_pending_state(|s| {
                s.size = size.map(Into::into);
                s.bounds = Some(pane.into());
                s.capabilities.replace([
                    xdg_toplevel::WmCapabilities::Fullscreen,
                    xdg_toplevel::WmCapabilities::Minimize,
                    xdg_toplevel::WmCapabilities::WindowMenu,
                ]);
            });
        }
        t.shell.send_configure();
    }

    /// The announced window toplevel `i` is a dialog of: its xdg parent, or the X window it is
    /// transient for; 0 for a window of its own. Read once, when it is announced.
    fn parent_of(&self, i: usize) -> i64 {
        let announced = |t: &&Toplevel| t.announced;
        let found = match &self.toplevels[i].shell {
            Shell::Xdg(s) => s.parent().and_then(|p| {
                self.toplevels.iter().filter(announced).find(|t| t.shell.wl_surface().as_ref() == Some(&p))
            }),
            Shell::X11(w) => w.is_transient_for().and_then(|parent| {
                self.toplevels.iter().filter(announced).find(|t| matches!(&t.shell, Shell::X11(s) if s.window_id() == parent))
            }),
        };
        found.map(|t| t.id).unwrap_or(0)
    }

    pub fn add_toplevel(&mut self, shell: Shell) {
        self.toplevels.push(Toplevel {
            id: 0,
            shell,
            announced: false,
            window: None,
            visible: true,
            view_size: None,
            class: WindowClass::Main,
            fullscreen: false,
            asked_fullscreen: false,
            scale: self.output_scale,
            output_integer: integer_scale(self.output_scale),
            buffer_scale: 1,
            viewport_src: None,
            viewport_dst: None,
            geometry: None,
            content: (0.0, 0.0, 0.0, 0.0),
            buffer_size: (0, 0),
            shown: (0, 0, 0, 0),
            reported_size: (0, 0),
            clamp_logged: false,
            last_kind: None,
            paced: Paced::new(),
            root_draw: DrawState::default(),
            parts: Vec::new(),
            layer_count: 1,
            swallow_touch: false,
            swallow_button: false,
            pointer_at: (0.0, 0.0),
            stats: CopyStats::default(),
            last_log: Instant::now(),
        });
    }

    /// Takes the toplevel off the launcher's screen: its id ends, its window and view go. The
    /// toplevel itself may stay (an unmapped xdg_toplevel maps again with a new id).
    fn withdraw(&mut self, i: usize) {
        self.dismiss_popups(i);
        let t = &mut self.toplevels[i];
        let id = t.id;
        let was_announced = t.announced;
        t.announced = false;
        t.id = 0;
        t.window = None;
        t.visible = true;
        t.view_size = None;
        t.clamp_logged = false;
        t.fullscreen = false;
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
        self.loose_releases.extend(t.parts.drain(..).map(|p| p.buffer));
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

/// The root of `surface`'s subsurface tree; None for a gone surface.
fn root_of(surface: &WlSurface) -> Option<WlSurface> {
    let mut root = surface.clone();
    for _ in 0..1024 {
        if !root.is_alive() {
            return None;
        }
        match get_parent(&root) {
            Some(parent) => root = parent,
            None => return Some(root),
        }
    }
    None
}

/// A surface's origin in a tree walk, from its parent's: a subsurface adds its position.
fn tree_origin(states: &SurfaceData, parent: (f64, f64)) -> (f64, f64) {
    if states.role == Some("subsurface") {
        let l = states.cached_state.get::<SubsurfaceCachedState>().current().location;
        (parent.0 + l.x as f64, parent.1 + l.y as f64)
    } else {
        parent
    }
}

/// The committed xdg window geometry origin of an alive surface, (0, 0) when it has none.
fn geometry_origin(surface: &WlSurface) -> (i32, i32) {
    with_states(surface, |states| {
        states.cached_state.get::<SurfaceCachedState>().current().geometry.map(|g| (g.loc.x, g.loc.y)).unwrap_or((0, 0))
    })
}

/// A popup's committed position, relative to its parent's window geometry.
fn popup_position(popup: &PopupSurface) -> (i32, i32) {
    with_states(popup.wl_surface(), |states| {
        states
            .data_map
            .get::<XdgPopupSurfaceData>()
            .and_then(|d| d.lock().ok().map(|d| (d.current.geometry.loc.x, d.current.geometry.loc.y)))
            .unwrap_or((0, 0))
    })
}

/// Appends the mapped surfaces of the tree at `root` to `out`, back to front (smithay keeps
/// each parent's place among its children, so below and above come out in protocol order),
/// with `root` at `base`; with `rects`, their input regions too. A surface without a buffer is
/// not mapped, and neither are its subsurfaces.
fn push_tree(
    t: &Toplevel,
    root: &WlSurface,
    base: (f64, f64),
    overlay: bool,
    popup: bool,
    out: &mut Vec<Layer>,
    mut rects: Option<&mut Vec<(bool, Rect)>>,
) {
    with_surface_tree_upward(
        root,
        base,
        |s, states, parent| {
            if t.draw_of(s).is_some() {
                TraversalAction::DoChildren(tree_origin(states, *parent))
            } else {
                TraversalAction::SkipChildren
            }
        },
        |s, states, parent| {
            let Some((buffer, draw)) = t.draw_of(s) else { return };
            let region = rects.as_deref_mut().and_then(|rects| {
                let mut guard = states.cached_state.get::<SurfaceAttributes>();
                let region = guard.current().input_region.as_ref()?;
                let start = rects.len();
                rects.extend(
                    region.rects.iter().map(|(kind, r)| (matches!(kind, RectangleKind::Add), (r.loc.x, r.loc.y, r.size.w, r.size.h))),
                );
                Some((start, rects.len()))
            });
            out.push(Layer { surface: s.clone(), buffer, origin: tree_origin(states, *parent), draw, overlay, popup, region });
        },
        |_, _, _| true,
    );
}

/// Draws `scene` onto an RGBA canvas, back to front: the first surface replaces, the others
/// blend. A surface whose buffer cannot be read is left out.
fn compose_into(
    scene: &[Layer],
    maps: &mut DmabufMaps,
    dst: &mut [u8],
    dst_stride: usize,
    canvas: (i32, i32),
    origin: (f64, f64),
    k: (f64, f64),
) {
    if !compose::clear(dst, dst_stride, canvas) {
        return;
    }
    for (n, l) in scene.iter().enumerate() {
        let mode = if n == 0 { compose::Mode::Copy } else { compose::Mode::Over };
        let _ = source::with_pixels(&l.buffer, maps, |ptr, len, desc| {
            let offset = usize::try_from(desc.offset).ok()?;
            let stride = usize::try_from(desc.stride).ok()?;
            let d = l.draw;
            let map = surface_map(desc.width, desc.height, d.buffer_scale, d.viewport_src, d.viewport_dst);
            let src_rect = map.to_buffer((0.0, 0.0, map.width, map.height), desc.width, desc.height);
            let dst_rect = compose::place(l.origin, (map.width, map.height), origin, k);
            // SAFETY: the pool or dmabuf mapping is `len` readable bytes for this closure.
            let src = unsafe { std::slice::from_raw_parts(ptr, len) };
            Some(compose::draw(src, offset, stride, src_rect, desc.format, dst, dst_stride, canvas, dst_rect, mode))
        });
    }
}

/// The view a toplevel's post limit is taken from: an X window is drawn in the view's pixels;
/// a Wayland client may draw it at its integer scale or the output's (`integer_scale_view`).
fn post_view(t: &Toplevel) -> Option<(i32, i32)> {
    if t.shell.is_x11() {
        t.view_size
    } else {
        integer_scale_view(t.view_size, t.scale, t.output_integer)
    }
}

/// What decides the shown rectangle, copied out of a toplevel so the copy can borrow its window.
#[derive(Clone, Copy)]
struct Layout {
    buffer_scale: i32,
    viewport_src: Option<RectF>,
    viewport_dst: Option<(i32, i32)>,
    geometry: Option<Rect>,
    /// `post_view` of the toplevel.
    post_view: Option<(i32, i32)>,
    output: (i32, i32),
}

impl Layout {
    fn of(t: &Toplevel, output: (i32, i32)) -> Layout {
        Layout {
            buffer_scale: t.buffer_scale,
            viewport_src: t.viewport_src,
            viewport_dst: t.viewport_dst,
            geometry: t.geometry,
            post_view: post_view(t),
            output,
        }
    }
}

/// The rectangle a copy of `desc` shows, in buffer pixels: the window geometry mapped from
/// logical to buffer pixels, clipped to the buffer and cut to the size limit, so a client never
/// decides how much the launcher allocates. Also the uncut rectangle and the shown part in
/// logical coordinates. None for a buffer with an impossible layout.
fn shown_rect(l: Layout, desc: &Desc) -> Option<(Rect, Rect, RectF)> {
    let (w, h) = (desc.width, desc.height);
    if w <= 0 || h <= 0 || desc.stride < w.checked_mul(4)? || desc.offset < 0 {
        return None;
    }
    let map = surface_map(w, h, l.buffer_scale, l.viewport_src, l.viewport_dst);
    let full = map.to_buffer(map.logical_content(l.geometry), w, h);
    let shown = clamp_to_limit(full, post_limit(l.post_view, l.output));
    Some((shown, full, map.to_logical(shown)))
}

/// A hash of the part of a dmabuf `buffer` a copy shows (about 1 ms for a full frame); None for
/// shm or when it cannot be read.
fn content_hash(t: &Toplevel, buffer: &WlBuffer, output: (i32, i32), maps: &mut DmabufMaps) -> Option<u64> {
    let layout = Layout::of(t, output);
    source::with_pixels(buffer, maps, |ptr, len, desc| {
        if desc.kind != Kind::Dmabuf {
            return None;
        }
        let (shown, _, _) = shown_rect(layout, desc)?;
        let stride = desc.stride as usize;
        let (start, bytes) = content_span(usize::try_from(desc.offset).ok()?, stride, shown, len)?;
        // SAFETY: as in post_buffer: the mapping lives for this closure, the range is checked.
        let src = unsafe { std::slice::from_raw_parts(ptr.add(start), bytes) };
        let row = shown.2 as usize * 4;
        let mut h: u64 = 0xcbf2_9ce4_8422_2325;
        for y in 0..shown.3 as usize {
            for c in src[y * stride..y * stride + row].chunks_exact(8) {
                h = (h ^ u64::from_ne_bytes([c[0], c[1], c[2], c[3], c[4], c[5], c[6], c[7]])).wrapping_mul(0x0100_0000_01b3);
            }
        }
        Some(h)
    })
    .ok()
    .flatten()
}

/// Copies the shown part of `buffer` (shm or dmabuf) into the toplevel's window. Err with the
/// platform's code when the post failed; Ok also when the buffer could not be read (logged,
/// nothing posted, the window keeps its previous frame).
fn post_buffer(t: &mut Toplevel, buffer: &WlBuffer, output: (i32, i32), maps: &mut DmabufMaps) -> Result<(), i32> {
    let id = t.id;
    let layout = Layout::of(t, output);
    let Some(window) = t.window.as_mut() else { return Ok(()) };
    let stats = &mut t.stats;
    let result = source::with_pixels(buffer, maps, |ptr, len, desc| {
        let (shown, full, logical) = shown_rect(layout, desc)?;
        let (start, bytes) = content_span(usize::try_from(desc.offset).ok()?, desc.stride as usize, shown, len)?;
        // SAFETY: the pool or dmabuf mapping lives for this closure and content_span checked the
        // range against its length.
        let src = unsafe { std::slice::from_raw_parts(ptr.add(start), bytes) };
        let posted = window.post(src, desc.stride as usize, shown.2, shown.3, desc.format).map(|timing| {
            stats.frames += 1;
            stats.copy_us_sum += timing.copy_us;
            stats.copy_us_max = stats.copy_us_max.max(timing.copy_us);
            stats.total_us_sum += timing.total_us;
            stats.last_copy_us = timing.copy_us;
        });
        Some((posted, *desc, shown, full, logical))
    });
    let (posted, desc, shown, full, logical) = match result {
        Ok(Some(v)) => v,
        Ok(None) => return Ok(()),
        Err(e) => {
            lwarn!("toplevel {id}: unreadable buffer: {e}");
            return Ok(());
        }
    };
    if t.last_kind != Some(desc.kind) {
        t.last_kind = Some(desc.kind);
        linfo!(
            "toplevel {id}: kind={} format={} modifier=0x{:x} stride={} size={}x{}",
            desc.kind.name(),
            fourcc_name(desc.fourcc),
            desc.modifier,
            desc.stride,
            desc.width,
            desc.height
        );
    }
    if shown != full && !t.clamp_logged {
        t.clamp_logged = true;
        let limit = post_limit(post_view(t), output);
        lwarn!("toplevel {id}: frame {}x{} is over the limit {}x{}; showing its top-left part", full.2, full.3, limit.0, limit.1);
    }
    t.buffer_size = (desc.width, desc.height);
    t.shown = shown;
    t.content = logical;
    if COPY_LOG.load(Ordering::Relaxed) && t.last_log.elapsed() >= LOG_INTERVAL && t.stats.frames > 0 {
        let s = &t.stats;
        linfo!(
            "copy toplevel={} kind={} buffer={}x{} shown={}x{}+{}+{} bytes={} frames={} copy_us_last={} copy_us_avg={} copy_us_max={} post_us_avg={}",
            id,
            desc.kind.name(),
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

    /// smithay calls this for each surface whose state it applies, so a synchronised
    /// subsurface comes here when its parent's commit applies its cached state.
    fn commit(&mut self, surface: &WlSurface) {
        // Popups need their first configure to proceed.
        if let Some(p) = self.popups.iter().find(|p| p.surface.wl_surface() == surface) {
            if !p.surface.is_initial_configure_sent() {
                let _ = p.surface.send_configure();
            }
        }
        self.popups.retain(|p| p.surface.alive());

        let Some(root) = root_of(surface) else { return };
        let root_index = self.toplevel_index_for(&root);
        if let Some(i) = root_index {
            let t = &mut self.toplevels[i];
            if !t.shell.is_initial_configure_sent() {
                self.send_first_configure(i);
            }
        }

        let mut frames = std::mem::take(&mut self.scratch_frames);
        let (assignment, scale, viewport) = with_states(surface, |states| {
            let (assignment, scale) = {
                let mut guard = states.cached_state.get::<SurfaceAttributes>();
                let attrs = guard.current();
                frames.append(&mut attrs.frame_callbacks);
                (attrs.buffer.take(), attrs.buffer_scale)
            };
            let mut guard = states.cached_state.get::<ViewportCachedState>();
            let v = guard.current();
            let viewport = (v.src.map(|r| (r.loc.x, r.loc.y, r.size.w, r.size.h)), v.dst.map(|d| (d.w, d.h)));
            (assignment, scale, viewport)
        });

        if let Some(i) = root_index.filter(|_| root == *surface) {
            self.commit_root(i, assignment, scale, viewport, &mut frames);
        } else if let Some(i) = self.owner_of(surface) {
            self.commit_part(i, surface, assignment, scale, viewport, &mut frames);
        } else {
            // Cursors, drag icons and surfaces of no window: acknowledged on the tick.
            if let Some(BufferAssignment::NewBuffer(b)) = assignment {
                self.loose_releases.push(b);
            }
            self.loose_frames.append(&mut frames);
        }
        self.scratch_frames = frames;
        self.schedule();
    }

    fn destroyed(&mut self, surface: &WlSurface) {
        for t in &mut self.toplevels {
            if let Some(k) = t.parts.iter().position(|p| p.surface == *surface) {
                let p = t.parts.swap_remove(k);
                t.paced.release_later(p.buffer);
                t.paced.repaint();
            }
        }
        self.schedule();
    }
}

impl BufferHandler for State {
    fn buffer_destroyed(&mut self, buffer: &WlBuffer) {
        for t in &mut self.toplevels {
            t.paced.forget(buffer);
            t.parts.retain(|p| p.buffer != *buffer);
        }
        self.loose_releases.retain(|b| b != buffer);
        self.maps.forget(buffer);
    }
}

impl DmabufHandler for State {
    fn dmabuf_state(&mut self) -> &mut DmabufState {
        &mut self.dmabuf_state
    }

    /// Only what the copy can read is taken: one linear plane of an advertised format, a stride
    /// covering the width. Anything else fails with the protocol's own error (`failed` for
    /// create, a protocol error for create_immed).
    fn dmabuf_imported(&mut self, _global: &DmabufGlobal, dmabuf: Dmabuf, notifier: ImportNotifier) {
        match source::check_import(&dmabuf) {
            Ok(()) => {
                if notifier.successful::<State>().is_err() {
                    lwarn!("dmabuf import: the client went away");
                }
            }
            Err(why) => {
                if !DMABUF_REJECT_LOGGED.swap(true, Ordering::Relaxed) {
                    lwarn!("dmabuf import refused ({why}); further refusals are not logged");
                }
                notifier.failed();
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
        surface.with_pending_state(|s| s.decoration_mode = Some(DecorationMode::ServerSide));
        self.add_toplevel(Shell::Xdg(surface));
    }

    fn new_popup(&mut self, surface: PopupSurface, positioner: PositionerState) {
        let geometry = self.constrained(&surface, positioner);
        surface.with_pending_state(|s| s.geometry = geometry);
        self.popups.push(Popup { surface, grab: false, dismissed: false });
    }

    /// The popup takes the keyboard while its toplevel has it; a tap outside the popups
    /// dismisses them (`press_dismisses`).
    fn grab(&mut self, surface: PopupSurface, _seat: WlSeat, _serial: Serial) {
        if let Some(p) = self.popups.iter_mut().find(|p| p.surface == surface) {
            p.grab = true;
        }
        self.refocus();
    }

    fn reposition_request(&mut self, surface: PopupSurface, positioner: PositionerState, token: u32) {
        let geometry = self.constrained(&surface, positioner);
        surface.with_pending_state(|s| {
            s.geometry = geometry;
            s.positioner = positioner;
        });
        surface.send_repositioned(token);
    }

    fn popup_destroyed(&mut self, surface: PopupSurface) {
        let owner = self.owner_of(surface.wl_surface());
        self.popups.retain(|p| p.surface != surface && p.surface.alive());
        // Popups left without their parent are dismissed.
        for k in (0..self.popups.len()).rev() {
            if !self.popups[k].dismissed && self.owner_of(self.popups[k].surface.wl_surface()).is_none() {
                self.popups[k].surface.send_popup_done();
                self.popups[k].dismissed = true;
            }
        }
        if let Some(i) = owner {
            self.toplevels[i].paced.repaint();
        }
        self.refocus();
        self.schedule();
    }

    fn fullscreen_request(&mut self, surface: ToplevelSurface, _output: Option<WlOutput>) {
        if let Some(i) = self.toplevel_index_for(surface.wl_surface()) {
            self.request_fullscreen(i, true);
        }
        // Answered at once with the state as it stands; the launcher's choice follows.
        if surface.is_initial_configure_sent() {
            surface.send_configure();
        }
    }

    fn unfullscreen_request(&mut self, surface: ToplevelSurface) {
        if let Some(i) = self.toplevel_index_for(surface.wl_surface()) {
            self.request_fullscreen(i, false);
        }
        if surface.is_initial_configure_sent() {
            surface.send_configure();
        }
    }

    /// A drag on the app's own title bar: the launcher lifts the window as a carry. Resize
    /// requests are left to the default (ignored): the pane decides the size.
    fn move_request(&mut self, surface: ToplevelSurface, _seat: WlSeat, _serial: Serial) {
        if let Some(i) = self.toplevel_index_for(surface.wl_surface()) {
            self.request_move(i);
        }
    }

    /// A right-click on the app's own title bar: the launcher opens the rim tab's menu.
    fn show_window_menu(&mut self, surface: ToplevelSurface, _seat: WlSeat, _serial: Serial, _location: Point<i32, Logical>) {
        if let Some(i) = self.toplevel_index_for(surface.wl_surface()) {
            let t = &self.toplevels[i];
            if t.announced {
                let id = t.id;
                self.events.window_menu_request(id);
            }
        }
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
        let scale = self.owner_of(&surface).map(|i| self.toplevels[i].scale).unwrap_or(self.output_scale);
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

    fn focus_changed(&mut self, seat: &Seat<State>, focused: Option<&KeyboardFocus>) {
        self.focus_data_device(seat, focused);
    }
}

impl SelectionHandler for State {
    type SelectionUserData = ClipSource;

    fn new_selection(&mut self, ty: SelectionTarget, source: Option<SelectionSource>, _seat: Seat<State>) {
        if ty == SelectionTarget::Clipboard {
            self.wayland_selection_set(source.map(|s| s.mime_types()));
        }
    }

    fn send_selection(&mut self, ty: SelectionTarget, mime_type: String, fd: OwnedFd, _seat: Seat<State>, source: &ClipSource) {
        if ty == SelectionTarget::Clipboard {
            self.serve_selection(*source, mime_type, fd);
        }
    }
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
delegate_dmabuf!(State);
