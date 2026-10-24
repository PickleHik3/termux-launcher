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
public final class SettingsBackStackState {

    /**
     * Android's historical task-reset timeout: a background task older than this reopens at its
     * root rather than where the user left it (the platform's own ACTIVITY_INACTIVE_RESET_TIME
     * behaviour, from the days before excludeFromRecents tasks were reliably kept warm). Settings
     * is an excludeFromRecents singleTask task, which the platform never resets on its own, so it
     * enforces the same 30-minute rule itself instead of relying on the OS to do it. The
     * Appearance surface keeps where it was left for the same window
     * ({@code com.termux.app.surfaces.AppearanceReturnState}).
     */
    public static final long RETAIN_WINDOW_MS = 30 * 60 * 1000L;

    /**
     * The shape of the settings tree a stack was saved against. 1 (or absent) is the tree before
     * the 10-19 restructure, whose stacks {@link #migrate} maps; 2 is the tree by the wall's halves.
     */
    static final int TREE_VERSION = 2;

    private static final String JSON_KEY_STOPPED_AT = "stopped_at_epoch_ms";
    private static final String JSON_KEY_TREE_VERSION = "tree_version";
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
    final int treeVersion;

    SettingsBackStackState(@NonNull List<Entry> entries, long stoppedAtEpochMs) {
        this(entries, stoppedAtEpochMs, TREE_VERSION);
    }

    SettingsBackStackState(@NonNull List<Entry> entries, long stoppedAtEpochMs, int treeVersion) {
        this.entries = entries;
        this.stoppedAtEpochMs = stoppedAtEpochMs;
        this.treeVersion = treeVersion;
    }

    /** The entries to replay: as saved, or mapped onto today's tree when saved against an older one. */
    @NonNull
    List<Entry> entriesForCurrentTree() {
        return treeVersion < TREE_VERSION ? migrate(entries) : entries;
    }

    /**
     * The entries a reopen rebuilds: {@link #entriesForCurrentTree}, cut before the first editor
     * (SettingsTree's {@code editor} screens, the Set up Linux apps guide among them). An editor is
     * a task, not a place: a reopen lands on the page that opened it, never back inside it.
     */
    @NonNull
    List<Entry> entriesToRestore() {
        List<Entry> out = new ArrayList<>();
        for (Entry entry : entriesForCurrentTree()) {
            com.termux.app.fragments.settings.SettingsTree.Screen screen =
                com.termux.app.fragments.settings.SettingsTree.find(entry.className);
            if (screen != null && screen.editor) break;
            out.add(entry);
        }
        return out;
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
            root.put(JSON_KEY_TREE_VERSION, treeVersion);
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

    /**
     * A saved stack in today's tree (SettingsTree). A stack saved before the restructure names
     * pages that moved, folded or now sit under another section: each entry is mapped to the page
     * that holds it now (a moved row's scroll key travels with it), and whenever an entry is not a
     * child of the one before it the path to it is rebuilt from its section's page, so Back walks
     * up the new tree. Entries for the same page collapse into one. A name the tree does not know
     * (a screen pushed from code, say a benchmark run) stays as it was, after whatever came before
     * it; the activity's class check still drops one that no longer exists.
     */
    @NonNull
    static List<Entry> migrate(@NonNull List<Entry> saved) {
        List<Entry> out = new ArrayList<>();
        for (Entry entry : saved) {
            String name = com.termux.app.fragments.settings.SettingsTree.redirect(
                entry.className, entry.scrollToKey);
            com.termux.app.fragments.settings.SettingsTree.Screen screen =
                com.termux.app.fragments.settings.SettingsTree.find(name);
            if (screen == null) {
                out.add(new Entry(name, entry.titleResId, entry.titleText, entry.place, entry.scrollToKey));
                continue;
            }
            // The tree's own title: a saved resource id belongs to the build that saved it.
            Entry moved = new Entry(name, screen.titleRes, null, entry.place, entry.scrollToKey);
            Entry last = out.isEmpty() ? null : out.get(out.size() - 1);
            if (last != null && last.className.equals(name)) {
                out.set(out.size() - 1, moved);
            } else if (last != null && name.equals(childOf(last.className, screen))) {
                out.add(moved);
            } else {
                out.clear();
                List<com.termux.app.fragments.settings.SettingsTree.Screen> path =
                    com.termux.app.fragments.settings.SettingsTree.path(name);
                for (int i = 0; i < path.size() - 1; i++) {
                    out.add(new Entry(path.get(i).className, path.get(i).titleRes, null, null, null));
                }
                out.add(moved);
            }
        }
        return out;
    }

    /** {@code screen}'s name when {@code parent} is the page that opens it, else null. */
    @Nullable
    private static String childOf(@NonNull String parent,
                                  @NonNull com.termux.app.fragments.settings.SettingsTree.Screen screen) {
        return parent.equals(screen.parent) ? screen.className : null;
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
            return new SettingsBackStackState(entries, stoppedAt, root.optInt(JSON_KEY_TREE_VERSION, 1));
        } catch (JSONException e) {
            return null;
        }
    }
}
