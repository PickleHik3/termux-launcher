package com.termux.app.launcher.widget.builtin;

import androidx.annotation.NonNull;

/**
 * The Nerd Font glyphs the built-in widgets put beside a reading: on the Device widget a glyph
 * leads its bar's word label ("RAM 3.1/7.7 GB"), never stands in for it. CPU and memory are the
 * status bar's own glyphs, so a reading looks the same wherever the launcher shows it.
 * Android-free.
 */
public final class StatGlyphs {
    private StatGlyphs() { }

    /** nf-oct-cpu, as the status bar's CPU reading. */
    public static final String CPU = "";
    /** nf-fa-memory, as the status bar's RAM reading. */
    public static final String MEMORY = "";
    /** nf-fa-thermometer_half. */
    public static final String TEMPERATURE = "";
    /** nf-fa-battery_full. */
    public static final String BATTERY = "";
    /** nf-fa-hdd_o (nf-fa-hard_drive). */
    public static final String DISK = "";
    /** nf-fa-bolt; also the charging mark. */
    public static final String POWER = "";

    /** A reading inside running text: "{glyph} 31.4°C". */
    @NonNull public static String reading(@NonNull String glyph, @NonNull String value) {
        return glyph + " " + value;
    }
}
