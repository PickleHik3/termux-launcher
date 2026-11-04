package com.termux.app.wall;

/**
 * Which pills Edit pages shows along the bottom (ADR 0015 section 2): Add page while Home pages
 * are on the wall, "Bring back N Home pages" while they are set aside (Use as Terminal only),
 * Add terminal only while the wall has no Terminal page. Pure.
 */
public final class PageOverviewAddPolicy {

    private PageOverviewAddPolicy() {}

    /** Whether "Add page" shows: only while the wall shows Home pages at all. */
    public static boolean showsAddPage(boolean homePagesShown) {
        return homePagesShown;
    }

    /**
     * The N of "Bring back N Home pages": the Home pages set aside, while the wall shows none;
     * 0 hides the pill.
     */
    public static int bringBackCount(boolean homePagesShown, int detachedHomePages) {
        return homePagesShown ? 0 : Math.max(0, detachedHomePages);
    }

    /** Whether "Add terminal" shows: only while the wall has no Terminal page, as only one can. */
    public static boolean showsAddTerminal(boolean hasTerminalPage) {
        return !hasTerminalPage;
    }
}
