//! Turns successive frames into the smallest escape stream this renderer knows how to make:
//! changed cells only, changed sized runs only, changed kitty placements only.

use std::collections::HashSet;
use std::fmt::Write;

use super::escapes::{self, Crop};
use super::{Buffer, Style, Sym};
use crate::picture::Picture;

/// One kitty picture on screen. `(pic.id(), pid)` names the placement: drawing the same pair
/// again next frame with other fields moves/re-crops it in place, leaving it out deletes it.
#[derive(Clone, Debug)]
pub struct Placement {
    pub pic: Picture,
    /// Placement id, unique per picture (1..). Two placements of one picture need two ids.
    pub pid: u32,
    /// Top-left cell.
    pub col: u16,
    pub row: u16,
    /// Pixel offset inside that cell; must be smaller than the cell size.
    pub px_x: u16,
    pub px_y: u16,
    /// Display box in cells (the picture is scaled into it); 0 = natural pixel size.
    pub cols: u16,
    pub rows: u16,
    /// Source rectangle in picture pixels (None = whole picture).
    pub crop: Option<Crop>,
    /// Stacking: negative draws under text, positive over it.
    pub z: i32,
}

impl Placement {
    /// Whole picture at its natural size at `(col, row)`, under the text (z = -1).
    pub fn new(pic: &Picture, col: u16, row: u16) -> Placement {
        Placement {
            pic: pic.clone(),
            pid: 1,
            col,
            row,
            px_x: 0,
            px_y: 0,
            cols: 0,
            rows: 0,
            crop: None,
            z: -1,
        }
    }

    /// Positions the picture at an absolute pixel point on a grid of `cell_w`×`cell_h` pixels:
    /// splits it into a cell plus the in-cell offset. For motion a few pixels per frame.
    pub fn at_px(mut self, x: u32, y: u32, cell_w: u16, cell_h: u16) -> Placement {
        let cw = cell_w.max(1) as u32;
        let ch = cell_h.max(1) as u32;
        self.col = (x / cw) as u16;
        self.row = (y / ch) as u16;
        self.px_x = (x % cw) as u16;
        self.px_y = (y % ch) as u16;
        self
    }

    pub fn pid(mut self, pid: u32) -> Placement {
        self.pid = pid;
        self
    }
    pub fn z(mut self, z: i32) -> Placement {
        self.z = z;
        self
    }
    pub fn fit(mut self, cols: u16, rows: u16) -> Placement {
        self.cols = cols;
        self.rows = rows;
        self
    }
    pub fn crop(mut self, crop: Crop) -> Placement {
        self.crop = Some(crop);
        self
    }

    fn key(&self) -> (u32, u32) {
        (self.pic.id(), self.pid)
    }
}

impl PartialEq for Placement {
    fn eq(&self, o: &Placement) -> bool {
        self.pic.id() == o.pic.id()
            && self.pid == o.pid
            && self.col == o.col
            && self.row == o.row
            && self.px_x == o.px_x
            && self.px_y == o.px_y
            && self.cols == o.cols
            && self.rows == o.rows
            && self.crop == o.crop
            && self.z == o.z
    }
}

/// Keeps the last frame and writes only what changed.
#[derive(Default)]
pub struct Renderer {
    prev: Option<Buffer>,
    prev_places: Vec<Placement>,
    uploaded: HashSet<u32>,
    /// zlib-compress picture uploads (`o=z`).
    pub compress: bool,
    /// Wrap each frame in synchronized output (mode 2026).
    pub sync: bool,
}

impl Renderer {
    pub fn new() -> Renderer {
        Renderer { compress: true, sync: true, ..Default::default() }
    }

    /// Forget the screen: the next frame clears and redraws everything (after a resize, or
    /// when something else wrote to the terminal).
    pub fn invalidate(&mut self) {
        self.prev = None;
    }

    /// Frees a picture's data in the terminal; it is uploaded again if drawn later.
    pub fn forget(&mut self, out: &mut String, pic_id: u32) {
        if self.uploaded.remove(&pic_id) {
            escapes::kitty_delete_image(out, pic_id);
        }
        self.prev_places.retain(|p| p.pic.id() != pic_id);
    }

