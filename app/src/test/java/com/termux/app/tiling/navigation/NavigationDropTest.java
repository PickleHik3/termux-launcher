package com.termux.app.tiling.navigation;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Where a window carried in navigation mode lands (#433): a half, the centre, or a gap. */
public class NavigationDropTest {

    private static final long NONE = NavigationDrop.NO_WINDOW;
    private static final float CATCH = 30f;
    /** The page's room, in its strip's coordinates. */
    private static final float[] SPAN = {0f, 0f, 1000f, 2000f};

    private static NavigationDrop.Tile tile(long id, int column, float l, float t, float r, float b) {
        return new NavigationDrop.Tile(id, column, l, t, r, b);
    }

    /** Three half-width Scrolling columns, the middle one holding two stacked windows. */
    private static final List<NavigationDrop.Tile> COLUMNS = Arrays.asList(
        tile(1L, 0, 0f, 0f, 490f, 2000f),
        tile(2L, 1, 510f, 0f, 1000f, 990f),
        tile(3L, 1, 510f, 1010f, 1000f, 2000f),
        tile(4L, 2, 1020f, 0f, 1510f, 2000f));

    private static NavigationDrop.Drop scrolling(long carried, float x, float y) {
        return NavigationDrop.at(COLUMNS, false, carried, x, y, CATCH, SPAN);
    }

    @Test
    public void theCentreOfAnotherWindowJoinsItAsATab() {
        NavigationDrop.Drop drop = scrolling(4L, 245f, 1000f);
        assertEquals(NavigationDrop.Type.TAB, drop.type);
        assertEquals(1L, drop.targetId);
    }

    @Test
    public void aWindowsTopOrBottomHalfStacksAboveOrBelowIt() {
        NavigationDrop.Drop top = scrolling(4L, 245f, 150f);
        assertEquals(NavigationDrop.Type.SIDE, top.type);
        assertEquals(NavigationDrop.Side.TOP, top.side);
        assertEquals(1L, top.targetId);
        // What it lights: the top half of the window.
        assertEquals(0f, top.top, 0.01f);
        assertEquals(1000f, top.bottom, 0.01f);

        NavigationDrop.Drop bottom = scrolling(4L, 245f, 1900f);
        assertEquals(NavigationDrop.Side.BOTTOM, bottom.side);
        assertEquals(1000f, bottom.top, 0.01f);
    }

    @Test
    public void aWindowsLeftOrRightHalfTakesThatSide() {
        // Under Dwindle one tile filling the room: its left and right halves.
        NavigationDrop.Drop left = NavigationDrop.at(Collections.singletonList(tile(1L, 0, 0f, 0f, 1000f, 2000f)),
            true, 9L, 60f, 1000f, CATCH, SPAN);
        assertEquals(NavigationDrop.Type.SIDE, left.type);
        assertEquals(NavigationDrop.Side.LEFT, left.side);
        assertEquals(500f, left.right, 0.01f);

        NavigationDrop.Drop right = NavigationDrop.at(Collections.singletonList(tile(1L, 0, 0f, 0f, 1000f, 2000f)),
            true, 9L, 940f, 1000f, CATCH, SPAN);
        assertEquals(NavigationDrop.Side.RIGHT, right.side);
        assertEquals(500f, right.left, 0.01f);
    }

    @Test
    public void aGapBetweenColumnsMakesItsOwnColumnThere() {
        NavigationDrop.Drop drop = scrolling(4L, 500f, 400f);
        assertEquals(NavigationDrop.Type.COLUMN, drop.type);
        // After window 1's column.
        assertEquals(1L, drop.targetId);
        assertEquals(NavigationDrop.Side.RIGHT, drop.side);
        // A band centred on the gap, the room's height.
        assertEquals(500f - CATCH / 2f, drop.left, 0.01f);
        assertEquals(500f + CATCH / 2f, drop.right, 0.01f);
        assertEquals(0f, drop.top, 0.01f);
        assertEquals(2000f, drop.bottom, 0.01f);
    }

    @Test
    public void theGapCatchesALittleInsideTheWindowsBesideIt() {
        assertEquals(NavigationDrop.Type.COLUMN, scrolling(4L, 490f - CATCH + 1f, 400f).type);
        assertEquals(NavigationDrop.Type.COLUMN, scrolling(4L, 510f + CATCH - 1f, 400f).type);
    }

    @Test
    public void beforeTheFirstColumnAndAfterTheLastAreGapsToo() {
        NavigationDrop.Drop first = scrolling(2L, 5f, 400f);
        assertEquals(NavigationDrop.Type.COLUMN, first.type);
        assertEquals(1L, first.targetId);
        assertEquals(NavigationDrop.Side.LEFT, first.side);

        NavigationDrop.Drop last = scrolling(2L, 1600f, 400f);
        assertEquals(NavigationDrop.Type.COLUMN, last.type);
        assertEquals(4L, last.targetId);
        assertEquals(NavigationDrop.Side.RIGHT, last.side);
    }

    @Test
    public void anEmptyPlaceTakesTheWindowAsItsFirstColumn() {
        NavigationDrop.Drop drop = NavigationDrop.at(Collections.<NavigationDrop.Tile>emptyList(), false,
            4L, 500f, 500f, CATCH, SPAN);
        assertEquals(NavigationDrop.Type.COLUMN, drop.type);
        assertEquals(NONE, drop.targetId);
        assertEquals(1000f, drop.right, 0.01f);
        assertEquals(2000f, drop.bottom, 0.01f);
    }

    @Test
    public void theCarriedWindowItselfIsNoPlaceToLand() {
        assertEquals(NavigationDrop.Type.NONE, scrolling(1L, 245f, 1000f).type);
        assertEquals(NavigationDrop.Type.NONE, scrolling(1L, 245f, 100f).type);
    }

    @Test
    public void theGapsBesideAWindowAloneInItsColumnChangeNothing() {
        // Window 4 stands alone in the last column: the gap before it and the end are its own slot.
        assertEquals(NavigationDrop.Type.NONE, scrolling(4L, 1010f, 400f).type);
        assertEquals(NavigationDrop.Type.NONE, scrolling(4L, 1600f, 400f).type);
        // Window 1 alone in the first column: the start is its own slot too.
        assertEquals(NavigationDrop.Type.NONE, scrolling(1L, 5f, 400f).type);
    }

    @Test
    public void aFullColumnTakesNoThirdWindowAboveOrBelow() {
        assertEquals(NavigationDrop.Type.NONE, scrolling(4L, 755f, 100f).type);
        // Its centre still makes a tab.
        assertEquals(NavigationDrop.Type.TAB, scrolling(4L, 755f, 495f).type);
        // A window of that column may move to its other half.
        NavigationDrop.Drop within = scrolling(3L, 755f, 100f);
        assertEquals(NavigationDrop.Type.SIDE, within.type);
        assertEquals(2L, within.targetId);
        assertEquals(NavigationDrop.Side.TOP, within.side);
    }

    @Test
    public void underDwindleAGapGoesToTheNearestTilesFacingSide() {
        List<NavigationDrop.Tile> tiles = Arrays.asList(
            tile(1L, 0, 0f, 0f, 490f, 2000f),
            tile(2L, 1, 510f, 0f, 1000f, 2000f));
        // Just right of tile 1's edge, in the gap: tile 1's right side, as its right half would.
        NavigationDrop.Drop drop = NavigationDrop.at(tiles, true, 9L, 495f, 1000f, CATCH, SPAN);
        assertEquals(NavigationDrop.Type.SIDE, drop.type);
        assertEquals(1L, drop.targetId);
        assertEquals(NavigationDrop.Side.RIGHT, drop.side);
        // Under Dwindle a tile's sides are halves everywhere: no column gaps.
        NavigationDrop.Drop top = NavigationDrop.at(tiles, true, 9L, 755f, 100f, CATCH, SPAN);
        assertEquals(NavigationDrop.Side.TOP, top.side);
        assertEquals(2L, top.targetId);
    }
}
