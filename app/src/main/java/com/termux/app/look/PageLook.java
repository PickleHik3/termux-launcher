package com.termux.app.look;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.wall.WallPages.Page;
import com.termux.app.wall.WallPages.PageKind;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceProperty;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceSlot;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * The Look of a page (ADR 0015 §6, {@code page-look-2026-10-27.md}): resolved, immutable
 * {@link Snapshot}s cached per page id, writes at a {@link Scope}, and change events.
 *
 * <p>Resolution, per surface and property: the page's override ({@code <key>@<pageId>}) if the
 * property may be detached per page, else the surface's wall-wide value (the plain key) when the
 * surface is detached from Base ({@code surface_inherit_<slot>_<property>}), else Base
 * ({@code surface_base_*}). A page override of a wall-wide-only key (shapes, material, intensity,
 * the inherit flags, Base itself) is ignored.
 */
public final class PageLook {

    /** Where a write lands: the whole wall, or one page. */
    public static final class Scope {
        public static final Scope GLOBAL = new Scope(-1);

        private final int pageId;

        private Scope(int pageId) { this.pageId = pageId; }

        @NonNull
        public static Scope page(int pageId) { return new Scope(pageId); }

        public boolean isGlobal() { return pageId < 0; }

        /** The page id, or -1 for Global. */
        public int pageId() { return pageId; }

        @Override public boolean equals(Object o) {
            return o instanceof Scope && ((Scope) o).pageId == pageId;
        }

        @Override public int hashCode() { return pageId; }

        @Override public String toString() { return isGlobal() ? "Global" : "Page#" + pageId; }
    }

    /** Told which pages' snapshots a change touched; {@link #of} then answers the new ones. */
    public interface Listener {
        void changed(@NonNull Set<Integer> pageIds);
    }

    /** One page's resolved Look. Immutable. */
    public static final class Snapshot {
        public final int pageId;
        @NonNull public final PageKind kind;

        private final int[][] values;
        private final int keyboardBackdropOpacity;
        private final int wallpaperDim;
        private final String material;
        private final int materialIntensity;

        private Snapshot(@NonNull LookStore store, @NonNull Page page) {
            pageId = page.id;
            kind = page.kind;
            values = new int[SurfaceSlot.values().length][SurfaceProperty.values().length];
            for (SurfaceSlot slot : SurfaceSlot.values())
                for (SurfaceProperty property : SurfaceProperty.values())
                    values[slot.ordinal()][property.ordinal()] =
                        LookKeys.resolve(store, kind, pageId, slot, property);
            String backdropKey = TERMUX_APP.KEY_IN_APP_KEYBOARD_BACKDROP_OPACITY;
            String pageKey = LookKeys.pageKey(backdropKey, pageId);
            keyboardBackdropOpacity = LookKeys.normalizeBackdrop(store.has(pageKey)
                ? store.getInt(pageKey, -1)
                : store.getInt(backdropKey, TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_BACKDROP_OPACITY));
            wallpaperDim = LookKeys.wallpaperDim(store, pageId);
            String materialKey = TERMUX_APP.KEY_SURFACE_MATERIAL;
            String pageMaterial = LookKeys.pageKey(materialKey, pageId);
            material = store.has(pageMaterial)
                ? store.getString(pageMaterial, TERMUX_APP.DEFAULT_SURFACE_MATERIAL)
                : store.getString(materialKey, TERMUX_APP.DEFAULT_SURFACE_MATERIAL);
            String intensityKey = TERMUX_APP.KEY_SURFACE_MATERIAL_INTENSITY;
            String pageIntensity = LookKeys.pageKey(intensityKey, pageId);
            materialIntensity = Math.max(0, Math.min(100, store.has(pageIntensity)
                ? store.getInt(pageIntensity, TERMUX_APP.DEFAULT_SURFACE_MATERIAL_INTENSITY)
                : store.getInt(intensityKey, TERMUX_APP.DEFAULT_SURFACE_MATERIAL_INTENSITY)));
        }

        /**
         * The resolved number of a surface's property, clamped as its getter clamps it. Shapes
         * (corner radius, side gap) answer the wall's Base value. A property the slot does not
         * have answers its shipped value.
         */
        public int value(@NonNull SurfaceSlot slot, @NonNull SurfaceProperty property) {
            return values[slot.ordinal()][property.ordinal()];
        }

        // The 15 pair values (status bar x4, keyboard x5, dock tint and grain, app bar opacity,
        // extra keys blur, material and intensity), named as the preference getters were.

        public int statusBarBlurRadius() { return value(SurfaceSlot.STATUS, SurfaceProperty.BLUR); }
        public int statusBarOpacity() { return value(SurfaceSlot.STATUS, SurfaceProperty.OPACITY); }
        public int statusBarTintStrength() { return value(SurfaceSlot.STATUS, SurfaceProperty.TINT); }
        public int statusBarGrain() { return value(SurfaceSlot.STATUS, SurfaceProperty.GRAIN); }

        public int inAppKeyboardTintStrength() { return value(SurfaceSlot.KEYBOARD, SurfaceProperty.TINT); }
        public int inAppKeyboardBackgroundOpacity() { return value(SurfaceSlot.KEYBOARD, SurfaceProperty.OPACITY); }
        public int inAppKeyboardBlurRadius() { return value(SurfaceSlot.KEYBOARD, SurfaceProperty.BLUR); }
        public int inAppKeyboardGrain() { return value(SurfaceSlot.KEYBOARD, SurfaceProperty.GRAIN); }
        /** The keyboard's whole-backdrop opacity (0..100); an unset value renders opaque (100). */
        public int inAppKeyboardBackdropOpacity() { return keyboardBackdropOpacity; }

