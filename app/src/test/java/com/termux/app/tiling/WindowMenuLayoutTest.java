package com.termux.app.tiling;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/** How the layouts read a held mark's changes (#425): reorder, minimised, maximised. */
public class WindowMenuLayoutTest {

    private static final float W = 1000f;
    private static final float H = 2000f;
    private static final float GAP = 20f;
    private static final ScrollingLayoutPolicy.Viewport PORTRAIT =
        new ScrollingLayoutPolicy.Viewport(W, H, GAP, false);

    private static TilingStack withShells(int count) {
        TilingStack stack = TilingStack.empty(1);
        stack.goTo(Place.workspace(1));
        for (int i = 0; i < count; i++) stack.open(WindowSpec.shell(null));
        return stack;
    }

    private static ScrollingLayoutPolicy.Placement placementOf(ScrollingLayoutPolicy.Layout layout, long id) {
        for (ScrollingLayoutPolicy.Placement p : layout.placements) if (p.windowId == id) return p;
        return null;
    }

    private static List<Long> ids(ScrollingLayoutPolicy.Layout layout) {
        List<Long> ids = new ArrayList<>();
        for (ScrollingLayoutPolicy.Placement p : layout.placements) ids.add(p.windowId);
        return ids;
    }

    @Test
    public void aMinimisedWindowHasNoColumn() {
        TilingStack stack = withShells(3);
        stack.setMinimised(2, true);

        ScrollingLayoutPolicy.State state = ScrollingColumns.sync(stack.workspace(1), null, PORTRAIT);

        assertEquals(2, state.columnCount());
        assertEquals(java.util.Arrays.asList(1L, 3L), ids(ScrollingLayoutPolicy.layout(state, PORTRAIT)));
    }

    @Test
    public void aMinimisedWindowHasNoDwindleTileAndItsSiblingTakesTheRoom() {
        TilingStack stack = withShells(2);
        DwindleTree tree = DwindleTree.empty();
        tree.sync(DwindleTree.tilesOf(stack.workspace(1)), W, H, GAP);
        stack.setMinimised(2, true);

        tree.sync(DwindleTree.tilesOf(stack.workspace(1)), W, H, GAP);
        DwindleTree.Tiles tiles = tree.layout(W, H, GAP, 0f);

        assertEquals(1, tiles.layout.placements.size());
        assertEquals(H, placementOf(tiles.layout, 1).bottom, 0.01f);
    }

    @Test
    public void aReorderedDwindleWorkspaceMovesTheWindowsAndKeepsTheTiles() {
        TilingStack stack = withShells(3);
        DwindleTree tree = DwindleTree.empty();
        tree.sync(DwindleTree.tilesOf(stack.workspace(1)), W, H, GAP);
        ScrollingLayoutPolicy.Layout before = tree.layout(W, H, GAP, 0f).layout;
        ScrollingLayoutPolicy.Placement firstRoom = placementOf(before, 1);
        ScrollingLayoutPolicy.Placement thirdRoom = placementOf(before, 3);

        stack.moveWithin(1, 2);
        assertTrue(tree.reorder(DwindleTree.tilesOf(stack.workspace(1))));
        tree.sync(DwindleTree.tilesOf(stack.workspace(1)), W, H, GAP);
        ScrollingLayoutPolicy.Layout after = tree.layout(W, H, GAP, 0f).layout;

        // Window 2 now stands where 1 stood, and 1 where 3 stood.
        assertEquals(firstRoom.left, placementOf(after, 2).left, 0.01f);
        assertEquals(firstRoom.top, placementOf(after, 2).top, 0.01f);
        assertEquals(thirdRoom.left, placementOf(after, 1).left, 0.01f);
        assertEquals(thirdRoom.top, placementOf(after, 1).top, 0.01f);
    }

    @Test
    public void aReorderThatChangesTheTilesIsLeftToTheSync() {
        TilingStack stack = withShells(2);
        DwindleTree tree = DwindleTree.empty();
        tree.sync(DwindleTree.tilesOf(stack.workspace(1)), W, H, GAP);
        stack.open(WindowSpec.shell(null));

        assertEquals(false, tree.reorder(DwindleTree.tilesOf(stack.workspace(1))));
    }

    @Test
    public void aMaximisedWindowFillsTheRoomAloneWhereTheScreenIs() {
        TilingStack stack = withShells(3);
        ScrollingLayoutPolicy.State state = ScrollingColumns.sync(stack.workspace(1), null, PORTRAIT);
        ScrollingLayoutPolicy.Layout layout = ScrollingLayoutPolicy.layout(state, PORTRAIT);

        ScrollingLayoutPolicy.Layout max = MaximisedLayout.over(layout, 2, 640f, W, H);

        assertEquals(1, max.placements.size());
        ScrollingLayoutPolicy.Placement p = max.placements.get(0);
        assertEquals(2, p.windowId);
        assertEquals(640f, p.left, 0.01f);
        assertEquals(640f + W, p.right, 0.01f);
        assertEquals(0f, p.top, 0.01f);
        assertEquals(H, p.bottom, 0.01f);
        assertEquals(layout.stripWidth, max.stripWidth, 0.01f);
    }

    @Test
    public void aWindowNotInTheLayoutLeavesItAsItIs() {
        TilingStack stack = withShells(2);
        ScrollingLayoutPolicy.State state = ScrollingColumns.sync(stack.workspace(1), null, PORTRAIT);
        ScrollingLayoutPolicy.Layout layout = ScrollingLayoutPolicy.layout(state, PORTRAIT);

        assertSame(layout, MaximisedLayout.over(layout, 99, 0f, W, H));
    }

    @Test
    public void aMaximisedDwindleTileDropsTheTabStrips() {
        TilingStack stack = withShells(2);
        stack.joinTab(2, 1);
        stack.open(WindowSpec.shell(null));
        DwindleTree tree = DwindleTree.empty();
        tree.sync(DwindleTree.tilesOf(stack.workspace(1)), W, H, GAP);
        DwindleTree.Tiles tiles = tree.layout(W, H, GAP, 40f);
        assertEquals(1, tiles.strips.size());

        DwindleTree.Tiles max = MaximisedLayout.over(tiles, 3, W, H);

        assertNotNull(max);
        assertEquals(0, max.strips.size());
        assertEquals(1, max.layout.placements.size());
        assertEquals(3, max.layout.placements.get(0).windowId);
        assertEquals(W, max.layout.placements.get(0).right, 0.01f);
    }
}
