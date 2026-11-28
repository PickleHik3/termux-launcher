package com.termux.app.tiling;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The Linux windows of loaded saved Workspaces waiting for their apps (#431), launched again by
 * the desktop: a window that opens goes into the oldest one waiting for it, matched by the
 * catalogue key it was launched from, else by its app id, as the restart replay matches
 * ({@link LinuxWindowReturn}). An empty key or app id matches nothing.
 *
 * <p>Pure: no Android views. Not thread-safe; the UI thread owns it.
 */
public final class LinuxRelaunch {

    private static final class Waiting {
        @NonNull final String appId;
        @NonNull final String key;

        Waiting(@Nullable String appId, @Nullable String key) {
            this.appId = appId == null ? "" : appId;
            this.key = key == null ? "" : key;
        }
    }

    /** By window id, so the oldest window comes first. */
    @NonNull private final TreeMap<Long, Waiting> waiting = new TreeMap<>();

    /** Window {@code windowId} waits for a window of app {@code appId}, launched from {@code key}. */
    public void await(long windowId, @Nullable String appId, @Nullable String key) {
        waiting.put(windowId, new Waiting(appId, key));
    }

    /**
     * The waiting window a new window of app {@code appId}, launched from {@code key}, goes into,
     * which then waits no more; {@link TilingStack#NO_WINDOW} when none waits for it.
     */
    public long claim(@Nullable String appId, @Nullable String key) {
        String app = appId == null ? "" : appId;
        String launch = key == null ? "" : key;
        long found = TilingStack.NO_WINDOW;
        if (!launch.isEmpty()) {
            for (Map.Entry<Long, Waiting> entry : waiting.entrySet()) {
                if (entry.getValue().key.equals(launch)) {
                    found = entry.getKey();
                    break;
                }
            }
        }
        if (found == TilingStack.NO_WINDOW && !app.isEmpty()) {
            for (Map.Entry<Long, Waiting> entry : waiting.entrySet()) {
                if (entry.getValue().appId.equals(app)) {
                    found = entry.getKey();
                    break;
                }
            }
        }
        if (found != TilingStack.NO_WINDOW) waiting.remove(found);
        return found;
    }

    public boolean isWaiting(long windowId) {
        return waiting.containsKey(windowId);
    }

    /** Window {@code windowId} waits no more (it closed, or its app will not come). */
    public void forget(long windowId) {
        waiting.remove(windowId);
    }

    public boolean isEmpty() {
        return waiting.isEmpty();
    }

    /** The windows waiting, oldest first. */
    @NonNull
    public List<Long> windows() {
        return new ArrayList<>(waiting.keySet());
    }
}
