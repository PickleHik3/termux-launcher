package com.termux.app.wall;

import androidx.annotation.Nullable;

import com.termux.app.page.PageKind;

/** Where a wall page starts below the canvas's top; the bands themselves come from place. */
public final class PageTopInset {

    private PageTopInset() {}

    /**
     * Where {@code page} starts below the canvas's top: a Home page at Home's inset; every
     * Terminal page ({@code null} is one the wall does not know, the display window's) at the
     * Terminal's inset.
     */
    public static int pageTopInsetPx(@Nullable PageKind page, int terminalTopInsetPx,
                                     int homeTopInsetPx) {
        if (page == PageKind.HOME) return Math.max(0, homeTopInsetPx);
        return Math.max(0, terminalTopInsetPx);
    }
}
