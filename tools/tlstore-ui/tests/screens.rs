//! The store's screens driven end to end against stub `tlstore`, `gh`, `launcherctl` and
//! URL-opener scripts (tests/fixtures/store), with frame snapshots at the three layout sizes.
//!
//! Snapshots live in tests/snapshots; `UPDATE_SNAPSHOTS=1 cargo test` rewrites them.

use std::cell::RefCell;
use std::path::{Path, PathBuf};
use std::rc::Rc;
use std::sync::atomic::{AtomicU32, Ordering};
use std::time::{Duration, Instant};

use tlstore_ui::app::{Ctx, Nav, Screen};
use tlstore_ui::render::{Frame, HitMap, Sym};
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
}

struct Opts {
    gh: Gh,
    launcherctl: bool,
    opener: bool,
    pics: bool,
    caps: bool,
    motion: Option<Box<dyn Motion>>,
}

impl Default for Opts {
    fn default() -> Opts {
        Opts { gh: Gh::SignedIn, launcherctl: true, opener: true, pics: true, caps: false, motion: None }
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
        ctx.motion = o.motion.is_some();
        if o.caps {
            ctx.caps = Caps::all();
        }
        let router = match o.motion {
            Some(m) => Router::with_motion(env, m),
            None => Router::new(env),
        };
        let mut h = H { dir, ctx, router: Some(router), hits: HitMap::default(), sink, text: String::new() };
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

const SIZES: [(u16, u16); 3] = [(52, 45), (52, 23), (40, 34)];

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
    let mut h = H::new(52, 23, Opts::default());
    assert!(h.has("f fullscreen · hide the keyboard for more room"), "{}", h.text);
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
    let mut h = H::new(52, 23, Opts { launcherctl: false, ..Opts::default() });
    assert!(!h.has("fullscreen"), "{}", h.text);
    h.key(Key::Char('f'));
    assert_eq!(h.r().top(), "apps");
}

#[test]
fn paging_when_rows_do_not_fit() {
    // 52×30 is the Strip tier with room for 4 rows: 7 items make two pages.
    let mut h = H::new(52, 30, Opts::default());
    assert!(h.has("‹ ● ○ ›"), "{}", h.text);
    assert!(!h.has("sigye"));
    h.key(Key::PageDown);
    assert!(h.has("‹ ○ ● ›") && h.has("sigye"), "{}", h.text);
    h.tap("‹");
    assert!(h.has("‹ ● ○ ›"));
}

#[test]
fn item_scrolls_by_keys_and_drag() {
    let mut h = H::new(52, 23, Opts::default());
    open_item(&mut h, "kitten");
    h.pump(|r| r.st.starred("kovidgoyal/kitty").is_some());
    assert!(!h.has("good to know"), "{}", h.text);
    h.key(Key::End);
    assert!(h.has("good to know · works best"), "{}", h.text);
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
    let mut h = H::new(52, 45, Opts::default());
    assert!(h.has("A P P S"));
    h.resize(52, 23);
    assert!(!h.has("A P P S") && h.has("f fullscreen"), "{}", h.text);
    h.resize(40, 34);
    assert!(h.has(" Notes "));
}

#[test]
fn kitty_terminal_gets_pictures_and_links() {
    let mut h = H::new(52, 45, Opts { caps: true, ..Opts::default() });
    assert!(h.log("calls.log").contains("picture dawn"));
    open_item(&mut h, "kitten");
    let router = h.router.as_mut().unwrap();
    let mut f = Frame::new(&mut h.ctx);
    router.draw(&mut f);
    assert!(f.places.len() >= 4, "mark, word, cover, demo: {}", f.places.len());
    assert!(f.buf.links().iter().any(|(_, u)| u == "https://github.com/kovidgoyal/kitty"));
}

#[test]
fn no_picture_command_falls_back_to_stand_ins() {
    let mut h = H::new(52, 45, Opts { caps: true, ..Opts::default() });
    std::fs::write(h.dir.join("nopics"), "").unwrap();
    open_item(&mut h, "sigye");
    let router = h.router.as_mut().unwrap();
    let mut f = Frame::new(&mut h.ctx);
    router.draw(&mut f);
    // Mark and hero word only; the cover is a text stand-in.
    assert_eq!(f.places.len(), 2);
    assert!(screen_text(&f).contains("S I G Y E"));
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
    assert!(h.has("A P P S") && !h.has("/ apps"), "{}", h.text);
    // Entering frame: the item, crumb half revealed.
    h.draw();
    assert!(h.has("S I G Y E") && h.has("TLSTORE / apps ") && !h.has("/ apps / s"), "{}", h.text);
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
