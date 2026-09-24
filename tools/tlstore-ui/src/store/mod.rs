//! The store's screens. One [`Router`] is the only `app::Screen`: it owns the shared state
//! ([`Store`]), a stack of [`View`]s, the background tasks, and the single place navigation
//! happens (where [`scene::Motion`] gets its leave/enter hooks).

pub mod data;
pub mod front;
pub mod installing;
pub mod item;
pub mod motion;
pub mod paint;
pub mod proc;
pub mod readme;
pub mod scene;

use std::collections::HashMap;
use std::os::fd::RawFd;
use std::path::PathBuf;
use std::rc::Rc;
use std::time::{Duration, Instant};

use crate::app::{Ctx, Nav, Screen};
use crate::layout::{self, Header, PICTURE_MAX_ROWS};
use crate::picture::Picture;
use crate::render::Frame;
use crate::term::{Event, Key};

use data::{parse_progress, parse_updates, Catalog, Progress, STEPS};
use paint::{draw_keys, draw_notice, Facts, Paint, Slots, A_HOME, A_KEY0};
use proc::{Env, Fetch, Task, TaskKind};
use scene::{Fx, Motion, NavKind, NoMotion, Phase, Scene};

/// How long an item rests under the cursor before its header picture is placed.
pub const PICTURE_REST: Duration = Duration::from_millis(150);

/// What a view wants after an event.
pub enum Go {
    Stay,
    /// Not mine: the router may use it (`f` full screen, `q` quit, `esc` back).
    Pass,
    Push(Box<dyn View>),
    Replace(Box<dyn View>),
    Back,
    Home,
    Quit,
}

/// One screen of the store, drawn inside the shared frame.
pub trait View {
    /// "front", "item", "installing".
    fn name(&self) -> &'static str;
    /// Draws the header and the body (the router draws the notice and key rows after).
    fn draw(&mut self, p: &mut Paint, st: &mut Store);
    /// The five key slots.
    fn keys(&self, st: &mut Store) -> Slots;
    fn handle(&mut self, ev: &Event, st: &mut Store) -> Go;
    /// The catalog was reloaded (after an install, a refresh): fix cursors.
    fn refresh(&mut self, _st: &Store) {}
}

/// install, update or remove.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Verb {
    Install,
    Update,
    Remove,
}

impl Verb {
    pub fn arg(self) -> &'static str {
        match self {
            Verb::Install => "install",
            Verb::Update => "update",
            Verb::Remove => "remove",
        }
    }
    /// The facts-strip word while it runs.
    pub fn ing(self) -> &'static str {
        match self {
            Verb::Install => "installing",
            Verb::Update => "updating",
            Verb::Remove => "removing",
        }
    }
}

/// One item's closing line from the progress stream.
#[derive(Clone, Debug, PartialEq, Eq)]
pub struct Done {
    pub name: String,
    pub ok: bool,
    pub message: String,
}

/// A running (or finished) install/update/remove.
pub struct Job {
    pub verb: Verb,
    /// What the person asked for (the stream may also report the parts they need).
    pub names: Vec<String>,
    task: Option<Task>,
    /// Item the stream is on now.
    pub current: Option<String>,
    pub pct: u8,
    /// Index into [`STEPS`] of the step in progress (4 = all four done).
    pub step: usize,
    pub done: Vec<Done>,
    /// Exit code once the script is gone.
    pub exit: Option<i32>,
    pub cancelled: bool,
}

pub fn and_list(names: &[&str]) -> String {
    match names {
        [] => String::new(),
        [a] => a.to_string(),
        [rest @ .., last] => format!("{} and {last}", rest.join(", ")),
    }
}

fn sentence(s: &str) -> String {
    let mut c = s.chars();
    let mut out: String = match c.next() {
        Some(f) => f.to_uppercase().chain(c).collect(),
        None => return String::new(),
    };
    if !out.ends_with('.') {
        out.push('.');
    }
    out
}

impl Job {
    pub fn running(&self) -> bool {
        self.exit.is_none()
    }

    /// The requested item the header shows: the one in progress, else the next, else the last.
    pub fn shown_name(&self) -> String {
        if let Some(c) = &self.current {
            if self.names.contains(c) && !self.done.iter().any(|d| &d.name == c) {
                return c.clone();
            }
        }
        self.names
            .iter()
            .find(|n| !self.done.iter().any(|d| &d.name == *n))
            .or(self.names.last())
            .cloned()
            .unwrap_or_default()
    }

    /// Requested items still waiting behind the shown one.
    pub fn queued(&self) -> Vec<String> {
        let shown = self.shown_name();
        self.names
            .iter()
            .filter(|n| **n != shown && !self.done.iter().any(|d| &d.name == *n))
            .cloned()
            .collect()
    }

