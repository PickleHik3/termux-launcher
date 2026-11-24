//! Plain-text clipboard bridge, the parts that need no compositor: which mime type to read,
//! the size cap, reading and writing a client's pipe without ever waiting on it for long, and
//! the register that keeps a text from bouncing back to where it came from.
//!
//! Text only: no images, no primary selection, no drag and drop.

use std::fs::File;
use std::io::{Read, Write};
use std::os::fd::{AsRawFd, FromRawFd, OwnedFd};
use std::time::{Duration, Instant};

/// The most text that crosses either way (128 KiB of UTF-8); a longer one is dropped whole,
/// never cut. Small enough that the launcher's side, a binder text of UTF-16, fits the async
/// transaction buffer.
pub const MAX_TEXT_BYTES: usize = 128 * 1024;

/// How long one transfer may take in all. A client that never reads or never writes costs a
/// short-lived thread for this long and nothing else.
pub const TRANSFER_TIMEOUT: Duration = Duration::from_secs(2);

/// What the compositor offers for text it owns, best first.
pub const OFFERED_MIMES: [&str; 5] = ["text/plain;charset=utf-8", "text/plain", "UTF8_STRING", "TEXT", "STRING"];

/// The mime types the compositor reads text in, best first; anything else is not text.
const READ_ORDER: [&str; 5] = ["text/plain;charset=utf-8", "UTF8_STRING", "text/plain", "TEXT", "STRING"];

/// The best plain-text type among `offered`, or None when the source offers no text.
pub fn pick_text_mime(offered: &[String]) -> Option<&str> {
    READ_ORDER
        .iter()
        .find_map(|want| offered.iter().find(|m| m.eq_ignore_ascii_case(want)).map(String::as_str))
}

/// The last text that crossed between Android and Linux, in either direction. A text equal to
/// it is not sent on: it either came from the other side or was already sent.
#[derive(Default)]
pub struct Echo {
    last: Option<String>,
}

impl Echo {
    /// Records `text` as crossing now; false when it is the one that crossed last.
    pub fn crossing(&mut self, text: &str) -> bool {
        if self.last.as_deref() == Some(text) {
            return false;
        }
        self.last = Some(text.to_owned());
        true
    }
}

fn set_nonblocking(file: &File) {
    // SAFETY: fcntl on a descriptor this File owns.
    unsafe {
        let fd = file.as_raw_fd();
        let flags = libc::fcntl(fd, libc::F_GETFL);
        if flags >= 0 {
            libc::fcntl(fd, libc::F_SETFL, flags | libc::O_NONBLOCK);
        }
    }
}

/// Waits until `file` is ready for `events` or `deadline` passes; false on timeout or error.
fn wait(file: &File, events: i16, deadline: Instant) -> bool {
    loop {
        let left = deadline.saturating_duration_since(Instant::now());
        if left.is_zero() {
            return false;
        }
        let mut p = libc::pollfd { fd: file.as_raw_fd(), events, revents: 0 };
        let ms = left.as_millis().clamp(1, i32::MAX as u128) as i32;
        // SAFETY: one valid pollfd.
        let n = unsafe { libc::poll(&mut p, 1, ms) };
        if n > 0 {
            return true;
        }
        if n < 0 && std::io::Error::last_os_error().kind() != std::io::ErrorKind::Interrupted {
            return false;
        }
    }
}

/// Reads `fd` to its end. None when it takes longer than `timeout` or holds more than `cap`
/// bytes. Blocks the calling thread, so never the compositor's.
pub fn read_text(fd: OwnedFd, cap: usize, timeout: Duration) -> Option<String> {
    let mut file = File::from(fd);
    set_nonblocking(&file);
    let deadline = Instant::now() + timeout;
    let mut bytes = Vec::new();
    let mut buf = [0u8; 8192];
    loop {
        match file.read(&mut buf) {
            Ok(0) => break,
            Ok(n) => {
                if bytes.len() + n > cap {
                    return None;
                }
                bytes.extend_from_slice(&buf[..n]);
            }
            Err(e) if e.kind() == std::io::ErrorKind::WouldBlock => {
                if !wait(&file, libc::POLLIN, deadline) {
                    return None;
                }
            }
            Err(e) if e.kind() == std::io::ErrorKind::Interrupted => {}
            Err(_) => return None,
        }
    }
    Some(String::from_utf8_lossy(&bytes).into_owned())
}

