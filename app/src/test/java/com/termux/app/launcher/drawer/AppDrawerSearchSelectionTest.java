package com.termux.app.launcher.drawer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class AppDrawerSearchSelectionTest {

    private static AppDrawerSearchSelection with(int count) {
        AppDrawerSearchSelection s = new AppDrawerSearchSelection();
        s.onResults(count, true, true);
        return s;
    }

    @Test public void freshQueryHighlightsFirst() {
        assertEquals(0, with(5).index());
    }

    @Test public void noQueryOrNoResultsHasNoHighlight() {
        AppDrawerSearchSelection s = with(5);
        assertTrue(s.onResults(5, false, true));
        assertEquals(AppDrawerSearchSelection.NONE, s.index());
        s.onResults(0, true, true);
        assertEquals(AppDrawerSearchSelection.NONE, s.index());
    }

    @Test public void queryChangeResetsToFirst() {
        AppDrawerSearchSelection s = with(5);
        s.moveBy(3);
        s.onResults(4, true, true);
        assertEquals(0, s.index());
    }

    @Test public void sameQueryKeepsPlaceClamped() {
        AppDrawerSearchSelection s = with(5);
        s.moveBy(4);
        s.onResults(3, true, false);
        assertEquals(2, s.index());
    }

    @Test public void stepsClampAtBothEnds() {
        AppDrawerSearchSelection s = with(3);
        assertFalse(s.moveBy(-1));
        assertTrue(s.moveBy(1));
        assertTrue(s.moveBy(1));
        assertFalse(s.moveBy(1));
        assertEquals(2, s.index());
    }

    @Test public void rowsMoveByColumnsAndLandOnShortLastRow() {
        AppDrawerSearchSelection s = with(10);
        s.moveBy(2);
        assertTrue(s.moveRows(1, 4));
        assertEquals(6, s.index());
        assertTrue(s.moveRows(1, 4));
        assertEquals(9, s.index());
        assertFalse(s.moveRows(1, 4));
        assertTrue(s.moveRows(-1, 4));
        assertEquals(5, s.index());
    }

    @Test public void upFromFirstRowStaysPut() {
        AppDrawerSearchSelection s = with(10);
        s.moveBy(2);
        assertTrue(s.moveRows(-1, 4));
        assertEquals(0, s.index());
        assertFalse(s.moveRows(-1, 4));
    }

    @Test public void clearRemovesHighlightAndMovesDoNothing() {
        AppDrawerSearchSelection s = with(3);
        assertTrue(s.clear());
        assertFalse(s.clear());
        assertFalse(s.moveBy(1));
    }
}
