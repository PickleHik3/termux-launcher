package com.termux.app.wall.pageswitch;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.page.PageKind;
import com.termux.app.wall.pageswitch.MarkControls.Control;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class MarkControlsTest {

    private static PageBarModel.Entry only(PageBarModel.Input in, PageKind kind) {
        for (PageBarModel.Entry e : PageBarModel.build(in).entries()) if (e.kind == kind) return e;
        throw new AssertionError("no " + kind);
    }

    private static PageBarModel.Input wall(PageKind... kinds) {
        PageBarModel.Input in = new PageBarModel.Input();
        PageBarModel.Page[] pages = new PageBarModel.Page[kinds.length];
        for (int i = 0; i < kinds.length; i++) pages[i] = new PageBarModel.Page(i + 1, kinds[i], null);
        in.pages = Arrays.asList(pages);
        in.displayRunning = true;
        return in;
    }

    @Test public void theHoldIsFourHundredMilliseconds() {
        assertEquals(400L, MarkControls.HOLD_MS);
    }

    @Test public void windowsOpenControlsAndPagesNever() {
        PageBarModel.Input in = wall(PageKind.HOME, PageKind.TERMINAL, PageKind.DISPLAY,
            PageKind.ASSISTANT);
        in.windowNames = Arrays.asList(null, "vim");
        in.linuxWindows = new boolean[] {false, true};
        assertTrue(MarkControls.opens(only(in, PageKind.TERMINAL), false));
        assertTrue(MarkControls.opens(only(in, PageKind.LINUX), false));
        assertTrue(MarkControls.opens(only(in, PageKind.DISPLAY), false));
        assertFalse(MarkControls.opens(only(in, PageKind.HOME), false));
        assertFalse(MarkControls.opens(only(in, PageKind.ASSISTANT), false));
        assertFalse("Overview", MarkControls.opens(null, false));
        assertFalse("a folded stack is many windows", MarkControls.opens(only(in, PageKind.TERMINAL), true));
    }

    @Test public void theTerminalPageWithNoWindowIsNotAWindow() {
        PageBarModel.Input in = wall(PageKind.TERMINAL);
        in.windowNames = Collections.emptyList();
        assertFalse(MarkControls.opens(only(in, PageKind.TERMINAL), false));
    }

    @Test public void moveIsForLinuxWindowsOnlyAndCloseIsLast() {
        assertArrayEquals(new Control[] {Control.MINIMISE, Control.MAXIMISE, Control.CLOSE},
            MarkControls.controlsFor(PageKind.TERMINAL));
        assertArrayEquals(new Control[] {Control.MINIMISE, Control.MAXIMISE, Control.MOVE,
            Control.CLOSE}, MarkControls.controlsFor(PageKind.LINUX));
        assertArrayEquals(new Control[] {Control.MAXIMISE, Control.CLOSE},
            MarkControls.controlsFor(PageKind.DISPLAY));
    }

    @Test public void controlsPackAgainstTheRowsEndWithTheNameBefore() {
        MarkControls.Layout l = new MarkControls.Layout(4, 20f, 0f, 400f, 50f, 30f, 1f);
        assertFalse(l.atStart());
        assertEquals(4 * 20f + 3 * 4f, l.lengthPx(), 0.001f);
        assertEquals(400f - l.lengthPx(), l.startPx(), 0.001f);
        assertEquals("Close's centre is the line's end less half a control", 390f, l.centreAt(3), 0.001f);
        assertEquals(l.startPx() - 4f, l.nameEndPx(), 0.001f);
        assertEquals(0, l.controlAt(l.centreAt(0)));
        assertEquals(3, l.controlAt(399f));
        assertEquals(-1, l.controlAt(100f));
        assertTrue("the name's place is covered", l.covers(l.nameEndPx() - 25f));
        assertFalse(l.covers(100f));
    }

    @Test public void aFingerOverTheirPlaceSendsThemToTheRowsStart() {
        MarkControls.Layout l = new MarkControls.Layout(3, 20f, 10f, 400f, 50f, 380f, 1f);
        assertTrue(l.atStart());
        assertEquals("after the name, from the row's start", 10f + 50f + 4f, l.startPx(), 0.001f);
        assertEquals(-1, l.controlAt(380f));
    }

    @Test public void rightToLeftMirrorsSoCloseStaysAtTheLinesEnd() {
        MarkControls.Layout l = new MarkControls.Layout(3, 20f, 0f, 400f, 0f, 30f, 1f);
        assertEquals(380f, l.leftPx(2, 400f, false), 0.001f);
        assertEquals("Close on the left", 0f, l.leftPx(2, 400f, true), 0.001f);
        assertTrue(l.leftPx(0, 400f, true) > l.leftPx(2, 400f, true));
    }

    @Test public void theHoldOpensOnceAndAMovePastTheSlopCancelsIt() {
        MarkControls.Hold hold = new MarkControls.Hold();
        assertFalse("a page mark arms nothing", hold.arm(false, 10f, 5f));
        assertFalse(hold.fire());
        assertTrue(hold.arm(true, 10f, 5f));
        assertTrue("within the slop", hold.moved(14f, 7f, 8f));
        assertTrue(hold.fire());
        assertTrue(hold.open());
        assertFalse("once", hold.fire());
        hold.end();
        assertTrue(hold.arm(true, 10f, 5f));
        assertFalse("along past the slop", hold.moved(30f, 5f, 8f));
        assertFalse(hold.fire());
        assertTrue(hold.arm(true, 10f, 5f));
        assertFalse("across past the slop", hold.moved(10f, 20f, 8f));
        assertFalse(hold.fire());
    }
}
