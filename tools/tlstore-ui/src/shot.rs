//! Dev-only PNG preview renderer (cargo feature `shot`; `tlstore-ui --shot`). Drives the real
//! [`Router`] headlessly against a store fixture with every capability on and motion off,
//! takes the resting frame and paints it: surface background, cell backgrounds, kitty
//! placements under the text, every glyph from the bundled JetBrains Mono (bold, italic, dim,
//! reverse, underline styles and colours, strikethrough), OSC 66 sized runs at their scale
//! and fraction, then the placements above the text. Cells are a fixed 12×26 px.
//!
//! The font is bundled only under the feature, so the shipped binary never carries it.

use std::cell::RefCell;
use std::io;
use std::path::{Path, PathBuf};
use std::rc::Rc;
use std::sync::atomic::{AtomicU32, Ordering};
use std::time::Duration;

use fontdue::{Font, FontSettings};

use crate::app::{Ctx, Screen};
use crate::palette::Palette;
use crate::render::{Buffer, Color, Frame, Placement, Rgb, SizedRun, Style, Sym, Underline};
use crate::store::proc::Env;
use crate::store::{Router, Verb};
use crate::term::{self, Caps, Event, Key, Size};

/// The fixed cell of a shot, in pixels.
pub const CELL_W: u16 = 12;
pub const CELL_H: u16 = 26;
/// JetBrains Mono advances 0.6 em, so 20 px fills a 12 px column; its 1.32 em line is 26.4 px.
const FONT_PX: f32 = 20.0;

static REGULAR: &[u8] = include_bytes!("../assets/fonts/jetbrainsmono/JetBrainsMono-Regular.ttf");
static BOLD: &[u8] = include_bytes!("../assets/fonts/jetbrainsmono/JetBrainsMono-Bold.ttf");
static ITALIC: &[u8] = include_bytes!("../assets/fonts/jetbrainsmono/JetBrainsMono-Italic.ttf");
static BOLD_ITALIC: &[u8] = include_bytes!("../assets/fonts/jetbrainsmono/JetBrainsMono-BoldItalic.ttf");

const USAGE: &str = "tlstore-ui --shot <cols>x<rows> --screen <spec> --out <file.png> [--store <dir>]
tlstore-ui --shot-all --out <dir> [--store <dir>]

  <spec>  front[:cursor]  front:selected=<a,b>  front:updates
          item:<name>[:scroll]  installing:<name>:<pct>
  <dir>   a store fixture like tests/fixtures/store (the default)
";

// ---------------------------------------------------------------------------
// What to shoot
// ---------------------------------------------------------------------------

/// One screen state, parsed from a `--screen` spec.
#[derive(Clone, Debug, PartialEq, Eq)]
pub enum Spec {
    /// The Apps view: the cursor on row `cursor`, `selected` names marked, the updates
    /// view pushed when `updates`.
    Front { cursor: usize, selected: Vec<String>, updates: bool },
    /// An item's page scrolled `scroll` rows.
    Item { name: String, scroll: u16 },
    /// The Installing view with a job on `name` at `pct` percent.
    Installing { name: String, pct: u8 },
}

impl Spec {
    pub fn parse(s: &str) -> Result<Spec, String> {
        let mut parts = s.split(':');
        let head = parts.next().unwrap_or("");
        match head {
            "front" => {
                let mut spec = Spec::Front { cursor: 0, selected: vec![], updates: false };
                let Spec::Front { cursor, selected, updates } = &mut spec else { unreachable!() };
                for p in parts {
                    if p == "updates" {
                        *updates = true;
                    } else if let Some(list) = p.strip_prefix("selected=") {
                        selected.extend(list.split(',').filter(|n| !n.is_empty()).map(str::to_string));
                    } else {
                        *cursor = p.parse().map_err(|_| format!("bad front part {p:?}"))?;
                    }
                }
                Ok(spec)
            }
            "item" => {
                let name = parts.next().filter(|n| !n.is_empty()).ok_or("item needs a name")?.to_string();
                let scroll = match parts.next() {
                    Some(n) => n.parse().map_err(|_| format!("bad scroll {n:?}"))?,
                    None => 0,
                };
                Ok(Spec::Item { name, scroll })
            }
            "installing" => {
                let name =
                    parts.next().filter(|n| !n.is_empty()).ok_or("installing needs a name")?.to_string();
                let pct = parts.next().ok_or("installing needs a percentage")?;
                let pct = pct.parse().map_err(|_| format!("bad percentage {pct:?}"))?;
                Ok(Spec::Installing { name, pct })
            }
            _ => Err(format!("unknown screen {head:?}")),
        }
    }

