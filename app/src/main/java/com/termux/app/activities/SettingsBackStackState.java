package com.termux.app.activities;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * The settings back stack as plain data: which fragment class each pushed screen showed, the
 * toolbar title it carried, its deep-link arguments (place/scrollToKey, if any), and when the
 * Activity holding this stack was last stopped. Kept free of Android framework classes beyond
 * annotations -- no Context, SharedPreferences, Fragment or Activity -- so {@link SettingsActivity}
 * does the actual reading/writing of SharedPreferences and this class only turns the stack into a
 * string and back, which unit tests exercise directly, without Robolectric.
 */
final class SettingsBackStackState {

    /**
     * Android's historical task-reset timeout: a background task older than this reopens at its
     * root rather than where the user left it (the platform's own ACTIVITY_INACTIVE_RESET_TIME
     * behaviour, from the days before excludeFromRecents tasks were reliably kept warm). Settings
     * is an excludeFromRecents singleTask task, which the platform never resets on its own, so it
     * enforces the same 30-minute rule itself instead of relying on the OS to do it.
     */
    static final long RETAIN_WINDOW_MS = 30 * 60 * 1000L;

    private static final String JSON_KEY_STOPPED_AT = "stopped_at_epoch_ms";
    private static final String JSON_KEY_ENTRIES = "entries";
    private static final String JSON_KEY_CLASS_NAME = "class_name";
    private static final String JSON_KEY_TITLE_RES_ID = "title_res_id";
    private static final String JSON_KEY_TITLE_TEXT = "title_text";
    private static final String JSON_KEY_PLACE = "place";
    private static final String JSON_KEY_SCROLL_TO_KEY = "scroll_to_key";

    /**
     * One pushed screen. Its title is either a resource id (screens opened through
     * {@link SettingsActivity#openScreen}, which always know their string resource) or, failing
     * that, plain resolved text (screens opened through {@code onPreferenceStartFragment}, which
     * only ever sees the clicked {@code Preference}'s already-resolved title).
     */
    static final class Entry {
        @NonNull final String className;
        final int titleResId;
        @Nullable final String titleText;
        @Nullable final String place;
        @Nullable final String scrollToKey;

        Entry(@NonNull String className, int titleResId, @Nullable String titleText,
              @Nullable String place, @Nullable String scrollToKey) {
            this.className = className;
            this.titleResId = titleResId;
            this.titleText = titleText;
            this.place = place;
            this.scrollToKey = scrollToKey;
        }
    }

    @NonNull final List<Entry> entries;
    final long stoppedAtEpochMs;

    SettingsBackStackState(@NonNull List<Entry> entries, long stoppedAtEpochMs) {
        this.entries = entries;
        this.stoppedAtEpochMs = stoppedAtEpochMs;
    }

    /**
     * @return true when {@code nowEpochMs} is within {@link #RETAIN_WINDOW_MS} of the moment this
     * stack was saved. A negative gap (the saved timestamp is in the future, e.g. the device clock
     * was moved back) is treated as not fresh rather than trusted blindly.
     */
    boolean isFresh(long nowEpochMs) {
        if (stoppedAtEpochMs <= 0) return false;
        long elapsed = nowEpochMs - stoppedAtEpochMs;
        return elapsed >= 0 && elapsed < RETAIN_WINDOW_MS;
    }

    @NonNull
    String serialize() {
        JSONObject root = new JSONObject();
        try {
            root.put(JSON_KEY_STOPPED_AT, stoppedAtEpochMs);
            JSONArray array = new JSONArray();
            for (Entry entry : entries) {
                JSONObject entryJson = new JSONObject();
                entryJson.put(JSON_KEY_CLASS_NAME, entry.className);
                entryJson.put(JSON_KEY_TITLE_RES_ID, entry.titleResId);
                if (entry.titleText != null) entryJson.put(JSON_KEY_TITLE_TEXT, entry.titleText);
                if (entry.place != null) entryJson.put(JSON_KEY_PLACE, entry.place);
                if (entry.scrollToKey != null) entryJson.put(JSON_KEY_SCROLL_TO_KEY, entry.scrollToKey);
                array.put(entryJson);
            }
            root.put(JSON_KEY_ENTRIES, array);
        } catch (JSONException ignored) {
            // Every value put above is a String or a primitive int/long, which JSONObject#put
            // never actually rejects; the checked exception is declared but not reachable here.
        }
        return root.toString();
    }

    /** Parses a stack saved by {@link #serialize()}, or null when {@code raw} is empty or malformed. */
    @Nullable
    static SettingsBackStackState parse(@Nullable String raw) {
        if (raw == null || raw.trim().isEmpty()) return null;
        try {
            JSONObject root = new JSONObject(raw);
            long stoppedAt = root.optLong(JSON_KEY_STOPPED_AT, 0);
            List<Entry> entries = new ArrayList<>();
            JSONArray array = root.optJSONArray(JSON_KEY_ENTRIES);
            if (array != null) {
                for (int i = 0; i < array.length(); i++) {
                    JSONObject entryJson = array.optJSONObject(i);
                    if (entryJson == null) continue;
                    String className = entryJson.optString(JSON_KEY_CLASS_NAME, "");
                    // A blank class name cannot be loaded; skip rather than fail the whole stack.
                    if (className.isEmpty()) continue;
                    int titleResId = entryJson.optInt(JSON_KEY_TITLE_RES_ID, 0);
                    String titleText = entryJson.has(JSON_KEY_TITLE_TEXT)
                        ? entryJson.optString(JSON_KEY_TITLE_TEXT, null) : null;
                    String place = entryJson.has(JSON_KEY_PLACE)
                        ? entryJson.optString(JSON_KEY_PLACE, null) : null;
                    String scrollToKey = entryJson.has(JSON_KEY_SCROLL_TO_KEY)
                        ? entryJson.optString(JSON_KEY_SCROLL_TO_KEY, null) : null;
                    entries.add(new Entry(className, titleResId, titleText, place, scrollToKey));
                }
            }
            return new SettingsBackStackState(entries, stoppedAt);
        } catch (JSONException e) {
            return null;
        }
    }
}
