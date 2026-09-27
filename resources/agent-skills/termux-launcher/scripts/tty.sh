# tty.sh — shared helper, sourced by notify.sh/progress.sh: finds the terminal device of the
# pane THIS agent is running in, so out-of-band escapes (notifications, the progress ring, a
# clipboard write) land on screen even though this script's own stdout is captured by a tool
# pipe, not the terminal. POSIX sh, no bashisms.

# Prints the pane's tty device path (e.g. /dev/pts/12) on stdout, or nothing + exit 1.
# Walks up the process tree from $$ (this shell), reading ppid from /proc/<pid>/stat field 4,
# and returns the first ancestor whose fd 0, 1 or 2 resolves to /dev/pts/*. That ancestor is the
# pane's own shell (or something it execed), so its stdio is the pane's real terminal — a coding
# agent's tool calls run underneath it with stdout/stderr redirected elsewhere.
tlskill_pane_tty() {
    pid=$$
    tries=0
    while [ "$tries" -lt 64 ]; do
        tries=$((tries + 1))
        stat=$(cat "/proc/$pid/stat" 2>/dev/null) || return 1
        # Strip "pid (comm) " — using the LAST ") " keeps this correct even when comm itself
        # contains parentheses. What remains is "state ppid pgrp ...".
        rest=${stat##*) }
        ppid=$(set -- $rest; echo "$2")
        case "$ppid" in
            ''|*[!0-9]*) return 1 ;;
        esac
        [ "$ppid" -gt 1 ] || return 1
        for fd in 0 1 2; do
            link=$(readlink "/proc/$ppid/fd/$fd" 2>/dev/null) || continue
            case "$link" in
                /dev/pts/*)
                    echo "$link"
                    return 0
                    ;;
            esac
        done
        pid=$ppid
    done
    return 1
}

# Resolves the device to write escapes to, or prints an error to stderr and returns 1.
# Sets TLSKILL_TTY on success.
tlskill_resolve_tty() {
    TLSKILL_TTY=$(tlskill_pane_tty 2>/dev/null)
    if [ -z "$TLSKILL_TTY" ]; then
        # A subshell, so a failed open shows in its status instead of passing -w
        # and failing later with the shell's own complaint.
        if (: >/dev/tty) 2>/dev/null; then
            TLSKILL_TTY=/dev/tty
        else
            echo "tlskill: could not find this pane's terminal device (no /dev/pts ancestor, no /dev/tty)" >&2
            return 1
        fi
    fi
    return 0
}
