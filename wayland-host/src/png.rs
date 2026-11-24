//! A minimal PNG writer for the debug frame dump: 8-bit RGBA, no filtering, stored (uncompressed)
//! deflate blocks. Large files, no dependency, and any PNG reader opens them.

/// CRC-32 (IEEE), as PNG chunks use.
pub fn crc32(data: &[u8]) -> u32 {
    crc32_update(0xFFFF_FFFF, data) ^ 0xFFFF_FFFF
}

fn crc32_update(mut crc: u32, data: &[u8]) -> u32 {
    for &b in data {
        crc ^= b as u32;
        for _ in 0..8 {
            crc = if crc & 1 != 0 { (crc >> 1) ^ 0xEDB8_8320 } else { crc >> 1 };
        }
    }
    crc
}

/// Adler-32, the zlib stream's checksum.
pub fn adler32(data: &[u8]) -> u32 {
    let (mut a, mut b) = (1u32, 0u32);
    for chunk in data.chunks(5552) {
        for &x in chunk {
            a += x as u32;
            b += a;
        }
        a %= 65521;
        b %= 65521;
    }
    (b << 16) | a
}

fn chunk(out: &mut Vec<u8>, kind: &[u8; 4], data: &[u8]) {
    out.extend_from_slice(&(data.len() as u32).to_be_bytes());
    out.extend_from_slice(kind);
    out.extend_from_slice(data);
    let crc = crc32_update(crc32_update(0xFFFF_FFFF, kind), data) ^ 0xFFFF_FFFF;
    out.extend_from_slice(&crc.to_be_bytes());
}

/// Largest stored deflate block.
const STORED_MAX: usize = 65_535;

/// Encodes `width` x `height` RGBA pixels (`rgba` holds `width * 4` bytes per row, rows packed).
/// None when the sizes do not agree.
pub fn encode_rgba(width: u32, height: u32, rgba: &[u8]) -> Option<Vec<u8>> {
    let row = (width as usize).checked_mul(4)?;
    if width == 0 || height == 0 || rgba.len() != row.checked_mul(height as usize)? {
        return None;
    }
    // Scanlines with filter byte 0.
    let mut raw = Vec::with_capacity((row + 1) * height as usize);
    for line in rgba.chunks_exact(row) {
        raw.push(0);
        raw.extend_from_slice(line);
    }
    let mut z = Vec::with_capacity(raw.len() + raw.len() / STORED_MAX * 5 + 16);
    z.extend_from_slice(&[0x78, 0x01]);
    let blocks: Vec<&[u8]> = raw.chunks(STORED_MAX).collect();
    for (i, block) in blocks.iter().enumerate() {
        z.push(if i + 1 == blocks.len() { 1 } else { 0 });
        let len = block.len() as u16;
        z.extend_from_slice(&len.to_le_bytes());
        z.extend_from_slice(&(!len).to_le_bytes());
        z.extend_from_slice(block);
    }
    z.extend_from_slice(&adler32(&raw).to_be_bytes());

    let mut out = Vec::with_capacity(z.len() + 64);
    out.extend_from_slice(b"\x89PNG\r\n\x1a\n");
    let mut ihdr = Vec::with_capacity(13);
    ihdr.extend_from_slice(&width.to_be_bytes());
    ihdr.extend_from_slice(&height.to_be_bytes());
    // Bit depth 8, colour type 6 (RGBA), deflate, filter 0, no interlace.
    ihdr.extend_from_slice(&[8, 6, 0, 0, 0]);
    chunk(&mut out, b"IHDR", &ihdr);
    chunk(&mut out, b"IDAT", &z);
    chunk(&mut out, b"IEND", &[]);
    Some(out)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn checksums_match_their_reference_values() {
        assert_eq!(crc32(b"123456789"), 0xCBF4_3926);
        assert_eq!(adler32(b"Wikipedia"), 0x11E6_0398);
    }

    #[test]
    fn a_png_has_signature_header_and_end() {
        let png = encode_rgba(2, 1, &[1, 2, 3, 4, 5, 6, 7, 8]).unwrap();
        assert_eq!(&png[..8], b"\x89PNG\r\n\x1a\n");
        assert_eq!(&png[12..16], b"IHDR");
        assert_eq!(&png[16..24], &[0, 0, 0, 2, 0, 0, 0, 1]);
        // IEND with its well-known CRC closes the file.
        assert_eq!(&png[png.len() - 12..], &[0, 0, 0, 0, b'I', b'E', b'N', b'D', 0xAE, 0x42, 0x60, 0x82]);
    }

    #[test]
    fn pixels_are_stored_verbatim_after_a_filter_byte() {
        let pixels = [10u8, 20, 30, 40];
        let png = encode_rgba(1, 1, &pixels).unwrap();
        // IDAT data: zlib header, one final stored block of 5 bytes, then filter 0 + pixel.
        let idat = png.windows(4).position(|w| w == b"IDAT").unwrap() + 4;
        assert_eq!(&png[idat..idat + 2], &[0x78, 0x01]);
        assert_eq!(&png[idat + 2..idat + 7], &[1, 5, 0, 0xFA, 0xFF]);
        assert_eq!(&png[idat + 7..idat + 12], &[0, 10, 20, 30, 40]);
    }

    #[test]
    fn big_images_split_into_stored_blocks() {
        // 200 rows of 100 px = 80,200 raw bytes: two blocks.
        let png = encode_rgba(100, 200, &vec![7u8; 100 * 4 * 200]).unwrap();
        assert!(png.len() > 80_200);
    }

    #[test]
    fn mismatched_sizes_are_refused() {
        assert_eq!(encode_rgba(2, 2, &[0; 4]), None);
        assert_eq!(encode_rgba(0, 1, &[]), None);
    }
}
