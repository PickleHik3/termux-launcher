package com.termux.app.fragments.settings;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.termux.app.dock.DockLayoutPolicy;
import com.termux.app.fragments.settings.LayoutCanvasGeometry.KeyCell;
import com.termux.app.fragments.settings.LayoutCanvasGeometry.KeyRole;
import com.termux.app.fragments.settings.LayoutCanvasGeometry.SplitRows;
import com.termux.app.place.PlaceLayout.Edge;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

import org.junit.Test;

import java.util.List;

/** The layout canvas's to-scale mapping and its key rows: pure, so no view is needed. */
public class LayoutCanvasGeometryTest {

    private static boolean has(List<KeyCell> row, KeyRole role) {
        for (KeyCell cell : row) if (cell.role == role) return true;
        return false;
    }

    @Test
    public void theDockBandIsTheLaunchersOwnFigureAndGrowsWithItsScale() {
        for (boolean floating : new boolean[] {false, true}) {
            int smallest = LayoutCanvasGeometry.dockBandHeightDp(
                DockLayoutPolicy.minSizePreset(), floating);
            int shipped = LayoutCanvasGeometry.dockBandHeightDp(
                TERMUX_APP.DEFAULT_APP_LAUNCHER_BAR_HEIGHT, floating);
            int largest = LayoutCanvasGeometry.dockBandHeightDp(
                DockLayoutPolicy.maxSizePreset(), floating);
            assertTrue("a dock has a band", smallest > 0);
            assertTrue("a bigger scale is a taller band", shipped >= smallest && largest > smallest);
            assertTrue("the band is a real dock's, in dp, not a fraction", shipped > 30 && shipped < 200);
        }
    }

    @Test
    public void theKeyboardHeightIsItsRowsTimesItsScaleAndStaysUnderItsShareOfTheScreen() {
        float shipped = LayoutCanvasGeometry.keyboardHeightDp(
            TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_HEIGHT_SCALE, false, 0f);
        assertEquals(52f * TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_HEIGHT_SCALE * 4f + 3f + 7f,
            shipped, 0.01f);
        float landscape = LayoutCanvasGeometry.keyboardHeightDp(
            TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_HEIGHT_SCALE, true, 0f);
        assertTrue("landscape rows are shorter", landscape < shipped);
        assertTrue(LayoutCanvasGeometry.keyboardHeightDp(1.3f, false, 0f)
            > LayoutCanvasGeometry.keyboardHeightDp(0.8f, false, 0f));

        // The cap is the keyboard's own fraction of the screen, whatever the scale asks for.
        assertEquals(0.42f * 300f,
            LayoutCanvasGeometry.keyboardHeightDp(TERMUX_APP.MAX_IN_APP_KEYBOARD_HEIGHT_SCALE,
                false, 300f), 0.001f);
        assertEquals(0.40f * 300f,
            LayoutCanvasGeometry.keyboardHeightDp(TERMUX_APP.MAX_IN_APP_KEYBOARD_HEIGHT_SCALE,
                true, 300f), 0.001f);
        // A scale outside the store's range is taken at its edge.
        assertEquals(LayoutCanvasGeometry.keyboardHeightDp(
                TERMUX_APP.MAX_IN_APP_KEYBOARD_HEIGHT_SCALE, false, 0f),
            LayoutCanvasGeometry.keyboardHeightDp(9f, false, 0f), 0.001f);
    }

    @Test
    public void theChinIsInRealDpClampedToTheStoresRange() {
        assertEquals(0, LayoutCanvasGeometry.chinDp(-5));
        assertEquals(12, LayoutCanvasGeometry.chinDp(12));
        assertEquals(TERMUX_APP.MAX_IN_APP_KEYBOARD_BOTTOM_PADDING,
            LayoutCanvasGeometry.chinDp(10_000));
    }

    @Test
    public void dpBecomeCanvasPixelsByOneScale() {
        float scale = LayoutCanvasGeometry.canvasScale(200f, 400f);
        assertEquals(0.5f, scale, 0.0001f);
        assertEquals(24f, LayoutCanvasGeometry.toCanvasPx(48f, scale), 0.0001f);
        assertEquals("no screen to ask leaves the scale neutral", 1f,
            LayoutCanvasGeometry.canvasScale(200f, 0f), 0f);
        // A band twice as thick stays twice as thick on the canvas.
        assertEquals(2f * LayoutCanvasGeometry.toCanvasPx(30f, scale),
            LayoutCanvasGeometry.toCanvasPx(60f, scale), 0.0001f);
    }

