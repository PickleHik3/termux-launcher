//! Installing (also updating and removing): the header of the item in progress, the 3×
//! count-up percentage, the steps beside it, the drawn progress line, what comes next, and
//! the summary when it ends.

use crate::layout;
use crate::picture::shapes;
use crate::render::{Sizing, Underline};
use crate::term::{Event, Key};

use super::data::STEPS;
use super::paint::{draw_header, HeaderContent, Masthead, Paint, Slot, Slots, A_BACK, A_CONTEXT};
use super::scene::El;
use super::{header_for, Go, Store, View};

#[derive(Default)]
pub struct Installing;

impl Installing {
    pub fn new() -> Installing {
        Installing
    }
}

impl View for Installing {
    fn name(&self) -> &'static str {
        "installing"
    }

    fn draw(&mut self, p: &mut Paint, st: &mut Store) {
        let pal = *p.f.pal();
        let (cw, ch) = p.cell();
        let Some(job) = &st.job else {
            let hdr = layout::header(p.f.cols(), p.f.rows(), 7, None);
            let content = HeaderContent {
                masthead: Masthead::Page { repo: None, setup: false },
                picture: None,
                name: "",
                standfirst: "",
                facts: Default::default(),
            };
            draw_header(p, &hdr, &content);
            p.text(El::Block(0), hdr.body.x, hdr.body.y, "Nothing is being installed.", pal.dim_s().italic());
            return;
        };
        let running = job.running();
        let verb = job.verb;
        let name = job.shown_name();
        let on_shown = job.current.as_deref() == Some(name.as_str()) || job.current.is_none();
        let ok_all = !running && !job.done.is_empty() && job.done.iter().all(|d| d.ok);
        let failed = job.failed(&name);
        let target: u8 = if ok_all {
            100
        } else if on_shown || !running {
            job.pct
        } else {
            job.pct.min(99)
        };
        let step = if ok_all { STEPS.len() } else { job.step };
        let also = job.current.clone().filter(|c| running && !job.names.contains(c));
        let queued = job.queued();
        let summary = job.summary();

        let info = st.cat.info(&st.env, &name).clone();
        let setup = info.setup();
        let repo = info.upstream().map(str::to_string);
        let (hdr, pic) = header_for(p, st, &name, 7, !setup, false);
        let state = if running {
            Some(verb.ing())
        } else if failed {
            Some("failed")
        } else {
            None
        };
        let facts = st.facts(&name, state);
        let content = HeaderContent {
            masthead: Masthead::Page { repo, setup },
            picture: pic,
            name: &name,
            standfirst: info.standfirst(),
            facts,
        };
        draw_header(p, &hdr, &content);

        let b = hdr.body;
        let g = b.x;
        // The count-up: motion may show a number on its way to the target.
        let pct = match p.fx.get(El::Block(0)).value {
            Some(v) => v.round().clamp(0.0, 100.0) as u8,
            None => target,
        };
        let digits = pct.to_string();
        if p.f.ctx.caps.text_sizing {
            // The sign sits right after the digits, so a third digit (100) pushes it along
            // instead of landing on top of the number.
            let r = p.sized(El::Block(0), g, b.y, &digits, Sizing::scale(3), pal.accent_s().bold());
            p.sized(El::Block(0), r.right(), b.y + 1, "%", Sizing::scale(2), pal.dim_s());
        } else {
            let e = p.text(El::Block(0), g, b.y, &digits, pal.accent_s().bold());
            p.text(El::Block(0), e, b.y, "%", pal.dim_s());
        }
        // The scene keeps the number at rest, so motion can see where it is heading.
        if let Some(el) = p.scene.elements.iter_mut().find(|e| e.el == El::Block(0)) {
            el.text = Some(target.to_string());
        }

        // Steps beside the number; the summary once it is over.
        let sx = g + 12;
        let sw = b.right().saturating_sub(sx);
        if running {
            for (i, words) in STEPS.iter().enumerate() {
                let el = El::Block(1 + i as u16);
                let style = if step > i {
                    pal.dim_s()
                } else if step == i {
                    pal.accent_s().underline(Underline::Dotted).ul_color(pal.accent)
                } else {
                    pal.rule_s()
                };
                p.text_clip(el, sx, b.y + i as u16, words, style, b.right());
            }
        } else {
            let mut y = b.y;
            for (i, l) in summary.iter().enumerate() {
                for line in layout::wrap(l, sw, 2) {
                    if y >= b.y + 4 {
                        break;
                    }
                    let style = if i == 0 { pal.ink_s() } else { pal.dim_s() };
                    p.text_clip(El::Block(1 + (y - b.y)), sx, y, &line, style, b.right());
                    y += 1;
                }
            }
        }

        // The progress line: one picture per target percentage.
        let ly = b.y + 5;
        if ly < b.bottom() {
            let placed = p.f.ctx.caps.kitty_graphics && {
                let (w, h) = (b.w as u32 * cw as u32, ch as u32);
                let line_h = (ch as u32 / 6).max(2);
                let key = format!("progress:{target}:{w}x{h}:{:?}:{:?}", pal.accent, pal.rule);
                let pic =
                    p.f.ctx
                        .pics
                        .shape(key, || shapes::progress_line(w, h, line_h, target, pal.rule, pal.accent));
                p.picture(El::Block(5), &pic, g, ly, (0, 0), 1)
            };
            if !placed {
                let on = (target as u32 * b.w as u32).div_ceil(100) as usize;
                let e = p.text(El::Block(5), g, ly, &"─".repeat(on), pal.accent_s());
                p.text(El::Block(5), e, ly, &"─".repeat(b.w as usize - on.min(b.w as usize)), pal.rule_s());
            }
        }

        // What else the job brings, or what comes next.
        let ny = b.y + 6;
        if running && ny < b.bottom() {
            if let Some(c) = also {
                let s = layout::fit_line(&format!("also bringing {c}"), b.w);
                p.text(El::Block(6), g, ny, &s, pal.dim_s().italic());
            } else if !queued.is_empty() {
                let s = layout::fit_line(&format!("then {}", queued.join(", ")), b.w);
                p.text(El::Block(6), g, ny, &s, pal.dim_s());
            }
        }
    }

    fn keys(&self, st: &mut Store) -> Slots {
        let first = if st.job_running() {
            Slot::new("x", "cancel", Key::Char('x'))
        } else {
            Slot::new("⏎", "done", Key::Enter)
        };
        [Some(first), None, None, None, Some(Slot::new("esc", "back", Key::Esc))]
    }

    fn handle(&mut self, ev: &Event, st: &mut Store) -> Go {
        match ev {
            Event::Key(Key::Esc | Key::Backspace) | Event::Tap { action: A_BACK, .. } => Go::Back,
            Event::Key(Key::Enter) if !st.job_running() => Go::Back,
            Event::Key(Key::Char('x')) if st.job_running() => {
                st.cancel_job();
                Go::Stay
            }
            Event::Key(Key::Char('q')) if st.job_running() => {
                st.notice = Some("Press esc to keep it going, or x to cancel.".into());
                Go::Stay
            }
            Event::Tap { action: A_CONTEXT, .. } => {
                let name = st.job.as_ref().map(Job::shown_name).unwrap_or_default();
                if let Some(rp) = st.cat.info(&st.env, &name).upstream().map(str::to_string) {
                    st.open_repo(&rp);
                }
                Go::Stay
            }
            Event::Key(_) => Go::Pass,
            _ => Go::Stay,
        }
    }
}

use super::Job;
