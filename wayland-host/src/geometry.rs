//! Which part of a client's buffer is shown, how big it may be, and how view touches map back
//! into it.
//!
//! A client that draws its own shadow (GTK) makes its buffer larger than its window and says
//! where the window is with xdg_surface.set_window_geometry, in surface (logical) coordinates.
//! Only that rectangle is shown, and input is offset by its origin. Logical and buffer pixels
//! differ by the client's buffer scale (wl_surface.set_buffer_scale, for wl_output scale) or by
//! its viewport (wp_viewport, for fractional scale); `SurfaceMap` turns one into the other.

/// x, y, width, height.
pub type Rect = (i32, i32, i32, i32);
/// x, y, width, height in logical (surface) coordinates.
pub type RectF = (f64, f64, f64, f64);

/// The window geometry clipped to the buffer; the whole buffer when there is no geometry or it
/// does not overlap the buffer.
pub fn content_rect(buffer_w: i32, buffer_h: i32, geometry: Option<Rect>) -> Rect {
    let full = (0, 0, buffer_w.max(0), buffer_h.max(0));
    let Some((gx, gy, gw, gh)) = geometry else { return full };
    if gw <= 0 || gh <= 0 {
        return full;
    }
    let x0 = gx.clamp(0, buffer_w.max(0));
    let y0 = gy.clamp(0, buffer_h.max(0));
    let x1 = gx.saturating_add(gw).clamp(0, buffer_w.max(0));
    let y1 = gy.saturating_add(gh).clamp(0, buffer_h.max(0));
    if x1 <= x0 || y1 <= y0 {
        return full;
    }
    (x0, y0, x1 - x0, y1 - y0)
}

/// A point in view pixels to surface coordinates: the content rectangle (logical) is stretched
/// over the whole view.
pub fn view_to_surface(x: f32, y: f32, view: Option<(i32, i32)>, content: RectF) -> (f64, f64) {
    let (cx, cy, cw, ch) = content;
    match view {
        Some((vw, vh)) if vw > 0 && vh > 0 && cw > 0.0 && ch > 0.0 => {
            (cx + x as f64 * cw / vw as f64, cy + y as f64 * ch / vh as f64)
        }
        _ => (cx + x as f64, cy + y as f64),
    }
}

/// Byte offset of the content's first pixel and the bytes the copy reads, for a buffer with
/// `stride` bytes per row. None when the content does not fit in `len` bytes from `offset`.
pub fn content_span(offset: usize, stride: usize, content: Rect, len: usize) -> Option<(usize, usize)> {
    let (x, y, w, h) = content;
    if x < 0 || y < 0 || w <= 0 || h <= 0 || stride < (x as usize + w as usize) * 4 {
        return None;
    }
    let start = offset + y as usize * stride + x as usize * 4;
    let bytes = stride * (h as usize - 1) + w as usize * 4;
    if start.checked_add(bytes)? > len {
        return None;
    }
    Some((start, bytes))
}

/// How one commit's surface (logical) coordinates map to its buffer's pixels, per axis:
/// `buffer = offset + logical * k`.
#[derive(Clone, Copy, Debug, PartialEq)]
pub struct SurfaceMap {
    /// The surface's logical size.
    pub width: f64,
    pub height: f64,
    kx: f64,
    ky: f64,
    ox: f64,
    oy: f64,
}

/// The map for a `buffer_w` x `buffer_h` buffer with `buffer_scale`, an optional viewport
/// source crop (in buffer pixels divided by the buffer scale) and an optional viewport
/// destination (the logical size). Invalid parts fall back to the plain buffer.
pub fn surface_map(buffer_w: i32, buffer_h: i32, buffer_scale: i32, src: Option<RectF>, dst: Option<(i32, i32)>) -> SurfaceMap {
    let bs = buffer_scale.max(1) as f64;
    let (bw, bh) = (buffer_w.max(0) as f64, buffer_h.max(0) as f64);
    let src = match src {
        Some((x, y, w, h)) if x >= 0.0 && y >= 0.0 && w > 0.0 && h > 0.0 && w.is_finite() && h.is_finite() => (x, y, w, h),
        _ => (0.0, 0.0, bw / bs, bh / bs),
    };
    let (dw, dh) = match dst {
        Some((w, h)) if w > 0 && h > 0 => (w as f64, h as f64),
        _ => (src.2, src.3),
    };
    if dw <= 0.0 || dh <= 0.0 {
        return SurfaceMap { width: 0.0, height: 0.0, kx: 1.0, ky: 1.0, ox: 0.0, oy: 0.0 };
    }
    SurfaceMap { width: dw, height: dh, kx: src.2 * bs / dw, ky: src.3 * bs / dh, ox: src.0 * bs, oy: src.1 * bs }
}