    /// The word a Front row shows for `name` while this job touches it: a step word while it
    /// is in progress, `ready` / `failed` once done (`failed` also after the job ended).
    pub fn word_for(&self, name: &str) -> Option<(&'static str, bool)> {
        if let Some(d) = self.done.iter().find(|d| d.name == name) {
            return if d.ok { self.running().then_some(("ready", false)) } else { Some(("failed", true)) };
        }
        if self.running() && self.current.as_deref() == Some(name) {
            return Some((data::step_word(self.step), false));
        }
        None
    }

    /// True when `name` was asked for and did not make it.
    pub fn failed(&self, name: &str) -> bool {
        self.done.iter().any(|d| d.name == name && !d.ok)
    }

    fn feed(&mut self, line: &str) {
        match parse_progress(line) {
            Some(Progress::Step { name, pct, words }) => {
                if self.current.as_deref() != Some(&name) {
                    self.current = Some(name);
                    self.step = 0;
                }
                self.pct = pct;
                if let Some(i) = STEPS.iter().position(|s| *s == words) {
                    self.step = if i == STEPS.len() - 1 { STEPS.len() } else { i };
                }
            }
            Some(Progress::Done { name, ok, message }) => {
                if ok && self.current.as_deref() == Some(&name) {
                    self.pct = 100;
                    self.step = STEPS.len();
                }
                self.done.push(Done { name, ok, message });
            }
            None => {}
        }
    }

    /// Plain sentences for the end of the job: what worked, what did not, kept files.
    pub fn summary(&self) -> Vec<String> {
        let ok: Vec<&str> = self
            .names
            .iter()
            .filter(|n| self.done.iter().any(|d| &d.name == *n && d.ok))
            .map(String::as_str)
            .collect();
        let failed: Vec<&str> = self
            .names
            .iter()
            .filter(|n| !self.done.iter().any(|d| &d.name == *n && d.ok))
            .map(String::as_str)
            .collect();
        let mut lines = Vec::new();
        if !ok.is_empty() {
            let many = ok.len() > 1;
            let tail = match (self.verb, many) {
                (Verb::Install, false) => "is ready",
                (Verb::Install, true) => "are ready",
                (Verb::Update, false) => "is up to date",
                (Verb::Update, true) => "are up to date",
                (Verb::Remove, false) => "was removed",
                (Verb::Remove, true) => "were removed",
            };
            lines.push(format!("{} {tail}.", and_list(&ok)));
        }
        if !failed.is_empty() {
            if self.cancelled {
                lines.push(format!("Stopped before {} was done.", and_list(&failed)));
            } else {
                lines.push(format!("Could not {} {}. Try again later.", self.verb.arg(), and_list(&failed)));
            }
        }
        for d in &self.done {
            if d.ok && d.message.starts_with("kept your") {
                lines.push(sentence(&d.message));
            }
        }
        lines
    }
}

/// gh as far as starring goes.
#[derive(Clone, Copy, Debug, PartialEq, Eq)]
pub enum Gh {
    Checking,
    Ready,
    SignedOut,
    Missing,
}

/// What the script has answered for a [`Fetch`].
#[derive(Clone, Debug, PartialEq, Eq)]
pub enum Got {
    Pending,
    Ready(PathBuf),
    Failed(i32),
}

/// An item's README as far as the script and the cache go.
#[derive(Clone, Debug)]
pub enum Readme {
    Loading,
    Doc(Rc<readme::Doc>),
    /// A setup: nothing upstream to read.
    NoUpstream,
    /// Offline with nothing cached.
    Unavailable,
}

/// The notice Front shows when `s` is pressed without a working gh.
pub const GH_NOTICE: &str = "starring needs gh: pkg install gh, then gh auth login";

/// State every view reads and changes.
pub struct Store {
    pub env: Env,
    pub cat: Catalog,
    pub job: Option<Job>,
    tasks: Vec<Task>,
    pub gh: Gh,
    /// Star state per `owner/repo`: None while asking gh.
    pub stars: HashMap<String, Option<bool>>,
    /// The launcher's keyboard is held down for us.
    pub fullscreen: bool,
    /// A one-line message on the notice row until the next key or tap.
    pub notice: Option<String>,
    /// True while the startup catalog refresh runs (a job waits for it).
    pub refreshing: bool,
    /// Star this repo as soon as gh says it is not starred yet (`s` pressed while asking).
    pub pending_star: Option<String>,
    /// Files the script fetches for the screens, by request.
    fetched: HashMap<Fetch, Got>,
    /// Parsed READMEs by item name (the fetched file, read once).
    docs: HashMap<String, Rc<readme::Doc>>,
    /// "Now" as the router's clock says (a fake clock in tests).
    pub now: Instant,
    /// The router keeps ticking until then (a header picture waiting out its rest).
    pub wake_at: Option<Instant>,
    /// The item the header shows and since when.
    header_since: Option<(String, Instant)>,
    /// The frame being drawn belongs to a view on its way out: it does not move the header.
    pub leaving: bool,
}

