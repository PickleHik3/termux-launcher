//! The store's motion (D7): one [`Timeline`] behind the router's [`Motion`] hook.
//!
//! A navigation plays in two parts. **Leave** (120 ms): the old view's body fades toward the
//! page; its body pictures are gone at once. **Enter**: body element i fades up over 200 ms
//! starting at min(30·i, 100) ms, so everything is at rest by 300 ms. The header, notice and
//! keys never move; the header text swaps with its item, and the header picture is placed by
//! the view once its item has rested under the cursor (`Store::header_rested`). Inside a
//! screen, the install number counts up to each new value. Input is never dropped: the
//! router hands every event to the current view the frame it arrives, during the leave too.

use std::time::{Duration, Instant};

use super::scene::{Effect, El, Fx, Motion, NavKind, Phase, Scene};

/// Easing curves, each mapping 0–1 to 0–1.
pub mod ease {
    /// `cubic-bezier(.16,1,.3,1)`: a snappy deceleration.
    pub fn out(t: f32) -> f32 {
        bezier(0.16, 1.0, 0.3, 1.0, t)
    }

    /// CSS `cubic-bezier(x1,y1,x2,y2)` at progress `t` (solve x(s) = t, return y(s)).
    pub fn bezier(x1: f32, y1: f32, x2: f32, y2: f32, t: f32) -> f32 {
        let t = t.clamp(0.0, 1.0);
        if t == 0.0 || t == 1.0 {
            return t;
        }
        let (x1, y1, x2, y2) = (x1 as f64, y1 as f64, x2 as f64, y2 as f64);
        let coord = |a: f64, b: f64, s: f64| {
            let u = 1.0 - s;
            3.0 * u * u * s * a + 3.0 * u * s * s * b + s * s * s
        };
        let slope = |a: f64, b: f64, s: f64| {
            let u = 1.0 - s;
            3.0 * u * u * a + 6.0 * u * s * (b - a) + 3.0 * s * s * (1.0 - b)
        };
        let target = t as f64;
        // Newton first, bisection when the slope is too flat.
        let mut s = target;
        for _ in 0..8 {
            let err = coord(x1, x2, s) - target;
            if err.abs() < 1e-7 {
                return coord(y1, y2, s) as f32;
            }
            let d = slope(x1, x2, s);
            if d.abs() < 1e-6 {
                break;
            }
            s = (s - err / d).clamp(0.0, 1.0);
        }
        let (mut lo, mut hi) = (0.0f64, 1.0f64);
        s = target;
        for _ in 0..60 {
            let x = coord(x1, x2, s);
            if (x - target).abs() < 1e-7 {
                break;
            }
            if x < target {
                lo = s;
            } else {
                hi = s;
            }
            s = (lo + hi) / 2.0;
        }
        coord(y1, y2, s) as f32
    }
}

/// D7's timing, in milliseconds.
pub mod timing {
    /// The old view's body fades out for this long before the new one starts.
    pub const LEAVE: f32 = 120.0;
    /// Each body element fades up over this long…
    pub const ARRIVE: f32 = 200.0;
    /// …starting `STAGGER` ms after the one before, at most `STAGGER_MAX` after the first.
    pub const STAGGER: f32 = 30.0;
    pub const STAGGER_MAX: f32 = 100.0;
    /// Everything is at rest by then.
    pub const ENTER: f32 = STAGGER_MAX + ARRIVE;
    /// The count-up: this long plus a little per point, at most `COUNT_MAX`.
    pub const COUNT_MIN: f32 = 200.0;
    pub const COUNT_PER: f32 = 6.0;
    pub const COUNT_MAX: f32 = 700.0;
}

use timing::*;

/// Progress 0–1 of something that starts `at` ms and lasts `dur` ms, at `t` ms.
fn prog(t: f32, at: f32, dur: f32) -> f32 {
    if dur <= 0.0 {
        return if t >= at { 1.0 } else { 0.0 };
    }
    ((t - at) / dur).clamp(0.0, 1.0)
}

fn ms(d: Duration) -> f32 {
    d.as_secs_f32() * 1000.0
}

