package com.termux.app.tour;

import static org.junit.Assert.assertEquals;

import com.termux.app.place.PlaceLayout;

import org.junit.Test;

import java.util.EnumSet;
import java.util.Set;

/**
 * The run gets out of the way of anything that covers the wall, asks the way to the half of the
 * wall a stage is taught on, comes back when it can, and says "Got it" over whatever the user's
 * gesture just opened.
 */
public class TourCardVisibilityTest {

    private static final Set<TourChrome> NOTHING = EnumSet.noneOf(TourChrome.class);
    private static final TourRun.RunContext PHONE = new TourRun.RunContext(PlaceLayout.Edge.BOTTOM);
    private static final TourStep.Side HOME = TourStep.Side.HOME;
    private static final TourStep.Side WORKSPACE = TourStep.Side.WORKSPACE;

    private static TourStep step(String id) {
        for (TourStep step : TourRun.steps(PHONE))
            if (step.id.equals(id)) return step;
        throw new AssertionError("no card " + id + " in the run");
    }

    @Test
    public void withNothingInTheWayACardSitsAgainstItsControl() {
        assertEquals(TourCardVisibility.NORMAL,
            TourCardVisibility.decide(step(TourRun.FIND_APPS), 0, NOTHING, HOME, false));
        assertEquals(TourCardVisibility.NORMAL,
            TourCardVisibility.decide(step(TourRun.KEYBOARD), 0, NOTHING, WORKSPACE, false));
    }

    @Test
    public void everyFullPlaneSurfaceTakesTheCardOffTheScreen() {
        for (TourChrome chrome : TourChrome.values()) {
            assertEquals("a card still drawing over " + chrome, TourCardVisibility.HIDDEN,
                TourCardVisibility.decide(step(TourRun.FIND_APPS), 0, EnumSet.of(chrome), HOME,
                    false));
        }
    }

    @Test
    public void gotItIsSaidOverWhateverTheGestureOpenedHelpIncluded() {
        for (TourChrome chrome : TourChrome.values()) {
            assertEquals(chrome.name(), TourCardVisibility.COMPLETED,
                TourCardVisibility.decide(step(TourRun.FIND_HELP), 1, EnumSet.of(chrome),
                    WORKSPACE, true));
        }
        // The seam's second half lands the wall on Home, the other half from its own.
        assertEquals(TourCardVisibility.COMPLETED,
            TourCardVisibility.decide(step(TourRun.PAGE_SWIPE), 1, NOTHING, HOME, true));
    }

    @Test
    public void aCardTaughtOnTheOtherHalfAsksTheWayThere() {
        assertEquals(TourCardVisibility.AWAY,
            TourCardVisibility.decide(step(TourRun.KEYBOARD), 0, NOTHING, HOME, false));
        assertEquals(TourCardVisibility.AWAY,
            TourCardVisibility.decide(step(TourRun.FIND_APPS), 0, NOTHING, WORKSPACE, false));
        // The seam lesson's halves sit on either side of it.
        assertEquals(TourCardVisibility.NORMAL,
            TourCardVisibility.decide(step(TourRun.PAGE_SWIPE), 0, NOTHING, HOME, false));
        assertEquals(TourCardVisibility.AWAY,
            TourCardVisibility.decide(step(TourRun.PAGE_SWIPE), 1, NOTHING, HOME, false));
        assertEquals(TourCardVisibility.NORMAL,
            TourCardVisibility.decide(step(TourRun.PAGE_SWIPE), 1, NOTHING, WORKSPACE, false));
    }

    @Test
    public void thePaletteCardIsReadOnEitherHalf() {
        assertEquals(TourCardVisibility.NORMAL,
            TourCardVisibility.decide(step(TourRun.FIND_ACTION), 0, NOTHING, HOME, false));
        assertEquals(TourCardVisibility.NORMAL,
            TourCardVisibility.decide(step(TourRun.FIND_ACTION), 0, NOTHING, WORKSPACE, false));
    }

    @Test
    public void untilTheWallHasSaidWhereItRestsNoCardIsSentAway() {
        assertEquals(TourCardVisibility.NORMAL,
            TourCardVisibility.decide(step(TourRun.KEYBOARD), 0, NOTHING, null, false));
    }

    @Test
    public void chromeWinsOverBeingAway() {
        assertEquals(TourCardVisibility.HIDDEN,
            TourCardVisibility.decide(step(TourRun.KEYBOARD), 0, EnumSet.of(TourChrome.DRAWER),
                HOME, false));
    }

    @Test
    public void theClosingCardIsNeverSentAwayButWaitsBehindChrome() {
        assertEquals(TourCardVisibility.NORMAL,
            TourCardVisibility.decide(step(TourRun.CLOSING), 0, NOTHING, WORKSPACE, false));
        assertEquals(TourCardVisibility.NORMAL,
            TourCardVisibility.decide(step(TourRun.CLOSING), 0, NOTHING, HOME, false));
        assertEquals(TourCardVisibility.HIDDEN,
            TourCardVisibility.decide(step(TourRun.CLOSING), 0, EnumSet.of(TourChrome.PALETTE),
                HOME, false));
    }

    @Test
    public void noCardIsNothingToDraw() {
        assertEquals(TourCardVisibility.HIDDEN,
            TourCardVisibility.decide((TourStep) null, 0, NOTHING, HOME, false));
    }
}
