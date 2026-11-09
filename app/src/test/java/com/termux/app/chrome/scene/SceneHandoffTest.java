package com.termux.app.chrome.scene;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/** The pure parts of the scene thread's handoff: the pointer ring and the image ledger. */
public class SceneHandoffTest {

    private final float[] mouse = new float[4];

    @Test public void ringDeliversEveryKindInOrder() {
        SceneEventRing ring = new SceneEventRing();
        SceneTouch touch = new SceneTouch();
        assertTrue(ring.isEmpty());
        assertTrue(ring.push(SceneEventRing.DOWN, 10f, 20f));
        assertTrue(ring.push(SceneEventRing.MOVE, 30f, 40f));
        assertTrue(ring.push(SceneEventRing.UP, 50f, 60f));
        assertFalse(ring.isEmpty());
        assertEquals(3, ring.drainInto(touch));
        assertTrue(ring.isEmpty());
        assertEquals(0, ring.drainInto(touch));
        // View 100 tall at scale 1: y flips; up keeps the last position, z goes negative.
        touch.fill(mouse, 100f, 1f);
        assertEquals(50f, mouse[0], 0f);
        assertEquals(40f, mouse[1], 0f);
        assertEquals(-10f, mouse[2], 0f);
        assertEquals(80f, mouse[3], 0f);
    }

    @Test public void fullRingDropsTheNewRecordAndFreesSlotsOnDrain() {
        SceneEventRing ring = new SceneEventRing();
        for (int i = 0; i < SceneEventRing.CAPACITY; i++) {
            assertTrue(ring.push(SceneEventRing.MOVE, i, 0f));
        }
        assertFalse(ring.push(SceneEventRing.MOVE, 99f, 0f));
        assertEquals(SceneEventRing.CAPACITY, ring.drainInto(new SceneTouch()));
        assertTrue(ring.push(SceneEventRing.DOWN, 1f, 1f));
        // The indices wrapped past the capacity once; the record still arrives.
        SceneTouch touch = new SceneTouch();
        assertEquals(1, ring.drainInto(touch));
        assertTrue(touch.isDown());
    }

    @Test public void ringSurvivesAProducerAndAConsumerRacing() throws Exception {
        final SceneEventRing ring = new SceneEventRing();
        final int total = 5000;
        final int[] accepted = {0};
        Thread producer = new Thread(() -> {
            for (int i = 0; i < total; i++) {
                if (ring.push(SceneEventRing.MOVE, i, 0f)) accepted[0]++;
                else Thread.yield();
            }
        });
        SceneTouch touch = new SceneTouch();
        producer.start();
        int taken = 0;
        while (producer.isAlive() || !ring.isEmpty()) taken += ring.drainInto(touch);
        producer.join();
        assertEquals(accepted[0], taken);
    }

    private static final class Img {
        boolean closed;
    }

    private final List<Img> closedOrder = new ArrayList<>();

    private SceneImageLedger<Img> ledger() {
        return new SceneImageLedger<>(4, 2, 100L, img -> {
            assertFalse("closed twice", img.closed);
            img.closed = true;
            closedOrder.add(img);
        });
    }

    @Test public void oldImageClosesOnlyAfterEnoughNewerFramesAndTime() {
        SceneImageLedger<Img> ledger = ledger();
        Img a = new Img(), b = new Img(), c = new Img(), d = new Img();
        ledger.publish(a, 0L);
        ledger.publish(b, 70L);
        ledger.publish(c, 140L);
        // a has two newer frames, but b replaced it only 70 ms ago.
        assertFalse(a.closed);
        ledger.reap(169L);
        assertFalse(a.closed);
        ledger.reap(170L);
        assertTrue(a.closed);
        assertFalse(b.closed);
        // The frame on screen and the one just behind it stay, however old: each needs two newer ones.
        ledger.reap(100_000L);
        assertFalse(b.closed);
        assertFalse(c.closed);
        assertEquals(2, ledger.held());
        ledger.publish(d, 100_100L);
        assertTrue(b.closed);
        assertFalse(c.closed);
        assertFalse(d.closed);
    }

    @Test public void producerIsHeldBackWhenTheQueueWouldRunDry() {
        SceneImageLedger<Img> ledger = ledger();
        for (int i = 0; i < 4; i++) {
            assertTrue(ledger.canProduce());
            ledger.publish(new Img(), i);
        }
        assertFalse(ledger.canProduce());
        ledger.reap(1000L);
        assertTrue(ledger.canProduce());
    }

    @Test public void closeAllReturnsEverythingOnceInOrder() {
        SceneImageLedger<Img> ledger = ledger();
        Img a = new Img(), b = new Img();
        ledger.publish(a, 0L);
        ledger.publish(b, 10L);
        ledger.closeAll();
        assertTrue(a.closed && b.closed);
        assertEquals(0, ledger.held());
        assertEquals(a, closedOrder.get(0));
        ledger.closeAll();
        assertEquals(2, closedOrder.size());
    }
}
