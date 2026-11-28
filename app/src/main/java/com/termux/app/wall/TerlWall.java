package com.termux.app.wall;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.page.PageKind;

/**
 * v1's wall under Terl (the Terl switch on, with the tiling desktop built): Home's pages alone.
 * Terminals, the Display and the Assistant are windows of the **Workspace** stack below Home, so
 * the wall lays out no Terminal, Display or Assistant page, and Home's page dots (the Home card's
 * Page dots) name Home's pages only, with no Terminal windows among them. With the switch off
 * every answer is v1's.
 *
 * <p>Pure: the switch goes in, the answers come out.
 */
public final class TerlWall {

    private TerlWall() {}

    /** Whether the wall lays out a page of {@code kind}: under Terl only Home's. */
    public static boolean laysOut(@Nullable PageKind kind, boolean terl) {
        return !terl || kind == PageKind.HOME;
    }
}
