//! The preview renderer (`--features shot`): a PNG comes out, with ink where the hero word is.
#![cfg(feature = "shot")]

use std::path::PathBuf;

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
    // The script word "goodies" sits on rows 4–5 (53×26 kitty snapshot); light pixels there.
    let word = (4 * CELL_H as u32..6 * CELL_H as u32)
        .flat_map(|y| (0..img.width).map(move |x| (x, y)))
        .filter(|&(x, y)| img.pixel(x, y).luma() > 0.5)
        .count();
    assert!(word > 200, "only {word} light pixels where the hero word is");
    let _ = std::fs::remove_file(&out);
}
