package com.termux.app.place;

import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.place.layout.KindLayout;
import com.termux.app.place.layout.LayoutFields;
import com.termux.app.place.layout.LayoutKind;
import com.termux.app.place.layout.LayoutStore;
import com.termux.app.place.layout.Layouts;
import com.termux.app.place.layout.LayoutsJson;
import com.termux.app.place.layout.Sizes;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The launcher preferences as {@link PlaceLayoutStore} sees them once step 17 has run (ADR 0020):
 * every layout key it names ({@code layout.<scope><orientation>.<field>}, {@code place.dress.*},
 * a place's {@code keyboard_open}) is read from and written to the kind's document in the
 * {@code layouts} file, never the flat key, which stays as it was for a downgrade. Everything else
 * passes through to the preferences.
 *
 * <p>The scope names the kind: none is the Workspace's, {@code home.} Home's, {@code minimal.}
 * Workspace-minimal's. The retired scopes {@code display.} and {@code assistant.} (and their
 * {@code place.dress.*} spellings) are the Workspace's too (ADR 0025): Terminal, Display and
 * Assistant pages wear one document. Each place's remembered keyboard stays its own: the
 * Terminal's in the document, the Display's and the Assistant's in their flat keys. The four sizes are the one {@code sizes} document
 * whatever the scope, and the widget grid is Home's whatever the scope: the cross-layout rules of
 * before (linked keys, grid key, dock rule, page fallback) are the shape of the data now.
 *
 * <p>Until {@link #useDocuments} is called (while the migration steps run on the flat keys) it is
 * the preferences unchanged.
 */
final class LayoutDocumentPrefs implements SharedPreferences {

    private static final String LAYOUT = "layout.";
    private static final String DRESS = "place.dress.";
    private static final String KEYBOARD_OPEN = "." + LayoutsJson.KEYBOARD_OPEN;

    /** Where a key lives: a kind's document (or the sizes, kind null), a section, a field. */
    static final class Route {
        @Nullable final LayoutKind kind;
        @Nullable final PlaceOrientation orientation;
        @NonNull final String field;

        Route(@Nullable LayoutKind kind, @Nullable PlaceOrientation orientation,
              @NonNull String field) {
            this.kind = kind;
            this.orientation = orientation;
            this.field = field;
        }
    }

    @NonNull private final SharedPreferences mPrefs;
    @Nullable private volatile LayoutStore mLayouts;
    /** A reading of the store alone: the documents without the editor's draft, no writes. */
    private final boolean mStoredOnly;

    LayoutDocumentPrefs(@NonNull SharedPreferences prefs) {
        this(prefs, false);
    }

    private LayoutDocumentPrefs(@NonNull SharedPreferences prefs, boolean storedOnly) {
        mPrefs = prefs;
        mStoredOnly = storedOnly;
    }

    /**
     * These preferences as the store holds them, the Appearance editor's drafts left out: what
     * its unsaved question compares against. Writes through it are dropped.
     */
    @NonNull
    LayoutDocumentPrefs storedOnly() {
        LayoutDocumentPrefs stored = new LayoutDocumentPrefs(mPrefs, true);
        stored.mLayouts = mLayouts;
        return stored;
    }

    void useDocuments(@NonNull LayoutStore layouts) {
        mLayouts = layouts;
    }

    @Nullable
    LayoutStore documents() {
        return mLayouts;
    }

    @NonNull
    SharedPreferences flat() {
        return mPrefs;
    }

    // ---------------------------------------------------------------- routing

    @Nullable
    static Route route(@NonNull String key) {
        if (key.startsWith(LAYOUT)) return routeLayout(key.substring(LAYOUT.length()));
        if (key.startsWith(DRESS)) return routeDress(key.substring(DRESS.length()));
        if (key.equals(PlaceLayoutStore.memoryKey(com.termux.app.wall.PaneWallPage.TERMINAL,
            LayoutsJson.KEYBOARD_OPEN)))
            return new Route(LayoutKind.TERMINAL, null, LayoutsJson.KEYBOARD_OPEN);
        // The Display's and the Assistant's remembered keyboard are their places' own, not the
        // layout's: their documents are retired (ADR 0025), so they stay in the flat keys.
        return null;
    }

    @Nullable
    private static Route routeLayout(@NonNull String rest) {
        LayoutKind kind = LayoutKind.TERMINAL;
        for (LayoutKind k : new LayoutKind[]{LayoutKind.HOME, LayoutKind.TERMINAL_MINIMAL,
            LayoutKind.DISPLAY, LayoutKind.ASSISTANT}) {
            String scope = scopeOf(k);
            if (rest.startsWith(scope)) {
                // A retired scope's keys are the Workspace's (ADR 0025).
                kind = k.live();
                rest = rest.substring(scope.length());
                break;
            }
        }
        PlaceOrientation orientation = null;
        for (PlaceOrientation o : PlaceOrientation.values()) {
            String prefix = o.storageValue() + ".";
            if (rest.startsWith(prefix)) {
                orientation = o;
                rest = rest.substring(prefix.length());
                break;
            }
        }
        if (rest.indexOf('.') >= 0) return null;
        if (orientation == null) {
            // The dock's design, search field and drawer tile are one value for every page (ADR
            // 0023 §1): held by Home, whatever scope asks.
            if (LayoutsJson.SHARED_WIDE_FIELDS.contains(rest))
                return new Route(LayoutKind.HOME, null, rest);
            return LayoutsJson.WIDE_FIELDS.contains(rest)
                && !LayoutsJson.KEYBOARD_OPEN.equals(rest) ? new Route(kind, null, rest) : null;
        }
        // The status bar's edge and compact rest likewise (ADR 0023 §1).
        if (LayoutsJson.SHARED_ORIENTATION_FIELDS.contains(rest))
            return new Route(LayoutKind.HOME, orientation, rest);
        if (LayoutsJson.SIZE_FIELDS.contains(rest)) return new Route(null, orientation, rest);
        if (LayoutsJson.GRID_FIELDS.contains(rest))
            return new Route(LayoutKind.HOME, orientation, rest);
        if (LayoutsJson.ORIENTATION_FIELDS.contains(rest))
            return new Route(kind, orientation, rest);
        return null;
    }

    @Nullable
    private static Route routeDress(@NonNull String rest) {
        String[] parts = rest.split("\\.", 3);
        if (parts.length != 3) return null;
        LayoutKind kind = LayoutKind.forKey(parts[0]);
        if (kind == null || kind == LayoutKind.TERMINAL_MINIMAL) return null;
        for (PlaceOrientation o : PlaceOrientation.values()) {
            // A Display or Assistant page shows what the Workspace shows (ADR 0025).
            if (o.storageValue().equals(parts[1]))
                return new Route(kind.live(), o, LayoutsJson.DRESS_PREFIX + parts[2]);
        }
        return null;
    }

    /** The flat scope a kind's keys are spelled with. */
    @NonNull
    static String scopeOf(@NonNull LayoutKind kind) {
        switch (kind) {
            case HOME: return LayoutVariant.HOME.keyScope();
            case TERMINAL_MINIMAL: return LayoutVariant.MINIMAL.keyScope();
            case DISPLAY: return LayoutVariant.DISPLAY.keyScope();
            case ASSISTANT: return LayoutVariant.ASSISTANT.keyScope();
            default: return LayoutVariant.NORMAL.keyScope();
        }
    }

    @Nullable
    private Object read(@NonNull LayoutStore store, @NonNull Route route) {
        Layouts layouts = mStoredOnly ? store.stored() : store.current();
        LayoutFields fields;
        if (route.kind == null) {
            fields = layouts.sizes().orientation(route.orientation);
        } else {
            KindLayout layout = layouts.kind(route.kind);
            fields = route.orientation == null ? layout.wide() : layout.orientation(route.orientation);
        }
        return normalized(route.field, fields.get(route.field));
    }

    /** A number as the flat key held it: the two scales as floats, everything else as ints. */
    @Nullable
    private static Object normalized(@NonNull String field, @Nullable Object value) {
        if (!(value instanceof Number)) return value;
        if (LayoutsJson.DOCK_HEIGHT.equals(field) || LayoutsJson.KEYBOARD_HEIGHT.equals(field))
            return ((Number) value).floatValue();
        return ((Number) value).intValue();
    }

    // ---------------------------------------------------------------- reads

    @Nullable
    private Object value(@NonNull String key) {
        LayoutStore store = mLayouts;
        Route route = store == null ? null : route(key);
        if (route == null) return reads().getAll().get(key);
        return read(store, route);
    }

    /**
     * Whether {@code key} reads straight from the preferences: no route into the documents. A
     * typed getter then delegates to the preferences' own, so no read copies the whole map
     * ({@code getAll}); PlaceLayoutStore reads on travel frames.
     */
    private boolean unrouted(@Nullable String key) {
        LayoutStore store = mLayouts;
        return key == null || store == null || route(key) == null;
    }

    @NonNull
    @Override
    public Map<String, ?> getAll() {
        LayoutStore store = mLayouts;
        if (store == null) return reads().getAll();
        Map<String, Object> all = new HashMap<>();
        for (Map.Entry<String, ?> e : reads().getAll().entrySet()) {
            if (route(e.getKey()) == null) all.put(e.getKey(), e.getValue());
        }
        Layouts layouts = mStoredOnly ? store.stored() : store.current();
        for (LayoutKind kind : LayoutKind.values()) {
            // A retired kind's keys read the Workspace's document, listed once under its own.
            if (kind.retired()) continue;
            KindLayout layout = layouts.kind(kind);
            String scope = LAYOUT + scopeOf(kind);
            for (String field : layout.wide().keys()) {
                if (LayoutsJson.KEYBOARD_OPEN.equals(field)) {
                    if (kind == LayoutKind.TERMINAL)
                        all.put(PlaceLayoutStore.memoryKey(
                                com.termux.app.wall.PaneWallPage.TERMINAL, field),
                            layout.wide().get(field));
                } else if (LayoutsJson.WIDE_FIELDS.contains(field)) {
                    // Shared fields read from Home alone: another kind's stale copy is not shown.
                    if (kind != LayoutKind.HOME && LayoutsJson.SHARED_WIDE_FIELDS.contains(field))
                        continue;
                    all.put(scope + field, layout.wide().get(field));
                }
            }
            for (PlaceOrientation o : PlaceOrientation.values()) {
                LayoutFields fields = layout.orientation(o);
                for (String field : fields.keys()) {
                    Object value = normalized(field, fields.get(field));
                    if (field.startsWith(LayoutsJson.DRESS_PREFIX)) {
                        if (kind != LayoutKind.TERMINAL_MINIMAL)
                            all.put(DRESS + kind.key + "." + o.storageValue() + "."
                                + field.substring(LayoutsJson.DRESS_PREFIX.length()), value);
                    } else if (LayoutsJson.GRID_FIELDS.contains(field)) {
                        if (kind == LayoutKind.HOME)
                            all.put(scope + o.storageValue() + "." + field, value);
                    } else if (LayoutsJson.ORIENTATION_FIELDS.contains(field)
                        || LayoutsJson.STATUS_EDGE.equals(field)) {
                        if (kind != LayoutKind.HOME
                            && LayoutsJson.SHARED_ORIENTATION_FIELDS.contains(field)) continue;
                        all.put(scope + o.storageValue() + "." + field, value);
                    }
                }
            }
        }
        for (PlaceOrientation o : PlaceOrientation.values()) {
            LayoutFields fields = layouts.sizes().orientation(o);
            for (String field : fields.keys()) {
                if (LayoutsJson.SIZE_FIELDS.contains(field))
                    all.put(LAYOUT + o.storageValue() + "." + field, normalized(field, fields.get(field)));
            }
        }
        return all;
    }

    @Nullable
    @Override
    public String getString(String key, @Nullable String defValue) {
        if (unrouted(key)) {
            try {
                return reads().getString(key, defValue);
            } catch (ClassCastException other) {
                return defValue;
            }
        }
        Object value = value(key);
        return value instanceof String ? (String) value : defValue;
    }

    @Nullable
    @Override
    public Set<String> getStringSet(String key, @Nullable Set<String> defValues) {
        return reads().getStringSet(key, defValues);
    }

    @Override
    public int getInt(String key, int defValue) {
        if (unrouted(key)) {
            try {
                return reads().getInt(key, defValue);
            } catch (ClassCastException otherNumber) {
                // Stored as another number type: read it as the document path does.
                Object stored = reads().getAll().get(key);
                return stored instanceof Number ? ((Number) stored).intValue() : defValue;
            }
        }
        Object value = value(key);
        return value instanceof Number ? ((Number) value).intValue() : defValue;
    }

    @Override
    public long getLong(String key, long defValue) {
        if (unrouted(key)) {
            try {
                return reads().getLong(key, defValue);
            } catch (ClassCastException otherNumber) {
                // Stored as another number type: read it as the document path does.
                Object stored = reads().getAll().get(key);
                return stored instanceof Number ? ((Number) stored).longValue() : defValue;
            }
        }
        Object value = value(key);
        return value instanceof Number ? ((Number) value).longValue() : defValue;
    }

    @Override
    public float getFloat(String key, float defValue) {
        if (unrouted(key)) {
            try {
                return reads().getFloat(key, defValue);
            } catch (ClassCastException otherNumber) {
                // Stored as another number type: read it as the document path does.
                Object stored = reads().getAll().get(key);
                return stored instanceof Number ? ((Number) stored).floatValue() : defValue;
            }
        }
        Object value = value(key);
        return value instanceof Number ? ((Number) value).floatValue() : defValue;
    }

    @Override
    public boolean getBoolean(String key, boolean defValue) {
        if (unrouted(key)) {
            try {
                return reads().getBoolean(key, defValue);
            } catch (ClassCastException other) {
                return defValue;
            }
        }
        Object value = value(key);
        return value instanceof Boolean ? (Boolean) value : defValue;
    }

    @Override
    public boolean contains(String key) {
        LayoutStore store = mLayouts;
        Route route = store == null ? null : route(key);
        return route == null ? reads().contains(key) : read(store, route) != null;
    }

    @NonNull
    @Override
    public Editor edit() {
        return new DocumentEditor();
    }

    /** What a read of the flat keys asks: the store, or the editing session's draft over it. */
    private SharedPreferences reads() {
        if (mStoredOnly) return mPrefs;
        SharedPreferences reads = com.termux.shared.settings.preferences.SharedPreferencesPreview.readsFor(mPrefs);
        return reads == null ? mPrefs : reads;
    }

    @Override
    public void registerOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) {
        if (mStoredOnly) return;
        com.termux.shared.settings.preferences.PreferenceDraft.listen(mPrefs, l);
    }

    @Override
    public void unregisterOnSharedPreferenceChangeListener(OnSharedPreferenceChangeListener l) {
        if (mStoredOnly) return;
        com.termux.shared.settings.preferences.PreferenceDraft.unlisten(mPrefs, l);
    }

    // ---------------------------------------------------------------- writes

    /**
     * What a place remembers rather than how it is arranged: whether it was left with the keyboard
     * up, and whether the status bar rests compact. Not the Appearance editor's: written to the
     * file even while its draft is open, so the wall moving under the editor never reads as an
     * unsaved edit, and Discard leaves it as the place left it.
     */
    static boolean isMemory(@NonNull String field) {
        return LayoutsJson.KEYBOARD_OPEN.equals(field) || LayoutsJson.STATUS_COMPACT.equals(field);
    }

    private final class DocumentEditor implements Editor {
        private final Editor mFlat = mPrefs.edit();
        /** Routed changes in order; a null value removes the field. */
        private final List<Object[]> mChanges = new ArrayList<>();
        private boolean mFlatTouched;

        /**
         * Minimal mode is the Appearance editor's: while its session holds a draft over these
         * preferences, the flag is written into the draft, so Done stores it and Discard drops it.
         */
        @Nullable private Editor mDraftFlat;
        private boolean mDraftTouched;

        @Nullable
        private Editor draftFor(@NonNull String key) {
            if (!key.equals(PlaceLayoutStore.minimalKey()))
                return null;
            if (mDraftFlat == null) {
                com.termux.shared.settings.preferences.PreferenceDraft draft =
                    com.termux.shared.settings.preferences.PreferenceDraft.current();
                if (draft != null && draft.isOpen() && draft.store() == mPrefs)
                    mDraftFlat = draft.edit();
            }
            return mDraftFlat;
        }

        private Editor put(@NonNull String key, @Nullable Object value,
                           @NonNull java.util.function.Consumer<Editor> flat) {
            Route route = mLayouts == null ? null : route(key);
            if (route == null) {
                Editor draft = draftFor(key);
                if (draft != null) {
                    flat.accept(draft);
                    mDraftTouched = true;
                } else {
                    flat.accept(mFlat);
                    mFlatTouched = true;
                }
            } else {
                mChanges.add(new Object[]{route, value});
            }
            return this;
        }

        @Override public Editor putString(String key, @Nullable String value) {
            return put(key, value, e -> e.putString(key, value));
        }

        @Override public Editor putStringSet(String key, @Nullable Set<String> values) {
            mFlat.putStringSet(key, values);
            mFlatTouched = true;
            return this;
        }

        @Override public Editor putInt(String key, int value) {
            return put(key, value, e -> e.putInt(key, value));
        }

        @Override public Editor putLong(String key, long value) {
            return put(key, value, e -> e.putLong(key, value));
        }

        @Override public Editor putFloat(String key, float value) {
            return put(key, value, e -> e.putFloat(key, value));
        }

        @Override public Editor putBoolean(String key, boolean value) {
            return put(key, value, e -> e.putBoolean(key, value));
        }

        @Override public Editor remove(String key) {
            return put(key, null, e -> e.remove(key));
        }

        @Override public Editor clear() {
            mFlat.clear();
            mFlatTouched = true;
            return this;
        }

        @Override public boolean commit() {
            if (mStoredOnly) return false;
            writeDocuments();
            if (mDraftTouched && mDraftFlat != null) mDraftFlat.apply();
            return !mFlatTouched || mFlat.commit();
        }

        @Override public void apply() {
            if (mStoredOnly) return;
            writeDocuments();
            if (mDraftTouched && mDraftFlat != null) mDraftFlat.apply();
            if (mFlatTouched) mFlat.apply();
        }

        private void writeDocuments() {
            LayoutStore store = mLayouts;
            if (store == null || mChanges.isEmpty()) return;
            // While the Appearance editor holds a draft, the arrangement goes into it; what a
            // place remembers goes to the file as ever.
            if (store.isDraftOpen()) {
                List<Object[]> memory = new ArrayList<>();
                List<Object[]> arrangement = new ArrayList<>();
                for (Object[] change : mChanges)
                    (isMemory(((Route) change[0]).field) ? memory : arrangement).add(change);
                write(store, memory, false);
                write(store, arrangement, true);
            } else {
                write(store, mChanges, false);
            }
            mChanges.clear();
        }

        private void write(@NonNull LayoutStore store, @NonNull List<Object[]> changes,
                           boolean draft) {
            if (changes.isEmpty()) return;
            EnumMap<LayoutKind, List<Object[]>> byKind = new EnumMap<>(LayoutKind.class);
            List<Object[]> sizes = new ArrayList<>();
            for (Object[] change : changes) {
                Route route = (Route) change[0];
                if (route.kind == null) sizes.add(change);
                else byKind.computeIfAbsent(route.kind, k -> new ArrayList<>()).add(change);
            }
            for (Map.Entry<LayoutKind, List<Object[]>> e : byKind.entrySet()) {
                java.util.function.UnaryOperator<KindLayout> edit = layout -> {
                    KindLayout out = layout;
                    for (Object[] change : e.getValue()) {
                        Route route = (Route) change[0];
                        out = route.orientation == null ? out.withWide(route.field, change[1])
                            : out.with(route.orientation, route.field, change[1]);
                    }
                    return out;
                };
                if (draft) store.updateDraft(e.getKey(), edit);
                else store.update(e.getKey(), edit);
            }
            if (!sizes.isEmpty()) {
                java.util.function.UnaryOperator<Sizes> edit = s -> {
                    Sizes out = s;
                    for (Object[] each : sizes) {
                        Route route = (Route) each[0];
                        out = out.with(route.orientation, route.field, each[1]);
                    }
                    return out;
                };
                if (draft) store.updateSizesDraft(edit);
                else store.updateSizes(edit);
            }
        }
    }
}
