//! The store's screens. One [`Router`] is the only `app::Screen`: it owns the shared state
//! ([`Store`]), a stack of [`View`]s, the background tasks, and the single place navigation
//! happens (where [`scene::Motion`] gets its leave/enter hooks).

pub mod apps;
pub mod data;
pub mod installing;
pub mod item;
pub mod motion;
pub mod nogh;
pub mod paint;
pub mod proc;
pub mod scene;
pub mod updates;

use std::collections::HashMap;
use std::os::fd::RawFd;
use std::time::Instant;

use crate::app::{Ctx, Nav, Screen};
use crate::render::Frame;
use crate::term::{Event, Key};

use data::{parse_progress, parse_updates, Catalog, Progress, STEPS};
use paint::{draw_chrome, Chrome, ContextItem, Hint, Paint, A_CONTEXT, A_HOME, A_KEY0};
use proc::{Env, Task, TaskKind};
use scene::{Fx, Motion, NavKind, NoMotion, Phase, Scene};

/// What a view wants after an event.
pub enum Go {
    Stay,
    /// Not mine: the router may use it (`f` fullscreen, `q` quit).
    Pass,
    Push(Box<dyn View>),
    Replace(Box<dyn View>),
    Back,
    Home,
    Quit,
}

/// One screen of the store, drawn inside the shared frame.
pub trait View {
    /// "apps", "item", "updates", "installing", "nogh".
    fn name(&self) -> &'static str;
    /// Breadcrumb, context item, hero and key hints.
    fn chrome(&mut self, st: &mut Store) -> Chrome;
    /// The content between the hero and the key row.
    fn body(&mut self, p: &mut Paint, st: &mut Store, r: &crate::layout::Regions);
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
    /// The hero lead and breadcrumb word.
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

    /// 1-based position of the requested item being worked on.
    pub fn index(&self) -> usize {
        let done = self.names.iter().filter(|n| self.done.iter().any(|d| &d.name == *n)).count();
        (done + usize::from(self.running())).clamp(1, self.names.len().max(1))
    }

    /// The requested item the hero shows: the one in progress, else the next, else the last.
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
    /// A one-line message above the key row until the next key or tap.
    pub notice: Option<String>,
    /// True while the startup catalog refresh runs (a job waits for it).
    pub refreshing: bool,
    refresh_buf: String,
    /// Star this repo as soon as gh says it is not starred yet (`s` pressed while asking).
    pub pending_star: Option<String>,
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
            refresh_buf: String::new(),
            pending_star: None,
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
        let buf = out.lines.join("\n");
        let kind = self.tasks[i].kind.clone();
        if out.exit.is_none() {
            // Keep partial output of a refresh until it ends.
            if kind == TaskKind::Refresh && !out.lines.is_empty() {
                self.stash_refresh(&buf);
            }
            return false;
        }
        let code = out.exit.unwrap_or(-1);
        self.tasks.remove(i);
        match kind {
            TaskKind::Refresh => {
                self.refreshing = false;
                self.stash_refresh(&buf);
                let updates = std::mem::take(&mut self.refresh_buf);
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
            TaskKind::Job | TaskKind::Detached => {}
        }
        reloaded
    }

