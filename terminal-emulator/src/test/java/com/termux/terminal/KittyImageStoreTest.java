package com.termux.terminal;

import junit.framework.TestCase;

/**
 * The store's bookkeeping is deliberately free of bitmap operations so it can be pinned on the
 * JVM: reservations carry explicit dimensions and byte counts, and bitmaps stay null here.
 */
public class KittyImageStoreTest extends TestCase {

    public void testResolveByIdAndNumberWithLatestWinning() {
        KittyImageStore store = new KittyImageStore();
        store.reserve(5, 77, 10, 10, 400);
        store.reserve(9, 77, 20, 20, 1600);
        assertEquals(5, store.resolveId(5, 0));
        assertEquals(9, store.resolveId(0, 77));
        store.remove(9);
        assertEquals("an older image with the same number becomes latest again",
            5, store.resolveId(0, 77));
        store.remove(5);
        assertEquals(0, store.resolveId(0, 77));
    }

    public void testAssignFreeIdSkipsStoredIds() {
        KittyImageStore store = new KittyImageStore();
        assertEquals(1, store.assignFreeId());
        store.reserve(1, 0, 1, 1, 4);
        store.reserve(2, 0, 1, 1, 4);
        assertEquals(3, store.assignFreeId());
        store.remove(1);
        assertEquals(1, store.assignFreeId());
    }

    public void testByteBudgetAndImageCountLimits() {
        KittyImageStore store = new KittyImageStore();
        int half = (int) (KittyImageStore.MAX_STORED_BYTES / 2);
        assertFalse(store.wouldExceedLimits(1, half));
        store.reserve(1, 0, 1, 1, half);
        assertFalse(store.wouldExceedLimits(2, half));
        assertTrue(store.wouldExceedLimits(2, half + 1));
        assertFalse("replacing an image reclaims its budget first",
            store.wouldExceedLimits(1, half + 1));
        for (long id = 2; id <= KittyImageStore.MAX_STORED_IMAGES; id++) store.reserve(id, 0, 1, 1, 0);
        assertTrue(store.wouldExceedLimits(9999, 0));
        assertFalse("replacement does not add a new image", store.wouldExceedLimits(5, 0));
    }

    public void testCompleteAttachesOnlyToLiveReservation() {
        KittyImageStore store = new KittyImageStore();
        store.reserve(4, 0, 2, 2, 16);
        assertEquals(16, store.totalBytes());
        assertTrue(store.complete(4, null, 32));
        assertEquals("real byte count replaces the estimate", 32, store.totalBytes());
        store.remove(4);
        assertEquals(0, store.totalBytes());
        assertFalse("a deleted reservation cannot be completed", store.complete(4, null, 32));
    }

    public void testAbandonOnlyRemovesPendingReservations() {
        KittyImageStore store = new KittyImageStore();
        store.reserve(4, 0, 2, 2, 16);
        store.complete(4, null, 16);
        store.abandon(4);
        assertNotNull("a completed image survives abandon", store.get(4));
        store.reserve(5, 0, 2, 2, 16);
        store.abandon(5);
        assertNull(store.get(5));
    }

    public void testClearEmptiesEverything() {
        KittyImageStore store = new KittyImageStore();
        store.reserve(1, 7, 2, 2, 16);
        store.clear();
        assertEquals(0, store.count());
        assertEquals(0, store.totalBytes());
        assertEquals(0, store.resolveId(0, 7));
    }

    /** An animated entry under an arbitrary id, so several can share one store. */
    private static KittyImageStore.Entry animatedEntry(KittyImageStore store, long id, int frames,
                                                       int gapMs, int frameBytes) {
        store.reserve(id, 0, 2, 2, 16);
        store.complete(id, null, 16);
        KittyImageStore.Entry entry = store.get(id);
        KittyImageStore.setFrameGap(entry, 1, gapMs);
        for (int i = 0; i < frames; i++) store.addFrame(entry, null, frameBytes, gapMs);
        return entry;
    }

    private static KittyImageStore.Entry animatedEntry(KittyImageStore store, int frames, int gapMs) {
        store.reserve(1, 0, 2, 2, 16);
        store.complete(1, null, 16);
        KittyImageStore.Entry entry = store.get(1);
        KittyImageStore.setFrameGap(entry, 1, gapMs);
        for (int i = 0; i < frames; i++) store.addFrame(entry, null, 16, gapMs);
        return entry;
    }

