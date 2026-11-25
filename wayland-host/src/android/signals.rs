//! What apps tell the launcher about their windows beyond drawing them: `xdg_activation_v1`
//! (bring this window in front), `zwp_idle_inhibit_v1` (keep the screen on) and presentation
//! feedback.
//!
//! The policy (whose tokens count, when; which window holds an inhibitor) is in
//! `crate::activation` and `crate::idle`; this file reads the focus and the clock for it and
//! reports to Java through `HostEvents`.

use std::time::Instant;

use smithay::reexports::wayland_server::backend::ClientId;
use smithay::reexports::wayland_server::protocol::wl_surface::WlSurface;
use smithay::reexports::wayland_server::Resource;
use smithay::wayland::idle_inhibit::IdleInhibitHandler;
use smithay::wayland::xdg_activation::{XdgActivationHandler, XdgActivationState, XdgActivationToken, XdgActivationTokenData};

use crate::activation;

use super::host::{root_of, State};
use super::log::linfo;

impl State {
    /// The client that owns toplevel `i`'s surface.
    fn client_of(&self, i: usize) -> Option<ClientId> {
        self.toplevels[i].shell.wl_surface()?.client().map(|c| c.id())
    }

    /// The window `id` (if any) took focus, lost it or got input: its client counts as recently
    /// active for activation tokens.
    pub fn note_active(&mut self, id: Option<i64>) {
        let Some(i) = id.and_then(|id| self.toplevel_index(id)) else { return };
        if let Some(client) = self.client_of(i) {
            self.recent.note(client, Instant::now());
        }
    }

    /// Whether a token made by `client` is honoured now: it holds the keyboard, or was active within the grace.
    fn token_client_ok(&self, client: &ClientId) -> bool {
        let focused = self.focused.and_then(|id| self.toplevel_index(id)).and_then(|i| self.client_of(i)).as_ref() == Some(client);
        activation::token_valid(focused, self.recent.since(client, Instant::now()))
    }
}

impl XdgActivationHandler for State {
    fn activation_state(&mut self) -> &mut XdgActivationState {
        &mut self.activation_state
    }

    /// A token from a client the user was not just using is never stored, so a later activate
    /// with it finds nothing. Expired tokens are dropped here, so the table stays small.
    fn token_created(&mut self, _token: XdgActivationToken, data: XdgActivationTokenData) -> bool {
        self.activation_state.retain_tokens(|_, d| activation::token_fresh(d.timestamp.elapsed()));
        data.client_id.as_ref().map_or(false, |c| self.token_client_ok(c))
    }

    /// An app asked for one of its windows to come in front. The launcher shows it as a tap on
    /// its chip does. A token works once.
    fn request_activation(&mut self, token: XdgActivationToken, token_data: XdgActivationTokenData, surface: WlSurface) {
        self.activation_state.remove_token(&token);
        if !activation::token_fresh(token_data.timestamp.elapsed()) {
            return;
        }
        let Some(i) = root_of(&surface).and_then(|root| self.toplevel_index_for(&root)) else { return };
        let id = self.toplevels[i].id;
        if id <= 0 {
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