impl SurfaceMap {
    /// The window geometry clipped to the surface, in logical coordinates; the whole surface
    /// when there is no usable geometry.
    pub fn logical_content(&self, geometry: Option<Rect>) -> RectF {
        let full = (0.0, 0.0, self.width, self.height);
        let Some((gx, gy, gw, gh)) = geometry else { return full };
        if gw <= 0 || gh <= 0 {
            return full;
        }
        let x0 = (gx as f64).clamp(0.0, self.width);
        let y0 = (gy as f64).clamp(0.0, self.height);
        let x1 = (gx as f64 + gw as f64).clamp(0.0, self.width);
        let y1 = (gy as f64 + gh as f64).clamp(0.0, self.height);
        if x1 <= x0 || y1 <= y0 {
            return full;
        }
        (x0, y0, x1 - x0, y1 - y0)
    }

    /// A logical rectangle in buffer pixels, rounded and clipped to the buffer.
    pub fn to_buffer(&self, r: RectF, buffer_w: i32, buffer_h: i32) -> Rect {
        let x0 = (self.ox + r.0 * self.kx).round() as i64;
        let y0 = (self.oy + r.1 * self.ky).round() as i64;
        let x1 = (self.ox + (r.0 + r.2) * self.kx).round() as i64;
        let y1 = (self.oy + (r.1 + r.3) * self.ky).round() as i64;
        let (bw, bh) = (buffer_w.max(0) as i64, buffer_h.max(0) as i64);
        let (x0, x1) = (x0.clamp(0, bw), x1.clamp(0, bw));
        let (y0, y1) = (y0.clamp(0, bh), y1.clamp(0, bh));
        (x0 as i32, y0 as i32, (x1 - x0).max(0) as i32, (y1 - y0).max(0) as i32)
    }

    /// Buffer pixels per logical unit, per axis.
    pub fn density(&self) -> (f64, f64) {
        (if self.kx > 0.0 { self.kx } else { 1.0 }, if self.ky > 0.0 { self.ky } else { 1.0 })
    }

    /// A buffer rectangle back in logical coordinates.
    pub fn to_logical(&self, r: Rect) -> RectF {
        let kx = if self.kx > 0.0 { self.kx } else { 1.0 };
        let ky = if self.ky > 0.0 { self.ky } else { 1.0 };
        ((r.0 as f64 - self.ox) / kx, (r.1 as f64 - self.oy) / ky, r.2 as f64 / kx, r.3 as f64 / ky)
    }
}

/// Smallest and largest output scale the host accepts; anything else (NaN, 0) is 1.
pub const MIN_SCALE: f64 = 0.5;
pub const MAX_SCALE: f64 = 8.0;
/// wp_fractional_scale_v1 carries scales in 120ths.
const SCALE_UNITS: f64 = 120.0;

/// The launcher's display density as a scale the protocol can carry exactly.
pub fn sane_scale(scale: f32) -> f64 {
    let s = scale as f64;
    if !s.is_finite() || s < MIN_SCALE {
        return 1.0;
    }
    (s.min(MAX_SCALE) * SCALE_UNITS).round() / SCALE_UNITS
}

/// The integer wl_output / preferred buffer scale for a fractional one: rounded up, so a client
/// that only knows integers draws at least as sharp as the screen.
pub fn integer_scale(scale: f64) -> i32 {
    (scale.ceil() as i32).max(1)
}

