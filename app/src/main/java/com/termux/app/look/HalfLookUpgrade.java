package com.termux.app.look;

import androidx.annotation.NonNull;

import com.termux.app.wall.WallPages.Page;
import com.termux.app.wall.WallPages.PageKind;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceProperty;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceSlot;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The one-time upgrade from the half keys ({@code <key>_home}, {@code <key>_workspace}) to the
 * page model: a wall value (the plain key) and, where the Workspace differs, page overrides on
 * the Terminal page (and on a Display page when one is given).
 *
 * <p>Per key: equal or absent half values become the wall value. Where they differ Home's is the
 * wall value, and the Workspace's goes to {@code <key>@<terminalPageId>} when a page may hold
 * that key. The surface numbers are settled by what each page <em>shows</em>, not by raw key:
 * after the per-key step the Terminal page's resolved value is compared with what the Workspace
 * showed before, and any surface number that moved gets the Workspace's value as an override. So a
 * Workspace that followed a Base of its own, or a detached value Home never used, still looks the
 * same. Canvas keys were never per half and stay wall values. The half keys are not deleted
 * (a downgrade still finds them). Gated by {@link #KEY_VERSION}; pure over the store it reads.
 */
public final class HalfLookUpgrade {

    private HalfLookUpgrade() { }

    public static final String KEY_VERSION = "page_look_upgrade_version";
    public static final int VERSION = 1;

    private static final String HOME_SUFFIX = "_home";
    private static final String WORKSPACE_SUFFIX = "_workspace";

    /** One store write the upgrade makes. */
    public static final class Write {
        enum Type { INT, BOOL, STRING }

        @NonNull public final String key;
        final Type type;
        final int intValue;
        final boolean boolValue;
        final String stringValue;

        private Write(String key, Type type, int i, boolean b, String s) {
            this.key = key;
            this.type = type;
            this.intValue = i;
            this.boolValue = b;
            this.stringValue = s;
        }

        void applyTo(@NonNull LookStore store) {
            switch (type) {
                case INT: store.putInt(key, intValue); break;
                case BOOL: store.putBoolean(key, boolValue); break;
                default: store.putString(key, stringValue); break;
            }
        }

        @Override public String toString() {
            return key + "=" + (type == Type.INT ? intValue : type == Type.BOOL ? boolValue
                : stringValue);
        }
    }

    /** A key that was held per half, with the number it reads when absent. */
    private static final class Entry {
        final String key;
        final Write.Type type;
        final Object defaultValue;
        /** True when a page may hold its own value, so the Workspace's goes to an override. */
        final boolean overridable;

        Entry(String key, Write.Type type, Object defaultValue, boolean overridable) {
            this.key = key;
            this.type = type;
            this.defaultValue = defaultValue;
            this.overridable = overridable;
        }
    }

    private static final List<Entry> ENTRIES = new ArrayList<>();

    static {
        for (SurfaceSlot slot : SurfaceSlot.values()) {
            for (SurfaceProperty property : SurfaceProperty.values()) {
                if (!TermuxAppSharedPreferences.isPerPage(slot, property)) continue;
                ENTRIES.add(new Entry(LookKeys.surfaceKey(slot, property), Write.Type.INT,
                    LookKeys.surfaceDefault(slot, property), true));
                ENTRIES.add(new Entry(LookKeys.inheritKey(slot, property), Write.Type.BOOL,
                    TERMUX_APP.DEFAULT_VALUE_SURFACE_INHERITS_BASE, false));
            }
        }
        for (SurfaceProperty property : SurfaceProperty.values()) {
            if (TermuxAppSharedPreferences.isPerPageProperty(property))
                ENTRIES.add(new Entry(property.baseKey, Write.Type.INT, property.baseDefault, false));
        }
        ENTRIES.add(new Entry(TERMUX_APP.KEY_IN_APP_KEYBOARD_BACKDROP_OPACITY, Write.Type.INT,
            TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_BACKDROP_OPACITY, true));
        ENTRIES.add(new Entry(TERMUX_APP.KEY_SURFACE_MATERIAL, Write.Type.STRING,
            TERMUX_APP.DEFAULT_SURFACE_MATERIAL, true));
        ENTRIES.add(new Entry(TERMUX_APP.KEY_SURFACE_MATERIAL_INTENSITY, Write.Type.INT,
            TERMUX_APP.DEFAULT_SURFACE_MATERIAL_INTENSITY, true));
    }

    /** Whether the upgrade has run on this store. */
    public static boolean done(@NonNull LookStore store) {
        return store.getInt(KEY_VERSION, 0) >= VERSION;
    }

    /**
     * Runs the upgrade once: applies {@link #plan} and records the version. Returns false, and
     * touches nothing, when the version key says it ran. {@code displayPageId} is -1 for none.
     */
    public static boolean run(@NonNull LookStore store, int terminalPageId, int displayPageId) {
        if (done(store)) return false;
        for (Write write : plan(store, terminalPageId, displayPageId)) write.applyTo(store);
        store.putInt(KEY_VERSION, VERSION);
        return true;
    }

    /**
     * The writes the upgrade makes for this store, in order, version key not included. Reads only;
     * a store with no half keys answers an empty list. {@code terminalPageId} and
     * {@code displayPageId} (-1 for none) name the pages that receive the Workspace's values.
     */
    @NonNull
    public static List<Write> plan(@NonNull LookStore store, int terminalPageId,
                                   int displayPageId) {
        Map<String, Write> writes = new LinkedHashMap<>();
        List<Integer> targets = new ArrayList<>();
        if (terminalPageId >= 0) targets.add(terminalPageId);
        if (displayPageId >= 0) targets.add(displayPageId);

        // Step 1: per key, the wall value is Home's; a backdrop that differs goes to the pages.
        Overlay after = new Overlay(store);
        for (Entry entry : ENTRIES) {
            boolean hasHome = store.has(entry.key + HOME_SUFFIX);
            boolean hasWorkspace = store.has(entry.key + WORKSPACE_SUFFIX);
            if (!hasHome && !hasWorkspace) continue;
            Object home = read(store, entry, hasHome ? entry.key + HOME_SUFFIX : entry.key);
            Object workspace = read(store, entry,
                hasWorkspace ? entry.key + WORKSPACE_SUFFIX : entry.key);
            Object plain = store.has(entry.key) ? read(store, entry, entry.key) : null;
            if (plain == null ? !home.equals(entry.defaultValue) : !plain.equals(home))
                put(writes, after, entry.key, entry.type, home);
            if (entry.overridable && !home.equals(workspace))
                for (int id : targets)
                    put(writes, after, LookKeys.pageKey(entry.key, id), entry.type, workspace);
        }

        // Step 2: what each page shows must not move. Compare the Terminal (and Display) page's
        // resolved surface numbers with the Workspace's before the upgrade.
        PageLook now = new PageLook(after);
        PageLook before = new PageLook(new WorkspaceView(store));
        try {
            for (int id : targets) {
                PageKind kind = id == terminalPageId ? PageKind.TERMINAL : PageKind.DISPLAY;
                Page page = new Page(id, kind, null, null);
                PageLook.Snapshot shown = before.of(page);
                PageLook.Snapshot got = now.of(page);
                if (!got.surfaceMaterial().equals(shown.surfaceMaterial())) {
                    String key = LookKeys.pageKey(TERMUX_APP.KEY_SURFACE_MATERIAL, id);
                    writes.put(key, new Write(key, Write.Type.STRING, 0, false,
                        shown.surfaceMaterial()));
                }
                if (got.surfaceMaterialIntensity() != shown.surfaceMaterialIntensity()) {
                    String key = LookKeys.pageKey(TERMUX_APP.KEY_SURFACE_MATERIAL_INTENSITY, id);
                    writes.put(key, new Write(key, Write.Type.INT,
                        shown.surfaceMaterialIntensity(), false, null));
                }
                for (SurfaceSlot slot : SurfaceSlot.values()) {
                    for (SurfaceProperty property : SurfaceProperty.values()) {
                        if (!LookKeys.detachable(slot, property, kind)) continue;
                        int want = shown.value(slot, property);
                        if (got.value(slot, property) == want) continue;
                        String key = LookKeys.pageKey(LookKeys.surfaceKey(slot, property), id);
                        writes.put(key, new Write(key, Write.Type.INT, want, false, null));
                    }
                }
            }
        } finally {
            now.close();
            before.close();
        }
        return Collections.unmodifiableList(new ArrayList<>(writes.values()));
    }

    private static Object read(LookStore store, Entry entry, String key) {
        switch (entry.type) {
            case INT: return store.getInt(key, (Integer) entry.defaultValue);
            case BOOL: return store.getBoolean(key, (Boolean) entry.defaultValue);
            default: return store.getString(key, (String) entry.defaultValue);
        }
    }

    private static void put(Map<String, Write> writes, Overlay after, String key,
                            Write.Type type, Object value) {
        Write write;
        switch (type) {
            case INT: write = new Write(key, type, (Integer) value, false, null); break;
            case BOOL: write = new Write(key, type, 0, (Boolean) value, null); break;
            default: write = new Write(key, type, 0, false, (String) value); break;
        }
        writes.put(key, write);
        write.applyTo(after);
    }

    /** The store with the planned writes laid over it, so the plan can be checked without writing. */
    private static final class Overlay implements LookStore {
        private final LookStore base;
        private final MemoryLookStore top = new MemoryLookStore();

        Overlay(LookStore base) { this.base = base; }

        @Override public int getInt(@NonNull String key, int d) { return top.has(key) ? top.getInt(key, d) : base.getInt(key, d); }
        @Override public boolean getBoolean(@NonNull String key, boolean d) { return top.has(key) ? top.getBoolean(key, d) : base.getBoolean(key, d); }
        @NonNull @Override public String getString(@NonNull String key, @NonNull String d) { return top.has(key) ? top.getString(key, d) : base.getString(key, d); }
        @Override public boolean has(@NonNull String key) { return top.has(key) || base.has(key); }
        @Override public void putInt(@NonNull String key, int value) { top.putInt(key, value); }
        @Override public void putBoolean(@NonNull String key, boolean value) { top.putBoolean(key, value); }
        @Override public void putString(@NonNull String key, @NonNull String value) { top.putString(key, value); }
        @Override public void remove(@NonNull String key) { throw new UnsupportedOperationException(); }
        @NonNull @Override public Set<String> keys() { Set<String> all = base.keys(); all.addAll(top.keys()); return all; }
        @Override public void addListener(@NonNull Listener listener) { top.addListener(listener); }
        @Override public void removeListener(@NonNull Listener listener) { top.removeListener(listener); }
    }

    /** The store as the Workspace half read it: its own key where it had one. Read-only. */
    private static final class WorkspaceView implements LookStore {
        private final LookStore base;

        WorkspaceView(LookStore base) { this.base = base; }

        private String keyFor(String key) {
            return base.has(key + WORKSPACE_SUFFIX) ? key + WORKSPACE_SUFFIX : key;
        }

        @Override public int getInt(@NonNull String key, int d) { return base.getInt(keyFor(key), d); }
        @Override public boolean getBoolean(@NonNull String key, boolean d) { return base.getBoolean(keyFor(key), d); }
        @NonNull @Override public String getString(@NonNull String key, @NonNull String d) { return base.getString(keyFor(key), d); }
        @Override public boolean has(@NonNull String key) { return base.has(keyFor(key)); }
        @Override public void putInt(@NonNull String key, int value) { throw new UnsupportedOperationException(); }
        @Override public void putBoolean(@NonNull String key, boolean value) { throw new UnsupportedOperationException(); }
        @Override public void putString(@NonNull String key, @NonNull String value) { throw new UnsupportedOperationException(); }
        @Override public void remove(@NonNull String key) { throw new UnsupportedOperationException(); }
        @NonNull @Override public Set<String> keys() { return base.keys(); }
        @Override public void addListener(@NonNull Listener listener) { }
        @Override public void removeListener(@NonNull Listener listener) { }
    }
}
