package com.termux.app.tour;

/**
 * The surfaces that cover the home screen whole, and so cover the run's cards with it.
 *
 * <p>Each of these is a full-plane surface the user is inside: it takes the touches, it draws over
 * the control the card is glowing, and a card floating on top of it is pointing at something the
 * user can no longer see. {@link TourCardVisibility} is what decides what the overlay does about
 * it; this is only the list.
 */
public enum TourChrome {
    /** The app drawer plane, pulled down off the dock. */
    DRAWER,
    /** The command palette. */
    PALETTE,
    /** A terminal sheet — the modal plane the terminal's own prompts live on. */
    TERMINAL_SHEET,
    /** The surface editor. */
    SURFACE_EDITOR
}
