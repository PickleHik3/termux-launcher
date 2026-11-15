package com.termux.app.wall;

import androidx.annotation.NonNull;

/**
 * The page overview's rules (Launcher3's overview for this wall): one strip of cards, one per
 * page in wall order (ADR 0015). The Terminal page is one card however many windows it holds; its
 * windows are places in that page's row, not pages. Pure, so where a dragged card may land, where
 * an added card goes, which cards can be removed and how, and when a display can be added read as
 * arithmetic over the strip's kinds.
 */
public final class PageOverviewPolicy {

    private PageOverviewPolicy() {}

    /** No such position: a refused drop, or an empty strip. */
    public static final int NONE = -1;

    /**
     * Whether a card of {@code kind} is drawn live (the activity's own content, chrome included)
     * while it is focused. Not the display window's, whose picture is its surface's, and not the
     * Assistant page's, whose card is the still card with its icon and name: a live draw showed
     * whatever chrome stood on screen and the chat's heading at the root's scale. Not the Terminal
     * page's while it has no window ({@code windows}), as the wall has nothing there to show.
     */
    public static boolean drawsLive(@NonNull Kind kind, int windows) {
        if (kind == Kind.TERMINAL) return windows > 0;
        return kind != Kind.DISPLAY && kind != Kind.ASSISTANT;
    }

    /** What a card shows. */
    public enum Kind {
        /** A Home page. */
        HOME_PAGE,
        /** The Terminal page, however many windows it holds. */
        TERMINAL,
        /** The display window. */
        DISPLAY,
        /** The Assistant page (ADR 0022). */
        ASSISTANT
    }

    /** How a card's removal goes. */
    public enum Removal {
        /** It cannot be removed here: the last Home page, or the Terminal page. */
        REFUSED,
        /** It is empty and goes at once. */
        AT_ONCE,
        /** It holds something; it goes with an undo snackbar, and is only really removed after. */
        WITH_UNDO
    }

    /** Whether the card at {@code position} is a Home page's. */
    public static boolean isHome(int position, @NonNull java.util.List<Kind> strip) {
        return position >= 0 && position < strip.size() && strip.get(position) == Kind.HOME_PAGE;
    }

    /** The Terminal card's position in {@code strip}; {@link #NONE} when the wall has none. */
    public static int terminalAt(@NonNull java.util.List<Kind> strip) {
        return strip.indexOf(Kind.TERMINAL);
    }

    /**
     * Where a card dragged from {@code from} and dropped over {@code over} goes (its final
     * position, which is also its page index on the wall), or {@link #NONE}. A Home card and the
     * Terminal card land wherever they are dropped; Display and Assistant cards stay where they
     * stand.
     */
    public static int dropTarget(int from, int over, @NonNull java.util.List<Kind> strip) {
        int count = strip.size();
        if (from < 0 || from >= count || over < 0 || over >= count) return NONE;
        Kind kind = strip.get(from);
        return kind == Kind.HOME_PAGE || kind == Kind.TERMINAL ? over : NONE;
    }

    /**
     * The position "+ Add page" inserts a Home page at: right after the focused Home card, or
     * after the last Home page when another card is focused.
     */
    public static int addPageIndex(int focused, @NonNull java.util.List<Kind> strip) {
        if (isHome(focused, strip)) return focused + 1;
        for (int i = strip.size() - 1; i >= 0; i--) if (strip.get(i) == Kind.HOME_PAGE) return i + 1;
        return 0;
    }

    /**
     * The window the Terminal page shows on arriving there: the row's current window
     * ({@code rowCurrent}) among {@code rowCount}, 0 while the row has none.
     */
    public static int frontWindow(int rowCurrent, int rowCount) {
        return Math.max(0, Math.min(rowCurrent, rowCount - 1));
    }

    /**
     * The wall position a card's tap or Back lands on, given the strip's order: a Home card its
     * page, the Terminal card the window in front ({@code frontWindow}).
     */
    @NonNull
    public static WallRow.Position wallPosition(int position, @NonNull java.util.List<Kind> strip,
                                                int frontWindow) {
        if (isHome(position, strip)) {
            int n = 0;
            for (int i = 0; i < position; i++) if (strip.get(i) == Kind.HOME_PAGE) n++;
            return WallRow.homePage(n);
        }
        if (position >= 0 && position < strip.size() && strip.get(position) == Kind.DISPLAY) {
            return new WallRow.Position(PaneWallPage.DISPLAY, 0);
        }
        if (position >= 0 && position < strip.size() && strip.get(position) == Kind.ASSISTANT) {
            return new WallRow.Position(PaneWallPage.ASSISTANT, 0);
        }
        return WallRow.window(Math.max(0, frontWindow));
    }