/// A view of `px` pixels as the logical size asked of a client drawing at `scale`.
pub fn logical_size(px: (i32, i32), scale: f64) -> (i32, i32) {
    let s = if scale > 0.0 { scale } else { 1.0 };
    (((px.0 as f64) / s).round().max(1.0) as i32, ((px.1 as f64) / s).round().max(1.0) as i32)
}

/// The size a client that knows only integer scales draws a `view` at: it gets the rounded-up
/// `integer_scale` and a logical size of view / `scale`, so its buffer is the view times
/// integer / fractional (1.2 at scale 2.5, 1.6 at 1.25). A client that reads only the output's
/// integer (`output_integer`, which may be another window's) draws at that instead, so the larger
/// of the two is allowed. That is the view the post limit is taken from, so such a window is shown
/// whole (the view scales it down) rather than cut. A client on fractional scale draws the view
/// itself and posts within it either way.
pub fn integer_scale_view(view: Option<(i32, i32)>, scale: f64, output_integer: i32) -> Option<(i32, i32)> {
    let (w, h) = view?;
    let s = if scale.is_finite() && scale > 0.0 { scale } else { 1.0 };
    let k = (integer_scale(s).max(output_integer) as f64 / s).max(1.0);
    let grow = |v: i32| ((v as f64) * k).ceil().min(i32::MAX as f64) as i32;
    Some((grow(w), grow(h)))
}

/// The largest side a posted frame may have, whatever the view: below every GPU's texture limit
/// the launcher could meet.
pub const MAX_POST_SIDE: i32 = 4096;
/// The margin over the configured view size a frame may have: an eighth of the view plus this
/// many pixels per axis. It absorbs a client still answering an older, larger configure and
/// integer-scale rounding; anything past it is cropped, never allocated on the launcher's side.
pub const POST_MARGIN_PX: i32 = 64;

/// The largest frame the host posts into a window's Surface: the view the launcher configured
/// (else the output) plus the margin, capped at `MAX_POST_SIDE`.
pub fn post_limit(view: Option<(i32, i32)>, output: (i32, i32)) -> (i32, i32) {
    let (w, h) = match view {
        Some((w, h)) if w > 0 && h > 0 => (w, h),
        _ => (output.0.max(1), output.1.max(1)),
    };
    let grow = |v: i32| v.saturating_add(v / 8).saturating_add(POST_MARGIN_PX).min(MAX_POST_SIDE);
    (grow(w), grow(h))
}

/// The limit for a frame the client drew in answer to the latest configure (`caught_up`): the
/// view's `post_limit`, widened to a square of the output's longer side. A window whose minimum
/// size is bigger than its view (a dialog wider than the page) is posted whole, and the launcher
/// shows it scaled down to fit; the square still bounds what a client makes the launcher
/// allocate. A frame of an older configure keeps the view's limit, so a stale, larger frame is
/// cropped as before.
pub fn frame_limit(view: Option<(i32, i32)>, output: (i32, i32), caught_up: bool) -> (i32, i32) {
    let limit = post_limit(view, output);
    if !caught_up {
        return limit;
    }
    let side = output.0.max(output.1).max(1);
    let whole = post_limit(Some((side, side)), output);
    (limit.0.max(whole.0), limit.1.max(whole.1))
}

/// The size, in view pixels, the launcher is told a window drew: the shown `content` (logical)
/// at the window's `scale` (pixels per logical unit; 1 for X windows, whose content is in
/// pixels). The launcher compares it with the slot and lays a bigger window out at this size,
/// scaled down. On an axis where the content fits the view's logical size it is never reported
/// wider than the view, so rounding of a fractional scale never reads as overflow.
pub fn reported_size(content: RectF, scale: f64, view: Option<(i32, i32)>) -> (i32, i32) {
    let s = if scale.is_finite() && scale > 0.0 { scale } else { 1.0 };
    let px = |c: f64| (c * s).round().clamp(0.0, i32::MAX as f64) as i32;
    let (w, h) = (px(content.2), px(content.3));
    let Some((vw, vh)) = view.filter(|v| v.0 > 0 && v.1 > 0) else { return (w, h) };
    let (lw, lh) = logical_size((vw, vh), s);
    let snap = |p: i32, c: f64, logical: i32, v: i32| if c <= logical as f64 + 1e-6 { p.min(v) } else { p };
    (snap(w, content.2, lw, vw), snap(h, content.3, lh, vh))
}

