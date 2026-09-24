//! The app loop: a stack of screens, input and signals in, one diffed frame out per change,
//! and a tick at up to 60 fps while a screen animates.

use std::io;
use std::os::fd::RawFd;
use std::path::PathBuf;
use std::time::{Duration, Instant};

use crate::palette::Palette;
use crate::picture::Pictures;
use crate::render::{ActionId, Frame, HitMap, Renderer};
use crate::term::{self, Caps, Event, Key, MouseKind, Parser, Size, TermIo, Tty};

/// Frame interval while animating (60 fps).
pub const FRAME: Duration = Duration::from_micros(16_667);
/// How often a frame of a header clip is sent while one is streaming (one per tick), so the
/// upload never holds the loop for longer than one frame's write.
pub const STREAM_PACE: Duration = FRAME;
/// How long a lone ESC waits for the rest of a sequence before it counts as the Esc key.
pub const ESC_TIMEOUT: Duration = Duration::from_millis(30);

/// Shared state every screen can read and use.
pub struct Ctx {
    /// Grid and cell pixel size (never 0×0 pixels: falls back to 8×20).
    pub size: Size,
    pub caps: Caps,
    pub palette: Palette,
    pub pics: Pictures,
    /// False when motion is turned off (`TLSTORE_MOTION=0`); timelines should jump to the end.
    pub motion: bool,
    /// `$HOME`, for the palette and anything else screens read.
    pub home: PathBuf,
    /// The terminal told us its cell size in pixels (the kernel's window size or a CSI 16 t
    /// answer). When false, `size` holds a guess and pictures that must sit at exact pixel
    /// sizes (the script word) are not drawn.
    pub cell_known: bool,
}

impl Ctx {
    /// A context for unit tests: no capabilities, built-in dark palette, 8×20 px cells.
    pub fn for_tests(cols: u16, rows: u16) -> Ctx {
        Ctx {
            size: Size::new(cols, rows, 8, 20),
            caps: Caps::default(),
            palette: Palette::builtin_dark(),
            pics: Pictures::new(),
            motion: true,
            home: PathBuf::from("/nonexistent"),
            cell_known: true,
        }
    }
}

/// What a screen wants after handling an event.
pub enum Nav {
    /// Stay on this screen (it is redrawn; the diff makes an unchanged frame free).
    Stay,
    /// Open a screen on top; `Pop` returns here.
    Push(Box<dyn Screen>),
    /// Swap this screen for another.
    Replace(Box<dyn Screen>),
    /// Close this screen; closing the last one quits.
    Pop,
    Quit,
}

/// One screen of the store.
pub trait Screen {
    /// Draw the whole screen into `f` (every frame starts blank). Register tap regions with
    /// `f.hit` as you draw.
    fn draw(&mut self, f: &mut Frame);
    /// Handle one event: keys, mouse, `Tap` on a region this screen registered, `Resize`
    /// (the context already holds the new size), `Readable` for a watched fd.
    fn handle(&mut self, ev: &Event, ctx: &mut Ctx) -> Nav;
    /// True while something moves; the loop then calls [`Screen::tick`] every frame.
    fn animating(&self) -> bool {
        false
    }
    /// Advance timelines to `now`. Return true when the next frame differs.
    fn tick(&mut self, _now: Instant, _ctx: &mut Ctx) -> bool {
        false
    }
    /// File descriptors to watch (e.g. a child's stdout); each readable one arrives as
    /// `Event::Readable(fd)`. The screen reads it itself.
    fn watch(&self) -> Vec<RawFd> {
        Vec::new()
    }
}

/// Start-up options.
pub struct Options {
    pub probe_timeout: Duration,
    pub motion: bool,
}

impl Default for Options {
    fn default() -> Options {
        let motion = std::env::var("TLSTORE_MOTION").map(|v| v != "0").unwrap_or(true);
        Options { probe_timeout: term::probe::PROBE_TIMEOUT, motion }
    }
}

fn resolve_cell(mut s: Size, caps: &Caps, last: Size) -> Size {
    if s.cell_w == 0 || s.cell_h == 0 {
        let (w, h) = caps
            .cell_px
            .or(if last.cell_w > 0 { Some((last.cell_w, last.cell_h)) } else { None })
            .unwrap_or((8, 20));
        s.cell_w = w;
        s.cell_h = h;
    }
    s
}

