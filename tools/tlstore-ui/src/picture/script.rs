//! Script words set in Pinyon Script (SIL Open Font License, `assets/fonts/pinyonscript/OFL.txt`).

use fontdue::{Font, FontSettings};

use crate::render::Rgb;

static FACE: &[u8] = include_bytes!("../../assets/fonts/pinyonscript/PinyonScript-Regular.ttf");

pub fn load_face() -> Font {
    Font::from_bytes(FACE, FontSettings { scale: 64.0, ..FontSettings::default() })
        .expect("bundled font parses")
}

/// Rasterises `text` so the face's full line (ascender to descender) is exactly `px_h`
/// pixels; returns (width, height, straight-alpha RGBA). Swashes that reach past the line
/// are clipped at the top and bottom, and kept at the sides.
pub fn rasterise(face: &Font, text: &str, px_h: u32, color: Rgb) -> (u32, u32, Vec<u8>) {
    let px_h = px_h.max(1);
    let unit = face.horizontal_line_metrics(1.0);
    let (asc, desc) = match unit {
        Some(m) if m.ascent - m.descent > 0.0 => (m.ascent, m.descent),
        _ => (0.8, -0.2),
    };
    let size = px_h as f32 / (asc - desc);
    let baseline = asc * size;

    struct Placed {
        x: i32,
        y: i32,
        w: usize,
        h: usize,
        cov: Vec<u8>,
    }
    let mut glyphs = Vec::new();
    let mut pen = 0.0f32;
    let mut prev: Option<char> = None;
    for ch in text.chars() {
        if let Some(p) = prev {
            pen += face.horizontal_kern(p, ch, size).unwrap_or(0.0);
        }
        let (m, cov) = face.rasterize(ch, size);
        let x = (pen + m.xmin as f32).round() as i32;
        let y = (baseline - (m.height as f32 + m.ymin as f32)).round() as i32;
        glyphs.push(Placed { x, y, w: m.width, h: m.height, cov });
        pen += m.advance_width;
        prev = Some(ch);
    }
    let left = glyphs.iter().filter(|g| g.w > 0).map(|g| g.x).min().unwrap_or(0).min(0);
    let right =
        glyphs.iter().filter(|g| g.w > 0).map(|g| g.x + g.w as i32).max().unwrap_or(0).max(pen.ceil() as i32);
    let w = (right - left).max(1) as u32;
    let h = px_h;
    let mut alpha = vec![0u8; (w * h) as usize];
    for g in &glyphs {
        for gy in 0..g.h {
            let y = g.y + gy as i32;
            if y < 0 || y >= h as i32 {
                continue;
            }
            for gx in 0..g.w {
                let x = g.x - left + gx as i32;
                if x < 0 || x >= w as i32 {
                    continue;
                }
                let a = &mut alpha[y as usize * w as usize + x as usize];
                // Connecting strokes overlap; keep the stronger coverage so joins stay solid.
                *a = (*a).max(g.cov[gy * g.w + gx]);
            }
        }
    }
    let mut rgba = Vec::with_capacity(alpha.len() * 4);
    for a in alpha {
        rgba.extend_from_slice(&[color.0, color.1, color.2, a]);
    }
    (w, h, rgba)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn word_has_requested_height_and_ink() {
        let face = load_face();
        for h in [20u32, 60, 97] {
            let (w, hh, rgba) = rasterise(&face, "goodies", h, Rgb(10, 20, 30));
            assert_eq!(hh, h);
            assert_eq!(rgba.len(), (w * hh * 4) as usize);
            assert!(w > h, "a seven-letter word is wider than tall ({w}x{hh})");
            let inked = rgba.chunks(4).filter(|p| p[3] > 128).count();
            assert!(inked > (w * hh) as usize / 50, "only {inked} inked pixels");
            assert!(rgba.chunks(4).all(|p| p[..3] == [10, 20, 30]));
        }
    }

    #[test]
    fn cache_keeps_ids() {
        let mut pics = crate::picture::Pictures::new();
        let a = pics.script_word("kitten", 40, Rgb(0, 0, 0));
        let b = pics.script_word("kitten", 40, Rgb(0, 0, 0));
        let c = pics.script_word("kitten", 41, Rgb(0, 0, 0));
        assert_eq!(a.id(), b.id());
        assert_ne!(a.id(), c.id());
        assert_eq!(c.height(), 41);
    }
}
