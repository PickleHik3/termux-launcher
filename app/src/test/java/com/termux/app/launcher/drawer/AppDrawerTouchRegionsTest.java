package com.termux.app.launcher.drawer;

import static org.junit.Assert.assertEquals;

import com.termux.app.launcher.drawer.AppDrawerTouchRegions.Region;
import com.termux.app.launcher.drawer.AppDrawerTransitionGeometry.Frame;

import org.junit.Test;

/**
 * The three-way touch split.
 *
 * <p>Reference geometry is the open content view on the 1080px panel the rest of the drawer suite
 * uses: a 90px (30dp at 3x) letter column against the right edge, the grid filling everything to its
 * left, and chrome above, below and to the left of the grid.
 */
public class AppDrawerTouchRegionsTest {

    private static final float WIDTH = 1080f;
    private static final float COLUMN_WIDTH = 90f;

    /** The search pill occupies the top 150px; the bottom 192px is the reserved band. */
    private static final Frame GRID = new Frame(24f, 150f, WIDTH - COLUMN_WIDTH, 1900f);
    private static final Frame COLUMN = new Frame(WIDTH - COLUMN_WIDTH, 150f, WIDTH, 1900f);

    private static Region resolve(float x, float y) {
        return AppDrawerTouchRegions.resolve(x, y, GRID, COLUMN, true, true);
    }

    @Test
    public void gridPointsAreOwnedByTheGrid() {
        assertEquals(Region.GRID, resolve(540f, 800f));
        assertEquals(Region.GRID, resolve(24f, 150f));
        assertEquals(Region.GRID, resolve(WIDTH - COLUMN_WIDTH - 1f, 1899f));
    }

    @Test
    public void columnPointsAreOwnedByTheColumn() {
        assertEquals(Region.COLUMN, resolve(WIDTH - COLUMN_WIDTH, 150f));
        assertEquals(Region.COLUMN, resolve(WIDTH - 1f, 1000f));
        assertEquals(Region.COLUMN, resolve(WIDTH - 45f, 1899f));
    }

    @Test
    public void everythingElseIsChromeSoTheCloseDragStillWorksThere() {
        // The pill above the grid, the left margin, the strip below the grid and the bottom band.
        assertEquals(Region.CHROME, resolve(540f, 80f));
        assertEquals(Region.CHROME, resolve(10f, 800f));
        assertEquals(Region.CHROME, resolve(540f, 1900f));
        assertEquals(Region.CHROME, resolve(540f, 2200f));
        // Off the right edge of the column.
        assertEquals(Region.CHROME, resolve(WIDTH, 800f));
    }

    @Test
    public void nothingIsTouchableWhileTheDrawerIsNotInteractive() {
        // Mid-transition every point belongs to the plane, letters and grid included.
        assertEquals(Region.CHROME,
            AppDrawerTouchRegions.resolve(540f, 800f, GRID, COLUMN, false, true));
        assertEquals(Region.CHROME,
            AppDrawerTouchRegions.resolve(WIDTH - 45f, 800f, GRID, COLUMN, false, true));
    }

    @Test
    public void anInactiveColumnGivesItsStripBackToTheCloseDrag() {
        // A non-empty query deactivates the column: its letters would be meaningless over a ranked
        // list, so the strip must behave like chrome rather than like a dead scrubber.
        assertEquals(Region.CHROME,
            AppDrawerTouchRegions.resolve(WIDTH - 45f, 800f, GRID, COLUMN, true, false));
        // The grid is unaffected.
        assertEquals(Region.GRID,
            AppDrawerTouchRegions.resolve(540f, 800f, GRID, COLUMN, true, false));
    }

    @Test
    public void boundsAreHalfOpenLikeTheViewLayersOwnHitTest() {
        // Left and top edges are inside, right and bottom are not — matching getLeft()..getRight().
        assertEquals(Region.GRID, resolve(GRID.left, GRID.top));
        assertEquals(Region.CHROME, resolve(GRID.left - 0.01f, GRID.top));
        assertEquals(Region.CHROME, resolve(GRID.left, GRID.top - 0.01f));
        assertEquals(Region.COLUMN, resolve(COLUMN.right - 0.01f, COLUMN.bottom - 0.01f));
        assertEquals(Region.CHROME, resolve(COLUMN.right, COLUMN.bottom - 0.01f));
        assertEquals(Region.CHROME, resolve(COLUMN.right - 0.01f, COLUMN.bottom));
    }

    @Test
    public void missingOrDegenerateFramesResolveToChromeRatherThanThrowing() {
        // Before layout there is no grid and no column, and the plane must still be draggable.
        assertEquals(Region.CHROME,
            AppDrawerTouchRegions.resolve(540f, 800f, null, null, true, true));
        Frame empty = new Frame(0f, 0f, 0f, 0f);
        assertEquals(Region.CHROME,
            AppDrawerTouchRegions.resolve(0f, 0f, empty, empty, true, true));
        // A column that has not been measured yet does not swallow grid points.
        assertEquals(Region.GRID,
            AppDrawerTouchRegions.resolve(540f, 800f, GRID, empty, true, true));
    }

    @Test
    public void theColumnWinsAnOverlapSoLettersNeverBecomeDeadStrip() {
        // The layout gives the grid a right margin of exactly the column width, so this cannot
        // happen — but if a future horizontal or category view forgets to subtract it, the letters
        // must keep working rather than silently going dead.
        Frame overlappingGrid = new Frame(24f, 150f, WIDTH, 1900f);
        assertEquals(Region.COLUMN, AppDrawerTouchRegions.resolve(
            WIDTH - 45f, 800f, overlappingGrid, COLUMN, true, true));
    }
}
