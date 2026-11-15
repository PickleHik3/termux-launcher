package com.termux.app.wall.switcher;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.termux.app.wall.switcher.PickerModel.Action;
import com.termux.app.wall.switcher.PickerModel.Cell;
import com.termux.app.wall.switcher.PickerModel.CellKind;
import com.termux.app.wall.switcher.PickerModel.Column;
import com.termux.app.wall.switcher.PickerModel.Input;
import com.termux.app.wall.switcher.PickerModel.Kind;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

public class PickerModelTest {

    /** A wall with two Home pages, a Terminal, a Display and an Assistant page, on Home 1. */
    private static Input wall() {
        Input in = new Input();
        in.homePageIds = Arrays.asList(1, 2);
        in.defaultStartPageId = 1;
        in.terminalPageId = 3;
        in.displayPageId = 4;
        in.assistantPageId = 5;
        in.currentKind = Kind.HOME;
        in.currentPageId = 1;
        return in;
    }

    private static PickerModel build(Input in) {
        return PickerModel.build(in);
    }

    @Test public void alwaysFourKindsInOrder() {
        PickerModel model = build(wall());
        assertEquals(4, model.columns().size());
        assertSame(Kind.HOME, model.columns().get(0).kind);
        assertSame(Kind.TERMINAL, model.columns().get(1).kind);
        assertSame(Kind.DISPLAY, model.columns().get(2).kind);
        assertSame(Kind.ASSISTANT, model.columns().get(3).kind);
        for (Column c : model.columns()) assertSame(CellKind.GLYPH, c.glyph.kind);
    }

    @Test public void assistantHasNoColumnAndTwoHomePagesMakeAStack() {
        Input in = wall();
        in.terminalWindowLabels = Arrays.asList("zsh", "vim");
        PickerModel model = build(in);
        assertEquals(2, model.column(Kind.HOME).cells.size());
        assertTrue(model.column(Kind.ASSISTANT).cells.isEmpty());
    }

    @Test public void oneHomePageHasNoColumn() {
        Input in = wall();
        in.homePageIds = Collections.singletonList(1);
        assertTrue(build(in).column(Kind.HOME).cells.isEmpty());
    }

    @Test public void homePagesStackInOrderWithTheCurrentOneClosing() {
        Input in = wall();
        in.homePageIds = Arrays.asList(1, 2, 3);
        in.homePageLabels = Arrays.asList("Work", "", null);
        in.currentPageId = 2;
        Column col = build(in).column(Kind.HOME);
        assertEquals(3, col.cells.size());
        assertEquals("Work", col.cells.get(0).label);
        assertEquals("Home 2", col.cells.get(1).label);
        assertEquals("Home 3", col.cells.get(2).label);
        assertTrue(col.cells.get(1).current);
        assertSame(Action.CLOSE, col.cells.get(1).target.action);
        assertFalse(col.cells.get(0).current);
        assertSame(Action.GO_PAGE, col.cells.get(2).target.action);
        assertEquals(3, col.cells.get(2).target.pageId);
        assertSame(CellKind.WINDOW, col.cells.get(0).kind);
    }

    @Test public void homeOverflowOpensThePageOverview() {
        Input in = wall();
        in.homePageIds = Arrays.asList(1, 2, 3, 4, 5, 6);
        in.capacity = 3;
        Column col = build(in).column(Kind.HOME);
        assertEquals(3, col.cells.size());
        Cell top = col.cells.get(2);
        assertSame(CellKind.OVERFLOW, top.kind);
        assertEquals("+4", top.label);
        assertSame(Action.OPEN_PAGE_OVERVIEW, top.target.action);
    }

    // ---- Home target ----

    @Test public void homeGoesToTheStartPageWhenItIsAHomePage() {
        Input in = wall();
        in.defaultStartPageId = 2;
        Cell home = build(in).column(Kind.HOME).glyph;
        assertSame(Action.GO_PAGE, home.target.action);
        assertEquals(2, home.target.pageId);
    }

    @Test public void homeGoesToTheOnlyHomePage() {
        Input in = wall();
        in.homePageIds = Collections.singletonList(7);
        in.defaultStartPageId = 3; // the Terminal page
        assertEquals(7, build(in).column(Kind.HOME).glyph.target.pageId);
    }

    @Test public void homeGoesToHomeOneWhenTheStartPageIsNotHome() {
        Input in = wall();
        in.defaultStartPageId = 5; // Assistant
        assertEquals(1, build(in).column(Kind.HOME).glyph.target.pageId);
        in.currentPageId = 2;
        assertEquals(1, build(in).column(Kind.HOME).glyph.target.pageId);
    }

    @Test public void noHomePageStillGoesHome() {
        Input in = wall();
        in.homePageIds = Collections.emptyList();
        Cell home = build(in).column(Kind.HOME).glyph;
        assertSame(Action.GO_HOME, home.target.action);
    }

    @Test public void homeOnItsTargetPageJustCloses() {
        Cell home = build(wall()).column(Kind.HOME).glyph;
        assertTrue(home.current);
        assertSame(Action.CLOSE, home.target.action);
    }

