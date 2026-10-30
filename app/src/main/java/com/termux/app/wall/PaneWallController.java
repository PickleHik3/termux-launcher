package com.termux.app.wall;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.terminal.PaneSurfaceStyle;

/**
 * Owns the wall: its row of pages (Home pages, the Workspace's windows, and the Display pages,
 * each a page of its own; ADR 0008, ADR 0015 A3b), which page is in front, and every way in and out
 * of them. The activity hands it the layout and the answers it cannot work out for itself — whether
 * the current session has a window, how many Home pages and windows there are and which is in
 * front — and nothing else needs to know the wall exists.
 */
public final class PaneWallController implements PaneWallLayout.Listener {

    /** What the wall asks the activity. */
    public interface Host {
        /** Honour the system's reduce-motion setting for the page slide. */
        boolean reducedMotion();
        /**
         * Whether the current session's row has a terminal window, so the Workspace is on the
         * row (a Display page is a page of its own, not one of the row's windows). Home is on it unless {@link #useAs} is
         * Terminal only.
         */
        default boolean hasWindow() { return true; }
        /**
         * Use as, read fresh on every ask: under Terminal only the row is the Workspace alone,
         * and the home press, the last window closing and a cold start all land there.
         */
        @NonNull default UseAs useAs() { return UseAs.HOME; }
        /**
         * Whether an empty Workspace can be started by Home's swipe past its last page: a shell
         * can be made now. Asked only while the Workspace has no window.
         */
        default boolean canStartWorkspace() { return false; }
        /**
         * Home's swipe has carried the wall into an empty Workspace: start its first shell, as the
         * Terminal entry does into an empty row. The wall already rests on (or slides to) the
         * Workspace. False when no shell could be made; the wall then goes back to Home.
         */
        default boolean startWorkspaceShell() { return false; }
        /**
         * How many pages of its own kind {@code page} holds: the Home pages, or the terminal
         * windows of the current session's row.
         */
        default int innerCount(@NonNull PaneWallPage page) { return 1; }
        /** Which of them is in front, counting from 0. */
        default int innerIndex(@NonNull PaneWallPage page) { return 0; }
        /**
         * The wall's pages (ADR 0015), read fresh on every ask, or null for the kind-keyed row
         * alone ({@link UseAs#rowPages}). The wall lays out in their order.
         */
        @Nullable default WallPages wallPages() { return null; }
        /**
         * Bind the one Home frame to the Home run of global Home-page indexes {@code first} to
         * {@code last}, both included: it pages inside that run, and hands the swipe to the wall
         * at the run's ends. {@code last} below 0 for every Home page, the default order's run.
         */
        default void bindHomeRun(int first, int last) { }
        /**
         * Bind the one display view to Display page {@code pageId} (ADR 0015, A3b): the Display
         * page the wall is on or heading for, which shows the display live while the others show
         * their picture or card. -1 while the wall has no Display page.
         */
        default void bindDisplayPage(int pageId) { }
        /**
         * The wall came to rest on Display page {@code pageId}, the bound one: the display starts
         * here when it is down ({@link DisplayPagePolicy#startsOnLanding}).
         */
        default void onDisplayPageLanded(int pageId) { }
        /** Turn {@code page} to its {@code index}-th page at once (Home page n). */
        default void showInnerPage(@NonNull PaneWallPage page, int index) { }
        /**
         * The Home page the Home press lands on, counting from 0: Edit pages' default page, page
         * 1 until one is chosen.
         */
        default int homePressPage() { return 0; }
        /**
         * How far the page in front of {@code page}'s view stands off its rest while that view's
         * own swipe turns it, as a fraction of a page width, positive to the right: Home's grid
         * mid-swipe and while the next Home page slides in. The windows' arrival is the wall's own
         * nudge and is not asked. Read on every frame the wallpaper pans; it must not allocate.
         */
        default float innerShiftFraction(@NonNull PaneWallPage page) { return 0f; }
        /**
         * A border drag committed to the window {@code steps} away and the wall still stands where
         * the finger let go: picture what is leaving. {@link #onInnerStep} follows.
         */
        default void onInnerStepBegin(@NonNull PaneWallPage page, int steps) { }
        /**
         * Show the window {@code steps} away, the wall at rest again: it comes in from
         * {@code fromPx}, where the drag had already brought it.
         */
        default void onInnerStep(@NonNull PaneWallPage page, int steps, float fromPx) { }
        /** A page came to rest on screen. */
        default void onWallPageSettled(@NonNull PaneWallPage page) { }
        /** The wall committed to a different page, before the slide finishes. */
        default void onWallPageChanged(@NonNull PaneWallPage page) { }
        /** The wall moved: signed distance from the current page's rest, for the place switch. */
        default void onWallOffsetChanged(float offsetPx) { }
        /** The Terminal page just went fully off screen, or just came back; see
         *  {@link PaneWallLayout.Listener#onTerminalOffScreenChanged}. */
        default void onTerminalOffScreenChanged(boolean offScreen) { }
        /**
         * How much of the terminal's rims the slide leaves showing changed
         * ({@link PaneWallPolicy#terminalRimAlpha}): the wall's outline fade, or all of them while
         * the transition fades the terminal's page. The wall's own pages take the outline fade
         * themselves; the terminal's panes draw their rims through their controller, which the
         * host owns.
         */
        default void onWallOutlineAlphaChanged(float alpha) { }
        /**
         * Whether the pages a border drag pulls tip like planks ({@link PlankTilt}): Fancier
         * Glass on, with the phone animating. Asked as the drag claims the finger.
         */
        default boolean isPlankTiltEnabled() { return false; }
        /**
         * A vertical swipe off the page's bottom border asked for the keyboard: {@code open} up,
         * closed down. On every place and in every mode, and it turns a keyboard switched off
         * back on, as the keyboard key does: it is the one way back that is always there. Only
         * for a swipe the keyboard could not follow ({@link #onKeyboardRevealBegin}).
         */
        default void onBorderKeyboardSwipe(boolean open) { }
        /**
         * Whether the keyboard swipe is armed on the page in view. Always on the Workspace; on a
         * Home page it is the palette gesture, which Settings can turn off.
         */
        default boolean isBorderKeyboardSwipeEnabled() { return true; }
        /**
         * Whether a sideways swipe that starts at this screen point, on a terminal window's text,
         * pages the row ({@link PaneWallLayout.Listener#isBodySwipePaging}): Settings lets it,
         * and the pane under the finger gives the swipe away. Asked as a finger lands.
         */
        default boolean bodySwipePagesWall(float rawX, float rawY) { return false; }
        /**
         * Whether a clearly sideways swipe that starts at this screen point, in a terminal
         * window's last row on the seam band, pages the row ({@link
         * PaneWallLayout.Listener#isSeamSwipePaging}): the pane under the finger gives the swipe
         * away. The body swipe's setting is not asked; the band is the bottom border's.
         */
        default boolean seamSwipePagesWall(float rawX, float rawY) {
            return bodySwipePagesWall(rawX, rawY);
        }
        /**
         * The keyboard swipe was claimed, going up ({@code opening}) or down, and the keyboard is
         * to follow the finger: the keyboard's height in px the finger drives it over, or 0 where
         * it cannot follow, and the swipe then asks through {@link #onBorderKeyboardSwipe} on
         * release. See {@link PaneWallLayout.Listener#onKeyboardRevealBegin}.
         */
        default int onKeyboardRevealBegin(boolean opening) { return 0; }
        /** How much of the keyboard a swipe it follows shows this frame, 0 down to 1 up. */
        default void onKeyboardRevealProgress(float reveal) { }
        /** The keyboard a swipe carried came to rest, up ({@code open}) or down. */
        default void onKeyboardRevealEnd(boolean open) { }
        /**
         * Whether a vertical swipe off the page's top border drives the status bar's fold: where
         * the bar stands along the top, can unfold, and is not put away. Asked on every frame the
         * wall draws its grabbers; see {@link PaneWallLayout.Listener#isBorderStatusSwipeEnabled}.
         */
        default boolean isBorderStatusSwipeEnabled() { return false; }
        /** Whether the top border's status swipe needs two fingers. */
        default boolean isBorderStatusSwipeTwoFingers() { return false; }
        /**
         * A swipe off the top border asked for the bar unfolded ({@code expand}) or folded, for a
         * swipe the fold did not follow ({@link #onStatusFoldBegin} answered 0, or reduced motion).
         */
        default void onBorderStatusSwipe(boolean expand) { }
        /**
         * The status bar swipe was claimed, going down ({@code expanding}) or up: the fold's whole
         * way in px for the finger to drive, or 0 where it cannot follow. See
         * {@link PaneWallLayout.Listener#onStatusFoldBegin}.
         */
        default int onStatusFoldBegin(boolean expanding) { return 0; }
        /** The finger has the bar {@code towardOpenPx} from the form it had, positive opening. */
        default void onStatusFoldProgress(float towardOpenPx) { }
        /** The fold's finger let go or lost it: land the bar unfolded ({@code expanded}) or folded. */
        default void onStatusFoldEnd(boolean expanded, float towardOpenVelocityPxPerSec) { }
        /**
         * A page sunk under a held border ({@link PageSink}) is drawn at {@code scale} about its
         * centre; 1 once it is back up. The terminal's frame line is drawn outside its page and
         * has to follow by hand.
         */
        default void onPageSinkChanged(@NonNull PaneWallPage page, float scale) { }
        /**
         * A page tipped, or its dim changed, on a frame the wall did not otherwise report; see
         * {@link PaneWallLayout.Listener#onPagePoseChanged}. The wall's own pages have re-aimed
         * their glass already; the terminal's slabs are the host's.
         */
        default void onPagePoseChanged() { }
    }

