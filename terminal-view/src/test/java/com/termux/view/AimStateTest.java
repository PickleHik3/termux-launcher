package com.termux.view;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** When the aim opens, what takes it away, and where the lift clicks. */
public class AimStateTest {

    private static final float SLOP = 21f;

    private AimState held() {
        AimState aim = new AimState();
        aim.down(100f, 200f, SLOP);
        return aim;
    }

    @Test
    public void aFingerDownIsPendingNotAiming() {
        AimState aim = held();
        assertEquals(AimState.Phase.PENDING, aim.phase());
        assertFalse(aim.isAiming());
    }

    @Test
    public void aStillHoldOpensTheAimWhenTheDelayElapses() {
        AimState aim = held();
        aim.move(103f, 204f);
        assertTrue(aim.delayElapsed());
        assertTrue(aim.isAiming());
        assertEquals(100f, aim.aimX(), 0.001f);
        assertEquals(200f, aim.aimY(), 0.001f);
    }

    @Test
    public void movingBeforeTheDelayGivesTheGestureToTheScroll() {
        AimState aim = held();
        assertFalse(aim.move(100f, 260f));
        assertEquals(AimState.Phase.CANCELLED, aim.phase());
        assertFalse(aim.delayElapsed());
        assertFalse(aim.isAiming());
    }

    @Test
    public void theDelayOnlyEverOpensAPendingHold() {
        AimState aim = new AimState();
        assertFalse(aim.delayElapsed());
        assertEquals(AimState.Phase.IDLE, aim.phase());
    }

    @Test
    public void draggingCarriesTheAimAlong() {
        AimState aim = held();
        aim.delayElapsed();
        assertTrue(aim.move(340f, 500f));
        assertTrue(aim.isAiming());
        assertEquals(340f, aim.aimX(), 0.001f);
        assertEquals(500f, aim.aimY(), 0.001f);
    }

    @Test
    public void aDragThatStaysInTheSamePlaceOwesNoRepaint() {
        AimState aim = held();
        aim.delayElapsed();
        assertFalse(aim.move(100f, 200f));
    }

    @Test
    public void theLoupeOutlivesTheHold() {
        // The hold is recognised on the view's own timer and takes nothing away: the loupe opens
        // before it, stays open through it, follows the finger and is still what the lift clicks.
        AimState aim = held();
        assertTrue(aim.delayElapsed());
        HoldGesture hold = new HoldGesture();
        hold.down(100f, 200f, SLOP, true);
        assertEquals(HoldGesture.Outcome.HOLD_AIMED, hold.holdElapsed(true, false));
        assertTrue(aim.isAiming());
        assertTrue(aim.move(140f, 260f));
        assertTrue(aim.isAiming());
        assertTrue(aim.lift());
        assertEquals(140f, aim.aimX(), 0.001f);
        assertEquals(260f, aim.aimY(), 0.001f);
    }

    @Test
    public void aSecondFingerTakesTheAimAway() {
        AimState aim = held();
        aim.delayElapsed();
        aim.cancel();
        assertEquals(AimState.Phase.CANCELLED, aim.phase());
        assertFalse(aim.isAiming());
        assertFalse(aim.lift());
    }

    @Test
    public void aCancelledAimIgnoresFurtherMovement() {
        AimState aim = held();
        aim.cancel();
        assertFalse(aim.move(400f, 600f));
        assertEquals(AimState.Phase.CANCELLED, aim.phase());
    }

    @Test
    public void liftingAnOpenAimClicksWhereItEnded() {
        AimState aim = held();
        aim.delayElapsed();
        aim.move(262f, 380f);
        assertTrue(aim.lift());
        assertEquals(262f, aim.aimX(), 0.001f);
        assertEquals(380f, aim.aimY(), 0.001f);
        assertEquals(AimState.Phase.IDLE, aim.phase());
    }

    @Test
    public void liftingBeforeTheAimOpenedClicksNothingOfItsOwn() {
        AimState aim = held();
        assertFalse(aim.lift());
        assertEquals(AimState.Phase.IDLE, aim.phase());
    }

    @Test
    public void aSecondGestureStartsClean() {
        AimState aim = held();
        aim.cancel();
        aim.reset();
        assertEquals(AimState.Phase.IDLE, aim.phase());
        aim.down(10f, 20f, SLOP);
        assertTrue(aim.delayElapsed());
        assertEquals(10f, aim.aimX(), 0.001f);
    }
}
