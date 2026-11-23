//! Things the compositor must let go of by hand when it stops, run once and last-in first-out.
//!
//! The event loop cannot be trusted to drop its sources: smithay's X11 window manager registers
//! a source whose closure holds a `LoopHandle`, an `Rc` cycle the loop never frees. A source
//! that only the loop owns (the listening socket, whose drop removes `wayland-0` and
//! `wayland-0.lock`; the display) would then outlive Stop and block the next Start. Each such
//! source registers its removal here and `State::shutdown` runs them.

#[derive(Default)]
pub struct Teardown {
    steps: Vec<Box<dyn FnOnce()>>,
}

impl Teardown {
    pub fn add(&mut self, step: impl FnOnce() + 'static) {
        self.steps.push(Box::new(step));
    }

    /// Runs every step added, the latest first. A second call finds nothing left.
    pub fn run(&mut self) {
        while let Some(step) = self.steps.pop() {
            step();
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::cell::RefCell;
    use std::rc::Rc;

    #[test]
    fn steps_run_latest_first_and_once() {
        let log = Rc::new(RefCell::new(Vec::new()));
        let mut t = Teardown::default();
        for n in 1..=3 {
            let log = log.clone();
            t.add(move || log.borrow_mut().push(n));
        }
        t.run();
        t.run();
        assert_eq!(*log.borrow(), vec![3, 2, 1]);
    }

    /// A value an Rc cycle would keep alive (as the loop keeps the listening socket) is
    /// released by a step that takes it out.
    #[test]
    fn a_step_frees_what_a_cycle_would_keep() {
        struct Guard(Rc<RefCell<bool>>);
        impl Drop for Guard {
            fn drop(&mut self) {
                *self.0.borrow_mut() = true;
            }
        }
        let dropped = Rc::new(RefCell::new(false));
        let slot: Rc<RefCell<Option<Guard>>> = Rc::new(RefCell::new(Some(Guard(dropped.clone()))));
        let cycle = slot.clone();
        let mut t = Teardown::default();
        t.add(move || drop(cycle.borrow_mut().take()));
        drop(slot);
        assert!(!*dropped.borrow());
        t.run();
        assert!(*dropped.borrow());
    }
}
