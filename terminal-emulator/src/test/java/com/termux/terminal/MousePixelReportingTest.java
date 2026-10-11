package com.termux.terminal;

/**
 * SGR-Pixels mouse reporting (DECSET 1016) and any-event tracking (DECSET 1003). The terminal is
 * 10x10 cells of {@link #INITIAL_CELL_WIDTH_PIXELS} x {@link #INITIAL_CELL_HEIGHT_PIXELS}, so its
 * text area is 130 x 150 pixels.
 */
public class MousePixelReportingTest extends TerminalTestCase {

	private static final int AREA_WIDTH = 10 * INITIAL_CELL_WIDTH_PIXELS;
	private static final int AREA_HEIGHT = 10 * INITIAL_CELL_HEIGHT_PIXELS;

	public void testSgrPixelsModeIsSetResetAndQueried() {
		withTerminalSized(10, 10);
		assertFalse(mTerminal.isMousePixelPositionActive());
		assertEnteringStringGivesResponse("\033[?1016$p", "\033[?1016;2$y");
		enterString("\033[?1016h");
		assertTrue(mTerminal.isMousePixelPositionActive());
		assertEnteringStringGivesResponse("\033[?1016$p", "\033[?1016;1$y");
		enterString("\033[?1016l");
		assertFalse(mTerminal.isMousePixelPositionActive());
		assertEnteringStringGivesResponse("\033[?1016$p", "\033[?1016;2$y");
	}

	public void testPressAndReleaseReportPixelsOneBased() {
		withTerminalSized(10, 10);
		enterString("\033[?1003h\033[?1006h\033[?1016h");
		mTerminal.sendMouseEvent(TerminalEmulator.MOUSE_LEFT_BUTTON, 3, 4, 30, 50, true, 0);
		assertEquals("\033[<0;31;51M", mOutput.getOutputAndClear());
		mTerminal.sendMouseEvent(TerminalEmulator.MOUSE_LEFT_BUTTON, 3, 4, 30, 50, false, 0);
		assertEquals("\033[<0;31;51m", mOutput.getOutputAndClear());
		// The top-left pixel of the text area is 1;1, as xterm sends it.
		mTerminal.sendMouseEvent(TerminalEmulator.MOUSE_LEFT_BUTTON, 1, 1, 0, 0, true, 0);
		assertEquals("\033[<0;1;1M", mOutput.getOutputAndClear());
	}

	public void testPixelsAreClampedToTheTextArea() {
		withTerminalSized(10, 10);
		enterString("\033[?1000h\033[?1006h\033[?1016h");
		mTerminal.sendMouseEvent(TerminalEmulator.MOUSE_LEFT_BUTTON, 0, 11, -5, 9999, true, 0);
		assertEquals("\033[<0;1;" + AREA_HEIGHT + "M", mOutput.getOutputAndClear());
		mTerminal.sendMouseEvent(TerminalEmulator.MOUSE_LEFT_BUTTON, 10, 10, AREA_WIDTH, AREA_HEIGHT, false, 0);
		assertEquals("\033[<0;" + AREA_WIDTH + ";" + AREA_HEIGHT + "m", mOutput.getOutputAndClear());
	}

	public void testDragAndWheelReportPixelsWithModifiers() {
		withTerminalSized(10, 10);
		enterString("\033[?1002h\033[?1006h\033[?1016h");
		mTerminal.sendMouseEvent(TerminalEmulator.MOUSE_LEFT_BUTTON_MOVED, 3, 4, 31, 52, true,
			TerminalEmulator.MOUSE_MODIFIER_SHIFT | TerminalEmulator.MOUSE_MODIFIER_ALT);
		assertEquals("\033[<44;32;53M", mOutput.getOutputAndClear());
		mTerminal.sendMouseEvent(TerminalEmulator.MOUSE_WHEELUP_BUTTON, 2, 2, 20, 20, true, 0);
		assertEquals("\033[<64;21;21M", mOutput.getOutputAndClear());
		mTerminal.sendMouseEvent(TerminalEmulator.MOUSE_WHEELDOWN_BUTTON, 2, 2, 20, 20, true,
			TerminalEmulator.MOUSE_MODIFIER_CTRL);
		assertEquals("\033[<81;21;21M", mOutput.getOutputAndClear());
	}

	public void testSgrPixelsWithoutSgrStillUsesTheSgrFormat() {
		withTerminalSized(10, 10);
		enterString("\033[?1000h\033[?1016h");
		mTerminal.sendMouseEvent(TerminalEmulator.MOUSE_LEFT_BUTTON, 3, 4, 30, 50, true, 0);
		assertEquals("\033[<0;31;51M", mOutput.getOutputAndClear());
	}

