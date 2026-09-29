package com.termux.app.wall;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * When the Display page stands a still copy of its surface in for the wall's motions: when it
 * copies, when it reuses the copy it kept, when it falls back to today's motion, and how it
 * swaps back. The page does the pixels; this is every decision it makes about them.
 */
public class SurfaceStandInTest {

    private static final int W = 1080;
    private static final int H = 1800;

    private final SurfaceStandIn still = new SurfaceStandIn();

    /** A live display on screen, copied at {@code nowMs} and landed straight away. */
    private void copyLanded(long nowMs) {
        assertEquals(SurfaceStandIn.Begin.COPY, still.begin(true, true, W, H, nowMs));
        assertEquals(SurfaceStandIn.Copied.SHOW, still.copied(still.copyGeneration(),
            still.copyEpoch(), true, W, H, nowMs));
    }

    /** The same, then at rest on screen and swapped back: the copy is kept, nothing stands in. */
    private void copyKept(long nowMs) {
        copyLanded(nowMs);
        assertEquals(SurfaceStandIn.Rest.SWAP, still.rest(true));
        assertTrue(still.swapped());
    }

    @Test
    public void aLiveSurfaceIsCopiedAndStandsInOnceTheCopyLands() {
        assertEquals(SurfaceStandIn.Begin.COPY, still.begin(true, true, W, H, 0L));
        assertFalse("until the copy lands the page moves as it always has", still.isStill());
        assertFalse(still.isStandInShown());
        assertEquals(SurfaceStandIn.Copied.SHOW, still.copied(still.copyGeneration(),
            still.copyEpoch(), true, W, H, 16L));
        assertTrue("the stand-in is up: the surface goes down and the page takes a layer",
            still.isStill());
        assertTrue(still.isStandInShown());
    }

    @Test
    public void atRestTheSurfaceComesBackFirstAndTheStandInGoesOnceItDrew() {
        copyLanded(0L);
        assertEquals(SurfaceStandIn.Rest.SWAP, still.rest(true));
        assertFalse("the surface is back, so no layer and no tilt", still.isStill());
        assertTrue("the stand-in stays over it until it has drawn", still.isStandInShown());
        assertTrue(still.swapped());
        assertEquals(SurfaceStandIn.State.IDLE, still.state());
        assertFalse(still.isStandInShown());
        assertTrue("kept for the next arrival", still.hasCache());
    }

    @Test
    public void atRestWithTheSurfaceStayingDownTheStandInGoesAtOnce() {
        // The motion carried the Display off screen: its surface stays parked.
        copyLanded(0L);
        assertEquals(SurfaceStandIn.Rest.REMOVE, still.rest(false));
        assertEquals(SurfaceStandIn.State.IDLE, still.state());
        assertTrue(still.hasCache());
    }

    @Test
    public void aFailedCopyFallsBackSilentlyAndKeepsNothing() {
        // No data, secure content, a surface gone between the check and the call: all one answer.
        assertEquals(SurfaceStandIn.Begin.COPY, still.begin(true, true, W, H, 0L));
        assertEquals(SurfaceStandIn.Copied.DROP, still.copied(still.copyGeneration(),
            still.copyEpoch(), false, W, H, 16L));
        assertEquals(SurfaceStandIn.State.IDLE, still.state());
        assertFalse(still.isStill());
        assertFalse(still.hasCache());
        assertEquals("nothing to swap back", SurfaceStandIn.Rest.NONE, still.rest(true));
    }

    @Test
    public void aSurfaceOfNoSizeStandsNothingIn() {
        assertEquals(SurfaceStandIn.Begin.NOTHING, still.begin(true, true, 0, H, 0L));
        assertEquals(SurfaceStandIn.Begin.NOTHING, still.begin(true, true, W, 0, 0L));
        assertEquals(SurfaceStandIn.State.IDLE, still.state());
        // Nor a copy that came back empty-sized.
        assertEquals(SurfaceStandIn.Begin.COPY, still.begin(true, true, W, H, 0L));
        assertEquals(SurfaceStandIn.Copied.DROP, still.copied(still.copyGeneration(),
            still.copyEpoch(), true, 0, 0, 16L));
        assertFalse(still.isStill());
    }