    @Test public void homeGlyphIsLitOnAnotherHomePageButStillGoesToTheTarget() {
        Input in = wall();
        in.currentPageId = 2;
        Cell home = build(in).column(Kind.HOME).glyph;
        assertTrue(home.current);
        assertSame(Action.GO_PAGE, home.target.action);
        assertEquals(1, home.target.pageId);
    }

    // ---- Terminal ----

    @Test public void terminalWindowsStackInOrderWithTheCurrentOneMarked() {
        Input in = wall();
        in.currentKind = Kind.TERMINAL;
        in.currentPageId = 3;
        in.terminalWindowLabels = Arrays.asList("zsh", "vim", "htop");
        in.currentTerminalWindow = 1;
        Column col = build(in).column(Kind.TERMINAL);
        assertEquals(3, col.cells.size());
        assertEquals("zsh", col.cells.get(0).label);
        assertFalse(col.cells.get(0).current);
        assertTrue(col.cells.get(1).current);
        assertSame(Action.CLOSE, col.cells.get(1).target.action);
        assertSame(Action.GO_TERMINAL, col.cells.get(2).target.action);
        assertEquals(2, col.cells.get(2).target.index);
        assertTrue(col.glyph.current);
        assertSame(Action.CLOSE, col.glyph.target.action);
    }

    @Test public void windowsWithTheSameNameCarryTheirWindowNumber() {
        Input in = wall();
        in.terminalWindowLabels = Arrays.asList("home", "vim", "home", "\uE795 home", "vim ", "zsh");
        Column col = build(in).column(Kind.TERMINAL);
        assertEquals("home 1", col.cells.get(0).label);
        assertEquals("vim 2", col.cells.get(1).label);
        assertEquals("home 3", col.cells.get(2).label);
        assertEquals("home 4", col.cells.get(3).label);
        assertEquals("vim 5", col.cells.get(4).label);
        assertEquals("zsh", col.cells.get(5).label);
    }

    @Test public void terminalGlyphGoesToTheTerminalAtItsCurrentWindow() {
        Input in = wall();
        in.terminalWindowLabels = Arrays.asList("zsh", "vim");
        Cell glyph = build(in).column(Kind.TERMINAL).glyph;
        assertFalse(glyph.current);
        assertSame(Action.GO_TERMINAL, glyph.target.action);
        assertEquals(-1, glyph.target.index);
    }

    @Test public void noTerminalWindowIsMarkedOffTheTerminal() {
        Input in = wall();
        in.terminalWindowLabels = Arrays.asList("zsh", "vim");
        in.currentTerminalWindow = 0;
        for (Cell c : build(in).column(Kind.TERMINAL).cells) assertFalse(c.current);
    }

    // ---- Display ----

    @Test public void displayNotRunningHasOneStartCell() {
        Input in = wall();
        in.displayRunning = false;
        Column col = build(in).column(Kind.DISPLAY);
        assertEquals(1, col.cells.size());
        assertSame(CellKind.START_DISPLAY, col.cells.get(0).kind);
        assertSame(Action.START_DISPLAY, col.cells.get(0).target.action);
        assertEquals("Start display", col.cells.get(0).label);
    }

    @Test public void displayRunningListsItsApps() {
        Input in = wall();
        in.displayRunning = true;
        in.displayAppLabels = Arrays.asList("Firefox", "GIMP");
        in.displayAppIds = Arrays.asList(100, 200);
        in.currentKind = Kind.DISPLAY;
        in.currentPageId = 4;
        in.currentDisplayApp = 200;
        Column col = build(in).column(Kind.DISPLAY);
        assertEquals(2, col.cells.size());
        assertSame(Action.GO_DISPLAY_APP, col.cells.get(0).target.action);
        assertEquals(100, col.cells.get(0).target.index);
        assertEquals(4, col.cells.get(0).target.pageId);
        assertTrue(col.cells.get(1).current);
        assertSame(Action.CLOSE, col.cells.get(1).target.action);
    }

    @Test public void displayRunningWithNoAppsHasNoCells() {
        Input in = wall();
        in.displayRunning = true;
        assertTrue(build(in).column(Kind.DISPLAY).cells.isEmpty());
    }

    @Test public void displayGlyphGoesToTheLastUsedDisplayPage() {
        Cell glyph = build(wall()).column(Kind.DISPLAY).glyph;
        assertFalse(glyph.dimmed);
        assertSame(Action.GO_PAGE, glyph.target.action);
        assertEquals(4, glyph.target.pageId);
    }

    @Test public void missingDisplayPageIsDimmedAndAddsOne() {
        Input in = wall();
        in.displayPageId = -1;
        Cell glyph = build(in).column(Kind.DISPLAY).glyph;
        assertTrue(glyph.dimmed);
        assertSame(Action.ADD_DISPLAY, glyph.target.action);
    }

    @Test public void displayThatCannotBeUsedIsDimmedAndEmpty() {
        Input in = wall();
        in.displayAvailable = false;
        Column col = build(in).column(Kind.DISPLAY);
        assertTrue(col.glyph.dimmed);
        assertSame(Action.CLOSE, col.glyph.target.action);
        assertTrue(col.cells.isEmpty());
    }

