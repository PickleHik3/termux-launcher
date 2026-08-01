package com.termux.app.terminal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.termux.launcherctl.LauncherToolRegistry;

import org.junit.Test;

/**
 * The parts of the key inspector that do not need a live Activity: how bytes are rendered, and the
 * registration invariants that keep the inspector from breaking what it is meant to observe.
 */
public class TerminalKeyInspectorTest {

    @Test
    public void controlBytesUseCaretNotation() {
        // Matches `cat -v`, so a reading here can be compared with one taken inside the shell.
        assertEquals("^[[97;5u", TerminalKeyInspector.caret("\033[97;5u"));
        assertEquals("^M", TerminalKeyInspector.caret("\r"));
        assertEquals("^I", TerminalKeyInspector.caret("\t"));
        assertEquals("^?", TerminalKeyInspector.caret("\177"));
        assertEquals("^@", TerminalKeyInspector.caret("\0"));
        assertEquals("^A", TerminalKeyInspector.caret("\001"));
    }

    @Test
    public void printableBytesAreLeftAlone() {
        assertEquals("abc", TerminalKeyInspector.caret("abc"));
        assertEquals("é", TerminalKeyInspector.caret("é"));
    }

    @Test
    public void nothingWrittenIsSaidSo() {
        assertEquals("(nothing)", TerminalKeyInspector.caret(null));
        assertEquals("(nothing)", TerminalKeyInspector.caret(""));
    }

    @Test
    public void theToolIsRegisteredAndDispatchable() {
        LauncherToolRegistry.ToolMetadata tool =
            LauncherToolRegistry.getInstance().getTool(LauncherToolRegistry.TOOL_APP_KEY_INSPECTOR);
        assertNotNull(tool);
        assertEquals(LauncherToolRegistry.ToolExecutor.TERMINAL, tool.executor);
        assertEquals(LauncherToolRegistry.ToolRisk.LOW, tool.risk);
        assertTrue(TerminalActionDispatcher.handles(LauncherToolRegistry.TOOL_APP_KEY_INSPECTOR));
    }

    /**
     * A diagnostic that needs a chord to open cannot report that chord's own key events, so it is
     * deliberately bound to nothing. Openable from the palette only.
     */
    @Test
    public void theToolHasNoDefaultBinding() {
        LauncherToolRegistry.ToolMetadata tool =
            LauncherToolRegistry.getInstance().getTool(LauncherToolRegistry.TOOL_APP_KEY_INSPECTOR);
        assertTrue(tool.defaultBindings.isEmpty());
        assertTrue(TerminalKeyBindingResolver.getInstance().getConflicts().isEmpty());
    }

    /** With no inspector open, every recording site must be a cheap null check and nothing more. */
    @Test
    public void nothingIsActiveByDefault() {
        assertNull(TerminalKeyInspector.active());
    }
}
