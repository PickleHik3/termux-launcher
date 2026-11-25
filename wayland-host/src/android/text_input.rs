//! `zwp_text_input_v3`: the launcher's keyboard for a Linux app's text field.
//!
//! The host is the input method itself (smithay's `wayland::text_input` only relays to an
//! input-method-v2 client, which there is none of here), so the protocol is spoken here. Each
//! text input is entered on the surface with keyboard focus when it belongs to the same client.
//! When the focused window's text input is enabled the launcher hears "text field focused" for
//! that window (`HostEvents::text_input`), and "text field left" when it is disabled, destroyed
//! or loses focus. The launcher's typed text comes back as `commit_string`, its composing text as
//! `preedit_string`, and its deletions as `delete_surrounding_text`, each followed by `done`.
//! Keys (arrows, Enter, Backspace) keep reaching the app as wl_keyboard keys.

use smithay::reexports::wayland_protocols::wp::text_input::zv3::server::zwp_text_input_manager_v3::{
    self, ZwpTextInputManagerV3,
};
use smithay::reexports::wayland_protocols::wp::text_input::zv3::server::zwp_text_input_v3::{
    self, ZwpTextInputV3,
};
use smithay::reexports::wayland_server::backend::ClientId;
use smithay::reexports::wayland_server::protocol::wl_surface::WlSurface;
use smithay::reexports::wayland_server::{
    Client, DataInit, Dispatch, DisplayHandle, GlobalDispatch, New, Resource, WEnum,
};

use crate::text_field::{bytes_after, bytes_before, utf16_to_byte, Committed, Field, FieldTracker, Pending};

use super::host::State;

const MANAGER_VERSION: u32 = 1;

struct Instance {
    object: ZwpTextInputV3,
    /// Commits the client sent: `done` carries it back.
    serial: u32,
    pending: Pending,
    state: Committed,
    /// `enter` was sent for the current focus.
    entered: bool,
}

/// Every live text input and the surface they are entered on.
#[derive(Default)]
pub struct TextInputs {
    instances: Vec<Instance>,
    /// The wl_surface with keyboard focus; text inputs of its client are entered on it.
    focus: Option<WlSurface>,
    tracker: FieldTracker,
}

impl TextInputs {
    fn index_of(&self, object: &ZwpTextInputV3) -> Option<usize> {
        self.instances.iter().position(|i| i.object == *object)
    }

    /// The enabled text input of the focused surface's client, which the launcher types into.
    fn active(&self) -> Option<usize> {
        let focus = self.focus.as_ref().filter(|s| s.is_alive())?;
        let focus_id = focus.id();
        self.instances
            .iter()
            .position(|i| i.entered && i.state.enabled && i.object.id().same_client_as(&focus_id))
    }
}

/// Offers `zwp_text_input_manager_v3`; the global lives as long as the display.
pub fn create_global(dh: &DisplayHandle) {
    dh.create_global::<State, ZwpTextInputManagerV3, ()>(MANAGER_VERSION, ());
}

impl State {
    /// Keyboard focus moved to `surface` (None: no surface): text inputs leave the old one and
    /// enter the new one's, and the launcher hears what that does to the field.
    pub fn text_input_focus(&mut self, surface: Option<WlSurface>) {
        let ti = &mut self.text_inputs;
        if ti.focus == surface {
            return;
        }
        if let Some(old) = ti.focus.take().filter(|s| s.is_alive()) {
            for i in ti.instances.iter_mut().filter(|i| i.entered) {
                i.object.leave(&old);
                i.entered = false;
                // A text input that left is disabled until its app enables it again.
                i.state = Committed::default();
                i.pending = Pending::default();
            }
        }
        if let Some(new) = surface.as_ref() {
            let id = new.id();
            for i in ti.instances.iter_mut().filter(|i| i.object.id().same_client_as(&id)) {
                i.object.enter(new);
                i.entered = true;
            }
        }
        ti.focus = surface;
        self.text_input_changed();
    }

    /// Tells the launcher when the focused window's field appears, changes or goes.
    fn text_input_changed(&mut self) {
        let now = match (self.text_inputs.active(), self.focused) {
            (Some(i), Some(window)) => {
                let s = &self.text_inputs.instances[i].state;
                Some(Field { window, hint: s.hint, purpose: s.purpose })
            }
            _ => None,
        };
        let (left, focused) = self.text_inputs.tracker.update(now);
        if let Some(window) = left {
            self.events.text_input(window, false, 0, 0);
        }
        if let Some(f) = focused {
            self.events.text_input(f.window, true, f.hint as i32, f.purpose as i32);
        }
    }

    /// The text input the launcher may type into for window `id`: only the focused window's.
    fn text_input_for(&self, id: i64) -> Option<usize> {
        if id <= 0 || self.focused != Some(id) {
            return None;
        }
        self.text_inputs.active()
    }

