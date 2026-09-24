//! Drawing through the motion hooks, and the shared header every screen has: masthead,
//! picture, name, standfirst, facts strip; plus the notice row and the fixed key slots.

use crate::layout::{self, Header, Tier};
use crate::picture::Picture;
use crate::render::{text_width, Color, Crop, Frame, Placement, Rect, Sizing, Style, Underline};
use crate::term::Key;

use super::scene::{Effect, El, Fx, Scene};

/// Tap actions owned by the shared header and key row. Screens use 100 and up.
pub const A_HOME: u32 = 1;
pub const A_BACK: u32 = 2;
/// Front: `↑ N updates`; Item and Installing: the upstream link.
pub const A_CONTEXT: u32 = 3;
/// Front with the updates filter on: `all`.
pub const A_ALL: u32 = 4;
/// Front: the header body (picture, name, standfirst, facts) opens the item.
pub const A_HEADER: u32 = 5;
pub const A_KEY0: u32 = 10;

/// A frame being drawn by one view: effects applied, elements recorded, and for scrolled
/// content an offset and a clip.
pub struct Paint<'p, 'a> {
    pub f: &'p mut Frame<'a>,
    pub fx: &'p Fx,
    pub scene: &'p mut Scene,
    /// Only rows inside this rect are drawn (pictures are cropped to it).
    pub clip: Option<Rect>,
    /// Logical rows scrolled off the top of `clip`.
    pub scroll: u16,
}

fn fade(style: Style, surface: crate::render::Rgb, alpha: f32) -> Style {
    if alpha >= 1.0 {
        return style;
    }
    let mut s = style;
    let a = alpha.max(0.0);
    if let Color::Rgb(c) = s.fg {
        s.fg = Color::Rgb(surface.mix(c, a));
    }
    if let Color::Rgb(c) = s.bg {
        s.bg = Color::Rgb(surface.mix(c, a));
    }
    if let Color::Rgb(c) = s.ul_color {
        s.ul_color = Color::Rgb(surface.mix(c, a));
    }
    s
}

impl<'p, 'a> Paint<'p, 'a> {
    pub fn new(f: &'p mut Frame<'a>, fx: &'p Fx, scene: &'p mut Scene) -> Paint<'p, 'a> {
        scene.cell = (f.ctx.size.cell_w, f.ctx.size.cell_h);
        Paint { f, fx, scene, clip: None, scroll: 0 }
    }

    /// Screen row for logical row `y`, or None when scrolled away / clipped.
    pub fn row(&self, y: u16) -> Option<u16> {
        let y = y.checked_sub(self.scroll)?;
        match self.clip {
            Some(c) if y < c.y || y >= c.bottom() => None,
            _ => (y < self.f.rows()).then_some(y),
        }
    }

    pub fn cell(&self) -> (u16, u16) {
        (self.f.ctx.size.cell_w.max(1), self.f.ctx.size.cell_h.max(1))
    }

    pub fn note(&mut self, el: El, rect: Rect, pic: Option<(u32, u32)>, text: Option<&str>) {
        if let Some(e) = self.scene.elements.iter_mut().find(|e| e.el == el) {
            let r = e.rect;
            let (x, y) = (r.x.min(rect.x), r.y.min(rect.y));
            e.rect = Rect::new(x, y, r.right().max(rect.right()) - x, r.bottom().max(rect.bottom()) - y);
            if e.picture.is_none() {
                e.picture = pic;
            }
            if e.text.is_none() {
                e.text = text.map(str::to_string);
            }
        } else {
            self.scene.push(el, rect, pic, text);
        }
    }

    /// Text at logical (x, y) as part of `el`. Returns the column after it (as if drawn, even
    /// when clipped or hidden, so layouts do not jump).
    pub fn text(&mut self, el: El, x: u16, y: u16, s: &str, style: Style) -> u16 {
        self.text_clip(el, x, y, s, style, u16::MAX)
    }

    pub fn text_clip(&mut self, el: El, x: u16, y: u16, s: &str, style: Style, max_x: u16) -> u16 {
        let w = text_width(s) as u16;
        let end = x.saturating_add(w).min(max_x.max(x));
        let e = self.fx.get(el);
        if let Some(sy) = self.row(y) {
            self.note(el, Rect::new(x, sy, end - x, 1), None, Some(s));
            if e.alpha > 0.0 {
                let st = fade(style, self.f.pal().surface, e.alpha);
                self.f.text_clip(x, sy, s, st, max_x.min(self.f.cols()));
            }
        }
        end
    }