/// Text fading: alpha is stepped to sixteenths, so a fade does not rewrite every cell of the
/// text on every frame.
fn alpha_steps(a: f32) -> f32 {
    (a.clamp(0.0, 1.0) * 16.0).round() / 16.0
}

fn set(fx: &mut Fx, el: El, e: Effect) {
    if !e.at_rest() {
        fx.set(el, e);
    }
}

/// Body elements a first, not-yet-drawn frame hides without knowing how many exist.
const HIDE: u16 = 64;

struct Transition {
    start: Instant,
    /// False for the very first view (nothing to leave).
    leave: bool,
    /// The view being left, as last drawn.
    from: Scene,
    /// The entering view has been drawn once (its layout is known).
    seen: bool,
}

struct Count {
    start: Instant,
    from: f32,
    to: f32,
    dur: f32,
}

/// The store's motion. See the module notes for the sequence.
#[derive(Default)]
pub struct Timeline {
    tr: Option<Transition>,
    count: Option<Count>,
    /// The install number as last shown (None off the installing screen).
    shown: Option<f32>,
    /// A view has been drawn or navigated to (the first one plays the entry once).
    started: bool,
}

impl Timeline {
    pub fn new() -> Timeline {
        Timeline::default()
    }

    /// When body element `i` starts to arrive.
    pub fn arrive_at(i: usize) -> f32 {
        (STAGGER * i as f32).min(STAGGER_MAX)
    }

    /// The leave: the body fades; body pictures are simply gone.
    fn leave_fx(t: f32, from: &Scene) -> Fx {
        let mut fx = Fx::default();
        let a = alpha_steps(1.0 - ease::out(prog(t, 0.0, LEAVE)));
        for e in from.body() {
            let eff = if e.picture.is_some() { Effect::hidden() } else { Effect::alpha(a) };
            set(&mut fx, e.el, eff);
        }
        fx
    }

    /// The entry at `e` ms after it began. `cur` is the entering view's scene (empty on its
    /// first frame: then everything that arrives is simply hidden).
    fn enter_fx(e: f32, cur: &Scene) -> Fx {
        let mut fx = Fx::default();
        if cur.elements.is_empty() {
            for i in 0..HIDE {
                fx.set(El::Row(i), Effect::hidden());
                fx.set(El::Block(i), Effect::hidden());
            }
            fx.set(El::Pill, Effect::hidden());
            fx.set(El::Pager, Effect::hidden());
            return fx;
        }
        for (i, el) in cur.body().enumerate() {
            let a = alpha_steps(ease::out(prog(e, Self::arrive_at(i), ARRIVE)));
            set(&mut fx, el.el, Effect::alpha(a));
        }
        fx
    }

    fn count_value(c: &Count, now: Instant) -> f32 {
        let p = prog(ms(now.saturating_duration_since(c.start)), 0.0, c.dur);
        c.from + (c.to - c.from) * ease::out(p)
    }
}

impl Motion for Timeline {
    fn navigate(&mut self, _kind: NavKind, from: &Scene, _to: &'static str, now: Instant) {
        // Every kind plays the same sequence. A navigation during a transition simply starts
        // over from the view as it was last drawn.
        self.tr = Some(Transition { start: now, leave: true, from: from.clone(), seen: false });
        self.count = None;
        self.shown = None;
        self.started = true;
    }

    fn frame(&mut self, now: Instant, current: &Scene) -> Phase {
        let mut fx = Fx::default();
        let mut entering = false;
        if let Some(tr) = &self.tr {
            let t = ms(now.saturating_duration_since(tr.start));
            if tr.leave && t < LEAVE {
                return Phase::Leaving(Self::leave_fx(t, &tr.from));
            }
            let e = if tr.leave { t - LEAVE } else { t };
            if e >= ENTER {
                self.tr = None;
            } else {
                fx = Self::enter_fx(e, current);
                entering = true;
            }
        }
        if let Some(c) = &self.count {
            let v = Self::count_value(c, now);
            self.shown = Some(v);
            let mut eff = fx.get(El::Block(0));
            eff.value = Some(v);
            fx.set(El::Block(0), eff);
            entering = true;
            if ms(now.saturating_duration_since(c.start)) >= c.dur {
                self.shown = Some(c.to);
                self.count = None;
            }
        }
        if entering {
            Phase::Entering(fx)
        } else {
            Phase::Idle
        }
    }

