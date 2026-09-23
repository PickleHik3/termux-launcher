//! `--demo`: the shared structure with dummy rows, to run the crate end to end. Not a real
//! screen; P4 replaces it. Also the reference for how a screen uses the pieces.

use std::time::{Duration, Instant};

use crate::app::{Ctx, Nav, Screen};
use crate::layout::{self, Regions, Tier};
use crate::render::{text_width, Crop, Frame, Placement, Rect, Sizing, Underline};
use crate::term::{Event, Key};

const HOME: u32 = 1;
const QUIT: u32 = 2;
const ROW0: u32 = 100;

const ROWS: [(&str, &str, &str); 3] =
    [("kitten", "TOOLS", "installed"), ("dawn", "NOTE TAKING", "0.4.1"), ("opencode", "AI", "get")];

pub struct Demo {
    selected: usize,
    /// Hero word rise: start time of the only animation, a placeholder for P5's timeline.
    rise_from: Option<Instant>,
}

impl Default for Demo {
    fn default() -> Self {
        Demo::new()
    }
}

impl Demo {
    pub fn new() -> Demo {
        Demo { selected: 0, rise_from: Some(Instant::now()) }
    }

    /// 0.0 → 1.0 over the rise; 1.0 when done or motion is off.
    fn rise(&self, now: Instant, motion: bool) -> f32 {
        match (self.rise_from, motion) {
            (Some(t), true) => (now.duration_since(t).as_secs_f32() / 0.35).min(1.0),
            _ => 1.0,
        }
    }

    fn masthead(&self, f: &mut Frame, r: &Regions) {
        let pal = *f.pal();
        let y = r.masthead.y;
        let x0 = r.masthead.x;
        let (cw, ch) = (f.ctx.size.cell_w, f.ctx.size.cell_h);
        // The mark: as tall as ~half a cell, whole pixels, vertically centred in the row.
        let px = (ch as u32 / 10).max(1);
        let mark = f.ctx.pics.mark(px, pal.ink);
        let (mcols, _) = mark.cells(cw, ch);
        let off_y = (ch as u32).saturating_sub(mark.height()) / 2;
        let placed = f.picture(
            Placement::new(&mark, x0, y)
                .at_px(x0 as u32 * cw as u32, y as u32 * ch as u32 + off_y, cw, ch)
                .z(1),
        );
        let mark_end = if placed { x0 + mcols } else { f.text(x0, y, "TLSTORE", pal.ink_s().bold()) };
        f.hit(Rect::new(x0, y, mark_end - x0, 1), HOME);
        f.text(mark_end + 1, y, "/ apps", pal.dim_s());
        f.text_right(r.masthead.right(), y, "↑ 2 updates", pal.accent_s());
        f.hline(r.rule.x, r.rule.right(), r.rule.y, '─', pal.rule_s());
    }

    fn hero(&self, f: &mut Frame, r: &Regions, rise: f32) {
        let pal = *f.pal();
        let lead = layout::spaced_caps("terminal");
        let lead_end = f.text(r.hero_lead.x, r.hero_lead.y, &lead, pal.dim_s());
        let (cw, ch) = (f.ctx.size.cell_w as u32, f.ctx.size.cell_h as u32);
        let (x, y, rows) = if r.tier == Tier::Compact {
            (lead_end + 2, r.hero.y, 1)
        } else {
            (r.hero_word.x, r.hero_word.y, r.hero_word.h)
        };
        let word = f.ctx.pics.script_word("goodies", rows as u32 * ch, pal.accent);
        // Rise out of a mask: the picture starts lower and cropped to what is above the mask
        // line, and ends at rest, uncropped.
        let lift = ((1.0 - rise) * word.height() as f32 * 0.6) as u32;
        let shown = word.height() - lift;
        let p = Placement::new(&word, x, y)
            .at_px(x as u32 * cw, y as u32 * ch + lift, cw as u16, ch as u16)
            .crop(Crop { x: 0, y: 0, w: word.width(), h: shown.max(1) });
        if !f.picture(p) {
            let sized = Sizing::scale(rows.clamp(1, 7) as u8);
            f.sized(x, y, "goodies", sized, pal.accent_s().italic());
        }
    }

    fn rows(&self, f: &mut Frame, r: &Regions) {
        let pal = *f.pal();
        for (i, (name, cat, status)) in ROWS.iter().enumerate() {
            let y = r.body.y + i as u16 * 2;
            if y >= r.body.bottom() {
                break;
            }
            let sel = i == self.selected;
            let row = r.body.row(i as u16 * 2);
            if sel {
                f.fill(row, pal.tonal_s());
            }
            let base = if sel { pal.tonal_s() } else { pal.ink_s() };
            let dim = if sel { pal.tonal_s() } else { pal.dim_s() };
            let mut x = f.text(row.x + 1, y, &format!("{:02}", i + 1), dim);
            x = f.text(x + 2, y, name, base.bold());
            if !r.narrow {
                x = f.text(x + 2, y, cat, dim);
            }
            let st_w = text_width(status) as u16;
            let st_x = row.right().saturating_sub(st_w + 1);
            f.leaders(x, st_x, y, dim);
            let st = if *status == "get" { base.underline(Underline::Single) } else { base };
            f.text(st_x, y, status, st);
            f.hit(row, ROW0 + i as u32);
        }
    }