    /// Text centred in `within` on logical row `y`; returns its start column.
    pub fn centred(&mut self, el: El, within: Rect, y: u16, s: &str, style: Style) -> u16 {
        let x = layout::centre_x(within, text_width(s) as u16);
        self.text_clip(el, x, y, s, style, within.right());
        x
    }

    /// Text ending just before column `right`; returns its start column.
    pub fn right(&mut self, el: El, right: u16, y: u16, s: &str, style: Style) -> u16 {
        let x = right.saturating_sub(text_width(s) as u16);
        self.text_clip(el, x, y, s, style, right);
        x
    }

    /// Fills logical `rect` (clipped row by row) as part of `el`.
    pub fn fill(&mut self, el: El, rect: Rect, style: Style) {
        let e = self.fx.get(el);
        if e.alpha <= 0.0 {
            return;
        }
        for dy in 0..rect.h {
            if let Some(sy) = self.row(rect.y + dy) {
                self.note(el, Rect::new(rect.x, sy, rect.w, 1), None, None);
                let st = fade(style, self.f.pal().surface, e.alpha);
                self.f.fill(Rect::new(rect.x, sy, rect.w, 1), st);
            }
        }
    }

    pub fn hline(&mut self, el: El, x0: u16, x1: u16, y: u16, ch: char, style: Style) {
        let e = self.fx.get(el);
        if let Some(sy) = self.row(y) {
            self.note(el, Rect::new(x0, sy, x1.saturating_sub(x0), 1), None, None);
            if e.alpha > 0.0 {
                self.f.hline(x0, x1, sy, ch, fade(style, self.f.pal().surface, e.alpha));
            }
        }
    }

    /// OSC 66 text (plain when unsupported) at logical (x, y); returns the cells it covers.
    pub fn sized(&mut self, el: El, x: u16, y: u16, s: &str, sizing: Sizing, style: Style) -> Rect {
        let e = self.fx.get(el);
        let Some(sy) = self.row(y) else {
            let (w, h) =
                if self.f.ctx.caps.text_sizing { sizing.cells(s) } else { (text_width(s) as u16, 1) };
            return Rect::new(x, y, w, h);
        };
        // A sized run must fit inside the clip.
        let (_, h) = sizing.cells(s);
        let fits = self.clip.is_none_or(|c| sy + h <= c.bottom());
        let sizing = if fits { sizing } else { Sizing::scale(1) };
        let st = fade(style, self.f.pal().surface, e.alpha);
        let r = if e.alpha > 0.0 {
            self.f.sized(x, sy, s, sizing, st)
        } else {
            let (w, h) =
                if self.f.ctx.caps.text_sizing { sizing.cells(s) } else { (text_width(s) as u16, 1) };
            Rect::new(x, sy, w, h)
        };
        self.note(el, r, None, Some(s));
        r
    }

