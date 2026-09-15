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
 * wrong, and {@link #lastMissReason()} says which of those it was.
 */
public final class TourViewTargets implements TourTargets {

    /** The extra key the split card points at, as the keys row stores it. */
    private static final String SPLIT_KEY_NAME =
        TermuxTerminalExtraKeys.LAUNCHER_TOOL_KEY_PREFIX + TerminalActionDispatcher.TOOL_PANE_SPLIT;

    /** The extra key that shows and hides the keyboard, as the keys row names it. */
    private static final String KEYBOARD_TOGGLE_KEY_NAME = "KEYBOARD";

    /** The seams the tour needs into the activity's view tree. */
    public interface ViewFinder {
        @Nullable
        View findTourView(int viewId);

        /**
         * One key of the in-app keyboard, on screen, or false when the keyboard is down or that
         * layout does not carry the key. Keys are the one thing the chrome measures per cap
         * rather than laying out as views of their own, so they cannot be answered with a view id.
         *
         * @param keyName the key as a layout file names it: "ctrl", "alt", "shift", "enter",
         *     "space", or a letter
         */
        boolean findTourKeyRect(@NonNull String keyName, @NonNull Rect outOnScreen);

        /**
         * The ? on the corner tab that is up, in screen coordinates, or false when no tab is
         * showing. The tab draws its buttons rather than laying them out as views, so only the
         * chrome that drew it can say where that one is.
         */
        default boolean findTourHelpButtonRect(@NonNull Rect outOnScreen) {
            return false;
        }
    }

    @NonNull private final ViewFinder mFinder;
    @NonNull private final View mOverlay;
    private final int[] mLocation = new int[2];

    @NonNull private String mMissReason = "none";

    public TourViewTargets(@NonNull ViewFinder finder, @NonNull View overlay) {
        mFinder = finder;
        mOverlay = overlay;
    }

    @Override
    @NonNull
    public String lastMissReason() {
        return mMissReason;
    }

    @Override
    @Nullable
    public Rect rectFor(@NonNull String targetId) {
        mMissReason = "none";
        if (mOverlay.getWidth() <= 0 || mOverlay.getHeight() <= 0)
            return miss("the overlay has not been laid out yet");
        switch (targetId) {
            case STATUS_BAR:
                return rectInOverlay(mFinder.findTourView(R.id.terminal_window_bar_host),
                    "the status bar is not on screen");
            case PLUS_BUTTON:
                return rectInOverlay(windowBarChild(true),
                    "this place offers no + on its window row");
            case WINDOW_CHIP:
                return rectInOverlay(windowBarChild(false),
                    "no window chip is current on this place's row");
            case WINDOW_CLOSE:
                return rectInOverlay(closeButtonView(),
                    "no chip is offering its x right now");
            case SPLIT_KEY:
                return rectInOverlay(extraKeyView(SPLIT_KEY_NAME),
                    "the extra keys row is down or is not carrying the split key");
            case KEYBOARD_TOGGLE_KEY:
                return rectInOverlay(extraKeyView(KEYBOARD_TOGGLE_KEY_NAME),
                    "the extra keys row is down or is not carrying the keyboard key");
            // Drawn by the pane's own corner tab rather than laid out as a view, so the chrome
            // measures it. Before the tab is up there is no ? to glow, and the card glows the
            // corner the tab comes out of instead — which is what its own sentence asks for.
            case HELP_BUTTON:
                Rect help = helpButtonRect();
                return help != null ? help : paneCornerRect();
            case PANE_CORNER:
                return paneCornerRect();
            // Both dock styles are the same view; landscape swaps it for the rail.
            case DOCK:
                return rectInOverlay(firstShown(R.id.apps_bar_viewpager, R.id.dock_rail_scroll),
                    "neither the dock nor the landscape rail is on screen");
            // The row lives in the dock, above the content or down a side column, one at a time.
            case AZ_ROW:
                return rectInOverlay(azRowView(), "the A-Z row is switched off or not on screen");
            case NONE:
                return miss("this card points at nothing");
            default:
                String keyName = keyboardKeyName(targetId);
                if (keyName != null) return keyRect(keyName);
                return miss("no target is registered under \"" + targetId + "\"");
        }
    }

    @Nullable
    private Rect miss(@NonNull String reason) {
        mMissReason = reason;
        return null;
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
     * The x the selected chip reveals, which exists only between the tap that asks for it and the
     * few seconds later when the bar takes it away again. Null the rest of the time, which is most
     * of the time, and the card that points at it simply shows without a glow.
     */
    @Nullable
    private View closeButtonView() {
        View bar = mFinder.findTourView(R.id.terminal_window_bar);
        if (!(bar instanceof TerminalWindowBar) || !isOnScreen(bar)) return null;
        return ((TerminalWindowBar) bar).closeButtonView();
    }

    /**
     * One key of the extra keys row, on whichever page of it is up. Null when the row is hidden or
     * when the user has taken that key off their layout, both of which are ordinary.
     */
    @Nullable
    private View extraKeyView(@NonNull String keyName) {
        View pager = mFinder.findTourView(R.id.terminal_toolbar_view_pager);
        if (!isOnScreen(pager)) return null;
        return extraKeyIn(pager, keyName);
    }

    @Nullable
    private View extraKeyIn(@Nullable View view, @NonNull String keyName) {
        if (!isOnScreen(view)) return null;
        if (view instanceof ExtraKeysView) {
            View key = ((ExtraKeysView) view).buttonForKey(keyName);
            return isOnScreen(key) ? key : null;
        }
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            View found = extraKeyIn(group.getChildAt(i), keyName);
            if (found != null) return found;
        }
        return null;
    }

    /** The ? of the corner tab that is up, converted out of screen coordinates into the overlay's. */
    @Nullable
    private Rect helpButtonRect() {
        Rect onScreen = new Rect();
        if (!mFinder.findTourHelpButtonRect(onScreen) || onScreen.isEmpty()) return null;
        mOverlay.getLocationOnScreen(mLocation);
        onScreen.offset(-mLocation[0], -mLocation[1]);
        return onScreen;
    }

    /**
     * A corner of the terminal pane, and the terminal's rather than the display's, because the
     * display may well be switched off. The square is the one the panes themselves answer touches
     * in — the bigger {@link CornerZones#PANE_SIZE_DP} a terminal pane keeps, because its corner is
     * held rather than tapped — clamped the same way, so the glow is the hit area and not a guess
     * at it.
     */
    @Nullable
    private Rect paneCornerRect() {
        Rect pane = rectInOverlay(mFinder.findTourView(R.id.terminal_view),
            "no terminal pane is on screen");
        if (pane == null) return null;
        float density = mOverlay.getResources().getDisplayMetrics().density;
        int size = Math.round(CornerZones.clampSize(CornerZones.paneSizePx(density),
            pane.width(), pane.height()));
        if (size <= 0) return miss("the pane is too small to have a corner zone");
        return new Rect(pane.left, pane.top, pane.left + size, pane.top + size);
    }

    /**
     * The A-Z row wherever it is installed, or null when the user has switched it off.
     *
     * <p>The row is one view that moves between three hosts, so a host that is merely on screen is
     * not the answer — the dock's host stays laid out while the row is living above the content.
     * The host that actually contains the row wins, and an empty host is only ever a fallback.
     */
    @Nullable
    private View azRowView() {
        int[] hostIds = {R.id.place_az_bar_top, R.id.place_az_bar_column, R.id.apps_bar_az_row};
        View fallback = null;
        for (int hostId : hostIds) {
            View host = mFinder.findTourView(hostId);
            if (!isOnScreen(host)) continue;
            if (host instanceof AzScrubRowView) return host;
            View row = firstDescendantOfType(host);
            if (row != null) return row;
            if (fallback == null) fallback = host;
        }
        return fallback;
    }

    @Nullable
    private static View firstDescendantOfType(@Nullable View view) {
        if (!isOnScreen(view)) return null;
        if (view instanceof AzScrubRowView) return view;
        if (!(view instanceof ViewGroup)) return null;
        ViewGroup group = (ViewGroup) view;
        for (int i = 0; i < group.getChildCount(); i++) {
            View found = firstDescendantOfType(group.getChildAt(i));
            if (found != null) return found;
        }
        return null;
    }

    /** The keyboard key each key target stands for, as the layout files name them. */
    @Nullable
    private static String keyboardKeyName(@NonNull String targetId) {
        switch (targetId) {
            case CTRL_KEY: return "ctrl";
            case ALT_KEY: return "alt";
            case SHIFT_KEY: return "shift";
            case ENTER_KEY: return "enter";
            case C_KEY: return "c";
            case SPACE_BAR: return "space";
            default: return null;
        }
    }

    /** One keyboard key, converted out of screen coordinates into the overlay's. */
    @Nullable
    private Rect keyRect(@NonNull String keyName) {
        Rect onScreen = new Rect();
        if (!mFinder.findTourKeyRect(keyName, onScreen) || onScreen.isEmpty())
            return miss("the in-app keyboard is down or carries no \"" + keyName + "\" key");
        // Screen coordinates on both sides: the keyboard measures its caps against the display,
        // not against this window, so the overlay has to be located the same way to subtract it.
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
    private Rect rectInOverlay(@Nullable View view, @NonNull String missReason) {
        if (!isOnScreen(view)) return miss(missReason);
        // Window coordinates on both sides: the offset between them is what is wanted, and taking
        // it in the window's space keeps the status bar inset out of the subtraction entirely.
        mOverlay.getLocationInWindow(mLocation);
        int overlayLeft = mLocation[0];
        int overlayTop = mLocation[1];
        view.getLocationInWindow(mLocation);
        int left = mLocation[0] - overlayLeft;
        int top = mLocation[1] - overlayTop;
        return new Rect(left, top, left + view.getWidth(), top + view.getHeight());
    }
}
