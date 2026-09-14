package com.termux.app.tour;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.EnumSet;
import java.util.Set;

/**
 * The rule the first device pass asked for: the run gets out of the way of anything that covers
 * the home screen, except the one card that is asking the user to close it.
 */
public class TourCardVisibilityTest {

    private static final Set<TourChrome> NOTHING = EnumSet.noneOf(TourChrome.class);

    @Test
    public void withNothingInTheWayACardSitsAgainstItsControl() {
        assertEquals(TourCardVisibility.NORMAL,
            TourCardVisibility.decide(false, false, NOTHING, TourSignals.DRAWER_OPENED));
    }

    @Test
    public void aCardThatAsksForTheTopOfTheScreenGetsItWithNothingInTheWay() {
        assertEquals(TourCardVisibility.COMPACT_TOP,
            TourCardVisibility.decide(true, false, NOTHING, TourSignals.APP_LAUNCHED_FROM_SCRUB));
    }

    @Test
    public void everyFullPlaneSurfaceTakesTheCardOffTheScreen() {
        for (TourChrome chrome : TourChrome.values()) {
            assertEquals("a card still drawing over " + chrome, TourCardVisibility.HIDDEN,
                TourCardVisibility.decide(false, false, EnumSet.of(chrome),
                    TourSignals.APP_LAUNCHED_FROM_SCRUB));
        }
    }

    @Test
    public void aTopAnchoredCardIsHiddenByChromeLikeAnyOther() {
        // The A-Z card resting at the top is still a card drawing over an open drawer.
        assertEquals(TourCardVisibility.HIDDEN,
            TourCardVisibility.decide(true, false, EnumSet.of(TourChrome.DRAWER),
                TourSignals.APP_LAUNCHED_FROM_SCRUB));
    }

    @Test
    public void theCardAskingToCloseTheDrawerStaysUpAtTheTopOfTheScreen() {
        assertEquals(TourCardVisibility.COMPACT_TOP,
            TourCardVisibility.decide(false, false, EnumSet.of(TourChrome.DRAWER),
                TourSignals.DRAWER_CLOSED));
    }

    @Test
    public void thatOnlyHoldsForTheSurfaceTheCardIsActuallyAbout() {
        assertEquals(TourCardVisibility.HIDDEN,
            TourCardVisibility.decide(false, false, EnumSet.of(TourChrome.PALETTE),
                TourSignals.DRAWER_CLOSED));
    }

    @Test
    public void aScrubInProgressHidesEverything() {
        assertEquals(TourCardVisibility.HIDDEN,
            TourCardVisibility.decide(true, true, NOTHING,
                TourSignals.APP_LAUNCHED_FROM_SCRUB));
        assertEquals(TourCardVisibility.HIDDEN,
            TourCardVisibility.decide(false, true, EnumSet.of(TourChrome.DRAWER),
                TourSignals.DRAWER_CLOSED));
    }

    @Test
    public void onlyTheDrawerHasACardThatAsksForItsClose() {
        assertEquals(TourChrome.DRAWER,
            TourCardVisibility.chromeClosedBy(TourSignals.DRAWER_CLOSED));
        assertNull(TourCardVisibility.chromeClosedBy(TourSignals.DRAWER_OPENED));
        assertNull(TourCardVisibility.chromeClosedBy(TourSignals.PALETTE_OPENED));
        assertNull(TourCardVisibility.chromeClosedBy(null));
    }

    @Test
    public void theRunsOwnCardsAreJudgedByTheStageTheyAreOn() {
        TourStep drawer = TourRun.steps().get(5);
        Set<TourChrome> drawerUp = EnumSet.of(TourChrome.DRAWER);
        // Stage 0 asks the user to pull the drawer down, so a drawer already down hides it.
        assertEquals(TourCardVisibility.HIDDEN,
            TourCardVisibility.decide(drawer, 0, false, drawerUp));
        // Stage 1 asks them to close it, and has to be readable on top of it.
        assertEquals(TourCardVisibility.COMPACT_TOP,
            TourCardVisibility.decide(drawer, 1, false, drawerUp));
        assertEquals(TourCardVisibility.NORMAL,
            TourCardVisibility.decide(drawer, 0, false, NOTHING));
    }

    @Test
    public void theScrubCardIsTheOneThatRestsAtTheTopAndGoesForTheScrubItself() {
        TourStep scrub = TourRun.steps().get(6);
        assertEquals(TourCardVisibility.COMPACT_TOP,
            TourCardVisibility.decide(scrub, 0, false, NOTHING));
        assertEquals(TourCardVisibility.HIDDEN,
            TourCardVisibility.decide(scrub, 0, true, NOTHING));
    }

    @Test
    public void theClosingCardWaitsBehindThePaletteItsOwnCardOpened() {
        TourStep closing = TourRun.steps().get(8);
        assertEquals(TourCardVisibility.HIDDEN,
            TourCardVisibility.decide(closing, 0, false, EnumSet.of(TourChrome.PALETTE)));
        assertEquals(TourCardVisibility.NORMAL,
            TourCardVisibility.decide(closing, 0, false, NOTHING));
    }

    @Test
    public void noCardAtAllIsHidden() {
        assertEquals(TourCardVisibility.HIDDEN,
            TourCardVisibility.decide(null, 0, false, NOTHING));
    }
}
