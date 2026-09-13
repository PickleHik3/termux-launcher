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
     */
    public void onWindowCountSettled(int count) {
        if (count < 0) return;
        Integer previous = mWindowCount;
        mWindowCount = count;
        if (previous == null || previous == count) return;
        emit(count > previous ? WINDOW_OPENED : WINDOW_CLOSED);
    }

    /**
     * The window the row is showing as current. Only a move to a different one is the user tapping
     * a chip; the row re-applies its selection every time it is rebuilt, including right after the
     * + button made a window current by creating it.
     */
    public void onWindowSelected(String windowId) {
        if (windowId == null) return;
        String previous = mSelectedWindow;
        mSelectedWindow = windowId;
        if (previous == null || previous.equals(windowId)) return;
        emit(WINDOW_CHIP_SELECTED);
    }

    /** The drawer's resting state, once it has settled there. */
    public void onDrawerOpenSettled(boolean open) {
        if (mDrawerOpen != null && mDrawerOpen == open) return;
        boolean first = mDrawerOpen == null;
        mDrawerOpen = open;
        if (first) return;
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
