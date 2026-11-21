package com.termux.app.wall.pageswitch;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class PageBarLayoutTest {

    private static final float EPS = 0.01f;

    @Test public void bandHeightIsTopAirPillAndBottomAir() {
        assertEquals(64f, PageBarLayout.bandHeightDp(), EPS);
        assertEquals(128f, new PageBarLayout(2f, 800f, 3).bandHeightPx(), EPS);
    }

    @Test public void maxSlotsCountsMinSlotsInsideTheSideInsets() {
        assertEquals(8, PageBarLayout.maxSlots(1f, 360f));
        assertEquals(9, PageBarLayout.maxSlots(2f, 800f));
        assertEquals(1, PageBarLayout.maxSlots(1f, 10f));
    }

    @Test public void slotWidthStopsAtMaxAndShrinksWithCount() {
        assertEquals(53f, new PageBarLayout(1f, 360f, 3).slotWidthPx(), EPS);
        assertEquals(41f, new PageBarLayout(1f, 360f, 8).slotWidthPx(), EPS);
        assertEquals(36.44f, new PageBarLayout(1f, 360f, 9).slotWidthPx(), EPS);
    }

    @Test public void theKeysBarSpansThePaneWithEqualUncappedSlots() {
        PageBarLayout l = new PageBarLayout(1f, 360f, 3, true);
        assertEquals(328f / 3f, l.slotWidthPx(), EPS);
        assertEquals(328f, l.pillWidthPx(), EPS);
        assertEquals(16f, l.pillLeftPx(), EPS);
        assertEquals(16f + 328f / 6f, l.restCenterX(0), EPS);
    }

    @Test public void pillIsCentredAndSlotsFillItEndToEnd() {
        PageBarLayout l = new PageBarLayout(1f, 360f, 5);
        assertEquals(265f, l.pillWidthPx(), EPS);
        assertEquals(47.5f, l.pillLeftPx(), EPS);
        assertEquals(47.5f + 26.5f, l.restCenterX(0), EPS);
        assertEquals(360f - 47.5f - 26.5f, l.restCenterX(4), EPS);
    }

    @Test public void slotAtClampsAtTheEndsAndUsesRestSlots() {
        PageBarLayout l = new PageBarLayout(1f, 360f, 5);
        assertEquals(0, l.slotAt(-50f));
        assertEquals(0, l.slotAt(10f));
        assertEquals(0, l.slotAt(47.5f + 52f));
        assertEquals(1, l.slotAt(47.5f + 54f));
        assertEquals(2, l.slotAt(180f));
        assertEquals(4, l.slotAt(400f));
    }

    @Test public void subIndexSplitsAFoldedSlotEqually() {
        PageBarLayout l = new PageBarLayout(1f, 360f, 5);
        float left = l.pillLeftPx() + 2 * l.slotWidthPx();
        assertEquals(0, l.subIndexAt(left + 1f, 2, 3));
        assertEquals(1, l.subIndexAt(left + 26f, 2, 3));
        assertEquals(2, l.subIndexAt(left + 52f, 2, 3));
        assertEquals(2, l.subIndexAt(left + 90f, 2, 3));
        assertEquals(0, l.subIndexAt(left - 90f, 2, 3));
        assertEquals(0, l.subIndexAt(left + 30f, 2, 1));
    }

    private static final float[] c = new float[7];
    private static final float[] s = new float[7];

    /** Seven slots on a 420 px pane: slot 53, pill 371 wide from 24.5. */
    private static PageBarLayout seven() {
        return new PageBarLayout(1f, 420f, 7);
    }

    @Test public void slideKeepsEndsAndPlacesNeighboursAtOnePointTwoFiveSlots() {
        PageBarLayout l = seven();
        l.slidePositions(3f, 1f, c, s);
        float focusX = l.restCenterX(3);
        assertEquals(0f, s[3], EPS);
        assertEquals(focusX - 1.25f * 53f, c[2], EPS);
        assertEquals(focusX + 1.25f * 53f, c[4], EPS);
        assertEquals(1.25f, s[2], EPS);
        assertEquals(1.25f, s[4], EPS);
        assertEquals(l.restCenterX(0), c[0], EPS);
        assertEquals(l.restCenterX(6), c[6], EPS);
        assertEquals(1f, s[0], EPS);
        assertEquals(1f, s[6], EPS);
    }

    @Test public void slideSpacesGlyphsBetweenEndAndNeighbourEvenly() {
        PageBarLayout l = seven();
        l.slidePositions(3f, 1f, c, s);
        assertEquals((c[0] + c[2]) / 2f, c[1], EPS);
        assertEquals((c[4] + c[6]) / 2f, c[5], EPS);
        assertEquals(1f, s[1], EPS);
        assertEquals(1f, s[5], EPS);
    }

    @Test public void aNeighbourThatIsAnEndIsClampedInsideThePill() {
        PageBarLayout l = seven();
        l.slidePositions(1f, 1f, c, s);
        // rest(1) - 66.25 would sit left of the pill's inner edge plus half the scaled glyph.
        assertEquals(24.5f + 22f * 1.25f / 2f, c[0], EPS);
        assertEquals(1.25f, s[0], EPS);
        assertEquals(0f, s[1], EPS);
        l.slidePositions(5f, 1f, c, s);
        assertEquals(24.5f + 371f - 22f * 1.25f / 2f, c[6], EPS);
    }

    @Test public void aFoldedEndNeighbourKeepsItsPlatesInsideThePill() {
        PageBarLayout l = seven();
        boolean[] folded = new boolean[7];
        folded[6] = true;
        l.slidePositions(5f, 1f, c, s, folded);
        float unit = 1.25f;
        float pillRight = 24.5f + 371f;
        float reach = (11f + PageBarGlyphPainter.STACK_OVERHANG_RIGHT) * unit;
        assertEquals(pillRight, c[6] + reach, EPS);
        // Unfolded, the same slot sits further out.
        l.slidePositions(5f, 1f, c, s);
        assertEquals(pillRight - 11f * unit, c[6], EPS);
    }

    @Test public void focusOnAnEndDrawsNothingThereAndOnlyOneNeighbour() {
        PageBarLayout l = seven();
        l.slidePositions(0f, 1f, c, s);
        assertEquals(0f, s[0], EPS);
        assertEquals(l.restCenterX(0), c[0], EPS);
        assertEquals(l.restCenterX(0) + 1.25f * 53f, c[1], EPS);
        assertEquals(l.restCenterX(6), c[6], EPS);
        assertEquals(1f, s[6], EPS);
    }

    @Test public void fractionalFocusInterpolatesBetweenWholeLayouts() {
        PageBarLayout l = seven();
        float[] c2 = new float[7];
        float[] s2 = new float[7];
        l.slidePositions(2f, 1f, c, s);
        l.slidePositions(3f, 1f, c2, s2);
        float[] cm = new float[7];
        float[] sm = new float[7];
        l.slidePositions(2.25f, 1f, cm, sm);
        for (int i = 0; i < 7; i++) {
            assertEquals(c[i] + (c2[i] - c[i]) * 0.25f, cm[i], EPS);
            if (i == 2 || i == 3) continue;
            assertEquals(s[i] + (s2[i] - s[i]) * 0.25f, sm[i], EPS);
        }
        // The glyph nearer the finger is in the bubble; the other stays a full-size neighbour.
        assertEquals(0f, sm[2], EPS);
        assertEquals(PageBarLayout.NEIGHBOUR_SCALE, sm[3], EPS);
        l.slidePositions(2.75f, 1f, cm, sm);
        assertEquals(PageBarLayout.NEIGHBOUR_SCALE, sm[2], EPS);
        assertEquals(0f, sm[3], EPS);
    }

    @Test public void enterBlendsRestIntoTheSlideLayout() {
        PageBarLayout l = seven();
        l.slidePositions(3f, 0f, c, s);
        for (int i = 0; i < 7; i++) {
            assertEquals(l.restCenterX(i), c[i], EPS);
            assertEquals(1f, s[i], EPS);
        }
        l.slidePositions(3f, 0.5f, c, s);
        assertEquals(0.5f, s[3], EPS);
        assertEquals(1.125f, s[2], EPS);
    }

    @Test public void focusAtFollowsTheFingerAndClamps() {
        PageBarLayout l = seven();
        assertEquals(0f, l.focusAt(-10f), EPS);
        assertEquals(3f, l.focusAt(l.restCenterX(3)), EPS);
        assertEquals(3.5f, l.focusAt(l.restCenterX(3) + 26.5f), EPS);
        assertEquals(6f, l.focusAt(1000f), EPS);
    }

    @Test public void bubbleStaysInsideThePaneAndSitsAbovePill() {
        PageBarLayout l = new PageBarLayout(1f, 100f, 2);
        assertEquals(34f, l.bubbleCenterX(0), EPS);
        assertEquals(66f, l.bubbleCenterX(1), EPS);
        PageBarLayout wide = seven();
        assertEquals(wide.restCenterX(3), wide.bubbleCenterX(3), EPS);
        assertEquals(-46f, wide.bubbleCenterYFromPillTopPx(), EPS);
        assertEquals(-88f, wide.labelBaselineFromPillTopPx(), EPS);
    }

    @Test public void densityScalesEveryPxValue() {
        PageBarLayout l = new PageBarLayout(2f, 800f, 4);
        assertEquals(96f, l.pillHeightPx(), EPS);
        assertEquals(8f, l.pillTopPx(), EPS);
        assertEquals(44f, l.glyphPx(), EPS);
        assertEquals(106f, l.slotWidthPx(), EPS);
        assertEquals(-92f, l.bubbleCenterYFromPillTopPx(), EPS);
    }
}
