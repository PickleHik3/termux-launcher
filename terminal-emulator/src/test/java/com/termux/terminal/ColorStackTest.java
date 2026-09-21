package com.termux.terminal;

/**
 * The xterm color stack: {@code CSI # P} saves the palette, {@code CSI # Q} restores it and
 * {@code CSI # R} reports where the stack stands. A program borrows the palette with these and hands
 * it back, and the terminal hands it back for a program that is killed before it can.
 */
public class ColorStackTest extends TerminalTestCase {

    private static final int RED = 0xFFFF0000;
    private static final int BLUE = 0xFF0000FF;

    private int color(int index) {
        return mTerminal.mColors.mCurrentColors[index];
    }

    private void setColor(int index, String spec) {
        enterString("\033]4;" + index + ";" + spec + "\007");
    }

    public void testPopRestoresTheSavedPalette() {
        withTerminalSized(5, 3);
        setColor(1, "#FF0000");
        assertEquals(RED, color(1));

        enterString("\033[#P");
        setColor(1, "#0000FF");
        assertEquals(BLUE, color(1));

        enterString("\033[#Q");
        assertEquals("Popping restores the color that was pushed", RED, color(1));
    }

    /** The whole palette is saved: the 256 indexed colors and the foreground, background and cursor. */
    public void testTheSpecialColorsAreSavedToo() {
        withTerminalSized(5, 3);
        enterString("\033[#P");
        enterString("\033]10;#FF0000\007\033]11;#FF0000\007\033]12;#FF0000\007");
        setColor(200, "#FF0000");
        enterString("\033[#Q");
        assertFalse(RED == color(TextStyle.COLOR_INDEX_FOREGROUND));
        assertFalse(RED == color(TextStyle.COLOR_INDEX_BACKGROUND));
        assertFalse(RED == color(TextStyle.COLOR_INDEX_CURSOR));
        assertFalse(RED == color(200));
    }

    /** A pop that changed something repaints; one that changed nothing does not. */
    public void testOnlyAChangedPaletteTellsTheClient() {
        withTerminalSized(5, 3);
        enterString("\033[#P");
        int before = mOutput.colorsChanged;
        enterString("\033[#Q");
        assertEquals("An unchanged palette is not a color change", before, mOutput.colorsChanged);

        enterString("\033[#P");
        setColor(1, "#0000FF");
        before = mOutput.colorsChanged;
        enterString("\033[#Q");
        assertEquals(before + 1, mOutput.colorsChanged);
    }

    /** Popping an empty stack leaves the palette alone rather than clearing it. */
    public void testPopWithNothingPushedIsIgnored() {
        withTerminalSized(5, 3);
        setColor(1, "#FF0000");
        enterString("\033[#Q");
        assertEquals(RED, color(1));
    }

    /** Pushes nest, and each pop takes the palette back one step. */
    public void testPushesNest() {
        withTerminalSized(5, 3);
        setColor(1, "#FF0000");
        enterString("\033[#P");
        setColor(1, "#0000FF");
        enterString("\033[#P");
        setColor(1, "#00FF00");

        enterString("\033[#Q");
        assertEquals(BLUE, color(1));
        enterString("\033[#Q");
        assertEquals(RED, color(1));
    }

    /** XTREPORTCOLORS answers with the current entry and how many are stored. */
    public void testReportColors() {
        withTerminalSized(5, 3);
        assertEnteringStringGivesResponse("\033[#R", "\033[?0;0#Q");
        enterString("\033[#P");
        assertEnteringStringGivesResponse("\033[#R", "\033[?1;1#Q");
        enterString("\033[#P");
        assertEnteringStringGivesResponse("\033[#R", "\033[?2;2#Q");
        enterString("\033[#Q");
        assertEnteringStringGivesResponse("\033[#R", "\033[?1;1#Q");
    }

    /** An explicit slot is written and read by number. */
    public void testAnExplicitSlot() {
        withTerminalSized(5, 3);
        setColor(1, "#FF0000");
        enterString("\033[3#P");
        assertEnteringStringGivesResponse("\033[#R", "\033[?3;3#Q");
        setColor(1, "#0000FF");
        enterString("\033[3#Q");
        assertEquals(RED, color(1));
    }

    /** The stack is bounded: a push past the last slot overwrites it instead of growing. */
    public void testDepthIsBounded() {
        withTerminalSized(5, 3);
        for (int i = 0; i < TerminalColors.MAX_COLOR_STACK_DEPTH + 5; i++) {
            setColor(1, "#FF0000");
            enterString("\033[#P");
        }
        assertEnteringStringGivesResponse("\033[#R",
            "\033[?" + TerminalColors.MAX_COLOR_STACK_DEPTH + ";" + TerminalColors.MAX_COLOR_STACK_DEPTH + "#Q");

        setColor(1, "#0000FF");
        enterString("\033[#Q");
        assertEquals("The overwritten top is what comes back", RED, color(1));
    }

    /** A terminal reset drops the saved palettes with everything else. */
    public void testResetEmptiesTheStack() {
        withTerminalSized(5, 3);
        enterString("\033[#P");
        mTerminal.reset();
        assertEnteringStringGivesResponse("\033[#R", "\033[?0;0#Q");
    }
}
