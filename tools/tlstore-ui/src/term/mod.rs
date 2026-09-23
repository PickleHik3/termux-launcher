//! The terminal: raw mode, alternate screen, mouse and resize modes, signals, and restoring
//! everything on the way out (normal exit, panic, SIGINT/SIGTERM/SIGHUP).

pub mod input;
pub mod probe;

use std::io;
use std::os::fd::RawFd;
use std::sync::atomic::{AtomicBool, AtomicI32, AtomicU32, Ordering};
use std::sync::Mutex;
use std::time::Duration;

pub use input::{Event, Key, Mouse, MouseKind, Parser, Reply, Size};
pub use probe::{probe, Caps};

/// What the probe and the app loop need from a terminal; a fake implements it in tests.
pub trait TermIo {
    fn write_all(&mut self, bytes: &[u8]) -> io::Result<()>;
    /// Reads what is available, waiting at most `timeout`. Returns 0 on timeout.
    fn read_timeout(&mut self, buf: &mut [u8], timeout: Duration) -> io::Result<usize>;
}

/// Written on entry after the probe: alternate screen is already on.
const ENTER_MODES: &str = "\x1b[?25l\x1b[?7l\x1b[?1000h\x1b[?1002h\x1b[?1006h";
const RESIZE_ON: &str = "\x1b[?2048h";
/// Undoes everything, in reverse: modes off, pictures gone, pen reset, wrap and cursor back,
/// main screen.
const LEAVE: &str = "\x1b[?2048l\x1b[?1006l\x1b[?1002l\x1b[?1000l\x1b[?2026l\x1b_Ga=d,d=A,q=2\x1b\\\x1b[0m\x1b[?7h\x1b[?25h\x1b[?1049l";

struct Saved {
    fd: RawFd,
    termios: libc::termios,
}

static SAVED: Mutex<Option<Saved>> = Mutex::new(None);
static RESTORED: AtomicBool = AtomicBool::new(true);

fn write_fd(fd: RawFd, mut b: &[u8]) -> io::Result<()> {
    while !b.is_empty() {
        // SAFETY: b points to valid memory of b.len() bytes.
        let n = unsafe { libc::write(fd, b.as_ptr().cast(), b.len()) };
        if n < 0 {
            let e = io::Error::last_os_error();
            match e.kind() {
                io::ErrorKind::Interrupted => continue,
                io::ErrorKind::WouldBlock => {
                    poll_fds(&[(fd, libc::POLLOUT)], Some(Duration::from_millis(50)))?;
                    continue;
                }
                _ => return Err(e),
            }
        }
        b = &b[n as usize..];
    }
    Ok(())
}

/// Puts the terminal back the way it was found. Safe to call more than once and from the
/// panic hook; the second call does nothing.
pub fn restore() {
    if RESTORED.swap(true, Ordering::SeqCst) {
        return;
    }
    let guard = SAVED.lock().unwrap_or_else(|e| e.into_inner());
    if let Some(s) = guard.as_ref() {
        let _ = write_fd(s.fd, LEAVE.as_bytes());
        // SAFETY: fd and termios were valid when saved; tcsetattr only reads the struct.
        unsafe {
            libc::tcsetattr(s.fd, libc::TCSAFLUSH, &s.termios);
        }
    }
}

/// The controlling terminal in raw mode on the alternate screen. Dropping it restores.
pub struct Tty {
    fd: RawFd,
    owned: bool,
}

