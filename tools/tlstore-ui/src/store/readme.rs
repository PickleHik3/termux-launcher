//! The upstream README, rendered under the Item page's rules: parse the markdown into
//! [`Block`]s (dropping what the rules drop), then fit the blocks into [`Row`]s of a given
//! width. Both steps are pure; the Item view paints the rows.

use pulldown_cmark::{CodeBlockKind, Event, LinkType, Options, Parser, Tag, TagEnd};

use crate::render::text_width;

/// Sections dropped with their content, by heading text (case-insensitive, trailing
/// punctuation removed), besides the item's own `readme-skip` list.
pub const SKIP_SECTIONS: [&str; 18] = [
    "installation",
    "install",
    "installing",
    "building",
    "build",
    "requirements",
    "contributing",
    "contributors",
    "license",
    "licence",
    "changelog",
    "sponsors",
    "sponsoring",
    "acknowledgements",
    "acknowledgments",
    "star history",
    "installation & usage",
    "building from source",
];

/// Rendered rows stop here; a link to the rest follows.
pub const MAX_ROWS: usize = 400;
/// Code blocks longer than this are cut.
pub const CODE_LINES: usize = 12;
/// An inline picture takes at most this many rows.
pub const IMAGE_ROWS: u16 = 6;

/// A run of text with one style.
#[derive(Clone, Debug, Default, PartialEq, Eq)]
pub struct Span {
    pub text: String,
    pub bold: bool,
    pub italic: bool,
    pub code: bool,
    pub link: Option<String>,
}

impl Span {
    pub fn plain(text: &str) -> Span {
        Span { text: text.to_string(), ..Span::default() }
    }
    fn same_style(&self, o: &Span) -> bool {
        self.bold == o.bold && self.italic == o.italic && self.code == o.code && self.link == o.link
    }
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Marker {
    Bullet,
    Number(u64),
    Task(bool),
}

#[derive(Clone, Debug, PartialEq, Eq)]
pub struct ListItem {
    /// 0 or 1: deeper items are flattened to 1.
    pub depth: u8,
    pub marker: Marker,
    pub spans: Vec<Span>,
}

#[derive(Clone, Debug, PartialEq, Eq)]
pub enum Block {
    H2(String),
    /// H3 and deeper.
    H3(String),
    Para(Vec<Span>),
    Quote(Vec<Span>),
    Image {
        src: String,
        alt: String,
    },
    List(Vec<ListItem>),
    Code(Vec<String>),
    Table {
        header: Vec<String>,
        rows: Vec<Vec<String>>,
    },
    Rule,
}

/// A parsed README: what is left after the drop rules.
#[derive(Clone, Debug, Default, PartialEq, Eq)]
pub struct Doc {
    /// The first image before the first H2: the header picture.
    pub first_image: Option<String>,
    pub blocks: Vec<Block>,
}

/// Heading text as compared against the skip lists.
fn norm_title(s: &str) -> String {
    s.trim().trim_end_matches([':', '.', '!', '?', ')', ' ', '…']).to_lowercase()
}

/// What an image URL is to us.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum ImageKind {
    /// PNG, JPEG and anything we can try to decode.
    Inline,
    /// SVG, shields and badges: nothing is shown.
    Skip,
    /// Animated: a link with the file name.
    Gif,
}

pub fn image_kind(src: &str) -> ImageKind {
    let s = src.to_lowercase();
    let path = s.split(['?', '#']).next().unwrap_or("");
    if path.ends_with(".svg") || s.contains("shields.io") || s.contains("badge") || s.contains("/badges/") {
        ImageKind::Skip
    } else if path.ends_with(".gif") {
        ImageKind::Gif
    } else {
        ImageKind::Inline
    }
}

/// The last path segment of a URL, for a GIF's link text.
pub fn file_name(src: &str) -> String {
    let path = src.split(['?', '#']).next().unwrap_or(src);
    path.rsplit('/').next().filter(|s| !s.is_empty()).unwrap_or(path).to_string()
}

/// The host of a URL, for a bare link's text.
pub fn host(url: &str) -> String {
    let rest = url.split_once("://").map(|(_, r)| r).unwrap_or(url);
    let host = rest.split(['/', '?', '#']).next().unwrap_or(rest);
    host.trim_start_matches("www.").to_string()
}

fn decode_entities(s: &str) -> String {
    if !s.contains('&') {
        return s.to_string();
    }
    let mut out = String::with_capacity(s.len());
    let mut rest = s;
    while let Some(i) = rest.find('&') {
        out.push_str(&rest[..i]);
        rest = &rest[i..];
        let Some(j) = rest.find(';').filter(|j| *j <= 8) else {
            out.push('&');
            rest = &rest[1..];
            continue;
        };
        let ent = &rest[1..j];
        let rep = match ent {
            "amp" => Some('&'),
            "lt" => Some('<'),
            "gt" => Some('>'),
            "quot" => Some('"'),
            "apos" | "#39" => Some('\''),
            "nbsp" => Some(' '),
            e if e.starts_with('#') => {
                let n = e.trim_start_matches('#');
                let v = if let Some(h) = n.strip_prefix(['x', 'X']) {
                    u32::from_str_radix(h, 16).ok()
                } else {
                    n.parse().ok()
                };
                v.and_then(char::from_u32)
            }
            _ => None,
        };
        match rep {
            Some(c) => out.push(c),
            None => out.push_str(&rest[..=j]),
        }
        rest = &rest[j + 1..];
    }
    out.push_str(rest);
    out
}

