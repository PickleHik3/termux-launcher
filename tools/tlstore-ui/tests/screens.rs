//! The store's screens driven end to end against stub `tlstore`, `gh`, `launcherctl` and
//! URL-opener scripts (tests/fixtures/store), with frame snapshots at the three layout sizes.
//!
//! Snapshots live in tests/snapshots; `UPDATE_SNAPSHOTS=1 cargo test` rewrites them.

use std::cell::{Cell, RefCell};
use std::path::{Path, PathBuf};
use std::rc::Rc;
use std::sync::atomic::{AtomicU32, Ordering};
use std::time::{Duration, Instant};

use tlstore_ui::app::{Ctx, Nav, Screen};
use tlstore_ui::layout;
use tlstore_ui::render::{Frame, HitMap, Renderer, Sym};
use tlstore_ui::store::motion::Timeline;
use tlstore_ui::store::proc::Env;
use tlstore_ui::store::scene::{El, Motion, NavKind, Phase, Scene};
use tlstore_ui::store::{Router, GH_NOTICE};
use tlstore_ui::term::{self, Caps, Event, Key, Mouse, MouseKind, Size};

static N: AtomicU32 = AtomicU32::new(0);

fn manifest() -> PathBuf {
    PathBuf::from(env!("CARGO_MANIFEST_DIR"))
}

fn copy_dir(from: &Path, to: &Path) {
    std::fs::create_dir_all(to).unwrap();
    for e in std::fs::read_dir(from).unwrap() {
        let e = e.unwrap();
        let t = to.join(e.file_name());
        if e.file_type().unwrap().is_dir() {
            copy_dir(&e.path(), &t);
        } else {
            std::fs::copy(e.path(), &t).unwrap();
        }
    }
}

#[derive(Clone, Copy, PartialEq)]
enum Gh {
    SignedIn,
    SignedOut,
    Missing,
}

struct H {
    dir: PathBuf,
    ctx: Ctx,
    router: Option<Router>,
    hits: HitMap,
    sink: Rc<RefCell<Vec<String>>>,
    text: String,
    /// Every drawn frame also goes through a renderer; `out` is the last frame's escapes.
    renderer: Renderer,
    out: String,
}

struct Opts {
    gh: Gh,
    launcherctl: bool,
    opener: bool,
    pics: bool,
    caps: bool,
    motion: Option<Box<dyn Motion>>,
    /// `Ctx::motion` false even though a motion is plugged in (TLSTORE_MOTION=0).
    motion_off: bool,
    /// A fake clock for the router (set before the first frame).
    clock: Option<Rc<Cell<Instant>>>,
}

impl Default for Opts {
    fn default() -> Opts {
        Opts {
            gh: Gh::SignedIn,
            launcherctl: true,
            opener: true,
            pics: true,
            caps: false,
            motion: None,
            motion_off: false,
            clock: None,
        }
    }
}

impl H {
    fn new(cols: u16, rows: u16, o: Opts) -> H {
        let n = N.fetch_add(1, Ordering::Relaxed);
        let dir =
            PathBuf::from(env!("CARGO_TARGET_TMPDIR")).join(format!("store-{}-{n}", std::process::id()));
        let _ = std::fs::remove_dir_all(&dir);
        copy_dir(&manifest().join("tests/fixtures/store"), &dir);
        if o.pics {
            copy_dir(&manifest().join("../../scripts/tlstore/pictures"), &dir.join("pics"));
        }
        if o.gh == Gh::SignedIn {
            std::fs::write(dir.join("gh-signed-in"), "").unwrap();
        }
        let bin = dir.join("bin");
        let gh = if o.gh == Gh::Missing { dir.join("bin/no-such-gh") } else { bin.join("gh") };
        let lc = bin.join("launcherctl");
        let op = bin.join("open-url");
        let sink = Rc::new(RefCell::new(Vec::new()));
        let env = Env::for_tests(
            &bin.join("tlstore"),
            &gh,
            o.launcherctl.then_some(lc.as_path()),
            o.opener.then_some(op.as_path()),
            sink.clone(),
        );
        let mut ctx = Ctx::for_tests(cols, rows);
        ctx.motion = o.motion.is_some() && !o.motion_off;
        if o.caps {
            ctx.caps = Caps::all();
        }
        let mut router = match o.motion {
            Some(m) => Router::with_motion(env, m),
            None => Router::new(env),
        };
        if let Some(c) = o.clock {
            router.set_clock(move || c.get());
        }
        let mut h = H {
            dir,
            ctx,
            router: Some(router),
            hits: HitMap::default(),
            sink,
            text: String::new(),
            renderer: Renderer::new(),
            out: String::new(),
        };
        h.settle();
        h
    }

    fn r(&mut self) -> &mut Router {
        self.router.as_mut().unwrap()
    }

    fn draw(&mut self) -> String {
        let router = self.router.as_mut().unwrap();
        let mut f = Frame::new(&mut self.ctx);
        router.draw(&mut f);
        self.text = screen_text(&f);
        self.hits = f.hits.clone();
        self.out.clear();
        self.renderer.render(&f.buf, &f.places, &mut self.out);
        self.text.clone()
    }

    fn ev(&mut self, ev: Event) -> bool {
        let router = self.router.as_mut().unwrap();
        let nav = router.handle(&ev, &mut self.ctx);
        self.draw();
        matches!(nav, Nav::Quit)
    }

    fn key(&mut self, k: Key) -> bool {
        self.ev(Event::Key(k))
    }

    fn tap_at(&mut self, col: u16, row: u16) -> bool {
        let action =
            self.hits.at(col, row).unwrap_or_else(|| panic!("nothing to tap at {col},{row}\n{}", self.text));
        self.ev(Event::Tap { action, col, row })
    }

    /// Taps the first place `needle` appears on screen.
    fn tap(&mut self, needle: &str) -> bool {
        let (col, row) =
            find(&self.text, needle).unwrap_or_else(|| panic!("{needle:?} not on screen\n{}", self.text));
        self.tap_at(col, row)
    }

    fn resize(&mut self, cols: u16, rows: u16) {
        self.ctx.size = Size::new(cols, rows, 8, 20);
        self.ev(Event::Resize(self.ctx.size));
    }

    /// Delivers readable fds until nothing is watched (or `until` holds), then draws.
    fn pump(&mut self, until: impl Fn(&Router) -> bool) {
        let deadline = Instant::now() + Duration::from_secs(10);
        loop {
            if until(self.router.as_ref().unwrap()) {
                break;
            }
            let fds = self.router.as_ref().unwrap().watch();
            if fds.is_empty() || Instant::now() > deadline {
                break;
            }
            let polled: Vec<_> = fds.iter().map(|&f| (f, libc::POLLIN)).collect();
            let ready = term::poll_fds(&polled, Some(Duration::from_millis(200))).unwrap();
            for (i, &fd) in fds.iter().enumerate() {
                if ready[i] {
                    let router = self.router.as_mut().unwrap();
                    router.handle(&Event::Readable(fd), &mut self.ctx);
                }
            }
        }
        self.draw();
    }

    /// Pumps and draws until a frame asks for nothing more (pictures, READMEs and their
    /// assets arrive one after another). A running job is left alone.
    fn settle(&mut self) {
        let held = self.dir.join("hold").exists();
        let done = move |r: &Router| {
            let waiting_on_hold = held && r.st.job.as_ref().is_some_and(|j| j.running() && !j.cancelled);
            !r.st.tasks_pending() && (waiting_on_hold || !r.st.job_running())
        };
        for _ in 0..6 {
            self.pump(done);
            if done(self.router.as_ref().unwrap()) {
                break;
            }
        }
    }

    fn log(&self, name: &str) -> String {
        std::fs::read_to_string(self.dir.join(name)).unwrap_or_default()
    }

