//! The cell buffer, the per-frame drawing surface and the diffing renderer.

pub mod diff;
pub mod escapes;
pub mod frame;
pub mod style;

pub use diff::{Placement, Renderer};
pub use escapes::{Crop, Sizing};
pub use frame::{ActionId, Frame, HitMap};
pub use style::{Color, Rgb, Style, Underline};

use unicode_width::UnicodeWidthChar;

/// Display width of one character in cells (0 for combining marks, 2 for wide characters).
pub fn char_width(c: char) -> usize {
    if c == '\t' {
        return 1;
    }
    UnicodeWidthChar::width(c).unwrap_or(0)
}

/// Display width of a string in cells.
pub fn text_width(s: &str) -> usize {
    s.chars().map(char_width).sum()
}

/// A rectangle of cells. `x`/`y` are 0-based columns/rows.
#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash, Default)]
pub struct Rect {
    pub x: u16,
    pub y: u16,
    pub w: u16,
    pub h: u16,
}

impl Rect {
    pub const fn new(x: u16, y: u16, w: u16, h: u16) -> Rect {
        Rect { x, y, w, h }
    }
    pub fn right(&self) -> u16 {
        self.x.saturating_add(self.w)
    }
    pub fn bottom(&self) -> u16 {
        self.y.saturating_add(self.h)
    }
    pub fn contains(&self, col: u16, row: u16) -> bool {
        col >= self.x && col < self.right() && row >= self.y && row < self.bottom()
    }
    pub fn is_empty(&self) -> bool {
        self.w == 0 || self.h == 0
    }
    /// Shrinks by `dx` columns on each side and `dy` rows top and bottom.
    pub fn inset(&self, dx: u16, dy: u16) -> Rect {
        Rect {
            x: self.x + dx.min(self.w / 2),
            y: self.y + dy.min(self.h / 2),
            w: self.w.saturating_sub(dx * 2),
            h: self.h.saturating_sub(dy * 2),
        }
    }
    /// The row `i` of this rect as a one-row rect.
    pub fn row(&self, i: u16) -> Rect {
        Rect { x: self.x, y: self.y + i, w: self.w, h: 1 }
    }
    pub fn intersect(&self, o: &Rect) -> Rect {
        let x = self.x.max(o.x);
        let y = self.y.max(o.y);
        let r = self.right().min(o.right());
        let b = self.bottom().min(o.bottom());
        Rect { x, y, w: r.saturating_sub(x), h: b.saturating_sub(y) }
    }
}

/// What a cell shows.
#[derive(Clone, Debug, PartialEq, Eq)]
pub enum Sym {
    Char(char),
    /// A base character plus combining marks.
    Cluster(Box<str>),
    /// Right half of a wide character in the cell to the left.
    WideTail,
    /// Part of a text-sizing run (see [`SizedRun`]); drawn by the run, not per cell.
    Covered,
}

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct Cell {
    pub sym: Sym,
    pub style: Style,
}

impl Cell {
    pub fn blank() -> Cell {
        Cell { sym: Sym::Char(' '), style: Style::new() }
    }
}

/// A piece of text drawn with OSC 66 at (x, y), covering `sizing.cells(text)` cells.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct SizedRun {
    pub x: u16,
    pub y: u16,
    pub text: String,
    pub sizing: Sizing,
    pub style: Style,
}

impl SizedRun {
    pub fn rect(&self) -> Rect {
        let (w, h) = self.sizing.cells(&self.text);
        Rect::new(self.x, self.y, w, h)
    }
}

/// A grid of cells plus the sized runs laid over it. Later writes win: writing into a wide
/// character's half or into a sized run's area clears that character or run first.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct Buffer {
    pub w: u16,
    pub h: u16,
    cells: Vec<Cell>,
    runs: Vec<SizedRun>,
}

impl Buffer {
    pub fn new(w: u16, h: u16) -> Buffer {
        Buffer { w, h, cells: vec![Cell::blank(); w as usize * h as usize], runs: Vec::new() }
    }

    pub fn area(&self) -> Rect {
        Rect::new(0, 0, self.w, self.h)
    }

    fn idx(&self, x: u16, y: u16) -> usize {
        y as usize * self.w as usize + x as usize
    }

    pub fn get(&self, x: u16, y: u16) -> Option<&Cell> {
        if x < self.w && y < self.h {
            Some(&self.cells[self.idx(x, y)])
        } else {
            None
        }
    }

    pub fn runs(&self) -> &[SizedRun] {
        &self.runs
    }

    /// Makes (x, y) safe to overwrite: breaks a wide pair or a sized run that uses it.
    fn clear_at(&mut self, x: u16, y: u16) {
        let i = self.idx(x, y);
        match self.cells[i].sym {
            Sym::Covered => {
                if let Some(k) = self.runs.iter().position(|r| r.rect().contains(x, y)) {
                    let r = self.runs.remove(k).rect();
                    for ry in r.y..r.bottom().min(self.h) {
                        for rx in r.x..r.right().min(self.w) {
                            let j = self.idx(rx, ry);
                            self.cells[j] = Cell::blank();
                        }
                    }
                } else {
                    self.cells[i] = Cell::blank();
                }
            }
            Sym::WideTail => {
                self.cells[i].sym = Sym::Char(' ');
                if x > 0 {
                    self.cells[i - 1].sym = Sym::Char(' ');
                }
            }
            _ => {
                if x + 1 < self.w && self.cells[i + 1].sym == Sym::WideTail {
                    self.cells[i + 1].sym = Sym::Char(' ');
                }
            }
        }
    }

