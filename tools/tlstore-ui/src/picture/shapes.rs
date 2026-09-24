//! The RGBA pictures the store draws itself: the header picture's bottom fade, the cursor
//! pill and the Installing progress line. Straight alpha, row-major, like every [`super::Picture`].

use crate::render::Rgb;

/// Fades the bottom `frac` of `rgba` (a `w`×`h` picture) to alpha 0, so a header picture
/// melts into the wallpaper along its bottom edge.
pub fn fade_bottom(w: u32, h: u32, rgba: &mut [u8], frac: f32) {
    if w == 0 || h == 0 {
        return;
    }
    let start = (h as f32 * (1.0 - frac.clamp(0.0, 1.0))).floor() as u32;
    let span = h.saturating_sub(start).max(1) as f32;
    for y in start..h {
        let t = (y - start) as f32 / span;
        let keep = (1.0 - t).clamp(0.0, 1.0);
        for x in 0..w {
            let i = ((y * w + x) * 4 + 3) as usize;
            rgba[i] = (rgba[i] as f32 * keep).round() as u8;
        }
    }
}

/// Signed distance from (x, y) to a rounded rectangle `w`×`h` with corner radius `r`
/// (negative inside), in pixels; used for anti-aliased edges.
fn rounded_distance(x: f32, y: f32, w: f32, h: f32, r: f32) -> f32 {
    let r = r.min(w / 2.0).min(h / 2.0).max(0.0);
    let (cx, cy) = (w / 2.0, h / 2.0);
    let dx = (x - cx).abs() - (cx - r);
    let dy = (y - cy).abs() - (cy - r);
    let outside = (dx.max(0.0).powi(2) + dy.max(0.0).powi(2)).sqrt();
    outside + dx.max(dy).min(0.0) - r
}

/// Edge coverage 0–1 from a signed distance (one pixel of anti-aliasing).
fn coverage(d: f32) -> f32 {
    (0.5 - d).clamp(0.0, 1.0)
}

/// A rounded rectangle `w`×`h` filled with `color` at `alpha` (0–1): the cursor pill.
pub fn pill(w: u32, h: u32, radius: f32, color: Rgb, alpha: f32) -> (u32, u32, Vec<u8>) {
    let (w, h) = (w.max(1), h.max(1));
    let mut rgba = vec![0u8; (w * h * 4) as usize];
    for y in 0..h {
        for x in 0..w {
            let d = rounded_distance(x as f32 + 0.5, y as f32 + 0.5, w as f32, h as f32, radius);
            let a = coverage(d) * alpha.clamp(0.0, 1.0);
            let i = ((y * w + x) * 4) as usize;
            rgba[i..i + 4].copy_from_slice(&[color.0, color.1, color.2, (a * 255.0).round() as u8]);
        }
    }
    (w, h, rgba)
}

/// The Installing progress line, `w` pixels wide inside a picture `h` pixels tall (one row):
/// a rounded track in `track` at half alpha, and the first `pct` percent filled in `fill`
/// with a two-pixel soft glow around it. `line_h` is the line's own thickness.
pub fn progress_line(w: u32, h: u32, line_h: u32, pct: u8, track: Rgb, fill: Rgb) -> (u32, u32, Vec<u8>) {
    let (w, h) = (w.max(1), h.max(1));
    let line_h = line_h.clamp(1, h);
    let top = (h - line_h) as f32 / 2.0;
    let fill_w = (w as f32 * pct.min(100) as f32 / 100.0).round();
    let glow = 2.0;
    let mut rgba = vec![0u8; (w * h * 4) as usize];
    for y in 0..h {
        for x in 0..w {
            let (px, py) = (x as f32 + 0.5, y as f32 + 0.5 - top);
            let d_track = rounded_distance(px, py, w as f32, line_h as f32, line_h as f32 / 2.0);
            let mut a = coverage(d_track) * 0.5;
            let mut c = track;
            if fill_w > 0.0 {
                let d_fill = rounded_distance(px, py, fill_w, line_h as f32, line_h as f32 / 2.0);
                let core = coverage(d_fill);
                // The glow: alpha falling off over two pixels outside the fill.
                let halo = (1.0 - d_fill.max(0.0) / glow).clamp(0.0, 1.0) * 0.35;
                let fa = core.max(halo);
                if fa > 0.0 {
                    // Fill over track: composite, then straighten the alpha again.
                    let out_a = fa + a * (1.0 - fa);
                    let mix = |f: u8, t: u8| {
                        ((f as f32 * fa + t as f32 * a * (1.0 - fa)) / out_a.max(1e-6)).round() as u8
                    };
                    c = Rgb(mix(fill.0, track.0), mix(fill.1, track.1), mix(fill.2, track.2));
                    a = out_a;
                }
            }
            let i = ((y * w + x) * 4) as usize;
            rgba[i..i + 4].copy_from_slice(&[c.0, c.1, c.2, (a.clamp(0.0, 1.0) * 255.0).round() as u8]);
        }
    }
    (w, h, rgba)
}