/// Writes `text` to `fd` and closes it. False when the reader is too slow or gone.
pub fn write_text(fd: OwnedFd, text: &[u8], timeout: Duration) -> bool {
    let mut file = File::from(fd);
    set_nonblocking(&file);
    let deadline = Instant::now() + timeout;
    let mut rest = text;
    while !rest.is_empty() {
        match file.write(rest) {
            Ok(0) => return false,
            Ok(n) => rest = &rest[n..],
            Err(e) if e.kind() == std::io::ErrorKind::WouldBlock => {
                if !wait(&file, libc::POLLOUT, deadline) {
                    return false;
                }
            }
            Err(e) if e.kind() == std::io::ErrorKind::Interrupted => {}
            Err(_) => return false,
        }
    }
    true
}

/// A pipe: (read end, write end), both close-on-exec.
pub fn pipe() -> Option<(OwnedFd, OwnedFd)> {
    let mut fds = [0i32; 2];
    // SAFETY: fds has room for the two descriptors pipe2 writes.
    if unsafe { libc::pipe2(fds.as_mut_ptr(), libc::O_CLOEXEC) } != 0 {
        return None;
    }
    // SAFETY: pipe2 just made both descriptors and nothing else owns them.
    Some(unsafe { (OwnedFd::from_raw_fd(fds[0]), OwnedFd::from_raw_fd(fds[1])) })
}

#[cfg(test)]
mod tests {
    use super::*;

    fn v(items: &[&str]) -> Vec<String> {
        items.iter().map(|s| s.to_string()).collect()
    }

    #[test]
    fn prefers_utf8_text_over_legacy_targets() {
        assert_eq!(pick_text_mime(&v(&["STRING", "text/plain", "text/plain;charset=utf-8"])), Some("text/plain;charset=utf-8"));
        assert_eq!(pick_text_mime(&v(&["TEXT", "UTF8_STRING"])), Some("UTF8_STRING"));
        assert_eq!(pick_text_mime(&v(&["STRING", "TEXT"])), Some("TEXT"));
    }

    #[test]
    fn ignores_what_is_not_text() {
        assert_eq!(pick_text_mime(&v(&["image/png", "text/html"])), None);
        assert_eq!(pick_text_mime(&[]), None);
        assert_eq!(pick_text_mime(&v(&["Text/Plain;Charset=UTF-8"])), Some("Text/Plain;Charset=UTF-8"));
    }

    #[test]
    fn the_same_text_crosses_once() {
        let mut e = Echo::default();
        assert!(e.crossing("a"));
        assert!(!e.crossing("a"));
        assert!(e.crossing("b"));
        assert!(e.crossing("a"));
    }

    #[test]
    fn reads_what_the_writer_sends() {
        let (r, w) = pipe().unwrap();
        assert!(write_text(w, "héllo".as_bytes(), Duration::from_secs(1)));
        assert_eq!(read_text(r, 100, Duration::from_secs(1)).as_deref(), Some("héllo"));
    }

    #[test]
    fn drops_text_over_the_cap() {
        let (r, w) = pipe().unwrap();
        let t = std::thread::spawn(move || write_text(w, &[b'x'; 5000], Duration::from_secs(1)));
        assert_eq!(read_text(r, 4999, Duration::from_secs(1)), None);
        let _ = t.join();
        let (r, w) = pipe().unwrap();
        let t = std::thread::spawn(move || write_text(w, &[b'x'; 5000], Duration::from_secs(1)));
        assert_eq!(read_text(r, 5000, Duration::from_secs(1)).map(|s| s.len()), Some(5000));
        let _ = t.join();
    }

    #[test]
    fn a_writer_that_never_closes_times_out() {
        let (r, _w) = pipe().unwrap();
        let start = Instant::now();
        assert_eq!(read_text(r, 100, Duration::from_millis(100)), None);
        assert!(start.elapsed() < Duration::from_secs(1));
    }

    #[test]
    fn a_reader_that_never_reads_times_out() {
        let (_r, w) = pipe().unwrap();
        assert!(!write_text(w, &vec![b'x'; 1 << 20], Duration::from_millis(100)));
    }
}
