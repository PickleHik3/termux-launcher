//! wl_shm pixels to an Android RGBA_8888 window buffer.
//!
//! Wayland's ARGB8888 is a little-endian u32 `0xAARRGGBB`, so its bytes in memory are
//! B, G, R, A; it is premultiplied. Android's RGBA_8888 is R, G, B, A in memory and TextureView
//! composes it as premultiplied, so the copy only swaps B and R. XRGB8888 has an undefined X
//! byte, which becomes 255.

/// Converts one row of `width` pixels. `src` and `dst` must hold at least `width * 4` bytes.
#[inline]
pub fn convert_row(src: &[u8], dst: &mut [u8], width: usize, opaque: bool) {
    let n = width * 4;
    let alpha = if opaque { 0xFF00_0000u32 } else { 0 };
    for (s, d) in src[..n].chunks_exact(4).zip(dst[..n].chunks_exact_mut(4)) {
        let v = u32::from_le_bytes([s[0], s[1], s[2], s[3]]);
        let out = (v & 0xFF00_FF00) | ((v >> 16) & 0xFF) | ((v & 0xFF) << 16) | alpha;
        d.copy_from_slice(&out.to_le_bytes());
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
    opaque: bool,
) {
    for y in 0..height {
        let s = &src[y * src_stride..];
        let d = &mut dst[y * dst_stride..];
        convert_row(s, d, width, opaque);
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn argb(a: u8, r: u8, g: u8, b: u8) -> [u8; 4] {
        ((a as u32) << 24 | (r as u32) << 16 | (g as u32) << 8 | b as u32).to_le_bytes()
    }

    #[test]
    fn argb_swaps_red_and_blue_and_keeps_alpha() {
        let src = argb(0x80, 0x40, 0x20, 0x10);
        let mut dst = [0u8; 4];
        convert_row(&src, &mut dst, 1, false);
        assert_eq!(dst, [0x40, 0x20, 0x10, 0x80]);
    }

    #[test]
    fn transparent_pixel_stays_transparent() {
        let src = argb(0, 0, 0, 0);
        let mut dst = [9u8; 4];
        convert_row(&src, &mut dst, 1, false);
        assert_eq!(dst, [0, 0, 0, 0]);
    }

    #[test]
    fn xrgb_forces_alpha_to_255() {
        let src = argb(0x00, 0x11, 0x22, 0x33);
        let mut dst = [0u8; 4];
        convert_row(&src, &mut dst, 1, true);
        assert_eq!(dst, [0x11, 0x22, 0x33, 0xFF]);
    }

    #[test]
    fn strides_are_honoured_and_padding_untouched() {
        // 2x2 image, source stride 12 (one pixel of padding), destination stride 16.
        let mut src = vec![0u8; 24];
        for (i, px) in [(0, 1u8), (1, 2), (3, 3), (4, 4)] {
            src[i * 4..i * 4 + 4].copy_from_slice(&argb(255, px, 0, 0));
        }
        let mut dst = vec![0xEEu8; 32];
        convert(&src, 12, &mut dst, 16, 2, 2, false);
        assert_eq!(&dst[0..4], &[1, 0, 0, 255]);
        assert_eq!(&dst[4..8], &[2, 0, 0, 255]);
        assert_eq!(&dst[8..16], &[0xEE; 8]);
        assert_eq!(&dst[16..20], &[3, 0, 0, 255]);
        assert_eq!(&dst[20..24], &[4, 0, 0, 255]);
    }
}