    /**
     * The strip position the overview opens focused on: the Home card the wall rests on
     * ({@code homePage}, clamped among the Home cards) while it is on Home, else the Terminal
     * card; the first card when there is neither.
     */
    public static int openingFocus(@NonNull java.util.List<Kind> strip, boolean onHome,
                                   int homePage) {
        if (strip.isEmpty()) return NONE;
        if (!onHome) {
            int terminal = terminalAt(strip);
            if (terminal >= 0) return terminal;
        }
        int last = NONE;
        int n = 0;
        for (int i = 0; i < strip.size(); i++) {
            if (strip.get(i) != Kind.HOME_PAGE) continue;
            if (n == homePage) return i;
            last = i;
            n++;
        }
        return last >= 0 ? last : 0;
    }

    /**
     * Where the card at {@code position} stands while the card from {@code from} is held at
     * {@code held} ({@link #dropTarget}; {@code from} itself while it is held where it cannot
     * land, so nothing makes room): the cards between shift one place toward the gap it left.
     */
    public static int shiftedPlace(int position, int from, int held) {
        if (position == from) return held;
        if (from < held && position > from && position <= held) return position - 1;
        if (from > held && position >= held && position < from) return position + 1;
        return position;
    }

    /** Whether "+ Add display" is offered: only while no display is open, as only one can exist. */
    public static boolean canAddDisplay(boolean displayOpen) {
        return canAddDisplay(displayOpen, false);
    }

    /**
     * As {@link #canAddDisplay(boolean)}, but a display open on a temporary page (a drawer
     * launch's) does not count: adding then makes that page a lasting one instead of a second.
     */
    public static boolean canAddDisplay(boolean displayOpen, boolean onTemporaryPage) {
        return !displayOpen || onTemporaryPage;
    }

    /** Marks the Terminal card's place in {@link #wallOrder}. */
    public static final int TERMINAL_PAGE = Integer.MIN_VALUE;

    /**
     * The strip in wall order (ADR 0015: Display pages are wall pages): Home page {@code i} is
     * {@code i}, Display page {@code j} (of {@code displays}, in wall order) is {@code -(j + 1)},
     * the Terminal page is {@link #TERMINAL_PAGE}. {@code homesBefore[j]} is how many Home pages
     * stand before Display page {@code j} on the wall; {@code afterTerminal[j]} whether it stands
     * after the Terminal page. {@code beforeTerminal} is how many Home pages stand before the
     * Terminal page; {@code hasTerminal} false puts no Terminal card in the strip.
     */
    @NonNull
    public static java.util.List<Integer> wallOrder(int pages, boolean hasTerminal,
            int beforeTerminal, @NonNull int[] homesBefore, @NonNull boolean[] afterTerminal) {
        java.util.List<Integer> out = new java.util.ArrayList<>();
        int before = hasTerminal ? Math.max(0, Math.min(pages, beforeTerminal)) : -1;
        for (int gap = 0; gap <= pages; gap++) {
            for (int j = 0; j < homesBefore.length; j++) {
                if (homesBefore[j] == gap && !(gap == before && afterTerminal[j])) out.add(-(j + 1));
            }
            if (gap == before) {
                out.add(TERMINAL_PAGE);
                for (int j = 0; j < homesBefore.length; j++) {
                    if (homesBefore[j] == gap && afterTerminal[j]) out.add(-(j + 1));
                }
            }
            if (gap < pages) out.add(gap);
        }
        return out;
    }

    /**
     * The strip's kinds for {@link #wallOrder}'s {@code order}; {@code assistant[j]} is whether
     * Display page {@code j} is the Assistant page.
     */
    @NonNull
    public static java.util.List<Kind> kinds(@NonNull java.util.List<Integer> order,
                                             @NonNull boolean[] assistant) {
        java.util.List<Kind> out = new java.util.ArrayList<>(order.size());
        for (int slot : order) {
            if (slot == TERMINAL_PAGE) out.add(Kind.TERMINAL);
            else if (slot < 0) out.add(assistant[-slot - 1] ? Kind.ASSISTANT : Kind.DISPLAY);
            else out.add(Kind.HOME_PAGE);
        }
        return out;
    }

    /**
     * Whether a card of {@code kind} shows the remove button: every card but the last Home page
     * and the Terminal page, which leaves only through its menu's Remove Terminal.
     */
    public static boolean canDelete(@NonNull Kind kind, int homeCount) {
        if (kind == Kind.TERMINAL) return false;
        return kind != Kind.HOME_PAGE || homeCount > 1;
    }

    /**
     * How a card's removal goes. A Home page holding widgets, icons, folders or floats
     * ({@code contents}) asks for undo; the display window, an open display, always does. Empty
     * things go at once.
     */
    @NonNull
    public static Removal removal(@NonNull Kind kind, int homeCount, int contents) {
        if (!canDelete(kind, homeCount)) return Removal.REFUSED;
        if (kind == Kind.HOME_PAGE) return contents > 0 ? Removal.WITH_UNDO : Removal.AT_ONCE;
        // An Assistant page's chats are kept either way (ADR 0022); Undo brings the page back.
        return Removal.WITH_UNDO;
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
