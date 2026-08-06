package com.termux.app.statusbar;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.termux.app.terminal.TerminalWindowBar;
import com.termux.privileged.PrivilegedBackendManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Resolves, per shell pid, what is running in the foreground of that pane so the window pills can
 * label themselves with the open file, the foreground process, or (when idle) the directory.
 *
 * Detection reads {@code /proc/<pid>/stat} (field tpgid) to find the pane's foreground process
 * group, then {@code /proc/<tpgid>/cmdline} for its name and arguments and {@code /proc/<tpgid>/stat}
 * again for its CPU time. The reads are funnelled through the launcher's privileged backend (Shizuku
 * or su/rish) so they work past Android's hidepid/ptrace restrictions. Results are cached; refreshes
 * are throttled and coalesced so the privileged IPC does not run more than once per
 * {@link #MIN_INTERVAL_MS}.
 */
public final class WindowForegroundResolver {

    /** What a pane's foreground currently is. */
    public static final class ForegroundInfo {
        /** Shell itself is in the foreground — the pane is idle; label with the directory. */
        public final boolean idle;
        /** Kernel pid of the foreground process-group leader; -1 when idle/unknown. */
        public final int foregroundPid;
        /** Foreground process basename, e.g. {@code codex}, {@code nvim}. Null when idle/unknown. */
        @Nullable public final String processName;
        /** Open file basename for editors, e.g. {@code config.toml}. Null otherwise. */
        @Nullable public final String openFile;
        /** Full foreground argv as read from procfs. Empty for idle/unknown panes. */
        @NonNull public final List<String> command;
        /**
         * Whether the foreground process burned enough CPU since the previous poll to count as
         * actively working. False for the first poll of a process, when there is no delta yet.
         */
        public final boolean working;
        /** Its CPU use since the previous poll, as a fraction of one core; -1 when unknown. */
        public final double cpuFraction;

        ForegroundInfo(boolean idle, int foregroundPid, @Nullable String processName,
                       @Nullable String openFile, @NonNull List<String> command,
                       double cpuFraction) {
            this.idle = idle;
            this.foregroundPid = foregroundPid;
            this.processName = processName;
            this.openFile = openFile;
            this.command = Collections.unmodifiableList(new ArrayList<>(command));
            this.cpuFraction = cpuFraction;
            this.working = cpuFraction >= WORKING_CPU_FRACTION;
        }
    }

    /**
     * How much of one core the pane's foreground process has to be using to read as working.
     *
     * <p>This is the line between "a command is running" and "a command is doing something". An idle
     * editor, a {@code sleep}, and a TUI clock repainting once a second all sit far below it; an agent
     * streaming output, a build, and a test run all sit far above. It is also above the cost of
     * redrawing a full-screen TUI for each keystroke, which is what keeps typing from reading as work.
     */
    private static final double WORKING_CPU_FRACTION = 0.10d;

    /** Kernel clock ticks per second, i.e. the unit of the utime/stime fields of /proc/pid/stat. */
    private static final double CLOCK_TICKS_PER_SECOND = clockTicksPerSecond();

    public interface Listener {
        /** Called on the main thread when cached foreground data changed. */
        void onForegroundResolved();
    }

    private static final long MIN_INTERVAL_MS = 1500L;

    /** One CPU-time reading for a foreground process, so the next poll can take a delta. */
    private static final class CpuSample {
        final int pid;
        final long ticks;
        final long atMs;

        CpuSample(int pid, long ticks, long atMs) {
            this.pid = pid;
            this.ticks = ticks;
            this.atMs = atMs;
        }
    }

    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private final Map<Integer, ForegroundInfo> mCache = new HashMap<>();
    /** Previous CPU reading per shell pid, keyed by shell so a new foreground resets the delta. */
    private final Map<Integer, CpuSample> mCpuSamples = new HashMap<>();
    @Nullable private final Listener mListener;

    private long mLastRunAt;
    private boolean mInFlight;

    public WindowForegroundResolver(@Nullable Listener listener) {
        mListener = listener;
    }

    @Nullable
    public ForegroundInfo get(int pid) {
        return mCache.get(pid);
    }

    /**
     * Request a refresh for the given shell pids. No-op when the privileged backend is unavailable,
     * a refresh is already in flight, or the throttle window has not elapsed. On completion the
     * listener fires only if any cached entry changed.
     */
    public void refresh(@NonNull List<Integer> pids, long nowMs) {
        if (pids.isEmpty() || mInFlight) return;
        if (nowMs - mLastRunAt < MIN_INTERVAL_MS) return;
        PrivilegedBackendManager manager = PrivilegedBackendManager.getInstance();
        if (!manager.isPrivilegedAvailable()) return;

        mLastRunAt = nowMs;
        mInFlight = true;
        String command = buildCommand(pids);
        java.util.List<Integer> asked = new ArrayList<>(pids);
        manager.executeCommand(command).whenComplete((output, error) -> {
            boolean changed = error == null && output != null && applyOutput(output, asked, nowMs);
            mMainHandler.post(() -> {
                mInFlight = false;
                if (changed && mListener != null) mListener.onForegroundResolved();
            });
        });
    }

    public void clear() {
        mCache.clear();
        mCpuSamples.clear();
    }

    @NonNull
    @VisibleForTesting
    static String buildCommand(@NonNull List<Integer> pids) {
        StringBuilder list = new StringBuilder();
        for (int pid : pids) {
            if (pid < 1) continue;
            if (list.length() > 0) list.append(' ');
            list.append(pid);
        }
        // POSIX/mksh-compatible. stat field parsing strips the "(comm)" prefix so a comm containing
        // spaces cannot shift the columns. Positional parameters past the ninth are braced — "$12" is
        // "$1" followed by a literal 2 in every POSIX shell. cmdline nulls become tabs for a
        // single-line payload, and it stays last on the line so an argument containing "|" is safe.
        //
        // Two kinds of line come back: one "pid|fg|tpgid|cmdline" (or "idle"/"x") per shell, then one
        // "g|pgrp|ticks" per process belonging to any of the foreground groups just found. The CPU
        // figure is summed from those rows rather than read off the group leader, because a leader that
        // is only waiting — "sh build.sh" while its child does the work — has no CPU of its own and
        // reported every wrapped command as idle. cutime/cstime cannot stand in: they are only filled
        // in once a child has been reaped.
        //
        // The group rows come from one "cat" over every /proc/<pid>/stat and a shell-side filter, not a
        // read per process: the kernel here has no /proc/<pid>/task/<pid>/children, and other-uid task
        // directories are not listable by the shell uid the privileged backend runs as, so walking the
        // tree is not available. Matching on pgrp is also the exact definition of the process group,
        // with no depth limit.
        return "groups=' '; for p in " + list + "; do "
            + "st=$(cat /proc/$p/stat 2>/dev/null); "
            + "if [ -z \"$st\" ]; then printf '%s|x|\\n' \"$p\"; continue; fi; "
            + "rest=${st##*) }; set -- $rest; tpgid=$6; "
            + "case \"$tpgid\" in ''|*[!0-9]*) printf '%s|idle|\\n' \"$p\"; continue;; esac; "
            + "if [ \"$tpgid\" = \"$p\" ] || [ \"$tpgid\" -lt 1 ]; then printf '%s|idle|\\n' \"$p\"; continue; fi; "
            + "cl=$(cat /proc/$tpgid/cmdline 2>/dev/null | tr '\\0' '\\t'); "
            + "groups=\"$groups$tpgid \"; "
            + "printf '%s|fg|%s|%s\\n' \"$p\" \"$tpgid\" \"$cl\"; "
            + "done; "
            + "if [ -n \"${groups# }\" ]; then "
            + "cat /proc/[0-9]*/stat 2>/dev/null | while read -r line; do "
            + "lrest=${line##*) }; set -- $lrest; "
            + "case \"$groups\" in *\" $3 \"*) "
            + "printf 'g|%s|%s\\n' \"$3\" \"$(( ${12} + ${13} ))\";; esac; "
            + "done; fi";
    }

    /** Returns true if the cache changed. */
    @VisibleForTesting
    boolean applyOutput(@NonNull String output, @NonNull List<Integer> asked, long nowMs) {
        String[] lines = output.split("\n");
        // Group rows are emitted after every shell row, so they are collected first: a shell's CPU
        // figure is the sum over its whole foreground group.
        Map<Integer, Long> groupTicks = new HashMap<>();
        for (String line : lines) {
            String trimmed = line.trim();
            if (!trimmed.startsWith("g|")) continue;
            String[] parts = trimmed.split("\\|", 3);
            if (parts.length < 3) continue;
            int group = parseInt(parts[1]);
            long ticks = parseLong(parts[2]);
            if (group < 1 || ticks < 0) continue;
            Long running = groupTicks.get(group);
            groupTicks.put(group, running == null ? ticks : running + ticks);
        }

        Map<Integer, ForegroundInfo> next = new HashMap<>();
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("g|")) continue;
            String[] parts = trimmed.split("\\|", 4);
            if (parts.length < 2) continue;
            Integer pid;
            try {
                pid = Integer.parseInt(parts[0].trim());
            } catch (NumberFormatException e) {
                continue;
            }
            String kind = parts[1];
            if ("idle".equals(kind)) {
                mCpuSamples.remove(pid);
                next.put(pid, new ForegroundInfo(true, -1, null, null,
                    Collections.emptyList(), -1d));
            } else if ("fg".equals(kind) && parts.length == 4) {
                int foregroundPid = parseInt(parts[2]);
                Long ticks = groupTicks.get(foregroundPid);
                ForegroundInfo info = parseForeground(parts[3], foregroundPid,
                    cpuFraction(pid, foregroundPid, ticks == null ? -1L : ticks, nowMs));
                if (info != null) next.put(pid, info);
            }
            // "x" (unreadable) leaves no entry so callers fall back to title/cwd.
        }
        // Entries for pids that were asked about but produced nothing readable are dropped, and so are
        // any left over from a shell that has since exited: the kernel reuses pids, and a stale entry
        // would hand a brand-new shell the foreground process of a dead one. Pids absent from `asked`
        // are still preserved, since this round simply did not cover them.
        boolean changed = false;
        for (Map.Entry<Integer, ForegroundInfo> entry : next.entrySet()) {
            if (!equalInfo(mCache.get(entry.getKey()), entry.getValue())) changed = true;
        }
        for (Integer pid : asked) {
            if (next.containsKey(pid)) continue;
            if (mCache.remove(pid) != null) changed = true;
            mCpuSamples.remove(pid);
        }
        mCache.putAll(next);
        return changed;
    }

    @Nullable
    private static ForegroundInfo parseForeground(@NonNull String payload, int foregroundPid,
                                                  double cpuFraction) {
        if (payload.isEmpty()) return null;
        String[] argv = payload.split("\t");
        String process = basename(argv[0]);
        if (process.isEmpty()) return null;
        process = process.toLowerCase(Locale.ROOT);
        String openFile = null;
        if (TerminalWindowBar.isEditor(process)) {
            for (int i = argv.length - 1; i >= 1; i--) {
                String arg = argv[i];
                if (arg.isEmpty() || arg.startsWith("-") || arg.startsWith("+")) continue;
                openFile = basename(arg);
                break;
            }
        }
        List<String> command = new ArrayList<>();
        Collections.addAll(command, argv);
        return new ForegroundInfo(false, foregroundPid, process, openFile, command, cpuFraction);
    }

    /**
     * CPU use of {@code foregroundPid} since this shell's previous poll, as a fraction of one core, or
     * -1 when there is no comparable previous reading — a first sighting, a different foreground
     * process than last time, an unreadable stat, or a sum that fell because a descendant was reaped
     * between polls. Reported rather than assumed to be zero: the caller's threshold treats unknown as
     * not-working, and one poll of latency at the start of a command is better than a wrong number.
     */
    private double cpuFraction(int shellPid, int foregroundPid, long ticks, long nowMs) {
        if (foregroundPid < 1 || ticks < 0) {
            mCpuSamples.remove(shellPid);
            return -1d;
        }
        CpuSample previous = mCpuSamples.get(shellPid);
        mCpuSamples.put(shellPid, new CpuSample(foregroundPid, ticks, nowMs));
        if (previous == null || previous.pid != foregroundPid) return -1d;
        long elapsedMs = nowMs - previous.atMs;
        // A counter that went backwards means the pid was reused; treat it as a new process.
        if (elapsedMs <= 0L || ticks < previous.ticks) return -1d;
        double seconds = elapsedMs / 1000d;
        return (ticks - previous.ticks) / CLOCK_TICKS_PER_SECOND / seconds;
    }

    private static long parseLong(@NonNull String value) {
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return -1L;
        }
    }

    private static int parseInt(@NonNull String value) {
        long parsed = parseLong(value);
        return parsed < 0L || parsed > Integer.MAX_VALUE ? -1 : (int) parsed;
    }

    private static double clockTicksPerSecond() {
        try {
            long ticks = android.system.Os.sysconf(android.system.OsConstants._SC_CLK_TCK);
            if (ticks > 0L) return ticks;
        } catch (Throwable ignored) {
        }
        return 100d;
    }

    @NonNull
    private static String basename(@NonNull String path) {
        String value = path.trim();
        int slash = value.lastIndexOf('/');
        if (slash >= 0) value = value.substring(slash + 1);
        return value;
    }

    /**
     * Compares the states callers render, not every field: {@code cpuFraction} is a fresh measurement
     * every poll, so including it would report a change — and repaint the bar — on every single poll.
     * The {@code working} flag it feeds is what the pills actually show.
     */
    private static boolean equalInfo(@Nullable ForegroundInfo a, @Nullable ForegroundInfo b) {
        if (a == null || b == null) return a == b;
        return a.idle == b.idle
            && a.foregroundPid == b.foregroundPid
            && a.working == b.working
            && equalStr(a.processName, b.processName)
            && equalStr(a.openFile, b.openFile)
            && a.command.equals(b.command);
    }

    private static boolean equalStr(@Nullable String a, @Nullable String b) {
        return a == null ? b == null : a.equals(b);
    }
}
