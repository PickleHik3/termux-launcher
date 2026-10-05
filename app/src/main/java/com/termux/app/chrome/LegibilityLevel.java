package com.termux.app.chrome;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.termux.settings.preferences.TerminalContrastLevel;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

/**
 * The band legibility level. It used to follow the "Terminal contrast" preference; that preference
 * now changes only the terminal's own palette ({@link TerminalContrastLevel}), so
 * {@link #of(TermuxAppSharedPreferences)} always answers {@link #DEFAULT} and every band is held
 * to the original {@link OnGlass} targets. The class and {@link #target(double)} stay so callers
 * keep compiling.
 *
 * <p>Pure and context free, so it unit tests on the JVM.</p>
 */
public enum LegibilityLevel {

    /** Softer: body text at 3.0, the WCAG large-text floor. The wallpaper shows most. */
    SOFTER(TerminalContrastLevel.SOFTER, 3.0d),
    /** Default: body text at 4.5, WCAG AA — exactly the targets {@link OnGlass} was written with. */
    DEFAULT(TerminalContrastLevel.DEFAULT, OnGlass.TARGET_BODY_TEXT),
    /** Harder: body text at 7.0, WCAG AAA. The most veil, the most legible. */
    HARDER(TerminalContrastLevel.HARDER, 7.0d);

    /** The terminal palette contrast this same choice sets; what the palette reads. */
    @NonNull public final TerminalContrastLevel terminalContrast;

    /** The body-text target every band is held to at this level. */
    public final double bodyText;

    LegibilityLevel(@NonNull TerminalContrastLevel terminalContrast, double bodyText) {
        this.terminalContrast = terminalContrast;
        this.bodyText = bodyText;
    }

    /** The level the palette's own contrast choice means; null reads as {@link #DEFAULT}. */
    @NonNull
    public static LegibilityLevel of(@Nullable TerminalContrastLevel level) {
        if (level == null) return DEFAULT;
        for (LegibilityLevel candidate : values()) {
            if (candidate.terminalContrast == level) return candidate;
        }
        return DEFAULT;
    }

    /** Always {@link #DEFAULT}: the contrast preference no longer moves the chrome bands. */
    @NonNull
    public static LegibilityLevel of(@Nullable TermuxAppSharedPreferences preferences) {
        return DEFAULT;
    }

    /**
     * The terminal palette level the preferences ask for; read straight from the preference, not
     * through {@link #of(TermuxAppSharedPreferences)}.
     */
    @NonNull
    public static TerminalContrastLevel terminalContrast(
            @Nullable TermuxAppSharedPreferences preferences) {
        return preferences == null ? TerminalContrastLevel.DEFAULT
            : preferences.getTerminalContrastLevel();
    }

    /** How far this level moves every tier: 1 at {@link #DEFAULT}. */
    public double factor() {
        return bodyText / OnGlass.TARGET_BODY_TEXT;
    }

    /**
     * {@code tier} at this level: one of {@link OnGlass#TARGET_BODY_TEXT},
     * {@link OnGlass#TARGET_LARGE_TEXT} or {@link OnGlass#TARGET_DECORATION} scaled by
     * {@link #factor()}, never below {@link OnGlass#TARGET_DECORATION} (and never below a tier
     * that was already under it). At {@link #DEFAULT} every tier comes back unchanged.
     */
    public double target(double tier) {
        if (this == DEFAULT) return tier;
        // Exact for the body tier, rather than 7.000000000000001 out of the division.
        if (Double.compare(tier, OnGlass.TARGET_BODY_TEXT) == 0) return bodyText;
        double floor = Math.min(tier, OnGlass.TARGET_DECORATION);
        return Math.max(floor, tier * factor());
    }
}
