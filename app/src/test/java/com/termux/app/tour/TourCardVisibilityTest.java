package com.termux.app.tour;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import org.junit.Test;

import java.util.EnumSet;
import java.util.Set;

/**
 * The rule the first device pass asked for: the run gets out of the way of anything that covers
 * the home screen, except the one card that is asking the user to close it — and help, which no
 * card is ever allowed to draw over.
 */
public class TourCardVisibilityTest {

    private static final Set<TourChrome> NOTHING = EnumSet.noneOf(TourChrome.class);
    private static final TourRun.RunContext PHONE = new TourRun.RunContext(false, true);

    /** By id, not by position: the run gains and loses cards, and these do not move. */
    private static TourStep step(String id) {
        for (TourStep step : TourRun.steps(PHONE))
            if (step.id.equals(id)) return step;
        throw new AssertionError("no card " + id + " in the run");
    }

    @Test
    public void withNothingInTheWayACardSitsAgainstItsControl() {
        assertEquals(TourCardVisibility.NORMAL,
            TourCardVisibility.decide(false, NOTHING, TourSignals.DRAWER_OPENED));
    }

    @Test
    public void aCardThatAsksForTheTopOfTheScreenGetsItWithNothingInTheWay() {
        assertEquals(TourCardVisibility.COMPACT_TOP,
            TourCardVisibility.decide(true, NOTHING, TourSignals.APP_LAUNCHED));
    }

    @Test
    public void everyFullPlaneSurfaceTakesTheCardOffTheScreen() {
        for (TourChrome chrome : TourChrome.values()) {
            assertEquals("a card still drawing over " + chrome, TourCardVisibility.HIDDEN,
                TourCardVisibility.decide(false, EnumSet.of(chrome), TourSignals.APP_LAUNCHED));
        }
    }

