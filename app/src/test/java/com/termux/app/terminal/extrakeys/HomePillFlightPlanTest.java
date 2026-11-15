package com.termux.app.terminal.extrakeys;

import static com.termux.app.terminal.extrakeys.HomePillFlightPlan.Flight.NONE;
import static com.termux.app.terminal.extrakeys.HomePillFlightPlan.Flight.TO_DOCK;
import static com.termux.app.terminal.extrakeys.HomePillFlightPlan.Flight.TO_PILL;
import static org.junit.Assert.assertEquals;

import com.termux.app.terminal.io.ExtraKeysTarget;
import com.termux.app.wall.PaneWallPage;

import org.junit.Test;

/** The flight a settle makes, from the facts before and after it. */
public class HomePillFlightPlanTest {

    private static HomePillFlightPlan.Flight plan(PaneWallPage before, PaneWallPage after,
                                                  boolean pillShown, boolean barBefore,
                                                  boolean barAfter, boolean on, boolean motion,
                                                  ExtraKeysTarget target) {
        return HomePillFlightPlan.decide(before, after, pillShown, barBefore, barAfter, on, motion,
            target);
    }

    @Test
    public void homeToAPageWithADockedBarFliesToTheDock() {
        assertEquals(TO_DOCK, plan(PaneWallPage.WIDGETS, PaneWallPage.TERMINAL, true, false, true,
            true, true, ExtraKeysTarget.TERMINAL));
        assertEquals(TO_DOCK, plan(PaneWallPage.WIDGETS, PaneWallPage.DISPLAY, true, false, true,
            true, true, ExtraKeysTarget.DISPLAY));
        assertEquals(TO_DOCK, plan(PaneWallPage.WIDGETS, PaneWallPage.ASSISTANT, true, false, true,
            true, true, ExtraKeysTarget.ASSISTANT));
    }

    @Test
    public void homeToAPageWithNoBarDoesNotFly() {
        assertEquals(NONE, plan(PaneWallPage.WIDGETS, PaneWallPage.TERMINAL, true, false, false,
            true, true, ExtraKeysTarget.TERMINAL));
    }

    @Test
    public void aPillThatWasNotShownHasNothingToFly() {
        assertEquals(NONE, plan(PaneWallPage.WIDGETS, PaneWallPage.TERMINAL, false, false, true,
            true, true, ExtraKeysTarget.TERMINAL));
    }

    @Test
    public void aDockedBarToHomeFliesToThePill() {
        assertEquals(TO_PILL, plan(PaneWallPage.TERMINAL, PaneWallPage.WIDGETS, false, true, false,
            true, true, ExtraKeysTarget.HOME));
        assertEquals(TO_PILL, plan(PaneWallPage.ASSISTANT, PaneWallPage.WIDGETS, false, true, false,
            true, true, ExtraKeysTarget.HOME));
    }

    @Test
    public void homeWithAFloatFocusedOrNoBarBeforeDoesNotFly() {
        assertEquals(NONE, plan(PaneWallPage.TERMINAL, PaneWallPage.WIDGETS, false, true, false,
            true, true, ExtraKeysTarget.TERMINAL));
        assertEquals(NONE, plan(PaneWallPage.TERMINAL, PaneWallPage.WIDGETS, false, false, false,
            true, true, ExtraKeysTarget.HOME));
        assertEquals(NONE, plan(PaneWallPage.TERMINAL, PaneWallPage.WIDGETS, false, true, false,
            true, true, null));
    }

    @Test
    public void aHomeToHomePageChangeStaysPut() {
        assertEquals(NONE, plan(PaneWallPage.WIDGETS, PaneWallPage.WIDGETS, true, false, false,
            true, true, ExtraKeysTarget.HOME));
    }

    @Test
    public void aWorkspaceToWorkspaceChangeStaysPut() {
        assertEquals(NONE, plan(PaneWallPage.TERMINAL, PaneWallPage.ASSISTANT, false, true, true,
            true, true, ExtraKeysTarget.ASSISTANT));
    }

    @Test
    public void withTheSwitchOffPageChangesAreAsToday() {
        assertEquals(NONE, plan(PaneWallPage.WIDGETS, PaneWallPage.TERMINAL, true, false, true,
            false, true, ExtraKeysTarget.TERMINAL));
        assertEquals(NONE, plan(PaneWallPage.TERMINAL, PaneWallPage.WIDGETS, false, true, false,
            false, true, ExtraKeysTarget.HOME));
    }

    @Test
    public void reducedMotionMakesNoFlight() {
        assertEquals(NONE, plan(PaneWallPage.WIDGETS, PaneWallPage.TERMINAL, true, false, true,
            true, false, ExtraKeysTarget.TERMINAL));
        assertEquals(NONE, plan(PaneWallPage.TERMINAL, PaneWallPage.WIDGETS, false, true, false,
            true, false, ExtraKeysTarget.HOME));
    }
}
