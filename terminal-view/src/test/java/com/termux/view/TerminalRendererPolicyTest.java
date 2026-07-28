package com.termux.view;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TerminalRendererPolicyTest {

    @Test
    public void neverKeepsCaltEverywhere() {
        assertFalse(TerminalRenderer.disablesLigatures(
            TerminalRenderer.LigaturePolicy.NEVER, false));
        assertFalse(TerminalRenderer.disablesLigatures(
            TerminalRenderer.LigaturePolicy.NEVER, true));
    }

    @Test
    public void cursorDisablesCaltOnlyInCursorRun() {
        assertFalse(TerminalRenderer.disablesLigatures(
            TerminalRenderer.LigaturePolicy.CURSOR, false));
        assertTrue(TerminalRenderer.disablesLigatures(
            TerminalRenderer.LigaturePolicy.CURSOR, true));
    }

    @Test
    public void alwaysDisablesCaltEverywhere() {
        assertTrue(TerminalRenderer.disablesLigatures(
            TerminalRenderer.LigaturePolicy.ALWAYS, false));
        assertTrue(TerminalRenderer.disablesLigatures(
            TerminalRenderer.LigaturePolicy.ALWAYS, true));
    }
}