/// `ESC ] 99` desktop notification: title, then body.
pub fn osc99(title: &str, body: &str) -> String {
    let clean = |s: &str| s.replace(['\x1b', '\x07', ';'], " ");
    format!("\x1b]99;i=tlstore:d=0;{}\x1b\\\x1b]99;i=tlstore:p=body;{}\x1b\\", clean(title), clean(body))
}

impl Store {
    /// Loads the catalog and starts the background refresh and the gh check.
    pub fn new(env: Env) -> Store {
        let cat = Catalog::load(&env);
        let mut st = Store {
            env,
            cat,
            job: None,
            tasks: Vec::new(),
            gh: Gh::Checking,
            stars: HashMap::new(),
            fullscreen: false,
            notice: None,
            refreshing: false,
            pending_star: None,
            fetched: HashMap::new(),
            docs: HashMap::new(),
            now: Instant::now(),
            wake_at: None,
            header_since: None,
            leaving: false,
        };
        if let Ok(t) = Task::spawn(&st.env.tlstore, &["update", "--check", "--tsv"], TaskKind::Refresh) {
            st.tasks.push(t);
            st.refreshing = true;
        }
        match Task::spawn(&st.env.gh, &["auth", "status"], TaskKind::GhAuth) {
            Ok(t) => st.tasks.push(t),
            Err(_) => st.gh = Gh::Missing,
        }
        st
    }

    pub fn job_running(&self) -> bool {
        self.job.as_ref().is_some_and(Job::running)
    }

    /// Starts `tlstore <verb> --progress names…` (after the refresh, if one runs).
    pub fn start_job(&mut self, verb: Verb, names: Vec<String>) -> bool {
        if self.job_running() {
            self.notice = Some("Wait for the current one to finish.".into());
            return false;
        }
        self.job = Some(Job {
            verb,
            names,
            task: None,
            current: None,
            pct: 0,
            step: 0,
            done: Vec::new(),
            exit: None,
            cancelled: false,
        });
        if !self.refreshing {
            self.spawn_job();
        }
        true
    }

    fn spawn_job(&mut self) {
        let Some(job) = self.job.as_mut() else { return };
        if job.task.is_some() || job.exit.is_some() {
            return;
        }
        let mut args: Vec<String> = vec![job.verb.arg().into(), "--progress".into()];
        args.extend(job.names.iter().cloned());
        match Task::spawn(&self.env.tlstore, &args, TaskKind::Job) {
            Ok(t) => job.task = Some(t),
            Err(_) => {
                job.exit = Some(-1);
                self.finish_job();
            }
        }
    }

    /// Asks the running job to stop.
    pub fn cancel_job(&mut self) {
        if let Some(job) = self.job.as_mut().filter(|j| j.running()) {
            job.cancelled = true;
            match &job.task {
                Some(t) => t.terminate(),
                None => {
                    job.exit = Some(-1);
                    self.finish_job();
                }
            }
        }
    }

    fn finish_job(&mut self) {
        self.cat.reload(&self.env);
        let Some(job) = &self.job else { return };
        let lines = job.summary();
        let body = lines.first().cloned().unwrap_or_default();
        if !body.is_empty() {
            (self.env.tty_write)(&osc99("tlstore", &body));
        }
    }

    /// Fds the app loop should watch.
    pub fn watch(&self) -> Vec<RawFd> {
        let mut v: Vec<RawFd> = self.tasks.iter().filter_map(Task::fd).collect();
        if let Some(fd) = self.job.as_ref().and_then(|j| j.task.as_ref()).and_then(Task::fd) {
            v.push(fd);
        }
        v
    }

