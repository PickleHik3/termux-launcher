package com.termux.app.terminal.inappkeyboard.voice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The card editor's draft: caret moves and edits, line breaks, late phrases, where a tap lands,
 * and the cleanup's marks moving with the text.
 */
public class VoiceEditModelTest {

    @Test
    public void typingBackspaceAndDeleteWorkAtTheCaret() {
        VoiceEditModel model = new VoiceEditModel("open the page", 5);
        model.insert("up ");
        assertEquals("open up the page", model.text());
        assertEquals(8, model.caretUtf16());
        model.backspace();
        model.backspace();
        model.backspace();
        assertEquals("open the page", model.text());
        model.delete();
        assertEquals("open he page", model.text());
        assertEquals(5, model.caretUtf16());
    }

    @Test
    public void theCaretNeverSplitsASurrogatePair() {
        String emoji = new String(Character.toChars(0x1F600));
        VoiceEditModel model = new VoiceEditModel("a" + emoji + "b", 2);
        // UTF-16 offset 2 is inside the pair: the caret goes after it.
        assertEquals(3, model.caretUtf16());
        model.backspace();
        assertEquals("ab", model.text());
        assertEquals(1, model.caretUtf16());
    }

    @Test
    public void caretClampsAndCarriageReturnsAreNeverKept() {
        VoiceEditModel model = new VoiceEditModel("ab", 99);
        assertEquals(2, model.caretUtf16());
        model.moveCaret(-10);
        assertEquals(0, model.caret());
        model.insert("x\r");
        assertEquals("xab", model.text());
        new VoiceEditModel(null, -3);
    }

    @Test
    public void homeAndEndStayOnTheCaretsLine() {
        VoiceEditModel model = new VoiceEditModel("Shopping:\n- milk\n- eggs", 13);
        model.moveCaretToLineStart();
        assertEquals(10, model.caretUtf16());
        model.moveCaretToLineEnd();
        assertEquals(16, model.caretUtf16());
        model.moveCaret(1);
        model.moveCaretToLineEnd();
        assertEquals(model.text().length(), model.caretUtf16());
        model.setCaretUtf16(0);
        model.moveCaretToLineStart();
        assertEquals(0, model.caretUtf16());
    }

    @Test
    public void aLatePhraseJoinsTheEndAndTheCaretStaysPut() {
        VoiceEditModel model = new VoiceEditModel("open the", 4);
        model.appendPhrase(" settings page");
        assertEquals("open the settings page", model.text());
        assertEquals(4, model.caretUtf16());
        assertEquals("a\nb", VoiceEditModel.joinOnto("a\n", " b"));
        assertEquals("a b", VoiceEditModel.joinOnto("a ", "b"));
        assertEquals("b", VoiceEditModel.joinOnto("", " b"));
        assertEquals("a", VoiceEditModel.joinOnto("a", "  "));
    }

    @Test
    public void aTapOnPlainTextLandsWhereItIsAndTheGhostMeansTheEnd() {
        assertEquals(3, VoiceEditModel.shownOffset(null, "hello", 3));
        // The ghost bar sits after a space past the text.
        assertEquals(5, VoiceEditModel.shownOffset(null, "hello", 7));
        assertEquals(5, VoiceEditModel.shownOffset(null, "hello", -1));
    }

    @Test
    public void aTapOnTheMarkedCleanupLandsInTheCleanedText() {
        String raw = "um open the read me";
        String cleaned = "Open the README.";
        List<VoiceWordDiff.Op> ops = VoiceWordDiff.diff(raw, cleaned);
        String displayed = displayed(ops);
        // Inside a kept word: the same place in that word of the cleaned text.
        int inThe = displayed.indexOf("the") + 2;
        assertEquals(cleaned.indexOf("the") + 2, VoiceEditModel.shownOffset(ops, cleaned, inThe));
        int inReadme = displayed.indexOf("README.") + 3;
        assertEquals(cleaned.indexOf("README.") + 3, VoiceEditModel.shownOffset(ops, cleaned, inReadme));
        // Past the last word: the end.
        assertEquals(cleaned.length(), VoiceEditModel.shownOffset(ops, cleaned, displayed.length() + 4));
        // On a removed word: the end of the kept word before it, or the start with none.
        int onUm = displayed.indexOf("um") + 1;
        if (onUm > 0) assertEquals(0, VoiceEditModel.shownOffset(ops, cleaned, onUm));
    }

