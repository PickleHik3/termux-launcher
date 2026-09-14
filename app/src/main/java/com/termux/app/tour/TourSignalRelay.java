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
    private String mPlace;
    private Boolean mStatusBarCollapsed;
    private Integer mWindowCount;
    private String mSelectedWindow;
    private Boolean mDrawerOpen;
    private Integer mSessionCount;
    private String mCurrentSession;
    private String mActiveWindow;
    /** Whether the palette is up as far as the run knows; null until it has ever seen it open. */
    private Boolean mPaletteOpen;
    /** The session and window the keyboard chapter began in, and so the ones it must end in. */
    private String mChapterHomeSession;
    private String mChapterHomeWindow;

    @Override
    public void setTourSignalListener(Listener listener) {
        mListener = listener;
    }

    /**
     * The place the run is taught on. Everything else is "changed"; coming back to this one is
     * "returned", which is what the second half of the first card waits for. Pinned by the
     * launcher rather than read from wherever the wall happened to rest when the run was built:
     * a replay from Settings, or a resume after a process death, can find the wall on the
     * display, and a run that called that home asked for every terminal control from a place
     * that has none of them.
     */
    public void setHomePlace(String placeId) {
        mHomePlace = placeId;
    }

    /**
     * The current place, when the wall has settled on it. Edge-triggered on the place itself: a
     * rotation re-settles the place the wall is already on, and that is not a swipe.
     */
    public void onPlaceSettled(String placeId) {
        if (placeId == null) return;
        if (mHomePlace == null) mHomePlace = placeId;
        String previous = mPlace;
        mPlace = placeId;
        if (previous == null || previous.equals(placeId)) return;
        emit(mHomePlace.equals(placeId) ? PLACE_RETURNED : PLACE_CHANGED);
    }

    /**
     * Whether the wall is resting on the place the run is taught on. True until the wall has
     * ever said where it is: a launcher with no wall at all has only the terminal.
     */
    public boolean isOnHomePlace() {
        return mPlace == null || mHomePlace == null || mHomePlace.equals(mPlace);
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
     * The sessions the launcher is holding, whenever the sessions list has just been rebuilt.
     *
     * <p>One more session than last time is the chapter's Ctrl+Alt+Shift+C. The current session
     * coming back to the one the chapter began in is what the "return to your first session" card
     * waits for, and it is deliberately the settled state rather than the swipe that asked for it:
     * the space bar's corner swipes walk a ring, so a user two sessions along has swiped without
     * arriving.
     *
     * @param count how many sessions there are, or -1 when the launcher has none to count yet
     * @param currentSessionId the session that is current, or null when there is none
     */
    public void onSessionsSettled(int count, String currentSessionId) {
        if (count >= 0) {
            Integer previous = mSessionCount;
            mSessionCount = count;
            if (previous != null && count > previous) emit(SESSION_OPENED);
        }
        if (currentSessionId == null) return;
        String previous = mCurrentSession;
        mCurrentSession = currentSessionId;
        if (previous == null || previous.equals(currentSessionId)) return;
        if (currentSessionId.equals(mChapterHomeSession)) emit(SESSION_RETURNED);
    }

    /**
     * The window the launcher is showing, whenever the active pane has settled on it. Like the
     * session above, the card waits for the arrival and not for the swipe.
     */
    public void onActiveWindowSettled(String windowId) {
        if (windowId == null) return;
        String previous = mActiveWindow;
        mActiveWindow = windowId;
        if (previous == null || previous.equals(windowId)) return;
        if (windowId.equals(mChapterHomeWindow)) emit(WINDOW_RETURNED);
    }

    /**
     * Remembers where the keyboard chapter began, so its last two cards can ask for the way back.
     *
     * <p>Called when the chapter's first card is shown rather than when the run starts: the cards
     * before it open and close a window of their own, and "your first window" means the one the
     * chapter left the user on.
     */
    public void markKeyboardChapterHome() {
        mChapterHomeSession = mCurrentSession;
        mChapterHomeWindow = mActiveWindow;
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
        mPaletteOpen = Boolean.TRUE;
        emit(PALETTE_OPENED);
    }

    /**
     * The command palette went away. Edge-triggered against the open above rather than emitted
     * from every call: the interceptor funnel this arrives from hands its slot back on pause, on a
     * configuration change and on destroy too, and a close of a palette the run never saw open is
     * not the gesture the card is asking for.
     */
    public void onPaletteClosed() {
        if (!Boolean.TRUE.equals(mPaletteOpen)) return;
        mPaletteOpen = Boolean.FALSE;
        emit(PALETTE_CLOSED);
    }

    private void emit(String signalId) {
        if (mListener != null) mListener.onTourSignal(signalId);
    }
}