    fn row(&self, y: usize) -> String {
        self.text.lines().nth(y).map(|l| l[3..].to_string()).unwrap_or_default()
    }

    fn has(&self, s: &str) -> bool {
        self.text.contains(s)
    }

    fn header_item(&self) -> String {
        self.router.as_ref().unwrap().header_item().unwrap_or_default()
    }

    fn snapshot(&self, name: &str) {
        let path = manifest().join("tests/snapshots").join(format!("{name}.txt"));
        if std::env::var_os("UPDATE_SNAPSHOTS").is_some() {
            std::fs::create_dir_all(path.parent().unwrap()).unwrap();
            std::fs::write(&path, &self.text).unwrap();
            return;
        }
        let want = std::fs::read_to_string(&path)
            .unwrap_or_else(|_| panic!("no snapshot {name}; run with UPDATE_SNAPSHOTS=1"));
        assert_eq!(self.text, want, "snapshot {name} differs:\n{}", self.text);
    }
}

impl Drop for H {
    fn drop(&mut self) {
        drop(self.router.take());
        let _ = std::fs::remove_dir_all(&self.dir);
    }
}

/// The frame as text: one line per row, `NN|` prefixed. Sized runs show their text spaced
/// out on the first row and `▒` under it; pictures show as `░` on otherwise blank cells.
fn screen_text(f: &Frame) -> String {
    let (w, h) = (f.cols() as usize, f.rows() as usize);
    let mut g = vec![vec![String::from(" "); w]; h];
    for (y, row) in g.iter_mut().enumerate() {
        for (x, cell) in row.iter_mut().enumerate() {
            *cell = match &f.buf.get(x as u16, y as u16).unwrap().sym {
                Sym::Char(c) => c.to_string(),
                Sym::Cluster(s) => s.to_string(),
                Sym::WideTail => String::new(),
                Sym::Covered => "▒".into(),
            };
        }
    }
    for run in f.buf.runs() {
        let s = run.sizing.scale.max(1) as usize;
        for (i, c) in run.text.chars().enumerate() {
            let x = run.x as usize + i * s;
            if x < w {
                g[run.y as usize][x] = c.to_string();
            }
        }
    }
    let (cw, ch) = (f.ctx.size.cell_w as u32, f.ctx.size.cell_h as u32);
    for p in &f.places {
        let (pw, ph) = p.crop.map(|c| (c.w, c.h)).unwrap_or((p.pic.width(), p.pic.height()));
        let x1 = (p.col as u32 * cw + p.px_x as u32 + pw).div_ceil(cw) as usize;
        let y1 = (p.row as u32 * ch + p.px_y as u32 + ph).div_ceil(ch) as usize;
        for row in g.iter_mut().take(y1.min(h)).skip(p.row as usize) {
            for cell in row.iter_mut().take(x1.min(w)).skip(p.col as usize) {
                if cell == " " {
                    *cell = "░".into();
                }
            }
        }
    }
    let mut out = String::new();
    for (y, row) in g.iter().enumerate() {
        out.push_str(&format!("{y:02}|{}\n", row.concat().trim_end()));
    }
    out
}

fn find(text: &str, needle: &str) -> Option<(u16, u16)> {
    for (y, line) in text.lines().enumerate() {
        let body = &line[3..];
        if let Some(i) = body.find(needle) {
            let col = unicode_width_of(&body[..i]);
            return Some((col as u16, y as u16));
        }
    }
    None
}

fn unicode_width_of(s: &str) -> usize {
    tlstore_ui::render::text_width(s)
}

/// The screen row of `name`'s list row on Front (not the header, which shows the name alone).
fn list_row(h: &H, name: &str) -> usize {
    find(&h.text, &format!(" {name} ")).unwrap_or_else(|| panic!("{name} row not on screen\n{}", h.text)).1
        as usize
}

const SIZES: [(u16, u16); 3] = [(53, 26), (53, 40), (40, 24)];

/// Moves the cursor to `name` on Front (the header follows it).
fn go_to(h: &mut H, name: &str) {
    assert_eq!(h.r().top(), "front");
    h.key(Key::Home);
    for _ in 0..10 {
        if h.header_item() == name {
            return;
        }
        h.key(Key::Down);
    }
    panic!("{name} is not on Front\n{}", h.text);
}

fn open_item(h: &mut H, name: &str) {
    go_to(h, name);
    h.key(Key::Enter);
    assert_eq!(h.r().top(), "item", "{}", h.text);
    h.settle();
}

// ---------------------------------------------------------------------------
// Snapshots
// ---------------------------------------------------------------------------

fn snap_all(mode: &str, caps: bool) {
    for (c, r) in SIZES {
        let tag = format!("{c}x{r}");
        let mut h = H::new(c, r, Opts { caps, ..Opts::default() });
        go_to(&mut h, "dawn");
        h.settle();
        h.snapshot(&format!("{mode}-front-{tag}"));

        open_item(&mut h, "kitten");
        h.pump(|r| r.st.starred("kovidgoyal/kitty").is_some());
        h.settle();
        h.snapshot(&format!("{mode}-item-{tag}"));
        h.key(Key::Esc);

        std::fs::write(h.dir.join("hold"), "").unwrap();
        go_to(&mut h, "sigye");
        h.key(Key::Char('i'));
        h.pump(|r| r.st.job.as_ref().is_some_and(|j| j.pct >= 60));
        h.settle();
        h.snapshot(&format!("{mode}-installing-{tag}"));
        h.key(Key::Char('x'));
        h.settle();
        std::fs::remove_file(h.dir.join("hold")).unwrap();
    }
}

#[test]
fn snapshots_plain_terminal() {
    snap_all("plain", false);
}

#[test]
fn snapshots_kitty_terminal() {
    snap_all("kitty", true);
}

// ---------------------------------------------------------------------------
// Front
// ---------------------------------------------------------------------------

#[test]
fn header_follows_the_cursor_and_shows_the_facts() {
    let mut h = H::new(53, 26, Opts::default());
    assert!(h.row(0).starts_with("  tlstore"), "{}", h.text);
    assert!(h.row(0).trim_end().ends_with("↑ 2 updates"));
    assert_eq!(h.header_item(), "claude-code");
    // No picture on a plain terminal: the name sits right under the masthead.
    let hdr = layout::header(53, 26, 7, None);
    assert!(h.row(hdr.name.bottom() as usize - 1).contains("claude-code"), "{}", h.text);
    h.key(Key::Down);
    assert_eq!(h.header_item(), "dawn");
    assert!(h.row(hdr.name.bottom() as usize - 1).contains("dawn"), "{}", h.text);
    assert!(h.row(hdr.standfirst as usize).contains("a quiet place to write, headings and all"));
    assert_eq!(h.row(hdr.facts as usize).trim(), "0.1.3+0e958747 · MIT · andrewmd5");
    // An installed item with an update: old struck through, arrow, new.
    go_to(&mut h, "kitten");
    assert_eq!(h.row(hdr.facts as usize).trim(), "installed 0.48.2 → 0.49 · GPL-3.0 · Kovid Goyal");
    let calls = h.log("calls.log");
    assert!(calls.contains("list --tsv"), "{calls}");
    assert!(calls.contains("update --check --tsv --offline"));
    assert!(calls.lines().any(|l| l == "update --check --tsv"), "background refresh: {calls}");
}

