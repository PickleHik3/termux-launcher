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
 * whose whole ask is to close that very surface — that one still has to be readable, so it shows
 * compact at the top of the screen, where the surface it is asking about is not. A card that falls
 * due behind chrome is not lost: it is simply shown when the chrome goes.
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
     * The surface a card asking for {@code signalId} is asking the user to close, or null when the
     * card is not about closing anything.
     */
    @Nullable
    public static TourChrome chromeClosedBy(@Nullable String signalId) {
        if (TourSignals.DRAWER_CLOSED.equals(signalId)) return TourChrome.DRAWER;
        return null;
    }

    /**
     * @param topAnchored the card asks to rest at the top of the screen whatever else is going on
     * @param gestureInProgress a finger is mid-gesture on a control the run is teaching — the A-Z
     *     scrub, which paints its own previews across the screen. Nothing of the run draws over
     *     that: the user is choosing, and they have to be able to see what they are choosing.
     * @param chromeUp the full-plane surfaces in front of the user right now
     * @param awaitedSignal what the card that is up is waiting for, or null when it waits for its
     *     own button
     */
    public static int decide(boolean topAnchored, boolean gestureInProgress,
                             Set<TourChrome> chromeUp, @Nullable String awaitedSignal) {
        if (gestureInProgress) return HIDDEN;
        if (chromeUp != null && !chromeUp.isEmpty()) {
            TourChrome closes = chromeClosedBy(awaitedSignal);
            return closes != null && chromeUp.contains(closes) ? COMPACT_TOP : HIDDEN;
        }
        return topAnchored ? COMPACT_TOP : NORMAL;
    }

    /** The same question for the card that is up, which knows its own stage. */
    public static int decide(@Nullable TourStep step, int stage, boolean gestureInProgress,
                             Set<TourChrome> chromeUp) {
        if (step == null) return HIDDEN;
        return decide(step.topAnchored, gestureInProgress, chromeUp, step.signalAt(stage));
    }

    private TourCardVisibility() {}
}