/// The value of attribute `name` inside an HTML tag's text.
fn attr(tag: &str, name: &str) -> Option<String> {
    let lower = tag.to_lowercase();
    let mut from = 0;
    while let Some(i) = lower[from..].find(name) {
        let at = from + i;
        let before_ok = at == 0 || !lower.as_bytes()[at - 1].is_ascii_alphanumeric();
        let after = lower[at + name.len()..].trim_start();
        if before_ok && after.starts_with('=') {
            let v = tag[tag.len() - after.len() + 1..].trim_start();
            let val = match v.chars().next() {
                Some(q @ ('"' | '\'')) => v[1..].split(q).next().unwrap_or(""),
                _ => v.split([' ', '>', '/']).next().unwrap_or(""),
            };
            return Some(decode_entities(val));
        }
        from = at + name.len();
    }
    None
}

#[derive(Default)]
struct TableBuild {
    header: Vec<String>,
    rows: Vec<Vec<String>>,
    in_head: bool,
    row: Vec<String>,
    cell: String,
}

struct Builder {
    skip: Vec<String>,
    doc: Doc,
    seen_h2: bool,
    /// Dropping a section: until a heading of this level or higher.
    skipping: Option<u8>,
    details: u32,
    spans: Vec<Span>,
    bold: u32,
    italic: u32,
    link: Option<(String, usize, bool)>,
    heading: Option<u8>,
    heading_text: String,
    quote: u32,
    code: Option<String>,
    lists: Vec<Option<u64>>,
    items: Vec<ListItem>,
    table: Option<TableBuild>,
    image: Option<(String, String)>,
    html: String,
}

impl Builder {
    fn new(skip: &[String]) -> Builder {
        Builder {
            skip: skip.iter().map(|s| norm_title(s)).collect(),
            doc: Doc::default(),
            seen_h2: false,
            skipping: None,
            details: 0,
            spans: Vec::new(),
            bold: 0,
            italic: 0,
            link: None,
            heading: None,
            heading_text: String::new(),
            quote: 0,
            code: None,
            lists: Vec::new(),
            items: Vec::new(),
            table: None,
            image: None,
            html: String::new(),
        }
    }

    fn visible(&self) -> bool {
        self.seen_h2 && self.skipping.is_none() && self.details == 0
    }

    fn should_skip(&self, title: &str) -> bool {
        let t = norm_title(title);
        SKIP_SECTIONS.contains(&t.as_str()) || self.skip.contains(&t)
    }

    fn push_text(&mut self, text: &str, code: bool) {
        if text.is_empty() {
            return;
        }
        if let Some(h) = self.heading {
            let _ = h;
            self.heading_text.push_str(text);
            return;
        }
        if let Some(c) = self.code.as_mut() {
            c.push_str(text);
            return;
        }
        if let Some(t) = self.table.as_mut() {
            t.cell.push_str(text);
            return;
        }
        if let Some((_, alt)) = self.image.as_mut() {
            alt.push_str(text);
            return;
        }
        if !self.visible() {
            return;
        }
        let span = Span {
            text: text.to_string(),
            bold: self.bold > 0,
            italic: self.italic > 0,
            code,
            link: self.link.as_ref().map(|l| l.0.clone()),
        };
        if let Some(last) = self.spans.last_mut().filter(|l| l.same_style(&span)) {
            last.text.push_str(text);
        } else {
            self.spans.push(span);
        }
    }

    fn flush_para(&mut self) {
        if self.spans.is_empty() {
            return;
        }
        let spans = std::mem::take(&mut self.spans);
        if self.spans_blank(&spans) {
            return;
        }
        if !self.lists.is_empty() {
            if let Some(item) = self.items.last_mut() {
                if !item.spans.is_empty() {
                    item.spans.push(Span::plain(" "));
                }
                item.spans.extend(spans);
            }
        } else if self.quote > 0 {
            self.doc.blocks.push(Block::Quote(spans));
        } else {
            self.doc.blocks.push(Block::Para(spans));
        }
    }

    fn spans_blank(&self, spans: &[Span]) -> bool {
        spans.iter().all(|s| s.text.trim().is_empty())
    }

    fn start_heading(&mut self, level: u8) {
        self.flush_para();
        self.heading = Some(level);
        self.heading_text.clear();
    }

