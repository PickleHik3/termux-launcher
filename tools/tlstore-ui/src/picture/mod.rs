//! Pictures: RGBA bitmaps the renderer uploads to the terminal once and places by id.
//! Four sources: script words set in the bundled Pinyon Script face, the pixel TLSTORE mark,
//! JPEG/PNG files from disk (catalog and README pictures; an APNG header picture is a clip,
//! see [`apng`]), and the shapes the store draws itself ([`shapes`]).

pub mod apng;
pub mod file;
pub mod mark;
pub mod script;
pub mod shapes;

use std::cell::{Cell, RefCell};
use std::collections::{HashMap, HashSet};
use std::io;
use std::path::{Path, PathBuf};
use std::rc::Rc;
use std::sync::atomic::{AtomicU32, Ordering};
use std::sync::mpsc::{self, Receiver, TryRecvError};

use crate::render::Rgb;

pub use apng::Cel;
pub use file::Fit;

/// How much of a header picture fades out along its bottom edge.
pub const HEADER_FADE: f32 = 0.38;

/// The frames after the first of an animated picture, arriving from the frame worker.
struct Clip {
    /// How long the first frame (the picture itself) shows, once known.
    first_gap: Cell<u32>,
    cels: RefCell<Vec<Cel>>,
    /// The worker's channel; `None` once every frame is here.
    rx: RefCell<Option<Receiver<apng::Msg>>>,
}

struct PicData {
    id: u32,
    w: u32,
    h: u32,
    rgba: Vec<u8>,
    clip: Option<Clip>,
}

/// A shared, immutable RGBA picture (straight alpha, row-major). Cloning is cheap. Each new
/// picture gets a fresh kitty image id. An animated picture is its first frame plus the
/// frames after it ([`Picture::cel_count`]), which may still be arriving.
#[derive(Clone)]
pub struct Picture(Rc<PicData>);

impl std::fmt::Debug for Picture {
    fn fmt(&self, f: &mut std::fmt::Formatter<'_>) -> std::fmt::Result {
        write!(f, "Picture#{}({}x{})", self.0.id, self.0.w, self.0.h)?;
        if self.is_animated() {
            write!(f, "+{}", self.cel_count())?;
        }
        Ok(())
    }
}

/// Kitty image ids are shared by everything in the terminal; start high to stay clear of
/// other programs' small ids.
static NEXT_ID: AtomicU32 = AtomicU32::new(0x7153_0001);

impl Picture {
    /// Wraps `rgba` (length must be `w * h * 4`).
    pub fn new(w: u32, h: u32, rgba: Vec<u8>) -> Picture {
        Picture::with_clip(w, h, rgba, None)
    }

    /// An animated picture whose frames are all here: `rgba` shows for `first_gap_ms`, then
    /// each of `cels` (every one `w * h * 4` bytes) for its own gap, round and round.
    pub fn animated(w: u32, h: u32, rgba: Vec<u8>, first_gap_ms: u32, cels: Vec<Cel>) -> Picture {
        for c in &cels {
            assert_eq!(c.rgba.len(), w as usize * h as usize * 4, "cel length");
        }
        let clip = Clip {
            first_gap: Cell::new(first_gap_ms),
            cels: RefCell::new(cels),
            rx: RefCell::new(None),
        };
        Picture::with_clip(w, h, rgba, Some(clip))
    }

    /// An animated picture whose frames after the first arrive on `rx` (see [`apng::stream`]).
    fn streaming(w: u32, h: u32, rgba: Vec<u8>, rx: Receiver<apng::Msg>) -> Picture {
        let clip = Clip {
            first_gap: Cell::new(apng::DEFAULT_GAP_MS),
            cels: RefCell::new(Vec::new()),
            rx: RefCell::new(Some(rx)),
        };
        Picture::with_clip(w, h, rgba, Some(clip))
    }