    /** Saved-instance-state key for the half the wall rested in ({@link WallRow#HALF_HOME}…). */
    public static final String ARG_HALF = "wall_half";
    /** Saved-instance-state key for the page's index within its half ({@link WallRow#savedIndex}). */
    public static final String ARG_INDEX = "wall_index";

    @NonNull private final PaneWallLayout mWall;
    @NonNull private final Host mHost;
    @Nullable private WidgetPaneFrame mWidgetsPage;
    @Nullable private com.termux.app.x11.X11PaneFrame mDisplayWindow;
    @Nullable private PaneSurfaceStyle mStyle;
    /** How much of the pages' outlines the last frame left showing; 1 at rest. */
    private float mOutlineAlpha = 1f;
    /** How much of the terminal's rims the last frame left showing; 1 at rest. */
    private float mTerminalRimAlpha = 1f;
    /**
     * The Workspace was asked for — by Start in or by restored state — before the row had a
     * window: the wall goes there as soon as one arrives, unless something else moved it first.
     */
    private boolean mWorkspacePending;
    /**
     * Home's seam is carrying a swipe into a Workspace with no window: the row holds the
     * Workspace's page (its pane host, empty, under the Workspace's chrome) for the swipe to bring
     * in, and the shell is started only when the wall commits there ({@link WallRow#seamStartsShell}).
     * Cleared when the swipe springs back to Home or the shell's window arrives.
     */
    private boolean mSeamIntoEmpty;
    /** That crossing's shell has been asked for: a page change back and forth starts no second. */
    private boolean mSeamShellStarted;

    // ---- The cells and the binding (ADR 0016) -----------------------------------------------
    // The wall lays out one cell per page; each kind's one view is bound to one page.
    /** The page list the cells were laid out from; null where only the kinds are known. */
    @Nullable private WallPages mKnownPages;
    /** The cells are [Home...][Terminal]: the wallpaper's row reads as it always did. */
    private boolean mDefaultOrder = true;
    /**
     * The Terminal page keeps its cell with no window, as a stop whose landing starts the first
     * shell.
     */
    private boolean mEmptyTerminalStop;
    /** The row had a window at the last {@link #refreshPages}. */
    private boolean mHadWindow;
    /** The run last handed to the host ({@link Host#bindHomeRun}). */
    private int mBoundFirst = 0;
    private int mBoundLast = -1;
    /** The Display page the display view was last bound to; -1 for none yet. */
    private int mDisplayPageId = -1;
    /** The Display page last handed to the host ({@link Host#bindDisplayPage}). */
    private int mBoundDisplayPageId = -1;
    /** The cells hold a Display page: the wallpaper's pan reads the pages' order. */
    private boolean mHasDisplaySlot;

    public PaneWallController(@NonNull PaneWallLayout wall, @NonNull Host host) {
        mWall = wall;
        mHost = host;
        mWall.setListener(this);
        mWall.setReducedMotion(host.reducedMotion());
        refreshPages();
    }

    @NonNull
    public PaneWallLayout wall() {
        return mWall;
    }

    /** The terminal's pane host is the Workspace's page while a terminal window is in front. */
    public void attachTerminalPage(@NonNull View paneHost) {
        mWall.setPageView(PaneWallPage.TERMINAL, paneHost);
    }

    /**
     * The terminal's pane host as the wall's middle page, with where its panes stand along its
     * bottom border, so that border's glow lies under the pane pressed ({@link PaneEdges}).
     */
    public void attachTerminalPage(@NonNull View paneHost, @Nullable PaneEdges edges) {
        mWall.setTerminalPaneEdges(edges);
        attachTerminalPage(paneHost);
    }

    /**
     * Re-read the page list (ADR 0016): the wall lays out one cell per page, and keeps the page
     * it is on. Harmless and cheap when nothing changed. A page that leaves while the wall rests
     * on it hands the wall to the page on its left, which is bound and shown where it stands.
     */
    public void refreshPages() {
        mWall.setReducedMotion(mHost.reducedMotion());
        boolean hasWindow = mHost.hasWindow();
        boolean lostWindow = mHadWindow && !hasWindow;
        mHadWindow = hasWindow;
        if (hasWindow) { mSeamIntoEmpty = false; mSeamShellStarted = false; }
        syncHomeCell();
        int before = mWall.currentPageId();
        mWall.setCells(cells(hasWindow || mSeamIntoEmpty));
        int after = mWall.currentPageId();
        // The bindings follow the list: a bound page that went hands its view to another.
        int home = mWall.boundPageId(PaneWallPage.WIDGETS);
        if (after != before && home == after) bindHome(after);
        else bindRunOf(home);
        // A kind-only cell holds every page of its kind: handed the wall, it turns to its last.
        if (after != before && after < 0 && mWall.currentPage() != PaneWallPage.TERMINAL) {
            PaneWallPage kind = mWall.currentPage();
            mHost.showInnerPage(kind, Math.max(0, mHost.innerCount(kind) - 1));
        }
        int display = mWall.boundPageId(PaneWallPage.DISPLAY);
        bindDisplay(display >= 0 ? display : -1);
        // The last window closed under the wall while the empty Terminal page keeps its cell: the
        // wall hands to the Home page beside it, as it does when the Terminal page leaves.
        if (lostWindow && mEmptyTerminalStop && !mSeamIntoEmpty && !mWorkspacePending
            && mWall.currentPage() == PaneWallPage.TERMINAL) {
            goToLastHomePage(false);
        }
        if (mWorkspacePending && has(PaneWallPage.TERMINAL)) {
            mWorkspacePending = false;
            mWall.goTo(PaneWallPage.TERMINAL, false);
        }
    }

