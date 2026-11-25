//! The text field a Linux app has focused, through `zwp_text_input_v3`: each text input's
//! double-buffered state, which window's field the launcher was told about, and the offsets the
//! launcher's UTF-16 numbers become in the protocol's UTF-8 bytes. Pure, so `cargo test` covers
//! it; `android::text_input` speaks the protocol and feeds it.

/// The longest text one `commit_string` or `preedit_string` carries: a Wayland message is at
/// most 4096 bytes, header and length included. Longer texts from the launcher are dropped.
pub const MAX_TEXT_BYTES: usize = 4000;

/// What a text input sends before its next `commit`; `commit` applies it whole.
#[derive(Debug, Default, Clone, PartialEq, Eq)]
pub struct Pending {
    /// `enable` (true) or `disable` (false) since the last commit.
    pub enable: Option<bool>,
    /// `set_content_type`: the hint bits and the purpose, raw protocol values.
    pub content: Option<(u32, u32)>,
    /// `set_surrounding_text`: the text around the cursor and the cursor's byte offset in it.
    pub surrounding: Option<(String, u32)>,
}

/// A text input's state as of its last `commit`.
#[derive(Debug, Default, Clone, PartialEq, Eq)]
pub struct Committed {
    pub enabled: bool,
    pub hint: u32,
    pub purpose: u32,
    /// The text around the cursor the app last told, and the cursor's byte offset in it; empty
    /// when it told none.
    pub surrounding: String,
    pub cursor: u32,
}

impl Committed {
    /// Applies one `commit`. Enabling or disabling starts from the protocol's initial state
    /// (no content type, no surrounding text) before the rest of the commit applies.
    pub fn apply(&mut self, pending: Pending) {
        if let Some(on) = pending.enable {
            *self = Committed { enabled: on, ..Committed::default() };
        }
        if let Some((hint, purpose)) = pending.content {
            self.hint = hint;
            self.purpose = purpose;
        }
        if let Some((text, cursor)) = pending.surrounding {
            self.cursor = cursor.min(text.len() as u32);
            self.surrounding = text;
        }
    }
}

/// The field the launcher is told about: the focused window's enabled text input.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub struct Field {
    pub window: i64,
    pub hint: u32,
    pub purpose: u32,
}

/// Which field the launcher was last told about, so it hears each change once.
#[derive(Debug, Default)]
pub struct FieldTracker {
    told: Option<Field>,
}

impl FieldTracker {
    /// The field as it stands now (None: no enabled text input on the focused surface). Returns
    /// the window whose field the launcher should hear left, then the field it should hear
    /// focused; both None when nothing changed. A new content type on the same window's field is
    /// told as focused again, without a left first. `renewed`: the app enabled its text input
    /// again in this commit (GTK does on a tap in a field that already has focus, to ask for the
    /// keyboard back), so the field is told focused again even unchanged.
    pub fn update(&mut self, now: Option<Field>, renewed: bool) -> (Option<i64>, Option<Field>) {
        if now == self.told && !(renewed && now.is_some()) {
            return (None, None);
        }
        let left = match (self.told, now) {
            (Some(old), Some(new)) if old.window == new.window => None,
            (Some(old), _) => Some(old.window),
            (None, _) => None,
        };
        self.told = now;
        (left, now)
    }

    /// The window whose field the launcher was last told focused.
    pub fn told(&self) -> Option<i64> {
        self.told.map(|f| f.window)
    }
}

/// Bytes of the `units` UTF-16 code units of `text` that end at byte `cursor`, for
/// `delete_surrounding_text`'s before length. With no surrounding text known each unit counts as
/// one byte, which is right for ASCII.
pub fn bytes_before(text: &str, cursor: u32, units: u32) -> u32 {
    if text.is_empty() {
        return units;
    }
    let end = floor_boundary(text, cursor as usize);
    let mut left = units as usize;
    let mut bytes = 0usize;
    for c in text[..end].chars().rev() {
        if left == 0 {
            break;
        }
        left = left.saturating_sub(c.len_utf16());
        bytes += c.len_utf8();
    }
    bytes as u32
}

/// Bytes of the `units` UTF-16 code units of `text` that start at byte `cursor`, for
/// `delete_surrounding_text`'s after length; ASCII-sized with no surrounding text, as above.
pub fn bytes_after(text: &str, cursor: u32, units: u32) -> u32 {
    if text.is_empty() {
        return units;
    }
    let start = floor_boundary(text, cursor as usize);
    let mut left = units as usize;
    let mut bytes = 0usize;
    for c in text[start..].chars() {
        if left == 0 {
            break;
        }
        left = left.saturating_sub(c.len_utf16());
        bytes += c.len_utf8();
    }
    bytes as u32
}

/// The byte offset in `text` of UTF-16 index `unit` (clamped to the text), for the preedit
/// cursor; -1 (no cursor shown) stays -1.
pub fn utf16_to_byte(text: &str, unit: i32) -> i32 {
    if unit < 0 {
        return -1;
    }
    let mut left = unit as usize;
    let mut bytes = 0usize;
    for c in text.chars() {
        if left == 0 {
            break;
        }
        left = left.saturating_sub(c.len_utf16());
        bytes += c.len_utf8();
    }
    bytes as i32
}

