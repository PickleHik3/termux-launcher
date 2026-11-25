//! The idle inhibitors apps hold (`zwp_idle_inhibit_v1`).
//!
//! An inhibitor belongs to a surface, and a surface can hold several. The host keeps one entry per
//! inhibitor and asks, per window, whether any inhibitor lives on one of its surfaces
//! (`android/signals.rs`); the launcher turns that into the screen staying on while the window is
//! in front. The surface is the key, so a test can use plain numbers.

/// One entry per live inhibitor.
pub struct Inhibitors<K> {
    held: Vec<K>,
}

impl<K> Default for Inhibitors<K> {
    fn default() -> Self {
        Inhibitors { held: Vec::new() }
    }
}

impl<K: PartialEq> Inhibitors<K> {
    /// An inhibitor was created on `surface`.
    pub fn add(&mut self, surface: K) {
        self.held.push(surface);
    }

    /// One inhibitor of `surface` was destroyed; false when it had none.
    pub fn remove_one(&mut self, surface: &K) -> bool {
        match self.held.iter().position(|k| k == surface) {
            Some(i) => {
                self.held.swap_remove(i);
                true
            }
            None => false,
        }
    }

    /// Every inhibitor of `surface` ends (the surface is gone).
    pub fn remove_all(&mut self, surface: &K) {
        self.held.retain(|k| k != surface);
    }

    /// Whether any inhibitor's surface satisfies `f`.
    pub fn any(&self, mut f: impl FnMut(&K) -> bool) -> bool {
        self.held.iter().any(|k| f(k))
    }

    pub fn is_empty(&self) -> bool {
        self.held.is_empty()
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn a_surface_holds_as_long_as_one_of_its_inhibitors_lives() {
        let mut i = Inhibitors::default();
        assert!(!i.any(|k: &u32| *k == 1));
        i.add(1);
        i.add(1);
        assert!(i.any(|k| *k == 1));
        assert!(i.remove_one(&1));
        assert!(i.any(|k| *k == 1));
        assert!(i.remove_one(&1));
        assert!(!i.any(|k| *k == 1));
        assert!(i.is_empty());
    }

    #[test]
    fn removing_what_is_not_held_changes_nothing() {
        let mut i = Inhibitors::default();
        i.add(1);
        assert!(!i.remove_one(&2));
        assert!(i.any(|k| *k == 1));
    }

    #[test]
    fn a_gone_surface_drops_all_its_inhibitors_and_only_its_own() {
        let mut i = Inhibitors::default();
        i.add(1);
        i.add(1);
        i.add(2);
        i.remove_all(&1);
        assert!(!i.any(|k| *k == 1));
        assert!(i.any(|k| *k == 2));
    }
}