    /**
     * The cells the wall lays out: one per page of {@link Host#wallPages}, in order, by page id;
     * under Terminal only without the Home pages, and without the Terminal page while it has no
     * window and no first shell can be started from it. A kind-only list ({@link UseAs#rowPages})
     * where the pages are not known.
     */
    @NonNull
    private java.util.List<PaneWallLayout.Cell> cells(boolean onRow) {
        boolean homeShown = mHost.useAs() == UseAs.HOME;
        WallPages pages = mHost.wallPages();
        // Interim until #186 (ADR 0016 section 6): the Terminal page with no window is a cell only
        // when a first shell can start from it; #186 makes it always a stop.
        // An empty Terminal page keeps its cell, as a stop whose landing starts the first shell.
        boolean emptyStop = homeShown && pages != null && !onRow && mHost.canStartWorkspace();
        java.util.List<PaneWallLayout.Cell> out = new java.util.ArrayList<>();
        boolean hasHome = false;
        boolean hasTerminal = false;
        boolean hasDisplay = false;
        boolean homeAfterTerminal = false;
        if (pages != null) {
            for (WallPages.Page page : pages.pages()) {
                PaneWallPage kind = kindOf(page.kind);
                if (kind == PaneWallPage.WIDGETS && !homeShown) continue;
                if (kind == PaneWallPage.TERMINAL && !(onRow || emptyStop || !homeShown)) continue;
                if (kind == PaneWallPage.WIDGETS && hasTerminal) homeAfterTerminal = true;
                hasHome |= kind == PaneWallPage.WIDGETS;
                hasTerminal |= kind == PaneWallPage.TERMINAL;
                hasDisplay |= kind == PaneWallPage.DISPLAY;
                out.add(new PaneWallLayout.Cell(page.id, kind, pages.displayName(page, null)));
            }
        }
        if (pages == null || out.isEmpty() || (homeShown && !hasHome)
            || (onRow && !hasTerminal)) {
            mKnownPages = null;
            mDefaultOrder = true;
            mHasDisplaySlot = false;
            mEmptyTerminalStop = false;
            out.clear();
            for (PaneWallPage kind : mHost.useAs().rowPages(onRow)) {
                out.add(new PaneWallLayout.Cell(PaneWallLayout.kindPageId(kind), kind, null));
            }
            return out;
        }
        mKnownPages = pages;
        mEmptyTerminalStop = emptyStop;
        mHasDisplaySlot = hasDisplay;
        mDefaultOrder = !hasDisplay && !homeAfterTerminal;
        return out;
    }

    /** The wall's page kind of a page of the list. */
    @NonNull
    static PaneWallPage kindOf(@NonNull WallPages.PageKind kind) {
        switch (kind) {
            case HOME: return PaneWallPage.WIDGETS;
            case DISPLAY: return PaneWallPage.DISPLAY;
            case TERMINAL:
            default: return PaneWallPage.TERMINAL;
        }
    }

    /** Whether the wall has a cell of {@code kind}. */
    private boolean has(@NonNull PaneWallPage kind) {
        return mWall.boundPageId(kind) != PaneWallLayout.NO_PAGE;
    }

    /** Bind the display view to Display page {@code pageId}, telling the host when it moved. */
    private void bindDisplay(int pageId) {
        if (pageId >= 0) {
            mDisplayPageId = pageId;
            mWall.bind(PaneWallPage.DISPLAY, pageId);
        }
        if (pageId == mBoundDisplayPageId) return;
        mBoundDisplayPageId = pageId;
        mHost.bindDisplayPage(pageId);
    }

    /** The Display page the display view is bound to; -1 while the wall has none. */
    public int boundDisplayPage() {
        return mBoundDisplayPageId;
    }

    /**
     * Go to Display page {@code pageId} (-1: the bound one, else the nearest): the display view
     * is bound to it and the wall slides there, a far one from a stand-off (W3). False when the
     * wall has no such Display page.
     */
    public boolean goToDisplayPage(int pageId, boolean animate) {
        WallPages pages = mKnownPages;
        if (pages == null) {
            if (!has(PaneWallPage.DISPLAY)) return false;
            mWorkspacePending = false;
            return mWall.goTo(PaneWallPage.DISPLAY, animate);
        }
        int id = pageId;
        if (mWall.kindOf(id) != PaneWallPage.DISPLAY) {
            WallPages.Page target = pages.displayTarget(currentPageId(), mBoundDisplayPageId);
            id = target == null ? PaneWallLayout.NO_PAGE : target.id;
        }
        if (mWall.kindOf(id) != PaneWallPage.DISPLAY) return false;
        return goToPage(id, animate);
    }

    /** The id of the page the wall is on; -1 while the wall's pages are not known. */
    public int currentPageId() {
        syncHomeCell();
        int id = mWall.currentPageId();
        return id < 0 ? -1 : id;
    }

    /**
     * Go to page {@code pageId}'s cell. Its kind's view is bound to it first, so the page arrives
     * live: the Home frame turned to that page, the display view on that Display page. A Home
     * page of the run the wall is on is shown by the frame where it stands.
     */
    private boolean goToPage(int pageId, boolean animate) {
        PaneWallPage kind = mWall.kindOf(pageId);
        if (kind == null) return false;
        mWorkspacePending = false;
        syncHomeCell();
        if (kind == PaneWallPage.WIDGETS && mWall.currentPage() == PaneWallPage.WIDGETS
            && !mWall.isMoving() && sameRun(mWall.currentPageId(), pageId)) {
            bindHome(pageId);
            mWall.follow(pageId);
            return true;
        }
        bindCell(pageId);
        return mWall.goToCell(pageId, animate);
    }

    /** Bind the view of page {@code pageId}'s kind to it: the binding a landing makes. */
    private void bindCell(int pageId) {
        PaneWallPage kind = mWall.kindOf(pageId);
        if (kind == null) return;
        if (kind == PaneWallPage.WIDGETS) {
            bindHome(pageId);
        } else if (kind == PaneWallPage.DISPLAY) {
            if (pageId >= 0) bindDisplay(pageId);
            else mWall.bind(kind, pageId);
        } else {
            mWall.bind(kind, pageId);
        }
    }

    /**
     * Bind the Home frame to Home page {@code pageId}: to the run of Home cells that holds it,
     * turned to that page.
     */
    private void bindHome(int pageId) {
        if (mWall.kindOf(pageId) != PaneWallPage.WIDGETS) return;
        bindRunOf(pageId);
        int index = homeIndexOf(pageId);
        if (index >= 0 && mHost.innerIndex(PaneWallPage.WIDGETS) != index) {
            mHost.showInnerPage(PaneWallPage.WIDGETS, index);
        }
        mWall.bind(PaneWallPage.WIDGETS, pageId);
    }

