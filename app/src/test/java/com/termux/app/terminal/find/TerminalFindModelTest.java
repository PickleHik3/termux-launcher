package com.termux.app.terminal.find;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TerminalFindModelTest {

    @Test public void typingSearchesLiveAndCountsEveryHit() {
        TerminalFindModel model = model("alpha beta", "beta gamma", "nothing here");
        model.typeText("b");
        model.typeText("e");
        model.typeText("t");
        model.typeText("a");

        assertEquals(2, model.matches().size());
        assertEquals("2/2", model.counter());
        // Newest first: the session opens on the last hit, which is where the eye already is.
        assertEquals(1, model.matches().get(model.currentIndex()).row);
    }

    @Test public void matchesCarryTheirExactColumnsSoTheOverlayCanLightThem() {
        TerminalFindModel model = model("xx needle xx");
        model.typeText("needle");

        TerminalFindModel.Match match = model.matches().get(0);
        assertEquals(3, match.startColumn);
        assertEquals(8, match.endColumn);
    }

    @Test public void searchIsCaseInsensitiveAndFindsRepeatsOnOneRow() {
        TerminalFindModel model = model("Log log LOG");
        model.typeText("log");

        assertEquals(3, model.matches().size());
        assertEquals("3/3", model.counter());
    }

    @Test public void stepWrapsBothWaysLikeVimsNAndShiftN() {
        TerminalFindModel model = model("hit", "hit", "hit");
        model.typeText("hit");
        model.commitQuery();
        assertEquals(2, model.currentIndex());

        model.command('n', false);
        assertEquals(0, model.currentIndex());
        model.command('N', false);
        assertEquals(2, model.currentIndex());
        model.command('N', false);
        assertEquals(1, model.currentIndex());
    }

    @Test public void arrowsWalkMatchesWhileTypingAndTheCursorAfterwards() {
        TerminalFindModel model = model("hit one", "hit two");
        model.typeText("hit");
        // While typing, n and N are still characters; stepping is what the arrows are for.
        assertEquals(TerminalFindModel.Result.IGNORED, model.command('n', false));
        model.step(-1);
        assertEquals(0, model.currentIndex());

        model.commitQuery();
        assertEquals(TerminalFindModel.Mode.NAVIGATE, model.mode());
        assertEquals(0, model.cursorRow());
        assertEquals(0, model.cursorColumn());
    }

    @Test public void motionsMoveTheCopyCursorAndStayInsideTheTranscript() {
        TerminalFindModel model = model("alpha beta", "second row");
        model.typeText("alpha");
        model.commitQuery();

        model.command('l', false);
        assertEquals(1, model.cursorColumn());
        model.command('w', false);
        assertEquals(6, model.cursorColumn());
        model.command('b', false);
        assertEquals(0, model.cursorColumn());
        model.command('$', false);
        assertEquals(9, model.cursorColumn());
        model.command('0', false);
        assertEquals(0, model.cursorColumn());

        model.command('k', false);
        assertEquals(0, model.cursorRow());
        model.command('G', false);
        assertEquals(1, model.cursorRow());
        model.command('g', false);
        model.command('g', false);
        assertEquals(0, model.cursorRow());
    }

    @Test public void charwiseSelectionYanksFromAnchorToCursorAcrossRows() {
        TerminalFindModel model = model("abcdef", "ghijkl");
        model.typeText("abc");
        model.commitQuery();
        model.command('v', false);
        model.command('j', false);
        model.command('l', false);
        model.command('l', false);

        assertEquals(TerminalFindModel.Selection.CHAR, model.selection());
        assertEquals("abcdef\nghi", model.selectedText());
    }

    @Test public void blockSelectionYanksTheSquareAndNothingBesideIt() {
        TerminalFindModel model = model("abcdef", "ghijkl", "mnopqr");
        model.typeText("abc");
        model.commitQuery();
        model.command('l', false);           // cursor at column 1
        model.command('v', true);            // Ctrl-V: block
        model.command('j', false);
        model.command('j', false);
        model.command('l', false);
        model.command('l', false);           // to column 3

        assertEquals(TerminalFindModel.Selection.BLOCK, model.selection());
        assertEquals("bcd\nhij\nnop", model.selectedText());
    }

    @Test public void linewiseSelectionTakesWholeRows() {
        TerminalFindModel model = model("first row", "second row");
        model.typeText("first");
        model.commitQuery();
        model.command('l', false);
        model.command('V', false);
        model.command('j', false);

        assertEquals("first row\nsecond row", model.selectedText());
    }

    @Test public void theSameSelectionKeyTwiceDropsTheSelection() {
        TerminalFindModel model = model("abcdef");
        model.typeText("abc");
        model.commitQuery();
        model.command('v', false);
        assertEquals(TerminalFindModel.Mode.SELECT, model.mode());
        model.command('v', false);
        assertEquals(TerminalFindModel.Selection.NONE, model.selection());
        assertEquals(TerminalFindModel.Mode.NAVIGATE, model.mode());
    }

    @Test public void yankEndsTheSessionAndCarriesTheText() {
        TerminalFindModel model = model("copy me   ", "other");
        model.typeText("copy");
        model.commitQuery();
        model.command('V', false);

        assertEquals(TerminalFindModel.Result.YANKED, model.command('y', false));
        // Trailing cell padding is not part of what the user pointed at.
        assertEquals("copy me", model.yankedText());
    }

    @Test public void escapeUnwindsSelectionThenNavigationThenTheSession() {
        TerminalFindModel model = model("abc");
        model.typeText("abc");
        model.commitQuery();
        model.command('v', false);

        assertEquals(TerminalFindModel.Result.HANDLED, model.escape());
        assertEquals(TerminalFindModel.Selection.NONE, model.selection());
        assertEquals(TerminalFindModel.Result.CLOSED, model.escape());
    }

    @Test public void backspacingPastAnEmptyQueryClosesTheSession() {
        TerminalFindModel model = model("abc");
        model.typeText("a");
        assertEquals(TerminalFindModel.Result.HANDLED, model.backspace());
        assertEquals("", model.query());
        assertEquals(TerminalFindModel.Result.CLOSED, model.backspace());
    }

    @Test public void slashReturnsToEditingTheQueryWithItIntact() {
        TerminalFindModel model = model("abc");
        model.typeText("ab");
        model.commitQuery();
        model.command('/', false);

        assertEquals(TerminalFindModel.Mode.TYPING, model.mode());
        assertEquals("ab", model.query());
    }

    @Test public void aQueryWithNoHitsCountsZeroAndFocusesNothing() {
        TerminalFindModel model = model("alpha");
        model.typeText("zzz");

        assertEquals("0/0", model.counter());
        assertTrue(model.matches().isEmpty());
        assertNull(model.focusRow());
        // Stepping an empty result set must not throw or move anything.
        assertEquals(TerminalFindModel.Result.HANDLED, model.step(1));
    }

    @Test public void rowsKeepTheirTranscriptCoordinatesIncludingNegativeOnes() {
        List<TerminalFindModel.Line> lines = new ArrayList<>();
        lines.add(new TerminalFindModel.Line(-2, "old hit"));
        lines.add(new TerminalFindModel.Line(-1, "quiet"));
        lines.add(new TerminalFindModel.Line(0, "new hit"));
        TerminalFindModel model = new TerminalFindModel(lines);
        model.typeText("hit");

        assertEquals(-2, model.matches().get(0).row);
        assertEquals(0, model.matches().get(1).row);
        assertEquals(Integer.valueOf(0), model.focusRow());
        model.commitQuery();
        model.command('n', false);
        assertEquals(-2, model.cursorRow());
    }

    private static TerminalFindModel model(String... rows) {
        List<TerminalFindModel.Line> lines = new ArrayList<>();
        int row = 0;
        for (String text : Arrays.asList(rows)) lines.add(new TerminalFindModel.Line(row++, text));
        return new TerminalFindModel(lines);
    }
}