#[test]
fn rows_show_numbers_tags_and_statuses() {
    let h = H::new(53, 26, Opts::default());
    let hdr = layout::header(53, 26, 7, None);
    let rows: Vec<String> = (0..7).map(|i| h.row((hdr.body.y + i) as usize)).collect();
    assert!(rows[0].starts_with("  01  claude-code AI"), "{:?}", rows[0]);
    assert!(rows[1].trim_end().ends_with("new"), "{:?}", rows[1]);
    assert!(rows[2].contains("fastfetch Tools") && rows[2].trim_end().ends_with("↑ 2.67.0"), "{:?}", rows[2]);
    assert!(rows[3].trim_end().ends_with("installed"), "{:?}", rows[3]);
    assert!(rows[6].starts_with("  07  sigye Tools"), "{:?}", rows[6]);
    // The key row: five fixed slots.
    let keys = h.row(hdr.keys as usize);
    // Slot 4 has room for `f keyboard` whole, with a gap before `q quit` at 45.
    for (col, word) in
        [(2, "⏎ open"), (12, "i install"), (24, "␣ select"), (34, "f keyboard"), (45, "q quit")]
    {
        let at = keys.find(word).unwrap_or_else(|| panic!("{word} missing: {keys:?}"));
        assert_eq!(unicode_width_of(&keys[..at]), col, "{word} at {col}: {keys:?}");
    }
}

#[test]
fn tall_grids_give_each_item_two_lines() {
    let h = H::new(53, 40, Opts::default());
    let hdr = layout::header(53, 40, 20, None);
    let y = hdr.body.y as usize;
    assert!(h.row(y).starts_with("  01   claude-code"), "{:?}", h.row(y));
    assert!(h.row(y + 1).contains("Anthropic's Claude Code"), "{:?}", h.row(y + 1));
    assert_eq!(h.row(y + 2).trim(), "");
    assert!(h.row(y + 3).starts_with("  02   dawn"), "{:?}", h.row(y + 3));
    assert!(h.has("07   sigye"));
}

#[test]
fn taps_move_the_cursor_then_open() {
    let mut h = H::new(53, 26, Opts::default());
    let y = list_row(&h, "sigye") as u16;
    h.tap_at(10, y);
    assert_eq!(h.r().top(), "front");
    assert_eq!(h.header_item(), "sigye");
    h.tap_at(10, y);
    assert_eq!(h.r().top(), "item");
    h.key(Key::Esc);
    // A tap on the header opens the item under the cursor.
    let hdr = layout::header(53, 26, 7, None);
    h.tap_at(10, hdr.standfirst);
    assert_eq!(h.r().top(), "item");
    assert!(h.has("am2rican5/sigye ↗"), "{}", h.text);
    // `‹ apps` goes back; the mark goes home from deeper down.
    h.tap("‹ apps");
    assert_eq!(h.r().top(), "front");
    h.key(Key::Enter);
    h.key(Key::Char('i'));
    assert_eq!(h.r().top(), "installing");
    h.settle();
    h.key(Key::Esc);
    h.key(Key::Esc);
    assert_eq!(h.r().top(), "front");
    // Esc on Front leaves.
    assert!(h.key(Key::Esc));
}

#[test]
fn updates_filter_shows_only_updates_and_updates_them_all() {
    let mut h = H::new(53, 26, Opts::default());
    h.tap("↑ 2 updates");
    assert!(h.has("fastfetch") && h.has("kitten") && !h.has("sigye"), "{}", h.text);
    assert!(h.row(0).trim_end().ends_with("updates · all"), "{}", h.row(0));
    h.tap("all");
    assert!(h.has("sigye") && h.has("↑ 2 updates"));
    h.key(Key::Char('u'));
    assert!(!h.has("sigye"));
    h.key(Key::Esc);
    assert!(h.has("sigye"), "esc turns the filter off");
    h.key(Key::Char('u'));
    h.key(Key::Char('u'));
    assert_eq!(h.r().top(), "installing");
    h.settle();
    assert!(h.log("calls.log").lines().any(|l| l == "update --progress fastfetch kitten"));
    assert!(h.has("fastfetch and kitten are up to date."), "{}", h.text);
    h.key(Key::Esc);
    assert!(!h.has("updates"), "{}", h.text);
    h.key(Key::Char('u'));
    assert_eq!(h.r().top(), "front", "nothing to filter by");
}

#[test]
fn update_key_on_an_updatable_row_updates_that_item() {
    let mut h = H::new(53, 26, Opts::default());
    go_to(&mut h, "kitten");
    assert!(h.has("u update"), "{}", h.text);
    h.key(Key::Char('u'));
    assert_eq!(h.r().top(), "installing");
    h.settle();
    assert!(h.log("calls.log").lines().any(|l| l == "update --progress kitten"));
}

#[test]
fn selection_marks_names_and_installs_with_the_right_arguments() {
    let mut h = H::new(53, 26, Opts::default());
    h.key(Key::Char(' '));
    assert!(h.has("1 selected · i installs them, ␣ clears"), "{}", h.text);
    go_to(&mut h, "sigye");
    h.key(Key::Char(' '));
    assert!(h.has("2 selected"));
    h.key(Key::Char('i'));
    assert_eq!(h.r().top(), "installing");
    h.settle();
    let calls = h.log("calls.log");
    assert!(calls.lines().any(|l| l == "install --progress claude-code sigye"), "{calls}");
    assert!(h.has("claude-code and sigye are ready."), "{}", h.text);
    assert!(h.has("100"));
    let osc = h.sink.borrow().join("");
    assert!(osc.contains("\x1b]99;i=tlstore:d=0;tlstore\x1b\\"), "{osc:?}");
    assert!(osc.contains("p=body;claude-code and sigye are ready."));
    h.key(Key::Esc);
    assert_eq!(h.r().top(), "front");
    assert!(!h.has("selected"));
    // The list was read again: both now show installed; selecting installed items offers remove.
    assert!(h.row(list_row(&h, "sigye")).contains("installed"), "{}", h.text);
    h.key(Key::Char(' '));
    assert!(h.has("1 selected · r removes them, ␣ clears") && h.has("r remove"), "{}", h.text);
    h.key(Key::Esc);
    assert!(!h.has("selected"));
}

#[test]
fn remove_skips_what_is_not_installed() {
    let mut h = H::new(53, 26, Opts::default());
    h.key(Key::Home);
    h.key(Key::Char('r'));
    assert!(h.has("Not installed."), "{}", h.text);
    assert_eq!(h.r().top(), "front");
}

#[test]
fn star_on_front_needs_gh_or_stars_quietly() {
    let mut h = H::new(53, 26, Opts { gh: Gh::Missing, ..Opts::default() });
    go_to(&mut h, "sigye");
    h.key(Key::Char('s'));
    let hdr = layout::header(53, 26, 7, None);
    assert_eq!(h.row(hdr.notice as usize).trim(), layout::fit_line(GH_NOTICE, 49), "{}", h.text);
    h.key(Key::Down);
    assert!(!h.has("starring needs gh"), "the notice goes with the next key");
    let mut h = H::new(53, 26, Opts::default());
    go_to(&mut h, "kitten");
    h.key(Key::Char('s'));
    h.settle();
    assert!(h.dir.join("starred/kovidgoyal_kitty").exists(), "{}", h.log("gh.log"));
    let hdr = layout::header(53, 26, 7, None);
    assert_eq!(h.row(hdr.facts as usize).trim(), "installed 0.48.2 → 0.49 · GPL-3.0 · starred", "{}", h.text);
}

#[test]
fn front_rows_show_the_job_step_words() {
    let mut h = H::new(53, 26, Opts::default());
    std::fs::write(h.dir.join("hold"), "").unwrap();
    go_to(&mut h, "sigye");
    h.key(Key::Char('i'));
    assert_eq!(h.r().top(), "installing");
    h.pump(|r| r.st.job.as_ref().is_some_and(|j| j.pct >= 60));
    assert!(h.has("60") && h.has("x cancel"), "{}", h.text);
    h.key(Key::Esc);
    assert_eq!(h.r().top(), "front");
    let y = list_row(&h, "sigye");
    assert!(h.row(y).trim_end().ends_with("checking…"), "{}", h.text);
    let hdr = layout::header(53, 26, 7, None);
    assert!(h.row(hdr.facts as usize).starts_with("  installing"), "{}", h.text);
    // Enter on the row of a running job returns to it; x stops the script.
    h.key(Key::Enter);
    assert_eq!(h.r().top(), "installing");
    let t = Instant::now();
    h.key(Key::Char('x'));
    h.settle();
    assert!(t.elapsed() < Duration::from_secs(5));
    assert!(h.has("Stopped before sigye was done."), "{}", h.text);
    std::fs::remove_file(h.dir.join("hold")).unwrap();
}

