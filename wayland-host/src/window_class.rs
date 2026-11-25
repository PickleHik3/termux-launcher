//! A toplevel's class, decided once before its first configure. The class says what size the
//! window is told (Main gets the pane, the others keep their own) and where the launcher puts it.
//! `WindowClass::of` is pure; the xdg and X11 paths gather the hints and call it.

use crate::protocol::{
    WINDOW_CLASS_DIALOG, WINDOW_CLASS_FIXED, WINDOW_CLASS_MAIN, WINDOW_CLASS_MASK, WINDOW_CLASS_SHIFT,
    WINDOW_CLASS_TRANSIENT,
};

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum WindowClass {
    Main,
    Dialog,
    Fixed,
    Transient,
}

/// The X11 window type; xdg toplevels pass `Normal`.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum WindowKind {
    Normal,
    Dialog,
    Utility,
    Splash,
    Toolbar,
    Notification,
    Unknown,
}

#[derive(Clone, Copy, Debug)]
pub struct WindowHints {
    pub has_parent: bool,
    pub kind: WindowKind,
    pub modal: bool,
    /// Size limits in logical px; 0 on an axis means unset.
    pub min: (i32, i32),
    pub max: (i32, i32),
    /// The pane in logical px; (0, 0) when no view size is known yet.
    pub pane: (i32, i32),
}

impl WindowClass {
    pub fn of(h: &WindowHints) -> WindowClass {
        let (minw, minh) = h.min;
        let (maxw, maxh) = h.max;
        let set = maxw > 0 && maxh > 0;
        let fixed = set
            && ((minw == maxw && minh == maxh)
                || (h.pane.0 > 0 && h.pane.1 > 0 && maxw < h.pane.0 && maxh < h.pane.1));
        if h.has_parent || h.modal || h.kind == WindowKind::Dialog {
            return WindowClass::Dialog;
        }
        match h.kind {
            WindowKind::Splash
            | WindowKind::Notification
            | WindowKind::Toolbar
            | WindowKind::Utility => return WindowClass::Transient,
            _ => {}
        }
        if fixed {
            WindowClass::Fixed
        } else {
            WindowClass::Main
        }
    }

    /// This class in `window_created`'s flags.
    pub fn flags(self) -> i32 {
        let n = match self {
            WindowClass::Main => WINDOW_CLASS_MAIN,
            WindowClass::Dialog => WINDOW_CLASS_DIALOG,
            WindowClass::Fixed => WINDOW_CLASS_FIXED,
            WindowClass::Transient => WINDOW_CLASS_TRANSIENT,
        };
        (n << WINDOW_CLASS_SHIFT) & WINDOW_CLASS_MASK
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn h() -> WindowHints {
        WindowHints {
            has_parent: false,
            kind: WindowKind::Normal,
            modal: false,
            min: (0, 0),
            max: (0, 0),
            pane: (500, 900),
        }
    }

    #[test]
    fn plain_window_is_main() {
        assert_eq!(WindowClass::of(&h()), WindowClass::Main);
    }

    #[test]
    fn unknown_type_is_main() {
        let mut x = h();
        x.kind = WindowKind::Unknown;
        assert_eq!(WindowClass::of(&x), WindowClass::Main);
    }

    #[test]
    fn large_max_stays_main() {
        let mut x = h();
        x.max = (2000, 2000);
        assert_eq!(WindowClass::of(&x), WindowClass::Main);
    }

    #[test]
    fn min_bigger_than_pane_stays_main() {
        let mut x = h();
        x.min = (800, 600);
        assert_eq!(WindowClass::of(&x), WindowClass::Main);
    }

    #[test]
    fn parent_is_dialog() {
        let mut x = h();
        x.has_parent = true;
        assert_eq!(WindowClass::of(&x), WindowClass::Dialog);
    }

    #[test]
    fn dialog_type_is_dialog() {
        let mut x = h();
        x.kind = WindowKind::Dialog;
        assert_eq!(WindowClass::of(&x), WindowClass::Dialog);
    }

    #[test]
    fn modal_is_dialog() {
        let mut x = h();
        x.modal = true;
        assert_eq!(WindowClass::of(&x), WindowClass::Dialog);
    }

    #[test]
    fn min_equals_max_is_fixed() {
        let mut x = h();
        x.min = (300, 200);
        x.max = (300, 200);
        assert_eq!(WindowClass::of(&x), WindowClass::Fixed);
    }

    #[test]
    fn max_smaller_than_pane_on_both_axes_is_fixed() {
        let mut x = h();
        x.max = (400, 700);
        assert_eq!(WindowClass::of(&x), WindowClass::Fixed);
    }

    #[test]
    fn max_smaller_on_one_axis_is_main() {
        let mut x = h();
        x.max = (400, 1200);
        assert_eq!(WindowClass::of(&x), WindowClass::Main);
    }

    #[test]
    fn splash_notification_toolbar_utility_are_transient() {
        for k in [
            WindowKind::Splash,
            WindowKind::Notification,
            WindowKind::Toolbar,
            WindowKind::Utility,
        ] {
            let mut x = h();
            x.kind = k;
            assert_eq!(WindowClass::of(&x), WindowClass::Transient);
        }
    }

    #[test]
    fn utility_with_parent_is_dialog() {
        let mut x = h();
        x.kind = WindowKind::Utility;
        x.has_parent = true;
        assert_eq!(WindowClass::of(&x), WindowClass::Dialog);
    }

    #[test]
    fn flags_use_bits_4_to_7() {
        assert_eq!(WindowClass::Main.flags(), 0);
        assert_eq!(WindowClass::Dialog.flags(), 0x10);
        assert_eq!(WindowClass::Fixed.flags(), 0x20);
        assert_eq!(WindowClass::Transient.flags(), 0x30);
    }
}
