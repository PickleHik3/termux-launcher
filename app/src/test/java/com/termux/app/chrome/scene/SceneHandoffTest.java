package com.termux.app.chrome.scene;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/** The pure parts of the scene thread's handoff: the event ring and the image ledger. */
public class SceneHandoffTest {

    private final float[] hills = new float[SceneEvents.MAX_HILLS * 4];
    private final float[] sparks = new float[SceneEvents.MAX_SPARKS * 4];
    private final float[] pulseRect = new float[SceneEvents.MAX_PULSES * 4];
    private final float[] pulseT = new float[SceneEvents.MAX_PULSES * 2];
    private final float[] basins = new float[SceneEvents.MAX_BASINS * 4];

    @Test public void ringDeliversEveryKindInOrder() {
        SceneEventRing ring = new SceneEventRing();
        SceneEvents events = new SceneEvents();
        assertTrue(ring.isEmpty());
        assertTrue(ring.push(SceneEventRing.HILL, 10f, 20f, 0f, 0f, 1000L));
        assertTrue(ring.push(SceneEventRing.SPARK, 30f, 40f, 0f, 0f, 1100L));
        assertTrue(ring.push(SceneEventRing.PULSE, 50f, 60f, 7f, 8f, 1200L));
        assertFalse(ring.isEmpty());
        assertEquals(3, ring.drainInto(events));
        assertTrue(ring.isEmpty());
        assertEquals(0, ring.drainInto(events));
        events.fill(1300L, 1f, hills, sparks, pulseRect, pulseT, basins);
        assertEquals(10f, hills[0], 0f);
        assertEquals(20f, hills[1], 0f);
        assertTrue(hills[2] > 0f);
        assertEquals(30f, sparks[0], 0f);
        assertTrue(sparks[3] > 0f);
        assertEquals(50f, pulseRect[0], 0f);
        assertEquals(8f, pulseRect[3], 0f);
        assertTrue(pulseT[1] > 0f);
    }

    @Test public void fullRingDropsTheNewRecordAndFreesSlotsOnDrain() {
        SceneEventRing ring = new SceneEventRing();
        for (int i = 0; i < SceneEventRing.CAPACITY; i++) {
            assertTrue(ring.push(SceneEventRing.HILL, i, 0f, 0f, 0f, 1000L + i));
        }
        assertFalse(ring.push(SceneEventRing.HILL, 99f, 0f, 0f, 0f, 2000L));
        assertEquals(SceneEventRing.CAPACITY, ring.drainInto(new SceneEvents()));
        assertTrue(ring.push(SceneEventRing.SPARK, 1f, 1f, 0f, 0f, 3000L));
        // The indices wrapped past the capacity once; the record still arrives.
        SceneEvents events = new SceneEvents();
        assertEquals(1, ring.drainInto(events));
        assertTrue(events.active(3100L));
    }

    @Test public void ringSurvivesAProducerAndAConsumerRacing() throws Exception {
        final SceneEventRing ring = new SceneEventRing();
        final int total = 5000;
        final int[] accepted = {0};
        Thread producer = new Thread(() -> {
            for (int i = 0; i < total; i++) {
                if (ring.push(SceneEventRing.HILL, i, 0f, 0f, 0f, i)) accepted[0]++;
                else Thread.yield();
            }
        });
        SceneEvents events = new SceneEvents();
        producer.start();
        int taken = 0;
        while (producer.isAlive() || !ring.isEmpty()) taken += ring.drainInto(events);
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