#[cfg(test)]
mod tests {
    use super::*;

    fn alpha_at(rgba: &[u8], w: u32, x: u32, y: u32) -> u8 {
        rgba[((y * w + x) * 4 + 3) as usize]
    }

    #[test]
    fn bottom_fade_reaches_zero_and_leaves_the_top_alone() {
        let (w, h) = (4, 100);
        let mut rgba = vec![255u8; (w * h * 4) as usize];
        fade_bottom(w, h, &mut rgba, 0.38);
        assert_eq!(alpha_at(&rgba, w, 0, 0), 255);
        assert_eq!(alpha_at(&rgba, w, 0, 61), 255);
        let mid = alpha_at(&rgba, w, 0, 80);
        assert!(mid > 60 && mid < 200, "{mid}");
        assert!(alpha_at(&rgba, w, 0, 99) <= 8);
        // Monotone downwards.
        let col: Vec<u8> = (0..h).map(|y| alpha_at(&rgba, w, 1, y)).collect();
        assert!(col.windows(2).all(|p| p[1] <= p[0]));
    }

    #[test]
    fn pill_is_transparent_at_the_corners_and_solid_inside() {
        let (w, h, rgba) = pill(80, 20, 10.0, Rgb(1, 2, 3), 0.18);
        assert_eq!((w, h), (80, 20));
        assert_eq!(alpha_at(&rgba, w, 0, 0), 0);
        assert_eq!(alpha_at(&rgba, w, 79, 19), 0);
        assert_eq!(alpha_at(&rgba, w, 40, 10), 46);
        assert_eq!(alpha_at(&rgba, w, 40, 0), 46, "the flat top edge is fully covered");
        assert_eq!(&rgba[(10 * w as usize + 40) * 4..][..3], &[1, 2, 3]);
    }

    #[test]
    fn progress_line_fills_left_to_right_with_a_glow() {
        let (w, h, rgba) = progress_line(200, 20, 4, 50, Rgb(100, 100, 100), Rgb(255, 0, 0));
        let mid_y = 10;
        // Filled part: the fill colour, opaque.
        let i = ((mid_y * w + 20) * 4) as usize;
        assert_eq!(&rgba[i..i + 4], &[255, 0, 0, 255]);
        // Track part: the track colour at half alpha.
        let j = ((mid_y * w + 180) * 4) as usize;
        assert_eq!(&rgba[j..j + 3], &[100, 100, 100]);
        assert!((120..=135).contains(&rgba[j + 3]), "{}", rgba[j + 3]);
        // The glow: something just above the line where the fill is, nothing where it is not.
        assert!(alpha_at(&rgba, w, 20, 6) > 0);
        assert_eq!(alpha_at(&rgba, w, 180, 6), 0);
        assert_eq!(alpha_at(&rgba, w, 20, 0), 0, "the row's top edge stays clear");
        assert_eq!(h, 20);
        // Nothing filled at 0 percent.
        let (_, _, empty) = progress_line(200, 20, 4, 0, Rgb(100, 100, 100), Rgb(255, 0, 0));
        assert_eq!(&empty[i..i + 3], &[100, 100, 100]);
    }
}
