package com.termux.app.tour;

import static org.junit.Assert.assertEquals;

import com.termux.app.place.PlaceLayout;

import org.junit.Test;

import java.util.EnumSet;
import java.util.Set;

/**
 * The run gets out of the way of anything that covers the home screen, comes back when it goes,
 * and says "Got it" over whatever the user's gesture just opened.
 */
public class TourCardVisibilityTest {

    private static final Set<TourChrome> NOTHING = EnumSet.noneOf(TourChrome.class);
    private static final TourRun.RunContext PHONE = new TourRun.RunContext(PlaceLayout.Edge.BOTTOM);

    private static TourStep step(String id) {
        for (TourStep step : TourRun.steps(PHONE))
            if (step.id.equals(id)) return step;
        throw new AssertionError("no card " + id + " in the run");
    }

    @Test
    public void withNothingInTheWayACardSitsAgainstItsControl() {
        assertEquals(TourCardVisibility.NORMAL,
            TourCardVisibility.decide(step(TourRun.FIND_APPS), NOTHING, true, false));
    }

    @Test
    public void everyFullPlaneSurfaceTakesTheCardOffTheScreen() {
        for (TourChrome chrome : TourChrome.values()) {
            assertEquals("a card still drawing over " + chrome, TourCardVisibility.HIDDEN,
                TourCardVisibility.decide(step(TourRun.FIND_APPS), EnumSet.of(chrome), true,
                    false));
        }
    }

    @Test
    public void gotItIsSaidOverWhateverTheGestureOpenedHelpIncluded() {
        for (TourChrome chrome : TourChrome.values()) {
            assertEquals(chrome.name(), TourCardVisibility.COMPLETED,
                TourCardVisibility.decide(step(TourRun.FIND_HELP), EnumSet.of(chrome), true,
                    true));
        }
        assertEquals(TourCardVisibility.COMPLETED,
            TourCardVisibility.decide(step(TourRun.BORDER_DRAG), NOTHING, false, true));
    }

    @Test
    public void aCardTaughtOnTheTerminalAsksTheWayBackFromAnotherPlace() {
        assertEquals(TourCardVisibility.AWAY,
            TourCardVisibility.decide(step(TourRun.FIND_ACTION), NOTHING, false, false));
        // The border and its pills are on every place, so their cards are read wherever the wall
        // rests.
        assertEquals(TourCardVisibility.NORMAL,
            TourCardVisibility.decide(step(TourRun.BORDER_DRAG), NOTHING, false, false));
        assertEquals(TourCardVisibility.NORMAL,
            TourCardVisibility.decide(step(TourRun.KEYBOARD), NOTHING, false, false));
    }

    @Test
    public void chromeWinsOverBeingAway() {
        assertEquals(TourCardVisibility.HIDDEN,
            TourCardVisibility.decide(step(TourRun.FIND_ACTION), EnumSet.of(TourChrome.DRAWER),
                false, false));
    }

    @Test
    public void theClosingCardIsNeverSentAwayButWaitsBehindChrome() {
        assertEquals(TourCardVisibility.NORMAL,
            TourCardVisibility.decide(step(TourRun.CLOSING), NOTHING, false, false));
        assertEquals(TourCardVisibility.HIDDEN,
            TourCardVisibility.decide(step(TourRun.CLOSING), EnumSet.of(TourChrome.PALETTE),
                true, false));
    }

    @Test
    public void noCardIsNothingToDraw() {
        assertEquals(TourCardVisibility.HIDDEN,
            TourCardVisibility.decide((TourStep) null, NOTHING, true, false));
    }
}
