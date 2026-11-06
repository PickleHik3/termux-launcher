package com.termux.app.chrome;

import com.termux.app.statusbar.StatusRowKeepOut.Mode;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The camera box and the splits it asks of the surfaces at the top, on a 1080 x 2412 phone at
 * density 2.625 with a Floating bar 21..1059 x 21..105 whose radius is 26dp (68px).
 *
 * <p>The shape fixtures stand in for the cutout path's bounds. Pong's path bounds were not read;
 * the real device's are taken to equal its bounding rect (454..540 x 0..126) here.
 */
public class CameraHoleGeometryTest {

    private static final int W = 1080;
    private static final int BAND = 126;
    private static final float DENSITY = 2.625f;
    private static final int MARGIN = 11;
    private static final int BASE = 63;
    private static final int RADIUS = 68;
    private static final int MIN_SIDE = CameraHoleGeometry.minSidePx(RADIUS, DENSITY);

    private static int[] auto(int l, int t, int r, int b) {
        return CameraHoleGeometry.keepOutBox(Mode.AUTO, true, l, t, r, b, BAND, 0, 0, 100,
            MARGIN, BASE, W);
    }

    private static CameraHoleGeometry.Split bar(int[] box) {
        return CameraHoleGeometry.split(box, 21, 21, 1059, 105, MIN_SIDE);
    }

    @Test public void aCircleHoleNearTheLeftEndTrimsTheLeftCard() {
        int[] box = auto(60, 30, 120, 90);
        assertArrayEquals(new int[] {49, 0, 131, 90}, box);
        CameraHoleGeometry.Split split = bar(box);
        assertEquals(CameraHoleGeometry.Kind.TRIM_LEFT, split.kind);
        assertEquals(131, split.rightCardLeftPx);
    }

    @Test public void aCentredCircleSplitsTheBarInTwo() {
        CameraHoleGeometry.Split split = bar(auto(510, 30, 570, 90));
        assertEquals(CameraHoleGeometry.Kind.TWO, split.kind);
        assertEquals(499, split.leftCardRightPx);
        assertEquals(581, split.rightCardLeftPx);
    }

    @Test public void aPillHoleSplitsTheBarInTwo() {
        CameraHoleGeometry.Split split = bar(auto(440, 20, 640, 90));
        assertEquals(CameraHoleGeometry.Kind.TWO, split.kind);
        assertEquals(429, split.leftCardRightPx);
        assertEquals(651, split.rightCardLeftPx);
    }

    @Test public void onlyTheShapesBoundsAreUsedAndTheBoxReachesTheScreenTop() {
        // Whatever the path, the bounds reaching the whole band make a full-band box.
        int[] box = auto(500, 0, 580, BAND);
        assertArrayEquals(new int[] {489, 0, 591, BAND}, box);
    }

    @Test public void noCutoutMeansNoBoxInAutoAndABaseInCustom() {
        assertNull(CameraHoleGeometry.keepOutBox(Mode.AUTO, false, 0, 0, 0, 0, BAND, 0, 0, 100,
            MARGIN, BASE, W));
        assertEquals(CameraHoleGeometry.Split.NONE, bar(null));
        int[] box = CameraHoleGeometry.keepOutBox(Mode.CUSTOM, false, 0, 0, 0, 0, BAND, 0, 0, 100,
            MARGIN, BASE, W);
        // 63px base centred in 1080: 508.5..571.5; the band is 126 tall.
        assertNotNull(box);
        assertEquals(Math.round(508.5f) - MARGIN, box[0]);
        assertEquals(Math.round(571.5f) + MARGIN, box[2]);
        assertEquals(BAND, box[3]);
        // No band, no window, or no base: nothing to stand on.
        assertNull(CameraHoleGeometry.keepOutBox(Mode.CUSTOM, false, 0, 0, 0, 0, 0, 0, 0, 100,
            MARGIN, BASE, W));
        assertNull(CameraHoleGeometry.keepOutBox(Mode.CUSTOM, false, 0, 0, 0, 0, BAND, 0, 0, 100,
            MARGIN, BASE, 0));
    }