    /// A file-name-safe form: `installing:dawn:64` → `installing-dawn-64`.
    pub fn slug(&self) -> String {
        match self {
            Spec::Front { cursor, selected, updates } => {
                let mut s = String::from("front");
                if *cursor > 0 {
                    s.push_str(&format!("-{cursor}"));
                }
                if !selected.is_empty() {
                    s.push_str(&format!("-selected-{}", selected.join("-")));
                }
                if *updates {
                    s.push_str("-updates");
                }
                s
            }
            Spec::Item { name, scroll } => {
                if *scroll > 0 {
                    format!("item-{name}-{scroll}")
                } else {
                    format!("item-{name}")
                }
            }
            Spec::Installing { name, pct } => format!("installing-{name}-{pct}"),
        }
    }
}

// ---------------------------------------------------------------------------
// Driving the router
// ---------------------------------------------------------------------------

static N: AtomicU32 = AtomicU32::new(0);

fn copy_dir(from: &Path, to: &Path) -> io::Result<()> {
    std::fs::create_dir_all(to)?;
    for e in std::fs::read_dir(from)? {
        let e = e?;
        let t = to.join(e.file_name());
        if e.file_type()?.is_dir() {
            copy_dir(&e.path(), &t)?;
        } else {
            std::fs::copy(e.path(), &t)?;
        }
    }
    Ok(())
}

/// The default fixture, in this crate's source tree.
pub fn default_store() -> PathBuf {
    Path::new(env!("CARGO_MANIFEST_DIR")).join("tests/fixtures/store")
}

/// A router on a private copy of the store fixture (its stub script writes logs next to
/// itself). Dropped, the copy is removed.
struct Stage {
    dir: PathBuf,
    ctx: Ctx,
    router: Router,
}

impl Stage {
    fn new(cols: u16, rows: u16, store: &Path) -> io::Result<Stage> {
        let n = N.fetch_add(1, Ordering::Relaxed);
        let dir = std::env::temp_dir().join(format!("tlstore-shot-{}-{n}", std::process::id()));
        let _ = std::fs::remove_dir_all(&dir);
        copy_dir(store, &dir)?;
        let pictures = Path::new(env!("CARGO_MANIFEST_DIR")).join("../../scripts/tlstore/pictures");
        if !dir.join("pics").is_dir() && pictures.is_dir() {
            copy_dir(&pictures, &dir.join("pics"))?;
        }
        std::fs::write(dir.join("gh-signed-in"), "")?;
        let bin = dir.join("bin");
        let sink = Rc::new(RefCell::new(Vec::new()));
        let env = Env::for_tests(
            &bin.join("tlstore"),
            &bin.join("gh"),
            Some(&bin.join("launcherctl")),
            Some(&bin.join("open-url")),
            sink,
        );
        let mut ctx = Ctx::for_tests(cols, rows);
        ctx.size = Size::new(cols, rows, CELL_W, CELL_H);
        ctx.caps = Caps::all();
        ctx.motion = false;
        let router = Router::new(env);
        let mut st = Stage { dir, ctx, router };
        st.settle();
        Ok(st)
    }

    fn draw(&mut self) -> (Buffer, Vec<Placement>) {
        let mut f = Frame::new(&mut self.ctx);
        self.router.draw(&mut f);
        let Frame { buf, places, .. } = f;
        (buf, places)
    }

    fn key(&mut self, k: Key) {
        self.router.handle(&Event::Key(k), &mut self.ctx);
        self.draw();
    }

