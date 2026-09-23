//! Byte stream → events: keys, SGR mouse, in-band resize reports and the replies the
//! capability probe asks for. Stateful across reads, so a sequence split between two reads
//! still parses.

use std::os::fd::RawFd;

use crate::render::{ActionId, Rgb};

#[derive(Clone, Copy, Debug, PartialEq, Eq, Hash)]
pub enum Key {
    /// Any printable character, including `' '` and `'/'`.
    Char(char),
    /// A character typed with Alt (ESC prefix).
    Alt(char),
    /// Ctrl + letter, lowercase (`Ctrl('c')`).
    Ctrl(char),
    Enter,
    Esc,
    Tab,
    BackTab,
    Backspace,
    Delete,
    Up,
    Down,
    Left,
    Right,
    Home,
    End,
    PageUp,
    PageDown,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum MouseKind {
    Press,
    Release,
    /// Motion with a button held (a finger moving).
    Drag,
    /// Motion with no button (only when all-motion tracking is on).
    Move,
    ScrollUp,
    ScrollDown,
}

/// A mouse (touch) event. `col`/`row` are 0-based cells.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct Mouse {
    pub kind: MouseKind,
    /// 0 left, 1 middle, 2 right, 3 none.
    pub button: u8,
    pub col: u16,
    pub row: u16,
    pub shift: bool,
    pub alt: bool,
    pub ctrl: bool,
}

/// The terminal grid and the pixel size of one cell.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub struct Size {
    pub cols: u16,
    pub rows: u16,
    /// Cell size in pixels (best known; see `app` for where it comes from).
    pub cell_w: u16,
    pub cell_h: u16,
}

impl Size {
    pub const fn new(cols: u16, rows: u16, cell_w: u16, cell_h: u16) -> Size {
        Size { cols, rows, cell_w, cell_h }
    }
}

/// Terminal answers to queries. Screens can ignore these.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Reply {
    /// Kitty graphics query answer for image id `id`.
    Kitty { id: u32, ok: bool },
    /// Cursor position report, 1-based.
    Cursor { row: u16, col: u16 },
    /// DECRQM answer: mode and state (0 unknown, 1 set, 2 reset, 3 always set, 4 always reset).
    Mode { mode: u16, state: u8 },
    /// Cell size in pixels (`CSI 16 t` answer).
    CellPx { w: u16, h: u16 },
    /// Background colour (OSC 11 answer).
    Background(Rgb),
    /// Primary device attributes: the probe's end marker.
    DeviceAttrs,
}

#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Event {
    Key(Key),
    Mouse(Mouse),
    /// New grid size. From in-band reports (mode 2048) the parser leaves cell size 0 when the
    /// terminal sent no pixels; the app fills it in before screens see it.
    Resize(Size),
    /// Press and release on the same hit region (made by the app loop, not the parser).
    Tap {
        action: ActionId,
        col: u16,
        row: u16,
    },
    /// A file descriptor a screen asked to watch became readable (made by the app loop).
    Readable(RawFd),
    Reply(Reply),
}

enum Step {
    Ev(Event, usize),
    Skip(usize),
    Incomplete,
}

/// Incremental parser. Feed it bytes; call [`Parser::flush`] when no more bytes arrived
/// within the escape timeout so a lone ESC becomes [`Key::Esc`].
#[derive(Default)]
pub struct Parser {
    buf: Vec<u8>,
}

/// A partial sequence longer than this is garbage and is dropped.
const MAX_PENDING: usize = 1 << 16;

impl Parser {
    pub fn new() -> Parser {
        Parser::default()
    }

    /// True while bytes are held waiting for the rest of a sequence.
    pub fn pending(&self) -> bool {
        !self.buf.is_empty()
    }

