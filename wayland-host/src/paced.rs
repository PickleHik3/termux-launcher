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

use std::time::{Duration, Instant};

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
    /// When the held buffer was committed: the start of its settle window.
    committed: Option<Instant>,
    /// The content hash last posted from each dmabuf (one entry per live buffer).
    posted: Vec<(B, u64)>,
    /// The held buffer's first read matched what it last posted (the client's GPU has not written
    /// it yet) and nothing has been posted for this commit.
    held_back: bool,
    /// The window of a held-back buffer ended unchanged: post it now.
    overdue: bool,
    /// The next first read posts whatever the buffer holds (a repaint of a blank Surface).
    forced: bool,
}

/// How long after its commit the held dmabuf is read again: the client's GPU may still be
/// writing it when the copy runs (no fence on KGSL), and it commits nothing more. Measured
/// late writes land up to about 30 ms after the commit.
pub const SETTLE_WINDOW: Duration = Duration::from_millis(60);

/// A dmabuf copy that may still change: the content hash of the last read that was posted.
struct Settle {
    active: bool,
    hash: u64,
}

impl<B: PartialEq + Clone, F> Paced<B, F> {
    pub fn new() -> Paced<B, F> {
        Paced {
            held: Held::new(),
            dirty: false,
            releases: Vec::new(),
            frames: Vec::new(),
            settle: Settle { active: false, hash: 0 },
            committed: None,
            posted: Vec::new(),
            held_back: false,
            overdue: false,
            forced: false,
        }
    }

    /// The held buffer's first read after its commit. `hash` is the content hash for a dmabuf,
    /// None for shm (complete at commit). True: copy and post it now. False: the dmabuf still
    /// holds what it last posted, so the client's GPU has not written it yet; post nothing, the
    /// window keeps the previous frame and the settle reads it on every tick, posting at the first
    /// change or, if the window ends unchanged, at its end.
    pub fn first_read(&mut self, hash: Option<u64>) -> bool {
        let forced = std::mem::take(&mut self.forced);
        self.overdue = false;
        let Some(hash) = hash else {
            self.settle = Settle { active: false, hash: 0 };
            self.held_back = false;
            return true;
        };
        let stale = !forced && self.posted_hash().is_some_and(|p| p == hash);
        self.held_back = stale;
        self.settle = Settle { active: true, hash };
        if !stale {
            self.record(hash);
        }
        !stale
    }

    fn posted_hash(&self) -> Option<u64> {
        let cur = self.held.current()?;
        self.posted.iter().find(|(b, _)| b == cur).map(|(_, h)| *h)
    }

    fn record(&mut self, hash: u64) {
        let Some(cur) = self.held.current() else { return };
        match self.posted.iter_mut().find(|(b, _)| b == cur) {
            Some(e) => e.1 = hash,
            None => self.posted.push((cur.clone(), hash)),
        }
    }

    /// The held buffer to read again now: a shown window whose last copy may still change, has no
    /// newer commit waiting and is within `SETTLE_WINDOW` of its commit. Past the window the
    /// settle is over.
    pub fn settle_due(&mut self, shown: bool, now: Instant) -> Option<B> {
        if !shown || self.dirty || !self.settle.active {
            return None;
        }
        let within = self.committed.is_some_and(|c| now.saturating_duration_since(c) < SETTLE_WINDOW);
        if !within {
            self.settle.active = false;
            if self.held_back {
                self.overdue = true;
                return self.held.current().cloned();
            }
            return None;
        }
        self.held.current().cloned()
    }

    /// The content hash of the buffer read by `settle_due`. True when it must be posted: it differs
    /// from the last posted one, or a held-back buffer's window ended unchanged. An unchanged read
    /// inside the window changes nothing: the write may still come.
    pub fn settled(&mut self, hash: Option<u64>) -> bool {
        let overdue = std::mem::take(&mut self.overdue);
        let post = match hash {
            Some(h) if h != self.settle.hash => {
                self.settle.hash = h;
                self.record(h);
                true
            }
            Some(_) => overdue,
            None => {
                self.settle.active = false;
                self.held_back
            }
        };
        if post {
            self.held_back = false;
        }
        post
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
        self.dirty || self.settle.active || !self.releases.is_empty() || !self.frames.is_empty()
    }

    /// Buffers or frame callbacks wait to be let go (a hidden window needs a keep-alive).
    pub fn has_waiting(&self) -> bool {
        !self.releases.is_empty() || !self.frames.is_empty()
    }

    /// The client committed `buffer`: hold it, and let the one it replaces wait for the tick.
    pub fn commit_buffer(&mut self, buffer: B) {
        self.commit_buffer_at(buffer, Instant::now());
    }

    /// `commit_buffer` with the commit's time.
    pub fn commit_buffer_at(&mut self, buffer: B, now: Instant) {
        self.committed = Some(now);
        if let Some(old) = self.held.attach(buffer) {
            self.releases.push(old);
        }
        self.dirty = true;
        self.settle.active = false;
        self.held_back = false;
        self.overdue = false;
    }

