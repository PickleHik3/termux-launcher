package com.termux.view;

import android.app.Application;
import android.os.Build;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.ConscryptMode;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = {Build.VERSION_CODES.P}, application = Application.class)
@ConscryptMode(ConscryptMode.Mode.OFF)
public class TerminalViewCurrentInputTest {

    @Test
    public void extractCurrentInput_returnsNullWhenSplitCharMissing() {
        assertNull(TerminalView.extractCurrentInputFromLine("command", 0, ':', null));
    }

    @Test
    public void extractCurrentInput_sanitizesInput() {
        String result = TerminalView.extractCurrentInputFromLine("shell: git status!", 0, ':', null);
        assertEquals("git status", result);
    }

    @Test
    public void extractCurrentInput_collapsesSpaces() {
        String result = TerminalView.extractCurrentInputFromLine("prompt:   foo   bar", 0, ':', null);
        assertEquals("foo bar", result);
    }

    @Test
    public void extractCurrentInput_insertsCharAtEnd() {
        String result = TerminalView.extractCurrentInputFromLine("cmd: foo", 8, ':', 'x');
        assertEquals("foox", result);
    }

    @Test
    public void extractCurrentInput_insertsCharInMiddle() {
        String result = TerminalView.extractCurrentInputFromLine("cmd: foo", 7, ':', 'x');
        assertEquals("foxo", result);
    }

    @Test
    public void extractCurrentInput_insertsCharAtStart() {
        String result = TerminalView.extractCurrentInputFromLine("cmd: foo", 0, ':', 'x');
        assertEquals("foox", result);
    }

    @Test
    public void literalAppSearchPrefixRequiresACommandBoundary() {
        assertTrue(TerminalView.hasAppSearchPrefixInLine("user@host:~$ %fire", 18, '%'));
        assertTrue(TerminalView.hasAppSearchPrefixInLine("%fire", 5, '%'));
        assertFalse(TerminalView.hasAppSearchPrefixInLine("echo 50%fire", 12, '%'));
        assertFalse(TerminalView.hasAppSearchPrefixInLine("echo %fire", 10, '%'));
    }

    @Test
    public void literalAppSearchPrefixRejectsShellSyntaxAfterPrefix() {
        assertFalse(TerminalView.hasAppSearchPrefixInLine("$ %fire/fox", 11, '%'));
        assertFalse(TerminalView.hasAppSearchPrefixInLine("$ command", 9, '%'));
    }
}