    /// Writes one character; returns the columns it took (0 for a combining mark that joined
    /// the previous cell, or when out of bounds).
    pub fn put_char(&mut self, x: u16, y: u16, c: char, style: Style) -> u16 {
        if x >= self.w || y >= self.h {
            return 0;
        }
        let cw = char_width(c);
        if cw == 0 {
            if x > 0 {
                let i = self.idx(x - 1, y);
                let joined = match &self.cells[i].sym {
                    Sym::Char(b) => Some(format!("{b}{c}")),
                    Sym::Cluster(s) => Some(format!("{s}{c}")),
                    _ => None,
                };
                if let Some(s) = joined {
                    self.cells[i].sym = Sym::Cluster(s.into_boxed_str());
                }
            }
            return 0;
        }
        self.clear_at(x, y);
        let i = self.idx(x, y);
        if cw == 2 {
            if x + 1 >= self.w {
                self.cells[i] = Cell { sym: Sym::Char(' '), style };
                return 1;
            }
            self.clear_at(x + 1, y);
            self.cells[i] = Cell { sym: Sym::Char(c), style };
            self.cells[i + 1] = Cell { sym: Sym::WideTail, style };
            return 2;
        }
        let c = if c == '\t' { ' ' } else { c };
        self.cells[i] = Cell { sym: Sym::Char(c), style };
        1
    }

    /// Writes `s` from (x, y), stopping before column `max_x`. Returns the column after the
    /// last character written. Control characters are skipped.
    pub fn put_str(&mut self, x: u16, y: u16, s: &str, style: Style, max_x: u16) -> u16 {
        let max_x = max_x.min(self.w);
        let mut cx = x;
        for c in s.chars() {
            if c.is_control() && c != '\t' {
                continue;
            }
            let cw = char_width(c) as u16;
            if cx + cw > max_x {
                break;
            }
            cx += self.put_char(cx, y, c, style);
        }
        cx
    }

    /// Paints `rect` with spaces in `style`.
    pub fn fill(&mut self, rect: Rect, style: Style) {
        let r = rect.intersect(&self.area());
        for y in r.y..r.bottom() {
            for x in r.x..r.right() {
                self.put_char(x, y, ' ', style);
            }
        }
    }

    /// Adds a sized run. Returns its rect, or `None` when it does not fit inside the buffer.
    pub fn put_run(&mut self, run: SizedRun) -> Option<Rect> {
        let r = run.rect();
        if r.is_empty() || r.right() > self.w || r.bottom() > self.h {
            return None;
        }
        for y in r.y..r.bottom() {
            for x in r.x..r.right() {
                self.clear_at(x, y);
            }
        }
        for y in r.y..r.bottom() {
            for x in r.x..r.right() {
                let i = self.idx(x, y);
                self.cells[i] = Cell { sym: Sym::Covered, style: run.style };
            }
        }
        self.runs.push(run);
        Some(r)
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn wide_pairs_break_cleanly() {
        let mut b = Buffer::new(4, 1);
        assert_eq!(b.put_char(1, 0, '字', Style::new()), 2);
        assert_eq!(b.get(2, 0).unwrap().sym, Sym::WideTail);
        b.put_char(2, 0, 'x', Style::new());
        assert_eq!(b.get(1, 0).unwrap().sym, Sym::Char(' '));
        assert_eq!(b.get(2, 0).unwrap().sym, Sym::Char('x'));
    }

    #[test]
    fn writing_into_run_removes_it() {
        let mut b = Buffer::new(10, 3);
        let r = b
            .put_run(SizedRun {
                x: 0,
                y: 0,
                text: "ab".into(),
                sizing: Sizing::scale(2),
                style: Style::new(),
            })
            .unwrap();
        assert_eq!(r, Rect::new(0, 0, 4, 2));
        assert_eq!(b.get(3, 1).unwrap().sym, Sym::Covered);
        b.put_char(3, 1, 'z', Style::new());
        assert!(b.runs().is_empty());
        assert_eq!(b.get(0, 0).unwrap().sym, Sym::Char(' '));
    }

    #[test]
    fn run_that_does_not_fit_is_refused() {
        let mut b = Buffer::new(5, 2);
        assert!(b
            .put_run(SizedRun {
                x: 2,
                y: 0,
                text: "abc".into(),
                sizing: Sizing::scale(2),
                style: Style::new()
            })
            .is_none());
    }

    #[test]
    fn put_str_clips() {
        let mut b = Buffer::new(5, 1);
        assert_eq!(b.put_str(2, 0, "hello", Style::new(), 5), 5);
        assert_eq!(b.get(4, 0).unwrap().sym, Sym::Char('l'));
    }
}
