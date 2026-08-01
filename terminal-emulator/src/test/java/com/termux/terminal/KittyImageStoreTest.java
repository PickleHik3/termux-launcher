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
}
