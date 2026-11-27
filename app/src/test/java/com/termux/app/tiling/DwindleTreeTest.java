package com.termux.app.tiling;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.termux.app.wall.BorderDrag;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** A Dwindle Workspace's split tree (#420), through its public interface. */
public class DwindleTreeTest {

    private static final float W = 1000f;
    private static final float H = 2000f;
    private static final float GAP = 20f;

    private static TilingStack withShells(int count) {
        TilingStack stack = TilingStack.empty(1);
        for (int i = 0; i < count; i++) stack.openIn(Place.workspace(1), WindowSpec.shell(null), false);
        return stack;
    }

    private static DwindleTree synced(TilingWorkspace workspace, DwindleTree tree) {
        tree.sync(DwindleTree.tilesOf(workspace), W, H, GAP);
        return tree;
    }

    private static ScrollingLayoutPolicy.Placement placementOf(DwindleTree.Tiles tiles, long id) {
        for (ScrollingLayoutPolicy.Placement p : tiles.layout.placements) if (p.windowId == id) return p;
        return null;
    }

    private static void assertRect(ScrollingLayoutPolicy.Placement p, float l, float t, float r, float b) {
        assertNotNull(p);
        assertEquals(l, p.left, 0.01f);
        assertEquals(t, p.top, 0.01f);
        assertEquals(r, p.right, 0.01f);
        assertEquals(b, p.bottom, 0.01f);
    }

    @Test
    public void oneWindowFillsTheRoom() {
        TilingStack stack = withShells(1);
        DwindleTree tree = synced(stack.workspace(1), DwindleTree.empty());
        DwindleTree.Tiles tiles = tree.layout(W, H, GAP, 0f);

        assertEquals(1, tiles.layout.placements.size());
        assertRect(placementOf(tiles, 1), 0f, 0f, W, H);
        assertEquals(W, tiles.layout.stripWidth, 0.01f);
    }

    @Test
    public void eachNewWindowSplitsTheOneBeforeItAlongItsLongerSide() {
        TilingStack stack = withShells(3);
        DwindleTree tree = synced(stack.workspace(1), DwindleTree.empty());
        DwindleTree.Tiles tiles = tree.layout(W, H, GAP, 0f);

        // 1000×2000 is tall: stacked. Its lower half 1000×990 is wider than tall: side by side.
        float half = (H - GAP) / 2f;
        assertRect(placementOf(tiles, 1), 0f, 0f, W, half);
        float lowerTop = half + GAP;
        float halfW = (W - GAP) / 2f;
        assertRect(placementOf(tiles, 2), 0f, lowerTop, halfW, H);
        assertRect(placementOf(tiles, 3), halfW + GAP, lowerTop, W, H);
    }

    @Test
    public void aWindowOpenedAfterTheFocusedOneSplitsThatTile() {
        TilingStack stack = withShells(2);
        DwindleTree tree = synced(stack.workspace(1), DwindleTree.empty());
        stack.focusWindow(1);
        long opened = stack.open(WindowSpec.shell(null));
        synced(stack.workspace(1), tree);
        DwindleTree.Tiles tiles = tree.layout(W, H, GAP, 0f);

        float half = (H - GAP) / 2f;
        float halfW = (W - GAP) / 2f;
        // Window 1's upper half splits side by side; window 2 keeps the lower half.
        assertRect(placementOf(tiles, 1), 0f, 0f, halfW, half);
        assertRect(placementOf(tiles, opened), halfW + GAP, 0f, W, half);
        assertRect(placementOf(tiles, 2), 0f, half + GAP, W, H);
        assertEquals(Arrays.asList(1L, opened, 2L), shownInOrder(tree));
    }

    @Test
    public void aClosedWindowsSiblingTakesItsSpace() {
        TilingStack stack = withShells(3);
        DwindleTree tree = synced(stack.workspace(1), DwindleTree.empty());
        stack.close(2);
        synced(stack.workspace(1), tree);
        DwindleTree.Tiles tiles = tree.layout(W, H, GAP, 0f);

        float half = (H - GAP) / 2f;
        assertRect(placementOf(tiles, 1), 0f, 0f, W, half);
        assertRect(placementOf(tiles, 3), 0f, half + GAP, W, H);
    }

    @Test
    public void aTabbedTileShowsItsActiveWindowBelowItsTabStrip() {
        TilingStack stack = withShells(2);
        DwindleTree tree = synced(stack.workspace(1), DwindleTree.empty());
        long tab = stack.open(WindowSpec.shell(null));
        stack.joinTab(tab, 1);
        synced(stack.workspace(1), tree);
        DwindleTree.Tiles tiles = tree.layout(W, H, GAP, 30f);

        float half = (H - GAP) / 2f;
        assertEquals(2, tiles.layout.placements.size());
        assertNull(placementOf(tiles, 1));
        assertRect(placementOf(tiles, tab), 0f, 30f, W, half);
        assertEquals(1, tiles.strips.size());
        DwindleTree.TabStrip strip = tiles.strips.get(0);
        assertEquals(Arrays.toString(new long[] {1, tab}), Arrays.toString(strip.windows));
        assertEquals(tab, strip.shown);
        assertEquals(0f, strip.top, 0.01f);
        assertEquals(30f, strip.bottom, 0.01f);
        assertEquals(W, strip.right, 0.01f);

        // A tap on the first tab shows it: the stack's focus moves, the tile follows.
        stack.focusWindow(1);
        synced(stack.workspace(1), tree);
        tiles = tree.layout(W, H, GAP, 30f);
        assertRect(placementOf(tiles, 1), 0f, 30f, W, half);
        assertEquals(1, tiles.strips.get(0).shown);
    }

