package com.termux.app.launcher.widget.builtin;

import androidx.annotation.NonNull;

/**
 * The Roundline icons (as {@link WidgetIcons} glyphs) the built-in widgets put
 * beside a reading: on the Device widget an icon leads its bar's word label ("RAM 3.1/7.7 GB"), never stands in for it. CPU and memory are the
 * status bar's own icons, so a reading looks the same wherever the launcher shows it.
 * Android-free.
 */
public final class StatGlyphs {
    private StatGlyphs() { }

    /** The status bar's CPU icon. */
    public static final String CPU = WidgetIcons.CPU;
    /** The status bar's RAM icon. */
    public static final String MEMORY = WidgetIcons.MEMORY;
    /** A thermometer. */
    public static final String TEMPERATURE = WidgetIcons.TEMPERATURE;
    /** A battery. */
    public static final String BATTERY = WidgetIcons.BATTERY;
    /** A drive. */
    public static final String DISK = WidgetIcons.DISK;
    /** The charging mark: a phone with its cable plugged in. */
    public static final String POWER = WidgetIcons.POWER;

    /** A reading inside running text: "{glyph} 31.4°C". */
    @NonNull public static String reading(@NonNull String glyph, @NonNull String value) {
        return glyph + " " + value;
    }
}
