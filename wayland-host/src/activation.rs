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

#[cfg(test)]
mod tests {
    use super::*;

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