    /// Reads the task behind `fd`. Returns true when the catalog was reloaded.
    pub fn on_readable(&mut self, fd: RawFd) -> bool {
        let mut reloaded = false;
        if let Some(job) = self.job.as_mut() {
            if let Some(t) = job.task.as_mut().filter(|t| t.fd() == Some(fd)) {
                let out = t.read();
                for l in &out.lines {
                    job.feed(l);
                }
                if let Some(code) = out.exit {
                    job.exit = Some(code);
                    self.finish_job();
                    reloaded = true;
                }
                return reloaded;
            }
        }
        let Some(i) = self.tasks.iter().position(|t| t.fd() == Some(fd)) else { return false };
        let out = self.tasks[i].read();
        // Output is judged whole at exit; keep what came before it.
        self.tasks[i].lines.extend(out.lines);
        let Some(code) = out.exit else { return false };
        let task = self.tasks.remove(i);
        let lines = task.lines;
        match task.kind {
            TaskKind::Refresh => {
                self.refreshing = false;
                let updates = lines.join("\n");
                self.cat.reload(&self.env);
                if code == 0 {
                    self.cat.updates = parse_updates(&updates);
                }
                reloaded = true;
                self.spawn_job();
            }
            TaskKind::GhAuth => self.gh = if code == 0 { Gh::Ready } else { Gh::SignedOut },
            TaskKind::GhStarred(repo) => {
                self.stars.insert(repo.clone(), Some(code == 0));
                if self.pending_star.as_deref() == Some(repo.as_str()) {
                    self.pending_star = None;
                    if code != 0 {
                        self.toggle_star(&repo);
                    }
                }
            }
            TaskKind::GhStar(repo, new) => {
                if code != 0 {
                    self.stars.insert(repo, Some(!new));
                    self.notice = Some("GitHub could not be reached. Try again later.".into());
                }
            }
            TaskKind::Fetch(f) => {
                let path = lines.first().map(|l| PathBuf::from(l.trim()));
                let got = match path {
                    Some(p) if code == 0 && p.is_file() => Got::Ready(p),
                    _ => Got::Failed(code),
                };
                self.fetched.insert(f, got);
            }
            TaskKind::Job | TaskKind::Detached => {}
        }
        reloaded
    }

    /// Asks the script for `f` (once) and says what it has answered so far.
    pub fn fetch(&mut self, f: Fetch) -> Got {
        if let Some(g) = self.fetched.get(&f) {
            return g.clone();
        }
        let got = match Task::spawn(&self.env.tlstore, &f.args(), TaskKind::Fetch(f.clone())) {
            Ok(t) => {
                self.tasks.push(t);
                Got::Pending
            }
            Err(_) => Got::Failed(-1),
        };
        self.fetched.insert(f, got.clone());
        got
    }

    /// The item's catalog picture, once fetched.
    pub fn picture_path(&mut self, name: &str) -> Option<PathBuf> {
        match self.fetch(Fetch::Picture(name.to_string())) {
            Got::Ready(p) => Some(p),
            _ => None,
        }
    }

    /// The item's README, parsed under its `readme-skip` list, once fetched.
    pub fn readme(&mut self, name: &str) -> Readme {
        if let Some(d) = self.docs.get(name) {
            return Readme::Doc(d.clone());
        }
        match self.fetch(Fetch::Readme(name.to_string())) {
            Got::Pending => Readme::Loading,
            Got::Failed(2) => Readme::NoUpstream,
            Got::Failed(_) => Readme::Unavailable,
            Got::Ready(p) => {
                let text = std::fs::read_to_string(&p).unwrap_or_default();
                let skip = self.cat.info(&self.env, name).readme_skip();
                let doc = Rc::new(readme::parse(&text, &skip));
                self.docs.insert(name.to_string(), doc.clone());
                Readme::Doc(doc)
            }
        }
    }

    /// A README image, once fetched (asks the script the first time).
    pub fn asset_path(&mut self, name: &str, src: &str) -> Got {
        self.fetch(Fetch::Asset(name.to_string(), src.to_string()))
    }

    /// The header picture's file for `name`: the README's first image when it has arrived,
    /// else the catalog picture. `None` while nothing has arrived or nothing exists; the
    /// bool says whether something may still come.
    pub fn header_picture_path(&mut self, name: &str, readme_first: bool, animate: bool) -> (Option<PathBuf>, bool) {
        // The hero clip wins once it is here; until then (or without one) the still shows.
        if animate {
            if let Got::Ready(p) = self.fetch(Fetch::Demo(name.to_string())) {
                return (Some(p), false);
            }
        }
        let mut pending = false;
        if readme_first {
            let first = match self.readme(name) {
                Readme::Loading => {
                    pending = true;
                    None
                }
                Readme::Doc(d) => d.first_image.clone(),
                _ => None,
            };
            if let Some(src) = first {
                match self.asset_path(name, &src) {
                    Got::Ready(p) => return (Some(p), false),
                    Got::Pending => pending = true,
                    Got::Failed(_) => {}
                }
            }
        }
        match self.fetch(Fetch::Picture(name.to_string())) {
            Got::Ready(p) => (Some(p), pending),
            Got::Pending => (None, true),
            Got::Failed(_) => (None, pending),
        }
    }

    /// Notes that the header shows `name` (from now, when it is a new one).
    pub fn header_shown(&mut self, name: &str) -> Instant {
        match &self.header_since {
            Some((n, t)) if n == name => *t,
            _ if self.leaving => self.now,
            _ => {
                self.header_since = Some((name.to_string(), self.now));
                self.now
            }
        }
    }