    /// Reads every watched fd until nothing is watched (the startup refresh, gh checks).
    fn settle(&mut self) {
        let deadline = std::time::Instant::now() + Duration::from_secs(10);
        loop {
            let fds = self.router.watch();
            if fds.is_empty() || std::time::Instant::now() > deadline {
                break;
            }
            let polled: Vec<_> = fds.iter().map(|&f| (f, libc::POLLIN)).collect();
            let Ok(ready) = term::poll_fds(&polled, Some(Duration::from_millis(200))) else { break };
            for (i, &fd) in fds.iter().enumerate() {
                if ready[i] {
                    self.router.handle(&Event::Readable(fd), &mut self.ctx);
                }
            }
        }
        self.draw();
    }

    fn index_of(&self, name: &str) -> io::Result<usize> {
        self.router
            .st
            .cat
            .items
            .iter()
            .position(|i| i.name == name)
            .ok_or_else(|| io::Error::new(io::ErrorKind::NotFound, format!("no item {name:?} in the store")))
    }

    fn cursor_to(&mut self, index: usize) {
        self.key(Key::Home);
        for _ in 0..index {
            self.key(Key::Down);
        }
    }

    /// Brings the router to `spec` and returns its resting frame.
    fn stage(&mut self, spec: &Spec) -> io::Result<(Buffer, Vec<Placement>)> {
        match spec {
            Spec::Front { cursor, selected, updates } => {
                for name in selected {
                    let i = self.index_of(name)?;
                    self.cursor_to(i);
                    self.key(Key::Char(' '));
                }
                self.cursor_to(*cursor);
                if *updates {
                    self.key(Key::Char('u'));
                }
            }
            Spec::Item { name, scroll } => {
                let i = self.index_of(name)?;
                self.cursor_to(i);
                self.key(Key::Enter);
                self.settle();
                for _ in 0..*scroll {
                    self.key(Key::Down);
                }
            }
            Spec::Installing { name, pct } => {
                self.index_of(name)?;
                let st = &mut self.router.st;
                let installed = st.cat.item(name).is_some_and(|i| i.installed.is_some());
                let verb = if !installed {
                    Verb::Install
                } else if st.cat.update_for(name).is_some() {
                    Verb::Update
                } else {
                    Verb::Remove
                };
                st.fake_job(verb, name, *pct);
                self.router.show_installing();
            }
        }
        self.settle();
        Ok(self.draw())
    }
}

impl Drop for Stage {
    fn drop(&mut self) {
        let _ = std::fs::remove_dir_all(&self.dir);
    }
}

// ---------------------------------------------------------------------------
// Painting
// ---------------------------------------------------------------------------

/// A straight-alpha RGBA bitmap.
pub struct Image {
    pub width: u32,
    pub height: u32,
    pub rgba: Vec<u8>,
}

impl Image {
    fn filled(width: u32, height: u32, c: Rgb) -> Image {
        let rgba = [c.0, c.1, c.2, 255].repeat(width as usize * height as usize);
        Image { width, height, rgba }
    }

    pub fn pixel(&self, x: u32, y: u32) -> Rgb {
        let i = (y as usize * self.width as usize + x as usize) * 4;
        Rgb(self.rgba[i], self.rgba[i + 1], self.rgba[i + 2])
    }

    fn blend(&mut self, x: i32, y: i32, c: Rgb, a: f32) {
        if x < 0 || y < 0 || x >= self.width as i32 || y >= self.height as i32 || a <= 0.0 {
            return;
        }
        let i = (y as usize * self.width as usize + x as usize) * 4;
        let a = a.min(1.0);
        let mix = |d: u8, s: u8| (d as f32 + (s as f32 - d as f32) * a).round() as u8;
        self.rgba[i] = mix(self.rgba[i], c.0);
        self.rgba[i + 1] = mix(self.rgba[i + 1], c.1);
        self.rgba[i + 2] = mix(self.rgba[i + 2], c.2);
    }

