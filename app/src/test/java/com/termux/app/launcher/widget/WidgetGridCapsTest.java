package com.termux.app.launcher.widget;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

import org.junit.Test;

/** The grid sizes a wall can hold: no cell under 56 dp either way, inside the settings range. */
public class WidgetGridCapsTest {

    @Test
    public void pongsPortraitWallHoldsSixByTen() {
        // 1014 x 1766 px at 2.625 px/dp.
        WidgetGridCaps caps = WidgetGridCaps.forWallPx(1014, 1766, 2.625f);
        assertEquals(6, caps.maxColumns);
        assertEquals(10, caps.maxRows);
    }

    @Test
    public void aCapNeverLeavesACellUnderTheFloorByMoreThanRounding() {
        for (float wall = 100f; wall <= 1200f; wall += 7f) {
            int n = WidgetGridCaps.maxCells(wall, 1, 100);
            float cell = (wall - 2f * WidgetGridMetrics.EDGE_DP
                - WidgetGridMetrics.GAP_DP * (n - 1)) / n;
            assertTrue("wall " + wall, n == 1 || cell >= WidgetGridCaps.MIN_CELL_DP);
            float next = (wall - 2f * WidgetGridMetrics.EDGE_DP
                - WidgetGridMetrics.GAP_DP * n) / (n + 1);
            assertTrue("one more would be too small at " + wall,
                next < WidgetGridCaps.MIN_CELL_DP);
        }
    }

    @Test
    public void aTinyWallStillGetsTheSettingsFloor() {
        WidgetGridCaps caps = WidgetGridCaps.forWall(90f, 90f);
        assertEquals(TERMUX_APP.MIN_APP_LAUNCHER_WIDGET_GRID_COLUMNS, caps.maxColumns);
        assertEquals(TERMUX_APP.MIN_APP_LAUNCHER_WIDGET_GRID_ROWS, caps.maxRows);
    }

    @Test
    public void aHugeWallStopsAtTheSettingsCeiling() {
        WidgetGridCaps caps = WidgetGridCaps.forWall(5000f, 5000f);
        assertEquals(TERMUX_APP.MAX_APP_LAUNCHER_WIDGET_GRID_COLUMNS, caps.maxColumns);
        assertEquals(TERMUX_APP.MAX_APP_LAUNCHER_WIDGET_GRID_ROWS, caps.maxRows);
    }

    @Test
    public void anUnmeasuredWallIsTheSettingsRangeAlone() {
        assertEquals(WidgetGridCaps.unbounded(), WidgetGridCaps.forWall(0f, 600f));
        assertEquals(WidgetGridCaps.unbounded(), WidgetGridCaps.forWallPx(100, 100, 0f));
        assertEquals(TERMUX_APP.MAX_APP_LAUNCHER_WIDGET_GRID_COLUMNS,
            WidgetGridCaps.unbounded().maxColumns);
    }

    @Test
    public void landscapeSwapsWhatEachAxisCanHold() {
        WidgetGridCaps landscape = WidgetGridCaps.forWall(673f, 386f);
        // Ten would fit; the settings range's own ceiling (8) still applies.
        assertEquals(TERMUX_APP.MAX_APP_LAUNCHER_WIDGET_GRID_COLUMNS, landscape.maxColumns);
        assertEquals(6, landscape.maxRows);
    }

    @Test
    public void clampsStayInsideTheFloorAndTheCap() {
        WidgetGridCaps caps = WidgetGridCaps.forWall(386f, 673f);
        assertEquals(6, caps.clampColumns(8));
        assertEquals(10, caps.clampRows(12));
        assertEquals(TERMUX_APP.MIN_APP_LAUNCHER_WIDGET_GRID_COLUMNS, caps.clampColumns(0));
        assertEquals(4, caps.clampColumns(4));
    }

    @Test
    public void theWheelsOfferNothingAboveTheCap() {
        WidgetGridCaps caps = WidgetGridCaps.forWall(386f, 673f);
        assertEquals(6, GridSizeWheelPolicy.columns(caps).maximum());
        assertEquals(10, GridSizeWheelPolicy.rows(caps).maximum());
        assertEquals(6, GridSizeWheelPolicy.columns(caps).clamp(8));
        assertEquals(TERMUX_APP.MIN_APP_LAUNCHER_WIDGET_GRID_COLUMNS,
            GridSizeWheelPolicy.columns(caps).minimum());
    }
}
