package com.termux.terminal;

/**
 * The alternate screen resizes in place, as kitty does (ADR 0019, spec finding 1): rows stay
 * top-anchored, cut or padded at the bottom and the right, never rotated or reflowed. Before,
 * a shrink wrapped the ring at the new total and a full-screen program (kew) showed its frame's
 * lower half above its upper half until it repainted.
 */
public class AltScreenResizeTest extends TerminalTestCase {

    private void fillAlt() {
        withTerminalSized(5, 5);
        enterString("\033[?1049h");
        enterString("AAAAA\r\nBBBBB\r\nCCCCC\r\nDDDDD\r\nEEEE");
    }

    public void testShrinkingKeepsRowOrderAndCutsTheBottom() {
        fillAlt();
        resize(5, 3);
        assertLinesAre("AAAAA", "BBBBB", "CCCCC");
        // The cursor was on the last row: it is clamped into the screen, not moved with a ring.
        assertEquals(2, mTerminal.getCursorRow());
    }

    public void testGrowingPadsBelowAndKeepsRowsInPlace() {
        fillAlt();
        resize(5, 7);
        assertLinesAre("AAAAA", "BBBBB", "CCCCC", "DDDDD", "EEEE ", "     ", "     ");
    }

    public void testNarrowingCutsColumnsWithoutWrapping() {
        fillAlt();
        resize(3, 5);
        assertLinesAre("AAA", "BBB", "CCC", "DDD", "EEE");
        resize(6, 5);
        assertLinesAre("AAA   ", "BBB   ", "CCC   ", "DDD   ", "EEE   ");
    }

    public void testShrinkThenGrowNeverRotates() {
        fillAlt();
        resize(5, 2);
        assertLinesAre("AAAAA", "BBBBB");
        resize(4, 4);
        assertLinesAre("AAAA", "BBBB", "    ", "    ");
    }

    public void testTheMainScreenStillReflows() {
        withTerminalSized(5, 3);
        enterString("ABCDE");
        resize(3, 3);
        // The main screen keeps its rewrap: the cut text wraps onto the next row.
        assertLineIs(0, "ABC");
        assertLineIs(1, "DE ");
    }

    public void testTheAltScreenPredictsNoShift() {
        fillAlt();
        assertEquals(0, mTerminal.predictRowsOnlyResizeShift(3));
    }
}