    fn rect(&mut self, x: i32, y: i32, w: i32, h: i32, c: Rgb, a: f32) {
        for yy in y..y + h {
            for xx in x..x + w {
                self.blend(xx, yy, c, a);
            }
        }
    }

    /// Encodes as PNG.
    pub fn png(&self) -> io::Result<Vec<u8>> {
        let mut out = Vec::new();
        let mut enc = png::Encoder::new(&mut out, self.width, self.height);
        enc.set_color(png::ColorType::Rgba);
        enc.set_depth(png::BitDepth::Eight);
        let mut w = enc.write_header().map_err(io::Error::other)?;
        w.write_image_data(&self.rgba).map_err(io::Error::other)?;
        w.finish().map_err(io::Error::other)?;
        Ok(out)
    }
}

struct Faces {
    regular: Font,
    bold: Font,
    italic: Font,
    bold_italic: Font,
}

impl Faces {
    fn load() -> Faces {
        let load = |b: &'static [u8]| {
            Font::from_bytes(b, FontSettings { scale: FONT_PX, ..FontSettings::default() })
                .expect("bundled JetBrains Mono parses")
        };
        Faces {
            regular: load(REGULAR),
            bold: load(BOLD),
            italic: load(ITALIC),
            bold_italic: load(BOLD_ITALIC),
        }
    }

    fn pick(&self, st: &Style) -> &Font {
        match (st.bold, st.italic) {
            (false, false) => &self.regular,
            (true, false) => &self.bold,
            (false, true) => &self.italic,
            (true, true) => &self.bold_italic,
        }
    }
}

/// Where text sits inside a box of `h` pixels for a `px` face: the line is centred and the
/// baseline is `ascent` below its top.
fn baseline(face: &Font, px: f32, top: f32, h: f32) -> i32 {
    let (asc, desc) = match face.horizontal_line_metrics(px) {
        Some(m) => (m.ascent, m.descent),
        None => (0.77 * px, -0.23 * px),
    };
    (top + (h - (asc - desc)) / 2.0 + asc).round() as i32
}

/// The colours a cell really shows: (foreground, background if any), after reverse and dim.
fn colours(st: &Style, pal: &Palette) -> (Rgb, Option<Rgb>) {
    let fg = match st.fg {
        Color::Rgb(c) => c,
        Color::Default => pal.ink,
    };
    let bg = match st.bg {
        Color::Rgb(c) => Some(c),
        Color::Default => None,
    };
    let (fg, bg) = if st.reverse { (bg.unwrap_or(pal.surface), Some(fg)) } else { (fg, bg) };
    let fg = if st.dim { fg.mix(bg.unwrap_or(pal.surface), 0.4) } else { fg };
    (fg, bg)
}

/// One glyph with its left edge at `left` on `baseline`; a missing glyph is a hollow box.
fn glyph(img: &mut Image, face: &Font, c: char, px: f32, left: i32, baseline: i32, color: Rgb) {
    if c == ' ' || c.is_control() {
        return;
    }
    if matches!(c, '★' | '☆') {
        star(img, c == '★', px, left, baseline, color);
        return;
    }
    if !face.has_glyph(c) {
        let w = (px * 0.5).round() as i32;
        let h = (px * 0.7).round() as i32;
        let x = left + (px * 0.05).round() as i32;
        let y = baseline - h;
        img.rect(x, y, w, 1, color, 0.6);
        img.rect(x, y + h - 1, w, 1, color, 0.6);
        img.rect(x, y, 1, h, color, 0.6);
        img.rect(x + w - 1, y, 1, h, color, 0.6);
        return;
    }
    let (m, cov) = face.rasterize(c, px);
    if m.width == 0 {
        return;
    }
    let gx = left + m.xmin;
    let gy = baseline - (m.height as i32 + m.ymin);
    for (i, &a) in cov.iter().enumerate() {
        if a > 0 {
            img.blend(gx + (i % m.width) as i32, gy + (i / m.width) as i32, color, a as f32 / 255.0);
        }
    }
}

