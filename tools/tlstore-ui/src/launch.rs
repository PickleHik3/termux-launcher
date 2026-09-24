//! Where the store opens. It is designed for 53×26; started in a smaller pane inside the
//! launcher, it moves into a window of its own (`launcherctl window open`), and the pane it
//! was started in just says so. Where that is not possible it stays put, with a hint.

use std::ffi::OsStr;
use std::path::Path;
use std::process::{Command, Stdio};

use crate::layout::{BASE_COLS, BASE_ROWS};

/// What the pane it was started in prints after the store moved to its own window.
pub const OPENED: &str = "tlstore opened in its own window.";
/// The notice shown when the store has to run in a pane smaller than it is designed for.
pub const TOO_SMALL: &str = "Make this pane bigger to see the whole store.";

/// Where to run.
#[derive(Debug, PartialEq, Eq)]
pub enum Start {
    /// Run here, with an optional first notice.
    Here(Option<&'static str>),
    /// A new window runs the store now; this process should print [`OPENED`] and end.
    Moved,
}

/// Decides where to run in a `cols`×`rows` terminal. `no_window` is `TLSTORE_NO_WINDOW=1`
/// (tests, and the copy started in the new window, which must never move again).
pub fn start(cols: u16, rows: u16, no_window: bool, launcherctl: Option<&OsStr>, exe: &Path) -> Start {
    if cols >= BASE_COLS && rows >= BASE_ROWS {
        return Start::Here(None);
    }
    if !no_window {
        if let Some(lc) = launcherctl {
            if open_window(lc, exe) {
                return Start::Moved;
            }
        }
    }
    Start::Here(Some(TOO_SMALL))
}

/// `launcherctl window open --title tlstore -- env TLSTORE_NO_WINDOW=1 <exe>`; true when the
/// launcher answered `{"ok":true,…}` and exited 0.
pub fn open_window(launcherctl: &OsStr, exe: &Path) -> bool {
    let out = Command::new(launcherctl)
        .args(["window", "open", "--title", "tlstore", "--", "env", "TLSTORE_NO_WINDOW=1"])
        .arg(exe)
        .stdin(Stdio::null())
        .stderr(Stdio::null())
        .stdout(Stdio::piped())
        .output();
    match out {
        Ok(o) if o.status.success() => {
            let text: String =
                String::from_utf8_lossy(&o.stdout).chars().filter(|c| !c.is_whitespace()).collect();
            text.contains("\"ok\":true")
        }
        _ => false,
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::os::unix::fs::PermissionsExt;
    use std::path::PathBuf;

    fn stub(dir: &Path, body: &str) -> PathBuf {
        std::fs::create_dir_all(dir).unwrap();
        let p = dir.join("launcherctl");
        std::fs::write(&p, format!("#!/bin/sh\necho \"$@\" >> '{}/calls'\n{body}\n", dir.display())).unwrap();
        std::fs::set_permissions(&p, std::fs::Permissions::from_mode(0o755)).unwrap();
        p
    }

    fn tmp(name: &str) -> PathBuf {
        let d = std::env::temp_dir().join(format!("tlstore-launch-{}-{name}", std::process::id()));
        let _ = std::fs::remove_dir_all(&d);
        d
    }

    #[test]
    fn big_enough_runs_here_without_asking() {
        let d = tmp("big");
        let lc = stub(&d, r#"echo '{"ok":true,"id":"p1","window":2,"columns":53,"rows":40}'"#);
        assert_eq!(start(53, 26, false, Some(lc.as_os_str()), Path::new("/x/tlstore-ui")), Start::Here(None));
        assert!(!d.join("calls").exists());
    }

    #[test]
    fn small_pane_moves_to_its_own_window() {
        let d = tmp("move");
        let lc = stub(&d, r#"echo '{"ok": true, "id":"p1","window":2,"columns":53,"rows":40}'"#);
        assert_eq!(start(40, 20, false, Some(lc.as_os_str()), Path::new("/x/tlstore-ui")), Start::Moved);
        let calls = std::fs::read_to_string(d.join("calls")).unwrap();
        assert_eq!(calls, "window open --title tlstore -- env TLSTORE_NO_WINDOW=1 /x/tlstore-ui\n");
    }

    #[test]
    fn failure_or_no_launcherctl_stays_with_a_hint() {
        let d = tmp("fail");
        let lc = stub(&d, r#"echo '{"ok":false,"error":"no"}'; exit 1"#);
        let exe = Path::new("/x/tlstore-ui");
        assert_eq!(start(53, 20, false, Some(lc.as_os_str()), exe), Start::Here(Some(TOO_SMALL)));
        let d2 = tmp("fail2");
        let lc2 = stub(&d2, r#"echo '{"ok":false}'"#);
        assert_eq!(start(53, 20, false, Some(lc2.as_os_str()), exe), Start::Here(Some(TOO_SMALL)));
        assert_eq!(start(53, 20, false, None, exe), Start::Here(Some(TOO_SMALL)));
        let missing = d.join("no-such-launcherctl");
        assert_eq!(start(53, 20, false, Some(missing.as_os_str()), exe), Start::Here(Some(TOO_SMALL)));
    }

    #[test]
    fn no_window_override_never_asks() {
        let d = tmp("override");
        let lc = stub(&d, r#"echo '{"ok":true}'"#);
        assert_eq!(start(40, 20, true, Some(lc.as_os_str()), Path::new("/x")), Start::Here(Some(TOO_SMALL)));
        assert!(!d.join("calls").exists());
    }
}
