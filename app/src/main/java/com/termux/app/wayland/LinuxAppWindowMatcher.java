package com.termux.app.wayland;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Which open Linux window belongs to which launched app, so a second tap on an app whose window is
 * still open can bring that window forward instead of starting a second copy. Pure: the caller
 * feeds it launches, the processes they started and the host's window events, and asks
 * {@link #windowFor}.
 *
 * <p>A window is tied to a launch by its client pid being a started pid or a descendant of one
 * (native Wayland apps). An X11 window's client pid is Xwayland's, so it is tied by time instead:
 * to the one launch begun within {@link #X11_MATCH_WINDOW_MS} that has no window yet. Anything
 * less certain is left unmatched, and an unmatched app is simply started again, as before.
 */
final class LinuxAppWindowMatcher {

    /** How long after a launch began an X11 window with no pid link may still be its window. */
    static final long X11_MATCH_WINDOW_MS = 10_000L;
    /** The most parent links followed from a window's client pid. */
    static final int MAX_DEPTH = 12;

    /** The parent of {@code pid}, or a value below 1 when it is unknown or the process is gone. */
    interface ParentLookup {
        int parentOf(int pid);
    }

    private final ParentLookup parents;
    /** Pids each key's launches started. */
    private final Map<String, Set<Integer>> pids = new HashMap<>();
    /** When each key's latest launch began, while it has no window. */
    private final Map<String, Long> unclaimed = new HashMap<>();
    /** Each key's live windows, oldest first. */
    private final Map<String, List<Long>> windows = new HashMap<>();
    /** Window id to its key. */
    private final Map<Long, String> owner = new LinkedHashMap<>();

    LinuxAppWindowMatcher(@NonNull ParentLookup parents) {
        this.parents = parents;
    }

    /** A launch of {@code key} is about to run. A key with no live window starts afresh. */
    void launched(@NonNull String key, long nowMs) {
        if (!windows.containsKey(key)) pids.remove(key);
        unclaimed.put(key, nowMs);
    }

    /** The launch of {@code key} created process {@code pid} (below 1: not known, ignored). */
    void started(@NonNull String key, int pid) {
        if (pid < 1) return;
        Set<Integer> set = pids.get(key);
        if (set == null) {
            set = new HashSet<>();
            pids.put(key, set);
        }
        set.add(pid);
    }

    /** The host made window {@code id}; {@code x11}: it is an Xwayland window. */
    void windowCreated(long id, int clientPid, boolean x11, long nowMs) {
        if (owner.containsKey(id)) return;
        String key = keyByPid(clientPid);
        if (key == null && x11) key = soleRecentLaunch(nowMs);
        if (key == null) return;
        unclaimed.remove(key);
        owner.put(id, key);
        List<Long> list = windows.get(key);
        if (list == null) {
            list = new ArrayList<>();
            windows.put(key, list);
        }
        list.add(id);
    }

    /**
     * Window {@code id} is known to belong to {@code key}: the host's note for it, replayed to a
     * launcher that restarted while the app ran. Replaces whatever the window was matched to.
     */
    void restore(long id, @NonNull String key) {
        String was = owner.get(id);
        if (key.equals(was)) return;
        if (was != null) windowDestroyed(id);
        owner.put(id, key);
        unclaimed.remove(key);
        List<Long> list = windows.get(key);
        if (list == null) {
            list = new ArrayList<>();
            windows.put(key, list);
        }
        list.add(id);
    }

    /** The host closed window {@code id}. Its app is forgotten with its last window. */
    void windowDestroyed(long id) {
        String key = owner.remove(id);
        if (key == null) return;
        List<Long> list = windows.get(key);
        if (list == null) return;
        list.remove(id);
        if (list.isEmpty()) {
            windows.remove(key);
            pids.remove(key);
        }
    }

    /** The window to bring forward for {@code key}, or -1 when there is none. */
    long windowFor(@NonNull String key) {
        List<Long> list = windows.get(key);
        return list == null || list.isEmpty() ? -1L : list.get(0);
    }

    /** The key window {@code id} belongs to, or null when it is not matched to a launch. */
    @Nullable
    String keyFor(long id) {
        return owner.get(id);
    }

    /** The host's windows are being replayed: forget them, keep what was launched. */
    void clearWindows() {
        owner.clear();
        windows.clear();
    }

    /** The host stopped: nothing is open and nothing launched counts any more. */
    void clear() {
        clearWindows();
        pids.clear();
        unclaimed.clear();
    }

    @Nullable
    private String keyByPid(int clientPid) {
        int pid = clientPid;
        for (int depth = 0; depth < MAX_DEPTH && pid > 1; depth++) {
            for (Map.Entry<String, Set<Integer>> e : pids.entrySet()) {
                if (e.getValue().contains(pid)) return e.getKey();
            }
            int parent = parents.parentOf(pid);
            if (parent == pid) return null;
            pid = parent;
        }
        return null;
    }

    /** The one launch begun within the window that has no window yet; null when none or several. */
    @Nullable
    private String soleRecentLaunch(long nowMs) {
        String found = null;
        for (Map.Entry<String, Long> e : unclaimed.entrySet()) {
            long age = nowMs - e.getValue();
            if (age < 0 || age > X11_MATCH_WINDOW_MS) continue;
            if (found != null) return null;
            found = e.getKey();
        }
        return found;
    }

    /**
     * The parent pid in a {@code /proc/<pid>/stat} line ("pid (comm) S ppid ..."); the name may
     * hold spaces and brackets, so the fields are read after its last bracket. 0 when unreadable.
     */
    static int parentFromStat(@Nullable String stat) {
        if (stat == null) return 0;
        int close = stat.lastIndexOf(')');
        if (close < 0) return 0;
        String[] fields = stat.substring(close + 1).trim().split("\\s+");
        if (fields.length < 2) return 0;
        try {
            return Integer.parseInt(fields[1]);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
