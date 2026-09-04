package com.termux.app.wall;

import android.os.Bundle;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

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
    }

    /** Saved-instance-state key for the page the wall is showing. */
    public static final String ARG_PAGE = "pane_wall_page";

    @NonNull private final PaneWallLayout mWall;
    @NonNull private final Host mHost;

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

    @NonNull
    public PaneWallPage currentPage() {
        return mWall.currentPage();
    }

    public boolean isTerminalShowing() {
        return mWall.currentPage() == PaneWallPage.TERMINAL && !mWall.isMoving();
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
}
