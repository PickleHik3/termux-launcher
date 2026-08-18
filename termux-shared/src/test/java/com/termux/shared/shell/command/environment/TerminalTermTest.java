package com.termux.shared.shell.command.environment;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class TerminalTermTest {

    @Test
    public void unconfiguredOrInvalidValuesKeepHistoricalDefault() {
        assertEquals("xterm-256color", TerminalTerm.resolve(null));
        assertEquals("xterm-256color", TerminalTerm.resolve("  "));
        assertEquals("xterm-256color", TerminalTerm.resolve("xterm-kitty\nBAD=value"));
    }

    @Test
    public void configuredValueIsTrimmedAndPreserved() {
        assertEquals("xterm-kitty", TerminalTerm.resolve("  xterm-kitty  "));
        assertEquals("screen-256color-bce", TerminalTerm.resolve("screen-256color-bce"));
    }
}
