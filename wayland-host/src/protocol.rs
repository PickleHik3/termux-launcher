//! The numbers the launcher and the host share. The table is a mirror of the constants in
//! `app/src/main/aidl/com/termux/app/wayland/ILinuxWindows.aidl`, which is the source; a test on
//! each side (here, and `LinuxWindowsProtocolTest` in the app) fails when the two drift.
//!
//! Only the constants the host reads are mirrored; the host states and the interface version are
//! the service's alone.

/// Touch actions: `MotionEvent.getActionMasked()`.
pub const TOUCH_DOWN: i32 = 0;
pub const TOUCH_UP: i32 = 1;
pub const TOUCH_MOVE: i32 = 2;
pub const TOUCH_CANCEL: i32 = 3;
pub const TOUCH_POINTER_DOWN: i32 = 5;
pub const TOUCH_POINTER_UP: i32 = 6;

/// Pointer actions.
pub const POINTER_MOTION: i32 = 0;
pub const POINTER_BUTTON_DOWN: i32 = 1;
pub const POINTER_BUTTON_UP: i32 = 2;
pub const POINTER_SCROLL: i32 = 3;
pub const POINTER_LEAVE: i32 = 4;

/// Linux input button codes (BTN_LEFT, BTN_RIGHT, BTN_MIDDLE).
pub const BTN_LEFT: i32 = 272;
pub const BTN_RIGHT: i32 = 273;
pub const BTN_MIDDLE: i32 = 274;

/// Window flags.
pub const WINDOW_X11: i32 = 1;

/// Touch pointers one call carries; more are dropped.
pub const MAX_TOUCH_POINTERS: i32 = 10;

/// `(name, value)` of every constant above, for the drift test.
pub const TABLE: [(&str, i32); 16] = [
    ("TOUCH_DOWN", TOUCH_DOWN),
    ("TOUCH_UP", TOUCH_UP),
    ("TOUCH_MOVE", TOUCH_MOVE),
    ("TOUCH_CANCEL", TOUCH_CANCEL),
    ("TOUCH_POINTER_DOWN", TOUCH_POINTER_DOWN),
    ("TOUCH_POINTER_UP", TOUCH_POINTER_UP),
    ("POINTER_MOTION", POINTER_MOTION),
    ("POINTER_BUTTON_DOWN", POINTER_BUTTON_DOWN),
    ("POINTER_BUTTON_UP", POINTER_BUTTON_UP),
    ("POINTER_SCROLL", POINTER_SCROLL),
    ("POINTER_LEAVE", POINTER_LEAVE),
    ("BTN_LEFT", BTN_LEFT),
    ("BTN_RIGHT", BTN_RIGHT),
    ("BTN_MIDDLE", BTN_MIDDLE),
    ("WINDOW_X11", WINDOW_X11),
    ("MAX_TOUCH_POINTERS", MAX_TOUCH_POINTERS),
];

/// Prefixes of the AIDL constants the host must mirror; the rest (states, version) are Java's.
pub const MIRRORED_PREFIXES: [&str; 5] = ["TOUCH_", "POINTER_", "BTN_", "WINDOW_", "MAX_"];

#[cfg(test)]
mod tests {
    use super::*;

    const AIDL: &str = include_str!("../../app/src/main/aidl/com/termux/app/wayland/ILinuxWindows.aidl");

    /// `const int NAME = VALUE;` lines of the AIDL file.
    fn aidl_constants() -> Vec<(String, i32)> {
        AIDL.lines()
            .filter_map(|line| {
                let rest = line.trim().strip_prefix("const int ")?;
                let (name, value) = rest.split_once('=')?;
                let value = value.trim().strip_suffix(';')?.trim().parse().ok()?;
                Some((name.trim().to_string(), value))
            })
            .collect()
    }

    #[test]
    fn every_mirrored_constant_matches_the_aidl() {
        let aidl = aidl_constants();
        for (name, value) in TABLE {
            let found = aidl.iter().find(|(n, _)| n == name);
            assert_eq!(found.map(|(_, v)| *v), Some(value), "{name} differs from ILinuxWindows.aidl");
        }
    }

    #[test]
    fn every_aidl_constant_the_host_reads_is_mirrored() {
        for (name, value) in aidl_constants() {
            if MIRRORED_PREFIXES.iter().any(|p| name.starts_with(p)) {
                assert_eq!(TABLE.iter().find(|(n, _)| *n == name).map(|(_, v)| *v), Some(value), "{name} is not mirrored");
            }
        }
    }
}