    @Test
    public void aSurfaceNotYetCreatedOrInvalidHasNothingToCopy() {
        // Running, on screen, but no valid buffer: not live, and nothing kept to reuse.
        assertEquals(SurfaceStandIn.Begin.NOTHING, still.begin(true, false, W, H, 0L));
        assertFalse(still.isStill());
    }

    @Test
    public void aCopyLandingAfterTheMotionEndedIsOnlyKept() {
        assertEquals(SurfaceStandIn.Begin.COPY, still.begin(true, true, W, H, 0L));
        int generation = still.copyGeneration();
        int epoch = still.copyEpoch();
        assertEquals("rest came first: nothing was up", SurfaceStandIn.Rest.NONE,
            still.rest(true));
        assertEquals(SurfaceStandIn.Copied.KEEP, still.copied(generation, epoch, true, W, H, 40L));
        assertFalse(still.isStill());
        assertTrue(still.hasCache());
    }

    @Test
    public void aParkedDisplayArrivingReusesTheRecentCopyOfItsSize() {
        copyKept(0L);
        // Parked since: no live surface, but the copy from when it left.
        assertEquals(SurfaceStandIn.Begin.REUSE, still.begin(true, false, W, H, 5_000L));
        assertTrue("still at once: it tips as it arrives", still.isStill());
        assertTrue(still.isStandInShown());
    }

    @Test
    public void aParkedDisplayArrivingWithoutAFittingCopySlidesInAsToday() {
        copyKept(0L);
        assertEquals("another size", SurfaceStandIn.Begin.NOTHING,
            still.begin(true, false, W, H - 1, 5_000L));
        assertEquals("too old", SurfaceStandIn.Begin.NOTHING,
            still.begin(true, false, W, H, SurfaceStandIn.ARRIVAL_MAX_AGE_MS));
        assertEquals("a clock from before the copy", SurfaceStandIn.Begin.NOTHING,
            still.begin(true, false, W, H, -1L));
        assertFalse(still.isStill());
        assertTrue(still.cacheFits(W, H, SurfaceStandIn.ARRIVAL_MAX_AGE_MS - 1L));
    }

    @Test
    public void aLiveSurfaceIsCopiedAfreshEvenWithAFittingCopyKept() {
        copyKept(0L);
        assertEquals("the live picture, not the kept one", SurfaceStandIn.Begin.COPY,
            still.begin(true, true, W, H, 1_000L));
    }

    @Test
    public void noDisplayRunningIsPlainViewsAndStillAtOnce() {
        assertEquals(SurfaceStandIn.Begin.FLAT, still.begin(false, false, W, H, 0L));
        assertTrue("no surface to strand: the page takes the layer and the tilt",
            still.isStill());
        assertFalse("nothing to draw in its place", still.isStandInShown());
        assertEquals(SurfaceStandIn.Rest.REMOVE, still.rest(true));
        assertEquals(SurfaceStandIn.State.IDLE, still.state());
        assertFalse(still.isStill());
    }

    @Test
    public void aSecondBeginInTheSameMotionOnlyKeeps() {
        copyLanded(0L);
        // The drag began the motion, the sink asks again: nothing new.
        assertEquals(SurfaceStandIn.Begin.KEEP, still.begin(true, false, W, H, 16L));
        assertTrue(still.isStill());
        // And while the copy is on its way, a second begin waits for the same landing.
        SurfaceStandIn other = new SurfaceStandIn();
        assertEquals(SurfaceStandIn.Begin.COPY, other.begin(true, true, W, H, 0L));
        int generation = other.copyGeneration();
        assertEquals(SurfaceStandIn.Begin.KEEP, other.begin(true, true, W, H, 8L));
        assertEquals(generation, other.copyGeneration());
    }

