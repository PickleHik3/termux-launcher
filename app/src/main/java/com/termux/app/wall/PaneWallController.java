package com.termux.app.wall;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.terminal.PaneSurfaceStyle;

/**
 * Owns the wall: its row of pages (Home pages, then the Workspace's windows, then the display
 * window while a display is up; ADR 0008), which page is in front, and every way in and out of
 * them. The activity hands it the layout and the answers it cannot work out for itself — whether
 * the current session has a window, how many Home pages and windows there are and which is in
 * front — and nothing else needs to know the wall exists.
 */
public final class PaneWallController implements PaneWallLayout.Listener {

    /** What the wall asks the activity. */
    public interface Host {
        /** Honour the system's reduce-motion setting for the page slide. */
        boolean reducedMotion();
        /**
         * Whether the current session has a window, so the Workspace is on the row. Home is on
         * it always; the usage mode is not asked.
         */
        default boolean hasWindow() { return true; }
        /**
         * A display is up, so its window is on the row: running, or asked for and starting.
         * Nothing switches it on (ADR 0008).
         */
        boolean isDisplayUp();
        /**
         * How many pages of its own kind {@code page} holds: the Home pages, the current
         * session's windows; 1 for the Display page.
         */
        default int innerCount(@NonNull PaneWallPage page) { return 1; }
        /** Which of them is in front, counting from 0. */
        default int innerIndex(@NonNull PaneWallPage page) { return 0; }
        /** Turn {@code page} to its {@code index}-th page at once (Home page n). */
        default void showInnerPage(@NonNull PaneWallPage page, int index) { }
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
         * How much of the pages' outlines the slide leaves showing changed
         * ({@link PaneWallPolicy#outlineAlpha}). The wall's own pages take it themselves; the
         * terminal's panes draw their rims through their controller, which the host owns.
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
    @Nullable private com.termux.app.x11.X11PaneFrame mDisplayPage;
    @Nullable private PaneSurfaceStyle mStyle;
    /** How much of the pages' outlines the last frame left showing; 1 at rest. */
    private float mOutlineAlpha = 1f;
    /**
     * The Workspace was asked for — by Start in or by restored state — before the row had a
     * window: the wall goes there as soon as one arrives, unless something else moved it first.
     */
    private boolean mWorkspacePending;

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

    /** The terminal's pane host is the wall's middle page. */
    public void attachTerminalPage(@NonNull View paneHost) {
        mWall.setPageView(PaneWallPage.TERMINAL, paneHost);
    }

    /**
     * Re-read what decides the row: whether there is a window, and whether a display is up. A
     * page that leaves while the wall rests on it hands the wall to the page on its left, turned
     * to its last: the last window when the display goes down, the last Home page when the last
     * window closes.
     */
    public void refreshPages() {
        mWall.setReducedMotion(mHost.reducedMotion());
        PaneWallPage before = mWall.currentPage();
        mWall.setPages(PaneWallPolicy.rowPages(mHost.hasWindow(), mHost.isDisplayUp()));
        PaneWallPage after = mWall.currentPage();
        if (after != before && !mWall.pages().contains(before)) {
            mHost.showInnerPage(after, Math.max(0, mHost.innerCount(after) - 1));
        }
        if (mWorkspacePending && mWall.pages().contains(PaneWallPage.TERMINAL)) {
            mWorkspacePending = false;
            mWall.goTo(PaneWallPage.TERMINAL, false);
        }
    }

    /**
     * The seam out of Home for the widget grid: whether a forward swipe on the last Home page has
     * somewhere to go on the row, and the wall's plain drag to carry it there.
     */
    @NonNull
    public com.termux.app.launcher.widget.WidgetPaneView.Seam homeSeam() {
        return new com.termux.app.launcher.widget.WidgetPaneView.Seam() {
            @Override public boolean canLeaveForward() {
                return mWall.currentPage() == PaneWallPage.WIDGETS && !mWall.isMoving()
                    && mWall.areGesturesEnabled()
                    && PaneWallPolicy.hasNeighbour(mWall.pages(), PaneWallPage.WIDGETS, 1);
            }
            @Override public void begin() {
                mWorkspacePending = false;
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
     * Put the embedded display on the wall as its Display page. Inflated here rather than in the
     * layout file so an install that never turns the display on never builds its surface.
     */
    @Nullable
    public com.termux.app.x11.X11PaneFrame attachDisplayPage(@NonNull LayoutInflater inflater) {
        if (mDisplayPage != null) return mDisplayPage;
        com.termux.app.x11.X11PaneFrame frame = (com.termux.app.x11.X11PaneFrame) inflater.inflate(
            com.termux.R.layout.view_x11_pane, mWall, false);
        mWall.addView(frame);
        mDisplayPage = frame;
        mWall.setPageView(PaneWallPage.DISPLAY, frame);
        applyStyle(mStyle);
        return frame;
    }

    @Nullable
    public com.termux.app.x11.X11PaneFrame displayPage() {
        return mDisplayPage;
    }

    /**
     * Take the Display place off the wall: the page's view goes, and the X view's native context
     * goes with it when the view leaves the window. The wall is told about its places first, so
     * one resting on the Display page hands over to the terminal while the page is still there.
     */
    public void detachDisplayPage() {
        com.termux.app.x11.X11PaneFrame page = mDisplayPage;
        if (page == null) return;
        mDisplayPage = null;
        refreshPages();
        mWall.setPageView(PaneWallPage.DISPLAY, null);
        mWall.removeView(page);
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
        if (mDisplayPage != null) mDisplayPage.applyStyle(style);
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
        boolean pixels = mWall.isPageOnScreen(PaneWallPage.TERMINAL);
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
            + " width=" + mWall.getWidth()
            + " terminalX=" + (terminal == null ? "none" : String.valueOf(terminal.getTranslationX()))
            + " terminalVisibility=" + (terminal == null ? "none" : String.valueOf(terminal.getVisibility()));
    }

    /** The terminal page slides in from {@code fromPx} beside its place; see PaneWallLayout#nudgePage. */
    public void nudgeTerminalPage(float fromPx, long durationMs,
                                  @Nullable android.view.animation.Interpolator interpolator,
                                  @Nullable Runnable onEnd) {
        mWall.nudgePage(PaneWallPage.TERMINAL, fromPx, durationMs, interpolator, onEnd);
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
     * To the Workspace's window in front, without animation where the caller is already changing
     * everything (entering the surface editor). Nothing happens while the row has no window.
     */
    public void returnToTerminal(boolean animate) {
        goTo(PaneWallPage.TERMINAL, animate);
    }

    /** Home page 1: where the home press lands while the launcher is in front. */
    public void goHome(boolean animate) {
        mHost.showInnerPage(PaneWallPage.WIDGETS, 0);
        goTo(PaneWallPage.WIDGETS, animate);
    }

    /**
     * The last Home page, the one beside the seam: where the wall goes when the last window of
     * the row closes.
     */
    public void goToLastHomePage(boolean animate) {
        mHost.showInnerPage(PaneWallPage.WIDGETS,
            WallRow.lastHomePage(mHost.innerCount(PaneWallPage.WIDGETS)));
        goTo(PaneWallPage.WIDGETS, animate);
    }

    /**
     * A cold start: Home page 1, or the Workspace when Start in says so — at once if the row has
     * a window already, else as soon as one arrives.
     */
    public void landOnColdStart(@Nullable String startIn) {
        if (WallRow.coldStart(startIn) == PaneWallPage.TERMINAL) {
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
        return new WallRow.Position(page, page == PaneWallPage.DISPLAY ? 0 : mHost.innerIndex(page));
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
        state.putInt(ARG_INDEX, WallRow.savedIndex(position,
            mHost.innerCount(PaneWallPage.TERMINAL)));
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
            mHost.innerCount(PaneWallPage.WIDGETS), windows,
            mWall.pages().contains(PaneWallPage.DISPLAY));
        if (WallRow.HALF_WORKSPACE.equals(half) && windows <= 0) {
            landInWorkspace();
            return true;
        }
        if (position.kind == PaneWallPage.WIDGETS) {
            mHost.showInnerPage(PaneWallPage.WIDGETS, position.index);
            goTo(PaneWallPage.WIDGETS, false);
        } else if (position.kind == PaneWallPage.TERMINAL) {
            landInWorkspace();
        } else {
            goTo(position.kind, false);
        }
        return true;
    }

    // ---- PaneWallLayout.Listener -----------------------------------------------------------

    @Override
    public void onWallPageChanged(@NonNull PaneWallPage page) {
        mHost.onWallPageChanged(page);
    }

    @Override
    public void onWallPageSettled(@NonNull PaneWallPage page) {
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
     * The row is one line: past the last Home page comes the first window, and before the
     * Display page the last one. The Workspace's view shows one window at a time, so a drag that
     * may bring it in from the side turns it to the window beside the seam first, while it is
     * still off screen.
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
        } else if (page == PaneWallPage.DISPLAY) {
            mHost.showInnerPage(PaneWallPage.TERMINAL, windows - 1);
        }
    }

    @Override
    public boolean isBorderPagingEnabled(@NonNull PaneWallPage page) {
        // A Home page has no border; it pages by a plain swipe anywhere.
        return page != PaneWallPage.WIDGETS;
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
        if (alpha == mOutlineAlpha) return;
        mOutlineAlpha = alpha;
        if (mWidgetsPage != null) mWidgetsPage.setOutlineTravelAlpha(alpha);
        if (mDisplayPage != null) mDisplayPage.setOutlineTravelAlpha(alpha);
        mHost.onWallOutlineAlphaChanged(alpha);
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
        if (mDisplayPage != null) mDisplayPage.onWallMoved();
    }

    @Override
    public boolean isPlankTiltEnabled(@NonNull PaneWallPage page) {
        // Every place, the Display too: its picture is a SurfaceView a rotation leaves flat, so the
        // wall tips it only while a still copy stands in for the surface (SurfacePage), and slides
        // it flat until then.
        return mHost.isPlankTiltEnabled();
    }
}
