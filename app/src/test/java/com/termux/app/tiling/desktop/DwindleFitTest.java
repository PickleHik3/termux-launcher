package com.termux.app.tiling.desktop;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.termux.app.terminal.DwindleTilingPolicy;
import com.termux.app.tiling.DwindleTree;
import com.termux.app.tiling.ScrollingLayoutPolicy;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/** Whether a Dwindle tile splits or takes a tab (#420), through the tree it decides for. */
public class DwindleFitTest {

    private static final float CELL_W = 7.2f;
    private static final float CELL_H = 15f;
    private static final float MIN_W = DwindleTilingPolicy.minWidthForColumns(DwindleTilingPolicy.TERMINAL_MIN_COLUMNS, CELL_W);
    private static final float MIN_H = DwindleTilingPolicy.minHeightForRows(DwindleTilingPolicy.TERMINAL_MIN_ROWS, CELL_H);
    private static final float GAP = 8f;
    private static final float STRIP = 26f;

    private static final DwindleFit.Minimums TERMINALS = new DwindleFit.Minimums() {
        @Override public float width(long windowId) { return MIN_W; }
        @Override public float height(long windowId) { return MIN_H; }
    };

    @Test
    public void thirteenTerminalsSwitchedToDwindleInAPhoneRoomStayUsable() {
        float width = 400f;
        float height = 700f;
        List<DwindleTree.Tile> columns = new ArrayList<>();
        for (long id = 1; id <= 13; id++) columns.add(new DwindleTree.Tile(new long[] {id}, id));
        DwindleTree tree = DwindleTree.empty();
        List<DwindleTree.Join> joins = tree.build(columns, width, height, GAP, new DwindleFit(TERMINALS, GAP, STRIP));

        int windows = 0;
        for (DwindleTree.Tile tile : tree.tiles()) windows += tile.windows.length;
        assertEquals(13, windows);
        assertTrue("some joined as tabs", !joins.isEmpty());
        for (ScrollingLayoutPolicy.Placement p : tree.layout(width, height, GAP, STRIP).layout.placements) {
            assertTrue("window " + p.windowId + " is " + p.width() + " wide", p.width() >= MIN_W);
            assertTrue("window " + p.windowId + " is " + p.height() + " tall", p.height() >= MIN_H);
        }
    }

    @Test
    public void aTabbedTileIsJudgedByItsWholeRoomAlongTheTreesSplit() {
        // 360×380 with a 26px strip: the tree stacks it (taller than wide), and two halves of 186
        // hold a terminal each, the tabbed one with its strip too. Judged below the strip
        // (360×354, wider than tall) it would split side by side into 176 wide and take a tab.
        DwindleFit fit = new DwindleFit(TERMINALS, GAP, STRIP);
        assertTrue(fit.splits(360f, 380f, new DwindleTree.Tile(new long[] {1, 2}, 2),
            new DwindleTree.Tile(new long[] {3}, 3)));
    }

    @Test
    public void aTabbedSideKeepsItsStripWhenSplit() {
        // 300×340 stacks into halves of (340 - 8) / 2 = 166: a single terminal fits, a tabbed one with its strip does not.
        DwindleFit fit = new DwindleFit(TERMINALS, GAP, STRIP);
        assertTrue(fit.splits(300f, 340f, new DwindleTree.Tile(new long[] {1}, 1),
            new DwindleTree.Tile(new long[] {3}, 3)));
        assertTrue(!fit.splits(300f, 340f, new DwindleTree.Tile(new long[] {1, 2}, 2),
            new DwindleTree.Tile(new long[] {3}, 3)));
    }
}
