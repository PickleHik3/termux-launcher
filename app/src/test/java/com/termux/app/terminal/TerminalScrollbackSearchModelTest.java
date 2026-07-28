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
}
