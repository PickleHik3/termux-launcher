//! The preview renderer (`--features shot`): a PNG comes out, with ink where the name is.
#![cfg(feature = "shot")]

use std::path::PathBuf;

use tlstore_ui::layout;
use tlstore_ui::shot::{self, Spec, CELL_H, CELL_W};

#[test]
fn front_at_53x26_is_a_png_with_ink() {
    let out =
        PathBuf::from(env!("CARGO_TARGET_TMPDIR")).join(format!("shot-front-{}.png", std::process::id()));
    let spec = Spec::parse("front").unwrap();
    shot::shoot(53, 26, &spec, None, &out).unwrap();
    let bytes = std::fs::read(&out).unwrap();
    assert!(bytes.starts_with(b"\x89PNG\r\n\x1a\n"), "not a PNG");

    let img = shot::render(53, 26, &spec, None).unwrap();
    assert_eq!((img.width, img.height), (53 * CELL_W as u32, 26 * CELL_H as u32));
    let surface = tlstore_ui::palette::Palette::builtin_dark().surface;
    let inked = (0..img.height)
        .flat_map(|y| (0..img.width).map(move |x| (x, y)))
        .filter(|&(x, y)| img.pixel(x, y) != surface)
        .count();
    let total = (img.width * img.height) as usize;
    assert!(inked > total / 50 && inked < total * 3 / 4, "{inked} of {total} pixels are not the surface");
    // The script-face name sits on the header's name rows (Front at 53×26: an 8-row picture,
    // then three rows of name); light pixels there.
    let name = layout::header(53, 26, 7, Some(u16::MAX)).name;
    let word = (name.y as u32 * CELL_H as u32..name.bottom() as u32 * CELL_H as u32)
        .flat_map(|y| (0..img.width).map(move |x| (x, y)))
        .filter(|&(x, y)| img.pixel(x, y).luma() > 0.5)
        .count();
    assert!(word > 200, "only {word} light pixels where the name is");
    let _ = std::fs::remove_file(&out);
}
