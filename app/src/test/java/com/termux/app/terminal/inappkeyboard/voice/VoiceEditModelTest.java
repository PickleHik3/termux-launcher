package com.termux.app.terminal.inappkeyboard.voice;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

import java.util.List;

/** The card editor's draft: caret moves and edits, line breaks, late phrases, and where a tap lands. */
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
