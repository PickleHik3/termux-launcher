//! The programs the store calls: the `tlstore` script, `gh`, `launcherctl`, the URL opener.
//! Short questions run to completion ([`Env::run`]); long ones run as a [`Task`] whose stdout
//! the app loop watches.

use std::cell::RefCell;
use std::ffi::{OsStr, OsString};
use std::io::{self, Read, Write};
use std::os::fd::{AsRawFd, RawFd};
use std::os::unix::process::{CommandExt, ExitStatusExt};
use std::path::{Path, PathBuf};
use std::process::{Child, ChildStdout, Command, Stdio};
use std::rc::Rc;

/// Where the programs are and where terminal-side messages (OSC 99 notices) go.
pub struct Env {
    /// The tlstore script (`$TLSTORE`, else `tlstore` on PATH).
    pub tlstore: OsString,
    /// `gh` (`$TLSTORE_GH`, else `gh`); a failed spawn means gh is missing.
    pub gh: OsString,
    /// `launcherctl` when present (`$TLSTORE_LAUNCHERCTL`, else PATH); None hides fullscreen.
    pub launcherctl: Option<OsString>,
    /// URL opener when present (`$TLSTORE_OPEN`, else `termux-open-url` on PATH).
    pub opener: Option<OsString>,
    /// Writes an escape sequence straight to the terminal (outside the frame diff).
    pub tty_write: Box<dyn FnMut(&str)>,
}

/// Looks `name` up on PATH (a name with a slash is taken as a path).
pub fn which(name: &OsStr) -> Option<PathBuf> {
    let p = Path::new(name);
    if p.components().count() > 1 {
        return p.is_file().then(|| p.to_path_buf());
    }
    let path = std::env::var_os("PATH")?;
    std::env::split_paths(&path).map(|d| d.join(name)).find(|c| c.is_file())
}

impl Env {
    /// The real environment.
    pub fn from_env() -> Env {
        let var = |k: &str| std::env::var_os(k).filter(|v| !v.is_empty());
        let tlstore = var("TLSTORE").unwrap_or_else(|| "tlstore".into());
        let gh = var("TLSTORE_GH").unwrap_or_else(|| "gh".into());
        let launcherctl =
            var("TLSTORE_LAUNCHERCTL").or_else(|| Some("launcherctl".into())).filter(|p| which(p).is_some());
        let opener =
            var("TLSTORE_OPEN").or_else(|| Some("termux-open-url".into())).filter(|p| which(p).is_some());
        Env {
            tlstore,
            gh,
            launcherctl,
            opener,
            tty_write: Box::new(|s: &str| {
                if let Ok(mut t) = std::fs::OpenOptions::new().write(true).open("/dev/tty") {
                    let _ = t.write_all(s.as_bytes());
                }
            }),
        }
    }

    /// An environment for tests: programs by path, terminal writes collected in `sink`.
    pub fn for_tests(
        tlstore: &Path,
        gh: &Path,
        launcherctl: Option<&Path>,
        opener: Option<&Path>,
        sink: Rc<RefCell<Vec<String>>>,
    ) -> Env {
        Env {
            tlstore: tlstore.into(),
            gh: gh.into(),
            launcherctl: launcherctl.map(Into::into),
            opener: opener.map(Into::into),
            tty_write: Box::new(move |s: &str| sink.borrow_mut().push(s.to_string())),
        }
    }

    /// Runs `prog args…` to the end; returns (exit code, stdout). -1 for a signal. Errs when
    /// the program cannot start.
    pub fn run<S: AsRef<OsStr>>(&self, prog: &OsStr, args: &[S]) -> io::Result<(i32, String)> {
        let out = Command::new(prog)
            .args(args)
            .stdin(Stdio::null())
            .stderr(Stdio::null())
            .stdout(Stdio::piped())
            .output()?;
        Ok((out.status.code().unwrap_or(-1), String::from_utf8_lossy(&out.stdout).into_owned()))
    }

    /// `tlstore args…`, stdout on success.
    pub fn script<S: AsRef<OsStr>>(&self, args: &[S]) -> io::Result<String> {
        let (code, out) = self.run(&self.tlstore, args)?;
        if code == 0 {
            Ok(out)
        } else {
            Err(io::Error::other(format!("tlstore exited {code}")))
        }
    }
}

/// Something the script fetches for a screen, off the draw path: the answer is a file path
/// on the task's first output line.
#[derive(Clone, Debug, PartialEq, Eq, Hash)]
pub enum Fetch {
    /// `tlstore picture <name>`: the catalog picture.
    Picture(String),
    /// `tlstore picture <name> demo`: the catalog hero clip (an APNG), when it has one.
    Demo(String),
    /// `tlstore readme <name>`: the cached upstream README.
    Readme(String),
    /// `tlstore readme-asset <name> <src>`: an image the README refers to.
    Asset(String, String),
}

