//! Rootless Xwayland: every mapped X11 window becomes a toplevel like an xdg_toplevel, through
//! the same Java callbacks.
//!
//! smithay spawns the prefix's Xwayland and runs the X11 window manager on its connection.
//! Its X11 sockets and lock files go in `SMITHAY_X11_TMPDIR` (our smithay patch; upstream
//! hardcodes /tmp), which nativeStart sets to the prefix's tmp before the compositor thread
//! exists, so Termux X clients find `.X11-unix/X<n>` there.
//!
//! Xwayland's life is tied to the compositor thread that spawns it: the patch sets
//! PR_SET_PDEATHSIG = SIGKILL in the child, so it dies when that thread ends, on a clean stop
//! and when the whole process dies; a clean stop also reaps it. If Xwayland dies first, its
//! windows go and X clients have no display until the host starts again. Locks a dead
//! compositor left behind are removed on the next start.

use std::fs::File;
use std::os::fd::OwnedFd;
use std::os::unix::net::UnixStream;
use std::path::{Path, PathBuf};
use std::process::Stdio;

use smithay::delegate_xwayland_shell;
use smithay::reexports::calloop::RegistrationToken;
use smithay::reexports::wayland_server::protocol::wl_surface::WlSurface;
use smithay::reexports::wayland_server::Client;
use smithay::reexports::x11rb::connection::Connection;
use smithay::reexports::x11rb::protocol::xproto::{AtomEnum, PropMode, Window as X11Window};
use smithay::reexports::x11rb::wrapper::ConnectionExt as _;
use smithay::reexports::x11rb::rust_connection::{DefaultStream, RustConnection};
use smithay::xwayland::xwm::settings::Value;
use smithay::utils::{Logical, Rectangle};
use smithay::wayland::selection::SelectionTarget;
use smithay::wayland::xwayland_shell::{XWaylandShellHandler, XWaylandShellState};
use smithay::xwayland::xwm::{Reorder, ResizeEdge, WmWindowProperty, XwmId};
use smithay::xwayland::{X11Surface, X11Wm, XWayland, XWaylandClientData, XWaylandEvent, XwmHandler};

use crate::xlock::{self, Owner};
use crate::xscale;

use super::host::{OrWindow, Shell, State};
use super::log::{lerror, linfo, lwarn};

pub struct XwaylandConfig {
    binary: PathBuf,
    runtime_dir: PathBuf,
}

impl XwaylandConfig {
    pub fn new(binary: String, runtime_dir: &str) -> XwaylandConfig {
        XwaylandConfig { binary: binary.into(), runtime_dir: runtime_dir.into() }
    }

    /// The runtime dir is <prefix>/tmp/<name>, so its parent is the prefix's tmp: where the
    /// X11 sockets and locks go.
    pub fn tmp_dir(&self) -> Option<PathBuf> {
        self.runtime_dir.parent().map(Path::to_path_buf)
    }
}

pub struct XwaylandState {
    /// Kept until the host stops, even after Xwayland is gone: smithay's window-manager event
    /// source stays registered (its token is not ours) and asks `xwm_state` for it, so dropping
    /// it while the loop runs would panic.
    pub(super) wm: Option<X11Wm>,
    /// The XWayland source, which owns the display's lock and sockets; removed when Xwayland
    /// is gone so the display number is free again.
    token: Option<RegistrationToken>,
    client: Client,
}

