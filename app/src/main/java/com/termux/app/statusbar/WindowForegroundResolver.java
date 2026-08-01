package com.termux.app.statusbar;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

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
 * group, then {@code /proc/<tpgid>/cmdline} for its name and arguments. The reads are funnelled
 * through the launcher's privileged backend (Shizuku or su/rish) so they work past Android's
 * hidepid/ptrace restrictions. Results are cached; refreshes are throttled and coalesced so the
 * privileged IPC does not run more than once per {@link #MIN_INTERVAL_MS}.
 */
public final class WindowForegroundResolver {

    /** What a pane's foreground currently is. */
    public static final class ForegroundInfo {
        /** Shell itself is in the foreground — the pane is idle; label with the directory. */
        public final boolean idle;
        /** Foreground process basename, e.g. {@code codex}, {@code nvim}. Null when idle/unknown. */
        @Nullable public final String processName;
        /** Open file basename for editors, e.g. {@code config.toml}. Null otherwise. */
        @Nullable public final String openFile;
        /** Full foreground argv as read from procfs. Empty for idle/unknown panes. */
        @NonNull public final List<String> command;

        ForegroundInfo(boolean idle, @Nullable String processName, @Nullable String openFile,
                       @NonNull List<String> command) {
            this.idle = idle;
            this.processName = processName;
            this.openFile = openFile;
            this.command = Collections.unmodifiableList(new ArrayList<>(command));
        }
    }

    public interface Listener {
        /** Called on the main thread when cached foreground data changed. */
        void onForegroundResolved();
    }

    private static final long MIN_INTERVAL_MS = 1500L;

    private final Handler mMainHandler = new Handler(Looper.getMainLooper());
    private final Map<Integer, ForegroundInfo> mCache = new HashMap<>();
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
        manager.executeCommand(command).whenComplete((output, error) -> {
            boolean changed = error == null && output != null && applyOutput(output);
            mMainHandler.post(() -> {
                mInFlight = false;
                if (changed && mListener != null) mListener.onForegroundResolved();
            });
        });
    }

    public void clear() {
        mCache.clear();
    }

    @NonNull
    private static String buildCommand(@NonNull List<Integer> pids) {
        StringBuilder list = new StringBuilder();
        for (int pid : pids) {
            if (pid < 1) continue;
            if (list.length() > 0) list.append(' ');
            list.append(pid);
        }
        // POSIX/mksh-compatible. stat field parsing strips the "(comm)" prefix so a comm containing
        // spaces cannot shift the tpgid column. cmdline nulls become tabs for a single-line payload.
        return "for p in " + list + "; do "
            + "st=$(cat /proc/$p/stat 2>/dev/null); "
            + "if [ -z \"$st\" ]; then printf '%s|x|\\n' \"$p\"; continue; fi; "
            + "rest=${st##*) }; set -- $rest; tpgid=$6; "
            + "case \"$tpgid\" in ''|*[!0-9]*) printf '%s|idle|\\n' \"$p\"; continue;; esac; "
            + "if [ \"$tpgid\" = \"$p\" ] || [ \"$tpgid\" -lt 1 ]; then printf '%s|idle|\\n' \"$p\"; continue; fi; "
            + "cl=$(cat /proc/$tpgid/cmdline 2>/dev/null | tr '\\0' '\\t'); "
            + "printf '%s|fg|%s\\n' \"$p\" \"$cl\"; "
            + "done";
    }

    /** Returns true if the cache changed. */
    private boolean applyOutput(@NonNull String output) {
        Map<Integer, ForegroundInfo> next = new HashMap<>();
        for (String line : output.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            String[] parts = trimmed.split("\\|", 3);
            if (parts.length < 2) continue;
            Integer pid;
            try {
                pid = Integer.parseInt(parts[0].trim());
            } catch (NumberFormatException e) {
                continue;
            }
            String kind = parts[1];
            if ("idle".equals(kind)) {
                next.put(pid, new ForegroundInfo(true, null, null, Collections.emptyList()));
            } else if ("fg".equals(kind) && parts.length == 3) {
                ForegroundInfo info = parseForeground(parts[2]);
                if (info != null) next.put(pid, info);
            }
            // "x" (unreadable) leaves no entry so callers fall back to title/cwd.
        }
        // Preserve entries for pids not covered this round (e.g. absent from output).
        boolean changed = false;
        for (Map.Entry<Integer, ForegroundInfo> entry : next.entrySet()) {
            if (!equalInfo(mCache.get(entry.getKey()), entry.getValue())) changed = true;
        }
        mCache.putAll(next);
        return changed;
    }

    @Nullable
    private static ForegroundInfo parseForeground(@NonNull String payload) {
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
        return new ForegroundInfo(false, process, openFile, command);
    }

    @NonNull
    private static String basename(@NonNull String path) {
        String value = path.trim();
        int slash = value.lastIndexOf('/');
        if (slash >= 0) value = value.substring(slash + 1);
        return value;
    }

    private static boolean equalInfo(@Nullable ForegroundInfo a, @Nullable ForegroundInfo b) {
        if (a == null || b == null) return a == b;
        return a.idle == b.idle
            && equalStr(a.processName, b.processName)
            && equalStr(a.openFile, b.openFile)
            && a.command.equals(b.command);
    }

    private static boolean equalStr(@Nullable String a, @Nullable String b) {
        return a == null ? b == null : a.equals(b);
    }
}
