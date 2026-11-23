//! The buffer a toplevel shows now, held unreleased until the client replaces it.
//!
//! Holding the current wl_buffer (as Weston's pixman renderer does) lets the host repaint a
//! fresh Surface at once, from the client's own pixels, without a host-side copy. The protocol
//! allows it: a client must not touch a buffer until wl_buffer.release.

/// Generic over the buffer type so the rules are testable without a Wayland connection.
pub struct Held<T> {
    current: Option<T>,
}

impl<T: PartialEq> Held<T> {
    pub const fn new() -> Held<T> {
        Held { current: None }
    }

    pub fn current(&self) -> Option<&T> {
        self.current.as_ref()
    }

    /// The client committed `new`. Returns the buffer to release now: the one it replaces,
    /// unless the client re-committed the very buffer already held.
    pub fn attach(&mut self, new: T) -> Option<T> {
        if self.current.as_ref() == Some(&new) {
            return None;
        }
        self.current.replace(new)
    }

    /// The client attached no buffer (the window is unmapped), or the toplevel is going.
    /// Returns the buffer to release.
    pub fn remove(&mut self) -> Option<T> {
        self.current.take()
    }

    /// The client destroyed `buffer`; it can no longer be read or released.
    pub fn forget(&mut self, buffer: &T) {
        if self.current.as_ref() == Some(buffer) {
            self.current = None;
        }
    }
}

impl<T: PartialEq> Default for Held<T> {
    fn default() -> Held<T> {
        Held::new()
    }
}

#[cfg(test)]
mod tests {
    use super::Held;

    #[test]
    fn first_attach_releases_nothing_and_holds_it() {
        let mut h = Held::new();
        assert_eq!(h.attach(1), None);
        assert_eq!(h.current(), Some(&1));
    }

    #[test]
    fn a_new_buffer_releases_the_previous_one() {
        let mut h = Held::new();
        h.attach(1);
        assert_eq!(h.attach(2), Some(1));
        assert_eq!(h.current(), Some(&2));
    }

    #[test]
    fn recommitting_the_held_buffer_releases_nothing() {
        let mut h = Held::new();
        h.attach(1);
        assert_eq!(h.attach(1), None);
        assert_eq!(h.current(), Some(&1));
    }

    #[test]
    fn remove_hands_back_the_held_buffer_once() {
        let mut h = Held::new();
        h.attach(1);
        assert_eq!(h.remove(), Some(1));
        assert_eq!(h.remove(), None);
        assert_eq!(h.current(), None);
    }

    #[test]
    fn a_destroyed_buffer_is_forgotten_not_released() {
        let mut h = Held::new();
        h.attach(1);
        h.forget(&2);
        assert_eq!(h.current(), Some(&1));
        h.forget(&1);
        assert_eq!(h.current(), None);
        assert_eq!(h.attach(3), None);
    }
}