    pub fn feed(&mut self, bytes: &[u8], out: &mut Vec<Event>) {
        self.buf.extend_from_slice(bytes);
        let mut pos = 0;
        while pos < self.buf.len() {
            match parse(&self.buf[pos..]) {
                Step::Ev(e, n) => {
                    out.push(e);
                    pos += n;
                }
                Step::Skip(n) => pos += n,
                Step::Incomplete => break,
            }
        }
        self.buf.drain(..pos);
        if self.buf.len() > MAX_PENDING {
            self.buf.clear();
        }
    }

    /// Resolves whatever is pending after a quiet period: a lone ESC is the Esc key, an ESC
    /// followed by an incomplete sequence is dropped.
    pub fn flush(&mut self, out: &mut Vec<Event>) {
        if self.buf == [0x1b] {
            out.push(Event::Key(Key::Esc));
        }
        self.buf.clear();
    }
}

fn utf8_len(b: u8) -> usize {
    match b {
        0x00..=0x7f => 1,
        0xc0..=0xdf => 2,
        0xe0..=0xef => 3,
        0xf0..=0xf7 => 4,
        _ => 0,
    }
}

fn parse(b: &[u8]) -> Step {
    let c = b[0];
    match c {
        0x1b => parse_esc(b),
        b'\r' | b'\n' => Step::Ev(Event::Key(Key::Enter), 1),
        b'\t' => Step::Ev(Event::Key(Key::Tab), 1),
        0x7f | 0x08 => Step::Ev(Event::Key(Key::Backspace), 1),
        0x01..=0x1a => Step::Ev(Event::Key(Key::Ctrl((b'a' + c - 1) as char)), 1),
        0x00 | 0x1c..=0x1f => Step::Skip(1),
        _ => match decode_char(b) {
            Some(Ok((ch, n))) => Step::Ev(Event::Key(Key::Char(ch)), n),
            Some(Err(n)) => Step::Skip(n),
            None => Step::Incomplete,
        },
    }
}

/// `Some(Ok((char, len)))`, `Some(Err(skip))` for invalid bytes, `None` when incomplete.
fn decode_char(b: &[u8]) -> Option<Result<(char, usize), usize>> {
    let n = utf8_len(b[0]);
    if n == 0 {
        return Some(Err(1));
    }
    if b.len() < n {
        return None;
    }
    match std::str::from_utf8(&b[..n]) {
        Ok(s) => Some(Ok((s.chars().next().unwrap_or(' '), n))),
        Err(_) => Some(Err(1)),
    }
}

/// Finds the string terminator (BEL or ESC \) from `from`; returns (payload end, total len).
fn find_st(b: &[u8], from: usize, allow_bel: bool) -> Option<(usize, usize)> {
    let mut i = from;
    while i < b.len() {
        if allow_bel && b[i] == 0x07 {
            return Some((i, i + 1));
        }
        if b[i] == 0x1b {
            if i + 1 >= b.len() {
                return None;
            }
            if b[i + 1] == b'\\' {
                return Some((i, i + 2));
            }
        }
        i += 1;
    }
    None
}

fn parse_esc(b: &[u8]) -> Step {
    if b.len() < 2 {
        return Step::Incomplete;
    }
    match b[1] {
        b'[' => parse_csi(b),
        b'O' => {
            if b.len() < 3 {
                return Step::Incomplete;
            }
            let k = match b[2] {
                b'A' => Key::Up,
                b'B' => Key::Down,
                b'C' => Key::Right,
                b'D' => Key::Left,
                b'H' => Key::Home,
                b'F' => Key::End,
                b'M' => Key::Enter,
                _ => return Step::Skip(3),
            };
            Step::Ev(Event::Key(k), 3)
        }
        b']' => match find_st(b, 2, true) {
            None => Step::Incomplete,
            Some((end, total)) => match parse_osc(&b[2..end]) {
                Some(r) => Step::Ev(Event::Reply(r), total),
                None => Step::Skip(total),
            },
        },
        b'_' => match find_st(b, 2, false) {
            None => Step::Incomplete,
            Some((end, total)) => match parse_apc(&b[2..end]) {
                Some(r) => Step::Ev(Event::Reply(r), total),
                None => Step::Skip(total),
            },
        },
        b'P' | b'^' | b'X' => match find_st(b, 2, false) {
            None => Step::Incomplete,
            Some((_, total)) => Step::Skip(total),
        },
        0x1b => Step::Ev(Event::Key(Key::Esc), 1),
        c if c >= 0x20 && c != 0x7f => match decode_char(&b[1..]) {
            Some(Ok((ch, n))) => Step::Ev(Event::Key(Key::Alt(ch)), 1 + n),
            Some(Err(_)) => Step::Ev(Event::Key(Key::Esc), 1),
            None => Step::Incomplete,
        },
        _ => Step::Ev(Event::Key(Key::Esc), 1),
    }
}

