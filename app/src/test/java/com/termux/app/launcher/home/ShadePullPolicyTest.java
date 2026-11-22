package com.termux.app.launcher.home;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.launcher.home.HomeGestures.Action;

import org.junit.Test;

public class ShadePullPolicyTest {

    private static final int BAR = 96;

    @Test
    public void pullsLiveOnlyWithEverythingInPlace() {
        assertTrue(ShadePullPolicy.pullsLive(35, Action.NOTIFICATIONS, false, true, BAR));
        assertTrue(ShadePullPolicy.pullsLive(36, Action.NOTIFICATIONS, false, true, BAR));
    }

    @Test
    public void belowAndroid15TheSwipeOpensOnRelease() {
        // Up to 14 the injected press takes the touch stream from the launcher's finger.
        assertFalse(ShadePullPolicy.pullsLive(34, Action.NOTIFICATIONS, false, true, BAR));
        assertFalse(ShadePullPolicy.pullsLive(28, Action.NOTIFICATIONS, false, true, BAR));
    }

    @Test
    public void otherActionsAndTheKeyboardSwipeNeverPull() {
        assertFalse(ShadePullPolicy.pullsLive(35, Action.QUICK_SETTINGS, false, true, BAR));
        assertFalse(ShadePullPolicy.pullsLive(35, Action.NONE, false, true, BAR));
        assertFalse(ShadePullPolicy.pullsLive(35, Action.PALETTE, false, true, BAR));
        assertFalse(ShadePullPolicy.pullsLive(35, Action.NOTIFICATIONS, true, true, BAR));
    }

    @Test
    public void noLaneOrNoStatusBarKeepsTheReleaseToOpen() {
        assertFalse(ShadePullPolicy.pullsLive(35, Action.NOTIFICATIONS, false, false, BAR));
        // A hidden status bar would put the press on the launcher's own window.
        assertFalse(ShadePullPolicy.pullsLive(35, Action.NOTIFICATIONS, false, true, 0));
    }

    @Test
    public void thePressGoesDownInTheMiddleOfTheStatusBar() {
        assertEquals(48f, ShadePullPolicy.pressY(BAR), 0f);
        assertEquals(1f, ShadePullPolicy.pressY(1), 0f);
    }

    @Test
    public void theDragFollowsTheFingersTravelFromTheClaim() {
        float press = ShadePullPolicy.pressY(BAR);
        assertEquals(press, ShadePullPolicy.dragY(press, 800f, 800f), 0f);
        assertEquals(press + 250f, ShadePullPolicy.dragY(press, 800f, 1050f), 0f);
        // Back above where it was claimed: the shade is shut, never pulled above the bar.
        assertEquals(press, ShadePullPolicy.dragY(press, 800f, 600f), 0f);
        assertEquals(540f, ShadePullPolicy.dragX(540f), 0f);
        assertEquals(0f, ShadePullPolicy.dragX(-3f), 0f);
    }

    @Test
    public void onlyAFailedPullLetGoAsASwipeDownOpensTheOldWay() {
        assertTrue(ShadePullPolicy.fallsBackOnRelease(true, 1));
        assertFalse(ShadePullPolicy.fallsBackOnRelease(true, 0));
        assertFalse(ShadePullPolicy.fallsBackOnRelease(true, -1));
        assertFalse("a healthy pull settles in the shade itself",
            ShadePullPolicy.fallsBackOnRelease(false, 1));
    }
}
