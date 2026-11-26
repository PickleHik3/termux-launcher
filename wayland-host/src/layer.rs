//! The limited `zwlr_layer_shell_v1`: the rules that do not need smithay.
//!
//! The launcher is the shell, so only the overlay and top layers are taken (launchers such as
//! wofi and fuzzel, notification bubbles such as mako); each is shown as a Transient over the
//! current page, sized by its own request and anchors within the page. The background and bottom
//! layers (wallpapers, panels, docks) are refused with `closed`, which makes a client exit or
//! log. Exclusive zones are ignored: nothing reserves room in the launcher.

use crate::protocol::WINDOW_LAYER;
use crate::window_class::WindowClass;

/// The protocol's layers, bottom-most first.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum LayerKind {
    Background,
    Bottom,
    Top,
    Overlay,
}

/// Whether a surface on `kind` is taken: the overlay and top layers only.
pub fn admitted(kind: LayerKind) -> bool {
    matches!(kind, LayerKind::Top | LayerKind::Overlay)
}

pub use crate::protocol::{LAYER_KEYBOARD_EXCLUSIVE as KEYBOARD_EXCLUSIVE, LAYER_KEYBOARD_NONE as KEYBOARD_NONE, LAYER_KEYBOARD_ON_DEMAND as KEYBOARD_ON_DEMAND};

/// Whether the surface may hold the keyboard: on demand and exclusive do, none never.
pub fn takes_keyboard(interactivity: i32) -> bool {
    interactivity == KEYBOARD_EXCLUSIVE || interactivity == KEYBOARD_ON_DEMAND
}

/// The `window_created` flags of a layer surface: the layer bit and the Transient class, so a
/// launcher that does not know layers still draws it over the page in front.
pub fn window_flags() -> i32 {
    WINDOW_LAYER | WindowClass::Transient.flags()
}

/// The size a layer surface is configured with, in logical px: what it asked for, and for an axis
/// it asked 0 on (the protocol means "fill between the anchors") the pane less the margins; never
/// bigger than the pane, never under 1. `margins` is top, right, bottom, left; `pane` the page.
pub fn configure_size(requested: (i32, i32), margins: [i32; 4], pane: (i32, i32)) -> (i32, i32) {
    let axis = |asked: i32, room: i32, margin: i32| {
        let room = room.max(1);
        let size = if asked > 0 { asked } else { room - margin };
        size.clamp(1, room)
    };
    (axis(requested.0, pane.0, margins[1] + margins[3]), axis(requested.1, pane.1, margins[0] + margins[2]))
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::protocol::{WINDOW_CLASS_MASK, WINDOW_CLASS_SHIFT, WINDOW_CLASS_TRANSIENT};

    #[test]
    fn only_the_overlay_and_top_layers_are_admitted() {
        assert!(admitted(LayerKind::Overlay));
        assert!(admitted(LayerKind::Top));
        assert!(!admitted(LayerKind::Bottom));
        assert!(!admitted(LayerKind::Background));
    }

    #[test]
    fn a_layer_surface_is_a_transient_marked_as_a_layer() {
        let flags = window_flags();
        assert_ne!(flags & WINDOW_LAYER, 0);
        assert_eq!((flags & WINDOW_CLASS_MASK) >> WINDOW_CLASS_SHIFT, WINDOW_CLASS_TRANSIENT);
    }

    #[test]
    fn the_layer_bit_is_clear_of_the_class_bits_and_the_replay_bit() {
        assert_eq!(WINDOW_LAYER & WINDOW_CLASS_MASK, 0);
        assert_eq!(WINDOW_LAYER & crate::protocol::WINDOW_REPLAYED, 0);
        assert_eq!(WINDOW_LAYER & crate::protocol::WINDOW_X11, 0);
    }

    #[test]
    fn on_demand_and_exclusive_take_the_keyboard_and_none_does_not() {
        assert!(takes_keyboard(KEYBOARD_ON_DEMAND));
        assert!(takes_keyboard(KEYBOARD_EXCLUSIVE));
        assert!(!takes_keyboard(KEYBOARD_NONE));
        assert!(!takes_keyboard(7));
    }

    #[test]
    fn a_requested_size_is_kept() {
        assert_eq!(configure_size((300, 200), [0; 4], (500, 900)), (300, 200));
    }

    #[test]
    fn a_zero_axis_fills_the_pane_between_the_margins() {
        // A full-width bar 40 high with 10 px side margins and a 5 px top margin.
        assert_eq!(configure_size((0, 40), [5, 10, 0, 10], (500, 900)), (480, 40));
        // A full-height strip.
        assert_eq!(configure_size((60, 0), [8, 0, 12, 0], (500, 900)), (60, 880));
    }

    #[test]
    fn a_surface_bigger_than_the_pane_is_cut_to_it() {
        assert_eq!(configure_size((2000, 3000), [0; 4], (500, 900)), (500, 900));
    }

    #[test]
    fn margins_larger_than_the_pane_leave_one_pixel() {
        assert_eq!(configure_size((0, 0), [600, 600, 600, 600], (500, 900)), (1, 1));
        assert_eq!(configure_size((0, 0), [0; 4], (0, 0)), (1, 1));
    }
}