    @Test
    public void bandsThatFitAreLeftAloneAndThoseThatDoNotAreSqueezedToKeepTheOpening() {
        assertEquals("a stack that fits is not touched", 1f,
            LayoutCanvasGeometry.fitFactor(1000f, 700f, 50f), 0f);
        assertEquals("an empty stack has nothing to squeeze", 1f,
            LayoutCanvasGeometry.fitFactor(1000f, 0f, 50f), 0f);
        // 80% of 1000 is 800, less 100 of air, for 1200 of bands.
        assertEquals(700f / 1200f, LayoutCanvasGeometry.fitFactor(1000f, 1200f, 100f), 0.0001f);
        assertEquals("never squeezed to nothing", LayoutCanvasGeometry.MIN_FIT,
            LayoutCanvasGeometry.fitFactor(1000f, 1200f, 5000f), 0f);
    }

    @Test
    public void theStatusBarIsTallerExpandedThanCollapsed() {
        for (boolean floating : new boolean[] {false, true}) {
            assertTrue(LayoutCanvasGeometry.statusBandDp(Edge.TOP, false, floating)
                > LayoutCanvasGeometry.statusBandDp(Edge.TOP, true, floating));
        }
    }

    @Test
    public void theDockedKeyboardIsFourRowsAcrossTenUnits() {
        List<List<KeyCell>> rows = LayoutCanvasGeometry.dockedKeyRows();
        assertEquals(4, rows.size());
        assertEquals(10, rows.get(0).size());
        assertEquals(9, rows.get(1).size());
        assertEquals("shift, seven keys, backspace", 9, rows.get(2).size());
        assertTrue(has(rows.get(2), KeyRole.SHIFT) && has(rows.get(2), KeyRole.BACKSPACE));
        List<KeyCell> bottom = rows.get(3);
        KeyCell last = bottom.get(bottom.size() - 1);
        assertEquals("the bottom row runs edge to edge", LayoutCanvasGeometry.ROW_UNITS,
            last.x + last.w, 0.001f);
        assertEquals(KeyRole.ENTER, last.role);
    }

    @Test
    public void aSplitKeyboardIsTheRealRowsPartedAtTheirMiddle() {
        List<List<KeyCell>> docked = LayoutCanvasGeometry.dockedKeyRows();
        SplitRows split = LayoutCanvasGeometry.splitKeyRows();
        assertEquals(docked.size(), split.left.size());
        assertEquals(docked.size(), split.right.size());
        float eps = 0.001f;
        for (int r = 0; r < docked.size(); r++) {
            for (List<List<KeyCell>> half : java.util.Arrays.asList(split.left, split.right)) {
                float edge = 0f;
                List<KeyCell> row = half.get(r);
                assertTrue("every row has keys on both halves", !row.isEmpty());
                for (KeyCell cell : row) {
                    assertTrue("inside its half", cell.x >= -eps
                        && cell.x + cell.w <= LayoutCanvasGeometry.HALF_UNITS + eps);
                    assertTrue("keys do not overlap", cell.x >= edge - eps);
                    edge = cell.x + cell.w;
                }
            }
            int dockedCount = docked.get(r).size();
            int splitCount = split.left.get(r).size() + split.right.get(r).size();
            // Only the space bar is cut in two, and only on the bottom row.
            assertEquals(r == docked.size() - 1 ? dockedCount + 1 : dockedCount, splitCount);
        }
        List<KeyCell> bottomLeft = split.left.get(3);
        List<KeyCell> bottomRight = split.right.get(3);
        assertTrue("the space bar is on both halves",
            has(bottomLeft, KeyRole.SPACE) && has(bottomRight, KeyRole.SPACE));
        assertTrue("shift stays on the left, backspace and enter on the right",
            has(split.left.get(2), KeyRole.SHIFT) && !has(split.right.get(2), KeyRole.SHIFT)
                && has(split.right.get(2), KeyRole.BACKSPACE) && has(bottomRight, KeyRole.ENTER));
        assertTrue(!has(bottomLeft, KeyRole.ENTER));
    }
}
