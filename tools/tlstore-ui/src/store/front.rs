//! Front: the list, with the header showing the item under the cursor; multi-select, the
//! updates filter, paging, and the step words of a running job.

use std::collections::BTreeSet;

use crate::layout::{self, Tier, PICTURE_MIN_ROWS};
use crate::picture::shapes;
use crate::render::{text_width, Rect, Sizing, Style, Underline};
use crate::term::{Event, Key, MouseKind};

use super::data::{Info, Item};
use super::paint::{
    draw_header, HeaderContent, Masthead, Paint, Slot, Slots, A_ALL, A_CONTEXT, A_HEADER, A_HOME,
};
use super::scene::El;
use super::{header_for, installing::Installing, item::ItemView, Go, Store, Verb, View};

const A_PREV: u32 = 101;
const A_NEXT: u32 = 102;
const A_DOT: u32 = 120;
const A_ROW: u32 = 200;

/// The cursor pill's alpha over the surface.
const PILL_ALPHA: f32 = 0.18;

pub struct Front {
    /// Index into the shown list.
    pub cursor: usize,
    /// Only items with an update.
    pub filter: bool,
    pub sel: BTreeSet<String>,
    per_page: usize,
    /// Rows per page as last drawn, to turn taps into indexes.
    page_base: usize,
}

impl Default for Front {
    fn default() -> Self {
        Front::new()
    }
}

impl Front {
    pub fn new() -> Front {
        Front { cursor: 0, filter: false, sel: BTreeSet::new(), per_page: 6, page_base: 0 }
    }

    pub fn shown<'s>(&self, st: &'s Store) -> Vec<&'s Item> {
        st.cat.items.iter().filter(|i| !self.filter || st.cat.update_for(&i.name).is_some()).collect()
    }

    fn current(&self, st: &Store) -> Option<String> {
        self.shown(st).get(self.cursor).map(|i| i.name.clone())
    }

    fn clamp(&mut self, st: &Store) {
        let n = self.shown(st).len();
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

    fn all_installed(&self, st: &Store, names: &[String]) -> bool {
        names.iter().all(|n| st.cat.item(n).is_some_and(|i| i.installed.is_some()))
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
            Some(n) => {
                if st.job.as_ref().is_some_and(|j| j.running() && j.names.contains(&n)) {
                    Go::Push(Box::new(Installing::new()))
                } else {
                    Go::Push(Box::new(ItemView::new(&n)))
                }
            }
            None => Go::Stay,
        }
    }

    fn set_filter(&mut self, on: bool, st: &Store) {
        if self.filter != on {
            self.filter = on;
            self.cursor = 0;
        }
        self.clamp(st);
    }

    /// `u`: with the filter on, update everything shown; on an item with an update, update
    /// it; otherwise show the updates.
    fn update_key(&mut self, st: &mut Store) -> Go {
        if self.filter {
            let names: Vec<String> = self.shown(st).iter().map(|i| i.name.clone()).collect();
            if !names.is_empty() && st.start_job(Verb::Update, names) {
                self.filter = false;
                self.sel.clear();
                return Go::Push(Box::new(Installing::new()));
            }
            return Go::Stay;
        }
        if let Some(n) = self.current(st).filter(|n| st.cat.update_for(n).is_some()) {
            if st.start_job(Verb::Update, vec![n]) {
                return Go::Push(Box::new(Installing::new()));
            }
            return Go::Stay;
        }
        if !st.cat.updates.is_empty() {
            self.set_filter(true, st);
        }
        Go::Stay
    }
}