/// A five-point star (JetBrains Mono has none): filled for `★`, an outline for `☆`, about
/// the size of a capital letter, centred in a `px`-wide slot.
fn star(img: &mut Image, filled: bool, px: f32, left: i32, baseline: i32, color: Rgb) {
    let r = px * 0.36;
    let (cx, cy) = (left as f32 + px * 0.3, baseline as f32 - px * 0.36);
    // Ten vertices, outer and inner alternating, starting from the top point.
    let pts: Vec<(f32, f32)> = (0..10)
        .map(|i| {
            let a = -std::f32::consts::FRAC_PI_2 + i as f32 * std::f32::consts::PI / 5.0;
            let rr = if i % 2 == 0 { r } else { r * 0.42 };
            (cx + rr * a.cos(), cy + rr * a.sin())
        })
        .collect();
    let inside = |x: f32, y: f32, k: f32| {
        let mut hit = false;
        for i in 0..pts.len() {
            let (x1, y1) = (cx + (pts[i].0 - cx) * k, cy + (pts[i].1 - cy) * k);
            let j = (i + 1) % pts.len();
            let (x2, y2) = (cx + (pts[j].0 - cx) * k, cy + (pts[j].1 - cy) * k);
            if (y1 > y) != (y2 > y) && x < (x2 - x1) * (y - y1) / (y2 - y1) + x1 {
                hit = !hit;
            }
        }
        hit
    };
    let (x0, y0) = ((cx - r).floor() as i32, (cy - r).floor() as i32);
    let n = (2.0 * r).ceil() as i32 + 1;
    for yy in y0..y0 + n {
        for xx in x0..x0 + n {
            // Four samples per pixel soften the edges.
            let mut cover = 0.0;
            for (ox, oy) in [(0.25, 0.25), (0.75, 0.25), (0.25, 0.75), (0.75, 0.75)] {
                let (sx, sy) = (xx as f32 + ox, yy as f32 + oy);
                let on = inside(sx, sy, 1.0) && (filled || !inside(sx, sy, 1.0 - 1.4 / r));
                if on {
                    cover += 0.25;
                }
            }
            img.blend(xx, yy, color, cover);
        }
    }
}

/// Underline and strikethrough over `w` pixels from `x` for text of `px` on `baseline`.
fn decorate(img: &mut Image, st: &Style, x: i32, w: i32, baseline: i32, px: f32, fg: Rgb) {
    let t = ((px / 12.0).round() as i32).max(1);
    if st.strike {
        img.rect(x, baseline - (px * 0.3).round() as i32, w, t, fg, 1.0);
    }
    if st.underline == Underline::None {
        return;
    }
    let ul = match st.ul_color {
        Color::Rgb(c) => c,
        Color::Default => fg,
    };
    let y = baseline + (px * 0.14).round() as i32;
    let cw = CELL_W as i32 * ((px / FONT_PX).round() as i32).max(1);
    match st.underline {
        Underline::None => {}
        Underline::Single => img.rect(x, y, w, t, ul, 1.0),
        Underline::Double => {
            img.rect(x, y, w, t, ul, 1.0);
            img.rect(x, y + 2 * t, w, t, ul, 1.0);
        }
        Underline::Curly => {
            let amp = 1.5 * t as f32;
            for i in 0..w {
                let phase = (x + i) as f32 / cw as f32 * std::f32::consts::TAU;
                let off = amp * phase.sin();
                let yy = y as f32 + t as f32 / 2.0 + off;
                let top = yy.floor() as i32;
                let frac = yy - yy.floor();
                for k in 0..t {
                    img.blend(x + i, top + k, ul, 1.0 - frac);
                    img.blend(x + i, top + k + 1, ul, frac);
                }
            }
        }
        Underline::Dotted => {
            for i in 0..w {
                if (x + i).rem_euclid(2 * t) < t {
                    img.rect(x + i, y, 1, t, ul, 1.0);
                }
            }
        }
        Underline::Dashed => {
            let period = (cw / 2).max(2);
            for i in 0..w {
                if (x + i).rem_euclid(period) < period - t.max(2) {
                    img.rect(x + i, y, 1, t, ul, 1.0);
                }
            }
        }
    }
}