    /// The client attached no buffer: the window is unmapped. The held buffer waits for the tick.
    pub fn remove_buffer(&mut self) {
        if let Some(old) = self.held.remove() {
            self.releases.push(old);
        }
        self.dirty = false;
        self.settle.active = false;
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
        self.forced = self.dirty;
        self.dirty
    }

    /// The client destroyed `buffer`: it can no longer be read or released.
    pub fn forget(&mut self, buffer: &B) {
        self.held.forget(buffer);
        self.releases.retain(|b| b != buffer);
        self.posted.retain(|(b, _)| b != buffer);
        if self.held.current().is_none() {
            self.dirty = false;
            self.settle.active = false;
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
        self.settle.active = false;
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

    /// The host's tick for a dmabuf window at `now`: `content` is what the buffer holds when
    /// read. Returns what was copied this tick.
    fn dmabuf_tick(p: &mut Paced<u32, u32>, now: Instant, content: impl Fn(u32) -> u64) -> Option<(u32, u64)> {
        if let Some(b) = tick(p, true, false).0 {
            let h = content(b);
            return if p.first_read(Some(h)) { Some((b, h)) } else { None };
        }
        let b = p.settle_due(true, now)?;
        let h = content(b);
        if p.settled(Some(h)) { Some((b, h)) } else { None }
    }

    fn ms(t0: Instant, n: u64) -> Instant {
        t0 + Duration::from_millis(n)
    }

    #[test]
    fn a_dmabuf_that_changes_after_an_unchanged_read_is_copied_again() {
        let t0 = Instant::now();
        let mut p: Paced<u32, u32> = Paced::new();
        p.commit_buffer_at(1, t0);
        p.commit_buffer_at(2, t0);
        // The GPU starts late: the copy and two more reads see the old picture, then the new one.
        let content = |_b: u32| 0;
        assert_eq!(tick(&mut p, true, false).0, Some(2));
        assert!(p.first_read(Some(100)));
        let mut shown = Vec::new();
        for (t, h) in [(17u64, 100u64), (33, 100), (50, 200), (58, 200)] {
            if let Some(c) = dmabuf_tick(&mut p, ms(t0, t), |_| h) {
                shown.push(c);
            }
        }
        assert_eq!(shown, vec![(2, 200)]);
    }

    #[test]
    fn a_change_after_the_window_is_not_copied_and_idle_stays_idle() {
        let t0 = Instant::now();
        let mut p: Paced<u32, u32> = Paced::new();
        p.commit_buffer_at(1, t0);
        assert_eq!(dmabuf_tick(&mut p, t0, |_| 7), Some((1, 7)));
        assert!(p.has_work());
        assert_eq!(dmabuf_tick(&mut p, ms(t0, 20), |_| 7), None);
        assert!(p.has_work());
        assert_eq!(dmabuf_tick(&mut p, t0 + SETTLE_WINDOW, |_| 8), None);
        assert!(!p.has_work());
        assert_eq!(dmabuf_tick(&mut p, ms(t0, 80), |_| 9), None);
    }

    #[test]
    fn a_buffer_that_does_not_change_is_copied_exactly_once() {
        let t0 = Instant::now();
        let mut p: Paced<u32, u32> = Paced::new();
        p.commit_buffer_at(1, t0);
        let mut copies = 0;
        for n in 0..8 {
            if dmabuf_tick(&mut p, ms(t0, n * 10), |_| 7).is_some() {
                copies += 1;
            }
        }
        assert_eq!(copies, 1);
        assert!(!p.has_work());
    }

    #[test]
    fn a_newer_commit_ends_the_settle_of_the_old_buffer() {
        let t0 = Instant::now();
        let mut p: Paced<u32, u32> = Paced::new();
        p.commit_buffer_at(1, t0);
        assert_eq!(dmabuf_tick(&mut p, t0, |_| 1), Some((1, 1)));
        assert!(p.has_work());
        p.commit_buffer_at(2, ms(t0, 10));
        // The old buffer is not read again: the next tick copies the new one.
        assert_eq!(p.settle_due(true, ms(t0, 10)), None);
        assert_eq!(dmabuf_tick(&mut p, ms(t0, 17), |b| b as u64 * 10), Some((2, 20)));
        // The window now runs from the newer commit.
        assert_eq!(dmabuf_tick(&mut p, ms(t0, 65), |_| 21), Some((2, 21)));
    }

    #[test]
    fn a_buffer_that_keeps_changing_stops_at_the_window() {
        let t0 = Instant::now();
        let mut p: Paced<u32, u32> = Paced::new();
        p.commit_buffer_at(1, t0);
        let n = std::cell::Cell::new(0u64);
        let mut copies = 0;
        for k in 0..20 {
            if dmabuf_tick(&mut p, ms(t0, k * 10), |_| {
                n.set(n.get() + 1);
                n.get()
            })
            .is_some()
            {
                copies += 1;
            }
        }
        // The copy at 0 ms and a read each 10 ms up to 50 ms.
        assert_eq!(copies, 6);
        assert!(!p.has_work());
    }

    #[test]
    fn a_hidden_window_and_a_shm_copy_do_not_settle() {
        let mut p: Paced<u32, u32> = Paced::new();
        p.commit_buffer(1);
        tick(&mut p, true, false);
        assert!(p.first_read(None));
        assert_eq!(p.settle_due(true, Instant::now()), None);
        assert!(!p.has_work());
        assert!(p.first_read(Some(5)));
        assert_eq!(p.settle_due(false, Instant::now()), None);
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

    /// Commits `buffer` at `t` and runs the first tick, as the host does.
    fn commit_and_first(p: &mut Paced<u32, u32>, buffer: u32, t: Instant, h: u64) -> bool {
        p.commit_buffer_at(buffer, t);
        assert_eq!(tick(p, true, false).0, Some(buffer));
        p.first_read(Some(h))
    }

    #[test]
    fn a_buffer_not_written_yet_is_not_posted_until_it_changes() {
        let t0 = Instant::now();
        let mut p: Paced<u32, u32> = Paced::new();
        assert!(commit_and_first(&mut p, 1, t0, 10));
        // Buffer 2 is new: posted at once. Then buffer 1 comes back still holding picture 10.
        assert!(commit_and_first(&mut p, 2, ms(t0, 20), 20));
        assert!(!commit_and_first(&mut p, 1, ms(t0, 40), 10));
        assert!(p.has_work());
        assert_eq!(dmabuf_tick(&mut p, ms(t0, 51), |_| 10), None);
        // The GPU lands: posted once, at the change.
        assert_eq!(dmabuf_tick(&mut p, ms(t0, 62), |_| 30), Some((1, 30)));
        assert_eq!(dmabuf_tick(&mut p, ms(t0, 73), |_| 30), None);
    }

    #[test]
    fn a_buffer_that_never_changes_is_posted_at_the_end_of_the_window() {
        let t0 = Instant::now();
        let mut p: Paced<u32, u32> = Paced::new();
        assert!(commit_and_first(&mut p, 1, t0, 10));
        assert!(commit_and_first(&mut p, 2, ms(t0, 5), 20));
        assert!(!commit_and_first(&mut p, 1, ms(t0, 20), 10));
        assert_eq!(dmabuf_tick(&mut p, ms(t0, 50), |_| 10), None);
        assert_eq!(dmabuf_tick(&mut p, ms(t0, 20) + SETTLE_WINDOW, |_| 10), Some((1, 10)));
        assert!(!p.has_work());
        assert_eq!(dmabuf_tick(&mut p, ms(t0, 100), |_| 10), None);
    }

    #[test]
    fn a_repaint_is_posted_even_when_the_buffer_is_unchanged() {
        let t0 = Instant::now();
        let mut p: Paced<u32, u32> = Paced::new();
        assert!(commit_and_first(&mut p, 1, t0, 10));
        assert!(p.repaint());
        assert_eq!(tick(&mut p, true, false).0, Some(1));
        assert!(p.first_read(Some(10)));
    }

    #[test]
    fn a_newer_commit_supersedes_a_held_back_buffer() {
        let t0 = Instant::now();
        let mut p: Paced<u32, u32> = Paced::new();
        assert!(commit_and_first(&mut p, 1, t0, 10));
        assert!(commit_and_first(&mut p, 2, ms(t0, 5), 20));
        assert!(!commit_and_first(&mut p, 1, ms(t0, 20), 10));
        // Buffer 2 is committed again with new content before buffer 1 ever posts.
        assert!(commit_and_first(&mut p, 2, ms(t0, 30), 21));
        assert_eq!(p.settle_due(true, ms(t0, 40)), Some(2));
        // The window of buffer 1 is gone: no late post for it.
        assert!(!p.settled(Some(21)));
        assert_eq!(p.settle_due(true, ms(t0, 20) + SETTLE_WINDOW), Some(2));
        assert!(!p.settled(Some(21)));
    }

    #[test]
    fn a_destroyed_buffer_forgets_its_hash() {
        let t0 = Instant::now();
        let mut p: Paced<u32, u32> = Paced::new();
        assert!(commit_and_first(&mut p, 1, t0, 10));
        assert!(commit_and_first(&mut p, 2, ms(t0, 5), 20));
        p.forget(&1);
        // A new buffer with a recycled id and the same pixels posts at once.
        assert!(commit_and_first(&mut p, 1, ms(t0, 20), 10));
    }

    #[test]
    fn holding_a_post_still_releases_and_answers_frames_on_the_tick() {
        let t0 = Instant::now();
        let mut p: Paced<u32, u32> = Paced::new();
        assert!(commit_and_first(&mut p, 1, t0, 10));
        assert!(commit_and_first(&mut p, 2, ms(t0, 5), 20));
        p.commit_buffer_at(1, ms(t0, 20));
        let mut f = vec![7];
        p.add_frames(&mut f);
        let (copy, rel, fr) = tick(&mut p, true, false);
        assert_eq!((copy, rel, fr), (Some(1), vec![2], vec![7]));
        assert!(!p.first_read(Some(10)));
    }
}
