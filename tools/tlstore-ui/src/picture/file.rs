//! JPEG/PNG files from disk, decoded to RGBA and scaled into a pixel box.

use std::io;
use std::path::Path;

/// How a picture goes into its box.
#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash)]
pub enum Fit {
    /// Whole picture visible, aspect kept; the result may be smaller than the box on one axis.
    Contain,
    /// Box filled exactly, aspect kept; the overflow is cropped evenly from both sides.
    Cover,
    /// Box width filled, aspect kept; a picture taller than the box is cropped evenly top and
    /// bottom, a wider one comes back shorter than the box.
    Width,
}

/// Decodes a PNG or JPEG (by its first bytes) to (width, height, straight RGBA).
pub fn decode(bytes: &[u8]) -> io::Result<(u32, u32, Vec<u8>)> {
    if bytes.starts_with(b"\x89PNG\r\n\x1a\n") {
        decode_png(bytes)
    } else if bytes.starts_with(&[0xff, 0xd8]) {
        decode_jpeg(bytes)
    } else {
        Err(io::Error::new(io::ErrorKind::InvalidData, "not a PNG or JPEG file"))
    }
}

fn bad<E: std::fmt::Display>(e: E) -> io::Error {
    io::Error::new(io::ErrorKind::InvalidData, e.to_string())
}

fn decode_png(bytes: &[u8]) -> io::Result<(u32, u32, Vec<u8>)> {
    let mut dec = png::Decoder::new(bytes);
    dec.set_transformations(png::Transformations::EXPAND | png::Transformations::STRIP_16);
    let mut r = dec.read_info().map_err(bad)?;
    let mut buf = vec![0; r.output_buffer_size()];
    let info = r.next_frame(&mut buf).map_err(bad)?;
    let (w, h) = (info.width, info.height);
    let px = (w * h) as usize;
    let rgba = to_rgba(info.color_type, &buf[..info.buffer_size()], px)?;
    Ok((w, h, rgba))
}

/// Expanded, 8-bit PNG samples of `px` pixels in `color` to straight RGBA.
pub(crate) fn to_rgba(color: png::ColorType, src: &[u8], px: usize) -> io::Result<Vec<u8>> {
    Ok(match color {
        png::ColorType::Rgba => src.chunks(4).take(px).flatten().copied().collect(),
        png::ColorType::Rgb => src.chunks(3).take(px).flat_map(|c| [c[0], c[1], c[2], 255]).collect(),
        png::ColorType::GrayscaleAlpha => {
            src.chunks(2).take(px).flat_map(|c| [c[0], c[0], c[0], c[1]]).collect()
        }
        png::ColorType::Grayscale => src.iter().take(px).flat_map(|&g| [g, g, g, 255]).collect(),
        png::ColorType::Indexed => return Err(bad("indexed PNG was not expanded")),
    })
}

fn decode_jpeg(bytes: &[u8]) -> io::Result<(u32, u32, Vec<u8>)> {
    use zune_jpeg::zune_core::colorspace::ColorSpace;
    use zune_jpeg::zune_core::options::DecoderOptions;
    let opts = DecoderOptions::default().jpeg_set_out_colorspace(ColorSpace::RGBA);
    let mut d = zune_jpeg::JpegDecoder::new_with_options(bytes, opts);
    let px = d.decode().map_err(|e| bad(format!("{e:?}")))?;
    let (w, h) = d.dimensions().ok_or_else(|| bad("JPEG without dimensions"))?;
    Ok((w as u32, h as u32, px))
}

/// Loads `path` and scales it into `box_w`×`box_h` pixels.
pub fn load_scaled(path: &Path, box_w: u32, box_h: u32, fit: Fit) -> io::Result<(u32, u32, Vec<u8>)> {
    let bytes = std::fs::read(path)?;
    let (w, h, rgba) = decode(&bytes)?;
    Ok(fit_into(w, h, &rgba, box_w, box_h, fit))
}

