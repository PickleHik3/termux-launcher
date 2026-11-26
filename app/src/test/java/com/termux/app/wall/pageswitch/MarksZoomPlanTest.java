package com.termux.app.wall.pageswitch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MarksZoomPlanTest {

    private static final float MARK = 12f;
    private static final float DENSITY = 2f;
    private static final float DELTA = 1e-3f;
    /** The thin row: 16dp, the strip 32dp, the reach above 3dp at this density, less than a zoomed mark stands past the thin row. */
    private static final float THIN = 32f;
    private static final float STRIP = 64f;
    private static final float REACH = 6f;
    private static final float ROW_TOP = 0f;

    private static MarksTouchLayout touch(float finger, float lineStart, float lineEnd) {
        MarksRowLayout rest = new MarksRowLayout(MarksRowLayoutTest.entries("HTA", 2, -1), null,
            true, MARK, 0f, 200f);
        return new MarksTouchLayout(rest, DENSITY, finger, 1, lineStart, lineEnd);
    }

    @Test public void theGroundHugsTheWidenedLineWithRoomForTheZoomedRing() {
        MarksTouchLayout t = touch(100f, 0f, 600f);
        MarksZoomPlan plan = new MarksZoomPlan(t, MARK, DENSITY, 0f, 600f, 0f, ROW_TOP, ROW_TOP + THIN, true, REACH);
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
            120f, ROW_TOP, ROW_TOP + THIN, true, REACH);
        assertEquals(Math.min(plan.groundStart(), 0f), plan.frameStart(), DELTA);
        assertEquals(Math.max(plan.groundEnd(), 600f), plan.frameEnd(), DELTA);
    }

    @Test public void theTitleTakesTheReadingsRoomOrAShareOfTheLine() {
        assertEquals(80f, MarksZoomPlan.titleMaxPx(80f, 600f), DELTA);
        assertEquals(600f * MarksZoomPlan.TITLE_LINE_SHARE, MarksZoomPlan.titleMaxPx(0f, 600f),
            DELTA);
        assertEquals("never more than half the line", 300f, MarksZoomPlan.titleMaxPx(500f, 600f),
            DELTA);
        MarksZoomPlan none = new MarksZoomPlan(touch(100f, 0f, 600f), MARK, DENSITY, 0f, 600f, 0f, ROW_TOP, ROW_TOP + THIN, true, REACH);
        assertEquals(0f, none.titleWidth(), DELTA);
        assertFalse("no title never moves", none.titleAtStart(599f));
    }

    @Test public void theTitleGoesRoundToTheStartWhileTheFingerIsOverItsPlace() {
        MarksZoomPlan plan = new MarksZoomPlan(touch(100f, 0f, 600f), MARK, DENSITY, 0f, 600f,
            120f, ROW_TOP, ROW_TOP + THIN, true, REACH);
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

    @Test public void inTheThinRowTheGroundReachesTheTouchReachAboveAndTheRestBelow() {
        MarksZoomPlan plan = new MarksZoomPlan(touch(100f, 0f, 600f), MARK, DENSITY, 0f, 600f,
            0f, 0f, THIN, true, REACH);
        float height = plan.groundCrossEnd() - plan.groundCrossStart();
        assertTrue("taller than the row", height > THIN);
        assertEquals("the reach above, no more", -REACH, plan.groundCrossStart(), DELTA);
        assertEquals("the rest below, over the first text line", THIN + height - THIN - REACH,
            plan.groundCrossEnd(), DELTA);
        assertEquals("the marks centred in the ground",
            (plan.groundCrossStart() + plan.groundCrossEnd()) / 2f, plan.markCross(), DELTA);
        assertEquals("the title on the row's line", THIN / 2f, plan.titleCross(), DELTA);
        assertEquals(plan.groundCrossStart(), plan.frameCrossStart(), DELTA);
        assertEquals(plan.groundCrossEnd(), plan.frameCrossEnd(), DELTA);
    }

    @Test public void inTheStripTheGroundStaysInsideTheBarWhenItFits() {
        // A small mark fits the 32dp bar with its ring and pad: centred inside it.
        float small = 8f;
        MarksTouchLayout t = touch(100f, 0f, 600f);
        MarksZoomPlan plan = new MarksZoomPlan(t, small, DENSITY, 0f, 600f, 0f, 0f, STRIP,
            true, REACH);
        assertTrue(plan.groundCrossStart() >= 0f);
        assertTrue(plan.groundCrossEnd() <= STRIP);
        assertEquals(STRIP / 2f, plan.markCross(), DELTA);
        assertEquals(0f, plan.frameCrossStart(), DELTA);
        assertEquals(STRIP, plan.frameCrossEnd(), DELTA);
        // A mark too tall for the bar follows the thin row's rule.
        MarksZoomPlan tall = new MarksZoomPlan(t, 40f, DENSITY, 0f, 600f, 0f, 0f, STRIP, true,
            REACH);
        assertTrue(tall.groundCrossEnd() - tall.groundCrossStart() > STRIP);
        assertTrue(tall.groundCrossStart() >= -REACH - DELTA);
    }

    @Test public void aColumnReachesTowardItsEdgeByTheReachAndInwardByTheRest() {
        float width = THIN;
        // A column on the right edge: outward is the end side across the line.
        MarksZoomPlan right = new MarksZoomPlan(touch(100f, 0f, 600f), MARK, DENSITY, 0f, 600f,
            0f, 0f, width, false, REACH);
        assertEquals(width + REACH, right.groundCrossEnd(), DELTA);
        assertTrue(right.groundCrossStart() < 0f);
        // On the left edge it mirrors.
        MarksZoomPlan left = new MarksZoomPlan(touch(100f, 0f, 600f), MARK, DENSITY, 0f, 600f,
            0f, 0f, width, true, REACH);
        assertEquals(-REACH, left.groundCrossStart(), DELTA);
        assertTrue(left.groundCrossEnd() > width);
    }
}
