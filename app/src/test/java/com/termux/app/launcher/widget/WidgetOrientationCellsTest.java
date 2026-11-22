package com.termux.app.launcher.widget;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.os.Build;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Both orientations' cell px for one grid (ADR 0020 §12). */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.S, application = Application.class)
public class WidgetOrientationCellsTest {
    // 1080 x 2412 at 420 dpi.
    private static final float DENSITY = 2.625f;
    private static final float W = 1080f / DENSITY, H = 2412f / DENSITY;
    private static final float STATUS = 24f, ICON = 48f, EDGE = 12f;

    private static WidgetOrientationCells.Geometry phone(int columns, int rows) {
        return new WidgetOrientationCells.Geometry(W, H, STATUS, ICON, EDGE, columns, rows,
            DENSITY);
    }

    @Test
    public void aPhoneUsesTheSideBarAndATabletDoesNot() {
        assertTrue(WidgetOrientationCells.usesSideBar(W, H));
        assertTrue(WidgetOrientationCells.usesSideBar(H, W));
        assertFalse(WidgetOrientationCells.usesSideBar(800f, 1280f));
    }

    @Test
    public void theWallsAreTheSameWhicheverWayTheScreenIsReported() {
        float[] upright = WidgetOrientationCells.wallsDp(phone(4, 6));
        float[] turned = WidgetOrientationCells.wallsDp(new WidgetOrientationCells.Geometry(
            H, W, STATUS, ICON, EDGE, 4, 6, DENSITY));
        assertArrayEquals(upright, turned, 0.001f);
        float dock = WidgetGridCaps.portraitDockChromeDp(ICON);
        assertEquals(W, upright[0], 0.001f);
        assertEquals(H - STATUS - dock, upright[1], 0.001f);
        // Landscape: the long side less the side bar, by the short side less the status bar.
        assertEquals(H - WidgetOrientationCells.sideBarDp(ICON), upright[2], 0.001f);
        assertEquals(W - STATUS, upright[3], 0.001f);
    }

    @Test
    public void aTabletKeepsItsDockAlongTheBottomInLandscape() {
        WidgetOrientationCells.Geometry tablet = new WidgetOrientationCells.Geometry(800f, 1280f,
            STATUS, ICON, EDGE, 6, 6, 2f);
        float[] walls = WidgetOrientationCells.wallsDp(tablet);
        assertEquals(1280f, walls[2], 0.001f);
        assertEquals(800f - STATUS - WidgetGridCaps.portraitDockChromeDp(ICON), walls[3], 0.001f);
    }

    @Test
    public void aSpanIsItsCellsAndTheGapsBetween() {
        float wall = 400f, gap = WidgetGridMetrics.GAP_DP;
        float cell = (wall - 2 * EDGE - 3 * gap) / 4;
        assertEquals(Math.round(cell * 2f), WidgetOrientationCells.spanPx(wall, EDGE, 4, 1, 2f));
        assertEquals(Math.round((2 * cell + gap) * 2f),
            WidgetOrientationCells.spanPx(wall, EDGE, 4, 2, 2f));
        // The whole grid is the wall less its rim.
        assertEquals(Math.round((wall - 2 * EDGE) * 2f),
            WidgetOrientationCells.spanPx(wall, EDGE, 4, 4, 2f));
    }

    @Test
    public void theTurnedSizeIsKnownBeforeTheTurnAndTheLiveOneIsAsMeasured() {
        WidgetOrientationCells.Geometry g = phone(4, 6);
        int[] worked = WidgetOrientationCells.spanPxBoth(g, 2, 2);
        // A portrait landscape cell is wider and shorter.
        assertTrue(worked[2] > worked[0]);
        assertTrue(worked[3] < worked[1]);
        int inset = 20;
        int[] upright = WidgetOrientationCells.bothFromMeasured(g, 2, 2,
            worked[0] - inset, worked[1] - inset, false);
        assertArrayEquals(new int[] {worked[0] - inset, worked[1] - inset,
            worked[2] - inset, worked[3] - inset}, upright);
        int[] turned = WidgetOrientationCells.bothFromMeasured(g, 2, 2,
            worked[2] - inset, worked[3] - inset, true);
        assertArrayEquals(upright, turned);
    }

    @Test
    public void bothSizesFeedTheOptionsOnceAndAreUnchangedOnTheTurn() {
        WidgetOrientationCells.Geometry g = phone(4, 6);
        int[] worked = WidgetOrientationCells.spanPxBoth(g, 2, 2);
        int[] upright = WidgetOrientationCells.bothFromMeasured(g, 2, 2, worked[0] - 8,
            worked[1] - 8, false);
        WidgetSizeOptionsPolicy.Result first = WidgetSizeOptionsPolicy.calculateBoth(null,
            upright[0], upright[1], upright[2], upright[3], DENSITY, 30);
        assertTrue(first.changed);
        int[] turned = WidgetOrientationCells.bothFromMeasured(g, 2, 2, worked[2] - 8,
            worked[3] - 8, true);
        WidgetSizeOptionsPolicy.Result again = WidgetSizeOptionsPolicy.calculateBoth(
            first.options, turned[0], turned[1], turned[2], turned[3], DENSITY, 30);
        assertFalse("nothing to push on the turn", again.changed);
    }
}
