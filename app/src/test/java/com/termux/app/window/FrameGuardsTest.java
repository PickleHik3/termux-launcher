package com.termux.app.window;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * The three guards (ADR 0028 section 5): foreign writer, landing wrote, crossing laid out. They
 * count and log, never throw, and cost nothing while the motion log is off.
 */
public class FrameGuardsTest {

    private final float[] view = {0.5f, 10f};
    private final List<String> ring = new ArrayList<>();
    private FrameGuards guards;
    private FrameGuards.Writer writer;
    private int alpha;
    private int tx;

    @Before
    public void setUp() {
        Counters.clear();
        Counters.enable(true, (event, kv) -> ring.add(event + " " + kv));
        guards = new FrameGuards();
        writer = guards.writer("dock");
        alpha = writer.own("alpha", () -> view[0]);
        tx = writer.own("translationX", () -> view[1]);
    }

    @After
    public void tearDown() {
        Counters.enable(false, null);
        Counters.clear();
    }

    private void write(int slot, float value) {
        view[slot == alpha ? 0 : 1] = value;
        writer.wrote(slot);
    }

    @Test
    public void aValueTheWriterDidNotWriteIsAForeignWriter() {
        writer.check();
        write(alpha, 0.8f);
        view[0] = 0.2f; // someone else

        writer.check();

        assertEquals(1, Counters.countOf(FrameGuards.FOREIGN_WRITER));
        assertTrue(ring.toString(), ring.get(0).startsWith("guard foreign-writer dock alpha wrote=0.8 now=0.2"));
    }

    @Test
    public void aForeignWriteIsCountedOnceNotOnEveryLaterCheck() {
        writer.check();
        view[1] = 99f;

        writer.check();
        writer.check();

        assertEquals(1, Counters.countOf(FrameGuards.FOREIGN_WRITER));
    }

    @Test
    public void theWritersOwnWritesAreNeverForeign() {
        writer.check();
        write(alpha, 0.1f);
        write(tx, 4f);
        writer.check();
        write(alpha, 0.9f);
        writer.check();

        assertEquals(0, Counters.countOf(FrameGuards.FOREIGN_WRITER));
    }

    @Test
    public void changingAPropertyOnTheLandingFrameIsLandingWrote() {
        writer.check();
        write(alpha, 0.5f);
        guards.setLanding(true);

        write(tx, 12f);

        assertEquals(1, Counters.countOf(FrameGuards.LANDING_WROTE));
        assertTrue(ring.toString(), ring.get(0).startsWith("guard landing-wrote dock translationX was=10.0 now=12.0"));
    }

    @Test
    public void aLandingFrameThatWritesTheRestAgainWroteNothing() {
        writer.check();
        write(tx, 10f);
        guards.setLanding(true);

        write(tx, 10f);
        write(alpha, 0.5f);

        assertEquals(0, Counters.countOf(FrameGuards.LANDING_WROTE));
    }

    @Test
    public void aChangeOffTheLandingFrameIsNotLandingWrote() {
        writer.check();
        guards.setLanding(false);

        write(tx, 40f);

        assertEquals(0, Counters.countOf(FrameGuards.LANDING_WROTE));
    }

    @Test
    public void aLayoutRequestFromAWriterIsCountedAndSaysWhetherACrossingWasUnderWay() {
        guards.layoutRequested("CHROME_COMMIT", "late in pre-draw");
        guards.setCrossing(true);
        guards.layoutRequested("dock", "requestLayout");

        assertEquals(2, Counters.countOf(FrameGuards.CROSSING_LAID_OUT));
        assertEquals("guard crossing-laid-out CHROME_COMMIT late in pre-draw", ring.get(0));
        assertEquals("guard crossing-laid-out dock requestLayout crossing", ring.get(1));
    }

    @Test
    public void withTheMotionLogOffNoGuardReadsOrCountsAndTurningItOnResyncsFirst() {
        Counters.enable(false, null);
        int[] reads = {0};
        FrameGuards.Writer other = guards.writer("slab");
        int slot = other.own("alpha", () -> {
            reads[0]++;
            return view[0];
        });
        other.check();
        view[0] = 0.9f;
        other.wrote(slot);
        guards.layoutRequested("slab", "x");
        assertEquals("nothing is read while off", 0, reads[0]);
        assertTrue(Counters.lines().isEmpty());

        Counters.enable(true, null);
        view[0] = 0.3f; // changed while it was off
        other.check();

        assertEquals("the first check after turning on only resyncs", 0,
            Counters.countOf(FrameGuards.FOREIGN_WRITER));
    }

    @Test
    public void manyOwnedPropertiesGrowTheWriter() {
        FrameGuards.Writer big = guards.writer("big");
        float[] values = new float[10];
        int[] slots = new int[10];
        for (int i = 0; i < 10; i++) {
            final int index = i;
            slots[i] = big.own("p" + i, () -> values[index]);
        }
        big.check();
        values[9] = 5f;
        big.check();

        assertEquals(1, Counters.countOf(FrameGuards.FOREIGN_WRITER));
        assertEquals(9, slots[9]);
    }
}