    public void testFrameBookkeepingAndGapEdits() {
        KittyImageStore store = new KittyImageStore();
        KittyImageStore.Entry entry = animatedEntry(store, 2, 100);
        assertEquals(3, KittyImageStore.frameCount(entry));
        assertEquals(300, entry.animationDurationMs);
        assertEquals(32, store.totalFrameBytes());
        KittyImageStore.setFrameGap(entry, 2, 40);
        assertEquals(240, entry.animationDurationMs);
        assertEquals(40, KittyImageStore.frameGap(entry, 2));
        KittyImageStore.setFrameGap(entry, 3, -5);
        assertEquals("negative gaps clamp to gapless", 0, KittyImageStore.frameGap(entry, 3));
        store.remove(1);
        assertEquals("removing the image releases its frame quota", 0, store.totalFrameBytes());
    }

    public void testAdvanceRunsLoopsAndStops() {
        KittyImageStore store = new KittyImageStore();
        KittyImageStore.Entry entry = animatedEntry(store, 2, 100);
        entry.animationState = KittyImageStore.ANIMATION_RUNNING;
        entry.maxLoops = 2; // v=3: loop twice
        entry.frameShownAtUptime = 1000;
        assertFalse("not due yet", KittyImageStore.advanceAnimation(entry, 1050));
        assertTrue(KittyImageStore.advanceAnimation(entry, 1100));
        assertEquals(1, entry.currentFrame);
        assertTrue(KittyImageStore.advanceAnimation(entry, 1200));
        assertEquals(2, entry.currentFrame);
        assertTrue("wraps to the root frame on loop", KittyImageStore.advanceAnimation(entry, 1300));
        assertEquals(0, entry.currentFrame);
        assertEquals(1, entry.currentLoop);
        for (int i = 0; i < 2; i++) {
            KittyImageStore.advanceAnimation(entry, 1400 + i * 100);
        }
        assertEquals(2, entry.currentFrame);
        assertFalse("second wrap exhausts the loop budget", KittyImageStore.advanceAnimation(entry, 1600));
        assertFalse("an exhausted animation reports no deadline",
            KittyImageStore.nextAnimationDeadline(entry) >= 0);
    }

    public void testAdvanceSkipsGaplessFramesAndWaitsWhenLoading() {
        KittyImageStore store = new KittyImageStore();
        KittyImageStore.Entry entry = animatedEntry(store, 3, 100);
        KittyImageStore.setFrameGap(entry, 2, 0); // frame 2 is gapless base data
        entry.animationState = KittyImageStore.ANIMATION_RUNNING;
        entry.frameShownAtUptime = 0;
        assertTrue(KittyImageStore.advanceAnimation(entry, 100));
        assertEquals("gapless frame 2 is skipped over", 2, entry.currentFrame);

        entry.animationState = KittyImageStore.ANIMATION_LOADING;
        entry.currentFrame = 3;
        entry.frameShownAtUptime = 200;
        assertFalse("loading mode waits at the last frame instead of looping",
            KittyImageStore.advanceAnimation(entry, 5000));
        assertEquals(3, entry.currentFrame);
        assertEquals("a waiting loading-mode animation must not spin the scheduler",
            -1, KittyImageStore.nextAnimationDeadline(entry));
    }

    /** Three frames of 100 ms each; the entry starts on the root frame at uptime 0. */
    private static KittyImageStore.Entry runningAnimation(KittyImageStore store, int maxLoops) {
        store.reserve(1, 0, 4, 4, 64);
        KittyImageStore.Entry entry = store.get(1);
        KittyImageStore.setFrameGap(entry, 1, 100);
        store.addFrame(entry, null, 64, 100);
        store.addFrame(entry, null, 64, 100);
        entry.animationState = KittyImageStore.ANIMATION_RUNNING;
        entry.maxLoops = maxLoops;
        entry.frameShownAtUptime = 0;
        return entry;
    }

    public void testCatchUpLandsWhereTheAnimationWouldHaveBeen() {
        KittyImageStore store = new KittyImageStore();
        KittyImageStore.Entry entry = runningAnimation(store, 0);
        assertTrue(KittyImageStore.catchUpAnimation(entry, 250));
        assertEquals("two whole gaps and half of a third", 2, entry.currentFrame);
        assertEquals("the half-gap already served still counts", 200, entry.frameShownAtUptime);
        // Playback carries on from there rather than restarting the gap.
        assertFalse(KittyImageStore.advanceAnimation(entry, 250));
        assertTrue(KittyImageStore.advanceAnimation(entry, 300));
        assertEquals(0, entry.currentFrame);
    }

    public void testCatchUpCollapsesWholeCyclesInsteadOfWalkingThem() {
        KittyImageStore store = new KittyImageStore();
        KittyImageStore.Entry entry = runningAnimation(store, 0);
        // A day of suspension at 300 ms a cycle: the walk must not step through 288,000 frames.
        assertTrue(KittyImageStore.catchUpAnimation(entry, 86_400_000L + 150));
        assertEquals(1, entry.currentFrame);
        assertEquals(86_400_000L + 100, entry.frameShownAtUptime);
    }

