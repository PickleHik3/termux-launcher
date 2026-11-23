//! Android key codes (`KeyEvent.KEYCODE_*`) to Linux evdev codes (`KEY_*`), which wl_keyboard
//! carries. Covers letters, digits, punctuation, modifiers, arrows and the editing keys.

const LETTERS: [u32; 26] = [
    30, 48, 46, 32, 18, 33, 34, 35, 23, 36, 37, 38, 50, // A..M
    49, 24, 25, 16, 19, 31, 20, 22, 47, 17, 45, 21, 44, // N..Z
];

/// None for a key with no evdev counterpart here.
pub fn android_to_evdev(keycode: i32) -> Option<u32> {
    Some(match keycode {
        29..=54 => LETTERS[(keycode - 29) as usize], // KEYCODE_A..KEYCODE_Z
        7 => 11,                                     // 0
        8..=16 => (keycode - 8 + 2) as u32,          // 1..9 → KEY_1..KEY_9
        19 => 103,                                   // DPAD_UP → KEY_UP
        20 => 108,                                   // DPAD_DOWN
        21 => 105,                                   // DPAD_LEFT
        22 => 106,                                   // DPAD_RIGHT
        55 => 51,                                    // COMMA
        56 => 52,                                    // PERIOD → KEY_DOT
        57 => 56,                                    // ALT_LEFT
        58 => 100,                                   // ALT_RIGHT
        59 => 42,                                    // SHIFT_LEFT
        60 => 54,                                    // SHIFT_RIGHT
        61 => 15,                                    // TAB
        62 => 57,                                    // SPACE
        66 => 28,                                    // ENTER
        67 => 14,                                    // DEL → KEY_BACKSPACE
        68 => 41,                                    // GRAVE
        69 => 12,                                    // MINUS
        70 => 13,                                    // EQUALS → KEY_EQUAL
        71 => 26,                                    // LEFT_BRACKET → KEY_LEFTBRACE
        72 => 27,                                    // RIGHT_BRACKET
        73 => 43,                                    // BACKSLASH
        74 => 39,                                    // SEMICOLON
        75 => 40,                                    // APOSTROPHE
        76 => 53,                                    // SLASH
        92 => 104,                                   // PAGE_UP
        93 => 109,                                   // PAGE_DOWN
        111 => 1,                                    // ESCAPE
        112 => 111,                                  // FORWARD_DEL → KEY_DELETE
        113 => 29,                                   // CTRL_LEFT
        114 => 97,                                   // CTRL_RIGHT
        115 => 58,                                   // CAPS_LOCK
        117 => 125,                                  // META_LEFT
        118 => 126,                                  // META_RIGHT
        122 => 102,                                  // MOVE_HOME
        123 => 107,                                  // MOVE_END
        124 => 110,                                  // INSERT
        131..=140 => (keycode - 131 + 59) as u32,    // F1..F10
        141 => 87,                                   // F11
        142 => 88,                                   // F12
        _ => return None,
    })
}

#[cfg(test)]
mod tests {
    use super::android_to_evdev as map;

    #[test]
    fn letters() {
        assert_eq!(map(29), Some(30)); // A
        assert_eq!(map(45), Some(16)); // Q
        assert_eq!(map(54), Some(44)); // Z
        assert_eq!(map(41), Some(50)); // M
    }

    #[test]
    fn digits() {
        assert_eq!(map(7), Some(11)); // 0
        assert_eq!(map(8), Some(2)); // 1
        assert_eq!(map(16), Some(10)); // 9
    }

    #[test]
    fn editing_and_arrows() {
        assert_eq!(map(66), Some(28)); // Enter
        assert_eq!(map(67), Some(14)); // Backspace
        assert_eq!(map(61), Some(15)); // Tab
        assert_eq!(map(111), Some(1)); // Escape
        assert_eq!(map(19), Some(103));
        assert_eq!(map(22), Some(106));
    }

    #[test]
    fn modifiers() {
        assert_eq!(map(59), Some(42));
        assert_eq!(map(113), Some(29));
        assert_eq!(map(57), Some(56));
    }

    #[test]
    fn function_keys() {
        assert_eq!(map(131), Some(59));
        assert_eq!(map(140), Some(68));
        assert_eq!(map(142), Some(88));
    }

    #[test]
    fn unmapped() {
        assert_eq!(map(4), None); // BACK stays with Android
        assert_eq!(map(0), None);
        assert_eq!(map(-1), None);
    }

    #[test]
    fn no_two_keys_share_a_code() {
        let mut seen = std::collections::HashMap::new();
        for k in 0..300 {
            if let Some(e) = map(k) {
                assert!(seen.insert(e, k).is_none(), "evdev {e} from {k} and {:?}", seen.get(&e));
            }
        }
    }
}
