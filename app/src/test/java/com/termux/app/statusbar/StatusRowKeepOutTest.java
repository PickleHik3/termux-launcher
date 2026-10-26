package com.termux.app.statusbar;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The status row's keep-out (#163) on a 1080 × 2400 phone with a centred camera hole 80px wide
 * and 90px tall, and 100px screen corners.
 */
public class StatusRowKeepOutTest {

    private static final int W = 1080;
    private static final int H = 2400;
    private static final int MARGIN = 11;

    private static StatusRowKeepOut.Screen screen(StatusRowKeepOut.Mode mode, boolean hole,
                                                  float radius, int customCentre,
                                                  int customWidth) {
        return new StatusRowKeepOut.Screen(hole, 500, 0, 580, 90, 80, radius, radius, radius,
            radius, mode, customCentre, customWidth, MARGIN);
    }

    private static StatusRowKeepOut.Screen auto() {
        return screen(StatusRowKeepOut.Mode.AUTO, true, 100f, 0, 0);
    }

    // ------------------------------------------------------------------ corner formula

    @Test public void cornerInsetFollowsTheCircle() {
        assertEquals(100f, StatusRowKeepOut.cornerInsetPx(100f, 0f), 1e-4f);
        assertEquals(100f, StatusRowKeepOut.cornerInsetPx(100f, -5f), 1e-4f);
        // r - sqrt(r² - (r - y)²) at y = 50: 100 - sqrt(7500).
        assertEquals(100f - (float) Math.sqrt(7500), StatusRowKeepOut.cornerInsetPx(100f, 50f),
            1e-3f);
        assertEquals(0f, StatusRowKeepOut.cornerInsetPx(100f, 100f), 0f);
        assertEquals(0f, StatusRowKeepOut.cornerInsetPx(100f, 140f), 0f);
        assertEquals(0f, StatusRowKeepOut.cornerInsetPx(0f, 0f), 0f);
    }

    // ------------------------------------------------------------------ answers

    @Test public void aRowInTheCameraBandLeavesTheHoleAndClearsTheCorners() {
        StatusRowKeepOut.Answer answer =
            StatusRowKeepOut.answer(auto(), W, H, 0, 10, W, 76);
        // At the row's top (y = 10) the curve stands 100 - sqrt(100² - 90²) = 56.4 in.
        assertEquals(57, answer.leftInsetPx);
        assertEquals(57, answer.rightInsetPx);
        assertTrue(answer.hasGap());
        assertEquals(500 - MARGIN, answer.gapStartPx);
        assertEquals(580 + MARGIN, answer.gapEndPx);
    }

    @Test public void aFloatingRowCountsItsOwnMarginTowardsTheCorner() {
        StatusRowKeepOut.Answer answer =
            StatusRowKeepOut.answer(auto(), W, H, 30, 10, W - 30, 76);
        assertEquals(27, answer.leftInsetPx);
        assertEquals(27, answer.rightInsetPx);
        assertEquals(500 - MARGIN - 30, answer.gapStartPx);
        assertEquals(580 + MARGIN - 30, answer.gapEndPx);
    }

    @Test public void aRowBelowTheHoleAndTheCornersKeepsNothing() {
        StatusRowKeepOut.Answer answer =
            StatusRowKeepOut.answer(auto(), W, H, 0, 100, W, 166);
        assertTrue(answer.isNone());
        assertEquals(StatusRowKeepOut.Answer.NONE, answer);
    }

    @Test public void noHoleMeansNoGap() {
        StatusRowKeepOut.Answer answer = StatusRowKeepOut.answer(
            screen(StatusRowKeepOut.Mode.AUTO, false, 100f, 0, 0), W, H, 0, 10, W, 76);
        assertFalse(answer.hasGap());
        assertEquals(57, answer.leftInsetPx);
    }

    @Test public void squareCornersStepNothingIn() {
        StatusRowKeepOut.Answer answer = StatusRowKeepOut.answer(
            screen(StatusRowKeepOut.Mode.AUTO, true, 0f, 0, 0), W, H, 0, 0, W, 66);
        assertEquals(0, answer.leftInsetPx);
        assertEquals(0, answer.rightInsetPx);
        assertTrue(answer.hasGap());
    }

    @Test public void aBottomRowClearsTheBottomCorners() {
        StatusRowKeepOut.Answer answer =
            StatusRowKeepOut.answer(auto(), W, H, 0, H - 70, W, H - 4);
        // 4px from the bottom edge: 100 - sqrt(100² - 96²) = 72.
        assertEquals(72, answer.leftInsetPx);
        assertEquals(72, answer.rightInsetPx);
        assertFalse(answer.hasGap());
    }

