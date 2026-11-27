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

    @Test public void moveAndScaleAreForLinuxWindowsOnlyAndCloseIsLast() {
        assertArrayEquals(new Control[] {Control.MINIMISE, Control.MAXIMISE, Control.CLOSE},
            MarkControls.controlsFor(PageKind.TERMINAL));
        assertArrayEquals(new Control[] {Control.MINIMISE, Control.MAXIMISE, Control.MOVE,
            Control.SCALE, Control.CLOSE}, MarkControls.controlsFor(PageKind.LINUX));
        assertArrayEquals(new Control[] {Control.MAXIMISE, Control.CLOSE},
            MarkControls.controlsFor(PageKind.DISPLAY));
    }

    @Test public void aLinuxWindowThatTakesNoScaleOfItsOwnHasNoScale() {
        assertArrayEquals(new Control[] {Control.MINIMISE, Control.MAXIMISE, Control.MOVE,
            Control.CLOSE}, MarkControls.controlsFor(PageKind.LINUX, false));
        assertArrayEquals("the flag changes only a Linux window",
            MarkControls.controlsFor(PageKind.TERMINAL),
            MarkControls.controlsFor(PageKind.TERMINAL, false));
    }

    @Test public void fiveControlsAndTheNameFitA360dpPhoneBySpacingThenEllipsis() {
        float density = 3f;
        float size = 28f * density;
        // 360dp less the 16dp gutters.
        float line = (360f - 32f) * density;
        float roomy = MarkControls.nameRoomPx(5, size, line, 60f * density, density);
        assertEquals("a short name keeps its width", 60f * density, roomy, 0.001f);
        MarkControls.Layout l = new MarkControls.Layout(5, size, 0f, line, roomy, 0f, density);
        assertEquals("the ordinary spacing holds it", MarkControls.GAP_DP * density, l.gapPx(), 0.001f);
        assertTrue(l.nameEndPx() - roomy >= 0f);
        // A long name: the spacing closes up before the name is cut.
        float longName = line - 5f * size - 4f * MarkControls.GAP_DP * density - 1f;
        MarkControls.Layout tight = new MarkControls.Layout(5, size, 0f, line, longName, 0f, density);
        assertTrue("the gap gave", tight.gapPx() < MarkControls.GAP_DP * density);
        assertTrue(tight.gapPx() >= MarkControls.MIN_GAP_DP * density);
        assertTrue("name and controls stay inside the line",
            tight.startPx() + tight.lengthPx() <= line + 0.01f && tight.nameEndPx() - longName >= -0.01f);
        // Wider than the line holds even at the least gap: the caller ellipsizes to this room.
        float room = MarkControls.nameRoomPx(5, size, line, line, density);
        assertTrue(room < line);
        MarkControls.Layout cut = new MarkControls.Layout(5, size, 0f, line, room, 0f, density);
        assertEquals("the least gap", MarkControls.MIN_GAP_DP * density, cut.gapPx(), 0.001f);
        assertEquals("the name starts at the line's start", 0f, cut.nameEndPx() - room, 0.01f);
        assertEquals("Close ends at the line's end", line, cut.startPx() + cut.lengthPx(), 0.01f);
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
