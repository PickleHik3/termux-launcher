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

    // ---- The built window (ADR 0015, A2a) ---------------------------------------------------
    // The wall lays out a distinct-kind page list: the current slot and its neighbours, with one
    // Home frame bound to one Home run. These are the slots it was built from, or null where the
    // kind-keyed row stands alone (no pages known, Terminal only, no Home run).
    @Nullable private java.util.List<WallPages.Slot> mSlots;
    @Nullable private WallPages mSlotPages;
    /** The slots are [Home...][Terminal]: everything reads as it always did. */
    private boolean mDefaultOrder = true;
    /** A page id of the Home run the frame is bound to; -1 for the first run. */
    private int mHomeRunId = -1;
    /**
     * The Terminal page stands in the built window with no window, as a stop whose landing
     * starts the first shell (non-default orders only).
     */
    private boolean mEmptyTerminalStop;
    /** The row had a window at the last {@link #refreshPages}. */
    private boolean mHadWindow;
    /** The run last handed to the host ({@link Host#bindHomeRun}). */
    private int mBoundFirst = 0;
    private int mBoundLast = -1;
    /** The Display page the display view is bound to (A3b); -1 for none, or the first one. */
    private int mDisplayPageId = -1;
    /** The Display page last handed to the host ({@link Host#bindDisplayPage}). */
    private int mBoundDisplayPageId = -1;
    /** The slots hold a Display page: the wallpaper's pan reads the pages' order. */
    private boolean mHasDisplaySlot;
    /**
     * For one re-build: the slot the window is built around instead of the wall's current one,
     * to bring a Display page that is not next door into the window ({@link #goToDisplayPage}).
     */
    private int mLandingSlot = -1;
    static final String SLOTS_TAG = "WallSlots";

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
     * Re-read what decides the row: whether the current session's row has a window. A page that
     * leaves while the wall rests on it hands the wall to the page on its left, turned to its
     * last: the last Home page when the last window closes.
     */
    public void refreshPages() {
        mWall.setReducedMotion(mHost.reducedMotion());
        PaneWallPage before = mWall.currentPage();
        boolean hasWindow = mHost.hasWindow();
        boolean lostWindow = mHadWindow && !hasWindow;
        mHadWindow = hasWindow;
        if (hasWindow) { mSeamIntoEmpty = false; mSeamShellStarted = false; }
        // Which side of the Terminal page the Home frame stood on, for the hand-over below.
        java.util.List<PaneWallPage> was = mWall.pages();
        int homeSide = Integer.signum(was.indexOf(PaneWallPage.WIDGETS)
            - was.indexOf(PaneWallPage.TERMINAL));
        mWall.setPages(builtWindow(hasWindow || mSeamIntoEmpty));
        PaneWallPage after = mWall.currentPage();
        // Home taken off the row by Use as hands the wall to the Workspace as it stands, the
        // window in front kept; only a page handing to its left turns that page to its last.
        // A Home run on the right of the page that went is turned to its first.
        if (after != before && !mWall.pages().contains(before)
            && after != PaneWallPage.TERMINAL) {
            int page = Math.max(0, mHost.innerCount(after) - 1);
            if (after == PaneWallPage.WIDGETS && !mDefaultOrder && mSlots != null) {
                page = runPage(boundSlot(), WallRow.arrivalPage(
                    PaneWallPolicy.runLength(mSlots, boundSlot()), homeSide > 0 ? -1 : 1));
            }
            mHost.showInnerPage(after, page);
        }
        // The last window closed under the wall while the empty Workspace keeps its stop: the
        // wall hands to the Home run beside it, as it does when the Workspace leaves the row.
        if (lostWindow && mEmptyTerminalStop && !mSeamIntoEmpty && !mWorkspacePending
            && mWall.currentPage() == PaneWallPage.TERMINAL) {
            goToLastHomePage(false);
        }
        if (mWorkspacePending && mWall.pages().contains(PaneWallPage.TERMINAL)) {
            mWorkspacePending = false;
            mWall.goTo(PaneWallPage.TERMINAL, false);
        }
    }

    /**
     * The page list the wall lays out: the kind-keyed row ({@link UseAs#rowPages}) where the
     * pages are not known or have no Home run, else the built window over the slots
     * ({@link PaneWallPolicy#builtWindow}). Binds the Home frame to its run on the way.
     */
    @NonNull
    private java.util.List<PaneWallPage> builtWindow(boolean onRow) {
        boolean homeShown = mHost.useAs() == UseAs.HOME;
        WallPages pages = mHost.wallPages();
        // Outside the default order an empty Workspace keeps its slot, as a stop whose landing
        // starts the first shell: two Home runs never merge over it, and the far run stays
        // reachable through it. The default order keeps the Workspace off the row, as always.
        boolean emptyStop = homeShown && pages != null && !onRow && mHost.canStartWorkspace()
            && !PaneWallPolicy.isDefaultOrder(PaneWallPolicy.wallSlots(pages, true));
        mEmptyTerminalStop = emptyStop;
        // Each Display page is a slot of its own (A3b). Under Terminal only the Home slots go and
        // the Workspace stays on the row while it is empty too, as its kind-keyed row does.
        java.util.List<WallPages.Slot> slots = pages == null ? null
            : PaneWallPolicy.wallSlots(pages, onRow || emptyStop || !homeShown, true);
        if (slots != null && !homeShown) slots = withoutHome(slots);
        boolean hasHome = slots != null
            && PaneWallPolicy.firstSlotOf(slots, PaneWallPage.WIDGETS) >= 0;
        boolean hasDisplay = slots != null
            && PaneWallPolicy.firstSlotOf(slots, PaneWallPage.DISPLAY) >= 0;
        if (slots == null || (homeShown ? !hasHome : !hasDisplay)
            || (onRow && PaneWallPolicy.firstSlotOf(slots, PaneWallPage.TERMINAL) < 0)) {
            mSlots = null;
            mSlotPages = null;
            mDefaultOrder = true;
            mHasDisplaySlot = false;
            mEmptyTerminalStop = false;
            bindRun(0, -1);
            bindDisplay(-1);
            return mHost.useAs().rowPages(onRow);
        }
        mSlots = slots;
        mSlotPages = pages;
        mDefaultOrder = PaneWallPolicy.isDefaultOrder(slots);
        mHasDisplaySlot = hasDisplay;
        int current = mLandingSlot >= 0 && mLandingSlot < slots.size() ? mLandingSlot
            : currentSlot(slots);
        int home = hasHome ? PaneWallPolicy.boundHomeSlot(slots, current,
            PaneWallPolicy.slotHolding(slots, mHomeRunId)) : -1;
        if (home >= 0) mHomeRunId = slots.get(home).id();
        if (mDefaultOrder || home < 0) {
            bindRun(0, -1);
        } else {
            int first = PaneWallPolicy.runStart(slots, home);
            bindRun(first, first + PaneWallPolicy.runLength(slots, home) - 1);
        }
        int display = PaneWallPolicy.boundDisplaySlot(slots, current,
            PaneWallPolicy.slotHolding(slots, mDisplayPageId));
        bindDisplay(display >= 0 ? slots.get(display).id() : -1);
        return PaneWallPolicy.builtWindow(slots, current, home, display);
    }

    /** {@code slots} without their Home slots: the row under Terminal only. */
    @NonNull
    private static java.util.List<WallPages.Slot> withoutHome(
            @NonNull java.util.List<WallPages.Slot> slots) {
        java.util.List<WallPages.Slot> out = new java.util.ArrayList<>(slots.size());
        for (WallPages.Slot slot : slots) if (slot.kind != WallPages.PageKind.HOME) out.add(slot);
        return out;
    }

    /** Bind the display view to Display page {@code pageId}, telling the host when it moved. */
    private void bindDisplay(int pageId) {
        if (pageId >= 0) mDisplayPageId = pageId;
        if (pageId == mBoundDisplayPageId) return;
        mBoundDisplayPageId = pageId;
        mHost.bindDisplayPage(pageId);
    }

    /** The Display page the display view is bound to; -1 while the wall has none. */
    public int boundDisplayPage() {
        return mBoundDisplayPageId;
    }

    /**
     * Go to Display page {@code pageId} (-1: the bound one, else the first): the window is
     * re-built around it when it is not next door, and the wall slides there. False when the
     * wall has no such Display page.
     */
    public boolean goToDisplayPage(int pageId, boolean animate) {
        java.util.List<WallPages.Slot> slots = mSlots;
        if (slots == null) return false;
        int slot = PaneWallPolicy.slotHolding(slots, pageId >= 0 ? pageId : mDisplayPageId);
        if (slot < 0 || slots.get(slot).kind != WallPages.PageKind.DISPLAY) {
            slot = PaneWallPolicy.firstSlotOf(slots, PaneWallPage.DISPLAY);
        }
        if (slot < 0) return false;
        mDisplayPageId = slots.get(slot).id();
        mLandingSlot = mWall.currentPage() == PaneWallPage.DISPLAY
            && mBoundDisplayPageId == mDisplayPageId ? -1 : slot;
        try {
            rebuild();
        } finally {
            mLandingSlot = -1;
        }
        if (!mWall.pages().contains(PaneWallPage.DISPLAY)) return false;
        mWorkspacePending = false;
        mWall.goTo(PaneWallPage.DISPLAY, animate);
        return true;
    }

    /** Re-build the window around the wall as it stands (a re-bound Home frame, say). */
    private void rebuild() {
        mWall.setPages(builtWindow(mHost.hasWindow() || mSeamIntoEmpty));
    }

    /**
     * The slot the wall is on: the bound Home run on Home, the bound Display page on Display,
     * else the slot of the page's kind; the first Home slot, else the Terminal's, else the
     * first, where the page has none.
     */
    private int currentSlot(@NonNull java.util.List<WallPages.Slot> slots) {
        PaneWallPage page = mWall.currentPage();
        int slot;
        if (page == PaneWallPage.DISPLAY) {
            slot = PaneWallPolicy.slotHolding(slots, mDisplayPageId);
            if (slot >= 0 && slots.get(slot).kind == WallPages.PageKind.DISPLAY) return slot;
        }
        slot = page == PaneWallPage.WIDGETS ? -1 : PaneWallPolicy.firstSlotOf(slots, page);
        if (slot >= 0) return slot;
        slot = PaneWallPolicy.slotHolding(slots, mHomeRunId);
        if (slot >= 0 && slots.get(slot).kind == WallPages.PageKind.HOME) return slot;
        slot = PaneWallPolicy.firstSlotOf(slots, PaneWallPage.WIDGETS);
        if (slot >= 0) return slot;
        slot = PaneWallPolicy.firstSlotOf(slots, PaneWallPage.TERMINAL);
        return slot >= 0 ? slot : (slots.isEmpty() ? -1 : 0);
    }

    /** The index among {@link #mSlots} of the Home run the frame is bound to. */
    private int boundSlot() {
        java.util.List<WallPages.Slot> slots = mSlots;
        if (slots == null) return 0;
        int slot = PaneWallPolicy.slotHolding(slots, mHomeRunId);
        return slot >= 0 ? slot : Math.max(0, PaneWallPolicy.firstSlotOf(slots, PaneWallPage.WIDGETS));
    }

    /** The global Home-page index of page {@code pageInRun} of Home slot {@code slot}. */
    private int runPage(int slot, int pageInRun) {
        java.util.List<WallPages.Slot> slots = mSlots;
        return slots == null ? pageInRun : PaneWallPolicy.runStart(slots, slot) + pageInRun;
    }

    private void bindRun(int first, int last) {
        if (first == mBoundFirst && last == mBoundLast) return;
        mBoundFirst = first;
        mBoundLast = last;
        mHost.bindHomeRun(first, last);
    }

    /**
     * Whether the Terminal page, on the row or not, stands next to the bound Home run on side
     * {@code direction}: where Home's swipe into an empty Workspace goes. Always forward for the
     * default order.
     */
    private boolean terminalBesideRun(int direction) {
        if (mSlots == null || mDefaultOrder || mSlotPages == null) return direction > 0;
        java.util.List<WallPages.Slot> all = PaneWallPolicy.wallSlots(mSlotPages, true);
        int home = PaneWallPolicy.slotHolding(all, mHomeRunId);
        int terminal = PaneWallPolicy.firstSlotOf(all, PaneWallPage.TERMINAL);
        return home >= 0 && terminal >= 0 && terminal == home + direction;
    }

    /**
     * Show global Home page {@code index}, the frame bound to the run that holds it first, so a
     * Home page of a run after the Terminal page is shown where it stands.
     */
    private void showHomePage(int index) {
        java.util.List<WallPages.Slot> slots = mSlots;
        if (slots != null && !mDefaultOrder) {
            int slot = PaneWallPolicy.homeSlotAt(slots, index);
            if (slot >= 0) {
                mHomeRunId = slots.get(slot).id();
                rebuild();
            }
        }
        mHost.showInnerPage(PaneWallPage.WIDGETS, index);
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
                return mWall.currentPage() == PaneWallPage.WIDGETS && !mWall.isMoving()
                    && mWall.areGesturesEnabled()
                    && WallRow.seamContinues(
                        PaneWallPolicy.hasNeighbour(mWall.pages(), PaneWallPage.WIDGETS, 1),
                        !mHost.hasWindow() && mHost.canStartWorkspace() && terminalBesideRun(1));
            }
            // A Home run after the Terminal page leaves backward from its first page.
            @Override public boolean canLeaveBackward() {
                return mWall.currentPage() == PaneWallPage.WIDGETS && !mWall.isMoving()
                    && mWall.areGesturesEnabled()
                    && WallRow.seamContinues(
                        PaneWallPolicy.hasNeighbour(mWall.pages(), PaneWallPage.WIDGETS, -1),
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

    /** The places this install has, in spatial order. */
    @NonNull
    public java.util.List<PaneWallPage> pages() {
        return mWall.pages();
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
            + " displayPage=" + mBoundDisplayPageId
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
     * How many pages the row has: the Home pages, and the windows, the display window's included
     * (the Workspace counts as one page while it is on the row before its windows are known). See
     * {@link WallParallax#rowCount}.
     */
    public int rowCount() {
        if ((!mDefaultOrder || mHasDisplaySlot) && mSlotPages != null) {
            return WallParallax.rowCount(mSlotPages, rowWindowCount(mWall.pages()));
        }
        return WallParallax.rowCount(rowHomeCount(), rowWindowCount(mWall.pages()));
    }

    /**
     * Where the row is right now, in pages from Home page 1: whole at rest, fractional while
     * anything moves it. Composed from the three things that move the row, none of which knows
     * about the others: the wall's offset between its views (the seam, a border drag between
     * windows), Home's own swipe between Home pages, and the
     * terminal page's nudge as a window arrives. Read on every frame the wallpaper pans, so
     * nothing here allocates.
     */
    public float rowPosition() {
        java.util.List<PaneWallPage> pages = mWall.pages();
        int homes = rowHomeCount();
        int windows = rowWindowCount(pages);
        PaneWallPage current = mWall.currentPage();
        int restAt = rowIndexOf(current, homes, windows);
        int width = mWall.getWidth();
        float at = restAt;
        if (width > 0 && current == PaneWallPage.WIDGETS) {
            at = WallParallax.innerPosition(restAt,
                mHost.innerShiftFraction(PaneWallPage.WIDGETS), WallParallax.ADJACENT);
        } else if (width > 0 && current == PaneWallPage.TERMINAL) {
            float nudgePx = mWall.nudgePx(PaneWallPage.TERMINAL);
            if (nudgePx != 0f) {
                int from = mNudgeFromWindow == WallParallax.ADJACENT ? WallParallax.ADJACENT
                    : rowIndexOf(PaneWallPage.TERMINAL, mNudgeFromWindow, homes, windows);
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
            PaneWallPage neighbour = PaneWallPolicy.neighbour(pages, current, steps);
            // Past the row's end nothing comes in, and the picture holds.
            neighbourAt = neighbour == current ? at : rowIndexOf(neighbour, homes, windows);
        }
        return WallParallax.slidePosition(at, neighbourAt, offsetPx, width);
    }

    /**
     * Whether the page the wall shows {@code steps} away from the current one (-1 on its left, +1
     * on its right) is a window of the current page's own — the previous or next window inside
     * the Workspace — rather than the next view on the row: as the wall places its pages
     * ({@link PaneWallLayout#applyPagePositions}), so never during a slide from one view to
     * another. What reads the wall's offset as a place change asks this first.
     */
    public boolean showsInnerPage(int steps) {
        return !mWall.isCrossSlide() && hasInnerPage(mWall.currentPage(), steps);
    }

    /** Where the page {@code kind}'s view shows sits in the row. */
    private int rowIndexOf(@NonNull PaneWallPage kind, int homes, int windows) {
        return rowIndexOf(kind, mHost.innerIndex(kind), homes, windows);
    }

    /**
     * Where page {@code index} of {@code kind} sits in the row: the Home-first arithmetic for the
     * default order, else the stop's place among the pages' stops ({@link WallParallax#rowIndex}).
     */
    private int rowIndexOf(@NonNull PaneWallPage kind, int index, int homes, int windows) {
        WallPages pages = mSlotPages;
        if ((mDefaultOrder && !mHasDisplaySlot) || pages == null) {
            return WallParallax.rowIndex(kind, index, homes, windows);
        }
        WallPages.Stop stop;
        if (kind == PaneWallPage.DISPLAY && mDisplayPageId >= 0) {
            stop = WallPages.Stop.of(mDisplayPageId);
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

    private int rowWindowCount(@NonNull java.util.List<PaneWallPage> pages) {
        if (!pages.contains(PaneWallPage.TERMINAL)) return 0;
        return Math.max(1, mHost.innerCount(PaneWallPage.TERMINAL));
    }

    /** Navigate by the {@code page=} argument of {@code wall.go}: a name, or left/right. */
    public boolean goTo(@Nullable String name) {
        PaneWallPage page = PaneWallPolicy.parsePage(mWall.pages(), mWall.currentPage(), name);
        if (page == null) return false;
        mWorkspacePending = false;
        return mWall.goTo(page, true);
    }

    public boolean goTo(@NonNull PaneWallPage page, boolean animate) {
        mWorkspacePending = false;
        return mWall.goTo(page, animate);
    }

    /**
     * To the Workspace, as Home's swipe past its last page goes there: its window in front, or,
     * with the row empty, its first shell started and the wall brought there when the window
     * arrives. False when there is no Workspace to show and none can be started.
     */
    public boolean goToWorkspace(boolean animate) {
        boolean homeShown = mHost.useAs().showsHomePage();
        boolean hasWindow = mHost.hasWindow();
        // Under Terminal only the Workspace is on the row while it is empty too; going there
        // still starts its first shell, as the Terminal entry does.
        if (mWall.pages().contains(PaneWallPage.TERMINAL) && (homeShown || hasWindow))
            return goTo(PaneWallPage.TERMINAL, animate);
        if (hasWindow || !mHost.canStartWorkspace() || !mHost.startWorkspaceShell())
            return !homeShown && goTo(PaneWallPage.TERMINAL, animate);
        mWorkspacePending = true;
        refreshPages();
        return true;
    }

    /**
     * To the Workspace's window in front, without animation where the caller is already changing
     * everything (entering the surface editor). Nothing happens while the row has no window.
     */
    public void returnToTerminal(boolean animate) {
        goTo(PaneWallPage.TERMINAL, animate);
    }

    /**
     * The default Home page (page 1 unless Edit pages chose another): where the home press lands
     * while the launcher is in front.
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
        showHomePage(Math.max(0, mHost.homePressPage()));
        goTo(PaneWallPage.WIDGETS, animate);
    }

    /** Whether the wall's pages name the Terminal page as home ({@link WallPages#setHome}). */
    private boolean homeIsTerminalPage() {
        WallPages pages = mHost.wallPages();
        WallPages.Page home = pages == null ? null : pages.home();
        return home != null && home.kind == WallPages.PageKind.TERMINAL;
    }

    /**
     * The last Home page, the one beside the seam: where the wall goes when the last window of
     * the row closes.
     */
    public void goToLastHomePage(boolean animate) {
        if (!mHost.useAs().showsHomePage()) {
            goTo(PaneWallPage.TERMINAL, animate);
            return;
        }
        if (!mDefaultOrder && mSlots != null) {
            // The bound run's page beside the Terminal page, on whichever side it stands.
            int slot = boundSlot();
            int terminal = PaneWallPolicy.firstSlotOf(mSlots, PaneWallPage.TERMINAL);
            int side = terminal < 0 || terminal > slot ? 1 : -1;
            mHost.showInnerPage(PaneWallPage.WIDGETS, runPage(slot,
                WallRow.arrivalPage(PaneWallPolicy.runLength(mSlots, slot), side)));
        } else {
            mHost.showInnerPage(PaneWallPage.WIDGETS,
                WallRow.lastHomePage(mHost.innerCount(PaneWallPage.WIDGETS)));
        }
        goTo(PaneWallPage.WIDGETS, animate);
    }

    /**
     * A cold start: Home page 1, or the Workspace when Start in says so — at once if the row has
     * a window already, else as soon as one arrives.
     */
    public void landOnColdStart(@Nullable String startIn) {
        if (WallRow.coldStart(mHost.useAs().startIn(startIn)) == PaneWallPage.TERMINAL) {
            landInWorkspace();
        } else {
            goHome(false);
        }
    }

    private void landInWorkspace() {
        if (mWall.pages().contains(PaneWallPage.TERMINAL)) {
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
        int windows = mWall.pages().contains(PaneWallPage.TERMINAL)
            ? mHost.innerCount(PaneWallPage.TERMINAL) : 0;
        WallRow.Position position = WallRow.restore(half, index,
            mHost.innerCount(PaneWallPage.WIDGETS), windows);
        if (WallRow.HALF_WORKSPACE.equals(half) && windows <= 0) {
            landInWorkspace();
            return true;
        }
        if (position.kind == PaneWallPage.WIDGETS) {
            // wall_index is the global Home-page index: the run that holds it is bound first.
            showHomePage(position.index);
            goTo(PaneWallPage.WIDGETS, false);
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
                && mWall.pages().contains(PaneWallPage.WIDGETS)) {
                mWall.goTo(PaneWallPage.WIDGETS, true);
            }
            return;
        }
        refreshPages();
    }

    @Override
    public void onWallPageSettled(@NonNull PaneWallPage page) {
        // The swipe sprang back to Home: the empty Workspace's page leaves the row again.
        if (mSeamIntoEmpty && page == PaneWallPage.WIDGETS) {
            mSeamIntoEmpty = false;
            mSeamShellStarted = false;
            refreshPages();
        }
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
     * The row is one line: past the last Home page comes the first window. The Workspace's view
     * shows one window at a time, so a drag that may bring it in from the side turns it to the
     * window beside the seam first, while it is still off screen.
     */
    @Override
    public void onWallDragBegin(@NonNull PaneWallPage page) {
        mWorkspacePending = false;
        if (!mDefaultOrder && mSlots != null) {
            onSlotDragBegin(page);
            return;
        }
        // Off the first window the drag may bring Home in from the left: on its last page, the
        // one beside the seam, whatever page it was left on (a Home page added while away, say).
        if (page == PaneWallPage.TERMINAL && mHost.innerIndex(PaneWallPage.TERMINAL) == 0) {
            int last = WallRow.lastHomePage(mHost.innerCount(PaneWallPage.WIDGETS));
            if (mHost.innerIndex(PaneWallPage.WIDGETS) != last) {
                mHost.showInnerPage(PaneWallPage.WIDGETS, last);
            }
        }
        int windows = mHost.innerCount(PaneWallPage.TERMINAL);
        if (windows <= 0 || !mWall.pages().contains(PaneWallPage.TERMINAL)) return;
        if (page == PaneWallPage.WIDGETS) {
            mHost.showInnerPage(PaneWallPage.TERMINAL, 0);
        }
    }

    /**
     * {@link #onWallDragBegin} for any order: off Home the Workspace waits on the window beside
     * the Home run, its first when it stands on the run's right, its last on the left. Off the
     * Terminal page the Home frame is posed by {@link #onWallDragHeading}, once the drag's way
     * is known.
     */
    private void onSlotDragBegin(@NonNull PaneWallPage page) {
        if (page != PaneWallPage.WIDGETS) return;
        java.util.List<PaneWallPage> pages = mWall.pages();
        int windows = mHost.innerCount(PaneWallPage.TERMINAL);
        if (windows <= 0 || !pages.contains(PaneWallPage.TERMINAL)) return;
        boolean right = PaneWallPolicy.relativePosition(pages, PaneWallPage.WIDGETS,
            PaneWallPage.TERMINAL) > 0;
        mHost.showInnerPage(PaneWallPage.TERMINAL, right ? 0 : windows - 1);
    }

    /**
     * A drag off the Terminal page heading for a Home run: the frame is bound to that run and
     * turned to its page beside the Terminal page, before the frame moves. With a Home run on
     * both sides a reversal re-binds it to the other one, under the finger.
     */
    @Override
    public void onWallDragHeading(@NonNull PaneWallPage page, int steps) {
        java.util.List<WallPages.Slot> slots = mSlots;
        if (slots == null || page == PaneWallPage.WIDGETS) return;
        // A step to the next window stays inside the Workspace.
        if (page == PaneWallPage.TERMINAL && hasInnerPage(PaneWallPage.TERMINAL, steps)) return;
        int here = currentSlot(slots);
        int target = here + steps;
        if (here < 0 || target < 0 || target >= slots.size()) return;
        if (slots.get(target).kind == WallPages.PageKind.DISPLAY) {
            // The display view goes with the drag to the Display page it heads for (A3b). A
            // Display page next to another is not re-bound from it: the window leaves the
            // second out, so the wall does not slide from one Display page to the next.
            int id = slots.get(target).id();
            if (slots.get(here).kind != WallPages.PageKind.DISPLAY
                && id != mBoundDisplayPageId) {
                mDisplayPageId = id;
                rebuild();
            }
            return;
        }
        if (mDefaultOrder || slots.get(target).kind != WallPages.PageKind.HOME) return;
        int was = boundSlot();
        if (was != target) {
            mHomeRunId = slots.get(target).id();
            if (com.termux.BuildConfig.DEBUG) {
                android.util.Log.d(SLOTS_TAG, "uptime=" + android.os.SystemClock.uptimeMillis()
                    + " rebind slot " + was + "->" + target + " steps=" + steps
                    + " homePage=" + mHost.innerIndex(PaneWallPage.WIDGETS) + " "
                    + describeState(), new Throwable("WallSlots caller"));
            }
            rebuild();
        }
        // Arriving from the Terminal page on the run's right is its last page; on its left, its first.
        int arrival = runPage(target, WallRow.arrivalPage(
            PaneWallPolicy.runLength(slots, target), -steps));
        if (mHost.innerIndex(PaneWallPage.WIDGETS) != arrival) {
            mHost.showInnerPage(PaneWallPage.WIDGETS, arrival);
        }
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
