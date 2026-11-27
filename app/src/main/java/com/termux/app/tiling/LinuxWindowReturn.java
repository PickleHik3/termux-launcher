package com.termux.app.tiling;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * After a launcher restart, which running Linux app goes back into which saved Linux window (#419).
 * Linux apps outlive the launcher in the Linux windows host; when the launcher comes back the host
 * replays the windows it still runs, and each one slots back into the window it had: first by the
 * host's own window id, which stays while the host runs, then by app id, oldest saved window
 * first. A saved window that nothing running takes is gone (its app ended while the launcher was
 * away); a running window no saved one takes is new to the desktop.
 *
 * <p>Pure: no Android views.
 */
public final class LinuxWindowReturn {

    /** No host window id saved, or none known. */
    public static final long NO_LINUX_ID = 0L;

    /** A saved Linux window waiting for its app. */
    public static final class Saved {
        /** The tiling window's id. */
        public final long windowId;
        /** The app id it was saved with. */
        @NonNull public final String appId;
        /** The host's window id it showed, or {@link #NO_LINUX_ID}. */
        public final long linuxId;

        public Saved(long windowId, @Nullable String appId, long linuxId) {
            this.windowId = windowId;
            this.appId = appId == null ? "" : appId;
            this.linuxId = linuxId;
        }
    }

    /** One window the host replayed. */
    public static final class Running {
        public final long linuxId;
        @NonNull public final String appId;

        public Running(long linuxId, @Nullable String appId) {
            this.linuxId = linuxId;
            this.appId = appId == null ? "" : appId;
        }
    }

    /** What the match decided. */
    public static final class Result {
        /** Host window id to the saved window it slots back into, in replay order. */
        @NonNull public final Map<Long, Long> returned;
        /** Running windows no saved window took, in replay order: new windows. */
        @NonNull public final List<Running> unclaimed;
        /** Saved windows nothing running took, oldest first: they close. */
        @NonNull public final List<Long> gone;

        Result(@NonNull Map<Long, Long> returned, @NonNull List<Running> unclaimed, @NonNull List<Long> gone) {
            this.returned = Collections.unmodifiableMap(returned);
            this.unclaimed = Collections.unmodifiableList(unclaimed);
            this.gone = Collections.unmodifiableList(gone);
        }
    }

    private LinuxWindowReturn() {}

    /**
     * Matches {@code running} (in replay order) to {@code saved}. A host window id matches only a
     * saved window of the same app (or one saved without an app id), so a host that started again
     * and reuses ids never puts one app into another's window.
     */
    @NonNull
    public static Result match(@NonNull List<Saved> saved, @NonNull List<Running> running) {
        List<Saved> waiting = new ArrayList<>(saved);
        waiting.sort((a, b) -> Long.compare(a.windowId, b.windowId));
        Map<Long, Long> returned = new LinkedHashMap<>();
        Map<Long, Running> left = new LinkedHashMap<>();
        for (Running window : running) if (!left.containsKey(window.linuxId)) left.put(window.linuxId, window);
        // By host window id first: the host kept the id, so this is the very window.
        Map<Long, Running> byId = new HashMap<>(left);
        for (int i = 0; i < waiting.size(); i++) {
            Saved candidate = waiting.get(i);
            if (candidate.linuxId == NO_LINUX_ID) continue;
            Running window = byId.get(candidate.linuxId);
            if (window == null || !sameApp(candidate, window)) continue;
            returned.put(window.linuxId, candidate.windowId);
            left.remove(window.linuxId);
            byId.remove(window.linuxId);
            waiting.remove(i--);
        }
        // Then by app id, in replay order, each taking the oldest saved window of its app.
        List<Running> unclaimed = new ArrayList<>();
        for (Running window : left.values()) {
            Saved taken = null;
            if (!window.appId.isEmpty()) {
                for (Saved candidate : waiting) {
                    if (candidate.appId.equals(window.appId)) {
                        taken = candidate;
                        break;
                    }
                }
            }
            if (taken == null) {
                unclaimed.add(window);
                continue;
            }
            waiting.remove(taken);
            returned.put(window.linuxId, taken.windowId);
        }
        List<Long> gone = new ArrayList<>();
        for (Saved candidate : waiting) gone.add(candidate.windowId);
        // Keep the replay order for what returned, whichever pass matched it.
        Map<Long, Long> ordered = new LinkedHashMap<>();
        for (Running window : running) {
            Long id = returned.get(window.linuxId);
            if (id != null) ordered.put(window.linuxId, id);
        }
        return new Result(ordered, unclaimed, gone);
    }

    private static boolean sameApp(@NonNull Saved saved, @NonNull Running running) {
        return saved.appId.isEmpty() || running.appId.isEmpty() || saved.appId.equals(running.appId);
    }
}