    /// A picture at its natural size, top-left at logical cell (col, row) plus a pixel
    /// offset `off` (x, y). Cropped to the clip; hidden by a faded effect. False when the
    /// terminal cannot show pictures (draw a stand-in), also when it is entirely clipped.
    pub fn picture(&mut self, el: El, pic: &Picture, col: u16, row: u16, off: (u32, u32), pid: u32) -> bool {
        let (off_x, off_y) = off;
        if !self.f.ctx.caps.kitty_graphics {
            return false;
        }
        let e = self.fx.get(el);
        let (cw, ch) = (self.f.ctx.size.cell_w.max(1) as i64, self.f.ctx.size.cell_h.max(1) as i64);
        let mut x = col as i64 * cw + off_x as i64;
        let mut top = (row as i64 - self.scroll as i64) * ch + off_y as i64;
        let (pw, ph) = (pic.width() as i64, pic.height() as i64);
        let mut crop = Crop { x: 0, y: 0, w: pic.width(), h: pic.height() };
        let (clip_top, clip_bot) = match self.clip {
            Some(c) => (c.y as i64 * ch, c.bottom() as i64 * ch),
            None => (0, self.f.rows() as i64 * ch),
        };
        if top < clip_top {
            let cut = (clip_top - top).min(ph);
            crop.y = cut as u32;
            crop.h = crop.h.saturating_sub(cut as u32);
            top = clip_top;
        }
        let bottom = top + crop.h as i64;
        if bottom > clip_bot {
            crop.h = crop.h.saturating_sub((bottom - clip_bot) as u32);
        }
        if x < 0 {
            crop.x = (-x).min(pw) as u32;
            crop.w = crop.w.saturating_sub(crop.x);
            x = 0;
        }
        let cols = self.f.cols() as i64 * cw;
        if x + crop.w as i64 > cols {
            crop.w = (cols - x).max(0) as u32;
        }
        let (c, r) = pic.cells(cw as u16, ch as u16);
        if crop.w == 0 || crop.h == 0 || e.alpha < 0.5 {
            // Hidden by its effect (or clipped away): still recorded where it rests, so motion
            // knows the element is a picture and how big it is.
            if let Some(sy) = self.row(row) {
                self.note(el, Rect::new(col, sy, c, r), Some((pic.id(), pid)), None);
            }
            return true;
        }
        let rect = Rect::new(
            (x / cw) as u16,
            (top / ch) as u16,
            crop.w.div_ceil(cw as u32) as u16,
            crop.h.div_ceil(ch as u32) as u16,
        );
        let mut p =
            Placement::new(pic, 0, 0).at_px(x as u32, top as u32, cw as u16, ch as u16).pid(pid).z(-1);
        if crop != (Crop { x: 0, y: 0, w: pic.width(), h: pic.height() }) {
            p = p.crop(crop);
        }
        self.f.picture(p);
        self.note(el, rect, Some((pic.id(), pid)), None);
        true
    }

    /// A tap region at logical `rect` (clipped to what is visible).
    pub fn hit(&mut self, rect: Rect, action: u32) {
        for dy in 0..rect.h {
            if let Some(sy) = self.row(rect.y + dy) {
                self.f.hit(Rect::new(rect.x, sy, rect.w, 1), action);
            }
        }
    }

    pub fn link(&mut self, rect: Rect, url: &str) {
        if let Some(sy) = self.row(rect.y) {
            self.f.link(Rect::new(rect.x, sy, rect.w, 1), url);
        }
    }
}

/// The masthead's two shapes.
#[derive(Clone, Debug, PartialEq, Eq)]
pub enum Masthead {
    /// Front: the wordmark (tap: home) over the masthead row and the blank under it, `↑ N
    /// updates` or `updates · all` right-aligned on the first, `N items · M installed` on the
    /// second.
    Front { updates: usize, filter: bool, items: usize, installed: usize },
    /// Item and Installing: `‹ apps` (tap: back) and the upstream link, or `our setup`.
    Page { repo: Option<String>, setup: bool },
}

/// The facts strip: `[state] version [→ new] · more…`.
#[derive(Clone, Debug, Default, PartialEq, Eq)]
pub struct Facts {
    /// `installed`, `installing`, `updating`, `removing`, `failed`.
    pub state: Option<String>,
    pub version: String,
    /// The version an update brings: `version` is struck through, this follows an arrow.
    pub new: Option<String>,
    /// Licence, author, size, `starred`, as known.
    pub more: Vec<String>,
}

/// Everything the shared header needs from the current view. The picture is already
/// fitted to the header's picture rows and faded.
pub struct HeaderContent<'a> {
    pub masthead: Masthead,
    pub picture: Option<Picture>,
    pub name: &'a str,
    pub standfirst: &'a str,
    pub facts: Facts,
}

