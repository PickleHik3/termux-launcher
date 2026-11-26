package com.termux.app.wall.pageswitch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class MarksZoomPlanTest {

    private static final float MARK = 12f;
    private static final float DELTA = 1e-3f;
    /** The thin row, 32px; the strip, 64px. */
    private static final float THIN = 32f;
    private static final float STRIP = 64f;

    private static MarksTouchLayout touch(float lineEnd) {
        MarksRowLayout rest = new MarksRowLayout(MarksRowLayoutTest.entries("HTA", 2, -1), null,
            true, MARK, 200f);
        return new MarksTouchLayout(rest, 2f, 1, lineEnd);
    }

    private static MarksZoomPlan plan(float titleMax, float rowStart, float rowEnd) {
        return new MarksZoomPlan(touch(600f), MARK, 0f, 600f, titleMax, rowStart, rowEnd);
    }

    @Test public void theFrameHoldsTheRowAndTheMarksAndNothingElse() {
        MarksTouchLayout t = touch(600f);
        MarksZoomPlan p = plan(0f, 0f, THIN);
        assertEquals(Math.min(0f, t.contentStartPx()), p.frameStart(), DELTA);
        assertEquals(Math.max(600f, t.contentStartPx() + t.contentLengthPx()), p.frameEnd(), DELTA);
        // A zoomed 12px mark is 18px: it fits the 32px row, so the frame is the row.
        assertEquals(0f, p.frameCrossStart(), DELTA);
        assertEquals(THIN, p.frameCrossEnd(), DELTA);
        assertEquals(THIN / 2f, p.markCross(), DELTA);
    }

    @Test public void aMarkTallerThanTheRowReachesPastItEvenly() {
        float big = 40f;
        MarksZoomPlan p = new MarksZoomPlan(touch(600f), big, 0f, 600f, 0f, 0f, THIN);
        float half = big * MarksTouchLayout.ZOOM_AT_FINGER / 2f;
        assertEquals(THIN / 2f - half, p.frameCrossStart(), DELTA);
        assertEquals(THIN / 2f + half, p.frameCrossEnd(), DELTA);
        assertEquals(THIN / 2f, p.markCross(), DELTA);
        MarksZoomPlan strip = new MarksZoomPlan(touch(600f), big, 0f, 600f, 0f, 0f, STRIP);
        assertEquals("the strip holds it", 0f, strip.frameCrossStart(), DELTA);
        assertEquals(STRIP, strip.frameCrossEnd(), DELTA);
    }

    @Test public void theTitleTakesTheReadingsRoomOrAShareOfTheLine() {
        assertEquals(80f, MarksZoomPlan.titleMaxPx(80f, 600f), DELTA);
        assertEquals(600f * MarksZoomPlan.TITLE_LINE_SHARE, MarksZoomPlan.titleMaxPx(0f, 600f),
            DELTA);
        assertEquals("never more than half the line", 300f, MarksZoomPlan.titleMaxPx(500f, 600f),
            DELTA);
        MarksZoomPlan none = plan(0f, 0f, THIN);
        assertEquals(0f, none.titleWidth(), DELTA);
        assertFalse("no title never moves", none.titleAtStart(599f));
    }

    @Test public void theTitleGoesRoundToTheStartWhileTheFingerIsOverItsPlace() {
        MarksZoomPlan plan = plan(120f, 0f, THIN);
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
