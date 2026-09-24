//! The shared header every screen has (Revision 6): masthead, picture, name, standfirst,
//! facts strip, body, notice row and key row, as one pure function of the grid size.

use crate::render::{text_width, Rect};

/// The grid the store is designed for: an in-app terminal with the launcher's keyboard up.
pub const BASE_COLS: u16 = 53;
pub const BASE_ROWS: u16 = 26;

/// The header picture never takes more than this many rows, and is not drawn under
/// [`PICTURE_MIN_ROWS`] (its rows go to the body instead).
pub const PICTURE_MAX_ROWS: u16 = 12;
pub const PICTURE_MIN_ROWS: u16 = 4;

/// Layout tier from the live grid height.
#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash)]
pub enum Tier {
    /// 40 rows or more: Front rows are two lines (name, standfirst) with a blank between.
    Tall,
    /// 26–39 rows (the baseline): single-line rows.
    Base,
    /// Under 26 rows: no picture, the name two rows tall.
    Compact,
}

pub fn tier(_cols: u16, rows: u16) -> Tier {
    match rows {
        40.. => Tier::Tall,
        BASE_ROWS..=39 => Tier::Base,
        _ => Tier::Compact,
    }
}

/// Under 44 columns category tags drop and the gutter narrows to one column.
pub fn narrow(cols: u16) -> bool {
    cols < 44
}

pub fn gutter(cols: u16) -> u16 {
    if narrow(cols) {
        1
    } else {
        2
    }
}

/// Rows one Front item takes in a tier (Tall: name, standfirst, blank).
pub fn item_rows(t: Tier) -> u16 {
    if t == Tier::Tall {
        3
    } else {
        1
    }
}

/// Where each part of the header goes. Row numbers are screen rows; `body` is what is left
/// between the header and the notice row.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct Header {
    pub tier: Tier,
    pub narrow: bool,
    pub gutter: u16,
    pub cols: u16,
    pub rows: u16,
    /// The content column: inside the gutters, every row.
    pub content: Rect,
    /// Row 0.
    pub masthead: u16,
    /// The picture rows (content width), when one is drawn.
    pub picture: Option<Rect>,
    /// The name rows: three (two in Compact).
    pub name: Rect,
    pub standfirst: u16,
    pub facts: u16,
    pub body: Rect,
    /// `rows - 2`: a notice or the selection bar.
    pub notice: u16,
    /// `rows - 1`.
    pub keys: u16,
}

/// Rows the header takes besides the picture and its blank: masthead, blank, name, standfirst,
/// facts, blank, notice, keys.
fn fixed_rows(t: Tier) -> u16 {
    if t == Tier::Compact {
        9
    } else {
        10
    }
}

/// The header for a `cols`×`rows` grid. `body_need` is what the screen's body wants (Front:
/// its rows; Item and Installing: a first look at the body); `pic_rows` is `None` when the
/// screen has no picture to show and otherwise the picture's own height in rows
/// (`u16::MAX` when unknown). The picture takes what is spare after the fixed rows and the
/// body, at most [`PICTURE_MAX_ROWS`] and its own height; under [`PICTURE_MIN_ROWS`] it is not
/// drawn. Never panics on tiny grids (rects may be empty).
pub fn header(cols: u16, rows: u16, body_need: u16, pic_rows: Option<u16>) -> Header {
    let t = tier(cols, rows);
    let nar = narrow(cols);
    let g = gutter(cols);
    let inner_w = cols.saturating_sub(g * 2);
    let line = |y: u16| y.min(rows.saturating_sub(1));
    let name_rows: u16 = if t == Tier::Compact { 2 } else { 3 };

    let spare = rows.saturating_sub(fixed_rows(t) + 1 + body_need);
    let pic_h = match pic_rows {
        Some(own) if t != Tier::Compact => spare.min(PICTURE_MAX_ROWS).min(own),
        _ => 0,
    };
    let pic_h = if pic_h >= PICTURE_MIN_ROWS { pic_h } else { 0 };

    let mut y = 2;
    let picture = (pic_h > 0).then(|| {
        let r = Rect::new(g, y, inner_w, pic_h);
        y += pic_h + 1;
        r
    });
    let name = Rect::new(g, line(y), inner_w, name_rows);
    y += name_rows;
    let standfirst = line(y);
    let facts = line(y + 1);
    let body_y = y + 3;
    let keys = rows.saturating_sub(1);
    let notice = rows.saturating_sub(2);
    let body_h = notice.saturating_sub(body_y);
    let body = Rect::new(g, line(body_y), inner_w, body_h);
    Header {
        tier: t,
        narrow: nar,
        gutter: g,
        cols,
        rows,
        content: Rect::new(g, 0, inner_w, rows),
        masthead: 0,
        picture,
        name,
        standfirst,
        facts,
        body,
        notice,
        keys,
    }
}

