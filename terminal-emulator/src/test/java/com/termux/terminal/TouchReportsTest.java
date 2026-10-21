package com.termux.terminal;

/**
 * Touch reports, private mode 5523: while it is set each pointer of a touch goes to the program as
 * {@code CSI < kind ; pointer ; x ; y ; time t}, with the pixel position as SGR-Pixels (1016) has
 * it. The terminal is 10x10 cells of {@link #INITIAL_CELL_WIDTH_PIXELS} x
 * {@link #INITIAL_CELL_HEIGHT_PIXELS}.
 */
public class TouchReportsTest extends TerminalTestCase {

    private static final int AREA_WIDTH = 10 * INITIAL_CELL_WIDTH_PIXELS;
    private static final int AREA_HEIGHT = 10 * INITIAL_CELL_HEIGHT_PIXELS;

    public void testOffByDefault() {
        withTerminalSized(10, 10);
        assertFalse(mTerminal.isTouchReportsMode());
    }

    public void testSetAndResetSayNothing() {
        withTerminalSized(10, 10);
        enterString("\033[?5523h");
        assertTrue(mTerminal.isTouchReportsMode());
        assertEquals("Setting says nothing of its own", "", mOutput.getOutputAndClear());
        enterString("\033[?5523l");
        assertFalse(mTerminal.isTouchReportsMode());
        assertEquals("Resetting says nothing of its own", "", mOutput.getOutputAndClear());
    }

    /** DECRQM: 1 while set, 2 while not, never 0, so a program can probe for support. */
    public void testDecrqmReportsTheMode() {
        withTerminalSized(10, 10);
        assertEnteringStringGivesResponse("\033[?5523$p", "\033[?5523;2$y");
        enterString("\033[?5523h");
        assertEnteringStringGivesResponse("\033[?5523$p", "\033[?5523;1$y");
        enterString("\033[?5523l");
        assertEnteringStringGivesResponse("\033[?5523$p", "\033[?5523;2$y");
    }

    public void testRisResetsTheMode() {
        withTerminalSized(10, 10);
        enterString("\033[?5523h");
        enterString("\033c");
        assertFalse(mTerminal.isTouchReportsMode());
    }

    /** XTSAVE / XTRESTORE carry the mode like the other private modes. */
    public void testSaveAndRestore() {
        withTerminalSized(10, 10);
        enterString("\033[?5523h\033[?5523s\033[?5523l");
        assertFalse(mTerminal.isTouchReportsMode());
        enterString("\033[?5523r");
        assertTrue(mTerminal.isTouchReportsMode());
    }

    /** The mouse modes neither imply touch reports nor are touched by them. */
    public void testIndependentOfMouseModes() {
        withTerminalSized(10, 10);
        enterString("\033[?1002h\033[?1006h\033[?1016h");
        assertFalse(mTerminal.isTouchReportsMode());
        enterString("\033[?5523h");
        assertTrue(mTerminal.isMouseTrackingActive());
        assertTrue(mTerminal.isMousePixelPositionActive());
        enterString("\033[?5523l");
        assertTrue(mTerminal.isMouseTrackingActive());
    }

    public void testEachKindIsReported() {
        withTerminalSized(10, 10);
        enterString("\033[?5523h");
        mTerminal.sendTouchEvent(TerminalEmulator.TOUCH_DOWN, 0, 30, 50, 1000);
        assertEquals("\033[<0;0;31;51;1000t", mOutput.getOutputAndClear());
        mTerminal.sendTouchEvent(TerminalEmulator.TOUCH_MOVE, 0, 31, 52, 1016);
        assertEquals("\033[<1;0;32;53;1016t", mOutput.getOutputAndClear());
        mTerminal.sendTouchEvent(TerminalEmulator.TOUCH_DOWN, 1, 0, 0, 1020);
        assertEquals("\033[<0;1;1;1;1020t", mOutput.getOutputAndClear());
        mTerminal.sendTouchEvent(TerminalEmulator.TOUCH_UP, 1, 5, 6, 1030);
        assertEquals("\033[<2;1;6;7;1030t", mOutput.getOutputAndClear());
        mTerminal.sendTouchEvent(TerminalEmulator.TOUCH_CANCEL, 0, 31, 52, 1040);
        assertEquals("\033[<3;0;32;53;1040t", mOutput.getOutputAndClear());
    }

    public void testPixelsAreClampedToTheTextArea() {
        withTerminalSized(10, 10);
        enterString("\033[?5523h");
        mTerminal.sendTouchEvent(TerminalEmulator.TOUCH_MOVE, 2, -5, 9999, 7);
        assertEquals("\033[<1;2;1;" + AREA_HEIGHT + ";7t", mOutput.getOutputAndClear());
        mTerminal.sendTouchEvent(TerminalEmulator.TOUCH_MOVE, 2, AREA_WIDTH, AREA_HEIGHT, 8);
        assertEquals("\033[<1;2;" + AREA_WIDTH + ";" + AREA_HEIGHT + ";8t", mOutput.getOutputAndClear());
    }

    /** Uptime past 2^31 ms (about 24.8 days) wraps instead of going out negative or 64-bit. */
    public void testTimeIsMaskedTo31Bits() {
        withTerminalSized(10, 10);
        enterString("\033[?5523h");
        mTerminal.sendTouchEvent(TerminalEmulator.TOUCH_DOWN, 0, 0, 0, (1L << 31) + 5);
        assertEquals("\033[<0;0;1;1;5t", mOutput.getOutputAndClear());
        mTerminal.sendTouchEvent(TerminalEmulator.TOUCH_UP, 0, 0, 0, 0x7fffffffL);
        assertEquals("\033[<2;0;1;1;2147483647t", mOutput.getOutputAndClear());
    }

    public void testNothingIsSentWhileTheModeIsOff() {
        withTerminalSized(10, 10);
        mTerminal.sendTouchEvent(TerminalEmulator.TOUCH_DOWN, 0, 30, 50, 1000);
        assertEquals("", mOutput.getOutputAndClear());
        enterString("\033[?5523h\033[?5523l");
        mTerminal.sendTouchEvent(TerminalEmulator.TOUCH_DOWN, 0, 30, 50, 1000);
        assertEquals("", mOutput.getOutputAndClear());
    }
}
