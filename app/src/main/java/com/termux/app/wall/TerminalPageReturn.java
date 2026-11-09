package com.termux.app.wall;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The Terminal page coming back (ADR 0015 section 1): a wall without one, once a terminal window
 * opens (Ctrl+Alt+C, a Terminal app, New terminal, the local API), takes the Terminal page back
 * after its Home pages and lands on it. Pure: the wall asks whether, and where.
 */
public final class TerminalPageReturn {

    private TerminalPageReturn() {}

    /** {@link #addAfter}'s answer when the page goes first: there is no Home page to follow. */
    public static final int FIRST = -2;

    /**
     * Whether the Terminal page must be added back: the pages are known ({@code pages} not
     * null), none of them is the Terminal page, and a terminal window is open.
     */
    public static boolean needed(@Nullable WallPages pages, boolean terminalWindowOpen) {
        return pages != null && terminalWindowOpen && pages.terminal() == null;
    }

    /** The page the Terminal page goes after: the last Home page on the wall, else {@link #FIRST}. */
    public static int addAfter(@NonNull WallPages pages) {
        int after = FIRST;
        for (WallPages.Page page : pages.pages()) {
            if (page.kind == WallPages.PageKind.HOME) after = page.id;
        }
        return after;
    }

    /** {@code pages} with the Terminal page back where {@link #addAfter} puts it. */
    @NonNull
    public static WallPages.Result restore(@NonNull WallPages pages) {
        int after = addAfter(pages);
        if (after != FIRST) return pages.add(WallPages.PageKind.TERMINAL, after, null);
        WallPages.Result added = pages.add(WallPages.PageKind.TERMINAL, WallPages.END, null);
        if (!added.ok() || added.page == null) return added;
        WallPages.Result moved = added.pages.move(added.page.id, 0);
        return moved.ok() ? new WallPages.Result(moved.pages, WallPages.ChangeType.ADDED,
            added.page, null, -1, 0) : added;
    }
}
