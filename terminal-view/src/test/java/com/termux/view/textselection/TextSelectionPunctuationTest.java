package com.termux.view.textselection;

import static org.junit.Assert.assertEquals;

import com.termux.terminal.TerminalRow;

import org.junit.Test;

public class TextSelectionPunctuationTest {

    private static TerminalRow row(String text) {
        TerminalRow row = new TerminalRow(40, 0L);
        for (int i = 0; i < text.length(); i++) row.setChar(i, text.charAt(i), 0L);
        return row;
    }

    @Test
    public void fullStopAfterAPathIsLeftOut() {
        String text = "/tmp/handoff.md.";
        assertEquals(text.length() - 2,
            TextSelectionCursorController.trimTrailingPunctuation(row(text), 0, text.length() - 1));
    }

    @Test
    public void everyKindOfClosingPunctuationGoes() {
        String text = "word?!.,;:";
        assertEquals(3, TextSelectionCursorController.trimTrailingPunctuation(row(text), 0, text.length() - 1));
    }

    @Test
    public void aWordWithoutPunctuationIsUntouched() {
        assertEquals(4, TextSelectionCursorController.trimTrailingPunctuation(row("hello"), 0, 4));
    }

    @Test
    public void punctuationAloneKeepsItsFirstCell() {
        assertEquals(0, TextSelectionCursorController.trimTrailingPunctuation(row("..."), 0, 2));
    }

    @Test
    public void aDotInsideTheWordStays() {
        String text = "file.md";
        assertEquals(6, TextSelectionCursorController.trimTrailingPunctuation(row(text), 0, 6));
    }

    @Test
    public void onlyTheEndOfTheSelectionMoves() {
        String text = "x say: hi.";
        assertEquals(8, TextSelectionCursorController.trimTrailingPunctuation(row(text), 7, 9));
    }
}