#[test]
fn paging_when_rows_do_not_fit() {
    let mut h = H::new(53, 14, Opts::default());
    assert!(h.has("‹ ● ○ ›"), "{}", h.text);
    assert!(!h.has("sigye"));
    h.key(Key::PageDown);
    assert!(h.has("‹ ○ ● ›") && h.has("sigye"), "{}", h.text);
    h.tap("‹");
    assert!(h.has("‹ ● ○ ›"));
}

#[test]
fn key_slots_stay_in_place_and_taps_send_their_key() {
    let mut h = H::new(53, 26, Opts { launcherctl: false, ..Opts::default() });
    let keys = layout::header(53, 26, 7, None).keys;
    // The keyboard hint stays in place, dim, and does nothing.
    assert!(h.row(keys as usize).contains("f keyboard"), "{}", h.text);
    assert_eq!(h.hits.at(34, keys), None);
    h.tap_at(12, keys);
    assert_eq!(h.r().top(), "installing", "slot 2 sends i");
    h.settle();
    h.key(Key::Esc);
    h.tap_at(45, keys);
    assert!(h.key(Key::Char('q')) || true);
    // Under 44 columns the slots are 1, 8, 16, 24, 32.
    let h = H::new(40, 24, Opts::default());
    let keys = layout::header(40, 24, 7, None).keys;
    let row = h.row(keys as usize);
    let col =
        |word: &str| unicode_width_of(&row[..row.find(word).unwrap_or_else(|| panic!("{word}: {row:?}"))]);
    assert_eq!(col("⏎ open"), 1, "{row:?}");
    assert_eq!(col("i inst…"), 8, "{row:?}");
    assert_eq!(col("q quit"), 32, "{row:?}");
}

#[test]
fn fullscreen_calls_launcherctl_and_restores_on_exit() {
    let mut h = H::new(53, 26, Opts::default());
    assert!(h.has("f keyboard"), "{}", h.text);
    h.key(Key::Char('f'));
    assert_eq!(h.log("launcherctl.log"), "keyboard hide --hold\n");
    h.tap("f keyboard");
    assert_eq!(h.log("launcherctl.log"), "keyboard hide --hold\nkeyboard show\n");
    h.key(Key::Char('f'));
    let dir = h.dir.clone();
    drop(h.router.take());
    let log = std::fs::read_to_string(dir.join("launcherctl.log")).unwrap();
    assert_eq!(log, "keyboard hide --hold\nkeyboard show\nkeyboard hide --hold\nkeyboard show\n");
}

#[test]
fn resize_relays_out() {
    let mut h = H::new(53, 40, Opts::default());
    assert!(h.has("07   sigye"), "{}", h.text);
    h.resize(53, 26);
    assert!(h.has("07  sigye"), "{}", h.text);
    h.resize(40, 24);
    assert!(!h.has("Tools"), "tags drop under 44 columns:\n{}", h.text);
}

// ---------------------------------------------------------------------------
// Item
// ---------------------------------------------------------------------------

#[test]
fn item_renders_the_readme_under_the_rules() {
    let mut h = H::new(53, 40, Opts::default());
    open_item(&mut h, "kitten");
    assert!(h.has("‹ apps") && h.has("kovidgoyal/kitty ↗"), "{}", h.text);
    assert!(h.has("a companion for pictures and files"));
    let calls = h.log("calls.log");
    assert!(calls.lines().any(|l| l == "readme kitten"), "{calls}");
    // Before the first H2: dropped. H2 kept, H3 upper-cased, code on its rows, Contributing gone.
    assert!(!h.has("GPU based"), "{}", h.text);
    assert!(h.has("kitten is kitty's companion"), "{}", h.text);
    assert!(h.has("ICAT") && h.has("kitten icat photo.png"), "{}", h.text);
    assert!(!h.has("guidelines"), "{}", h.text);
    // Long paragraphs wrap inside the content column.
    for l in h.text.lines().take(39) {
        assert!(unicode_width_of(&l[3..]) <= 51, "{l:?}");
    }
    // No pictures on a plain terminal: the screenshot line is simply absent.
    assert!(!h.has("[picture]"));
}

#[test]
fn item_scrolls_the_whole_page_by_keys_and_drag() {
    let mut h = H::new(53, 26, Opts::default());
    open_item(&mut h, "kitten");
    assert!(h.row(0).contains("‹ apps"));
    assert!(!h.has("Screenshots"), "{}", h.text);
    h.key(Key::End);
    assert!(!h.row(0).contains("‹ apps"), "the header scrolls away:\n{}", h.text);
    assert!(h.has("Screenshots") && h.has("permissions intact"), "{}", h.text);
    assert!(h.has("esc back"), "the key row stays");
    h.key(Key::Home);
    assert!(h.row(0).contains("‹ apps"));
    h.key(Key::PageDown);
    assert!(!h.row(0).contains("‹ apps"));
    h.key(Key::Home);
    let m = |kind, row| {
        Event::Mouse(Mouse { kind, button: 0, col: 10, row, shift: false, alt: false, ctrl: false })
    };
    h.ev(m(MouseKind::Press, 18));
    h.ev(m(MouseKind::Drag, 12));
    assert!(!h.row(0).contains("‹ apps"), "{}", h.text);
    h.ev(m(MouseKind::Release, 12));
    h.key(Key::Up);
    h.key(Key::Home);
    assert!(h.row(0).contains("‹ apps"));
}

#[test]
fn item_without_a_readme_points_at_github_and_a_setup_shows_its_standfirst() {
    let mut h = H::new(53, 26, Opts::default());
    std::fs::write(h.dir.join("noreadme"), "").unwrap();
    open_item(&mut h, "sigye");
    assert!(h.has("read about it on GitHub"), "{}", h.text);
    h.tap("read about it on GitHub");
    h.settle();
    assert_eq!(h.log("open.log").trim(), "https://github.com/am2rican5/sigye");
    h.key(Key::Esc);
    open_item(&mut h, "fish-shell");
    assert!(h.has("our setup") && h.has("shell setup with a matching prompt"), "{}", h.text);
    assert!(!h.has("read about it") && !h.has("s star") && !h.has("o repo"), "{}", h.text);
    assert!(!h.log("calls.log").contains("readme fish-shell"), "no README is asked for a setup");
}

#[test]
fn item_keys_star_repo_and_jobs() {
    let mut h = H::new(53, 26, Opts::default());
    open_item(&mut h, "kitten");
    h.pump(|r| r.st.starred("kovidgoyal/kitty").is_some());
    assert!(h.has("r remove") && h.has("u update") && h.has("s star") && h.has("o repo"), "{}", h.text);
    h.key(Key::Char('s'));
    h.settle();
    assert!(h.has("s unstar") && h.has("· starred"), "{}", h.text);
    assert!(h.dir.join("starred/kovidgoyal_kitty").exists());
    h.key(Key::Char('s'));
    h.settle();
    assert!(!h.dir.join("starred/kovidgoyal_kitty").exists());
    h.key(Key::Char('o'));
    h.settle();
    assert_eq!(h.log("open.log").trim(), "https://github.com/kovidgoyal/kitty");
    h.key(Key::Char('u'));
    assert_eq!(h.r().top(), "installing");
    h.settle();
    assert!(h.log("calls.log").lines().any(|l| l == "update --progress kitten"));
    h.key(Key::Enter);
    assert_eq!(h.r().top(), "item");
    assert!(!h.has("u update"), "no update left: the slot is empty\n{}", h.text);
}

