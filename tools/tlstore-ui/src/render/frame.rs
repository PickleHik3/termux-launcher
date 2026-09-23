//! The surface a screen draws one frame into: cells, sized text, pictures and tap regions.

use super::{text_width, Buffer, Placement, Rect, SizedRun, Sizing, Style};
use crate::app::Ctx;
use crate::layout::{self, Regions, Tier};
use crate::palette::Palette;

/// What a tap on a region means; screens pick their own numbering.
pub type ActionId = u32;

/// Tap regions of one frame, in drawing order (later = on top).
#[derive(Clone, Debug, Default, PartialEq, Eq)]
pub struct HitMap {
    regions: Vec<(Rect, ActionId)>,
}

impl HitMap {
    pub fn push(&mut self, rect: Rect, action: ActionId) {
        if !rect.is_empty() {
            self.regions.push((rect, action));
        }
    }
    /// The topmost region under (col, row).
    pub fn at(&self, col: u16, row: u16) -> Option<ActionId> {
        self.regions.iter().rev().find(|(r, _)| r.contains(col, row)).map(|(_, a)| *a)
    }
    /// The rect of the topmost region with this action.
    pub fn rect_of(&self, action: ActionId) -> Option<Rect> {
        self.regions.iter().rev().find(|(_, a)| *a == action).map(|(r, _)| *r)
    }
    pub fn clear(&mut self) {
        self.regions.clear();
    }
    pub fn len(&self) -> usize {
        self.regions.len()
    }
    pub fn is_empty(&self) -> bool {
        self.regions.is_empty()
    }
}

/// One frame being drawn. Everything is clipped to the screen.
pub struct Frame<'a> {
    pub buf: Buffer,
    pub places: Vec<Placement>,
    pub hits: HitMap,
    /// Size, capabilities, palette and the picture cache.
    pub ctx: &'a mut Ctx,
}