    @Test
    public void aTapOnACleanupWithLineBreaksLandsOnTheRightLine() {
        String raw = "shopping list milk eggs";
        String cleaned = "Shopping list:\n- milk\n- eggs";
        List<VoiceWordDiff.Op> ops = VoiceWordDiff.diff(raw, cleaned);
        String displayed = displayed(ops);
        int inEggs = displayed.lastIndexOf("eggs") + 2;
        assertEquals(cleaned.lastIndexOf("eggs") + 2, VoiceEditModel.shownOffset(ops, cleaned, inEggs));
    }

    // ------------------------------------------------------------------ the cleanup's marks

    private static VoiceEditModel.Mark mark(int start, int end, VoiceWordDiff.Kind kind) {
        return new VoiceEditModel.Mark(start, end, kind);
    }

    private static final VoiceWordDiff.Kind CHANGED = VoiceWordDiff.Kind.CHANGED;
    private static final VoiceWordDiff.Kind ADDED = VoiceWordDiff.Kind.ADDED;

    @Test
    public void aCorrectedWordIsMarkedAndTheRemovedOnesAreNot() {
        // wrold and there are removed, world added: only world is in the cleaned text to mark.
        assertEquals(Collections.singletonList(mark(6, 11, ADDED)),
            VoiceEditModel.cleanupMarks("hello wrold there", "hello world"));
        assertEquals(Arrays.asList(mark(0, 6, CHANGED), mark(21, 28, CHANGED)),
            VoiceEditModel.cleanupMarks("please summarise the readme", "Please summarise the README."));
    }

    @Test
    public void marksAcrossLineBreaksLandOnTheCleanedText() {
        String cleaned = "Shopping list:\n- milk\n- eggs";
        List<VoiceEditModel.Mark> marks = VoiceEditModel.cleanupMarks("shopping list milk eggs", cleaned);
        assertEquals(Arrays.asList(mark(0, 8, CHANGED), mark(9, 14, CHANGED), mark(15, 16, ADDED),
            mark(22, 23, ADDED)), marks);
        assertEquals("list:", cleaned.substring(9, 14));
    }

    @Test
    public void marksFollowTheCleanedTextsOwnWhitespaceNotThePanelsLayout() {
        // The panel would lay these out with one space and two breaks; the editor has the text itself.
        String cleaned = "Hello  world.\n\n\nBye";
        assertEquals(Arrays.asList(mark(0, 5, CHANGED), mark(7, 13, CHANGED), mark(16, 19, CHANGED)),
            VoiceEditModel.cleanupMarks("hello world bye", cleaned));
    }

    @Test
    public void anAddedWordAtTheEndIsMarkedAndNoCleanupMarksNothing() {
        assertEquals(Arrays.asList(mark(0, 4, CHANGED), mark(14, 21, ADDED)),
            VoiceEditModel.cleanupMarks("open the page", "Open the page please."));
        assertTrue(VoiceEditModel.cleanupMarks("run the tests", "run the tests").isEmpty());
        assertTrue(VoiceEditModel.cleanupMarks("", "").isEmpty());
    }

    @Test
    public void typingInsideAMarkGrowsItAndAtItsEdgesDoesNot() {
        VoiceEditModel model = new VoiceEditModel("hello world", 8,
            Collections.singletonList(mark(6, 11, ADDED)));
        model.insert("x");
        assertEquals("hello woxrld", model.text());
        assertEquals(Collections.singletonList(mark(6, 12, ADDED)), model.marks());
        // At its start: the mark moves on, it does not take the typing in.
        model.setCaretUtf16(6);
        model.insert("big ");
        assertEquals(Collections.singletonList(mark(10, 16, ADDED)), model.marks());
        // At its end: nothing changes.
        model.setCaretUtf16(16);
        model.insert("!");
        assertEquals("hello big woxrld!", model.text());
        assertEquals(Collections.singletonList(mark(10, 16, ADDED)), model.marks());
        // Before it: it shifts.
        model.setCaretUtf16(0);
        model.insert("oh ");
        assertEquals(Collections.singletonList(mark(13, 19, ADDED)), model.marks());
        assertEquals("woxrld", model.text().substring(13, 19));
    }