/// Draws the masthead, the picture, the name, the standfirst and the facts strip.
pub fn draw_header(p: &mut Paint, h: &Header, c: &HeaderContent) {
    let pal = *p.f.pal();
    let (cw, ch) = p.cell();
    let x0 = h.gutter;
    let right = h.content.right();
    let y = h.masthead;

    // Masthead.
    match &c.masthead {
        Masthead::Front { updates, filter, items, installed } => {
            // The wordmark: `tlstore` in the script face over the masthead row and the blank
            // under it (two rows of pixels) where the header has that blank; one row of pixels
            // in Compact, where it does not. Text when the terminal cannot show pictures.
            let mark_rows: u16 = if h.name.y >= y + 2 { 2 } else { 1 };
            let word = (p.f.ctx.cell_known && p.f.ctx.caps.kitty_graphics)
                .then(|| p.f.ctx.pics.script_word("tlstore", mark_rows as u32 * ch as u32, pal.ink))
                .filter(|w| w.width() <= (h.content.w / 2) as u32 * cw as u32);
            let placed = word.as_ref().is_some_and(|w| p.picture(El::Mark, w, x0, y, (0, 0), 1));
            let mark_end = match (&word, placed) {
                (Some(w), true) => {
                    let (mcols, _) = w.cells(cw, ch);
                    p.note(El::Mark, Rect::new(x0, y, mcols, mark_rows), Some((w.id(), 1)), None);
                    x0 + mcols
                }
                _ => p.text(El::Mark, x0, y, "tlstore", pal.ink_s().bold()),
            };
            p.hit(Rect::new(x0, y, mark_end - x0, mark_rows), A_HOME);
            if mark_rows == 2 && *items > 0 {
                let count =
                    format!("{} item{} · {} installed", items, if *items == 1 { "" } else { "s" }, installed);
                let room = right.saturating_sub(mark_end + 2);
                if text_width(&count) as u16 <= room {
                    p.right(El::Context, right, y + 1, &count, pal.dim_s());
                }
            }
            if *filter {
                let all_x = p.right(El::Context, right, y, "all", pal.dim_s().underline(Underline::Single));
                p.hit(Rect::new(all_x, y, right - all_x, 1), A_ALL);
                let dot = p.right(El::Context, all_x, y, " · ", pal.dim_s());
                p.right(El::Context, dot, y, "updates", pal.accent_s());
            } else if *updates > 0 {
                let text = format!("↑ {updates} update{}", if *updates == 1 { "" } else { "s" });
                let cx = p.right(El::Context, right, y, &text, pal.accent_s());
                p.hit(Rect::new(cx, y, right - cx, 1), A_CONTEXT);
            }
        }
        Masthead::Page { repo, setup } => {
            let e = p.text(El::Mark, x0, y, "‹", pal.accent_s());
            let e = p.text(El::Mark, e + 1, y, "apps", pal.dim_s());
            p.hit(Rect::new(x0, y, e - x0 + 1, 1), A_BACK);
            match repo {
                Some(r) => {
                    let room = right.saturating_sub(e + 2);
                    let text = layout::fit_line(&format!("{r} ↗"), room);
                    let st = pal.accent_s().underline(Underline::Curly).ul_color(pal.accent);
                    let cx = p.right(El::Context, right, y, &text, st);
                    let rect = Rect::new(cx, y, right - cx, 1);
                    p.link(rect, &format!("https://github.com/{r}"));
                    p.hit(rect, A_CONTEXT);
                }
                None if *setup => {
                    p.right(El::Context, right, y, "our setup", pal.dim_s());
                }
                None => {}
            }
        }
    }

    // Picture: contain-fitted already; centred in its rows.
    if let (Some(r), Some(pic)) = (h.picture, &c.picture) {
        let box_w = r.w as u32 * cw as u32;
        let box_h = r.h as u32 * ch as u32;
        let off_x = box_w.saturating_sub(pic.width()) / 2;
        let off_y = box_h.saturating_sub(pic.height()) / 2;
        let x_px = r.x as u32 * cw as u32 + off_x;
        p.picture(El::Picture, pic, (x_px / cw as u32) as u16, r.y, (x_px % cw as u32, off_y), 1);
    }

    // Name: the script face at exactly the name rows' height, else sized mono.
    let name = c.name;
    if !name.is_empty() {
        let n = h.name;
        let pic_ok = p.f.ctx.caps.kitty_graphics && p.f.ctx.cell_known;
        let word = pic_ok
            .then(|| p.f.ctx.pics.script_word(name, n.h as u32 * ch as u32, pal.ink))
            .filter(|w| w.width() <= n.w as u32 * cw as u32);
        let placed = word.as_ref().is_some_and(|w| p.picture(El::Name, w, n.x, n.y, (0, 0), 1));
        if !placed {
            let tw = text_width(name) as u16;
            let mut scale: u16 = if h.tier == Tier::Compact { 2 } else { 3 };
            if h.narrow {
                scale = scale.min(2);
            }
            while scale > 1 && tw * scale > n.w {
                scale -= 1;
            }
            let scale = if p.f.ctx.caps.text_sizing { scale } else { 1 };
            let shown = if tw > n.w { layout::fit_line(name, n.w) } else { name.to_string() };
            let y = n.bottom().saturating_sub(scale.max(1));
            p.sized(El::Name, n.x, y, &shown, Sizing::scale(scale as u8), pal.ink_s().bold());
        }
    }

    // Standfirst.
    if !c.standfirst.is_empty() {
        let line = layout::fit_line(c.standfirst, h.content.w);
        p.text(El::Standfirst, x0, h.standfirst, &line, pal.dim_s().italic());
    }

    // Facts strip: whole pieces only; when they do not fit, licence, author and size give
    // way from the end (`starred` stays).
    let fy = h.facts;
    let f = &c.facts;
    let mut head: Vec<(String, Style)> = Vec::new();
    if let Some(state) = &f.state {
        let st = if state == "failed" { pal.ink_s().bold() } else { pal.dim_s() };
        head.push((state.clone(), st));
        if !f.version.is_empty() {
            head.push((" ".into(), pal.dim_s()));
        }
    }
    if !f.version.is_empty() {
        match &f.new {
            Some(new) => {
                head.push((f.version.clone(), pal.dim_s().strike()));
                head.push((" → ".into(), pal.dim_s()));
                head.push((new.clone(), pal.accent_s()));
            }
            None => head.push((f.version.clone(), pal.dim_s())),
        }
    }
    let head_w: u16 = head.iter().map(|(t, _)| text_width(t) as u16).sum();
    let (starred, mut more): (Vec<&String>, Vec<&String>) = f.more.iter().partition(|m| *m == "starred");
    let width = |more: &[&String]| -> u16 {
        let mut w = head_w;
        for m in more.iter().chain(starred.iter()) {
            w += text_width(m) as u16 + if w > 0 { 3 } else { 0 };
        }
        w
    };
    while !more.is_empty() && width(&more) > h.content.w {
        more.pop();
    }
    let mut x = x0;
    for (t, st) in &head {
        x = p.text_clip(El::Facts, x, fy, t, *st, right);
    }
    for m in more.iter().chain(starred.iter()) {
        let sep = if x > x0 { " · " } else { "" };
        x = p.text_clip(El::Facts, x, fy, &format!("{sep}{m}"), pal.dim_s(), right);
    }
}

