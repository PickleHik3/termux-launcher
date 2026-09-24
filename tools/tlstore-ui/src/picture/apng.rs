//! APNG: the frames of an animated PNG composed onto a full canvas one at a time (`acTL` /
//! `fcTL`: dispose NONE / BACKGROUND / PREVIOUS, blend SOURCE / OVER), so a whole clip never
//! sits in memory at once; the guard that thins a clip to what the terminal and this process
//! can afford; and the worker that fits the frames after the first off the draw path.

use std::io;
use std::sync::mpsc::Sender;

use png::{BlendOp, DisposeOp};

use super::file::{self, Fit};
use super::shapes;

/// One composed frame: how long it shows, and its pixels (straight RGBA, row-major).
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct Cel {
    pub gap_ms: u32,
    pub rgba: Vec<u8>,
}

/// What a frame shows for until its `fcTL` says otherwise: one frame at 12 fps.
pub const DEFAULT_GAP_MS: u32 = 83;

/// A clip's decoded frames take at most this many bytes (RGBA, as the terminal stores them)…
pub const MAX_CLIP_BYTES: usize = 32 * 1024 * 1024;
/// …and at most this many frames, the still included.
pub const MAX_CLIP_FRAMES: usize = 48;

/// What the frame worker sends back: the still's gap once the frames folded into it are
/// known, then every kept frame after the still, in order.
#[derive(Debug, PartialEq, Eq)]
pub enum Msg {
    StillGap(u32),
    Frame(Cel),
}

const PNG_MAGIC: &[u8] = b"\x89PNG\r\n\x1a\n";

fn bad<E: std::fmt::Display>(e: E) -> io::Error {
    io::Error::new(io::ErrorKind::InvalidData, e.to_string())
}

/// Reads an APNG frame by frame, composing each onto the canvas.
pub struct Apng<'a> {
    reader: png::Reader<&'a [u8]>,
    width: u32,
    height: u32,
    /// Frames in the animation (`acTL`).
    frames: u32,
    /// Frames still to compose.
    left: u32,
    /// The `IDAT` image is not part of the animation (no `fcTL` before it): read past it first.
    skip_default: bool,
    canvas: Vec<u8>,
    prev: Vec<u8>,
    buf: Vec<u8>,
}