    public void testCatchUpCountsTheLoopsItSleptThroughAndStops() {
        KittyImageStore store = new KittyImageStore();
        KittyImageStore.Entry entry = runningAnimation(store, 3);
        assertFalse("ten cycles exhaust a three-loop animation",
            KittyImageStore.catchUpAnimation(entry, 3000));
        assertEquals(3, entry.currentLoop);
        assertFalse(KittyImageStore.isAnimatable(entry));
        assertEquals("a finished animation rests where it was left", 0, entry.currentFrame);
    }

    public void testCatchUpOnAStoppedAnimationOnlyResetsTheClock() {
        KittyImageStore store = new KittyImageStore();
        KittyImageStore.Entry entry = runningAnimation(store, 0);
        entry.animationState = KittyImageStore.ANIMATION_STOPPED;
        assertFalse(KittyImageStore.catchUpAnimation(entry, 5000));
        assertEquals(0, entry.currentFrame);
        assertEquals("it resumes its gap from now, not from before the suspension",
            5000, entry.frameShownAtUptime);
    }

    public void testCatchUpIsANoOpWithoutElapsedTime() {
        KittyImageStore store = new KittyImageStore();
        KittyImageStore.Entry entry = runningAnimation(store, 0);
        entry.frameShownAtUptime = 500;
        assertFalse(KittyImageStore.catchUpAnimation(entry, 500));
        assertEquals(0, entry.currentFrame);
        assertEquals(500, entry.frameShownAtUptime);
    }

    public void testRemoveFramePromotesRootAndFollowsCurrent() {
        KittyImageStore store = new KittyImageStore();
        KittyImageStore.Entry entry = animatedEntry(store, 3, 100);
        entry.currentFrame = 2;
        assertTrue(store.removeFrame(entry, 2));
        assertEquals(3, KittyImageStore.frameCount(entry));
        assertEquals("current frame index follows the removed predecessor", 1, entry.currentFrame);
        assertEquals(300, entry.animationDurationMs);

        assertTrue("deleting the root promotes frame 2", store.removeFrame(entry, 1));
        assertEquals(2, KittyImageStore.frameCount(entry));
        assertEquals(0, entry.currentFrame);
        assertEquals("the promoted frame's bytes move to the image quota",
            16, store.totalFrameBytes());

        assertTrue(store.removeFrame(entry, 99));
        assertEquals("an out-of-range number clamps to the last frame", 1, KittyImageStore.frameCount(entry));
        assertFalse("no extra frames left to delete", store.removeFrame(entry, 1));
    }

    public void testDropFramesReleasesTheQuotaAndStopsTheAnimation() {
        KittyImageStore store = new KittyImageStore();
        KittyImageStore.Entry entry = animatedEntry(store, 3, 100);
        entry.animationState = KittyImageStore.ANIMATION_RUNNING;
        entry.currentFrame = 2;
        entry.currentLoop = 1;
        entry.frameShownAtUptime = 5000;

        assertEquals(48, store.dropFrames(entry));
        assertEquals(0, store.totalFrameBytes());
        assertEquals("only the root frame is left", 1, KittyImageStore.frameCount(entry));
        assertEquals("the root frame becomes what is displayed", 0, entry.currentFrame);
        assertEquals(KittyImageStore.ANIMATION_STOPPED, entry.animationState);
        assertEquals("the root gap is all the duration that is left", 100, entry.animationDurationMs);
        assertFalse(KittyImageStore.isAnimatable(entry));
        assertEquals("the image itself survives and stays placeable", entry, store.get(1));
        assertEquals(0, store.dropFrames(entry));
    }

    public void testInFlightFramesHoldTheQuotaUntilTheyAreReleased() {
        KittyImageStore store = new KittyImageStore();
        store.reserve(1, 0, 8, 8, 256);
        KittyImageStore.Entry entry = store.get(1);
        int half = (int) (KittyImageStore.MAX_FRAME_BYTES / 2);
        store.reserveFrameBytes(entry, half);
        assertEquals(half, store.pendingFrameBytes());
        assertEquals("nothing has committed yet", 0, store.totalFrameBytes());
        assertFalse("the other half is still free", store.wouldExceedFrameLimits(entry, half));
        store.reserveFrameBytes(entry, half);
        assertTrue("two frames in flight fill the quota between them",
            store.wouldExceedFrameLimits(entry, 1));

        // A commit hands the charge over to the committed ledger, leaving the total unchanged.
        store.releaseFrameBytes(entry, half);
        store.addFrame(entry, null, half, 40);
        assertEquals(half, store.pendingFrameBytes());
        assertEquals(half, store.totalFrameBytes());
        assertTrue(store.wouldExceedFrameLimits(entry, 1));

        // A failure releases without committing, so the quota comes back.
        store.releaseFrameBytes(entry, half);
        assertEquals(0, store.pendingFrameBytes());
        assertFalse(store.wouldExceedFrameLimits(entry, half));
    }

