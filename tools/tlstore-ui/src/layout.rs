//! The shared screen structure at each grid size: masthead, hairline rule, hero, body, key row.

use crate::render::{text_width, Rect};

/// The grid the store is designed for: an in-app terminal with the launcher's keyboard up.
/// Every screen is laid out for this first; more room adds to it (a cover, more rows).
pub const BASE_COLS: u16 = 53;
pub const BASE_ROWS: u16 = 26;

/// Layout tier from the live grid height.
#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash)]
pub enum Tier {
    /// 40 rows or more: a gap under the rule and a three-row hero word; screens add a cover
    /// when they have eight rows to spare.
    Tall,
    /// 26–39 rows (the baseline): the hero word is two rows tall.
    Base,
    /// Under 26 rows: no margins, the hero on one row.
    Compact,
}

pub fn tier(_cols: u16, rows: u16) -> Tier {
    match rows {
        40.. => Tier::Tall,
        BASE_ROWS..=39 => Tier::Base,
        _ => Tier::Compact,
    }
}

/// Under 44 columns category tags drop and the gutter narrows.
pub fn narrow(cols: u16) -> bool {
    cols < 44
}

/// Where each part of the shared structure goes. Everything sits inside the one side gutter.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct Regions {
    pub tier: Tier,
    pub narrow: bool,
    /// Side margin in columns.
    pub gutter: u16,
    /// Masthead line: mark at the left, breadcrumb after it, one context item right-aligned.
    pub masthead: Rect,
    /// The hairline rule row.
    pub rule: Rect,
    /// The whole hero block.
    pub hero: Rect,
    /// The spaced small-caps lead line (Compact: the one hero row; the word follows the lead).
    pub hero_lead: Rect,
    /// The rows the hero word fills (Compact: same row as the lead).
    pub hero_word: Rect,
    /// Content between the hero and the key row. The row under it is the notice line.
    pub body: Rect,
    /// The key-hint row.
    pub keys: Rect,
}

/// Regions for a `cols`×`rows` grid. Degrades without panicking on tiny grids (rects may be
/// empty).
pub fn regions(cols: u16, rows: u16) -> Regions {
    let t = tier(cols, rows);
    let nar = narrow(cols);
    let gutter = if nar { 1 } else { 2 };
    let inner_w = cols.saturating_sub(gutter * 2);
    let line = |y: u16, h: u16| Rect::new(gutter, y.min(rows), inner_w, h.min(rows.saturating_sub(y)));

    // (top margin, blank rows between rule and hero, word rows, blank after hero, bottom margin)
    let (top, gap, word_rows, after, bottom) = match t {
        Tier::Tall => (1, 1, 3, 1, 1),
        Tier::Base => (1, u16::from(rows >= 30), 2, 1, 1),
        Tier::Compact => (0, 0, 0, 1, 0),
    };
    let masthead = line(top, 1);
    let rule = line(top + 1, 1);
    let hero_y = top + 2 + gap;
    let (hero, hero_lead, hero_word) = if t == Tier::Compact {
        let r = line(hero_y, 1);
        (r, r, r)
    } else {
        (line(hero_y, 1 + word_rows), line(hero_y, 1), line(hero_y + 1, word_rows))
    };
    let keys_y = rows.saturating_sub(1 + bottom);
    let keys = line(keys_y, 1);
    let body_y = hero.bottom() + after;
    let body_h = keys_y.saturating_sub(body_y + 1);
    let body = line(body_y, body_h);
    Regions { tier: t, narrow: nar, gutter, masthead, rule, hero, hero_lead, hero_word, body, keys }
}

/// Rows a cover picture may take when a screen has `spare` rows left over: none under eight
/// (a cover is never squeezed), otherwise seven, growing by one for every two more spare
/// rows, at most `max` — and always one row left for the blank under it.
pub fn cover_rows(spare: u16, max: u16) -> u16 {
    if spare < 8 {
        return 0;
    }
    (7 + spare.saturating_sub(12) / 2).min(max).min(spare - 1)
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

/// Letter-spaces a lead line: `"terminal"` → `"T E R M I N A L"`. Words are separated by
/// three spaces.
pub fn spaced_caps(s: &str) -> String {
    let mut out = String::new();
    for (wi, word) in s.split_whitespace().enumerate() {
        if wi > 0 {
            out.push_str("   ");
        }
        for (ci, c) in word.chars().enumerate() {
            if ci > 0 {
                out.push(' ');
            }
            out.extend(c.to_uppercase());
        }
    }
    out
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
    }

    #[test]
    fn baseline_regions() {
        let r = regions(53, 26);
        assert_eq!(r.tier, Tier::Base);
        assert_eq!(r.masthead, Rect::new(2, 1, 49, 1));
        assert_eq!(r.rule, Rect::new(2, 2, 49, 1));
        assert_eq!(r.hero_lead, Rect::new(2, 3, 49, 1));
        assert_eq!(r.hero_word, Rect::new(2, 4, 49, 2));
        assert_eq!(r.body, Rect::new(2, 7, 49, 16));
        assert_eq!(r.keys, Rect::new(2, 24, 49, 1));
    }

    #[test]
    fn tall_regions() {
        let r = regions(53, 40);
        assert_eq!(r.tier, Tier::Tall);
        assert_eq!(r.hero_lead.y, 4);
        assert_eq!(r.hero_word, Rect::new(2, 5, 49, 3));
        assert_eq!(r.body, Rect::new(2, 9, 49, 28));
        assert_eq!(r.keys.y, 38);
    }

    #[test]
    fn short_grid_is_compact() {
        let r = regions(52, 23);
        assert_eq!(r.tier, Tier::Compact);
        assert_eq!(r.masthead.y, 0);
        assert_eq!(r.hero, Rect::new(2, 2, 48, 1));
        assert_eq!(r.keys.y, 22);
        assert_eq!(r.body, Rect::new(2, 4, 48, 17));
    }

    #[test]
    fn covers_need_eight_spare_rows() {
        assert_eq!(cover_rows(7, 12), 0);
        assert_eq!(cover_rows(8, 12), 7);
        assert_eq!(cover_rows(12, 12), 7);
        assert_eq!(cover_rows(20, 12), 11);
        assert_eq!(cover_rows(40, 12), 12);
        assert_eq!(cover_rows(8, 5), 5);
    }

    #[test]
    fn narrow_and_tiny_grids_do_not_panic() {
        let r = regions(40, 26);
        assert!(r.narrow && r.gutter == 1 && r.tier == Tier::Base);
        for (c, rr) in [(0, 0), (1, 1), (5, 3), (10, 8)] {
            let _ = regions(c, rr);
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
    }

    #[test]
    fn helpers() {
        assert_eq!(centre_x(Rect::new(2, 0, 10, 1), 4), 5);
        assert_eq!(spaced_caps("terminal"), "T E R M I N A L");
        assert_eq!(spaced_caps("no. 03"), "N O .   0 3");
    }
}
