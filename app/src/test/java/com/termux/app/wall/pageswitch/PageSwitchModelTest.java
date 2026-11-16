package com.termux.app.wall.pageswitch;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.termux.app.wall.pageswitch.PageSwitchModel.Action;
import com.termux.app.wall.pageswitch.PageSwitchModel.Cell;
import com.termux.app.wall.pageswitch.PageSwitchModel.Input;
import com.termux.app.wall.pageswitch.PageSwitchModel.Kind;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class PageSwitchModelTest {

    /** Two Home pages (the second named Work), a Terminal with two windows, Assistant and a running Display, on Home 1. */
    private static Input wall() {
        Input in = new Input();
        in.homePageIds = Arrays.asList(1, 2);
        in.homePageLabels = Arrays.asList(null, "Work");
        in.defaultStartPageId = 1;
        in.terminalPageId = 3;
        in.terminalWindowLabels = Arrays.asList("bash", "htop");
        in.assistantPageId = 5;
        in.displayPageId = 4;
        in.displayRunning = true;
        in.displayTitle = "XFCE";
        in.currentKind = Kind.HOME;
        in.currentPageId = 1;
        return in;
    }

    private static List<Cell> cells(Input in) {
        return PageSwitchModel.build(in).cells();
    }

    private static String names(Input in) {
        StringBuilder sb = new StringBuilder();
        for (Cell c : cells(in)) sb.append(sb.length() == 0 ? "" : "|").append(c.fullName);
        return sb.toString();
    }

    @Test public void rowOrderPutsAssistantBeforeDisplay() {
        assertEquals("Home 1|Home 2 · Work|Terminal 1 · bash|Terminal 2 · htop"
            + "|Assistant|Display · XFCE", names(wall()));
    }

    @Test public void terminalOnlyDropsHome() {
        Input in = wall();
        in.homeOffered = false;
        in.currentKind = Kind.TERMINAL;
        in.currentPageId = 3;
        in.currentTerminalWindow = 0;
        PageSwitchModel m = PageSwitchModel.build(in);
        assertFalse(m.shows(Kind.HOME));
        assertEquals(4, m.cells().size());
        assertEquals(Kind.TERMINAL, m.cells().get(0).kind);
    }

    @Test public void assistantOffDropsItsCell() {
        Input in = wall();
        in.assistantPageId = -1;
        PageSwitchModel m = PageSwitchModel.build(in);
        assertFalse(m.shows(Kind.ASSISTANT));
        assertEquals(5, m.cells().size());
        assertEquals(Kind.DISPLAY, m.cells().get(4).kind);
    }

    @Test public void aDisplayThatIsNotRunningDropsItsCell() {
        Input in = wall();
        in.displayRunning = false;
        PageSwitchModel m = PageSwitchModel.build(in);
        assertFalse(m.shows(Kind.DISPLAY));
        assertEquals(Kind.ASSISTANT, m.cells().get(m.cells().size() - 1).kind);
    }

    @Test public void fullNamesCarryKindNumberAndTitle() {
        Input in = wall();
        in.displayTitle = null;
        in.terminalWindowLabels = Arrays.asList(" htop");
        List<Cell> cells = cells(in);
        assertEquals("Home 1", cells.get(0).fullName);
        assertNull(cells.get(0).title);
        assertEquals("Home 2 · Work", cells.get(1).fullName);
        assertEquals("Work", cells.get(1).title);
        assertEquals("Terminal 1 · htop", cells.get(2).fullName);
        assertEquals("Assistant", cells.get(3).fullName);
        assertEquals("Display", cells.get(4).fullName);
        assertEquals(0, cells.get(3).number);
    }

    @Test public void kindNamesComeFromTheInput() {
        Input in = wall();
        in.kindNames = new String[] {"H", "T", "A", "D"};
        assertEquals("H 1", cells(in).get(0).fullName);
        assertEquals("A", cells(in).get(4).fullName);
    }

    @Test public void theCurrentCellClosesAndTheOthersGo() {
        Input in = wall();
        PageSwitchModel m = PageSwitchModel.build(in);
        assertEquals(0, m.currentIndex());
        assertTrue(m.cells().get(0).current);
        assertEquals(Action.CLOSE, m.cells().get(0).target.action);
        assertEquals(Action.GO_PAGE, m.cells().get(1).target.action);
        assertEquals(2, m.cells().get(1).target.pageId);
        assertEquals(Action.GO_TERMINAL, m.cells().get(3).target.action);
        assertEquals(3, m.cells().get(3).target.pageId);
        assertEquals(1, m.cells().get(3).target.index);
        assertEquals(Action.GO_PAGE, m.cells().get(4).target.action);
        assertEquals(5, m.cells().get(4).target.pageId);
        assertEquals(4, m.cells().get(5).target.pageId);
    }

    @Test public void onTerminalOnlyTheCurrentWindowIsCurrent() {
        Input in = wall();
        in.currentKind = Kind.TERMINAL;
        in.currentPageId = 3;
        in.currentTerminalWindow = 1;
        PageSwitchModel m = PageSwitchModel.build(in);
        assertEquals(3, m.currentIndex());
        assertEquals(Action.GO_TERMINAL, m.cells().get(2).target.action);
        assertEquals(Action.CLOSE, m.cells().get(3).target.action);
        assertFalse(m.cells().get(0).current);
    }

    @Test public void aTerminalWithNoWindowsHasOneCell() {
        Input in = wall();
        in.terminalWindowLabels = Collections.emptyList();
        PageSwitchModel m = PageSwitchModel.build(in);
        Cell cell = m.cells().get(2);
        assertEquals("Terminal 1", cell.fullName);
        assertEquals(Action.GO_TERMINAL, cell.target.action);
        assertEquals(-1, cell.target.index);
        in.currentKind = Kind.TERMINAL;
        in.currentPageId = 3;
        PageSwitchModel on = PageSwitchModel.build(in);
        assertEquals(Action.CLOSE, on.cells().get(2).target.action);
        assertEquals(2, on.currentIndex());
    }

    @Test public void assistantAndDisplayCloseWhenCurrent() {
        Input in = wall();
        in.currentKind = Kind.ASSISTANT;
        in.currentPageId = 5;
        assertEquals(Action.CLOSE, cells(in).get(4).target.action);
        in.currentKind = Kind.DISPLAY;
        in.currentPageId = 4;
        PageSwitchModel m = PageSwitchModel.build(in);
        assertEquals(Action.CLOSE, m.cells().get(5).target.action);
        assertEquals(5, m.currentIndex());
    }

    @Test public void noCurrentCellGivesMinusOne() {
        Input in = wall();
        in.currentPageId = 99;
        assertEquals(-1, PageSwitchModel.build(in).currentIndex());
    }

    @Test public void numberOfReadsTheCurrentHomePageAndWindow() {
        Input in = wall();
        in.currentPageId = 2;
        assertEquals(2, PageSwitchModel.build(in).numberOf(Kind.HOME));
        assertEquals(0, PageSwitchModel.build(in).numberOf(Kind.TERMINAL));
        assertEquals(0, PageSwitchModel.build(in).numberOf(Kind.ASSISTANT));
        assertEquals(0, PageSwitchModel.build(in).numberOf(Kind.DISPLAY));
        in.currentKind = Kind.TERMINAL;
        in.currentPageId = 3;
        in.currentTerminalWindow = 1;
        PageSwitchModel m = PageSwitchModel.build(in);
        assertEquals(2, m.numberOf(Kind.TERMINAL));
        // Off Home the glyph holds the Home page it goes to: the one the app starts on.
        assertEquals(1, m.numberOf(Kind.HOME));
        in.defaultStartPageId = 2;
        assertEquals(2, PageSwitchModel.build(in).numberOf(Kind.HOME));
        in.homePageIds = Collections.emptyList();
        assertEquals(0, PageSwitchModel.build(in).numberOf(Kind.HOME));
    }

    @Test public void terminalGlyphHoldsItsWindowWhileHomeIsInFront() {
        Input in = wall();
        in.terminalWindowLabels = Arrays.asList("a", "b", "c");
        in.currentTerminalWindow = 2;
        PageSwitchModel m = PageSwitchModel.build(in);
        assertEquals(1, m.numberOf(Kind.HOME));
        assertEquals(3, m.numberOf(Kind.TERMINAL));
        // Empty only when there really are no windows.
        in.currentTerminalWindow = -1;
        assertEquals(0, PageSwitchModel.build(in).numberOf(Kind.TERMINAL));
    }

    @Test public void homeTargetPrefersTheStartPageElseTheFirst() {
        assertEquals(2, PageSwitchModel.homeTarget(Arrays.asList(1, 2), 2));
        assertEquals(1, PageSwitchModel.homeTarget(Arrays.asList(1, 2), 9));
        assertEquals(-1, PageSwitchModel.homeTarget(Collections.<Integer>emptyList(), 1));
    }

    @Test public void plainLabelDropsIconCodePointsButNeverEmpties() {
        assertEquals("htop", PageSwitchModel.plainLabel(" htop"));
        assertEquals("", PageSwitchModel.plainLabel(""));
    }
}