    fn stash_refresh(&mut self, s: &str) {
        if !s.is_empty() {
            self.refresh_buf.push_str(s);
            self.refresh_buf.push('\n');
        }
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

    /// Checks gh again (after the person signed in elsewhere).
    pub fn recheck_gh(&mut self) -> Gh {
        self.gh = match self.env.run(&self.env.gh, &["auth", "status"]) {
            Ok((0, _)) => Gh::Ready,
            Ok(_) => Gh::SignedOut,
            Err(_) => Gh::Missing,
        };
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

    /// Opens https://github.com/<repo> with the opener, or shows the address.
    pub fn open_repo(&mut self, repo: &str) {
        let url = format!("https://github.com/{repo}");
        let started = self
            .env
            .opener
            .clone()
            .and_then(|o| Task::spawn(&o, &[url.as_str()], TaskKind::Detached).ok())
            .map(|t| self.tasks.push(t))
            .is_some();
        if !started {
            self.notice = Some(url);
        }
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

    /// Context item for "↑ N updates" (None when there are none).
    pub fn updates_link(&self) -> Option<ContextItem> {
        let n = self.cat.updates.len();
        (n > 0).then(|| ContextItem {
            text: format!("↑ {n} update{}", if n == 1 { "" } else { "s" }),
            link: true,
        })
    }
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
    hints: Vec<Hint>,
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

    /// With a motion timeline (P5).
    pub fn with_motion(env: Env, motion: Box<dyn Motion>) -> Router {
        Router {
            st: Store::new(env),
            views: vec![Box::new(apps::Apps::new())],
            leave: Leave::None,
            motion,
            scene: Scene::default(),
            leave_scene: Scene::default(),
            hints: Vec::new(),
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

    fn chrome_for(&mut self, idx: usize) -> Chrome {
        let st = &mut self.st;
        let v = &mut self.views[idx];
        let mut c = v.chrome(st);
        if let Some(job) = st.job.as_ref().filter(|j| j.running()) {
            if v.name() != "installing" {
                c.context = Some(ContextItem {
                    text: format!("{} {} of {}", job.verb.ing(), job.index(), job.names.len()),
                    link: true,
                });
            }
        }
        c
    }

    /// Draws view `which` (None: the owned leaving view). `leaving`: the frame belongs to the
    /// view being left, so its scene goes to `leave_scene` and the current scene stays as is.
    fn draw_view(&mut self, f: &mut Frame, which: Option<usize>, fx: &Fx, leaving: bool) {
        let r = f.regions();
        let notice = self.st.notice.clone();
        match which {
            Some(idx) => {
                let chrome = self.chrome_for(idx);
                let mut scene = Scene::new(self.views[idx].name());
                let mut p = Paint::new(f, fx, &mut scene);
                self.hints = draw_chrome(&mut p, &r, &chrome, notice.as_deref());
                self.views[idx].body(&mut p, &mut self.st, &r);
                if leaving {
                    self.leave_scene = scene;
                } else {
                    self.scene = scene;
                }
            }
            None => {
                let Leave::Owned(v) = &mut self.leave else { return };
                let chrome = v.chrome(&mut self.st);
                let mut scene = Scene::new(v.name());
                let mut p = Paint::new(f, fx, &mut scene);
                draw_chrome(&mut p, &r, &chrome, None);
                v.body(&mut p, &mut self.st, &r);
                self.leave_scene = scene;
            }
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
        self.draw_phase(f, now);
        if self.motion_on && self.motion.drawn(now, &self.scene) {
            f.clear();
            self.draw_phase(f, now);
        }
    }

    fn handle(&mut self, ev: &Event, ctx: &mut Ctx) -> Nav {
        self.motion_on = ctx.motion;
        match ev {
            Event::Readable(fd) => {
                if self.st.on_readable(*fd) {
                    for v in &mut self.views {
                        v.refresh(&self.st);
                    }
                    if self.top() != "installing" {
                        if let Some(job) = self.st.job.as_ref().filter(|j| !j.running()) {
                            self.st.notice = job.summary().first().cloned();
                        }
                    }
                }
                return Nav::Stay;
            }
            Event::Resize(_) => return Nav::Stay,
            Event::Key(_) | Event::Tap { .. } => self.st.notice = None,
            _ => {}
        }
        let ev = match ev {
            Event::Tap { action: A_HOME, .. } => return self.navigate(Go::Home),
            Event::Tap { action: A_CONTEXT, .. } => {
                let go = if self.st.job_running() && self.top() != "installing" {
                    Go::Push(Box::new(installing::Installing::new()))
                } else if !self.st.cat.updates.is_empty() && self.top() != "updates" {
                    Go::Push(Box::new(updates::Updates::new()))
                } else {
                    Go::Stay
                };
                return self.navigate(go);
            }
            Event::Tap { action, .. } if (A_KEY0..A_KEY0 + 20).contains(action) => {
                match self.hints.get((*action - A_KEY0) as usize) {
                    Some(h) => Event::Key(h.key),
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
        self.motion.active()
    }

    fn tick(&mut self, _now: Instant, _ctx: &mut Ctx) -> bool {
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
