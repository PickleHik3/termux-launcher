//! Item: one app's page, written from the catalog by the item spread rules. Scrolls when
//! taller than the screen.

use crate::layout::{self, Regions, Tier};
use crate::picture::{Fit, Picture};
use crate::render::{text_width, Rect, Style, Underline};
use crate::term::{Event, Key, MouseKind};

use super::data::Info;
use super::nogh::NoGh;
use super::paint::{Chrome, ContextItem, Hint, Paint};
use super::scene::El;
use super::{installing::Installing, Gh, Go, Store, Verb, View};

const A_REPO: u32 = 100;
const A_STAR: u32 = 101;
const A_MORE: u32 = 102;

pub struct ItemView {
    pub name: String,
    pub scroll: u16,
    /// Content height and visible height from the last frame.
    content_h: u16,
    view_h: u16,
    drag: Option<(u16, u16)>,
}

impl ItemView {
    pub fn new(name: &str) -> ItemView {
        ItemView { name: name.to_string(), scroll: 0, content_h: 0, view_h: 0, drag: None }
    }

    fn max_scroll(&self) -> u16 {
        self.content_h.saturating_sub(self.view_h)
    }

    fn scroll_by(&mut self, d: i32) {
        self.scroll = (self.scroll as i32 + d).clamp(0, self.max_scroll() as i32) as u16;
    }

    fn info(&self, st: &mut Store) -> Info {
        st.cat.info(&st.env, &self.name).clone()
    }

    fn installed(&self, st: &Store) -> bool {
        st.cat.item(&self.name).is_some_and(|i| i.installed.is_some())
    }

    fn job(&self, st: &mut Store, verb: Verb) -> Go {
        if st.start_job(verb, vec![self.name.clone()]) {
            Go::Push(Box::new(Installing::new()))
        } else {
            Go::Stay
        }
    }
}

/// The `s` key on an item with an upstream: star through gh, or explain how to get gh.
pub fn star_key(st: &mut Store, name: &str, repo: &str) -> Go {
    match st.gh_now() {
        Gh::Ready => {
            st.want_star(repo);
            if st.starred(repo).is_some() {
                st.toggle_star(repo);
            } else {
                st.pending_star = Some(repo.to_string());
            }
            Go::Stay
        }
        _ => Go::Push(Box::new(NoGh::new(name, repo))),
    }
}

/// The item's cover picture at `c`, when it is really there. False (nothing drawn) when the
/// terminal shows no pictures or the item has none: then there is no cover at all.
pub fn cover_picture(p: &mut Paint, st: &mut Store, name: &str, c: Rect, demo: bool) -> Option<Picture> {
    if !p.f.ctx.caps.kitty_graphics || c.w == 0 || c.h == 0 {
        return None;
    }
    let (cw, ch) = (p.f.ctx.size.cell_w as u32, p.f.ctx.size.cell_h as u32);
    let path = st.cat.picture(&st.env, name, demo)?;
    let fit = if demo { Fit::Contain } else { Fit::Cover };
    p.f.ctx.pics.file(&path, c.w as u32 * cw, c.h as u32 * ch, fit).ok()
}

/// Whether the item has a picture the terminal can show (asks the script once per item).
pub fn has_picture(p: &Paint, st: &mut Store, name: &str, demo: bool) -> bool {
    p.f.ctx.caps.kitty_graphics && st.cat.picture(&st.env, name, demo).is_some()
}

/// The star rule: ten dashes, the mark, ten dashes.
pub fn star_rule(p: &mut Paint, el: El, within: Rect, y: u16, mark: &str, mark_st: Style) -> Rect {
    let pal = *p.f.pal();
    let dashes = "─".repeat(10);
    let w = 10 + 1 + text_width(mark) as u16 + 1 + 10;
    let x = layout::centre_x(within, w);
    let mut e = p.text(el, x, y, &dashes, pal.rule_s());
    e = p.text(el, e + 1, y, mark, mark_st);
    p.text(el, e + 1, y, &dashes, pal.rule_s());
    Rect::new(x, y, w, 1)
}

