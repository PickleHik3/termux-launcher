package com.termux.app.wall;

import androidx.annotation.NonNull;

/**
 * The page overview's rules (Launcher3's overview for this wall; ADR 0008 "one wall, two
 * halves"): one strip of cards, Home's pages first and then the current session's windows, the
 * display window among them where {@link DisplaySlot} stands it. Pure, so the halves, where a
 * dragged card may land, where an added card goes, which cards can be removed and how, and when
 * a display can be added read as arithmetic.
 *
 * <p>A strip position counts every card from 0: Home page {@code i} is position {@code i}, and
 * the window at row place {@code r} is position {@code homeCount + r}. The seam stands between
 * position {@code homeCount - 1} and {@code homeCount}.
 */
public final class PageOverviewPolicy {

    private PageOverviewPolicy() {}

    /** No such position: a refused drop, or an empty strip. */
    public static final int NONE = -1;

    /** What a card shows. */
    public enum Kind {
        /** A Home page. */
        HOME_PAGE,
        /** A terminal window of the current session. */
        TERMINAL,
        /** The display window. */
        DISPLAY
    }

    /** How a card's removal goes. */
    public enum Removal {
        /** It cannot be removed: the last Home page. */
        REFUSED,
        /** It is empty and goes at once. */
        AT_ONCE,
        /** It holds something; it goes with an undo snackbar, and is only really removed after. */
        WITH_UNDO
    }

    /** How many cards the strip holds: every Home page and every window of the row. */
    public static int cardCount(int homeCount, int rowCount) {
        return Math.max(0, homeCount) + Math.max(0, rowCount);
    }

    /** Whether position {@code position} is a Home page's card. */
    public static boolean isHome(int position, int homeCount) {
        return position >= 0 && position < homeCount;
    }

    /** The kind of card at {@code position}; the display slot is a row place, NONE when absent. */
    @NonNull
    public static Kind kindAt(int position, int homeCount, int displaySlot) {
        if (isHome(position, homeCount)) return Kind.HOME_PAGE;
        return DisplaySlot.isDisplay(position - homeCount, displaySlot) ? Kind.DISPLAY : Kind.TERMINAL;
    }

    /** The row place of the window card at {@code position}. */
    public static int rowPlace(int position, int homeCount) {
        return position - Math.max(0, homeCount);
    }

    /** The strip position of the window at row place {@code row}. */
    public static int positionOfRow(int row, int homeCount) {
        return Math.max(0, homeCount) + row;
    }

    /** The first and last positions of the half {@code position} stands in, as {@code {first, last}}. */
    @NonNull
    public static int[] halfBounds(int position, int homeCount, int rowCount) {
        if (isHome(position, homeCount)) return new int[] {0, homeCount - 1};
        return new int[] {Math.max(0, homeCount), cardCount(homeCount, rowCount) - 1};
    }

    /** Whether positions {@code a} and {@code b} stand in the same half of the strip. */
    public static boolean sameHalf(int a, int b, int homeCount) {
        return isHome(a, homeCount) == isHome(b, homeCount);
    }

    /**
     * Where a card dragged from {@code from} and dropped over position {@code over} goes: that
     * position when it is in the card's own half, else {@link #NONE} — a drop across the seam is
     * refused and the card springs back. A drop on its own place is a drop that changes nothing.
     */
    public static int dropTarget(int from, int over, int homeCount, int rowCount) {
        int count = cardCount(homeCount, rowCount);
        if (from < 0 || from >= count || over < 0 || over >= count) return NONE;
        return sameHalf(from, over, homeCount) ? over : NONE;
    }

    /**
     * Where the card at {@code position} stands while the card from {@code from} is held at
     * {@code held} ({@link #dropTarget}; {@code from} itself while it is held across the seam, so
     * nothing makes room): the cards between shift one place toward the gap it left.
     */
    public static int shiftedPlace(int position, int from, int held) {
        if (position == from) return held;
        if (from < held && position > from && position <= held) return position - 1;
        if (from > held && position >= held && position < from) return position + 1;
        return position;
    }