fn nums(p: &[u8]) -> Vec<u32> {
    // Sub-parameters after ':' are ignored; only the leading number of each field counts.
    p.split(|&c| c == b';')
        .map(|f| {
            let f = f.split(|&c| c == b':').next().unwrap_or(&[]);
            std::str::from_utf8(f).ok().and_then(|s| s.parse().ok()).unwrap_or(0)
        })
        .collect()
}

fn parse_csi(b: &[u8]) -> Step {
    let mut i = 2;
    while i < b.len() && (0x20..=0x3f).contains(&b[i]) {
        i += 1;
    }
    if i >= b.len() {
        return if b.len() > 256 { Step::Skip(b.len()) } else { Step::Incomplete };
    }
    let fin = b[i];
    let total = i + 1;
    if !(0x40..=0x7e).contains(&fin) {
        return Step::Skip(total);
    }
    let body = &b[2..i];
    let (marker, rest) = match body.first() {
        Some(&m) if matches!(m, b'<' | b'?' | b'>' | b'=') => (Some(m), &body[1..]),
        _ => (None, body),
    };
    let split = rest.iter().position(|c| (0x20..=0x2f).contains(c)).unwrap_or(rest.len());
    let (params, inter) = rest.split_at(split);
    let p = nums(params);
    let p0 = p.first().copied().unwrap_or(0);
    let arg = |k: usize| p.get(k).copied().unwrap_or(0);

    let ev = match (marker, inter, fin) {
        (Some(b'<'), _, b'M' | b'm') => {
            if p.len() < 3 {
                return Step::Skip(total);
            }
            let code = p0;
            let button = (code & 3) as u8;
            let kind = if code & 64 != 0 {
                if code & 1 == 0 {
                    MouseKind::ScrollUp
                } else {
                    MouseKind::ScrollDown
                }
            } else if code & 32 != 0 {
                if button == 3 {
                    MouseKind::Move
                } else {
                    MouseKind::Drag
                }
            } else if fin == b'm' {
                MouseKind::Release
            } else {
                MouseKind::Press
            };
            Event::Mouse(Mouse {
                kind,
                button,
                col: arg(1).saturating_sub(1) as u16,
                row: arg(2).saturating_sub(1) as u16,
                shift: code & 4 != 0,
                alt: code & 8 != 0,
                ctrl: code & 16 != 0,
            })
        }
        (Some(b'?'), b"$", b'y') => Event::Reply(Reply::Mode { mode: p0 as u16, state: arg(1) as u8 }),
        (Some(b'?'), _, b'c') => Event::Reply(Reply::DeviceAttrs),
        (None, b"", b'R') if p.len() == 2 => {
            Event::Reply(Reply::Cursor { row: p0 as u16, col: arg(1) as u16 })
        }
        (None, b"", b't') => match p0 {
            48 if p.len() >= 3 => {
                let (rows, cols) = (arg(1) as u16, arg(2) as u16);
                let (ph, pw) = (arg(3), arg(4));
                let cw = if cols > 0 && pw > 0 { (pw / cols as u32) as u16 } else { 0 };
                let ch = if rows > 0 && ph > 0 { (ph / rows as u32) as u16 } else { 0 };
                Event::Resize(Size::new(cols, rows, cw, ch))
            }
            6 if p.len() >= 3 => Event::Reply(Reply::CellPx { w: arg(2) as u16, h: arg(1) as u16 }),
            _ => return Step::Skip(total),
        },
        (None, b"", _) => {
            let key = match fin {
                b'A' => Key::Up,
                b'B' => Key::Down,
                b'C' => Key::Right,
                b'D' => Key::Left,
                b'H' => Key::Home,
                b'F' => Key::End,
                b'Z' => Key::BackTab,
                b'~' => match p0 {
                    1 | 7 => Key::Home,
                    4 | 8 => Key::End,
                    3 => Key::Delete,
                    5 => Key::PageUp,
                    6 => Key::PageDown,
                    _ => return Step::Skip(total),
                },
                b'u' => match p0 {
                    13 => Key::Enter,
                    27 => Key::Esc,
                    9 => Key::Tab,
                    127 => Key::Backspace,
                    c => match char::from_u32(c) {
                        Some(ch) if !ch.is_control() => Key::Char(ch),
                        _ => return Step::Skip(total),
                    },
                },
                _ => return Step::Skip(total),
            };
            Event::Key(key)
        }
        _ => return Step::Skip(total),
    };
    Step::Ev(ev, total)
}

