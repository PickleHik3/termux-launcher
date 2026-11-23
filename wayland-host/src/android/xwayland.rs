//! Rootless Xwayland: every mapped X11 window becomes a toplevel like an xdg_toplevel, through
//! the same Java callbacks.
//!
//! smithay spawns the prefix's Xwayland and runs the X11 window manager on its connection.
//! Its X11 sockets and lock files go in `SMITHAY_X11_TMPDIR` (our smithay patch; upstream
//! hardcodes /tmp), set to the prefix's tmp so Termux X clients find `.X11-unix/X<n>` there.

use std::fs::File;
use std::path::{Path, PathBuf};
use std::process::Stdio;

use smithay::delegate_xwayland_shell;
use smithay::reexports::calloop::RegistrationToken;
use smithay::reexports::wayland_server::protocol::wl_surface::WlSurface;
use smithay::reexports::x11rb::protocol::xproto::Window as X11Window;
use smithay::utils::{Logical, Rectangle};
use smithay::wayland::xwayland_shell::{XWaylandShellHandler, XWaylandShellState};
use smithay::xwayland::xwm::{Reorder, ResizeEdge, WmWindowProperty, XwmId};
use smithay::xwayland::{X11Surface, X11Wm, XWayland, XWaylandEvent, XwmHandler};

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
}

pub struct XwaylandState {
    wm: Option<X11Wm>,
    token: RegistrationToken,
}

pub fn start(state: &mut State, config: XwaylandConfig) {
    let (Some(bin_dir), Some(tmp_dir)) = (config.binary.parent(), config.runtime_dir.parent()) else {
        lwarn!("Xwayland: bad paths {:?} {:?}", config.binary, config.runtime_dir);
        return;
    };
    // The runtime dir is <prefix>/tmp/<name>, so its parent is the prefix's tmp.
    std::env::set_var("SMITHAY_X11_TMPDIR", tmp_dir);
    let _ = std::fs::create_dir_all(tmp_dir.join(".X11-unix"));
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
    let token = state.loop_handle.insert_source(xwayland, move |event, _, state| match event {
        XWaylandEvent::Ready { x11_socket, display_number } => {
            match X11Wm::start_wm(state.loop_handle.clone(), x11_socket, client.clone()) {
                Ok(wm) => {
                    if let Some(x) = state.xwayland.as_mut() {
                        x.wm = Some(wm);
                    }
                    linfo!("Xwayland ready: DISPLAY=:{display_number}");
                }
                Err(e) => lerror!("cannot start the X11 window manager: {e}"),
            }
        }
        XWaylandEvent::Error => lerror!("Xwayland exited while starting; see {}", log_path.display()),
    });
    match token {
        Ok(token) => state.xwayland = Some(XwaylandState { wm: None, token }),
        Err(e) => lerror!("cannot watch Xwayland: {e}"),
    }
}

pub fn stop(state: &mut State) {
    if let Some(x) = state.xwayland.take() {
        drop(x.wm);
        // Dropping the source disconnects Xwayland's client, which ends it, and frees the
        // display's socket and lock.
        state.loop_handle.remove(x.token);
    }
}

fn x11_index(state: &State, window: &X11Surface) -> Option<usize> {
    state.toplevels.iter().position(|t| matches!(&t.shell, Shell::X11(w) if w == window))
}

impl XwmHandler for State {
    fn xwm_state(&mut self, _xwm: XwmId) -> &mut X11Wm {
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
            geo.size.w = w as i32;
        }
        if let Some(h) = h {
            geo.size.h = h as i32;
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
