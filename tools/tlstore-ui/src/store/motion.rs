//! The store's motion: one [`Timeline`] behind the router's [`Motion`] hook, with the timing
//! and easing of the approved Flow design (project-docs/tlstore/design/Flow.dc.html).
//!
//! A navigation plays in two parts. **Leave** (160 ms): the old view's content fades toward
//! the page and its pictures lift a little; the masthead stays. **Enter** (≈820 ms, input
//! already goes to the new view): the breadcrumb decodes from `░▒▓/_<>=+` left to right,
//! the rule draws out, the hero lead fades up, the hero word rises out of its mask on a
//! spring, the cover wipes down, and the rows (or text blocks) arrive 45 ms apart with their
//! leaders drawing out behind them. Inside a screen: rows re-stagger quickly when the list
//! changes (filter, category, page), and the install number counts up to each new value.
//!
//! Terminals without pictures get the text parts only: pictures' moves, crops and wipes
//! apply to picture elements, while text stand-ins just fade.

use std::time::{Duration, Instant};

use super::scene::{Effect, El, Fx, Motion, NavKind, Phase, Scene};

/// Easing curves, each mapping 0–1 to 0–1 (the spring overshoots on the way).
pub mod ease {
    /// Flow's `--spring`: CSS `linear()` stops, overshoot ≈ 9% at 26%, settled by ≈ 70%.
    const SPRING: [(f32, f32); 16] = [
        (0.0, 0.0),
        (0.0105, 0.009),
        (0.021, 0.035),
        (0.044, 0.141),
        (0.129, 0.723),
        (0.167, 0.938),
        (0.194, 1.017),
        (0.225, 1.067),
        (0.26, 1.089),
        (0.303, 1.079),
        (0.36, 1.049),
        (0.426, 1.024),
        (0.503, 1.011),
        (0.592, 1.004),
        (0.693, 1.001),
        (1.0, 1.0),
    ];

    /// The spring (piecewise linear between Flow's stops, exactly as CSS draws it).
    pub fn spring(t: f32) -> f32 {
        let t = t.clamp(0.0, 1.0);
        for w in SPRING.windows(2) {
            let ((x0, y0), (x1, y1)) = (w[0], w[1]);
            if t <= x1 {
                return y0 + (y1 - y0) * (t - x0) / (x1 - x0);
            }
        }
        1.0
    }

    /// Flow's `--out`, `cubic-bezier(.16,1,.3,1)`: a snappy deceleration.
    pub fn out(t: f32) -> f32 {
        bezier(0.16, 1.0, 0.3, 1.0, t)
    }

