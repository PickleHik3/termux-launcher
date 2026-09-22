package com.termux.view;

import android.app.Application;
import android.os.Build;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;

/**
 * The nodes are sized to the visible rows and no larger: a pane that shrinks must let go of the
 * recordings of the rows it lost, and a renderer that is dropped must let go of all of them. All
 * three nodes of a row travel together — the image node is bounded by the visible rows exactly as
 * the background and the glyphs are, and is freed with them.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.Q, application = Application.class)
public class TerminalRowNodesTest {

    @Test
    public void growingKeepsTheRowsItAlreadyHadAndShrinkingDropsTheRest() {
        TerminalRowNodes nodes = new TerminalRowNodes();

        nodes.resize(4);
        assertEquals(4, nodes.size());
        Object firstBackground = nodes.background(0);
        Object firstGlyphs = nodes.glyphs(0);
        Object firstImages = nodes.images(0);

        nodes.resize(6);
        assertEquals(6, nodes.size());
        assertSame("a row that is still on screen keeps its recording",
            firstBackground, nodes.background(0));
        assertSame(firstGlyphs, nodes.glyphs(0));
        assertSame(firstImages, nodes.images(0));

        nodes.resize(2);
        assertEquals(2, nodes.size());
        assertSame(firstBackground, nodes.background(0));
        assertSame(firstImages, nodes.images(0));
    }

    @Test
    public void everyRowHasAnImageNodeOfItsOwn() {
        TerminalRowNodes nodes = new TerminalRowNodes();
        nodes.resize(3);

        for (int row = 0; row < 3; row++) {
            assertNotSame("a row's images must not share a node with its glyphs",
                nodes.glyphs(row), nodes.images(row));
            assertNotSame(nodes.background(row), nodes.images(row));
        }
        assertNotSame("and no two rows may share one", nodes.images(0), nodes.images(1));
    }

    @Test
    public void discardingLetsGoOfEveryRow() {
        TerminalRowNodes nodes = new TerminalRowNodes();
        nodes.resize(3);
        Object firstBackground = nodes.background(0);
        Object firstImages = nodes.images(0);

        nodes.discard();
        assertEquals(0, nodes.size());

        nodes.resize(3);
        assertNotSame("nothing survives a discard", firstBackground, nodes.background(0));
        assertNotSame(firstImages, nodes.images(0));
    }
}