    @Test
    public void movedAgainMidSwapTheSurfaceGoesBackDownUnderTheSameStandIn() {
        copyLanded(0L);
        assertEquals(SurfaceStandIn.Rest.SWAP, still.rest(true));
        assertEquals(SurfaceStandIn.Begin.KEEP, still.begin(true, true, W, H, 30L));
        assertTrue(still.isStill());
        assertFalse("the swap the new motion overtook takes nothing down", still.swapped());
        assertTrue(still.isStandInShown());
    }

    @Test
    public void theDisplayStoppingDropsTheCopyButNotTheOneStandingIn() {
        copyLanded(0L);
        assertFalse("mid-motion on it: the bitmap stays until rest", still.drop());
        assertTrue(still.isStandInShown());
        assertFalse(still.hasCache());
        assertEquals(SurfaceStandIn.Rest.REMOVE, still.rest(false));
        assertFalse("nothing kept for the next arrival", still.hasCache());
        assertEquals(SurfaceStandIn.Begin.NOTHING, still.begin(true, false, W, H, 16L));
    }

    @Test
    public void aDropWhileACopyIsOnItsWayLetsTheLandingGo() {
        assertEquals(SurfaceStandIn.Begin.COPY, still.begin(true, true, W, H, 0L));
        int generation = still.copyGeneration();
        int epoch = still.copyEpoch();
        assertTrue(still.drop());
        assertEquals(SurfaceStandIn.State.IDLE, still.state());
        assertEquals(SurfaceStandIn.Copied.DROP, still.copied(generation, epoch, true, W, H, 16L));
        assertFalse(still.hasCache());
    }

    @Test
    public void aResetForgetsEverythingAndEveryCopyInFlight() {
        assertEquals(SurfaceStandIn.Begin.COPY, still.begin(true, true, W, H, 0L));
        int generation = still.copyGeneration();
        int epoch = still.copyEpoch();
        still.reset();
        assertEquals(SurfaceStandIn.State.IDLE, still.state());
        assertEquals(SurfaceStandIn.Copied.DROP, still.copied(generation, epoch, true, W, H, 16L));
        assertFalse(still.hasCache());
        copyLanded(20L);
        still.reset();
        assertFalse(still.isStill());
        assertFalse(still.isStandInShown());
        assertFalse(still.hasCache());
    }

    @Test
    public void aLateCopyLandingWhileAnotherStandsInIsDropped() {
        // One surface-sized bitmap at a time: the one standing in is the one kept.
        assertEquals(SurfaceStandIn.Begin.COPY, still.begin(true, true, W, H, 0L));
        int lateGeneration = still.copyGeneration();
        int lateEpoch = still.copyEpoch();
        still.rest(true);
        copyLanded(100L);
        assertEquals(SurfaceStandIn.Copied.DROP, still.copied(lateGeneration, lateEpoch, true, W,
            H, 120L));
        assertTrue(still.isStill());
    }

    @Test
    public void aLentCopyIsNotKeptUntilItsSuccessorLands() {
        copyKept(0L);
        assertEquals(SurfaceStandIn.Begin.COPY, still.begin(true, true, W, H, 1_000L));
        still.lendCache();
        assertFalse(still.hasCache());
        assertEquals(SurfaceStandIn.Copied.DROP, still.copied(still.copyGeneration(),
            still.copyEpoch(), false, W, H, 1_016L));
        assertFalse("the failed copy wrote over the one lent to it", still.hasCache());
    }

    @Test
    public void aKeptCopyExpiresButNotWhileItStandsIn() {
        copyKept(0L);
        assertFalse(still.expire(SurfaceStandIn.ARRIVAL_MAX_AGE_MS - 1L));
        assertTrue(still.expire(SurfaceStandIn.ARRIVAL_MAX_AGE_MS));
        assertFalse(still.hasCache());
        assertFalse("nothing left to expire", still.expire(SurfaceStandIn.ARRIVAL_MAX_AGE_MS * 2));

        copyLanded(0L);
        assertFalse(still.expire(SurfaceStandIn.ARRIVAL_MAX_AGE_MS * 2));
        assertTrue(still.hasCache());
    }
}
