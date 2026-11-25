//! What `wp_presentation` feedback says about a post.
//!
//! A window's frame is presented when the host has posted it to the window's surface: the time is
//! that post's (CLOCK_MONOTONIC, the clock the host tells clients about), the refresh interval is
//! the display's frame period when the launcher told one, else unknown (sent as 0). The post is a
//! CPU copy handed to the compositor, not a vsync, so no vsync, hardware-clock or zero-copy flag
//! is ever set. Feedback for a frame that is never posted is discarded
//! (`android/signals.rs`).

use std::time::Duration;

/// Shortest and longest frame period taken as the display's (a 1000 Hz and a 5 Hz display);
/// anything else is a bad number and counts as unknown.
const MIN_PERIOD: Duration = Duration::from_millis(1);
const MAX_PERIOD: Duration = Duration::from_millis(200);

/// The refresh interval to tell clients, None when the display's rate is not known.
pub fn refresh_interval(frame_period: Duration) -> Option<Duration> {
    (MIN_PERIOD..=MAX_PERIOD).contains(&frame_period).then_some(frame_period)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn a_display_rate_is_told_as_its_period() {
        assert_eq!(refresh_interval(Duration::from_nanos(16_666_667)), Some(Duration::from_nanos(16_666_667)));
        assert_eq!(refresh_interval(Duration::from_micros(8_333)), Some(Duration::from_micros(8_333)));
    }

    #[test]
    fn a_missing_or_absurd_rate_is_unknown() {
        assert_eq!(refresh_interval(Duration::ZERO), None);
        assert_eq!(refresh_interval(Duration::from_micros(10)), None);
        assert_eq!(refresh_interval(Duration::from_secs(5)), None);
    }
}