    fn active(&self) -> bool {
        self.tr.is_some() || self.count.is_some()
    }

    fn drawn(&mut self, now: Instant, current: &Scene) -> bool {
        if current.elements.is_empty() {
            // A leaving view was drawn.
            return false;
        }
        let mut redraw = false;

        // The very first view enters too (without a leave).
        if !self.started {
            self.started = true;
            self.tr = Some(Transition { start: now, leave: false, from: Scene::default(), seen: true });
            redraw = true;
        }
        // First look at an entering view: draw it again now that its layout is known.
        if let Some(tr) = self.tr.as_mut().filter(|tr| !tr.seen) {
            tr.seen = true;
            redraw = true;
        }

        // The install number heads somewhere new: count to it from what is shown.
        if current.screen == "installing" {
            let target =
                current.get(El::Block(0)).and_then(|b| b.text.as_deref()).and_then(|t| t.parse::<f32>().ok());
            if let Some(to) = target {
                let heading = self.count.as_ref().map(|c| c.to).or(self.shown);
                if heading != Some(to) {
                    let from = match &self.count {
                        Some(c) => Self::count_value(c, now),
                        None => self.shown.unwrap_or(0.0),
                    };
                    let dur = (COUNT_MIN + COUNT_PER * (to - from).abs()).min(COUNT_MAX);
                    self.count = Some(Count { start: now, from, to, dur });
                    redraw = true;
                }
            }
        } else {
            self.shown = None;
            self.count = None;
        }
        redraw
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::render::Rect;

    fn samples(f: fn(f32) -> f32) -> Vec<f32> {
        (0..=200).map(|i| f(i as f32 / 200.0)).collect()
    }

    #[test]
    fn out_easing_hits_its_ends_and_never_turns_back() {
        assert_eq!(ease::out(0.0), 0.0);
        assert_eq!(ease::out(1.0), 1.0);
        assert_eq!(ease::out(-1.0), 0.0);
        assert_eq!(ease::out(2.0), 1.0);
        let s = samples(ease::out);
        assert!(s.windows(2).all(|w| w[1] >= w[0] - 1e-6), "monotonic");
        assert!(ease::out(0.1) > 0.4 && ease::out(0.3) > 0.85, "{} {}", ease::out(0.1), ease::out(0.3));
        assert!((ease::out(0.1) - 0.49439).abs() < 1e-4, "{}", ease::out(0.1));
        assert!((ease::bezier(0.0, 0.0, 1.0, 1.0, 0.37) - 0.37).abs() < 1e-4);
    }

    fn front(rows: u16) -> Scene {
        let mut s = Scene::new("front");
        s.cell = (8, 20);
        s.push(El::Mark, Rect::new(2, 0, 7, 1), None, None);
        s.push(El::Name, Rect::new(2, 11, 20, 3), None, Some("dawn"));
        s.push(El::Pill, Rect::new(1, 17, 51, 1), Some((9, 1)), None);
        for i in 0..rows {
            s.push(El::Row(i), Rect::new(2, 17 + i, 49, 1), None, Some(&format!("{i:02}")));
        }
        s.push(El::Keys, Rect::new(2, 25, 49, 1), None, None);
        s
    }

    #[test]
    fn leave_fades_the_body_only_and_hides_its_pictures() {
        let t0 = Instant::now();
        let at = |ms: u64| t0 + Duration::from_millis(ms);
        let mut m = Timeline::new();
        let from = front(5);
        m.navigate(NavKind::Push, &from, "item", t0);
        assert!(m.active());
        let Phase::Leaving(fx) = m.frame(at(0), &Scene::new("item")) else { panic!("leaving") };
        assert_eq!(fx.get(El::Pill).alpha, 0.0, "pictures are gone at once");
        assert!(fx.get(El::Row(0)).alpha >= 0.9);
        assert!(fx.get(El::Mark).at_rest() && fx.get(El::Name).at_rest() && fx.get(El::Keys).at_rest());
        let Phase::Leaving(fx) = m.frame(at(30), &Scene::new("item")) else { panic!("leaving at 30") };
        let a = fx.get(El::Row(3)).alpha;
        assert!(a > 0.0 && a < 0.5, "{a}");
        assert!(fx.get(El::Name).at_rest(), "the header never moves");
        let Phase::Leaving(fx) = m.frame(at(119), &Scene::new("item")) else { panic!("leaving at 119") };
        assert!(fx.get(El::Row(0)).alpha <= 0.07);
    }

    #[test]
    fn enter_staggers_body_elements_thirty_ms_apart_and_rests_by_three_hundred() {
        let t0 = Instant::now();
        let at = |ms: u64| t0 + Duration::from_millis(ms);
        let mut m = Timeline::new();
        m.navigate(NavKind::Pop, &Scene::new("item"), "front", t0);
        // First entering frame: nothing is known yet, so everything that could arrive is hidden.
        let Phase::Entering(fx) = m.frame(at(120), &Scene::new("front")) else { panic!("entering") };
        assert_eq!(fx.get(El::Row(0)).alpha, 0.0);
        assert_eq!(fx.get(El::Pill).alpha, 0.0);
        assert!(fx.get(El::Name).at_rest());
        let cur = front(6);
        // The pill is body element 0, rows 1..: element i starts at 30·i ms, capped at 100.
        let Phase::Entering(fx) = m.frame(at(120 + 10), &cur) else { panic!("entering at 10") };
        assert!(fx.get(El::Pill).alpha < 0.5, "pill still hidden early");
        assert_eq!(fx.get(El::Row(0)).alpha, 0.0, "row 0 (element 1) starts at 30 ms");
        let Phase::Entering(fx) = m.frame(at(120 + 45), &cur) else { panic!("entering at 45") };
        assert!(fx.get(El::Row(0)).alpha > 0.0, "row 0 (element 1) has started");
        assert_eq!(fx.get(El::Row(1)).alpha, 0.0, "row 1 (element 2) starts at 60 ms");
        let Phase::Entering(fx) = m.frame(at(120 + 150), &cur) else { panic!("entering at 150") };
        assert!(fx.get(El::Row(0)).alpha > fx.get(El::Row(2)).alpha);
        assert!(fx.get(El::Row(2)).alpha > fx.get(El::Row(5)).alpha);
        assert!(fx.get(El::Row(5)).alpha > 0.0, "element 6 starts at 100 ms, not 180");
        assert_eq!(Timeline::arrive_at(5), 100.0);
        let Phase::Entering(fx) = m.frame(at(120 + 290), &cur) else { panic!("entering at 290") };
        assert!(fx.get(El::Row(5)).alpha >= 0.9);
        assert!(matches!(m.frame(at(120 + 300), &cur), Phase::Idle));
        assert!(!m.active());
    }

    fn scene(screen: &'static str, number: &str) -> Scene {
        let mut s = Scene::new(screen);
        s.cell = (8, 20);
        s.push(El::Block(0), Rect::new(2, 17, 6, 3), None, Some(number));
        s
    }

    fn value(fx: &Phase) -> Option<f32> {
        match fx {
            Phase::Entering(fx) => fx.get(El::Block(0)).value,
            _ => None,
        }
    }

    #[test]
    fn install_number_counts_up_to_each_new_value() {
        let t0 = Instant::now();
        let at = |ms: u64| t0 + Duration::from_millis(ms);
        let mut m = Timeline::new();
        m.started = true; // not the first view: no entry
        assert!(m.drawn(at(0), &scene("installing", "40")));
        assert_eq!(value(&m.frame(at(0), &scene("installing", "40"))), Some(0.0));
        let mid = value(&m.frame(at(100), &scene("installing", "40"))).unwrap();
        assert!(mid > 5.0 && mid < 40.0, "{mid}");
        assert!(!m.drawn(at(100), &scene("installing", "40")), "same target: no restart");
        // A new value mid-count: count on from where it is.
        assert!(m.drawn(at(150), &scene("installing", "65")));
        let from = value(&m.frame(at(150), &scene("installing", "65"))).unwrap();
        assert!(from > mid && from < 40.0, "{from}");
        let end = value(&m.frame(at(2000), &scene("installing", "65")));
        assert_eq!(end, Some(65.0));
        assert!(matches!(m.frame(at(2100), &scene("installing", "65")), Phase::Idle));
        assert!(!m.active());
    }
}
