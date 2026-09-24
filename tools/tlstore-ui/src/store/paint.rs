//! Drawing through the motion hooks, and the shared frame every screen has: masthead, rule,
//! hero, notice and key row.

use crate::layout::{self, Regions, Tier};
use crate::picture::Picture;
use crate::render::{text_width, Color, Crop, Frame, Placement, Rect, Sizing, Style, Underline};
use crate::term::Key;

use super::scene::{Effect, El, Fx, Scene};

/// Tap actions owned by the shared frame. Screens use 100 and up.
pub const A_HOME: u32 = 1;
pub const A_CONTEXT: u32 = 2;
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
    if let Color::Rgb(c) = s.fg {
        s.fg = Color::Rgb(surface.mix(c, alpha.max(0.0)));
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

    fn shift(&self, e: &Effect, x: u16, y: u16) -> Option<(u16, u16)> {
        let (cw, ch) = (self.f.ctx.size.cell_w.max(1) as i32, self.f.ctx.size.cell_h.max(1) as i32);
        let nx = x as i32 + (e.dx as f32 / cw as f32).round() as i32;
        let ny = y as i32 + (e.dy as f32 / ch as f32).round() as i32;
        (nx >= 0 && ny >= 0).then_some((nx as u16, ny as u16))
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
            if e.alpha > 0.0 && e.shown > 0.0 {
                if let Some((dx, dy)) = self.shift(&e, x, sy) {
                    let shown_text: String = match &e.text {
                        Some(t) => t.clone(),
                        None if e.reveal < 1.0 => {
                            let n = (s.chars().count() as f32 * e.reveal.max(0.0)).round() as usize;
                            s.chars().take(n).collect()
                        }
                        None => s.to_string(),
                    };
                    let st = fade(style, self.f.pal().surface, e.alpha);
                    let max = max_x.saturating_add(dx).saturating_sub(x);
                    self.f.text_clip(dx, dy, &shown_text, st, max.min(self.f.cols()));
                }
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
                let mut st = style;
                if e.alpha < 1.0 {
                    if let Color::Rgb(c) = st.bg {
                        st.bg = Color::Rgb(self.f.pal().surface.mix(c, e.alpha));
                    }
                }
                self.f.fill(Rect::new(rect.x, sy, rect.w, 1), st);
            }
        }
    }

    /// Dot leaders from `x0` to `x1`, drawn out from the left by the element's `reveal`.
    pub fn leaders(&mut self, el: El, x0: u16, x1: u16, y: u16, style: Style) {
        let e = self.fx.get(el);
        if let Some(sy) = self.row(y) {
            let span = x1.saturating_sub(x0) as f32;
            let x1 = x0 + (span * e.reveal.min(e.line).clamp(0.0, 1.0)).round() as u16;
            self.f.leaders(x0, x1, sy, fade(style, self.f.pal().surface, e.alpha));
        }
    }

    pub fn hline(&mut self, el: El, x0: u16, x1: u16, y: u16, ch: char, style: Style) {
        let e = self.fx.get(el);
        if let Some(sy) = self.row(y) {
            self.note(el, Rect::new(x0, sy, x1.saturating_sub(x0), 1), None, None);
            let span = x1.saturating_sub(x0) as f32;
            let x1 = x0 + (span * e.reveal.min(e.line).clamp(0.0, 1.0)).round() as u16;
            self.f.hline(x0, x1, sy, ch, fade(style, self.f.pal().surface, e.alpha));
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
        let r = if e.alpha > 0.0 && e.shown > 0.0 {
            self.f.sized(x, sy, s, sizing, st)
        } else {
            Rect::new(x, sy, text_width(s) as u16, 1)
        };
        self.note(el, r, None, Some(s));
        r
    }

    /// A picture at its natural size, top-left at logical cell (col, row) plus a pixel
    /// offset `off` (x, y). Cropped to the clip and by the element's effect. False when the terminal
    /// cannot show pictures (draw a stand-in) — also false when it is entirely clipped.
    pub fn picture(&mut self, el: El, pic: &Picture, col: u16, row: u16, off: (u32, u32), pid: u32) -> bool {
        let (off_x, off_y) = off;
        if !self.f.ctx.caps.kitty_graphics {
            return false;
        }
        let e = self.fx.get(el);
        let (cw, ch) = (self.f.ctx.size.cell_w.max(1) as i64, self.f.ctx.size.cell_h.max(1) as i64);
        let mut x = col as i64 * cw + off_x as i64 + e.dx as i64;
        let mut top = (row as i64 - self.scroll as i64) * ch + off_y as i64 + e.dy as i64;
        let (pw, ph) = (pic.width() as i64, pic.height() as i64);
        let mut crop = Crop { x: 0, y: 0, w: pic.width(), h: pic.height() };
        // Effect: only the top `shown` part.
        crop.h = ((ph as f32) * e.shown.clamp(0.0, 1.0)).round() as u32;
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
        if crop.w == 0 || crop.h == 0 || e.alpha < 0.5 {
            // Hidden by its effect (or clipped away): still recorded where it rests, so motion
            // knows the element is a picture and how big it is.
            if let Some(sy) = self.row(row) {
                let (c, r) = pic.cells(cw as u16, ch as u16);
                self.note(el, Rect::new(col, sy, c, r), Some((pic.id(), pid)), None);
            }
            return true;
        }
        // While an effect moves or crops it, the scene keeps the picture where it rests.
        let rect = match self.row(row) {
            Some(sy) if !e.at_rest() => {
                let (c, r) = pic.cells(cw as u16, ch as u16);
                Rect::new(col, sy, c, r)
            }
            _ => Rect::new(
                (x / cw) as u16,
                (top / ch) as u16,
                crop.w.div_ceil(cw as u32) as u16,
                crop.h.div_ceil(ch as u32) as u16,
            ),
        };
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

/// One key hint on the key row; tapping it sends `key`.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct Hint {
    pub label: &'static str,
    pub words: String,
    pub key: Key,
}

impl Hint {
    pub fn new(label: &'static str, words: &str, key: Key) -> Hint {
        Hint { label, words: words.to_string(), key }
    }
    fn width(&self) -> u16 {
        (text_width(self.label) + 1 + text_width(&self.words)) as u16
    }
}

/// The right-hand masthead item.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct ContextItem {
    pub text: String,
    /// Tappable (drawn in the accent with a double underline).
    pub link: bool,
}