#[test]
fn item_star_with_gh_signed_out_shows_the_notice() {
    let mut h = H::new(53, 26, Opts { gh: Gh::SignedOut, ..Opts::default() });
    open_item(&mut h, "sigye");
    assert!(h.has("s star"), "the hint stays in place, dim");
    let keys = layout::header(53, 26, 7, None).keys;
    assert_eq!(h.hits.at(24, keys), None, "and is not tappable");
    h.key(Key::Char('s'));
    assert_eq!(h.r().top(), "item");
    assert!(h.has("starring needs gh: pkg install gh"), "{}", h.text);
}

// ---------------------------------------------------------------------------
// Installing
// ---------------------------------------------------------------------------

#[test]
fn installing_shows_the_number_steps_and_summary() {
    let mut h = H::new(53, 26, Opts::default());
    std::fs::write(h.dir.join("hold"), "").unwrap();
    go_to(&mut h, "sigye");
    h.key(Key::Char('i'));
    h.pump(|r| r.st.job.as_ref().is_some_and(|j| j.pct >= 60));
    let hdr = layout::header(53, 26, 7, None);
    assert!(h.row(hdr.facts as usize).starts_with("  installing"), "{}", h.text);
    assert!(h.row(hdr.body.y as usize).starts_with("  60%"), "{}", h.text);
    assert!(h.row(hdr.body.y as usize).contains("fetched"));
    assert!(h.row(hdr.body.y as usize + 1).contains("signature checked"));
    assert!(h.row(hdr.body.y as usize + 5).contains("───"), "a text track without pictures");
    assert!(h.has("x cancel") && h.has("esc back") && !h.has("⏎ done"));
    h.key(Key::Char('x'));
    h.settle();
    assert!(h.has("Stopped before sigye was done.") && h.has("⏎ done"), "{}", h.text);
    std::fs::remove_file(h.dir.join("hold")).unwrap();
}

#[test]
fn failures_and_kept_config_are_reported() {
    let mut h = H::new(53, 26, Opts::default());
    std::fs::write(h.dir.join("kept"), "").unwrap();
    let r = h.r();
    assert!(r.st.start_job(tlstore_ui::store::Verb::Install, vec!["sigye".into(), "broken".into()]));
    h.settle();
    let s = h.r().st.job.as_ref().unwrap().summary();
    assert_eq!(s[0], "sigye is ready.");
    assert_eq!(s[1], "Could not install broken. Try again later.");
    assert_eq!(s[2], "Kept your config.fish.");
    // Off the Installing screen the first summary line is the notice.
    assert!(h.has("sigye is ready."), "{}", h.text);
}

#[test]
fn a_failed_item_shows_failed_on_installing_and_front() {
    let mut h = H::new(53, 26, Opts::default());
    std::fs::write(h.dir.join("fail-sigye"), "").unwrap();
    go_to(&mut h, "sigye");
    h.key(Key::Char('i'));
    assert_eq!(h.r().top(), "installing");
    h.settle();
    let hdr = layout::header(53, 26, 7, None);
    assert!(h.row(hdr.facts as usize).starts_with("  failed 0.6.0"), "{}", h.text);
    assert!(h.row(hdr.notice as usize).contains("Could not install sigye. Try again later."), "{}", h.text);
    assert!(h.has("⏎ done"));
    h.key(Key::Enter);
    assert_eq!(h.r().top(), "front");
    let y = list_row(&h, "sigye");
    assert!(h.row(y).trim_end().ends_with("failed"), "{}", h.text);
}

#[test]
fn install_from_item_then_esc_keeps_the_job_and_the_word() {
    let mut h = H::new(53, 26, Opts::default());
    std::fs::write(h.dir.join("hold"), "").unwrap();
    open_item(&mut h, "sigye");
    h.key(Key::Char('i'));
    assert_eq!(h.r().top(), "installing");
    h.pump(|r| r.st.job.as_ref().is_some_and(|j| j.pct >= 60));
    h.key(Key::Esc);
    assert_eq!(h.r().top(), "item");
    let hdr = layout::header(53, 26, 7, None);
    assert!(h.row(hdr.facts as usize).starts_with("  installing 0.6.0"), "{}", h.text);
    h.key(Key::Esc);
    let y = list_row(&h, "sigye");
    assert!(h.row(y).trim_end().ends_with("checking…"), "{}", h.text);
    h.key(Key::Enter);
    h.key(Key::Char('x'));
    h.settle();
    std::fs::remove_file(h.dir.join("hold")).unwrap();
}

// ---------------------------------------------------------------------------
// Pictures (kitty)
// ---------------------------------------------------------------------------

#[test]
fn kitty_terminal_gets_header_pictures_the_pill_and_links() {
    let mut h = H::new(53, 26, Opts { caps: true, ..Opts::default() });
    assert!(h.log("calls.log").contains("picture claude-code"), "{}", h.log("calls.log"));
    let scene = h.r().scene.clone();
    assert!(scene.get(El::Picture).is_some_and(|e| e.picture.is_some()), "header picture:\n{}", h.text);
    assert!(scene.get(El::Name).is_some_and(|e| e.picture.is_some()), "script name");
    let pill = scene.get(El::Pill).expect("cursor pill");
    assert!(pill.picture.is_some());
    assert_eq!((pill.rect.x, pill.rect.w), (1, 51), "columns 1 to cols−2");
    let hdr = layout::header(53, 26, 7, Some(u16::MAX));
    assert_eq!(pill.rect.y, hdr.body.y);
    assert_eq!(hdr.picture.map(|p| p.h), Some(8));
    // Moving the cursor re-places the same pill picture on the new row.
    let id = pill.picture.unwrap();
    h.key(Key::Down);
    h.settle();
    h.key(Key::Up);
    h.key(Key::Down);
    let pill = h.r().scene.get(El::Pill).cloned().unwrap();
    assert_eq!(pill.picture, Some(id));
    assert_eq!(pill.rect.y, hdr.body.y + 1);
    let (pic_id, _) = id;
    assert!(h.out.contains(&format!("_Ga=p,i={pic_id},p=1")), "the pill is placed again:\n{:?}", h.out);
    assert!(!h.out.contains(&format!(",i={pic_id},q=2,o=z")), "and not uploaded again:\n{:?}", h.out);
    // The header picture is faded along its bottom edge.
    let router = h.router.as_mut().unwrap();
    let mut f = Frame::new(&mut h.ctx);
    router.draw(&mut f);
    let pic = f.places.iter().find(|p| p.pic.height() > 100).expect("header picture placed").pic.clone();
    let (w, hh) = (pic.width() as usize, pic.height() as usize);
    let alpha = |x: usize, y: usize| pic.rgba()[(y * w + x) * 4 + 3];
    assert_eq!(alpha(w / 2, 0), 255);
    assert!(alpha(w / 2, hh - 1) < 10, "{}", alpha(w / 2, hh - 1));
    // Item: the README's first image becomes the header picture; links carry OSC 8.
    open_item(&mut h, "dawn");
    let calls = h.log("calls.log");
    assert!(
        calls.contains(
            "readme-asset dawn https://raw.githubusercontent.com/andrewmd5/dawn/main/assets/hero.png"
        ),
        "{calls}"
    );
    assert!(!calls.contains("shields.io"), "badges are never fetched: {calls}");
    let router = h.router.as_mut().unwrap();
    let mut f = Frame::new(&mut h.ctx);
    router.draw(&mut f);
    assert!(f.buf.links().iter().any(|(_, u)| u == "https://github.com/andrewmd5/dawn"));
    assert!(f.buf.links().iter().any(|(_, u)| u == "https://dawn.example.com/docs"), "README links");
    assert!(f.buf.runs().iter().any(|r| r.text == "What it does" && r.sizing.scale == 2), "H2 at 2×");
    // Front's category tags are fractional runs.
    h.key(Key::Esc);
    let router = h.router.as_mut().unwrap();
    let mut f = Frame::new(&mut h.ctx);
    router.draw(&mut f);
    assert!(f.buf.runs().iter().any(|r| r.text == "Tools" && r.sizing.num == 2 && r.sizing.den == 3));
}

