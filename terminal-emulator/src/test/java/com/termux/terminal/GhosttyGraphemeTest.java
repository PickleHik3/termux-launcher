package com.termux.terminal;

import java.nio.charset.StandardCharsets;

/**
 * Emoji and combining clusters on the Ghostty core against the Java core fed the same output:
 * row text, cell widths and the cursor column, on screen and read back from history. Runs only with
 * the host library ({@code -Pghostty=true}); elsewhere every test returns at once.
 */
public class GhosttyGraphemeTest extends TerminalTestCase {

    private static final int COLUMNS = 40;
    private static final int ROWS = 4;

    /** The lines of the emoji script, one per entry. */
    private static final String[] LINES = {
        "ZWJ family: \uD83D\uDC68\u200D\uD83D\uDC69\u200D\uD83D\uDC67\u200D\uD83D\uDC66 | women: \uD83D\uDC69\u200D\uD83D\uDCBB \uD83E\uDDD1\u200D\uD83D\uDE80",
        "skin tones: \uD83D\uDC4D\uD83C\uDFFD \uD83D\uDC4B\uD83C\uDFFF \uD83D\uDE4C\uD83C\uDFFB",
        "flags: \uD83C\uDDEF\uD83C\uDDF5 \uD83C\uDDF0\uD83C\uDDFC \uD83C\uDDFA\uD83C\uDDF3",
        "text: \u2764 \u2708 \u2600",
        "combining: e\u0301 n\u0303 a\u030A A\u030A \u1EB9\u0304 | CJK: \u65E5\u672C\u8A9E",
    };

    /**
     * Emoji-presentation clusters whose first code point is narrow: the rainbow flag and VS16
     * pairs. Ghostty gives each two cells, as the Ghostty app does; the Java core stores them in
     * one, so these lines match it in text only and the cursor sits one column per cluster later.
     */
    private static final String[] WIDER_ON_GHOSTTY = {
        "flag: \uD83C\uDFF3\uFE0F\u200D\uD83C\uDF08",
        "VS16: \u2764\uFE0F \u2708\uFE0F \u2600\uFE0F",
    };
    private static final int[] EXTRA_COLUMNS = {1, 3};

    private static TerminalEmulator emulator(boolean ghostty) {
        TerminalEmulator emulator = new TerminalEmulator(new MockTerminalOutput(), false, COLUMNS, ROWS,
            INITIAL_CELL_WIDTH_PIXELS, INITIAL_CELL_HEIGHT_PIXELS, 100, null);
        if (ghostty) assertTrue("library failed to load: " + GhosttyNative.loadError(), emulator.useGhosttyCore());
        else useRealGraphemeBreaks(emulator);
        return emulator;
    }

    /**
     * The JVM's android.jar stubs have no ICU, so the Java core would not cluster here; give it
     * the real ICU4J rules it has on a device.
     */
    private static void useRealGraphemeBreaks(TerminalEmulator emulator) {
        try {
            java.lang.reflect.Field field = TerminalEmulator.class.getDeclaredField("mGraphemeClusterer");
            field.setAccessible(true);
            field.set(emulator, new GraphemeClusterer(new Icu4jBreakIterator()));
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }

    private static void feed(TerminalEmulator emulator, String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        emulator.append(bytes, bytes.length);
    }

    private static String rowText(TerminalContent content, int row) {
        return content.text(0, row, content.columns(), row, false, false);
    }

    private static void assertSameRow(String what, TerminalContent java, TerminalContent ghostty, int y) {
        assertEquals(what + " text of row " + y, rowText(java, y), rowText(ghostty, y));
        TerminalRow j = java.row(y), g = ghostty.row(y);
        for (int x = 0; x < java.columns(); x++)
            assertEquals(what + " cell start at row " + y + " column " + x + " (" + rowText(java, y) + ")",
                j.findStartOfColumn(x), g.findStartOfColumn(x));
    }

    private static boolean hostLibrary() {
        return System.getProperty(GhosttyNative.TEST_LIBRARY_DIR_PROPERTY) != null;
    }

    public void testEachLineMatchesTheJavaCoreWithTheCursorColumn() {
        if (!hostLibrary()) return;
        for (String line : LINES) {
            TerminalEmulator java = emulator(false), ghostty = emulator(true);
            feed(java, line);
            feed(ghostty, line);
            assertEquals("cursor column of " + line, java.getCursorCol(), ghostty.getCursorCol());
            assertEquals("cursor row of " + line, java.getCursorRow(), ghostty.getCursorRow());
            assertSameRow("screen", java.getContent(), ghostty.getContent(), 0);
        }
    }

    public void testRowsReadBackFromHistoryMatchTheJavaCore() {
        if (!hostLibrary()) return;
        StringBuilder all = new StringBuilder();
        for (String line : LINES) all.append(line).append("\r\n");
        for (int i = 0; i < 2 * ROWS; i++) all.append("filler\r\n");
        TerminalEmulator java = emulator(false), ghostty = emulator(true);
        feed(java, all.toString());
        feed(ghostty, all.toString());
        TerminalContent jc = java.getContent(), gc = ghostty.getContent();
        assertEquals(jc.historyRows(), gc.historyRows());
        assertTrue(gc.historyRows() >= LINES.length);
        for (int y = -jc.historyRows(); y < jc.screenRows(); y++) assertSameRow("history", jc, gc, y);
        assertEquals(java.getCursorCol(), ghostty.getCursorCol());
    }

    public void testEmojiPresentationClustersTakeTwoCellsOnGhostty() {
        if (!hostLibrary()) return;
        for (int i = 0; i < WIDER_ON_GHOSTTY.length; i++) {
            String line = WIDER_ON_GHOSTTY[i];
            String scrolled = line + "\r\nx\r\nx\r\nx\r\nx\r\nx\r\n";
            TerminalEmulator java = emulator(false), ghostty = emulator(true);
            feed(java, scrolled);
            feed(ghostty, scrolled);
            TerminalContent jc = java.getContent(), gc = ghostty.getContent();
            assertEquals(jc.historyRows(), gc.historyRows());
            // The line itself is read back from history; its text matches the Java core.
            for (int y = -jc.historyRows(); y < jc.screenRows(); y++)
                assertEquals("text of row " + y, rowText(jc, y), rowText(gc, y));
            TerminalEmulator j2 = emulator(false), g2 = emulator(true);
            feed(j2, line);
            feed(g2, line);
            assertEquals("cursor column of " + line, j2.getCursorCol() + EXTRA_COLUMNS[i], g2.getCursorCol());
        }
    }

    public void testAProgramCanTurnClusteringOff() {
        if (!hostLibrary()) return;
        TerminalEmulator ghostty = emulator(true);
        feed(ghostty, "\033[?2027l👍🏽");
        // Without clustering the tone modifier takes its own cell: hand and modifier, two wide each.
        assertEquals(4, ghostty.getCursorCol());
    }
}
