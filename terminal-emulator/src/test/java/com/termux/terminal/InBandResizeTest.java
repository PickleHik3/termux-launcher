package com.termux.terminal;

/**
 * In-band resize notifications, private mode 2048: while set, every resize tells the program its new
 * size as {@code CSI 48 ; rows ; columns ; height ; width t}, so it need not race SIGWINCH with a
 * size query.
 */
public class InBandResizeTest extends TerminalTestCase {

    private String sizeReport(int rows, int columns) {
        return "\033[48;" + rows + ";" + columns + ";"
            + (rows * INITIAL_CELL_HEIGHT_PIXELS) + ";" + (columns * INITIAL_CELL_WIDTH_PIXELS) + "t";
    }

    /** Setting the mode answers at once, so a program that just asked knows its size. */
    public void testSettingTheModeReportsTheCurrentSize() {
        withTerminalSized(10, 4);
        assertEnteringStringGivesResponse("\033[?2048h", sizeReport(4, 10));
    }

    public void testEveryResizeIsReportedWhileSet() {
        withTerminalSized(10, 4);
        enterString("\033[?2048h");
        mOutput.getOutputAndClear();

        resize(20, 6);
        assertEquals(sizeReport(6, 20), mOutput.getOutputAndClear());
        resize(8, 3);
        assertEquals(sizeReport(3, 8), mOutput.getOutputAndClear());
    }

    /** A font size change moves the pixel size without moving the grid, and still counts. */
    public void testACellSizeChangeIsReported() {
        withTerminalSized(10, 4);
        enterString("\033[?2048h");
        mOutput.getOutputAndClear();

        mTerminal.resize(10, 4, INITIAL_CELL_WIDTH_PIXELS * 2, INITIAL_CELL_HEIGHT_PIXELS * 2);
        assertEquals("\033[48;4;10;" + (4 * INITIAL_CELL_HEIGHT_PIXELS * 2) + ";"
            + (10 * INITIAL_CELL_WIDTH_PIXELS * 2) + "t", mOutput.getOutputAndClear());
    }

    /** A resize to the same size says nothing: there is nothing to tell. */
    public void testAnUnchangedSizeIsNotReported() {
        withTerminalSized(10, 4);
        enterString("\033[?2048h");
        mOutput.getOutputAndClear();

        resize(10, 4);
        assertEquals("", mOutput.getOutputAndClear());
    }

    public void testNothingIsReportedWhileUnset() {
        withTerminalSized(10, 4);
        resize(20, 6);
        assertEquals("", mOutput.getOutputAndClear());
    }

    public void testResettingTheModeStopsTheReports() {
        withTerminalSized(10, 4);
        enterString("\033[?2048h");
        mOutput.getOutputAndClear();
        enterString("\033[?2048l");
        assertEquals("Resetting says nothing of its own", "", mOutput.getOutputAndClear());

        resize(20, 6);
        assertEquals("", mOutput.getOutputAndClear());
    }

    /** DECRQM: 1 while set, 2 while not. */
    public void testDecrqmReportsTheMode() {
        withTerminalSized(10, 4);
        assertEnteringStringGivesResponse("\033[?2048$p", "\033[?2048;2$y");
        enterString("\033[?2048h");
        mOutput.getOutputAndClear();
        assertEnteringStringGivesResponse("\033[?2048$p", "\033[?2048;1$y");
        enterString("\033[?2048l");
        assertEnteringStringGivesResponse("\033[?2048$p", "\033[?2048;2$y");
    }

    /** The pixel figures are the ones XTWINOPS 14 reports for the same screen. */
    public void testThePixelSizeMatchesXtwinops() {
        withTerminalSized(10, 4);
        enterString("\033[?2048h");
        String inBand = mOutput.getOutputAndClear();
        enterString("\033[14t");
        String winops = mOutput.getOutputAndClear();

        String inBandPixels = inBand.substring(inBand.indexOf(";", inBand.indexOf(";", 6) + 1) + 1, inBand.length() - 1);
        assertEquals("\033[4;" + inBandPixels + "t", winops);
    }
}
