package com.termux.app.wall.pageswitch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MarksZoomPlanTest {

    private static final float MARK = 12f;
    private static final float DENSITY = 2f;
    private static final float DELTA = 1e-3f;

    private static MarksTouchLayout touch(float finger, float lineStart, float lineEnd) {
        MarksRowLayout rest = new MarksRowLayout(MarksRowLayoutTest.entries("HTA", 2, -1), null,
            true, MARK, 0f, 200f);
        return new MarksTouchLayout(rest, DENSITY, finger, 1, lineStart, lineEnd);
    }

    @Test public void theGroundHugsTheWidenedLineWithRoomForTheZoomedRing() {
        MarksTouchLayout t = touch(100f, 0f, 600f);
        MarksZoomPlan plan = new MarksZoomPlan(t, MARK, DENSITY, 0f, 600f, 0f);
        float pad = MarksZoomPlan.GROUND_PAD_DP * DENSITY;
        float zoomed = MARK * MarksTouchLayout.ZOOM_AT_FINGER;
        float ring = PageMarkShapes.RING_W * zoomed + PageMarkShapes.ringStroke(zoomed);
        float overhang = Math.max(0f, (ring - t.pitchPx()) / 2f);
        assertEquals(t.contentStartPx() - pad - overhang, plan.groundStart(), DELTA);
        assertEquals(t.contentStartPx() + t.contentLengthPx() + pad + overhang, plan.groundEnd(),
            DELTA);
        float tall = PageMarkShapes.RING_H * zoomed + PageMarkShapes.ringStroke(zoomed);
        assertEquals(tall / 2f + pad, plan.crossHalf(), DELTA);
        assertTrue("taller than the 16dp row", plan.crossHalf() * 2f > 16f * DENSITY);
        assertEquals("a capsule", plan.crossHalf(), plan.groundRadius(), DELTA);
    }

    @Test public void theFrameHoldsTheGroundAndTheWholeRowForTheTitle() {
        MarksZoomPlan plan = new MarksZoomPlan(touch(100f, 0f, 600f), MARK, DENSITY, 0f, 600f,
            120f);
        assertEquals(Math.min(plan.groundStart(), 0f), plan.frameStart(), DELTA);
        assertEquals(Math.max(plan.groundEnd(), 600f), plan.frameEnd(), DELTA);
    }

    @Test public void theTitleTakesTheReadingsRoomOrAShareOfTheLine() {
        assertEquals(80f, MarksZoomPlan.titleMaxPx(80f, 600f), DELTA);
        assertEquals(600f * MarksZoomPlan.TITLE_LINE_SHARE, MarksZoomPlan.titleMaxPx(0f, 600f),
            DELTA);
        assertEquals("never more than half the line", 300f, MarksZoomPlan.titleMaxPx(500f, 600f),
            DELTA);
        MarksZoomPlan none = new MarksZoomPlan(touch(100f, 0f, 600f), MARK, DENSITY, 0f, 600f, 0f);
        assertEquals(0f, none.titleWidth(), DELTA);
        assertFalse("no title never moves", none.titleAtStart(599f));
    }

    @Test public void theTitleGoesRoundToTheStartWhileTheFingerIsOverItsPlace() {
        MarksZoomPlan plan = new MarksZoomPlan(touch(100f, 0f, 600f), MARK, DENSITY, 0f, 600f,
            120f);
        assertFalse(plan.titleAtStart(479f));
        assertTrue(plan.titleAtStart(480f));
        assertTrue(plan.titleAtStart(599f));
        float w = 90f;
        assertEquals("against the end", 600f - w, plan.titleStartAt(0f, w), DELTA);
        assertEquals("fully out past the end", 600f, plan.titleStartAt(1f, w), DELTA);
        assertEquals("and in against the start", 0f, plan.titleWrapStartAt(1f, w), DELTA);
        // Halfway, one half leaves past the end as the other comes in from the start.
        assertEquals(600f - w / 2f, plan.titleStartAt(0.5f, w), DELTA);
        assertEquals(-w / 2f, plan.titleWrapStartAt(0.5f, w), DELTA);
    }
}
