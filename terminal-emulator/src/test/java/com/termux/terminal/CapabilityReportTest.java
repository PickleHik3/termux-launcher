package com.termux.terminal;

/**
 * Capability advertisement replies that image-capable TUI clients use for feature detection:
 * primary device attributes (DA1), XTVERSION and XTSMGRAPHICS.
 */
public class CapabilityReportTest extends TerminalTestCase {

    /** DA1 must include parameter 4, which advertises sixel support. */
    public void testPrimaryDeviceAttributesAdvertisesSixel() {
        withTerminalSized(80, 24);
        enterString("\033[c");
        String response = mOutput.getOutputAndClear();
        assertEquals("\033[?64;1;2;4;6;9;15;18;21;22c", response);
    }

    /** "CSI > 0 q" (XTVERSION) reports the terminal name, terminated like xterm/kitty/foot. */
    public void testXtVersionReportsTerminalName() {
        withTerminalSized(80, 24);
        enterString("\033[>0q");
        String response = mOutput.getOutputAndClear();
        assertTrue("Unexpected XTVERSION response: " + response,
            response.startsWith("\033P>|termux-launcher") && response.endsWith("\033\\"));
    }

    /** "CSI > q" with a missing argument defaults to 0 and still reports. */
    public void testXtVersionWithoutExplicitArgument() {
        withTerminalSized(80, 24);
        enterString("\033[>q");
        assertTrue(mOutput.getOutputAndClear().startsWith("\033P>|termux-launcher"));
    }

    /** XTSMGRAPHICS color register read and read-maximum both report the fixed 256 registers. */
    public void testGraphicsAttributesColorRegisters() {
        withTerminalSized(80, 24);
        enterString("\033[?1;1S");
        assertEquals("\033[?1;0;256S", mOutput.getOutputAndClear());
        enterString("\033[?1;4S");
        assertEquals("\033[?1;0;256S", mOutput.getOutputAndClear());
    }

    /** XTSMGRAPHICS sixel geometry read reports the screen size in pixels. */
    public void testGraphicsAttributesSixelGeometry() {
        withTerminalSized(80, 24);
        int width = 80 * INITIAL_CELL_WIDTH_PIXELS;
        int height = 24 * INITIAL_CELL_HEIGHT_PIXELS;
        enterString("\033[?2;1S");
        assertEquals("\033[?2;0;" + width + ";" + height + "S", mOutput.getOutputAndClear());
        enterString("\033[?2;4S");
        assertEquals("\033[?2;0;" + width + ";" + height + "S", mOutput.getOutputAndClear());
    }

    /** Setting the sixel geometry is refused with a bad-action status. */
    public void testGraphicsAttributesSixelGeometrySetRefused() {
        withTerminalSized(80, 24);
        enterString("\033[?2;3;100;100S");
        assertEquals("\033[?2;2;0S", mOutput.getOutputAndClear());
    }

    /** An unknown item is answered with a bad-item status instead of being swallowed. */
    public void testGraphicsAttributesUnknownItem() {
        withTerminalSized(80, 24);
        enterString("\033[?3;1S");
        assertEquals("\033[?3;1;0S", mOutput.getOutputAndClear());
    }
}
