package com.termux.app.dock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Application;
import android.os.Build;

import com.termux.app.terminal.TerminalWindowBar.WindowItem;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.util.Arrays;
import java.util.List;

/**
 * The Wall strip's tiles from the run strip's entries: the row's windows and the Home floats
 * after them, a float flagged, each tile with its number, its glyph kept apart and a plain
 * distinguishing label, keyed and shaped alongside the entries.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class)
public class WallStripRowViewTilesTest {

    private static final String TERMINAL = "";

    private static WindowItem chip(String label) {
        return new WindowItem(label, label);
    }

    private static List<RunStripModel.Entry> entries() {
        // Two row windows, then a minimised float on page 2.
        return RunStripModel.build(Arrays.asList(chip(TERMINAL + " nvim"),
            chip(TERMINAL + " home"), chip(TERMINAL + " htop · 2")), 1, 2, -1,
            new boolean[]{true}, "New window");
    }

    @Test
    public void wallEntries_giveATilePerWindow_theFloatFlagged() {
        float[] split = {0f, 0f, 0.5f, 1f, 0.5f, 0f, 1f, 1f};
        List<WallStripRowView.Tile> tiles = WallStripRowView.tilesOf(
            RunStripModel.wallEntries(entries()), Arrays.asList(split, null, null),
            Arrays.asList(11L, 12L, 13L));
        assertEquals(3, tiles.size());
        assertFalse(tiles.get(0).floating);
        assertFalse(tiles.get(1).floating);
        assertTrue(tiles.get(2).floating);
        assertEquals(13L, tiles.get(2).key);
        assertEquals(2, tiles.get(2).chipIndex);
        assertEquals("3", tiles.get(2).number);
        assertEquals("htop", tiles.get(2).label);
        assertEquals(TERMINAL, tiles.get(2).glyph);
        assertTrue(tiles.get(1).current);
        assertEquals(8, tiles.get(0).shape.length);
        assertEquals(4, tiles.get(2).shape.length);
    }

    @Test
    public void rowEntries_leaveTheFloatOut() {
        List<WallStripRowView.Tile> tiles = WallStripRowView.tilesOf(
            RunStripModel.rowEntries(entries()), null);
        assertEquals(2, tiles.size());
        assertEquals("nvim", tiles.get(0).label);
        assertEquals("1", tiles.get(0).number);
    }

    @Test
    public void aTileLabel_isPlainText_noSymbolTheMonospaceFaceLacks() {
        for (WallStripRowView.Tile tile : WallStripRowView.tilesOf(
                RunStripModel.wallEntries(entries()), null)) {
            tile.label.codePoints().forEach(cp -> assertFalse(RunStripModel.isNerdSymbol(cp)));
            tile.number.codePoints().forEach(cp -> assertFalse(RunStripModel.isNerdSymbol(cp)));
        }
    }

    @Test
    public void aFloatFlagChange_isATileChange() {
        List<WallStripRowView.Tile> asFloat = WallStripRowView.tilesOf(
            RunStripModel.wallEntries(entries()), null);
        List<RunStripModel.Entry> asRow = RunStripModel.build(Arrays.asList(
            chip(TERMINAL + " nvim"), chip(TERMINAL + " home"), chip(TERMINAL + " htop · 2")),
            1, 3, -1, null, "New window");
        List<WallStripRowView.Tile> asWindow = WallStripRowView.tilesOf(
            RunStripModel.wallEntries(asRow), null);
        assertFalse(asFloat.get(2).equals(asWindow.get(2)));
    }
}
