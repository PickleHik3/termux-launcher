package com.termux.app.tour;

import android.graphics.Rect;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;
import com.termux.app.AzScrubRowView;
import com.termux.app.chrome.CornerZones;
import com.termux.app.terminal.TerminalActionDispatcher;
import com.termux.app.terminal.TerminalWindowBar;
import com.termux.app.terminal.io.TermuxTerminalExtraKeys;
import com.termux.shared.termux.extrakeys.ExtraKeysView;

/**
 * {@link TourTargets} over the live chrome, measured in the overlay's own space every time it is
 * asked.
 *
 * <p>The views are looked up through a one-method finder rather than held, because the chrome the
 * run points at is re-inflated and re-parented under it — a rotation, a place change and the
 * surface editor all replace the status bar the tour is glowing, the A-Z row moves between three
 * hosts, and the dock becomes a rail in landscape.
 *
 * <p>Null is a normal answer, and most of this class is the ways a control legitimately is not
 * there: the plus and the chips do not exist on the landscape rail, the split key is only on the
 * page of the extra keys row the user has configured it onto, and the space bar is gone whenever
 * the keyboard is down. The overlay draws the card without a glow rather than pointing somewhere
 * wrong.
 */
public final class TourViewTargets implements TourTargets {

    /** The extra key the split card points at, as the keys row stores it. */
    private static final String SPLIT_KEY_NAME =
        TermuxTerminalExtraKeys.LAUNCHER_TOOL_KEY_PREFIX + TerminalActionDispatcher.TOOL_PANE_SPLIT;

    /** The seams the tour needs into the activity's view tree. */
    public interface ViewFinder {
        @Nullable
        View findTourView(int viewId);

        /**
         * The in-app keyboard's space bar, on screen, or false when there is no keyboard up. It
         * is the one control the chrome measures per key rather than laying out as a view of its
         * own, so it cannot be answered with a view id.
         */
        boolean findTourSpaceBarRect(@NonNull Rect outOnScreen);
    }

    @NonNull private final ViewFinder mFinder;
    @NonNull private final View mOverlay;
    private final int[] mLocation = new int[2];

    public TourViewTargets(@NonNull ViewFinder finder, @NonNull View overlay) {
        mFinder = finder;
        mOverlay = overlay;
    }

    @Override
    @Nullable
    public Rect rectFor(@NonNull String targetId) {
        switch (targetId) {
            case STATUS_BAR:
                return rectInOverlay(mFinder.findTourView(R.id.terminal_window_bar_host));
            case PLUS_BUTTON:
                return rectInOverlay(windowBarChild(true));
            case WINDOW_CHIP:
                return rectInOverlay(windowBarChild(false));
            case SPLIT_KEY:
                return rectInOverlay(splitKeyView());
            case PANE_CORNER:
                return paneCornerRect();
            // Both dock styles are the same view; landscape swaps it for the rail.
            case DOCK:
                return rectInOverlay(firstShown(R.id.apps_bar_viewpager, R.id.dock_rail_scroll));
            // The row lives in the dock, above the content or down a side column, one at a time.
            case AZ_ROW:
                return rectInOverlay(azRowView());
            case SPACE_BAR:
                return spaceBarRect();
            default:
                return null;
        }
    }

    /**
     * The plus at the end of the window row, or the chip that is current. Null on the landscape
     * rail, which shows the windows as a column and offers no plus at all.
     */
    @Nullable
    private View windowBarChild(boolean plus) {
        View bar = mFinder.findTourView(R.id.terminal_window_bar);
        if (!(bar instanceof TerminalWindowBar) || !isOnScreen(bar)) return null;
        TerminalWindowBar windowBar = (TerminalWindowBar) bar;
        return plus ? windowBar.createWindowButtonView() : windowBar.selectedTabView();
    }

