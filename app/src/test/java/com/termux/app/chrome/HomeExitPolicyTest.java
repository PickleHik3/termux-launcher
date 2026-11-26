package com.termux.app.chrome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class HomeExitPolicyTest {

    private static HomeExitPolicy.Step step(boolean open, boolean draft, boolean inFront) {
        return HomeExitPolicy.decide(open, draft, inFront);
    }

    @Test public void nothingOpenInFrontGoesToTheHomePage() {
        assertEquals(HomeExitPolicy.Step.GO_HOME, step(false, false, true));
    }

    @Test public void nothingOpenReturningComesBackToThePageLeft() {
        assertEquals(HomeExitPolicy.Step.RETURN, step(false, false, false));
    }

    @Test public void openWithoutDraftClosesAndStaysOnThePage() {
        assertEquals(HomeExitPolicy.Step.CLOSE_STAY, step(true, false, true));
        assertEquals(HomeExitPolicy.Step.CLOSE_STAY, step(true, false, false));
    }

    @Test public void openWithDraftAsksFirstWhereverTheLauncherIs() {
        assertEquals(HomeExitPolicy.Step.ASK, step(true, true, true));
        assertEquals(HomeExitPolicy.Step.ASK, step(true, true, false));
    }

    @Test public void aDraftOnASurfaceThatIsNotOpenAsksNothing() {
        assertEquals(HomeExitPolicy.Step.GO_HOME, step(false, true, true));
    }

    @Test public void theAnswerContinueClosesAndStaysStayChangesNothing() {
        assertEquals(HomeExitPolicy.Step.CLOSE_STAY, HomeExitPolicy.afterAnswer(true));
        assertEquals(HomeExitPolicy.Step.NONE, HomeExitPolicy.afterAnswer(false));
    }

    @Test public void aGestureHomeFromAnotherKindOfPageHoldsTheLanding() {
        assertTrue(HomeExitPolicy.holdsLanding(HomeExitPolicy.Step.GO_HOME, true, true, true));
    }

    @Test public void theLandingIsHeldOnlyWhenTheWallLandsAtOnceOnAShownWindow() {
        assertFalse("buttons: the wall plays its own slide",
            HomeExitPolicy.holdsLanding(HomeExitPolicy.Step.GO_HOME, true, true, false));
        assertFalse("a window not shown draws no stale frame",
            HomeExitPolicy.holdsLanding(HomeExitPolicy.Step.GO_HOME, true, false, true));
        assertFalse("on a Home page already, or landing on another kind",
            HomeExitPolicy.holdsLanding(HomeExitPolicy.Step.GO_HOME, false, true, true));
        for (HomeExitPolicy.Step other : HomeExitPolicy.Step.values()) {
            if (other == HomeExitPolicy.Step.GO_HOME) continue;
            assertFalse(other.name(), HomeExitPolicy.holdsLanding(other, true, true, true));
        }
    }
}