    /// Typed text, inserted at the cursor in place of any composing text.
    pub fn commit_text(&mut self, id: i64, text: String) {
        let Some(i) = self.text_input_for(id) else { return };
        let inst = &self.text_inputs.instances[i];
        inst.object.commit_string(Some(text));
        inst.object.done(inst.serial);
    }

    /// Composing text shown at the cursor (empty clears it); `cursor` is a UTF-16 index into it,
    /// -1 hides the cursor.
    pub fn set_preedit(&mut self, id: i64, text: String, cursor: i32) {
        let Some(i) = self.text_input_for(id) else { return };
        let inst = &self.text_inputs.instances[i];
        let at = utf16_to_byte(&text, cursor);
        inst.object.preedit_string((!text.is_empty()).then_some(text), at, at);
        inst.object.done(inst.serial);
    }

    /// Deletes `before` and `after` UTF-16 code units around the cursor, measured in the
    /// surrounding text the app last told.
    pub fn delete_surrounding(&mut self, id: i64, before: i32, after: i32) {
        let Some(i) = self.text_input_for(id) else { return };
        let inst = &self.text_inputs.instances[i];
        let s = &inst.state;
        let before = bytes_before(&s.surrounding, s.cursor, before.max(0) as u32);
        let after = bytes_after(&s.surrounding, s.cursor, after.max(0) as u32);
        inst.object.delete_surrounding_text(before, after);
        inst.object.done(inst.serial);
    }
}

impl GlobalDispatch<ZwpTextInputManagerV3, ()> for State {
    fn bind(
        _state: &mut State,
        _dh: &DisplayHandle,
        _client: &Client,
        resource: New<ZwpTextInputManagerV3>,
        _data: &(),
        data_init: &mut DataInit<'_, State>,
    ) {
        data_init.init(resource, ());
    }
}

impl Dispatch<ZwpTextInputManagerV3, ()> for State {
    fn request(
        state: &mut State,
        _client: &Client,
        _resource: &ZwpTextInputManagerV3,
        request: zwp_text_input_manager_v3::Request,
        _data: &(),
        _dh: &DisplayHandle,
        data_init: &mut DataInit<'_, State>,
    ) {
        if let zwp_text_input_manager_v3::Request::GetTextInput { id, .. } = request {
            let object = data_init.init(id, ());
            // One seat: a text input made while its client has the focus is entered at once.
            let entered = match state.text_inputs.focus.as_ref().filter(|s| s.is_alive()) {
                Some(focus) if object.id().same_client_as(&focus.id()) => {
                    object.enter(focus);
                    true
                }
                _ => false,
            };
            state.text_inputs.instances.push(Instance {
                object,
                serial: 0,
                pending: Pending::default(),
                state: Committed::default(),
                entered,
            });
        }
    }
}

impl Dispatch<ZwpTextInputV3, ()> for State {
    fn request(
        state: &mut State,
        _client: &Client,
        resource: &ZwpTextInputV3,
        request: zwp_text_input_v3::Request,
        _data: &(),
        _dh: &DisplayHandle,
        _data_init: &mut DataInit<'_, State>,
    ) {
        let Some(i) = state.text_inputs.index_of(resource) else { return };
        let inst = &mut state.text_inputs.instances[i];
        match request {
            zwp_text_input_v3::Request::Enable => inst.pending.enable = Some(true),
            zwp_text_input_v3::Request::Disable => inst.pending.enable = Some(false),
            zwp_text_input_v3::Request::SetContentType { hint, purpose } => {
                let hint = match hint {
                    WEnum::Value(h) => h.bits(),
                    WEnum::Unknown(raw) => raw,
                };
                let purpose = match purpose {
                    WEnum::Value(p) => p.into(),
                    WEnum::Unknown(raw) => raw,
                };
                inst.pending.content = Some((hint, purpose));
            }
            zwp_text_input_v3::Request::SetSurroundingText { text, cursor, .. } => {
                inst.pending.surrounding = Some((text, cursor.max(0) as u32));
            }
            zwp_text_input_v3::Request::Commit => {
                // Counted even while not entered, so `done` stays in step with the app.
                inst.serial = inst.serial.wrapping_add(1);
                let pending = std::mem::take(&mut inst.pending);
                // Requests between leave and the next enter are ignored, as the protocol says.
                if inst.entered {
                    inst.state.apply(pending);
                    state.text_input_changed();
                }
            }
            // Text change cause and cursor rectangle: the launcher's keyboard has no use for them.
            _ => {}
        }
    }

    fn destroyed(state: &mut State, _client: ClientId, resource: &ZwpTextInputV3, _data: &()) {
        if let Some(i) = state.text_inputs.index_of(resource) {
            state.text_inputs.instances.swap_remove(i);
            state.text_input_changed();
        }
    }
}
