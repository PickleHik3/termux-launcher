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
    /** One more session than there was: the keyboard chapter's Ctrl+Alt+Shift+C. */
    String SESSION_OPENED = "session.opened";
    /**
     * The session the keyboard chapter started in is the current one again. A state, not a swipe:
     * the card is asking the user to get back, and a swipe that lands somewhere else has not.
     */
    String SESSION_RETURNED = "session.returned";
    /** The window the keyboard chapter started in is the active one again; a state, as above. */
    String WINDOW_RETURNED = "window.returned";
    /** A pane's corner menu was raised. */
    String PANE_CORNER_MENU = "pane.corner_menu";
    /** A pane's corner menu went away again. */
    String PANE_CONTROLS_DISMISSED = "pane.controls_dismissed";
    /** The app drawer settled open. */
    String DRAWER_OPENED = "drawer.opened";
    /** The app drawer settled closed again. */
    String DRAWER_CLOSED = "drawer.closed";
    /** The A-Z row's scrub launched an app. */
    String APP_LAUNCHED_FROM_SCRUB = "launcher.scrub_launch";
    /** An Android app was launched from the launcher, however the user found it. */
    String APP_LAUNCHED = "launcher.app_launched";
    /** The launcher is in front of the user again after an app was launched from it. */
    String LAUNCHER_RESUMED = "launcher.resumed";

    /** Help came up, from the corner tab or from anywhere else that opens it. */
    String HELP_OPENED = "help.opened";
    /** Help went away again, however it was dismissed. */
    String HELP_CLOSED = "help.closed";

    /** The keyboard settled showing. */
    String KEYBOARD_SHOWN = "keyboard.shown";
    /** The keyboard settled hidden. */
    String KEYBOARD_HIDDEN = "keyboard.hidden";
    /** The command palette was opened by the space bar's swipe up. */
    String PALETTE_OPENED = "palette.opened";
    /**
     * The command palette went away again, however it was dismissed. Reported from the one call
     * every close path makes — the interceptor funnel that hands the keyboard slot back — so an
     * outside tap, Esc and a second invocation all count.
     */
    String PALETTE_CLOSED = "palette.closed";

    /** What a signal source talks to. */
    interface Listener {
        void onTourSignal(String signalId);
    }

    void setTourSignalListener(Listener listener);
}
