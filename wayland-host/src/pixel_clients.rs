//! The processes the launcher started as Wayland desktops (sway, labwc). A nested desktop ignores
//! the host's scale and draws one buffer pixel per logical pixel, so its window is configured in
//! real pixels (scale 1) from the very first configure. The launcher marks the process it started
//! (`Command::PixelClient`); a toplevel whose client is that process, or a child of it, is one.
//! Pure, apart from the parent lookup the caller hands in.

/// How far up the process tree a client is looked for: the launch's shell `exec`s into the
/// desktop, so the desktop is usually the marked pid itself.
pub const MAX_DEPTH: usize = 4;

#[derive(Default, Debug)]
pub struct PixelClients {
    pids: Vec<i32>,
}

impl PixelClients {
    /// Marks `pid` (on) or forgets it (off). Pids below 1 are ignored.
    pub fn mark(&mut self, pid: i32, on: bool) {
        self.pids.retain(|&p| p != pid);
        if on && pid > 0 {
            self.pids.push(pid);
        }
    }

    /// Whether `pid` or one of its first `MAX_DEPTH` ancestors is marked; `parent_of` answers
    /// 0 when it cannot tell.
    pub fn covers(&self, pid: i32, parent_of: impl Fn(i32) -> i32) -> bool {
        if self.pids.is_empty() {
            return false;
        }
        let mut p = pid;
        for _ in 0..=MAX_DEPTH {
            if p <= 1 {
                return false;
            }
            if self.pids.contains(&p) {
                return true;
            }
            let parent = parent_of(p);
            if parent == p {
                return false;
            }
            p = parent;
        }
        false
    }
}

/// The scale a new window's first configure uses: 1 for a Wayland desktop, else the page's.
pub fn first_scale(pixel: bool, page_scale: f64) -> f64 {
    if pixel {
        1.0
    } else {
        page_scale
    }
}

/// The parent pid in a `/proc/<pid>/stat` line ("pid (comm) S ppid ..."), read after the name's
/// last bracket since the name may hold spaces and brackets. 0 when unreadable.
pub fn parent_from_stat(stat: &str) -> i32 {
    let Some(close) = stat.rfind(')') else { return 0 };
    let mut fields = stat[close + 1..].split_whitespace();
    fields.next();
    fields.next().and_then(|f| f.parse().ok()).unwrap_or(0)
}

/// The parent of `pid` from /proc, 0 when it is gone or unreadable.
pub fn parent_pid(pid: i32) -> i32 {
    std::fs::read_to_string(format!("/proc/{pid}/stat"))
        .map(|s| parent_from_stat(&s))
        .unwrap_or(0)
}

#[cfg(test)]
mod tests {
    use super::*;

    fn tree(pid: i32) -> i32 {
        // 50 is sway, started as 40 (the launch's shell), under 10 (the host).
        match pid {
            51 => 50,
            50 => 40,
            40 => 10,
            10 => 1,
            _ => 0,
        }
    }

    #[test]
    fn the_marked_process_and_its_children_draw_in_pixels() {
        let mut c = PixelClients::default();
        assert!(!c.covers(50, tree));
        c.mark(40, true);
        assert!(c.covers(40, tree));
        assert!(c.covers(50, tree));
        assert!(c.covers(51, tree));
        assert!(!c.covers(10, tree));
        assert!(!c.covers(99, tree));
        assert!(!c.covers(0, tree));
    }

    #[test]
    fn a_forgotten_process_is_an_ordinary_client_again() {
        let mut c = PixelClients::default();
        c.mark(40, true);
        c.mark(40, true);
        c.mark(40, false);
        assert!(!c.covers(40, tree));
        c.mark(0, true);
        c.mark(-3, true);
        assert!(!c.covers(1, tree));
    }

    #[test]
    fn the_first_configure_takes_scale_one_only_for_a_desktop() {
        assert_eq!(1.0, first_scale(true, 2.5));
        assert_eq!(2.5, first_scale(false, 2.5));
    }

    #[test]
    fn the_parent_is_read_after_the_name() {
        assert_eq!(40, parent_from_stat("50 (sway) S 40 50 40 0"));
        assert_eq!(7, parent_from_stat("9 (a) b) (c) R 7 1"));
        assert_eq!(0, parent_from_stat("garbage"));
    }
}