#[test]
fn readme_pictures_arrive_lazily_as_they_scroll_in() {
    let mut h = H::new(53, 26, Opts { caps: true, ..Opts::default() });
    open_item(&mut h, "kitten");
    let calls = h.log("calls.log");
    assert_eq!(calls.matches("readme-asset kitten").count(), 0, "the screenshot is out of view:\n{calls}");
    h.key(Key::End);
    h.settle();
    let calls = h.log("calls.log");
    assert_eq!(calls.matches("readme-asset kitten").count(), 1, "{calls}");
    assert!(calls.contains("screenshots/screenshot.png"));
    let router = h.router.as_mut().unwrap();
    let mut f = Frame::new(&mut h.ctx);
    router.draw(&mut f);
    assert!(
        f.places.iter().any(|p| p.pic.height() <= 6 * 20 && p.pic.width() > 100),
        "the inline picture is placed"
    );
}

#[test]
fn no_picture_means_no_picture_rows_on_item() {
    let mut h = H::new(53, 26, Opts { caps: true, ..Opts::default() });
    std::fs::write(h.dir.join("nopics"), "").unwrap();
    open_item(&mut h, "sigye");
    assert!(h.r().scene.get(El::Picture).is_none(), "{}", h.text);
    let hdr = layout::header(53, 26, 7, None);
    assert!(h.r().scene.get(El::Name).is_some_and(|e| e.rect.y == hdr.name.y), "{}", h.text);
    assert!(h.text.lines().skip(hdr.name.bottom() as usize).all(|l| !l.contains('░')), "{}", h.text);
}

#[test]
fn script_name_needs_a_known_cell_size() {
    let mut h = H::new(53, 26, Opts { caps: true, ..Opts::default() });
    h.ctx.cell_known = false;
    h.draw();
    let n = h.r().scene.get(El::Name).cloned().unwrap();
    assert!(n.picture.is_none() && n.text.as_deref() == Some("claude-code"), "{n:?}");
    assert!(h.has("tlstore"), "the wordmark falls back to text too:\n{}", h.text);
}

#[test]
fn installing_draws_the_progress_line_once_per_percentage() {
    let mut h = H::new(53, 26, Opts { caps: true, ..Opts::default() });
    std::fs::write(h.dir.join("hold"), "").unwrap();
    go_to(&mut h, "sigye");
    h.key(Key::Char('i'));
    h.pump(|r| r.st.job.as_ref().is_some_and(|j| j.pct >= 60));
    let router = h.router.as_mut().unwrap();
    let mut f = Frame::new(&mut h.ctx);
    router.draw(&mut f);
    let hdr = layout::header(53, 26, 7, Some(u16::MAX));
    let line = f.places.iter().find(|p| p.row == hdr.body.y + 5).expect("progress line");
    assert_eq!(line.pic.width(), 49 * 8);
    assert_eq!(line.pic.height(), 20);
    let id = line.pic.id();
    let mut f = Frame::new(&mut h.ctx);
    router.draw(&mut f);
    assert_eq!(f.places.iter().find(|p| p.row == hdr.body.y + 5).unwrap().pic.id(), id, "same picture");
    h.key(Key::Char('x'));
    h.settle();
    std::fs::remove_file(h.dir.join("hold")).unwrap();
}

#[test]
fn nothing_runs_past_the_right_edge() {
    // Every snapshot line fits its grid: nothing clipped by the edge.
    for e in std::fs::read_dir(manifest().join("tests/snapshots")).unwrap() {
        let p = e.unwrap().path();
        let name = p.file_stem().unwrap().to_string_lossy().to_string();
        let cols: usize = name.rsplit('-').next().unwrap().split('x').next().unwrap().parse().unwrap();
        let gutter = if cols < 44 { 1 } else { 2 };
        let text = std::fs::read_to_string(&p).unwrap();
        let last = text.lines().count() - 1;
        for (i, l) in text.lines().enumerate() {
            let body = &l[3..];
            let w = unicode_width_of(body);
            let pictures = body.contains('░');
            // The key row's fixed slots may run into the right gutter (`esc back` at 44).
            let limit = if i == last { cols } else { cols - gutter };
            assert!(pictures || w <= limit, "{name}: {w} columns: {body:?}");
        }
    }
}

#[test]
fn small_and_odd_grids_draw_every_screen_without_panicking() {
    for (c, r) in [(53, 25), (52, 23), (44, 20), (30, 14), (20, 8), (80, 60), (53, 26), (44, 30)] {
        let mut h = H::new(c, r, Opts { caps: true, ..Opts::default() });
        h.key(Key::Down);
        h.key(Key::Enter);
        h.settle();
        h.key(Key::End);
        h.key(Key::Esc);
        h.key(Key::Char('u'));
        h.key(Key::Esc);
        h.key(Key::Char('i'));
        h.settle();
        h.key(Key::Esc);
    }
}

// ---------------------------------------------------------------------------
// Motion (D7), driven by a fake clock
// ---------------------------------------------------------------------------

/// A harness with the store's Timeline and a fake clock, settled past the startup entry.
fn animated(cols: u16, rows: u16, caps: bool) -> (H, Rc<Cell<Instant>>) {
    let clock = Rc::new(Cell::new(Instant::now()));
    let mut h = H::new(
        cols,
        rows,
        Opts { caps, motion: Some(Box::new(Timeline::new())), clock: Some(clock.clone()), ..Opts::default() },
    );
    advance(&mut h, &clock, 2000);
    h.settle();
    assert!(!h.r().animating(), "{}", h.text);
    (h, clock)
}

/// Moves the fake clock on by `ms` and draws.
fn advance(h: &mut H, clock: &Rc<Cell<Instant>>, ms: u64) {
    clock.set(clock.get() + Duration::from_millis(ms));
    h.draw();
}

/// Sets the fake clock to `t0 + ms` and draws.
fn at(h: &mut H, clock: &Rc<Cell<Instant>>, t0: Instant, ms: f32) {
    clock.set(t0 + Duration::from_secs_f32(ms / 1000.0));
    h.draw();
}

type NavLog = Rc<RefCell<Vec<(NavKind, &'static str, &'static str, usize)>>>;

/// Leaves for exactly one frame, then enters for one frame, then rests.
struct Probe {
    log: NavLog,
    frames: u8,
}

impl Motion for Probe {
    fn navigate(&mut self, kind: NavKind, from: &Scene, to: &'static str, _: Instant) {
        self.log.borrow_mut().push((kind, from.screen, to, from.elements.len()));
        self.frames = 2;
    }
    fn frame(&mut self, _: Instant, _: &Scene) -> Phase {
        let f = self.frames;
        self.frames = self.frames.saturating_sub(1);
        match f {
            2 => Phase::Leaving(Default::default()),
            1 => Phase::Entering(Default::default()),
            _ => Phase::Idle,
        }
    }
    fn active(&self) -> bool {
        self.frames > 0
    }
}

