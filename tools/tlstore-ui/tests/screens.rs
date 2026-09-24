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
use tlstore_ui::render::{Frame, HitMap, Renderer, Sym};
use tlstore_ui::store::motion::{Timeline, GLYPHS};
use tlstore_ui::store::proc::Env;
use tlstore_ui::store::scene::{Effect, El, Fx, Motion, NavKind, Phase, Scene};
use tlstore_ui::store::Router;
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
        h.draw();
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

    fn keys(&mut self, s: &str) {
        for c in s.chars() {
            self.key(Key::Char(c));
        }
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

    /// Taps `needle` on the row that also shows `on_row` (to tell a chip from a list tag).
    fn tap_on(&mut self, on_row: &str, needle: &str) -> bool {
        let (_, y) = find(&self.text, on_row).unwrap_or_else(|| panic!("{on_row:?} not on screen"));
        let line = self.text.lines().nth(y as usize).unwrap()[3..].to_string();
        let i = line.rfind(needle).unwrap_or_else(|| panic!("{needle:?} not on row {y}"));
        self.tap_at(unicode_width_of(&line[..i]) as u16, y)
    }

    fn resize(&mut self, cols: u16, rows: u16) {
        self.ctx.size = Size::new(cols, rows, 8, 20);
        self.ev(Event::Resize(self.ctx.size));
    }

    /// Delivers readable fds until nothing is watched (or `until` holds).
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

    fn settle(&mut self) {
        self.pump(|_| false);
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

const SIZES: [(u16, u16); 3] = [(53, 26), (53, 40), (40, 26)];

fn open_item(h: &mut H, name: &str) {
    h.key(Key::Home);
    for _ in 0..10 {
        if h.r().top() == "apps" && current_row_is(h, name) {
            break;
        }
        h.key(Key::Down);
    }
    h.key(Key::Enter);
    assert_eq!(h.r().top(), "item", "{}", h.text);
}

fn current_row_is(h: &mut H, name: &str) -> bool {
    // The cursor row's name is drawn in the accent; the snapshot cannot show colour, so ask
    // for the item page and back out when it is the wrong one.
    h.key(Key::Enter);
    let ok = h.has(&format!("/ apps / {name}"));
    h.key(Key::Esc);
    ok
}

// ---------------------------------------------------------------------------
// Snapshots
// ---------------------------------------------------------------------------

fn snap_all(mode: &str, caps: bool) {
    for (c, r) in SIZES {
        let tag = format!("{c}x{r}");
        let mut h = H::new(c, r, Opts { caps, ..Opts::default() });
        h.snapshot(&format!("{mode}-apps-{tag}"));

        open_item(&mut h, "kitten");
        h.pump(|r| r.st.starred("kovidgoyal/kitty").is_some());
        h.snapshot(&format!("{mode}-item-{tag}"));
        h.key(Key::Esc);

        h.key(Key::Char('u'));
        assert_eq!(h.r().top(), "updates");
        h.snapshot(&format!("{mode}-updates-{tag}"));
        h.key(Key::Esc);

        std::fs::write(h.dir.join("hold"), "").unwrap();
        open_item(&mut h, "sigye");
        h.key(Key::Char('i'));
        h.pump(|r| r.st.job.as_ref().is_some_and(|j| j.pct >= 60));
        h.snapshot(&format!("{mode}-installing-{tag}"));
        h.key(Key::Char('c'));
        h.settle();
        std::fs::remove_file(h.dir.join("hold")).unwrap();
        h.key(Key::Esc);
        h.key(Key::Esc);

        let mut h = H::new(c, r, Opts { caps, gh: Gh::Missing, ..Opts::default() });
        open_item(&mut h, "sigye");
        h.key(Key::Char('s'));
        assert_eq!(h.r().top(), "nogh");
        h.snapshot(&format!("{mode}-nogh-{tag}"));
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
// Behaviour
// ---------------------------------------------------------------------------

#[test]
fn shared_frame_on_apps() {
    let h = H::new(52, 45, Opts::default());
    assert!(h.row(1).starts_with("  TLSTORE / apps"), "{}", h.text);
    assert!(h.row(1).trim_end().ends_with("↑ 2 updates"));
    assert!(h.row(2).contains("────"));
    assert!(h.row(4).contains("T E R M I N A L"));
    assert!(h.has("goodies"));
    let calls = h.log("calls.log");
    assert!(calls.contains("list --tsv"), "{calls}");
    assert!(calls.contains("update --check --tsv --offline"));
    assert!(calls.lines().any(|l| l == "update --check --tsv"), "background refresh: {calls}");
}

#[test]
fn key_and_tap_navigation_between_all_screens() {
    let mut h = H::new(52, 45, Opts::default());
    // Apps → Item by tap on a row name, back by esc.
    h.tap("sigye");
    assert_eq!(h.r().top(), "item");
    assert!(h.has("/ apps / sigye"));
    h.key(Key::Esc);
    assert_eq!(h.r().top(), "apps");
    // Context item → Updates, key-row tap back.
    h.tap("↑ 2 updates");
    assert_eq!(h.r().top(), "updates");
    assert!(h.has("T W O   O F   Y O U R S"), "{}", h.text);
    h.tap("esc back");
    assert_eq!(h.r().top(), "apps");
    // Item → Installing → mark tap goes home from anywhere.
    h.tap("sigye");
    h.key(Key::Char('i'));
    assert_eq!(h.r().top(), "installing");
    h.settle();
    assert!(h.has("sigye is ready."), "{}", h.text);
    h.tap("TLSTORE");
    assert_eq!(h.r().top(), "apps");
    assert_eq!(h.r().depth(), 1);
    // Esc on Apps leaves.
    assert!(h.key(Key::Esc));
}

#[test]
fn category_chips_and_search_filter_the_list() {
    let mut h = H::new(52, 45, Opts::default());
    assert!(h.has("claude-code") && h.has("sigye"));
    h.tap_on("Note taking", "AI");
    assert!(h.has("claude-code") && !h.has("sigye"), "{}", h.text);
    h.key(Key::BackTab);
    assert!(h.has("sigye") && !h.has("claude-code"), "{}", h.text);
    h.key(Key::Tab);
    h.key(Key::Tab);
    assert!(h.has("sigye") && h.has("claude-code"));
    h.key(Key::Char('/'));
    h.keys("kit");
    assert!(h.has("/ kit") && h.has("kitten") && !h.has("sigye"), "{}", h.text);
    h.key(Key::Esc);
    assert!(h.has("sigye"));
}

#[test]
fn narrow_grid_uses_short_chip_and_drops_tags() {
    let h = H::new(40, 34, Opts::default());
    assert!(h.has(" Notes ") && !h.has("Note taking"), "{}", h.text);
    assert!(!h.has("TOOLS"));
    let w = H::new(52, 45, Opts::default());
    assert!(w.has("TOOLS") && w.has(" Note taking "));
}

#[test]
fn multi_select_installs_with_the_right_arguments_and_reads_progress() {
    let mut h = H::new(52, 45, Opts::default());
    let (x, y) = find(&h.text, "claude-code").unwrap();
    // Tap on the mark column toggles; space toggles the current row.
    h.tap_at(2, y);
    assert!(h.has("✓") && h.has("1 selected"), "{}", h.text);
    let _ = x;
    h.tap_at(2, find(&h.text, "sigye").unwrap().1);
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
    assert_eq!(h.r().top(), "apps");
    assert!(!h.has("selected"));
    // The list was read again: both now show installed.
    assert!(h.row(find(&h.text, "sigye").unwrap().1 as usize).contains("installed"), "{}", h.text);
}

#[test]
fn remove_skips_what_is_not_installed_and_update_all_runs() {
    let mut h = H::new(52, 45, Opts::default());
    h.key(Key::Home);
    h.key(Key::Char('r'));
    assert!(h.has("Not installed."), "{}", h.text);
    h.key(Key::Char('u'));
    h.key(Key::Char('a'));
    h.settle();
    assert!(h.log("calls.log").lines().any(|l| l == "update --progress fastfetch kitten"));
    assert!(h.has("fastfetch and kitten are up to date."), "{}", h.text);
    h.key(Key::Esc);
    assert!(h.has("Everything is up to date."), "{}", h.text);
}

#[test]
fn failures_and_kept_config_are_reported() {
    let mut h = H::new(52, 45, Opts::default());
    std::fs::write(h.dir.join("kept"), "").unwrap();
    let r = h.r();
    assert!(r.st.start_job(tlstore_ui::store::Verb::Install, vec!["sigye".into(), "broken".into()]));
    h.settle();
    let s = h.r().st.job.as_ref().unwrap().summary();
    assert_eq!(s[0], "sigye is ready.");
    assert_eq!(s[1], "Could not install broken. Try again later.");
    assert_eq!(s[2], "Kept your config.fish.");
}

#[test]
fn cancel_stops_the_script() {
    let mut h = H::new(52, 45, Opts::default());
    std::fs::write(h.dir.join("hold"), "").unwrap();
    open_item(&mut h, "sigye");
    h.key(Key::Char('i'));
    h.pump(|r| r.st.job.as_ref().is_some_and(|j| j.pct >= 60));
    assert!(h.has("60") && h.has("keep going quietly"), "{}", h.text);
    // Esc keeps it going; the masthead offers the way back.
    h.key(Key::Esc);
    assert_eq!(h.r().top(), "item");
    assert!(h.has("installing 1 of 1"), "{}", h.text);
    h.tap("installing 1 of 1");
    assert_eq!(h.r().top(), "installing");
    let t = Instant::now();
    h.key(Key::Char('c'));
    h.settle();
    assert!(t.elapsed() < Duration::from_secs(5));
    assert!(h.has("Stopped before sigye was done."), "{}", h.text);
}

#[test]
fn star_with_gh_signed_in() {
    let mut h = H::new(52, 45, Opts::default());
    open_item(&mut h, "kitten");
    h.pump(|r| r.st.starred("kovidgoyal/kitty").is_some());
    assert!(h.has("☆") && h.has("s star"), "{}", h.text);
    h.key(Key::Char('s'));
    h.settle();
    assert!(h.has("★ starred") && h.has("s unstar"), "{}", h.text);
    assert!(h.dir.join("starred/kovidgoyal_kitty").exists());
    h.key(Key::Char('s'));
    h.settle();
    assert!(!h.dir.join("starred/kovidgoyal_kitty").exists());
    let log = h.log("gh.log");
    assert!(log.contains("auth status"));
    assert!(log.contains("api /user/starred/kovidgoyal/kitty"));
    assert!(log.contains("api -X PUT /user/starred/kovidgoyal/kitty"));
    assert!(log.contains("api -X DELETE /user/starred/kovidgoyal/kitty"));
    // Only star calls change anything.
    for l in log.lines() {
        assert!(l == "auth status" || l.starts_with("api ") && l.contains("/user/starred/"), "{l}");
    }
    // Setups have no star and no upstream.
    h.key(Key::Esc);
    open_item(&mut h, "fish-shell");
    assert!(h.has("our setup") && !h.has("☆") && !h.has("s star"), "{}", h.text);
}

#[test]
fn star_with_gh_signed_out_shows_the_footnote_then_stars_after_sign_in() {
    let mut h = H::new(52, 45, Opts { gh: Gh::SignedOut, ..Opts::default() });
    open_item(&mut h, "sigye");
    assert!(!h.has("☆"), "marks stay hidden until gh works");
    h.key(Key::Char('s'));
    assert_eq!(h.r().top(), "nogh");
    assert!(
        h.has("Starring needs gh.") && h.has("$ gh auth login") && !h.has("pkg install gh"),
        "{}",
        h.text
    );
    h.key(Key::Char('o'));
    h.settle();
    assert_eq!(h.log("open.log").trim(), "https://github.com/am2rican5/sigye");
    std::fs::write(h.dir.join("gh-signed-in"), "").unwrap();
    h.key(Key::Char('s'));
    assert_eq!(h.r().top(), "item");
    h.settle();
    assert!(h.dir.join("starred/am2rican5_sigye").exists(), "{}", h.log("gh.log"));
    assert!(h.has("★ starred"), "{}", h.text);
}

#[test]
fn star_with_gh_missing_and_no_opener_prints_the_address() {
    let mut h = H::new(52, 45, Opts { gh: Gh::Missing, opener: false, ..Opts::default() });
    open_item(&mut h, "sigye");
    h.key(Key::Char('s'));
    assert!(h.has("$ pkg install gh") && h.has("$ gh auth login"), "{}", h.text);
    h.key(Key::Char('o'));
    assert!(h.has("https://github.com/am2rican5/sigye"), "{}", h.text);
    h.key(Key::Esc);
    assert_eq!(h.r().top(), "item");
}

#[test]
fn fullscreen_calls_launcherctl_and_restores_on_exit() {
    let mut h = H::new(53, 26, Opts::default());
    assert!(h.has("f keyboard") && !h.has("fullscreen"), "{}", h.text);
    h.key(Key::Char('f'));
    assert_eq!(h.log("launcherctl.log"), "keyboard hide --hold\n");
    assert!(h.has("f keyboard"));
    h.tap("f keyboard");
    assert_eq!(h.log("launcherctl.log"), "keyboard hide --hold\nkeyboard show\n");
    h.key(Key::Char('f'));
    let dir = h.dir.clone();
    drop(h.router.take());
    let log = std::fs::read_to_string(dir.join("launcherctl.log")).unwrap();
    assert_eq!(log, "keyboard hide --hold\nkeyboard show\nkeyboard hide --hold\nkeyboard show\n");
}

#[test]
fn no_launcherctl_hides_the_hint() {
    let mut h = H::new(53, 26, Opts { launcherctl: false, ..Opts::default() });
    assert!(!h.has("f keyboard"), "{}", h.text);
    h.key(Key::Char('f'));
    assert_eq!(h.r().top(), "apps");
}

#[test]
fn paging_when_rows_do_not_fit() {
    // 53×26, the baseline, has room for six rows: 7 items make two pages.
    let mut h = H::new(53, 26, Opts::default());
    assert!(h.has("‹ ● ○ ›"), "{}", h.text);
    assert!(!h.has("sigye"));
    h.key(Key::PageDown);
    assert!(h.has("‹ ○ ● ›") && h.has("sigye"), "{}", h.text);
    h.tap("‹");
    assert!(h.has("‹ ● ○ ›"));
}

#[test]
fn item_scrolls_by_keys_and_drag() {
    let mut h = H::new(53, 26, Opts::default());
    open_item(&mut h, "kitten");
    h.pump(|r| r.st.starred("kovidgoyal/kitty").is_some());
    assert!(!h.has("G O O D") && h.has("more below ↓"), "{}", h.text);
    h.key(Key::End);
    assert!(h.has("G O O D   T O   K N O W") && h.has("best in a kitty-compatible terminal"), "{}", h.text);
    assert!(!h.has("more below"), "{}", h.text);
    h.key(Key::Home);
    h.tap("more below ↓");
    assert!(!h.has("kovidgoyal/kitty"), "a tap on the cue pages down:\n{}", h.text);
    h.key(Key::Home);
    assert!(h.has("kovidgoyal/kitty"));
    let m = |kind, row| {
        Event::Mouse(Mouse { kind, button: 0, col: 10, row, shift: false, alt: false, ctrl: false })
    };
    h.ev(m(MouseKind::Press, 18));
    h.ev(m(MouseKind::Drag, 4));
    assert!(!h.has("kovidgoyal/kitty"), "{}", h.text);
}

#[test]
fn resize_relays_out() {
    let mut h = H::new(53, 40, Opts::default());
    assert!(h.has("sigye") && !h.has("‹ ● ○ ›"), "all seven rows fit:\n{}", h.text);
    h.resize(53, 26);
    assert!(!h.has("sigye") && h.has("‹ ● ○ ›"), "{}", h.text);
    h.resize(40, 26);
    assert!(h.has(" Notes "));
}

#[test]
fn kitty_terminal_gets_pictures_and_links() {
    let mut h = H::new(53, 26, Opts { caps: true, ..Opts::default() });
    // The baseline has no room for a cover: the list comes first.
    assert!(!h.log("calls.log").contains("picture dawn"), "{}", h.log("calls.log"));
    assert!(h.r().scene.get(El::HeroWord).is_some_and(|e| e.picture.is_some()), "script word picture");
    assert!(h.r().scene.get(El::Cover).is_none());
    // A tall window has the room: the featured cover and, on an item, cover and demo.
    let mut h = H::new(53, 60, Opts { caps: true, ..Opts::default() });
    assert!(h.log("calls.log").contains("picture dawn"));
    assert!(h.r().scene.get(El::Cover).is_some_and(|e| e.picture.is_some()), "{}", h.text);
    open_item(&mut h, "kitten");
    h.pump(|r| r.st.starred("kovidgoyal/kitty").is_some());
    let router = h.router.as_mut().unwrap();
    let mut f = Frame::new(&mut h.ctx);
    router.draw(&mut f);
    assert_eq!(f.places.len(), 3, "mark, cover, demo (the name is sized text)");
    assert!(f.buf.runs().iter().any(|r| r.text == "kitten" && r.sizing.scale == 3));
    assert!(f.buf.links().iter().any(|(_, u)| u == "https://github.com/kovidgoyal/kitty"));
}

#[test]
fn script_word_needs_a_known_cell_size() {
    let mut h = H::new(53, 26, Opts { caps: true, ..Opts::default() });
    h.ctx.cell_known = false;
    h.draw();
    let w = h.r().scene.get(El::HeroWord).cloned().unwrap();
    assert!(w.picture.is_none() && w.text.as_deref() == Some("goodies"), "{w:?}");
    assert!(h.has("TLSTORE / apps"), "the pixel mark falls back to text too:\n{}", h.text);
}

#[test]
fn no_picture_means_no_cover_at_all() {
    let mut h = H::new(53, 60, Opts { caps: true, ..Opts::default() });
    std::fs::write(h.dir.join("nopics"), "").unwrap();
    open_item(&mut h, "sigye");
    let router = h.router.as_mut().unwrap();
    let mut f = Frame::new(&mut h.ctx);
    router.draw(&mut f);
    // The mark only: no cover, no stand-in box, the page starts right under the hero.
    assert_eq!(f.places.len(), 1);
    let text = screen_text(&f);
    assert!(!text.contains("S I G Y E"), "{text}");
    assert!(text.lines().skip(2).all(|l| !l.contains('░')), "{text}");
    assert!(text.lines().nth(9).unwrap().contains("am2rican5/sigye"), "{text}");
}

#[test]
fn nothing_runs_past_the_right_edge() {
    // Every snapshot line fits its grid: nothing clipped by the edge.
    for e in std::fs::read_dir(manifest().join("tests/snapshots")).unwrap() {
        let p = e.unwrap().path();
        let name = p.file_stem().unwrap().to_string_lossy().to_string();
        let cols: usize = name.rsplit('-').next().unwrap().split('x').next().unwrap().parse().unwrap();
        let gutter = if cols < 44 { 1 } else { 2 };
        for l in std::fs::read_to_string(&p).unwrap().lines() {
            let body = &l[3..];
            let w = unicode_width_of(body);
            let pictures = body.contains('░');
            assert!(pictures || w <= cols - gutter, "{name}: {w} columns: {body:?}");
        }
    }
}

// ---------------------------------------------------------------------------
// Motion hooks (P5)
// ---------------------------------------------------------------------------

type NavLog = Rc<RefCell<Vec<(NavKind, &'static str, &'static str, usize)>>>;

/// Leaves for exactly one frame with the breadcrumb hidden, then enters for one frame with the
/// hero word shifted down 40 px, then rests.
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
        let mut fx = Fx::default();
        let f = self.frames;
        self.frames = self.frames.saturating_sub(1);
        match f {
            2 => {
                fx.set(El::Crumb, Effect { alpha: 0.0, ..Effect::default() });
                Phase::Leaving(fx)
            }
            1 => {
                fx.set(El::Crumb, Effect { reveal: 0.5, ..Effect::default() });
                Phase::Entering(fx)
            }
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
    let mut h = H::new(52, 45, Opts { motion: Some(Box::new(probe)), ..Opts::default() });
    assert!(h.r().scene.get(El::Row(0)).is_some(), "apps records its rows");
    assert!(h.r().scene.get(El::HeroWord).is_some());
    h.tap("sigye");
    // Leaving frame: the old view (apps) drawn, crumb hidden.
    assert!(h.r().animating());
    assert!(h.has(" Note taking ") && !h.has("/ apps"), "{}", h.text);
    // Entering frame: the item, crumb half revealed.
    h.draw();
    assert!(h.has("N O .   0 7") && h.has("TLSTORE / apps ") && !h.has("/ apps / s"), "{}", h.text);
    h.draw();
    assert!(h.has("/ apps / sigye") && !h.r().animating());
    h.key(Key::Esc);
    h.draw();
    h.draw();
    h.tap("sigye");
    h.draw();
    h.draw();
    h.tap("TLSTORE");
    let log = log.borrow();
    let kinds: Vec<_> = log.iter().map(|(k, f, t, _)| (*k, *f, *t)).collect();
    assert_eq!(
        kinds,
        vec![
            (NavKind::Push, "apps", "item"),
            (NavKind::Pop, "item", "apps"),
            (NavKind::Push, "apps", "item"),
            (NavKind::Home, "item", "apps"),
        ]
    );
    assert!(log.iter().all(|(_, _, _, n)| *n > 5), "the leaving scene is handed over: {log:?}");
}

// ---------------------------------------------------------------------------
// Motion (P5): the Timeline, driven by a fake clock
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
    assert!(!h.r().animating());
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

/// The frame's output with picture uploads (`a=t` and their continuation chunks) left out,
/// and the printable text it writes (everything outside escape sequences).
fn split_output(out: &str) -> (usize, usize, String) {
    let b = out.as_bytes();
    let (mut i, mut upload, mut text) = (0, 0, String::new());
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
                    if b[start + 1] == b'_' && (body.starts_with("Ga=t") || body.starts_with("Gm=")) {
                        upload += i - start;
                    }
                }
                _ => i += 2,
            }
        } else {
            let ch = out[i..].chars().next().unwrap();
            text.push(ch);
            i += ch.len_utf8();
        }
    }
    (out.len() - upload.min(out.len()), upload, text)
}