    @Test public void offLeavesNoGapButStillClearsTheCorners() {
        StatusRowKeepOut.Answer answer = StatusRowKeepOut.answer(
            screen(StatusRowKeepOut.Mode.OFF, true, 100f, 0, 0), W, H, 0, 10, W, 76);
        assertFalse(answer.hasGap());
        assertEquals(57, answer.leftInsetPx);
    }

    @Test public void customPlacesItsOwnBoxEvenWithoutAndroidsHole() {
        StatusRowKeepOut.Answer answer = StatusRowKeepOut.answer(
            screen(StatusRowKeepOut.Mode.CUSTOM, false, 0f, -100, 60), W, H, 0, 10, W, 76);
        assertTrue(answer.hasGap());
        assertEquals(410, answer.gapStartPx);
        assertEquals(470, answer.gapEndPx);
        // Without Android's hole the box reaches down the system bar's band, and no further.
        assertTrue(StatusRowKeepOut.answer(
            screen(StatusRowKeepOut.Mode.CUSTOM, false, 0f, -100, 60), W, H, 0, 80, W, 146)
            .isNone());
    }

    @Test public void customWithNoWidthLeavesNoGap() {
        assertFalse(StatusRowKeepOut.answer(
            screen(StatusRowKeepOut.Mode.CUSTOM, true, 0f, 0, 0), W, H, 0, 10, W, 76).hasGap());
    }

    @Test public void modeReadsTheStoredValueLeniently() {
        assertEquals(StatusRowKeepOut.Mode.AUTO, StatusRowKeepOut.Mode.fromPreference(null));
        assertEquals(StatusRowKeepOut.Mode.AUTO, StatusRowKeepOut.Mode.fromPreference("auto"));
        assertEquals(StatusRowKeepOut.Mode.AUTO, StatusRowKeepOut.Mode.fromPreference("bogus"));
        assertEquals(StatusRowKeepOut.Mode.CUSTOM, StatusRowKeepOut.Mode.fromPreference("custom"));
        assertEquals(StatusRowKeepOut.Mode.OFF, StatusRowKeepOut.Mode.fromPreference("off"));
    }

    // ------------------------------------------------------------------ flow

    /** Strip (flexible), stats cluster, end widgets with a 30px end margin. */
    private static int[][] flowRow(int clusterWidth, int start, int end, int gapStart,
                                   int gapEnd) {
        int[] left = new int[3];
        int[] width = new int[3];
        StatusRowKeepOut.flow(new int[] {0, 0, 0}, new int[] {0, clusterWidth, 60},
            new int[] {0, 0, 30}, new float[] {1f, 0f, 0f}, start, end, gapStart, gapEnd,
            left, width);
        return new int[][] {left, width};
    }

    @Test public void theChipsFillLeftOfACentredHoleAndTheReadingsKeepTheEnd() {
        int[][] placed = flowRow(200, 0, W, 489, 591);
        assertEquals(0, placed[0][0]);
        assertEquals(489, placed[1][0]);
        assertEquals(790, placed[0][1]);
        assertEquals(990, placed[0][2]);
    }

    @Test public void readingsThatDoNotFitRightOfTheHoleCrossToItsLeft() {
        int[][] placed = flowRow(450, 0, W, 489, 591);
        // The cluster stands left of the gap, ending where it starts; the strip takes the rest.
        assertEquals(489, placed[0][1] + placed[1][1]);
        assertEquals(39, placed[1][0]);
        assertEquals(990, placed[0][2]);
    }

    @Test public void aHoleNearTheStartPutsTheChipsAfterIt() {
        int[][] placed = flowRow(200, 0, W, 40, 140);
        assertEquals(140, placed[0][0]);
        assertEquals(650, placed[1][0]);
        assertEquals(790, placed[0][1]);
    }

    @Test public void cornersAloneNarrowTheRow() {
        int[][] placed = flowRow(200, 57, W - 57, 0, 0);
        assertEquals(57, placed[0][0]);
        assertEquals(676, placed[1][0]);
        assertEquals(733, placed[0][1]);
        assertEquals(933, placed[0][2]);
    }

    @Test public void aCentredClusterStepsOffTheHole() {
        // The Widgets place: strip, cluster, centring spacer, end widgets.
        int[] left = new int[4];
        int[] width = new int[4];
        StatusRowKeepOut.flow(new int[] {0, 0, 0, 0}, new int[] {0, 200, 0, 60},
            new int[] {0, 0, 0, 30}, new float[] {1f, 0f, 1f, 0f}, 0, W, 489, 591, left, width);
        int clusterRight = left[1] + width[1];
        assertTrue(clusterRight <= 489 || left[1] >= 591);
        assertEquals(990, left[3]);
        for (int i = 0; i < 4; i++) {
            int right = left[i] + width[i];
            assertTrue("child " + i + " under the hole", right <= 489 || left[i] >= 591);
        }
    }
}
