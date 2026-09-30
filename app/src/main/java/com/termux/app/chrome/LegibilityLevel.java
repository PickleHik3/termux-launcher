package com.termux.app.chrome;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.termux.settings.preferences.TerminalContrastLevel;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

/**
 * How hard every band in the launcher works to be read: the one legibility control, and the one
 * place it is read from.
 *
 * <p>It is the "Terminal contrast" preference ({@code terminal_contrast_level}, Softer / Default /
 * Harder) and nothing new: no second key, no second slider. That preference always drove the
 * terminal palette's own contrast ({@link TerminalContrastLevel}); it now also sets the body-text
 * target every band's veil is bought against (appearance-layout-editor SPEC §2) — 3.0, 4.5 or
 * 7.0 — and every other tier moves by the same factor. The palette and {@link ChromeInk} both ask
 * here, so the two meanings of the one control cannot drift apart.</p>
 *
 * <p>Scaling is proportional, with one floor: {@link OnGlass#TARGET_DECORATION} never drops below
 * itself. Softer would otherwise put the separator dots at 1.33:1, which is where the reporting
 * device measured them invisible (1.01) before the decoration tier existed. Large text at Softer
 * lands on 2.0 exactly, at the same floor; nothing reaches under it.</p>
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

    /** The level the preferences hold; null preferences (a test, a detached host) read as default. */
    @NonNull
    public static LegibilityLevel of(@Nullable TermuxAppSharedPreferences preferences) {
        return preferences == null ? DEFAULT : of(preferences.getTerminalContrastLevel());
    }

    /**
     * The terminal palette contrast the preferences ask for. The palette's side of the same
     * accessor: whoever builds a palette asks here, as {@link ChromeInk} does for its targets.
     */
    @NonNull
    public static TerminalContrastLevel terminalContrast(
            @Nullable TermuxAppSharedPreferences preferences) {
        return of(preferences).terminalContrast;
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