impl<'a> Apng<'a> {
    /// Opens `bytes` when they are an APNG (a PNG with an `acTL` chunk and at least one
    /// frame); `Ok(None)` for a plain PNG or anything else.
    pub fn open(bytes: &'a [u8]) -> io::Result<Option<Apng<'a>>> {
        if !bytes.starts_with(PNG_MAGIC) {
            return Ok(None);
        }
        let mut dec = png::Decoder::new(bytes);
        dec.set_transformations(png::Transformations::EXPAND | png::Transformations::STRIP_16);
        let reader = dec.read_info().map_err(bad)?;
        let info = reader.info();
        let Some(actl) = info.animation_control else { return Ok(None) };
        if actl.num_frames == 0 {
            return Ok(None);
        }
        let (width, height) = (info.width, info.height);
        let px = width as usize * height as usize;
        if px == 0 {
            return Err(bad("empty PNG"));
        }
        let skip_default = info.frame_control.is_none();
        Ok(Some(Apng {
            reader,
            width,
            height,
            frames: actl.num_frames,
            left: actl.num_frames,
            skip_default,
            canvas: vec![0; px * 4],
            prev: Vec::new(),
            // Any subframe fits in the whole image at four bytes a pixel (16-bit is stripped).
            buf: vec![0; px * 4],
        }))
    }

    pub fn size(&self) -> (u32, u32) {
        (self.width, self.height)
    }

    /// Frames in the animation.
    pub fn frames(&self) -> u32 {
        self.frames
    }

    /// Composes the next frame; `None` after the last.
    pub fn next_frame(&mut self) -> io::Result<Option<Cel>> {
        if self.left == 0 {
            return Ok(None);
        }
        if self.skip_default {
            self.reader.next_frame(&mut self.buf).map_err(bad)?;
            self.skip_default = false;
        }
        let info = self.reader.next_frame(&mut self.buf).map_err(bad)?;
        let fc = self.reader.info().frame_control.ok_or_else(|| bad("APNG frame without fcTL"))?;
        let (fw, fh) = (info.width, info.height);
        if fc.x_offset.saturating_add(fw) > self.width || fc.y_offset.saturating_add(fh) > self.height {
            return Err(bad("APNG frame outside the canvas"));
        }
        let px = fw as usize * fh as usize;
        let sub = file::to_rgba(info.color_type, &self.buf[..info.buffer_size()], px)?;
        let first = self.left == self.frames;
        // The first frame has nothing to go back to.
        let dispose = match fc.dispose_op {
            DisposeOp::Previous if first => DisposeOp::Background,
            d => d,
        };
        if matches!(dispose, DisposeOp::Previous) {
            self.prev.clone_from(&self.canvas);
        }
        let region = (fc.x_offset, fc.y_offset, fw, fh);
        compose(&mut self.canvas, self.width, &sub, region, fc.blend_op);
        let cel = Cel { gap_ms: gap_ms(fc.delay_num, fc.delay_den), rgba: self.canvas.clone() };
        match dispose {
            DisposeOp::None => {}
            DisposeOp::Background => clear(&mut self.canvas, self.width, region),
            DisposeOp::Previous => restore(&mut self.canvas, &self.prev, self.width, region),
        }
        self.left -= 1;
        Ok(Some(cel))
    }
}

/// `fcTL` delay to milliseconds (a zero denominator means hundredths); never 0, which the
/// terminal would read as "use the default".
fn gap_ms(num: u16, den: u16) -> u32 {
    let den = if den == 0 { 100 } else { den as u32 };
    ((num as u32 * 1000 + den / 2) / den).max(1)
}

/// Draws `sub` (straight RGBA, `w`×`h`) onto `canvas` at (x, y): SOURCE copies, OVER
/// composites by alpha.
fn compose(canvas: &mut [u8], canvas_w: u32, sub: &[u8], region: (u32, u32, u32, u32), blend: BlendOp) {
    let (x0, y0, w, h) = region;
    for row in 0..h as usize {
        for col in 0..w as usize {
            let s = (row * w as usize + col) * 4;
            let d = ((y0 as usize + row) * canvas_w as usize + x0 as usize + col) * 4;
            let src = &sub[s..s + 4];
            match blend {
                BlendOp::Source => canvas[d..d + 4].copy_from_slice(src),
                BlendOp::Over => {
                    let sa = src[3] as u32;
                    if sa == 255 {
                        canvas[d..d + 4].copy_from_slice(src);
                    } else if sa > 0 {
                        let da = canvas[d + 3] as u32;
                        let keep = da * (255 - sa) / 255;
                        let out_a = sa + keep;
                        for k in 0..3 {
                            let mixed = src[k] as u32 * sa + canvas[d + k] as u32 * keep;
                            canvas[d + k] = (mixed / out_a) as u8;
                        }
                        canvas[d + 3] = out_a as u8;
                    }
                }
            }
        }
    }
}

fn clear(canvas: &mut [u8], canvas_w: u32, region: (u32, u32, u32, u32)) {
    let (x0, y0, w, h) = region;
    for row in 0..h as usize {
        let d = ((y0 as usize + row) * canvas_w as usize + x0 as usize) * 4;
        canvas[d..d + w as usize * 4].fill(0);
    }
}

fn restore(canvas: &mut [u8], prev: &[u8], canvas_w: u32, region: (u32, u32, u32, u32)) {
    let (x0, y0, w, h) = region;
    for row in 0..h as usize {
        let d = ((y0 as usize + row) * canvas_w as usize + x0 as usize) * 4;
        canvas[d..d + w as usize * 4].copy_from_slice(&prev[d..d + w as usize * 4]);
    }
}

/// The frame stride that keeps a clip of `frames` frames of `frame_bytes` each within
/// [`MAX_CLIP_BYTES`] and [`MAX_CLIP_FRAMES`]: 1 when it fits as it is, else 2, 4, 8… (every
/// other frame dropped, again and again). A clip that never fits keeps its still alone.
pub fn stride(frames: usize, frame_bytes: usize) -> usize {
    let mut s = 1;
    loop {
        let kept = frames.div_ceil(s);
        if kept <= 1 || (kept <= MAX_CLIP_FRAMES && kept.saturating_mul(frame_bytes) <= MAX_CLIP_BYTES) {
            return s;
        }
        s *= 2;
    }
}

/// Keeps every `stride`-th frame of `cels`, each kept frame showing for as long as it and the
/// frames dropped after it did.
pub fn thin(cels: Vec<Cel>, stride: usize) -> Vec<Cel> {
    let stride = stride.max(1);
    let mut out: Vec<Cel> = Vec::with_capacity(cels.len().div_ceil(stride));
    for (i, c) in cels.into_iter().enumerate() {
        if i % stride == 0 {
            out.push(c);
        } else if let Some(last) = out.last_mut() {
            last.gap_ms += c.gap_ms;
        }
    }
    out
}

/// The frame worker: composes every frame of the APNG in `bytes`, keeps every `stride`-th,
/// fits each kept one into `box_w`×`box_h` (fading its bottom when `fade`, like the still)
/// and sends it down `tx` — the still's own gap first, then the frames after it. Stops
/// quietly when the receiver is gone.
pub fn stream(
    bytes: &[u8],
    box_w: u32,
    box_h: u32,
    fit: Fit,
    fade: bool,
    stride: usize,
    tx: &Sender<Msg>,
) -> io::Result<()> {
    let Some(mut apng) = Apng::open(bytes)? else { return Ok(()) };
    let (sw, sh) = apng.size();
    let stride = stride.max(1);
    let mut sent = 0usize;
    // The kept frame waiting for the gaps of the frames dropped after it.
    let mut pending: Option<Cel> = None;
    let send = |c: Cel, sent: &mut usize| -> bool {
        let msg = if *sent == 0 { Msg::StillGap(c.gap_ms) } else { Msg::Frame(c) };
        *sent += 1;
        tx.send(msg).is_ok()
    };
    let mut i = 0usize;
    while let Some(cel) = apng.next_frame()? {
        if i % stride == 0 {
            if let Some(p) = pending.take() {
                if !send(p, &mut sent) {
                    return Ok(());
                }
            }
            // The still's pixels are already on screen: only its gap is wanted.
            let rgba = if i == 0 {
                Vec::new()
            } else {
                let (w, h, mut rgba) = file::fit_into(sw, sh, &cel.rgba, box_w, box_h, fit);
                if fade {
                    shapes::fade_bottom(w, h, &mut rgba, super::HEADER_FADE);
                }
                rgba
            };
            pending = Some(Cel { gap_ms: cel.gap_ms, rgba });
        } else if let Some(p) = pending.as_mut() {
            p.gap_ms += cel.gap_ms;
        }
        i += 1;
    }
    if let Some(p) = pending {
        send(p, &mut sent);
    }
    Ok(())
}

#[cfg(test)]
pub(crate) mod tests {
    use super::*;
    use png::ColorType;