/// Everything the shared frame needs from the current view.
#[derive(Clone, Debug, Default)]
pub struct Chrome {
    pub crumb: String,
    pub context: Option<ContextItem>,
    pub lead: String,
    pub word: String,
    /// Set the word in the script face (a picture at the exact pixel size) where the terminal
    /// allows; otherwise, and for item names, it is sized text.
    pub script: bool,
    pub keys: Vec<Hint>,
}

/// The hints that fit in `width` columns, two spaces apart: dropped from the end, except
/// that a last `esc` or `q` hint (the way out) always stays.
pub fn fit_hints(hints: &[Hint], width: u16) -> Vec<Hint> {
    let mut v: Vec<Hint> = hints.to_vec();
    let total = |v: &[Hint]| v.iter().map(|h| h.width()).sum::<u16>() + 2 * v.len().saturating_sub(1) as u16;
    while v.len() > 1 && total(&v) > width {
        let keep_last = v.last().is_some_and(|h| matches!(h.key, Key::Esc | Key::Char('q')));
        let at = if keep_last { v.len() - 2 } else { v.len() - 1 };
        v.remove(at);
    }
    v
}

/// Draws the masthead, rule, hero, notice and key row. Returns the hints drawn (their taps
/// are `A_KEY0 + index`).
pub fn draw_chrome(p: &mut Paint, r: &Regions, c: &Chrome, notice: Option<&str>) -> Vec<Hint> {
    let pal = *p.f.pal();
    let (cw, ch) = (p.f.ctx.size.cell_w, p.f.ctx.size.cell_h);

    // Masthead: mark, breadcrumb, context.
    let y = r.masthead.y;
    let x0 = r.masthead.x;
    let px = (ch as u32 / 10).max(1);
    // The pixel mark only where cells have a known pixel size (it is drawn pixel for pixel).
    let mark = p.f.ctx.cell_known.then(|| p.f.ctx.pics.mark(px, pal.ink));
    let placed = mark.as_ref().is_some_and(|m| {
        let off_y = (ch as u32).saturating_sub(m.height()) / 2;
        p.picture(El::Mark, m, x0, y, (0, off_y), 1)
    });
    let mark_end = match (&mark, placed) {
        (Some(m), true) => {
            let (mcols, _) = m.cells(cw, ch);
            p.note(El::Mark, Rect::new(x0, y, mcols, 1), Some((m.id(), 1)), None);
            x0 + mcols
        }
        _ => p.text(El::Mark, x0, y, "TLSTORE", pal.ink_s().bold()),
    };
    p.hit(Rect::new(x0, y, mark_end - x0, 1), A_HOME);
    let crumb_x = mark_end + 1;
    let mut crumb_max = r.masthead.right();
    if let Some(ctx) = &c.context {
        let st = if ctx.link {
            pal.accent_s().underline(Underline::Double).ul_color(pal.accent)
        } else {
            pal.dim_s()
        };
        let cx = p.right(El::Context, r.masthead.right(), y, &ctx.text, st);
        if ctx.link {
            p.hit(Rect::new(cx, y, r.masthead.right() - cx, 1), A_CONTEXT);
        }
        crumb_max = cx.saturating_sub(1);
    }
    p.text_clip(El::Crumb, crumb_x, y, &c.crumb, pal.dim_s(), crumb_max);
    p.hline(El::Rule, r.rule.x, r.rule.right(), r.rule.y, '─', pal.rule_s());

    draw_hero(p, r, &c.lead, &c.word, c.script);

    if let Some(n) = notice {
        let ny = r.keys.y.saturating_sub(1);
        if ny > r.hero.bottom() {
            let line = layout::fit_line(n, r.keys.w);
            p.centred(El::Notice, r.keys, ny, &line, pal.dim_s().italic());
        }
    }

    // Key row.
    let shown = fit_hints(&c.keys, r.keys.w);
    let mut x = r.keys.x;
    for (i, h) in shown.iter().enumerate() {
        let start = x;
        x = p.text(El::Keys, x, r.keys.y, h.label, pal.accent_s());
        x = p.text(El::Keys, x + 1, r.keys.y, &h.words, pal.dim_s());
        p.hit(Rect::new(start, r.keys.y, x - start, 1), A_KEY0 + i as u32);
        x += 2;
    }
    shown
}

