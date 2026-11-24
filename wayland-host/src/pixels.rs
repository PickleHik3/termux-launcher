//! Client pixels (wl_shm or linear dmabuf) to an Android RGBA_8888 window buffer.
//!
//! All four accepted formats are 32-bit little-endian words, premultiplied:
//! - ARGB8888 / XRGB8888 (`0xAARRGGBB`): bytes in memory B, G, R, A, so B and R are swapped.
//! - ABGR8888 / XBGR8888 (`0xAABBGGRR`): bytes R, G, B, A, Android's order already.
//!
//! Android's RGBA_8888 is R, G, B, A in memory and TextureView composes it as premultiplied. The
//! X formats have an undefined X byte, which becomes 255.

/// How one format's words become RGBA bytes.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct PixelFormat {
    /// B and R trade places (ARGB/XRGB).
    pub swap_rb: bool,
    /// The fourth byte is not alpha; it becomes 255.
    pub opaque: bool,
}

pub const ARGB: PixelFormat = PixelFormat { swap_rb: true, opaque: false };
pub const XRGB: PixelFormat = PixelFormat { swap_rb: true, opaque: true };
pub const ABGR: PixelFormat = PixelFormat { swap_rb: false, opaque: false };
pub const XBGR: PixelFormat = PixelFormat { swap_rb: false, opaque: true };

/// DRM fourcc codes (`fourcc_code(a, b, c, d)` = little-endian bytes a, b, c, d).
pub const DRM_FORMAT_ARGB8888: u32 = u32::from_le_bytes(*b"AR24");
pub const DRM_FORMAT_XRGB8888: u32 = u32::from_le_bytes(*b"XR24");
pub const DRM_FORMAT_ABGR8888: u32 = u32::from_le_bytes(*b"AB24");
pub const DRM_FORMAT_XBGR8888: u32 = u32::from_le_bytes(*b"XB24");

/// The formats the host takes as dmabufs, in the order advertised.
pub const DMABUF_FORMATS: [u32; 4] = [DRM_FORMAT_ARGB8888, DRM_FORMAT_XRGB8888, DRM_FORMAT_ABGR8888, DRM_FORMAT_XBGR8888];

/// The conversion for a DRM fourcc, or None for a format the host does not take.
pub fn drm_format(fourcc: u32) -> Option<PixelFormat> {
    match fourcc {
        DRM_FORMAT_ARGB8888 => Some(ARGB),
        DRM_FORMAT_XRGB8888 => Some(XRGB),
        DRM_FORMAT_ABGR8888 => Some(ABGR),
        DRM_FORMAT_XBGR8888 => Some(XBGR),
        _ => None,
    }
}

/// Converts one row of `width` pixels. `src` and `dst` must hold at least `width * 4` bytes.
#[inline]
pub fn convert_row(src: &[u8], dst: &mut [u8], width: usize, format: PixelFormat) {
    let n = width * 4;
    let alpha = if format.opaque { 0xFF00_0000u32 } else { 0 };
    if format.swap_rb {
        for (s, d) in src[..n].chunks_exact(4).zip(dst[..n].chunks_exact_mut(4)) {
            let v = u32::from_le_bytes([s[0], s[1], s[2], s[3]]);
            let out = (v & 0xFF00_FF00) | ((v >> 16) & 0xFF) | ((v & 0xFF) << 16) | alpha;
            d.copy_from_slice(&out.to_le_bytes());
        }
    } else if format.opaque {
        for (s, d) in src[..n].chunks_exact(4).zip(dst[..n].chunks_exact_mut(4)) {
            let v = u32::from_le_bytes([s[0], s[1], s[2], s[3]]) | alpha;
            d.copy_from_slice(&v.to_le_bytes());
        }
    } else {
        dst[..n].copy_from_slice(&src[..n]);
    }
}