impl Tty {
    /// Opens `/dev/tty` (falls back to stdin/stdout), saves its settings, switches to raw
    /// mode and the alternate screen, and installs the panic hook. Mouse and resize modes are
    /// turned on by [`Tty::enable_modes`] after the probe.
    pub fn open() -> io::Result<Tty> {
        // SAFETY: plain libc calls with valid arguments.
        let (fd, owned) = unsafe {
            let fd = libc::open(c"/dev/tty".as_ptr(), libc::O_RDWR | libc::O_NOCTTY | libc::O_CLOEXEC);
            if fd >= 0 {
                (fd, true)
            } else if libc::isatty(0) == 1 && libc::isatty(1) == 1 {
                (0, false)
            } else {
                return Err(io::Error::other("no terminal"));
            }
        };
        // SAFETY: termios is plain data; tcgetattr fills it.
        let mut t: libc::termios = unsafe { std::mem::zeroed() };
        if unsafe { libc::tcgetattr(fd, &mut t) } != 0 {
            let e = io::Error::last_os_error();
            if owned {
                unsafe { libc::close(fd) };
            }
            return Err(e);
        }
        *SAVED.lock().unwrap_or_else(|e| e.into_inner()) = Some(Saved { fd, termios: t });
        let mut raw = t;
        // SAFETY: cfmakeraw edits the struct in place.
        unsafe { libc::cfmakeraw(&mut raw) };
        raw.c_cc[libc::VMIN] = 1;
        raw.c_cc[libc::VTIME] = 0;
        if unsafe { libc::tcsetattr(fd, libc::TCSAFLUSH, &raw) } != 0 {
            return Err(io::Error::last_os_error());
        }
        RESTORED.store(false, Ordering::SeqCst);
        let prev = std::panic::take_hook();
        std::panic::set_hook(Box::new(move |info| {
            restore();
            prev(info);
        }));
        let tty = Tty { fd, owned };
        write_fd(fd, b"\x1b[?1049h\x1b[?25l\x1b[H\x1b[2J")?;
        Ok(tty)
    }

    pub fn fd(&self) -> RawFd {
        self.fd
    }

    /// Mouse (press/release/drag, SGR coordinates), no autowrap, hidden cursor, and in-band
    /// resize reports when the terminal has them.
    pub fn enable_modes(&mut self, caps: &Caps) -> io::Result<()> {
        let mut s = String::from(ENTER_MODES);
        if caps.in_band_resize {
            s.push_str(RESIZE_ON);
        }
        write_fd(self.fd, s.as_bytes())
    }

    /// Grid and pixel size from the kernel (`TIOCGWINSZ`). Cell pixels are 0 when unknown.
    pub fn size(&self) -> io::Result<Size> {
        // SAFETY: winsize is plain data; ioctl fills it.
        let mut ws: libc::winsize = unsafe { std::mem::zeroed() };
        if unsafe { libc::ioctl(self.fd, libc::TIOCGWINSZ, &mut ws) } != 0 {
            return Err(io::Error::last_os_error());
        }
        let cw = ws.ws_xpixel.checked_div(ws.ws_col).unwrap_or(0);
        let ch = ws.ws_ypixel.checked_div(ws.ws_row).unwrap_or(0);
        Ok(Size::new(ws.ws_col, ws.ws_row, cw, ch))
    }
}

impl TermIo for Tty {
    fn write_all(&mut self, bytes: &[u8]) -> io::Result<()> {
        write_fd(self.fd, bytes)
    }
    fn read_timeout(&mut self, buf: &mut [u8], timeout: Duration) -> io::Result<usize> {
        let ready = poll_fds(&[(self.fd, libc::POLLIN)], Some(timeout))?;
        if !ready[0] {
            return Ok(0);
        }
        // SAFETY: buf is valid for buf.len() bytes.
        let n = unsafe { libc::read(self.fd, buf.as_mut_ptr().cast(), buf.len()) };
        if n < 0 {
            let e = io::Error::last_os_error();
            if e.kind() == io::ErrorKind::Interrupted || e.kind() == io::ErrorKind::WouldBlock {
                return Ok(0);
            }
            return Err(e);
        }
        Ok(n as usize)
    }
}

impl Drop for Tty {
    fn drop(&mut self) {
        restore();
        if self.owned {
            // SAFETY: we opened this fd.
            unsafe { libc::close(self.fd) };
        }
    }
}

