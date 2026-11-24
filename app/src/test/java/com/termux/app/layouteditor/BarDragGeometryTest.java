package com.termux.app.layouteditor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.layouteditor.BarLandingPolicy.Bar;
import com.termux.app.layouteditor.BarLandingPolicy.Hold;
import com.termux.app.layouteditor.BarLandingPolicy.Landing;
import com.termux.app.layouteditor.BarLandingPolicy.Zone;
import com.termux.app.place.PlaceLayout.Edge;

import org.junit.Test;

/** A finger on the frame becomes a Hold, and a Landing becomes a line; plain JVM. */
public class BarDragGeometryTest {

    private static final float W = 400f;
    private static final float H = 800f;

    /** Status on top (0..40); apps (700..750) and extra keys (750..800) on the bottom. */
    private static BarDragGeometry.Frame frame() {
        BarDragGeometry.Frame frame = new BarDragGeometry.Frame(W, H);
        frame.addBand(Bar.STATUS_BAR, Edge.TOP, false, 0, 0, W, 40);
        frame.addBand(Bar.APPS_ROW, Edge.BOTTOM, false, 0, 700, W, 750);
        frame.addBand(Bar.EXTRA_KEYS, Edge.BOTTOM, false, 0, 750, W, 800);
        return frame;
    }

    @Test public void nearestSideChoosesTheEdge() {
        BarDragGeometry.Frame frame = frame();
        assertEquals(Edge.TOP, BarDragGeometry.holdAt(frame, Bar.APPS_ROW, 200, 100).edge);
        assertEquals(Edge.BOTTOM, BarDragGeometry.holdAt(frame, Bar.STATUS_BAR, 200, 600).edge);
        assertEquals(Edge.LEFT, BarDragGeometry.holdAt(frame, Bar.APPS_ROW, 20, 400).edge);
        assertEquals(Edge.RIGHT, BarDragGeometry.holdAt(frame, Bar.APPS_ROW, 390, 400).edge);
    }

    @Test public void theGapIsCountedAmongTheOtherBandsFromTheScreenEdge() {
        BarDragGeometry.Frame frame = frame();
        // Holding the status bar over the bottom stack: outer = extra keys (center 775).
        assertEquals(0, BarDragGeometry.holdAt(frame, Bar.STATUS_BAR, 200, 790).index);
        assertEquals(1, BarDragGeometry.holdAt(frame, Bar.STATUS_BAR, 200, 740).index);
        assertEquals(2, BarDragGeometry.holdAt(frame, Bar.STATUS_BAR, 200, 650).index);
    }

    @Test public void theHeldBarDoesNotCountItself() {
        BarDragGeometry.Frame frame = frame();
        // Extra keys held over its own stack: only apps remains (center 725).
        assertEquals(0, BarDragGeometry.holdAt(frame, Bar.EXTRA_KEYS, 200, 790).index);
        assertEquals(1, BarDragGeometry.holdAt(frame, Bar.EXTRA_KEYS, 200, 650).index);
    }

    @Test public void aSideEdgeCountsFromTheSide() {
        BarDragGeometry.Frame frame = new BarDragGeometry.Frame(W, H);
        frame.addBand(Bar.AZ_INDEX, Edge.RIGHT, false, 360, 0, W, H);
        assertEquals(0, BarDragGeometry.holdAt(frame, Bar.APPS_ROW, 395, 400).index);
        assertEquals(1, BarDragGeometry.holdAt(frame, Bar.APPS_ROW, 330, 400).index);
    }

    @Test public void belowTheKeyboardIsItsOwnGroup() {
        BarDragGeometry.Frame frame = new BarDragGeometry.Frame(W, H);
        frame.setKeyboard(0, 500, W, 700);
        frame.addBand(Bar.APPS_ROW, Edge.BOTTOM, false, 0, 450, W, 500);
        frame.addBand(Bar.EXTRA_KEYS, Edge.BOTTOM, true, 0, 750, W, 800);
        Hold under = BarDragGeometry.holdAt(frame, Bar.AZ_INDEX, 200, 780);
        assertTrue(under.underKeyboard);
        assertEquals(0, under.index);
        Hold underInner = BarDragGeometry.holdAt(frame, Bar.AZ_INDEX, 200, 720);
        assertTrue(underInner.underKeyboard);
        assertEquals(1, underInner.index);
        Hold over = BarDragGeometry.holdAt(frame, Bar.AZ_INDEX, 200, 520);
        assertFalse(over.underKeyboard);
    }

    @Test public void theHiddenColumnWinsWhereItStands() {
        BarDragGeometry.Frame frame = frame();
        frame.setHidden(400, 100, 440, 500);
        assertEquals(Zone.HIDDEN, BarDragGeometry.holdAt(frame, Bar.APPS_ROW, 420, 200).zone);
        assertEquals(Zone.EDGE, BarDragGeometry.holdAt(frame, Bar.APPS_ROW, 200, 200).zone);
    }

    @Test public void thePaneIsHeldInsideItAwayFromTheEdges() {
        BarDragGeometry.Frame frame = frame();
        frame.setPane(0, 40, W, 700, 48f);
        assertEquals(Zone.PANE, BarDragGeometry.holdAt(frame, Bar.STATUS_BAR, 200, 300).zone);
        assertEquals(Zone.EDGE, BarDragGeometry.holdAt(frame, Bar.STATUS_BAR, 200, 45).zone);
    }

    // ------------------------------------------------------------------------------ the line

    private static float[] line(BarDragGeometry.Frame frame, Bar bar, Landing landing) {
        float[] out = new float[4];
        assertTrue(BarDragGeometry.slotLine(frame, bar, landing, out));
        return out;
    }

    private static Landing edge(Edge edge, int index) {
        return new Landing(true, Zone.EDGE, edge, index, false, null, null);
    }

    @Test public void theLineSitsInTheGapBetweenBands() {
        BarDragGeometry.Frame frame = frame();
        // Over the bottom stack for the status bar: gaps at 800, 750, 700.
        assertEquals(800f, line(frame, Bar.STATUS_BAR, edge(Edge.BOTTOM, 0))[1], 0.01f);
        assertEquals(750f, line(frame, Bar.STATUS_BAR, edge(Edge.BOTTOM, 1))[1], 0.01f);
        assertEquals(700f, line(frame, Bar.STATUS_BAR, edge(Edge.BOTTOM, 2))[1], 0.01f);
        float[] top = line(frame, Bar.APPS_ROW, edge(Edge.TOP, 0));
        assertEquals(0f, top[1], 0.01f);
        assertEquals(0f, top[0], 0.01f);
        assertEquals(W, top[2], 0.01f);
    }

    @Test public void anEmptyEdgeDrawsAtTheScreenEdge() {
        BarDragGeometry.Frame frame = frame();
        float[] left = line(frame, Bar.APPS_ROW, edge(Edge.LEFT, 0));
        assertEquals(0f, left[0], 0.01f);
        assertEquals(0f, left[1], 0.01f);
        assertEquals(H, left[3], 0.01f);
    }

    @Test public void aRefusedLandingDrawsNoLine() {
        BarDragGeometry.Frame frame = frame();
        assertFalse(BarDragGeometry.slotLine(frame, Bar.APPS_ROW,
            new Landing(false, Zone.EDGE, Edge.TOP, 0, false, null, null), new float[4]));
    }
}