    fn end_heading(&mut self) {
        let Some(level) = self.heading.take() else { return };
        let title = std::mem::take(&mut self.heading_text).trim().to_string();
        if self.details > 0 {
            return;
        }
        if let Some(l) = self.skipping {
            if level <= l {
                self.skipping = None;
            } else {
                return;
            }
        }
        if level == 1 {
            return;
        }
        if level == 2 {
            self.seen_h2 = true;
        }
        if self.should_skip(&title) {
            self.skipping = Some(level);
            return;
        }
        if !self.seen_h2 || title.is_empty() {
            return;
        }
        self.doc.blocks.push(if level == 2 { Block::H2(title) } else { Block::H3(title) });
    }

    fn image_done(&mut self, src: String, alt: String) {
        if src.is_empty() || self.details > 0 || self.skipping.is_some() {
            return;
        }
        if !self.seen_h2 {
            if self.doc.first_image.is_none() && image_kind(&src) == ImageKind::Inline {
                self.doc.first_image = Some(src);
            }
            return;
        }
        if self.table.is_some() {
            return;
        }
        self.flush_para();
        self.doc.blocks.push(Block::Image { src, alt: alt.trim().to_string() });
    }

    fn end_link(&mut self) {
        let Some((url, from, auto)) = self.link.take() else { return };
        let text: String = self.spans.iter().skip(from).map(|s| s.text.as_str()).collect();
        let bare = auto || text.trim() == url || text.trim() == url.trim_end_matches('/');
        if bare && from < self.spans.len() {
            self.spans.truncate(from);
            self.spans.push(Span {
                text: host(&url),
                bold: self.bold > 0,
                italic: self.italic > 0,
                code: false,
                link: Some(url),
            });
        }
    }

    /// Raw HTML, buffered across events so a tag split over lines still parses.
    fn flush_html(&mut self) {
        if self.html.is_empty() {
            return;
        }
        let html = std::mem::take(&mut self.html);
        let mut rest = html.as_str();
        while let Some(i) = rest.find('<') {
            self.html_text(&rest[..i]);
            let Some(j) = rest[i..].find('>') else {
                self.html_text(&rest[i..]);
                return;
            };
            let tag = &rest[i + 1..i + j];
            rest = &rest[i + j + 1..];
            self.html_tag(tag);
        }
        self.html_text(rest);
    }

    fn html_text(&mut self, text: &str) {
        if self.details > 0 || text.trim().is_empty() {
            return;
        }
        let t = decode_entities(text);
        let t: String = t.split_whitespace().collect::<Vec<_>>().join(" ");
        let lead = if text.starts_with(char::is_whitespace) && !self.spans.is_empty() { " " } else { "" };
        self.push_text(&format!("{lead}{t}"), false);
    }

    fn html_tag(&mut self, tag: &str) {
        let tag = tag.trim();
        if tag.starts_with('!') {
            return;
        }
        let closing = tag.starts_with('/');
        let name: String = tag
            .trim_start_matches('/')
            .chars()
            .take_while(|c| c.is_ascii_alphanumeric())
            .collect::<String>()
            .to_lowercase();
        match (name.as_str(), closing) {
            ("details", false) => self.details += 1,
            ("details", true) => self.details = self.details.saturating_sub(1),
            _ if self.details > 0 => {}
            ("img", _) => {
                let src = attr(tag, "src").unwrap_or_default();
                let alt = attr(tag, "alt").unwrap_or_default();
                self.image_done(src, alt);
            }
            ("h1" | "h2" | "h3", false) => self.start_heading(name.as_bytes()[1] - b'0'),
            ("h1" | "h2" | "h3", true) => self.end_heading(),
            ("br", _) => self.push_text("\n", false),
            ("p" | "div" | "li" | "tr", true) => self.flush_para(),
            ("b" | "strong", false) => self.bold += 1,
            ("b" | "strong", true) => self.bold = self.bold.saturating_sub(1),
            ("i" | "em", false) => self.italic += 1,
            ("i" | "em", true) => self.italic = self.italic.saturating_sub(1),
            ("a", false) => {
                if let Some(href) = attr(tag, "href") {
                    self.link = Some((href, self.spans.len(), false));
                }
            }
            ("a", true) => self.end_link(),
            _ => {}
        }
    }