/// One client word as an RGBA_8888 word (R in the low byte, as Android lays it out in memory).
#[inline]
pub fn to_rgba(v: u32, format: PixelFormat) -> u32 {
    let v = if format.swap_rb { (v & 0xFF00_FF00) | ((v >> 16) & 0xFF) | ((v & 0xFF) << 16) } else { v };
    if format.opaque {
        v | 0xFF00_0000
    } else {
        v
    }
}

/// Premultiplied source-over of one RGBA word onto another: `src + dst * (1 - src.alpha)`, per
/// channel, saturating (a client may send alpha below its colour).
#[inline]
pub fn over(src: u32, dst: u32) -> u32 {
    let a = src >> 24;
    if a == 255 || dst == 0 {
        return src;
    }
    if src == 0 {
        return dst;
    }
    let inv = 255 - a;
    let mut out = 0u32;
    for shift in [0, 8, 16, 24] {
        let s = (src >> shift) & 0xFF;
        let d = (dst >> shift) & 0xFF;
        // d * inv / 255, rounded.
        let t = d * inv + 128;
        let scaled = (t + (t >> 8)) >> 8;
        out |= (s + scaled).min(255) << shift;
    }
    out
}

/// Blends one row of `width` client pixels over an RGBA row; an opaque format is a plain
/// conversion. `src` and `dst` must hold at least `width * 4` bytes.
#[inline]
pub fn blend_row(src: &[u8], dst: &mut [u8], width: usize, format: PixelFormat) {
    if format.opaque {
        convert_row(src, dst, width, format);
        return;
    }
    let n = width * 4;
    for (s, d) in src[..n].chunks_exact(4).zip(dst[..n].chunks_exact_mut(4)) {
        let sv = to_rgba(u32::from_le_bytes([s[0], s[1], s[2], s[3]]), format);
        let dv = u32::from_le_bytes([d[0], d[1], d[2], d[3]]);
        d.copy_from_slice(&over(sv, dv).to_le_bytes());
    }
}

