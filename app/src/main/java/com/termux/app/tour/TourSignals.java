package com.termux.app.tour;

/**
 * The events the run listens for, and the one way anything reports one.
 *
 * <p>A signal is the launcher saying a gesture already happened — the tour never watches touches
 * of its own, so the overlay can stay passive and a user who finds the control on their own
 * clears the card without being told to.
 *
 * <p>Every id below has an adapter on {@link TourSignalRelay}, fed from the one place in the
 * chrome that already decides the thing.
 */
public interface TourSignals {

    /** The place under the status bar became a different one. */
    String PLACE_CHANGED = "place.changed";
    /** The place the run started on is showing again. */
    String PLACE_RETURNED = "place.returned";
    /** The status bar settled expanded. */
    String STATUS_BAR_EXPANDED = "status_bar.expanded";
    /** The status bar settled compact again. */
    String STATUS_BAR_COLLAPSED = "status_bar.collapsed";

    /** The top row is showing one more window than it was. */
    String WINDOW_OPENED = "window.opened";
    /** A different window became the current one. */
    String WINDOW_CHIP_SELECTED = "window.chip_selected";
    /** The top row is showing one window fewer than it was. */
    String WINDOW_CLOSED = "window.closed";
    /** A split was asked for, from the extra keys row or anywhere else that dispatches it. */
    String PANE_SPLIT = "pane.split";
    /** A pane's corner menu was raised. */
    String PANE_CORNER_MENU = "pane.corner_menu";
    /** The app drawer settled open. */
    String DRAWER_OPENED = "drawer.opened";
    /** The app drawer settled closed again. */
    String DRAWER_CLOSED = "drawer.closed";
    /** The A-Z row's scrub launched an app. */
    String APP_LAUNCHED_FROM_SCRUB = "launcher.scrub_launch";
    /** The command palette was opened by the space bar's swipe up. */
    String PALETTE_OPENED = "palette.opened";

    /** What a signal source talks to. */
    interface Listener {
        void onTourSignal(String signalId);
    }

    void setTourSignalListener(Listener listener);
}