    @Test public void landscapeWithNoShapeOnTheTopEdgeChangesNothing() {
        // The host passes hasShape false when no rect touches y = 0.
        assertNull(CameraHoleGeometry.keepOutBox(Mode.AUTO, false, 0, 0, 0, 0, 72, 0, 0, 100,
            MARGIN, BASE, 2412));
        assertEquals(CameraHoleGeometry.Split.NONE,
            CameraHoleGeometry.split(null, 21, 21, 2391, 105, MIN_SIDE));
    }

    @Test public void aBarBelowTheBoxIsUntouched() {
        int[] box = auto(510, 30, 570, 90);
        // The system bar shows: the bar stands under it, at or below the box's bottom (90).
        assertEquals(CameraHoleGeometry.Split.NONE,
            CameraHoleGeometry.split(box, 21, 90, 1059, 174, MIN_SIDE));
        assertEquals(CameraHoleGeometry.Split.NONE,
            CameraHoleGeometry.split(box, 21, 150, 1059, 234, MIN_SIDE));
        // A bar beside the box (a side surface) is untouched too.
        assertEquals(CameraHoleGeometry.Split.NONE,
            CameraHoleGeometry.split(box, 600, 21, 1059, 105, MIN_SIDE));
    }

    @Test public void aNarrowRightSideTrimsRightAndTwoNarrowSidesAreCovered() {
        int[] near = auto(1000, 30, 1040, 90);
        CameraHoleGeometry.Split trim = bar(near);
        assertEquals(CameraHoleGeometry.Kind.TRIM_RIGHT, trim.kind);
        assertEquals(989, trim.leftCardRightPx);
        // A 200px surface cannot keep two 136px sides around a hole.
        int[] box = auto(510, 30, 570, 90);
        assertEquals(CameraHoleGeometry.Split.COVERED,
            CameraHoleGeometry.split(box, 440, 21, 640, 105, MIN_SIDE));
    }

    @Test public void nudgesMoveAndSizeTheBoxExactly() {
        int[] base = CameraHoleGeometry.keepOutBox(Mode.CUSTOM, true, 500, 0, 580, 80, BAND, 0, 0,
            100, 0, BASE, W);
        assertArrayEquals(new int[] {500, 0, 580, 80}, base);
        // Scale about the centre (540, 40).
        assertArrayEquals(new int[] {520, 0, 560, 60}, CameraHoleGeometry.keepOutBox(Mode.CUSTOM,
            true, 500, 0, 580, 80, BAND, 0, 0, 50, 0, BASE, W));
        assertArrayEquals(new int[] {460, 0, 620, 120}, CameraHoleGeometry.keepOutBox(Mode.CUSTOM,
            true, 500, 0, 580, 80, BAND, 0, 0, 200, 0, BASE, W));
        // Offsets move it, margin grows it on both sides.
        assertArrayEquals(new int[] {490, 0, 570, 90}, CameraHoleGeometry.keepOutBox(Mode.CUSTOM,
            true, 500, 0, 580, 80, BAND, -10, 10, 100, 0, BASE, W));
        assertArrayEquals(new int[] {476, 0, 604, 80}, CameraHoleGeometry.keepOutBox(Mode.CUSTOM,
            true, 500, 0, 580, 80, BAND, 0, 0, 100, 24, BASE, W));
    }

    @Test public void offIsNullWhateverTheShape() {
        assertNull(CameraHoleGeometry.keepOutBox(Mode.OFF, true, 500, 0, 580, 80, BAND, 5, 5, 150,
            MARGIN, BASE, W));
    }

    @Test public void aSideBelowTwiceTheRadiusIsTooNarrowEvenOver40dp() {
        // 40dp = 105px; a 60dp card (r = 158px) wants 316px a side.
        int big = CameraHoleGeometry.minSidePx(158f, DENSITY);
        assertEquals(316, big);
        assertEquals(105, CameraHoleGeometry.minSidePx(20f, DENSITY));
        int[] box = auto(510, 30, 570, 90);
        // Left side 499 - 21 = 478 is enough for 316, a 300 wide surface's is not.
        assertEquals(CameraHoleGeometry.Kind.TWO,
            CameraHoleGeometry.split(box, 21, 21, 1059, 105, big).kind);
        assertEquals(CameraHoleGeometry.Kind.TRIM_LEFT,
            CameraHoleGeometry.split(box, 250, 21, 1059, 105, big).kind);
    }

