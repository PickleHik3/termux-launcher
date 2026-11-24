//! The seat's keyboard focus: a Wayland surface, or an X11 window, whose focus also has to be
//! set on the X server (smithay's X11Surface does that, then enters its wl_surface).

use std::borrow::Cow;

use smithay::backend::input::KeyState;
use smithay::input::keyboard::{KeyboardTarget, KeysymHandle, ModifiersState};
use smithay::input::Seat;
use smithay::reexports::wayland_server::protocol::wl_surface::WlSurface;
use smithay::utils::{IsAlive, Serial};
use smithay::wayland::seat::WaylandFocus;
use smithay::xwayland::X11Surface;

use super::host::State;

#[derive(Debug, Clone, PartialEq)]
pub enum KeyboardFocus {
    Wl(WlSurface),
    X11(X11Surface),
}

impl IsAlive for KeyboardFocus {
    fn alive(&self) -> bool {
        match self {
            KeyboardFocus::Wl(s) => s.alive(),
            KeyboardFocus::X11(w) => w.alive(),
        }
    }
}

impl WaylandFocus for KeyboardFocus {
    fn wl_surface(&self) -> Option<Cow<'_, WlSurface>> {
        match self {
            KeyboardFocus::Wl(s) => Some(Cow::Borrowed(s)),
            KeyboardFocus::X11(w) => w.wl_surface().map(Cow::Owned),
        }
    }
}

impl KeyboardTarget<State> for KeyboardFocus {
    fn enter(&self, seat: &Seat<State>, data: &mut State, keys: Vec<KeysymHandle<'_>>, serial: Serial) {
        match self {
            KeyboardFocus::Wl(s) => KeyboardTarget::enter(s, seat, data, keys, serial),
            KeyboardFocus::X11(w) => KeyboardTarget::enter(w, seat, data, keys, serial),
        }
    }

    fn leave(&self, seat: &Seat<State>, data: &mut State, serial: Serial) {
        match self {
            KeyboardFocus::Wl(s) => KeyboardTarget::leave(s, seat, data, serial),
            KeyboardFocus::X11(w) => KeyboardTarget::leave(w, seat, data, serial),
        }
    }

    fn key(&self, seat: &Seat<State>, data: &mut State, key: KeysymHandle<'_>, state: KeyState, serial: Serial, time: u32) {
        match self {
            KeyboardFocus::Wl(s) => KeyboardTarget::key(s, seat, data, key, state, serial, time),
            KeyboardFocus::X11(w) => KeyboardTarget::key(w, seat, data, key, state, serial, time),
        }
    }

    fn modifiers(&self, seat: &Seat<State>, data: &mut State, modifiers: ModifiersState, serial: Serial) {
        match self {
            KeyboardFocus::Wl(s) => KeyboardTarget::modifiers(s, seat, data, modifiers, serial),
            KeyboardFocus::X11(w) => KeyboardTarget::modifiers(w, seat, data, modifiers, serial),
        }
    }
}