    /** Hand the host the run of Home cells that holds page {@code pageId}. */
    private void bindRunOf(int pageId) {
        if (pageId < 0 || mWall.kindOf(pageId) != PaneWallPage.WIDGETS) {
            bindRun(0, -1);
            return;
        }
        java.util.List<PaneWallLayout.Cell> cells = mWall.cells();
        int at = mWall.indexOfCell(pageId);
        int start = at;
        while (start > 0 && cells.get(start - 1).kind == PaneWallPage.WIDGETS) start--;
        int end = at;
        while (end + 1 < cells.size() && cells.get(end + 1).kind == PaneWallPage.WIDGETS) end++;
        int first = homeIndexOf(cells.get(start).pageId);
        int last = first + end - start;
        // Every Home page in one run: the default order's run.
        if (first == 0 && last == homeCount() - 1) bindRun(0, -1);
        else bindRun(first, last);
    }

    /** Whether two Home pages stand in one run of Home cells. */
    private boolean sameRun(int a, int b) {
        java.util.List<PaneWallLayout.Cell> cells = mWall.cells();
        int from = mWall.indexOfCell(a);
        int to = mWall.indexOfCell(b);
        if (from < 0 || to < 0) return false;
        for (int i = Math.min(from, to); i <= Math.max(from, to); i++) {
            if (cells.get(i).kind != PaneWallPage.WIDGETS) return false;
        }
        return true;
    }

    /** The global Home-page index of Home page {@code pageId}; -1 when it is no Home cell. */
    private int homeIndexOf(int pageId) {
        if (pageId < 0) return -1;
        int index = 0;
        for (PaneWallLayout.Cell cell : mWall.cells()) {
            if (cell.pageId == pageId) return cell.kind == PaneWallPage.WIDGETS ? index : -1;
            if (cell.kind == PaneWallPage.WIDGETS) index++;
        }
        return -1;
    }

    /** The Home page at global Home-page index {@code index}; NO_PAGE when the pages are not known. */
    private int homeIdAt(int index) {
        if (mKnownPages == null) return PaneWallLayout.NO_PAGE;
        int at = 0;
        for (PaneWallLayout.Cell cell : mWall.cells()) {
            if (cell.kind != PaneWallPage.WIDGETS) continue;
            if (at++ == index) return cell.pageId;
        }
        return PaneWallLayout.NO_PAGE;
    }

    private int homeCount() {
        int count = 0;
        for (PaneWallLayout.Cell cell : mWall.cells()) {
            if (cell.kind == PaneWallPage.WIDGETS) count++;
        }
        return count;
    }

    /**
     * The Home frame pages inside its run by its own swipe: the wall stands on the cell of the
     * Home page it shows. Asked before anything reads the current page.
     */
    private void syncHomeCell() {
        if (mKnownPages == null || mWall.currentPage() != PaneWallPage.WIDGETS) return;
        int id = homeIdAt(mHost.innerIndex(PaneWallPage.WIDGETS));
        if (id != PaneWallLayout.NO_PAGE && sameRun(mWall.currentPageId(), id)) mWall.follow(id);
    }

    private void bindRun(int first, int last) {
        if (first == mBoundFirst && last == mBoundLast) return;
        mBoundFirst = first;
        mBoundLast = last;
        mHost.bindHomeRun(first, last);
    }

    /**
     * Go to global Home page {@code index}: its cell, the frame bound to it on the way; where the
     * pages are not known, the Home page turned to it.
     */
    private boolean goToHomePage(int index, boolean animate) {
        int id = homeIdAt(index);
        if (id != PaneWallLayout.NO_PAGE) return goToPage(id, animate);
        mHost.showInnerPage(PaneWallPage.WIDGETS, index);
        return goTo(PaneWallPage.WIDGETS, animate);
    }

    /**
     * Whether the Terminal page stands in the cell beside the current one on side
     * {@code direction}: where Home's swipe into an empty Workspace goes. Always forward where
     * only the kinds are known.
     */
    private boolean terminalBesideRun(int direction) {
        if (mKnownPages == null) return direction > 0;
        int terminal = mWall.indexOfCell(mWall.boundPageId(PaneWallPage.TERMINAL));
        return terminal >= 0 && terminal == mWall.indexOfCell(mWall.currentPageId()) + direction;
    }

    /**
     * The seam out of Home for the widget grid: whether a forward swipe on the last Home page has
     * somewhere to go on the row, and the wall's plain drag to carry it there. With the Workspace
     * empty the swipe still crosses: the Workspace's page joins the row for it, empty, and its
     * first shell starts only if the release commits there.
     */
    @NonNull
    public com.termux.app.launcher.widget.WidgetPaneView.Seam homeSeam() {
        return new com.termux.app.launcher.widget.WidgetPaneView.Seam() {
            @Override public boolean canLeaveForward() {
                syncHomeCell();
                return mWall.currentPage() == PaneWallPage.WIDGETS && !mWall.isMoving()
                    && mWall.areGesturesEnabled()
                    && WallRow.seamContinues(mWall.neighbourId(1) != PaneWallLayout.NO_PAGE,
                        !mHost.hasWindow() && mHost.canStartWorkspace() && terminalBesideRun(1));
            }
            // A Home run after the Terminal page leaves backward from its first page.
            @Override public boolean canLeaveBackward() {
                syncHomeCell();
                return mWall.currentPage() == PaneWallPage.WIDGETS && !mWall.isMoving()
                    && mWall.areGesturesEnabled()
                    && WallRow.seamContinues(mWall.neighbourId(-1) != PaneWallLayout.NO_PAGE,
                        !mHost.hasWindow() && mHost.canStartWorkspace() && terminalBesideRun(-1));
            }
            @Override public void begin() {
                mWorkspacePending = false;
                if (!mHost.hasWindow() && mHost.canStartWorkspace()) {
                    // Only the page joins the row: nothing is started until the release commits.
                    mSeamIntoEmpty = true;
                    mSeamShellStarted = false;
                    refreshPages();
                }
                mWall.beginPlainDrag();
            }
            @Override public void drag(float dxPx) { mWall.dragTo(dxPx); }
            @Override public void end(float velocityPxPerSec) { mWall.endDrag(velocityPxPerSec); }
            @Override public void cancel() { mWall.cancelDrag(); }
        };
    }

    /** Build the wall's Widgets page: the app-widget grid, dressed as a pane. */
    @Nullable
    public WidgetPaneFrame attachWidgetsPage(@NonNull LayoutInflater inflater) {
        if (mWidgetsPage != null) return mWidgetsPage;
        WidgetPaneFrame frame = (WidgetPaneFrame) inflater.inflate(
            com.termux.R.layout.view_widget_pane, mWall, false);
        mWall.addView(frame, 0);
        mWidgetsPage = frame;
        mWall.setPageView(PaneWallPage.WIDGETS, frame);
        applyStyle(mStyle);
        return frame;
    }

    @Nullable
    public WidgetPaneFrame widgetsPage() {
        return mWidgetsPage;
    }

    /** True once the widget grid has been moved onto the wall. */
    public boolean hasWidgetsPage() {
        return mWidgetsPage != null;
    }

