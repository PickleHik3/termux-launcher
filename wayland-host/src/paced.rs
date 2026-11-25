//! One window's frame pacing: what a commit leaves for the next tick, and what the tick does.
//!
//! A commit only records: the new buffer is held (the window is dirty), the buffer it replaces
//! and the commit's frame callbacks wait. On the tick a shown window copies its held buffer once
//! (however many commits came), and only then are the waiting buffers released and the frame
//! callbacks answered, so a client that draws on wl_buffer.release or ignores frame callbacks
//! still gets at most one round per tick. A hidden window copies nothing and keeps its dirty
//! buffer for when it is shown; its waiting buffers and callbacks are let go only on the slow
//! keep-alive, so a client of a hidden window draws about once per keep-alive.
//!
//! Generic over the buffer and callback types so the rules are testable without a Wayland
//! connection.

use std::time::Duration;

use crate::held::Held;

/// The frame tick when the launcher gives none: 60 Hz.
pub const DEFAULT_FRAME_PERIOD: Duration = Duration::from_nanos(16_666_667);
/// The tick is never faster than 240 Hz nor slower than 10 Hz, whatever the launcher says.
pub const MIN_FRAME_PERIOD: Duration = Duration::from_nanos(4_166_667);
pub const MAX_FRAME_PERIOD: Duration = Duration::from_millis(100);

/// The display's refresh period from Java as the host's frame tick.
pub fn frame_period(nanos: i64) -> Duration {
    if nanos <= 0 {
        return DEFAULT_FRAME_PERIOD;
    }
    Duration::from_nanos(nanos as u64).clamp(MIN_FRAME_PERIOD, MAX_FRAME_PERIOD)
}

pub struct Paced<B, F> {
    held: Held<B>,
    dirty: bool,
    releases: Vec<B>,
    frames: Vec<F>,
}

impl<B: PartialEq + Clone, F> Paced<B, F> {
    pub fn new() -> Paced<B, F> {
        Paced { held: Held::new(), dirty: false, releases: Vec::new(), frames: Vec::new() }
    }

    /// The buffer on screen (or to be put there), held until the client replaces it.
    pub fn current(&self) -> Option<&B> {
        self.held.current()
    }

    pub fn is_dirty(&self) -> bool {
        self.dirty
    }

    /// Something waits for a tick.
    pub fn has_work(&self) -> bool {
        self.dirty || !self.releases.is_empty() || !self.frames.is_empty()
    }

    /// Buffers or frame callbacks wait to be let go (a hidden window needs a keep-alive).
    pub fn has_waiting(&self) -> bool {
        !self.releases.is_empty() || !self.frames.is_empty()
    }

    /// The client committed `buffer`: hold it, and let the one it replaces wait for the tick.
    pub fn commit_buffer(&mut self, buffer: B) {
        if let Some(old) = self.held.attach(buffer) {
            self.releases.push(old);
        }
        self.dirty = true;
    }

    /// The client attached no buffer: the window is unmapped. The held buffer waits for the tick.
    pub fn remove_buffer(&mut self) {
        if let Some(old) = self.held.remove() {
            self.releases.push(old);
        }
        self.dirty = false;
    }

    /// A buffer another surface of this window let go (a subsurface or popup replaced or
    /// removed it): released after the next copy, like the window's own.
    pub fn release_later(&mut self, buffer: B) {
        self.releases.push(buffer);
    }

    /// STALE296 diag: `buffer` waits for its release (replaced, not yet given back).
    pub fn waits_release(&self, buffer: &B) -> bool {
        self.releases.contains(buffer)
    }

    /// The commit's frame callbacks wait for the tick (moved out of `frames`).
    pub fn add_frames(&mut self, frames: &mut Vec<F>) {
        self.frames.append(frames);
    }

    /// Asks for the held buffer to be copied again on the next shown tick (a new or resized
    /// Surface is blank). False when nothing is held.
    pub fn repaint(&mut self) -> bool {
        self.dirty = self.held.current().is_some();
        self.dirty
    }

    /// The client destroyed `buffer`: it can no longer be read or released.
    pub fn forget(&mut self, buffer: &B) {
        self.held.forget(buffer);
        self.releases.retain(|b| b != buffer);
        if self.held.current().is_none() {
            self.dirty = false;
        }
    }

    /// One tick. Returns the buffer to copy now (shown and dirty). Buffers to release and frame
    /// callbacks to answer go to `releases` and `frames`; the caller does both after the copy.
    /// A hidden window gives them up only when `keepalive` is due, and keeps its dirty buffer.
    pub fn tick(&mut self, shown: bool, keepalive: bool, releases: &mut Vec<B>, frames: &mut Vec<F>) -> Option<B> {
        let copy = if shown && self.dirty {
            self.dirty = false;
            self.held.current().cloned()
        } else {
            None
        };
        if shown || keepalive {
            releases.append(&mut self.releases);
            frames.append(&mut self.frames);
        }
        copy
    }

