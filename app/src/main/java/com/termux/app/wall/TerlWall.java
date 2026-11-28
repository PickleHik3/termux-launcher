package com.termux.app.wall;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.launcher.home.HomePageIndicator;
import com.termux.app.page.PageKind;

/**
 * v1's wall under Terl (the Terl switch on, with the tiling desktop built): Home's pages alone.
 * Terminals, the Display and the Assistant are windows of the **Workspace** stack below Home, so
 * the wall lays out no Terminal, Display or Assistant page, and v1's page glyph row under the Home
 * grid stands down: on Home the **control bar** shows a dot per Home page instead. With the switch
 * off every answer is v1's.
 *
 * <p>Pure: the switch goes in, the answers come out.
 */
public final class TerlWall {

    private TerlWall() {}

    /** Whether the wall lays out a page of {@code kind}: under Terl only Home's. */
    public static boolean laysOut(@Nullable PageKind kind, boolean terl) {
        return !terl || kind == PageKind.HOME;
    }

    /** Settings › Home › Page indicator as the Home grid wears it: off under Terl. */
    @NonNull
    public static HomePageIndicator indicator(@NonNull HomePageIndicator chosen, boolean terl) {
        return terl ? HomePageIndicator.OFF : chosen;
    }
}
