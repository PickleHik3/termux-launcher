package com.termux.app.wall;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The wall's row as a list of pages (ADR 0008): Home page 1 … Home page n, then the Workspace's
 * windows, then the display window while a display is up. Pure, so the seam, the insertion of a new
 * window, Start in and the saved position read as arithmetic.
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

    @NonNull
    public static Position display() {
        return new Position(PaneWallPage.DISPLAY, 0);
    }

    /** The half a kind of page belongs to: Home, or the Workspace (windows and the display). */
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
     * from the last Home page, and only when the row goes on past Home ({@code rowContinues}:
     * the Workspace has a window, or the Display page is up). Otherwise the swipe stays with
     * Home, which pages or stops at its end. Backward never leaves Home: Home page 1 is the
     * row's first page.
     */
    public static boolean swipeLeavesHome(int homePage, int homeCount, int direction,
                                          boolean rowContinues) {
        return direction > 0 && rowContinues && homePage >= Math.max(1, homeCount) - 1;
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

    /** The page kind a cold start shows, by the Start in preference; Home for anything unknown. */
    @NonNull
    public static PaneWallPage coldStart(@Nullable String startIn) {
        return START_IN_WORKSPACE.equals(startIn) ? PaneWallPage.TERMINAL : PaneWallPage.WIDGETS;
    }

    /**
     * The kind of page the wall opens on, known before anything is laid out: the half a
     * recreation saved ({@code savedHalf}, {@link #HALF_HOME} or {@link #HALF_WORKSPACE}), else
     * the page Start in names. Only the half matters to what the chrome wears (HomeChromePolicy),
     * so a Workspace half is the window page whether it lands on a window, the Display page, or
     * waits for the windows to come back. A half this version does not know opens Home, as
     * {@link #restore} does.
     */
    @NonNull
    public static PaneWallPage openingKind(@Nullable String savedHalf, @Nullable String startIn) {
        if (savedHalf == null) return coldStart(startIn);
        return HALF_WORKSPACE.equals(savedHalf) ? PaneWallPage.TERMINAL : PaneWallPage.WIDGETS;
    }

    /**
     * The saved index of {@code position} within its half: a Home page's own index, a window's,
     * and the Display page after the windows.
     */
    public static int savedIndex(@NonNull Position position, int windowCount) {
        return position.kind == PaneWallPage.DISPLAY ? Math.max(0, windowCount) : position.index;
    }

    /**
     * The page a saved half and index come back to, on the row as it is now. A Home page past
     * the last comes back as the last; a Workspace index past the windows is the Display page if
     * it is up, else the last window. A half this version does not know, or a Workspace with
     * nothing in it, comes back to Home page 1.
     */
    @NonNull
    public static Position restore(@Nullable String half, int index, int homeCount,
                                   int windowCount, boolean displayOn) {
        if (HALF_HOME.equals(half)) {
            return homePage(Math.min(Math.max(0, index), lastHomePage(homeCount)));
        }
        if (HALF_WORKSPACE.equals(half)) {
            if (index >= 0 && index < windowCount) return window(index);
            if (displayOn) return display();
            if (windowCount > 0) return window(windowCount - 1);
        }
        return homePage(0);
    }

    /**
     * Whether the Workspace is on the row (spec §6, Lifetime): while the current session has a
     * window. Home floats are shells but never windows of the row, so they do not count. Before
     * the terminal service is bound nothing is known and the Workspace is assumed.
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
}