    @Test public void thePaneTopGapIsInThePanesOwnPixels() {
        int[] box = auto(510, 30, 570, 90);
        int[] gap = new int[2];
        assertTrue(CameraHoleGeometry.paneTopGap(box, 0, 0, W, gap));
        assertArrayEquals(new int[] {499, 581}, gap);
        // The right half of a vertical split, 540..1080: the gap starts at its own 0.
        assertTrue(CameraHoleGeometry.paneTopGap(box, 540, 0, W, gap));
        assertArrayEquals(new int[] {0, 41}, gap);
        // Beside it, below it, or without a box: none.
        assertFalse(CameraHoleGeometry.paneTopGap(box, 0, 0, 400, gap));
        assertFalse(CameraHoleGeometry.paneTopGap(box, 0, 90, W, gap));
        assertFalse(CameraHoleGeometry.paneTopGap(null, 0, 0, W, gap));
        assertArrayEquals(new int[] {0, 0}, gap);
    }

    /**
     * The old Custom (a centre and a width, no margin) as the new nudges, within a pixel: the
     * migration the activity runs, from {@link CameraHoleGeometry#migrateScalePct} and
     * {@link CameraHoleGeometry#migrateOffsetXDp}.
     */
    @Test public void theOldCentreAndWidthBecomeTheNewNudges() {
        // With a shape 454..540: old centre +30dp, width 40dp.
        int centrePx = Math.round(30 * DENSITY);
        int widthPx = Math.round(40 * DENSITY);
        int oldLeft = W / 2 + centrePx - widthPx / 2;
        int oldRight = oldLeft + widthPx;
        int scale = CameraHoleGeometry.migrateScalePct(widthPx, 540 - 454);
        int dxDp = CameraHoleGeometry.migrateOffsetXDp(W / 2 + centrePx, (454 + 540) / 2, DENSITY);
        int[] box = CameraHoleGeometry.keepOutBox(Mode.CUSTOM, true, 454, 0, 540, 126, BAND,
            Math.round(dxDp * DENSITY), 0, scale, 0, BASE, W);
        assertEquals(oldLeft, box[0], 3);
        assertEquals(oldRight, box[2], 3);
        // Without a shape: the base is 24dp, so 24dp wide at 100%.
        int base = Math.round(CameraHoleGeometry.CUSTOM_BASE_DP * DENSITY);
        int scaleNo = CameraHoleGeometry.migrateScalePct(widthPx, base);
        int dxNo = CameraHoleGeometry.migrateOffsetXDp(W / 2 + centrePx, W / 2, DENSITY);
        assertEquals(30, dxNo);
        int[] noShape = CameraHoleGeometry.keepOutBox(Mode.CUSTOM, false, 0, 0, 0, 0, BAND,
            Math.round(dxNo * DENSITY), 0, scaleNo, 0, base, W);
        assertEquals(oldLeft, noShape[0], 3);
        assertEquals(oldRight, noShape[2], 3);
        // The scale is clamped to its range.
        assertEquals(200, CameraHoleGeometry.migrateScalePct(widthPx, 10));
        assertEquals(50, CameraHoleGeometry.migrateScalePct(10, widthPx));
        assertEquals(200, CameraHoleGeometry.migrateOffsetXDp(W * 5, W / 2, DENSITY));
    }

