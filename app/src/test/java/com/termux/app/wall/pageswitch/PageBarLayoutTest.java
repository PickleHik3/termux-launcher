package com.termux.app.wall.pageswitch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.termux.app.ScrubWave;

import org.junit.Test;

public class PageBarLayoutTest {

    private static final float EPS = 0.01f;

    @Test public void bandIsTheTouchRow() {
        assertEquals(48f, PageBarLayout.bandHeightDp(), EPS);
        assertEquals(96f, new PageBarLayout(2f, 800f, 3).bandHeightPx(), EPS);
    }

    @Test public void maxSlotsCountsMinSlotsInsideTheSideInsets() {
        assertEquals(8, PageBarLayout.maxSlots(1f, 360f));
        assertEquals(9, PageBarLayout.maxSlots(2f, 800f));
        assertEquals(1, PageBarLayout.maxSlots(1f, 10f));
    }

    @Test public void homeSlotsAreTheFoldsSlotWidth() {
        assertEquals(40f, new PageBarLayout(1f, 360f, 3).slotWidthPx(), EPS);
        assertEquals(40f, new PageBarLayout(1f, 360f, 8).slotWidthPx(), EPS);
        assertEquals(36.44f, new PageBarLayout(1f, 360f, 9).slotWidthPx(), EPS);
    }

    @Test public void theKeysBarSpansThePaneWithEqualUncappedSlots() {
        PageBarLayout l = new PageBarLayout(1f, 360f, 3, true);
        assertEquals(328f / 3f, l.slotWidthPx(), EPS);
        assertEquals(328f, l.pillWidthPx(), EPS);
        assertEquals(16f, l.pillLeftPx(), EPS);
        assertEquals(16f + 328f / 6f, l.restCenterX(0), EPS);
        // Its slab is as wide as its touch row: the pop-up's width rule is unchanged.
        assertEquals(l.pillLeftPx(), l.slabLeftPx(), EPS);
        assertEquals(l.pillWidthPx(), l.slabWidthPx(), EPS);
    }

    @Test public void pillIsCentredAndSlotsFillItEndToEnd() {
        PageBarLayout l = new PageBarLayout(1f, 360f, 5);
        assertEquals(200f, l.pillWidthPx(), EPS);
        assertEquals(80f, l.pillLeftPx(), EPS);
        assertEquals(100f, l.restCenterX(0), EPS);
        assertEquals(260f, l.restCenterX(4), EPS);
    }

    @Test public void theSlabHugsTheGlyphsAndKeepsTheirRhythmToItsEnds() {
        PageBarLayout l = new PageBarLayout(1f, 360f, 5);
        assertEquals(24.5f, l.slabHeightPx(), EPS);
        // Was 87.75 and 184.5 (the end glyph 4 dp from the slab's round end against 23.5 dp
        // between glyphs); the end glyph now stands half a slot in, as the pitch between glyphs.
        assertEquals(80f, l.slabLeftPx(), EPS);
        assertEquals(200f, l.slabWidthPx(), EPS);
        assertEquals(44f, l.slabBottomPx(), EPS);
        assertEquals(19.5f, l.slabTopPx(), EPS);
        assertEquals(31.75f, l.slabCenterYPx(), EPS);
        // The end glyph's centre is half the pitch from the slab's end (11.75 dp of air to its
        // box), the gaps between glyphs unchanged at 23.5 dp; every glyph's top the gap inside.
        assertEquals(l.slotWidthPx() / 2f, l.restCenterX(0) - l.slabLeftPx(), EPS);
        assertEquals(11.75f, (l.restCenterX(0) - l.glyphPx() / 2f) - l.slabLeftPx(), EPS);
        assertEquals(23.5f, l.restCenterX(1) - l.restCenterX(0) - l.glyphPx(), EPS);
        assertEquals(4f, (l.slabCenterYPx() - l.glyphPx() / 2f) - l.slabTopPx(), EPS);
        // And the slab's bottom the gap above the touch row's, the dock's top.
        assertEquals(4f, l.pillBottomPx() - l.slabBottomPx(), EPS);
    }

    @Test public void slotAtClampsAtTheEndsAndUsesRestSlots() {
        PageBarLayout l = new PageBarLayout(1f, 360f, 5);
        assertEquals(0, l.slotAt(-50f));
        assertEquals(0, l.slotAt(10f));
        assertEquals(0, l.slotAt(80f + 39f));
        assertEquals(1, l.slotAt(80f + 41f));
        assertEquals(2, l.slotAt(180f));
        assertEquals(4, l.slotAt(400f));
    }

