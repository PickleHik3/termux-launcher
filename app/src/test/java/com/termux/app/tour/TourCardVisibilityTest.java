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
            TourCardVisibility.decide(false, NOTHING, TourSignals.DRAWER_OPENED));
    }

    @Test
    public void aCardThatAsksForTheTopOfTheScreenGetsItWithNothingInTheWay() {
        assertEquals(TourCardVisibility.COMPACT_TOP,
            TourCardVisibility.decide(true, NOTHING, TourSignals.APP_LAUNCHED_FROM_SCRUB));
    }

    @Test
    public void everyFullPlaneSurfaceTakesTheCardOffTheScreen() {
        for (TourChrome chrome : TourChrome.values()) {
            assertEquals("a card still drawing over " + chrome, TourCardVisibility.HIDDEN,
                TourCardVisibility.decide(false, EnumSet.of(chrome),
                    TourSignals.APP_LAUNCHED_FROM_SCRUB));
        }
    }

    @Test
    public void aTopAnchoredCardIsHiddenByChromeLikeAnyOther() {
        // The A-Z card resting at the top is still a card drawing over an open drawer.
        assertEquals(TourCardVisibility.HIDDEN,
            TourCardVisibility.decide(true, EnumSet.of(TourChrome.DRAWER),
                TourSignals.APP_LAUNCHED_FROM_SCRUB));
    }

    @Test
    public void theCardAskingToCloseTheDrawerStaysUpAtTheTopOfTheScreen() {
        assertEquals(TourCardVisibility.COMPACT_TOP,
            TourCardVisibility.decide(false, EnumSet.of(TourChrome.DRAWER),
                TourSignals.DRAWER_CLOSED));
    }

    @Test
    public void thatOnlyHoldsForTheSurfaceTheCardIsActuallyAbout() {
        assertEquals(TourCardVisibility.HIDDEN,
            TourCardVisibility.decide(false, EnumSet.of(TourChrome.PALETTE),
                TourSignals.DRAWER_CLOSED));
    }

    @Test
    public void theDrawerAndThePaletteBothHaveACardThatAsksForTheirClose() {
        assertEquals(TourChrome.DRAWER,
            TourCardVisibility.chromeClosedBy(TourSignals.DRAWER_CLOSED));
        assertEquals(TourChrome.PALETTE,
            TourCardVisibility.chromeClosedBy(TourSignals.PALETTE_CLOSED));
        assertNull(TourCardVisibility.chromeClosedBy(TourSignals.DRAWER_OPENED));
        assertNull(TourCardVisibility.chromeClosedBy(TourSignals.PALETTE_OPENED));
        assertNull(TourCardVisibility.chromeClosedBy(null));
    }

    @Test
    public void theCardAskingToCloseThePaletteStaysUpAtTheTopOfTheScreen() {
        TourStep palette = step("palette");
        Set<TourChrome> paletteUp = EnumSet.of(TourChrome.PALETTE);
        // Stage 0 asks the user to open it, so a palette already up hides the card.
        assertEquals(TourCardVisibility.HIDDEN,
            TourCardVisibility.decide(palette, 0, paletteUp));
        // Stage 1 asks them to close it, and has to be readable on top of it.
        assertEquals(TourCardVisibility.COMPACT_TOP,
            TourCardVisibility.decide(palette, 1, paletteUp));
        assertEquals(TourCardVisibility.NORMAL,
            TourCardVisibility.decide(palette, 0, NOTHING));
    }

    @Test
    public void theRunsOwnCardsAreJudgedByTheStageTheyAreOn() {
        TourStep drawer = step("drawer");
        Set<TourChrome> drawerUp = EnumSet.of(TourChrome.DRAWER);
        // Stage 0 asks the user to pull the drawer down, so a drawer already down hides it.
        assertEquals(TourCardVisibility.HIDDEN,
            TourCardVisibility.decide(drawer, 0, drawerUp));
        // Stage 1 asks them to close it, and has to be readable on top of it.
        assertEquals(TourCardVisibility.COMPACT_TOP,
            TourCardVisibility.decide(drawer, 1, drawerUp));
        assertEquals(TourCardVisibility.NORMAL,
            TourCardVisibility.decide(drawer, 0, NOTHING));
    }

    @Test
    public void theScrubCardStaysAtTheTopOfTheScreenWhileTheUserScrubs() {
        // It used to go off the screen entirely while a finger was down on the letters, which on
        // the third device pass read as the card vanishing the moment the user obeyed it. It sits
        // at the top, clear of the icons and of the scrub's previews, until the app is launched.
        TourStep scrub = step("az_scrub");
        assertEquals(TourCardVisibility.COMPACT_TOP,
            TourCardVisibility.decide(scrub, 0, NOTHING));
        assertEquals(TourCardVisibility.COMPACT_TOP,
            TourCardVisibility.decide(true, NOTHING, TourSignals.APP_LAUNCHED_FROM_SCRUB));
    }

    @Test
    public void theClosingCardWaitsBehindThePaletteItsOwnCardOpened() {
        TourStep closing = step("closing");
        assertEquals(TourCardVisibility.HIDDEN,
            TourCardVisibility.decide(closing, 0, EnumSet.of(TourChrome.PALETTE)));
        assertEquals(TourCardVisibility.NORMAL,
            TourCardVisibility.decide(closing, 0, NOTHING));
    }

    /** By id, not by position: the run gains and loses cards, and these three do not move. */
    private static TourStep step(String id) {
        for (TourStep step : TourRun.steps())
            if (step.id.equals(id)) return step;
        throw new AssertionError("no card " + id + " in the run");
    }

    @Test
    public void noCardAtAllIsHidden() {
        assertEquals(TourCardVisibility.HIDDEN,
            TourCardVisibility.decide(null, 0, NOTHING));
    }

    @Test
    public void aCardTaughtOnTheTerminalAsksForTheWayBackWhenTheWallIsElsewhere() {
        // The run was started, or resumed, or wandered, onto the display place: the +, the
        // keyboard and the panes it is about are not there, and a card left pointing at them stood
        // wherever the last measured control had been.
        for (String id : new String[] {"window", "kb_split", "kb_session_back", "pane_corner",
                "drawer", "az_scrub", "palette"}) {
            assertEquals(id + " away from the terminal", TourCardVisibility.AWAY,
                TourCardVisibility.decide(step(id), 0, NOTHING, false));
        }
    }

    @Test
    public void theCardsAboutTheStatusBarAndTheClosingCardCanBeReadOnAnyPlace() {
        assertEquals(TourCardVisibility.NORMAL,
            TourCardVisibility.decide(step("status_place"), 0, NOTHING, false));
        // Its second half is the one card that is off the terminal by design.
        assertEquals(TourCardVisibility.NORMAL,
            TourCardVisibility.decide(step("status_place"), 1, NOTHING, false));
        assertEquals(TourCardVisibility.NORMAL,
            TourCardVisibility.decide(step("status_expand"), 0, NOTHING, false));
        assertEquals(TourCardVisibility.NORMAL,
            TourCardVisibility.decide(step("closing"), 0, NOTHING, false));
    }

    @Test
    public void chromeStillWinsOverBeingAwayFromTheTerminal() {
        // A drawer pulled down over the display place covers the card like any other drawer.
        assertEquals(TourCardVisibility.HIDDEN,
            TourCardVisibility.decide(step("window"), 0, EnumSet.of(TourChrome.DRAWER), false));
        assertEquals(TourCardVisibility.COMPACT_TOP,
            TourCardVisibility.decide(step("drawer"), 1, EnumSet.of(TourChrome.DRAWER), false));
    }

    @Test
    public void onTheTerminalNothingChanges() {
        assertEquals(TourCardVisibility.NORMAL,
            TourCardVisibility.decide(step("window"), 0, NOTHING, true));
        assertEquals(TourCardVisibility.COMPACT_TOP,
            TourCardVisibility.decide(step("az_scrub"), 0, NOTHING, true));
        assertEquals(TourCardVisibility.HIDDEN,
            TourCardVisibility.decide(null, 0, NOTHING, false));
    }
}