/// Scales straight-alpha RGBA into a box per `fit`.
pub fn fit_into(sw: u32, sh: u32, src: &[u8], box_w: u32, box_h: u32, fit: Fit) -> (u32, u32, Vec<u8>) {
    let (bw, bh) = (box_w.max(1), box_h.max(1));
    if sw == 0 || sh == 0 {
        return (bw, bh, vec![0; (bw * bh * 4) as usize]);
    }
    match fit {
        Fit::Contain => {
            let s = (bw as f64 / sw as f64).min(bh as f64 / sh as f64);
            let dw = ((sw as f64 * s).round() as u32).clamp(1, bw);
            let dh = ((sh as f64 * s).round() as u32).clamp(1, bh);
            (dw, dh, resize(sw, sh, src, (0.0, 0.0, sw as f64, sh as f64), dw, dh))
        }
        Fit::Width if (sh as f64 * bw as f64 / sw as f64).round() as u32 <= bh => {
            let dh = ((sh as f64 * bw as f64 / sw as f64).round() as u32).max(1);
            (bw, dh, resize(sw, sh, src, (0.0, 0.0, sw as f64, sh as f64), bw, dh))
        }
        Fit::Cover | Fit::Width => {
            let s = (bw as f64 / sw as f64).max(bh as f64 / sh as f64);
            let cw = bw as f64 / s;
            let ch = bh as f64 / s;
            let cx = (sw as f64 - cw) / 2.0;
            let cy = (sh as f64 - ch) / 2.0;
            (bw, bh, resize(sw, sh, src, (cx, cy, cw, ch), bw, bh))
        }
    }
}

/// Resamples the source window (x, y, w, h) in source pixels to dw×dh with a separable tent
/// filter: bilinear when enlarging, an area average when shrinking. Works in premultiplied
/// alpha so transparent edges do not darken.
pub fn resize(sw: u32, sh: u32, src: &[u8], win: (f64, f64, f64, f64), dw: u32, dh: u32) -> Vec<u8> {
    let (sw, sh, dw, dh) = (sw as usize, sh as usize, dw as usize, dh as usize);
    let pre: Vec<[f32; 4]> = src
        .chunks(4)
        .take(sw * sh)
        .map(|p| {
            let a = p[3] as f32 / 255.0;
            [p[0] as f32 * a, p[1] as f32 * a, p[2] as f32 * a, p[3] as f32]
        })
        .collect();

    let weights = |n_dst: usize, start: f64, len: f64, n_src: usize| -> Vec<Vec<(usize, f32)>> {
        let scale = len / n_dst as f64;
        let support = scale.max(1.0);
        (0..n_dst)
            .map(|i| {
                let centre = start + (i as f64 + 0.5) * scale - 0.5;
                let lo = (centre - support).floor().max(0.0) as usize;
                let hi = ((centre + support).ceil() as usize).min(n_src - 1);
                let mut v: Vec<(usize, f32)> = (lo..=hi)
                    .filter_map(|j| {
                        let w = 1.0 - ((j as f64 - centre).abs() / support);
                        (w > 0.0).then_some((j, w as f32))
                    })
                    .collect();
                if v.is_empty() {
                    v.push((centre.round().clamp(0.0, (n_src - 1) as f64) as usize, 1.0));
                }
                let sum: f32 = v.iter().map(|x| x.1).sum();
                v.iter_mut().for_each(|x| x.1 /= sum);
                v
            })
            .collect()
    };
    let wx = weights(dw, win.0, win.2, sw);
    let wy = weights(dh, win.1, win.3, sh);

    // Horizontal pass: sh × dw.
    let mut mid = vec![[0f32; 4]; sh * dw];
    for y in 0..sh {
        for (x, ws) in wx.iter().enumerate() {
            let mut acc = [0f32; 4];
            for &(j, w) in ws {
                let p = pre[y * sw + j];
                for k in 0..4 {
                    acc[k] += p[k] * w;
                }
            }
            mid[y * dw + x] = acc;
        }
    }
    // Vertical pass, then back to straight alpha.
    let mut out = vec![0u8; dw * dh * 4];
    for (y, ws) in wy.iter().enumerate() {
        for x in 0..dw {
            let mut acc = [0f32; 4];
            for &(j, w) in ws {
                let p = mid[j * dw + x];
                for k in 0..4 {
                    acc[k] += p[k] * w;
                }
            }
            let a = acc[3].clamp(0.0, 255.0);
            let i = (y * dw + x) * 4;
            if a > 0.0 {
                let inv = 255.0 / a;
                out[i] = (acc[0] * inv).round().clamp(0.0, 255.0) as u8;
                out[i + 1] = (acc[1] * inv).round().clamp(0.0, 255.0) as u8;
                out[i + 2] = (acc[2] * inv).round().clamp(0.0, 255.0) as u8;
            }
            out[i + 3] = a.round() as u8;
        }
    }
    out
}