    fn with_clip(w: u32, h: u32, rgba: Vec<u8>, clip: Option<Clip>) -> Picture {
        assert_eq!(rgba.len(), w as usize * h as usize * 4, "rgba length");
        Picture(Rc::new(PicData { id: NEXT_ID.fetch_add(1, Ordering::Relaxed), w, h, rgba, clip }))
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

    /// True for a picture with frames after the first (arrived or still coming).
    pub fn is_animated(&self) -> bool {
        self.0.clip.is_some()
    }

    /// Takes what the frame worker has sent so far.
    fn drain(&self) {
        let Some(clip) = &self.0.clip else { return };
        let done = {
            let rx = clip.rx.borrow();
            let Some(r) = rx.as_ref() else { return };
            loop {
                match r.try_recv() {
                    Ok(apng::Msg::StillGap(g)) => clip.first_gap.set(g),
                    // A frame of another size would be a bug in the worker; it is not shown.
                    Ok(apng::Msg::Frame(c)) => {
                        if c.rgba.len() == self.0.rgba.len() {
                            clip.cels.borrow_mut().push(c);
                        }
                    }
                    Err(TryRecvError::Empty) => break false,
                    Err(TryRecvError::Disconnected) => break true,
                }
            }
        };
        if done {
            *clip.rx.borrow_mut() = None;
        }
    }

    /// How long the first frame shows, in milliseconds (an animated picture; else 0).
    pub fn gap_ms(&self) -> u32 {
        self.drain();
        self.0.clip.as_ref().map_or(0, |c| c.first_gap.get())
    }

    /// Frames after the first that have arrived so far.
    pub fn cel_count(&self) -> usize {
        self.drain();
        self.0.clip.as_ref().map_or(0, |c| c.cels.borrow().len())
    }

    /// Reads frame `i` (0 = the first frame after the still) when it has arrived.
    pub fn with_cel<R>(&self, i: usize, f: impl FnOnce(&Cel) -> R) -> Option<R> {
        self.drain();
        let clip = self.0.clip.as_ref()?;
        let cels = clip.cels.borrow();
        cels.get(i).map(f)
    }

    /// True once every frame is here (always, for a still).
    pub fn complete(&self) -> bool {
        self.drain();
        self.0.clip.as_ref().is_none_or(|c| c.rx.borrow().is_none())
    }
}

/// Caches every picture a run makes, so a word, file or shape is made once and keeps its
/// kitty id (and so is uploaded once). Only one clip is kept at a time: a new one replaces
/// the last, whose frames are freed.
#[derive(Default)]
pub struct Pictures {
    face: Option<fontdue::Font>,
    words: HashMap<(String, u32, Rgb), Picture>,
    marks: HashMap<(u32, Rgb), Picture>,
    /// By path, box, fit, fade and whether the frames were wanted.
    files: HashMap<(PathBuf, u32, u32, Fit, bool, bool), Picture>,
    /// Files asked for as clips that turned out to be plain pictures.
    plain: HashSet<PathBuf>,
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
        self.file_with(path, box_w, box_h, fit, false, false)
    }

    /// A header picture: contain-fitted into the box, its bottom [`HEADER_FADE`] faded to
    /// transparent. With `animate`, an APNG file comes back as a clip: the first frame now,
    /// the frames after it fitted and faded the same way by a worker thread, thinned to
    /// [`apng::MAX_CLIP_BYTES`] / [`apng::MAX_CLIP_FRAMES`]. Cached by path, box and `animate`.
    pub fn header_picture(
        &mut self,
        path: &Path,
        box_w: u32,
        box_h: u32,
        animate: bool,
    ) -> io::Result<Picture> {
        self.file_with(path, box_w, box_h, Fit::Contain, true, animate)
    }

    fn file_with(
        &mut self,
        path: &Path,
        box_w: u32,
        box_h: u32,
        fit: Fit,
        fade: bool,
        animate: bool,
    ) -> io::Result<Picture> {
        let animate = animate && !self.plain.contains(path);
        let key = (path.to_path_buf(), box_w, box_h, fit, fade, animate);
        if let Some(p) = self.files.get(&key) {
            return Ok(p.clone());
        }
        if animate {
            let bytes = std::fs::read(path)?;
            return match apng::Apng::open(&bytes)? {
                Some(a) => {
                    let p = clip(a, &bytes, box_w, box_h, fit, fade)?;
                    // One clip at a time: the last one's frames go with it.
                    self.files.retain(|k, _| !k.5);
                    self.files.insert(key, p.clone());
                    Ok(p)
                }
                None => {
                    self.plain.insert(path.to_path_buf());
                    self.file_with(path, box_w, box_h, fit, fade, false)
                }
            };
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
        self.plain.clear();
        self.shapes.clear();
    }
}

/// The first frame of `apng`, fitted and faded, as a picture whose other frames a worker
/// thread fits from its own copy of `bytes`; a plain picture when the guard leaves the still
/// alone or the worker cannot start.
fn clip(
    mut apng: apng::Apng<'_>,
    bytes: &[u8],
    box_w: u32,
    box_h: u32,
    fit: Fit,
    fade: bool,
) -> io::Result<Picture> {
    let (sw, sh) = apng.size();
    let frames = apng.frames() as usize;
    let first = apng
        .next_frame()?
        .ok_or_else(|| io::Error::new(io::ErrorKind::InvalidData, "APNG without frames"))?;
    drop(apng);
    let (w, h, mut rgba) = file::fit_into(sw, sh, &first.rgba, box_w, box_h, fit);
    if fade {
        shapes::fade_bottom(w, h, &mut rgba, HEADER_FADE);
    }
    let stride = apng::stride(frames, rgba.len());
    if frames.div_ceil(stride) <= 1 {
        return Ok(Picture::new(w, h, rgba));
    }
    let (tx, rx) = mpsc::channel();
    let owned = bytes.to_vec();
    let spawned = std::thread::Builder::new()
        .name("tlstore-hero".into())
        .spawn(move || apng::stream(&owned, box_w, box_h, fit, fade, stride, &tx));
    Ok(match spawned {
        Ok(_) => Picture::streaming(w, h, rgba, rx),
        Err(_) => Picture::new(w, h, rgba),
    })
}

#[cfg(test)]
mod tests {
    use super::*;