impl View for Front {
    fn name(&self) -> &'static str {
        "front"
    }

    fn draw(&mut self, p: &mut Paint, st: &mut Store) {
        let pal = *p.f.pal();
        let (cols, rows) = (p.f.cols(), p.f.rows());
        let (cw, ch) = p.cell();
        let list: Vec<Item> = self.shown(st).into_iter().cloned().collect();
        self.cursor = self.cursor.min(list.len().saturating_sub(1));
        let tier = layout::tier(cols, rows);
        let tall = tier == Tier::Tall;
        let per_item = layout::item_rows(tier);
        // The list asks for its rows, but leaves the picture its smallest height when it can.
        let list_need = (list.len() as u16 * per_item).saturating_sub(u16::from(tall)).max(1);
        let body_need = list_need.min(rows.saturating_sub(11 + PICTURE_MIN_ROWS)).max(1);

        // Header: the item under the cursor.
        let cur = list.get(self.cursor).cloned();
        let name = cur.as_ref().map(|i| i.name.clone()).unwrap_or_default();
        let (hdr, pic) = header_for(p, st, &name, body_need, false, true, true);
        let info = if name.is_empty() { Info::default() } else { st.cat.info(&st.env, &name).clone() };
        let state = st.job.as_ref().and_then(|j| {
            if j.running() && j.current.as_deref() == Some(name.as_str()) {
                Some(j.verb.ing())
            } else if j.failed(&name) {
                Some("failed")
            } else {
                None
            }
        });
        let facts = if name.is_empty() { Default::default() } else { st.facts(&name, state) };
        let content = HeaderContent {
            masthead: Masthead::Front {
                updates: st.cat.updates.len(),
                filter: self.filter,
                items: st.cat.items.len(),
                installed: st.cat.items.iter().filter(|i| i.installed.is_some()).count(),
            },
            picture: pic,
            name: &name,
            standfirst: info.standfirst(),
            facts,
        };
        draw_header(p, &hdr, &content);
        if cur.is_some() {
            p.hit(Rect::new(0, 2, cols, hdr.facts.saturating_sub(1)), A_HEADER);
        }

        // Rows.
        let b = hdr.body;
        let fits = list_need <= b.h;
        let avail = if fits { b.h } else { b.h.saturating_sub(1) };
        let per = ((avail + u16::from(tall)) / per_item.max(1)).max(1) as usize;
        self.per_page = per;
        let pages = list.len().div_ceil(per).max(1);
        let page = self.page().min(pages - 1);
        self.page_base = page * per;
        if list.is_empty() {
            let msg = match (&st.cat.error, self.filter) {
                (Some(e), _) => e.clone(),
                (None, true) => "Everything is up to date.".to_string(),
                (None, false) => "Nothing here yet.".to_string(),
            };
            p.text_clip(El::Row(0), b.x, b.y, &msg, pal.dim_s().italic(), b.right());
        }
        let pill_rows = if tall { 2 } else { 1 };
        for (k, it) in list.iter().enumerate().skip(page * per).take(per) {
            let k_on = (k - page * per) as u16;
            let y = b.y + k_on * per_item;
            let el = El::Row(k_on);
            let is_cur = k == self.cursor;
            let on = self.sel.contains(&it.name);
            if is_cur {
                let placed = p.f.ctx.caps.kitty_graphics && {
                    let w_cells = cols.saturating_sub(2);
                    let key = format!("pill:{w_cells}x{pill_rows}:{cw}x{ch}:{:?}", pal.accent);
                    let (pw, ph) = (w_cells as u32 * cw as u32, pill_rows as u32 * ch as u32);
                    let pill =
                        p.f.ctx
                            .pics
                            .shape(key, || shapes::pill(pw, ph, ch as f32 / 2.0, pal.accent, PILL_ALPHA));
                    p.picture(El::Pill, &pill, 1, y, (0, 0), 1)
                };
                if !placed {
                    p.fill(El::Pill, Rect::new(0, y, 1, pill_rows), Style::new().bg(pal.accent));
                }
            }
            let num = format!("{:02}", it.no);
            let name_x = if tall {
                p.sized(el, b.x, y, &num, Sizing::scale(2), pal.rule_s());
                b.x + 5
            } else {
                p.text(el, b.x, y, &num, pal.dim_s());
                b.x + 4
            };
            let mut name_st = pal.ink_s();
            if is_cur {
                name_st = name_st.bold();
            }
            if on {
                name_st = name_st.underline(Underline::Double).ul_color(pal.accent);
            }
            let mut x = p.text_clip(el, name_x, y, &it.name, name_st, b.right());
            if !hdr.narrow && !it.category.is_empty() {
                let tw = text_width(&it.category) as u16;
                if x + 1 + tw + 12 < b.right() {
                    let sizing = Sizing { valign: 1, ..Sizing::frac(1, 2, 3) };
                    x = p.sized(el, x + 1, y, &it.category, sizing, pal.dim_s()).right();
                }
            }
            let (label, st_) = match st.job.as_ref().and_then(|j| j.word_for(&it.name)) {
                Some((w, failed)) => {
                    (w.to_string(), if failed { pal.accent_s().bold() } else { pal.accent_s() })
                }
                None => {
                    let s = st.cat.status(it);
                    let style = if s.hot() { pal.accent_s() } else { pal.dim_s() };
                    (s.label(), style)
                }
            };
            if !label.is_empty() && x + 1 + text_width(&label) as u16 <= b.right() {
                p.right(el, b.right(), y, &label, st_);
            }
            if tall {
                let sf = layout::fit_line(&it.summary, b.w.saturating_sub(5));
                p.text(el, name_x, y + 1, &sf, pal.dim_s().italic());
            }
            p.hit(Rect::new(0, y, cols, pill_rows), A_ROW + k_on as u32);
        }

        // Pager on the last body row.
        if pages > 1 {
            let py = b.bottom().saturating_sub(1);
            let dots: Vec<&str> = (0..pages).map(|i| if i == page { "●" } else { "○" }).collect();
            let s = format!("‹ {} ›", dots.join(" "));
            let x = p.centred(El::Pager, b, py, &s, pal.dim_s());
            p.hit(Rect::new(x, py, 1, 1), A_PREV);
            for i in 0..pages {
                p.hit(Rect::new(x + 2 + i as u16 * 2, py, 1, 1), A_DOT + i as u32);
            }
            p.hit(Rect::new(x + text_width(&s) as u16 - 1, py, 1, 1), A_NEXT);
        }

        // The selection bar on the notice row.
        if st.notice.is_none() && !self.sel.is_empty() {
            let names: Vec<String> = self.sel.iter().cloned().collect();
            let verb = if self.all_installed(st, &names) { "r removes them" } else { "i installs them" };
            let text = format!("{} selected · {verb}, ␣ clears", self.sel.len());
            let line = layout::fit_line(&text, hdr.content.w);
            p.text(El::Notice, hdr.gutter, hdr.notice, &line, pal.dim_s());
        }
    }

    fn keys(&self, st: &mut Store) -> Slots {
        let list = self.shown(st);
        let has = !list.is_empty();
        let cur = list.get(self.cursor);
        let verb = if !self.sel.is_empty() {
            let names: Vec<String> = self.sel.iter().cloned().collect();
            if self.all_installed(st, &names) {
                Slot::new("r", "remove", Key::Char('r'))
            } else {
                Slot::new("i", "install", Key::Char('i'))
            }
        } else {
            match cur {
                Some(i) if st.cat.update_for(&i.name).is_some() => Slot::new("u", "update", Key::Char('u')),
                Some(i) if i.installed.is_some() => Slot::new("r", "remove", Key::Char('r')),
                _ => Slot::new("i", "install", Key::Char('i')),
            }
        };
        [
            Some(Slot::new("⏎", "open", Key::Enter).when(has)),
            Some(verb.when(has)),
            Some(Slot::new("␣", "select", Key::Char(' ')).when(has)),
            Some(Slot::new("f", "full", Key::Char('f')).when(st.env.launcherctl.is_some())),
            Some(Slot::new("q", "quit", Key::Char('q'))),
        ]
    }

    fn handle(&mut self, ev: &Event, st: &mut Store) -> Go {
        let n = self.shown(st).len();
        let per = self.per_page.max(1);
        let last = n.saturating_sub(1);
        match ev {
            Event::Key(Key::Up | Key::Char('k')) => self.cursor = self.cursor.saturating_sub(1),
            Event::Key(Key::Down | Key::Char('j')) => self.cursor = (self.cursor + 1).min(last),
            Event::Key(Key::PageDown | Key::Right) => self.cursor = ((self.page() + 1) * per).min(last),
            Event::Key(Key::PageUp | Key::Left) => self.cursor = self.page().saturating_sub(1) * per,
            Event::Key(Key::Home) => self.cursor = 0,
            Event::Key(Key::End) => self.cursor = last,
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
            Event::Key(Key::Char('u')) => return self.update_key(st),
            Event::Key(Key::Char('s')) => {
                if let Some(nm) = self.current(st) {
                    st.star(&nm);
                }
            }
            Event::Key(Key::Esc) if self.filter => self.set_filter(false, st),
            Event::Key(Key::Esc) if !self.sel.is_empty() => self.sel.clear(),
            Event::Mouse(m) if m.kind == MouseKind::ScrollDown => self.cursor = (self.cursor + 1).min(last),
            Event::Mouse(m) if m.kind == MouseKind::ScrollUp => self.cursor = self.cursor.saturating_sub(1),
            Event::Tap { action, .. } => {
                let a = *action;
                match a {
                    A_HOME => {
                        self.set_filter(false, st);
                        self.cursor = 0;
                    }
                    A_HEADER => return self.open(st),
                    A_CONTEXT => {
                        if !st.cat.updates.is_empty() {
                            self.set_filter(true, st);
                        }
                    }
                    A_ALL => self.set_filter(false, st),
                    A_PREV => self.cursor = self.page().saturating_sub(1) * per,
                    A_NEXT => self.cursor = ((self.page() + 1) * per).min(last),
                    _ if (A_DOT..A_DOT + 50).contains(&a) => {
                        self.cursor = ((a - A_DOT) as usize * per).min(last);
                    }
                    _ if (A_ROW..A_ROW + 100).contains(&a) => {
                        let idx = (self.page_base + (a - A_ROW) as usize).min(last);
                        if idx == self.cursor {
                            return self.open(st);
                        }
                        self.cursor = idx;
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
