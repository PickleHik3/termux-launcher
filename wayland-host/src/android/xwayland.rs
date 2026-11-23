//! Rootless Xwayland (stretch). Placeholder until the core works.

use smithay::reexports::wayland_server::protocol::wl_surface::WlSurface;
use smithay::wayland::compositor::CompositorClientState;

use super::host::State;

pub struct XwaylandConfig;

impl XwaylandConfig {
    pub fn new(_binary: String, _runtime_dir: &str) -> XwaylandConfig {
        XwaylandConfig
    }
}
pub struct XwaylandState;

#[derive(Default)]
pub struct XwaylandClientData {
    pub compositor_state: CompositorClientState,
}

impl smithay::reexports::wayland_server::backend::ClientData for XwaylandClientData {}

pub fn start(_state: &mut State, _config: XwaylandConfig) {}
pub fn stop(_state: &mut State) {}
pub fn commit_hook(_state: &mut State, _surface: &WlSurface) {}
