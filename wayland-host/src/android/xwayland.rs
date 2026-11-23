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
use std::os::unix::net::UnixStream;
use std::path::{Path, PathBuf};
use std::process::Stdio;

use smithay::delegate_xwayland_shell;
use smithay::reexports::calloop::RegistrationToken;
use smithay::reexports::wayland_server::protocol::wl_surface::WlSurface;
use smithay::reexports::wayland_server::Client;
use smithay::reexports::x11rb::protocol::xproto::Window as X11Window;
use smithay::utils::{Logical, Rectangle};
use smithay::wayland::xwayland_shell::{XWaylandShellHandler, XWaylandShellState};
use smithay::xwayland::xwm::{Reorder, ResizeEdge, WmWindowProperty, XwmId};
use smithay::xwayland::{X11Surface, X11Wm, XWayland, XWaylandClientData, XWaylandEvent, XwmHandler};

use crate::xlock::{self, Owner};

use super::host::{Shell, State};
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
    wm: Option<X11Wm>,
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
    let token = state.loop_handle.insert_source(xwayland, move |event, _, state| match event {
        XWaylandEvent::Ready { x11_socket, display_number } => {
            match X11Wm::start_wm(state.loop_handle.clone(), x11_socket, wm_client.clone()) {
                Ok(wm) => {
                    if let Some(x) = state.xwayland.as_mut() {
                        x.wm = Some(wm);
                    }
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

impl XwmHandler for State {
    fn xwm_state(&mut self, _xwm: XwmId) -> &mut X11Wm {
        // smithay asks only from its window-manager source, which exists only once start_wm
        // gave us the wm, and the wm is kept until the loop has stopped (see XwaylandState).
        self.xwayland.as_mut().and_then(|x| x.wm.as_mut()).expect("X11 window manager")
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

    fn mapped_override_redirect_window(&mut self, _xwm: XwmId, _window: X11Surface) {}

    fn unmapped_window(&mut self, _xwm: XwmId, window: X11Surface) {
        if let Some(i) = x11_index(self, &window) {
            self.remove_toplevel_at(i);
        }
        if !window.is_override_redirect() {
            let _ = window.set_mapped(false);
        }
    }

    fn destroyed_window(&mut self, _xwm: XwmId, window: X11Surface) {
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

    fn configure_notify(&mut self, _xwm: XwmId, _window: X11Surface, _geometry: Rectangle<i32, Logical>, _above: Option<X11Window>) {}

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

    fn surface_associated(&mut self, _xwm: XwmId, wl_surface: WlSurface, _surface: X11Surface) {
        self.output.enter(&wl_surface);
    }
}

delegate_xwayland_shell!(State);
