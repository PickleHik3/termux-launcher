package com.termux.app.tour;

/**
 * The events the run listens for, and the one way anything reports one.
 *
 * <p>A signal is the launcher saying a gesture already happened — the tour never watches touches
 * of its own, so the overlay can stay passive and a user who finds the control on their own
 * clears the card without being told to.
 *
 * <p>Only the two status-bar steps are wired today; the rest of the ids exist so the run reads as
 * one table, and so the step data does not have to move when their adapters land.
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

    // TODO(tour phase 2): adapters for the signals below.
    String WINDOW_OPENED = "window.opened";
    String WINDOW_CHIP_SELECTED = "window.chip_selected";
    String WINDOW_CLOSED = "window.closed";
    String PANE_SPLIT = "pane.split";
    String PANE_CORNER_MENU = "pane.corner_menu";
    String DRAWER_OPENED = "drawer.opened";
    String DRAWER_CLOSED = "drawer.closed";
    String APP_LAUNCHED_FROM_SCRUB = "launcher.scrub_launch";
    String PALETTE_OPENED = "palette.opened";

    /** What a signal source talks to. */
    interface Listener {
        void onTourSignal(String signalId);
    }

    void setTourSignalListener(Listener listener);
}
