package com.termux.terminal;

import java.nio.charset.StandardCharsets;

/**
 * A Ghostty error mid-stream hands the pane to the Java core by replay: the Java screen afterwards
 * shows what the Ghostty screen showed, text, colours and cursor, with recent history above it.
 * Runs only with {@code -Pghostty=true} (the host library); elsewhere every test returns at once.
 */
public class GhosttyReplayFallbackTest extends TerminalTestCase {

    private static final int COLUMNS = 20, ROWS = 6, TRANSCRIPT = 100;

    /** Coloured, wide, emoji, wrapped and plain lines, enough to push rows into history. */
    private static final String FIRST = "plain one\r\n"
        + "\033[1;31mbold red\033[0m and \033[38;2;10;200;30mrgb\033[0m\r\n"
        + "\033[44mblue bg\033[0m \033[4munder\033[24m\r\n"
        + "wide 漢字テスト end\r\n"
        + "emoji 👍 ok 🎉 x\r\n"
        + "a line long enough to wrap onto the next row\r\n";
    private static final String SECOND = "\033[7mreverse\033[27m \033[3mitalic\033[23m\r\n"
        + "\033[38;5;208m256 orange\033[39m \033[48;5;22mgreen\033[49m\r\n"
        + "後で 🎉 after\r\n"
        + "last \033[1mbold\033[22m";

    private boolean hostLibrary() {
        return System.getProperty(GhosttyNative.TEST_LIBRARY_DIR_PROPERTY) != null;
    }

    private TerminalEmulator ghosttyTerminal() {
        TerminalEmulator terminal = new TerminalEmulator(mOutput, false, COLUMNS, ROWS, INITIAL_CELL_WIDTH_PIXELS,
            INITIAL_CELL_HEIGHT_PIXELS, TRANSCRIPT, null);
        assertTrue("library failed to load: " + GhosttyNative.loadError(), terminal.useGhosttyCore());
        return terminal;
    }

    private static void append(TerminalEmulator terminal, String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        terminal.append(bytes, bytes.length);
    }

    /** Feed FIRST, then fail the sync that would have shown SECOND. */
    private TerminalEmulator failedMidStream(boolean formatterFailsToo) {
        TerminalEmulator terminal = ghosttyTerminal();
        append(terminal, FIRST);
        byte[] bytes = SECOND.getBytes(StandardCharsets.UTF_8);
        terminal.appendBatched(bytes, bytes.length);
        terminal.failNextGhosttySyncForTests(new IllegalStateException("injected"), formatterFailsToo);
        terminal.flushBatchedOutput();
        return terminal;
    }

    public void testReplayShowsWhatTheGhosttyScreenShowed() {
        if (!hostLibrary()) return;
        TerminalEmulator reference = ghosttyTerminal();
        append(reference, FIRST + SECOND);

        TerminalEmulator replayed = failedMidStream(false);
        assertFalse("the pane is on the Java core", replayed.isGhosttyCoreActive());

        TerminalContent want = reference.getContent();
        TerminalContent got = replayed.getContent();
        assertTrue("history was replayed", got.historyRows() > 0);
        int history = Math.min(want.historyRows(), got.historyRows());
        for (int y = -history; y < ROWS; y++) assertRowsEqual(y, want.row(y), got.row(y));
        assertEquals(reference.getCursorRow(), replayed.getCursorRow());
        assertEquals(reference.getCursorCol(), replayed.getCursorCol());

        // The Java core carries on with later output where Ghostty left off.
        append(replayed, " more");
        append(reference, " more");
        assertRowsEqual(ROWS - 1, reference.getContent().row(ROWS - 1), replayed.getContent().row(ROWS - 1));
        reference.releaseGhosttyCore();
    }

    public void testModesSurviveTheReplay() {
        if (!hostLibrary()) return;
        TerminalEmulator terminal = ghosttyTerminal();
        append(terminal, "\033[?1h\033[?2004h\033[?1006h\033[?1002h");
        terminal.failNextGhosttySyncForTests(new IllegalStateException("injected"), false);
        append(terminal, "x");
        assertFalse(terminal.isGhosttyCoreActive());
        assertTrue(terminal.isCursorKeysApplicationMode());
        assertTrue(terminal.isBracketedPasteMode());
        assertTrue(terminal.isMouseTrackingActive());
    }

