package com.termux.app.launcher.widget.builtin;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Map;

/**
 * The Glass switch every built-in widget's settings carry, per widget: off by default, the card
 * exactly as it has always been; on, the card is the launcher's glass. The wallpaper's shared
 * blur ({@code PaneGlassBackdropView}, the slab the panes and pages wear) is drawn under a
 * see-through card in the scheme's surface colour, with the glass rim, so the widget's ink keeps
 * reading as it does on the solid card. Where the launcher has no blur to give (no wallpaper
 * glass configured), the see-through card alone is the glass.
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
     * Whether the widget's card is see-through: always while the whole page is glass (as before),
     * and while the widget's own switch is on.
     */
    public static boolean cardGlass(boolean pageGlass, boolean widgetGlass) {
        return pageGlass || widgetGlass;
    }

    /**
     * Whether the widget draws the wallpaper's blur under its card: its switch on, a live widget
     * (a picker preview samples nothing), and the launcher's glass up with a frame to give.
     */
    public static boolean slabShown(boolean widgetGlass, boolean preview, boolean launcherGlass) {
        return widgetGlass && !preview && launcherGlass;
    }
}
