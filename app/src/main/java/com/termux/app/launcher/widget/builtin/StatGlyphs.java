package com.termux.app.launcher.widget.builtin;

import androidx.annotation.NonNull;

/**
 * The Nerd Font glyphs the built-in widgets put where a reading's name would go: a glyph and its
 * value, never a word. CPU and memory are the status bar's own glyphs, so a reading looks the same
 * wherever the launcher shows it. Android-free; the word stays as the glyph's spoken description.
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