fn floor_boundary(text: &str, at: usize) -> usize {
    let mut at = at.min(text.len());
    while !text.is_char_boundary(at) {
        at -= 1;
    }
    at
}

#[cfg(test)]
mod tests {
    use super::*;

    fn enable() -> Pending {
        Pending { enable: Some(true), ..Pending::default() }
    }

    #[test]
    fn nothing_is_committed_before_commit() {
        let c = Committed::default();
        assert!(!c.enabled);
    }

    #[test]
    fn enable_with_content_type_in_one_commit_keeps_the_type() {
        let mut c = Committed::default();
        c.apply(Pending { enable: Some(true), content: Some((0x80, 8)), surrounding: None });
        assert!(c.enabled);
        assert_eq!((c.hint, c.purpose), (0x80, 8));
    }

    #[test]
    fn enable_again_resets_the_old_state() {
        let mut c = Committed::default();
        c.apply(Pending { enable: Some(true), content: Some((1, 2)), surrounding: Some(("ab".into(), 1)) });
        c.apply(enable());
        assert_eq!(c, Committed { enabled: true, ..Committed::default() });
    }

    #[test]
    fn disable_clears_everything() {
        let mut c = Committed::default();
        c.apply(Pending { enable: Some(true), content: Some((1, 2)), surrounding: None });
        c.apply(Pending { enable: Some(false), ..Pending::default() });
        assert_eq!(c, Committed::default());
    }

    #[test]
    fn a_commit_without_enable_keeps_the_enabled_state() {
        let mut c = Committed::default();
        c.apply(enable());
        c.apply(Pending { content: Some((4, 5)), ..Pending::default() });
        assert!(c.enabled);
        assert_eq!((c.hint, c.purpose), (4, 5));
    }

    #[test]
    fn a_cursor_past_the_text_is_clamped() {
        let mut c = Committed::default();
        c.apply(Pending { surrounding: Some(("abc".into(), 99)), ..Pending::default() });
        assert_eq!(c.cursor, 3);
    }

    fn field(window: i64, purpose: u32) -> Field {
        Field { window, hint: 0, purpose }
    }

    #[test]
    fn the_launcher_hears_a_field_once() {
        let mut t = FieldTracker::default();
        assert_eq!(t.update(Some(field(3, 0)), false), (None, Some(field(3, 0))));
        assert_eq!(t.update(Some(field(3, 0)), false), (None, None));
        assert_eq!(t.told(), Some(3));
    }

    #[test]
    fn enabling_again_tells_the_same_field_again() {
        let mut t = FieldTracker::default();
        t.update(Some(field(3, 0)), true);
        assert_eq!(t.update(Some(field(3, 0)), true), (None, Some(field(3, 0))));
        assert_eq!(t.update(None, true), (Some(3), None));
    }

    #[test]
    fn a_field_left_is_told_with_its_window() {
        let mut t = FieldTracker::default();
        t.update(Some(field(3, 0)), false);
        assert_eq!(t.update(None, false), (Some(3), None));
        assert_eq!(t.update(None, false), (None, None));
        assert_eq!(t.told(), None);
    }

    #[test]
    fn focus_moving_to_another_window_leaves_the_old_field_first() {
        let mut t = FieldTracker::default();
        t.update(Some(field(3, 0)), false);
        assert_eq!(t.update(Some(field(4, 0)), false), (Some(3), Some(field(4, 0))));
    }

    #[test]
    fn a_new_content_type_in_the_same_window_is_told_without_leaving() {
        let mut t = FieldTracker::default();
        t.update(Some(field(3, 0)), false);
        assert_eq!(t.update(Some(field(3, 8)), false), (None, Some(field(3, 8))));
    }

    #[test]
    fn utf16_units_become_utf8_bytes_before_the_cursor() {
        // "aé😀|b": é is 2 bytes / 1 unit, 😀 is 4 bytes / 2 units.
        let text = "aé😀b";
        let cursor = (1 + 2 + 4) as u32;
        assert_eq!(bytes_before(text, cursor, 2), 4);
        assert_eq!(bytes_before(text, cursor, 3), 6);
        assert_eq!(bytes_before(text, cursor, 99), 7);
        assert_eq!(bytes_after(text, cursor, 1), 1);
        assert_eq!(bytes_after(text, cursor, 5), 1);
    }

    #[test]
    fn without_surrounding_text_units_count_as_bytes() {
        assert_eq!(bytes_before("", 0, 3), 3);
        assert_eq!(bytes_after("", 0, 2), 2);
    }

    #[test]
    fn a_cursor_inside_a_character_rounds_down() {
        assert_eq!(bytes_before("é", 1, 1), 0);
        assert_eq!(bytes_after("é", 1, 1), 2);
    }

    #[test]
    fn preedit_cursor_is_a_byte_offset() {
        assert_eq!(utf16_to_byte("aé😀", 0), 0);
        assert_eq!(utf16_to_byte("aé😀", 2), 3);
        assert_eq!(utf16_to_byte("aé😀", 4), 7);
        assert_eq!(utf16_to_byte("aé😀", 9), 7);
        assert_eq!(utf16_to_byte("abc", -1), -1);
    }
}