    public void testAlternateScreenComesBackOnTheAlternateScreen() {
        if (!hostLibrary()) return;
        TerminalEmulator terminal = ghosttyTerminal();
        append(terminal, "shell\r\n\033[?1049h\033[Hfull screen app");
        terminal.failNextGhosttySyncForTests(new IllegalStateException("injected"), false);
        append(terminal, "!");
        assertFalse(terminal.isGhosttyCoreActive());
        assertTrue(terminal.isAlternateBufferActive());
        assertEquals("full screen app!", terminal.getContent().text(0, 0, COLUMNS - 1, 0).trim());
    }

    public void testFormatterFailureStartsEmptyWithANotice() {
        if (!hostLibrary()) return;
        TerminalEmulator terminal = failedMidStream(true);
        assertFalse(terminal.isGhosttyCoreActive());
        assertEquals(0, terminal.getContent().historyRows());
        String screen = terminal.getContent().text(0, 0, COLUMNS - 1, ROWS - 1, true, false);
        assertEquals(TerminalEmulator.REPLAY_FAILED_NOTICE.trim(), screen.trim());
    }

    public void testTheTurnThatFailsKeepsItsOutput() {
        if (!hostLibrary()) return;
        TerminalEmulator terminal = ghosttyTerminal();
        append(terminal, "before");
        // Armed between turns: the next turn's sync fails, after Ghostty parsed that turn's bytes.
        terminal.failNextGhosttySyncForTests(new IllegalStateException("injected"), false);
        terminal.flushBatchedOutput(); // nothing pending: no sync, no failure
        assertTrue(terminal.isGhosttyCoreActive());
        append(terminal, " after");
        assertFalse(terminal.isGhosttyCoreActive());
        assertEquals("before after", terminal.getContent().text(0, 0, COLUMNS - 1, 0).trim());
    }

    public void testClustersReplayAsTheJavaCoreShowsThem() {
        if (!hostLibrary()) return;
        // Ghostty joins a skin tone or a flag into one two-cell cluster (mode 2027); the Java core
        // has no clustering mode and gives each code point its own cells. A replay is the original
        // text again, so it shows such a line exactly as the Java core would have from the start.
        String clusters = "skin 👍🏽 flag 🇸🇪 x\r\n";
        TerminalEmulator java = new TerminalEmulator(mOutput, false, COLUMNS, ROWS, INITIAL_CELL_WIDTH_PIXELS,
            INITIAL_CELL_HEIGHT_PIXELS, TRANSCRIPT, null);
        append(java, clusters);
        TerminalEmulator terminal = ghosttyTerminal();
        append(terminal, clusters);
        terminal.failNextGhosttySyncForTests(new IllegalStateException("injected"), false);
        append(terminal, " ");
        assertFalse(terminal.isGhosttyCoreActive());
        assertRowsEqual(0, java.getContent().row(0), terminal.getContent().row(0));
    }

    private static void assertRowsEqual(int y, TerminalRow want, TerminalRow got) {
        String wantText = new String(want.mText, 0, want.getSpaceUsed());
        String gotText = new String(got.mText, 0, got.getSpaceUsed());
        assertEquals("row " + y + " text", wantText, gotText);
        for (int x = 0; x < COLUMNS; x++) {
            long w = want.getStyle(x), g = got.getStyle(x);
            assertEquals("row " + y + " col " + x + " fg", TextStyle.decodeForeColor(w), TextStyle.decodeForeColor(g));
            assertEquals("row " + y + " col " + x + " bg", TextStyle.decodeBackColor(w), TextStyle.decodeBackColor(g));
            assertEquals("row " + y + " col " + x + " effect", TextStyle.decodeEffect(w), TextStyle.decodeEffect(g));
        }
    }
}