    @Test
    public void aWindowJoiningAnotherAsATabLeavesItsOwnTile() {
        TilingStack stack = withShells(2);
        DwindleTree tree = synced(stack.workspace(1), DwindleTree.empty());
        stack.joinTab(1, 2);
        synced(stack.workspace(1), tree);

        // Window 2's tile keeps the room it had; window 1's goes and its room with it.
        assertEquals(1, tree.tiles().size());
        DwindleTree.Tiles tiles = tree.layout(W, H, GAP, 0f);
        assertRect(placementOf(tiles, 1), 0f, 0f, W, H);
    }

    @Test
    public void theTreeAndItsTabsRoundTripThroughTheSavedForm() throws Exception {
        TilingStack stack = withShells(3);
        TilingWorkspace one = stack.workspace(1);
        stack.setLayout(1, WorkspaceLayout.DWINDLE);
        DwindleTree tree = synced(one, DwindleTree.empty());
        long tab = stack.open(WindowSpec.shell(null));
        stack.joinTab(tab, 3);
        synced(one, tree);
        tree.setRatio(1, 0.3f);
        stack.setLayoutState(1, tree.encode());

        String saved = TilingDesktopStore.encode(stack, java.util.Collections.<Long, String>emptyMap(),
            java.util.Collections.<Long, Long>emptyMap());
        TilingDesktopStore.Saved back = TilingDesktopStore.decode(saved);
        assertNotNull(back);
        TilingWorkspace restored = back.stack.workspace(1);
        assertEquals(WorkspaceLayout.DWINDLE, restored.layout());
        DwindleTree read = DwindleTree.decode(restored.layoutState());
        assertNotNull(read);
        read.sync(DwindleTree.tilesOf(restored), W, H, GAP);

        DwindleTree.Tiles before = tree.layout(W, H, GAP, 30f);
        DwindleTree.Tiles after = read.layout(W, H, GAP, 30f);
        assertEquals(before.layout.placements.size(), after.layout.placements.size());
        for (ScrollingLayoutPolicy.Placement p : before.layout.placements) {
            ScrollingLayoutPolicy.Placement q = placementOf(after, p.windowId);
            assertRect(q, p.left, p.top, p.right, p.bottom);
        }
        assertEquals(1, after.strips.size());
        assertEquals(tab, after.strips.get(0).shown);
        assertEquals(Arrays.toString(new long[] {3, tab}), Arrays.toString(after.strips.get(0).windows));
    }

    @Test
    public void anUnreadableSavedFormReadsAsNothing() {
        assertNull(DwindleTree.decode(null));
        assertNull(DwindleTree.decode(""));
        assertNull(DwindleTree.decode("{\"columns\":[]}"));
        assertNull(DwindleTree.decode("not json"));
    }

    @Test
    public void aGapDragMovesTheSplitAndClamps() {
        TilingStack stack = withShells(2);
        DwindleTree tree = synced(stack.workspace(1), DwindleTree.empty());
        assertTrue(tree.setRatio(0, 0.25f));
        DwindleTree.Tiles tiles = tree.layout(W, H, GAP, 0f);
        float top = (H - GAP) * 0.25f;
        assertRect(placementOf(tiles, 1), 0f, 0f, W, top);
        assertTrue(tree.setRatio(0, 0f));
        assertEquals(DwindleTree.MIN_RATIO, tree.ratioOf(0), 0.0001f);
        assertFalse(tree.setRatio(99, 0.5f));
    }

    @Test
    public void aStepGoesToTheNearestTileThatWay() {
        TilingStack stack = withShells(3);
        DwindleTree tree = synced(stack.workspace(1), DwindleTree.empty());
        List<ScrollingLayoutPolicy.Placement> placements = tree.layout(W, H, GAP, 0f).layout.placements;

        assertEquals(2, DwindleTree.neighbour(placements, 1, BorderDrag.Border.BOTTOM));
        assertEquals(3, DwindleTree.neighbour(placements, 2, BorderDrag.Border.RIGHT));
        assertEquals(1, DwindleTree.neighbour(placements, 3, BorderDrag.Border.TOP));
        assertEquals(2, DwindleTree.neighbour(placements, 3, BorderDrag.Border.LEFT));
        assertEquals(ScrollingLayoutPolicy.NO_WINDOW, DwindleTree.neighbour(placements, 1, BorderDrag.Border.TOP));
        assertEquals(ScrollingLayoutPolicy.NO_WINDOW, DwindleTree.neighbour(placements, 3, BorderDrag.Border.RIGHT));
    }

    private static List<Long> shownInOrder(DwindleTree tree) {
        List<Long> ids = new ArrayList<>();
        for (DwindleTree.Tile tile : tree.tiles()) ids.add(tile.shown);
        return ids;
    }
}