#[test]
fn timeline_plays_flow_for_a_push_to_item() {
    let (mut h, clock) = animated(52, 45, true);
    let from = h.r().scene.clone();
    assert_eq!(from.screen, "apps");
    assert!(from.get(El::HeroWord).is_some_and(|e| e.picture.is_some()), "apps: script word picture");
    h.tap("sigye");
    advance(&mut h, &clock, 2000);
    let cur = h.r().scene.clone();
    assert_eq!(cur.screen, "item");
    assert!(cur.get(El::HeroWord).is_some_and(|e| e.picture.is_none()), "item: the name is text");
    assert!(cur.get(El::Cover).is_some_and(|e| e.picture.is_some()), "sigye has room for its cover");
    let crumb = cur.get(El::Crumb).and_then(|e| e.text.clone()).unwrap();
    assert_eq!(crumb, "/ apps / sigye");

    let mut m = Timeline::new();
    let t0 = Instant::now();
    let t = |ms: u64| t0 + Duration::from_millis(ms);
    m.navigate(NavKind::Push, &from, "item", t0);
    assert!(m.active());

    // Leaving: text fades, pictures are simply gone (never moved), the masthead stays.
    let Phase::Leaving(fx) = m.frame(t(0), &cur) else { panic!("leaving at 0") };
    assert!(fx.get(El::Row(0)).at_rest() && fx.get(El::Crumb).at_rest());
    assert_eq!(fx.get(El::HeroWord).alpha, 0.0);
    let Phase::Leaving(fx) = m.frame(t(100), &cur) else { panic!("leaving at 100") };
    assert!(fx.get(El::Row(0)).alpha <= 0.07, "{:?}", fx.get(El::Row(0)));
    assert!(fx.get(El::Keys).alpha <= 0.07);
    let w = fx.get(El::HeroWord);
    assert!(w.alpha == 0.0 && w.dy == 0 && w.dx == 0 && w.shown == 1.0, "{w:?}");
    assert!(fx.get(El::Crumb).at_rest() && fx.get(El::Mark).at_rest() && fx.get(El::Context).at_rest());

    // t = 300 (140 ms into the entry): crumb decoding, rule drawing, the name fading up,
    // the cover hidden, blocks not yet.
    let Phase::Entering(fx) = m.frame(t(300), &cur) else { panic!("entering at 300") };
    let c = fx.get(El::Crumb).text.expect("crumb decoding");
    assert_eq!(c, tlstore_ui::store::motion::decode(&crumb, 4));
    assert!(c.starts_with("/ a") && c != crumb && c.chars().any(|ch| GLYPHS.contains(&ch)), "{c}");
    let rule = fx.get(El::Rule).reveal;
    assert!(rule > 0.6 && rule < 0.95, "rule {rule}");
    let lead = fx.get(El::HeroLead).alpha;
    assert!(lead > 0.2 && lead < 1.0, "lead {lead}");
    let w = fx.get(El::HeroWord);
    assert!(w.alpha > 0.0 && w.alpha < 1.0 && w.dy == 0, "word {w:?}");
    let cover = fx.get(El::Cover);
    assert!(cover.alpha == 0.0 && cover.dy == 0 && cover.shown == 1.0, "{cover:?}");
    assert_eq!(fx.get(El::Block(0)).alpha, 0.0);

    // t = 600 (440 ms in): crumb and rule done, the first blocks in, the cover still hidden.
    let Phase::Entering(fx) = m.frame(t(600), &cur) else { panic!("entering at 600") };
    assert!(fx.get(El::Crumb).at_rest());
    assert!(fx.get(El::Rule).reveal > 0.99);
    assert!(fx.get(El::Block(0)).alpha >= 0.9);
    assert_eq!(fx.get(El::Cover).alpha, 0.0);

    // t = 900 (740 ms in): all text has arrived; only pictures wait.
    let Phase::Entering(fx) = m.frame(t(900), &cur) else { panic!("entering at 900") };
    for e in &cur.elements {
        if e.picture.is_some() && e.el != El::Mark {
            assert_eq!(fx.get(e.el).alpha, 0.0, "{:?} shows early", e.el);
        } else {
            assert!(fx.get(e.el).at_rest(), "{:?} still moving: {:?}", e.el, fx.get(e.el));
        }
    }

    // Past ≈ 980 ms (160 leave + 820 entry): at rest, pictures shown, no more ticks.
    assert!(matches!(m.frame(t(1000), &cur), Phase::Idle));
    assert!(!m.active());
}

