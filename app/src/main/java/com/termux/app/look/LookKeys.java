package com.termux.app.look;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.wall.WallPages.PageKind;
import com.termux.shared.termux.settings.preferences.FrostStops;
import com.termux.shared.termux.settings.preferences.LookGrid;
import com.termux.shared.termux.settings.preferences.PageLookKeys;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceProperty;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceSlot;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

import java.util.HashSet;
import java.util.Set;

/**
 * The key scheme and resolution rules behind {@link PageLook}. Key names and defaults are the
 * preference constants' own; the rules mirror {@code TermuxAppSharedPreferences} (override key,
 * Base key, {@code surface_inherit_<slot>_<property>} flag) minus the halves.
 *
 * <p>A page override is the surface's key plus {@code "@<pageId>"}. The plain key is the wall-wide
 * value (the key the pre-halves installs wrote, and what a "both halves" write still writes).
 */
final class LookKeys {

    private LookKeys() { }

    static final String PAGE_SEPARATOR = PageLookKeys.PAGE_SEPARATOR;

    /** The key a page's override of {@code key} lives under. */
    @NonNull
    static String pageKey(@NonNull String key, int pageId) {
        return PageLookKeys.pageKey(key, pageId);
    }

    /** The page id a key's {@code @<id>} suffix names, or -1 when it has none. */
    static int pageIdOf(@NonNull String key) {
        int at = key.lastIndexOf('@');
        if (at <= 0 || at == key.length() - 1) return -1;
        try {
            int id = Integer.parseInt(key.substring(at + 1));
            return id < 0 ? -1 : id;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** The key without its {@code @<id>} suffix. */
    @NonNull
    static String plainOf(@NonNull String key) {
        return pageIdOf(key) < 0 ? key : key.substring(0, key.lastIndexOf('@'));
    }

    static boolean isLookProperty(@NonNull SurfaceProperty property) {
        return PageLookKeys.isLookProperty(property);
    }

    static boolean isShape(@NonNull SurfaceProperty property) {
        return !isLookProperty(property);
    }


    /** The surface's own key for a property, or null where the surface has none. */
    @Nullable
    static String surfaceKey(@NonNull SurfaceSlot slot, @NonNull SurfaceProperty property) {
        return TermuxAppSharedPreferences.surfaceOverrideKey(slot, property);
    }

    /** The number a surface ships with for a property (its own key's default). */
    static int surfaceDefault(@NonNull SurfaceSlot slot, @NonNull SurfaceProperty property) {
        switch (slot) {
            case KEYBOARD:
                switch (property) {
                    case BLUR: return TERMUX_APP.DEFAULT_SURFACE_BASE_BLUR;
                    case OPACITY: return TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_BACKGROUND_OPACITY;
                    case GRAIN: return TERMUX_APP.DEFAULT_SURFACE_BASE_GRAIN;
                    case TINT: return TERMUX_APP.DEFAULT_SURFACE_BASE_TINT;
                    default: return TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_HORIZONTAL_INSET;
                }
            case STATUS:
                switch (property) {
                    case BLUR: return TERMUX_APP.DEFAULT_STATUS_BAR_BLUR_RADIUS;
                    case OPACITY: return TERMUX_APP.DEFAULT_STATUS_BAR_OPACITY;
                    case GRAIN: return TERMUX_APP.DEFAULT_STATUS_BAR_GRAIN;
                    case TINT: return TERMUX_APP.DEFAULT_SURFACE_BASE_TINT;
                    case CORNER_RADIUS: return TERMUX_APP.DEFAULT_STATUS_BAR_CORNER_RADIUS;
                    default: return TERMUX_APP.DEFAULT_SURFACE_HORIZONTAL_INSET;
                }
            case CANVAS:
                switch (property) {
                    case BLUR: return TERMUX_APP.DEFAULT_TERMINAL_GLASS_BLUR_RADIUS;
                    case GRAIN: return TERMUX_APP.DEFAULT_TERMINAL_GLASS_GRAIN;
                    case TINT: return TERMUX_APP.DEFAULT_SURFACE_BASE_TINT;
                    default: return TERMUX_APP.DEFAULT_VALUE_TERMINAL_BACKGROUND_OPACITY;
                }
            default:
                switch (property) {
                    case BLUR: return TERMUX_APP.DEFAULT_VALUE_EXTRAKEYS_BLUR_RADIUS;
                    case OPACITY: return TERMUX_APP.DEFAULT_VALUE_APP_BAR_OPACITY;
                    case GRAIN: return TERMUX_APP.DEFAULT_VALUE_DOCK_GLASS_GRAIN;
                    case TINT: return TERMUX_APP.DEFAULT_SURFACE_BASE_TINT;
                    case CORNER_RADIUS: return TERMUX_APP.DEFAULT_APP_LAUNCHER_DOCK_CORNER_RADIUS;
                    default: return TERMUX_APP.DEFAULT_SURFACE_HORIZONTAL_INSET;
                }
        }
    }

    /** The range the getters clamp a look number to: blur 0..48, the rest 0..100. */
    static int clamp(@NonNull SurfaceProperty property, int value) {
        int max = property == SurfaceProperty.BLUR ? 48 : 100;
        return Math.max(0, Math.min(max, value));
    }

    /**
     * Whether a page may hold its own value of this surface property: blur, opacity, grain and tint
     * of the dock, status bar and keyboard, and of the canvas on a Terminal page.
     */
    static boolean detachable(@NonNull SurfaceSlot slot, @NonNull SurfaceProperty property,
                              @NonNull PageKind kind) {
        if (!isLookProperty(property) || surfaceKey(slot, property) == null) return false;
        return slot != SurfaceSlot.CANVAS || kind == PageKind.TERMINAL;
    }

    /** The keyboard backdrop opacity as the getter reads it: -1 (unset) renders opaque. */
    static int normalizeBackdrop(int raw) {
        if (raw < 0) return TERMUX_APP.MAX_IN_APP_KEYBOARD_BACKDROP_OPACITY;
        return Math.max(TERMUX_APP.MIN_IN_APP_KEYBOARD_BACKDROP_OPACITY,
            Math.min(TERMUX_APP.MAX_IN_APP_KEYBOARD_BACKDROP_OPACITY, raw));
    }

    /** The wallpaper dim as its getter reads it, including the retired opacity key's fallback. */
    static int wallpaperDim(@NonNull LookStore store, int pageId) {
        String pageKey = pageKey(TERMUX_APP.KEY_WALLPAPER_BACKDROP_DIM, pageId);
        int stored = pageId >= 0 && store.has(pageKey) ? store.getInt(pageKey, -1) : -1;
        if (stored < 0) stored = store.getInt(TERMUX_APP.KEY_WALLPAPER_BACKDROP_DIM, -1);
        if (stored < 0) {
            int legacy = store.getInt(TERMUX_APP.KEY_WALLPAPER_BACKDROP_OPACITY, -1);
            stored = legacy < 0 ? TERMUX_APP.DEFAULT_WALLPAPER_BACKDROP_DIM
                : 100 - Math.max(0, Math.min(100, legacy));
        }
        return Math.max(0, Math.min(100, stored));
    }

    /**
     * One surface number for a page: page override, else the surface's wall-wide value when it is
     * detached from Base, else Base. Shapes answer Base. A slot without the property answers the
     * shipped value.
     */
    static int resolve(@NonNull LookStore store, @NonNull PageKind kind, int pageId,
                       @NonNull SurfaceSlot slot, @NonNull SurfaceProperty property) {
        LookGrid grid = new LookGrid(gridStore(store));
        LookGrid.Element element = property.grid.perElement ? slot.element : null;
        return fromCell(property.grid, grid.value(property.grid, element,
            pageId >= 0 ? pageId : LookGrid.NO_PAGE));
    }

    /** A value in the old unit of the cell's property: a Frost stop as its blur in dp. */
    static int fromCell(@NonNull LookGrid.Property property, int value) {
        return property == LookGrid.Property.FROST ? FrostStops.blurDpFor(value) : value;
    }

    /** A value in the old unit as the cell holds it: a blur as its nearest Frost stop. */
    static int toCell(@NonNull LookGrid.Property property, int value) {
        return property == LookGrid.Property.FROST ? FrostStops.nearestStopByBlur(value) : value;
    }

    /**
     * The grid cell (everywhere) an old look key names, or null for a key outside the grid: a
     * Base key is the all-glass cell, a surface's own key that surface's element cell. A grid key
     * is read as itself, its page dropped.
     */
    @Nullable
    static LookGrid.Key cellOf(@NonNull String key) {
        LookGrid.Key grid = LookGrid.parse(key);
        if (grid != null) return new LookGrid.Key(grid.property, grid.element, LookGrid.NO_PAGE);
        for (SurfaceProperty property : SurfaceProperty.values()) {
            if (property.baseKey.equals(key))
                return new LookGrid.Key(property.grid, null, LookGrid.NO_PAGE);
            for (SurfaceSlot slot : SurfaceSlot.values()) {
                if (key.equals(surfaceKey(slot, property)))
                    return new LookGrid.Key(property.grid,
                        property.grid.perElement ? slot.element : null, LookGrid.NO_PAGE);
            }
        }
        return null;
    }

    /** The grid's view of a look store. */
    @NonNull
    static LookGrid.Store gridStore(@NonNull LookStore store) {
        return new LookGrid.Store() {
            @Override public boolean contains(@NonNull String key) { return store.has(key); }
            @Override public int getInt(@NonNull String key, int defaultValue) {
                return store.getInt(key, defaultValue);
            }
            @Nullable @Override public String getString(@NonNull String key, @Nullable String defaultValue) {
                return store.has(key) ? store.getString(key, defaultValue == null ? "" : defaultValue)
                    : defaultValue;
            }
            @Override public boolean getBoolean(@NonNull String key, boolean defaultValue) {
                return store.getBoolean(key, defaultValue);
            }
            @Override public void putInt(@NonNull String key, int value) { store.putInt(key, value); }
            @Override public void putString(@NonNull String key, @NonNull String value) {
                store.putString(key, value);
            }
            @Override public void putBoolean(@NonNull String key, boolean value) {
                store.putBoolean(key, value);
            }
            @Override public void remove(@NonNull String key) { store.remove(key); }
            @NonNull @Override public Set<String> keys() { return new HashSet<>(store.keys()); }
        };
    }

    /** Every key PageLook reads: a change to one of these (or its {@code @id} form) is a Look change. */
    static final Set<String> KNOWN = new HashSet<>();

    /** The keys a page may override: detachable surface keys and the two ground/keyboard inputs. */
    static final Set<String> OVERRIDABLE = PageLookKeys.DETACHABLE;

    static {
        for (SurfaceSlot slot : SurfaceSlot.values()) {
            for (SurfaceProperty property : SurfaceProperty.values()) {
                String key = surfaceKey(slot, property);
                if (key == null) continue;
                KNOWN.add(key);
            }
        }
        for (SurfaceProperty property : SurfaceProperty.values()) KNOWN.add(property.baseKey);
        KNOWN.add(TERMUX_APP.KEY_IN_APP_KEYBOARD_BACKDROP_OPACITY);
        KNOWN.add(TERMUX_APP.KEY_WALLPAPER_BACKDROP_DIM);
        KNOWN.add(TERMUX_APP.KEY_WALLPAPER_BACKDROP_OPACITY);
        KNOWN.add(TERMUX_APP.KEY_SURFACE_MATERIAL);
        KNOWN.add(TERMUX_APP.KEY_SURFACE_MATERIAL_INTENSITY);
    }

    /** True when the plain form of this key is one PageLook reads. */
    static boolean isKnown(@NonNull String key) {
        return KNOWN.contains(plainOf(key)) || LookGrid.parse(key) != null;
    }
}