    fn wait_complete(p: &Picture) {
        let deadline = std::time::Instant::now() + std::time::Duration::from_secs(10);
        while !p.complete() && std::time::Instant::now() < deadline {
            std::thread::sleep(std::time::Duration::from_millis(2));
        }
        assert!(p.complete(), "the frame worker did not finish");
    }

    #[test]
    fn an_apng_header_picture_is_a_clip_and_a_plain_png_is_not() {
        let dir = std::env::temp_dir().join(format!("tlstore-ui-clip-{}", std::process::id()));
        std::fs::create_dir_all(&dir).unwrap();
        let path = dir.join("hero.png");
        std::fs::write(&path, apng::tests::tiny_apng()).unwrap();
        let mut pics = Pictures::new();
        let still = pics.header_picture(&path, 4, 4, false).unwrap();
        assert!(!still.is_animated() && still.complete());
        assert_eq!((still.cel_count(), still.gap_ms()), (0, 0));
        let clip = pics.header_picture(&path, 4, 4, true).unwrap();
        assert!(clip.is_animated());
        assert_ne!(clip.id(), still.id());
        assert_eq!((clip.width(), clip.height()), (4, 4));
        assert_eq!(&clip.rgba()[..4], &[255, 0, 0, 255], "the still is the first frame");
        wait_complete(&clip);
        assert_eq!(clip.cel_count(), 3, "the frames after the still");
        assert_eq!(clip.gap_ms(), 100);
        assert_eq!(clip.with_cel(1, |c| c.gap_ms), Some(10));
        assert_eq!(clip.with_cel(2, |c| c.rgba.len()), Some(4 * 4 * 4));
        assert!(clip.with_cel(3, |_| ()).is_none());
        // Asked for again: the same clip.
        assert_eq!(pics.header_picture(&path, 4, 4, true).unwrap().id(), clip.id());
        // A new clip replaces it in the cache.
        let other = dir.join("hero2.png");
        std::fs::copy(&path, &other).unwrap();
        let second = pics.header_picture(&other, 4, 4, true).unwrap();
        assert_ne!(second.id(), clip.id());
        assert_ne!(pics.header_picture(&path, 4, 4, true).unwrap().id(), clip.id(), "decoded afresh");
        // A plain PNG asked for as a clip is the still, shared with the plain request.
        let plain = dir.join("plain.png");
        {
            let f = std::fs::File::create(&plain).unwrap();
            let mut e = png::Encoder::new(std::io::BufWriter::new(f), 2, 2);
            e.set_color(png::ColorType::Rgba);
            e.set_depth(png::BitDepth::Eight);
            let mut wr = e.write_header().unwrap();
            wr.write_image_data(&[9u8; 16]).unwrap();
        }
        let a = pics.header_picture(&plain, 2, 2, true).unwrap();
        let b = pics.header_picture(&plain, 2, 2, false).unwrap();
        assert!(!a.is_animated());
        assert_eq!(a.id(), b.id());
        std::fs::remove_dir_all(&dir).ok();
    }

    #[test]
    fn a_complete_animated_picture_reads_its_frames_in_order() {
        let cels = vec![Cel { gap_ms: 20, rgba: vec![1; 4] }, Cel { gap_ms: 30, rgba: vec![2; 4] }];
        let p = Picture::animated(1, 1, vec![0; 4], 40, cels);
        assert!(p.is_animated() && p.complete());
        assert_eq!(p.gap_ms(), 40);
        assert_eq!(p.cel_count(), 2);
        assert_eq!(p.with_cel(1, |c| (c.gap_ms, c.rgba[0])), Some((30, 2)));
        assert_eq!(format!("{p:?}"), format!("Picture#{}(1x1)+2", p.id()));
    }
}
