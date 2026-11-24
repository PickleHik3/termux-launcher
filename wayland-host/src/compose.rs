//! A window's view as a stack of surfaces: where each surface lands on the canvas, how its
//! pixels are drawn there, and which surface a point hits.
//!
//! The canvas is the window's content (its xdg window geometry, else the bounding box of its
//! surface tree) in the root buffer's pixels: canvas pixel (0, 0) is the content's logical
//! origin, and one logical unit is `k` canvas pixels (the root buffer's pixels per logical unit,
//! so a lone root surface is copied one to one). Every surface is placed by its origin and
//! logical size; a surface whose buffer has another density is resampled (nearest pixel).
//!
//! Surfaces are drawn back to front: the first with a plain copy, the rest with premultiplied
//! source-over (opaque formats are a plain copy). Clients choose every coordinate and size, so
//! everything is cut to the canvas before it indexes a pixel, and the source rectangle is checked
//! against the readable bytes.

use crate::geometry::{content_span, Rect, RectF};
use crate::pixels::{self, PixelFormat};

/// Logical coordinates and canvas pixels past this are cut before they become integers.
pub const MAX_COORD: f64 = (1 << 24) as f64;

fn coord(v: f64) -> f64 {
    if v.is_nan() {
        0.0
    } else {
        v.clamp(-MAX_COORD, MAX_COORD)
    }
}

/// The union of rectangles with a positive size; None when there are none.
pub fn bounds<I: IntoIterator<Item = RectF>>(rects: I) -> Option<RectF> {
    let mut acc: Option<(f64, f64, f64, f64)> = None;
    for (x, y, w, h) in rects {
        if !(w > 0.0 && h > 0.0) {
            continue;
        }
        let (x0, y0, x1, y1) = (coord(x), coord(y), coord(x + w), coord(y + h));
        acc = Some(match acc {
            None => (x0, y0, x1, y1),
            Some((a, b, c, d)) => (a.min(x0), b.min(y0), c.max(x1), d.max(y1)),
        });
    }
    acc.map(|(x0, y0, x1, y1)| (x0, y0, x1 - x0, y1 - y0))
}

/// The intersection of two logical rectangles, None when empty.
pub fn intersect_f(a: RectF, b: RectF) -> Option<RectF> {
    let x0 = a.0.max(b.0);
    let y0 = a.1.max(b.1);
    let x1 = (a.0 + a.2).min(b.0 + b.2);
    let y1 = (a.1 + a.3).min(b.1 + b.3);
    if x1 > x0 && y1 > y0 {
        Some((x0, y0, x1 - x0, y1 - y0))
    } else {
        None
    }
}

/// What the view shows of a window, in the root surface's logical coordinates: the window
/// geometry cut to the surface tree's bounding box, else the whole tree (xdg_surface rules).
pub fn window_content(geometry: Option<Rect>, tree: RectF) -> RectF {
    match geometry {
        Some((x, y, w, h)) if w > 0 && h > 0 => {
            intersect_f((x as f64, y as f64, w as f64, h as f64), tree).unwrap_or(tree)
        }
        _ => tree,
    }
}

/// The canvas size in pixels for `content` at `k` pixels per logical unit (not yet cut to the
/// post limit).
pub fn canvas_size(content: RectF, k: (f64, f64)) -> (i32, i32) {
    let w = coord((content.2 * k.0).round()).max(0.0);
    let h = coord((content.3 * k.1).round()).max(0.0);
    (w as i32, h as i32)
}

/// Where a surface with logical `origin` and `size` lands on the canvas, in canvas pixels; may be
/// partly or wholly off the canvas. Edges are rounded, so neighbours share their edge.
pub fn place(origin: (f64, f64), size: (f64, f64), content_origin: (f64, f64), k: (f64, f64)) -> Rect {
    let x0 = coord(((origin.0 - content_origin.0) * k.0).round());
    let y0 = coord(((origin.1 - content_origin.1) * k.1).round());
    let x1 = coord(((origin.0 + size.0 - content_origin.0) * k.0).round());
    let y1 = coord(((origin.1 + size.1 - content_origin.1) * k.1).round());
    (x0 as i32, y0 as i32, (x1 - x0).max(0.0) as i32, (y1 - y0).max(0.0) as i32)
}