/// The five key-row slots' columns. At 53 columns and up: 2, 12, 24, 34, 45 (`f keyboard`
/// fills slot 4 with a gap to spare and `q quit` / `esc back` fit the last 8); under 44
/// columns: 1, 8, 16, 24, 32; between, evenly inside the gutters.
pub fn key_slots(cols: u16) -> [u16; 5] {
    if cols >= BASE_COLS {
        [2, 12, 24, 34, 45]
    } else if narrow(cols) {
        [1, 8, 16, 24, 32]
    } else {
        let step = cols.saturating_sub(4) / 5;
        [2, 2 + step, 2 + 2 * step, 2 + 3 * step, 2 + 4 * step]
    }
}

/// Columns each key slot may use: up to the next slot, the last one up to the edge.
pub fn key_rooms(cols: u16) -> [u16; 5] {
    let s = key_slots(cols);
    [s[1] - s[0], s[2] - s[1], s[3] - s[2], s[4] - s[3], cols.saturating_sub(s[4])]
}

/// `s` word-wrapped to lines of at most `width` columns, at most `max_lines` of them; when the
/// text does not fit, the last line ends in `…` after its last whole word. Words are never cut,
/// except a single word wider than `width`, which is clipped with `…`.
pub fn wrap(s: &str, width: u16, max_lines: usize) -> Vec<String> {
    let w = width as usize;
    if w == 0 || max_lines == 0 {
        return Vec::new();
    }
    let mut lines: Vec<String> = Vec::new();
    let mut cur = String::new();
    let words: Vec<&str> = s.split_whitespace().collect();
    let mut i = 0;
    while i < words.len() {
        let word = words[i];
        let cw = text_width(&cur);
        let ww = text_width(word);
        if cur.is_empty() && ww > w {
            cur = clip(word, w);
            i += 1;
            lines.push(std::mem::take(&mut cur));
        } else if cur.is_empty() || cw + 1 + ww <= w {
            if !cur.is_empty() {
                cur.push(' ');
            }
            cur.push_str(word);
            i += 1;
            continue;
        } else {
            lines.push(std::mem::take(&mut cur));
        }
        if lines.len() == max_lines {
            break;
        }
    }
    if !cur.is_empty() && lines.len() < max_lines {
        lines.push(cur);
    } else if i < words.len() || !cur.is_empty() {
        // Text left over: the last line gives up words until the ellipsis fits.
        if let Some(last) = lines.last_mut() {
            *last = with_ellipsis(last, w);
        }
    }
    lines
}

/// `s` on one line of at most `width` columns: whole words, then `…` when some are left out.
pub fn fit_line(s: &str, width: u16) -> String {
    wrap(s, width, 1).into_iter().next().unwrap_or_default()
}

fn clip(word: &str, w: usize) -> String {
    let mut out = String::new();
    for c in word.chars() {
        if text_width(&out) + text_width(c.encode_utf8(&mut [0; 4])) + 1 > w {
            break;
        }
        out.push(c);
    }
    out.push('…');
    out
}

fn with_ellipsis(line: &str, w: usize) -> String {
    let mut words: Vec<&str> = line.split(' ').collect();
    while words.len() > 1 && text_width(&words.join(" ")) + 1 > w {
        words.pop();
    }
    let base = words.join(" ");
    if text_width(&base) + 1 > w {
        return clip(&base, w);
    }
    let base = base.trim_end_matches([',', ';', ':', '·', '-']).to_string();
    format!("{base}…")
}

