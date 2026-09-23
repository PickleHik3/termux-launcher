//! Apps: the featured cover, category chips, the list with multi-select, paging and search.

use std::collections::BTreeSet;

use crate::layout::{self, Regions, Tier};
use crate::picture::Fit;
use crate::render::{text_width, Rect, Sizing, Style};
use crate::term::{Event, Key, MouseKind};

use super::data::{Item, CATEGORIES};
use super::paint::{stand_in, Chrome, Hint, Paint};
use super::scene::El;
use super::{installing::Installing, item::ItemView, updates::Updates, Go, Store, Verb, View};

const A_COVER: u32 = 100;
const A_PREV: u32 = 101;
const A_NEXT: u32 = 102;
const A_BAND: u32 = 103;
const A_CHIP: u32 = 110;
const A_DOT: u32 = 120;
const A_ROW: u32 = 200;
const A_MARK: u32 = 300;

/// Where the parts of the Apps body go at a grid size.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct AppsLayout {
    pub cover: Option<Rect>,
    pub header_y: Option<u16>,
    pub chips_y: u16,
    pub rows_y: u16,
    pub per_page: usize,
    pub pager_y: u16,
    pub bar_y: u16,
}

pub fn apps_layout(r: &Regions, cols: u16) -> AppsLayout {
    let b = r.body;
    let mut y = b.y;
    let cover = (r.cover_rows > 0).then(|| {
        let c = Rect::new(0, y, cols, r.cover_rows);
        y += r.cover_rows + 1;
        c
    });
    let header_y = (r.tier != Tier::Compact).then(|| {
        let h = y;
        y += 1;
        h
    });
    let chips_y = y;
    let rows_y = if r.tier == Tier::Compact { y + 1 } else { y + 2 };
    let bar_y = b.bottom().saturating_sub(1);
    let pager_y = bar_y.saturating_sub(1);
    let per_page = (pager_y.saturating_sub(rows_y) / 2).max(1) as usize;
    AppsLayout { cover, header_y, chips_y, rows_y, per_page, pager_y, bar_y }
}

pub struct Apps {
    /// Index into the filtered list.
    pub cursor: usize,
    /// 0 = All, then CATEGORIES.
    pub cat: usize,
    pub query: String,
    pub typing: bool,
    pub sel: BTreeSet<String>,
    per_page: usize,
}

impl Default for Apps {
    fn default() -> Self {
        Apps::new()
    }
}

impl Apps {
    pub fn new() -> Apps {
        Apps { cursor: 0, cat: 0, query: String::new(), typing: false, sel: BTreeSet::new(), per_page: 6 }
    }

    pub fn filtered<'s>(&self, st: &'s Store) -> Vec<&'s Item> {
        let q = self.query.to_lowercase();
        st.cat
            .items
            .iter()
            .filter(|i| self.cat == 0 || i.category == CATEGORIES[self.cat - 1])
            .filter(|i| q.is_empty() || i.name.to_lowercase().contains(&q))
            .collect()
    }

    fn current(&self, st: &Store) -> Option<String> {
        self.filtered(st).get(self.cursor).map(|i| i.name.clone())
    }

    fn clamp(&mut self, st: &Store) {
        let n = self.filtered(st).len();
        self.cursor = self.cursor.min(n.saturating_sub(1));
    }

    fn page(&self) -> usize {
        self.cursor / self.per_page.max(1)
    }

    /// The selection, or the current row when nothing is selected.
    fn targets(&self, st: &Store) -> Vec<String> {
        if self.sel.is_empty() {
            self.current(st).into_iter().collect()
        } else {
            // Catalog order, not alphabetical.
            st.cat.items.iter().filter(|i| self.sel.contains(&i.name)).map(|i| i.name.clone()).collect()
        }
    }

    fn act(&mut self, st: &mut Store, verb: Verb) -> Go {
        let names: Vec<String> = self
            .targets(st)
            .into_iter()
            .filter(|n| {
                let installed = st.cat.item(n).is_some_and(|i| i.installed.is_some());
                if verb == Verb::Install {
                    !installed
                } else {
                    installed
                }
            })
            .collect();
        if names.is_empty() {
            st.notice =
                Some(if verb == Verb::Install { "Already installed." } else { "Not installed." }.into());
            return Go::Stay;
        }
        if st.start_job(verb, names) {
            self.sel.clear();
            Go::Push(Box::new(Installing::new()))
        } else {
            Go::Stay
        }
    }

    fn open(&self, st: &Store) -> Go {
        match self.current(st) {
            Some(n) => Go::Push(Box::new(ItemView::new(&n))),
            None => Go::Stay,
        }
    }

    fn cat_label(i: usize, narrow: bool) -> &'static str {
        match i {
            0 => "All",
            1 if narrow => "Notes",
            _ => CATEGORIES[i - 1],
        }
    }

    fn set_cat(&mut self, c: usize, st: &Store) {
        self.cat = c % (CATEGORIES.len() + 1);
        self.cursor = 0;
        self.clamp(st);
    }
}

