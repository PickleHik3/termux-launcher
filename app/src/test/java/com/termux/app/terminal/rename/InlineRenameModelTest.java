package com.termux.app.terminal.rename;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class InlineRenameModelTest {

    @Test
    public void seedsFromTheCurrentNameWithTheCaretAtItsEnd() {
        InlineRenameModel model = new InlineRenameModel("work", 8);
        assertEquals("work", model.text());
        assertEquals(4, model.caret());
        assertEquals(4, model.remaining());
    }

    @Test
    public void insertsAtTheCaretAndStopsAtTheCap() {
        InlineRenameModel model = new InlineRenameModel("ab", 4);
        model.moveCaret(-1);
        model.insert("xy");
        assertEquals("axyb", model.text());
        assertEquals(3, model.caret());
        // Cap reached: further typing is dropped rather than shifting the tail out.
        model.insert("z");
        assertEquals("axyb", model.text());
    }

    @Test
    public void countsCodePointsNotChars() {
        InlineRenameModel model = new InlineRenameModel(null, 2);
        model.insert("🚀");
        assertEquals(1, model.codePointCount());
        assertEquals(1, model.remaining());
        // Backspace deletes the whole surrogate pair, never half of it.
        model.backspace();
        assertEquals("", model.text());
        assertEquals(0, model.caret());
    }

    @Test
    public void capTruncatesASeededNameThatIsTooLong() {
        InlineRenameModel model = new InlineRenameModel("abcdefghij", 8);
        assertEquals("abcdefgh", model.text());
        assertEquals(0, model.remaining());
    }

    @Test
    public void deleteRemovesForwardAndLeavesTheCaret() {
        InlineRenameModel model = new InlineRenameModel("abc", 8);
        model.moveCaretToStart();
        model.delete();
        assertEquals("bc", model.text());
        assertEquals(0, model.caret());
    }

    @Test
    public void caretIsClampedToTheDraft() {
        InlineRenameModel model = new InlineRenameModel("ab", 8);
        model.moveCaret(9);
        assertEquals(2, model.caret());
        model.moveCaret(-9);
        assertEquals(0, model.caret());
    }

    @Test
    public void replaceAllTakesASuggestedNameWholesale() {
        InlineRenameModel model = new InlineRenameModel("old", 8);
        model.replaceAll("suggested");
        assertEquals("suggeste", model.text());
        assertEquals(8, model.caret());
    }

    @Test
    public void anEmptiedDraftCommitsAsNullSoTheNameIsCleared() {
        InlineRenameModel model = new InlineRenameModel("work", 8);
        assertEquals("work", model.committedName());
        model.replaceAll("   ");
        assertNull(model.committedName());
        assertTrue(model.isEmpty() || model.committedName() == null);
    }
}