    /// True once `name` has rested in the header for [`PICTURE_REST`] (at once when motion
    /// is off), so its picture may be placed; otherwise asks the router to tick until then.
    pub fn header_rested(&mut self, name: &str, motion: bool) -> bool {
        let since = self.header_shown(name);
        if !motion {
            return true;
        }
        if self.leaving {
            return self.header_since.as_ref().is_some_and(|(n, _)| n == name)
                && self.now >= since + PICTURE_REST;
        }
        let due = since + PICTURE_REST;
        if self.now >= due {
            if self.wake_at.is_some_and(|w| w <= self.now) {
                self.wake_at = None;
            }
            true
        } else {
            self.wake_at = Some(match self.wake_at {
                Some(w) => w.min(due),
                None => due,
            });
            false
        }
    }

    /// True while a background task other than a job runs (a fetch, a gh call).
    pub fn tasks_pending(&self) -> bool {
        !self.tasks.is_empty()
    }

    /// The item the header shows.
    pub fn header_item(&self) -> Option<&str> {
        self.header_since.as_ref().map(|(n, _)| n.as_str())
    }

    /// The facts strip for `name`; `state` overrides the installed word (a running verb,
    /// `failed`).
    pub fn facts(&mut self, name: &str, state: Option<&str>) -> Facts {
        let item = self.cat.item(name).cloned();
        let upd = self.cat.update_for(name).cloned();
        let info = self.cat.info(&self.env, name).clone();
        let version = match (&upd, &item) {
            (Some(u), _) => u.have.clone(),
            (None, Some(i)) => i.installed.clone().unwrap_or_else(|| i.version.clone()),
            _ => String::new(),
        };
        let state = state
            .map(str::to_string)
            .or_else(|| item.as_ref().and_then(|i| i.installed.as_ref()).map(|_| "installed".to_string()));
        let mut more = Vec::new();
        for k in ["Licence", "Author", "Size"] {
            if let Some(v) = info.get(k) {
                more.push(v.to_string());
            }
        }
        if let Some(repo) = info.upstream() {
            if self.gh == Gh::Ready && self.starred(repo) == Some(true) {
                more.push("starred".into());
            }
        }
        Facts { state, version, new: upd.map(|u| u.new), more }
    }

    /// Starts asking gh whether `repo` is starred (once per session).
    pub fn want_star(&mut self, repo: &str) {
        if self.gh != Gh::Ready || self.stars.contains_key(repo) {
            return;
        }
        let path = format!("/user/starred/{repo}");
        if let Ok(t) = Task::spawn(&self.env.gh, &["api", path.as_str()], TaskKind::GhStarred(repo.into())) {
            self.tasks.push(t);
            self.stars.insert(repo.into(), None);
        }
    }

    pub fn starred(&self, repo: &str) -> Option<bool> {
        self.stars.get(repo).copied().flatten()
    }

    /// Answers "is gh signed in" now, waiting for it if the startup check is still running.
    pub fn gh_now(&mut self) -> Gh {
        if self.gh == Gh::Checking {
            if let Some(i) = self.tasks.iter().position(|t| t.kind == TaskKind::GhAuth) {
                let mut t = self.tasks.remove(i);
                let out = t.finish();
                self.gh = if out.exit == Some(0) { Gh::Ready } else { Gh::SignedOut };
            }
        }
        self.gh
    }

    /// Stars or unstars `repo` through gh (optimistically; reverted if gh fails). Only when
    /// gh is ready and the state is known.
    pub fn toggle_star(&mut self, repo: &str) {
        let Some(cur) = self.starred(repo) else { return };
        let new = !cur;
        let path = format!("/user/starred/{repo}");
        let method = if new { "PUT" } else { "DELETE" };
        match Task::spawn(
            &self.env.gh,
            &["api", "-X", method, path.as_str()],
            TaskKind::GhStar(repo.into(), new),
        ) {
            Ok(t) => {
                self.tasks.push(t);
                self.stars.insert(repo.into(), Some(new));
            }
            Err(_) => self.gh = Gh::Missing,
        }
    }

    /// The `s` key on an item: star through gh, or say what gh needs on the notice row.
    /// True when a star was asked for.
    pub fn star(&mut self, name: &str) -> bool {
        let Some(repo) = self.cat.info(&self.env, name).upstream().map(str::to_string) else { return false };
        match self.gh_now() {
            Gh::Ready => {
                self.want_star(&repo);
                if self.starred(&repo).is_some() {
                    self.toggle_star(&repo);
                } else {
                    self.pending_star = Some(repo);
                }
                true
            }
            _ => {
                self.notice = Some(GH_NOTICE.into());
                false
            }
        }
    }

