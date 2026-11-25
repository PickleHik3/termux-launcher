//! What apps tell the launcher about their windows beyond drawing them: `xdg_activation_v1`
//! (bring this window in front), `zwp_idle_inhibit_v1` (keep the screen on) and presentation
//! feedback.
//!
//! The policy (whose tokens count, when; which window holds an inhibitor) is in
//! `crate::activation` and `crate::idle`; this file reads the focus and the clock for it and
//! reports to Java through `HostEvents`.

use smithay::reexports::wayland_server::protocol::wl_surface::WlSurface;
use smithay::reexports::wayland_server::Resource;
use smithay::wayland::idle_inhibit::IdleInhibitHandler;
use smithay::wayland::xdg_activation::{XdgActivationHandler, XdgActivationState, XdgActivationToken, XdgActivationTokenData};

use crate::activation;

use super::host::{root_of, State};
use super::log::linfo;

impl XdgActivationHandler for State {
    fn activation_state(&mut self) -> &mut XdgActivationState {
        &mut self.activation_state
    }

    /// Every token is stored (a second `firefox` process makes its own, with no window yet, and
    /// hands it to the running one); the rule applies when it is used. Expired tokens are dropped
    /// here, so the table stays small.
    fn token_created(&mut self, _token: XdgActivationToken, _data: XdgActivationTokenData) -> bool {
        self.activation_state.retain_tokens(|_, d| activation::token_fresh(d.timestamp.elapsed()));
        true
    }

    /// An app asked for one of its windows to come in front. The launcher shows it as a tap on
    /// its chip does. A token works once.
    fn request_activation(&mut self, token: XdgActivationToken, token_data: XdgActivationTokenData, surface: WlSurface) {
        self.activation_state.remove_token(&token);
        let id = root_of(&surface).and_then(|root| self.toplevel_index_for(&root)).map_or(0, |i| self.toplevels[i].id);
        // The asking client owns this Linux window (an announced toplevel of its own).
        let owns_window = id > 0;
        if !activation::token_valid(token_data.timestamp.elapsed(), owns_window, token_data.serial.is_some()) {
            return;
        }
        linfo!("activation requested for window {id}");
        self.events.activation_request(id);
    }
}

impl State {
    /// Tells the launcher which windows hold an idle inhibitor now: any inhibitor on one of the
    /// window's surfaces (its subsurfaces and popups too) counts. Only a change is sent.
    pub fn refresh_inhibit(&mut self) {
        for i in 0..self.toplevels.len() {
            let id = self.toplevels[i].id;
            if id <= 0 {
                continue;
            }
            let holds = self.inhibitors.any(|s| s.is_alive() && self.owner_of(s) == Some(i));
            if holds != self.toplevels[i].inhibiting {
                self.toplevels[i].inhibiting = holds;
                self.events.idle_inhibit(id, holds);
            }
        }
    }
}

impl IdleInhibitHandler for State {
    fn inhibit(&mut self, surface: WlSurface) {
        self.inhibitors.add(surface);
        self.refresh_inhibit();
    }

    fn uninhibit(&mut self, surface: WlSurface) {
        self.inhibitors.remove_one(&surface);
        self.refresh_inhibit();
    }
}