/// `r` with its size cut to `limit`, keeping its origin.
pub fn clamp_to_limit(r: Rect, limit: (i32, i32)) -> Rect {
    (r.0, r.1, r.2.min(limit.0.max(1)), r.3.min(limit.1.max(1)))
}

#[cfg(test)]
mod tests {
    use super::*;

    fn f(r: Rect) -> RectF {
        (r.0 as f64, r.1 as f64, r.2 as f64, r.3 as f64)
    }

    #[test]
    fn no_geometry_is_the_whole_buffer() {
        assert_eq!(content_rect(1120, 2204, None), (0, 0, 1120, 2204));
    }

    #[test]
    fn gtk_shadow_margin_is_cropped() {
        // The phone round: a 1080x2164 window in a 1120x2204 buffer, 20 px margin each side.
        assert_eq!(content_rect(1120, 2204, Some((20, 20, 1080, 2164))), (20, 20, 1080, 2164));
    }

    #[test]
    fn geometry_is_clipped_to_the_buffer() {
        assert_eq!(content_rect(100, 100, Some((-10, 90, 50, 50))), (0, 90, 40, 10));
    }

    #[test]
    fn empty_or_outside_geometry_falls_back_to_the_buffer() {
        assert_eq!(content_rect(100, 100, Some((0, 0, 0, 10))), (0, 0, 100, 100));
        assert_eq!(content_rect(100, 100, Some((200, 200, 10, 10))), (0, 0, 100, 100));
    }

    #[test]
    fn view_point_maps_into_the_geometry() {
        let content = f((20, 20, 1080, 2164));
        assert_eq!(view_to_surface(0.0, 0.0, Some((1080, 2164)), content), (20.0, 20.0));
        assert_eq!(view_to_surface(540.0, 1082.0, Some((1080, 2164)), content), (560.0, 1102.0));
        // A view half the content's size doubles distances.
        assert_eq!(view_to_surface(10.0, 10.0, Some((540, 1082)), content), (40.0, 40.0));
    }

    #[test]
    fn unknown_view_size_maps_one_to_one() {
        assert_eq!(view_to_surface(5.0, 6.0, None, f((20, 20, 100, 100))), (25.0, 26.0));
    }

    #[test]
    fn span_starts_at_the_content_origin() {
        // stride 4480 = 1120 px.
        let (start, bytes) = content_span(0, 4480, (20, 20, 1080, 2164), 4480 * 2204).unwrap();
        assert_eq!(start, 20 * 4480 + 20 * 4);
        assert_eq!(bytes, 4480 * 2163 + 1080 * 4);
    }

    #[test]
    fn span_past_the_pool_is_refused() {
        assert_eq!(content_span(0, 400, (0, 0, 100, 100), 400 * 99), None);
        assert_eq!(content_span(0, 400, (50, 0, 100, 1), 400 * 10), None);
    }

    #[test]
    fn scale_one_maps_buffer_and_surface_one_to_one() {
        let m = surface_map(1120, 2204, 1, None, None);
        let c = m.logical_content(Some((20, 20, 1080, 2164)));
        assert_eq!(c, f((20, 20, 1080, 2164)));
        assert_eq!(m.to_buffer(c, 1120, 2204), (20, 20, 1080, 2164));
    }

    #[test]
    fn integer_buffer_scale_multiplies_the_geometry() {
        // GTK at scale 3: a 360x720 window with a 10 px shadow, buffer 380x740 logical.
        let m = surface_map(1140, 2220, 3, None, None);
        assert_eq!((m.width, m.height), (380.0, 740.0));
        let c = m.logical_content(Some((10, 10, 360, 720)));
        assert_eq!(m.to_buffer(c, 1140, 2220), (30, 30, 1080, 2160));
        assert_eq!(m.to_logical((30, 30, 1080, 2160)), c);
    }

