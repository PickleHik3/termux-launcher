package com.termux.app.place;

import androidx.annotation.NonNull;

import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceProperty;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceSlot;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

/**
 * Which parts of the chrome a change of look has to repaint. The wall crossing between two places
 * that differ in one surface repaints that surface, not the whole appearance: a home screen with a
 * clear canvas costs a terminal repaint as the wall lands on it, not a dock, keyboard and status
 * bar rebuild besides. Anything this table cannot place repaints everything.
 */
public final class PlaceLookRefresh {

    /** A part of the chrome with a repaint of its own. */
    public enum Part { DOCK, KEYBOARD, STATUS, TERMINAL }

    private static final Map<String, EnumSet<Part>> PARTS = buildParts();

    private static Map<String, EnumSet<Part>> buildParts() {
        Map<String, EnumSet<Part>> parts = new HashMap<>();
        for (SurfaceSlot slot : SurfaceSlot.values()) {
            EnumSet<Part> part = partsOf(slot);
            for (SurfaceProperty property : SurfaceProperty.values()) {
                String key = TermuxAppSharedPreferences.surfaceOverrideKey(slot, property);
                if (key == null) continue;
                parts.put(key, part);
                parts.put(TERMUX_APP.KEY_SURFACE_INHERIT_PREFIX + slot.key + "_" + property.key,
                    part);
            }
        }
        parts.put(TERMUX_APP.KEY_APP_LAUNCHER_BUTTON_COUNT, EnumSet.of(Part.DOCK));
        parts.put(TERMUX_APP.KEY_IN_APP_KEYBOARD_KEY_CORNER_RADIUS_DP, EnumSet.of(Part.KEYBOARD));
        parts.put(TERMUX_APP.KEY_IN_APP_KEYBOARD_KEY_OPACITY, EnumSet.of(Part.KEYBOARD));
        parts.put(TERMUX_APP.KEY_IN_APP_KEYBOARD_KEY_MARGIN_SCALE, EnumSet.of(Part.KEYBOARD));
        parts.put(TERMUX_APP.KEY_STATUS_INDICATOR_CORNER_RADIUS, EnumSet.of(Part.STATUS));
        parts.put(TERMUX_APP.KEY_TERMINAL_CORNER_RADIUS, EnumSet.of(Part.TERMINAL));
        parts.put(TERMUX_APP.KEY_TERMINAL_PANE_GAP, EnumSet.of(Part.TERMINAL));
        // The dock's style and the wallpaper dim shape every surface; they stay out of the table
        // and so repaint everything, like any key it does not know.
        return parts;
    }

    /** The canvas tints the status bar's surface too, so it repaints both. */
    @NonNull
    private static EnumSet<Part> partsOf(@NonNull SurfaceSlot slot) {
        switch (slot) {
            case DOCK: return EnumSet.of(Part.DOCK);
            case KEYBOARD: return EnumSet.of(Part.KEYBOARD);
            case STATUS: return EnumSet.of(Part.STATUS);
            default: return EnumSet.of(Part.TERMINAL, Part.STATUS);
        }
    }

    /** The parts the given shared look keys, read differently than before, have to repaint. */
    @NonNull
    public static EnumSet<Part> partsFor(@NonNull Collection<String> keys) {
        EnumSet<Part> parts = EnumSet.noneOf(Part.class);
        for (String key : keys) {
            EnumSet<Part> part = PARTS.get(key);
            if (part == null) return EnumSet.allOf(Part.class);
            parts.addAll(part);
        }
        return parts;
    }

    private PlaceLookRefresh() { }
}
