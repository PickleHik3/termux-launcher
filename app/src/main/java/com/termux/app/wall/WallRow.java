package com.termux.app.wall;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The wall's row as a list of pages (ADR 0008): Home page 1 … Home page n, then the Workspace's
 * windows, the display window among them while a display was up (before ADR 0015 A3b). Pure, so the
 * seam, the insertion of a new window, Start in and the saved position read as arithmetic.
 *
 * <p>The wall does not hold a view per page. Home's pages are the widget grid's own pages, paged
 * by a plain swipe inside the grid; the windows are the terminal's, shown one at a time in its
 * pane host and paged by the border drag. The wall slides between the three views
 * ({@link PaneWallPage}), and a step inside one of them is that view's own page change. A
 * {@link Position} names a page by its kind and its index within that kind.
 */
public final class WallRow {

    /** Saved-state value of the half the wall rested in: Home. */
    public static final String HALF_HOME = "home";
    /** Saved-state value of the half the wall rested in: the Workspace. */
    public static final String HALF_WORKSPACE = "workspace";

    /** Start in: a cold start shows Home page 1. The default. */
    public static final String START_IN_HOME = "home";
    /** Start in: a cold start shows the Workspace, with one fresh shell window if it has none. */
    public static final String START_IN_WORKSPACE = "workspace";
    /**
     * Start in: a cold start shows the display window, which starts with the launcher. Only while
     * that is on; see {@link StartInPolicy}.
     */
    public static final String START_IN_DISPLAY = "display";

    private WallRow() {}

    /** One page of the row: its kind, and which one of that kind counting from 0. */
    public static final class Position {
        @NonNull public final PaneWallPage kind;
        public final int index;

        public Position(@NonNull PaneWallPage kind, int index) {
            this.kind = kind;
            this.index = Math.max(0, index);
        }

        @Override public boolean equals(Object other) {
            if (this == other) return true;
            if (!(other instanceof Position)) return false;
            Position that = (Position) other;
            return kind == that.kind && index == that.index;
        }

        @Override public int hashCode() {
            return kind.hashCode() * 31 + index;
        }

        @NonNull @Override public String toString() {
            return kind + "#" + index;
        }
    }

    @NonNull
    public static Position homePage(int index) {
        return new Position(PaneWallPage.WIDGETS, index);
    }

    @NonNull
    public static Position window(int index) {
        return new Position(PaneWallPage.TERMINAL, index);
    }

    /** The half a kind of page belongs to: Home, or the Workspace (its windows, the display's too). */
    @NonNull
    public static String halfOf(@NonNull PaneWallPage kind) {
        return kind == PaneWallPage.WIDGETS ? HALF_HOME : HALF_WORKSPACE;
    }

    /**
     * Whether a step of {@code steps} from page {@code index} of a kind with {@code count} pages
     * stays inside that kind: one Home page to the next, one window to the next. A step that
     * does not leaves it for the neighbouring stretch of the row, if there is one.
     */
    public static boolean hasInnerStep(int index, int count, int steps) {
        if (steps == 0 || count <= 1) return false;
        int target = index + steps;
        return target >= 0 && target < count;
    }

    /**
     * The seam from Home: a plain swipe forward on Home page {@code homePage} leaves Home only
     * from the last Home page, and only when the swipe has somewhere to go ({@code rowContinues}:
     * a cell after Home, the empty Terminal page's included). Otherwise the swipe stays with Home,
     * which pages or stops at its end. Backward never leaves Home: Home page 1 is the row's first
     * page.
     */
    public static boolean swipeLeavesHome(int homePage, int homeCount, int direction,
                                          boolean rowContinues) {
        return direction > 0 && rowContinues && homePage >= Math.max(1, homeCount) - 1;
    }

    // ---- Slots (ADR 0015, A2a): Home runs anywhere on the row -------------------------------

    /**
     * {@link #swipeLeavesHome} for any order: a swipe in {@code direction} leaves the Home run at
     * the run's edge on that side, when a slot lies on that side ({@code slotOnSide}). Inside the
     * run the swipe stays with Home, which pages. For the default order (no slot before the first
     * Home page) this is {@code swipeLeavesHome}.
     */
    public static boolean swipeLeavesRun(int pageInRun, int runLength, int direction,
                                         boolean slotOnSide) {
        if (!slotOnSide || direction == 0) return false;
        int last = Math.max(1, runLength) - 1;
        return direction > 0 ? pageInRun >= last : pageInRun <= 0;
    }