    #[test]
    fn density_is_buffer_pixels_per_logical_unit() {
        assert_eq!(surface_map(1140, 2220, 3, None, None).density(), (3.0, 3.0));
        assert_eq!(surface_map(1050, 2100, 1, None, Some((400, 800))).density(), (2.625, 2.625));
        assert_eq!(surface_map(0, 0, 1, None, None).density(), (1.0, 1.0));
    }

    #[test]
    fn viewport_destination_is_the_logical_size() {
        // Fractional scale 2.625: logical 411x800, buffer 1079x2100, viewport dst 411x800.
        let m = surface_map(1079, 2100, 1, None, Some((411, 800)));
        assert_eq!((m.width, m.height), (411.0, 800.0));
        assert_eq!(m.to_buffer(m.logical_content(None), 1079, 2100), (0, 0, 1079, 2100));
    }

    #[test]
    fn viewport_source_crops_the_buffer() {
        let m = surface_map(200, 200, 2, Some((10.0, 10.0, 50.0, 50.0)), Some((100, 100)));
        assert_eq!(m.to_buffer((0.0, 0.0, 100.0, 100.0), 200, 200), (20, 20, 100, 100));
    }

    #[test]
    fn a_bad_viewport_falls_back_to_the_buffer() {
        let m = surface_map(100, 50, 1, Some((0.0, 0.0, -1.0, 5.0)), Some((0, 10)));
        assert_eq!((m.width, m.height), (100.0, 50.0));
    }

    #[test]
    fn scales_are_sanitised_to_120ths() {
        assert_eq!(sane_scale(f32::NAN), 1.0);
        assert_eq!(sane_scale(0.0), 1.0);
        assert_eq!(sane_scale(100.0), MAX_SCALE);
        assert_eq!(sane_scale(2.625), 2.625);
        assert_eq!(integer_scale(2.625), 3);
        assert_eq!(integer_scale(1.0), 1);
        assert_eq!(logical_size((1080, 2100), 2.625), (411, 800));
        assert_eq!(logical_size((1, 1), 3.0), (1, 1));
    }

    #[test]
    fn a_frame_within_the_margin_is_posted_whole() {
        let limit = post_limit(Some((1080, 2164)), (1080, 2400));
        assert_eq!(limit, (1080 + 135 + POST_MARGIN_PX, 2164 + 270 + POST_MARGIN_PX));
        assert_eq!(clamp_to_limit((20, 20, 1080, 2164), limit), (20, 20, 1080, 2164));
    }

    #[test]
    fn a_huge_frame_is_cut_to_the_limit() {
        // A client posting 8192x8192 into a 1080x1000 view.
        let limit = post_limit(Some((1080, 1000)), (1080, 2400));
        let r = clamp_to_limit((0, 0, 8192, 8192), limit);
        assert!(r.2 <= limit.0 && r.3 <= limit.1);
        assert!(limit.0 < 1400 && limit.1 < 1300);
    }

    #[test]
    fn an_integer_scale_client_at_a_fractional_scale_is_posted_whole() {
        // GTK 3 at output scale 2.5 on a 1080x2200 view: logical 432x880, buffer scale 3,
        // a 1296x2640 window.
        let view = Some((1080, 2200));
        let (lw, lh) = logical_size(view.unwrap(), 2.5);
        let buffer = (lw * integer_scale(2.5), lh * integer_scale(2.5));
        assert_eq!(buffer, (1296, 2640));
        // The plain view's limit would cut its right and bottom edges.
        let plain = post_limit(view, (1080, 2412));
        assert!(plain.0 < buffer.0 && plain.1 < buffer.1);
        let limit = post_limit(integer_scale_view(view, 2.5, 3), (1080, 2412));
        assert_eq!(clamp_to_limit((0, 0, buffer.0, buffer.1), limit), (0, 0, 1296, 2640));
    }

