//! Byte-exact encoders for the escape sequences the renderer emits: OSC 66 text sizing and the
//! kitty graphics protocol (transmit, place, delete). Kept free of state so they can be tested
//! against golden strings.

use std::fmt::Write;

/// OSC 66 text-sizing parameters. See kitty's text-sizing protocol.
#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash)]
pub struct Sizing {
    /// Scale 1–7: the run is `scale` rows tall and each character `scale` columns wide.
    pub scale: u8,
    /// Width of the whole run in cells before scaling, 0 = let the terminal measure the text.
    pub width: u8,
    /// Fractional font scale `num/den` inside the block (0/0 = none). Both 0–15, num < den.
    pub num: u8,
    pub den: u8,
    /// Vertical alignment of a fractional run inside its block: 0 top, 1 bottom, 2 centre.
    pub valign: u8,
    /// Horizontal alignment: 0 left, 1 right, 2 centre.
    pub halign: u8,
}

impl Sizing {
    pub const fn scale(scale: u8) -> Sizing {
        Sizing { scale, width: 0, num: 0, den: 0, valign: 0, halign: 0 }
    }
    /// Fractional scale inside a `scale`-row block, e.g. `Sizing::frac(2, 3, 4)`.
    pub const fn frac(scale: u8, num: u8, den: u8) -> Sizing {
        Sizing { scale, width: 0, num, den, valign: 0, halign: 0 }
    }
    pub fn clamped(self) -> Sizing {
        let mut s = self;
        s.scale = s.scale.clamp(1, 7);
        s.width = s.width.min(7);
        s.num = s.num.min(15);
        s.den = s.den.min(15);
        if s.num >= s.den {
            s.num = 0;
            s.den = 0;
        }
        s.valign = s.valign.min(2);
        s.halign = s.halign.min(2);
        s
    }
    /// Cells covered by `text` written with this sizing: (columns, rows).
    pub fn cells(&self, text: &str) -> (u16, u16) {
        let s = self.clamped();
        let per = if s.width > 0 { s.width as u16 } else { crate::render::text_width(text) as u16 };
        (per * s.scale as u16, s.scale as u16)
    }
}

/// `ESC ] 66 ; <keys> ; <text> ESC \`. Keys with default values are left out.
pub fn osc66(out: &mut String, sizing: Sizing, text: &str) {
    let s = sizing.clamped();
    out.push_str("\x1b]66;");
    let mut first = true;
    let mut key = |out: &mut String, k: &str, v: u8| {
        if !first {
            out.push(':');
        }
        first = false;
        let _ = write!(out, "{k}={v}");
    };
    if s.scale != 1 {
        key(out, "s", s.scale);
    }
    if s.width != 0 {
        key(out, "w", s.width);
    }
    if s.den != 0 {
        key(out, "n", s.num);
        key(out, "d", s.den);
    }
    if s.valign != 0 {
        key(out, "v", s.valign);
    }
    if s.halign != 0 {
        key(out, "h", s.halign);
    }
    out.push(';');
    out.push_str(text);
    out.push_str("\x1b\\");
}

const B64: &[u8; 64] = b"ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";

/// Standard base64 with padding.
pub fn base64(data: &[u8]) -> String {
    let mut out = String::with_capacity(data.len().div_ceil(3) * 4);
    for chunk in data.chunks(3) {
        let b = [chunk[0], *chunk.get(1).unwrap_or(&0), *chunk.get(2).unwrap_or(&0)];
        let n = (b[0] as u32) << 16 | (b[1] as u32) << 8 | b[2] as u32;
        out.push(B64[(n >> 18) as usize & 63] as char);
        out.push(B64[(n >> 12) as usize & 63] as char);
        out.push(if chunk.len() > 1 { B64[(n >> 6) as usize & 63] as char } else { '=' });
        out.push(if chunk.len() > 2 { B64[n as usize & 63] as char } else { '=' });
    }
    out
}

/// Kitty graphics chunk size (base64 bytes per APC), the protocol's maximum.
pub const KITTY_CHUNK: usize = 4096;

/// Transmits straight-alpha RGBA pixels as image `id` (`a=t,f=32`), zlib-compressed when
/// `compress`, chunked. Quiet (`q=2`): the terminal sends no replies.
pub fn kitty_transmit(out: &mut String, id: u32, width: u32, height: u32, rgba: &[u8], compress: bool) {
    let head = format!("a=t,f=32,s={width},v={height},i={id},q=2");
    chunked(out, &head, rgba, compress, 6);
}

/// zlib level for animation frames: they come in a burst, so speed over size.
const FRAME_ZLIB_LEVEL: u8 = 1;

