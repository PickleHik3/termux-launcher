//! Which part of a client's buffer is shown, and how view touches map back into it.
//!
//! A client that draws its own shadow (GTK) makes its buffer larger than its window and says
//! where the window is with xdg_surface.set_window_geometry. Only that rectangle is shown, and
//! input is offset by its origin. Buffer scale is 1, so surface and buffer pixels agree.

/// x, y, width, height.
pub type Rect = (i32, i32, i32, i32);

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

/// A point in view pixels to surface coordinates: the content rectangle is stretched over the
/// whole view.
pub fn view_to_surface(x: f32, y: f32, view: Option<(i32, i32)>, content: Rect) -> (f64, f64) {
    let (cx, cy, cw, ch) = content;
    match view {
        Some((vw, vh)) if vw > 0 && vh > 0 && cw > 0 && ch > 0 => (
            cx as f64 + x as f64 * cw as f64 / vw as f64,
            cy as f64 + y as f64 * ch as f64 / vh as f64,
        ),
        _ => (cx as f64 + x as f64, cy as f64 + y as f64),
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

#[cfg(test)]
mod tests {
    use super::*;

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
        let content = (20, 20, 1080, 2164);
        assert_eq!(view_to_surface(0.0, 0.0, Some((1080, 2164)), content), (20.0, 20.0));
        assert_eq!(view_to_surface(540.0, 1082.0, Some((1080, 2164)), content), (560.0, 1102.0));
        // A view half the content's size doubles distances.
        assert_eq!(view_to_surface(10.0, 10.0, Some((540, 1082)), content), (40.0, 40.0));
    }

    #[test]
    fn unknown_view_size_maps_one_to_one() {
        assert_eq!(view_to_surface(5.0, 6.0, None, (20, 20, 100, 100)), (25.0, 26.0));
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
}