    @Test
    public void deletingAcrossAMarksEdgeTakesOnlyItsPartAndAnEmptyMarkGoes() {
        VoiceEditModel model = new VoiceEditModel("hello world now", 7,
            Arrays.asList(mark(0, 5, CHANGED), mark(6, 11, ADDED)));
        model.backspace(); // the w
        assertEquals(Arrays.asList(mark(0, 5, CHANGED), mark(6, 10, ADDED)), model.marks());
        model.backspace(); // the space between the marks
        assertEquals("helloorld now", model.text());
        assertEquals(Arrays.asList(mark(0, 5, CHANGED), mark(5, 9, ADDED)), model.marks());
        model.backspace(); // the last o of hello
        assertEquals(Arrays.asList(mark(0, 4, CHANGED), mark(4, 8, ADDED)), model.marks());
        // Forward delete through the whole of the second mark: it goes, the first stays.
        for (int k = 0; k < 4; k++) model.delete();
        assertEquals("hell now", model.text());
        assertEquals(Collections.singletonList(mark(0, 4, CHANGED)), model.marks());
        model.setCaretUtf16(4);
        for (int k = 0; k < 4; k++) model.backspace();
        assertTrue(model.marks().isEmpty());
        assertEquals(" now", model.text());
    }

    @Test
    public void aLatePhraseJoinsUnmarkedAfterAMarkAtTheEnd() {
        VoiceEditModel model = new VoiceEditModel("hello world", 0,
            Collections.singletonList(mark(6, 11, ADDED)));
        model.appendPhrase(" again");
        assertEquals("hello world again", model.text());
        assertEquals(Collections.singletonList(mark(6, 11, ADDED)), model.marks());
        assertEquals(0, model.caretUtf16());
    }

    @Test
    public void marksSurviveCarriageReturnsAndSurrogatePairs() {
        VoiceEditModel model = new VoiceEditModel("ab\r\ncd", 0, Collections.singletonList(mark(4, 6, CHANGED)));
        assertEquals("ab\ncd", model.text());
        assertEquals(Collections.singletonList(mark(3, 5, CHANGED)), model.marks());

        String emoji = new String(Character.toChars(0x1F600));
        VoiceEditModel pair = new VoiceEditModel("a" + emoji + "b", 3, Collections.singletonList(mark(0, 4, ADDED)));
        pair.backspace();
        assertEquals("ab", pair.text());
        assertEquals(Collections.singletonList(mark(0, 2, ADDED)), pair.marks());

        // Out of range is clamped, empty is dropped.
        VoiceEditModel clamped = new VoiceEditModel("abc", 0, Arrays.asList(mark(1, 9, ADDED), mark(2, 2, CHANGED)));
        assertEquals(Collections.singletonList(mark(1, 3, ADDED)), clamped.marks());
    }

    @Test
    public void theEditorOpensMarkedOnlyOnTheCleanupItself() {
        VoiceDictation dictation = new VoiceDictation();
        dictation.start("");
        dictation.append("hello wrold there");
        dictation.onStopped();
        assertNull(dictation.cleanedFrom());
        dictation.onCleaned("hello world");
        assertEquals(dictation.raw(), dictation.cleanedFrom());
        assertEquals(Collections.singletonList(mark(6, 11, ADDED)),
            VoiceEditModel.cleanupMarks(dictation.cleanedFrom(), dictation.editText()));
        // Undone: the text is as heard, nothing to mark; redo brings the cleanup back.
        dictation.toggleUndo();
        assertNull(dictation.cleanedFrom());
        dictation.toggleUndo();
        assertEquals(dictation.raw(), dictation.cleanedFrom());
        // Once edited, the text is the user's.
        dictation.beginEdit();
        dictation.commitEdit("hello world!");
        assertNull(dictation.cleanedFrom());
    }

    /** The marked text as the panel lays it out (VoiceTranscriptPanel#renderCleaned). */
    private static String displayed(List<VoiceWordDiff.Op> ops) {
        StringBuilder out = new StringBuilder();
        for (VoiceWordDiff.Op op : ops) {
            if (out.length() > 0) {
                if (op.breaks > 0) {
                    for (int b = 0; b < op.breaks; b++) out.append('\n');
                } else {
                    out.append(' ');
                }
            }
            out.append(op.word);
        }
        return out.toString();
    }
}