/// Adds one animation frame to image `id` (`a=f`): the whole canvas again (`s`×`v`, straight
/// RGBA), replacing the frame before it rather than blending (`X=1`), shown for `gap_ms`
/// (`z`, at least 1 so the terminal does not fall back to its default gap). Chunked like a
/// transmit; quiet.
pub fn kitty_frame(
    out: &mut String,
    id: u32,
    width: u32,
    height: u32,
    gap_ms: u32,
    rgba: &[u8],
    compress: bool,
) {
    let gap = gap_ms.max(1);
    let head = format!("a=f,i={id},f=32,s={width},v={height},X=1,z={gap},q=2");
    chunked(out, &head, rgba, compress, FRAME_ZLIB_LEVEL);
}

/// Starts image `id` looping (`a=a`): frame 1, the picture itself, shows for `first_gap_ms`
/// (`r=1,z=`), then the animation runs (`s=3`) round and round (`v=1`: for ever). Quiet.
pub fn kitty_animate(out: &mut String, id: u32, first_gap_ms: u32) {
    let gap = first_gap_ms.max(1);
    let _ = write!(out, "\x1b_Ga=a,i={id},r=1,z={gap},s=3,v=1,q=2\x1b\\");
}

/// Writes `head` plus the payload (`o=z` when `compress`, at zlib `level`) in
/// [`KITTY_CHUNK`]-sized chunks: the first carries the head, the rest only `m`.
fn chunked(out: &mut String, head: &str, data: &[u8], compress: bool, level: u8) {
    let payload = if compress {
        base64(&miniz_oxide::deflate::compress_to_vec_zlib(data, level))
    } else {
        base64(data)
    };
    let bytes = payload.as_bytes();
    let mut chunks = bytes.chunks(KITTY_CHUNK).peekable();
    let mut first = true;
    if chunks.peek().is_none() {
        let _ = write!(out, "\x1b_G{head};\x1b\\");
        return;
    }
    while let Some(chunk) = chunks.next() {
        let more = u8::from(chunks.peek().is_some());
        if first {
            let _ = write!(out, "\x1b_G{head}");
            if compress {
                out.push_str(",o=z");
            }
            let _ = write!(out, ",m={more};");
            first = false;
        } else {
            let _ = write!(out, "\x1b_Gm={more};");
        }
        // Chunks of a base64 string are ASCII, so this is valid UTF-8.
        out.push_str(std::str::from_utf8(chunk).unwrap_or(""));
        out.push_str("\x1b\\");
    }
}

/// A rectangle of source pixels: x, y, width, height.
#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash)]
pub struct Crop {
    pub x: u32,
    pub y: u32,
    pub w: u32,
    pub h: u32,
}

/// Places image `id` as placement `pid` at the cursor (the caller moves the cursor first).
/// `px_x`/`px_y` offset inside the first cell, `cols`/`rows` scale into that many cells
/// (0 = natural size), `crop` picks a source rectangle, `z` stacks (negative = under text).
/// `C=1` keeps the cursor where it is.
#[allow(clippy::too_many_arguments)]
pub fn kitty_place(
    out: &mut String,
    id: u32,
    pid: u32,
    px_x: u16,
    px_y: u16,
    cols: u16,
    rows: u16,
    crop: Option<Crop>,
    z: i32,
) {
    let _ = write!(out, "\x1b_Ga=p,i={id},p={pid}");
    if let Some(c) = crop {
        let _ = write!(out, ",x={},y={},w={},h={}", c.x, c.y, c.w, c.h);
    }
    if px_x != 0 {
        let _ = write!(out, ",X={px_x}");
    }
    if px_y != 0 {
        let _ = write!(out, ",Y={px_y}");
    }
    if cols != 0 {
        let _ = write!(out, ",c={cols}");
    }
    if rows != 0 {
        let _ = write!(out, ",r={rows}");
    }
    if z != 0 {
        let _ = write!(out, ",z={z}");
    }
    out.push_str(",C=1,q=2\x1b\\");
}

/// Deletes one placement, keeping the image data for later placements.
pub fn kitty_delete_placement(out: &mut String, id: u32, pid: u32) {
    let _ = write!(out, "\x1b_Ga=d,d=i,i={id},p={pid},q=2\x1b\\");
}

/// Deletes an image and frees its data.
pub fn kitty_delete_image(out: &mut String, id: u32) {
    let _ = write!(out, "\x1b_Ga=d,d=I,i={id},q=2\x1b\\");
}

