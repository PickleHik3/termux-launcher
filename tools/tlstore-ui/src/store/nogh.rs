//! Starring needs gh: a footnote on the item, with the two commands to run and the way out.

use crate::layout::Regions;
use crate::term::{Event, Key};

use crate::render::Rect;

use super::item::{draw_cover, item_cover_rows, star_rule};
use super::paint::{Chrome, Hint, Paint};
use super::scene::El;
use super::{Gh, Go, Store, View};

pub struct NoGh {
    pub name: String,
    pub repo: String,
}

impl NoGh {
    pub fn new(name: &str, repo: &str) -> NoGh {
        NoGh { name: name.to_string(), repo: repo.to_string() }
    }
}

impl View for NoGh {
    fn name(&self) -> &'static str {
        "nogh"
    }

    fn chrome(&mut self, st: &mut Store) -> Chrome {
        let no = st.cat.item(&self.name).map(|i| i.no).unwrap_or(0);
        let cat = st.cat.item(&self.name).map(|i| i.category.clone()).unwrap_or_default();
        Chrome {
            crumb: format!("/ apps / {}", self.name),
            context: None,
            lead: if cat.is_empty() { format!("no. {no:02}") } else { format!("no. {no:02} · {cat}") },
            word: self.name.clone(),
            keys: vec![
                Hint::new("o", "open the repo instead", Key::Char('o')),
                Hint::new("esc", "not now", Key::Esc),
            ],
        }
    }

    fn body(&mut self, p: &mut Paint, st: &mut Store, r: &Regions) {
        let pal = *p.f.pal();
        let b = r.body;
        let mut y = b.y;
        let cover = item_cover_rows(r);
        if cover > 0 {
            let cols = p.f.cols();
            draw_cover(p, st, &self.name, Rect::new(0, y, cols, cover));
            y += cover + 1;
        }
        star_rule(p, El::Block(0), b, y, "☆¹", pal.accent_s());
        let missing = st.gh == Gh::Missing;
        let cmds: &[&str] = if missing { &["pkg install gh", "gh auth login"] } else { &["gh auth login"] };
        let ask = if missing {
            ["Install it and sign in once, then press", "s again:"]
        } else {
            ["Sign in once, then press s again:", ""]
        };
        let lines = 3 + usize::from(!ask[1].is_empty()) as u16 + 1 + cmds.len() as u16;
        let top = (y + 3).min(b.bottom().saturating_sub(lines + 1)).max(y + 2);
        p.hline(El::Block(1), b.x, b.right(), top, '─', pal.rule_s());
        let mut y = top + 1;
        let x = p.text(El::Block(2), b.x, y, "¹", pal.accent_s());
        p.text(El::Block(2), x + 1, y, "Starring needs gh.", pal.ink_s().bold());
        y += 1;
        for l in ask.iter().filter(|l| !l.is_empty()) {
            p.text_clip(El::Block(2), b.x + 2, y, l, pal.dim_s(), b.right());
            y += 1;
        }
        y += 1;
        for c in cmds {
            let x = p.text(El::Block(3), b.x + 4, y, "$", pal.dim_s());
            p.text(El::Block(3), x + 1, y, c, pal.ink_s());
            y += 1;
        }
    }

    fn handle(&mut self, ev: &Event, st: &mut Store) -> Go {
        match ev {
            Event::Key(Key::Char('o')) => {
                st.open_repo(&self.repo);
                Go::Stay
            }
            Event::Key(Key::Char('s')) => {
                if st.recheck_gh() == Gh::Ready {
                    st.pending_star = Some(self.repo.clone());
                    st.want_star(&self.repo);
                    Go::Back
                } else {
                    Go::Stay
                }
            }
            Event::Key(Key::Esc | Key::Backspace) => Go::Back,
            Event::Key(_) => Go::Pass,
            _ => Go::Stay,
        }
    }
}
