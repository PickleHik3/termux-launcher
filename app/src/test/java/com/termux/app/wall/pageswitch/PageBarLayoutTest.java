package com.termux.app.wall.pageswitch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.termux.app.ScrubWave;

import org.junit.Test;

public class PageBarLayoutTest {

    private static final float EPS = 0.01f;
    /** Home's slot at density 1, in px: every Home number below is derived from it. */
    private static final float S = PageBarLayout.MIN_SLOT_DP;

    @Test public void bandIsTheTouchRow() {
        assertEquals(48f, PageBarLayout.bandHeightDp(), EPS);
        assertEquals(96f, new PageBarLayout(2f, 800f, 3).bandHeightPx(), EPS);
    }

    @Test public void maxSlotsCountsMinSlotsInsideTheSideInsets() {
        assertEquals((int) Math.floor((360f - 32f) / S), PageBarLayout.maxSlots(1f, 360f));
        assertEquals((int) Math.floor((800f - 64f) / (2f * S)), PageBarLayout.maxSlots(2f, 800f));
        assertEquals(1, PageBarLayout.maxSlots(1f, 10f));
    }

    @Test public void homeSlotsAreTheFoldsSlotWidth() {
        assertEquals(PageBarLayout.MIN_SLOT_DP, new PageBarLayout(1f, 360f, 3).slotWidthPx(), EPS);
        assertEquals(PageBarLayout.MIN_SLOT_DP, new PageBarLayout(1f, 360f, 8).slotWidthPx(), EPS);
        // More slots than fit at S share the room inside the side insets.
        int many = (int) Math.ceil(328f / S) + 1;
        assertEquals(328f / many, new PageBarLayout(1f, 360f, many).slotWidthPx(), EPS);
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
        assertEquals(5f * S, l.pillWidthPx(), EPS);
        assertEquals((360f - 5f * S) / 2f, l.pillLeftPx(), EPS);
        assertEquals(l.pillLeftPx() + S / 2f, l.restCenterX(0), EPS);
        assertEquals(l.pillLeftPx() + 4.5f * S, l.restCenterX(4), EPS);
    }

    @Test public void theSlabHugsTheGlyphsAndKeepsTheirRhythmToItsEnds() {
        PageBarLayout l = new PageBarLayout(1f, 360f, 5);
        // One rule: a square cell per glyph, the slab their row.
        assertEquals(S, l.slabHeightPx(), EPS);
        // The end glyph stands half a slot in, as the pitch between glyphs.
        assertEquals((360f - 5f * S) / 2f, l.slabLeftPx(), EPS);
        assertEquals(5f * S, l.slabWidthPx(), EPS);
        assertEquals(44f, l.slabBottomPx(), EPS);
        assertEquals(44f - S, l.slabTopPx(), EPS);
        assertEquals(44f - S / 2f, l.slabCenterYPx(), EPS);
        // The end glyph's centre is half the pitch from the slab's end, the air between glyphs
        // a slot less a glyph; every glyph's top the gap inside.
        assertEquals(l.slotWidthPx() / 2f, l.restCenterX(0) - l.slabLeftPx(), EPS);
        assertEquals((S - 16.5f) / 2f, (l.restCenterX(0) - l.glyphPx() / 2f) - l.slabLeftPx(), EPS);
        assertEquals(S - 16.5f, l.restCenterX(1) - l.restCenterX(0) - l.glyphPx(), EPS);
        // Above and below the glyph the same air as at the ends: half the air between glyphs.
        assertEquals((S - 16.5f) / 2f, (l.slabCenterYPx() - l.glyphPx() / 2f) - l.slabTopPx(), EPS);
        // And the slab's bottom the gap above the touch row's, the dock's top.
        assertEquals(4f, l.pillBottomPx() - l.slabBottomPx(), EPS);
    }

    @Test public void theSlabNeverGoesBelowAGlyphAndItsGap() {
        assertEquals(16.5f + 8f, PageBarLayout.homeSlabHeightDp(10f), EPS);
        assertEquals(S, PageBarLayout.homeSlabHeightDp(S), EPS);
    }

