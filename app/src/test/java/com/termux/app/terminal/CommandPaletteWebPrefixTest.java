package com.termux.app.terminal;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Which queries turn the palette into a web search. The prefix is the whole gate: everything
 * without one has to keep reaching the launcher's own actions and apps untouched.
 */
public class CommandPaletteWebPrefixTest {

    @Test
    public void questionMarkOpensWebMode() {
        assertEquals("nixos generations", TerminalCommandPalette.webQueryFor("?nixos generations"));
        assertEquals("example.com", TerminalCommandPalette.webQueryFor("? example.com"));
        assertEquals("", TerminalCommandPalette.webQueryFor("?"));
    }

    @Test
    public void goAlsoOpensIt() {
        assertEquals("example.com", TerminalCommandPalette.webQueryFor("go example.com"));
        assertEquals("example.com", TerminalCommandPalette.webQueryFor("GO example.com"));
        assertEquals("", TerminalCommandPalette.webQueryFor("go "));
    }

    @Test
    public void everythingElseStaysAnOrdinaryQuery() {
        assertNull(TerminalCommandPalette.webQueryFor("go"));
        assertNull(TerminalCommandPalette.webQueryFor("google"));
        assertNull(TerminalCommandPalette.webQueryFor("split pane"));
        assertNull(TerminalCommandPalette.webQueryFor(""));
        // The mark has to lead: a question inside a query is part of the query.
        assertNull(TerminalCommandPalette.webQueryFor("what is this?"));
    }
}
