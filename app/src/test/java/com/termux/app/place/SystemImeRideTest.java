package com.termux.app.place;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.termux.app.window.Hold;

import org.junit.Test;

/** The system keyboard's ownership (a request, its backstop) and the ride's offset (ADR 0030 section 5). */
public class SystemImeRideTest {

    private long now = 1_000L;
    private final SystemImeRide mRide = new SystemImeRide();

    @Test
    public void requestWaitsAndOwnsNothingYet() {
        Hold hold = mRide.request(() -> now);
        assertNotNull(hold);
        assertEquals("system-ime", hold.name());
        assertEquals(SystemImeRide.BACKSTOP_MS, hold.backstopMs());
        assertEquals(SystemImeRide.WAITING, mRide.state());
        assertFalse(mRide.owns());
    }

    @Test
    public void visibleInsetsOwnTheRideAndSignalTheHold() {
        Hold hold = mRide.request(() -> now);
        mRide.onInsets(true);
        assertTrue(mRide.owns());
        assertEquals(Hold.SIGNAL, hold.releaseReason());
    }

    @Test
    public void hiddenInsetsKeepWaiting() {
        Hold hold = mRide.request(() -> now);
        mRide.onInsets(false);
        assertEquals(SystemImeRide.WAITING, mRide.state());
        assertTrue(hold.isOpen());
    }

    @Test
    public void aRequestWhoseImeNeverComesBacksOffToNone() {
        Hold hold = mRide.request(() -> now);
        now += SystemImeRide.BACKSTOP_MS;
        assertFalse(hold.pollBackstop());
        assertEquals(Hold.BACKSTOP, hold.releaseReason());
        assertEquals(SystemImeRide.NONE, mRide.state());
        assertFalse(mRide.owns());
    }

    @Test
    public void anAnimationOwnsWithoutARequest() {
        mRide.onAnimationStart();
        assertTrue(mRide.owns());
    }

    @Test
    public void anAnimationSignalsAnOpenHold() {
        Hold hold = mRide.request(() -> now);
        mRide.onAnimationStart();
        assertTrue(mRide.owns());
        assertEquals(Hold.SIGNAL, hold.releaseReason());
    }

    @Test
    public void releaseDropsOwnershipAndEndsAnOpenHold() {
        Hold hold = mRide.request(() -> now);
        mRide.release();
        assertEquals(SystemImeRide.NONE, mRide.state());
        assertEquals("released", hold.releaseReason());
        mRide.request(() -> now);
        mRide.onInsets(true);
        mRide.release();
        assertFalse(mRide.owns());
    }

    @Test
    public void aRequestWhileOwnedStaysOwned() {
        mRide.request(() -> now);
        mRide.onInsets(true);
        mRide.request(() -> now);
        assertTrue(mRide.owns());
    }

    @Test
    public void aShowRidesFromBelowToItsLaidOutPose() {
        mRide.onAnimationStart(800);
        mRide.onAnimationProgress(0);
        assertEquals(-800, mRide.offsetPx());
        mRide.onAnimationProgress(400);
        assertEquals(-400, mRide.offsetPx());
        mRide.onAnimationProgress(800);
        assertEquals(0, mRide.offsetPx());
        mRide.onAnimationEnd();
        assertEquals(0, mRide.offsetPx());
    }

    @Test
    public void aHideRidesFromItsOldPoseDown() {
        mRide.onAnimationStart(0);
        mRide.onAnimationProgress(800);
        assertEquals(800, mRide.offsetPx());
        mRide.onAnimationProgress(0);
        assertEquals(0, mRide.offsetPx());
    }

    @Test
    public void noOffsetWhenNothingRuns() {
        assertEquals(0, mRide.offsetPx());
        assertEquals(0, SystemImeRide.rideOffsetPx(false, 300, 800));
        assertEquals(-500, SystemImeRide.rideOffsetPx(true, 300, 800));
    }
}