    /// Opens `url` with the opener, or shows the address.
    pub fn open_url(&mut self, url: &str) {
        let started = self
            .env
            .opener
            .clone()
            .and_then(|o| Task::spawn(&o, &[url], TaskKind::Detached).ok())
            .map(|t| self.tasks.push(t))
            .is_some();
        if !started {
            self.notice = Some(url.to_string());
        }
    }

    /// Opens https://github.com/<repo>.
    pub fn open_repo(&mut self, repo: &str) {
        self.open_url(&format!("https://github.com/{repo}"));
    }

    /// `f`: asks the launcher to hold its keyboard down, or to bring it back.
    pub fn toggle_fullscreen(&mut self) {
        let Some(lc) = self.env.launcherctl.clone() else { return };
        let args: &[&str] =
            if self.fullscreen { &["keyboard", "show"] } else { &["keyboard", "hide", "--hold"] };
        if matches!(self.env.run(&lc, args), Ok((0, _))) {
            self.fullscreen = !self.fullscreen;
        } else {
            self.notice = Some("The keyboard could not be put away here.".into());
        }
    }
}

/// The header for `name` with its picture fitted: asks for the picture off the draw path,
/// reserves its rows while it may still come (`reserve`), clamps them to the picture's own
/// height once it is here, and only places it after the item has rested in the header. With
/// `animate` (and motion on), an APNG picture plays: the still is placed first and its
/// frames follow in place (`Renderer::stream`).
pub fn header_for(
    p: &mut Paint,
    st: &mut Store,
    name: &str,
    body_need: u16,
    readme_first: bool,
    reserve: bool,
    animate: bool,
) -> (Header, Option<Picture>) {
    let (cols, rows) = (p.f.cols(), p.f.rows());
    let (cw, ch) = p.cell();
    if name.is_empty() {
        return (layout::header(cols, rows, body_need, None), None);
    }
    st.header_shown(name);
    if !p.f.ctx.caps.kitty_graphics {
        return (layout::header(cols, rows, body_need, None), None);
    }
    let animate = animate && p.f.ctx.motion;
    let (path, pending) = st.header_picture_path(name, readme_first, animate);
    let rested = st.header_rested(name, p.f.ctx.motion);
    let Some(path) = path else {
        let pic_rows = (pending || reserve).then_some(u16::MAX);
        return (layout::header(cols, rows, body_need, pic_rows), None);
    };
    let probe_hdr = layout::header(cols, rows, body_need, Some(u16::MAX));
    let box_w = probe_hdr.content.w as u32 * cw as u32;
    let box_h = PICTURE_MAX_ROWS as u32 * ch as u32;
    // The probe is a still: it only measures, and may never be placed.
    let probe = p.f.ctx.pics.header_picture(&path, box_w, box_h, false).ok();
    let Some(probe) = probe else {
        let pic_rows = reserve.then_some(u16::MAX);
        return (layout::header(cols, rows, body_need, pic_rows), None);
    };
    let own_rows = if reserve { u16::MAX } else { probe.height().div_ceil(ch as u32).max(1) as u16 };
    let hdr = layout::header(cols, rows, body_need, Some(own_rows));
    let Some(r) = hdr.picture else { return (hdr, None) };
    if !rested {
        return (hdr, None);
    }
    let (bw, bh) = (r.w as u32 * cw as u32, r.h as u32 * ch as u32);
    let pic = if probe.width() <= bw && probe.height() <= bh {
        if animate {
            p.f.ctx.pics.header_picture(&path, box_w, box_h, true).ok()
        } else {
            Some(probe)
        }
    } else {
        p.f.ctx.pics.header_picture(&path, bw, bh, animate).ok()
    };
    (hdr, pic)
}

/// Hooks for the preview renderer (`--shot`): a job at a given percentage without running
/// the script, and a way onto the Installing view without a key press.
#[cfg(feature = "shot")]
impl Store {
    /// Pretends `tlstore <verb> --progress <name>` is at `pct` percent (100 = finished, ok).
    /// Replaces any job; nothing is spawned, and nothing is watched.
    pub fn fake_job(&mut self, verb: Verb, name: &str, pct: u8) {
        let pct = pct.min(100);
        let step = match pct {
            0..=9 => 0,
            10..=59 => 1,
            60..=89 => 2,
            90..=99 => 3,
            _ => STEPS.len(),
        };
        let finished = pct == 100;
        let message = match verb {
            Verb::Install => "installed",
            Verb::Update => "updated",
            Verb::Remove => "removed",
        };
        self.job = Some(Job {
            verb,
            names: vec![name.to_string()],
            task: None,
            current: Some(name.to_string()),
            pct,
            step,
            done: if finished {
                vec![Done { name: name.to_string(), ok: true, message: message.into() }]
            } else {
                vec![]
            },
            exit: finished.then_some(0),
            cancelled: false,
        });
    }
}