/// Start column that centres `w` columns inside `within`.
pub fn centre_x(within: Rect, w: u16) -> u16 {
    within.x + within.w.saturating_sub(w) / 2
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn tiers_follow_rows() {
        assert_eq!(tier(53, 45), Tier::Tall);
        assert_eq!(tier(53, 40), Tier::Tall);
        assert_eq!(tier(53, 39), Tier::Base);
        assert_eq!(tier(53, 26), Tier::Base);
        assert_eq!(tier(53, 25), Tier::Compact);
        assert!(narrow(43) && !narrow(44));
        assert_eq!(item_rows(Tier::Tall), 3);
        assert_eq!(item_rows(Tier::Base), 1);
    }

    #[test]
    fn baseline_front_has_an_eight_row_picture_over_seven_rows() {
        let h = header(53, 26, 7, Some(u16::MAX));
        assert_eq!(h.tier, Tier::Base);
        assert_eq!(h.gutter, 2);
        assert_eq!(h.content, Rect::new(2, 0, 49, 26));
        assert_eq!(h.masthead, 0);
        assert_eq!(h.picture, Some(Rect::new(2, 2, 49, 8)));
        assert_eq!(h.name, Rect::new(2, 11, 49, 3));
        assert_eq!(h.standfirst, 14);
        assert_eq!(h.facts, 15);
        assert_eq!(h.body, Rect::new(2, 17, 49, 7));
        assert_eq!(h.notice, 24);
        assert_eq!(h.keys, 25);
    }

    #[test]
    fn tall_front_has_a_nine_row_picture_over_seven_two_line_items() {
        let h = header(53, 40, 7 * 3 - 1, Some(u16::MAX));
        assert_eq!(h.tier, Tier::Tall);
        assert_eq!(h.picture, Some(Rect::new(2, 2, 49, 9)));
        assert_eq!(h.name, Rect::new(2, 12, 49, 3));
        assert_eq!(h.standfirst, 15);
        assert_eq!(h.facts, 16);
        assert_eq!(h.body, Rect::new(2, 18, 49, 20));
        assert_eq!(h.notice, 38);
        assert_eq!(h.keys, 39);
    }

    #[test]
    fn picture_is_clamped_to_its_own_height_and_the_cap() {
        let h = header(53, 40, 7, Some(u16::MAX));
        assert_eq!(h.picture.map(|p| p.h), Some(PICTURE_MAX_ROWS));
        let h = header(53, 40, 7, Some(6));
        assert_eq!(h.picture.map(|p| p.h), Some(6));
        assert_eq!(h.body.y, 2 + 6 + 1 + 3 + 3);
        // Spare rows the picture cannot use stay blank before the notice row.
        assert!(h.body.h > 7);
        // Under four spare rows there is no picture and the body starts higher.
        let h = header(53, 26, 12, Some(u16::MAX));
        assert_eq!(h.picture, None);
        assert_eq!(h.name.y, 2);
        assert_eq!(h.body, Rect::new(2, 8, 49, 16));
        // No picture at all gives the same rows.
        assert_eq!(header(53, 26, 7, None).body, Rect::new(2, 8, 49, 16));
    }

    #[test]
    fn compact_has_no_picture_and_a_two_row_name() {
        let h = header(40, 24, 7, Some(u16::MAX));
        assert_eq!(h.tier, Tier::Compact);
        assert!(h.narrow && h.gutter == 1);
        assert_eq!(h.picture, None);
        assert_eq!(h.name, Rect::new(1, 2, 38, 2));
        assert_eq!(h.standfirst, 4);
        assert_eq!(h.facts, 5);
        assert_eq!(h.body, Rect::new(1, 7, 38, 15));
        assert_eq!(h.notice, 22);
        assert_eq!(h.keys, 23);
    }

    #[test]
    fn forty_four_columns_is_not_narrow() {
        let h = header(44, 30, 7, Some(u16::MAX));
        assert_eq!(h.tier, Tier::Base);
        assert!(!h.narrow && h.gutter == 2);
        assert_eq!(h.content.w, 40);
        assert_eq!(h.picture, Some(Rect::new(2, 2, 40, 12)));
        assert_eq!(h.name.y, 15);
        assert_eq!(h.body, Rect::new(2, 21, 40, 7));
        assert_eq!(h.keys, 29);
    }

    #[test]
    fn key_slots_are_fixed() {
        assert_eq!(key_slots(53), [2, 12, 24, 34, 45]);
        assert_eq!(key_slots(60), [2, 12, 24, 34, 45]);
        assert_eq!(key_slots(40), [1, 8, 16, 24, 32]);
        assert_eq!(key_rooms(53), [10, 12, 10, 11, 8]);
        // `f keyboard` (10 columns) needs slot 4 to leave a gap before slot 5.
        assert!(key_rooms(53)[3] > "f keyboard".len() as u16);
        assert_eq!(key_rooms(40), [7, 8, 8, 8, 8]);
        let mid = key_slots(48);
        assert!(mid.windows(2).all(|w| w[1] > w[0]) && mid[4] + key_rooms(48)[4] <= 48);
    }

    #[test]
    fn tiny_grids_do_not_panic() {
        for (c, r) in [(0, 0), (1, 1), (5, 3), (10, 8), (20, 5)] {
            let h = header(c, r, 7, Some(u16::MAX));
            assert!(h.body.bottom() <= r.max(1));
        }
    }

    #[test]
    fn wrapping_keeps_words_whole() {
        assert_eq!(wrap("a quiet place to write", 12, 3), vec!["a quiet", "place to", "write"]);
        assert_eq!(wrap("a quiet place to write", 12, 2), vec!["a quiet", "place to…"]);
        assert_eq!(wrap("short", 20, 2), vec!["short"]);
        assert_eq!(wrap("", 20, 2), Vec::<String>::new());
        assert_eq!(fit_line("best in a kitty-compatible terminal", 20), "best in a…");
        assert_eq!(fit_line("supercalifragilistic", 8), "superca…");
        for l in wrap("one two three four five six seven eight nine", 10, 3) {
            assert!(text_width(&l) <= 10, "{l}");
        }
        assert_eq!(centre_x(Rect::new(2, 0, 10, 1), 4), 5);
    }
}