    /// Appends the escapes that turn the previous frame into `buf` + `places`.
    pub fn render(&mut self, buf: &Buffer, places: &[Placement], out: &mut String) {
        if self.sync {
            out.push_str("\x1b[?2026h");
        }
        let mut pen: Option<Style> = None;
        let mut cur: Option<(u16, u16)> = None;

        let fresh = match &self.prev {
            Some(p) => p.w != buf.w || p.h != buf.h,
            None => true,
        };
        if fresh {
            out.push_str("\x1b[0m\x1b[H\x1b[2J");
            pen = Some(Style::new());
            cur = Some((0, 0));
            for p in self.prev_places.drain(..) {
                escapes::kitty_delete_placement(out, p.pic.id(), p.pid);
            }
            self.prev = Some(Buffer::new(buf.w, buf.h));
        }
        let prev = self.prev.as_ref().expect("prev set above");

        // Cells.
        for y in 0..buf.h {
            let mut x = 0;
            while x < buf.w {
                let new = buf.get(x, y).expect("in bounds");
                let old = prev.get(x, y).expect("same size");
                if new == old || matches!(new.sym, Sym::Covered | Sym::WideTail) {
                    x += 1;
                    continue;
                }
                if cur != Some((x, y)) {
                    let _ = write!(out, "\x1b[{};{}H", y + 1, x + 1);
                }
                if pen != Some(new.style) {
                    new.style.write_sgr(out);
                    pen = Some(new.style);
                }
                let wide = x + 1 < buf.w && buf.get(x + 1, y).map(|c| &c.sym) == Some(&Sym::WideTail);
                match &new.sym {
                    Sym::Char(c) => out.push(*c),
                    Sym::Cluster(s) => out.push_str(s),
                    _ => {}
                }
                let adv = if wide { 2 } else { 1 };
                x += adv;
                // At the right edge the cursor sits in the pending-wrap state; do not trust it.
                cur = if x < buf.w { Some((x, y)) } else { None };
            }
        }

        // Sized runs: any run not identical to one in the previous frame is written again.
        for run in buf.runs() {
            if prev.runs().contains(run) {
                continue;
            }
            let _ = write!(out, "\x1b[{};{}H", run.y + 1, run.x + 1);
            if pen != Some(run.style) {
                run.style.write_sgr(out);
                pen = Some(run.style);
            }
            escapes::osc66(out, run.sizing, &run.text);
            cur = None;
        }

        // Pictures.
        for old in &self.prev_places {
            if !places.iter().any(|p| p.key() == old.key()) {
                escapes::kitty_delete_placement(out, old.pic.id(), old.pid);
            }
        }
        for p in places {
            if self.prev_places.iter().any(|o| o == p) {
                continue;
            }
            let id = p.pic.id();
            if self.uploaded.insert(id) {
                escapes::kitty_transmit(out, id, p.pic.width(), p.pic.height(), p.pic.rgba(), self.compress);
            }
            if cur != Some((p.col, p.row)) {
                let _ = write!(out, "\x1b[{};{}H", p.row + 1, p.col + 1);
                cur = Some((p.col, p.row));
            }
            escapes::kitty_place(out, id, p.pid, p.px_x, p.px_y, p.cols, p.rows, p.crop, p.z);
        }

        if self.sync {
            out.push_str("\x1b[?2026l");
        }
        self.prev = Some(buf.clone());
        self.prev_places = places.to_vec();
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::render::{Rgb, SizedRun, Sizing};

    fn r() -> Renderer {
        Renderer { compress: false, sync: true, ..Default::default() }
    }

    #[test]
    fn first_frame_clears_then_writes_nonblank() {
        let mut b = Buffer::new(4, 2);
        b.put_str(0, 0, "ab", Style::new(), 4);
        b.put_str(1, 1, "c", Style::new().bold(), 4);
        let mut out = String::new();
        r().render(&b, &[], &mut out);
        assert_eq!(out, "\x1b[?2026h\x1b[0m\x1b[H\x1b[2Jab\x1b[2;2H\x1b[0;1mc\x1b[?2026l");
    }

    #[test]
    fn second_frame_writes_only_changes() {
        let mut rr = r();
        let mut b = Buffer::new(6, 2);
        b.put_str(0, 0, "hello", Style::new(), 6);
        let mut out = String::new();
        rr.render(&b, &[], &mut out);
        b.put_char(1, 0, 'a', Style::new().fg(Rgb(255, 0, 0)));
        b.put_char(2, 1, 'z', Style::new());
        out.clear();
        rr.render(&b, &[], &mut out);
        assert_eq!(out, "\x1b[?2026h\x1b[1;2H\x1b[0;38;2;255;0;0ma\x1b[2;3H\x1b[0mz\x1b[?2026l");
        out.clear();
        rr.render(&b, &[], &mut out);
        assert_eq!(out, "\x1b[?2026h\x1b[?2026l");
    }

    #[test]
    fn wide_char_advances_two() {
        let mut b = Buffer::new(5, 1);
        b.put_str(0, 0, "字x", Style::new(), 5);
        let mut out = String::new();
        let mut rr = r();
        rr.sync = false;
        rr.render(&b, &[], &mut out);
        assert_eq!(out, "\x1b[0m\x1b[H\x1b[2J字x");
    }

    #[test]
    fn sized_run_written_once_and_erased_by_cells() {
        let mut rr = r();
        rr.sync = false;
        let mut b = Buffer::new(8, 3);
        b.put_run(SizedRun { x: 1, y: 0, text: "Hi".into(), sizing: Sizing::scale(2), style: Style::new() });
        let mut out = String::new();
        rr.render(&b, &[], &mut out);
        assert_eq!(out, "\x1b[0m\x1b[H\x1b[2J\x1b[1;2H\x1b]66;s=2;Hi\x1b\\");
        out.clear();
        rr.render(&b, &[], &mut out);
        assert_eq!(out, "");
        // Drop the run: its cells come back as blanks, which overwrites it in the terminal.
        let b2 = Buffer::new(8, 3);
        out.clear();
        rr.render(&b2, &[], &mut out);
        assert_eq!(out, "\x1b[1;2H\x1b[0m    \x1b[2;2H    ");
    }

    #[test]
    fn placements_upload_once_move_and_delete() {
        let pic = Picture::new(1, 1, vec![0, 0, 0, 255]);
        let id = pic.id();
        let mut rr = r();
        rr.sync = false;
        let b = Buffer::new(4, 4);
        let mut out = String::new();
        rr.render(&b, &[Placement::new(&pic, 1, 2)], &mut out);
        assert_eq!(
            out,
            format!(
                "\x1b[0m\x1b[H\x1b[2J\x1b_Ga=t,f=32,s=1,v=1,i={id},q=2,m=0;AAAA/w==\x1b\\\x1b[3;2H\x1b_Ga=p,i={id},p=1,z=-1,C=1,q=2\x1b\\"
            )
        );
        out.clear();
        rr.render(&b, &[Placement::new(&pic, 1, 2).at_px(13, 45, 8, 20)], &mut out);
        assert_eq!(out, format!("\x1b[3;2H\x1b_Ga=p,i={id},p=1,X=5,Y=5,z=-1,C=1,q=2\x1b\\"));
        out.clear();
        rr.render(&b, &[], &mut out);
        assert_eq!(out, format!("\x1b_Ga=d,d=i,i={id},p=1,q=2\x1b\\"));
    }

    #[test]
    fn invalidate_redraws_and_deletes_old_placements() {
        let pic = Picture::new(1, 1, vec![0, 0, 0, 255]);
        let id = pic.id();
        let mut rr = r();
        rr.sync = false;
        let b = Buffer::new(2, 1);
        let mut out = String::new();
        rr.render(&b, &[Placement::new(&pic, 0, 0)], &mut out);
        rr.invalidate();
        out.clear();
        rr.render(&b, &[Placement::new(&pic, 0, 0)], &mut out);
        assert_eq!(
            out,
            format!("\x1b[0m\x1b[H\x1b[2J\x1b_Ga=d,d=i,i={id},p=1,q=2\x1b\\\x1b_Ga=p,i={id},p=1,z=-1,C=1,q=2\x1b\\")
        );
    }
}
