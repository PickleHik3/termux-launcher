package com.termux.app.launcher.widget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

import org.junit.Test;

/**
 * The grid sizes a wall can hold: no cell under 55 dp either way, inside the settings range, with
 * Home's Margin as the rim round the grid.
 */
public class WidgetGridCapsTest {

    /** Home's Margin as a new install seeds it. */
    private static final float SEEDED_MARGIN_DP = TERMUX_APP.DEFAULT_SURFACE_BASE_SIDE_GAP;

    @Test
    public void pongsPortraitWallHoldsSixColumnsFlushAndFiveAtTheSeededMargin() {
        // 1014 x 1766 px at 2.625 px/dp: a 386 x 673 dp wall.
        WidgetGridCaps flush = WidgetGridCaps.forWallPx(1014, 1766, 2.625f, 0f);
        assertEquals(6, flush.maxColumns);
        assertEquals(10, flush.maxRows);
        WidgetGridCaps seeded = WidgetGridCaps.forWallPx(1014, 1766, 2.625f, SEEDED_MARGIN_DP);
        assertEquals(5, seeded.maxColumns);
        assertEquals(10, seeded.maxRows);
    }

    /** A 360 x 640 dp phone at no Margin, the seeded 12 dp and the slider's 48 dp maximum. */
    @Test
    public void aWiderMarginLowersTheCapsOnANarrowPhone() {
        WidgetGridCaps none = WidgetGridCaps.forWall(360f, 640f, 0f);
        assertEquals(5, none.maxColumns);   // (360 + 8) / 63 = 5.8
        assertEquals(10, none.maxRows);     // (640 + 8) / 63 = 10.3
        WidgetGridCaps seeded = WidgetGridCaps.forWall(360f, 640f, 12f);
        assertEquals(5, seeded.maxColumns); // (360 - 24 + 8) / 63 = 5.5
        assertEquals(9, seeded.maxRows);    // (640 - 24 + 8) / 63 = 9.9
        WidgetGridCaps widest = WidgetGridCaps.forWall(360f, 640f, 48f);
        assertEquals(4, widest.maxColumns); // (360 - 96 + 8) / 63 = 4.3
        assertEquals(8, widest.maxRows);    // (640 - 96 + 8) / 63 = 8.8
        assertEquals("a five-column grid is held to four, never cut", 4,
            widest.clampColumns(5));
        assertEquals(4, GridSizeWheelPolicy.columns(widest).maximum());
    }

    @Test
    public void aNegativeMarginCountsAsNone() {
        assertEquals(WidgetGridCaps.forWall(360f, 640f, 0f),
            WidgetGridCaps.forWall(360f, 640f, -5f));
    }

    @Test
    public void aCapNeverLeavesACellUnderTheFloorByMoreThanRounding() {
        for (float edge : new float[] {0f, 12f, 48f}) {
            for (float wall = 100f + 2f * edge; wall <= 1200f; wall += 7f) {
                int n = WidgetGridCaps.maxCells(wall, edge, 1, 100);
                float cell = (wall - 2f * edge - WidgetGridMetrics.GAP_DP * (n - 1)) / n;
                assertTrue("wall " + wall + " edge " + edge,
                    n == 1 || cell >= WidgetGridCaps.MIN_CELL_DP);
                float next = (wall - 2f * edge - WidgetGridMetrics.GAP_DP * n) / (n + 1);
                assertTrue("one more would be too small at " + wall + " edge " + edge,
                    next < WidgetGridCaps.MIN_CELL_DP);
            }
        }
    }

    @Test
    public void aTinyWallStillGetsTheSettingsFloor() {
        WidgetGridCaps caps = WidgetGridCaps.forWall(90f, 90f, 48f);
        assertEquals(TERMUX_APP.MIN_APP_LAUNCHER_WIDGET_GRID_COLUMNS, caps.maxColumns);
        assertEquals(TERMUX_APP.MIN_APP_LAUNCHER_WIDGET_GRID_ROWS, caps.maxRows);
    }

    @Test
    public void aHugeWallStopsAtTheSettingsCeiling() {
        WidgetGridCaps caps = WidgetGridCaps.forWall(5000f, 5000f, SEEDED_MARGIN_DP);
        assertEquals(TERMUX_APP.MAX_APP_LAUNCHER_WIDGET_GRID_COLUMNS, caps.maxColumns);
        assertEquals(TERMUX_APP.MAX_APP_LAUNCHER_WIDGET_GRID_ROWS, caps.maxRows);
    }

    @Test
    public void anUnmeasuredWallIsTheSettingsRangeAlone() {
        assertEquals(WidgetGridCaps.unbounded(), WidgetGridCaps.forWall(0f, 600f, 12f));
        assertEquals(WidgetGridCaps.unbounded(), WidgetGridCaps.forWallPx(100, 100, 0f, 12f));
        assertEquals(TERMUX_APP.MAX_APP_LAUNCHER_WIDGET_GRID_COLUMNS,
            WidgetGridCaps.unbounded().maxColumns);
    }

    @Test
    public void landscapeSwapsWhatEachAxisCanHold() {
        WidgetGridCaps landscape = WidgetGridCaps.forWall(673f, 386f, 0f);
        // Ten would fit; the settings range's own ceiling (8) still applies.
        assertEquals(TERMUX_APP.MAX_APP_LAUNCHER_WIDGET_GRID_COLUMNS, landscape.maxColumns);
        assertEquals(6, landscape.maxRows);
    }

    @Test
    public void clampsStayInsideTheFloorAndTheCap() {
        WidgetGridCaps caps = WidgetGridCaps.forWall(386f, 673f, 0f);
        assertEquals(6, caps.clampColumns(8));
        assertEquals(10, caps.clampRows(12));
        assertEquals(TERMUX_APP.MIN_APP_LAUNCHER_WIDGET_GRID_COLUMNS, caps.clampColumns(0));
        assertEquals(4, caps.clampColumns(4));
    }

    @Test
    public void theWheelsOfferNothingAboveTheCap() {
        WidgetGridCaps caps = WidgetGridCaps.forWall(386f, 673f, 0f);
        assertEquals(6, GridSizeWheelPolicy.columns(caps).maximum());
        assertEquals(10, GridSizeWheelPolicy.rows(caps).maximum());
        assertEquals(6, GridSizeWheelPolicy.columns(caps).clamp(8));
        assertEquals(TERMUX_APP.MIN_APP_LAUNCHER_WIDGET_GRID_COLUMNS,
            GridSizeWheelPolicy.columns(caps).minimum());
    }
}
