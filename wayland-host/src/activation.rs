//! Which `xdg_activation_v1` tokens bring a window in front.
//!
//! On a phone one window is in front, so a Linux app taking it from another costs far less than
//! an app asking for its window and nothing happening. The rule is loose on purpose: a token
//! counts when it is fresh (made within `TOKEN_TTL`) and the client that uses it owns a Linux
//! window, or the token carried an input serial (`set_serial`, as a click or key gives).
//! Where the token was made does not matter: a second `firefox` launched from a terminal makes
//! its own, with no window of its own, and hands it to the running Firefox, which uses it.
//! A token works once. The rule is pure; `android/signals.rs` feeds it the clock and the window.

use std::time::Duration;

/// How long a token lives; an app passes it to its running instance within a moment.
pub const TOKEN_TTL: Duration = Duration::from_secs(10);

/// Whether a token made `age` ago still counts.
pub fn token_fresh(age: Duration) -> bool {
    age <= TOKEN_TTL
}

/// A token made `age` ago, used by a client that `owns_window` (a Linux window of its own),
/// carrying an input serial or not, is honoured.
pub fn token_valid(age: Duration, owns_window: bool, has_serial: bool) -> bool {
    token_fresh(age) && (owns_window || has_serial)
}

/// What an `xdg_activation_v1` request comes to.
#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum Verdict {
    /// The window comes in front.
    Raise,
    /// The window is not brought forward; it asks for the user instead (a glow on its rim and chip).
    Attention,
    /// Nothing: there is no Linux window to mark.
    Ignore,
}

/// A request with a token made `age` ago, for a window of the client that `owns_window`, with or
/// without an input serial: honoured as `Raise`; one the rule refuses still marks a window the
/// app owns (it asked for the user, and the launcher never raises on its own).
pub fn verdict(age: Duration, owns_window: bool, has_serial: bool) -> Verdict {
    if token_valid(age, owns_window, has_serial) {
        Verdict::Raise
    } else if owns_window {
        Verdict::Attention
    } else {
        Verdict::Ignore
    }
}

/// The urgency to tell the launcher when WM_HINTS now says `urgent` and it was last told `told`:
/// only a change is told.
pub fn urgency_change(told: bool, urgent: bool) -> Option<bool> {
    (told != urgent).then_some(urgent)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn a_stale_token_for_a_window_marks_it_instead_of_raising() {
        let old = TOKEN_TTL + Duration::from_secs(1);
        assert_eq!(verdict(old, true, true), Verdict::Attention);
        assert_eq!(verdict(old, true, false), Verdict::Attention);
    }

    #[test]
    fn an_honoured_token_raises_and_one_for_no_window_is_ignored() {
        assert_eq!(verdict(NOW, true, false), Verdict::Raise);
        assert_eq!(verdict(NOW, false, true), Verdict::Raise);
        assert_eq!(verdict(NOW, false, false), Verdict::Ignore);
        assert_eq!(verdict(TOKEN_TTL + NOW, false, true), Verdict::Ignore);
    }

    #[test]
    fn urgency_is_told_only_when_it_changes() {
        assert_eq!(urgency_change(false, true), Some(true));
        assert_eq!(urgency_change(true, false), Some(false));
        assert_eq!(urgency_change(true, true), None);
        assert_eq!(urgency_change(false, false), None);
    }

    const NOW: Duration = Duration::from_secs(1);

    #[test]
    fn a_fresh_token_used_by_a_client_with_a_window_is_honoured() {
        assert!(token_valid(NOW, true, false));
        assert!(token_valid(TOKEN_TTL, true, false));
    }

    #[test]
    fn a_fresh_token_with_an_input_serial_is_honoured_without_a_window() {
        assert!(token_valid(NOW, false, true));
    }

    #[test]
    fn a_client_with_no_window_and_no_serial_is_ignored() {
        assert!(!token_valid(NOW, false, false));
    }

    #[test]
    fn a_stale_token_is_ignored_whatever_it_carries() {
        let old = TOKEN_TTL + Duration::from_millis(1);
        assert!(!token_valid(old, true, true));
        assert!(!token_valid(old, true, false));
        assert!(!token_valid(old, false, true));
    }

    #[test]
    fn a_token_expires_after_ten_seconds() {
        assert!(token_fresh(Duration::from_secs(9)));
        assert!(!token_fresh(Duration::from_secs(11)));
    }
}
