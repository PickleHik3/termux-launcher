//! Asks the terminal what it can do, once, at start. Every query is sent in one write and
//! answered in order; the primary device attributes query goes last and every terminal
//! answers it, so its reply ends the probe early. A terminal that answers nothing costs at
//! most the timeout.

use std::time::{Duration, Instant};

use super::input::{Event, Parser, Reply};
use super::TermIo;
use crate::render::Rgb;

/// What the terminal answered. Anything unanswered is false/None.
#[derive(Clone, Copy, Debug, Default, PartialEq, Eq)]
pub struct Caps {
    /// Kitty graphics protocol (answered the `a=q` query with OK).
    pub kitty_graphics: bool,
    /// OSC 66 text sizing (a `w=2` run moved the cursor two cells).
    pub text_sizing: bool,
    /// Synchronized output, mode 2026.
    pub sync_output: bool,
    /// In-band resize reports, mode 2048.
    pub in_band_resize: bool,
    /// Cell size in pixels, from `CSI 16 t`.
    pub cell_px: Option<(u16, u16)>,
    /// Terminal background, from OSC 11; decides light or dark.
    pub background: Option<Rgb>,
    /// The terminal answered the final device-attributes query before the timeout.
    pub answered: bool,
}

impl Caps {
    /// Every capability on; for tests and for `--demo` on a known-good terminal.
    pub fn all() -> Caps {
        Caps {
            kitty_graphics: true,
            text_sizing: true,
            sync_output: true,
            in_band_resize: true,
            cell_px: None,
            background: None,
            answered: true,
        }
    }

    /// Dark unless the terminal reported a light background.
    pub fn dark(&self) -> Option<bool> {
        self.background.map(|b| b.luma() < 0.5)
    }
}

/// Default probe budget.
pub const PROBE_TIMEOUT: Duration = Duration::from_millis(150);

/// The query batch. Starts at column 1 of the current (cleared, alternate-screen) line, so
/// the OSC 66 space either moves the cursor to column 3 or not at all; erases the line after.
pub const QUERIES: &str = concat!(
    "\x1b_Gi=31,s=1,v=1,a=q,t=d,f=24;AAAA\x1b\\", // kitty graphics
    "\r\x1b]66;w=2; \x1b\\\x1b[6n",               // text sizing: where did the cursor go
    "\r\x1b[2K",
    "\x1b[?2026$p",    // synchronized output
    "\x1b[?2048$p",    // in-band resize
    "\x1b[16t",        // cell size in pixels
    "\x1b]11;?\x1b\\", // background colour
    "\x1b[c",          // device attributes: end marker
);