pub fn start(state: &mut State, config: XwaylandConfig) {
    let (Some(bin_dir), Some(tmp_dir)) = (config.binary.parent(), config.runtime_dir.parent()) else {
        lwarn!("Xwayland: bad paths {:?} {:?}", config.binary, config.runtime_dir);
        return;
    };
    let _ = std::fs::create_dir_all(tmp_dir.join(".X11-unix"));
    remove_stale_locks(tmp_dir);
    let log_path = config.runtime_dir.join("xwayland.log");
    let stderr = File::create(&log_path).map(Stdio::from).unwrap_or_else(|_| Stdio::null());
    let envs: Vec<(&str, &Path)> = vec![
        // Command looks the binary up in the child's PATH.
        ("PATH", bin_dir),
        ("TMPDIR", tmp_dir),
        ("XDG_RUNTIME_DIR", &config.runtime_dir),
    ];
    let (xwayland, client) = match XWayland::spawn(&state.display_handle, None, envs, false, Stdio::null(), stderr, |_| {}) {
        Ok(v) => v,
        Err(e) => {
            lerror!("cannot spawn Xwayland: {e}");
            return;
        }
    };
    let wm_client = client.clone();
    let socket_dir = tmp_dir.join(".X11-unix");
    let token = state.loop_handle.insert_source(xwayland, move |event, _, state| match event {
        XWaylandEvent::Ready { x11_socket, display_number } => {
            match X11Wm::start_wm(state.loop_handle.clone(), x11_socket, wm_client.clone()) {
                Ok(wm) => {
                    if let Some(x) = state.xwayland.as_mut() {
                        x.wm = Some(wm);
                    }
                    let scale = state.output_scale();
                    set_toolkit_scale(state, scale);
                    set_resources(socket_dir.join(format!("X{display_number}")), scale);
                    linfo!("Xwayland ready: DISPLAY=:{display_number}");
                    state.events.x_display(&format!(":{display_number}"));
                }
                Err(e) => lerror!("cannot start the X11 window manager: {e}"),
            }
        }
        XWaylandEvent::Error => {
            lerror!("Xwayland exited while starting; see {}", log_path.display());
            state.events.x_display("");
        }
    });
    match token {
        Ok(token) => state.xwayland = Some(XwaylandState { wm: None, token: Some(token), client }),
        Err(e) => lerror!("cannot watch Xwayland: {e}"),
    }
}

/// Tells X toolkits the output scale through XSETTINGS (see `crate::xscale`); GTK and
/// Chromium follow a change live. Called when Xwayland is ready and when the output scale
/// changes; nothing to do while no window manager runs.
pub fn set_toolkit_scale(state: &mut State, scale: f64) {
    let Some(wm) = state.xwayland.as_mut().and_then(|x| x.wm.as_mut()) else { return };
    let t = xscale::toolkit_scale(scale);
    let settings = [
        ("Xft/DPI".to_string(), Value::Integer(t.xft_dpi_1024)),
        ("Gdk/WindowScalingFactor".to_string(), Value::Integer(t.window_scale)),
        ("Gdk/UnscaledDPI".to_string(), Value::Integer(t.unscaled_dpi_1024)),
    ];
    match wm.set_xsettings(settings.into_iter()) {
        Ok(()) => linfo!("X toolkits: scale {scale}, window scale {}, Xft/DPI {}", t.window_scale, t.xft_dpi),
        Err(e) => lwarn!("cannot set XSETTINGS: {e:?}"),
    }
}

/// Writes `Xft.dpi` into the root window's `RESOURCE_MANAGER` (what `xrdb` does), for Xft
/// clients that do not read XSETTINGS. Once, when Xwayland is ready: a client reads it at start,
/// and writing it again later would replace whatever a user's `xrdb` added. On its own thread
/// and its own X connection, so the compositor never waits on Xwayland, which is its client.
fn set_resources(socket: PathBuf, scale: f64) {
    let text = xscale::resources(&xscale::toolkit_scale(scale));
    let spawned = std::thread::Builder::new().name("tl-xresources".into()).spawn(move || {
        let result = (|| -> Result<(), Box<dyn std::error::Error>> {
            let (stream, _) = DefaultStream::from_unix_stream(UnixStream::connect(&socket)?)?;
            let conn = RustConnection::connect_to_stream(stream, 0)?;
            let root = conn.setup().roots.first().ok_or("no X screen")?.root;
            conn.change_property8(PropMode::REPLACE, root, AtomEnum::RESOURCE_MANAGER, AtomEnum::STRING, text.as_bytes())?
                .check()?;
            Ok(())
        })();
        if let Err(e) = result {
            lwarn!("cannot set X resources on {}: {e}", socket.display());
        }
    });
    if let Err(e) = spawned {
        lwarn!("cannot start the X resources thread: {e}");
    }
}

