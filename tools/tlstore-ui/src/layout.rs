//! The shared screen structure at each grid size: masthead, hairline rule, hero, body, key row.

use crate::render::Rect;

/// Layout tier from the live grid height.
#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash)]
pub enum Tier {
    /// 40 rows or more: full cover picture, three-row script word.
    Full,
    /// 28–39 rows: a picture strip, two-row script word.
    Strip,
    /// Under 28 rows (keyboard open): no cover, one-line hero, `f fullscreen` hint.
    Compact,
}

pub fn tier(_cols: u16, rows: u16) -> Tier {
    match rows {
        40.. => Tier::Full,
        28..=39 => Tier::Strip,
        _ => Tier::Compact,
    }
}

/// Under 44 columns category tags drop.
pub fn narrow(cols: u16) -> bool {
    cols < 44
}

/// Where each part of the shared structure goes. All rects are inside the side gutters
/// except `rule`, which is also inside them (the design's hairline stops at the gutter).
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
    /// The rows the script word picture fills (Compact: same row as the lead).
    pub hero_word: Rect,
    /// Content between the hero and the key row.
    pub body: Rect,
    /// Suggested cover/picture height at the top of `body` (0 in Compact).
    pub cover_rows: u16,
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
    let (top, gap, word_rows, after, bottom, cover) = match t {
        Tier::Full => (1, 1, 3, 1, 1, 12),
        Tier::Strip => (1, 1, 2, 1, 1, 5),
        Tier::Compact => (0, 0, 0, 1, 0, 0),
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
    let cover_rows = if t == Tier::Full { cover.min(body_h / 3) } else { cover.min(body_h / 2) };
    Regions {
        tier: t,
        narrow: nar,
        gutter,
        masthead,
        rule,
        hero,
        hero_lead,
        hero_word,
        body,
        cover_rows,
        keys,
    }
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
        assert_eq!(tier(52, 45), Tier::Full);
        assert_eq!(tier(52, 40), Tier::Full);
        assert_eq!(tier(52, 39), Tier::Strip);
        assert_eq!(tier(52, 28), Tier::Strip);
        assert_eq!(tier(52, 27), Tier::Compact);
        assert_eq!(tier(52, 23), Tier::Compact);
        assert!(narrow(43) && !narrow(44));
    }

    #[test]
    fn full_phone_regions() {
        let r = regions(52, 45);
        assert_eq!(r.masthead, Rect::new(2, 1, 48, 1));
        assert_eq!(r.rule, Rect::new(2, 2, 48, 1));
        assert_eq!(r.hero_lead, Rect::new(2, 4, 48, 1));
        assert_eq!(r.hero_word, Rect::new(2, 5, 48, 3));
        assert_eq!(r.body.y, 9);
        assert_eq!(r.keys, Rect::new(2, 43, 48, 1));
        assert_eq!(r.body.bottom(), 42);
        assert_eq!(r.cover_rows, 11);
    }

    #[test]
    fn keyboard_open_is_compact() {
        let r = regions(52, 23);
        assert_eq!(r.tier, Tier::Compact);
        assert_eq!(r.masthead.y, 0);
        assert_eq!(r.hero, Rect::new(2, 2, 48, 1));
        assert_eq!(r.keys.y, 22);
        assert_eq!(r.cover_rows, 0);
        assert_eq!(r.body, Rect::new(2, 4, 48, 17));
    }

    #[test]
    fn narrow_and_tiny_grids_do_not_panic() {
        let r = regions(40, 30);
        assert!(r.narrow && r.gutter == 1 && r.tier == Tier::Strip);
        for (c, rr) in [(0, 0), (1, 1), (5, 3), (10, 8)] {
            let _ = regions(c, rr);
        }
    }

    #[test]
    fn helpers() {
        assert_eq!(centre_x(Rect::new(2, 0, 10, 1), 4), 5);
        assert_eq!(spaced_caps("terminal"), "T E R M I N A L");
        assert_eq!(spaced_caps("no. 03"), "N O .   0 3");
    }
}
