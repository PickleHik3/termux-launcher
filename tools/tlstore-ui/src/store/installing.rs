//! Installing (also updating and removing): the big number, the dot bar, the four steps, what
//! comes next, and the summary when it ends.

use crate::layout::{self, Regions};
use crate::render::{text_width, Sizing};
use crate::term::{Event, Key};

use super::data::STEPS;
use super::paint::{Chrome, ContextItem, Hint, Paint};
use super::scene::El;
use super::{Go, Store, View};

/// Rows under the number: gap, bar, gap, 4 steps, gap, 2 note lines, gap, next.
const BELOW: u16 = 12;

#[derive(Default)]
pub struct Installing;

impl Installing {
    pub fn new() -> Installing {
        Installing
    }
}

/// The largest text-sizing scale for the number that leaves room for the rest.
pub fn number_scale(body_h: u16, body_w: u16) -> u8 {
    let by_h = body_h.saturating_sub(BELOW);
    let by_w = body_w.saturating_sub(3) / 4;
    by_h.min(by_w).clamp(1, 7) as u8
}

impl View for Installing {
    fn name(&self) -> &'static str {
        "installing"
    }

    fn chrome(&mut self, st: &mut Store) -> Chrome {
        let Some(job) = &st.job else {
            return Chrome {
                crumb: "/ installing".into(),
                lead: "installing".into(),
                keys: vec![Hint::new("esc", "back", Key::Esc)],
                ..Chrome::default()
            };
        };
        let keys = if job.running() {
            vec![Hint::new("esc", "keep going quietly", Key::Esc), Hint::new("c", "cancel", Key::Char('c'))]
        } else {
            vec![Hint::new("esc", "back", Key::Esc)]
        };
        Chrome {
            crumb: format!("/ {}", job.verb.ing()),
            context: (job.names.len() > 1 && job.running()).then(|| ContextItem {
                text: format!("{} of {}", job.index(), job.names.len()),
                link: false,
            }),
            lead: job.verb.ing().into(),
            word: job.shown_name(),
            script: false,
            keys,
        }
    }

    fn body(&mut self, p: &mut Paint, st: &mut Store, r: &Regions) {
        let pal = *p.f.pal();
        let b = r.body;
        let Some(job) = &st.job else {
            p.centred(El::Block(0), b, b.y + 1, "Nothing is being installed.", pal.dim_s().italic());
            return;
        };
        let running = job.running();
        let shown = job.shown_name();
        let on_shown = job.current.as_deref() == Some(shown.as_str()) || job.current.is_none();
        let pct = if on_shown || !running { job.pct } else { job.pct.min(99) };
        let ok_all = !running && job.done.iter().all(|d| d.ok) && !job.done.is_empty();
        let target = if ok_all { 100 } else { pct };
        // The count-up: motion may show a number on its way to the target; the dot bar follows.
        let pct = match p.fx.get(El::Block(0)).value {
            Some(v) => v.round().clamp(0.0, 100.0) as u8,
            None => target,
        };

        // The number.
        let digits = pct.to_string();
        let mut y = b.y;
        if p.f.ctx.caps.text_sizing {
            let s = number_scale(b.h, b.w);
            let ps = (s / 3).max(1);
            let w = digits.len() as u16 * s as u16 + ps as u16;
            let x = layout::centre_x(b, w);
            let r1 = p.sized(El::Block(0), x, y, &digits, Sizing::scale(s), pal.accent_s().bold());
            p.sized(El::Block(0), r1.right(), y + s as u16 - ps as u16, "%", Sizing::scale(ps), pal.dim_s());
            y += s as u16 + 1;
        } else {
            let x = layout::centre_x(b, digits.len() as u16 + 1);
            let e = p.text(El::Block(0), x, y, &digits, pal.accent_s().bold());
            p.text(El::Block(0), e, y, "%", pal.dim_s());
            y += 2;
        }
        // The scene keeps the number at rest, so motion can see where it is heading.
        if let Some(el) = p.scene.elements.iter_mut().find(|e| e.el == El::Block(0)) {
            el.text = Some(target.to_string());
        }

        // The dot bar.
        let w = 40.min(b.w);
        let on = (pct as u32 * w as u32).div_ceil(100) as usize;
        let x = layout::centre_x(b, w);
        let e = p.text(El::Block(1), x, y, &"●".repeat(on), pal.accent_s());
        p.text(El::Block(1), e, y, &"○".repeat(w as usize - on.min(w as usize)), pal.rule_s());
        y += 2;

        // The steps.
        let sx = layout::centre_x(b, 25);
        let step = if ok_all { STEPS.len() } else { job.step };
        for (i, words) in STEPS.iter().enumerate() {
            let done = step > i;
            let now = !done && running && i == step;
            let (mark, mst) = if done {
                ("✓", pal.accent_s())
            } else if now {
                ("·", pal.dim_s())
            } else {
                (" ", pal.dim_s())
            };
            let el = El::Block(2 + i as u16);
            p.text(el, sx, y, mark, mst);
            p.text(el, sx + 3, y, words, if done || now { pal.ink_s() } else { pal.dim_s() });
            y += 1;
        }
        if let Some(c) = job.current.as_ref().filter(|c| running && !job.names.contains(c)) {
            p.centred(El::Block(6), b, y, &format!("with {c}"), pal.dim_s());
        }
        y += 1;

        // Note while it runs, summary when it ends.
        let lines: Vec<(String, bool)> = if running {
            let one = "Switch away if you like. You’ll get a notice when it’s done.";
            if text_width(one) as u16 <= b.w {
                vec![(one.into(), false)]
            } else {
                vec![
                    ("Switch away if you like.".into(), false),
                    ("You’ll get a notice when it’s done.".into(), false),
                ]
            }
        } else {
            job.summary().into_iter().enumerate().map(|(i, l)| (l, i == 0)).collect()
        };
        for (l, strong) in &lines {
            let st_ = if *strong { pal.ink_s() } else { pal.dim_s().italic() };
            p.centred(El::Block(7), b, y, l, st_);
            y += 1;
        }
        y += 1;

        // What comes next.
        let queued = job.queued();
        if running && !queued.is_empty() {
            let mut s = String::new();
            for (i, n) in queued.iter().enumerate() {
                if i > 0 {
                    s.push_str(", ");
                }
                s.push_str(n);
            }
            let upd = |n: &str| st.cat.update_for(n).map(|u| u.new.clone());
            let tail = if queued.len() == 1 { upd(&queued[0]).map(|v| format!(" → {v}")) } else { None };
            let w = 4 + 2 + text_width(&s) as u16 + tail.as_deref().map_or(0, |t| text_width(t) as u16);
            let x = layout::centre_x(b, w.min(b.w));
            let mut e = p.text(El::Block(8), x, y, "next", pal.dim_s());
            e = p.text_clip(El::Block(8), e + 2, y, &s, pal.ink_s(), b.right());
            if let Some(t) = tail {
                p.text_clip(El::Block(8), e, y, &t, pal.dim_s(), b.right());
            }
        }
    }

    fn handle(&mut self, ev: &Event, st: &mut Store) -> Go {
        match ev {
            Event::Key(Key::Esc | Key::Backspace) => Go::Back,
            Event::Key(Key::Enter) if !st.job_running() => Go::Back,
            Event::Key(Key::Char('c')) if st.job_running() => {
                st.cancel_job();
                Go::Stay
            }
            Event::Key(Key::Char('q')) if st.job_running() => {
                st.notice = Some("Press esc to keep it going, or c to cancel.".into());
                Go::Stay
            }
            Event::Key(_) => Go::Pass,
            _ => Go::Stay,
        }
    }
}
