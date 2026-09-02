package com.termux.app.launcher.drawer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/**
 * The focusless search field's state.
 *
 * <p>There is no {@code EditText} behind the drawer's pill, so nothing else holds this text and
 * nothing else clamps the caret. The returned "changed" flags are load-bearing too: they are what
 * stops a caret key from re-running the ranker over the whole catalogue.
 */
public class AppDrawerSearchModelTest {

    /** U+1F5C2, a surrogate pair — an app name, or a pasted query, can contain one. */
    private static final String CARD_INDEX = "🗂";

    @Test
    public void insertLandsAtTheCaretAndCarriesItAlong() {
        AppDrawerSearchModel model = new AppDrawerSearchModel();
        assertTrue(model.isEmpty());
        assertTrue(model.insertCodePoint('t'));
        assertTrue(model.insertCodePoint('r'));
        assertTrue(model.insertCodePoint('m'));
        assertEquals("trm", model.query());
        assertEquals(3, model.caret());
        assertFalse(model.isEmpty());

        // Back one and fix the typo in the middle.
        assertTrue(model.moveCursor(-1));
        assertTrue(model.insertCodePoint('e'));
        assertEquals("trem", model.query());
        assertEquals(3, model.caret());

        // Nothing typed is not a change, so the ranker is not re-run for it.
        assertFalse(model.insert(""));
        assertFalse(model.insertCodePoint(-1));
        assertEquals("trem", model.query());
    }

    @Test
    public void backspaceDeletesBeforeTheCaretAndStopsAtTheStart() {
        AppDrawerSearchModel model = new AppDrawerSearchModel();
        model.insert("term");
        model.moveCursor(-1);
        assertTrue(model.backspace());
        assertEquals("tem", model.query());
        assertEquals(2, model.caret());

        model.moveCursor(-9);
        assertEquals(0, model.caret());
        assertFalse(model.backspace());
        assertEquals("tem", model.query());
    }

    @Test
    public void clearEmptiesQueryAndCaretAndReportsWhetherThereWasAnything() {
        AppDrawerSearchModel model = new AppDrawerSearchModel();
        assertFalse(model.clear());
        model.insert("gallery");
        model.moveCursor(-3);
        assertTrue(model.clear());
        assertEquals("", model.query());
        assertEquals(0, model.caret());
        assertTrue(model.isEmpty());
        assertFalse(model.clear());
    }

    @Test
    public void caretClampsAtBothEnds() {
        AppDrawerSearchModel model = new AppDrawerSearchModel();
        model.insert("maps");
        assertFalse(model.moveCursor(1));
        assertFalse(model.moveCursor(0));
        assertEquals(4, model.caret());
        assertTrue(model.moveCursor(-40));
        assertEquals(0, model.caret());
        assertFalse(model.moveCursor(-1));
        assertTrue(model.moveCursor(40));
        assertEquals(4, model.caret());
    }

    @Test
    public void editsStepOverSurrogatePairsWhole() {
        AppDrawerSearchModel model = new AppDrawerSearchModel();
        model.insert("a" + CARD_INDEX + "b");
        assertEquals(4, model.length());
        assertEquals(4, model.caret());

        // One step back is one code point, not one char: the caret never lands inside the pair.
        assertTrue(model.moveCursor(-1));
        assertEquals(3, model.caret());
        assertTrue(model.moveCursor(-1));
        assertEquals(1, model.caret());

        model.moveCursor(1);
        assertTrue(model.backspace());
        assertEquals("ab", model.query());
        assertEquals(1, model.caret());
    }

    @Test
    public void replaceTakesTheWholeTextAndClampsTheCaret() {
        AppDrawerSearchModel model = new AppDrawerSearchModel();
        assertTrue(model.insert("te"));
        // What a keyboard reports after an autocorrection: a different word, caret at its end.
        assertTrue(model.replace("term", 4));
        assertEquals("term", model.query());
        assertEquals(4, model.caret());
        // The same text with the caret elsewhere is a caret move; out of range, it is clamped.
        assertTrue(model.replace("term", 2));
        assertEquals(2, model.caret());
        assertTrue(model.replace("term", 9));
        assertEquals(4, model.caret());
        // Neither text nor caret moved: not a change, so nothing is re-ranked.
        assertFalse(model.replace("term", 4));
        assertTrue(model.replace("", 0));
        assertTrue(model.isEmpty());
    }
}
