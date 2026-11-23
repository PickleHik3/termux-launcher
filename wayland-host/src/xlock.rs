//! When an X display's `.X<n>-lock` is stale, so a new Xwayland may take its number.
//!
//! A lock names its owner's pid. Over days pids wrap, so a dead server's pid can belong to some
//! other process of ours (a shell) and look alive. A live X server also listens on
//! `.X11-unix/X<n>`, so a lock whose pid is alive but whose socket does not answer is stale too.
//! A pid owned by another user (EPERM) is never touched.

/// What `kill(pid, 0)` said about a lock's owner.
#[derive(Clone, Copy, Debug, PartialEq)]
pub enum Owner {
    /// ESRCH: no such process.
    Gone,
    /// EPERM: another user's process; its lock is not ours to judge.
    OtherUser,
    Alive,
}

/// The pid in a lock file: decimal, padded with spaces, newline-terminated.
pub fn parse_lock_pid(text: &str) -> Option<i32> {
    text.trim().parse::<i32>().ok().filter(|pid| *pid > 0)
}

/// Whether the lock may be removed. `socket_answers` is whether `.X11-unix/X<n>` accepted a
/// connection; it is only asked about when the owner looks alive.
pub fn is_stale(owner: Owner, socket_answers: impl FnOnce() -> bool) -> bool {
    match owner {
        Owner::Gone => true,
        Owner::OtherUser => false,
        Owner::Alive => !socket_answers(),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn a_dead_owner_is_stale() {
        assert!(is_stale(Owner::Gone, || panic!("not asked")));
    }

    #[test]
    fn another_users_lock_is_kept() {
        assert!(!is_stale(Owner::OtherUser, || panic!("not asked")));
    }

    #[test]
    fn a_reused_pid_without_a_listening_server_is_stale() {
        assert!(is_stale(Owner::Alive, || false));
    }

    #[test]
    fn a_live_server_keeps_its_lock() {
        assert!(!is_stale(Owner::Alive, || true));
    }

    #[test]
    fn lock_pids_parse_like_x_writes_them() {
        assert_eq!(parse_lock_pid("      4242\n"), Some(4242));
        assert_eq!(parse_lock_pid("garbage"), None);
        assert_eq!(parse_lock_pid("0\n"), None);
        assert_eq!(parse_lock_pid("-5\n"), None);
    }
}