/// Deletes every image this client placed, freeing the data.
pub const KITTY_DELETE_ALL: &str = "\x1b_Ga=d,d=A,q=2\x1b\\";

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn osc66_plain_scale() {
        let mut s = String::new();
        osc66(&mut s, Sizing::scale(3), "Hi");
        assert_eq!(s, "\x1b]66;s=3;Hi\x1b\\");
        assert_eq!(Sizing::scale(3).cells("Hi"), (6, 3));
    }

    #[test]
    fn osc66_fraction_and_align() {
        let mut s = String::new();
        let mut z = Sizing::frac(2, 1, 2);
        z.valign = 2;
        z.width = 1;
        osc66(&mut s, z, "a");
        assert_eq!(s, "\x1b]66;s=2:w=1:n=1:d=2:v=2;a\x1b\\");
        assert_eq!(z.cells("a"), (2, 2));
    }

    #[test]
    fn osc66_scale_one_has_no_keys() {
        let mut s = String::new();
        osc66(&mut s, Sizing::scale(1), "x");
        assert_eq!(s, "\x1b]66;;x\x1b\\");
    }

    #[test]
    fn osc66_clamps() {
        let z = Sizing { scale: 9, width: 9, num: 5, den: 3, valign: 7, halign: 7 }.clamped();
        assert_eq!(z, Sizing { scale: 7, width: 7, num: 0, den: 0, valign: 2, halign: 2 });
    }

    #[test]
    fn base64_vectors() {
        assert_eq!(base64(b""), "");
        assert_eq!(base64(b"f"), "Zg==");
        assert_eq!(base64(b"fo"), "Zm8=");
        assert_eq!(base64(b"foo"), "Zm9v");
        assert_eq!(base64(b"foobar"), "Zm9vYmFy");
    }

    #[test]
    fn transmit_small_uncompressed() {
        let mut s = String::new();
        kitty_transmit(&mut s, 7, 1, 1, &[255, 0, 0, 255], false);
        assert_eq!(s, "\x1b_Ga=t,f=32,s=1,v=1,i=7,q=2,m=0;/wAA/w==\x1b\\");
    }

    #[test]
    fn transmit_chunks_large() {
        let rgba = vec![0x5a; 64 * 64 * 4];
        let mut s = String::new();
        kitty_transmit(&mut s, 9, 64, 64, &rgba, false);
        let n = s.matches("\x1b_G").count();
        assert_eq!(n, base64(&rgba).len().div_ceil(KITTY_CHUNK));
        assert!(s.starts_with("\x1b_Ga=t,f=32,s=64,v=64,i=9,q=2,m=1;"));
        assert!(s.contains("\x1b_Gm=0;"));
        assert_eq!(s.matches("m=1;").count(), n - 1);
    }

    #[test]
    fn transmit_compressed_roundtrips() {
        let rgba: Vec<u8> = (0..4000u32).map(|i| (i % 7) as u8).collect();
        let mut s = String::new();
        kitty_transmit(&mut s, 3, 10, 100, &rgba, true);
        assert!(s.starts_with("\x1b_Ga=t,f=32,s=10,v=100,i=3,q=2,o=z,m=0;"));
    }

    #[test]
    fn frame_and_loop() {
        let mut s = String::new();
        kitty_frame(&mut s, 7, 1, 1, 83, &[255, 0, 0, 255], false);
        assert_eq!(s, "\x1b_Ga=f,i=7,f=32,s=1,v=1,X=1,z=83,q=2,m=0;/wAA/w==\x1b\\");
        s.clear();
        kitty_frame(&mut s, 7, 1, 1, 0, &[255, 0, 0, 255], true);
        assert!(s.starts_with("\x1b_Ga=f,i=7,f=32,s=1,v=1,X=1,z=1,q=2,o=z,m=0;"), "{s:?}");
        s.clear();
        let rgba = vec![0x5a; 64 * 64 * 4];
        kitty_frame(&mut s, 9, 64, 64, 40, &rgba, false);
        let n = s.matches("\x1b_G").count();
        assert_eq!(n, base64(&rgba).len().div_ceil(KITTY_CHUNK));
        assert!(s.starts_with("\x1b_Ga=f,i=9,f=32,s=64,v=64,X=1,z=40,q=2,m=1;"));
        assert_eq!(s.matches("\x1b_Gm=").count(), n - 1, "continuation chunks carry only m");
        assert!(s.ends_with("\x1b\\") && s.contains("\x1b_Gm=0;"));
        s.clear();
        kitty_animate(&mut s, 7, 90);
        assert_eq!(s, "\x1b_Ga=a,i=7,r=1,z=90,s=3,v=1,q=2\x1b\\");
    }

    #[test]
    fn place_full() {
        let mut s = String::new();
        kitty_place(&mut s, 5, 2, 3, 4, 10, 6, Some(Crop { x: 0, y: 8, w: 80, h: 40 }), -1);
        assert_eq!(s, "\x1b_Ga=p,i=5,p=2,x=0,y=8,w=80,h=40,X=3,Y=4,c=10,r=6,z=-1,C=1,q=2\x1b\\");
    }

    #[test]
    fn place_minimal_and_delete() {
        let mut s = String::new();
        kitty_place(&mut s, 5, 1, 0, 0, 0, 0, None, 0);
        kitty_delete_placement(&mut s, 5, 1);
        kitty_delete_image(&mut s, 5);
        assert_eq!(
            s,
            "\x1b_Ga=p,i=5,p=1,C=1,q=2\x1b\\\x1b_Ga=d,d=i,i=5,p=1,q=2\x1b\\\x1b_Ga=d,d=I,i=5,q=2\x1b\\"
        );
    }
}
