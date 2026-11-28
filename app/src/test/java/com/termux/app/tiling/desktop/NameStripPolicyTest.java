package com.termux.app.tiling.desktop;

import static org.junit.Assert.assertEquals;

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
}