    /**
     * Build the display's frame on the wall as the Display pages' one view (A3b): it stands on
     * the bound Display page ({@link Host#bindDisplayPage}). Inflated here rather than in the
     * layout file so an install that never runs a display never builds its surface.
     */
    @Nullable
    public com.termux.app.x11.X11PaneFrame attachDisplayWindow(@NonNull LayoutInflater inflater) {
        if (mDisplayWindow != null) return mDisplayWindow;
        com.termux.app.x11.X11PaneFrame frame = (com.termux.app.x11.X11PaneFrame) inflater.inflate(
            com.termux.R.layout.view_x11_pane, mWall, false);
        mWall.addView(frame);
        mDisplayWindow = frame;
        mWall.setPageView(PaneWallPage.DISPLAY, frame);
        applyStyle(mStyle);
        return frame;
    }

    @Nullable
    public com.termux.app.x11.X11PaneFrame displayWindow() {
        return mDisplayWindow;
    }

    /**
     * Take the display window's frame off the wall: the view goes, and the X view's native context
     * goes with it when the view leaves the window. The Display page stays on the wall, with
     * no view until a frame is built again.
     */
    public void detachDisplayWindow() {
        com.termux.app.x11.X11PaneFrame frame = mDisplayWindow;
        if (frame == null) return;
        mDisplayWindow = null;
        mWall.setPageView(PaneWallPage.DISPLAY, null);
        refreshPages();
        mWall.removeView(frame);
    }

    /**
     * Dress every non-terminal page from the surface style. The terminal pages dress themselves
     * through {@code TerminalPaneController}; this is the same pass for the rest of the wall, and
     * it runs on the same triggers — a wallpaper change, a blur change, an editor slider tick.
     */
    public void applyStyle(@Nullable PaneSurfaceStyle style) {
        mStyle = style;
        // The grabber wears the rims' accent; a re-dress is where a scheme change arrives.
        mWall.refreshGrabberColor();
        if (mWidgetsPage != null) mWidgetsPage.applyStyle(style);
        if (mDisplayWindow != null) mDisplayWindow.applyStyle(style);
    }

    @NonNull
    public PaneWallPage currentPage() {
        return mWall.currentPage();
    }

    /** Whether a terminal window is the place the user is on, at rest. */
    public boolean isTerminalShowing() {
        return !mWall.isMoving() && terminalHasPixels();
    }

    /**
     * Whether any part of the terminal place can be on screen: it is the page at rest, or the wall
     * is moving and the terminal may be sliding into or out of the frame.
     *
     * <p>Not the negation of {@link #isTerminalShowing()}. That one asks whether the terminal is
     * the place the user is on; this one asks whether its pixels can be seen. A drag towards the
     * terminal shows it long before the wall commits to it, so anything that stops painting while
     * the terminal is away has to start again the moment the wall moves at all.
     */
    public boolean isTerminalOnScreen() {
        return mWall.isMoving() || terminalHasPixels();
    }

    private boolean mDisagreementLogged;

    /**
     * Whether the terminal page has pixels on screen, asked of the page view. The wall's record
     * of its current page is what it means to show; the pixels are what it shows. They part when a
     * slide is cut short, and that is logged once per episode so the path that cut it can be found.
     */
    private boolean terminalHasPixels() {
        boolean pixels = mWall.isTerminalHostOnScreen();
        boolean record = mWall.currentPage() == PaneWallPage.TERMINAL;
        if (mWall.isMoving() || pixels == record) {
            mDisagreementLogged = false;
        } else if (!mDisagreementLogged) {
            mDisagreementLogged = true;
            android.util.Log.w("TermuxWall", "record and pixels disagree: " + describeState());
        }
        return pixels;
    }

    /** The wall's state in one line, for the log. */
    @NonNull
    public String describeState() {
        View terminal = mWall.pageView(PaneWallPage.TERMINAL);
        return "page=" + mWall.currentPage() + " moving=" + mWall.isMoving()
            + " dragging=" + mWall.isDragging() + " offset=" + mWall.offsetPx()
            + " pageId=" + mWall.currentPageId() + " displayPage=" + mBoundDisplayPageId
            + " width=" + mWall.getWidth()
            + " terminalX=" + (terminal == null ? "none" : String.valueOf(terminal.getTranslationX()))
            + " terminalVisibility=" + (terminal == null ? "none" : String.valueOf(terminal.getVisibility()));
    }

    /**
     * The terminal page slides in from {@code fromPx} beside its place; see PaneWallLayout#nudgePage.
     *
     * @param fromWindow the window the arrival left, for the wallpaper's pan over every window
     *                   between ({@link #rowPosition}), or {@link WallParallax#ADJACENT} for the
     *                   one next door
     */
    public void nudgeTerminalPage(float fromPx, int fromWindow, long durationMs,
                                  @Nullable android.view.animation.Interpolator interpolator,
                                  @Nullable Runnable onEnd) {
        nudgeTerminalPage(fromPx, fromWindow, durationMs, interpolator, onEnd, null);
    }

    /** {@link #nudgeTerminalPage}, its clock held behind {@code clock}; see PaneWallLayout#nudgePage. */
    public void nudgeTerminalPage(float fromPx, int fromWindow, long durationMs,
                                  @Nullable android.view.animation.Interpolator interpolator,
                                  @Nullable Runnable onEnd, @Nullable FirstDrawGate clock) {
        mNudgeFromWindow = fromWindow;
        mWall.nudgePage(PaneWallPage.TERMINAL, fromPx, durationMs, interpolator, onEnd, clock);
    }

    /** The window the terminal page's current nudge left; see {@link #nudgeTerminalPage}. */
    private int mNudgeFromWindow = WallParallax.ADJACENT;

    // ---- The row's live position, for the wallpaper's pan ----------------------------------

    /**
     * How many stops the wallpaper's row spans: every Home page and window, and each Display
     * page, in the order of the cells, so the pan reads the wall the user arranged.
     */
    public int rowCount() {
        if ((!mDefaultOrder || mHasDisplaySlot) && mKnownPages != null) {
            return WallParallax.rowCount(mKnownPages, rowWindowCount());
        }
        return WallParallax.rowCount(rowHomeCount(), rowWindowCount());
    }

    /**
     * Where the wall stands on the wallpaper's row right now, in stops: the current cell's stop,
     * shifted by Home's own swipe or a window's nudge, and blended toward the neighbouring cell
     * while the wall slides. Read on every frame the wallpaper pans; it must not allocate.
     */
    public float rowPosition() {
        int homes = rowHomeCount();
        int windows = rowWindowCount();
        PaneWallPage current = mWall.currentPage();
        int restAt = rowIndexOfCell(mWall.currentPageId(), homes, windows);
        int width = mWall.getWidth();
        float at = restAt;
        if (width > 0 && current == PaneWallPage.WIDGETS) {
            at = WallParallax.innerPosition(restAt,
                mHost.innerShiftFraction(PaneWallPage.WIDGETS), WallParallax.ADJACENT);
        } else if (width > 0 && current == PaneWallPage.TERMINAL) {
            float nudgePx = mWall.nudgePx(PaneWallPage.TERMINAL);
            if (nudgePx != 0f) {
                int from = mNudgeFromWindow == WallParallax.ADJACENT ? WallParallax.ADJACENT
                    : rowIndexOf(PaneWallPage.TERMINAL, mNudgeFromWindow,
                        PaneWallLayout.NO_PAGE, homes, windows);
                at = WallParallax.innerPosition(restAt, nudgePx / width, from);
            }
        }
        float offsetPx = mWall.offsetPx();
        if (offsetPx == 0f || width <= 0) return at;
        // Positive: the pages sit to the right, so what is coming in is on the left.
        int steps = offsetPx > 0f ? -1 : 1;
        float neighbourAt;
        if (showsInnerPage(steps)) {
            // A border drag toward the next window inside the Workspace.
            neighbourAt = restAt + steps;
        } else {
            int neighbour = mWall.neighbourId(steps);
            // Past the row's end nothing comes in, and the picture holds.
            neighbourAt = neighbour == PaneWallLayout.NO_PAGE ? at
                : rowIndexOfCell(neighbour, homes, windows);
        }
        return WallParallax.slidePosition(at, neighbourAt, offsetPx, width);
    }