/// Converts a `width` x `height` image. Strides are in bytes.
pub fn convert(
    src: &[u8],
    src_stride: usize,
    dst: &mut [u8],
    dst_stride: usize,
    width: usize,
    height: usize,
    format: PixelFormat,
) {
    for y in 0..height {
        let s = &src[y * src_stride..];
        let d = &mut dst[y * dst_stride..];
        convert_row(s, d, width, format);
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    /// One pixel as the client's 32-bit word, in memory.
    fn word(v: u32) -> [u8; 4] {
        v.to_le_bytes()
    }

    fn one(src: [u8; 4], format: PixelFormat) -> [u8; 4] {
        let mut dst = [9u8; 4];
        convert_row(&src, &mut dst, 1, format);
        dst
    }

    #[test]
    fn argb_swaps_red_and_blue_and_keeps_alpha() {
        assert_eq!(one(word(0x80_40_20_10), drm_format(DRM_FORMAT_ARGB8888).unwrap()), [0x40, 0x20, 0x10, 0x80]);
    }

    #[test]
    fn xrgb_swaps_and_forces_alpha_to_255() {
        assert_eq!(one(word(0x00_11_22_33), drm_format(DRM_FORMAT_XRGB8888).unwrap()), [0x11, 0x22, 0x33, 0xFF]);
    }

    #[test]
    fn abgr_is_android_order_and_keeps_alpha() {
        // 0xAABBGGRR: A 0x80, B 0x10, G 0x20, R 0x40.
        assert_eq!(one(word(0x80_10_20_40), drm_format(DRM_FORMAT_ABGR8888).unwrap()), [0x40, 0x20, 0x10, 0x80]);
    }

    #[test]
    fn xbgr_keeps_order_and_forces_alpha_to_255() {
        assert_eq!(one(word(0x00_33_22_11), drm_format(DRM_FORMAT_XBGR8888).unwrap()), [0x11, 0x22, 0x33, 0xFF]);
    }

    #[test]
    fn other_formats_are_refused() {
        assert_eq!(drm_format(u32::from_le_bytes(*b"NV12")), None);
        assert_eq!(drm_format(0), None);
    }

    #[test]
    fn fourcc_codes_match_drm() {
        assert_eq!(DRM_FORMAT_ARGB8888, 0x3432_5241);
        assert_eq!(DRM_FORMAT_XRGB8888, 0x3432_5258);
        assert_eq!(DRM_FORMAT_ABGR8888, 0x3432_4241);
        assert_eq!(DRM_FORMAT_XBGR8888, 0x3432_4258);
    }

    #[test]
    fn transparent_pixel_stays_transparent() {
        assert_eq!(one(word(0), ARGB), [0, 0, 0, 0]);
        assert_eq!(one(word(0), ABGR), [0, 0, 0, 0]);
    }

    #[test]
    fn to_rgba_matches_the_row_conversion() {
        for format in [ARGB, XRGB, ABGR, XBGR] {
            let v = 0x80_40_20_10u32;
            let mut d = [0u8; 4];
            convert_row(&v.to_le_bytes(), &mut d, 1, format);
            assert_eq!(to_rgba(v, format).to_le_bytes(), d);
        }
    }

    #[test]
    fn over_is_premultiplied_source_over() {
        // Opaque source replaces; transparent source keeps the destination.
        assert_eq!(over(0xFF_10_20_30, 0xFF_FF_FF_FF), 0xFF_10_20_30);
        assert_eq!(over(0, 0xFF_01_02_03), 0xFF_01_02_03);
        // Half-transparent black over opaque white: white * (1 - 0.5).
        assert_eq!(over(0x80_00_00_00, 0xFF_FF_FF_FF), 0xFF_7F_7F_7F);
        // Half red (premultiplied 0x80) over half blue.
        let out = over(0x80_00_00_80, 0x80_80_00_00).to_le_bytes();
        assert_eq!(out, [0x80, 0x00, 0x40, 0xC0]);
        // Over an empty destination it is the source.
        assert_eq!(over(0x40_10_10_10, 0), 0x40_10_10_10);
    }

    #[test]
    fn over_saturates_a_bad_premultiplied_source() {
        assert_eq!(over(0x10_FF_FF_FF, 0xFF_FF_FF_FF), 0xFF_FF_FF_FF);
    }

    #[test]
    fn blend_row_blends_alpha_formats_and_copies_opaque_ones() {
        let white = [0xFFu8; 8];
        // ARGB 0x80000000 (half black) and a fully transparent pixel.
        let mut src = Vec::new();
        src.extend_from_slice(&0x80_00_00_00u32.to_le_bytes());
        src.extend_from_slice(&0u32.to_le_bytes());
        let mut dst = white;
        blend_row(&src, &mut dst, 2, ARGB);
        assert_eq!(dst, [0x7F, 0x7F, 0x7F, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF]);
        let mut dst = white;
        blend_row(&src, &mut dst, 2, XRGB);
        assert_eq!(dst, [0, 0, 0, 0xFF, 0, 0, 0, 0xFF]);
    }

    #[test]
    fn strides_are_honoured_and_padding_untouched() {
        // 2x2 image, source stride 12 (one pixel of padding), destination stride 16.
        for format in [ARGB, ABGR] {
            let mut src = vec![0u8; 24];
            for (i, px) in [(0, 1u32), (1, 2), (3, 3), (4, 4)] {
                // Red channel = px in either layout.
                let v = if format.swap_rb { 0xFF00_0000 | px << 16 } else { 0xFF00_0000 | px };
                src[i * 4..i * 4 + 4].copy_from_slice(&word(v));
            }
            let mut dst = vec![0xEEu8; 32];
            convert(&src, 12, &mut dst, 16, 2, 2, format);
            assert_eq!(&dst[0..4], &[1, 0, 0, 255]);
            assert_eq!(&dst[4..8], &[2, 0, 0, 255]);
            assert_eq!(&dst[8..16], &[0xEE; 8]);
            assert_eq!(&dst[16..20], &[3, 0, 0, 255]);
            assert_eq!(&dst[20..24], &[4, 0, 0, 255]);
        }
    }
}
