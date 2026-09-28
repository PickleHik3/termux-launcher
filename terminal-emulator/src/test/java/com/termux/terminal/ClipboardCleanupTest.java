package com.termux.terminal;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ClipboardCleanupTest {

    @Test
    public void forCopyStripsTrailingSpacesAndTabsPerLine() {
        assertEquals("hello", ClipboardCleanup.forCopy("hello   "));
        assertEquals("hello", ClipboardCleanup.forCopy("hello\t\t"));
        assertEquals("hello", ClipboardCleanup.forCopy("hello \t "));
    }

    @Test
    public void forCopyKeepsLeadingWhitespaceAndInteriorBlankLines() {
        assertEquals("  hello", ClipboardCleanup.forCopy("  hello  "));
        assertEquals("one\n\ntwo", ClipboardCleanup.forCopy("one   \n   \ntwo  "));
    }

    @Test
    public void forCopyDropsOnlyTrailingBlankLines() {
        assertEquals("one\ntwo", ClipboardCleanup.forCopy("one\ntwo\n\n   \n"));
    }

    @Test
    public void forCopyOfBlockSelectionRowsTrimsEachRow() {
        // A block (column) selection joins its rows with '\n' just like CHAR/LINE selection; each
        // row still carries its own trailing padding from the rectangle it was cut from.
        assertEquals("abc\nde\nfgh", ClipboardCleanup.forCopy("abc   \nde \nfgh"));
    }

    @Test
    public void forCopyOfWhitespaceOnlyTextIsEmpty() {
        assertEquals("", ClipboardCleanup.forCopy("   \n\t\n "));
    }

    @Test
    public void forCopyPassesThroughNullAndEmpty() {
        assertEquals(null, ClipboardCleanup.forCopy(null));
        assertEquals("", ClipboardCleanup.forCopy(""));
    }

    @Test
    public void forPasteTrimsTrailingNewlineFromSingleLine() {
        assertEquals("echo hi", ClipboardCleanup.forPaste("echo hi\n"));
    }

    @Test
    public void forPasteTrimsTrailingSpaces() {
        assertEquals("echo hi", ClipboardCleanup.forPaste("echo hi   "));
    }

    @Test
    public void forPasteOfWhitespaceOnlyTextIsEmpty() {
        assertEquals("", ClipboardCleanup.forPaste("   \t  "));
    }

    @Test
    public void forPasteLeavesMultiLineTextUntouched() {
        assertEquals("echo one\necho two\n", ClipboardCleanup.forPaste("echo one\necho two\n"));
    }

    @Test
    public void forPasteTrimsCrlf() {
        assertEquals("echo hi", ClipboardCleanup.forPaste("echo hi\r\n"));
    }

    @Test
    public void forPasteTrimsTrailingTabThenNewline() {
        assertEquals("echo hi", ClipboardCleanup.forPaste("echo hi\t\n"));
    }

    @Test
    public void forPastePassesThroughNullAndEmpty() {
        assertEquals(null, ClipboardCleanup.forPaste(null));
        assertEquals("", ClipboardCleanup.forPaste(""));
    }
}