    /**
     * The Home page the wall shows when it arrives at Home from the right, by the border drag
     * off the first window or when the last window closes: the last one, which is the page next
     * to the seam.
     */
    public static int lastHomePage(int homeCount) {
        return Math.max(0, homeCount - 1);
    }

    /**
     * Where a new window goes in the current session's list: right after the current window, so
     * related windows sit together. The first window of an empty session is window 1.
     */
    public static int insertionIndex(int currentWindow, int windowCount) {
        if (windowCount <= 0) return 0;
        return Math.max(0, Math.min(windowCount, currentWindow + 1));
    }

    /**
     * The page kind a cold start shows, by the Start in preference; Home for anything unknown.
     * The display window is one of the Workspace's windows, so Display lands there too.
     */
    @NonNull
    public static PaneWallPage coldStart(@Nullable String startIn) {
        return START_IN_WORKSPACE.equals(startIn) || START_IN_DISPLAY.equals(startIn)
            ? PaneWallPage.TERMINAL : PaneWallPage.WIDGETS;
    }

    /**
     * The kind of page the wall opens on, known before anything is laid out: the half a
     * recreation saved ({@code savedHalf}, {@link #HALF_HOME} or {@link #HALF_WORKSPACE}), else
     * the page Start in names. Only the half matters to what the chrome wears (the Dress),
     * so a Workspace half is the window page whether it lands on a terminal window, the display
     * window, or waits for the windows to come back. A half this version does not know opens Home, as
     * {@link #restore} does.
     */
    @NonNull
    public static PaneWallPage openingKind(@Nullable String savedHalf, @Nullable String startIn) {
        if (savedHalf == null) return coldStart(startIn);
        return HALF_WORKSPACE.equals(savedHalf) ? PaneWallPage.TERMINAL : PaneWallPage.WIDGETS;
    }

    /**
     * The saved index of {@code position} within its half: a Home page's own index, or a window's
     * place in the row, the display window's included.
     */
    public static int savedIndex(@NonNull Position position) {
        return position.index;
    }

    /**
     * The page a saved half and index come back to, on the row as it is now. A Home page past
     * the last comes back as the last, and so does a window past the last window — the display
     * window, which is not saved with the windows, may not be back yet. A half this version does
     * not know, or a Workspace with nothing in it, comes back to Home page 1.
     */
    @NonNull
    public static Position restore(@Nullable String half, int index, int homeCount,
                                   int windowCount) {
        if (HALF_HOME.equals(half)) {
            return homePage(Math.min(Math.max(0, index), lastHomePage(homeCount)));
        }
        if (HALF_WORKSPACE.equals(half) && windowCount > 0) {
            return window(Math.min(Math.max(0, index), windowCount - 1));
        }
        return homePage(0);
    }

    /**
     * Whether the Workspace is on the row (spec §6, Lifetime): while the current session's row has
     * a window, the display window counting as one. Home floats are shells but never windows of
     * the row, so they do not count. Before the terminal service is bound nothing is known and the
     * Workspace is assumed.
     */
    public static boolean hasWorkspace(boolean serviceBound, int currentSessionWindows) {
        return !serviceBound || currentSessionWindows > 0;
    }

    /**
     * Whether an activity that finds the terminal service with no shell starts one. Only a cold
     * start does — the process has not started its first shell yet, so the bootstrap, Welcome and
     * the first-boot tour get theirs — or a launch that asks for a shell by name (New session,
     * failsafe). After that an empty service is the user's empty Workspace, and Home stays.
     */
    public static boolean startsShellOnConnect(boolean sessionsEmpty, boolean firstShellStarted,
                                               boolean launchAsksForSession) {
        return sessionsEmpty && (!firstShellStarted || launchAsksForSession);
    }

    /**
     * What closing the Workspace's last shell — the last window, the last Home float, or an
     * {@code exit} in either — leaves on screen: Home's last page ({@code true}), or nothing, the
     * activity finishing as plain Termux does ({@code false}). Home stays whenever there is a Home
     * to show: the wall has Home pages ({@code hasHomePages}), or the launcher is the phone's home
     * app ({@code isHomeApp}), whose home press would only bring it straight back. Only a launcher
     * with no Home at all ends with its last shell.
     *
     * <p>The terminal service stops itself when it holds no shell and the activity finishes when
     * its service goes, so this is also whether the service keeps running with no shell.
     */
    public static boolean lastShellClosedShowsHome(boolean hasHomePages, boolean isHomeApp) {
        return hasHomePages || isHomeApp;
    }
}