    /// Flow's `--dram`, `cubic-bezier(.77,0,.175,1)`: a dramatic in-out.
    pub fn dram(t: f32) -> f32 {
        bezier(0.77, 0.0, 0.175, 1.0, t)
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

/// Flow's timing, in milliseconds.
pub mod timing {
    /// The old view fades out for this long before the new one starts.
    pub const LEAVE: f32 = 160.0;
    /// Breadcrumb decode: 12 steps of 32 ms.
    pub const CRUMB_STEPS: u32 = 12;
    pub const CRUMB_STEP: f32 = 32.0;
    pub const RULE: f32 = 600.0;
    pub const LEAD_AT: f32 = 60.0;
    pub const LEAD: f32 = 500.0;
    pub const WORD_AT: f32 = 100.0;
    pub const WORD: f32 = 600.0;
    pub const COVER_AT: f32 = 120.0;
    pub const COVER: f32 = 700.0;
    pub const CAPTION_AT: f32 = 180.0;
    pub const HEADER_AT: f32 = 225.0;
    /// Text arriving (caption, header, rows, blocks) fades up over this long.
    pub const ARRIVE: f32 = 300.0;
    pub const ROWS_AT: f32 = 260.0;
    pub const ROW_GAP: f32 = 45.0;
    /// The last row starts no later than this after `ROWS_AT` (long lists close up).
    pub const ROWS_SPAN: f32 = 240.0;
    pub const BLOCKS_AT: f32 = 180.0;
    pub const BLOCKS_SPAN: f32 = 300.0;
    /// A row's leaders start this long after the row and draw out over `LEADERS`.
    pub const LEADERS_BEHIND: f32 = 60.0;
    pub const LEADERS: f32 = 240.0;
    /// Everything has arrived by then (the cover wipe ends last).
    pub const ENTER: f32 = COVER_AT + COVER;
    /// In-screen row re-stagger: all of it within this.
    pub const RESTAGGER: f32 = 200.0;
    /// The count-up: this long plus a little per point, at most `COUNT_MAX`.
    pub const COUNT_MIN: f32 = 200.0;
    pub const COUNT_PER: f32 = 6.0;
    pub const COUNT_MAX: f32 = 700.0;
}

use timing::*;

/// The glyphs a breadcrumb decodes from.
pub const GLYPHS: [char; 9] = ['░', '▒', '▓', '/', '_', '<', '>', '=', '+'];

/// Breadcrumb `target` after `step` of [`timing::CRUMB_STEPS`] decode steps: the first
/// `step·len/12` characters are real, the rest are glyphs (spaces stay spaces).
pub fn decode(target: &str, step: u32) -> String {
    let len = target.chars().count();
    let k = step as usize * len / CRUMB_STEPS as usize;
    target
        .chars()
        .enumerate()
        .map(
            |(i, ch)| {
                if i < k || ch == ' ' {
                    ch
                } else {
                    GLYPHS[(i * 7 + step as usize * 3) % GLYPHS.len()]
                }
            },
        )
        .collect()
}

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

/// Text fading in: alpha is stepped to sixteenths, so a slow fade does not rewrite every
/// cell of the text on every frame.
fn alpha_steps(a: f32) -> f32 {
    (a.clamp(0.0, 1.0) * 16.0).round() / 16.0
}

fn fade(a: f32) -> Effect {
    Effect { alpha: alpha_steps(a), ..Effect::default() }
}

fn set(fx: &mut Fx, el: El, e: Effect) {
    if !e.at_rest() {
        fx.set(el, e);
    }
}

/// Row and block elements a first, not-yet-drawn frame hides without knowing how many exist.
const HIDE_ROWS: u16 = 64;
const HIDE_BLOCKS: u16 = 16;

struct Transition {
    start: Instant,
    /// False for the very first view (nothing to leave).
    leave: bool,
    /// The view being left, as last drawn.
    from: Scene,
    /// The entering view has been drawn once (its layout is known).
    seen: bool,
}

struct Restagger {
    start: Instant,
    rows: u16,
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
    restagger: Option<Restagger>,
    count: Option<Count>,
    /// The install number as last shown (None off the installing screen).
    shown: Option<f32>,
    /// The screen and row numbers last drawn (to notice a changed list).
    last_screen: Option<&'static str>,
    last_rows: Vec<String>,
    /// A view has been drawn or navigated to (the first one plays the entry once).
    started: bool,
}

impl Timeline {
    pub fn new() -> Timeline {
        Timeline::default()
    }

    fn row_names(scene: &Scene) -> Vec<String> {
        scene
            .elements
            .iter()
            .filter(|e| matches!(e.el, El::Row(_)))
            .map(|e| e.text.clone().unwrap_or_default())
            .collect()
    }

    /// The leave: everything but the masthead fades; pictures also lift ~0.4 row.
    fn leave_fx(t: f32, from: &Scene) -> Fx {
        let mut fx = Fx::default();
        let p = prog(t, 0.0, LEAVE);
        let lift = (from.cell.1 as f32 * 0.4 * ease::out(p)).round() as i32;
        for e in &from.elements {
            if matches!(e.el, El::Mark | El::Crumb | El::Context) {
                continue;
            }
            let eff = if e.picture.is_some() {
                // Kitty has no per-placement alpha: the picture is gone once alpha < 0.5.
                Effect { dy: -lift, alpha: 1.0 - p, ..Effect::default() }
            } else {
                fade(1.0 - ease::out(p))
            };
            set(&mut fx, e.el, eff);
        }
        fx
    }

    /// The entry at `e` ms after it began. `cur` is the entering view's scene (empty on its
    /// first frame: then everything that arrives is simply hidden).
    fn enter_fx(e: f32, cur: &Scene) -> Fx {
        let mut fx = Fx::default();
        let blind = cur.elements.is_empty();
        let is_pic = |el: El| cur.get(el).is_some_and(|x| x.picture.is_some());

        // Breadcrumb.
        let step = (e / CRUMB_STEP).floor() as u32;
        if step < CRUMB_STEPS {
            match cur.get(El::Crumb).and_then(|c| c.text.as_deref()) {
                Some(t) => {
                    set(&mut fx, El::Crumb, Effect { text: Some(decode(t, step)), ..Effect::default() })
                }
                None if blind => set(&mut fx, El::Crumb, Effect { alpha: 0.0, ..Effect::default() }),
                None => {}
            }
        }

        // Rule and hero.
        set(&mut fx, El::Rule, Effect { reveal: ease::out(prog(e, 0.0, RULE)), ..Effect::default() });
        set(&mut fx, El::HeroLead, fade(ease::out(prog(e, LEAD_AT, LEAD))));
        let wp = prog(e, WORD_AT, WORD);
        if is_pic(El::HeroWord) {
            let s = ease::spring(wp);
            let h = cur.get(El::HeroWord).map_or(0.0, |x| x.rect.h as f32 * cur.cell.1 as f32);
            set(
                &mut fx,
                El::HeroWord,
                Effect { dy: (h * (1.0 - s)).round() as i32, shown: s.min(1.0), ..Effect::default() },
            );
        } else {
            set(&mut fx, El::HeroWord, fade(ease::out(wp)));
        }

        // Cover and caption.
        if is_pic(El::Cover) {
            set(
                &mut fx,
                El::Cover,
                Effect { shown: ease::dram(prog(e, COVER_AT, COVER)), ..Effect::default() },
            );
        } else {
            set(&mut fx, El::Cover, fade(ease::out(prog(e, COVER_AT, ARRIVE))));
        }
        set(&mut fx, El::Caption, fade(ease::out(prog(e, CAPTION_AT, ARRIVE))));
        set(&mut fx, El::Header, fade(ease::out(prog(e, HEADER_AT, ARRIVE))));
        set(&mut fx, El::Chips, fade(ease::out(prog(e, HEADER_AT, ARRIVE))));

        // Rows, then the pager after the last one.
        let rows = if blind {
            HIDE_ROWS
        } else {
            cur.elements
                .iter()
                .filter_map(|x| if let El::Row(n) = x.el { Some(n + 1) } else { None })
                .max()
                .unwrap_or(0)
        };
        let last = Self::stagger(&mut fx, e, rows, ROWS_AT, ROW_GAP, ROWS_SPAN, ARRIVE);
        if rows > 0 || blind {
            set(&mut fx, El::Pager, fade(ease::out(prog(e, last, ARRIVE))));
        }

        // Text blocks (item page, installing, the gh footnote).
        let installing = cur.screen == "installing";
        let blocks = if blind {
            HIDE_BLOCKS
        } else {
            cur.elements
                .iter()
                .filter_map(|x| if let El::Block(n) = x.el { Some(n + 1) } else { None })
                .max()
                .unwrap_or(0)
        };
        let n = blocks.max(1) as f32;
        let gap = ROW_GAP.min(BLOCKS_SPAN / (n - 1.0).max(1.0));
        for b in 0..blocks {
            // The install number counts up and its dot bar follows it; they do not fade.
            if installing && b < 2 {
                continue;
            }
            let at = BLOCKS_AT + gap * b as f32;
            let eff = Effect {
                alpha: alpha_steps(ease::out(prog(e, at, ARRIVE))),
                line: ease::out(prog(e, at + LEADERS_BEHIND, LEADERS)),
                ..Effect::default()
            };
            set(&mut fx, El::Block(b), eff);
        }
        fx
    }

    /// Rows 0..n arriving from `at`, `gap` apart (closed up to fit `span`), fading over
    /// `dur` with leaders drawing out behind. Returns when the last row starts.
    fn stagger(fx: &mut Fx, t: f32, n: u16, at: f32, gap: f32, span: f32, dur: f32) -> f32 {
        let gap = if n > 1 { gap.min(span / (n - 1) as f32) } else { gap };
        let behind = (dur / 5.0).min(LEADERS_BEHIND);
        let lead = (dur - behind).min(LEADERS).max(dur * 0.8);
        for i in 0..n {
            let start = at + gap * i as f32;
            let eff = Effect {
                alpha: alpha_steps(ease::out(prog(t, start, dur))),
                line: ease::out(prog(t, start + behind, lead)),
                ..Effect::default()
            };
            set(fx, El::Row(i), eff);
        }
        at + gap * n.saturating_sub(1) as f32
    }

    fn count_value(c: &Count, now: Instant) -> f32 {
        let p = prog(ms(now.saturating_duration_since(c.start)), 0.0, c.dur);
        c.from + (c.to - c.from) * ease::out(p)
    }
}

impl Motion for Timeline {
    fn navigate(&mut self, _kind: NavKind, from: &Scene, _to: &'static str, now: Instant) {
        // Every kind plays the same sequence (as in Flow). A navigation during a transition
        // simply starts over from the view as it was last drawn.
        self.tr = Some(Transition { start: now, leave: true, from: from.clone(), seen: false });
        self.restagger = None;
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
        if let Some(r) = &self.restagger {
            let t = ms(now.saturating_duration_since(r.start));
            if t >= RESTAGGER {
                self.restagger = None;
            } else {
                // All rows within RESTAGGER: gaps close up so the last one ends in time.
                let dur = RESTAGGER / 2.0;
                Self::stagger(&mut fx, t, r.rows, 0.0, 20.0, RESTAGGER - dur, dur);
                entering = true;
            }
        }
        if let Some(c) = &self.count {
            let v = Self::count_value(c, now);
            self.shown = Some(v);
            fx.set(El::Block(0), Effect { value: Some(v), ..Effect::default() });
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
        self.tr.is_some() || self.restagger.is_some() || self.count.is_some()
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

        // Rows changed in place (filter, category, page): a quick re-stagger.
        let rows = Self::row_names(current);
        if self.tr.is_none()
            && current.screen == "apps"
            && self.last_screen == Some("apps")
            && !self.last_rows.is_empty()
            && rows != self.last_rows
        {
            self.restagger = Some(Restagger { start: now, rows: rows.len() as u16 });
            redraw = true;
        }
        self.last_rows = rows;
        self.last_screen = Some(current.screen);

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
    fn spring_overshoots_nine_percent_and_settles_by_seventy() {
        assert_eq!(ease::spring(0.0), 0.0);
        assert_eq!(ease::spring(1.0), 1.0);
        let s = samples(ease::spring);
        let (peak_i, peak) =
            s.iter().enumerate().fold((0, 0.0f32), |a, (i, &v)| if v > a.1 { (i, v) } else { a });
        assert!((peak - 1.089).abs() < 0.002, "peak {peak}");
        assert!((0.24..=0.28).contains(&(peak_i as f32 / 200.0)), "peak at {}", peak_i as f32 / 200.0);
        // Settled: within 0.2% of the end from 70% on.
        for (i, v) in s.iter().enumerate().skip(140) {
            assert!((v - 1.0).abs() <= 0.002, "t={} v={v}", i as f32 / 200.0);
        }
        // Rises monotonically up to the peak.
        assert!(s[..=peak_i].windows(2).all(|w| w[1] >= w[0]));
        assert!(ease::spring(0.129) > 0.72 && ease::spring(0.129) < 0.73);
    }

    #[test]
    fn beziers_hit_their_ends_and_never_turn_back() {
        for f in [ease::out as fn(f32) -> f32, ease::dram] {
            assert_eq!(f(0.0), 0.0);
            assert_eq!(f(1.0), 1.0);
            assert_eq!(f(-1.0), 0.0);
            assert_eq!(f(2.0), 1.0);
            let s = samples(f);
            assert!(s.windows(2).all(|w| w[1] >= w[0] - 1e-6), "monotonic");
            assert!(s.iter().all(|v| (0.0..=1.0 + 1e-6).contains(v)));
        }
        // Snappy decel: most of the way there early.
        assert!(ease::out(0.1) > 0.4 && ease::out(0.3) > 0.85, "{} {}", ease::out(0.1), ease::out(0.3));
        // Dramatic in-out: slow start, fast middle, slow end.
        assert!(ease::dram(0.2) < 0.06 && ease::dram(0.8) > 0.94);
        // Cross-checked against an independent bisection of the same curve.
        assert!((ease::dram(0.5) - 0.59597).abs() < 1e-4, "{}", ease::dram(0.5));
        assert!((ease::out(0.1) - 0.49439).abs() < 1e-4, "{}", ease::out(0.1));
        // Linear control points give a straight line.
        assert!((ease::bezier(0.0, 0.0, 1.0, 1.0, 0.37) - 0.37).abs() < 1e-4);
    }

    #[test]
    fn breadcrumb_decodes_left_to_right_in_twelve_steps() {
        let t = "/ apps / sigye";
        let s0 = decode(t, 0);
        assert_eq!(s0.chars().count(), t.chars().count());
        assert!(s0.chars().zip(t.chars()).all(|(a, b)| (b == ' ') == (a == ' ')));
        assert!(s0.chars().filter(|c| *c != ' ').all(|c| GLYPHS.contains(&c)));
        let s6 = decode(t, 6);
        assert!(s6.starts_with("/ apps"), "{s6}");
        assert_ne!(s6, t);
        assert_eq!(decode(t, 12), t);
    }

    fn scene(screen: &'static str, number: &str) -> Scene {
        let mut s = Scene::new(screen);
        s.cell = (8, 20);
        s.push(El::Crumb, Rect::new(10, 1, 12, 1), None, Some("/ installing"));
        s.push(El::Block(0), Rect::new(10, 9, 6, 3), None, Some(number));
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

    #[test]
    fn rows_restagger_within_200ms_when_the_list_changes() {
        let t0 = Instant::now();
        let at = |ms: u64| t0 + Duration::from_millis(ms);
        let apps = |names: &[&str]| {
            let mut s = Scene::new("apps");
            for (i, n) in names.iter().enumerate() {
                s.push(El::Row(i as u16), Rect::new(2, 20 + 2 * i as u16, 40, 1), None, Some(n));
            }
            s
        };
        let mut m = Timeline::new();
        m.started = true;
        assert!(!m.drawn(at(0), &apps(&["01", "02", "03"])));
        assert!(!m.drawn(at(10), &apps(&["01", "02", "03"])), "unchanged: nothing moves");
        assert!(m.drawn(at(20), &apps(&["02", "05"])));
        let Phase::Entering(fx) = m.frame(at(20), &apps(&["02", "05"])) else { panic!("restagger") };
        assert_eq!(fx.get(El::Row(0)).alpha, 0.0);
        assert_eq!(fx.get(El::Row(1)).alpha, 0.0);
        let Phase::Entering(fx) = m.frame(at(50), &apps(&["02", "05"])) else { panic!("restagger") };
        assert!(fx.get(El::Row(0)).alpha > fx.get(El::Row(1)).alpha, "row 0 ahead of row 1");
        assert!(matches!(m.frame(at(220), &apps(&["02", "05"])), Phase::Idle));
        assert!(!m.active());
    }
}
