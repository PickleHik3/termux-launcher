package com.termux.app.tiling;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.LongPredicate;

/**
 * The Linux windows of loaded saved Workspaces waiting for their apps (#431), launched again by
 * the desktop: a window that opens goes into the oldest one waiting for it, matched by the
 * catalogue key it was launched from, else by its app id, as the restart replay matches
 * ({@link LinuxWindowReturn}). An empty key or app id matches nothing. The windows still waiting
 * are part of the desktop's saved form ({@link #toJson}), so a restart keeps them waiting.
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

    /** The app id window {@code windowId} waits for; empty when it waits for none. */
    @NonNull
    public String appIdOf(long windowId) {
        Waiting entry = waiting.get(windowId);
        return entry == null ? "" : entry.appId;
    }

    /** The catalogue key window {@code windowId} waits for; null when none is known. */
    @Nullable
    public String keyOf(long windowId) {
        Waiting entry = waiting.get(windowId);
        return entry == null || entry.key.isEmpty() ? null : entry.key;
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

    /** The waiting windows as the desktop's saved form keeps them. */
    @NonNull
    public JSONArray toJson() {
        try {
            JSONArray array = new JSONArray();
            for (Map.Entry<Long, Waiting> entry : waiting.entrySet()) {
                JSONObject json = new JSONObject();
                json.put("window", entry.getKey().longValue());
                json.put("appId", entry.getValue().appId);
                if (!entry.getValue().key.isEmpty()) json.put("key", entry.getValue().key);
                array.put(json);
            }
            return array;
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    /** {@link #toJson}'s form read back, keeping only the windows {@code holds} accepts. */
    @NonNull
    public static LinuxRelaunch fromJson(@Nullable JSONArray array, @NonNull LongPredicate holds) {
        LinuxRelaunch relaunch = new LinuxRelaunch();
        for (int i = 0; array != null && i < array.length(); i++) {
            JSONObject json = array.optJSONObject(i);
            long id = json == null ? TilingStack.NO_WINDOW : json.optLong("window", TilingStack.NO_WINDOW);
            if (id <= 0 || !holds.test(id)) continue;
            relaunch.await(id, json.optString("appId", ""), json.optString("key", ""));
        }
        return relaunch;
    }

    /** The windows waiting, oldest first. */
    @NonNull
    public List<Long> windows() {
        return new ArrayList<>(waiting.keySet());
    }
}
