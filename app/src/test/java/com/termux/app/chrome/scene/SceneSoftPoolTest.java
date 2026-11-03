package com.termux.app.chrome.scene;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import org.junit.Test;

import java.nio.ByteBuffer;

public class SceneSoftPoolTest {

    @Test
    public void rotationNeverReturnsCurrentOrPrevious() {
        SceneSoftPool pool = new SceneSoftPool(3);
        int cur = -1, prev = -1;
        for (int i = 0; i < 20; i++) {
            int slot = pool.acquire();
            assertNotEquals(-1, slot);
            assertNotEquals(cur, slot);
            assertNotEquals(prev, slot);
            pool.published(slot);
            prev = cur;
            cur = slot;
        }
    }

    @Test
    public void unpublishedAcquireNeverHitsCurrent() {
        SceneSoftPool pool = new SceneSoftPool(3);
        pool.published(pool.acquire());
        pool.published(pool.acquire());
        int a = pool.acquire();
        int b = pool.acquire(); // a was never published (a failed copy)
        assertNotEquals(pool.current(), a);
        assertNotEquals(pool.current(), b);
    }

    @Test
    public void tooSmallPoolSkips() {
        SceneSoftPool pool = new SceneSoftPool(2);
        pool.published(pool.acquire());
        pool.published(pool.acquire());
        assertEquals(-1, pool.acquire());
    }

    @Test
    public void copiesPaddedRowsPacked() {
        int w = 2, h = 2, rowStride = 12;
        byte[] raw = new byte[rowStride * (h - 1) + w * 4];
        for (int y = 0; y < h; y++) {
            for (int i = 0; i < w * 4; i++) raw[y * rowStride + i] = (byte) (y * 10 + i + 1);
        }
        ByteBuffer dst = ByteBuffer.allocate(w * h * 4);
        SceneSoftPool.copyPacked(ByteBuffer.wrap(raw), rowStride, 4, w, h, dst);
        assertEquals(16, dst.remaining());
        byte[] out = new byte[16];
        dst.get(out);
        for (int i = 0; i < 8; i++) assertEquals(i + 1, out[i]);
        for (int i = 0; i < 8; i++) assertEquals(11 + i, out[8 + i]);
    }

    @Test
    public void copiesWidePixelStride() {
        byte[] raw = {1, 2, 3, 4, 9, 9, 9, 9, 5, 6, 7, 8, 9, 9, 9, 9};
        ByteBuffer dst = ByteBuffer.allocate(8);
        SceneSoftPool.copyPacked(ByteBuffer.wrap(raw), 16, 8, 2, 1, dst);
        byte[] out = new byte[8];
        dst.get(out);
        for (int i = 0; i < 8; i++) assertEquals(i + 1, out[i]);
    }
}
