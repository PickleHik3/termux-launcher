//! Updates: what has something new, one entry each between hairlines; update one or all.

use crate::layout::Regions;
use crate::render::Rect;
use crate::term::{Event, Key, MouseKind};

use super::paint::{Chrome, ContextItem, Hint, Paint};
use super::scene::El;
use super::{installing::Installing, Go, Store, Verb, View};

const A_ENTRY: u32 = 100;
/// Rows one entry takes: rule, name line, blank, standfirst, blank.
const ENTRY_H: u16 = 5;

pub fn count_word(n: usize) -> String {
    const W: [&str; 10] = ["no", "one", "two", "three", "four", "five", "six", "seven", "eight", "nine"];
    W.get(n).map(|s| s.to_string()).unwrap_or_else(|| n.to_string())
}

#[derive(Default)]
pub struct Updates {
    pub cursor: usize,
    top: usize,
}

impl Updates {
    pub fn new() -> Updates {
        Updates::default()
    }

    fn start(&self, st: &mut Store, names: Vec<String>) -> Go {
        if names.is_empty() {
            return Go::Stay;
        }
        if st.start_job(Verb::Update, names) {
            Go::Push(Box::new(Installing::new()))
        } else {
            Go::Stay
        }
    }
}

impl View for Updates {
    fn name(&self) -> &'static str {
        "updates"
    }

    fn chrome(&mut self, st: &mut Store) -> Chrome {
        let n = st.cat.updates.len();
        let lead = if n == 0 { "nothing new".to_string() } else { format!("{} of yours", count_word(n)) };
        let mut keys = Vec::new();
        if n > 0 {
            keys.push(Hint::new("⏎", "update this", Key::Enter));
            keys.push(Hint::new("a", "update all", Key::Char('a')));
        }
        keys.push(Hint::new("esc", "back", Key::Esc));
        Chrome {
            crumb: "/ updates".into(),
            context: (n > 0).then(|| ContextItem { text: format!("{n} waiting"), link: false }),
            lead,
            word: "updates".into(),
            script: true,
            keys,
        }
    }

    fn body(&mut self, p: &mut Paint, st: &mut Store, r: &Regions) {
        let pal = *p.f.pal();
        let b = r.body;
        let ups = st.cat.updates.clone();
        if ups.is_empty() {
            p.centred(El::Row(0), b, b.y + 1, "Everything is up to date.", pal.dim_s().italic());
            return;
        }
        self.cursor = self.cursor.min(ups.len() - 1);
        let fit = ((b.h.saturating_sub(1)) / ENTRY_H).max(1) as usize;
        if self.cursor < self.top {
            self.top = self.cursor;
        } else if self.cursor >= self.top + fit {
            self.top = self.cursor + 1 - fit;
        }
        let mut y = b.y;
        for (i, u) in ups.iter().enumerate().skip(self.top).take(fit) {
            let el = El::Row((i - self.top) as u16);
            p.hline(el, b.x, b.right(), y, '─', pal.rule_s());
            let ly = y + 1;
            let cur = i == self.cursor;
            if cur {
                p.fill(el, Rect::new(b.x, ly, 1, 1), crate::render::Style::new().bg(pal.accent));
            }
            let x = p.text(el, b.x + 2, ly, &format!("{:02}", i + 1), pal.dim_s());
            let name_st = if cur { pal.accent_s().bold() } else { pal.ink_s().bold() };
            let name_end = p.text(el, x + 2, ly, &u.name, name_st);
            let nx = p.right(el, b.right(), ly, &u.new, pal.accent_s().bold());
            let old = format!("{} → ", u.have);
            let ox = nx.saturating_sub(crate::render::text_width(&old) as u16);
            if ox > name_end {
                p.text(el, ox, ly, &old, pal.dim_s());
            }
            let info = st.cat.info(&st.env, &u.name).clone();
            if let Some(sf) = info.get("Standfirst") {
                let x = b.x + if r.narrow { 2 } else { 6 };
                let line = crate::layout::fit_line(sf, b.right().saturating_sub(x));
                p.text(el, x, ly + 2, &line, pal.dim_s().italic());
            }
            p.hit(Rect::new(b.x, y, b.w, ENTRY_H - 1), A_ENTRY + i as u32);
            y += ENTRY_H;
        }
        p.hline(El::Row(fit.min(ups.len() - self.top) as u16), b.x, b.right(), y, '─', pal.rule_s());
    }

    fn handle(&mut self, ev: &Event, st: &mut Store) -> Go {
        let n = st.cat.updates.len();
        match ev {
            Event::Key(Key::Up | Key::Char('k')) => self.cursor = self.cursor.saturating_sub(1),
            Event::Key(Key::Down | Key::Char('j')) => {
                self.cursor = (self.cursor + 1).min(n.saturating_sub(1))
            }
            Event::Mouse(m) if m.kind == MouseKind::ScrollUp => self.cursor = self.cursor.saturating_sub(1),
            Event::Mouse(m) if m.kind == MouseKind::ScrollDown => {
                self.cursor = (self.cursor + 1).min(n.saturating_sub(1))
            }
            Event::Key(Key::Enter) => {
                let name = st.cat.updates.get(self.cursor).map(|u| u.name.clone());
                return self.start(st, name.into_iter().collect());
            }
            Event::Key(Key::Char('a')) => {
                let names = st.cat.updates.iter().map(|u| u.name.clone()).collect();
                return self.start(st, names);
            }
            Event::Tap { action, .. } if *action >= A_ENTRY => {
                self.cursor = ((*action - A_ENTRY) as usize).min(n.saturating_sub(1));
            }
            Event::Key(Key::Esc | Key::Backspace | Key::Left) => return Go::Back,
            Event::Key(_) => return Go::Pass,
            _ => {}
        }
        Go::Stay
    }

    fn refresh(&mut self, st: &Store) {
        self.cursor = self.cursor.min(st.cat.updates.len().saturating_sub(1));
    }
}