#[test]
fn leaving_view_is_dropped_by_the_first_entering_frame() {
    let (mut h, clock) = animated(52, 45, true);
    let t0 = clock.get();
    h.tap("sigye");
    // Leaving: the apps view is still what is drawn.
    assert!(h.r().animating());
    assert!(h.has(" Note taking "), "{}", h.text);
    at(&mut h, &clock, t0, 80.0);
    assert!(h.has("/ apps") && !h.has("/ apps / s"), "masthead stays while leaving:\n{}", h.text);
    assert_eq!(h.r().scene.screen, "item", "the current scene is the new view's");
    // First entering frame: the item, everything still to arrive, crumb all glyphs.
    at(&mut h, &clock, t0, 170.0);
    assert_eq!(h.r().scene.screen, "item");
    assert!(!h.has(" Note taking ") && !h.has("sigye"), "{}", h.text);
    assert!(h.row(1).chars().any(|c| GLYPHS.contains(&c)), "{}", h.row(1));
    // And it never comes back.
    for ms in (186..1200).step_by(17) {
        at(&mut h, &clock, t0, ms as f32);
        assert!(!h.has(" Note taking "), "at {ms}\n{}", h.text);
    }
    assert!(h.has("/ apps / sigye") && !h.r().animating());
}

#[test]
fn input_during_the_leave_goes_to_the_new_view() {
    let (mut h, clock) = animated(52, 45, true);
    let t0 = clock.get();
    h.tap("sigye");
    at(&mut h, &clock, t0, 60.0);
    assert_eq!(h.r().top(), "item");
    h.key(Key::Esc);
    assert_eq!(h.r().top(), "apps");
    advance(&mut h, &clock, 2000);
    assert!(h.has(" Note taking ") && !h.r().animating());
}