impl<'a> Frame<'a> {
    pub fn new(ctx: &'a mut Ctx) -> Frame<'a> {
        Frame {
            buf: Buffer::new(ctx.size.cols, ctx.size.rows),
            places: Vec::new(),
            hits: HitMap::default(),
            ctx,
        }
    }

    /// Blank again, as if new: cells, pictures and tap regions dropped (to draw the frame over).
    pub fn clear(&mut self) {
        self.buf = Buffer::new(self.buf.w, self.buf.h);
        self.places.clear();
        self.hits.clear();
    }

    pub fn area(&self) -> Rect {
        self.buf.area()
    }
    pub fn cols(&self) -> u16 {
        self.buf.w
    }
    pub fn rows(&self) -> u16 {
        self.buf.h
    }
    pub fn pal(&self) -> &Palette {
        &self.ctx.palette
    }
    pub fn tier(&self) -> Tier {
        layout::tier(self.buf.w, self.buf.h)
    }
    pub fn narrow(&self) -> bool {
        layout::narrow(self.buf.w)
    }
    pub fn regions(&self) -> Regions {
        layout::regions(self.buf.w, self.buf.h)
    }

    /// Writes `s` at (x, y); returns the column after it. Clipped at the right edge.
    pub fn text(&mut self, x: u16, y: u16, s: &str, style: Style) -> u16 {
        let w = self.buf.w;
        self.buf.put_str(x, y, s, style, w)
    }

    /// As [`Frame::text`], stopping before column `max_x`.
    pub fn text_clip(&mut self, x: u16, y: u16, s: &str, style: Style, max_x: u16) -> u16 {
        self.buf.put_str(x, y, s, style, max_x)
    }

    /// Writes `s` so it ends just before column `right`; returns its start column.
    pub fn text_right(&mut self, right: u16, y: u16, s: &str, style: Style) -> u16 {
        let x = right.saturating_sub(text_width(s) as u16);
        self.buf.put_str(x, y, s, style, right);
        x
    }

    /// Writes `s` centred inside `within` on row `y`; returns its start column.
    pub fn text_centred(&mut self, within: Rect, y: u16, s: &str, style: Style) -> u16 {
        let x = layout::centre_x(within, text_width(s) as u16);
        self.buf.put_str(x, y, s, style, within.right());
        x
    }

    /// Paints `rect` with spaces in `style` (a background block).
    pub fn fill(&mut self, rect: Rect, style: Style) {
        self.buf.fill(rect, style);
    }

    /// Repeats `ch` from column `x0` up to (not including) `x1` on row `y`.
    pub fn hline(&mut self, x0: u16, x1: u16, y: u16, ch: char, style: Style) {
        let mut x = x0;
        while x < x1.min(self.buf.w) {
            let adv = self.buf.put_char(x, y, ch, style);
            if adv == 0 {
                break;
            }
            x += adv;
        }
    }

    /// Dot leaders from `x0` to `x1` on row `y`: a space, then `·` on every other column
    /// aligned to even columns so stacked rows line up, then a space before `x1`.
    pub fn leaders(&mut self, x0: u16, x1: u16, y: u16, style: Style) {
        let mut x = x0 + 1;
        while x + 1 < x1 {
            if x.is_multiple_of(2) {
                self.buf.put_char(x, y, '·', style);
            }
            x += 1;
        }
    }

    /// Draws `s` with OSC 66 at (x, y) and returns the cells it covers. When the terminal has
    /// no text sizing, or the run does not fit, writes it as plain one-row text instead.
    pub fn sized(&mut self, x: u16, y: u16, s: &str, sizing: Sizing, style: Style) -> Rect {
        if self.ctx.caps.text_sizing {
            let run = SizedRun { x, y, text: s.to_string(), sizing, style };
            if let Some(r) = self.buf.put_run(run) {
                return r;
            }
        }
        let end = self.text(x, y, s, style);
        Rect::new(x, y, end - x.min(end), 1)
    }

    /// Shows a picture (see [`Placement`]). Ignored, returning false, when the terminal has no
    /// kitty graphics; the screen should then draw a text stand-in.
    pub fn picture(&mut self, p: Placement) -> bool {
        if !self.ctx.caps.kitty_graphics {
            return false;
        }
        self.places.retain(|o| !(o.pic.id() == p.pic.id() && o.pid == p.pid));
        self.places.push(p);
        true
    }

    /// Makes the (already drawn) cells of one-row `rect` an OSC 8 hyperlink to `url`.
    pub fn link(&mut self, rect: Rect, url: &str) {
        self.buf.link(rect, url);
    }

    /// Makes `rect` a tap target for `action`. Register after drawing; later regions win.
    pub fn hit(&mut self, rect: Rect, action: ActionId) {
        let r = rect.intersect(&self.buf.area());
        self.hits.push(r, action);
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::render::Sym;

    #[test]
    fn hit_test_topmost_wins() {
        let mut h = HitMap::default();
        h.push(Rect::new(0, 0, 10, 5), 1);
        h.push(Rect::new(2, 2, 3, 1), 2);
        assert_eq!(h.at(3, 2), Some(2));
        assert_eq!(h.at(3, 3), Some(1));
        assert_eq!(h.at(10, 0), None);
        assert_eq!(h.rect_of(2), Some(Rect::new(2, 2, 3, 1)));
    }

    #[test]
    fn frame_hit_is_clipped_and_sized_falls_back() {
        let mut ctx = Ctx::for_tests(20, 10);
        let mut f = Frame::new(&mut ctx);
        f.hit(Rect::new(15, 8, 10, 10), 7);
        assert_eq!(f.hits.at(19, 9), Some(7));
        assert_eq!(f.hits.rect_of(7), Some(Rect::new(15, 8, 5, 2)));
        let r = f.sized(0, 0, "big", Sizing::scale(3), Style::new());
        assert_eq!(r, Rect::new(0, 0, 3, 1));
        assert_eq!(f.buf.get(0, 0).unwrap().sym, Sym::Char('b'));
        f.ctx.caps.text_sizing = true;
        let r = f.sized(0, 1, "big", Sizing::scale(3), Style::new());
        assert_eq!(r, Rect::new(0, 1, 9, 3));
    }

    #[test]
    fn right_and_centre_text() {
        let mut ctx = Ctx::for_tests(20, 3);
        let mut f = Frame::new(&mut ctx);
        assert_eq!(f.text_right(20, 0, "abc", Style::new()), 17);
        assert_eq!(f.text_centred(Rect::new(0, 0, 20, 3), 1, "abcd", Style::new()), 8);
        f.leaders(3, 11, 2, Style::new());
        let row: String = (0..20)
            .map(|x| match &f.buf.get(x, 2).unwrap().sym {
                Sym::Char(c) => *c,
                _ => '?',
            })
            .collect();
        assert_eq!(row, "    · · ·           ");
    }
}