/// Tracks a press so a release on the same region becomes a `Tap`.
#[derive(Default)]
pub struct TapTracker {
    pressed: Option<ActionId>,
}

impl TapTracker {
    /// Feed a mouse event; returns the Tap to deliver after it, if any.
    pub fn on_mouse(&mut self, m: &term::Mouse, hits: &HitMap) -> Option<Event> {
        match m.kind {
            MouseKind::Press if m.button == 0 => {
                self.pressed = hits.at(m.col, m.row);
                None
            }
            MouseKind::Release => {
                let p = self.pressed.take()?;
                (hits.at(m.col, m.row) == Some(p)).then_some(Event::Tap { action: p, col: m.col, row: m.row })
            }
            _ => None,
        }
    }
}

/// Opens the terminal, probes it, runs `first` (and whatever it navigates to) until the
/// stack empties or a quit signal/Ctrl-C arrives, then restores the terminal.
pub fn run(first: Box<dyn Screen>, opts: Options) -> io::Result<()> {
    let mut tty = Tty::open()?;
    let sig_r = term::install_signals()?;
    let mut parser = Parser::new();
    let (caps, early) = term::probe(&mut tty, &mut parser, opts.probe_timeout);
    tty.enable_modes(&caps)?;

    let home = std::env::var_os("HOME").map(PathBuf::from).unwrap_or_else(|| PathBuf::from("/"));
    let home_mode = Palette::exported_mode(&home);
    let dark = caps.dark().or(home_mode).unwrap_or(true);
    let raw = tty.size()?;
    let cell_known = (raw.cell_w > 0 && raw.cell_h > 0) || caps.cell_px.is_some();
    let size = resolve_cell(raw, &caps, Size::new(0, 0, 0, 0));
    let mut ctx = Ctx {
        size,
        caps,
        palette: Palette::load(&home, dark),
        pics: Pictures::new(),
        motion: opts.motion,
        home,
        cell_known,
    };

    let mut stack: Vec<Box<dyn Screen>> = vec![first];
    let mut renderer = Renderer::new();
    renderer.sync = true;
    let mut hits = HitMap::default();
    let mut taps = TapTracker::default();
    let mut out = String::with_capacity(64 * 1024);
    let mut pending: Vec<Event> = early;
    let mut dirty = true;
    let mut last_tick = Instant::now();
    let mut buf = [0u8; 8192];

    'main: loop {
        // Deliver queued events.
        let mut queue = std::mem::take(&mut pending);
        let mut i = 0;
        while i < queue.len() {
            let ev = queue[i];
            i += 1;
            if let Event::Resize(s) = ev {
                let s = resolve_cell(s, &ctx.caps, ctx.size);
                if s == ctx.size {
                    continue;
                }
                ctx.size = s;
                renderer.invalidate();
            }
            if let Event::Key(Key::Ctrl('c')) = ev {
                break 'main;
            }
            if let Event::Mouse(m) = &ev {
                if let Some(tap) = taps.on_mouse(m, &hits) {
                    queue.insert(i, tap);
                }
            }
            let Some(top) = stack.last_mut() else { break 'main };
            let ev = if let Event::Resize(_) = ev { Event::Resize(ctx.size) } else { ev };
            match top.handle(&ev, &mut ctx) {
                Nav::Stay => {}
                Nav::Push(s) => stack.push(s),
                Nav::Replace(s) => {
                    stack.pop();
                    stack.push(s);
                }
                Nav::Pop => {
                    stack.pop();
                    if stack.is_empty() {
                        break 'main;
                    }
                }
                Nav::Quit => break 'main,
            }
            dirty = true;
        }

        let Some(top) = stack.last_mut() else { break };
        if top.animating() {
            let now = Instant::now();
            if now.duration_since(last_tick) >= FRAME {
                last_tick = now;
                if top.tick(now, &mut ctx) {
                    dirty = true;
                }
            }
        }

        if dirty {
            let mut f = Frame::new(&mut ctx);
            top.draw(&mut f);
            let Frame { buf: cells, places, hits: new_hits, .. } = f;
            out.clear();
            renderer.render(&cells, &places, &mut out);
            tty.write_all(out.as_bytes())?;
            hits = new_hits;
            dirty = false;
        }
        // A header clip's frames go out one per pass, between events.
        if renderer.pending() {
            out.clear();
            renderer.stream(&mut out);
            if !out.is_empty() {
                tty.write_all(out.as_bytes())?;
            }
        }

        // Wait for input, a signal, a watched fd, the ESC timeout, the next frame or the next
        // clip frame to send.
        let watched = top.watch();
        let mut timeout = if parser.pending() {
            Some(ESC_TIMEOUT)
        } else if top.animating() {
            Some(FRAME.saturating_sub(last_tick.elapsed()))
        } else {
            None
        };
        if renderer.pending() {
            timeout = Some(timeout.map_or(STREAM_PACE, |t| t.min(STREAM_PACE)));
        }
        let mut fds: Vec<(RawFd, libc::c_short)> = vec![(tty.fd(), libc::POLLIN), (sig_r, libc::POLLIN)];
        fds.extend(watched.iter().map(|&fd| (fd, libc::POLLIN)));
        let ready = term::poll_fds(&fds, timeout)?;

        let sigs = term::take_signals(sig_r);
        if sigs.quit {
            break;
        }
        if sigs.resize {
            if let Ok(s) = tty.size() {
                pending.push(Event::Resize(s));
            }
        }
        if ready[0] {
            let n = tty.read_timeout(&mut buf, Duration::ZERO)?;
            if n > 0 {
                parser.feed(&buf[..n], &mut pending);
            }
        } else if parser.pending() && !ready.iter().any(|&r| r) {
            parser.flush(&mut pending);
        }
        for (k, &fd) in watched.iter().enumerate() {
            if ready[2 + k] {
                pending.push(Event::Readable(fd));
            }
        }
        // Replies arriving late (a slow probe answer) are not for screens.
        pending.retain(|e| !matches!(e, Event::Reply(_)));
    }
    drop(tty);
    Ok(())
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::render::Rect;

    fn m(kind: MouseKind, col: u16, row: u16) -> term::Mouse {
        term::Mouse { kind, button: 0, col, row, shift: false, alt: false, ctrl: false }
    }

    #[test]
    fn tap_needs_press_and_release_on_same_region() {
        let mut hits = HitMap::default();
        hits.push(Rect::new(0, 0, 5, 1), 1);
        hits.push(Rect::new(0, 1, 5, 1), 2);
        let mut t = TapTracker::default();
        assert_eq!(t.on_mouse(&m(MouseKind::Press, 1, 0), &hits), None);
        assert_eq!(
            t.on_mouse(&m(MouseKind::Release, 4, 0), &hits),
            Some(Event::Tap { action: 1, col: 4, row: 0 })
        );
        t.on_mouse(&m(MouseKind::Press, 1, 0), &hits);
        t.on_mouse(&m(MouseKind::Drag, 1, 1), &hits);
        assert_eq!(t.on_mouse(&m(MouseKind::Release, 1, 1), &hits), None);
        assert_eq!(t.on_mouse(&m(MouseKind::Release, 1, 1), &hits), None);
    }

    #[test]
    fn cell_size_falls_back() {
        let caps = Caps { cell_px: Some((9, 21)), ..Caps::default() };
        assert_eq!(
            resolve_cell(Size::new(52, 45, 0, 0), &caps, Size::new(0, 0, 0, 0)),
            Size::new(52, 45, 9, 21)
        );
        assert_eq!(
            resolve_cell(Size::new(52, 23, 0, 0), &Caps::default(), Size::new(52, 45, 8, 20)),
            Size::new(52, 23, 8, 20)
        );
        assert_eq!(
            resolve_cell(Size::new(1, 1, 0, 0), &Caps::default(), Size::new(0, 0, 0, 0)),
            Size::new(1, 1, 8, 20)
        );
        assert_eq!(resolve_cell(Size::new(1, 1, 7, 7), &caps, Size::new(0, 0, 0, 0)), Size::new(1, 1, 7, 7));
    }
}