/// The hero: spaced lead line and the word, both centred (Compact: side by side on one
/// row). The word is the script face as a picture — rasterised at exactly the pixels it is
/// placed at, never scaled — when `script` is set, the terminal shows pictures and told us
/// its cell size; otherwise it is text, sized with OSC 66 where the terminal can.
pub fn draw_hero(p: &mut Paint, r: &Regions, lead: &str, word: &str, script: bool) {
    let pal = *p.f.pal();
    let (cw, ch) = (p.f.ctx.size.cell_w as u32, p.f.ctx.size.cell_h as u32);
    let inner = r.hero;
    let lead_s = layout::spaced_caps(lead);
    let lead_s = if text_width(&lead_s) as u16 > inner.w { lead.to_uppercase() } else { lead_s };
    let lead_w = text_width(&lead_s) as u16;
    let compact = r.tier == Tier::Compact;
    let rows = if compact { 1 } else { r.hero_word.h.max(1) };
    let ctx = &p.f.ctx;
    let pic_ok = script && ctx.caps.kitty_graphics && ctx.cell_known && !word.is_empty();
    let word_pic = pic_ok
        .then(|| p.f.ctx.pics.script_word(word, rows as u32 * ch, pal.ink))
        .filter(|w| w.width() <= inner.w as u32 * cw);
    let word_st = if script { pal.ink_s().italic() } else { pal.ink_s().bold() };
    if compact {
        let word_cols = match &word_pic {
            Some(w) => w.cells(cw as u16, ch as u16).0,
            None => text_width(word) as u16,
        };
        let group = lead_w + 2 + word_cols;
        let (x, show_word) = if group <= inner.w {
            (layout::centre_x(inner, group), true)
        } else {
            (layout::centre_x(inner, lead_w), false)
        };
        let end = p.text_clip(El::HeroLead, x, inner.y, &lead_s, pal.dim_s(), inner.right());
        if show_word {
            let placed =
                word_pic.as_ref().is_some_and(|w| p.picture(El::HeroWord, w, end + 2, inner.y, (0, 0), 1));
            if !placed {
                p.text(El::HeroWord, end + 2, inner.y, word, word_st);
            }
        }
        return;
    }
    p.centred(El::HeroLead, r.hero_lead, r.hero_lead.y, &lead_s, pal.dim_s());
    if let Some(w) = &word_pic {
        let total = inner.w as u32 * cw;
        let x_px = inner.x as u32 * cw + total.saturating_sub(w.width()) / 2;
        if p.picture(El::HeroWord, w, (x_px / cw) as u16, r.hero_word.y, (x_px % cw, 0), 1) {
            return;
        }
    }
    // Sized text: the largest scale up to the hero's rows that fits across.
    let tw = text_width(word) as u16;
    let mut scale = if p.f.ctx.caps.text_sizing { rows.clamp(1, 7) } else { 1 };
    while scale > 1 && tw * scale > inner.w {
        scale -= 1;
    }
    let shown = if tw > inner.w { layout::fit_line(word, inner.w) } else { word.to_string() };
    let x = layout::centre_x(inner, text_width(&shown) as u16 * scale);
    p.sized(El::HeroWord, x, r.hero_word.y, &shown, Sizing::scale(scale as u8), word_st);
}