fn paint_cells(img: &mut Image, buf: &Buffer, faces: &Faces, pal: &Palette, text: bool) {
    let (cw, ch) = (CELL_W as i32, CELL_H as i32);
    for y in 0..buf.h {
        for x in 0..buf.w {
            let Some(cell) = buf.get(x, y) else { continue };
            let (px, py) = (x as i32 * cw, y as i32 * ch);
            let (fg, bg) = colours(&cell.style, pal);
            if !text {
                if let Some(bg) = bg {
                    let wide = matches!(cell.sym, Sym::Char(c) if crate::render::char_width(c) == 2);
                    img.rect(px, py, if wide { 2 * cw } else { cw }, ch, bg, 1.0);
                }
                continue;
            }
            let face = faces.pick(&cell.style);
            let base = baseline(face, FONT_PX, py as f32, ch as f32);
            let w = match &cell.sym {
                Sym::Char(c) => {
                    glyph(img, face, *c, FONT_PX, px, base, fg);
                    cw * crate::render::char_width(*c).max(1) as i32
                }
                Sym::Cluster(s) => {
                    for c in s.chars() {
                        glyph(img, face, c, FONT_PX, px, base, fg);
                    }
                    cw * crate::render::text_width(s).max(1) as i32
                }
                Sym::WideTail | Sym::Covered => continue,
            };
            decorate(img, &cell.style, px, w, base, FONT_PX, fg);
        }
    }
}

/// An OSC 66 run: each character in a slot `scale` cells wide, set at `scale × num/den` of
/// the base size and aligned inside the block by `valign`/`halign`.
fn paint_run(img: &mut Image, run: &SizedRun, faces: &Faces, pal: &Palette) {
    let s = run.sizing.clamped();
    let scale = s.scale.max(1) as f32;
    let frac = if s.den > 0 { s.num as f32 / s.den as f32 } else { 1.0 };
    let px = FONT_PX * scale * frac;
    let (cw, ch) = (CELL_W as f32, CELL_H as f32);
    let block_h = ch * scale;
    let mini_w = cw * scale * frac;
    let mini_h = ch * scale * frac;
    let top = run.y as f32 * ch;
    let y0 = match s.valign {
        1 => top + block_h - mini_h,
        2 => top + (block_h - mini_h) / 2.0,
        _ => top,
    };
    let face = faces.pick(&run.style);
    let (fg, _) = colours(&run.style, pal);
    let base = baseline(face, px, y0, mini_h);
    let mut col = run.x as f32 * cw;
    let start = col;
    for c in run.text.chars() {
        let w = crate::render::char_width(c) as f32;
        if w == 0.0 {
            continue;
        }
        let slot = w * scale * cw;
        let gx = match s.halign {
            1 => col + slot - mini_w * w,
            2 => col + (slot - mini_w * w) / 2.0,
            _ => col,
        };
        glyph(img, face, c, px, gx.round() as i32, base, fg);
        col += slot;
    }
    decorate(img, &run.style, start.round() as i32, (col - start).round() as i32, base, px, fg);
}

