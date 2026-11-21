package com.termux.terminal;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The Ghostty core's history read on demand ({@link GhosttyContent}) against the Java core fed the
 * same output. Runs only with the host library ({@code -Pghostty=true}); elsewhere every test
 * returns at once.
 */
public class GhosttyContentTest extends TerminalTestCase {

    private static boolean libraryBuilt() {
        return System.getProperty(GhosttyNative.TEST_LIBRARY_DIR_PROPERTY) != null;
    }

    private static TerminalEmulator emulator(int columns, int rows, int transcriptRows, boolean ghostty) {
        TerminalEmulator emulator = new TerminalEmulator(new MockTerminalOutput(), false, columns, rows,
            INITIAL_CELL_WIDTH_PIXELS, INITIAL_CELL_HEIGHT_PIXELS, transcriptRows, null);
        if (ghostty) assertTrue("library failed to load: " + GhosttyNative.loadError(), emulator.useGhosttyCore());
        return emulator;
    }

    private static void feed(TerminalEmulator emulator, String text) {
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        emulator.append(bytes, bytes.length);
    }

    /** Lines {@code from} to {@code to - 1}, each its own number then CR LF. */
    private static String numbered(int from, int to) {
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < to; i++) sb.append('L').append(i).append("\r\n");
        return sb.toString();
    }

    /** SGR colours and effects, wide characters, emoji, a combining mark and lines that wrap. */
    private static String mixed(int lines) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < lines; i++) {
            switch (i % 6) {
                case 0: sb.append(String.format(Locale.US, "\033[1;3%dm%04d bold\033[0m plain", i % 8, i)); break;
                case 1: sb.append(String.format(Locale.US, "%04d 日本語 wide", i)); break;
                case 2: sb.append(String.format(Locale.US, "%04d emoji 😀 and é", i)); break;
                case 3: sb.append(String.format(Locale.US, "%04d a long line that wraps past the edge of a narrow pane", i)); break;
                case 4: sb.append(String.format(Locale.US, "\033[38;5;%dm\033[48;2;10;20;30m%04d 256 on rgb\033[0m", i % 256, i)); break;
                default: sb.append(String.format(Locale.US, "\033[3;4;7m%04d italic under inverse\033[0m", i)); break;
            }
            sb.append("\r\n");
        }
        return sb.toString();
    }

    private static String rowText(TerminalContent content, int row) {
        return content.text(0, row, content.columns(), row, false, false);
    }

    /** Every readable row the same on both cores: text, wrap, and each cell's colours and effects. */
    private static void assertSameRows(TerminalContent java, TerminalContent ghostty) {
        assertEquals(java.columns(), ghostty.columns());
        assertEquals(java.screenRows(), ghostty.screenRows());
        assertEquals("history rows", java.historyRows(), ghostty.historyRows());
        for (int y = -java.historyRows(); y < java.screenRows(); y++) {
            TerminalRow j = java.row(y);
            TerminalRow g = ghostty.row(y);
            assertEquals("row " + y, rowText(java, y), rowText(ghostty, y));
            assertEquals("wrap of row " + y, j.getLineWrap(), g.getLineWrap());
            for (int x = 0; x < java.columns(); x++) {
                long js = j.getStyle(x), gs = g.getStyle(x);
                String at = "row " + y + " column " + x + " (" + rowText(java, y) + ")";
                assertEquals("foreground at " + at, TextStyle.decodeForeColor(js), TextStyle.decodeForeColor(gs));
                assertEquals("background at " + at, TextStyle.decodeBackColor(js), TextStyle.decodeBackColor(gs));
                // A blank cell's effects differ by core and draw nothing: the Java core erases a new
                // row with the whole current style, Ghostty with its background alone.
                if (j.mText[j.findStartOfColumn(x)] != ' ')
                    assertEquals("effect at " + at, TextStyle.decodeEffect(js), TextStyle.decodeEffect(gs));
            }
        }
    }

    public void testHistoryRowsReadOnDemandMatchTheJavaCore() {
        if (!libraryBuilt()) return;
        TerminalEmulator java = emulator(20, 5, 100, false);
        TerminalEmulator ghostty = emulator(20, 5, 100, true);
        // Several syncs, as output arrives in turns.
        String[] lines = mixed(60).split("(?<=\r\n)");
        for (int i = 0; i < lines.length; i += 7) {
            StringBuilder part = new StringBuilder();
            for (int k = i; k < Math.min(lines.length, i + 7); k++) part.append(lines[k]);
            feed(java, part.toString());
            feed(ghostty, part.toString());
        }
        assertTrue(ghostty.getContent().historyRows() > 20);
        assertEquals(0, ghostty.getScreen().getActiveTranscriptRows());
        assertSameRows(java.getContent(), ghostty.getContent());
        // Read twice, from the window the first read filled.
        assertSameRows(java.getContent(), ghostty.getContent());
        assertEquals(java.getContent().firstRowIndex(), ghostty.getContent().firstRowIndex());
    }

    public void testIndexesHoldAfterHistoryIsPrunedPastTheCap() {
        if (!libraryBuilt()) return;
        TerminalEmulator java = emulator(8, 4, 100, false);
        TerminalEmulator ghostty = emulator(8, 4, 100, true);
        // Turns of 40 rows, far past the 96-row cap and past several of the library's pages.
        for (int turn = 0; turn < 300; turn++) {
            String part = numbered(turn * 40, (turn + 1) * 40);
            feed(java, part);
            feed(ghostty, part);
            TerminalContent g = ghostty.getContent();
            assertEquals(java.getContent().historyRows(), g.historyRows());
            assertEquals("turn " + turn, java.getContent().firstRowIndex(), g.firstRowIndex());
            // The oldest readable row and the newest history row, named by absolute index.
            long first = g.firstRowIndex();
            assertEquals(rowText(java.getContent(), -java.getContent().historyRows()), rowText(g, -g.historyRows()));
            assertEquals("L" + (first - 0), rowText(g, -g.historyRows()));
            assertEquals("L" + (first + g.historyRows() - 1), rowText(g, -1));
        }
        assertEquals(96, ghostty.getContent().historyRows());
    }

    public void testOneWriteThatOutrunsHistoryLeavesNoOldIndexReadable() {
        if (!libraryBuilt()) return;
        TerminalEmulator ghostty = emulator(8, 4, 100, true);
        feed(ghostty, numbered(0, 50));
        TerminalContent g = ghostty.getContent();
        long newest = g.firstRowIndex() + g.historyRows() - 1;
        assertEquals("L46", rowText(g, -1));
        // Far more than the library keeps, in one write: the page holding both pins is pruned, and
        // the count must still put every row seen before below the oldest readable one.
        feed(ghostty, numbered(100000, 160000));
        assertTrue(newest < g.firstRowIndex());
        assertEquals("L159996", rowText(g, -1));
        assertEquals(96, g.historyRows());
        for (int y = -g.historyRows(); y < 0; y++) assertEquals("L" + (159996 + 1 + y), rowText(g, y));
    }

    /**
     * What {@code TerminalView.onScreenUpdated} does with a scrolled-back view and a selection: move
     * them up by the rows that scrolled, and stop at the oldest row once theirs is gone.
     */
    private static int follow(TerminalContent content, int topRow) {
        int shift = content.takeScrolledRows();
        if (-topRow + shift > content.historyRows()) return Integer.MIN_VALUE;
        return topRow - shift;
    }

    public void testScrolledBackViewAndSelectionStayOnTheirRowsWhileOutputArrives() {
        if (!libraryBuilt()) return;
        TerminalEmulator java = emulator(8, 4, 100, false);
        TerminalEmulator ghostty = emulator(8, 4, 100, true);
        feed(java, numbered(0, 60));
        feed(ghostty, numbered(0, 60));
        java.getContent().takeScrolledRows();
        ghostty.getContent().takeScrolledRows();
        int javaTop = -10, ghosttyTop = -10;
        String pinned = rowText(ghostty.getContent(), ghosttyTop);
        assertEquals(rowText(java.getContent(), javaTop), pinned);
        for (int turn = 0; ; turn++) {
            String part = numbered(60 + turn * 7, 60 + (turn + 1) * 7);
            feed(java, part);
            feed(ghostty, part);
            javaTop = follow(java.getContent(), javaTop);
            ghosttyTop = follow(ghostty.getContent(), ghosttyTop);
            assertEquals("turn " + turn, javaTop, ghosttyTop);
            if (ghosttyTop == Integer.MIN_VALUE) break; // pruned past the cap on both cores at once
            assertEquals("turn " + turn, pinned, rowText(ghostty.getContent(), ghosttyTop));
        }
    }

    public void testRowsLeftBeforeTheAlternateScreenAreCountedOnReturn() {
        if (!libraryBuilt()) return;
        TerminalEmulator ghostty = emulator(8, 4, 100, true);
        feed(ghostty, numbered(0, 20));
        ghostty.getContent().takeScrolledRows();
        feed(ghostty, numbered(20, 25) + "\033[?1049h\033[Halt");
        assertEquals(0, ghostty.getContent().historyRows());
        feed(ghostty, "\033[?1049l");
        TerminalContent g = ghostty.getContent();
        assertEquals(5, g.takeScrolledRows());
        assertEquals(22, g.historyRows());
        assertEquals("L21", rowText(g, -1));
    }

    public void testFindAndLinksOnScrollbackMatchTheJavaCore() {
        if (!libraryBuilt()) return;
        TerminalEmulator java = emulator(30, 4, 100, false);
        TerminalEmulator ghostty = emulator(30, 4, 100, true);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 40; i++) {
            if (i % 5 == 0) sb.append("see https://example.com/page/").append(i).append("/with/a/long/path?q=").append(i);
            else sb.append("plain line ").append(i);
            sb.append("\r\n");
        }
        feed(java, sb.toString());
        feed(ghostty, sb.toString());
        TerminalContent j = java.getContent(), g = ghostty.getContent();
        // Find reads one row at a time, as TerminalFindCoordinator.snapshot does.
        List<String> javaRows = new ArrayList<>(), ghosttyRows = new ArrayList<>();
        for (int y = -j.historyRows(); y < j.screenRows(); y++) javaRows.add(rowText(j, y));
        for (int y = -g.historyRows(); y < g.screenRows(); y++) ghosttyRows.add(rowText(g, y));
        assertEquals(javaRows, ghosttyRows);
        List<UrlDetector.UrlSpan> javaUrls = UrlDetector.find(j, -j.historyRows(), j.screenRows() - 1);
        List<UrlDetector.UrlSpan> ghosttyUrls = UrlDetector.find(g, -g.historyRows(), g.screenRows() - 1);
        assertEquals(8, javaUrls.size());
        assertEquals(describe(javaUrls), describe(ghosttyUrls));
        // A tap on a wrapped address in history.
        UrlDetector.UrlSpan first = javaUrls.get(0);
        int tapColumn = first.segmentStartColumn(0) + 1;
        assertEquals(first.url, TerminalLinks.at(java, tapColumn, first.segmentRow(0), true).uri);
        assertEquals(first.url, TerminalLinks.at(ghostty, tapColumn, first.segmentRow(0), true).uri);
        // The whole transcript, as a copy or a share takes it.
        assertEquals(j.transcriptText(true, false), g.transcriptText(true, false));
        assertEquals(j.transcriptText(true, true), g.transcriptText(true, true));
    }

    private static List<String> describe(List<UrlDetector.UrlSpan> spans) {
        List<String> out = new ArrayList<>();
        for (UrlDetector.UrlSpan span : spans) {
            StringBuilder sb = new StringBuilder(span.url);
            for (int s = 0; s < span.segmentCount(); s++)
                sb.append(' ').append(span.segmentRow(s)).append(':').append(span.segmentStartColumn(s))
                    .append('-').append(span.segmentEndColumn(s));
            out.add(sb.toString());
        }
        return out;
    }

    public void testResizeReflowsHistoryAndTheSizeComesFromTheSameSync() {
        if (!libraryBuilt()) return;
        TerminalEmulator ghostty = emulator(10, 4, 100, true);
        feed(ghostty, "0123456789abcdefghij\r\n" + numbered(0, 10));
        TerminalContent g = ghostty.getContent();
        int before = g.historyRows();
        assertEquals("0123456789", rowText(g, -before));
        assertEquals("abcdefghij", rowText(g, -before + 1));
        // Wider: the wrapped line rejoins in history, and rows read before are read again.
        ghostty.resize(20, 4, INITIAL_CELL_WIDTH_PIXELS, INITIAL_CELL_HEIGHT_PIXELS);
        assertEquals(20, g.columns());
        assertEquals(4, g.screenRows());
        int[] state = ghostty.ghosttyCore().state();
        assertEquals(state[GhosttyNative.STATE_COLS], g.columns());
        assertEquals(state[GhosttyNative.STATE_ROWS], g.screenRows());
        assertEquals(ghostty.mColumns, g.columns());
        assertEquals(ghostty.mRows, g.screenRows());
        assertEquals("0123456789abcdefghij", rowText(g, -g.historyRows()));
        assertEquals(before - 1, g.historyRows());
        // No Java transcript is kept, whatever the Java reflow pushed above the screen.
        ghostty.resize(20, 2, INITIAL_CELL_WIDTH_PIXELS, INITIAL_CELL_HEIGHT_PIXELS);
        assertEquals(0, ghostty.getScreen().getActiveTranscriptRows());
        assertEquals(2, g.screenRows());
        for (int y = -g.historyRows(); y < 2; y++) rowText(g, y);
    }

    public void testRowsOnlyResizePredictionMatchesTheGhosttyCore() {
        if (!libraryBuilt()) return;
        for (int rowsAfter : new int[]{3, 5, 8}) {
            for (int written : new int[]{2, 5, 30}) {
                TerminalEmulator ghostty = emulator(10, 5, 100, true);
                feed(ghostty, numbered(0, written) + "$ ");
                int predicted = ghostty.predictRowsOnlyResizeShift(rowsAfter);
                int markerBefore = -1;
                TerminalContent g = ghostty.getContent();
                String marker = "L" + (written - 1);
                for (int y = -g.historyRows(); y < g.screenRows(); y++) if (rowText(g, y).equals(marker)) markerBefore = y;
                ghostty.resize(10, rowsAfter, INITIAL_CELL_WIDTH_PIXELS, INITIAL_CELL_HEIGHT_PIXELS);
                int markerAfter = -1;
                for (int y = -g.historyRows(); y < g.screenRows(); y++) if (rowText(g, y).equals(marker)) markerAfter = y;
                assertEquals("5 -> " + rowsAfter + " rows after " + written + " lines", markerAfter - markerBefore, predicted);
            }
        }
    }

    public void testReleaseHandsTheHistoryToTheJavaTranscript() {
        if (!libraryBuilt()) return;
        TerminalEmulator ghostty = emulator(8, 4, 100, true);
        feed(ghostty, numbered(0, 300));
        TerminalContent g = ghostty.getContent();
        List<String> before = new ArrayList<>();
        for (int y = -g.historyRows(); y < g.screenRows(); y++) before.add(rowText(g, y));
        long firstIndex = g.firstRowIndex();
        ghostty.releaseGhosttyCore();
        TerminalContent java = ghostty.getContent();
        assertSame(ghostty.getScreen(), java);
        List<String> after = new ArrayList<>();
        for (int y = -java.historyRows(); y < java.screenRows(); y++) after.add(rowText(java, y));
        assertEquals(before, after);
        assertEquals(firstIndex, java.firstRowIndex());
    }

    public void testPromptJumpsReachAPromptInHistoryOnTheGhosttyCore() {
        if (!libraryBuilt()) return;
        TerminalEmulator java = emulator(8, 4, 100, false);
        TerminalEmulator ghostty = emulator(8, 4, 100, true);
        for (TerminalEmulator emulator : new TerminalEmulator[]{java, ghostty})
            feed(emulator, "\033]133;A\007$ ls\r\n" + numbered(0, 20));
        int row = ghostty.findPromptRow(0, true);
        assertEquals(java.findPromptRow(0, true), row);
        assertTrue("the prompt is in history: " + row, row < 0 && row != Integer.MIN_VALUE);
        assertEquals("$ ls", rowText(ghostty.getContent(), row).trim());
    }
}
