package com.termux.app.terminal;

import com.termux.terminal.TerminalBuffer;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import static org.junit.Assert.assertEquals;
import android.app.Application;
import org.robolectric.annotation.Config;

/** A pane read takes its last lines from the bottom rows only, and says the same as the whole transcript. */
@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class)
public class PaneTailTextTest {

    private static final int COLUMNS = 6, SCREEN_ROWS = 4;
    /** Any style: the text is all a pane read keeps. */
    private static final long STYLE = 0;

    /** {@code lines} written one per row, the screen scrolling as it fills; null leaves a row blank. */
    private static TerminalBuffer buffer(String... lines) {
        TerminalBuffer buffer = new TerminalBuffer(COLUMNS, 200, SCREEN_ROWS);
        int row = 0;
        for (String line : lines) {
            if (row == SCREEN_ROWS) {
                buffer.scrollDownOneLine(0, SCREEN_ROWS, STYLE);
                row = SCREEN_ROWS - 1;
            }
            if (line != null)
                for (int x = 0; x < line.length(); x++) buffer.setChar(x, row, line.charAt(x), STYLE);
            row++;
        }
        return buffer;
    }

    private static void assertMatchesWholeTranscript(TerminalBuffer buffer, int count) {
        String whole = TerminalActionDispatcher.lastLines(buffer.getTranscriptTextWithoutJoinedLines(), count);
        assertEquals(whole, TerminalActionDispatcher.tailText(buffer, count));
    }

    @Test
    public void tailOfALongTranscriptMatchesTheWholeRead() {
        String[] lines = new String[60];
        for (int i = 0; i < lines.length; i++) lines[i] = "l" + i;
        TerminalBuffer buffer = buffer(lines);
        for (int count = 1; count <= 70; count++) assertMatchesWholeTranscript(buffer, count);
    }

    @Test
    public void blankRowsBelowTheLastTextAreSkippedAsTheWholeReadSkipsThem() {
        String[] lines = new String[30];
        for (int i = 0; i < 20; i++) lines[i] = "  t" + i;
        TerminalBuffer buffer = buffer(lines);
        for (int count = 1; count <= 40; count++) assertMatchesWholeTranscript(buffer, count);
    }

    @Test
    public void shortTranscriptKeepsTheLeadingTrim() {
        TerminalBuffer buffer = buffer(null, "  a", "b");
        for (int count = 1; count <= 5; count++) assertMatchesWholeTranscript(buffer, count);
    }
}