    fn event(&mut self, ev: Event) {
        match &ev {
            Event::Html(t) | Event::InlineHtml(t) => {
                self.html.push_str(t);
                return;
            }
            _ => self.flush_html(),
        }
        match ev {
            Event::Start(tag) => match tag {
                Tag::Heading { level, .. } => self.start_heading(level as u8),
                Tag::Paragraph => {}
                Tag::BlockQuote(_) => {
                    self.flush_para();
                    self.quote += 1;
                }
                Tag::CodeBlock(kind) => {
                    self.flush_para();
                    let _ = matches!(kind, CodeBlockKind::Fenced(_));
                    self.code = Some(String::new());
                }
                Tag::List(start) => {
                    self.flush_para();
                    self.lists.push(start);
                }
                Tag::Item => {
                    self.flush_para();
                    let depth = self.lists.len().saturating_sub(1).min(1) as u8;
                    let marker = match self.lists.last_mut() {
                        Some(Some(n)) => {
                            let m = Marker::Number(*n);
                            *n += 1;
                            m
                        }
                        _ => Marker::Bullet,
                    };
                    self.items.push(ListItem { depth, marker, spans: Vec::new() });
                }
                Tag::Table(_) => {
                    self.flush_para();
                    self.table = Some(TableBuild::default());
                }
                Tag::TableHead => {
                    if let Some(t) = self.table.as_mut() {
                        t.in_head = true;
                        t.row.clear();
                    }
                }
                Tag::TableRow => {
                    if let Some(t) = self.table.as_mut() {
                        t.row.clear();
                    }
                }
                Tag::TableCell => {
                    if let Some(t) = self.table.as_mut() {
                        t.cell.clear();
                    }
                }
                Tag::Emphasis => self.italic += 1,
                Tag::Strong => self.bold += 1,
                Tag::Link { dest_url, link_type, .. } => {
                    let auto = matches!(link_type, LinkType::Autolink | LinkType::Email);
                    self.link = Some((dest_url.to_string(), self.spans.len(), auto));
                }
                Tag::Image { dest_url, .. } => self.image = Some((dest_url.to_string(), String::new())),
                _ => {}
            },
            Event::End(tag) => match tag {
                TagEnd::Heading(_) => self.end_heading(),
                TagEnd::Paragraph | TagEnd::HtmlBlock => self.flush_para(),
                TagEnd::BlockQuote(_) => {
                    self.flush_para();
                    self.quote = self.quote.saturating_sub(1);
                }
                TagEnd::CodeBlock => {
                    if let Some(c) = self.code.take() {
                        if self.visible() {
                            let lines: Vec<String> =
                                c.lines().map(|l| l.replace('\t', "    ").trim_end().to_string()).collect();
                            self.doc.blocks.push(Block::Code(lines));
                        }
                    }
                }
                TagEnd::Item => self.flush_para(),
                TagEnd::List(_) => {
                    self.flush_para();
                    self.lists.pop();
                    if self.lists.is_empty() {
                        let items = std::mem::take(&mut self.items);
                        if !items.is_empty() && self.visible() {
                            self.doc.blocks.push(Block::List(items));
                        }
                    }
                }
                TagEnd::TableHead => {
                    if let Some(t) = self.table.as_mut() {
                        t.header = std::mem::take(&mut t.row);
                        t.in_head = false;
                    }
                }
                TagEnd::TableRow => {
                    if let Some(t) = self.table.as_mut() {
                        let row = std::mem::take(&mut t.row);
                        t.rows.push(row);
                    }
                }
                TagEnd::TableCell => {
                    if let Some(t) = self.table.as_mut() {
                        let cell = std::mem::take(&mut t.cell);
                        t.row.push(cell.split_whitespace().collect::<Vec<_>>().join(" "));
                    }
                }
                TagEnd::Table => {
                    if let Some(t) = self.table.take() {
                        if self.visible() && !t.header.is_empty() {
                            self.doc.blocks.push(Block::Table { header: t.header, rows: t.rows });
                        }
                    }
                }
                TagEnd::Emphasis => self.italic = self.italic.saturating_sub(1),
                TagEnd::Strong => self.bold = self.bold.saturating_sub(1),
                TagEnd::Link => self.end_link(),
                TagEnd::Image => {
                    if let Some((src, alt)) = self.image.take() {
                        self.image_done(src, alt);
                    }
                }
                _ => {}
            },
            Event::Text(t) => self.push_text(&t, false),
            Event::Code(t) => self.push_text(&t, true),
            Event::SoftBreak => self.push_text(" ", false),
            Event::HardBreak => self.push_text("\n", false),
            Event::Rule => {
                self.flush_para();
                if self.visible() {
                    self.doc.blocks.push(Block::Rule);
                }
            }
            Event::TaskListMarker(done) => {
                if let Some(item) = self.items.last_mut() {
                    item.marker = Marker::Task(done);
                }
            }
            Event::Html(_) | Event::InlineHtml(_) => {}
            _ => {}
        }
    }

    fn finish(mut self) -> Doc {
        self.flush_html();
        self.flush_para();
        self.doc
    }
}

/// Parses `md` under the drop rules; `skip` is the item's own `readme-skip` list.
pub fn parse(md: &str, skip: &[String]) -> Doc {
    let opts = Options::ENABLE_TABLES | Options::ENABLE_TASKLISTS | Options::ENABLE_STRIKETHROUGH;
    let mut b = Builder::new(skip);
    for ev in Parser::new_ext(md, opts) {
        b.event(ev);
    }
    b.finish()
}

/// One fitted row of the Item body.
#[derive(Clone, Debug, PartialEq, Eq)]
pub enum Row {
    Blank,
    /// A wrapped line of text; `quote` draws `▎` at the gutter and the text dim italic.
    Text {
        indent: u16,
        spans: Vec<Span>,
        quote: bool,
    },
    /// Scale 2 bold; two rows tall where text sizing works.
    H2(String),
    /// Upper-case, bold, dim, dashed underline.
    H3(String),
    /// One line of a code block on a tonal background.
    Code(String),
    /// A picture fetched lazily; up to [`IMAGE_ROWS`] rows once it arrives.
    Image {
        src: String,
        alt: String,
    },
    /// The hairline under a table's header.
    Hairline,
    /// A dim link line (`… on GitHub`, a GIF's name, the rest of the page).
    Link {
        text: String,
        url: String,
    },
}

