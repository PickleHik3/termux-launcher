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
}