/// Removes `.X<n>-lock` files (and their `.X11-unix/X<n>` sockets) that no live server owns: a
/// compositor that crashed leaves its display locked, and smithay would otherwise pick the next
/// number each time. `xlock` decides; a live server answers on its socket.
fn remove_stale_locks(tmp_dir: &Path) {
    let Ok(entries) = std::fs::read_dir(tmp_dir) else { return };
    for entry in entries.flatten() {
        let name = entry.file_name();
        let Some(number) = name.to_str().and_then(|n| n.strip_prefix(".X")).and_then(|n| n.strip_suffix("-lock")) else {
            continue;
        };
        let Ok(number) = number.parse::<u32>() else { continue };
        let Some(pid) = std::fs::read_to_string(entry.path()).ok().and_then(|s| xlock::parse_lock_pid(&s)) else {
            continue;
        };
        // SAFETY: signal 0 only checks that the pid exists and may be signalled.
        let rc = unsafe { libc::kill(pid, 0) };
        let owner = if rc == 0 {
            Owner::Alive
        } else if std::io::Error::last_os_error().raw_os_error() == Some(libc::ESRCH) {
            Owner::Gone
        } else {
            Owner::OtherUser
        };
        let socket = tmp_dir.join(".X11-unix").join(format!("X{number}"));
        if xlock::is_stale(owner, || UnixStream::connect(&socket).is_ok()) {
            let _ = std::fs::remove_file(&socket);
            let _ = std::fs::remove_file(entry.path());
            linfo!("removed stale X display :{number} (pid {pid}, {owner:?})");
        }
    }
}

/// Ends Xwayland with the host, after the loop has stopped: reaps the child (a dropped display
/// never dispatches its disconnect) and frees the display.
pub fn stop(state: &mut State) {
    if let Some(mut x) = state.xwayland.take() {
        if let Some(data) = x.client.get_data::<XWaylandClientData>() {
            data.reap_child();
        }
        if let Some(token) = x.token.take() {
            state.loop_handle.remove(token);
        }
        drop(x.wm.take());
    }
}

fn x11_index(state: &State, window: &X11Surface) -> Option<usize> {
    state.toplevels.iter().position(|t| matches!(&t.shell, Shell::X11(w) if w == window))
}

/// An override-redirect window stops being shown; its owner's view is composed again.
fn drop_or_window(state: &mut State, window: &X11Surface) {
    if let Some(k) = state.or_windows.iter().position(|o| o.window == *window) {
        let o = state.or_windows.remove(k);
        state.repaint_id(o.owner);
    }
}

impl XwmHandler for State {
    fn xwm_state(&mut self, _xwm: XwmId) -> &mut X11Wm {
        // smithay asks only from its window-manager source, which exists only once start_wm
        // gave us the wm, and the wm is kept until the loop has stopped (see XwaylandState).
        self.xwayland.as_mut().and_then(|x| x.wm.as_mut()).expect("X11 window manager")
    }

    fn allow_selection_access(&mut self, _xwm: XwmId, selection: SelectionTarget) -> bool {
        selection == SelectionTarget::Clipboard
    }

    fn send_selection(&mut self, _xwm: XwmId, selection: SelectionTarget, mime_type: String, fd: OwnedFd) {
        if selection == SelectionTarget::Clipboard {
            self.serve_x11_read(mime_type, fd);
        }
    }

    fn new_selection(&mut self, _xwm: XwmId, selection: SelectionTarget, mime_types: Vec<String>) {
        if selection == SelectionTarget::Clipboard {
            self.x11_selection_set(mime_types);
        }
    }

    fn cleared_selection(&mut self, _xwm: XwmId, selection: SelectionTarget) {
        if selection == SelectionTarget::Clipboard {
            self.x11_selection_cleared();
        }
    }

    fn new_window(&mut self, _xwm: XwmId, _window: X11Surface) {}

    fn new_override_redirect_window(&mut self, _xwm: XwmId, _window: X11Surface) {}

    fn map_window_request(&mut self, _xwm: XwmId, window: X11Surface) {
        if let Err(e) = window.set_mapped(true) {
            lwarn!("X11 map: {e:?}");
            return;
        }
        if x11_index(self, &window).is_some() {
            return;
        }
        // Every window sits at the root's origin; each has its own wl_surface, so input never
        // has to be picked by position.
        let size = window.geometry().size;
        let _ = window.configure(Rectangle::new((0, 0).into(), size));
        self.add_toplevel(Shell::X11(window));
        let i = self.toplevels.len() - 1;
        self.announce(i);
    }

