package com.termux.app.wall;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.terminal.PaneSurfaceStyle;

/**
 * Owns the pane wall: which places this install has, which one is showing, and every way in and
 * out of them. The activity hands it the layout and the three answers it cannot work out for
 * itself; nothing else needs to know the wall exists.
 */
public final class PaneWallController implements PaneWallLayout.Listener {

    /** What the wall asks the activity. */
    public interface Host {
        /** Honour the system's reduce-motion setting for the page slide. */
        boolean reducedMotion();
        /** The terminal-only use case: no home surfaces, so no Widgets page. */
        boolean isTerminalOnly();
        /** The widgets feature is switched on. */
        boolean isWidgetsEnabled();
        /** The embedded display is built into this edition and switched on. */
        boolean isDisplayEnabled();
        /** A page came to rest on screen. */
        default void onWallPageSettled(@NonNull PaneWallPage page) { }
        /** The wall committed to a different page, before the slide finishes. */
        default void onWallPageChanged(@NonNull PaneWallPage page) { }
        /**
         * The wall was moved by something other than the finger that was dragging it. Every
         * surface that can drive a drag — the status bar, the window strip — must let go.
         */
        default void onWallDragInterrupted() { }
        /** The wall moved: signed distance from the current page's rest, for the place switch. */
        default void onWallOffsetChanged(float offsetPx) { }
        /** The Terminal page just went fully off screen, or just came back; see
         *  {@link PaneWallLayout.Listener#onTerminalOffScreenChanged}. */
        default void onTerminalOffScreenChanged(boolean offScreen) { }
    }

    /** Saved-instance-state key for the page the wall is showing. */
    public static final String ARG_PAGE = "pane_wall_page";

    @NonNull private final PaneWallLayout mWall;
    @NonNull private final Host mHost;
    @Nullable private WidgetPaneFrame mWidgetsPage;
    @Nullable private com.termux.app.x11.X11PaneFrame mDisplayPage;
    @Nullable private PaneSurfaceStyle mStyle;

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

    /** Re-read the preferences that decide which places exist. */
    public void refreshPages() {
        mWall.setReducedMotion(mHost.reducedMotion());
        mWall.setPages(PaneWallPolicy.availablePages(mHost.isTerminalOnly(),
            mHost.isWidgetsEnabled(), mHost.isDisplayEnabled()));
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
     * Dress every non-terminal page from the surface style. The terminal pages dress themselves
     * through {@code TerminalPaneController}; this is the same pass for the rest of the wall, and
     * it runs on the same triggers — a wallpaper change, a blur change, an editor slider tick.
     */
    public void applyStyle(@Nullable PaneSurfaceStyle style) {
        mStyle = style;
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
        return page != null && mWall.goTo(page, true);
    }

    public boolean goTo(@NonNull PaneWallPage page, boolean animate) {
        return mWall.goTo(page, animate);
    }

    /**
     * Back to the terminal, without animation where the caller is already changing everything —
     * the Home key, and entering the surface editor.
     */
    public void returnToTerminal(boolean animate) {
        mWall.goTo(PaneWallPolicy.homePage(), animate);
    }

    // ---- Dragging, from the status bar ------------------------------------------------------

    /** True while a sideways drag on the status bar has anywhere to take the wall. */
    public boolean canDrag() {
        return mWall.areGesturesEnabled() && mWall.pages().size() > 1;
    }

    /** Take a drag. False when the wall has nowhere to go, leaving the gesture to its owner. */
    public boolean beginDrag() {
        if (!canDrag()) return false;
        mWall.beginDrag();
        return true;
    }

    public void dragTo(float dxPx) {
        mWall.dragTo(dxPx);
    }

    public void endDrag(float velocityPxPerSec) {
        mWall.endDrag(velocityPxPerSec);
    }

    public void cancelDrag() {
        mWall.cancelDrag();
    }

    /** Hold the wall still while another surface owns the gesture. */
    public void setGesturesEnabled(boolean enabled) {
        mWall.setGesturesEnabled(enabled);
    }

    // ---- Saved state -----------------------------------------------------------------------

    /**
     * Activity recreation (rotation, theme) keeps the page. Process death does not: a cold start
     * is always the terminal, because that is the home screen.
     */
    public void onSaveInstanceState(@NonNull Bundle state) {
        state.putString(ARG_PAGE, mWall.currentPage().name());
    }

    public void restoreInstanceState(@Nullable Bundle state) {
        if (state == null) return;
        String name = state.getString(ARG_PAGE);
        if (name == null) return;
        try {
            mWall.goTo(PaneWallPage.valueOf(name), false);
        } catch (IllegalArgumentException ignored) {
            // A page that no longer exists leaves the wall on the terminal.
        }
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

    @Override
    public void onWallDragInterrupted() {
        mHost.onWallDragInterrupted();
    }

    @Override
    public void onWallOffsetChanged(float offsetPx) {
        // The pages' glass is aimed at the wallpaper in screen space and the wall moves them by
        // translation, which never redraws a child: each page re-aims its slab for this frame.
        if (mWidgetsPage != null) mWidgetsPage.onWallMoved();
        if (mDisplayPage != null) mDisplayPage.onWallMoved();
        mHost.onWallOffsetChanged(offsetPx);
    }

    @Override
    public void onTerminalOffScreenChanged(boolean offScreen) {
        mHost.onTerminalOffScreenChanged(offScreen);
    }
}
