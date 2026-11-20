package com.termux.app.wall.pageswitch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.app.page.PageKind;
import com.termux.app.wall.pageswitch.PageBarFold.Slot;
import com.termux.app.wall.pageswitch.PageBarModel.Entry;
import com.termux.app.wall.pageswitch.PageBarModel.Input;

import org.junit.Test;

import java.util.List;

public class PageBarFoldTest {

    private static List<Entry> entries(String kinds, int windows) {
        return PageBarModel.build(PageBarModelTest.wall(kinds, windows)).entries();
    }

    /** Slot sizes, e.g. "1,1,3". */
    private static String shape(PageBarFold.Result r) {
        StringBuilder sb = new StringBuilder();
        for (Slot s : r.slots) sb.append(sb.length() == 0 ? "" : ",").append(s.count);
        return sb.toString();
    }

    @Test public void fittingEntriesStayOneToASlot() {
        PageBarFold.Result r = PageBarFold.fold(entries("HHT", 3), 6);
        assertEquals("1,1,1,1,1", shape(r));
        assertFalse(r.overflow);
        assertFalse(r.slots.get(2).folded());
    }

    @Test public void windowsFoldFirstEvenWhenShorterThanAnotherRun() {
        PageBarFold.Result r = PageBarFold.fold(entries("HHHHT", 2), 5);
        assertEquals("1,1,1,1,2", shape(r));
        assertEquals(PageKind.TERMINAL, r.slots.get(4).kind);
        assertFalse(r.overflow);
    }

    @Test public void thenTheLongestRunFolds() {
        PageBarFold.Result r = PageBarFold.fold(entries("HHHHT", 3), 4);
        assertEquals("4,3", shape(r));
        assertFalse(r.overflow);
    }

    @Test public void tieFoldsTheRightmostRun() {
        PageBarFold.Result r = PageBarFold.fold(entries("HHDD", 0), 3);
        assertEquals("1,1,2", shape(r));
        assertEquals(PageKind.DISPLAY, r.slots.get(2).kind);
    }

    @Test public void aRunOfOneNeverFolds() {
        PageBarFold.Result r = PageBarFold.fold(entries("HTD", 1), 1);
        assertEquals("1,1,1", shape(r));
        assertTrue(r.overflow);
    }

    @Test public void overflowIsReportedWhenEveryRunIsFolded() {
        PageBarFold.Result r = PageBarFold.fold(entries("HHTHH", 2), 2);
        assertEquals("2,2,2", shape(r));
        assertTrue(r.overflow);
    }

    @Test public void foldedSlotShowsCountAndKnowsItsCurrentMember() {
        Input in = PageBarModelTest.wall("HT", 3);
        in.currentPageId = 2;
        in.currentWindow = 2;
        List<Entry> e = PageBarModel.build(in).entries();
        PageBarFold.Result r = PageBarFold.fold(e, 2);
        Slot t = r.slots.get(1);
        assertEquals(3, t.number);
        assertTrue(t.current);
        assertEquals(2, t.currentMember);
        assertEquals(1, t.first);
        assertFalse(r.slots.get(0).current);
    }

    @Test public void unfoldedSlotKeepsItsEntryNumber() {
        PageBarFold.Result r = PageBarFold.fold(entries("HH", 0), 5);
        assertEquals(2, r.slots.get(1).number);
        assertEquals(-1, r.slots.get(1).currentMember);
    }
}
