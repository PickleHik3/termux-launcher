//! Pictures: RGBA bitmaps the renderer uploads to the terminal once and places by id.
//! Four sources: script words set in the bundled Pinyon Script face, the pixel TLSTORE mark,
//! JPEG/PNG files from disk (catalog and README pictures), and the shapes the store draws
//! itself ([`shapes`]).

pub mod file;
pub mod mark;
pub mod script;
pub mod shapes;

use std::collections::HashMap;
use std::io;
use std::path::{Path, PathBuf};
use std::rc::Rc;
use std::sync::atomic::{AtomicU32, Ordering};

use crate::render::Rgb;

pub use file::Fit;

/// How much of a header picture fades out along its bottom edge.
pub const HEADER_FADE: f32 = 0.38;

struct PicData {
    id: u32,
    w: u32,
    h: u32,
    rgba: Vec<u8>,
}

/// A shared, immutable RGBA picture (straight alpha, row-major). Cloning is cheap. Each new
/// picture gets a fresh kitty image id.
#[derive(Clone)]
pub struct Picture(Rc<PicData>);

impl std::fmt::Debug for Picture {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        write!(f, "Picture#{}({}x{})", self.0.id, self.0.w, self.0.h)
    }
}

/// Kitty image ids are shared by everything in the terminal; start high to stay clear of
/// other programs' small ids.
static NEXT_ID: AtomicU32 = AtomicU32::new(0x7153_0001);

impl Picture {
    /// Wraps `rgba` (length must be `w * h * 4`).
    pub fn new(w: u32, h: u32, rgba: Vec<u8>) -> Picture {
        assert_eq!(rgba.len(), w as usize * h as usize * 4, "rgba length");
        Picture(Rc::new(PicData { id: NEXT_ID.fetch_add(1, Ordering::Relaxed), w, h, rgba }))
    }
    pub fn id(&self) -> u32 {
        self.0.id
    }
    pub fn width(&self) -> u32 {
        self.0.w
    }
    pub fn height(&self) -> u32 {
        self.0.h
    }
    pub fn rgba(&self) -> &[u8] {
        &self.0.rgba
    }
    /// Cells needed to show it at natural size on a `cell_w`×`cell_h` grid (rounded up).
    pub fn cells(&self, cell_w: u16, cell_h: u16) -> (u16, u16) {
        let cw = cell_w.max(1) as u32;
        let ch = cell_h.max(1) as u32;
        (self.0.w.div_ceil(cw) as u16, self.0.h.div_ceil(ch) as u16)
    }
}

/// Caches every picture a run makes, so a word, file or shape is made once and keeps its
/// kitty id (and so is uploaded once).
#[derive(Default)]
pub struct Pictures {
    face: Option<fontdue::Font>,
    words: HashMap<(String, u32, Rgb), Picture>,
    marks: HashMap<(u32, Rgb), Picture>,
    files: HashMap<(PathBuf, u32, u32, Fit, bool), Picture>,
    shapes: HashMap<String, Picture>,
}

impl Pictures {
    pub fn new() -> Pictures {
        Pictures::default()
    }

    /// `text` set in Pinyon Script, exactly `px_h` pixels tall (ascender to descender),
    /// in `color` on transparent. Cached.
    pub fn script_word(&mut self, text: &str, px_h: u32, color: Rgb) -> Picture {
        let key = (text.to_string(), px_h, color);
        if let Some(p) = self.words.get(&key) {
            return p.clone();
        }
        let face = self.face.get_or_insert_with(script::load_face);
        let (w, h, rgba) = script::rasterise(face, text, px_h, color);
        let p = Picture::new(w, h, rgba);
        self.words.insert(key, p.clone());
        p
    }

    /// The TLSTORE pixel mark, each mark pixel a `px`×`px` block. Cached.
    pub fn mark(&mut self, px: u32, color: Rgb) -> Picture {
        self.marks
            .entry((px, color))
            .or_insert_with(|| {
                let (w, h, rgba) = mark::render(px, color);
                Picture::new(w, h, rgba)
            })
            .clone()
    }

    /// A JPEG or PNG file scaled into a `box_w`×`box_h` pixel box (see [`Fit`]). Cached by
    /// path and box.
    pub fn file(&mut self, path: &Path, box_w: u32, box_h: u32, fit: Fit) -> io::Result<Picture> {
        self.file_with(path, box_w, box_h, fit, false)
    }

    /// A header picture: contain-fitted into the box, its bottom [`HEADER_FADE`] faded to
    /// transparent. Cached by path and box.
    pub fn header_picture(&mut self, path: &Path, box_w: u32, box_h: u32) -> io::Result<Picture> {
        self.file_with(path, box_w, box_h, Fit::Contain, true)
    }

    fn file_with(
        &mut self,
        path: &Path,
        box_w: u32,
        box_h: u32,
        fit: Fit,
        fade: bool,
    ) -> io::Result<Picture> {
        let key = (path.to_path_buf(), box_w, box_h, fit, fade);
        if let Some(p) = self.files.get(&key) {
            return Ok(p.clone());
        }
        let (w, h, mut rgba) = file::load_scaled(path, box_w, box_h, fit)?;
        if fade {
            shapes::fade_bottom(w, h, &mut rgba, HEADER_FADE);
        }
        let p = Picture::new(w, h, rgba);
        self.files.insert(key, p.clone());
        Ok(p)
    }

    /// A drawn shape, made by `make` the first time `key` is asked for. Cached by key, so the
    /// same shape keeps its id and is uploaded once.
    pub fn shape(&mut self, key: String, make: impl FnOnce() -> (u32, u32, Vec<u8>)) -> Picture {
        self.shapes
            .entry(key)
            .or_insert_with(|| {
                let (w, h, rgba) = make();
                Picture::new(w, h, rgba)
            })
            .clone()
    }

    /// Drops every cached picture (their terminal copies are freed by `Renderer::forget` or
    /// on exit).
    pub fn clear(&mut self) {
        self.words.clear();
        self.marks.clear();
        self.files.clear();
        self.shapes.clear();
    }
}