    /**
     * The split key on whichever page of the extra keys row is up. Null when the row is hidden or
     * when the user has taken that key off their layout, both of which are ordinary.
     */
    @Nullable
    private View splitKeyView() {
        View pager = mFinder.findTourView(R.id.terminal_toolbar_view_pager);
        if (!isOnScreen(pager)) return null;
        return splitKeyIn(pager);
    }

    @Nullable
    private View splitKeyIn(@Nullable View view) {
        if (!isOnScreen(view)) return null;
        if (view instanceof ExtraKeysView) {
            View key = ((ExtraKeysView) view).buttonForKey(SPLIT_KEY_NAME);
            return isOnScreen(key) ? key : null;
        }
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            View found = splitKeyIn(group.getChildAt(i));
            if (found != null) return found;
        }
        return null;
    }

    /**
     * A corner of the terminal pane, and the terminal's rather than the display's, because the
     * display may well be switched off. The square is the one the panes themselves answer touches
     * in, clamped the same way, so the glow is the hit area and not a guess at it.
     */
    @Nullable
    private Rect paneCornerRect() {
        Rect pane = rectInOverlay(mFinder.findTourView(R.id.terminal_view));
        if (pane == null) return null;
        float density = mOverlay.getResources().getDisplayMetrics().density;
        int size = Math.round(CornerZones.clampSize(CornerZones.sizePx(density),
            pane.width(), pane.height()));
        if (size <= 0) return null;
        return new Rect(pane.left, pane.top, pane.left + size, pane.top + size);
    }

    /** The A-Z row wherever it is installed, or null when the user has switched it off. */
    @Nullable
    private View azRowView() {
        View host = firstShown(R.id.place_az_bar_top, R.id.place_az_bar_column,
            R.id.apps_bar_az_row);
        if (host instanceof AzScrubRowView) return host;
        View row = firstDescendantOfType(host);
        return row != null ? row : host;
    }

    @Nullable
    private static View firstDescendantOfType(@Nullable View view) {
        if (view instanceof AzScrubRowView) return view;
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            View found = firstDescendantOfType(group.getChildAt(i));
            if (found != null) return found;
        }
        return null;
    }

    /** The space bar, converted out of screen coordinates into the overlay's. */
    @Nullable
    private Rect spaceBarRect() {
        Rect onScreen = new Rect();
        if (!mFinder.findTourSpaceBarRect(onScreen) || onScreen.isEmpty()) return null;
        if (mOverlay.getWidth() <= 0 || mOverlay.getHeight() <= 0) return null;
        mOverlay.getLocationOnScreen(mLocation);
        onScreen.offset(-mLocation[0], -mLocation[1]);
        return onScreen;
    }

    /** The first of these ids that is actually on screen, or null when none of them is. */
    @Nullable
    private View firstShown(int... viewIds) {
        for (int viewId : viewIds) {
            View view = mFinder.findTourView(viewId);
            if (isOnScreen(view)) return view;
        }
        return null;
    }

    /**
     * Whether a control is really in front of the user: {@link View#isShown()} rather than its own
     * visibility flag, because a hidden ancestor is how most of these disappear — a place change,
     * the keyboard going down, the landscape rail taking the dock's place.
     */
    private static boolean isOnScreen(@Nullable View view) {
        return view != null && view.isShown() && view.getWidth() > 0 && view.getHeight() > 0;
    }

    /** A view's bounds in the overlay's space, or null while it is not on screen. */
    @Nullable
    private Rect rectInOverlay(@Nullable View view) {
        if (!isOnScreen(view) || mOverlay.getWidth() <= 0 || mOverlay.getHeight() <= 0)
            return null;
        mOverlay.getLocationInWindow(mLocation);
        int overlayLeft = mLocation[0];
        int overlayTop = mLocation[1];
        view.getLocationInWindow(mLocation);
        int left = mLocation[0] - overlayLeft;
        int top = mLocation[1] - overlayTop;
        return new Rect(left, top, left + view.getWidth(), top + view.getHeight());
    }
}