        public int dockTintStrength() { return value(SurfaceSlot.DOCK, SurfaceProperty.TINT); }
        public int dockGlassGrain() { return value(SurfaceSlot.DOCK, SurfaceProperty.GRAIN); }
        public int appBarOpacity() { return value(SurfaceSlot.DOCK, SurfaceProperty.OPACITY); }
        public int extraKeysBlurRadius() { return value(SurfaceSlot.DOCK, SurfaceProperty.BLUR); }

        /** The material family of the Base triple: the page's own, else the wall's. */
        @NonNull public String surfaceMaterial() { return material; }

        /** Where on the family's curve the Base triple was set, 0..100: the page's own, else the wall's. */
        public int surfaceMaterialIntensity() { return materialIntensity; }

        // Ground inputs.

        /** The wallpaper's dim, 0..100: the page's own, else the wall's. */
        public int wallpaperDim() { return wallpaperDim; }

        /** The terminal canvas's tint, 0..100, on the Terminal page; 0 on any other page. */
        public int canvasTint() {
            return kind == PageKind.TERMINAL ? value(SurfaceSlot.CANVAS, SurfaceProperty.TINT) : 0;
        }
    }

    private final LookStore store;
    private final ConcurrentHashMap<Integer, Snapshot> cache = new ConcurrentHashMap<>();
    private final List<Listener> listeners = new CopyOnWriteArrayList<>();
    private final LookStore.Listener storeListener = this::onStoreKeyChanged;
    private volatile boolean quiet;

    public PageLook(@NonNull LookStore store) {
        this.store = store;
        store.addListener(storeListener);
    }

    /** Stops listening to the store. */
    public void close() {
        store.removeListener(storeListener);
    }

    public void addListener(@NonNull Listener listener) { listeners.add(listener); }

    public void removeListener(@NonNull Listener listener) { listeners.remove(listener); }

    /** The page's resolved Look, cached until a write that touches it. */
    @NonNull
    public Snapshot of(@NonNull Page page) {
        Snapshot cached = cache.get(page.id);
        if (cached != null && cached.kind == page.kind) return cached;
        Snapshot built = new Snapshot(store, page);
        cache.put(page.id, built);
        return built;
    }

    /**
     * Writes a Look number at a scope. Global writes the plain key (Base or a surface's wall-wide
     * value); a page writes its override, and only for a key a page may detach. Returns whether it
     * wrote.
     */
    public boolean write(@NonNull Scope scope, @NonNull String key, int value) {
        if (!LookKeys.KNOWN.contains(key)) return false;
        if (scope.isGlobal()) {
            store.putInt(key, value);
            return true;
        }
        if (!LookKeys.OVERRIDABLE.contains(key)) return false;
        store.putInt(LookKeys.pageKey(key, scope.pageId()), value);
        return true;
    }

    /** Writes a Look string (the material) at a scope; the same rules as {@link #write}. */
    public boolean writeString(@NonNull Scope scope, @NonNull String key, @NonNull String value) {
        if (!LookKeys.KNOWN.contains(key)) return false;
        if (scope.isGlobal()) {
            store.putString(key, value);
            return true;
        }
        if (!LookKeys.OVERRIDABLE.contains(key)) return false;
        store.putString(LookKeys.pageKey(key, scope.pageId()), value);
        return true;
    }

    /** Clears the level of the scope: the page's override, or the wall-wide value of the key. */
    public void reset(@NonNull Scope scope, @NonNull String key) {
        if (!LookKeys.KNOWN.contains(key)) return;
        store.remove(scope.isGlobal() ? key : LookKeys.pageKey(key, scope.pageId()));
    }

    /** Whether a page may hold its own value of this key. */
    public static boolean isDetachable(@NonNull String key) {
        return LookKeys.OVERRIDABLE.contains(key);
    }

    /**
     * Removes the {@code @id} keys of every page not in {@code liveIds}. Silent: no page that is
     * live changes. Returns how many keys went.
     */
    public int pruneUnknown(@NonNull Set<Integer> liveIds) {
        List<String> doomed = new ArrayList<>();
        for (String key : store.keys()) {
            int id = LookKeys.pageIdOf(key);
            if (id >= 0 && LookKeys.isKnown(key) && !liveIds.contains(id)) doomed.add(key);
        }
        removeQuietly(doomed);
        return doomed.size();
    }

    /** Removes one page's overrides (the page left the wall) and forgets its snapshot. */
    public int dropPage(int id) {
        List<String> doomed = new ArrayList<>();
        for (String key : store.keys())
            if (LookKeys.pageIdOf(key) == id && LookKeys.isKnown(key)) doomed.add(key);
        removeQuietly(doomed);
        cache.remove(id);
        return doomed.size();
    }

    private void removeQuietly(List<String> keys) {
        quiet = true;
        try {
            for (String key : keys) {
                store.remove(key);
                int id = LookKeys.pageIdOf(key);
                if (id >= 0) cache.remove(id);
            }
        } finally {
            quiet = false;
        }
    }

    private void onStoreKeyChanged(@Nullable String key) {
        if (quiet) return;
        if (key != null && !LookKeys.isKnown(key)) return;
        int id = key == null ? -1 : LookKeys.pageIdOf(key);
        Set<Integer> touched = new HashSet<>();
        if (id >= 0) {
            cache.remove(id);
            touched.add(id);
        } else {
            touched.addAll(cache.keySet());
            cache.clear();
        }
        if (touched.isEmpty()) return;
        Set<Integer> out = Collections.unmodifiableSet(touched);
        for (Listener listener : listeners) listener.changed(out);
    }
}
