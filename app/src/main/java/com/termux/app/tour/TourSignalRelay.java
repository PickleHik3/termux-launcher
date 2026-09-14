package com.termux.app.tour;

/**
 * Turns the chrome's own state changes into tour signals.
 *
 * <p>It is deliberately told plain values rather than handed the controllers: the launcher's
 * places and its status bar each already have exactly one listener slot, both taken, so the
 * adapters live at the call sites and only the meaning arrives here. That also makes "changed,
 * then came back" and "expanded, then collapsed again" testable without a phone.
 *
 * <p>Both inputs are edge-triggered. The status bar is re-applied on every place change and on
 * every rotation with the value it already had, and a card cleared by a state the user never
 * touched is the whole failure mode this guards against.
 */
public final class TourSignalRelay implements TourSignals {

    private Listener mListener;
    private String mHomePlace;
    private Boolean mStatusBarCollapsed;
    private Integer mWindowCount;
    private String mSelectedWindow;
    private Boolean mDrawerOpen;

    @Override
    public void setTourSignalListener(Listener listener) {
        mListener = listener;
    }

    /**
     * The place the run started on. Everything else is "changed"; coming back to this one is
     * "returned", which is what the second half of the first card waits for.
     */
    public void setHomePlace(String placeId) {
        mHomePlace = placeId;
    }

    /** The current place, when the status bar has settled on it. */
    public void onPlaceSettled(String placeId) {
        if (placeId == null) return;
        if (mHomePlace == null) {
            mHomePlace = placeId;
            return;
        }
        emit(mHomePlace.equals(placeId) ? PLACE_RETURNED : PLACE_CHANGED);
    }

    /** The status bar's resting state, once it has settled there. */
    public void onStatusBarCollapsedSettled(boolean collapsed) {
        if (mStatusBarCollapsed != null && mStatusBarCollapsed == collapsed) return;
        boolean first = mStatusBarCollapsed == null;
        mStatusBarCollapsed = collapsed;
        if (first) return;
        emit(collapsed ? STATUS_BAR_COLLAPSED : STATUS_BAR_EXPANDED);
    }

    /**
     * How many windows the top row is showing, whenever it has just been rebuilt. One more than
     * last time is the + button; one fewer is the chip's ×. The row is rebuilt on a rename, a
     * theme change and a rotation too, so a count that did not move says nothing.
     *
     * @param count -1 while the row is standing for something other than the terminal's windows —
     *     the display's apps, or the bare row the Widgets place shows. The count it carries there
     *     has nothing to do with windows, and the last real one is kept so that coming back to the
     *     terminal is not an open or a close.
     */
    public void onWindowCountSettled(int count) {
        if (count < 0) return;
        Integer previous = mWindowCount;
        mWindowCount = count;
        if (previous == null || previous == count) return;
        emit(count > previous ? WINDOW_OPENED : WINDOW_CLOSED);
    }

    /**
     * A window chip was tapped. Unlike the count above this is already the user's own edge — the
     * status bar reports a chip only from its tap listener, and never on a rebuild — so every call
     * is the gesture the card is asking for, including the first one of the run and a tap on the
     * chip that was already current, which is how the × is revealed.
     */
    public void onWindowSelected(String windowId) {
        if (windowId == null) return;
        mSelectedWindow = windowId;
        emit(WINDOW_CHIP_SELECTED);
    }

    /**
     * The drawer's resting state, once it has settled there.
     *
     * @param userDriven false when the launcher put the plane away itself — HOME, a rotation, a
     *     preference reload — which is a close the user never performed and must not clear the
     *     card that is asking them to perform it.
     */
    public void onDrawerOpenSettled(boolean open, boolean userDriven) {
        if (mDrawerOpen != null && mDrawerOpen == open) return;
        boolean first = mDrawerOpen == null;
        mDrawerOpen = open;
        if (first || !userDriven) return;
        emit(open ? DRAWER_OPENED : DRAWER_CLOSED);
    }

    /**
     * A split was asked for, however it was asked for. An action dispatch is already the one edge
     * — it happens when the user does it and never on a rebuild — so there is nothing to compare
     * against, unlike the states above.
     */
    public void onPaneSplit() {
        emit(PANE_SPLIT);
    }

    /** A pane's corner menu was raised. */
    public void onPaneCornerMenuOpened() {
        emit(PANE_CORNER_MENU);
    }

    /**
     * A pane's corner menu went away. The pane view dismisses its controls from one place, for
     * every way out of them — a tap anywhere else, a close, the surface editor — so like the
     * raise above this is already the user's own edge and has nothing to compare against.
     */
    public void onPaneControlsDismissed() {
        emit(PANE_CONTROLS_DISMISSED);
    }

    /** An app was launched by the A–Z row's scrub, rather than by a tap anywhere else. */
    public void onAppLaunchedFromScrub() {
        emit(APP_LAUNCHED_FROM_SCRUB);
    }

    /**
     * The command palette came up. The space bar's swipe up is one of four ways into it and
     * nothing downstream carries which one was used, so any open clears the card.
     */
    public void onPaletteOpened() {
        emit(PALETTE_OPENED);
    }

    private void emit(String signalId) {
        if (mListener != null) mListener.onTourSignal(signalId);
    }
}