    public void testInFlightFramesCountAgainstThePerImageFrameLimit() {
        KittyImageStore store = new KittyImageStore();
        store.reserve(1, 0, 2, 2, 16);
        KittyImageStore.Entry entry = store.get(1);
        for (int i = 0; i < KittyImageStore.MAX_FRAMES_PER_IMAGE; i++) store.reserveFrameBytes(entry, 16);
        assertTrue("frames in flight fill the count too", store.wouldExceedFrameLimits(entry, 16));
        store.releaseFrameBytes(entry, 16);
        assertFalse(store.wouldExceedFrameLimits(entry, 16));
    }

    public void testReleasingAcrossAClearCannotDriveTheLedgerNegative() {
        KittyImageStore store = new KittyImageStore();
        store.reserve(1, 0, 8, 8, 256);
        KittyImageStore.Entry entry = store.get(1);
        store.reserveFrameBytes(entry, 4096);
        store.clear();
        // The decode that was in flight during the clear still lands and releases.
        store.releaseFrameBytes(entry, 4096);
        assertEquals(0, store.pendingFrameBytes());
        store.reserve(2, 0, 8, 8, 256);
        assertFalse("a cleared store starts from a clean quota",
            store.wouldExceedFrameLimits(store.get(2), (int) KittyImageStore.MAX_FRAME_BYTES));
    }

    public void testReclaimFrameBudgetDropsOnlyUnreachableAnimations() {
        KittyImageStore store = new KittyImageStore();
        int frameBytes = (int) (KittyImageStore.MAX_FRAME_BYTES / 4);
        KittyImageStore.Entry orphan = animatedEntry(store, 1, 2, 100, frameBytes);
        KittyImageStore.Entry placed = animatedEntry(store, 2, 1, 100, frameBytes);
        KittyImageStore.Entry placeholder = animatedEntry(store, 3, 1, 100, frameBytes);
        store.putVirtualPlacement(placeholder,
            new KittyImageStore.VirtualPlacement(0, 0, 0, 2, 2, 2, 2));
        KittyImageStore.Entry incoming = animatedEntry(store, 4, 0, 100, frameBytes);

        assertTrue("the quota is full", store.wouldExceedFrameLimits(incoming, frameBytes));
        assertTrue(store.reclaimFrameBudget(incoming, frameBytes, id -> id == 2));
        assertEquals("the unreachable animation paid for the new frame", 1,
            KittyImageStore.frameCount(orphan));
        assertEquals("an animation with a placement is untouched", 2,
            KittyImageStore.frameCount(placed));
        assertEquals("an animation with a placeholder prototype is untouched", 2,
            KittyImageStore.frameCount(placeholder));
        assertFalse(store.wouldExceedFrameLimits(incoming, frameBytes));
    }

    public void testReclaimFrameBudgetFallsBackToTheOldestPlacedAnimation() {
        KittyImageStore store = new KittyImageStore();
        int frameBytes = (int) (KittyImageStore.MAX_FRAME_BYTES / 2);
        KittyImageStore.Entry oldest = animatedEntry(store, 1, 1, 100, frameBytes);
        KittyImageStore.Entry incoming = animatedEntry(store, 2, 1, 100, frameBytes);

        assertTrue("the quota is full with nothing unreachable in it",
            store.wouldExceedFrameLimits(incoming, frameBytes));
        assertTrue(store.reclaimFrameBudget(incoming, frameBytes, id -> true));
        assertEquals("the oldest animation gave up its motion", 1,
            KittyImageStore.frameCount(oldest));
        assertEquals("the growing animation never drops its own frames", 2,
            KittyImageStore.frameCount(incoming));
    }

    public void testReclaimFrameBudgetRefusesWhatCannotFitAtAll() {
        KittyImageStore store = new KittyImageStore();
        KittyImageStore.Entry entry = animatedEntry(store, 1, 1, 100, 16);
        int impossible = (int) Math.min(Integer.MAX_VALUE, KittyImageStore.MAX_FRAME_BYTES + 1);

        assertFalse("no amount of reclaiming fits a frame larger than the whole quota",
            store.reclaimFrameBudget(entry, impossible, id -> false));
        assertEquals("and nothing was thrown away chasing it", 2,
            KittyImageStore.frameCount(entry));

        KittyImageStore.Entry full = animatedEntry(store, 2, KittyImageStore.MAX_FRAMES_PER_IMAGE,
            100, 0);
        assertFalse("a per-image frame count is not a byte problem, so reclaiming cannot help",
            store.reclaimFrameBudget(full, 0, id -> false));
    }
}
