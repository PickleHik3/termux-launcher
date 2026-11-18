package com.termux.app.terminal.extrakeys;

import static com.termux.app.terminal.extrakeys.HomePillFlightPlan.Flight.NONE;
import static com.termux.app.terminal.extrakeys.HomePillFlightPlan.Flight.TO_DOCK;
import static com.termux.app.terminal.extrakeys.HomePillFlightPlan.Flight.TO_PILL;
import static org.junit.Assert.assertEquals;

import com.termux.app.page.PageKind;
import com.termux.app.terminal.io.ExtraKeysTarget;

import org.junit.Test;

/** The flight a settle makes, from the facts before and after it. */
public class HomePillFlightPlanTest {

    private static HomePillFlightPlan.Flight plan(PageKind before, PageKind after,
                                                  boolean pillShown, boolean barBefore,
                                                  boolean barAfter, boolean on, boolean motion,
                                                  ExtraKeysTarget target) {
        return HomePillFlightPlan.decide(before, after, pillShown, barBefore, barAfter, on, motion,
            target);
    }

    @Test
    public void homeToAPageWithADockedBarFliesToTheDock() {
        assertEquals(TO_DOCK, plan(PageKind.HOME, PageKind.TERMINAL, true, false, true,
            true, true, ExtraKeysTarget.TERMINAL));
        assertEquals(TO_DOCK, plan(PageKind.HOME, PageKind.DISPLAY, true, false, true,
            true, true, ExtraKeysTarget.DISPLAY));
        assertEquals(TO_DOCK, plan(PageKind.HOME, PageKind.ASSISTANT, true, false, true,
            true, true, ExtraKeysTarget.ASSISTANT));
    }

    @Test
    public void homeToAPageWithNoBarDoesNotFly() {
        assertEquals(NONE, plan(PageKind.HOME, PageKind.TERMINAL, true, false, false,
            true, true, ExtraKeysTarget.TERMINAL));
    }

    @Test
    public void aPillThatWasNotShownHasNothingToFly() {
        assertEquals(NONE, plan(PageKind.HOME, PageKind.TERMINAL, false, false, true,
            true, true, ExtraKeysTarget.TERMINAL));
    }

    @Test
    public void aDockedBarToHomeFliesToThePill() {
        assertEquals(TO_PILL, plan(PageKind.TERMINAL, PageKind.HOME, false, true, false,
            true, true, ExtraKeysTarget.HOME));
        assertEquals(TO_PILL, plan(PageKind.ASSISTANT, PageKind.HOME, false, true, false,
            true, true, ExtraKeysTarget.HOME));
    }

    @Test
    public void homeWithAFloatFocusedOrNoBarBeforeDoesNotFly() {
        assertEquals(NONE, plan(PageKind.TERMINAL, PageKind.HOME, false, true, false,
            true, true, ExtraKeysTarget.TERMINAL));
        assertEquals(NONE, plan(PageKind.TERMINAL, PageKind.HOME, false, false, false,
            true, true, ExtraKeysTarget.HOME));
        assertEquals(NONE, plan(PageKind.TERMINAL, PageKind.HOME, false, true, false,
            true, true, null));
    }

    @Test
    public void aHomeToHomePageChangeStaysPut() {
        assertEquals(NONE, plan(PageKind.HOME, PageKind.HOME, true, false, false,
            true, true, ExtraKeysTarget.HOME));
    }

    @Test
    public void aWorkspaceToWorkspaceChangeStaysPut() {
        assertEquals(NONE, plan(PageKind.TERMINAL, PageKind.ASSISTANT, false, true, true,
            true, true, ExtraKeysTarget.ASSISTANT));
    }

    @Test
    public void withTheSwitchOffPageChangesAreAsToday() {
        assertEquals(NONE, plan(PageKind.HOME, PageKind.TERMINAL, true, false, true,
            false, true, ExtraKeysTarget.TERMINAL));
        assertEquals(NONE, plan(PageKind.TERMINAL, PageKind.HOME, false, true, false,
            false, true, ExtraKeysTarget.HOME));
    }

    @Test
    public void reducedMotionMakesNoFlight() {
        assertEquals(NONE, plan(PageKind.HOME, PageKind.TERMINAL, true, false, true,
            true, false, ExtraKeysTarget.TERMINAL));
        assertEquals(NONE, plan(PageKind.TERMINAL, PageKind.HOME, false, true, false,
            true, false, ExtraKeysTarget.HOME));
    }
}
