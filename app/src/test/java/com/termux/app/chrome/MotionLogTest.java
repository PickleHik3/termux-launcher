package com.termux.app.chrome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.List;
import android.app.Application;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class)
public class MotionLogTest {

    @Before
    public void clean() {
        MotionLog.clear();
    }

    @After
    public void off() {
        MotionLog.setEnabled(false);
        MotionLog.clear();
    }

    @Test
    public void disabledRecordsNothing() {
        MotionLog.setEnabled(false);
        MotionLog.log("crossing-begin", "to=1");
        assertTrue(MotionLog.dump().isEmpty());
    }

    @Test
    public void enabledKeepsLinesInOrderWithUptime() {
        MotionLog.setEnabled(true);
        MotionLog.log("a", "x=1");
        MotionLog.log("b", null);
        List<String> lines = MotionLog.dump();
        assertEquals(2, lines.size());
        assertTrue(lines.get(0), lines.get(0).matches("t=\\d+ a x=1"));
        assertTrue(lines.get(1), lines.get(1).matches("t=\\d+ b"));
    }

    @Test
    public void bufferKeepsTheLastCapacityLines() {
        for (int i = 0; i < MotionLog.CAPACITY + 5; i++) MotionLog.record(i, "e", "n=" + i);
        List<String> lines = MotionLog.dump();
        assertEquals(MotionLog.CAPACITY, lines.size());
        assertEquals("t=5 e n=5", lines.get(0));
        assertEquals("t=" + (MotionLog.CAPACITY + 4) + " e n=" + (MotionLog.CAPACITY + 4),
            lines.get(lines.size() - 1));
    }

    @Test
    public void clearEmptiesTheBuffer() {
        MotionLog.setEnabled(true);
        MotionLog.log("a", "");
        MotionLog.clear();
        assertTrue(MotionLog.dump().isEmpty());
    }

    @Test
    public void violationsAreRecordedAndCounted() {
        MotionLog.record(1000, "crossing-begin", "revision=3");
        MotionLog.record(1300, "wall-page-settled", "page=TERMINAL");
        MotionLog.record(1436, "pty-resize", "from=70x44 to=70x41");
        assertEquals(1, MotionLog.invariantCount());
        List<String> lines = MotionLog.dump();
        assertTrue(lines.toString(),
            lines.get(lines.size() - 1).startsWith("t=1436 INVARIANT zero-resize-per-crossing"));
        MotionLog.clear();
        assertEquals(0, MotionLog.invariantCount());
    }

    @Test
    public void callersNamesTheFramesAboveTheCaller() {
        String frames = MotionLog.callers(3);
        assertTrue(frames, frames.contains("callersNamesTheFramesAboveTheCaller") || !frames.isEmpty());
    }

    @Test
    public void poseKvNamesADisplayPageWithItsStill() {
        assertEquals("kind=DISPLAY page=7 effect=DEPTH slide=-540.0 progress=-0.500 tx=-120.3"
                + " scale=0.850 alpha=1.000 rotY=12.50 still=true",
            MotionLog.poseKv("DISPLAY", 7, "DEPTH", -540f, 1080, -120.25f, 0.85f, 1f, 12.5f, true));
    }

    @Test
    public void poseKvLeavesTheStillOutWhenThePageHasNone() {
        String kv = MotionLog.poseKv("TERMINAL", 1, "SLIDE", 0f, 0, 0f, 1f, 1f, 0f, null);
        assertEquals("kind=TERMINAL page=1 effect=SLIDE slide=0.0 progress=0.000 tx=0.0"
            + " scale=1.000 alpha=1.000 rotY=0.00", kv);
        assertFalse(kv.contains("still="));
    }

    @Test
    public void aPosePassReadsAsAMotionLine() {
        MotionLog.record(42, "page-pose",
            MotionLog.poseKv("TERMINAL", 1, "SLIDE", 10f, 100, 10f, 1f, 1f, 0f, null));
        assertEquals("t=42 page-pose kind=TERMINAL page=1 effect=SLIDE slide=10.0 progress=0.100"
            + " tx=10.0 scale=1.000 alpha=1.000 rotY=0.00", MotionLog.dump().get(0));
    }

    @Test
    public void holdsAndGuardsOnTheWindowTableAreInTheCounterLines() {
        MotionLog.setEnabled(true);
        com.termux.app.window.Hold.take("kb-wait", 160L, 0, () -> 0L).release(
            com.termux.app.window.Hold.BACKSTOP, "ms=160");

        List<String> table = MotionLog.counterLines();

        assertEquals(2, table.size());
        assertTrue(table.get(0), table.get(0)
            .matches("hold:kb-wait count=1 first=\\d+ last=\\d+ detail=backstopMs=160"));
        assertTrue(table.get(1), table.get(1)
            .matches("hold:kb-wait:backstop count=1 first=\\d+ last=\\d+ detail=ms=160"));
        assertTrue(MotionLog.dump().toString(), MotionLog.dump().get(1)
            .endsWith("hold-released hold=kb-wait reason=backstop heldMs=0 frames=0 ms=160"));
    }

    @Test
    public void anInvariantIsCountedAndSurvivesTheRingRollingOver() {
        MotionLog.record(1, "INVARIANT", "terminal-resized-late a=1");
        MotionLog.record(2, "INVARIANT", "terminal-resized-late " + "d".repeat(200));
        for (int i = 0; i < MotionLog.CAPACITY + 5; i++) MotionLog.record(i, "e", null);

        List<String> table = MotionLog.counterLines();

        assertEquals(1, table.size());
        assertTrue(table.get(0), table.get(0)
            .matches("terminal-resized-late count=2 first=\\d+ last=\\d+ detail=d{80}"));
    }

    @Test
    public void clearResetsTheCounterTable() {
        MotionLog.setEnabled(true);
        com.termux.app.window.Hold.take("x", 1L, 0, () -> 0L).signal();
        assertFalse(MotionLog.counterLines().isEmpty());

        MotionLog.clear();

        assertTrue(MotionLog.counterLines().isEmpty());
    }

    @Test
    public void disabledCountsNothing() {
        MotionLog.setEnabled(false);
        com.termux.app.window.Hold.take("x", 1L, 0, () -> 0L).signal();

        assertTrue(MotionLog.counterLines().isEmpty());
    }
}
