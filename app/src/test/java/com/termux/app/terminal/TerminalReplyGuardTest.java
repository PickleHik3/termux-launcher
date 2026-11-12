package com.termux.app.terminal;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TerminalReplyGuardTest {

    @Test
    public void sendsWhenTheAskerIsStillInFront() {
        assertTrue(TerminalReplyGuard.shouldSend(42, "claude", 42, "claude"));
    }

    @Test
    public void refusesWhenTheProcessGroupChanged() {
        assertFalse(TerminalReplyGuard.shouldSend(42, "claude", 43, "claude"));
    }

    @Test
    public void refusesWhenTheNameChanged() {
        assertFalse(TerminalReplyGuard.shouldSend(42, "claude", 42, "bash"));
        assertFalse(TerminalReplyGuard.shouldSend(42, "claude", 42, null));
    }

    @Test
    public void refusesAnUnknownAsker() {
        assertFalse(TerminalReplyGuard.shouldSend(0, "claude", 0, "claude"));
        assertFalse(TerminalReplyGuard.shouldSend(42, null, 42, null));
        assertFalse(TerminalReplyGuard.shouldSend(42, "", 42, ""));
    }

    @Test
    public void replyTextEndsWithReturn() {
        assertEquals("yes\r", TerminalReplyGuard.replyText("yes"));
    }

    @Test
    public void lineBreaksBecomeSpaces() {
        assertEquals("a b c d\r", TerminalReplyGuard.replyText("a\nb\r\nc\rd"));
        assertEquals("a  b\r", TerminalReplyGuard.replyText("a\n\nb"));
    }

    @Test
    public void controlsAreDroppedButTabStays() {
        assertEquals("ab\r", TerminalReplyGuard.replyText("a\u0000\u0007\u001b\u007fb"));
        assertEquals("a\tb\r", TerminalReplyGuard.replyText("a\tb"));
    }

    @Test
    public void trailingWhitespaceIsTrimmed() {
        assertEquals("ok\r", TerminalReplyGuard.replyText("ok \n "));
    }

    @Test
    public void emptyAfterCleaningSendsNothing() {
        assertEquals("", TerminalReplyGuard.replyText(""));
        assertEquals("", TerminalReplyGuard.replyText(" \n\u0007 "));
        assertEquals("", TerminalReplyGuard.replyText(null));
    }
}