#[test]
fn motion_off_means_no_ticks_and_the_final_state_at_once() {
    let clock = Rc::new(Cell::new(Instant::now()));
    let mut h = H::new(
        52,
        45,
        Opts {
            caps: true,
            motion: Some(Box::new(Timeline::new())),
            motion_off: true,
            clock: Some(clock.clone()),
            ..Opts::default()
        },
    );
    assert!(!h.r().animating(), "no startup entry");
    assert!(h.has(" Note taking ") && h.has("sigye"));
    h.tap("sigye");
    assert!(!h.r().animating());
    assert!(h.has("/ apps / sigye") && h.has("N O"), "{}", h.text);
    h.key(Key::Esc);
    assert!(!h.r().animating() && h.has(" Note taking "));
    h.tap_on("Note taking", "AI");
    assert!(!h.r().animating() && h.has("claude-code") && !h.has("sigye"));
    // Same frame as with no motion plugged in at all.
    let mut plain = H::new(52, 45, Opts { caps: true, ..Opts::default() });
    plain.tap("sigye");
    let mut off = H::new(
        52,
        45,
        Opts { caps: true, motion: Some(Box::new(Timeline::new())), motion_off: true, ..Opts::default() },
    );
    off.tap("sigye");
    assert_eq!(off.text, plain.text);
}