    const RED: [u8; 4] = [255, 0, 0, 255];
    const GREEN: [u8; 4] = [0, 255, 0, 255];
    const HALF_BLUE: [u8; 4] = [0, 0, 255, 128];

    fn solid(n: usize, c: [u8; 4]) -> Vec<u8> {
        (0..n).flat_map(|_| c).collect()
    }

    /// A 4×4 APNG in four frames: red everywhere (100 ms); half-blue 2×2 at (1,1) OVER,
    /// disposed to PREVIOUS (50 ms); green 2×2 at (0,0) SOURCE, disposed to BACKGROUND
    /// (10 ms); a transparent pixel at (3,3) OVER (200 ms).
    pub(crate) fn tiny_apng() -> Vec<u8> {
        let mut out = Vec::new();
        {
            let mut e = png::Encoder::new(&mut out, 4, 4);
            e.set_color(ColorType::Rgba);
            e.set_depth(png::BitDepth::Eight);
            e.set_animated(4, 0).unwrap();
            e.set_frame_delay(1, 10).unwrap();
            let mut w = e.write_header().unwrap();
            w.write_image_data(&solid(16, RED)).unwrap();
            w.set_frame_dimension(2, 2).unwrap();
            w.set_frame_position(1, 1).unwrap();
            w.set_frame_delay(1, 20).unwrap();
            w.set_blend_op(BlendOp::Over).unwrap();
            w.set_dispose_op(DisposeOp::Previous).unwrap();
            w.write_image_data(&solid(4, HALF_BLUE)).unwrap();
            w.set_frame_position(0, 0).unwrap();
            w.set_frame_delay(1, 0).unwrap();
            w.set_blend_op(BlendOp::Source).unwrap();
            w.set_dispose_op(DisposeOp::Background).unwrap();
            w.write_image_data(&solid(4, GREEN)).unwrap();
            w.set_frame_dimension(1, 1).unwrap();
            w.set_frame_position(3, 3).unwrap();
            w.set_frame_delay(1, 5).unwrap();
            w.set_blend_op(BlendOp::Over).unwrap();
            w.set_dispose_op(DisposeOp::None).unwrap();
            w.write_image_data(&[0, 0, 0, 0]).unwrap();
            w.finish().unwrap();
        }
        out
    }

    fn px(rgba: &[u8], x: usize, y: usize) -> [u8; 4] {
        let i = (y * 4 + x) * 4;
        [rgba[i], rgba[i + 1], rgba[i + 2], rgba[i + 3]]
    }

