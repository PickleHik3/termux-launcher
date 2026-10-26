package com.termux.app.tour;

import androidx.annotation.Nullable;

import java.util.Set;

/**
 * Whether the card may draw at all right now, and where.
 *
 * <p>The overlay is passive and covers the whole window, so it would happily draw a card over a
 * surface the user has since opened on top of the control it is about — the drawer, the palette,
 * a terminal sheet, the surface editor, help. While any of that is up the card gets out of the way,
 * and comes back when it goes.
 *
 * <p>The one exception is the "Got it" a card says once its lesson is cleared. Clearing a lesson
 * usually opens the very surface it is about — the drawer, the palette, help — and the card stays
 * where it stood, over it, for the moment it takes to say so.
 *
 * <p>Pure, so every combination below is a unit test rather than a phone.
 */
public final class TourCardVisibility {

    /** Anchored to the control it is about, glow and demonstration included. */
    public static final int NORMAL = 0;
    /** Not drawn at all. */
    public static final int HIDDEN = 2;
    /**
     * At the top of the screen, saying the way to the other half of the wall and glowing nothing:
     * the stage is taught on Home and the wall rests on the Workspace, or the other way round.
     */
    public static final int AWAY = 3;
    /** Where it stood, saying "Got it", over whatever the gesture opened. */
    public static final int COMPLETED = 4;

    /**
     * @param chromeUp the full-plane surfaces in front of the user right now
     * @param onItsSide whether the wall rests on the half the stage's control stands on
     * @param completing whether the card is saying "Got it"
     */
    public static int decide(@Nullable Set<TourChrome> chromeUp, boolean onItsSide,
                             boolean completing) {
        if (completing) return COMPLETED;
        if (chromeUp != null && !chromeUp.isEmpty()) return HIDDEN;
        if (!onItsSide) return AWAY;
        return NORMAL;
    }

    /**
     * The same question for the card that is up, at its stage. The closing card is never sent
     * away.
     *
     * @param wallSide the half of the wall it rests on, or null while it has not said
     */
    public static int decide(@Nullable TourStep step, int stage,
                             @Nullable Set<TourChrome> chromeUp, @Nullable TourStep.Side wallSide,
                             boolean completing) {
        if (step == null) return HIDDEN;
        return decide(chromeUp, step.isTaughtOn(stage, wallSide), completing);
    }

    private TourCardVisibility() {}
}
