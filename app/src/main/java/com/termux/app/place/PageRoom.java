package com.termux.app.place;

import androidx.annotation.NonNull;

/**
 * The room a page leaves its content at its own rest (ADR 0030 section 3): how far the
 * page's cells end above the window's bottom. One number per page, read from that page's own rest
 * alone, so the rest and everything that predicts it (a keyboard's reveal, a crossing's end)
 * read the same function, and a crossing between two rests changes no page's room: both ends'
 * rests are known before it begins.
 *
 * <p>It is the stack's rest height for the page (the shared rows and the page's own lane, the
 * terminal's dock padding, the keyboard when that rest has it up), with the margin and foot
 * under it, less what an overlaying keyboard hands back, as {@link StackLayout} builds them;
 * Home's chrome floats over its grid and leaves it none.
 */
public final class PageRoom {

    private PageRoom() {}

    /** {@code rest}'s page's bottom inset at that rest. */
    public static int bottomInsetPx(@NonNull StackLayout.RestInput rest) {
        return StackLayout.restReservationPx(rest);
    }

    /**
     * Where a page's cells end above the wall's bottom: the page's room plus the band the
     * navigation inset keeps clear ({@link BottomInsets#keptPx}). The root layout that holds the
     * wall reaches the screen's bottom edge, under that band, where the content column it
     * replaced hung above the stack, which stands on it: the room alone put every page's cells
     * that band too low.
     */
    public static int wallInsetPx(int roomPx, int keptBandPx) {
        return Math.max(0, roomPx) + Math.max(0, keptBandPx);
    }

    /**
     * Whether a page whose cells stand {@code appliedInsetPx} above the bottom is resized by a
     * return to a rest of {@code restInsetPx}: only when its rest changed while it was away.
     */
    public static boolean resizesOnReturn(int appliedInsetPx, int restInsetPx) {
        return appliedInsetPx != restInsetPx;
    }
}
