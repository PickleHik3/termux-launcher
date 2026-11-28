package com.termux.app.tiling.desktop;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.termux.app.tiling.DwindleTree;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class NameStripPolicyTest {
    @Test public void aWindowOnATabStripIsNamedThere() {
        DwindleTree tree = DwindleTree.empty();
        tree.sync(Arrays.asList(new DwindleTree.Tile(new long[] {1, 2}, 1), new DwindleTree.Tile(new long[] {3}, 3)),
            1000f, 600f, 4f);
        DwindleTree.Tiles tiles = tree.layout(1000f, 600f, 4f, 26f);
        assertEquals(1, tiles.strips.size());
        assertEquals(Arrays.asList(3L), NameStripPolicy.named(Arrays.asList(1L, 3L), tiles.strips));
    }

    @Test public void withoutStripsEveryLiveWindowIsNamed() {
        assertEquals(Arrays.asList(5L, 6L), NameStripPolicy.named(Arrays.asList(5L, 6L),
            Collections.<DwindleTree.TabStrip>emptyList()));
    }

    @Test public void noLiveWindowsNoStrips() {
        List<Long> none = NameStripPolicy.named(Collections.<Long>emptyList(), Collections.<DwindleTree.TabStrip>emptyList());
        assertEquals(0, none.size());
    }

    @Test public void aTerminalKeepsItsRowsClearOfTheStrip() {
        int strip = NameStripPolicy.insetPx(16f, 3f, true);
        assertEquals(48, strip);
        assertEquals(0, NameStripPolicy.insetPx(16f, 3f, false));
        int height = 1200;
        int without = com.termux.view.TerminalView.rowsFor(height, 0, 30, 40);
        int with = com.termux.view.TerminalView.rowsFor(height, strip, 30, 40);
        assertEquals(29, without);
        assertEquals(28, with);
        // The text starts below the strip: rows times line height fit in what the strip leaves.
        assertTrue(strip + 30 + with * 40 <= height);
    }

    @Test public void theIconStandsClearOfTheWindowsCorner() {
        // A 42 px corner and an icon 9 px down: the corner's edge there is 42 - sqrt(42^2 - 33^2) = 16.02.
        assertEquals(17f, NameStripPolicy.leadPx(42f, 9f, 12f), 0f);
        assertEquals("a small corner leaves the padding", 12f, NameStripPolicy.leadPx(6f, 9f, 12f), 0f);
        assertEquals("square windows keep the padding", 12f, NameStripPolicy.leadPx(0f, 0f, 12f), 0f);
    }

    /** Round 5: the Float's title sat on its rim; it clears the corner and the rim as a tile's name does. */
    @Test
    public void aFloatsTitleClearsItsCornerAndRim() {
        // A small corner: the pad, then the rim.
        assertEquals(12f, NameStripPolicy.floatTitleLeadPx(4f, 2f, 10f), 0f);
        // A round corner pushes it in further than the pad.
        float lead = NameStripPolicy.floatTitleLeadPx(24f, 2f, 10f);
        assertTrue(lead > 12f);
        assertEquals(NameStripPolicy.leadPx(24f, 2f, 10f) + 2f, lead, 0f);
        // No corner, no rim: the pad alone.
        assertEquals(10f, NameStripPolicy.floatTitleLeadPx(0f, 0f, 10f), 0f);
    }
}
