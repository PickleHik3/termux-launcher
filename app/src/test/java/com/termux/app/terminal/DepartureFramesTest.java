package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** The departure card pictures only the leaving window's panes, in the tree's order. */
public class DepartureFramesTest {
    @Test
    public void framesOfOtherWindowsAreExcluded() {
        Map<String, String> frames = new HashMap<>();
        frames.put("a", "fa");
        frames.put("b", "fb");
        frames.put("other", "fo");
        assertEquals(Arrays.asList("fb", "fa"),
            TerminalPaneController.framesOf(Arrays.asList("b", "a"), frames));
    }

    @Test
    public void paneWithoutFrameIsSkipped() {
        Map<String, String> frames = new HashMap<>();
        frames.put("a", "fa");
        assertEquals(Collections.singletonList("fa"),
            TerminalPaneController.framesOf(Arrays.asList("missing", "a"), frames));
    }

    @Test
    public void noLeavesGiveNoFrames() {
        assertEquals(Collections.emptyList(),
            TerminalPaneController.framesOf(Collections.<String>emptyList(), new HashMap<String, String>()));
    }
}
