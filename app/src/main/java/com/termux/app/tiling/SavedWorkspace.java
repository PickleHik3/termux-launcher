package com.termux.app.tiling;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * A saved Workspace (#431; spec "Windows and places": sessions retire): one Workspace's windows,
 * layout, tabs, Floats and working directories, kept under a name in
 * {@link SavedWorkspaceStore} and loaded back as a new Workspace. Its form is the stack's own for
 * one Workspace ({@link TilingStack#workspaceToJson}), plus, per Linux window, the catalogue key
 * it was launched from when the launcher knew it, and whether it opens at start.
 *
 * <p>Loading gives every window a new id; the layout state (Scrolling's widths, Dwindle's tree)
 * names windows by id, so it is renumbered with them. Shells start in their directories when the
 * desktop next draws them; Linux apps are for the caller to launch again ({@link Loaded#linux}).
 *
 * <p>Each save has an id of its own, kept through renames; a Workspace saved as it or loaded from
 * it is marked with it ({@link TilingWorkspace#savedId()}), so the desktop can tell the two apart
 * from a Workspace that only shares the name. A file of a newer form is not read (so it is never
 * written back older); fields this form does not know are kept as they are.
 *
 * <p>Pure: no Android views. Immutable.
 */
public final class SavedWorkspace {

    private static final int VERSION = 1;

    /** A Linux window of a loaded Workspace, waiting for its app to be launched again. */
    public static final class Relaunch {
        /** The new window's id in the stack it was loaded into. */
        public final long windowId;
        /** Its app id (the window's class); empty when it was never learnt. */
        @NonNull public final String appId;
        /** The catalogue key it was launched from, or null when the launcher did not know it. */
        @Nullable public final String key;

        Relaunch(long windowId, @Nullable String appId, @Nullable String key) {
            this.windowId = windowId;
            this.appId = appId == null ? "" : appId;
            this.key = key;
        }
    }

    /** What a load added. */
    public static final class Loaded {
        /** The new Workspace's number. */
        public final int number;
        /** Each saved window id to its new one, tiles before Floats. */
        @NonNull public final Map<Long, Long> ids;
        /** The Linux windows to launch again, in order. */
        @NonNull public final List<Relaunch> linux;

        Loaded(int number, @NonNull Map<Long, Long> ids, @NonNull List<Relaunch> linux) {
            this.number = number;
            this.ids = Collections.unmodifiableMap(ids);
            this.linux = Collections.unmodifiableList(linux);
        }
    }

    /** The whole document as read, unknown fields included; empty for a new save. */
    @NonNull private final JSONObject document;
    @NonNull private final JSONObject workspace;
    @NonNull private final Map<Long, String> keys;
    @Nullable private final String id;
    private final boolean openAtStart;
    @NonNull private final TilingWorkspace preview;

    private SavedWorkspace(@NonNull JSONObject document, @NonNull JSONObject workspace, @NonNull Map<Long, String> keys,
                           @Nullable String id, boolean openAtStart, @NonNull TilingWorkspace preview) {
        this.document = document;
        this.workspace = workspace;
        this.keys = Collections.unmodifiableMap(keys);
        this.id = id;
        this.openAtStart = openAtStart;
        this.preview = preview;
    }

    /**
     * Workspace {@code number} of {@code stack} as it stands, with {@code launchKeys} (window id to
     * catalogue key) kept for its Linux windows. The caller writes the layout state and the
     * shells' directories into the stack first. Null for no such Workspace or an empty one.
     */
    @Nullable
    public static SavedWorkspace of(@NonNull TilingStack stack, int number, @NonNull Map<Long, String> launchKeys) {
        JSONObject json = stack.workspaceToJson(number);
        TilingWorkspace workspace = stack.workspace(number);
        if (json == null || workspace == null) return null;
        // What the Workspace was saved as before is the stack's mark, not part of this save.
        json.remove("savedId");
        TilingWorkspace preview = previewOf(json);
        if (preview == null) return null;
        Map<Long, String> keys = new HashMap<>();
        for (TilingWindow window : workspace.windows()) {
            String key = launchKeys.get(window.id());
            if (window.kind() == WindowKind.LINUX_APP && key != null && !key.isEmpty()) keys.put(window.id(), key);
        }
        return new SavedWorkspace(new JSONObject(), json, keys, java.util.UUID.randomUUID().toString(), false,
            preview);
    }

    /** Its own id, kept through renames; null for a file written without one. */
    @Nullable
    public String id() {
        return id;
    }

    /** Whether the desktop loads it when the launcher starts. */
    public boolean openAtStart() {
        return openAtStart;
    }

    @NonNull
    public SavedWorkspace withOpenAtStart(boolean on) {
        return on == openAtStart ? this : new SavedWorkspace(document, workspace, new HashMap<>(keys), id, on, preview);
    }

    /** How many windows it holds. */
    public int windowCount() {
        return preview.windows().size();
    }

    /** The text it is kept as. */
    @NonNull
    public String encode() {
        try {
            // Fields a later form added are kept as they were.
            JSONObject json = new JSONObject(document.toString());
            json.put("version", VERSION);
            if (id != null) json.put("id", id);
            json.put("openAtStart", openAtStart);
            json.put("workspace", new JSONObject(workspace.toString()));
            JSONObject launch = new JSONObject();
            for (Map.Entry<Long, String> entry : keys.entrySet()) launch.put(String.valueOf(entry.getKey()), entry.getValue());
            json.put("launch", launch);
            return json.toString(2);
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    /** {@link #encode}'s text read back, or null for anything that holds no window it can read. */
    @Nullable
    public static SavedWorkspace decode(@Nullable String text) {
        if (text == null || text.isEmpty()) return null;
        try {
            JSONObject json = new JSONObject(text);
            if (json.optInt("version", VERSION) > VERSION) return null;
            JSONObject workspace = json.optJSONObject("workspace");
            if (workspace == null) return null;
            TilingWorkspace read = previewOf(workspace);
            if (read == null) return null;
            Map<Long, String> keys = new HashMap<>();
            JSONObject launch = json.optJSONObject("launch");
            for (Iterator<String> names = launch == null ? null : launch.keys(); names != null && names.hasNext(); ) {
                String name = names.next();
                String key = launch.optString(name, "");
                try {
                    if (!key.isEmpty()) keys.put(Long.parseLong(name), key);
                } catch (NumberFormatException e) {
                    // Not a window id: left out.
                }
            }
            String id = json.optString("id", "");
            return new SavedWorkspace(json, workspace, keys, id.isEmpty() ? null : id,
                json.optBoolean("openAtStart", false), read);
        } catch (JSONException e) {
            return null;
        }
    }

    /**
     * Adds it to {@code stack} as a new Workspace before the waiting one, named {@code name} (null
     * keeps the name it was saved with); the place in front stays. A Display window is left out
     * when {@code stack} already has one, as there is one; an Assistant window always is. Null when nothing
     * was added.
     */
    @Nullable
    public Loaded loadInto(@NonNull TilingStack stack, @Nullable String name) {
        boolean hasDisplay = stack.firstOfKind(WindowKind.DISPLAY) != null;
        Map<Long, Long> added;
        try {
            added = stack.addWorkspace(new JSONObject(workspace.toString()));
        } catch (JSONException e) {
            return null;
        }
        if (added.isEmpty()) return null;
        Map<Long, Long> ids = new java.util.LinkedHashMap<>(added);
        for (Iterator<Map.Entry<Long, Long>> it = ids.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Long, Long> entry = it.next();
            TilingWindow window = stack.window(entry.getValue());
            if (window == null) continue;
            // The Assistant is the AI page now, no window (ADR 0043): a saved one is left out.
            boolean taken = (window.kind() == WindowKind.DISPLAY && hasDisplay)
                || window.kind() == WindowKind.ASSISTANT;
            if (!taken) continue;
            stack.close(window.id());
            it.remove();
        }
        if (ids.isEmpty()) return null;
        Place place = stack.placeOf(ids.values().iterator().next());
        if (place == null || place.isHome()) return null;
        int number = place.workspaceNumber();
        TilingWorkspace loaded = stack.workspace(number);
        if (name != null) stack.renameWorkspace(number, name);
        stack.setSavedId(number, id);
        stack.setLayoutState(number, renumber(loaded == null ? null : loaded.layoutState(), ids));
        List<Relaunch> linux = new ArrayList<>();
        for (Map.Entry<Long, Long> entry : ids.entrySet()) {
            TilingWindow window = stack.window(entry.getValue());
            if (window != null && window.kind() == WindowKind.LINUX_APP) {
                linux.add(new Relaunch(window.id(), window.appId(), keys.get(entry.getKey())));
            }
        }
        return new Loaded(number, ids, linux);
    }

    /**
     * The Workspace alone, in a stack of its own, built once as it was read: what a picture of it
     * is drawn from. Read it only; it is not a Workspace of any desktop.
     */
    @NonNull
    public TilingWorkspace preview() {
        return preview;
    }

    @Nullable
    private static TilingWorkspace previewOf(@NonNull JSONObject workspace) {
        TilingStack scratch = TilingStack.empty(0);
        Map<Long, Long> ids;
        try {
            ids = scratch.addWorkspace(new JSONObject(workspace.toString()));
        } catch (JSONException e) {
            return null;
        }
        if (ids.isEmpty()) return null;
        TilingWorkspace read = scratch.workspace(1);
        if (read == null) return null;
        scratch.setLayoutState(1, renumber(read.layoutState(), ids));
        return read;
    }

    /**
     * {@code state} (a Scrolling or a Dwindle layout state) with each window id put through
     * {@code ids}; an id it does not name becomes {@link TilingStack#NO_WINDOW}, which the layout
     * then drops. Null for no state or one of neither form.
     */
    @Nullable
    static String renumber(@Nullable String state, @NonNull Map<Long, Long> ids) {
        if (state == null || state.isEmpty()) return null;
        try {
            JSONObject json = new JSONObject(state);
            if (json.has("dwindle")) {
                JSONObject root = json.optJSONObject("dwindle");
                if (root != null) renumberNode(root, ids, 0);
                return json.toString();
            }
            JSONArray columns = json.optJSONArray("columns");
            if (columns == null) return null;
            for (int i = 0; i < columns.length(); i++) {
                JSONObject column = columns.optJSONObject(i);
                if (column != null) renumberArray(column.optJSONArray("windows"), ids);
            }
            return json.toString();
        } catch (JSONException e) {
            return null;
        }
    }

    private static void renumberNode(@NonNull JSONObject node, @NonNull Map<Long, Long> ids, int depth)
            throws JSONException {
        if (depth > DwindleTree.MAX_DEPTH) return;
        JSONArray windows = node.optJSONArray("windows");
        if (windows != null) {
            renumberArray(windows, ids);
            if (node.has("shown")) node.put("shown", renumbered(node.optLong("shown", TilingStack.NO_WINDOW), ids));
            return;
        }
        JSONObject first = node.optJSONObject("first");
        JSONObject second = node.optJSONObject("second");
        if (first != null) renumberNode(first, ids, depth + 1);
        if (second != null) renumberNode(second, ids, depth + 1);
    }

    private static void renumberArray(@Nullable JSONArray windows, @NonNull Map<Long, Long> ids)
            throws JSONException {
        for (int i = 0; windows != null && i < windows.length(); i++) {
            windows.put(i, renumbered(windows.optLong(i, TilingStack.NO_WINDOW), ids));
        }
    }

    private static long renumbered(long id, @NonNull Map<Long, Long> ids) {
        Long to = ids.get(id);
        return to == null ? TilingStack.NO_WINDOW : to;
    }
}
