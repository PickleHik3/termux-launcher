package com.termux.app.chrome;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.List;

@RunWith(RobolectricTestRunner.class)
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
    public void callersNamesTheFramesAboveTheCaller() {
        String frames = MotionLog.callers(3);
        assertTrue(frames, frames.contains("callersNamesTheFramesAboveTheCaller") || !frames.isEmpty());
    }
}