impl Fetch {
    pub fn args(&self) -> Vec<&str> {
        match self {
            Fetch::Picture(n) => vec!["picture", n],
            Fetch::Demo(n) => vec!["picture", n, "demo"],
            Fetch::Readme(n) => vec!["readme", n],
            Fetch::Asset(n, src) => vec!["readme-asset", n, src],
        }
    }
}

/// What a background task is for; the router routes its result by this.
#[derive(Clone, Debug, PartialEq, Eq)]
pub enum TaskKind {
    /// `tlstore install|update|remove --progress …`
    Job,
    /// `tlstore update --check --tsv` (refreshes the catalog first).
    Refresh,
    /// `gh auth status`
    GhAuth,
    /// `gh api /user/starred/<repo>`
    GhStarred(String),
    /// `gh api -X PUT|DELETE /user/starred/<repo>`; the bool is the new state.
    GhStar(String, bool),
    /// A file the script fetches for a screen.
    Fetch(Fetch),
    /// A program started and forgotten (the URL opener); only reaped.
    Detached,
}

/// A child whose stdout is read without blocking, line by line.
pub struct Task {
    pub kind: TaskKind,
    /// Complete lines read so far, for tasks whose whole output is judged at exit.
    pub lines: Vec<String>,
    child: Child,
    out: Option<ChildStdout>,
    buf: Vec<u8>,
    status: Option<i32>,
}

/// What a read of a task produced.
#[derive(Debug, Default, PartialEq, Eq)]
pub struct Output {
    pub lines: Vec<String>,
    /// Exit code once the child is gone (-1 = killed by a signal).
    pub exit: Option<i32>,
}

impl Task {
    /// Starts `prog args…` in its own process group with stdout piped and non-blocking.
    pub fn spawn<S: AsRef<OsStr>>(prog: &OsStr, args: &[S], kind: TaskKind) -> io::Result<Task> {
        let mut child = Command::new(prog)
            .args(args)
            .stdin(Stdio::null())
            .stderr(Stdio::null())
            .stdout(Stdio::piped())
            .process_group(0)
            .spawn()?;
        let out = child.stdout.take();
        if let Some(o) = &out {
            let fd = o.as_raw_fd();
            // SAFETY: fcntl on a pipe fd this process owns.
            unsafe {
                let fl = libc::fcntl(fd, libc::F_GETFL);
                libc::fcntl(fd, libc::F_SETFL, fl | libc::O_NONBLOCK);
            }
        }
        Ok(Task { kind, lines: Vec::new(), child, out, buf: Vec::new(), status: None })
    }

    /// The fd to watch (None once stdout hit EOF).
    pub fn fd(&self) -> Option<RawFd> {
        self.out.as_ref().map(|o| o.as_raw_fd())
    }

    pub fn finished(&self) -> bool {
        self.status.is_some()
    }

    pub fn pid(&self) -> u32 {
        self.child.id()
    }

    /// Reads what is there. Complete lines come back; at EOF the rest of the buffer is a last
    /// line and the child is reaped.
    pub fn read(&mut self) -> Output {
        let mut res = Output::default();
        let mut eof = false;
        if let Some(o) = self.out.as_mut() {
            let mut chunk = [0u8; 4096];
            loop {
                match o.read(&mut chunk) {
                    Ok(0) => {
                        eof = true;
                        break;
                    }
                    Ok(n) => self.buf.extend_from_slice(&chunk[..n]),
                    Err(e) if e.kind() == io::ErrorKind::Interrupted => continue,
                    Err(e) if e.kind() == io::ErrorKind::WouldBlock => break,
                    Err(_) => {
                        eof = true;
                        break;
                    }
                }
            }
        } else if self.status.is_none() {
            eof = true;
        }
        while let Some(i) = self.buf.iter().position(|&b| b == b'\n') {
            let line: Vec<u8> = self.buf.drain(..=i).collect();
            res.lines.push(String::from_utf8_lossy(&line[..line.len() - 1]).into_owned());
        }
        if eof {
            if !self.buf.is_empty() {
                res.lines.push(String::from_utf8_lossy(&std::mem::take(&mut self.buf)).into_owned());
            }
            self.out = None;
            let code = match self.child.wait() {
                Ok(s) => s.code().unwrap_or_else(|| if s.signal().is_some() { -1 } else { 0 }),
                Err(_) => -1,
            };
            self.status = Some(code);
            res.exit = Some(code);
        }
        res
    }

    /// Asks the whole process group to stop (SIGTERM).
    pub fn terminate(&self) {
        if self.status.is_none() {
            // SAFETY: signalling our own child's process group.
            unsafe {
                libc::kill(-(self.child.id() as libc::pid_t), libc::SIGTERM);
            }
        }
    }

    /// Blocks until the child exits, reading and returning everything it still prints.
    pub fn finish(&mut self) -> Output {
        let mut all = Output::default();
        while self.status.is_none() {
            if let Some(fd) = self.fd() {
                let _ =
                    crate::term::poll_fds(&[(fd, libc::POLLIN)], Some(std::time::Duration::from_millis(200)));
            }
            let o = self.read();
            all.lines.extend(o.lines);
            all.exit = o.exit;
        }
        all
    }
}