/// Composites one kitty placement: cropped source, scaled into its cell box when one is
/// given, at its pixel position, straight alpha over what is there.
fn paint_placement(img: &mut Image, p: &Placement) {
    let pic = &p.pic;
    let (sx, sy, sw, sh) = match p.crop {
        Some(c) => {
            (c.x, c.y, c.w.min(pic.width().saturating_sub(c.x)), c.h.min(pic.height().saturating_sub(c.y)))
        }
        None => (0, 0, pic.width(), pic.height()),
    };
    if sw == 0 || sh == 0 {
        return;
    }
    let (dw, dh) = match (p.cols, p.rows) {
        (0, 0) => (sw, sh),
        (c, 0) => {
            let w = c as u32 * CELL_W as u32;
            (w, (sh as u64 * w as u64 / sw as u64).max(1) as u32)
        }
        (0, r) => {
            let h = r as u32 * CELL_H as u32;
            ((sw as u64 * h as u64 / sh as u64).max(1) as u32, h)
        }
        (c, r) => (c as u32 * CELL_W as u32, r as u32 * CELL_H as u32),
    };
    let x0 = p.col as i32 * CELL_W as i32 + p.px_x as i32;
    let y0 = p.row as i32 * CELL_H as i32 + p.px_y as i32;
    let src = pic.rgba();
    let pw = pic.width() as usize;
    for dy in 0..dh {
        let yy = y0 + dy as i32;
        if yy < 0 || yy >= img.height as i32 {
            continue;
        }
        let syy = sy as usize + (dy as u64 * sh as u64 / dh as u64) as usize;
        for dx in 0..dw {
            let xx = x0 + dx as i32;
            if xx < 0 || xx >= img.width as i32 {
                continue;
            }
            let sxx = sx as usize + (dx as u64 * sw as u64 / dw as u64) as usize;
            let i = (syy * pw + sxx) * 4;
            let a = src[i + 3];
            if a > 0 {
                img.blend(xx, yy, Rgb(src[i], src[i + 1], src[i + 2]), a as f32 / 255.0);
            }
        }
    }
}

/// Paints a drawn frame.
pub fn paint(buf: &Buffer, places: &[Placement], pal: &Palette) -> Image {
    let faces = Faces::load();
    let mut img = Image::filled(buf.w as u32 * CELL_W as u32, buf.h as u32 * CELL_H as u32, pal.surface);
    paint_cells(&mut img, buf, &faces, pal, false);
    let mut order: Vec<&Placement> = places.iter().collect();
    order.sort_by_key(|p| p.z);
    for p in order.iter().filter(|p| p.z < 0) {
        paint_placement(&mut img, p);
    }
    paint_cells(&mut img, buf, &faces, pal, true);
    for run in buf.runs() {
        paint_run(&mut img, run, &faces, pal);
    }
    for p in order.iter().filter(|p| p.z >= 0) {
        paint_placement(&mut img, p);
    }
    img
}

// ---------------------------------------------------------------------------
// Entry points
// ---------------------------------------------------------------------------

/// Renders `spec` at `cols`×`rows` against `store` (None: the crate's fixture).
pub fn render(cols: u16, rows: u16, spec: &Spec, store: Option<&Path>) -> io::Result<Image> {
    let store = store.map(Path::to_path_buf).unwrap_or_else(default_store);
    let mut stage = Stage::new(cols, rows, &store)?;
    let (buf, places) = stage.stage(spec)?;
    Ok(paint(&buf, &places, &stage.ctx.palette))
}

/// Renders `spec` and writes it as a PNG at `out`.
pub fn shoot(cols: u16, rows: u16, spec: &Spec, store: Option<&Path>, out: &Path) -> io::Result<()> {
    let img = render(cols, rows, spec, store)?;
    if let Some(dir) = out.parent().filter(|d| !d.as_os_str().is_empty()) {
        std::fs::create_dir_all(dir)?;
    }
    std::fs::write(out, img.png()?)
}

/// The standard set: front, item:dawn and installing:dawn:64 at 53×26, 53×40 and 40×24,
/// as `<slug>-<cols>x<rows>.png` in `dir`. Returns the files written.
pub fn shoot_all(dir: &Path, store: Option<&Path>) -> io::Result<Vec<PathBuf>> {
    std::fs::create_dir_all(dir)?;
    let specs = [
        Spec::Front { cursor: 0, selected: vec![], updates: false },
        Spec::Item { name: "dawn".into(), scroll: 0 },
        Spec::Installing { name: "dawn".into(), pct: 64 },
    ];
    let mut files = Vec::new();
    for (cols, rows) in [(53u16, 26u16), (53, 40), (40, 24)] {
        for spec in &specs {
            let path = dir.join(format!("{}-{cols}x{rows}.png", spec.slug()));
            shoot(cols, rows, spec, store, &path)?;
            files.push(path);
        }
    }
    Ok(files)
}

