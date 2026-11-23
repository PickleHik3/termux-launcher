package com.termux.app.statusbar;

import com.termux.app.chrome.CameraHoleGeometry;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * {@link StatusRowKeepOut#partAtGap}: the row parted at the camera, chips before it and readings
 * after it in reading order, readings left out one at a time from the far end. A 1080px row with
 * 50px corner insets and a camera gap at 480..600; the readings are 90px each with 12px dots
 * between them and 20px of group chrome.
 */
public class StatusRowPartingTest {

    private static final int START = 50;
    private static final int END = 1030;
    private static final int FLOOR = 190;
    private static final int CHROME = 20;

    /** {@code readings} readings of {@code width}, a dot between each two. */
    private static int[] widths(int readings, int width) {
        int[] out = new int[readings * 2 - 1];
        for (int i = 0; i < out.length; i++) out[i] = i % 2 == 0 ? width : 12;
        return out;
    }

    private static boolean[] marks(int readings) {
        boolean[] out = new boolean[readings * 2 - 1];
        for (int i = 0; i < out.length; i++) out[i] = i % 2 == 1;
        return out;
    }

    private static int[] part(int gapStart, int gapEnd, boolean rtl, int readings, int width) {
        int[] out = new int[StatusRowKeepOut.PART_SIZE];
        StatusRowKeepOut.partAtGap(START, END, gapStart, gapEnd, rtl, FLOOR,
            widths(readings, width), marks(readings), readings * 2 - 1, CHROME, out);
        return out;
    }

    @Test public void aCentreCameraPutsTheChipsLeftAndTheReadingsRight() {
        int[] out = part(480, 600, false, 4, 90);
        assertEquals(START, out[StatusRowKeepOut.PART_CHIPS_LEFT]);
        assertEquals(480, out[StatusRowKeepOut.PART_CHIPS_RIGHT]);
        assertEquals(7, out[StatusRowKeepOut.PART_READINGS_KEPT]);
        assertEquals(END, out[StatusRowKeepOut.PART_READINGS_RIGHT]);
        assertEquals(END - CHROME - 4 * 90 - 3 * 12, out[StatusRowKeepOut.PART_READINGS_LEFT]);
        assertTrue(out[StatusRowKeepOut.PART_READINGS_LEFT] >= 600);
    }

    @Test public void aRightToLeftRowMirrorsIt() {
        int[] out = part(480, 600, true, 4, 90);
        assertEquals(600, out[StatusRowKeepOut.PART_CHIPS_LEFT]);
        assertEquals(END, out[StatusRowKeepOut.PART_CHIPS_RIGHT]);
        assertEquals(START, out[StatusRowKeepOut.PART_READINGS_LEFT]);
        assertEquals(START + CHROME + 4 * 90 + 3 * 12, out[StatusRowKeepOut.PART_READINGS_RIGHT]);
        assertTrue(out[StatusRowKeepOut.PART_READINGS_RIGHT] <= 480);
        assertEquals(7, out[StatusRowKeepOut.PART_READINGS_KEPT]);
    }

    @Test public void aCrowdedRowLeavesOutTheLastReadingsAndTheirDots() {
        // 430px right of the camera: 20 + 130 + 12 + 130 + 12 + 130 = 434 does not fit.
        int[] out = part(480, 600, false, 4, 130);
        assertEquals(3, out[StatusRowKeepOut.PART_READINGS_KEPT]);
        assertEquals(END - (CHROME + 130 + 12 + 130), out[StatusRowKeepOut.PART_READINGS_LEFT]);
        // Wider still: only the first reading stands, never the whole group dropped.
        int[] one = part(480, 600, false, 4, 250);
        assertEquals(1, one[StatusRowKeepOut.PART_READINGS_KEPT]);
    }

    @Test public void aDotNeverStandsLast() {
        assertEquals(1, StatusRowKeepOut.readingsThatFit(widths(2, 100), marks(2), 3, 0, 115));
        assertEquals(3, StatusRowKeepOut.readingsThatFit(widths(2, 100), marks(2), 3, 0, 212));
        assertEquals(0, StatusRowKeepOut.readingsThatFit(widths(2, 100), marks(2), 3, 20, 110));
    }

    @Test public void aCustomBoxWithNoCameraPartsTheRowTheSameWay() {
        // Custom with no shape: a 63px base centred in a 1080px window, 11px margin each side.
        int[] box = CameraHoleGeometry.keepOutBox(StatusRowKeepOut.Mode.CUSTOM, false, 0, 0, 0,
            0, 80, 0, 0, 100, 11, 63, 1080);
        int[] out = part(box[0], box[2], false, 4, 90);
        assertEquals(box[0], out[StatusRowKeepOut.PART_CHIPS_RIGHT]);
        assertTrue(out[StatusRowKeepOut.PART_READINGS_LEFT] >= box[2]);
        assertEquals(7, out[StatusRowKeepOut.PART_READINGS_KEPT]);
    }

    @Test public void aCameraAtTheChipsEndPutsEverythingAfterIt() {
        int[] out = part(60, 160, false, 4, 90);
        assertEquals(160, out[StatusRowKeepOut.PART_CHIPS_LEFT]);
        assertTrue(out[StatusRowKeepOut.PART_CHIPS_RIGHT] - out[StatusRowKeepOut.PART_CHIPS_LEFT]
            >= FLOOR);
        assertEquals(END, out[StatusRowKeepOut.PART_READINGS_RIGHT]);
        assertEquals(7, out[StatusRowKeepOut.PART_READINGS_KEPT]);
    }

    @Test public void aCameraAtTheReadingsEndPutsThemBeforeIt() {
        int[] out = part(950, 1030, false, 4, 90);
        assertEquals(950, out[StatusRowKeepOut.PART_READINGS_RIGHT]);
        assertEquals(out[StatusRowKeepOut.PART_READINGS_LEFT],
            out[StatusRowKeepOut.PART_CHIPS_RIGHT]);
        assertTrue(out[StatusRowKeepOut.PART_CHIPS_RIGHT] - START >= FLOOR);
        assertEquals(7, out[StatusRowKeepOut.PART_READINGS_KEPT]);
    }

    @Test public void floatingSplitCardsHoldTheChipsAndTheReadings() {
        // The Floating bar splits round the same box: chips in the left card, readings in the right.
        int[] box = {480, 0, 600, 126};
        CameraHoleGeometry.Split split = CameraHoleGeometry.split(box, 0, 20, 1080, 110,
            CameraHoleGeometry.minSidePx(30f, 2.625f));
        assertEquals(CameraHoleGeometry.Kind.TWO, split.kind);
        int[] out = part(box[0], box[2], false, 4, 90);
        assertTrue(out[StatusRowKeepOut.PART_CHIPS_RIGHT] <= split.leftCardRightPx);
        assertTrue(out[StatusRowKeepOut.PART_READINGS_LEFT] >= split.rightCardLeftPx);
    }
}