    /**
     * The Home page "+ Add page" inserts, counted among the pages as they stand: right after the
     * focused Home page, or after the last Home page when a window is focused.
     */
    public static int addPageIndex(int focused, int homeCount) {
        if (isHome(focused, homeCount)) return focused + 1;
        return Math.max(0, homeCount);
    }

    /**
     * The row place "+ Add terminal" and "+ Add display" insert at: right after the focused
     * window, or first among the windows when a Home page is focused.
     */
    public static int addWindowRow(int focused, int homeCount, int rowCount) {
        if (isHome(focused, homeCount) || focused < 0) return 0;
        return Math.max(0, Math.min(rowCount, rowPlace(focused, homeCount) + 1));
    }

    /** Whether "+ Add display" is offered: only while no display is open, as only one can exist. */
    public static boolean canAddDisplay(boolean displayOpen) {
        return !displayOpen;
    }

    /** Whether a card of {@code kind} shows the delete button: every card but the last Home page. */
    public static boolean canDelete(@NonNull Kind kind, int homeCount) {
        return kind != Kind.HOME_PAGE || homeCount > 1;
    }

    /**
     * How a card's removal goes. A Home page holding widgets, icons, folders or floats
     * ({@code contents}) asks for undo; a terminal running a process ({@code busy}) does; the
     * display window, an open display, always does. Empty things go at once.
     */
    @NonNull
    public static Removal removal(@NonNull Kind kind, int homeCount, int contents, boolean busy) {
        if (!canDelete(kind, homeCount)) return Removal.REFUSED;
        switch (kind) {
            case HOME_PAGE: return contents > 0 ? Removal.WITH_UNDO : Removal.AT_ONCE;
            case TERMINAL: return busy ? Removal.WITH_UNDO : Removal.AT_ONCE;
            default: return Removal.WITH_UNDO;
        }
    }

    /** Whether a card of {@code kind} shows the home button: Home page cards only. */
    public static boolean showsHomeButton(@NonNull Kind kind) {
        return kind == Kind.HOME_PAGE;
    }

    /**
     * The visible strip position of a card while one card is held back for undo: the cards past
     * the held one move up one place. {@code pending} is the held card's real position, NONE for
     * none; the held card itself has no visible place.
     */
    public static int visibleOf(int real, int pending) {
        if (pending < 0 || real < pending) return real;
        return real == pending ? NONE : real - 1;
    }

    /** The real strip position of the card at visible position {@code visible}. */
    public static int realOf(int visible, int pending) {
        if (pending < 0 || visible < pending) return visible;
        return visible + 1;
    }

    /**
     * The card focused after the card at {@code removed} left a strip now holding
     * {@code remaining} cards: the one that moved into its place, or the new last.
     */
    public static int focusAfterRemoval(int removed, int remaining) {
        if (remaining <= 0) return NONE;
        return Math.max(0, Math.min(removed, remaining - 1));
    }

    /** The wall position a card's tap or Back lands on. */
    @NonNull
    public static WallRow.Position wallPosition(int position, int homeCount) {
        if (isHome(position, homeCount)) return WallRow.homePage(position);
        return WallRow.window(rowPlace(position, homeCount));
    }

    /** The strip position the overview opens focused on: the page the wall rests on. */
    public static int openingFocus(boolean onHome, int homePage, int rowCurrent, int homeCount,
                                   int rowCount) {
        int count = cardCount(homeCount, rowCount);
        if (count == 0) return NONE;
        int position = onHome || rowCount <= 0 ? Math.max(0, Math.min(homePage, homeCount - 1))
            : positionOfRow(Math.max(0, Math.min(rowCurrent, rowCount - 1)), homeCount);
        return Math.max(0, Math.min(count - 1, position));
    }

    /**
     * Whether two fingers that started {@code startSpan} apart and are now {@code span} apart
     * have pinched in: closer by at least {@code commitPx} and by at least a quarter of the start.
     */
    public static boolean isPinchIn(float startSpan, float span, float commitPx) {
        if (startSpan <= 0f) return false;
        float closer = startSpan - span;
        return closer >= commitPx && span <= startSpan * 0.75f;
    }
}
