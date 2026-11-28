package com.termux.app.tiling;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;

import org.junit.Test;

import java.util.Arrays;

/** A gap drag worked out once (review of terl-window-ops): which windows the gap moves, how, and how far. */
public class GapPreviewTest {

    private static final ScrollingLayoutPolicy.Viewport PORTRAIT =
        new ScrollingLayoutPolicy.Viewport(1000f, 2000f, 20f, false);

    private static int indexOf(GapPreview plan, long id) {
        for (int i = 0; i < plan.count(); i++) if (plan.ids[i] == id) return i;
        return -1;
    }

    private static ScrollingLayoutPolicy.State halves(int count) {
        ScrollingLayoutPolicy.State s = ScrollingLayoutPolicy.State.EMPTY;
        for (int id = 1; id <= count; id++) {
            s = ScrollingLayoutPolicy.open(s, PORTRAIT, id);
            s = ScrollingLayoutPolicy.setWidthPreset(s, PORTRAIT, id, 0.5f);
        }
        return s;
    }

    @Test
    public void aColumnGapMovesItsColumnsEdgeAndEverythingRightOfItOnly() {
        ScrollingLayoutPolicy.Layout layout = ScrollingLayoutPolicy.layout(halves(3), PORTRAIT);
        GapPreview plan = GapPreview.columnGap(layout, 1, 230f, 1000f);
        assertTrue(plan.sideways);
        assertEquals("the column left of the dragged one is not in the plan", -1, indexOf(plan, 1));
        int dragged = indexOf(plan, 2);
        int right = indexOf(plan, 3);
        assertEquals(490f - 100f, plan.widthAt(dragged, -100f), 0.01f);
        assertEquals(510f, plan.leftAt(dragged, -100f), 0.01f);
        assertEquals(1020f - 100f, plan.leftAt(right, -100f), 0.01f);
        assertEquals(490f, plan.widthAt(right, -100f), 0.01f);
        assertEquals("the gap's middle follows", 1010f - 100f, plan.centreAt(-100f), 0.01f);
        assertEquals("never under the minimum", 230f - 490f, plan.delta(-5000f, 0f), 0.01f);
        assertEquals("never over full", 1000f - 490f, plan.delta(5000f, 0f), 0.01f);
        assertEquals("a column gap takes only the sideways travel", -40f, plan.delta(-40f, 300f), 0.01f);
    }

    @Test
    public void aStackGapGrowsTheTopAndMovesAndShrinksTheBottom() {
        ScrollingLayoutPolicy.State s = halves(2);
        s = ScrollingLayoutPolicy.stackInto(s, PORTRAIT, 2, ScrollingLayoutPolicy.LEFT);
        GapPreview plan = GapPreview.stackGap(ScrollingLayoutPolicy.layout(s, PORTRAIT), 0, 396f);
        assertFalse(plan.sideways);
        int top = indexOf(plan, 1);
        int bottom = indexOf(plan, 2);
        assertEquals(990f + 50f, plan.heightAt(top, 50f), 0.01f);
        assertEquals(1010f + 50f, plan.topAt(bottom, 50f), 0.01f);
        assertEquals(990f - 50f, plan.heightAt(bottom, 50f), 0.01f);
        assertEquals(990f - 396f, plan.delta(0f, 5000f), 0.01f);
    }

    /** Fixed minimums: every tile 300 wide and 500 tall at least. */
    private static final DwindleTree.Sizes SIZES = new DwindleTree.Sizes() {
        @Override public float minWidth(@NonNull DwindleTree.Tile tile) { return 300f; }
        @Override public float minHeight(@NonNull DwindleTree.Tile tile) { return 500f; }
    };

    private static DwindleTree threeTiles() {
        // 1000×2000: 1 above, then 2 and 3 side by side below it? The tree splits each new tile's
        // anchor along its longer side: 1 | 2 top over bottom, then 2's room 1000×990 side by side.
        DwindleTree tree = DwindleTree.empty();
        tree.sync(Arrays.asList(new DwindleTree.Tile(new long[] {1}, 1), new DwindleTree.Tile(new long[] {2}, 2),
            new DwindleTree.Tile(new long[] {3}, 3)), 1000f, 2000f, 20f);
        return tree;
    }

    @Test
    public void aSplitsMinimumsAddUpOverNestedSplitsTheSameWayAndTakeTheLargerAcross() {
        DwindleTree tree = threeTiles();
        float[] top = tree.sideMinimums(0, 20f, SIZES);
        assertEquals("the top tile alone", 500f, top[0], 0.01f);
        assertEquals("2 and 3 side by side need one height", 500f, top[1], 0.01f);
        float[] across = tree.sideMinimums(1, 20f, SIZES);
        assertEquals(300f, across[0], 0.01f);
        assertEquals(300f, across[1], 0.01f);
    }

    @Test
    public void aSplitsGapMovesEachTileOfItsRoomWithItsSideAndStopsAtTheMinimums() {
        DwindleTree tree = threeTiles();
        DwindleTree.Tiles tiles = tree.layout(1000f, 2000f, 20f, 0f);
        float[] min = tree.sideMinimums(0, 20f, SIZES);
        GapPreview plan = GapPreview.split(tiles, 0, min[0], min[1]);
        assertFalse(plan.sideways);
        assertEquals(3, plan.count());
        int one = indexOf(plan, 1);
        int two = indexOf(plan, 2);
        int three = indexOf(plan, 3);
        assertEquals(990f + 100f, plan.heightAt(one, 100f), 0.01f);
        assertEquals("the second side moves down with the gap", 1010f + 100f, plan.topAt(two, 100f), 0.01f);
        assertEquals(990f - 100f, plan.heightAt(three, 100f), 0.01f);
        assertEquals("the top keeps 500", 500f - 990f, plan.delta(0f, -5000f), 0.01f);
        assertEquals("the nested bottom keeps 500", 990f - 500f, plan.delta(0f, 5000f), 0.01f);
        // The nested gap moves only the two tiles it divides.
        float[] across = tree.sideMinimums(1, 20f, SIZES);
        GapPreview nested = GapPreview.split(tiles, 1, across[0], across[1]);
        assertEquals(2, nested.count());
        assertEquals(-1, indexOf(nested, 1));
    }
}