    /**
     * Whether a step of {@code steps} from the current cell stays inside its view (the next
     * window of the Terminal page) rather than crossing to the neighbouring cell. Never during a
     * slide from one cell to another.
     */
    public boolean showsInnerPage(int steps) {
        return !mWall.isCrossSlide() && hasInnerPage(mWall.currentPage(), steps);
    }

    /** Where page {@code pageId}'s cell stands on the wallpaper's row. */
    private int rowIndexOfCell(int pageId, int homes, int windows) {
        PaneWallPage kind = mWall.kindOf(pageId);
        if (kind == null) return 0;
        int index = kind == PaneWallPage.WIDGETS && pageId >= 0 ? homeIndexOf(pageId)
            : mHost.innerIndex(kind);
        return rowIndexOf(kind, index, pageId, homes, windows);
    }

    private int rowIndexOf(@NonNull PaneWallPage kind, int index, int pageId, int homes,
                           int windows) {
        WallPages pages = mKnownPages;
        if ((mDefaultOrder && !mHasDisplaySlot) || pages == null) {
            return WallParallax.rowIndex(kind, index, homes, windows);
        }
        WallPages.Stop stop;
        if (kind == PaneWallPage.DISPLAY) {
            int id = pageId >= 0 ? pageId : mDisplayPageId;
            if (id < 0) return 0;
            stop = WallPages.Stop.of(id);
        } else if (kind == PaneWallPage.WIDGETS) {
            java.util.List<WallPages.Page> homePages = pages.homePages();
            if (homePages.isEmpty()) return 0;
            stop = WallPages.Stop.of(homePages.get(
                Math.max(0, Math.min(homePages.size() - 1, index))).id);
        } else {
            WallPages.Page terminal = pages.terminal();
            if (terminal == null) return 0;
            stop = WallPages.Stop.window(terminal.id, Math.max(0, index));
        }
        return WallParallax.rowIndex(pages, stop, windows);
    }

    private int rowHomeCount() {
        return Math.max(1, mHost.innerCount(PaneWallPage.WIDGETS));
    }

    private int rowWindowCount() {
        if (!has(PaneWallPage.TERMINAL)) return 0;
        return Math.max(1, mHost.innerCount(PaneWallPage.TERMINAL));
    }

    /**
     * Go to a page by name ({@code wall.go}, the extra keys): {@code left}/{@code right} the
     * neighbouring cell whatever its kind; {@code home} the home press's page; {@code widgets}
     * the home page when it is a Home page, else the nearest; {@code display} the bound Display
     * page, else the nearest; {@code terminal}/{@code workspace} the Terminal page (ADR 0016
     * section 5). The target's view is bound before the slide, so it arrives live; a far one
     * arrives one width from a stand-off (W3). False when the wall has no such page.
     */
    public boolean goTo(@Nullable String name) {
        WallPages pages = mKnownPages;
        if (pages == null) {
            PaneWallPage page = PaneWallPolicy.parsePage(mWall.pages(), mWall.currentPage(), name);
            if (page == null) return false;
            mWorkspacePending = false;
            if (page == PaneWallPage.WIDGETS && PaneWallPolicy.namesHome(name))
                mHost.showInnerPage(PaneWallPage.WIDGETS, Math.max(0, mHost.homePressPage()));
            return mWall.goTo(page, true);
        }
        if (name == null) return false;
        int current = currentPageId();
        WallPages.Page target;
        switch (name.trim().toLowerCase(java.util.Locale.ROOT)) {
            case "left": target = pages.leftOf(current); break;
            case "right": target = pages.rightOf(current); break;
            case "home": {
                // The home press's page, Start in honoured (W9); else the nearest Home page.
                int id = homeIdAt(Math.max(0, mHost.homePressPage()));
                if (id != PaneWallLayout.NO_PAGE) return goToPage(id, true);
                target = pages.homeTarget(current);
                break;
            }
            case "widgets": target = pages.homeTarget(current); break;
            case "display": target = pages.displayTarget(current, mBoundDisplayPageId); break;
            case "workspace":
            case "terminal": target = pages.terminalTarget(); break;
            default: return false;
        }
        return target != null && goToPage(target.id, true);
    }

    /**
     * Go to a page of kind {@code page}: the current cell when it is of that kind, else the page
     * its view is bound to.
     */
    public boolean goTo(@NonNull PaneWallPage page, boolean animate) {
        mWorkspacePending = false;
        return mWall.goTo(page, animate);
    }

    /**
     * Go to the Terminal page: its window in front, or, with none, start its first shell and land
     * there once the window arrives. Under Terminal only the empty Workspace is a cell too.
     * False when there is nowhere to go.
     */
    public boolean goToWorkspace(boolean animate) {
        boolean homeShown = mHost.useAs().showsHomePage();
        boolean hasWindow = mHost.hasWindow();
        // Under Terminal only the Workspace is on the row while it is empty too; going there
        // still starts its first shell, as the Terminal entry does.
        if (has(PaneWallPage.TERMINAL) && (homeShown || hasWindow))
            return goTo(PaneWallPage.TERMINAL, animate);
        if (hasWindow || !mHost.canStartWorkspace() || !mHost.startWorkspaceShell())
            return !homeShown && goTo(PaneWallPage.TERMINAL, animate);
        mWorkspacePending = true;
        refreshPages();
        return true;
    }

    /** Back to the Terminal page's cell, from wherever the wall stands. */
    public void returnToTerminal(boolean animate) {
        goTo(PaneWallPage.TERMINAL, animate);
    }

    /**
     * The Android home press: the home page Edit pages set (a Display page, the Terminal page, or
     * the home press's Home page), wherever the wall is. A far one slides one width from a
     * stand-off and the pages between are never shown or started (W3, W9).
     */
    public void goHome(boolean animate) {
        // Edit pages set a Display page as home: the press lands there (A3b), and the landing
        // starts the display.
        int displayHome = DisplayPagePolicy.homeDisplayPage(mHost.wallPages());
        if (displayHome >= 0 && goToDisplayPage(displayHome, animate)) return;
        if (!mHost.useAs().showsHomePage()) {
            // Terminal only: the home press lands on the Workspace.
            goToWorkspace(animate);
            return;
        }
        if (homeIsTerminalPage()) {
            // Edit pages set the Terminal page as home: the press lands on its window in front,
            // or starts its first shell, as Home's swipe into the Workspace does.
            goToWorkspace(animate);
            return;
        }
        // The home press's page, wherever the wall is: a far one slides one width from a
        // stand-off, the pages between never shown (W3, W9).
        goToHomePage(Math.max(0, mHost.homePressPage()), animate);
    }

    private boolean homeIsTerminalPage() {
        WallPages pages = mHost.wallPages();
        WallPages.Page home = pages == null ? null : pages.home();
        return home != null && home.kind == WallPages.PageKind.TERMINAL;
    }

