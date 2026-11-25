package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import android.widget.LinearLayout;

import org.junit.Test;

/** A leaf may hold a Linux window (#387); a split's saved shape skips it. */
public final class TerminalPaneLinuxLeafTest {

    private static TerminalPaneController.Split split(int orientation, TerminalPaneController.Node a,
                                                      TerminalPaneController.Node b) {
        TerminalPaneController.Split s = new TerminalPaneController.Split();
        s.orientation = orientation;
        s.a = a;
        s.b = b;
        a.parent = s;
        b.parent = s;
        return s;
    }

    @Test
    public void aLinuxLeafHoldsAWindowAndNoShell() {
        TerminalPaneController.Leaf leaf = TerminalPaneController.Leaf.linux(7L);
        assertTrue(leaf.isLinux());
        assertEquals(7L, leaf.linuxWindowId);
        assertNull(leaf.session);
        assertFalse(new TerminalPaneController.Leaf(null).isLinux());
    }

    @Test
    public void theSavedShapeDropsLinuxLeavesAndPromotesTheirSiblings() {
        TerminalPaneController.Leaf shell = new TerminalPaneController.Leaf(null);
        TerminalPaneController.Leaf app = TerminalPaneController.Leaf.linux(3L);
        TerminalPaneController.Split split = split(LinearLayout.HORIZONTAL, shell, app);
        assertSame(shell, TerminalPaneController.withoutLinux(split));
    }

    @Test
    public void aTreeWithoutLinuxIsSavedAsItIs() {
        TerminalPaneController.Split split = split(LinearLayout.VERTICAL,
            new TerminalPaneController.Leaf(null), new TerminalPaneController.Leaf(null));
        assertSame(split, TerminalPaneController.withoutLinux(split));
    }

    @Test
    public void aNestedLinuxLeafLeavesTheOuterSplitStanding() {
        TerminalPaneController.Leaf a = new TerminalPaneController.Leaf(null);
        TerminalPaneController.Leaf b = new TerminalPaneController.Leaf(null);
        TerminalPaneController.Leaf app = TerminalPaneController.Leaf.linux(9L);
        TerminalPaneController.Split inner = split(LinearLayout.VERTICAL, b, app);
        TerminalPaneController.Split outer = split(LinearLayout.HORIZONTAL, a, inner);
        outer.weightA = 1.5f;
        outer.weightB = 0.5f;
        TerminalPaneController.Node saved = TerminalPaneController.withoutLinux(outer);
        assertTrue(saved instanceof TerminalPaneController.Split);
        TerminalPaneController.Split copy = (TerminalPaneController.Split) saved;
        assertSame(a, copy.a);
        assertSame(b, copy.b);
        assertEquals(1.5f, copy.weightA, 0f);
        assertEquals(0.5f, copy.weightB, 0f);
        // The live tree is untouched.
        assertSame(inner, outer.b);
    }

    @Test
    public void onlyLinuxPanesLeaveNoTree() {
        assertNull(TerminalPaneController.withoutLinux(TerminalPaneController.Leaf.linux(1L)));
    }
}
