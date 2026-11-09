package com.termux.terminal;

import junit.framework.TestCase;

import java.util.HashMap;
import java.util.Map;

/** The Ghostty core's bitmap cache on its own: what it keeps, what it charges, what it drops. */
public class GhosttyImageCacheTest extends TestCase {

    /** Images by id: {width, height, generation}; pixels are the id in every slot. */
    static final class FakeSource implements GhosttyImageCache.Source {
        final Map<Integer, long[]> images = new HashMap<>();
        int infoCalls, copies;

        @Override
        public boolean imageInfo(int imageId, long[] out) {
            infoCalls++;
            long[] image = images.get(imageId);
            if (image == null) return false;
            System.arraycopy(image, 0, out, 0, 3);
            return true;
        }

        @Override
        public int copyImageRows(int imageId, long generation, int firstRow, int rows, int[] argb) {
            long[] image = images.get(imageId);
            if (image == null || image[2] != generation) return -1;
            copies++;
            int count = (int) Math.min(rows, image[1] - firstRow);
            java.util.Arrays.fill(argb, 0, count * (int) image[0], imageId);
            return count;
        }

        @Override
        public long imageGeneration(int imageId) {
            long[] image = images.get(imageId);
            return image == null ? 0 : image[2];
        }
    }

    /** A bitmap here is its pixel array. */
    static final class ArrayBitmaps implements GhosttyImageCache.Bitmaps<int[]> {
        int created;

        @Override
        public int[] create(int width, int height) {
            created++;
            return new int[width * height];
        }

        @Override
        public void setRows(int[] bitmap, int[] argb, int width, int y, int rows) {
            System.arraycopy(argb, 0, bitmap, y * width, rows * width);
        }
    }

    private final FakeSource mSource = new FakeSource();
    private final ArrayBitmaps mBitmaps = new ArrayBitmaps();
    private final KittyImageStore.FrameBudget mBudget = new KittyImageStore.FrameBudget();

    private GhosttyImageCache<int[]> cache(long limit) {
        return new GhosttyImageCache<>(mSource, mBitmaps, mBudget, limit);
    }

    public void testAnImageIsCopiedOnceAndCheckedOncePerFrame() {
        mSource.images.put(1, new long[] {3, 2, 10});
        GhosttyImageCache<int[]> cache = cache(1000);
        cache.beginFrame(1);
        GhosttyImageCache.Entry<int[]> entry = cache.get(1);
        assertEquals(1, entry.bitmap[5]);
        assertEquals(24, cache.bytes());
        assertEquals(24, mBudget.used());
        assertSame(entry, cache.get(1));
        assertEquals("checked once in a frame", 1, mSource.infoCalls);
        cache.beginFrame(2);
        assertSame(entry, cache.get(1));
        assertEquals(2, mSource.infoCalls);
        assertEquals("same generation, no copy", 1, mBitmaps.created);
        assertEquals(10, cache.generationOf(1));
    }

    public void testANewGenerationRefillsTheSameBitmap() {
        mSource.images.put(1, new long[] {2, 2, 10});
        GhosttyImageCache<int[]> cache = cache(1000);
        cache.beginFrame(1);
        int[] first = cache.get(1).bitmap;
        mSource.images.put(1, new long[] {2, 2, 11});
        cache.beginFrame(2);
        GhosttyImageCache.Entry<int[]> entry = cache.get(1);
        assertSame(first, entry.bitmap);
        assertEquals(11, entry.generation);
        assertEquals(16, mBudget.used());
    }

    public void testImagesNotDrawnForTwoFramesAreDropped() {
        mSource.images.put(1, new long[] {1, 1, 1});
        mSource.images.put(2, new long[] {1, 1, 1});
        GhosttyImageCache<int[]> cache = cache(1000);
        cache.beginFrame(1);
        cache.get(1);
        cache.get(2);
        cache.beginFrame(2);
        cache.get(1);
        cache.beginFrame(3);
        assertEquals(1, cache.size());
        assertEquals(4, mBudget.used());
        cache.touch(1);
        cache.beginFrame(4);
        assertEquals("touched images stay", 1, cache.size());
    }

    public void testTheLimitEvictsWhatThisFrameDoesNotDraw() {
        mSource.images.put(1, new long[] {10, 1, 1});
        mSource.images.put(2, new long[] {10, 1, 1});
        mSource.images.put(3, new long[] {10, 1, 1});
        GhosttyImageCache<int[]> cache = cache(80);
        cache.beginFrame(1);
        cache.get(1);
        cache.get(2);
        assertEquals(80, mBudget.used());
        cache.beginFrame(2);
        cache.get(2);
        assertNotNull("1 was not drawn this frame, so it makes room", cache.get(3));
        assertEquals(80, mBudget.used());
        assertNull("both held images are drawn this frame: no room", cache.get(1));
        assertEquals(80, mBudget.used());
        assertNull("bigger than the whole limit", cacheWith(4, new long[] {100, 1, 1}, cache));
    }

    private GhosttyImageCache.Entry<int[]> cacheWith(int id, long[] image, GhosttyImageCache<int[]> cache) {
        mSource.images.put(id, image);
        return cache.get(id);
    }

    public void testTheBudgetIsSharedWithOtherSpenders() {
        mSource.images.put(1, new long[] {10, 1, 1});
        mBudget.spend(70);
        GhosttyImageCache<int[]> cache = cache(100);
        cache.beginFrame(1);
        assertNull(cache.get(1));
        mBudget.spend(-70);
        assertNotNull(cache.get(1));
    }

    public void testReleaseGivesEverythingBack() {
        mSource.images.put(1, new long[] {4, 4, 1});
        GhosttyImageCache<int[]> cache = cache(1000);
        cache.beginFrame(1);
        cache.get(1);
        cache.release();
        assertEquals(0, cache.bytes());
        assertEquals(0, mBudget.used());
        assertEquals(0, cache.size());
    }

    public void testAnImageThatWentIsDropped() {
        mSource.images.put(1, new long[] {4, 4, 1});
        GhosttyImageCache<int[]> cache = cache(1000);
        cache.beginFrame(1);
        cache.get(1);
        mSource.images.remove(1);
        cache.beginFrame(2);
        assertNull(cache.get(1));
        assertEquals(0, mBudget.used());
        assertEquals(0, cache.generationOf(1));
    }

    public void testWideImagesCopyInBands() {
        int width = GhosttyImageCache.BAND_PIXELS / 4 + 1;
        mSource.images.put(1, new long[] {width, 9, 1});
        GhosttyImageCache<int[]> cache = cache(Long.MAX_VALUE);
        cache.beginFrame(1);
        GhosttyImageCache.Entry<int[]> entry = cache.get(1);
        assertEquals(1, entry.bitmap[width * 9 - 1]);
        assertEquals("three rows a band", 3, mSource.copies);
    }
}
