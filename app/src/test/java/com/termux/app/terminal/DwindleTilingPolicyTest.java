package com.termux.app.terminal;

import android.graphics.RectF;
import android.widget.LinearLayout;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import android.app.Application;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class)
public class DwindleTilingPolicyTest {

    @Test
    public void splitOrientation_followsTheLongerSideAndStacksSquaresAndUnmeasuredPanes() {
        assertEquals(LinearLayout.HORIZONTAL, DwindleTilingPolicy.splitOrientationFor(1000, 600));
        assertEquals(LinearLayout.VERTICAL, DwindleTilingPolicy.splitOrientationFor(600, 1000));
        assertEquals(LinearLayout.VERTICAL, DwindleTilingPolicy.splitOrientationFor(500, 500));
        assertEquals(LinearLayout.VERTICAL, DwindleTilingPolicy.splitOrientationFor(0, 0));
    }

    @Test
    public void dropSide_usesTheTargetsAxisAndTheHalfUnderTheFinger() {
        RectF wide = new RectF(0, 0, 1000, 400);
        assertEquals(DwindleTilingPolicy.SIDE_LEFT, DwindleTilingPolicy.dropSideFor(wide, 100, 390));
        assertEquals(DwindleTilingPolicy.SIDE_RIGHT, DwindleTilingPolicy.dropSideFor(wide, 900, 10));
        // Overshooting the edge still lands on that edge's half.
        assertEquals(DwindleTilingPolicy.SIDE_RIGHT, DwindleTilingPolicy.dropSideFor(wide, 1200, 200));

        RectF tall = new RectF(0, 0, 400, 1000);
        assertEquals(DwindleTilingPolicy.SIDE_TOP, DwindleTilingPolicy.dropSideFor(tall, 390, 100));
        assertEquals(DwindleTilingPolicy.SIDE_BOTTOM, DwindleTilingPolicy.dropSideFor(tall, 10, 900));
    }

    @Test
    public void side_mapsToOrientationOrderAndHalf() {
        assertEquals(LinearLayout.HORIZONTAL, DwindleTilingPolicy.orientationForSide(DwindleTilingPolicy.SIDE_LEFT));
        assertEquals(LinearLayout.HORIZONTAL, DwindleTilingPolicy.orientationForSide(DwindleTilingPolicy.SIDE_RIGHT));
        assertEquals(LinearLayout.VERTICAL, DwindleTilingPolicy.orientationForSide(DwindleTilingPolicy.SIDE_TOP));
        assertEquals(LinearLayout.VERTICAL, DwindleTilingPolicy.orientationForSide(DwindleTilingPolicy.SIDE_BOTTOM));
        assertTrue(DwindleTilingPolicy.droppedFirst(DwindleTilingPolicy.SIDE_LEFT));
        assertTrue(DwindleTilingPolicy.droppedFirst(DwindleTilingPolicy.SIDE_TOP));
        assertFalse(DwindleTilingPolicy.droppedFirst(DwindleTilingPolicy.SIDE_RIGHT));
        assertFalse(DwindleTilingPolicy.droppedFirst(DwindleTilingPolicy.SIDE_BOTTOM));

        RectF target = new RectF(100, 200, 500, 1000);
        RectF out = new RectF();
        assertEquals(new RectF(100, 200, 300, 1000),
            DwindleTilingPolicy.halfFor(target, DwindleTilingPolicy.SIDE_LEFT, out));
        assertEquals(new RectF(300, 200, 500, 1000),
            DwindleTilingPolicy.halfFor(target, DwindleTilingPolicy.SIDE_RIGHT, out));
        assertEquals(new RectF(100, 200, 500, 600),
            DwindleTilingPolicy.halfFor(target, DwindleTilingPolicy.SIDE_TOP, out));
        assertEquals(new RectF(100, 600, 500, 1000),
            DwindleTilingPolicy.halfFor(target, DwindleTilingPolicy.SIDE_BOTTOM, out));
    }

    @Test
    public void spiralOrientations_halveTheLongerSideEachStep() {
        // Portrait: stack, then the 600x500 half is wider so it splits side by side, then the
        // 300x500 quarter stacks again.
        assertArrayEquals(new int[]{LinearLayout.VERTICAL, LinearLayout.HORIZONTAL, LinearLayout.VERTICAL},
            DwindleTilingPolicy.spiralOrientations(4, 600, 1000));
        // Landscape starts side by side.
        assertArrayEquals(new int[]{LinearLayout.HORIZONTAL, LinearLayout.VERTICAL},
            DwindleTilingPolicy.spiralOrientations(3, 1000, 600));
        assertArrayEquals(new int[0], DwindleTilingPolicy.spiralOrientations(1, 1000, 600));
        assertArrayEquals(new int[0], DwindleTilingPolicy.spiralOrientations(0, 1000, 600));
    }

    @Test
    public void placeNewWindow_splitsTheLongerSideAndTheNewWindowOpensAfterTheFocusedOne() {
        DwindleTilingPolicy.Placement wide = DwindleTilingPolicy.placeNewWindow(2000, 800, 400, 200, 400, 200, 0);
        assertEquals(DwindleTilingPolicy.KIND_SPLIT, wide.kind);
        assertEquals(LinearLayout.HORIZONTAL, wide.orientation);
        assertEquals(DwindleTilingPolicy.SIDE_RIGHT, wide.side);
        assertFalse(wide.newFirst);
        DwindleTilingPolicy.Placement tall = DwindleTilingPolicy.placeNewWindow(800, 2000, 400, 200, 400, 200, 0);
        assertEquals(LinearLayout.VERTICAL, tall.orientation);
        assertEquals(DwindleTilingPolicy.SIDE_BOTTOM, tall.side);
        assertEquals(LinearLayout.VERTICAL,
            DwindleTilingPolicy.placeNewWindow(1000, 1000, 100, 100, 100, 100, 0).orientation);
    }