/// Runs the probe. Returns the capabilities and any other events (keys typed meanwhile, an
/// early resize report) for the app to handle afterwards.
pub fn probe<T: TermIo>(io: &mut T, parser: &mut Parser, timeout: Duration) -> (Caps, Vec<Event>) {
    let mut caps = Caps::default();
    let mut other = Vec::new();
    if io.write_all(QUERIES.as_bytes()).is_err() {
        return (caps, other);
    }
    let deadline = Instant::now() + timeout;
    let mut buf = [0u8; 4096];
    let mut events = Vec::new();
    'outer: loop {
        let now = Instant::now();
        if now >= deadline {
            break;
        }
        let n = match io.read_timeout(&mut buf, deadline - now) {
            Ok(0) => continue,
            Ok(n) => n,
            Err(_) => break,
        };
        events.clear();
        parser.feed(&buf[..n], &mut events);
        for e in events.drain(..) {
            match e {
                Event::Reply(Reply::Kitty { id: 31, ok }) => caps.kitty_graphics = ok,
                Event::Reply(Reply::Cursor { col, .. }) => caps.text_sizing = col == 3,
                Event::Reply(Reply::Mode { mode, state }) => {
                    let on = matches!(state, 1..=3);
                    match mode {
                        2026 => caps.sync_output = on,
                        2048 => caps.in_band_resize = on,
                        _ => {}
                    }
                }
                Event::Reply(Reply::CellPx { w, h }) if w > 0 && h > 0 => caps.cell_px = Some((w, h)),
                Event::Reply(Reply::Background(c)) => caps.background = Some(c),
                Event::Reply(Reply::DeviceAttrs) => {
                    caps.answered = true;
                    break 'outer;
                }
                Event::Reply(_) => {}
                other_ev => other.push(other_ev),
            }
        }
    }
    (caps, other)
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::io;

    /// A terminal that swallows everything and never answers.
    struct Mute {
        written: Vec<u8>,
    }
    impl TermIo for Mute {
        fn write_all(&mut self, b: &[u8]) -> io::Result<()> {
            self.written.extend_from_slice(b);
            Ok(())
        }
        fn read_timeout(&mut self, _buf: &mut [u8], timeout: Duration) -> io::Result<usize> {
            std::thread::sleep(timeout.min(Duration::from_millis(20)));
            Ok(0)
        }
    }

    /// A terminal that answers with a canned byte string, split in two reads.
    struct Canned {
        chunks: Vec<Vec<u8>>,
    }
    impl TermIo for Canned {
        fn write_all(&mut self, _b: &[u8]) -> io::Result<()> {
            Ok(())
        }
        fn read_timeout(&mut self, buf: &mut [u8], timeout: Duration) -> io::Result<usize> {
            match self.chunks.pop() {
                Some(c) => {
                    buf[..c.len()].copy_from_slice(&c);
                    Ok(c.len())
                }
                None => {
                    std::thread::sleep(timeout);
                    Ok(0)
                }
            }
        }
    }

    #[test]
    fn silent_terminal_times_out_quickly_with_nothing() {
        let mut t = Mute { written: Vec::new() };
        let start = Instant::now();
        let (caps, other) = probe(&mut t, &mut Parser::new(), PROBE_TIMEOUT);
        let took = start.elapsed();
        assert!(took >= PROBE_TIMEOUT && took < Duration::from_millis(250), "took {took:?}");
        assert_eq!(caps, Caps::default());
        assert!(other.is_empty());
        assert_eq!(t.written, QUERIES.as_bytes());
    }

    #[test]
    fn full_answer_ends_early() {
        let answer = b"\x1b_Gi=31;OK\x1b\\\x1b[1;3R\x1b[?2026;2$y\x1b[?2048;2$y\x1b[6;20;8t\x1b]11;rgb:f0f0/f0f0/f0f0\x1b\\q\x1b[?62;c";
        let (a, b) = answer.split_at(17);
        let mut t = Canned { chunks: vec![b.to_vec(), a.to_vec()] };
        let start = Instant::now();
        let (caps, other) = probe(&mut t, &mut Parser::new(), Duration::from_secs(5));
        assert!(start.elapsed() < Duration::from_secs(1));
        assert_eq!(
            caps,
            Caps {
                kitty_graphics: true,
                text_sizing: true,
                sync_output: true,
                in_band_resize: true,
                cell_px: Some((8, 20)),
                background: Some(Rgb(0xf0, 0xf0, 0xf0)),
                answered: true,
            }
        );
        assert_eq!(caps.dark(), Some(false));
        assert_eq!(other, vec![Event::Key(crate::term::Key::Char('q'))]);
    }

    #[test]
    fn plain_terminal_answers_only_basics() {
        // Cursor stayed at column 1, modes unknown, no kitty reply.
        let mut t = Canned { chunks: vec![b"\x1b[1;1R\x1b[?2026;0$y\x1b[?1;2c".to_vec()] };
        let (caps, _) = probe(&mut t, &mut Parser::new(), PROBE_TIMEOUT);
        assert!(!caps.kitty_graphics && !caps.text_sizing && !caps.sync_output && caps.answered);
    }
}