    /** On the wave no two glyphs touch: the raised glyph and its neighbours keep air in a slot. */
    @Test public void liftedGlyphsNeverTouchTheirNeighbours() {
        PageBarLayout l = seven();
        float worst = Float.MAX_VALUE;
        for (int k = 0; k <= 600; k++) {
            float focus = k / 100f;
            for (int i = 0; i + 1 < 7; i++) {
                float half = l.glyphPx() * l.scaleAt(i, focus, 1f) / 2f;
                float next = l.glyphPx() * l.scaleAt(i + 1, focus, 1f) / 2f;
                worst = Math.min(worst, l.restCenterX(i + 1) - l.restCenterX(i) - half - next);
            }
        }
        assertTrue("air " + worst, worst > 0f);
    }

    @Test public void slotAtClampsAtTheEndsAndUsesRestSlots() {
        PageBarLayout l = new PageBarLayout(1f, 360f, 5);
        assertEquals(0, l.slotAt(-50f));
        assertEquals(0, l.slotAt(10f));
        float left = l.pillLeftPx();
        assertEquals(0, l.slotAt(left + S - 1f));
        assertEquals(1, l.slotAt(left + S + 1f));
        assertEquals(2, l.slotAt(left + 2.5f * S));
        assertEquals(4, l.slotAt(400f));
    }

    @Test public void subIndexSplitsAFoldedSlotEqually() {
        PageBarLayout l = new PageBarLayout(1f, 360f, 5);
        float left = l.pillLeftPx() + 2 * l.slotWidthPx();
        assertEquals(0, l.subIndexAt(left + 1f, 2, 3));
        assertEquals(1, l.subIndexAt(left + S * 0.5f, 2, 3));
        assertEquals(2, l.subIndexAt(left + S * 0.75f, 2, 3));
        assertEquals(2, l.subIndexAt(left + 90f, 2, 3));
        assertEquals(0, l.subIndexAt(left - 90f, 2, 3));
        assertEquals(0, l.subIndexAt(left + S * 0.75f, 2, 1));
    }

    @Test public void focusAtFollowsTheFingerAndClamps() {
        PageBarLayout l = seven();
        assertEquals(0f, l.focusAt(-10f), EPS);
        assertEquals(3f, l.focusAt(l.restCenterX(3)), EPS);
        assertEquals(3.5f, l.focusAt(l.restCenterX(3) + S / 2f), EPS);
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
                assertEquals(at, PageBarLayout.MIN_SLOT_DP * d, l.slotWidthPx(), EPS);
                assertEquals(at, PageBarLayout.MIN_SLOT_DP * d * n, l.pillWidthPx(), EPS);
                assertEquals(at, S * d, l.slabHeightPx(), EPS);
                assertEquals(at, PageBarLayout.MIN_SLOT_DP * d * n, l.slabWidthPx(), EPS);
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
        // Its bottom clears the well's top by LIFT_CLEAR_DP: past the rim, no further.
        float bottom = l.liftedCenterYPx(3, 3f, 1f) + l.raisedGlyphPx() / 2f;
        assertEquals(PageBarLayout.LIFT_CLEAR_DP, l.slabTopPx() - bottom, EPS);
        assertEquals(2f, PageBarLayout.LIFT_CLEAR_DP, 0f);
        assertEquals(1f, l.outOfSlab(3, 3f, 1f), EPS);
    }

    @Test public void theRaisedGlyphIsClearlyLargerThanTheRestGlyph() {
        PageBarLayout l = new PageBarLayout(2.625f, 1080f, 4);
        // A gentle swell: the raised glyph is 1.25x rest (20.625 dp), not the jump of 1.5x.
        assertEquals(20.625f * 2.625f, l.raisedGlyphPx(), EPS);
        assertTrue(l.raisedGlyphPx() > l.glyphPx());
    }