impl View for Apps {
    fn name(&self) -> &'static str {
        "apps"
    }

    fn chrome(&mut self, st: &mut Store) -> Chrome {
        let mut keys = vec![Hint::new("␣", "select", Key::Char(' ')), Hint::new("⏎", "open", Key::Enter)];
        keys.push(Hint::new("i", "install", Key::Char('i')));
        keys.push(Hint::new("r", "remove", Key::Char('r')));
        keys.push(Hint::new("/", "search", Key::Char('/')));
        keys.push(Hint::new("esc", "leave", Key::Esc));
        Chrome {
            crumb: "/ apps".into(),
            context: st.updates_link(),
            lead: "terminal".into(),
            word: "goodies".into(),
            keys,
        }
    }

    fn body(&mut self, p: &mut Paint, st: &mut Store, r: &Regions) {
        let pal = *p.f.pal();
        let cols = p.f.cols();
        let lay = apps_layout(r, cols);
        self.per_page = lay.per_page;
        self.clamp(st);
        let b = r.body;
        let (cw, ch) = (p.f.ctx.size.cell_w, p.f.ctx.size.cell_h);

        // Cover of the featured item.
        if let (Some(c), Some(feat)) = (lay.cover, st.cat.featured().cloned()) {
            let path =
                if p.f.ctx.caps.kitty_graphics { st.cat.picture(&st.env, &feat.name, false) } else { None };
            let pic = path.and_then(|path| {
                p.f.ctx.pics.file(&path, c.w as u32 * cw as u32, c.h as u32 * ch as u32, Fit::Cover).ok()
            });
            let placed = pic.is_some_and(|pic| p.picture(El::Cover, &pic, c.x, c.y, (0, 0), 1));
            let caption_st = if placed { pal.ink_s() } else { Style::new().fg(pal.ink).bg(pal.tonal) };
            if !placed {
                stand_in(p, El::Cover, c, "");
            }
            let status = st.cat.status(&feat).label();
            let no = format!("No. {:02}", feat.no);
            let x = b.x + 1;
            if c.h >= 6 {
                let cap = if status.is_empty() { no } else { format!("{no} · {status}") };
                p.text(El::Caption, x, c.bottom() - 3, &cap, caption_st);
                let name = layout::spaced_caps(&feat.name);
                let big = Sizing::scale(2);
                let fits = p.f.ctx.caps.text_sizing && x + big.cells(&name).0 <= b.right();
                if fits {
                    p.sized(El::Caption, x, c.bottom() - 2, &name, big, caption_st.bold());
                } else {
                    p.text(El::Caption, x, c.bottom() - 2, &name, caption_st.bold());
                }
            } else {
                let mut cap = format!("{no} · {}", feat.name);
                if !status.is_empty() {
                    cap = format!("{cap} · {status}");
                }
                p.text(El::Caption, x, c.bottom() - 1, &cap, caption_st);
            }
            p.hit(c, A_COVER);
        }

        if let Some(hy) = lay.header_y {
            p.text(El::Header, b.x, hy, &layout::spaced_caps("apps"), pal.ink_s().bold());
        }

        // Chips, or the search line.
        let y = lay.chips_y;
        if self.typing || !self.query.is_empty() {
            let mut x = p.text(El::Chips, b.x, y, "/ ", pal.accent_s());
            x = p.text(El::Chips, x, y, &self.query, pal.ink_s());
            if self.typing {
                p.fill(El::Chips, Rect::new(x, y, 1, 1), Style::new().bg(pal.accent));
            }
            p.right(El::Chips, b.right(), y, "esc clears", pal.dim_s());
        } else {
            let mut x = b.x;
            for i in 0..=CATEGORIES.len() {
                let label = format!(" {} ", Apps::cat_label(i, r.narrow));
                let st_ = if i == self.cat { pal.tonal_s().fg(pal.accent).bold() } else { pal.dim_s() };
                let end = p.text_clip(El::Chips, x, y, &label, st_, b.right());
                p.hit(Rect::new(x, y, end - x, 1), A_CHIP + i as u32);
                x = end + 1;
            }
        }

        // Rows.
        let list: Vec<Item> = self.filtered(st).into_iter().cloned().collect();
        let per = lay.per_page;
        let pages = list.len().div_ceil(per).max(1);
        let page = self.page().min(pages - 1);
        if list.is_empty() {
            let msg = if st.cat.error.is_some() {
                st.cat.error.clone().unwrap_or_default()
            } else if self.query.is_empty() {
                "Nothing here yet.".to_string()
            } else {
                "Nothing matches.".to_string()
            };
            p.text_clip(El::Row(0), b.x + 2, lay.rows_y, &msg, pal.dim_s().italic(), b.right());
        }
        for (k, it) in list.iter().enumerate().skip(page * per).take(per) {
            let k_on = (k - page * per) as u16;
            let y = lay.rows_y + k_on * 2;
            let el = El::Row(k_on);
            let cur = k == self.cursor;
            let on = self.sel.contains(&it.name);
            // The number first: it names the row in the scene (motion re-staggers the rows
            // when the numbers on screen change, not when a mark does).
            let mut x = p.text(el, b.x + 2, y, &format!("{:02}", it.no), pal.dim_s());
            match (cur, on) {
                (true, false) => p.fill(el, Rect::new(b.x, y, 1, 1), Style::new().bg(pal.accent)),
                (true, true) => {
                    p.text(el, b.x, y, "✓", Style::new().fg(pal.on_accent).bg(pal.accent).bold());
                }
                (false, true) => {
                    p.text(el, b.x, y, "✓", pal.accent_s().bold());
                }
                _ => {}
            }
            let name_st = if cur || on { pal.accent_s().bold() } else { pal.ink_s() };
            x = p.text_clip(el, x + 2, y, &it.name, name_st, b.right());
            if !r.narrow && !it.category.is_empty() {
                let tag = it.category.to_uppercase();
                let fr = Sizing { valign: 2, ..Sizing::frac(1, 3, 4) };
                if x + 1 + text_width(&tag) as u16 + 4 < b.right() {
                    x = p.sized(el, x + 1, y, &tag, fr, pal.dim_s()).right();
                }
            }
            let status = st.cat.status(it);
            let label = status.label();
            let sst = if status.hot() { pal.accent_s() } else { pal.dim_s() };
            let sx = if label.is_empty() { b.right() } else { p.right(el, b.right(), y, &label, sst) };
            p.leaders(el, x, sx, y, pal.rule_s());
            p.hit(Rect::new(b.x, y, b.w, 1), A_ROW + k_on as u32);
            p.hit(Rect::new(b.x, y, 2, 1), A_MARK + k_on as u32);
        }

        // Pager.
        if pages > 1 {
            let dots: Vec<&str> = (0..pages).map(|i| if i == page { "●" } else { "○" }).collect();
            let s = format!("‹ {} ›", dots.join(" "));
            let x = p.centred(El::Pager, b, lay.pager_y, &s, pal.dim_s());
            p.hit(Rect::new(x, lay.pager_y, 1, 1), A_PREV);
            for i in 0..pages {
                p.hit(Rect::new(x + 2 + i as u16 * 2, lay.pager_y, 1, 1), A_DOT + i as u32);
            }
            p.hit(Rect::new(x + text_width(&s) as u16 - 1, lay.pager_y, 1, 1), A_NEXT);
        }

        // Selection bar, or (keyboard open) the fullscreen band.
        let bar = Rect::new(b.x, lay.bar_y, b.w, 1);
        if !self.sel.is_empty() {
            p.fill(El::SelBar, bar, Style::new().bg(pal.tonal));
            let head = format!("{} selected", self.sel.len());
            let x = p.text(
                El::SelBar,
                b.x + 2,
                lay.bar_y,
                &head,
                Style::new().fg(pal.accent).bg(pal.tonal).bold(),
            );
            let long = "   i install · r remove · ␣ clear";
            let tail =
                if x + text_width(long) as u16 <= b.right() { long } else { "  i install · r remove" };
            p.text_clip(El::SelBar, x, lay.bar_y, tail, Style::new().fg(pal.dim).bg(pal.tonal), b.right());
        } else if r.tier == Tier::Compact && st.env.launcherctl.is_some() {
            p.fill(El::SelBar, bar, Style::new().bg(pal.tonal));
            let on = Style::new().fg(pal.accent).bg(pal.tonal);
            let (word, why) = if st.fullscreen {
                ("keyboard", " · bring the keyboard back")
            } else {
                ("fullscreen", " · hide the keyboard for more room")
            };
            let mut x = p.text(El::SelBar, b.x + 2, lay.bar_y, "f", on.bold());
            x = p.text(El::SelBar, x + 1, lay.bar_y, word, on);
            p.text_clip(El::SelBar, x, lay.bar_y, why, Style::new().fg(pal.dim).bg(pal.tonal), b.right());
            p.hit(bar, A_BAND);
        }
    }

    fn handle(&mut self, ev: &Event, st: &mut Store) -> Go {
        let n = self.filtered(st).len();
        let per = self.per_page.max(1);
        if self.typing {
            if let Event::Key(k) = ev {
                match k {
                    Key::Char(c) => {
                        self.query.push(*c);
                        self.cursor = 0;
                        return Go::Stay;
                    }
                    Key::Backspace => {
                        if self.query.pop().is_none() {
                            self.typing = false;
                        }
                        self.cursor = 0;
                        return Go::Stay;
                    }
                    Key::Enter => {
                        self.typing = false;
                        return Go::Stay;
                    }
                    Key::Esc => {
                        self.typing = false;
                        self.query.clear();
                        self.clamp(st);
                        return Go::Stay;
                    }
                    _ => {}
                }
            }
        }
        match ev {
            Event::Key(Key::Up | Key::Char('k')) => self.cursor = self.cursor.saturating_sub(1),
            Event::Key(Key::Down | Key::Char('j')) => {
                self.cursor = (self.cursor + 1).min(n.saturating_sub(1))
            }
            Event::Key(Key::PageDown | Key::Right) => {
                self.cursor = ((self.page() + 1) * per).min(n.saturating_sub(1));
            }
            Event::Key(Key::PageUp | Key::Left) => self.cursor = self.page().saturating_sub(1) * per,
            Event::Key(Key::Home) => self.cursor = 0,
            Event::Key(Key::End) => self.cursor = n.saturating_sub(1),
            Event::Key(Key::Tab) => self.set_cat(self.cat + 1, st),
            Event::Key(Key::BackTab) => self.set_cat(self.cat + CATEGORIES.len(), st),
            Event::Key(Key::Enter) => return self.open(st),
            Event::Key(Key::Char(' ')) => {
                if let Some(nm) = self.current(st) {
                    if !self.sel.remove(&nm) {
                        self.sel.insert(nm);
                    }
                }
            }
            Event::Key(Key::Char('i')) => return self.act(st, Verb::Install),
            Event::Key(Key::Char('r')) => return self.act(st, Verb::Remove),
            Event::Key(Key::Char('u')) if !st.cat.updates.is_empty() => {
                return Go::Push(Box::new(Updates::new()))
            }
            Event::Key(Key::Char('/')) => {
                self.typing = true;
                self.cursor = 0;
            }
            Event::Key(Key::Esc) if !self.query.is_empty() => {
                self.query.clear();
                self.clamp(st);
            }
            Event::Key(Key::Esc) if !self.sel.is_empty() => self.sel.clear(),
            Event::Mouse(m) if m.kind == MouseKind::ScrollDown => {
                self.cursor = (self.cursor + 1).min(n.saturating_sub(1))
            }
            Event::Mouse(m) if m.kind == MouseKind::ScrollUp => self.cursor = self.cursor.saturating_sub(1),
            Event::Tap { action, .. } => {
                let a = *action;
                let base = self.page() * per;
                match a {
                    A_COVER => {
                        if let Some(f) = st.cat.featured() {
                            return Go::Push(Box::new(ItemView::new(&f.name.clone())));
                        }
                    }
                    A_PREV => self.cursor = self.page().saturating_sub(1) * per,
                    A_NEXT => self.cursor = ((self.page() + 1) * per).min(n.saturating_sub(1)),
                    A_BAND => st.toggle_fullscreen(),
                    _ if (A_CHIP..A_CHIP + 4).contains(&a) => self.set_cat((a - A_CHIP) as usize, st),
                    _ if (A_DOT..A_DOT + 50).contains(&a) => {
                        self.cursor = ((a - A_DOT) as usize * per).min(n.saturating_sub(1))
                    }
                    _ if (A_MARK..A_MARK + 100).contains(&a) => {
                        self.cursor = (base + (a - A_MARK) as usize).min(n.saturating_sub(1));
                        return self.handle(&Event::Key(Key::Char(' ')), st);
                    }
                    _ if (A_ROW..A_ROW + 100).contains(&a) => {
                        self.cursor = (base + (a - A_ROW) as usize).min(n.saturating_sub(1));
                        return self.open(st);
                    }
                    _ => {}
                }
            }
            Event::Key(_) => return Go::Pass,
            _ => {}
        }
        Go::Stay
    }

    fn refresh(&mut self, st: &Store) {
        self.sel.retain(|n| st.cat.item(n).is_some());
        self.clamp(st);
    }
}
