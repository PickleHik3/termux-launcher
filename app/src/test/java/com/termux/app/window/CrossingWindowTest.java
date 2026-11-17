package com.termux.app.window;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/** The window a page crossing keeps open for the terminal's resize and redraw counters. */
public class CrossingWindowTest {

    private static final class Recorder implements CrossingWindow.Sink {
        final List<String> counted = new ArrayList<>();
        final List<String> ringed = new ArrayList<>();

        @Override public void count(String name, String detail) {
            counted.add(name + " " + detail);
        }

        @Override public void ring(String event, String kv) {
            ringed.add(event + " " + kv);
        }
    }

    private static final long MS = 1_000_000L;

    @Test
    public void aResizeBetweenBeginAndSettleCounts() {
        Recorder sink = new Recorder();
        CrossingWindow window = new CrossingWindow(sink);
        window.open(100);
        window.resized(150, "ab12", "80x24", "80x20", "layout");

        assertEquals(1, sink.counted.size());
        assertEquals("crossing-resized pane=ab12 from=80x24 to=80x20 cause=layout",
            sink.counted.get(0));
    }

    @Test
    public void aResizeOutsideACrossingDoesNotCount() {
        Recorder sink = new Recorder();
        CrossingWindow window = new CrossingWindow(sink);
        window.resized(50, "ab12", "80x24", "80x20", "layout");
        window.open(100);
        window.close();
        window.resized(200, "ab12", "80x20", "80x24", "layout");

        assertTrue(sink.counted.isEmpty());
    }

    @Test
    public void aCancelledCrossingClosesTheWindow() {
        Recorder sink = new Recorder();
        CrossingWindow window = new CrossingWindow(sink);
        window.open(100);
        window.close();

        assertFalse(window.isOpen(101));
        window.resized(102, "p", "a", "b", "c");
        assertTrue(sink.counted.isEmpty());
    }

    @Test
    public void aWindowNeverSettledClosesItselfAfterTheBackstop() {
        Recorder sink = new Recorder();
        CrossingWindow window = new CrossingWindow(sink);
        window.open(100);

        assertTrue(window.isOpen(100 + CrossingWindow.STALE_MS));
        assertFalse(window.isOpen(101 + CrossingWindow.STALE_MS));
        window.resized(200 + CrossingWindow.STALE_MS, "p", "a", "b", "c");
        assertTrue(sink.counted.isEmpty());
    }

    @Test
    public void aSlowDrawCountsAndAFastOneDoesNot() {
        Recorder sink = new Recorder();
        CrossingWindow window = new CrossingWindow(sink);
        window.open(100);
        window.drew(110, "ab12", 3 * MS);
        window.drew(120, "ab12", 8 * MS);
        assertTrue(sink.counted.isEmpty());

        window.drew(130, "ab12", 9 * MS);
        assertEquals(1, sink.counted.size());
        assertEquals("crossing-redraw-slow pane=ab12 ms=9", sink.counted.get(0));
    }

    @Test
    public void aDrawOutsideACrossingIsNotCounted() {
        Recorder sink = new Recorder();
        CrossingWindow window = new CrossingWindow(sink);
        window.drew(10, "ab12", 30 * MS);

        assertTrue(sink.counted.isEmpty());
    }

    @Test
    public void landingWritesOneRingLineWithTheDrawCountAndTheLongestDraw() {
        Recorder sink = new Recorder();
        CrossingWindow window = new CrossingWindow(sink);
        window.open(100);
        window.drew(110, "a", 2 * MS);
        window.drew(120, "b", 12 * MS);
        window.drew(130, "a", 4 * MS);
        window.close();
        window.close();

        assertEquals(1, sink.ringed.size());
        assertEquals("crossing-draws draws=3 maxMs=12", sink.ringed.get(0));
    }

    @Test
    public void aNewCrossingStartsItsDrawTallyAtZero() {
        Recorder sink = new Recorder();
        CrossingWindow window = new CrossingWindow(sink);
        window.open(100);
        window.drew(110, "a", 12 * MS);
        window.open(120);
        window.close();

        assertEquals("crossing-draws draws=0 maxMs=0", sink.ringed.get(0));
    }
}
