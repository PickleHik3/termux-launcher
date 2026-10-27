package com.termux.shared.termux.settings.preferences;

import androidx.annotation.NonNull;

import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceProperty;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceSlot;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * The key scheme of the page Look: which preference keys a page may override, and the
 * {@code <key>@<pageId>} form of an override. One list, read by the app's PageLook and by the
 * half-API shim in {@link TermuxAppSharedPreferences}.
 */
public final class PageLookKeys {

    private PageLookKeys() { }

    public static final String PAGE_SEPARATOR = "@";

    /** The preference holding the Terminal page's id, written by the app at start (-1: none). */
    public static final String KEY_TERMINAL_PAGE_ID = "wall_terminal_page_id";

    /**
     * The keys a page may hold its own value of: blur, opacity, grain and tint of the dock, status
     * bar, keyboard and canvas, the keyboard backdrop opacity and the wallpaper dim. Everything
     * else (Base, the inherit flags, shapes, material, intensity) is wall-wide only.
     */
    public static final Set<String> DETACHABLE;

    static {
        Set<String> keys = new HashSet<>();
        for (SurfaceSlot slot : SurfaceSlot.values()) {
            for (SurfaceProperty property : SurfaceProperty.values()) {
                if (!isLookProperty(property)) continue;
                String key = TermuxAppSharedPreferences.surfaceOverrideKey(slot, property);
                if (key != null) keys.add(key);
            }
        }
        keys.add(TERMUX_APP.KEY_IN_APP_KEYBOARD_BACKDROP_OPACITY);
        keys.add(TERMUX_APP.KEY_WALLPAPER_BACKDROP_DIM);
        DETACHABLE = Collections.unmodifiableSet(keys);
    }

    public static boolean isLookProperty(@NonNull SurfaceProperty property) {
        return property == SurfaceProperty.BLUR || property == SurfaceProperty.OPACITY
            || property == SurfaceProperty.GRAIN || property == SurfaceProperty.TINT;
    }

    /** The key a page's override of {@code key} lives under. */
    @NonNull
    public static String pageKey(@NonNull String key, int pageId) {
        return key + PAGE_SEPARATOR + pageId;
    }
}