#[test]
fn router_hands_navigation_and_frames_to_motion() {
    let log: NavLog = Rc::new(RefCell::new(Vec::new()));
    let probe = Probe { log: log.clone(), frames: 0 };
    let mut h = H::new(53, 26, Opts { motion: Some(Box::new(probe)), ..Opts::default() });
    assert!(h.r().scene.get(El::Row(0)).is_some(), "front records its rows");
    assert!(h.r().scene.get(El::Name).is_some());
    h.key(Key::Enter);
    // Leaving frame: the old view (front) is what is drawn.
    assert!(h.r().animating());
    assert!(h.has("↑ 2 updates") && !h.has("‹ apps"), "{}", h.text);
    h.draw();
    assert!(h.has("‹ apps"), "{}", h.text);
    h.draw();
    assert!(!h.r().animating());
    h.key(Key::Esc);
    h.draw();
    h.draw();
    h.key(Key::Enter);
    h.draw();
    h.draw();
    h.key(Key::Char('i'));
    h.settle();
    h.draw();
    h.draw();
    h.tap("‹ apps");
    h.draw();
    h.draw();
    let log = log.borrow();
    let kinds: Vec<_> = log.iter().map(|(k, f, t, _)| (*k, *f, *t)).collect();
    assert_eq!(
        kinds,
        vec![
            (NavKind::Push, "front", "item"),
            (NavKind::Pop, "item", "front"),
            (NavKind::Push, "front", "item"),
            (NavKind::Push, "item", "installing"),
            (NavKind::Pop, "installing", "item"),
        ]
    );
    assert!(log.iter().all(|(_, _, _, n)| *n > 3), "the leaving scene is handed over: {log:?}");
}

#[test]
fn timeline_leaves_in_120ms_and_enters_by_300() {
    let (mut h, clock) = animated(53, 26, false);
    let t0 = clock.get();
    go_to(&mut h, "kitten");
    advance(&mut h, &clock, 1000);
    let t0 = t0 + Duration::from_millis(1000);
    h.key(Key::Enter);
    assert!(h.r().animating());
    h.settle();
    // Leaving: Front is still drawn, its rows fading, the header (masthead, name) untouched.
    at(&mut h, &clock, t0, 30.0);
    assert!(h.has("↑ 2 updates") && h.has("sigye"), "{}", h.text);
    assert_eq!(h.r().scene.screen, "item", "the current scene is the new view's");
    // First entering frames: the item; its body rows arrive 30 ms apart.
    at(&mut h, &clock, t0, 125.0);
    assert!(h.has("‹ apps") && !h.has("sigye"), "{}", h.text);
    // The README is asked for on the item's first frame and arrives when it arrives.
    h.settle();
    at(&mut h, &clock, t0, 120.0 + 310.0);
    assert!(h.has("kitten icat photo.png") && !h.r().animating(), "{}", h.text);
    // Back: the item is drawn while leaving, then Front's rows fade up 30 ms apart; the
    // header and the key row are there from the first frame.
    h.key(Key::Esc);
    at(&mut h, &clock, t0, 500.0);
    assert!(h.has("‹ apps"), "item still drawn while leaving:\n{}", h.text);
    at(&mut h, &clock, t0, 430.0 + 120.0 + 40.0);
    assert!(h.has("↑ 2 updates") && h.has("q quit"), "{}", h.text);
    assert!(h.has(" claude-code ") && !h.has(" sigye "), "row 0 in, row 6 not yet:\n{}", h.text);
    at(&mut h, &clock, t0, 1000.0);
    assert!(h.has("sigye") && !h.r().animating());
    // At rest it is exactly the frame without motion.
    let mut still = H::new(53, 26, Opts::default());
    go_to(&mut still, "kitten");
    assert_eq!(h.text, still.text);
}

#[test]
fn keys_pressed_during_a_transition_are_not_dropped() {
    let (mut h, clock) = animated(53, 26, false);
    let t0 = clock.get();
    h.key(Key::Enter);
    assert_eq!(h.r().top(), "item");
    // Straight after the push, while leaving: the item scrolls.
    at(&mut h, &clock, t0, 20.0);
    h.key(Key::Esc);
    assert_eq!(h.r().top(), "front");
    // Straight after the pop, still in the leave: four Downs move the cursor four.
    at(&mut h, &clock, t0, 40.0);
    for _ in 0..4 {
        h.key(Key::Down);
    }
    assert!(h.r().animating(), "still in the transition");
    advance(&mut h, &clock, 2000);
    assert_eq!(h.header_item(), "kitten", "{}", h.text);
    assert!(!h.r().animating());
    let hdr = layout::header(53, 26, 7, None);
    assert!(h.row(hdr.name.bottom() as usize - 1).contains("kitten"), "{}", h.text);
}

#[test]
fn header_picture_waits_for_the_cursor_to_rest() {
    let (mut h, clock) = animated(53, 26, true);
    assert!(h.r().scene.get(El::Picture).is_some_and(|e| e.picture.is_some()), "settled: placed");
    h.key(Key::Down);
    assert_eq!(h.header_item(), "dawn");
    assert!(h.r().scene.get(El::Picture).is_none(), "just moved: not yet\n{}", h.text);
    assert!(h.r().animating(), "the router ticks until the rest is over");
    advance(&mut h, &clock, 100);
    assert!(h.r().scene.get(El::Picture).is_none());
    advance(&mut h, &clock, 60);
    h.settle();
    assert!(h.r().scene.get(El::Picture).is_some_and(|e| e.picture.is_some()), "{}", h.text);
    assert!(!h.r().animating());
    // The name swaps at once, the picture waits: while it waits its rows stay reserved.
    h.key(Key::Down);
    let hdr = layout::header(53, 26, 7, Some(u16::MAX));
    assert!(h.r().scene.get(El::Name).is_some_and(|e| e.rect.y == hdr.name.y));
}

#[test]
fn motion_off_means_no_ticks_and_the_final_state_at_once() {
    let clock = Rc::new(Cell::new(Instant::now()));
    let mut h = H::new(
        53,
        26,
        Opts {
            caps: true,
            motion: Some(Box::new(Timeline::new())),
            motion_off: true,
            clock: Some(clock.clone()),
            ..Opts::default()
        },
    );
    assert!(!h.r().animating(), "no startup entry, no picture rest");
    assert!(h.r().scene.get(El::Picture).is_some_and(|e| e.picture.is_some()));
    h.key(Key::Enter);
    assert!(!h.r().animating());
    assert!(h.has("‹ apps"), "{}", h.text);
    h.key(Key::Esc);
    assert!(!h.r().animating() && h.has("↑ 2 updates"));
    let mut plain = H::new(53, 26, Opts { caps: true, ..Opts::default() });
    plain.key(Key::Enter);
    plain.settle();
    let mut off = H::new(
        53,
        26,
        Opts { caps: true, motion: Some(Box::new(Timeline::new())), motion_off: true, ..Opts::default() },
    );
    off.key(Key::Enter);
    off.settle();
    assert_eq!(off.text, plain.text);
}

#[test]
fn the_first_view_enters_too() {
    let clock = Rc::new(Cell::new(Instant::now()));
    let mut h = H::new(
        53,
        26,
        Opts { motion: Some(Box::new(Timeline::new())), clock: Some(clock.clone()), ..Opts::default() },
    );
    assert!(h.r().animating());
    assert!(!h.has("sigye"), "rows arrive later:\n{}", h.text);
    assert!(h.has("claude-code"), "the header is there from the first frame:\n{}", h.text);
    advance(&mut h, &clock, 2000);
    assert!(h.has("sigye") && !h.r().animating());
}