/// One key-row slot; tapping it sends `key`. An `off` slot stays in place, dim, and is not
/// tappable.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct Slot {
    pub label: &'static str,
    pub words: &'static str,
    pub key: Key,
    pub on: bool,
}

impl Slot {
    pub fn new(label: &'static str, words: &'static str, key: Key) -> Slot {
        Slot { label, words, key, on: true }
    }
    pub fn off(mut self) -> Slot {
        self.on = false;
        self
    }
    /// On when `on`, else dim and not tappable.
    pub fn when(mut self, on: bool) -> Slot {
        self.on = on;
        self
    }
}

/// The five fixed slots.
pub type Slots = [Option<Slot>; 5];

/// Draws the key row: five fixed slots, label in the accent, words dim (everything dim when
/// the slot is off); words are cut so a column stays free before the next slot. Taps on an
/// `on` slot are `A_KEY0 + index`.
pub fn draw_keys(p: &mut Paint, h: &Header, slots: &Slots) {
    let pal = *p.f.pal();
    let cols = layout::key_slots(h.cols);
    let rooms = layout::key_rooms(h.cols);
    let y = h.keys;
    for (i, slot) in slots.iter().enumerate() {
        let Some(s) = slot else { continue };
        let x = cols[i];
        let label_st = if s.on { pal.accent_s() } else { pal.dim_s() };
        let e = p.text(El::Keys, x, y, s.label, label_st);
        let gap = u16::from(i + 1 < slots.len());
        let words_w = rooms[i].saturating_sub(text_width(s.label) as u16 + 1 + gap);
        let words = layout::fit_line(s.words, words_w);
        let end = p.text(El::Keys, e + 1, y, &words, pal.dim_s());
        if s.on {
            p.hit(Rect::new(x, y, end - x, 1), A_KEY0 + i as u32);
        }
    }
}

/// One line on the notice row, dim italic, at the gutter.
pub fn draw_notice(p: &mut Paint, h: &Header, text: &str) {
    let pal = *p.f.pal();
    let line = layout::fit_line(text, h.content.w);
    p.text(El::Notice, h.gutter, h.notice, &line, pal.dim_s().italic());
}

impl Effect {
    /// The effect a hidden picture gets (kitty has no alpha per placement).
    pub fn hidden() -> Effect {
        Effect::alpha(0.0)
    }
}
