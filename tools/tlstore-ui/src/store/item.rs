//! Item: the header, then the upstream README rendered by [`super::readme`]. The whole page
//! scrolls by rows; README pictures are fetched as they come into view.

use std::rc::Rc;

use crate::layout;
use crate::picture::Fit;
use crate::render::{Rect, Sizing, Style, Underline};
use crate::term::{Event, Key, MouseKind};

use super::paint::{draw_header, HeaderContent, Masthead, Paint, Slot, Slots, A_BACK, A_CONTEXT};
use super::readme::{self, Doc, Row, IMAGE_ROWS};
use super::scene::El;
use super::{header_for, installing::Installing, Gh, Go, Got, Readme, Store, Verb, View};

const A_LINK: u32 = 300;

/// Rows below the visible page a README picture is asked for ahead of time.
const PREFETCH_ROWS: u16 = 3;

struct Fitted {
    width: u16,
    doc: Rc<Doc>,
    rows: Vec<Row>,
}

pub struct ItemView {
    pub name: String,
    pub scroll: u16,
    /// Content height and visible height from the last frame.
    content_h: u16,
    view_h: u16,
    drag: Option<(u16, u16)>,
    fitted: Option<Fitted>,
    /// Link targets by tap index, from the last frame.
    links: Vec<String>,
}

impl ItemView {
    pub fn new(name: &str) -> ItemView {
        ItemView {
            name: name.to_string(),
            scroll: 0,
            content_h: 0,
            view_h: 0,
            drag: None,
            fitted: None,
            links: Vec::new(),
        }
    }

    fn max_scroll(&self) -> u16 {
        self.content_h.saturating_sub(self.view_h)
    }

    fn scroll_by(&mut self, d: i32) {
        self.scroll = (self.scroll as i32 + d).clamp(0, self.max_scroll() as i32) as u16;
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

    fn fit(&mut self, doc: &Rc<Doc>, width: u16, repo_url: &str) {
        let stale = match &self.fitted {
            Some(f) => f.width != width || !Rc::ptr_eq(&f.doc, doc),
            None => true,
        };
        if stale {
            self.fitted = Some(Fitted { width, doc: doc.clone(), rows: readme::fit(doc, width, repo_url) });
        }
    }
}

impl View for ItemView {
    fn name(&self) -> &'static str {
        "item"
    }