    /// Menus and tooltips of X apps: shown over the X window they belong to (`or_owner`), at
    /// their X position, in that window's view.
    fn mapped_override_redirect_window(&mut self, _xwm: XwmId, window: X11Surface) {
        if self.or_windows.iter().any(|o| o.window == window) {
            return;
        }
        match self.or_owner(&window) {
            Some(owner) => {
                self.or_windows.push(OrWindow { window, owner });
                self.repaint_id(owner);
            }
            None => linfo!("override-redirect X window 0x{:x} has no X window to be shown with", window.window_id()),
        }
    }

    fn unmapped_window(&mut self, _xwm: XwmId, window: X11Surface) {
        drop_or_window(self, &window);
        if let Some(i) = x11_index(self, &window) {
            self.remove_toplevel_at(i);
        }
        if !window.is_override_redirect() {
            let _ = window.set_mapped(false);
        }
    }

    fn destroyed_window(&mut self, _xwm: XwmId, window: X11Surface) {
        drop_or_window(self, &window);
        if let Some(i) = x11_index(self, &window) {
            self.remove_toplevel_at(i);
        }
    }

    fn configure_request(
        &mut self,
        _xwm: XwmId,
        window: X11Surface,
        _x: Option<i32>,
        _y: Option<i32>,
        w: Option<u32>,
        h: Option<u32>,
        _reorder: Option<Reorder>,
    ) {
        let mut geo = window.geometry();
        if let Some(w) = w {
            geo.size.w = i32::try_from(w).unwrap_or(i32::MAX);
        }
        if let Some(h) = h {
            geo.size.h = i32::try_from(h).unwrap_or(i32::MAX);
        }
        // A window shown in a view keeps the view's size.
        if let Some((vw, vh)) = x11_index(self, &window).and_then(|i| self.view_size(i)) {
            geo.size = (vw, vh).into();
        }
        geo.loc = (0, 0).into();
        let _ = window.configure(geo);
    }

    /// An override-redirect window moved or was resized.
    fn configure_notify(&mut self, _xwm: XwmId, window: X11Surface, _geometry: Rectangle<i32, Logical>, _above: Option<X11Window>) {
        if let Some(owner) = self.or_windows.iter().find(|o| o.window == window).map(|o| o.owner) {
            self.repaint_id(owner);
        }
    }

    fn property_notify(&mut self, _xwm: XwmId, window: X11Surface, property: WmWindowProperty) {
        if matches!(property, WmWindowProperty::Title | WmWindowProperty::Class) {
            if let Some(i) = x11_index(self, &window) {
                self.rename_at(i);
            }
        }
    }

    fn resize_request(&mut self, _xwm: XwmId, _window: X11Surface, _button: u32, _resize_edge: ResizeEdge) {}

    fn move_request(&mut self, _xwm: XwmId, _window: X11Surface, _button: u32) {}

    /// The X connection is gone (Xwayland crashed or was killed): every X window goes, and the
    /// display is freed. Xwayland is not restarted; X clients have no display until the host
    /// starts again.
    fn disconnected(&mut self, _xwm: XwmId) {
        lerror!("Xwayland is gone; its windows are closed");
        self.events.x_display("");
        self.or_windows.clear();
        for i in (0..self.toplevels.len()).rev() {
            if matches!(self.toplevels[i].shell, Shell::X11(_)) {
                self.remove_toplevel_at(i);
            }
        }
        if let Some(token) = self.xwayland.as_mut().and_then(|x| x.token.take()) {
            self.loop_handle.remove(token);
        }
    }
}

impl XWaylandShellHandler for State {
    fn xwayland_shell_state(&mut self) -> &mut XWaylandShellState {
        &mut self.xwayland_shell_state
    }

    fn surface_associated(&mut self, _xwm: XwmId, wl_surface: WlSurface, surface: X11Surface) {
        self.output.enter(&wl_surface);
        if let Some(owner) = self.or_windows.iter().find(|o| o.window == surface).map(|o| o.owner) {
            self.repaint_id(owner);
        }
    }
}

delegate_xwayland_shell!(State);
