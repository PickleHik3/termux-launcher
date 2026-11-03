package com.termux.app.launcher.widget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class HomeIconLiftTest {

    // ---- the distance

    /** Two slops: Launcher3's 16dp at the platform's 8dp slop, scaled with the device's slop. */
    @Test
    public void theLiftDistanceIsTwoTouchSlops() {
        assertEquals(16, HomeIconLift.liftDistancePx(8));
        assertEquals(42, HomeIconLift.liftDistancePx(21));
        assertEquals(1, HomeIconLift.liftDistancePx(0));
    }

    @Test
    public void onlyTravelPastTheDistanceLifts() {
        assertFalse(HomeIconLift.pastLiftDistance(0f, 0f, 16));
        assertFalse(HomeIconLift.pastLiftDistance(16f, 0f, 16));
        assertFalse(HomeIconLift.pastLiftDistance(11f, -11f, 16));
        assertTrue(HomeIconLift.pastLiftDistance(12f, 12f, 16));
        assertTrue(HomeIconLift.pastLiftDistance(0f, -17f, 16));
    }

    // ---- the long press

    /** An app icon opens its App menu, locked or not: the menu is not an edit. */
    @Test
    public void anAppIconOpensItsMenuLockedOrNot() {
        assertEquals(HomeIconLift.Hold.MENU, HomeIconLift.onLongPress(false, true, true, false));
        assertEquals(HomeIconLift.Hold.MENU, HomeIconLift.onLongPress(false, true, true, true));
    }

    /** A folder, or an app that is gone, has no menu: it lifts at once, unless locked. */
    @Test
    public void anItemWithoutAMenuLiftsAtOnce() {
        assertEquals(HomeIconLift.Hold.LIFT, HomeIconLift.onLongPress(false, false, true, false));
        assertEquals(HomeIconLift.Hold.REFUSE, HomeIconLift.onLongPress(false, false, true, true));
    }

    /** A widget keeps edit mode; so does any hold inside an open session. */
    @Test
    public void widgetsAndOpenSessionsKeepEditMode() {
        assertEquals(HomeIconLift.Hold.EDIT, HomeIconLift.onLongPress(false, false, false, false));
        assertEquals(HomeIconLift.Hold.REFUSE, HomeIconLift.onLongPress(false, false, false, true));
        assertEquals(HomeIconLift.Hold.EDIT, HomeIconLift.onLongPress(true, true, true, false));
    }

    // ---- the travel while the menu is up

    @Test
    public void shortTravelLeavesTheMenuUp() {
        assertEquals(HomeIconLift.Move.STAY, HomeIconLift.onHoldMove(false, false, false));
        assertEquals(HomeIconLift.Move.STAY, HomeIconLift.onHoldMove(false, true, false));
    }

    @Test
    public void travelPastTheDistanceLiftsTheIcon() {
        assertEquals(HomeIconLift.Move.LIFT, HomeIconLift.onHoldMove(true, false, false));
    }

    /** A locked layout refuses the lift once and keeps the menu; later moves say nothing more. */
    @Test
    public void aLockedLayoutRefusesOnceAndKeepsTheMenu() {
        assertEquals(HomeIconLift.Move.REFUSE, HomeIconLift.onHoldMove(true, true, false));
        assertEquals(HomeIconLift.Move.STAY, HomeIconLift.onHoldMove(true, true, true));
    }
}