#[test]
fn the_first_view_enters_too() {
    let clock = Rc::new(Cell::new(Instant::now()));
    let mut h = H::new(
        52,
        45,
        Opts { motion: Some(Box::new(Timeline::new())), clock: Some(clock.clone()), ..Opts::default() },
    );
    assert!(h.r().animating());
    assert!(!h.has("sigye"), "rows arrive later:\n{}", h.text);
    advance(&mut h, &clock, 2000);
    assert!(h.has("sigye") && !h.r().animating());
}

#[test]
fn rows_restagger_on_a_category_change() {
    let (mut h, clock) = animated(52, 45, false);
    let t0 = clock.get();
    h.tap_on("Note taking", "AI");
    assert!(h.r().animating());
    assert!(!h.has("claude-code"), "rows start hidden:\n{}", h.text);
    at(&mut h, &clock, t0, 200.0);
    assert!(h.has("claude-code") && !h.has("sigye"));
    assert!(!h.r().animating(), "a re-stagger lasts at most 200 ms");
    // Moving the cursor or marking a row is not a list change.
    h.key(Key::Char(' '));
    assert!(!h.r().animating());
}

#[test]
fn frames_stay_small_and_picture_only_frames_write_no_text() {
    let (mut h, clock) = animated(52, 45, true);
    h.out.clear();
    let t0 = clock.get();
    h.tap("sigye");
    let mut sizes = Vec::new();
    let mut uploads = 0;
    let mut t = 0.0f32;
    while t < 1100.0 {
        at(&mut h, &clock, t0, t);
        let (bytes, up, text) = split_output(&h.out);
        uploads += up;
        sizes.push((t as u32, bytes, text.chars().filter(|c| !c.is_whitespace()).count()));
        t += 1000.0 / 60.0;
    }
    let max = sizes.iter().map(|s| s.1).max().unwrap();
    let total: usize = sizes.iter().map(|s| s.1).sum();
    eprintln!(
        "push to item, 52x45 kitty: {} frames, max {max} B, mean {} B, uploads {uploads} B",
        sizes.len(),
        total / sizes.len()
    );
    for (t, b, n) in &sizes {
        eprintln!("  t={t:4} ms  {b:5} B  {n:4} printable");
    }
    assert!(max <= 8 * 1024, "a frame over 8 KB: {sizes:?}");
    // Pictures never move: once the text has arrived, frames write nothing until the cover
    // simply appears, placed once.
    assert!(h.r().scene.get(El::Cover).is_some_and(|e| e.picture.is_some()), "sigye has a cover picture");
    let late: Vec<_> = sizes.iter().filter(|s| (720..=960).contains(&s.0)).collect();
    for (t, b, n) in &late {
        assert!(*n == 0 && *b < 512, "t={t}: {b} B with {n} printable characters");
    }
    assert!(late.iter().filter(|s| s.1 > 0).count() <= 1, "nothing re-placed frame after frame: {late:?}");
    let placed = h.r().scene.elements.iter().filter(|e| e.picture.is_some()).count();
    assert!(placed >= 2, "mark and cover at rest");
}

