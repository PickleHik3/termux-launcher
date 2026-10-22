package com.termux.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

public class LatencyInstrumentsTest {

    @Test
    public void echoInsideWindowIsInline() {
        assertTrue(EchoInlineGate.shouldRedrawInline(true, 1_030L, 1_000L, 0L));
        assertTrue(EchoInlineGate.shouldRedrawInline(true, 1_050L, 1_000L, 0L));
    }

    @Test
    public void outputOutsideWindowOrWithoutWriteIsNot() {
        assertFalse(EchoInlineGate.shouldRedrawInline(true, 1_051L, 1_000L, 0L));
        assertFalse(EchoInlineGate.shouldRedrawInline(true, 1_010L, 0L, 0L));
        assertFalse(EchoInlineGate.shouldRedrawInline(true, 990L, 1_000L, 0L));
    }

    @Test
    public void switchOffAlwaysPosts() {
        assertFalse(EchoInlineGate.shouldRedrawInline(false, 1_010L, 1_000L, 0L));
    }

    @Test
    public void floodsCoalesceBetweenInlineDraws() {
        assertFalse(EchoInlineGate.shouldRedrawInline(true, 1_010L, 1_000L, 1_005L));
        assertTrue(EchoInlineGate.shouldRedrawInline(true, 1_013L, 1_000L, 1_005L));
    }

    @Test
    public void switchDefaultsAndReadsAtMostOncePerSecond() {
        AtomicInteger reads = new AtomicInteger();
        String[] value = {null};
        DebugSwitch s = new DebugSwitch("k", true, key -> {
            reads.incrementAndGet();
            return value[0];
        });
        assertTrue(s.isOn(10_000L));
        value[0] = "0";
        assertTrue("cached inside the second", s.isOn(10_500L));
        assertEquals(1, reads.get());
        assertFalse(s.isOn(11_000L));
        value[0] = "garbage";
        assertTrue("anything else is the default", s.isOn(12_000L));
        assertEquals(3, reads.get());
    }

    @Test
    public void switchDefaultOffTurnsOnWithOne() {
        DebugSwitch s = new DebugSwitch("k", false, key -> "1");
        assertTrue(s.isOn(0L));
    }

    @Test
    public void ringReportsPercentilesOverItsWindow() {
        PercentileRing ring = new PercentileRing(10);
        assertEquals(0L, ring.percentile(50));
        for (int i = 1; i <= 10; i++) ring.add(i);
        assertEquals(5L, ring.percentile(50));
        assertEquals(9L, ring.percentile(90));
        assertEquals(10L, ring.max());
        // The oldest samples fall out: 11..20 replace 1..10.
        for (int i = 11; i <= 20; i++) ring.add(i);
        assertEquals(10, ring.size());
        assertEquals(15L, ring.percentile(50));
        assertEquals(20L, ring.max());
    }
}
