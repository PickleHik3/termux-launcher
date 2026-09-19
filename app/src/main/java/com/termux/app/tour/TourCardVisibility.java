package com.termux.app.tour;

import androidx.annotation.Nullable;

import java.util.Set;

/**
 * Whether the card may draw at all right now, and where.
 *
 * <p>The overlay is passive and covers the whole window, so it happily draws a card over a surface
 * the user has since opened on top of the control it is about — the drawer, the palette, a terminal
 * sheet, the surface editor. On the first device pass that is exactly what happened twice: the A-Z
 * card sat on the open drawer, and the closing card sat on the open palette.
 *
 * <p>The rule: while any of that chrome is up the run gets out of the way, except for the one card
 * whose whole ask is about that very surface — closing it, or, for the pinned-apps sheet, saving
 * something in it. That one still has to be readable, so it shows compact at the top of the
 * screen, where the surface it is asking about is not. A card that falls
 * due behind chrome is not lost: it is simply shown when the chrome goes.
 *
 * <p>What is deliberately not here any more: the A-Z card used to go off the screen entirely while
 * a finger was down on the letters. On the third device pass that read as the card vanishing the
 * moment the user did the thing it asked for, so the card stays where it is — at the top of the
 * screen, clear of the icons and of the scrub's own previews — until the app is launched.
 *
 * <p>Help is the one surface with no exception at all: nothing the run has to say is worth drawing
 * over the page of answers it spent its first lesson teaching the user to reach, so every card,
 * practice hints included, waits while help is up.
 *
 * <p>Pure, so every combination below is a unit test rather than a phone.
 */
public final class TourCardVisibility {

    /** Anchored to the control it is about, glow and finger included. */
    public static final int NORMAL = 0;
    /** At the top of the screen under the status bar, with no glow: the card is all there is. */
    public static final int COMPACT_TOP = 1;
    /** Not drawn at all. */
    public static final int HIDDEN = 2;
    /**
     * Compact at the top of the screen, saying how to get back to the terminal and glowing
     * nothing: the card is taught on the terminal place and the wall is resting on another one.
     * The control it names is not on this place at all — no + on the display's row, no keyboard on
     * the widgets — so a card left pointing at it would stand where the last one stood, over
     * nothing, which is what "the tour falls out of place" looked like on the device.
     */
    public static final int AWAY = 3;

    /**
     * The surface a card asking for {@code signalId} is asking the user to close, or null when the
     * card is not about closing anything.
     */
    @Nullable
    public static TourChrome chromeClosedBy(@Nullable String signalId) {
        if (TourSignals.DRAWER_CLOSED.equals(signalId)) return TourChrome.DRAWER;
        if (TourSignals.PALETTE_CLOSED.equals(signalId)) return TourChrome.PALETTE;
        // Help is deliberately not here. The card asking the user to close help is the third
        // stage of the first lesson, and it waits behind help like every other card: help is a
        // page of answers the user went to read, and a card resting on top of it is in the way of
        // the very thing the lesson sent them for.
        return null;
    }

    /**
     * The surface a card asking for {@code signalId} is asking the user to finish with, or null
     * when the card is about nothing that is up. The one card that may be read over a surface:
     * everything else waits for the surface to go.
     */
    @Nullable
    public static TourChrome chromeAskedAboutBy(@Nullable String signalId) {
        TourChrome closes = chromeClosedBy(signalId);
        if (closes != null) return closes;
        // The pin editor is finished with by saving rather than by closing, and the card that asks
        // for that save is the only thing on screen that says what to do inside the sheet.
        if (TourSignals.PINNED_APPS_SAVED.equals(signalId)) return TourChrome.PIN_EDITOR;
        return null;
    }

    /**
     * @param topAnchored the card asks to rest at the top of the screen whatever else is going on
     * @param chromeUp the full-plane surfaces in front of the user right now
     * @param awaitedSignal what the card that is up is waiting for, or null when it waits for its
     *     own button
     */
    public static int decide(boolean topAnchored, Set<TourChrome> chromeUp,
                             @Nullable String awaitedSignal) {
        return decide(topAnchored, chromeUp, awaitedSignal, false, true);
    }

    /**
     * @param taughtOnTerminal whether the card's control lives on the terminal place
     * @param onTerminal whether the wall is resting on the terminal place. Chrome still wins: a
     *     drawer pulled down over the display place hides the card like any other.
     */
    public static int decide(boolean topAnchored, Set<TourChrome> chromeUp,
                             @Nullable String awaitedSignal, boolean taughtOnTerminal,
                             boolean onTerminal) {
        if (chromeUp != null && chromeUp.contains(TourChrome.HELP)) return HIDDEN;
        if (chromeUp != null && !chromeUp.isEmpty()) {
            TourChrome about = chromeAskedAboutBy(awaitedSignal);
            return about != null && chromeUp.contains(about) ? COMPACT_TOP : HIDDEN;
        }
        if (taughtOnTerminal && !onTerminal) return AWAY;
        return topAnchored ? COMPACT_TOP : NORMAL;
    }

    /** The same question for the card that is up, which knows its own stage. */
    public static int decide(@Nullable TourStep step, int stage, Set<TourChrome> chromeUp) {
        return decide(step, stage, chromeUp, true);
    }

    /** As above, told whether the wall is resting on the terminal place. */
    public static int decide(@Nullable TourStep step, int stage, Set<TourChrome> chromeUp,
                             boolean onTerminal) {
        if (step == null) return HIDDEN;
        return decide(step.topAnchored, chromeUp, step.signalAt(stage),
            step.taughtOnTheTerminal(), onTerminal);
    }

    private TourCardVisibility() {}
}