#[test]
fn plain_terminals_get_the_text_motion_only() {
    let (mut h, clock) = animated(52, 45, false);
    let rest_rule = {
        // The rule at rest, on the apps screen.
        h.row(2).chars().filter(|c| *c == '─').count()
    };
    let t0 = clock.get();
    h.tap("sigye");
    at(&mut h, &clock, t0, 160.0 + 130.0);
    assert!(!h.text.contains('░'), "no pictures here");
    assert!(h.row(1).chars().any(|c| GLYPHS.contains(&c)), "crumb decoding: {}", h.row(1));
    let rule = h.row(2).chars().filter(|c| *c == '─').count();
    assert!(rule > 0 && rule < rest_rule, "rule drawing out: {rule} of {rest_rule}");
    // The hero word does not move by rows here: if drawn, it is on its own row.
    let word_row = |h: &H| h.text.lines().position(|l| l.contains("sigye") && !l.contains("/ apps"));
    let early = word_row(&h);
    at(&mut h, &clock, t0, 2000.0);
    let rest = word_row(&h);
    assert!(rest.is_some());
    assert!(early.is_none() || early == rest, "{early:?} vs {rest:?}");
    // At rest it is exactly the frame without motion.
    let mut still = H::new(52, 45, Opts::default());
    still.tap("sigye");
    assert_eq!(h.text, still.text);
}

