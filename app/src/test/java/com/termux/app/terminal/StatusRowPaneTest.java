package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.widget.LinearLayout;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The thin row belongs to the panes along the window's top, from the one on screen at the
 * top-left to the one at the top-right, whichever way the window reads.
 */
public class StatusRowPaneTest {

    @Test
    public void stackedPanesGiveTheRowToTheTopOneInEitherDirection() {
        assertTrue(StatusRowPane.takesFirst(false, false));
        assertTrue(StatusRowPane.takesFirst(false, true));
    }

    @Test
    public void sideBySideGivesTheRowToTheLeftPane() {
        assertTrue("left to right: the first child is on the left",
            StatusRowPane.takesFirst(true, false));
        assertFalse("right to left: the first child is on the right",
            StatusRowPane.takesFirst(true, true));
    }

    private static TerminalPaneController.Leaf leaf() {
        return new TerminalPaneController.Leaf(null);
    }

    private static TerminalPaneController.Split split(int orientation, TerminalPaneController.Node a,
                                                      TerminalPaneController.Node b) {
        TerminalPaneController.Split split = new TerminalPaneController.Split();
        split.orientation = orientation;
        split.a = a;
        split.b = b;
        a.parent = split;
        b.parent = split;
        return split;
    }

    private static TerminalPaneController.Split columns(TerminalPaneController.Node a,
                                                        TerminalPaneController.Node b) {
        return split(LinearLayout.HORIZONTAL, a, b);
    }

    private static TerminalPaneController.Split rows(TerminalPaneController.Node a,
                                                     TerminalPaneController.Node b) {
        return split(LinearLayout.VERTICAL, a, b);
    }

    private static List<TerminalPaneController.Leaf> alongTop(TerminalPaneController.Node root) {
        List<TerminalPaneController.Leaf> out = new ArrayList<>();
        StatusRowPane.collectAlongTop(root, out);
        return out;
    }

    @Test
    public void onePaneHoldsTheTopAlone() {
        TerminalPaneController.Leaf a = leaf();
        assertEquals(1, StatusRowPane.panesAlongTop(a));
        assertTrue(StatusRowPane.alongTop(a, a));
        assertSame(a, StatusRowPane.endAlongTop(a, false, false));
        assertSame(a, StatusRowPane.endAlongTop(a, true, false));
        assertEquals(0, StatusRowPane.panesAlongTop(null));
    }

    @Test
    public void rowsOnlyKeepTheRowInTheTopPane() {
        TerminalPaneController.Leaf a = leaf();
        TerminalPaneController.Leaf b = leaf();
        TerminalPaneController.Split root = rows(a, b);
        assertEquals(1, StatusRowPane.panesAlongTop(root));
        assertTrue(StatusRowPane.alongTop(a, root));
        assertFalse(StatusRowPane.alongTop(b, root));
        assertSame(a, StatusRowPane.endAlongTop(root, false, false));
        assertSame("one pane holds the top: both ends are it", a,
            StatusRowPane.endAlongTop(root, true, false));
    }

    @Test
    public void twoColumnsBothHoldTheTopAndTheRowRunsLeftToRight() {
        TerminalPaneController.Leaf a = leaf();
        TerminalPaneController.Leaf b = leaf();
        TerminalPaneController.Split root = columns(a, b);
        assertEquals(2, StatusRowPane.panesAlongTop(root));
        assertTrue(StatusRowPane.alongTop(a, root));
        assertTrue(StatusRowPane.alongTop(b, root));
        assertSame(a, StatusRowPane.endAlongTop(root, false, false));
        assertSame(b, StatusRowPane.endAlongTop(root, true, false));
        // Right to left the first child lies on the right.
        assertSame(b, StatusRowPane.endAlongTop(root, false, true));
        assertSame(a, StatusRowPane.endAlongTop(root, true, true));
    }

    @Test
    public void threeColumnsAllHoldTheTop() {
        TerminalPaneController.Leaf a = leaf();
        TerminalPaneController.Leaf b = leaf();
        TerminalPaneController.Leaf c = leaf();
        TerminalPaneController.Split root = columns(a, columns(b, c));
        assertEquals(3, StatusRowPane.panesAlongTop(root));
        assertEquals(Arrays.asList(a, b, c), alongTop(root));
        assertSame(a, StatusRowPane.endAlongTop(root, false, false));
        assertSame(c, StatusRowPane.endAlongTop(root, true, false));
        assertSame(c, StatusRowPane.endAlongTop(root, false, true));
        assertSame(a, StatusRowPane.endAlongTop(root, true, true));
    }

    @Test
    public void twoColumnsWithOneSplitIntoRowsCountTheTopPaneOfEach() {
        TerminalPaneController.Leaf a = leaf();
        TerminalPaneController.Leaf below = leaf();
        TerminalPaneController.Leaf c = leaf();
        TerminalPaneController.Split root = columns(rows(a, below), c);
        assertEquals(2, StatusRowPane.panesAlongTop(root));
        assertEquals(Arrays.asList(a, c), alongTop(root));
        assertTrue(StatusRowPane.alongTop(a, root));
        assertFalse(StatusRowPane.alongTop(below, root));
        assertTrue(StatusRowPane.alongTop(c, root));
        assertSame(a, StatusRowPane.endAlongTop(root, false, false));
        assertSame(c, StatusRowPane.endAlongTop(root, true, false));
    }

    @Test
    public void columnsInTheUpperRowHoldTheTopAndTheLowerRowNone() {
        TerminalPaneController.Leaf a = leaf();
        TerminalPaneController.Leaf b = leaf();
        TerminalPaneController.Leaf c = leaf();
        TerminalPaneController.Leaf d = leaf();
        TerminalPaneController.Split root = rows(columns(a, columns(b, c)), d);
        assertEquals(3, StatusRowPane.panesAlongTop(root));
        assertFalse(StatusRowPane.alongTop(d, root));
        assertSame(c, StatusRowPane.endAlongTop(root, true, false));
    }

    @Test
    public void aStackedOrMaximizedPaneShownAloneHoldsTheTop() {
        TerminalPaneController.Leaf a = leaf();
        TerminalPaneController.Leaf b = leaf();
        rows(a, b);
        // The stack shows the active pane alone, whatever its place in the tree.
        assertTrue(StatusRowPane.alongTop(b, b));
        assertFalse("a pane outside the tree shown", StatusRowPane.alongTop(a, b));
        assertEquals(1, StatusRowPane.panesAlongTop(b));
    }
}