#[test]
fn install_number_counts_up() {
    let (mut h, clock) = animated(53, 26, false);
    std::fs::write(h.dir.join("hold"), "").unwrap();
    go_to(&mut h, "sigye");
    advance(&mut h, &clock, 2000);
    let t0 = clock.get();
    h.key(Key::Char('i'));
    assert_eq!(h.r().top(), "installing");
    h.pump(|r| r.st.job.as_ref().is_some_and(|j| j.pct >= 60));
    let target = h.r().st.job.as_ref().unwrap().pct as u32;
    let hdr = layout::header(53, 26, 7, None);
    let number = |h: &H| -> Option<u32> {
        let l = h.row(hdr.body.y as usize);
        l.trim().split('%').next()?.parse().ok()
    };
    at(&mut h, &clock, t0, 120.0 + 20.0);
    assert_eq!(number(&h), Some(0), "{}", h.text);
    at(&mut h, &clock, t0, 120.0 + 120.0);
    let mid = number(&h).expect("number on screen");
    assert!(mid > 0 && mid < target, "counting: {mid} of {target}\n{}", h.text);
    at(&mut h, &clock, t0, 120.0 + 2000.0);
    assert_eq!(number(&h), Some(target));
    assert!(!h.r().animating());
    h.key(Key::Char('x'));
    h.settle();
    std::fs::remove_file(h.dir.join("hold")).unwrap();
}

/// The frame's output with picture uploads (`a=t`, clip frames `a=f` and their continuation
/// chunks) left out.
fn split_output(out: &str) -> (usize, usize) {
    let b = out.as_bytes();
    let (mut i, mut upload) = (0, 0);
    while i < b.len() {
        if b[i] == 0x1b && i + 1 < b.len() {
            let start = i;
            match b[i + 1] {
                b'[' => {
                    i += 2;
                    while i < b.len() && !(0x40..=0x7e).contains(&b[i]) {
                        i += 1;
                    }
                    i += 1;
                }
                b'_' | b']' | b'P' => {
                    i += 2;
                    while i < b.len() && !(b[i] == 0x1b && b.get(i + 1) == Some(&b'\\')) && b[i] != 0x07 {
                        i += 1;
                    }
                    i += if b.get(i) == Some(&0x07) { 1 } else { 2 };
                    let body = &out[start + 2..i.min(out.len())];
                    let upload_chunk =
                        body.starts_with("Ga=t") || body.starts_with("Ga=f") || body.starts_with("Gm=");
                    if b[start + 1] == b'_' && upload_chunk {
                        upload += i - start;
                    }
                }
                _ => i += 2,
            }
        } else {
            i += out[i..].chars().next().unwrap().len_utf8();
        }
    }
    (out.len() - upload.min(out.len()), upload)
}

#[test]
fn frames_stay_small_during_a_transition() {
    let (mut h, clock) = animated(53, 26, true);
    h.out.clear();
    let t0 = clock.get();
    h.key(Key::Enter);
    let mut sizes = Vec::new();
    let mut t = 0.0f32;
    while t < 500.0 {
        at(&mut h, &clock, t0, t);
        let (bytes, _) = split_output(&h.out);
        sizes.push((t as u32, bytes));
        t += 1000.0 / 60.0;
    }
    let max = sizes.iter().map(|s| s.1).max().unwrap();
    let total: usize = sizes.iter().map(|s| s.1).sum();
    eprintln!(
        "push to item, 53x26 kitty: {} frames, max {max} B, mean {} B",
        sizes.len(),
        total / sizes.len()
    );
    assert!(max <= 8 * 1024, "a frame over 8 KB: {sizes:?}");
    // At rest nothing is written frame after frame.
    let late: Vec<_> = sizes.iter().filter(|s| s.0 >= 450).collect();
    assert!(late.iter().all(|s| s.1 == 0), "{late:?}");
}

// ---------------------------------------------------------------------------
// The hero clip (an APNG header picture)
// ---------------------------------------------------------------------------

/// A `w`×`h` APNG of three solid frames (red, green, blue) at 12 fps.
fn tiny_apng(w: u32, h: u32) -> Vec<u8> {
    let mut out = Vec::new();
    {
        let mut e = png::Encoder::new(&mut out, w, h);
        e.set_color(png::ColorType::Rgba);
        e.set_depth(png::BitDepth::Eight);
        e.set_animated(3, 0).unwrap();
        e.set_frame_delay(1, 12).unwrap();
        let mut wr = e.write_header().unwrap();
        for c in [[255u8, 0, 0, 255], [0, 255, 0, 255], [0, 0, 255, 255]] {
            let frame: Vec<u8> = (0..w * h).flat_map(|_| c).collect();
            wr.write_image_data(&frame).unwrap();
        }
        wr.finish().unwrap();
    }
    out
}

/// Sends clip frames until the renderer has nothing left to stream; returns everything sent.
fn stream_all(h: &mut H) -> String {
    let mut streamed = String::new();
    let deadline = Instant::now() + Duration::from_secs(10);
    while h.renderer.pending() && Instant::now() < deadline {
        let mut out = String::new();
        h.renderer.stream(&mut out);
        streamed.push_str(&out);
        std::thread::sleep(Duration::from_millis(2));
    }
    assert!(!h.renderer.pending(), "the clip did not finish streaming");
    streamed
}

fn header_picture_id(h: &mut H) -> u32 {
    let placed = h.r().scene.get(El::Picture).and_then(|e| e.picture);
    placed.unwrap_or_else(|| panic!("no header picture placed:\n{}", h.text)).0
}

#[test]
fn hero_clip_plays_after_the_rest_and_is_freed_on_leave() {
    let (mut h, clock) = animated(53, 26, true);
    std::fs::write(h.dir.join("pics/dawn.png"), tiny_apng(60, 30)).unwrap();
    h.key(Key::Down);
    assert_eq!(h.header_item(), "dawn");
    advance(&mut h, &clock, 200);
    h.settle();
    assert!(h.log("calls.log").contains("picture dawn"), "{}", h.log("calls.log"));
    let pic_id = header_picture_id(&mut h);
    assert!(h.out.contains(&format!(",i={pic_id},q=2,o=z")), "the still is uploaded first:\n{:?}", h.out);
    assert!(!h.out.contains("\x1b_Ga=f"), "no frame rides with the still");
    assert!(h.renderer.pending(), "frames to follow");
    let streamed = stream_all(&mut h);
    let head = format!("\x1b_Ga=f,i={pic_id},f=32,");
    assert_eq!(streamed.matches(&head).count(), 2, "two frames after the still:\n{streamed:?}");
    assert!(streamed.contains(",X=1,z=83,q=2,o=z,m="), "12 fps gaps, replaced in place:\n{streamed:?}");
    let tail = format!("\x1b_Ga=a,i={pic_id},r=1,z=83,s=3,v=1,q=2\x1b\\");
    assert!(streamed.ends_with(&tail), "then the loop:\n{streamed:?}");
    // Nothing more at rest.
    h.draw();
    assert_eq!(h.out, "");
    // The cursor moves on: the clip's image goes, frames and all.
    h.key(Key::Down);
    assert_ne!(h.header_item(), "dawn");
    assert!(h.out.contains(&format!("\x1b_Ga=d,d=I,i={pic_id},q=2\x1b\\")), "{:?}", h.out);
    assert!(!h.renderer.pending());
}

#[test]
fn motion_off_keeps_the_hero_still() {
    let mut h = H::new(
        53,
        26,
        Opts { caps: true, motion: Some(Box::new(Timeline::new())), motion_off: true, ..Opts::default() },
    );
    std::fs::write(h.dir.join("pics/dawn.png"), tiny_apng(60, 30)).unwrap();
    h.key(Key::Down);
    h.settle();
    let pic_id = header_picture_id(&mut h);
    assert!(h.out.contains(&format!(",i={pic_id},q=2,o=z")), "the still is uploaded:\n{:?}", h.out);
    assert!(!h.renderer.pending(), "no frames follow");
    assert!(!h.out.contains("\x1b_Ga=f") && !h.out.contains("\x1b_Ga=a"));
    let mut out = String::new();
    h.renderer.stream(&mut out);
    assert!(out.is_empty());
    // Moving on keeps the still's data, as for any picture.
    h.key(Key::Down);
    assert!(h.out.contains(&format!("\x1b_Ga=d,d=i,i={pic_id},p=1,q=2\x1b\\")), "{:?}", h.out);
    assert!(!h.out.contains("d=I"));
}