fn usage(msg: &str) -> io::Error {
    io::Error::new(io::ErrorKind::InvalidInput, format!("{msg}\n\n{USAGE}"))
}

/// `--shot` / `--shot-all` command lines (everything after the program name).
pub fn cli(args: &[String]) -> io::Result<()> {
    let mut size: Option<(u16, u16)> = None;
    let mut screen: Option<String> = None;
    let mut out: Option<PathBuf> = None;
    let mut store: Option<PathBuf> = None;
    let mut all = false;
    let mut it = args.iter();
    while let Some(a) = it.next() {
        match a.as_str() {
            "--shot" => {
                let v = it.next().ok_or_else(|| usage("--shot needs <cols>x<rows>"))?;
                let (c, r) = v.split_once('x').ok_or_else(|| usage("size is <cols>x<rows>"))?;
                size = Some((
                    c.parse().map_err(|_| usage("bad column count"))?,
                    r.parse().map_err(|_| usage("bad row count"))?,
                ));
            }
            "--shot-all" => all = true,
            "--screen" => screen = Some(it.next().ok_or_else(|| usage("--screen needs a spec"))?.clone()),
            "--out" => out = Some(it.next().ok_or_else(|| usage("--out needs a path"))?.into()),
            "--store" => store = Some(it.next().ok_or_else(|| usage("--store needs a directory"))?.into()),
            other => return Err(usage(&format!("unknown argument {other:?}"))),
        }
    }
    let out = out.ok_or_else(|| usage("--out is required"))?;
    if all {
        for f in shoot_all(&out, store.as_deref())? {
            println!("{}", f.display());
        }
        return Ok(());
    }
    let (cols, rows) = size.ok_or_else(|| usage("--shot needs <cols>x<rows>"))?;
    let spec = Spec::parse(&screen.ok_or_else(|| usage("--screen is required"))?).map_err(|e| usage(&e))?;
    shoot(cols, rows, &spec, store.as_deref(), &out)?;
    println!("{}", out.display());
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn specs_parse() {
        assert_eq!(Spec::parse("front"), Ok(Spec::Front { cursor: 0, selected: vec![], updates: false }));
        assert_eq!(
            Spec::parse("front:3:selected=a,b:updates"),
            Ok(Spec::Front { cursor: 3, selected: vec!["a".into(), "b".into()], updates: true })
        );
        assert_eq!(Spec::parse("item:dawn:7"), Ok(Spec::Item { name: "dawn".into(), scroll: 7 }));
        assert_eq!(Spec::parse("installing:dawn:64"), Ok(Spec::Installing { name: "dawn".into(), pct: 64 }));
        assert!(Spec::parse("installing:dawn").is_err());
        assert!(Spec::parse("nope").is_err());
        assert_eq!(Spec::parse("installing:dawn:64").unwrap().slug(), "installing-dawn-64");
    }

    #[test]
    fn styles_reach_the_pixels() {
        let pal = Palette::builtin_dark();
        let mut b = Buffer::new(6, 2);
        b.put_str(
            0,
            0,
            "ab",
            Style::new().fg(Rgb(255, 0, 0)).underline(Underline::Double).ul_color(Rgb(0, 255, 0)),
            6,
        );
        b.put_str(3, 0, "c", Style::new().bg(Rgb(0, 0, 255)), 6);
        b.put_run(SizedRun {
            x: 0,
            y: 1,
            text: "Z".into(),
            sizing: crate::render::Sizing::scale(1),
            style: Style::new().strike(),
        });
        let img = paint(&b, &[], &pal);
        assert_eq!((img.width, img.height), (72, 52));
        let has = |c: Rgb| (0..img.height).any(|y| (0..img.width).any(|x| img.pixel(x, y) == c));
        assert!(has(Rgb(255, 0, 0)), "red glyphs");
        assert!(has(Rgb(0, 255, 0)), "green underline");
        assert!(has(Rgb(0, 0, 255)), "blue cell background");
        assert!(has(pal.ink), "the sized run's glyph and strike");
    }
}