/// Polls `(fd, events)` pairs; returns which ones are ready (readable/writable or hung up).
/// `None` waits forever. An interrupting signal returns all-false.
pub fn poll_fds(fds: &[(RawFd, libc::c_short)], timeout: Option<Duration>) -> io::Result<Vec<bool>> {
    let mut p: Vec<libc::pollfd> =
        fds.iter().map(|&(fd, events)| libc::pollfd { fd, events, revents: 0 }).collect();
    let ms = match timeout {
        None => -1,
        // Round up so a sub-millisecond wait does not become a busy spin.
        Some(d) => d.as_micros().div_ceil(1000).min(i32::MAX as u128) as i32,
    };
    // SAFETY: p is a valid array of p.len() pollfds.
    let n = unsafe { libc::poll(p.as_mut_ptr(), p.len() as libc::nfds_t, ms) };
    if n < 0 {
        let e = io::Error::last_os_error();
        if e.kind() == io::ErrorKind::Interrupted {
            return Ok(vec![false; fds.len()]);
        }
        return Err(e);
    }
    Ok(p.iter().map(|x| x.revents & (x.events | libc::POLLHUP | libc::POLLERR) != 0).collect())
}

// ---- signals -------------------------------------------------------------------------------

const SIG_RESIZE: u32 = 1;
const SIG_QUIT: u32 = 2;
static SIG_FLAGS: AtomicU32 = AtomicU32::new(0);
static SIG_PIPE_W: AtomicI32 = AtomicI32::new(-1);

extern "C" fn on_signal(sig: libc::c_int) {
    let bit = if sig == libc::SIGWINCH { SIG_RESIZE } else { SIG_QUIT };
    SIG_FLAGS.fetch_or(bit, Ordering::SeqCst);
    let w = SIG_PIPE_W.load(Ordering::SeqCst);
    if w >= 0 {
        let b = [1u8];
        // SAFETY: write(2) is async-signal-safe.
        unsafe { libc::write(w, b.as_ptr().cast(), 1) };
    }
}

/// Signals that arrived since the last call.
#[derive(Clone, Copy, Debug, Default, PartialEq, Eq)]
pub struct Signals {
    pub resize: bool,
    pub quit: bool,
}

/// Installs SIGWINCH/SIGINT/SIGTERM/SIGHUP handlers that only set flags and wake a self-pipe.
/// Returns the pipe's read end for the poll loop.
pub fn install_signals() -> io::Result<RawFd> {
    let mut fds = [0 as libc::c_int; 2];
    // SAFETY: fds has room for two descriptors.
    if unsafe { libc::pipe(fds.as_mut_ptr()) } != 0 {
        return Err(io::Error::last_os_error());
    }
    for fd in fds {
        // SAFETY: fd is one we just created.
        unsafe {
            let fl = libc::fcntl(fd, libc::F_GETFL);
            libc::fcntl(fd, libc::F_SETFL, fl | libc::O_NONBLOCK);
            libc::fcntl(fd, libc::F_SETFD, libc::FD_CLOEXEC);
        }
    }
    SIG_PIPE_W.store(fds[1], Ordering::SeqCst);
    for sig in [libc::SIGWINCH, libc::SIGINT, libc::SIGTERM, libc::SIGHUP] {
        // SAFETY: sigaction with a zeroed struct and a valid extern "C" handler; no SA_RESTART
        // so poll wakes with EINTR.
        unsafe {
            let mut sa: libc::sigaction = std::mem::zeroed();
            sa.sa_sigaction = on_signal as extern "C" fn(libc::c_int) as usize;
            libc::sigemptyset(&mut sa.sa_mask);
            sa.sa_flags = 0;
            libc::sigaction(sig, &sa, std::ptr::null_mut());
        }
    }
    Ok(fds[0])
}

/// Reads and clears the signal flags, draining the self-pipe `pipe_r`.
pub fn take_signals(pipe_r: RawFd) -> Signals {
    let mut sink = [0u8; 64];
    // SAFETY: non-blocking read into a local buffer.
    while unsafe { libc::read(pipe_r, sink.as_mut_ptr().cast(), sink.len()) } > 0 {}
    let f = SIG_FLAGS.swap(0, Ordering::SeqCst);
    Signals { resize: f & SIG_RESIZE != 0, quit: f & SIG_QUIT != 0 }
}
