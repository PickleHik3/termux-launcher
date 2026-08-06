package com.termux.shared.termux.settings.preferences;

import androidx.annotation.NonNull;

/** Contrast targets used by the wallpaper-derived terminal palette. */
public enum TerminalContrastLevel {
    SOFTER("softer", 4.5d, 4.5d, 3.0d),
    DEFAULT("default", 7.0d, 4.5d, 3.0d),
    HARDER("harder", 10.0d, 7.0d, 4.5d);

    @NonNull public final String value;
    public final double foregroundRatio;
    public final double ansiRatio;
    public final double cursorRatio;

    TerminalContrastLevel(@NonNull String value, double foregroundRatio, double ansiRatio,
                          double cursorRatio) {
        this.value = value;
        this.foregroundRatio = foregroundRatio;
        this.ansiRatio = ansiRatio;
        this.cursorRatio = cursorRatio;
    }

    @NonNull
    public static TerminalContrastLevel from(@NonNull String value) {
        for (TerminalContrastLevel level : values()) {
            if (level.value.equals(value)) return level;
        }
        return DEFAULT;
    }
}