    fn draw(&mut self, p: &mut Paint, st: &mut Store) {
        let pal = *p.f.pal();
        let cols = p.f.cols();
        let (cw, ch) = p.cell();
        let info = st.cat.info(&st.env, &self.name).clone();
        let setup = info.setup();
        let repo = info.upstream().map(str::to_string);
        let repo_url = repo.as_ref().map(|r| format!("https://github.com/{r}")).unwrap_or_default();
        if let Some(r) = &repo {
            st.want_star(r);
        }
        let readme = if setup { Readme::NoUpstream } else { st.readme(&self.name) };

        let (hdr, pic) = header_for(p, st, &self.name, 7, !setup, false, true);
        let state = st.job.as_ref().and_then(|j| {
            if j.running() && j.current.as_deref() == Some(self.name.as_str()) {
                Some(j.verb.ing())
            } else if j.failed(&self.name) {
                Some("failed")
            } else {
                None
            }
        });
        let facts = st.facts(&self.name, state);
        let content = HeaderContent {
            masthead: Masthead::Page { repo: repo.clone(), setup },
            picture: pic,
            name: &self.name,
            standfirst: info.standfirst(),
            facts,
        };
        self.view_h = hdr.notice;
        p.clip = Some(Rect::new(0, 0, cols, hdr.notice));
        p.scroll = self.scroll;
        draw_header(p, &hdr, &content);

        let b = hdr.body;
        let mut y = b.y;
        let mut links: Vec<String> = Vec::new();
        let link_st = pal.accent_s().underline(Underline::Dotted).ul_color(pal.accent);
        let dim_link_st = pal.dim_s().underline(Underline::Dotted).ul_color(pal.dim);
        match readme {
            Readme::Doc(doc) => {
                self.fit(&doc, b.w, &repo_url);
                let fitted = self.fitted.as_ref().expect("fitted above");
                let mut k: u16 = 0;
                let ahead = self.scroll + self.view_h + PREFETCH_ROWS;
                for (i, row) in fitted.rows.iter().enumerate() {
                    let visible = p.row(y).is_some();
                    let el = if visible {
                        k += 1;
                        El::Block(k - 1)
                    } else {
                        El::Block(u16::MAX)
                    };
                    match row {
                        Row::Blank => y += 1,
                        Row::Text { indent, spans, quote } => {
                            let mut x = b.x + indent;
                            if *quote {
                                p.text(el, b.x, y, "▎", pal.rule_s());
                            }
                            for s in spans {
                                let mut style = if *quote { pal.dim_s().italic() } else { pal.ink_s() };
                                if s.bold {
                                    style = style.bold();
                                }
                                if s.italic {
                                    style = style.italic();
                                }
                                if s.code {
                                    style = Style { fg: pal.tonal_s().fg, bg: pal.tonal_s().bg, ..style };
                                }
                                if s.link.is_some() {
                                    style = Style {
                                        fg: link_st.fg,
                                        underline: link_st.underline,
                                        ul_color: link_st.ul_color,
                                        ..style
                                    };
                                }
                                let end = p.text_clip(el, x, y, &s.text, style, b.right());
                                if let Some(url) = &s.link {
                                    let r = Rect::new(x, y, end.saturating_sub(x), 1);
                                    p.link(r, url);
                                    p.hit(r, A_LINK + links.len() as u32);
                                    links.push(url.clone());
                                }
                                x = end;
                            }
                            y += 1;
                        }
                        Row::H2(t) => {
                            let h = if p.f.ctx.caps.text_sizing { 2 } else { 1 };
                            let shown = layout::fit_line(t, b.w / h.max(1));
                            p.sized(el, b.x, y, &shown, Sizing::scale(h as u8), pal.ink_s().bold());
                            y += h;
                        }
                        Row::H3(t) => {
                            let st_ = pal.dim_s().bold().underline(Underline::Dashed).ul_color(pal.rule);
                            p.text_clip(el, b.x, y, &layout::fit_line(t, b.w), st_, b.right());
                            y += 1;
                        }
                        Row::Code(c) => {
                            p.fill(el, Rect::new(b.x, y, b.w, 1), Style::new().bg(pal.tonal));
                            p.text_clip(el, b.x + 1, y, c, pal.tonal_s(), b.right().saturating_sub(1));
                            y += 1;
                        }
                        Row::Image { src, .. } => {
                            if !p.f.ctx.caps.kitty_graphics {
                                continue;
                            }
                            // Asked for only as it comes into view.
                            let got = if y <= ahead { st.asset_path(&self.name, src) } else { Got::Pending };
                            match got {
                                Got::Ready(path) => {
                                    let box_w = b.w as u32 * cw as u32;
                                    let box_h = IMAGE_ROWS as u32 * ch as u32;
                                    if let Ok(pic) = p.f.ctx.pics.file(&path, box_w, box_h, Fit::Contain) {
                                        let off = box_w.saturating_sub(pic.width()) / 2;
                                        let x_px = b.x as u32 * cw as u32 + off;
                                        let pid = 1 + i as u32;
                                        p.picture(
                                            el,
                                            &pic,
                                            (x_px / cw as u32) as u16,
                                            y,
                                            (x_px % cw as u32, 0),
                                            pid,
                                        );
                                        y += pic.height().div_ceil(ch as u32) as u16;
                                    }
                                }
                                Got::Pending => {
                                    p.text(el, b.x, y, "[picture]", pal.dim_s());
                                    y += 1;
                                }
                                Got::Failed(_) => {}
                            }
                        }
                        Row::Hairline => {
                            p.hline(el, b.x, b.right(), y, '─', pal.rule_s());
                            y += 1;
                        }
                        Row::Link { text, url } => {
                            let end = p.text_clip(el, b.x, y, text, dim_link_st, b.right());
                            let r = Rect::new(b.x, y, end.saturating_sub(b.x), 1);
                            p.link(r, url);
                            p.hit(r, A_LINK + links.len() as u32);
                            links.push(url.clone());
                            y += 1;
                        }
                    }
                }
            }
            Readme::Loading => {}
            Readme::NoUpstream if setup => {}
            Readme::NoUpstream | Readme::Unavailable => {
                let text = "read about it on GitHub";
                let end = p.text_clip(El::Block(0), b.x, y, text, dim_link_st, b.right());
                if !repo_url.is_empty() {
                    let r = Rect::new(b.x, y, end.saturating_sub(b.x), 1);
                    p.link(r, &repo_url);
                    p.hit(r, A_LINK);
                    links.push(repo_url.clone());
                }
                y += 1;
            }
        }
        self.links = links;
        self.content_h = y;
        self.scroll = self.scroll.min(self.max_scroll());
        p.clip = None;
        p.scroll = 0;
    }

    fn keys(&self, st: &mut Store) -> Slots {
        let info = st.cat.info(&st.env, &self.name).clone();
        let setup = info.setup();
        let repo = info.upstream().map(str::to_string);
        let starred = repo.as_deref().and_then(|r| st.starred(r)) == Some(true) && st.gh == Gh::Ready;
        let verb = if self.installed(st) {
            Slot::new("r", "remove", Key::Char('r'))
        } else {
            Slot::new("i", "install", Key::Char('i'))
        };
        [
            Some(verb),
            st.cat.update_for(&self.name).map(|_| Slot::new("u", "update", Key::Char('u'))),
            (!setup).then(|| {
                Slot::new("s", if starred { "unstar" } else { "star" }, Key::Char('s'))
                    .when(st.gh == Gh::Ready)
            }),
            (!setup).then(|| Slot::new("o", "repo", Key::Char('o'))),
            Some(Slot::new("esc", "back", Key::Esc)),
        ]
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
                st.star(&self.name);
            }
            Event::Key(Key::Char('o')) | Event::Tap { action: A_CONTEXT, .. } => {
                if let Some(rp) = repo {
                    st.open_repo(&rp);
                }
            }
            Event::Tap { action: A_BACK, .. } => return Go::Back,
            Event::Tap { action, .. } if *action >= A_LINK => {
                if let Some(url) = self.links.get((*action - A_LINK) as usize).cloned() {
                    st.open_url(&url);
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
