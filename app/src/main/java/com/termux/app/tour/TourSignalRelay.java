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

    private void emit(String signalId) {
        if (mListener != null) mListener.onTourSignal(signalId);
    }
}