#[cfg(feature = "shot")]
impl Router {
    /// Pushes the Installing view (the preview renderer's way in; a person gets there with
    /// `i`, `u` or `r`).
    pub fn show_installing(&mut self) {
        self.navigate(Go::Push(Box::new(installing::Installing::new())));
    }
}

enum Leave {
    None,
    /// The view under the top of the stack (after a Push).
    Below,
    Owned(Box<dyn View>),
}

/// The one `app::Screen`: shared frame, view stack, tasks, navigation.
pub struct Router {
    pub st: Store,
    views: Vec<Box<dyn View>>,
    leave: Leave,
    motion: Box<dyn Motion>,
    /// The current view's last drawn scene, and the leaving view's.
    pub scene: Scene,
    leave_scene: Scene,
    slots: Slots,
    motion_on: bool,
    /// Where "now" comes from (a fake clock in tests).
    clock: Box<dyn Fn() -> Instant>,
}

impl Router {
    /// With no motion at all (every navigation instant).
    pub fn new(env: Env) -> Router {
        Router::with_motion(env, Box::new(NoMotion))
    }

    /// With the store's motion ([`motion::Timeline`]); `Ctx::motion` still turns it off.
    pub fn animated(env: Env) -> Router {
        Router::with_motion(env, Box::new(motion::Timeline::new()))
    }

    /// Replaces the clock motion reads (tests drive transitions frame by frame with it).
    pub fn set_clock(&mut self, clock: impl Fn() -> Instant + 'static) {
        self.clock = Box::new(clock);
    }

    /// With a motion timeline.
    pub fn with_motion(env: Env, motion: Box<dyn Motion>) -> Router {
        Router {
            st: Store::new(env),
            views: vec![Box::new(front::Front::new())],
            leave: Leave::None,
            motion,
            scene: Scene::default(),
            leave_scene: Scene::default(),
            slots: Default::default(),
            motion_on: true,
            clock: Box::new(Instant::now),
        }
    }

    /// Name of the view on top.
    pub fn top(&self) -> &'static str {
        self.views.last().map(|v| v.name()).unwrap_or("")
    }

    pub fn depth(&self) -> usize {
        self.views.len()
    }

    /// The item the header shows (Front: the one under the cursor).
    pub fn header_item(&self) -> Option<String> {
        self.st.header_item().map(str::to_string)
    }

    /// The one place navigation happens.
    fn navigate(&mut self, go: Go) -> Nav {
        let now = (self.clock)();
        let (kind, leave) = match go {
            Go::Stay | Go::Pass => return Nav::Stay,
            Go::Quit => return Nav::Quit,
            Go::Push(v) => {
                self.views.push(v);
                (NavKind::Push, Leave::Below)
            }
            Go::Replace(v) => {
                let old = self.views.pop();
                self.views.push(v);
                (NavKind::Replace, old.map_or(Leave::None, Leave::Owned))
            }
            Go::Back => {
                if self.views.len() <= 1 {
                    return Nav::Quit;
                }
                let old = self.views.pop();
                (NavKind::Pop, old.map_or(Leave::None, Leave::Owned))
            }
            Go::Home => {
                if self.views.len() <= 1 {
                    return Nav::Stay;
                }
                let old = self.views.pop();
                self.views.truncate(1);
                (NavKind::Home, old.map_or(Leave::None, Leave::Owned))
            }
        };
        let to = self.top();
        if self.motion_on {
            let from = std::mem::take(&mut self.scene);
            self.motion.navigate(kind, &from, to, now);
            self.leave_scene = from;
            self.leave = leave;
        }
        self.scene = Scene::new(to);
        Nav::Stay
    }

    fn global_key(&mut self, k: Key) -> Go {
        match k {
            Key::Char('f') if self.st.env.launcherctl.is_some() => {
                self.st.toggle_fullscreen();
                Go::Stay
            }
            Key::Char('q') => Go::Quit,
            Key::Esc => Go::Back,
            _ => Go::Stay,
        }
    }

    /// Draws view `which` (None: the owned leaving view). `leaving`: the frame belongs to the
    /// view being left, so its scene goes to `leave_scene` and the current scene stays as is.
    fn draw_view(&mut self, f: &mut Frame, which: Option<usize>, fx: &Fx, leaving: bool) {
        let hdr = layout::header(f.cols(), f.rows(), 0, None);
        let notice = self.st.notice.clone();
        let st = &mut self.st;
        let view: &mut Box<dyn View> = match which {
            Some(idx) => &mut self.views[idx],
            None => match &mut self.leave {
                Leave::Owned(v) => v,
                _ => return,
            },
        };
        let mut scene = Scene::new(view.name());
        let mut p = Paint::new(f, fx, &mut scene);
        st.leaving = leaving;
        view.draw(&mut p, st);
        st.leaving = false;
        if let Some(n) = &notice {
            draw_notice(&mut p, &hdr, n);
        }
        let slots = view.keys(st);
        draw_keys(&mut p, &hdr, &slots);
        if leaving {
            self.leave_scene = scene;
        } else {
            self.scene = scene;
            self.slots = slots;
        }
    }

    /// Draws one frame in whatever phase motion says.
    fn draw_phase(&mut self, f: &mut Frame, now: Instant) {
        let top = self.views.len() - 1;
        let phase = if self.motion_on && self.motion.active() {
            self.motion.frame(now, &self.scene)
        } else {
            Phase::Idle
        };
        match phase {
            Phase::Leaving(fx) => match self.leave {
                Leave::Below if top > 0 => self.draw_view(f, Some(top - 1), &fx, true),
                Leave::Owned(_) => self.draw_view(f, None, &fx, true),
                _ => self.draw_view(f, Some(top), &fx, false),
            },
            Phase::Entering(fx) => {
                self.leave = Leave::None;
                self.draw_view(f, Some(top), &fx, false);
            }
            Phase::Idle => {
                self.leave = Leave::None;
                self.draw_view(f, Some(top), &Fx::default(), false);
            }
        }
    }
}