    @Test public void subIndexSplitsAFoldedSlotEqually() {
        PageBarLayout l = new PageBarLayout(1f, 360f, 5);
        float left = l.pillLeftPx() + 2 * l.slotWidthPx();
        assertEquals(0, l.subIndexAt(left + 1f, 2, 3));
        assertEquals(1, l.subIndexAt(left + 20f, 2, 3));
        assertEquals(2, l.subIndexAt(left + 30f, 2, 3));
        assertEquals(2, l.subIndexAt(left + 90f, 2, 3));
        assertEquals(0, l.subIndexAt(left - 90f, 2, 3));
        assertEquals(0, l.subIndexAt(left + 30f, 2, 1));
    }

    @Test public void focusAtFollowsTheFingerAndClamps() {
        PageBarLayout l = seven();
        assertEquals(0f, l.focusAt(-10f), EPS);
        assertEquals(3f, l.focusAt(l.restCenterX(3)), EPS);
        assertEquals(3.5f, l.focusAt(l.restCenterX(3) + 20f), EPS);
        assertEquals(6f, l.focusAt(1000f), EPS);
    }

    /** Seven slots on a 420 px pane at density 1. */
    private static PageBarLayout seven() {
        return new PageBarLayout(1f, 420f, 7);
    }

    // ---- Sizes at the phone's densities -----------------------------------------------------

    @Test public void bandPillAndSlotsAtPhoneDensitiesForTwoToEightGlyphs() {
        for (float d : new float[] {2.625f, 3f}) {
            for (int n = 2; n <= 8; n++) {
                PageBarLayout l = new PageBarLayout(d, 1080f, n);
                String at = "d=" + d + " n=" + n;
                assertEquals(at, 48f * d, l.bandHeightPx(), EPS);
                assertEquals(at, 48f * d, l.pillHeightPx(), EPS);
                assertEquals(at, 40f * d, l.slotWidthPx(), EPS);
                assertEquals(at, 40f * d * n, l.pillWidthPx(), EPS);
                assertEquals(at, 24.5f * d, l.slabHeightPx(), EPS);
                assertEquals(at, 40f * d * n, l.slabWidthPx(), EPS);
                assertEquals(at, 44f * d, l.slabBottomPx(), EPS);
                assertEquals(at, 16.5f * d, l.glyphPx(), EPS);
                // As wide as its touch row, the end glyphs half a slot from its ends.
                assertEquals(at, l.slabLeftPx() - l.pillLeftPx(),
                    l.pillLeftPx() + l.pillWidthPx() - l.slabLeftPx() - l.slabWidthPx(), EPS);
                assertEquals(at, l.slotWidthPx() / 2f, l.restCenterX(0) - l.slabLeftPx(), EPS);
                assertEquals(at, (1080f - l.pillWidthPx()) / 2f, l.pillLeftPx(), EPS);
            }
        }
    }

    @Test public void fontScaleMovesOnlyTheName() {
        for (float d : new float[] {2.625f, 3f}) {
            PageBarLayout l = new PageBarLayout(d, 1080f, 5);
            for (float fontScale : new float[] {1f, 1.3f}) {
                // A 13sp line's descent, about a quarter of its size.
                float descent = 13f * fontScale * d * 0.26f;
                float baseline = l.labelBaselinePx(2, 2f, 1f, descent);
                float glyphTop = l.liftedCenterYPx(2, 2f, 1f) - l.raisedGlyphPx() / 2f;
                assertEquals(4f * d, glyphTop - (baseline + descent), EPS);
                assertEquals(48f * d, l.bandHeightPx(), EPS);
                assertEquals(l.labelBaselinePeakPx(descent), baseline, EPS);
            }
        }
    }

    @Test public void theNameStaysInsideThePane() {
        PageBarLayout l = new PageBarLayout(1f, 200f, 2);
        assertEquals(16f, l.labelLeftPx(0, 150f), EPS);
        assertEquals(200f - 16f - 150f, l.labelLeftPx(1, 150f), EPS);
        assertEquals(l.restCenterX(0) - 10f, l.labelLeftPx(0, 20f), EPS);
        assertEquals(168f, l.labelRoomPx(), EPS);
    }

    // ---- The lift ---------------------------------------------------------------------------