impl Row {
    /// Rows this takes when everything is shown (the cap counts these).
    pub fn height(&self) -> usize {
        match self {
            Row::H2(_) => 2,
            Row::Image { .. } => IMAGE_ROWS as usize,
            _ => 1,
        }
    }
}

/// Wraps styled spans to lines of at most `width` columns on word boundaries; a `\n` in a
/// span breaks the line. Words wider than the line are cut.
pub fn wrap_spans(spans: &[Span], width: u16) -> Vec<Vec<Span>> {
    let w = width.max(1) as usize;
    let mut lines: Vec<Vec<Span>> = Vec::new();
    let mut cur: Vec<Span> = Vec::new();
    let mut cur_w = 0usize;
    let mut prev_space = true;

    fn push(cur: &mut Vec<Span>, cur_w: &mut usize, style: &Span, text: &str) {
        *cur_w += text_width(text);
        match cur.last_mut() {
            Some(l) if l.same_style(style) => l.text.push_str(text),
            _ => cur.push(Span { text: text.to_string(), ..style.clone() }),
        }
    }

    for span in spans {
        for (k, seg) in span.text.split('\n').enumerate() {
            if k > 0 {
                lines.push(std::mem::take(&mut cur));
                cur_w = 0;
                prev_space = true;
            }
            let leading = seg.starts_with(char::is_whitespace);
            let mut first = true;
            for word in seg.split_whitespace() {
                let space = !first || leading || prev_space;
                first = false;
                let ww = text_width(word);
                if cur_w == 0 {
                    // Start of a line: cut a word that cannot fit at all.
                    let mut rest = word;
                    while text_width(rest) > w {
                        let mut cut = String::new();
                        for c in rest.chars() {
                            if text_width(&cut) + text_width(c.encode_utf8(&mut [0; 4])) > w {
                                break;
                            }
                            cut.push(c);
                        }
                        push(&mut cur, &mut cur_w, span, &cut);
                        lines.push(std::mem::take(&mut cur));
                        cur_w = 0;
                        rest = &rest[cut.len()..];
                    }
                    push(&mut cur, &mut cur_w, span, rest);
                } else if space {
                    if cur_w + 1 + ww <= w {
                        push(&mut cur, &mut cur_w, span, " ");
                        push(&mut cur, &mut cur_w, span, word);
                    } else {
                        lines.push(std::mem::take(&mut cur));
                        cur_w = 0;
                        push(&mut cur, &mut cur_w, span, word);
                    }
                } else if cur_w + ww <= w {
                    push(&mut cur, &mut cur_w, span, word);
                } else {
                    lines.push(std::mem::take(&mut cur));
                    cur_w = 0;
                    push(&mut cur, &mut cur_w, span, word);
                }
            }
            prev_space = seg.ends_with(char::is_whitespace) || seg.is_empty();
        }
    }
    if !cur.is_empty() {
        lines.push(cur);
    }
    // A space that landed at the start of a line (after a hard break) is dropped.
    for l in &mut lines {
        if let Some(f) = l.first_mut() {
            let trimmed = f.text.trim_start().to_string();
            f.text = trimmed;
        }
        l.retain(|s| !s.text.is_empty());
    }
    lines
}

fn text_rows(spans: &[Span], width: u16, indent: u16, quote: bool) -> Vec<Row> {
    wrap_spans(spans, width.saturating_sub(indent))
        .into_iter()
        .map(|spans| Row::Text { indent, spans, quote })
        .collect()
}