    /**
     * Pong in Full screen, Floating, camera Auto at Margin 4dp: the bar's two cards as the views
     * stand. Every layer of a card (tint and rim, wallpaper frost) takes these margins, so the main
     * card's glass starts at the box's right edge and the side card's ends at its left edge.
     */
    @Test public void pongsBarSplitsIntoTwoCardsEitherSideOfTheBox() {
        int[] box = auto(454, 0, 540, 126);
        assertArrayEquals(new int[] {443, 0, 551, 126}, box);
        CameraHoleGeometry.Split split = bar(box);
        assertEquals(CameraHoleGeometry.Kind.TWO, split.kind);
        int[] margins = new int[4];
        assertTrue(CameraHoleGeometry.barCardMargins(split, 21, 1059, margins));
        // The right side (508) is wider than the left (422): the main card is the right one.
        assertCards(margins, 21, 1059, 551, 1059, 21, 443);
        // Pong's box read with the margin taken as 4px: 450..544.
        int[] narrow = {450, 0, 544, 126};
        assertTrue(CameraHoleGeometry.barCardMargins(bar(narrow), 21, 1059, margins));
        assertCards(margins, 21, 1059, 544, 1059, 21, 450);
        // A bar at the screen's 30..1050, as pong's crop shows it.
        assertTrue(CameraHoleGeometry.barCardMargins(
            CameraHoleGeometry.split(box, 30, 5, 1050, 85, MIN_SIDE), 30, 1050, margins));
        assertCards(margins, 30, 1050, 551, 1050, 30, 443);
    }

    /**
     * Pong's cutout path traced 511..540 (or past the rect's right edge) against a bounding rect
     * of 454..540: a sliver at one end, so the rect is the shape and the box centres on it.
     */
    @Test public void aTraceBesideOrInsideASliverOfTheRectFallsBackToTheRect() {
        assertFalse(CameraHoleGeometry.pathTraceFits(454, 540, 511, 540));
        assertFalse(CameraHoleGeometry.pathTraceFits(454, 540, 511, 569));
        assertFalse(CameraHoleGeometry.pathTraceFits(454, 540, 400, 450));
        // A circle traced inside its rect, round-out included, stands for the camera.
        assertTrue(CameraHoleGeometry.pathTraceFits(454, 540, 460, 534));
        assertTrue(CameraHoleGeometry.pathTraceFits(454, 540, 453, 541));
        assertTrue(CameraHoleGeometry.pathTraceFits(440, 640, 440, 540));
        assertFalse(CameraHoleGeometry.pathTraceFits(454, 540, 0, 0));
        // With the rect as the shape pong's box is 443..551, not 500..551.
        assertArrayEquals(new int[] {443, 0, 551, 126}, auto(454, 0, 540, 126));
    }

    @Test public void aWiderLeftSideMakesTheLeftCardTheMainOne() {
        int[] margins = new int[4];
        assertTrue(CameraHoleGeometry.barCardMargins(bar(auto(700, 30, 760, 90)), 21, 1059,
            margins));
        assertCards(margins, 21, 1059, 21, 689, 771, 1059);
    }

    @Test public void aTrimMovesOnlyTheMainCardAndNoneMovesNothing() {
        int[] margins = new int[4];
        assertFalse(CameraHoleGeometry.barCardMargins(bar(auto(60, 30, 120, 90)), 21, 1059,
            margins));
        assertArrayEquals(new int[] {110, 0, 0, 0}, margins);
        assertFalse(CameraHoleGeometry.barCardMargins(bar(auto(1000, 30, 1040, 90)), 21, 1059,
            margins));
        assertArrayEquals(new int[] {0, 70, 0, 0}, margins);
        assertFalse(CameraHoleGeometry.barCardMargins(CameraHoleGeometry.Split.NONE, 21, 1059,
            margins));
        assertArrayEquals(new int[] {0, 0, 0, 0}, margins);
        assertFalse(CameraHoleGeometry.barCardMargins(CameraHoleGeometry.Split.COVERED, 21, 1059,
            margins));
        assertArrayEquals(new int[] {0, 0, 0, 0}, margins);
    }

    /** The cards' window x ranges from a bar at {@code [left, right)} and its margins. */
    private static void assertCards(int[] margins, int left, int right, int mainFrom, int mainTo,
                                    int sideFrom, int sideTo) {
        assertEquals("main card left", mainFrom, left + margins[CameraHoleGeometry.MAIN_LEFT]);
        assertEquals("main card right", mainTo, right - margins[CameraHoleGeometry.MAIN_RIGHT]);
        assertEquals("side card left", sideFrom, left + margins[CameraHoleGeometry.SIDE_LEFT]);
        assertEquals("side card right", sideTo, right - margins[CameraHoleGeometry.SIDE_RIGHT]);
    }
}