    // ---- Assistant ----

    @Test public void assistantGlyphGoesToItsPage() {
        Cell glyph = build(wall()).column(Kind.ASSISTANT).glyph;
        assertFalse(glyph.dimmed);
        assertSame(Action.GO_PAGE, glyph.target.action);
        assertEquals(5, glyph.target.pageId);
    }

    @Test public void missingAssistantPageIsDimmedAndAddsOne() {
        Input in = wall();
        in.assistantPageId = -1;
        Cell glyph = build(in).column(Kind.ASSISTANT).glyph;
        assertTrue(glyph.dimmed);
        assertSame(Action.ADD_ASSISTANT, glyph.target.action);
    }

    @Test public void onTheAssistantPageItIsLitAndCloses() {
        Input in = wall();
        in.currentKind = Kind.ASSISTANT;
        in.currentPageId = 5;
        Cell glyph = build(in).column(Kind.ASSISTANT).glyph;
        assertTrue(glyph.current);
        assertSame(Action.CLOSE, glyph.target.action);
    }

    // ---- Overflow ----

    private static Input terminalWith(int windows, int capacity, int current) {
        Input in = wall();
        String[] labels = new String[windows];
        for (int i = 0; i < windows; i++) labels[i] = "w" + i;
        in.terminalWindowLabels = Arrays.asList(labels);
        in.capacity = capacity;
        in.currentKind = Kind.TERMINAL;
        in.currentPageId = 3;
        in.currentTerminalWindow = current;
        return in;
    }

    @Test public void aColumnThatFitsHasNoOverflow() {
        Column col = build(terminalWith(4, 4, 0)).column(Kind.TERMINAL);
        assertEquals(4, col.cells.size());
        for (Cell c : col.cells) assertSame(CellKind.WINDOW, c.kind);
    }

    @Test public void aFullColumnGivesItsTopCellToPlusN() {
        Column col = build(terminalWith(7, 4, 0)).column(Kind.TERMINAL);
        assertEquals(4, col.cells.size());
        Cell top = col.cells.get(3);
        assertSame(CellKind.OVERFLOW, top.kind);
        assertEquals(4, top.overflowCount);
        assertEquals("+4", top.label);
        assertSame(Action.OPEN_TERMINAL_LIST, top.target.action);
        assertEquals("w0", col.cells.get(0).label);
        assertEquals("w2", col.cells.get(2).label);
    }

    @Test public void overflowKeepsTheCurrentWindowInView() {
        Column col = build(terminalWith(10, 4, 8)).column(Kind.TERMINAL);
        assertEquals(4, col.cells.size());
        boolean found = false;
        for (Cell c : col.cells) if (c.current) found = true;
        assertTrue(found);
        Cell top = col.cells.get(3);
        assertSame(CellKind.OVERFLOW, top.kind);
        assertEquals(7, top.overflowCount);
    }

    @Test public void overflowWithRoomForOneCellIsJustPlusN() {
        Column col = build(terminalWith(3, 1, 0)).column(Kind.TERMINAL);
        assertEquals(1, col.cells.size());
        assertEquals(3, col.cells.get(0).overflowCount);
    }

    @Test public void noRoomMeansNoCells() {
        Column col = build(terminalWith(3, 0, 0)).column(Kind.TERMINAL);
        assertTrue(col.cells.isEmpty());
    }

    @Test public void displayAppsOverflowToTheirOwnList() {
        Input in = wall();
        in.displayRunning = true;
        in.displayAppLabels = Arrays.asList("a", "b", "c", "d");
        in.displayAppIds = Arrays.asList(1, 2, 3, 4);
        in.capacity = 3;
        Column col = build(in).column(Kind.DISPLAY);
        assertEquals(3, col.cells.size());
        assertSame(Action.OPEN_DISPLAY_LIST, col.cells.get(2).target.action);
        assertEquals(2, col.cells.get(2).overflowCount);
    }

    @Test public void tallestColumnIsTheMostCellsAboveAGlyph() {
        Input in = terminalWith(3, 9, 0);
        assertEquals(3, build(in).tallestColumn());
    }

    @Test public void windowLabelsLoseTheirIconCodePoints() {
        assertEquals("home", PickerModel.plainLabel("\uf015 home"));
        assertEquals("zsh", PickerModel.plainLabel(new String(Character.toChars(0xF0A00)) + " zsh"));
        assertEquals("plain", PickerModel.plainLabel("plain"));
        assertEquals("\uf015", PickerModel.plainLabel("\uf015"));
        Input in = wall();
        in.terminalWindowLabels = Arrays.asList("\uf120 vim");
        assertEquals("vim", build(in).column(Kind.TERMINAL).cells.get(0).label);
    }

    @Test public void kindNamesComeFromTheInput() {
        Input in = wall();
        in.kindNames = new String[] {"A", "B", "C", "D"};
        PickerModel model = build(in);
        assertEquals("B", model.column(Kind.TERMINAL).glyph.label);
        assertNotNull(model.column(Kind.ASSISTANT));
        assertNull(null);
    }
}
