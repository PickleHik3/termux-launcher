//! Which `xdg_activation_v1` tokens bring a window in front.
//!
//! The host hands a token to any client that asks, but acts on it only when the client that
//! asked was one the user was just using: it holds keyboard focus now, or took focus or input in
//! the last `GRACE`. A token also expires `TOKEN_TTL` after it was made and works once. The
//! rule is pure; `android/signals.rs` feeds it the clock and the focus.

use std::time::{Duration, Instant};

/// How long after its last focus or input a client may still get an activation token.
pub const GRACE: Duration = Duration::from_secs(5);
/// How long a token lives; an app passes it to its running instance within a moment.
pub const TOKEN_TTL: Duration = Duration::from_secs(15);

/// Most clients remembered; the least recent goes first.
const MAX_RECENT: usize = 8;

/// A token made by a client that `focused` (holds the keyboard now) or was last active
/// `since_active` ago is honoured.
pub fn token_valid(focused: bool, since_active: Option<Duration>) -> bool {
    focused || since_active.map_or(false, |d| d <= GRACE)
}

/// Whether a token made `age` ago is still good.
pub fn token_fresh(age: Duration) -> bool {
    age <= TOKEN_TTL
}

/// The clients that last had focus or input, with when.
pub struct Recent<K> {
    seen: Vec<(K, Instant)>,
}

impl<K> Default for Recent<K> {
    fn default() -> Self {
        Recent { seen: Vec::new() }
    }
}

impl<K: PartialEq> Recent<K> {
    /// `who` took focus, lost it, or got input at `now`.
    pub fn note(&mut self, who: K, now: Instant) {
        if let Some(entry) = self.seen.iter_mut().find(|(k, _)| *k == who) {
            entry.1 = now;
            return;
        }
        if self.seen.len() == MAX_RECENT {
            let oldest = (0..self.seen.len()).min_by_key(|&i| self.seen[i].1).unwrap_or(0);
            self.seen.swap_remove(oldest);
        }
        self.seen.push((who, now));
    }

    /// How long ago `who` was last noted, None when it never was (or was forgotten).
    pub fn since(&self, who: &K, now: Instant) -> Option<Duration> {
        self.seen.iter().find(|(k, _)| k == who).map(|(_, at)| now.saturating_duration_since(*at))
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn a_focused_client_is_honoured_however_long_ago_it_typed() {
        assert!(token_valid(true, None));
        assert!(token_valid(true, Some(Duration::from_secs(600))));
    }

    #[test]
    fn a_client_without_focus_is_honoured_only_within_the_grace() {
        assert!(token_valid(false, Some(Duration::from_secs(1))));
        assert!(token_valid(false, Some(GRACE)));
        assert!(!token_valid(false, Some(GRACE + Duration::from_millis(1))));
        assert!(!token_valid(false, None));
    }

    #[test]
    fn a_token_expires() {
        assert!(token_fresh(Duration::from_secs(1)));
        assert!(!token_fresh(TOKEN_TTL + Duration::from_millis(1)));
    }

    #[test]
    fn recent_remembers_when_a_client_was_last_seen() {
        let t0 = Instant::now();
        let mut r = Recent::default();
        assert_eq!(r.since(&1, t0), None);
        r.note(1, t0);
        r.note(2, t0 + Duration::from_secs(1));
        assert_eq!(r.since(&1, t0 + Duration::from_secs(3)), Some(Duration::from_secs(3)));
        r.note(1, t0 + Duration::from_secs(4));
        assert_eq!(r.since(&1, t0 + Duration::from_secs(5)), Some(Duration::from_secs(1)));
    }

    #[test]
    fn recent_forgets_the_least_recent_when_full() {
        let t0 = Instant::now();
        let mut r = Recent::default();
        for k in 0..MAX_RECENT as u32 {
            r.note(k, t0 + Duration::from_secs(k as u64));
        }
        r.note(100, t0 + Duration::from_secs(50));
        assert_eq!(r.since(&0, t0 + Duration::from_secs(60)), None);
        assert!(r.since(&1, t0 + Duration::from_secs(60)).is_some());
        assert!(r.since(&100, t0 + Duration::from_secs(60)).is_some());
    }
}