    /** The key's raised bar is a glass strip: the lifted glyph stays inside it, top and bottom. */
    @Test public void theRaisedBarsGlyphSwellsInsideItsStrip() {
        for (float d : new float[] {2.625f, 3f}) {
            PageBarLayout l = PageBarLayout.raised(d, 1080f, 5, 40f, 1000f);
            float peak = l.liftPeakPx();
            assertTrue(peak >= 0f);
            for (int k = 0; k <= 400; k++) {
                float focus = k / 100f;
                for (int i = 0; i < 5; i++) {
                    float half = l.glyphPx() * l.scaleAt(i, focus, 1f) / 2f;
                    float cy = l.liftedCenterYPx(i, focus, 1f);
                    assertTrue("top d=" + d, cy - half >= l.slabTopPx() - EPS);
                    assertTrue("bottom d=" + d, cy + half <= l.slabBottomPx() + EPS);
                }
            }
            // Fully up, its top LIFT_CLEAR_DP inside the strip's.
            float top = l.liftedCenterYPx(2, 2f, 1f) - l.raisedGlyphPx() / 2f;
            assertEquals(PageBarLayout.LIFT_CLEAR_DP * d, top - l.slabTopPx(), EPS);
        }
    }

    @Test public void onlyTheFocusGlyphLeavesTheWellAndTheNeighboursStayInside() {
        for (float d : new float[] {2f, 2.625f, 3f}) {
            PageBarLayout l = new PageBarLayout(d, 1080f, 6);
            float peak = l.liftPeakPx();
            for (int i = 0; i < 6; i++) {
                int slotsAway = Math.abs(i - 3);
                float lift = l.liftPx(i, 3f, 1f);
                float scale = l.scaleAt(i, 3f, 1f);
                if (slotsAway == 0) {
                    assertEquals(peak, lift, EPS);
                    assertEquals(PageBarLayout.RAISED_SCALE, scale, EPS);
                } else if (slotsAway == 1) {
                    assertEquals(0.25f * peak, lift, EPS);
                    assertTrue("scale " + scale, scale <= 1.07f && scale > 1f);
                    float top = l.liftedCenterYPx(i, 3f, 1f) - l.glyphPx() * scale / 2f;
                    assertTrue("pokes " + (l.slabTopPx() - top) / d,
                        l.slabTopPx() - top <= 1f * d + EPS);
                } else {
                    assertEquals(0f, lift, 0f);
                    assertEquals(1f, scale, 0f);
                }
            }
        }
    }

    @Test public void halfwayBetweenTwoSlotsTheyShareTheLiftEvenly() {
        PageBarLayout l = seven();
        assertEquals(l.liftPx(2, 2.5f, 1f), l.liftPx(3, 2.5f, 1f), EPS);
        assertTrue(l.liftPx(3, 2.5f, 1f) < l.liftPeakPx());
        assertEquals(0f, l.liftPx(1, 2.5f, 1f), 0f);
        assertEquals(0f, l.liftPx(4, 2.5f, 1f), 0f);
    }

    @Test public void theEnvelopeFallsMonotonicallyWithDistance() {
        float last = PageBarLayout.shape(0f);
        assertEquals(1f, last, 0f);
        for (int k = 1; k <= 300; k++) {
            float e = PageBarLayout.shape(k / 100f);
            assertTrue(e <= last + 1e-6f);
            last = e;
        }
        assertEquals(0f, last, 0f);
        assertEquals(PageBarLayout.NEIGHBOUR_SHARE, PageBarLayout.shape(1f), 1e-5f);
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
        assertEquals(1f + (PageBarLayout.RAISED_SCALE - 1f) / 2f, l.scaleAt(3, 3f, 0.5f), EPS);
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
        assertEquals(2f * S, l.slotWidthPx(), EPS);
        assertEquals(2f * S, l.slabHeightPx(), EPS);
        assertEquals(2f * (S / 2f + PageBarLayout.LIFT_CLEAR_DP + 20.625f / 2f),
            l.liftPeakPx(), EPS);
    }
}