#[cfg(test)]
mod tests {
    use super::*;

    fn solid(w: u32, h: u32, c: [u8; 4]) -> Vec<u8> {
        (0..w * h).flat_map(|_| c).collect()
    }

    #[test]
    fn contain_keeps_aspect() {
        let src = solid(400, 200, [200, 100, 50, 255]);
        let (w, h, px) = fit_into(400, 200, &src, 100, 100, Fit::Contain);
        assert_eq!((w, h), (100, 50));
        assert_eq!(&px[..4], &[200, 100, 50, 255]);
    }

    #[test]
    fn width_fills_the_width_and_crops_only_what_is_too_tall() {
        let src = solid(400, 100, [1, 2, 3, 255]);
        let (w, h, _) = fit_into(400, 100, &src, 200, 100, Fit::Width);
        assert_eq!((w, h), (200, 50), "a wide picture fills the width and comes back shorter");
        let tall = solid(100, 400, [1, 2, 3, 255]);
        let (w, h, _) = fit_into(100, 400, &tall, 200, 100, Fit::Width);
        assert_eq!((w, h), (200, 100), "a tall one fills the width and is cropped to the box");
    }

    #[test]
    fn cover_fills_box_and_crops_centre() {
        // Left half red, right half blue; a tall box keeps the middle, so both colours show.
        let mut src = Vec::new();
        for _y in 0..10 {
            for x in 0..40 {
                src.extend_from_slice(if x < 20 { &[255, 0, 0, 255] } else { &[0, 0, 255, 255] });
            }
        }
        let (w, h, px) = fit_into(40, 10, &src, 4, 10, Fit::Cover);
        assert_eq!((w, h), (4, 10));
        assert_eq!(&px[..4], &[255, 0, 0, 255]);
        assert_eq!(&px[12..16], &[0, 0, 255, 255]);
    }

    #[test]
    fn enlarging_is_smooth() {
        let src = [0, 0, 0, 255, 255, 255, 255, 255];
        let out = resize(2, 1, &src, (0.0, 0.0, 2.0, 1.0), 8, 1);
        let reds: Vec<u8> = out.chunks(4).map(|p| p[0]).collect();
        assert!(reds.windows(2).all(|w| w[0] <= w[1]), "{reds:?}");
        assert_eq!(reds[0], 0);
        assert_eq!(reds[7], 255);
    }

    #[test]
    fn png_roundtrip_from_disk() {
        let dir = std::env::temp_dir().join(format!("tlstore-ui-test-{}", std::process::id()));
        std::fs::create_dir_all(&dir).unwrap();
        let path = dir.join("p.png");
        {
            let f = std::fs::File::create(&path).unwrap();
            let mut e = png::Encoder::new(std::io::BufWriter::new(f), 6, 4);
            e.set_color(png::ColorType::Rgb);
            e.set_depth(png::BitDepth::Eight);
            let mut wr = e.write_header().unwrap();
            wr.write_image_data(&[9u8; 6 * 4 * 3]).unwrap();
        }
        let mut pics = crate::picture::Pictures::new();
        let p = pics.file(&path, 3, 3, Fit::Contain).unwrap();
        assert_eq!((p.width(), p.height()), (3, 2));
        assert_eq!(&p.rgba()[..4], &[9, 9, 9, 255]);
        let jpg = std::path::Path::new(env!("CARGO_MANIFEST_DIR")).join("tests/fixtures/tiny.jpg");
        let q = pics.file(&jpg, 8, 8, Fit::Cover).unwrap();
        assert_eq!((q.width(), q.height()), (8, 8));
        assert!(q.rgba().chunks(4).all(|p| p[3] == 255));
        assert!(pics.file(&dir.join("missing.png"), 3, 3, Fit::Cover).is_err());
        std::fs::remove_dir_all(&dir).ok();
    }
}