    #[test]
    fn whole_scales_and_bad_scales_keep_the_view() {
        assert_eq!(integer_scale_view(Some((1080, 2200)), 1.0, 1), Some((1080, 2200)));
        assert_eq!(integer_scale_view(Some((1080, 2200)), 3.0, 3), Some((1080, 2200)));
        assert_eq!(integer_scale_view(Some((1080, 2200)), f64::NAN, 1), Some((1080, 2200)));
        assert_eq!(integer_scale_view(None, 2.5, 3), None);
        // Below one the client still draws at one: never a smaller limit.
        assert_eq!(integer_scale_view(Some((100, 100)), 0.5, 1), Some((200, 200)));
        assert_eq!(integer_scale_view(Some((100, 100)), 1.25, 2), Some((160, 160)));
    }

    #[test]
    fn a_window_below_the_outputs_scale_leaves_room_for_the_outputs_integer() {
        // An app at 1.25 on an output at 2.5: a client that reads only wl_output draws at 3, a
        // buffer 2.4 times the view, which is shown whole rather than cut.
        assert_eq!(integer_scale_view(Some((100, 100)), 1.25, 3), Some((240, 240)));
        // An output below the window's own integer changes nothing.
        assert_eq!(integer_scale_view(Some((100, 100)), 2.5, 1), Some((120, 120)));
    }

    #[test]
    fn the_limit_never_passes_the_texture_cap() {
        assert_eq!(post_limit(Some((10_000, 10_000)), (1, 1)), (MAX_POST_SIDE, MAX_POST_SIDE));
        // No view yet: the output decides.
        assert_eq!(post_limit(None, (1080, 2400)).0, 1080 + 135 + POST_MARGIN_PX);
    }

    #[test]
    fn a_frame_wider_than_its_view_is_posted_whole_once_caught_up() {
        // Pong: "About Xfce" draws 1552x1294 in a 1038x1328 slot on a 1080x2400 output.
        let view = Some((1038, 1328));
        let stale = frame_limit(view, (1080, 2400), false);
        assert_eq!(stale, post_limit(view, (1080, 2400)));
        assert_eq!(clamp_to_limit((0, 0, 1552, 1294), stale), (0, 0, 1231, 1294));
        let limit = frame_limit(view, (1080, 2400), true);
        assert_eq!(clamp_to_limit((0, 0, 1552, 1294), limit), (0, 0, 1552, 1294));
        // Still bounded: a square of the output's longer side plus the margin, under the cap.
        assert_eq!(limit, (2400 + 300 + POST_MARGIN_PX, 2400 + 300 + POST_MARGIN_PX));
        assert_eq!(frame_limit(Some((4000, 4000)), (3000, 3000), true), (MAX_POST_SIDE, MAX_POST_SIDE));
    }

    #[test]
    fn the_report_is_in_view_pixels() {
        // Toolkit scale 2: 1552x1294 buffer px is 776x647 logical, 1552x1294 view px.
        assert_eq!(reported_size((0.0, 0.0, 776.0, 647.0), 2.0, Some((1038, 1328))), (1552, 1294));
        // A client on integer scale 3 at 2.625 draws its buffer 8/7 the view; the report is the view.
        assert_eq!(reported_size((0.0, 0.0, 395.0, 503.0), 2.625, Some((1038, 1320))), (1037, 1320));
        // X windows: pixels both sides.
        assert_eq!(reported_size((0.0, 0.0, 800.0, 600.0), 1.0, Some((1038, 1328))), (800, 600));
        assert_eq!(reported_size((0.0, 0.0, 800.0, 600.0), 1.0, None), (800, 600));
        // Nothing shown yet reports nothing.
        assert_eq!(reported_size((0.0, 0.0, 0.0, 0.0), 2.0, Some((1038, 1328))), (0, 0));
    }

    #[test]
    fn rounding_of_a_fitting_window_never_reads_as_overflow() {
        // 1036 / 2.625 rounds to 395 logical, which is 1036.9 px: still reported as 1036.
        assert_eq!(logical_size((1036, 1300), 2.625), (395, 495));
        assert_eq!(reported_size((0.0, 0.0, 395.0, 495.0), 2.625, Some((1036, 1300))), (1036, 1299));
        // One logical unit over the view is overflow.
        assert_eq!(reported_size((0.0, 0.0, 396.0, 495.0), 2.625, Some((1036, 1300))).0, 1040);
    }
}