impl Screen for Router {
    fn draw(&mut self, f: &mut Frame) {
        self.motion_on = f.ctx.motion;
        let now = (self.clock)();
        self.st.now = now;
        if self.st.wake_at.is_some_and(|w| now >= w) {
            self.st.wake_at = None;
        }
        self.draw_phase(f, now);
        if self.motion_on && self.motion.drawn(now, &self.scene) {
            f.clear();
            self.draw_phase(f, now);
        }
    }

    fn handle(&mut self, ev: &Event, ctx: &mut Ctx) -> Nav {
        self.motion_on = ctx.motion;
        self.st.now = (self.clock)();
        match ev {
            Event::Readable(fd) => {
                if self.st.on_readable(*fd) {
                    for v in &mut self.views {
                        v.refresh(&self.st);
                    }
                    if let Some(job) = self.st.job.as_ref().filter(|j| !j.running()) {
                        let lines = job.summary();
                        self.st.notice = if self.top() == "installing" {
                            lines
                                .iter()
                                .find(|l| l.starts_with("Could not") || l.starts_with("Stopped"))
                                .cloned()
                        } else {
                            lines.first().cloned()
                        };
                    }
                }
                return Nav::Stay;
            }
            Event::Resize(_) => return Nav::Stay,
            Event::Key(_) | Event::Tap { .. } => self.st.notice = None,
            _ => {}
        }
        let ev = match ev {
            Event::Tap { action: A_HOME, .. } if self.top() != "front" => return self.navigate(Go::Home),
            Event::Tap { action, .. } if (A_KEY0..A_KEY0 + 5).contains(action) => {
                match self.slots.get((*action - A_KEY0) as usize).and_then(|s| s.as_ref()).filter(|s| s.on) {
                    Some(s) => Event::Key(s.key),
                    None => return Nav::Stay,
                }
            }
            other => *other,
        };
        let top = self.views.len() - 1;
        let go = self.views[top].handle(&ev, &mut self.st);
        let go = match (go, ev) {
            (Go::Pass, Event::Key(k)) => self.global_key(k),
            (go, _) => go,
        };
        self.navigate(go)
    }

    fn animating(&self) -> bool {
        self.motion.active() || self.st.wake_at.is_some()
    }

    fn tick(&mut self, _now: Instant, _ctx: &mut Ctx) -> bool {
        let now = (self.clock)();
        if self.st.wake_at.is_some_and(|w| now >= w) {
            self.st.wake_at = None;
            return true;
        }
        self.motion.active()
    }

    fn watch(&self) -> Vec<RawFd> {
        self.st.watch()
    }
}

impl Drop for Router {
    fn drop(&mut self) {
        if self.st.fullscreen {
            if let Some(lc) = self.st.env.launcherctl.clone() {
                let _ = self.st.env.run(&lc, &["keyboard", "show"]);
            }
            self.st.fullscreen = false;
        }
        // Leaving mid-install: let it finish (its output still has a reader) rather than cut
        // it off halfway.
        if let Some(t) = self.st.job.as_mut().and_then(|j| j.task.as_mut()).filter(|t| !t.finished()) {
            eprintln!("tlstore: finishing what was started; this takes a moment.");
            let _ = t.finish();
        }
    }
}