    /**
     * Go to the Home page beside the Terminal page, the last before it, else the first after:
     * where the wall goes when the last window closes under it.
     */
    public void goToLastHomePage(boolean animate) {
        if (!mHost.useAs().showsHomePage()) {
            goTo(PaneWallPage.TERMINAL, animate);
            return;
        }
        if (mKnownPages != null) {
            java.util.List<PaneWallLayout.Cell> cells = mWall.cells();
            int terminal = mWall.indexOfCell(mWall.boundPageId(PaneWallPage.TERMINAL));
            int id = PaneWallLayout.NO_PAGE;
            if (terminal > 0 && cells.get(terminal - 1).kind == PaneWallPage.WIDGETS) {
                id = cells.get(terminal - 1).pageId;
            } else if (terminal >= 0 && terminal + 1 < cells.size()
                && cells.get(terminal + 1).kind == PaneWallPage.WIDGETS) {
                id = cells.get(terminal + 1).pageId;
            } else {
                id = homeIdAt(homeCount() - 1);
            }
            if (id != PaneWallLayout.NO_PAGE) {
                goToPage(id, animate);
                return;
            }
        }
        mHost.showInnerPage(PaneWallPage.WIDGETS,
            WallRow.lastHomePage(mHost.innerCount(PaneWallPage.WIDGETS)));
        goTo(PaneWallPage.WIDGETS, animate);
    }

    /**
     * Land where Start in says on a cold start, without a slide: the Terminal page, or the home
     * press's page.
     */
    public void landOnColdStart(@Nullable String startIn) {
        if (WallRow.coldStart(mHost.useAs().startIn(startIn)) == PaneWallPage.TERMINAL) {
            landInWorkspace();
        } else {
            goHome(false);
        }
    }

    private void landInWorkspace() {
        if (has(PaneWallPage.TERMINAL)) {
            goTo(PaneWallPage.TERMINAL, false);
        } else {
            mWorkspacePending = true;
        }
    }

    /** The page in front, as a row position. */
    @NonNull
    public WallRow.Position currentPosition() {
        PaneWallPage page = mWall.currentPage();
        return new WallRow.Position(page, mHost.innerIndex(page));
    }

    /** Hold the wall still while another surface owns the gesture. */
    public void setGesturesEnabled(boolean enabled) {
        mWall.setGesturesEnabled(enabled);
    }

    // ---- Saved state -----------------------------------------------------------------------

    /**
     * Activity recreation (rotation, theme) keeps the row position: the half the wall rested in
     * and the page's index within it, never a page name. Process death does not: a cold start
     * follows Start in ({@link #landOnColdStart}).
     */
    public void onSaveInstanceState(@NonNull Bundle state) {
        WallRow.Position position = currentPosition();
        state.putString(ARG_HALF, WallRow.halfOf(position.kind));
        state.putInt(ARG_INDEX, WallRow.savedIndex(position));
    }

    /**
     * Back to the saved row position. A half this version does not know comes back to Home page
     * 1; a state with no half at all changes nothing.
     *
     * @return whether the state named a position, so the caller does not land a cold start too
     */
    public boolean restoreInstanceState(@Nullable Bundle state) {
        if (state == null || !state.containsKey(ARG_HALF)) return false;
        String half = state.getString(ARG_HALF);
        int index = state.getInt(ARG_INDEX, 0);
        // The windows come back with the terminal's own saved state, which may not be there yet:
        // a count of 0 means "not known yet", and the Workspace is waited for.
        int windows = has(PaneWallPage.TERMINAL) ? mHost.innerCount(PaneWallPage.TERMINAL) : 0;
        WallRow.Position position = WallRow.restore(half, index,
            mHost.innerCount(PaneWallPage.WIDGETS), windows);
        if (WallRow.HALF_WORKSPACE.equals(half) && windows <= 0) {
            landInWorkspace();
            return true;
        }
        if (position.kind == PaneWallPage.WIDGETS) {
            // wall_index is the global Home-page index: the run that holds it is bound first.
            goToHomePage(position.index, false);
        } else {
            landInWorkspace();
        }
        return true;
    }

    // ---- PaneWallLayout.Listener -----------------------------------------------------------

    @Override
    public void onWallPageChanged(@NonNull PaneWallPage page) {
        mHost.onWallPageChanged(page);
        boolean intoEmpty = mSeamIntoEmpty || (mEmptyTerminalStop && !mHost.hasWindow());
        if (mSeamShellStarted || !WallRow.seamStartsShell(intoEmpty, page)) return;
        mSeamShellStarted = true;
        // The release committed to the empty Workspace: its first shell starts now, while the
        // wall slides there. Its window takes the page the swipe brought in.
        if (!mHost.startWorkspaceShell()) {
            mSeamIntoEmpty = false;
            boolean stop = mEmptyTerminalStop;
            refreshPages();
            // The empty stop stays on the row: with no shell to land on, the wall goes back.
            if (stop && mWall.currentPage() == PaneWallPage.TERMINAL
                && has(PaneWallPage.WIDGETS)) {
                mWall.goTo(PaneWallPage.WIDGETS, true);
            }
            return;
        }
        refreshPages();
    }

    @Override
    public void onWallPageSettled(@NonNull PaneWallPage page) {
        onWallCellSettled(mWall.currentPageId(), page);
    }

    /**
     * The wall came to rest on page {@code pageId}'s cell: the landing binds that page's kind's
     * view to it, and the still there gives way (ADR 0016). The chrome is not touched here: it
     * took the page kind's dress when the slide committed (ADR 0012).
     */
    @Override
    public void onWallCellSettled(int pageId, @NonNull PaneWallPage page) {
        // The swipe sprang back to Home: the empty Terminal page leaves the wall again.
        if (mSeamIntoEmpty && page == PaneWallPage.WIDGETS) {
            mSeamIntoEmpty = false;
            mSeamShellStarted = false;
            refreshPages();
        }
        bindCell(pageId);
        mHost.onWallPageSettled(page);
        if (page == PaneWallPage.DISPLAY && mBoundDisplayPageId >= 0) {
            mHost.onDisplayPageLanded(mBoundDisplayPageId);
        }
    }

    // The windows page inside the Workspace's view by the border drag; Home's pages page inside
    // the widget grid by its own swipe, so the wall never steps inside Home.
    @Override
    public boolean hasInnerPage(@NonNull PaneWallPage page, int steps) {
        return page == PaneWallPage.TERMINAL && WallRow.hasInnerStep(mHost.innerIndex(page),
            mHost.innerCount(page), steps);
    }

    @Override
    public void onInnerStepBegin(@NonNull PaneWallPage page, int steps) {
        mHost.onInnerStepBegin(page, steps);
    }

    @Override
    public void onInnerStep(@NonNull PaneWallPage page, int steps, float fromPx) {
        mHost.onInnerStep(page, steps, fromPx);
    }