impl View for ItemView {
    fn name(&self) -> &'static str {
        "item"
    }

    fn chrome(&mut self, st: &mut Store) -> Chrome {
        let info = self.info(st);
        let item = st.cat.item(&self.name).cloned();
        let no = item.as_ref().map(|i| i.no).unwrap_or(0);
        let cat = info.get("Category").unwrap_or("").to_string();
        let lead = if cat.is_empty() { format!("no. {no:02}") } else { format!("no. {no:02} · {cat}") };
        let repo = info.upstream().map(str::to_string);
        let starred = repo.as_deref().and_then(|r| st.starred(r)) == Some(true) && st.gh == Gh::Ready;
        let context = if starred {
            Some(ContextItem { text: "★ starred".into(), link: false })
        } else {
            st.updates_link()
        };
        let mut keys = Vec::new();
        if st.cat.update_for(&self.name).is_some() {
            keys.push(Hint::new("u", "update", Key::Char('u')));
        }
        if self.installed(st) {
            keys.push(Hint::new("r", "remove", Key::Char('r')));
        } else {
            keys.push(Hint::new("i", "install", Key::Char('i')));
        }
        if repo.is_some() {
            keys.push(Hint::new("s", if starred { "unstar" } else { "star" }, Key::Char('s')));
            keys.push(Hint::new("o", "repo", Key::Char('o')));
        }
        keys.push(Hint::new("esc", "back", Key::Esc));
        Chrome {
            crumb: format!("/ apps / {}", self.name),
            context,
            lead,
            word: self.name.clone(),
            script: false,
            keys,
        }
    }

    fn body(&mut self, p: &mut Paint, st: &mut Store, r: &Regions) {
        let pal = *p.f.pal();
        let info = self.info(st);
        let b = r.body;
        let cols = p.f.cols();
        let (cw, ch) = (p.f.ctx.size.cell_w, p.f.ctx.size.cell_h);
        self.view_h = b.h;
        let repo = info.upstream().map(str::to_string);

        // Everything the page says, fitted to the column first, so the height is known before
        // anything is drawn (a cover only goes on top when the rest fits under it).
        let text_w = b.w.min(44);
        let standfirst = info
            .get("Standfirst")
            .or(info.get("Summary"))
            .map(|s| layout::wrap(s, text_w, 2))
            .unwrap_or_default();
        let star = repo.is_some() && st.gh == Gh::Ready;
        let upd = st.cat.update_for(&self.name).cloned();
        let item = st.cat.item(&self.name).cloned();
        let version = match (&upd, &item) {
            (Some(u), _) => Some((u.have.clone(), Some(u.new.clone()))),
            (None, Some(i)) => {
                let v = i.installed.clone().unwrap_or_else(|| i.version.clone());
                (!v.is_empty() && v != "-").then_some((v, None))
            }
            _ => None,
        };
        let mut facts: Vec<(&str, String, Option<String>)> = Vec::new();
        if let Some((v, new)) = version {
            facts.push(("version", v, new));
        }
        for (label, key) in [("made by", "Author"), ("licence", "Licence"), ("size", "Size")] {
            if let Some(v) = info.get(key) {
                facts.push((label, v.to_string(), None));
            }
        }
        let does: Vec<String> =
            info.does().iter().map(|d| layout::fit_line(d, b.w.saturating_sub(2))).collect();
        let try_it = info.get("Try").map(str::to_string);
        let notes: Vec<String> = info.notes().iter().take(2).flat_map(|n| layout::wrap(n, b.w, 2)).collect();

        let mut need = 2; // the line and the blank under it
        if !standfirst.is_empty() {
            need += standfirst.len() as u16 + 1;
        }
        if star {
            need += 2;
        }
        if !facts.is_empty() {
            need += facts.len() as u16 + 3;
        }
        if !does.is_empty() {
            need += does.len() as u16 + 2;
        }
        if try_it.is_some() {
            need += 3;
        }
        if !notes.is_empty() {
            need += notes.len() as u16 + 1;
        }
        let spare = b.h.saturating_sub(need);
        // Pictures are asked for (the script may fetch them) only when there is room.
        let cover_rows = layout::cover_rows(spare, 7);
        let cover_rows = if cover_rows > 0 && has_picture(p, st, &self.name, false) { cover_rows } else { 0 };
        let cover = (cover_rows > 0)
            .then(|| cover_picture(p, st, &self.name, Rect::new(0, b.y, cols, cover_rows), false))
            .flatten();
        let spare = if cover.is_some() { spare - cover_rows - 1 } else { spare };
        let demo_rows = if r.tier == Tier::Tall { 6 } else { 4 };
        let demo = (info.get("Demo").is_some() && spare > demo_rows && has_picture(p, st, &self.name, true))
            .then(|| {
                let d = Rect::new(b.x, 0, b.w, demo_rows);
                cover_picture(p, st, &self.name, d, true)
            })
            .flatten();

        p.clip = Some(b);
        p.scroll = self.scroll;
        let mut y = b.y;
        let mut block = 0u16;
        let next = |b: &mut u16| {
            let e = El::Block(*b);
            *b += 1;
            e
        };

        // 1 Cover.
        if let Some(pic) = &cover {
            p.picture(El::Cover, pic, 0, y, (0, 0), 1);
            y += cover_rows + 1;
        }

        // 3 Line: installed state left, upstream right.
        let el = next(&mut block);
        let mut left_end = b.x;
        if let Some(it) = &item {
            if let Some(v) = &it.installed {
                left_end = p.text(el, b.x, y, &format!("installed {v}"), pal.dim_s());
            }
        }
        match &repo {
            Some(rp) => {
                let st_ = pal.accent_s().underline(Underline::Curly).ul_color(pal.accent);
                let shown = if left_end + 2 + text_width(rp) as u16 > b.right() {
                    layout::fit_line(rp, b.right().saturating_sub(left_end + 2))
                } else {
                    rp.clone()
                };
                let x = p.right(el, b.right(), y, &shown, st_);
                let rect = Rect::new(x, y, b.right() - x, 1);
                p.link(rect, &format!("https://github.com/{rp}"));
                p.hit(rect, A_REPO);
            }
            None if info.setup() => {
                p.right(el, b.right(), y, "our setup", pal.dim_s());
            }
            None => {}
        }
        y += 2;

        // 4 Standfirst.
        if !standfirst.is_empty() {
            let el = next(&mut block);
            for l in &standfirst {
                p.centred(el, b, y, l, pal.dim_s().italic());
                y += 1;
            }
            y += 1;
        }

        // 5 Star rule, only once gh works; never for a setup.
        if let (true, Some(rp)) = (star, &repo) {
            st.want_star(rp);
            let el = next(&mut block);
            let (mark, mst) = match st.starred(rp) {
                Some(true) => ("★", pal.accent_s()),
                Some(false) => ("☆", pal.accent_s()),
                None => ("☆", pal.rule_s()),
            };
            let rect = star_rule(p, el, b, y, mark, mst);
            p.hit(rect, A_STAR);
            y += 2;
        }

        // 6 Facts.
        if !facts.is_empty() {
            let el = next(&mut block);
            let box_w = 38.min(b.w);
            let x = layout::centre_x(b, box_w);
            let val_w = box_w.saturating_sub(16);
            let top = format!("┌{}┬{}┐", "─".repeat(11), "─".repeat(val_w as usize + 2));
            let bot = format!("└{}┴{}┘", "─".repeat(11), "─".repeat(val_w as usize + 2));
            p.text(el, x, y, &top, pal.rule_s());
            for (i, (label, v, new)) in facts.iter().enumerate() {
                let ry = y + 1 + i as u16;
                let mut cx = p.text(el, x, ry, "│", pal.rule_s());
                cx = p.text(el, cx + 1, ry, &format!("{label:<9}"), pal.dim_s());
                cx = p.text(el, cx + 1, ry, "│", pal.rule_s());
                let vx = cx + 1;
                let limit = vx + val_w;
                match new {
                    Some(n) => {
                        let full = format!("{v} → {n}");
                        if text_width(&full) as u16 <= val_w {
                            let e = p.text(el, vx, ry, &format!("{v} → "), pal.ink_s());
                            p.text(el, e, ry, n, pal.accent_s().bold());
                        } else {
                            // Only room for one: the new version is what matters.
                            p.text(el, vx, ry, &layout::fit_line(n, val_w), pal.accent_s().bold());
                        }
                    }
                    None => {
                        p.text(el, vx, ry, &layout::fit_line(v, val_w), pal.ink_s());
                    }
                }
                p.text(el, limit + 1, ry, "│", pal.rule_s());
            }
            p.text(el, x, y + 1 + facts.len() as u16, &bot, pal.rule_s());
            y += facts.len() as u16 + 3;
        }

        // 7 What it does.
        if !does.is_empty() {
            let el = next(&mut block);
            p.text(el, b.x, y, &layout::spaced_caps("what it does"), pal.ink_s().bold());
            y += 1;
            for d in &does {
                let x = p.text(el, b.x, y, "→", pal.accent_s());
                p.text(el, x + 1, y, d, pal.ink_s());
                y += 1;
            }
            y += 1;
        }

        // 8 Try it.
        if let Some(t) = &try_it {
            let el = next(&mut block);
            p.text(el, b.x, y, &layout::spaced_caps("try it"), pal.ink_s().bold());
            y += 1;
            let band = Rect::new(b.x, y, b.w, 1);
            let tone = Style::new().bg(pal.tonal);
            p.fill(el, band, tone);
            let x = p.text(el, band.x + 1, y, "$", tone.fg(pal.dim));
            let room = band.right().saturating_sub(x + 2);
            p.text(el, x + 1, y, &layout::fit_line(t, room), tone.fg(pal.on_tonal));
            y += 2;
        }

        // 9 Demo: only a real picture, only with room to spare.
        if let Some(pic) = &demo {
            let el = next(&mut block);
            let slack = (b.w as u32 * cw as u32).saturating_sub(pic.width()) / 2;
            let x_px = b.x as u32 * cw as u32 + slack;
            p.picture(el, pic, (x_px / cw as u32) as u16, y, (x_px % cw as u32, 0), 2);
            y += pic.height().div_ceil(ch.max(1) as u32) as u16 + 1;
        }

        // 10 Good to know.
        if !notes.is_empty() {
            let el = next(&mut block);
            p.text(el, b.x, y, &layout::spaced_caps("good to know"), pal.ink_s().bold());
            y += 1;
            for n in &notes {
                p.text(el, b.x, y, n, pal.dim_s().italic());
                y += 1;
            }
        }
        self.content_h = y - b.y;
        self.scroll = self.scroll.min(self.max_scroll());
        p.clip = None;
        p.scroll = 0;
        // The page goes on below: say so on the notice line (unless a notice is showing).
        if self.scroll < self.max_scroll() && st.notice.is_none() && b.bottom() < r.keys.y {
            let x = p.right(El::Notice, b.right(), b.bottom(), "more below ↓", pal.dim_s());
            p.hit(Rect::new(x, b.bottom(), b.right() - x, 1), A_MORE);
        }
    }

    fn handle(&mut self, ev: &Event, st: &mut Store) -> Go {
        let page = self.view_h.saturating_sub(2).max(1) as i32;
        let repo = st.cat.info(&st.env, &self.name).upstream().map(str::to_string);
        match ev {
            Event::Key(Key::Up | Key::Char('k')) => self.scroll_by(-1),
            Event::Key(Key::Down | Key::Char('j')) => self.scroll_by(1),
            Event::Key(Key::PageUp) => self.scroll_by(-page),
            Event::Key(Key::PageDown) => self.scroll_by(page),
            Event::Key(Key::Home) => self.scroll = 0,
            Event::Key(Key::End) => self.scroll = self.max_scroll(),
            Event::Key(Key::Esc | Key::Backspace | Key::Left) => return Go::Back,
            Event::Key(Key::Char('u')) if st.cat.update_for(&self.name).is_some() => {
                return self.job(st, Verb::Update);
            }
            Event::Key(Key::Char('i')) if !self.installed(st) => return self.job(st, Verb::Install),
            Event::Key(Key::Char('r')) if self.installed(st) => return self.job(st, Verb::Remove),
            Event::Key(Key::Char('s')) => {
                if let Some(rp) = repo {
                    return star_key(st, &self.name, &rp);
                }
            }
            Event::Key(Key::Char('o')) => {
                if let Some(rp) = repo {
                    st.open_repo(&rp);
                }
            }
            Event::Tap { action: A_REPO, .. } => {
                if let Some(rp) = repo {
                    st.open_repo(&rp);
                }
            }
            Event::Tap { action: A_MORE, .. } => self.scroll_by(page),
            Event::Tap { action: A_STAR, .. } => {
                if let Some(rp) = repo {
                    return star_key(st, &self.name, &rp);
                }
            }
            Event::Mouse(m) => match m.kind {
                MouseKind::ScrollDown => self.scroll_by(3),
                MouseKind::ScrollUp => self.scroll_by(-3),
                MouseKind::Press => self.drag = Some((m.row, self.scroll)),
                MouseKind::Drag => {
                    if let Some((row0, s0)) = self.drag {
                        let s = s0 as i32 + row0 as i32 - m.row as i32;
                        self.scroll = s.clamp(0, self.max_scroll() as i32) as u16;
                    }
                }
                MouseKind::Release => self.drag = None,
                _ => {}
            },
            Event::Key(_) => return Go::Pass,
            _ => {}
        }
        Go::Stay
    }
}
