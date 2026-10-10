package com.termux.shared.termux.settings.preferences;

import androidx.annotation.NonNull;

/**
 * The terminal palette recipe per level. Each level owns its text and colour outright rather than
 * only a floor under them: a foreground tone and a background tone for each polarity, a chroma
 * multiplier on the theme's own chroma, and a body-text target the foreground is held to against
 * whatever the text really stands on. Softer is pastel on a lifted ground, Default is the system
 * Material scheme as declared, Harder is the deepest ground, the brightest text and the most
 * chroma. Changes the terminal palette only, never the chrome bands or the pane's glass.
 */
public enum TerminalContrastLevel {
    //        value      body  ansi  cursor ×chroma chroma band  normal tone  bright tone  fg tone     bg tone     reach
    //                                              min   max    dark  light  dark  light  dark light  dark light
    SOFTER("softer",     3.0d, 3.5d, 3.0d, 0.65d,  14d,  30d,   84d,  46d,   92d,  34d,   80d, 30d,  16d,  90d,   4d),
    DEFAULT("default",   4.5d, 4.5d, 3.0d, 1.0d,   28d,  52d,   80d,  40d,   90d,  30d,   90d, 10d,   8d,  97d,  10d),
    HARDER("harder",     7.0d, 6.0d, 4.5d, 1.4d,   44d,  80d,   78d,  38d,   90d,  28d,   96d,  6d,   2d, 100d,  20d);

    @NonNull public final String value;
    /**
     * The contrast body text is held to: WCAG large-text 3.0, AA 4.5, AAA 7.0. The one source of
     * these numbers — the chrome's {@code LegibilityLevel} reads them from here.
     */
    public final double bodyTarget;
    public final double ansiRatio;
    public final double cursorRatio;
    /** Multiplies the theme's chroma before the band below clamps it. */
    public final double chromaScale;
    public final double chromaMin;
    public final double chromaMax;
    public final double normalToneDark;
    public final double normalToneLight;
    public final double brightToneDark;
    public final double brightToneLight;
    /** The foreground's HCT tone on a dark palette (pale ink) and on a light one (dark ink). */
    public final double fgToneDark;
    public final double fgToneLight;
    public final double bgToneDark;
    public final double bgToneLight;
    /**
     * How far, in HCT tone, a colour may move from its recipe tone to chase its contrast target.
     * The target is what the level aims for, not a demand: over a mid-tone wallpaper no colour can
     * reach 4.5:1, and chasing it all the way ends every colour at black or white.
     */
    public final double toneReach;

    TerminalContrastLevel(@NonNull String value, double bodyTarget, double ansiRatio,
                          double cursorRatio, double chromaScale, double chromaMin,
                          double chromaMax, double normalToneDark, double normalToneLight,
                          double brightToneDark, double brightToneLight,
                          double fgToneDark, double fgToneLight,
                          double bgToneDark, double bgToneLight, double toneReach) {
        this.value = value;
        this.bodyTarget = bodyTarget;
        this.ansiRatio = ansiRatio;
        this.cursorRatio = cursorRatio;
        this.chromaScale = chromaScale;
        this.chromaMin = chromaMin;
        this.chromaMax = chromaMax;
        this.normalToneDark = normalToneDark;
        this.normalToneLight = normalToneLight;
        this.brightToneDark = brightToneDark;
        this.brightToneLight = brightToneLight;
        this.fgToneDark = fgToneDark;
        this.fgToneLight = fgToneLight;
        this.bgToneDark = bgToneDark;
        this.bgToneLight = bgToneLight;
        this.toneReach = toneReach;
    }

    /** {@code sourceChroma} as this level spends it: scaled, then held inside the level's band. */
    public double accentChroma(double sourceChroma) {
        return Math.max(chromaMin, Math.min(chromaMax, sourceChroma * chromaScale));
    }

    @NonNull
    public static TerminalContrastLevel from(@NonNull String value) {
        for (TerminalContrastLevel level : values()) {
            if (level.value.equals(value)) return level;
        }
        return DEFAULT;
    }
}