    #[test]
    fn composes_dispose_and_blend_onto_a_full_canvas() {
        let bytes = tiny_apng();
        let mut a = Apng::open(&bytes).unwrap().expect("an APNG");
        assert_eq!(a.size(), (4, 4));
        assert_eq!(a.frames(), 4);
        let f0 = a.next_frame().unwrap().unwrap();
        assert_eq!(f0.gap_ms, 100);
        assert_eq!(f0.rgba, solid(16, RED));
        let f1 = a.next_frame().unwrap().unwrap();
        assert_eq!(f1.gap_ms, 50);
        assert_eq!(px(&f1.rgba, 0, 0), RED, "outside the subframe");
        let blended = px(&f1.rgba, 1, 1);
        assert_eq!(blended[3], 255);
        assert!(blended[0] > 100 && blended[0] < 140 && blended[2] > 100 && blended[2] < 140, "{blended:?}");
        let f2 = a.next_frame().unwrap().unwrap();
        assert_eq!(f2.gap_ms, 10, "a zero denominator counts hundredths");
        assert_eq!(px(&f2.rgba, 2, 2), RED, "PREVIOUS put the red back under the blue");
        assert_eq!(px(&f2.rgba, 0, 0), GREEN);
        assert_eq!(px(&f2.rgba, 1, 1), GREEN, "SOURCE replaces");
        let f3 = a.next_frame().unwrap().unwrap();
        assert_eq!(f3.gap_ms, 200);
        assert_eq!(px(&f3.rgba, 0, 0), [0, 0, 0, 0], "BACKGROUND cleared the green");
        assert_eq!(px(&f3.rgba, 1, 1), [0, 0, 0, 0]);
        assert_eq!(px(&f3.rgba, 2, 2), RED);
        assert_eq!(px(&f3.rgba, 3, 3), RED, "a transparent pixel OVER changes nothing");
        assert!(a.next_frame().unwrap().is_none());
    }

    #[test]
    fn a_plain_png_is_not_a_clip() {
        let mut out = Vec::new();
        {
            let mut e = png::Encoder::new(&mut out, 2, 2);
            e.set_color(ColorType::Rgb);
            e.set_depth(png::BitDepth::Eight);
            let mut w = e.write_header().unwrap();
            w.write_image_data(&[7u8; 12]).unwrap();
        }
        assert!(Apng::open(&out).unwrap().is_none());
        assert!(Apng::open(b"\xff\xd8not a png").unwrap().is_none());
    }

    #[test]
    fn the_guard_halves_until_it_fits() {
        assert_eq!(stride(10, 100), 1);
        assert_eq!(stride(48, MAX_CLIP_BYTES / 48), 1, "exactly at the limit fits");
        assert_eq!(stride(48, MAX_CLIP_BYTES / 48 + 1), 2, "one byte over: every other frame");
        assert_eq!(stride(100, 100), 4, "100 frames → 50 → 25");
        assert_eq!(stride(49, 1), 2);
        let alone = stride(5, MAX_CLIP_BYTES + 1);
        assert_eq!(5usize.div_ceil(alone), 1, "a frame too big for the quota leaves the still alone");
        assert_eq!(stride(1, MAX_CLIP_BYTES * 2), 1);
    }

    #[test]
    fn thinning_folds_the_dropped_gaps_into_the_kept_frames() {
        let cels: Vec<Cel> = (0..5).map(|i| Cel { gap_ms: 10 * (i + 1), rgba: vec![i as u8] }).collect();
        let kept = thin(cels, 2);
        assert_eq!(kept.iter().map(|c| c.rgba[0]).collect::<Vec<_>>(), vec![0, 2, 4]);
        assert_eq!(kept.iter().map(|c| c.gap_ms).collect::<Vec<_>>(), vec![30, 70, 50]);
    }

    #[test]
    fn the_worker_sends_the_still_gap_then_the_kept_frames_fitted() {
        let bytes = tiny_apng();
        let (tx, rx) = std::sync::mpsc::channel();
        stream(&bytes, 4, 4, Fit::Contain, false, 2, &tx).unwrap();
        drop(tx);
        let msgs: Vec<Msg> = rx.iter().collect();
        assert_eq!(msgs.len(), 2);
        assert_eq!(msgs[0], Msg::StillGap(150), "frames 0 and 1");
        let Msg::Frame(f) = &msgs[1] else { panic!("a frame") };
        assert_eq!(f.gap_ms, 210, "frames 2 and 3");
        assert_eq!(px(&f.rgba, 0, 0), GREEN);
        assert_eq!(px(&f.rgba, 2, 2), RED);
        // Without thinning, every frame after the still comes through, scaled into the box.
        let (tx, rx) = std::sync::mpsc::channel();
        stream(&bytes, 2, 2, Fit::Contain, false, 1, &tx).unwrap();
        drop(tx);
        let msgs: Vec<Msg> = rx.iter().collect();
        assert_eq!(msgs.len(), 4);
        assert_eq!(msgs[0], Msg::StillGap(100));
        assert!(msgs[1..].iter().all(|m| matches!(m, Msg::Frame(f) if f.rgba.len() == 2 * 2 * 4)));
    }
}