fn block_rows(b: &Block, width: u16, repo_url: &str) -> Vec<Row> {
    match b {
        Block::H2(t) => vec![Row::H2(t.clone())],
        Block::H3(t) => vec![Row::H3(t.to_uppercase())],
        Block::Para(spans) => text_rows(spans, width, 0, false),
        Block::Quote(spans) => text_rows(spans, width, 2, true),
        Block::Image { src, alt } => match image_kind(src) {
            ImageKind::Skip => Vec::new(),
            ImageKind::Gif => vec![Row::Link { text: file_name(src), url: src.clone() }],
            ImageKind::Inline => vec![Row::Image { src: src.clone(), alt: alt.clone() }],
        },
        Block::List(items) => {
            let mut rows = Vec::new();
            for it in items {
                let prefix = match it.marker {
                    Marker::Bullet => "• ".to_string(),
                    Marker::Number(n) => format!("{n}. "),
                    Marker::Task(false) => "☐ ".to_string(),
                    Marker::Task(true) => "☑ ".to_string(),
                };
                let indent = it.depth as u16 * 2;
                let hang = text_width(&prefix) as u16;
                let lines = wrap_spans(&it.spans, width.saturating_sub(indent + hang));
                let lines = if lines.is_empty() { vec![Vec::new()] } else { lines };
                for (i, mut l) in lines.into_iter().enumerate() {
                    let lead = if i == 0 { prefix.clone() } else { " ".repeat(hang as usize) };
                    l.insert(0, Span::plain(&lead));
                    rows.push(Row::Text { indent, spans: l, quote: false });
                }
            }
            rows
        }
        Block::Code(lines) => {
            let mut rows = Vec::new();
            let w = width.max(1) as usize;
            for l in lines.iter().take(CODE_LINES) {
                if l.is_empty() {
                    rows.push(Row::Code(String::new()));
                    continue;
                }
                let mut rest = l.as_str();
                while !rest.is_empty() {
                    let mut cut = String::new();
                    for c in rest.chars() {
                        if text_width(&cut) + text_width(c.encode_utf8(&mut [0; 4])) > w {
                            break;
                        }
                        cut.push(c);
                    }
                    if cut.is_empty() {
                        break;
                    }
                    rest = &rest[cut.len()..];
                    rows.push(Row::Code(cut));
                }
            }
            if lines.len() > CODE_LINES {
                rows.push(Row::Link { text: "… on GitHub".into(), url: repo_url.to_string() });
            }
            rows
        }
        Block::Table { header, rows: body } => {
            let n = header.len().max(body.iter().map(Vec::len).max().unwrap_or(0));
            if n == 0 {
                return Vec::new();
            }
            let mut widths = vec![0usize; n];
            for row in std::iter::once(header).chain(body.iter()) {
                for (i, c) in row.iter().enumerate() {
                    widths[i] = widths[i].max(text_width(c));
                }
            }
            let total: usize = widths.iter().sum::<usize>() + 2 * (n - 1);
            if total > width as usize {
                return vec![Row::Link {
                    text: format!("table: {n} columns, on GitHub"),
                    url: repo_url.to_string(),
                }];
            }
            let line = |row: &[String], bold: bool| {
                let mut s = String::new();
                for (i, w) in widths.iter().enumerate() {
                    let c = row.get(i).map(String::as_str).unwrap_or("");
                    s.push_str(c);
                    if i + 1 < n {
                        s.push_str(&" ".repeat(w - text_width(c) + 2));
                    }
                }
                Row::Text { indent: 0, spans: vec![Span { text: s, bold, ..Span::default() }], quote: false }
            };
            let mut rows = vec![line(header, true), Row::Hairline];
            rows.extend(body.iter().map(|r| line(r, false)));
            rows
        }
        Block::Rule => Vec::new(),
    }
}

/// Fits `doc` into rows of `width` columns: blocks one blank apart, H2s two rows tall, code
/// and tables cut as the rules say, and the first [`MAX_ROWS`] rows followed by a link to the
/// rest. `repo_url` is where the cut links point.
pub fn fit(doc: &Doc, width: u16, repo_url: &str) -> Vec<Row> {
    let mut rows: Vec<Row> = Vec::new();
    let mut count = 0usize;
    'blocks: for b in &doc.blocks {
        let mut br = block_rows(b, width, repo_url);
        if br.is_empty() {
            continue;
        }
        if !rows.is_empty() && !matches!(rows.last(), Some(Row::Blank)) {
            br.insert(0, Row::Blank);
        }
        for r in br {
            if count + r.height() > MAX_ROWS {
                rows.push(Row::Link { text: "read the rest on GitHub".into(), url: repo_url.to_string() });
                break 'blocks;
            }
            count += r.height();
            rows.push(r);
        }
    }
    while matches!(rows.last(), Some(Row::Blank)) {
        rows.pop();
    }
    rows
}

#[cfg(test)]
mod tests {
    use super::*;

    const REPO: &str = "https://github.com/o/r";

    fn text_of(rows: &[Row]) -> Vec<String> {
        rows.iter()
            .map(|r| match r {
                Row::Blank => String::new(),
                Row::Text { indent, spans, quote } => {
                    let mut s = if *quote { "▎ ".to_string() } else { String::new() };
                    s.push_str(&" ".repeat(*indent as usize));
                    s.extend(spans.iter().map(|x| x.text.as_str()));
                    s
                }
                Row::H2(t) => format!("## {t}"),
                Row::H3(t) => format!("### {t}"),
                Row::Code(c) => format!("| {c}"),
                Row::Image { src, .. } => format!("[image {src}]"),
                Row::Hairline => "────".into(),
                Row::Link { text, url } => format!("<{text} → {url}>"),
            })
            .collect()
    }

    #[test]
    fn everything_before_the_first_h2_is_dropped_but_the_first_image_is_kept() {
        let md = "# Title\n\n![shot](https://example.com/a.png)\n![two](b.png)\n\nIntro text.\n\n## Use\n\nBody.\n";
        let d = parse(md, &[]);
        assert_eq!(d.first_image.as_deref(), Some("https://example.com/a.png"));
        assert_eq!(d.blocks, vec![Block::H2("Use".into()), Block::Para(vec![Span::plain("Body.")])]);
        // A badge before the first H2 is not a header picture.
        let d = parse("![b](https://img.shields.io/x.svg)\n\n## A\nb\n", &[]);
        assert_eq!(d.first_image, None);
        // A README with no H2 at all renders nothing.
        assert!(parse("# Only a title\n\ntext\n", &[]).blocks.is_empty());
    }