    @Test
    public void placeNewWindow_joinsAsATabWhenAHalfWouldBeUnderItsMinimum() {
        float min40 = DwindleTilingPolicy.minWidthForColumns(DwindleTilingPolicy.TERMINAL_MIN_COLUMNS, 10f);
        assertEquals(400f, min40, 0f);
        // wide tile: halves are 450 wide, fine
        assertEquals(DwindleTilingPolicy.KIND_SPLIT,
            DwindleTilingPolicy.placeNewWindow(900, 500, min40, 100, min40, 100, 0).kind);
        // 790 wide: halves of 395 < 400, tab
        DwindleTilingPolicy.Placement tab = DwindleTilingPolicy.placeNewWindow(790, 500, min40, 100, min40, 100, 0);
        assertTrue(tab.isTab());
        assertEquals(-1, tab.side);
        // a tall tile stacks: the width stays whole, the height halves
        assertEquals(DwindleTilingPolicy.KIND_SPLIT,
            DwindleTilingPolicy.placeNewWindow(500, 900, min40, 100, min40, 100, 0).kind);
        assertTrue(DwindleTilingPolicy.placeNewWindow(500, 190, min40, 100, min40, 100, 0).isTab());
        // a tile narrower than the minimum is already under it: tab, whatever the orientation
        assertTrue(DwindleTilingPolicy.placeNewWindow(300, 900, min40, 100, min40, 100, 0).isTab());
    }

    @Test
    public void placeNewWindow_judgesTheNewWindowsOwnMinimumToo() {
        // the focused terminal fits a half, the new app's minimum does not
        assertTrue(DwindleTilingPolicy.placeNewWindow(1000, 600, 400, 100, 600, 100, 0).isTab());
        assertTrue(DwindleTilingPolicy.placeNewWindow(1000, 600, 400, 100, 400, 100, 0).kind == DwindleTilingPolicy.KIND_SPLIT);
        // height minimum of the new app on a stacked split
        assertTrue(DwindleTilingPolicy.placeNewWindow(600, 1000, 100, 100, 100, 600, 0).isTab());
    }

    @Test
    public void placeNewWindow_unmeasuredTileSplitsAndNoMinimumAlwaysSplits() {
        assertEquals(DwindleTilingPolicy.KIND_SPLIT, DwindleTilingPolicy.placeNewWindow(0, 0, 400, 100, 400, 100, 0).kind);
        assertEquals(DwindleTilingPolicy.KIND_SPLIT, DwindleTilingPolicy.placeNewWindow(10, 10, 0, 0, 0, 0, 0).kind);
    }

    @Test
    public void tile_aNewTabOpensAfterTheShownOneAndShows() {
        DwindleTilingPolicy.Tile t = DwindleTilingPolicy.Tile.single("a");
        assertFalse(t.isTabbed());
        t = t.withTab("b").withTab("c");
        assertEquals(java.util.Arrays.asList("a", "b", "c"), t.windows());
        assertEquals("c", t.shownWindow());
        t = t.show("a").withTab("d");
        assertEquals(java.util.Arrays.asList("a", "d", "b", "c"), t.windows());
        assertEquals("d", t.shownWindow());
        assertTrue(t.isTabbed());
        assertEquals("d", t.show("zzz").shownWindow());
    }

    @Test
    public void tile_closingTheShownTabShowsTheNextElseThePrevious() {
        DwindleTilingPolicy.Tile t = DwindleTilingPolicy.Tile.single("a").withTab("b").withTab("c").show("b");
        DwindleTilingPolicy.Tile afterB = t.without("b");
        assertEquals(java.util.Arrays.asList("a", "c"), afterB.windows());
        assertEquals("c", afterB.shownWindow());
        assertEquals("b", t.without("a").shownWindow());
        assertEquals("b", t.without("c").shownWindow());
        DwindleTilingPolicy.Tile last = DwindleTilingPolicy.Tile.single("a").withTab("b");
        assertEquals("a", last.without("b").shownWindow());
        assertNull(DwindleTilingPolicy.Tile.single("a").without("a"));
        assertEquals(t, t.without("nope"));
    }

    @Test
    public void placeNewWindow_reservesTheDividerGap() {
        assertEquals(DwindleTilingPolicy.KIND_SPLIT,
            DwindleTilingPolicy.placeNewWindow(800, 300, 400, 100, 400, 100, 0).kind);
        assertTrue(DwindleTilingPolicy.placeNewWindow(800, 300, 400, 100, 400, 100, 10).isTab());
        assertTrue(DwindleTilingPolicy.placeNewWindow(300, 800, 100, 400, 100, 400, 10).isTab());
    }

    @Test
    public void placeNewWindow_withATile_answersWhichWindowsShareTheTileAndWhichShows() {
        DwindleTilingPolicy.Tile ab = DwindleTilingPolicy.Tile.single("A").withTab("B");
        DwindleTilingPolicy.Placement tab =
            DwindleTilingPolicy.placeNewWindow(ab, "C", 790, 500, 400, 100, 400, 100, 0);
        assertTrue(tab.isTab());
        assertEquals(java.util.Arrays.asList("A", "B", "C"), tab.tile.windows());
        assertEquals("C", tab.tile.shownWindow());
        DwindleTilingPolicy.Placement split =
            DwindleTilingPolicy.placeNewWindow(ab, "C", 2000, 500, 400, 100, 400, 100, 0);
        assertEquals(DwindleTilingPolicy.KIND_SPLIT, split.kind);
        assertEquals(java.util.Collections.singletonList("C"), split.tile.windows());
    }
}