fn hex_channel(s: &str) -> Option<u8> {
    // X11 colour specs use 1–4 hex digits per channel; scale to 8 bits.
    if s.is_empty() || s.len() > 4 {
        return None;
    }
    let v = u32::from_str_radix(s, 16).ok()?;
    let max = (1u32 << (4 * s.len() as u32)) - 1;
    Some(((v * 255 + max / 2) / max) as u8)
}

fn parse_osc(p: &[u8]) -> Option<Reply> {
    let s = std::str::from_utf8(p).ok()?;
    let rest = s.strip_prefix("11;")?;
    let rgb = rest.strip_prefix("rgb:")?;
    let mut it = rgb.split('/');
    let r = hex_channel(it.next()?)?;
    let g = hex_channel(it.next()?)?;
    let b = hex_channel(it.next()?)?;
    Some(Reply::Background(Rgb(r, g, b)))
}

fn parse_apc(p: &[u8]) -> Option<Reply> {
    let s = std::str::from_utf8(p).ok()?;
    let body = s.strip_prefix('G')?;
    let (keys, msg) = body.split_once(';').unwrap_or((body, ""));
    let id = keys.split(',').find_map(|kv| kv.strip_prefix("i=")).and_then(|v| v.parse().ok()).unwrap_or(0);
    Some(Reply::Kitty { id, ok: msg == "OK" })
}

#[cfg(test)]
mod tests {
    use super::*;

    fn run(bytes: &[u8]) -> Vec<Event> {
        let mut p = Parser::new();
        let mut v = Vec::new();
        p.feed(bytes, &mut v);
        v
    }
    fn keys(bytes: &[u8]) -> Vec<Key> {
        run(bytes)
            .into_iter()
            .map(|e| match e {
                Event::Key(k) => k,
                other => panic!("not a key: {other:?}"),
            })
            .collect()
    }

    #[test]
    fn plain_keys() {
        assert_eq!(
            keys(b"a/ \r\x7f\x03\t"),
            vec![
                Key::Char('a'),
                Key::Char('/'),
                Key::Char(' '),
                Key::Enter,
                Key::Backspace,
                Key::Ctrl('c'),
                Key::Tab
            ]
        );
        assert_eq!(keys("é字".as_bytes()), vec![Key::Char('é'), Key::Char('字')]);
    }

    #[test]
    fn arrows_both_forms_and_tilde_keys() {
        assert_eq!(
            keys(b"\x1b[A\x1b[B\x1bOC\x1bOD\x1b[1;5A\x1b[5~\x1b[6~\x1b[3~\x1b[H\x1b[4~\x1b[Z"),
            vec![
                Key::Up,
                Key::Down,
                Key::Right,
                Key::Left,
                Key::Up,
                Key::PageUp,
                Key::PageDown,
                Key::Delete,
                Key::Home,
                Key::End,
                Key::BackTab
            ]
        );
    }

