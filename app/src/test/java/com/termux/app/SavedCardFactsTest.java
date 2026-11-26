package com.termux.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.termux.app.overview.CardFacts;
import com.termux.app.terminal.TerminalWorkspace;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class SavedCardFactsTest {

    private static TerminalWorkspace.Window window(String cwd) {
        return new TerminalWorkspace.Window(0, new TerminalWorkspace.Pane(cwd, null, null));
    }

    private static TerminalWorkspace.Session session(TerminalWorkspace.Window... windows) {
        return new TerminalWorkspace.Session(null, 0, Arrays.asList(windows));
    }

    @Test public void aFileWithNoWindowToOpenHasNoCard() {
        TerminalWorkspace.Window homeFloat = new TerminalWorkspace.Window(0,
            new TerminalWorkspace.Pane("/f", null, null), null, null,
            new TerminalWorkspace.HomeFloat(3, false, null, null));
        assertNull(SavedCardFacts.facts(new TerminalWorkspace("f", 1L, 0,
            Collections.singletonList(session(homeFloat)))));
    }

    @Test public void theCardIsDrawnFromTheFrontWindowsSavedTree() {
        TerminalWorkspace.Pane left = new TerminalWorkspace.Pane("/home/a", null,
            Collections.singletonList("/usr/bin/nvim"));
        TerminalWorkspace.Pane right = new TerminalWorkspace.Pane("/home/proj/", null, null);
        TerminalWorkspace.Window split = new TerminalWorkspace.Window(1,
            new TerminalWorkspace.Split(TerminalWorkspace.Split.HORIZONTAL, 1f, 2f, left, right));
        TerminalWorkspace saved = new TerminalWorkspace("two", 1L, 0,
            Collections.singletonList(session(split, window("/other"))));

        CardFacts.Workspace facts = SavedCardFacts.facts(saved);
        assertNotNull(facts);
        CardFacts.Split root = (CardFacts.Split) facts.root;
        assertTrue(root.sideBySide);
        assertEquals(1f, root.weightA, 0f);
        assertEquals(2f, root.weightB, 0f);
        CardFacts.Pane a = (CardFacts.Pane) root.a;
        CardFacts.Pane b = (CardFacts.Pane) root.b;
        assertFalse(a.active);
        assertTrue(b.active);
        assertEquals(Collections.singletonList("nvim"), Arrays.asList(a.lines));
        assertEquals(Collections.singletonList("proj"), Arrays.asList(b.lines));
    }

    @Test public void aStackedSplitAndFloatsAreDrawnToo() {
        TerminalWorkspace.FloatingPane floating = new TerminalWorkspace.FloatingPane(
            new TerminalWorkspace.Pane("/x", null, null), .2f, .1f, .5f, .4f);
        TerminalWorkspace.Window w = new TerminalWorkspace.Window(0,
            new TerminalWorkspace.Split(TerminalWorkspace.Split.VERTICAL, 1f, 1f,
                new TerminalWorkspace.Pane("/a", null, null),
                new TerminalWorkspace.Pane("/b", null, null)),
            Collections.singletonList(floating));

        CardFacts.Workspace facts = SavedCardFacts.facts(w);
        assertFalse(((CardFacts.Split) facts.root).sideBySide);
        assertEquals(1, facts.floats.size());
        assertEquals(.7f, facts.floats.get(0).right, .0001f);
        assertEquals(.5f, facts.floats.get(0).bottom, .0001f);
    }
}