    #[test]
    fn named_sections_and_the_item_skip_list_are_dropped_to_the_next_peer_heading() {
        let md = "## Features\nf\n## Installation:\nskip me\n### Deep\nalso skipped\n## Usage\nu\n\
                  ## portability\nslow\n### More\n### Back\nx\n## LICENSE\nmit\n";
        let d = parse(md, &["Portability".into()]);
        let titles: Vec<String> = d
            .blocks
            .iter()
            .filter_map(|b| match b {
                Block::H2(t) | Block::H3(t) => Some(t.clone()),
                _ => None,
            })
            .collect();
        assert_eq!(titles, vec!["Features", "Usage"]);
        assert!(!d.blocks.iter().any(|b| matches!(b, Block::Para(s) if s[0].text.contains("skip"))));
        assert!(!d.blocks.iter().any(|b| matches!(b, Block::Para(s) if s[0].text == "slow")));
        // A skipped H3 ends at the next H3 or higher, not at the end of the H2.
        let d = parse("## A\n### Contributing\nno\n### Yes\nyes\n", &[]);
        assert_eq!(d.blocks.len(), 3);
        assert_eq!(d.blocks[1], Block::H3("Yes".into()));
    }

    #[test]
    fn h1_is_dropped_and_h2_h3_are_kept_in_their_kinds() {
        let d = parse("## A\n# Big\ntext\n### Small\n#### Tiny\n", &[]);
        assert_eq!(
            d.blocks,
            vec![
                Block::H2("A".into()),
                Block::Para(vec![Span::plain("text")]),
                Block::H3("Small".into()),
                Block::H3("Tiny".into()),
            ]
        );
        let rows = fit(&d, 40, REPO);
        assert_eq!(text_of(&rows), vec!["## A", "", "text", "", "### SMALL", "", "### TINY"]);
        assert_eq!(rows[0].height(), 2);
    }

    #[test]
    fn paragraphs_keep_inline_styles_and_wrap_on_words() {
        let d = parse("## A\nSome **bold** and *it* with `code` and a [link](https://x.y/z) here.\n", &[]);
        let Block::Para(spans) = &d.blocks[1] else { panic!("{:?}", d.blocks) };
        assert!(spans.iter().any(|s| s.bold && s.text == "bold"));
        assert!(spans.iter().any(|s| s.italic && s.text == "it"));
        assert!(spans.iter().any(|s| s.code && s.text == "code"));
        assert!(spans.iter().any(|s| s.link.as_deref() == Some("https://x.y/z") && s.text == "link"));
        let rows = fit(&d, 20, REPO);
        for r in &rows {
            if let Row::Text { spans, .. } = r {
                let w: usize = spans.iter().map(|s| text_width(&s.text)).sum();
                assert!(w <= 20, "{spans:?}");
            }
        }
        assert_eq!(text_of(&rows)[2], "Some bold and it");
        // Glued spans stay one word; a hard break starts a new line.
        let d = parse("## A\n**bold**text  \nnext\n", &[]);
        assert_eq!(text_of(&fit(&d, 40, REPO))[2..], ["boldtext", "next"]);
    }

    #[test]
    fn bare_links_show_their_host() {
        let d = parse("## A\nSee <https://docs.example.com/x/y> and https://www.other.org/p.\n", &[]);
        let Block::Para(spans) = &d.blocks[1] else { panic!() };
        assert!(spans.iter().any(|s| s.text == "docs.example.com" && s.link.is_some()), "{spans:?}");
        assert_eq!(host("https://www.other.org/p"), "other.org");
        // A plain URL that markdown does not link stays as text.
        assert!(spans.iter().any(|s| s.text.contains("https://www.other.org/p")));
    }

    #[test]
    fn images_inline_skip_and_gif() {
        let d = parse(
            "## A\n![a](shot.PNG)\n\n![b](https://img.shields.io/badge/x.svg)\n\n![c](demo.gif)\n\n![d](https://a.b/e.svg)\n",
            &[],
        );
        let rows = fit(&d, 40, REPO);
        assert_eq!(text_of(&rows), vec!["## A", "", "[image shot.PNG]", "", "<demo.gif → demo.gif>"]);
        assert_eq!(rows[2].height(), 6);
        assert_eq!(image_kind("https://github.com/user-attachments/assets/abc"), ImageKind::Inline);
        assert_eq!(file_name("https://x/y/anim.gif?raw=1"), "anim.gif");
    }

    #[test]
    fn lists_have_markers_tasks_and_one_nesting_level() {
        let md = "## A\n- one\n- [ ] todo\n- [x] done\n  - deep\n    - deeper\n\n1. first\n2. second item that is long\n";
        let d = parse(md, &[]);
        let rows = fit(&d, 16, REPO);
        assert_eq!(
            text_of(&rows),
            vec![
                "## A",
                "",
                "• one",
                "☐ todo",
                "☑ done",
                "  • deep",
                "  • deeper",
                "",
                "1. first",
                "2. second item",
                "   that is long",
            ]
        );
    }