#[test]
fn install_number_counts_up_and_the_dot_bar_follows() {
    let (mut h, clock) = animated(52, 45, false);
    std::fs::write(h.dir.join("hold"), "").unwrap();
    h.tap("sigye");
    advance(&mut h, &clock, 2000);
    let t0 = clock.get();
    h.key(Key::Char('i'));
    assert_eq!(h.r().top(), "installing");
    h.pump(|r| r.st.job.as_ref().is_some_and(|j| j.pct >= 60));
    let target = h.r().st.job.as_ref().unwrap().pct as u32;
    let number = |h: &H| -> Option<(u32, usize)> {
        let l = h.text.lines().find(|l| l.trim_end().ends_with('%'))?;
        let n = l[3..].trim().trim_end_matches('%').parse().ok()?;
        let bar = h.text.lines().find(|l| l.contains('○') || l.contains('●'))?;
        Some((n, bar.matches('●').count()))
    };
    // The count starts on the first entering frame, from 0.
    at(&mut h, &clock, t0, 160.0 + 20.0);
    assert_eq!(number(&h).map(|n| n.0), Some(0), "{}", h.text);
    at(&mut h, &clock, t0, 160.0 + 120.0);
    let (mid, mid_dots) = number(&h).expect("number on screen");
    assert!(mid > 0 && mid < target, "counting: {mid} of {target}\n{}", h.text);
    at(&mut h, &clock, t0, 160.0 + 2000.0);
    let (end, end_dots) = number(&h).unwrap();
    assert_eq!(end, target);
    assert!(mid_dots < end_dots, "the dot bar follows: {mid_dots} then {end_dots}");
    assert!(!h.r().animating());
    h.key(Key::Char('c'));
    h.settle();
    std::fs::remove_file(h.dir.join("hold")).unwrap();
}