/// `r` cut to a `canvas`-sized area at the origin; None when nothing is left.
pub fn clip(r: Rect, canvas: (i32, i32)) -> Option<Rect> {
    let x0 = (r.0 as i64).max(0);
    let y0 = (r.1 as i64).max(0);
    let x1 = (r.0 as i64 + r.2.max(0) as i64).min(canvas.0.max(0) as i64);
    let y1 = (r.1 as i64 + r.3.max(0) as i64).min(canvas.1.max(0) as i64);
    if x1 > x0 && y1 > y0 {
        Some((x0 as i32, y0 as i32, (x1 - x0) as i32, (y1 - y0) as i32))
    } else {
        None
    }
}

/// A popup's surface origin in the root surface's coordinates: its parent's surface origin, plus
/// the parent's window geometry origin (the positioner works relative to it), plus the popup's
/// position, minus the popup's own window geometry origin (its shadow).
pub fn popup_origin(parent_origin: (f64, f64), parent_geometry: (i32, i32), position: (i32, i32), own_geometry: (i32, i32)) -> (f64, f64) {
    (
        coord(parent_origin.0 + parent_geometry.0 as f64 + position.0 as f64 - own_geometry.0 as f64),
        coord(parent_origin.1 + parent_geometry.1 as f64 + position.1 as f64 - own_geometry.1 as f64),
    )
}

/// How a surface's pixels meet what is under them.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Mode {
    /// Replace (the bottom surface, or an opaque format).
    Copy,
    /// Premultiplied source-over.
    Over,
}

/// Clears `canvas` (RGBA rows of `dst_stride` bytes) to transparent. False when `dst` is short.
pub fn clear(dst: &mut [u8], dst_stride: usize, canvas: (i32, i32)) -> bool {
    let (w, h) = (canvas.0.max(0) as usize, canvas.1.max(0) as usize);
    if w == 0 || h == 0 {
        return true;
    }
    if dst_stride < w * 4 || (h - 1) * dst_stride + w * 4 > dst.len() {
        return false;
    }
    for y in 0..h {
        dst[y * dst_stride..y * dst_stride + w * 4].fill(0);
    }
    true
}

/// Draws the `src_rect` part of a client buffer (`stride` bytes per row, first pixel at `offset`
/// in `src`) into `dst_rect` of an RGBA canvas, cut to the canvas. Equal sizes copy pixel for
/// pixel; otherwise each canvas pixel takes the nearest source pixel. False when nothing could be
/// drawn (empty, off the canvas, or a source rectangle the bytes do not hold).
#[allow(clippy::too_many_arguments)]
pub fn draw(
    src: &[u8],
    offset: usize,
    stride: usize,
    src_rect: Rect,
    format: PixelFormat,
    dst: &mut [u8],
    dst_stride: usize,
    canvas: (i32, i32),
    dst_rect: Rect,
    mode: Mode,
) -> bool {
    let (_, _, sw, sh) = src_rect;
    let (rx, ry, rw, rh) = dst_rect;
    if sw <= 0 || sh <= 0 || rw <= 0 || rh <= 0 {
        return false;
    }
    let Some((cx, cy, cw, ch)) = clip(dst_rect, canvas) else { return false };
    let (cx, cy, cw, ch) = (cx as usize, cy as usize, cw as usize, ch as usize);
    if dst_stride < (cx + cw) * 4 || (cy + ch - 1) * dst_stride + (cx + cw) * 4 > dst.len() {
        return false;
    }
    let Some((start, bytes)) = content_span(offset, stride, src_rect, src.len()) else { return false };
    let Some(src) = src.get(start..start + bytes) else { return false };
    let (sw, sh) = (sw as usize, sh as usize);
    let one_to_one = rw as usize == sw && rh as usize == sh;
    let opaque_copy = mode == Mode::Copy || format.opaque;
    for y in cy..cy + ch {
        let dy = (y as i64 - ry as i64) as usize;
        // Nearest pixel, sampled at the canvas pixel's centre.
        let sy = if one_to_one { dy } else { ((2 * dy + 1) * sh) / (2 * rh as usize) };
        let Some(srow) = src.get(sy * stride..) else { return false };
        let drow = &mut dst[y * dst_stride + cx * 4..y * dst_stride + (cx + cw) * 4];
        if one_to_one {
            let sx = (cx as i64 - rx as i64) as usize;
            let Some(s) = srow.get(sx * 4..(sx + cw) * 4) else { return false };
            if opaque_copy {
                pixels::convert_row(s, drow, cw, format);
            } else {
                pixels::blend_row(s, drow, cw, format);
            }
        } else {
            for (j, d) in drow.chunks_exact_mut(4).enumerate() {
                let dx = (cx as i64 + j as i64 - rx as i64) as usize;
                let sx = ((2 * dx + 1) * sw) / (2 * rw as usize);
                let Some(s) = srow.get(sx * 4..sx * 4 + 4) else { return false };
                let sv = pixels::to_rgba(u32::from_le_bytes([s[0], s[1], s[2], s[3]]), format);
                let out = if opaque_copy { sv } else { pixels::over(sv, u32::from_le_bytes([d[0], d[1], d[2], d[3]])) };
                d.copy_from_slice(&out.to_le_bytes());
            }
        }
    }
    true
}

