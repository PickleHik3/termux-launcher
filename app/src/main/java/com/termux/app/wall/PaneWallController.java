package com.termux.app.wall;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.terminal.PaneSurfaceStyle;

/**
 * Owns the wall: its row of pages (Home pages, then the Workspace's windows, the display window
 * among them while a display is up; ADR 0008), which page is in front, and every way in and out
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
         * Whether the current session's row has a window — a terminal window, or the display
         * window — so the Workspace is on the row. Home is on it unless {@link #useAs} is
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
         * How many pages of its own kind {@code page} holds: the Home pages, or the windows of
         * the current session's row, the display window's included.
         */
        default int innerCount(@NonNull PaneWallPage page) { return 1; }
        /** Which of them is in front, counting from 0. */
        default int innerIndex(@NonNull PaneWallPage page) { return 0; }
        /** Turn {@code page} to its {@code index}-th page at once (Home page n). */
        default void showInnerPage(@NonNull PaneWallPage page, int index) { }
        /**
         * The Home page the Home press lands on, counting from 0: Edit pages' default page, page
         * 1 until one is chosen.
         */
        default int homePressPage() { return 0; }
        /**
         * Start in as it stands ({@link StartInPolicy#effective}), read fresh on every home press:
         * the page that is Home, which the press lands on.
         */
        @NonNull default String startIn() { return WallRow.START_IN_HOME; }
        /**
         * The row place, counting from 0, of the window Start in {@code startIn} names as Home:
         * the first terminal window, or the display window. -1 while the row has no such window;
         * the press then lands on the Workspace as it stands.
         */
        default int homeWindowRow(@NonNull String startIn) { return -1; }
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
        if (hasWindow) { mSeamIntoEmpty = false; mSeamShellStarted = false; }
        mWall.setPages(mHost.useAs().rowPages(hasWindow || mSeamIntoEmpty));
        PaneWallPage after = mWall.currentPage();
        // Home taken off the row by Use as hands the wall to the Workspace as it stands, the
        // window in front kept; only a page handing to its left turns that page to its last.
        if (after != before && !mWall.pages().contains(before)
            && after != PaneWallPage.TERMINAL) {
            mHost.showInnerPage(after, Math.max(0, mHost.innerCount(after) - 1));
        }
        if (mWorkspacePending && mWall.pages().contains(PaneWallPage.TERMINAL)) {
            mWorkspacePending = false;
            mWall.goTo(PaneWallPage.TERMINAL, false);
        }
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
                        !mHost.hasWindow() && mHost.canStartWorkspace());
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
     * Build the display window's frame on the wall, waiting off screen until the display window
     * is the Workspace's window in front ({@link #setDisplayInFront}). Inflated here rather than in
     * the layout file so an install that never runs a display never builds its surface.
     */
    @Nullable
    public com.termux.app.x11.X11PaneFrame attachDisplayWindow(@NonNull LayoutInflater inflater) {
        if (mDisplayWindow != null) return mDisplayWindow;
        com.termux.app.x11.X11PaneFrame frame = (com.termux.app.x11.X11PaneFrame) inflater.inflate(
            com.termux.R.layout.view_x11_pane, mWall, false);
        mWall.addView(frame);
        mDisplayWindow = frame;
        mWall.setDisplayView(frame);
        applyStyle(mStyle);
        return frame;
    }

    @Nullable
    public com.termux.app.x11.X11PaneFrame displayWindow() {
        return mDisplayWindow;
    }

    /**
     * Take the display window's frame off the wall: the view goes, and the X view's native context
     * goes with it when the view leaves the window. A frame standing in front hands the
     * Workspace's page back to the pane host first, and the row is re-read for a Workspace that
     * held nothing else.
     */
    public void detachDisplayWindow() {
        com.termux.app.x11.X11PaneFrame frame = mDisplayWindow;
        if (frame == null) return;
        mDisplayWindow = null;
        mWall.setDisplayView(null);
        refreshPages();
        mWall.removeView(frame);
    }

    /**
     * Whether the display window is the Workspace's window in front: its frame then stands as the
     * Workspace's page. At once, with no travel; {@link #swapDisplayInFront} travels.
     */
    public void setDisplayInFront(boolean front) {
        mWall.setDisplayInFront(front);
    }

    /**
     * The same as a window switch: the arriving window slides in from {@code fromPx} and the one
     * that was in front goes off beside it ({@link PaneWallLayout#swapDisplayInFront}).
     *
     * @param fromWindow the row place the arrival left, for the wallpaper's pan
     * @param onEnd runs however the travel ends, at once where none could start
     * @return whether the front changed
     */
    public boolean swapDisplayInFront(boolean front, float fromPx, int fromWindow,
                                      long durationMs,
                                      @Nullable android.view.animation.Interpolator interpolator,
                                      @Nullable Runnable onEnd) {
        mNudgeFromWindow = fromWindow;
        return mWall.swapDisplayInFront(front, fromPx, durationMs, interpolator, onEnd);
    }

    public boolean isDisplayInFront() {
        return mWall.isDisplayInFront();
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

    /** Whether a terminal window is the place the user is on, at rest: not the display window. */
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
        boolean record = mWall.currentPage() == PaneWallPage.TERMINAL && !mWall.isDisplayInFront();
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
            + " displayInFront=" + mWall.isDisplayInFront()
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
        mNudgeFromWindow = fromWindow;
        mWall.nudgePage(PaneWallPage.TERMINAL, fromPx, durationMs, interpolator, onEnd);
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
                    : WallParallax.rowIndex(PaneWallPage.TERMINAL, mNudgeFromWindow, homes, windows);
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
        return WallParallax.rowIndex(kind, mHost.innerIndex(kind), homes, windows);
    }

    private int rowHomeCount() {
        return Math.max(1, mHost.innerCount(PaneWallPage.WIDGETS));
    }

    private int rowWindowCount(@NonNull java.util.List<PaneWallPage> pages) {
        if (!pages.contains(PaneWallPage.TERMINAL)) return 0;
        return Math.max(1, mHost.innerCount(PaneWallPage.TERMINAL));
    }

    /**
     * Navigate by the {@code page=} argument of {@code wall.go}: a name, or left/right. Home named
     * outright (the extra keys' Home key, {@code wall.widgets}) opens on the Home page a home
     * press would ({@link Host#homePressPage}); a neighbour keeps the page it was left on.
     */
    public boolean goTo(@Nullable String name) {
        PaneWallPage page = PaneWallPolicy.parsePage(mWall.pages(), mWall.currentPage(), name);
        if (page == null) return false;
        mWorkspacePending = false;
        if (page == PaneWallPage.WIDGETS && PaneWallPolicy.namesHome(name))
            mHost.showInnerPage(PaneWallPage.WIDGETS, Math.max(0, mHost.homePressPage()));
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
     * Where the home press lands while the launcher is in front: the page Start in names as Home
     * ({@link Host#startIn}), the same page a cold start opens on. Home: the default Home page
     * (page 1 unless Edit pages chose another). The Workspace or Display, and all of Terminal
     * only: the first terminal window or the display window ({@link Host#homeWindowRow}).
     */
    public void goHome(boolean animate) {
        String startIn = mHost.useAs().startIn(mHost.startIn());
        if (WallRow.coldStart(startIn) == PaneWallPage.TERMINAL) {
            int row = mHost.homeWindowRow(startIn);
            if (row >= 0) mHost.showInnerPage(PaneWallPage.TERMINAL, row);
            goToWorkspace(animate);
            return;
        }
        mHost.showInnerPage(PaneWallPage.WIDGETS, Math.max(0, mHost.homePressPage()));
        goTo(PaneWallPage.WIDGETS, animate);
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
        mHost.showInnerPage(PaneWallPage.WIDGETS,
            WallRow.lastHomePage(mHost.innerCount(PaneWallPage.WIDGETS)));
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
            mHost.showInnerPage(PaneWallPage.WIDGETS, position.index);
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
        if (mSeamShellStarted || !WallRow.seamStartsShell(mSeamIntoEmpty, page)) return;
        mSeamShellStarted = true;
        // The release committed to the empty Workspace: its first shell starts now, while the
        // wall slides there. Its window takes the page the swipe brought in.
        if (!mHost.startWorkspaceShell()) {
            mSeamIntoEmpty = false;
            refreshPages();
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
        return page == PaneWallPage.TERMINAL && !mWall.isDisplayInFront()
            && mHost.bodySwipePagesWall(rawX, rawY);
    }

    /**
     * A terminal window's last row pages the row by a clearly sideways swipe from the seam band
     * ({@link SeamSwipe}), where the terminal gives the swipe away, whatever the body swipe's
     * setting. Not the display window's picture, which takes every touch.
     */
    @Override
    public boolean isSeamSwipePaging(@NonNull PaneWallPage page, float rawX, float rawY) {
        return page == PaneWallPage.TERMINAL && !mWall.isDisplayInFront()
            && mHost.seamSwipePagesWall(rawX, rawY);
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