    fn keys(&self, f: &mut Frame, r: &Regions) {
        let pal = *f.pal();
        let y = r.keys.y;
        let mut x = r.keys.x;
        let hints: &[(&str, &str, u32)] = &[("↑↓", " move", 0), ("⏎", " open", 0), ("q", " quit", QUIT)];
        for (k, label, action) in hints {
            let start = x;
            x = f.text(x, y, k, pal.accent_s().bold());
            x = f.text(x, y, label, pal.dim_s());
            if *action != 0 {
                f.hit(Rect::new(start, y, x - start, 1), *action);
            }
            x += 3;
        }
        if r.tier == Tier::Compact {
            f.text_right(r.keys.right(), y, "f fullscreen", pal.dim_s());
        }
    }
}

impl Screen for Demo {
    fn draw(&mut self, f: &mut Frame) {
        let r = f.regions();
        let rise = self.rise(Instant::now(), f.ctx.motion);
        self.masthead(f, &r);
        self.hero(f, &r, rise);
        self.rows(f, &r);
        self.keys(f, &r);
    }

    fn handle(&mut self, ev: &Event, _ctx: &mut Ctx) -> Nav {
        match ev {
            Event::Key(Key::Char('q')) | Event::Key(Key::Esc) => Nav::Quit,
            Event::Key(Key::Up) | Event::Key(Key::Char('k')) => {
                self.selected = self.selected.saturating_sub(1);
                Nav::Stay
            }
            Event::Key(Key::Down) | Event::Key(Key::Char('j')) => {
                self.selected = (self.selected + 1).min(ROWS.len() - 1);
                Nav::Stay
            }
            Event::Tap { action: QUIT, .. } => Nav::Quit,
            Event::Tap { action: HOME, .. } => {
                self.rise_from = Some(Instant::now());
                Nav::Stay
            }
            Event::Tap { action, .. } if *action >= ROW0 => {
                self.selected = (*action - ROW0) as usize;
                Nav::Stay
            }
            _ => Nav::Stay,
        }
    }

    fn animating(&self) -> bool {
        self.rise_from.is_some()
    }

    fn tick(&mut self, now: Instant, _ctx: &mut Ctx) -> bool {
        if let Some(t) = self.rise_from {
            if now.duration_since(t) > Duration::from_millis(350) {
                self.rise_from = None;
            }
            return true;
        }
        false
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::render::{Renderer, Sym};
    use crate::term::Caps;

    fn row_text(f: &Frame, y: u16) -> String {
        (0..f.cols())
            .map(|x| match &f.buf.get(x, y).unwrap().sym {
                Sym::Char(c) => c.to_string(),
                Sym::Cluster(s) => s.to_string(),
                _ => String::new(),
            })
            .collect()
    }

    #[test]
    fn draws_structure_on_plain_terminal() {
        let mut ctx = Ctx::for_tests(52, 45);
        let mut d = Demo::new();
        let mut f = Frame::new(&mut ctx);
        d.draw(&mut f);
        assert!(row_text(&f, 1).starts_with("  TLSTORE / apps"));
        assert!(row_text(&f, 1).trim_end().ends_with("↑ 2 updates"));
        assert!(row_text(&f, 2).contains("────"));
        assert!(row_text(&f, 4).contains("T E R M I N A L"));
        assert!(row_text(&f, 9).contains("kitten"));
        assert!(row_text(&f, 43).contains("q quit"));
        assert!(f.places.is_empty());
        assert_eq!(f.hits.at(3, 1), Some(HOME));
        assert_eq!(f.hits.at(20, 11), Some(ROW0 + 1));
    }

    #[test]
    fn draws_pictures_and_renders_on_capable_terminal() {
        let mut ctx = Ctx::for_tests(52, 23);
        ctx.caps = Caps::all();
        ctx.motion = false;
        let mut d = Demo::new();
        let mut f = Frame::new(&mut ctx);
        d.draw(&mut f);
        assert_eq!(f.places.len(), 2);
        let word = &f.places[1];
        assert_eq!(word.pic.height(), 20);
        assert!(row_text(&f, 22).contains("f fullscreen"));
        let Frame { buf, places, .. } = f;
        let mut out = String::new();
        Renderer::new().render(&buf, &places, &mut out);
        assert!(out.contains("\x1b_Ga=t,f=32"));
        assert!(out.contains("a=p,"));
        assert!(out.starts_with("\x1b[?2026h") && out.ends_with("\x1b[?2026l"));
    }

    #[test]
    fn keys_and_taps_navigate() {
        let mut ctx = Ctx::for_tests(52, 45);
        let mut d = Demo::new();
        assert!(matches!(d.handle(&Event::Key(Key::Down), &mut ctx), Nav::Stay));
        assert_eq!(d.selected, 1);
        d.handle(&Event::Tap { action: ROW0 + 2, col: 0, row: 0 }, &mut ctx);
        assert_eq!(d.selected, 2);
        assert!(matches!(d.handle(&Event::Tap { action: QUIT, col: 0, row: 0 }, &mut ctx), Nav::Quit));
    }
}
