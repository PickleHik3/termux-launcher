package com.termux.shared.termux.settings.preferences;

import androidx.annotation.NonNull;

/**
 * The terminal palette recipe per level: Softer is pastel, Default is the system Material scheme
 * as declared, Harder is punchier (more chroma) without darkening. Changes the terminal palette
 * only, never the chrome bands.
 */
public enum TerminalContrastLevel {
    SOFTER("softer", 4.5d, 3.5d, 3.0d, 18d, 30d, 84d, 46d, 92d, 34d, 14d, 94d),
    DEFAULT("default", 7.0d, 4.5d, 3.0d, 28d, 52d, 80d, 40d, 90d, 30d, 8d, 97d),
    HARDER("harder", 10.0d, 6.0d, 4.5d, 44d, 72d, 78d, 38d, 90d, 28d, 6d, 98d);

    @NonNull public final String value;
    public final double foregroundRatio;
    public final double ansiRatio;
    public final double cursorRatio;
    public final double chromaMin;
    public final double chromaMax;
    public final double normalToneDark;
    public final double normalToneLight;
    public final double brightToneDark;
    public final double brightToneLight;
    public final double bgToneDark;
    public final double bgToneLight;

    TerminalContrastLevel(@NonNull String value, double foregroundRatio, double ansiRatio,
                          double cursorRatio, double chromaMin, double chromaMax,
                          double normalToneDark, double normalToneLight,
                          double brightToneDark, double brightToneLight,
                          double bgToneDark, double bgToneLight) {
        this.value = value;
        this.foregroundRatio = foregroundRatio;
        this.ansiRatio = ansiRatio;
        this.cursorRatio = cursorRatio;
        this.chromaMin = chromaMin;
        this.chromaMax = chromaMax;
        this.normalToneDark = normalToneDark;
        this.normalToneLight = normalToneLight;
        this.brightToneDark = brightToneDark;
        this.brightToneLight = brightToneLight;
        this.bgToneDark = bgToneDark;
        this.bgToneLight = bgToneLight;
    }

    @NonNull
    public static TerminalContrastLevel from(@NonNull String value) {
        for (TerminalContrastLevel level : values()) {
            if (level.value.equals(value)) return level;
        }
        return DEFAULT;
    }
}