	public void testACellOnlyReportUnderSgrPixelsGivesTheCellsTopLeftPixel() {
		withTerminalSized(10, 10);
		enterString("\033[?1000h\033[?1006h\033[?1016h");
		mTerminal.sendMouseEvent(TerminalEmulator.MOUSE_LEFT_BUTTON, 3, 4, true);
		assertEquals("\033[<0;" + (2 * INITIAL_CELL_WIDTH_PIXELS + 1) + ";"
			+ (3 * INITIAL_CELL_HEIGHT_PIXELS + 1) + "M", mOutput.getOutputAndClear());
	}

	public void testCellEncodingIsUnchangedWithSgrPixelsOff() {
		withTerminalSized(10, 10);
		enterString("\033[?1000h\033[?1006h\033[?1016h\033[?1016l");
		mTerminal.sendMouseEvent(TerminalEmulator.MOUSE_LEFT_BUTTON, 3, 4, 30, 50, true,
			TerminalEmulator.MOUSE_MODIFIER_CTRL);
		assertEquals("\033[<16;3;4M", mOutput.getOutputAndClear());

		enterString("\033[?1006l");
		mTerminal.sendMouseEvent(TerminalEmulator.MOUSE_LEFT_BUTTON, 3, 4, 30, 50, false, 0);
		assertEquals("\033[M" + (char) (32 + 3) + (char) (32 + 3) + (char) (32 + 4), mOutput.getOutputAndClear());
	}

	public void testAnyEventTrackingReportsMotionWithNoButton() {
		withTerminalSized(10, 10);
		enterString("\033[?1003h\033[?1006h");
		assertTrue(mTerminal.isMouseTrackingActive());
		assertTrue(mTerminal.isMouseTrackingMotionActive());
		assertTrue(mTerminal.isMouseTrackingAnyEventActive());
		mTerminal.sendMouseEvent(TerminalEmulator.MOUSE_MOVED_NO_BUTTON, 3, 4, true);
		assertEquals("\033[<35;3;4M", mOutput.getOutputAndClear());
		mTerminal.sendMouseEvent(TerminalEmulator.MOUSE_MOVED_NO_BUTTON, 3, 4, true,
			TerminalEmulator.MOUSE_MODIFIER_SHIFT);
		assertEquals("\033[<39;3;4M", mOutput.getOutputAndClear());
		mTerminal.sendMouseEvent(TerminalEmulator.MOUSE_LEFT_BUTTON_MOVED, 3, 4, true);
		assertEquals("\033[<32;3;4M", mOutput.getOutputAndClear());

		enterString("\033[?1016h");
		mTerminal.sendMouseEvent(TerminalEmulator.MOUSE_MOVED_NO_BUTTON, 3, 4, 30, 50, true, 0);
		assertEquals("\033[<35;31;51M", mOutput.getOutputAndClear());

		enterString("\033[?1016l\033[?1006l");
		mTerminal.sendMouseEvent(TerminalEmulator.MOUSE_MOVED_NO_BUTTON, 3, 4, true);
		assertEquals("\033[M" + (char) (32 + 35) + (char) (32 + 3) + (char) (32 + 4), mOutput.getOutputAndClear());
	}

	public void testButtonEventTrackingDoesNotReportMotionWithNoButton() {
		withTerminalSized(10, 10);
		enterString("\033[?1002h\033[?1006h");
		assertTrue(mTerminal.isMouseTrackingMotionActive());
		assertFalse(mTerminal.isMouseTrackingAnyEventActive());
		mTerminal.sendMouseEvent(TerminalEmulator.MOUSE_MOVED_NO_BUTTON, 3, 4, true);
		assertEquals("", mOutput.getOutputAndClear());
		mTerminal.sendMouseEvent(TerminalEmulator.MOUSE_LEFT_BUTTON_MOVED, 3, 4, true);
		assertEquals("\033[<32;3;4M", mOutput.getOutputAndClear());

		enterString("\033[?1000h");
		assertFalse(mTerminal.isMouseTrackingMotionActive());
		mTerminal.sendMouseEvent(TerminalEmulator.MOUSE_MOVED_NO_BUTTON, 3, 4, true);
		mTerminal.sendMouseEvent(TerminalEmulator.MOUSE_LEFT_BUTTON_MOVED, 3, 4, true);
		assertEquals("", mOutput.getOutputAndClear());
	}

	public void testTrackingModesAreMutuallyExclusive() {
		withTerminalSized(10, 10);
		enterString("\033[?1000h\033[?1002h\033[?1003h");
		assertEnteringStringGivesResponse("\033[?1000$p", "\033[?1000;2$y");
		assertEnteringStringGivesResponse("\033[?1002$p", "\033[?1002;2$y");
		assertEnteringStringGivesResponse("\033[?1003$p", "\033[?1003;1$y");
		enterString("\033[?1002h");
		assertEnteringStringGivesResponse("\033[?1003$p", "\033[?1003;2$y");
		assertEnteringStringGivesResponse("\033[?1002$p", "\033[?1002;1$y");
		enterString("\033[?1003h\033[?1003l");
		assertFalse(mTerminal.isMouseTrackingActive());
	}
}