    #[test]
    fn code_blocks_are_cut_at_twelve_lines() {
        let body: String = (1..=14).map(|i| format!("line {i}\n")).collect();
        let d = parse(&format!("## A\n```sh\n{body}```\n"), &[]);
        let rows = fit(&d, 40, REPO);
        let code: Vec<&Row> = rows.iter().filter(|r| matches!(r, Row::Code(_))).collect();
        assert_eq!(code.len(), 12);
        assert_eq!(rows.last(), Some(&Row::Link { text: "… on GitHub".into(), url: REPO.into() }));
        // Short blocks are whole; long lines wrap.
        let d = parse("## A\n\n    indented code that is quite long really\n", &[]);
        let rows = fit(&d, 20, REPO);
        assert_eq!(rows.iter().filter(|r| matches!(r, Row::Code(_))).count(), 2);
        assert!(!rows.iter().any(|r| matches!(r, Row::Link { .. })));
    }

    #[test]
    fn tables_fit_or_become_a_link() {
        let md = "## A\n| Key | Value |\n|---|---|\n| a | 1 |\n| bb | 22 |\n";
        let d = parse(md, &[]);
        let rows = fit(&d, 40, REPO);
        assert_eq!(text_of(&rows), vec!["## A", "", "Key  Value", "────", "a    1", "bb   22"]);
        let Row::Text { spans, .. } = &rows[2] else { panic!() };
        assert!(spans[0].bold);
        let rows = fit(&d, 8, REPO);
        assert_eq!(text_of(&rows)[2], format!("<table: 2 columns, on GitHub → {REPO}>"));
    }

    #[test]
    fn blockquotes_are_marked() {
        let d = parse("## A\n> quoted words\n> go on\n", &[]);
        assert!(matches!(&d.blocks[1], Block::Quote(_)));
        let rows = fit(&d, 40, REPO);
        assert_eq!(text_of(&rows)[2], "▎   quoted words go on");
        assert!(matches!(&rows[2], Row::Text { quote: true, indent: 2, .. }));
    }

    #[test]
    fn raw_html_is_reduced_to_what_matters() {
        let md = "<h1 align=\"center\">Name</h1>\n<p align=\"center\">\n  <img src=\"https://a.b/hero.png\" width=\"600\">\n</p>\n\n\
                  <h2>Features</h2>\nline one<br>line two\n\n<details>\n<summary>More</summary>\n\n## Hidden\n\nsecret\n\n</details>\n\n\
                  <h3>Sub</h3>\n<picture><source srcset=\"x.webp\"><img alt=\"pic\" src='shot.jpg'></picture>\n\n<div><b>bold</b> &amp; plain</div>\n";
        let d = parse(md, &[]);
        assert_eq!(d.first_image.as_deref(), Some("https://a.b/hero.png"));
        let rows = fit(&d, 40, REPO);
        let t = text_of(&rows);
        assert_eq!(t[0], "## Features");
        assert_eq!(&t[2..4], ["line one", "line two"]);
        assert!(!t.iter().any(|l| l.contains("secret") || l.contains("Hidden")), "{t:?}");
        assert!(t.contains(&"### SUB".to_string()));
        assert!(t.contains(&"[image shot.jpg]".to_string()), "{t:?}");
        assert!(t.contains(&"bold & plain".to_string()), "{t:?}");
        let Block::Para(spans) = d.blocks.last().unwrap() else { panic!("{:?}", d.blocks) };
        assert!(spans[0].bold && spans[0].text == "bold");
    }

    #[test]
    fn horizontal_rules_are_one_blank_row() {
        let d = parse("## A\none\n\n---\n\ntwo\n", &[]);
        assert_eq!(text_of(&fit(&d, 40, REPO)), vec!["## A", "", "one", "", "two"]);
    }

    #[test]
    fn the_page_is_capped_at_four_hundred_rows() {
        let body: String = (0..500).map(|i| format!("p{i}\n\n")).collect();
        let d = parse(&format!("## A\n{body}"), &[]);
        let rows = fit(&d, 40, REPO);
        let total: usize = rows.iter().map(Row::height).sum();
        assert!(total <= MAX_ROWS + 1, "{total}");
        assert_eq!(
            rows.last(),
            Some(&Row::Link { text: "read the rest on GitHub".into(), url: REPO.into() })
        );
    }

    #[test]
    fn wrap_spans_cuts_words_wider_than_the_line() {
        let lines = wrap_spans(&[Span::plain("abcdefghij xy")], 4);
        let t: Vec<String> = lines.iter().map(|l| l.iter().map(|s| s.text.as_str()).collect()).collect();
        assert_eq!(t, vec!["abcd", "efgh", "ij", "xy"]);
        assert!(wrap_spans(&[], 10).is_empty());
    }
}