    @Test
    public void aTopAnchoredCardIsHiddenByChromeLikeAnyOther() {
        assertEquals(TourCardVisibility.HIDDEN,
            TourCardVisibility.decide(true, EnumSet.of(TourChrome.DRAWER),
                TourSignals.APP_LAUNCHED));
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
    public void helpIsNeverTheSurfaceACardMayDrawOver() {
        // Even the stage whose whole ask is "close help to continue": help is the page of answers
        // the lesson sent the user to read, and a card on top of it is in the way of the answer.
        assertNull(TourCardVisibility.chromeClosedBy(TourSignals.HELP_CLOSED));
        assertNull(TourCardVisibility.chromeClosedBy(TourSignals.HELP_OPENED));
    }

    @Test
    public void everyStageOfEveryCardIsHiddenWhileHelpIsUp() {
        Set<TourChrome> helpUp = EnumSet.of(TourChrome.HELP);
        for (TourStep step : TourRun.steps(PHONE))
            for (int stage = 0; stage <= step.signalCount(); stage++)
                assertEquals(step.id + ":" + stage + " while help is up",
                    TourCardVisibility.HIDDEN, TourCardVisibility.decide(step, stage, helpUp));
    }

    @Test
    public void helpHidesACardThatAnotherSurfaceWouldHaveLetThrough() {
        // The drawer's own card is readable over the drawer, and still not over help.
        assertEquals(TourCardVisibility.HIDDEN,
            TourCardVisibility.decide(false, EnumSet.of(TourChrome.DRAWER, TourChrome.HELP),
                TourSignals.DRAWER_CLOSED));
        assertEquals(TourCardVisibility.HIDDEN,
            TourCardVisibility.decide(true, EnumSet.of(TourChrome.HELP), TourSignals.HELP_CLOSED));
    }

    @Test
    public void aPractisedLessonIsHiddenByHelpLikeEveryOtherCard() {
        // Practice is the same card shown on its own, so it is the same decision: help opens,
        // the hint waits.
        TourStep practised = step(TourRun.KEYBOARD);
        assertEquals(TourCardVisibility.HIDDEN,
            TourCardVisibility.decide(practised, 0, EnumSet.of(TourChrome.HELP)));
        assertEquals(TourCardVisibility.NORMAL,
            TourCardVisibility.decide(practised, 0, NOTHING));
    }

    @Test
    public void theHelpLessonWaitsBehindTheHelpItOpened() {
        TourStep help = step(TourRun.FIND_HELP);
        Set<TourChrome> helpUp = EnumSet.of(TourChrome.HELP);
        assertEquals(TourCardVisibility.HIDDEN, TourCardVisibility.decide(help, 1, helpUp));
        // The stage asking for the way back out is hidden too, and shows again once help is down —
        // which is the moment its own signal lands.
        assertEquals(TourCardVisibility.HIDDEN, TourCardVisibility.decide(help, 2, helpUp));
        assertEquals(TourCardVisibility.NORMAL, TourCardVisibility.decide(help, 2, NOTHING));
    }

    @Test
    public void theCardAskingToCloseThePaletteStaysUpAtTheTopOfTheScreen() {
        TourStep action = step(TourRun.FIND_ACTION);
        Set<TourChrome> paletteUp = EnumSet.of(TourChrome.PALETTE);
        assertEquals(TourCardVisibility.HIDDEN, TourCardVisibility.decide(action, 0, paletteUp));
        assertEquals(TourCardVisibility.COMPACT_TOP,
            TourCardVisibility.decide(action, 1, paletteUp));
        assertEquals(TourCardVisibility.NORMAL, TourCardVisibility.decide(action, 0, NOTHING));
    }

    @Test
    public void theRunsOwnCardsAreJudgedByTheStageTheyAreOn() {
        TourStep apps = step(TourRun.FIND_APPS);
        Set<TourChrome> drawerUp = EnumSet.of(TourChrome.DRAWER);
        // Stage 0 asks the user to pull the drawer down, so a drawer already down hides it.
        assertEquals(TourCardVisibility.HIDDEN, TourCardVisibility.decide(apps, 0, drawerUp));
        // Stage 1 asks them to tap an app, which happens on the drawer: it is not the card that
        // closes the drawer, so it waits like any other.
        assertEquals(TourCardVisibility.HIDDEN, TourCardVisibility.decide(apps, 1, drawerUp));
        assertEquals(TourCardVisibility.NORMAL, TourCardVisibility.decide(apps, 0, NOTHING));
    }

    @Test
    public void theClosingCardWaitsBehindWhateverIsInFrontOfIt() {
        TourStep closing = step(TourRun.CLOSING);
        assertEquals(TourCardVisibility.HIDDEN,
            TourCardVisibility.decide(closing, 0, EnumSet.of(TourChrome.PALETTE)));
        assertEquals(TourCardVisibility.HIDDEN,
            TourCardVisibility.decide(closing, 0, EnumSet.of(TourChrome.HELP)));
        assertEquals(TourCardVisibility.NORMAL,
            TourCardVisibility.decide(closing, 0, NOTHING));
    }

    @Test
    public void noCardAtAllIsHidden() {
        assertEquals(TourCardVisibility.HIDDEN, TourCardVisibility.decide(null, 0, NOTHING));
        assertEquals(TourCardVisibility.HIDDEN,
            TourCardVisibility.decide(null, 0, EnumSet.of(TourChrome.HELP)));
    }

    @Test
    public void aLessonTaughtOnTheTerminalAsksForTheWayBackWhenTheWallIsElsewhere() {
        for (String id : TourRun.lessons()) {
            assertEquals(id + " away from the terminal", TourCardVisibility.AWAY,
                TourCardVisibility.decide(step(id), 0, NOTHING, false));
        }
    }

    @Test
    public void theQuestionAndTheClosingCardCanBeReadOnAnyPlace() {
        assertEquals(TourCardVisibility.NORMAL,
            TourCardVisibility.decide(step(TourRun.HOME_CHOICE), 0, NOTHING, false));
        assertEquals(TourCardVisibility.NORMAL,
            TourCardVisibility.decide(step(TourRun.CLOSING), 0, NOTHING, false));
    }

    @Test
    public void chromeStillWinsOverBeingAwayFromTheTerminal() {
        assertEquals(TourCardVisibility.HIDDEN,
            TourCardVisibility.decide(step(TourRun.KEYBOARD), 0,
                EnumSet.of(TourChrome.DRAWER), false));
        assertEquals(TourCardVisibility.COMPACT_TOP,
            TourCardVisibility.decide(step(TourRun.FIND_ACTION), 1,
                EnumSet.of(TourChrome.PALETTE), false));
        assertEquals(TourCardVisibility.HIDDEN,
            TourCardVisibility.decide(step(TourRun.FIND_ACTION), 1,
                EnumSet.of(TourChrome.HELP), false));
    }

    @Test
    public void onTheTerminalNothingChanges() {
        assertEquals(TourCardVisibility.NORMAL,
            TourCardVisibility.decide(step(TourRun.FIND_HELP), 0, NOTHING, true));
        assertEquals(TourCardVisibility.NORMAL,
            TourCardVisibility.decide(step(TourRun.FIND_APPS), 0, NOTHING, true));
    }
}