/// One surface for hit testing: its logical rectangle in the root surface's coordinates and its
/// input region, as a range of `(add, rect)` entries (surface-local) in a shared list; None is
/// the whole surface.
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct HitLayer {
    pub rect: RectF,
    pub region: Option<(usize, usize)>,
}

/// wl_region semantics: rectangles added and subtracted in order.
pub fn region_contains(rects: &[(bool, Rect)], x: i32, y: i32) -> bool {
    let mut inside = false;
    for &(add, (rx, ry, rw, rh)) in rects {
        let hit = x as i64 >= rx as i64
            && y as i64 >= ry as i64
            && (x as i64) < rx as i64 + rw as i64
            && (y as i64) < ry as i64 + rh as i64;
        if hit {
            inside = add;
        }
    }
    inside
}

/// The topmost layer (the last in `layers`, which is back to front) whose rectangle and input
/// region hold the point, with the point in that surface's coordinates.
pub fn hit_test(layers: &[HitLayer], rects: &[(bool, Rect)], x: f64, y: f64) -> Option<(usize, (f64, f64))> {
    for (i, l) in layers.iter().enumerate().rev() {
        let (lx, ly) = (x - l.rect.0, y - l.rect.1);
        if !(lx >= 0.0 && ly >= 0.0 && lx < l.rect.2 && ly < l.rect.3) {
            continue;
        }
        if let Some((a, b)) = l.region {
            let Some(r) = rects.get(a..b) else { continue };
            if !region_contains(r, lx.floor() as i32, ly.floor() as i32) {
                continue;
            }
        }
        return Some((i, (lx, ly)));
    }
    None
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::pixels::{ARGB, XRGB};

    /// A w x h ARGB buffer filled with one word.
    fn buf(w: usize, h: usize, v: u32) -> Vec<u8> {
        v.to_le_bytes().iter().copied().cycle().take(w * h * 4).collect()
    }

    fn px(canvas: &[u8], stride: usize, x: usize, y: usize) -> [u8; 4] {
        let i = y * stride + x * 4;
        [canvas[i], canvas[i + 1], canvas[i + 2], canvas[i + 3]]
    }

    #[test]
    fn content_is_the_geometry_inside_the_tree_else_the_tree() {
        let tree = bounds([(0.0, 0.0, 100.0, 80.0), (90.0, -10.0, 30.0, 30.0)]).unwrap();
        assert_eq!(tree, (0.0, -10.0, 120.0, 90.0));
        assert_eq!(window_content(None, tree), tree);
        assert_eq!(window_content(Some((10, 10, 50, 50)), tree), (10.0, 10.0, 50.0, 50.0));
        // A geometry past the tree is cut to it; one outside falls back to the tree.
        assert_eq!(window_content(Some((100, 0, 100, 100)), tree), (100.0, 0.0, 20.0, 80.0));
        assert_eq!(window_content(Some((500, 500, 10, 10)), tree), tree);
        assert_eq!(bounds([(0.0, 0.0, 0.0, 5.0)]), None);
    }

    #[test]
    fn a_far_away_subsurface_is_bounded_not_overflowed() {
        let b = bounds([(0.0, 0.0, 10.0, 10.0), (i32::MAX as f64 * 4.0, 0.0, i32::MAX as f64, 1.0)]).unwrap();
        assert!(b.2 <= 2.0 * MAX_COORD);
        let r = place((f64::MAX, f64::NAN), (1e300, 1e300), (0.0, 0.0), (3.0, 3.0));
        assert!(clip(r, (100, 100)).is_none() || r.0 == 0);
        assert_eq!(clip((i32::MAX, i32::MAX, i32::MAX, i32::MAX), (100, 100)), None);
        assert_eq!(clip((i32::MIN, i32::MIN, i32::MAX, i32::MAX), (100, 100)), None);
    }

    #[test]
    fn placement_scales_from_the_content_origin() {
        // Scale 3: a subsurface at logical (10, 20), 40x30, content starts at (5, 5).
        assert_eq!(place((10.0, 20.0), (40.0, 30.0), (5.0, 5.0), (3.0, 3.0)), (15, 45, 120, 90));
        assert_eq!(canvas_size((5.0, 5.0, 360.0, 720.0), (3.0, 3.0)), (1080, 2160));
        assert_eq!(clip((-10, 5, 30, 200), (100, 100)), Some((0, 5, 20, 95)));
    }

    #[test]
    fn popup_origin_follows_the_parent_geometry_and_its_own_shadow() {
        // Parent at the root with a 20 px shadow; popup at (100, 40) of the parent's geometry
        // with a 10 px shadow of its own.
        assert_eq!(popup_origin((0.0, 0.0), (20, 20), (100, 40), (10, 10)), (110.0, 50.0));
        // A nested popup adds to its parent popup's origin.
        assert_eq!(popup_origin((110.0, 50.0), (10, 10), (0, 30), (10, 10)), (110.0, 80.0));
    }

    #[test]
    fn a_tree_composes_back_to_front_with_source_over() {
        // A transparent 4x4 root (Firefox's toplevel buffer), an opaque 2x2 subsurface at (1, 1)
        // and a half-black 2x2 popup at (2, 2).
        let canvas = (4, 4);
        let stride = 16;
        let mut dst = vec![0xAAu8; stride * 4];
        assert!(clear(&mut dst, stride, canvas));
        let root = buf(4, 4, 0);
        assert!(draw(&root, 0, 16, (0, 0, 4, 4), ARGB, &mut dst, stride, canvas, (0, 0, 4, 4), Mode::Copy));
        let sub = buf(2, 2, 0x00_FF_00_00); // XRGB red
        assert!(draw(&sub, 0, 8, (0, 0, 2, 2), XRGB, &mut dst, stride, canvas, (1, 1, 2, 2), Mode::Over));
        let popup = buf(2, 2, 0x80_00_00_00);
        assert!(draw(&popup, 0, 8, (0, 0, 2, 2), ARGB, &mut dst, stride, canvas, (2, 2, 2, 2), Mode::Over));
        assert_eq!(px(&dst, stride, 0, 0), [0, 0, 0, 0]);
        assert_eq!(px(&dst, stride, 1, 1), [0xFF, 0, 0, 0xFF]);
        // Half black over red.
        assert_eq!(px(&dst, stride, 2, 2), [0x7F, 0, 0, 0xFF]);
        // Half black over nothing.
        assert_eq!(px(&dst, stride, 3, 3), [0, 0, 0, 0x80]);
    }

    #[test]
    fn a_surface_partly_off_the_canvas_is_cut() {
        let canvas = (4, 4);
        let mut dst = vec![0u8; 64];
        // 4x4 source of distinct pixels drawn at (-2, -2): only its bottom-right quarter shows.
        let mut src = vec![0u8; 64];
        for i in 0..16u32 {
            src[i as usize * 4..i as usize * 4 + 4].copy_from_slice(&(0xFF00_0000 | i).to_le_bytes());
        }
        assert!(draw(&src, 0, 16, (0, 0, 4, 4), crate::pixels::ABGR, &mut dst, 16, canvas, (-2, -2, 4, 4), Mode::Copy));
        assert_eq!(px(&dst, 16, 0, 0)[0], 10); // source (2, 2)
        assert_eq!(px(&dst, 16, 1, 1)[0], 15); // source (3, 3)
        assert_eq!(px(&dst, 16, 2, 2), [0, 0, 0, 0]);
        // Entirely off the canvas: nothing.
        assert!(!draw(&src, 0, 16, (0, 0, 4, 4), ARGB, &mut dst, 16, canvas, (4, 0, 4, 4), Mode::Copy));
    }

    #[test]
    fn a_denser_or_sparser_buffer_is_resampled() {
        // A scale-1 2x1 subsurface on a scale-2 canvas covers 4x2 pixels.
        let mut src = Vec::new();
        src.extend_from_slice(&0xFF_00_00_01u32.to_le_bytes());
        src.extend_from_slice(&0xFF_00_00_02u32.to_le_bytes());
        let mut dst = vec![0u8; 4 * 2 * 4];
        assert!(draw(&src, 0, 8, (0, 0, 2, 1), crate::pixels::ABGR, &mut dst, 16, (4, 2), (0, 0, 4, 2), Mode::Copy));
        let firsts: Vec<u8> = (0..4).map(|x| px(&dst, 16, x, 1)[0]).collect();
        assert_eq!(firsts, vec![1, 1, 2, 2]);
    }

    #[test]
    fn a_source_rect_the_bytes_do_not_hold_draws_nothing() {
        let src = buf(2, 2, 0xFFFF_FFFF);
        let mut dst = vec![0u8; 64];
        assert!(!draw(&src, 0, 8, (0, 0, 2, 3), ARGB, &mut dst, 16, (4, 4), (0, 0, 2, 3), Mode::Copy));
        assert!(!draw(&src, 8, 8, (0, 0, 2, 2), ARGB, &mut dst, 16, (4, 4), (0, 0, 2, 2), Mode::Copy));
        // A destination slice shorter than the canvas.
        let mut short = vec![0u8; 20];
        assert!(!draw(&src, 0, 8, (0, 0, 2, 2), ARGB, &mut short, 16, (4, 4), (0, 0, 2, 2), Mode::Copy));
        assert!(!clear(&mut short, 16, (4, 4)));
        assert_eq!(dst, vec![0u8; 64]);
    }

    #[test]
    fn hits_go_to_the_topmost_surface_inside_its_input_region() {
        let layers = [
            HitLayer { rect: (0.0, 0.0, 100.0, 100.0), region: None },
            HitLayer { rect: (10.0, 10.0, 20.0, 20.0), region: None },
            // A popup whose input region leaves out its 5 px shadow.
            HitLayer { rect: (50.0, 50.0, 40.0, 40.0), region: Some((0, 1)) },
        ];
        let rects = [(true, (5, 5, 30, 30))];
        assert_eq!(hit_test(&layers, &rects, 15.0, 15.0), Some((1, (5.0, 5.0))));
        assert_eq!(hit_test(&layers, &rects, 60.0, 60.0), Some((2, (10.0, 10.0))));
        // The popup's shadow passes the touch to the root.
        assert_eq!(hit_test(&layers, &rects, 52.0, 52.0), Some((0, (52.0, 52.0))));
        assert_eq!(hit_test(&layers, &rects, 150.0, 5.0), None);
        // A bad range is no hit on that layer.
        let bad = [HitLayer { rect: (0.0, 0.0, 10.0, 10.0), region: Some((3, 9)) }];
        assert_eq!(hit_test(&bad, &rects, 1.0, 1.0), None);
    }

    #[test]
    fn regions_add_and_subtract_in_order() {
        let r = [(true, (0, 0, 10, 10)), (false, (2, 2, 4, 4)), (true, (3, 3, 1, 1))];
        assert!(region_contains(&r, 0, 0));
        assert!(!region_contains(&r, 2, 2));
        assert!(region_contains(&r, 3, 3));
        assert!(!region_contains(&r, 10, 0));
        assert!(!region_contains(&[], 0, 0));
    }
}
