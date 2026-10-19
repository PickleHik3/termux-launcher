package com.termux.app.launcher.widget.builtin;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Map;

/**
 * The Glass switch every built-in widget's settings carry, per widget: off by default, the solid
 * card; on, the widget wears the Look's glass — Base's blur, opacity, tint, grain, rim and Fancier
 * Glass, from the shared blur frames, drawn by the same slab a terminal pane wears
 * ({@code PaneGlass} over {@code LookGlassStyle}). A detached terminal, keyboard, status bar or
 * dock never reaches it. Where the launcher is not on the wallpaper there is no glass to wear,
 * and the see-through card alone stands in for it.
 *
 * <p>Pure: the view asks here and applies the answer.
 */
public final class BuiltinWidgetGlass {

    /** The key the switch is stored under in a widget's settings, and its value while on. */
    public static final String CONFIG_KEY = "glass";
    public static final String ON = "on";

    private BuiltinWidgetGlass() {}

    /** Whether a widget's stored settings value has the switch on. Absent or anything else is off. */
    public static boolean isOn(@Nullable String stored) {
        return ON.equals(stored);
    }

    /** The settings value for the switch: on is stored, off is left out like every empty field. */
    public static void write(@NonNull Map<String, String> values, boolean on) {
        values.put(CONFIG_KEY, on ? ON : "");
    }

    /**
     * Whether the widget wears the Look's slab: its switch on, a live widget (a picker preview
     * samples nothing), and the Look's glass worn at all (the launcher on the wallpaper).
     */
    public static boolean slabShown(boolean widgetGlass, boolean preview, boolean lookGlass) {
        return widgetGlass && !preview && lookGlass;
    }

    /**
     * Whether a widget fed the Look's corner radius has to be styled again: the style it was
     * built with was cut at another one. Compared at a quarter pixel, so float noise from the
     * same dp never restyles.
     */
    public static boolean cornersMoved(float styleRadiusPx, float lookRadiusPx) {
        return Math.abs(styleRadiusPx - lookRadiusPx) > 0.25f;
    }
}