    /**
     * A drag is taking the wall from the current cell: the Home cell follows the Home page the
     * frame shows, and the views that can arrive beside the seam are put on the page they will
     * show while still off screen (the window next to Home, a kind-only Home cell's last page).
     */
    @Override
    public void onWallDragBegin(@NonNull PaneWallPage page) {
        mWorkspacePending = false;
        syncHomeCell();
        // Off the first window a kind-only Home cell, which holds every Home page, comes in on
        // its last page, the one beside the seam.
        int left = mWall.neighbourId(-1);
        if (page == PaneWallPage.TERMINAL && left < 0 && left != PaneWallLayout.NO_PAGE
            && mWall.kindOf(left) == PaneWallPage.WIDGETS
            && mHost.innerIndex(PaneWallPage.TERMINAL) == 0) {
            int last = WallRow.lastHomePage(mHost.innerCount(PaneWallPage.WIDGETS));
            if (mHost.innerIndex(PaneWallPage.WIDGETS) != last) {
                mHost.showInnerPage(PaneWallPage.WIDGETS, last);
            }
        }
        // Leaving Home, the Terminal page's window beside the seam waits in front: the first
        // window when the Terminal page stands on Home's right, the last on its left.
        if (page != PaneWallPage.WIDGETS) return;
        int windows = mHost.innerCount(PaneWallPage.TERMINAL);
        if (windows <= 0 || !has(PaneWallPage.TERMINAL)) return;
        boolean right = mWall.indexOfCell(mWall.boundPageId(PaneWallPage.TERMINAL))
            > mWall.indexOfCell(mWall.currentPageId());
        mHost.showInnerPage(PaneWallPage.TERMINAL, right ? 0 : windows - 1);
    }

    /**
     * A drag now heads {@code steps} cells away: the view of the kind it heads for is bound to
     * that cell before it comes on screen, so the page arrives live rather than as its card. The
     * view under the finger is never taken from its cell.
     */
    @Override
    public void onWallDragHeading(@NonNull PaneWallPage page, int steps) {
        // A step to the next window stays inside the Workspace.
        if (page == PaneWallPage.TERMINAL && hasInnerPage(PaneWallPage.TERMINAL, steps)) return;
        int target = mWall.neighbourId(steps);
        PaneWallPage kind = mWall.kindOf(target);
        // The view the finger holds is not taken from it: a Display page beside another shows
        // its still until the landing binds it.
        if (kind == null || kind == page) return;
        // The view of the kind the drag heads for goes with it to the cell beside, so the page
        // arrives live: the Home frame turned to the Home page next to the seam, the display
        // view on that Display page.
        if (kind == PaneWallPage.WIDGETS || kind == PaneWallPage.DISPLAY) bindCell(target);
    }

    @Override
    public boolean isBorderPagingEnabled(@NonNull PaneWallPage page) {
        // A Home page has no border; it pages by a plain swipe anywhere.
        return page != PaneWallPage.WIDGETS;
    }

    /**
     * A terminal window's text pages the row by a sideways swipe, as its border does. Not the
     * display window's: what it shows takes every touch, and only its border pages.
     */
    @Override
    public boolean isBodySwipePaging(@NonNull PaneWallPage page, float rawX, float rawY) {
        return page == PaneWallPage.TERMINAL && mHost.bodySwipePagesWall(rawX, rawY);
    }

    /**
     * A terminal window's last row pages the row by a clearly sideways swipe from the seam band
     * ({@link SeamSwipe}), where the terminal gives the swipe away, whatever the body swipe's
     * setting. Not the display window's picture, which takes every touch.
     */
    @Override
    public boolean isSeamSwipePaging(@NonNull PaneWallPage page, float rawX, float rawY) {
        return page == PaneWallPage.TERMINAL && mHost.seamSwipePagesWall(rawX, rawY);
    }

    @Override
    public void onWallOffsetChanged(float offsetPx) {
        reaimPages();
        syncOutlineAlpha(offsetPx);
        mHost.onWallOffsetChanged(offsetPx);
    }

    /**
     * The pages' outlines fade out over the first stretch of a slide and back in over the last,
     * so one edge shows while two pages share the screen. Written only when the number moves,
     * which is a few frames at either end; a page pressed into a line's outer edge keeps its
     * outline, since it is alone on screen.
     */
    private void syncOutlineAlpha(float offsetPx) {
        float alpha = PaneWallPolicy.blendsPlaces(mWall.pages(), mWall.currentPage(), offsetPx)
            ? PaneWallPolicy.outlineAlpha(offsetPx, mWall.getWidth()) : 1f;
        if (alpha != mOutlineAlpha) {
            mOutlineAlpha = alpha;
            if (mWidgetsPage != null) mWidgetsPage.setOutlineTravelAlpha(alpha);
            if (mDisplayWindow != null) mDisplayWindow.setOutlineTravelAlpha(alpha);
        }
        // The terminal's rims are drawn in its page: while the transition fades that page they
        // go with its fade, not ahead of it (PaneWallPolicy#terminalRimAlpha).
        float rims = PaneWallPolicy.terminalRimAlpha(alpha, mWall.terminalPageAlpha());
        if (rims == mTerminalRimAlpha) return;
        mTerminalRimAlpha = rims;
        mHost.onWallOutlineAlphaChanged(rims);
    }

    @Override
    public void onTerminalOffScreenChanged(boolean offScreen) {
        mHost.onTerminalOffScreenChanged(offScreen);
    }

    @Override
    public boolean isBorderKeyboardSwipeEnabled() {
        return mHost.isBorderKeyboardSwipeEnabled();
    }

    @Override
    public void onBorderKeyboardSwipe(boolean open) {
        mHost.onBorderKeyboardSwipe(open);
    }

    @Override
    public int onKeyboardRevealBegin(boolean opening) {
        return mHost.onKeyboardRevealBegin(opening);
    }

    @Override
    public void onKeyboardRevealProgress(float reveal) {
        mHost.onKeyboardRevealProgress(reveal);
    }

    @Override
    public void onKeyboardRevealEnd(boolean open) {
        mHost.onKeyboardRevealEnd(open);
    }

    @Override
    public boolean isBorderStatusSwipeEnabled() {
        return mHost.isBorderStatusSwipeEnabled();
    }

    @Override
    public boolean isBorderStatusSwipeTwoFingers() {
        return mHost.isBorderStatusSwipeTwoFingers();
    }

    @Override
    public void onBorderStatusSwipe(boolean expand) {
        mHost.onBorderStatusSwipe(expand);
    }

    @Override
    public int onStatusFoldBegin(boolean expanding) {
        return mHost.onStatusFoldBegin(expanding);
    }

    @Override
    public void onStatusFoldProgress(float towardOpenPx) {
        mHost.onStatusFoldProgress(towardOpenPx);
    }

    @Override
    public void onStatusFoldEnd(boolean expanded, float towardOpenVelocityPxPerSec) {
        mHost.onStatusFoldEnd(expanded, towardOpenVelocityPxPerSec);
    }

    @Override
    public void onPageSinkChanged(@NonNull PaneWallPage page, float scale) {
        reaimPages();
        mHost.onPageSinkChanged(page, scale);
    }

    @Override
    public void onPagePoseChanged() {
        reaimPages();
        mHost.onPagePoseChanged();
    }

    /**
     * The pages' glass is aimed at the wallpaper in screen space, past the transforms the wall
     * puts on its pages — the slide, the sink, the plank's tilt — and a transform never redraws
     * a child: each page re-aims its slab for this frame. Only on the frames the wall moves.
     */
    private void reaimPages() {
        if (mWidgetsPage != null) mWidgetsPage.onWallMoved();
        if (mDisplayWindow != null) mDisplayWindow.onWallMoved();
    }

    @Override
    public boolean isPlankTiltEnabled(@NonNull PaneWallPage page) {
        // Every page, the display window too: its picture is a SurfaceView a rotation leaves flat,
        // so the wall tips it only while a still copy stands in for the surface (SurfacePage), and
        // slides it flat until then.
        return mHost.isPlankTiltEnabled();
    }
}