    @Test public void theGlyphUnderTheFingerIsFullyLiftedAndRaised() {
        PageBarLayout l = seven();
        assertEquals(l.liftPeakPx(), l.liftPx(3, 3f, 1f), EPS);
        assertEquals(PageBarLayout.RAISED_SCALE, l.scaleAt(3, 3f, 1f), EPS);
        // Its bottom clears the slab's top by the A–Z bar's lift for the letter under the finger.
        float bottom = l.liftedCenterYPx(3, 3f, 1f) + l.raisedGlyphPx() / 2f;
        assertEquals(ScrubWave.LIFT_DP * ScrubWave.FOCUS_LIFT_GAIN, l.slabTopPx() - bottom, EPS);
        assertEquals(1f, l.outOfSlab(3, 3f, 1f), EPS);
    }

    @Test public void theRaisedGlyphIsClearlyLargerThanTheRestGlyph() {
        PageBarLayout l = new PageBarLayout(2.625f, 1080f, 4);
        // 0.4 of the old 44dp bubble glyph (17.6dp) would not be; the raised glyph is 1.5x rest.
        assertTrue(0.4f * 44f <= 1.25f * PageBarLayout.GLYPH_DP);
        assertEquals(24.75f * 2.625f, l.raisedGlyphPx(), EPS);
        assertTrue(l.raisedGlyphPx() > 1.25f * l.glyphPx());
    }

    @Test public void neighboursFallOffOnTheAzWave() {
        PageBarLayout l = seven();
        float peak = l.liftPeakPx();
        for (int i = 0; i < 7; i++) {
            float env = ScrubWave.envelope(Math.abs(i - 3f));
            assertEquals("lift " + i, ScrubWave.lift(env) * peak, l.liftPx(i, 3f, 1f), EPS);
            assertEquals("scale " + i, 1f + 0.5f * env, l.scaleAt(i, 3f, 1f), EPS);
        }
        assertTrue(l.liftPx(2, 3f, 1f) < peak);
        assertTrue(l.liftPx(1, 3f, 1f) < l.liftPx(2, 3f, 1f));
        assertEquals(l.liftPx(2, 3f, 1f), l.liftPx(4, 3f, 1f), EPS);
    }

    @Test public void everythingRestsWhenTheFingerIsAway() {
        PageBarLayout l = seven();
        for (int i = 0; i < 7; i++) {
            assertEquals(0f, l.liftPx(i, 3f, 0f), 0f);
            assertEquals(1f, l.scaleAt(i, 3f, 0f), 0f);
            assertEquals(l.slabCenterYPx(), l.liftedCenterYPx(i, 3f, 0f), 0f);
            assertEquals(0f, l.outOfSlab(i, 3f, 0f), 0f);
        }
    }

    @Test public void enterScalesTheWholeLift() {
        PageBarLayout l = seven();
        assertEquals(l.liftPeakPx() / 2f, l.liftPx(3, 3f, 0.5f), EPS);
        assertEquals(1.25f, l.scaleAt(3, 3f, 0.5f), EPS);
    }

    @Test public void theLiftIsContinuousAsTheFingerMoves() {
        PageBarLayout l = seven();
        float step = 0.001f;
        float worstLift = 0f;
        float worstScale = 0f;
        for (int i = 0; i < 7; i++) {
            float lastLift = l.liftPx(i, 0f, 1f);
            float lastScale = l.scaleAt(i, 0f, 1f);
            for (int k = 1; k <= 6000; k++) {
                float f = k * step;
                float lift = l.liftPx(i, f, 1f);
                float scale = l.scaleAt(i, f, 1f);
                worstLift = Math.max(worstLift, Math.abs(lift - lastLift));
                worstScale = Math.max(worstScale, Math.abs(scale - lastScale));
                lastLift = lift;
                lastScale = scale;
            }
        }
        // A thousandth of a slot moves no glyph by more than a tenth of a pixel.
        assertTrue("lift jump " + worstLift, worstLift < 0.1f);
        assertTrue("scale jump " + worstScale, worstScale < 0.002f);
    }

    @Test public void aimingAtTheLiftedGlyphDoesNotCancel() {
        PageBarLayout l = seven();
        float top = l.liftedCenterYPx(3, 3f, 1f) - l.raisedGlyphPx() / 2f;
        assertTrue(top > l.pillTopPx() - l.cancelPx());
    }

    @Test public void densityScalesEveryPxValue() {
        PageBarLayout l = new PageBarLayout(2f, 800f, 4);
        assertEquals(96f, l.pillHeightPx(), EPS);
        assertEquals(0f, l.pillTopPx(), EPS);
        assertEquals(33f, l.glyphPx(), EPS);
        assertEquals(80f, l.slotWidthPx(), EPS);
        assertEquals(49f, l.slabHeightPx(), EPS);
        assertEquals(2f * 42.625f, l.liftPeakPx(), EPS);
    }
}
