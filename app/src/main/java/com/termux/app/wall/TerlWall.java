package com.termux.app.wall;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.page.PageKind;

/**
 * v1's wall under Terl (the Terl switch on, with the tiling desktop built): Home's pages alone.
 * Terminals, the Display and the Assistant are windows of the **Workspace** stack below Home, so
 * the wall lays out no Terminal, Display or Assistant page, and Home's page glyph row is Terl's own
 * {@code home.page_dots} (off by default; the control bar shows a dot per Home page), naming Home's
 * pages only when on. With the switch off every answer is v1's.
 *
 * <p>Pure: the switch goes in, the answers come out.
 */
public final class TerlWall {

    private TerlWall() {}

    /**
     * Settings › Home › Page indicator as the Home grid wears it: under Terl Terl's own
     * {@code home.page_dots} ({@code terlDots}, off by default), never v1's choice.
     */
    @androidx.annotation.NonNull
    public static com.termux.app.launcher.home.HomePageIndicator indicator(
            @androidx.annotation.NonNull com.termux.app.launcher.home.HomePageIndicator chosen,
            boolean terl, boolean terlDots) {
        if (!terl) return chosen;
        return terlDots ? com.termux.app.launcher.home.HomePageIndicator.GLYPHS
            : com.termux.app.launcher.home.HomePageIndicator.OFF;
    }

    /** Whether the wall lays out a page of {@code kind}: under Terl only Home's. */
    public static boolean laysOut(@Nullable PageKind kind, boolean terl) {
        return !terl || kind == PageKind.HOME;
    }
}