    #[test]
    fn lone_esc_waits_for_flush() {
        let mut p = Parser::new();
        let mut v = Vec::new();
        p.feed(b"\x1b", &mut v);
        assert!(v.is_empty() && p.pending());
        p.flush(&mut v);
        assert_eq!(v, vec![Event::Key(Key::Esc)]);
        assert!(!p.pending());
        assert_eq!(keys(b"\x1b\x1b"), vec![Key::Esc]);
        assert_eq!(keys(b"\x1bx"), vec![Key::Alt('x')]);
    }

    #[test]
    fn split_sequence_across_reads() {
        let mut p = Parser::new();
        let mut v = Vec::new();
        p.feed(b"\x1b[<0;1", &mut v);
        assert!(v.is_empty());
        p.feed(b"0;5M", &mut v);
        assert_eq!(v.len(), 1);
        p.feed(&[0xe5, 0xad], &mut v);
        assert_eq!(v.len(), 1);
        p.feed(&[0x97], &mut v);
        assert_eq!(v[1], Event::Key(Key::Char('字')));
    }

    #[test]
    fn sgr_mouse() {
        let ev = run(
            b"\x1b[<0;10;5M\x1b[<32;11;5M\x1b[<0;11;6m\x1b[<64;1;1M\x1b[<65;1;1M\x1b[<35;2;2M\x1b[<16;3;4M",
        );
        let m: Vec<(MouseKind, u16, u16)> = ev
            .iter()
            .map(|e| match e {
                Event::Mouse(m) => (m.kind, m.col, m.row),
                _ => panic!(),
            })
            .collect();
        assert_eq!(
            m,
            vec![
                (MouseKind::Press, 9, 4),
                (MouseKind::Drag, 10, 4),
                (MouseKind::Release, 10, 5),
                (MouseKind::ScrollUp, 0, 0),
                (MouseKind::ScrollDown, 0, 0),
                (MouseKind::Move, 1, 1),
                (MouseKind::Press, 2, 3),
            ]
        );
        if let Event::Mouse(m) = ev[6] {
            assert!(m.ctrl && !m.shift);
        }
    }

    #[test]
    fn in_band_resize() {
        assert_eq!(run(b"\x1b[48;45;52;900;416t"), vec![Event::Resize(Size::new(52, 45, 8, 20))]);
        assert_eq!(run(b"\x1b[48;23;52t"), vec![Event::Resize(Size::new(52, 23, 0, 0))]);
    }

    #[test]
    fn probe_replies() {
        let ev = run(
            b"\x1b_Gi=31;OK\x1b\\\x1b[1;3R\x1b[?2026;2$y\x1b[?2048;0$y\x1b[6;20;8t\x1b]11;rgb:1a1a/2b2b/ffff\x1b\\\x1b[?62;22c",
        );
        assert_eq!(
            ev,
            vec![
                Event::Reply(Reply::Kitty { id: 31, ok: true }),
                Event::Reply(Reply::Cursor { row: 1, col: 3 }),
                Event::Reply(Reply::Mode { mode: 2026, state: 2 }),
                Event::Reply(Reply::Mode { mode: 2048, state: 0 }),
                Event::Reply(Reply::CellPx { w: 8, h: 20 }),
                Event::Reply(Reply::Background(Rgb(0x1a, 0x2b, 0xff))),
                Event::Reply(Reply::DeviceAttrs),
            ]
        );
        assert_eq!(run(b"\x1b]11;rgb:ff/00/80\x07"), vec![Event::Reply(Reply::Background(Rgb(255, 0, 128)))]);
        assert_eq!(run(b"\x1b_Gi=31;ENOENT:x\x1b\\"), vec![Event::Reply(Reply::Kitty { id: 31, ok: false })]);
    }

    #[test]
    fn unknown_sequences_are_skipped() {
        assert_eq!(keys(b"\x1b[99xq\x1bP1$r0m\x1b\\w"), vec![Key::Char('q'), Key::Char('w')]);
    }
}
