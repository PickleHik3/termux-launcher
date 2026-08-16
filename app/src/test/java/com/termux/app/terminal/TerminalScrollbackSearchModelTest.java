package com.termux.app.terminal;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TerminalScrollbackSearchModelTest {

    @Test
    public void searchIsLiteralCaseInsensitiveAndNewestFirst() {
        List<TerminalScrollbackSearchModel.Line> lines = Arrays.asList(
            new TerminalScrollbackSearchModel.Line(-2, "First ERROR [x]"),
            new TerminalScrollbackSearchModel.Line(-1, "middle"),
            new TerminalScrollbackSearchModel.Line(0, "latest error [x] error"));
        List<TerminalScrollbackSearchModel.Match> matches =
            TerminalScrollbackSearchModel.search(lines, " Error ");
        assertEquals(3, matches.size());
        assertEquals(0, matches.get(0).row);
        assertEquals(17, matches.get(1).start);
        assertEquals(-2, matches.get(2).row);
        assertEquals(1, TerminalScrollbackSearchModel.search(lines, "[x]").get(0).row + 1);
    }

    @Test
    public void blankQueryReturnsNothingAndLongLinesGetSnippets() {
        assertTrue(TerminalScrollbackSearchModel.search(Arrays.asList(
            new TerminalScrollbackSearchModel.Line(0, "anything")), "  ").isEmpty());
        String line = "prefix ".repeat(20) + "needle" + " suffix".repeat(20);
        String snippet = TerminalScrollbackSearchModel.search(Arrays.asList(
            new TerminalScrollbackSearchModel.Line(-1, line)), "needle").get(0).snippet;
        assertTrue(snippet.startsWith("…"));
        assertTrue(snippet.endsWith("…"));
    }

    /**
     * Arrow navigation, which is the only way to walk the results from the in-app keyboard, an
     * extra-keys row or a hardware arrow — none of them can tap a row.
     */
    @Test
    public void highlightMovesByArrowsAndClampsAtBothEnds() {
        assertEquals(1, TerminalScrollbackSearchModel.moveHighlight(0, 1, 3));
        assertEquals(0, TerminalScrollbackSearchModel.moveHighlight(1, -1, 3));
        // Held at an end, an arrow stays put rather than wrapping to the far end.
        assertEquals(0, TerminalScrollbackSearchModel.moveHighlight(0, -1, 3));
        assertEquals(2, TerminalScrollbackSearchModel.moveHighlight(2, 1, 3));
        // A page key is a bigger delta and lands on the edge when it overshoots.
        assertEquals(2, TerminalScrollbackSearchModel.moveHighlight(0, 5, 3));
        assertEquals(0, TerminalScrollbackSearchModel.moveHighlight(2, -5, 3));
    }

    @Test
    public void highlightStaysAtZeroWithoutResults() {
        assertEquals(0, TerminalScrollbackSearchModel.moveHighlight(0, 1, 0));
        assertEquals(0, TerminalScrollbackSearchModel.moveHighlight(3, -1, 0));
    }
}
