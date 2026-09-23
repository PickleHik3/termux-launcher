//! The pixel TLSTORE mark: 3×5 block letters with one blank column between them.

use crate::render::Rgb;

const T: [u8; 5] = [0b111, 0b010, 0b010, 0b010, 0b010];
const L: [u8; 5] = [0b100, 0b100, 0b100, 0b100, 0b111];
const S: [u8; 5] = [0b111, 0b100, 0b111, 0b001, 0b111];
const O: [u8; 5] = [0b111, 0b101, 0b101, 0b101, 0b111];
const R: [u8; 5] = [0b110, 0b101, 0b110, 0b101, 0b101];
const E: [u8; 5] = [0b111, 0b100, 0b110, 0b100, 0b111];

const WORD: [[u8; 5]; 7] = [T, L, S, T, O, R, E];

/// Mark size in mark pixels: 7 letters × 3 + 6 gaps.
pub const MARK_W: u32 = 27;
pub const MARK_H: u32 = 5;

/// Whether mark pixel (x, y) is lit.
pub fn lit(x: u32, y: u32) -> bool {
    if x >= MARK_W || y >= MARK_H {
        return false;
    }
    let letter = (x / 4) as usize;
    let col = x % 4;
    if col == 3 {
        return false;
    }
    WORD[letter][y as usize] & (0b100 >> col) != 0
}

/// RGBA of the mark with each mark pixel drawn as a `px`×`px` block.
pub fn render(px: u32, color: Rgb) -> (u32, u32, Vec<u8>) {
    let px = px.max(1);
    let (w, h) = (MARK_W * px, MARK_H * px);
    let mut rgba = vec![0u8; (w * h * 4) as usize];
    for y in 0..h {
        for x in 0..w {
            if lit(x / px, y / px) {
                let i = ((y * w + x) * 4) as usize;
                rgba[i..i + 4].copy_from_slice(&[color.0, color.1, color.2, 255]);
            }
        }
    }
    (w, h, rgba)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn letters_match_the_bitmaps() {
        let row0: String = (0..MARK_W).map(|x| if lit(x, 0) { '#' } else { '.' }).collect();
        assert_eq!(row0, "###.#...###.###.###.##..###");
        let row4: String = (0..MARK_W).map(|x| if lit(x, 4) { '#' } else { '.' }).collect();
        assert_eq!(row4, ".#..###.###..#..###.#.#.###");
    }

    #[test]
    fn scales_by_integer_blocks() {
        let (w, h, rgba) = render(3, Rgb(1, 2, 3));
        assert_eq!((w, h), (81, 15));
        assert_eq!(&rgba[0..4], &[1, 2, 3, 255]);
        // Column 3 (the gap after T) is transparent.
        let i = (3 * 3 * 4) as usize;
        assert_eq!(rgba[i + 3], 0);
    }
}
