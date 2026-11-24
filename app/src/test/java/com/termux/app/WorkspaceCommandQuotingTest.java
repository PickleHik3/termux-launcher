package com.termux.app;

import com.termux.app.terminal.WorkspaceRestore;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * A restored pane runs its captured argv through a shell, so every word has to survive the trip
 * back through shell parsing as the one literal word it was. Captured argv comes from procfs and
 * is entirely user-controlled — filenames with spaces, quotes, backslashes and shell
 * metacharacters all reach this quoting, and getting it wrong would either break the command or
 * run something other than what was captured.
 *
 * <p>The expectations below were checked against real bash, fish and sh.
 */
public class WorkspaceCommandQuotingTest {

    @Test
    public void plainWordIsQuotedWhole() {
        assertEquals("'make'", WorkspaceRestore.shellQuote("make", false));
    }

    @Test
    public void spacesStayInsideOneWord() {
        assertEquals("'my file.txt'", WorkspaceRestore.shellQuote("my file.txt", false));
    }

    @Test
    public void posixClosesEscapesAndReopensAroundASingleQuote() {
        // POSIX single quotes contain no escapes at all, so the string has to be broken.
        assertEquals("'it'\\''s'", WorkspaceRestore.shellQuote("it's", false));
    }

    @Test
    public void fishEscapesTheQuoteInPlace() {
        // Fish honours \' inside single quotes, so the string never has to be broken.
        assertEquals("'it\\'s'", WorkspaceRestore.shellQuote("it's", true));
    }

    @Test
    public void posixLeavesBackslashesAloneButFishDoublesThem() {
        // Inside single quotes POSIX takes a backslash literally; fish reads it as an escape.
        assertEquals("'a\\b'", WorkspaceRestore.shellQuote("a\\b", false));
        assertEquals("'a\\\\b'", WorkspaceRestore.shellQuote("a\\b", true));
    }

    @Test
    public void fishQuotingSurvivesATrailingBackslash() {
        // Left undoubled, this would escape fish's own closing quote and swallow the rest of the
        // command line, which is how a mis-quoted argument turns into running something else.
        String quoted = WorkspaceRestore.shellQuote("trailing\\", true);
        assertEquals("'trailing\\\\'", quoted);
        assertTrue("must still be a closed string", quoted.endsWith("'"));
    }

    @Test
    public void shellMetacharactersAreNeutralisedInBothStyles() {
        for (String hostile : Arrays.asList("$HOME", "`id`", "$(id)", "a;rm -rf /", "a|b", "a&b",
                "*", "~", "\n")) {
            assertEquals("'" + hostile + "'", WorkspaceRestore.shellQuote(hostile, false));
            assertEquals("'" + hostile + "'", WorkspaceRestore.shellQuote(hostile, true));
        }
    }

    @Test
    public void commandLineJoinsEveryArgumentQuoted() {
        assertEquals("'git' 'commit' '-m' 'a message'", WorkspaceRestore.commandLine(
            Arrays.asList("git", "commit", "-m", "a message"), false));
    }

    @Test
    public void emptyArgumentSurvivesAsAnEmptyWord() {
        assertEquals("'grep' '' 'file'",
            WorkspaceRestore.commandLine(Arrays.asList("grep", "", "file"), false));
    }

    @Test
    public void emptyCommandProducesNothing() {
        assertEquals("", WorkspaceRestore.commandLine(Collections.emptyList(), false));
    }

    @Test
    public void fishIsDetectedByItsBinaryName() {
        assertTrue(TermuxActivity.isFishShell("/data/data/com.termux/files/usr/bin/fish"));
        assertFalse(TermuxActivity.isFishShell("/data/data/com.termux/files/usr/bin/bash"));
        // A shell whose path merely mentions fish is not fish.
        assertFalse(TermuxActivity.isFishShell("/opt/fish/bin/bash"));
        assertFalse(TermuxActivity.isFishShell(null));
    }
}
