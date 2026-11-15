package com.termux.app.statusbar;

import com.termux.app.dock.DockLayoutPolicy;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The chip strip keeps what one whole chip needs when the readings are wider than the row, with
 * and without a camera gap, and whatever corner radius pushes the chip inwards.
 */
public class StatusRowKeepsChipTest {

    private static final int W = 1080;
    private static final float DENSITY = 2.75f;

    /** Strip (flexible), stats cluster, centring spacer (flexible), end widgets (30px margin). */
    private static final int STRIP = 0;
    private static final int CLUSTER = 1;
    private static final int SPACER = 2;
    private static final int BELL = 3;

    private static int[][] place(int cluster, int bell, boolean spacer, int min,
                                 int start, int end, int gapStart, int gapEnd) {
        int[] left = new int[4];
        int[] width = new int[4];
        StatusRowKeepOut.flow(new int[] {0, 0, 0, 0}, new int[] {0, cluster, 0, bell},
            new int[] {0, 0, 0, 30}, new float[] {1f, 0f, spacer ? 1f : 0f, 0f},
            new int[] {min, 0, 0, 0}, start, end, gapStart, gapEnd, left, width);
        return new int[][] {left, width};
    }

    private static void assertNoOverlapAndInside(int[][] p, int start, int end) {
        int[] left = p[0];
        int[] width = p[1];
        for (int i = 0; i < 4; i++) {
            if (width[i] == 0) continue;
            assertTrue("child " + i + " inside", left[i] >= start && left[i] + width[i] <= end);
            for (int j = i + 1; j < 4; j++) {
                if (width[j] == 0) continue;
                boolean apart = left[i] + width[i] <= left[j] || left[j] + width[j] <= left[i];
                assertTrue("children " + i + " and " + j + " overlap", apart);
            }
        }
    }

    @Test public void aRowWiderThanItsReadingsIsPlacedAsBefore() {
        int[][] p = place(400, 60, false, 150, 0, W, 0, 0);
        assertEquals(W - 400 - 60 - 30, p[1][STRIP]);
        assertEquals(W - 30 - 60 - 400, p[0][CLUSTER]);
        assertEquals(0, p[0][STRIP]);
    }

    @Test public void readingsWiderThanTheRowGiveWayAndTheStripKeepsItsFloor() {
        // 1080 - 30 - 60 = 990 for the cluster and strip; the cluster alone is 900.
        int[][] p = place(900, 60, false, 300, 0, W, 0, 0);
        assertEquals(0, p[1][CLUSTER]);
        assertEquals(990, p[1][STRIP]);
        assertEquals(60, p[1][BELL]);
        assertNoOverlapAndInside(p, 0, W);
    }

    @Test public void theBellGoesOnlyAfterTheReadings() {
        int[][] p = place(900, 60, false, 1100, 0, W, 0, 0);
        assertEquals(0, p[1][CLUSTER]);
        assertEquals(0, p[1][BELL]);
        assertEquals(W, p[1][STRIP]);
        assertTrue(StatusRowKeepOut.isDropped(900, 0f, p[1][CLUSTER]));
        assertNoOverlapAndInside(p, 0, W);
    }

    @Test public void theCentringSpacerGivesWayBeforeAnyReading() {
        // Room for everything, but the spacer and strip share evenly and the strip's half is short.
        int[][] p = place(200, 60, true, 600, 0, W, 0, 0);
        assertEquals(200, p[1][CLUSTER]);
        assertEquals(60, p[1][BELL]);
        assertTrue(p[1][STRIP] >= 600);
        assertEquals(W - 30 - 60 - 200, p[1][STRIP] + p[1][SPACER]);
        assertNoOverlapAndInside(p, 0, W);
    }

    @Test public void aCameraGapSplittingTheRowKeepsTheFloorToo() {
        int[][] p = place(700, 60, false, 300, 0, W, 489, 591);
        assertTrue(p[1][STRIP] >= 300);
        assertNoOverlapAndInside(p, 0, W);
        for (int i = 0; i < 4; i++) {
            if (p[1][i] == 0) continue;
            assertTrue("child " + i + " under the hole",
                p[0][i] + p[1][i] <= 489 || p[0][i] >= 591);
        }
    }

    @Test public void aRowAlreadyHoldingTheFloorIsLeftAlone() {
        int[][] with = place(200, 60, false, 300, 0, W, 489, 591);
        int[] left = new int[4];
        int[] width = new int[4];
        StatusRowKeepOut.flow(new int[] {0, 0, 0, 0}, new int[] {0, 200, 0, 60},
            new int[] {0, 0, 0, 30}, new float[] {1f, 0f, 0f, 0f}, 0, W, 489, 591, left, width);
        for (int i = 0; i < 4; i++) {
            assertEquals(left[i], with[0][i]);
            assertEquals(width[i], with[1][i]);
        }
    }

    @Test public void theFloorFollowsTheCornersAndTheChipStaysInside() {
        int badge = 66;
        int chip = 120;
        int barChrome = 17;
        for (float radiusDp : new float[] {0f, 26f, 40f, 120f}) {
            int inset = DockLayoutPolicy.statusBarContentEdgeInsetPx(true,
                radiusDp * DENSITY, 26f * DENSITY, DENSITY);
            int padding = Math.round(14 * DENSITY);
            int min = StatusRowFitPolicy.stripMinPx(padding, inset, badge, barChrome, chip);
            int badgeLeft = padding + inset;
            assertTrue("badge whole at " + radiusDp, badgeLeft >= 0 && badgeLeft + badge <= min);
            int[][] p = place(900, 60, false, min, 0, W, 0, 0);
            assertTrue(p[1][STRIP] >= min);
            // The floor holds the whole run: badge, the bar's margin and the front chip.
            assertTrue(badgeLeft + badge + barChrome + chip <= p[1][STRIP]);
        }
    }

    @Test public void aMirroredRowKeepsTheFloorOnTheStripWhicheverEndItStandsAt() {
        // Right to left: bell, cluster, then the strip last.
        int[] left = new int[3];
        int[] width = new int[3];
        StatusRowKeepOut.flow(new int[] {30, 0, 0}, new int[] {60, 900, 0},
            new int[] {0, 0, 0}, new float[] {0f, 0f, 1f}, new int[] {0, 0, 300}, 0, W, 0, 0,
            left, width);
        assertTrue(width[2] >= 300);
        assertEquals(0, width[1]);
        assertEquals(W, left[2] + width[2]);
    }
}
