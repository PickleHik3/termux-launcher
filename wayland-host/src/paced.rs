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
    settle: Settle,
}

/// Ticks after a dmabuf copy on which the held buffer is read again: the client's GPU may not
/// have finished writing it when the copy ran (no fence on KGSL), and it commits nothing more.
pub const SETTLE_TICKS: u8 = 3;

/// A dmabuf copy that may still change: the checks left and the content hash of the last read.
struct Settle {
    left: u8,
    hash: u64,
}

impl<B: PartialEq + Clone, F> Paced<B, F> {
    pub fn new() -> Paced<B, F> {
        Paced { held: Held::new(), dirty: false, releases: Vec::new(), frames: Vec::new(), settle: Settle { left: 0, hash: 0 } }
    }

    /// The held buffer was just copied. `hash` is the content hash taken before the copy for a
    /// dmabuf, None for shm (complete at commit): a dmabuf is read again on the next ticks.
    pub fn copied(&mut self, hash: Option<u64>) {
        self.settle = match hash {
            Some(hash) => Settle { left: SETTLE_TICKS, hash },
            None => Settle { left: 0, hash: 0 },
        };
    }

    /// The held buffer to read again now: a shown window whose last copy may still change and
    /// has no newer commit waiting. Uses up one of the checks.
    pub fn settle_due(&mut self, shown: bool) -> Option<B> {
        if !shown || self.dirty || self.settle.left == 0 {
            return None;
        }
        self.settle.left -= 1;
        self.held.current().cloned()
    }

    /// The content hash of the buffer read by `settle_due`. True when it differs from the last
    /// copy: copy it again. An unchanged read ends the settle.
    pub fn settled(&mut self, hash: Option<u64>) -> bool {
        match hash {
            Some(hash) if hash != self.settle.hash => {
                self.settle.hash = hash;
                true
            }
            _ => {
                self.settle.left = 0;
                false
            }
        }
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
        self.dirty || self.settle.left > 0 || !self.releases.is_empty() || !self.frames.is_empty()
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
        self.settle.left = 0;
    }

    /// The client attached no buffer: the window is unmapped. The held buffer waits for the tick.
    pub fn remove_buffer(&mut self) {
        if let Some(old) = self.held.remove() {
            self.releases.push(old);
        }
        self.dirty = false;
        self.settle.left = 0;
    }

    /// A buffer another surface of this window let go (a subsurface or popup replaced or
    /// removed it): released after the next copy, like the window's own.
    pub fn release_later(&mut self, buffer: B) {
        self.releases.push(buffer);
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
            self.settle.left = 0;
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
        self.settle.left = 0;
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

    /// The host's tick for a dmabuf window: `content` is what the buffer holds when read.
    /// Returns what was copied this tick.
    fn dmabuf_tick(p: &mut Paced<u32, u32>, content: impl Fn(u32) -> u64) -> Option<(u32, u64)> {
        if let Some(b) = tick(p, true, false).0 {
            let h = content(b);
            p.copied(Some(h));
            return Some((b, h));
        }
        let b = p.settle_due(true)?;
        let h = content(b);
        if p.settled(Some(h)) { Some((b, h)) } else { None }
    }

    #[test]
    fn a_dmabuf_that_changes_after_the_copy_is_copied_again_until_it_settles() {
        let mut p: Paced<u32, u32> = Paced::new();
        p.commit_buffer(1);
        p.commit_buffer(2);
        // Buffer 2's GPU work finishes late: the first read is the old picture, then the new one.
        let reads = std::cell::Cell::new(0u64);
        let content = |_b: u32| {
            reads.set(reads.get() + 1);
            if reads.get() == 1 { 100 } else { 200 }
        };
        let mut shown = Vec::new();
        for _ in 0..6 {
            if let Some(c) = dmabuf_tick(&mut p, &content) {
                shown.push(c);
            }
        }
        assert_eq!(shown, vec![(2, 100), (2, 200)]);
        assert!(!p.has_work());
    }

    #[test]
    fn a_dmabuf_that_does_not_change_is_copied_exactly_once() {
        let mut p: Paced<u32, u32> = Paced::new();
        p.commit_buffer(1);
        let mut copies = 0;
        for _ in 0..6 {
            if dmabuf_tick(&mut p, |_| 7).is_some() {
                copies += 1;
            }
        }
        assert_eq!(copies, 1);
        assert!(!p.has_work());
    }

    #[test]
    fn a_newer_commit_ends_the_settle_of_the_old_buffer() {
        let mut p: Paced<u32, u32> = Paced::new();
        p.commit_buffer(1);
        assert_eq!(dmabuf_tick(&mut p, |_| 1), Some((1, 1)));
        assert!(p.has_work());
        p.commit_buffer(2);
        // The old buffer is not read again: the next tick copies the new one.
        assert_eq!(p.settle_due(true), None);
        assert_eq!(dmabuf_tick(&mut p, |b| b as u64 * 10), Some((2, 20)));
    }

    #[test]
    fn a_buffer_that_keeps_changing_is_copied_a_bounded_number_of_times() {
        let mut p: Paced<u32, u32> = Paced::new();
        p.commit_buffer(1);
        let n = std::cell::Cell::new(0u64);
        let mut copies = 0;
        for _ in 0..20 {
            if dmabuf_tick(&mut p, |_| {
                n.set(n.get() + 1);
                n.get()
            })
            .is_some()
            {
                copies += 1;
            }
        }
        assert_eq!(copies, 1 + SETTLE_TICKS as usize);
        assert!(!p.has_work());
    }

    #[test]
    fn a_hidden_window_and_a_shm_copy_do_not_settle() {
        let mut p: Paced<u32, u32> = Paced::new();
        p.commit_buffer(1);
        tick(&mut p, true, false);
        p.copied(None);
        assert_eq!(p.settle_due(true), None);
        assert!(!p.has_work());
        p.copied(Some(5));
        assert_eq!(p.settle_due(false), None);
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