    /// The window is going: everything it holds is let go.
    pub fn take_all(&mut self, releases: &mut Vec<B>, frames: &mut Vec<F>) {
        if let Some(b) = self.held.remove() {
            releases.push(b);
        }
        releases.append(&mut self.releases);
        frames.append(&mut self.frames);
        self.dirty = false;
    }
}

impl<B: PartialEq + Clone, F> Default for Paced<B, F> {
    fn default() -> Paced<B, F> {
        Paced::new()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn the_frame_period_follows_the_display_within_bounds() {
        assert_eq!(frame_period(0), DEFAULT_FRAME_PERIOD);
        assert_eq!(frame_period(-1), DEFAULT_FRAME_PERIOD);
        assert_eq!(frame_period(8_333_333), Duration::from_nanos(8_333_333));
        assert_eq!(frame_period(1), MIN_FRAME_PERIOD);
        assert_eq!(frame_period(i64::MAX), MAX_FRAME_PERIOD);
    }

    fn tick(p: &mut Paced<u32, u32>, shown: bool, keepalive: bool) -> (Option<u32>, Vec<u32>, Vec<u32>) {
        let (mut r, mut f) = (Vec::new(), Vec::new());
        let copy = p.tick(shown, keepalive, &mut r, &mut f);
        (copy, r, f)
    }

    #[test]
    fn many_commits_in_one_tick_copy_once_and_release_after() {
        let mut p = Paced::new();
        p.commit_buffer(1);
        p.add_frames(&mut vec![10]);
        p.commit_buffer(2);
        p.add_frames(&mut vec![11]);
        p.commit_buffer(3);
        // Nothing is released before the tick: a release-driven client waits for it.
        let (copy, releases, frames) = tick(&mut p, true, false);
        assert_eq!(copy, Some(3));
        assert_eq!(releases, vec![1, 2]);
        assert_eq!(frames, vec![10, 11]);
        // No new commit, no second copy.
        assert_eq!(tick(&mut p, true, false), (None, vec![], vec![]));
        assert_eq!(p.current(), Some(&3));
    }

    #[test]
    fn a_hidden_window_copies_nothing_and_waits_for_the_keepalive() {
        let mut p = Paced::new();
        p.commit_buffer(1);
        p.commit_buffer(2);
        p.add_frames(&mut vec![10]);
        assert_eq!(tick(&mut p, false, false), (None, vec![], vec![]));
        assert!(p.has_waiting());
        assert_eq!(tick(&mut p, false, true), (None, vec![1], vec![10]));
        // The last frame stays dirty, so showing the window paints it at once.
        assert!(p.is_dirty());
        assert_eq!(tick(&mut p, true, false).0, Some(2));
    }

    #[test]
    fn recommitting_the_held_buffer_releases_nothing_but_copies_again() {
        let mut p: Paced<u32, u32> = Paced::new();
        p.commit_buffer(1);
        tick(&mut p, true, false);
        p.commit_buffer(1);
        assert_eq!(tick(&mut p, true, false), (Some(1), vec![], vec![]));
    }

    #[test]
    fn repaint_needs_a_held_buffer() {
        let mut p: Paced<u32, u32> = Paced::new();
        assert!(!p.repaint());
        p.commit_buffer(1);
        tick(&mut p, true, false);
        assert!(p.repaint());
        assert_eq!(tick(&mut p, true, false).0, Some(1));
    }

    #[test]
    fn unmap_lets_the_held_buffer_go_on_the_tick() {
        let mut p: Paced<u32, u32> = Paced::new();
        p.commit_buffer(1);
        p.remove_buffer();
        assert_eq!(p.current(), None);
        assert_eq!(tick(&mut p, true, false), (None, vec![1], vec![]));
    }

    #[test]
    fn a_destroyed_buffer_is_neither_copied_nor_released() {
        let mut p: Paced<u32, u32> = Paced::new();
        p.commit_buffer(1);
        p.commit_buffer(2);
        p.forget(&1);
        p.forget(&2);
        assert_eq!(tick(&mut p, true, false), (None, vec![], vec![]));
    }

    #[test]
    fn a_subsurface_buffer_waits_for_the_copy_like_the_root() {
        let mut p: Paced<u32, u32> = Paced::new();
        p.commit_buffer(1);
        tick(&mut p, true, false);
        // A subsurface replaced buffer 7 and the window is marked for a new copy.
        p.release_later(7);
        assert!(p.repaint());
        assert_eq!(tick(&mut p, true, false), (Some(1), vec![7], vec![]));
        // Hidden: it waits for the keep-alive.
        p.release_later(8);
        assert_eq!(tick(&mut p, false, false), (None, vec![], vec![]));
        assert_eq!(tick(&mut p, false, true), (None, vec![8], vec![]));
    }

    #[test]
    fn take_all_hands_back_everything() {
        let mut p = Paced::new();
        p.commit_buffer(1);
        p.commit_buffer(2);
        p.add_frames(&mut vec![10]);
        let (mut r, mut f) = (Vec::new(), Vec::new());
        p.take_all(&mut r, &mut f);
        assert_eq!((r, f), (vec![2, 1], vec![10]));
        assert!(!p.has_work());
    }
}
