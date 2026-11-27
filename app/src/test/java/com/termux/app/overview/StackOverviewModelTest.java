package com.termux.app.overview;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.termux.app.tiling.Place;
import com.termux.app.tiling.TilingStack;
import com.termux.app.tiling.WindowKind;
import com.termux.app.tiling.WindowSpec;
import com.termux.app.tiling.WorkspaceLayout;

import org.junit.Test;

import java.util.List;

/** Overview's rows under the Terl switch (#430): the stack as rows, and what a drop does. */
public class StackOverviewModelTest {

    private static final WindowSpec SHELL = WindowSpec.shell("/home");

    // ---- rows ------------------------------------------------------------------------------------

    @Test
    public void oneRowForHomeThenEveryWorkspaceTopToBottomEndingWithTheWaitingOne() {
        TilingStack stack = TilingStack.firstStart(1, "/home");
        stack.openIn(Place.workspace(2), SHELL, false);

        List<StackOverviewModel.Row> rows = StackOverviewModel.of(stack).rows();

        assertEquals(4, rows.size());
        assertEquals(Place.home(0), rows.get(0).place);
        assertEquals(Place.workspace(1), rows.get(1).place);
        assertEquals(Place.workspace(2), rows.get(2).place);
        assertEquals(Place.workspace(3), rows.get(3).place);
        assertTrue(rows.get(3).waiting);
        assertTrue(rows.get(3).cards.isEmpty());
        assertFalse(rows.get(1).waiting);
    }

    @Test
    public void withoutHomeTheFirstRowIsWorkspaceOne() {
        TilingStack stack = TilingStack.firstStart(0, "/home");

        List<StackOverviewModel.Row> rows = StackOverviewModel.of(stack).rows();

        assertEquals(Place.workspace(1), rows.get(0).place);
        assertEquals(2, rows.size());
    }

    @Test
    public void eachWindowIsACardInItsWorkspaceOrderWithItsKindAndName() {
        TilingStack stack = TilingStack.firstStart(1, "/home");
        long shell = stack.workspace(1).windows().get(0).id();
        long app = stack.openIn(Place.workspace(1), WindowSpec.linuxApp("org.gimp", "GIMP"), false);
        long assistant = stack.openIn(Place.workspace(1), WindowSpec.assistant(), true);

        StackOverviewModel.Row row = StackOverviewModel.of(stack).rows().get(1);

        assertEquals(3, row.cards.size());
        assertEquals(shell, row.cards.get(0).windowId);
        assertEquals(WindowKind.SHELL, row.cards.get(0).kind);
        assertNull(row.cards.get(0).name);
        assertEquals(app, row.cards.get(1).windowId);
        assertEquals("GIMP", row.cards.get(1).name);
        assertEquals("org.gimp", row.cards.get(1).appId);
        assertEquals(assistant, row.cards.get(2).windowId);
        assertTrue("a Float stands after the tiles", row.cards.get(2).floating);
        assertEquals(-1, row.cards.get(2).group);
    }

    @Test
    public void thePlaceInFrontAndItsFocusedWindowAreMarked() {
        TilingStack stack = TilingStack.firstStart(1, "/home");
        long second = stack.openIn(Place.workspace(1), SHELL, false);
        stack.focusWindow(second);

        StackOverviewModel model = StackOverviewModel.of(stack);

        assertEquals(1, model.frontRow());
        assertTrue(model.rows().get(1).front);
        assertFalse(model.rows().get(0).front);
        assertFalse(model.rows().get(1).cards.get(0).front);
        assertTrue(model.rows().get(1).cards.get(1).front);
    }

    @Test
    public void onHomeNoWorkspaceCardIsInFront() {
        TilingStack stack = TilingStack.firstStart(1, "/home");

        StackOverviewModel model = StackOverviewModel.of(stack);

        assertEquals(0, model.frontRow());
        assertFalse(model.rows().get(1).cards.get(0).front);
    }

    @Test
    public void aWorkspaceRowCarriesItsNameAndLayoutAndHomeTakesNoWindows() {
        TilingStack stack = TilingStack.firstStart(1, "/home");
        stack.renameWorkspace(1, "build");
        stack.setLayout(1, WorkspaceLayout.DWINDLE);

        List<StackOverviewModel.Row> rows = StackOverviewModel.of(stack).rows();

        assertEquals("build", rows.get(1).name);
        assertEquals(WorkspaceLayout.DWINDLE, rows.get(1).layout);
        assertNull(rows.get(0).layout);
        assertFalse(rows.get(0).takesWindows);
        assertTrue(rows.get(1).takesWindows);
        assertTrue(rows.get(2).takesWindows);
    }

    @Test
    public void homeRowListsHomesFloats() {
        TilingStack stack = TilingStack.firstStart(1, "/home");
        long onHome = stack.openIn(Place.home(0), WindowSpec.assistant(), true);

        StackOverviewModel.Row home = StackOverviewModel.of(stack).rows().get(0);

        assertEquals(1, home.cards.size());
        assertEquals(onHome, home.cards.get(0).windowId);
        assertTrue(home.cards.get(0).floating);
        assertNull(home.cards.get(0).facts.root);
        assertTrue(home.cards.get(0).facts.floats.get(0).pane.active);
    }

    @Test
    public void tabsShareOneGroupAndSayTheyAreTabs() {
        TilingStack stack = TilingStack.firstStart(1, "/home");
        long first = stack.workspace(1).windows().get(0).id();
        long second = stack.openIn(Place.workspace(1), SHELL, false);
        stack.joinTab(second, first);

        StackOverviewModel.Row row = StackOverviewModel.of(stack).rows().get(1);

        assertEquals(row.cards.get(0).group, row.cards.get(1).group);
        assertTrue(row.cards.get(0).tabs);
        assertTrue(row.cards.get(1).tabs);
    }

    // ---- the card's picture ----------------------------------------------------------------------

    @Test
    public void scrollingColumnsStandSideBySideWithTheCardsOwnTileLit() {
        TilingStack stack = TilingStack.firstStart(1, "/home");
        long first = stack.workspace(1).windows().get(0).id();
        long second = stack.openIn(Place.workspace(1), SHELL, false);

        CardFacts.Workspace facts = StackOverviewModel.schematic(stack.workspace(1), second);

        CardFacts.Split split = (CardFacts.Split) facts.root;
        assertTrue(split.sideBySide);
        assertFalse(((CardFacts.Pane) split.a).active);
        assertTrue(((CardFacts.Pane) split.b).active);
        assertTrue(StackOverviewModel.schematic(stack.workspace(1), first).root instanceof CardFacts.Split);
    }

    @Test
    public void scrollingColumnsShareTheWidthEvenly() {
        TilingStack stack = TilingStack.firstStart(1, "/home");
        stack.openIn(Place.workspace(1), SHELL, false);
        stack.openIn(Place.workspace(1), SHELL, false);

        CardFacts.Split split = (CardFacts.Split) StackOverviewModel.schematic(stack.workspace(1), -1).root;

        // One of three on the left, two of three on the right, which halves again.
        assertEquals(1f, split.weightA, 0f);
        assertEquals(2f, split.weightB, 0f);
        CardFacts.Split rest = (CardFacts.Split) split.b;
        assertEquals(rest.weightA, rest.weightB, 0f);
    }

    @Test
    public void aStackedColumnIsOneWindowOverTheOther() {
        TilingStack stack = TilingStack.firstStart(1, "/home");
        long first = stack.workspace(1).windows().get(0).id();
        long second = stack.openIn(Place.workspace(1), SHELL, false);
        stack.stack(second, first);

        CardFacts.Split column = (CardFacts.Split) StackOverviewModel.schematic(stack.workspace(1), second).root;

        assertFalse(column.sideBySide);
        assertTrue(((CardFacts.Pane) column.b).active);
    }

    @Test
    public void tabsAreOneTileLitForEveryTab() {
        TilingStack stack = TilingStack.firstStart(1, "/home");
        long first = stack.workspace(1).windows().get(0).id();
        long second = stack.openIn(Place.workspace(1), SHELL, false);
        stack.joinTab(second, first);

        CardFacts.Node forFirst = StackOverviewModel.schematic(stack.workspace(1), first).root;
        CardFacts.Node forSecond = StackOverviewModel.schematic(stack.workspace(1), second).root;

        assertTrue(forFirst instanceof CardFacts.Pane);
        assertTrue(((CardFacts.Pane) forFirst).active);
        assertTrue(((CardFacts.Pane) forSecond).active);
    }

    @Test
    public void dwindleSpiralsSideBySideThenStacked() {
        TilingStack stack = TilingStack.firstStart(1, "/home");
        stack.setLayout(1, WorkspaceLayout.DWINDLE);
        stack.openIn(Place.workspace(1), SHELL, false);
        long third = stack.openIn(Place.workspace(1), SHELL, false);

        CardFacts.Split root = (CardFacts.Split) StackOverviewModel.schematic(stack.workspace(1), third).root;

        assertTrue(root.sideBySide);
        CardFacts.Split rest = (CardFacts.Split) root.b;
        assertFalse(rest.sideBySide);
        assertTrue(((CardFacts.Pane) rest.b).active);
    }

    @Test
    public void floatsStandOverTheTilesInACascadeOnTheCard() {
        TilingStack stack = TilingStack.firstStart(1, "/home");
        stack.openIn(Place.workspace(1), SHELL, true);
        long second = stack.openIn(Place.workspace(1), SHELL, true);

        CardFacts.Workspace facts = StackOverviewModel.schematic(stack.workspace(1), second);

        assertNotNull(facts.root);
        assertEquals(2, facts.floats.size());
        assertTrue(facts.floats.get(1).left > facts.floats.get(0).left);
        assertTrue(facts.floats.get(1).pane.active);
        for (CardFacts.Floating f : facts.floats) assertTrue(f.right <= 1f && f.bottom <= 1f);
    }

    @Test
    public void theWaitingWorkspaceIsDrawnEmpty() {
        TilingStack stack = TilingStack.firstStart(1, "/home");

        assertNull(StackOverviewModel.schematic(stack.workspace(2), -1).root);
    }

    // ---- what a drop does ------------------------------------------------------------------------

    @Test
    public void ontoAnotherCardMakesThemTabs() {
        TilingStack stack = TilingStack.firstStart(1, "/home");
        long first = stack.workspace(1).windows().get(0).id();
        long other = stack.openIn(Place.workspace(2), SHELL, false);
        StackOverviewModel model = StackOverviewModel.of(stack);

        OverviewDropPolicy.StackDrop drop = OverviewDropPolicy.stackDrop(model, other, 1, first);

        assertSame(OverviewDropPolicy.StackDrop.Type.TAB, drop.type);
        assertEquals(other, drop.windowId);
        assertEquals(first, drop.targetId);
    }

    @Test
    public void ontoACardOfItsOwnRowAlsoMakesTabs() {
        TilingStack stack = TilingStack.firstStart(1, "/home");
        long first = stack.workspace(1).windows().get(0).id();
        long second = stack.openIn(Place.workspace(1), SHELL, false);

        OverviewDropPolicy.StackDrop drop =
            OverviewDropPolicy.stackDrop(StackOverviewModel.of(stack), second, 1, first);

        assertSame(OverviewDropPolicy.StackDrop.Type.TAB, drop.type);
    }

    @Test
    public void ontoATabOfItsOwnTileChangesNothing() {
        TilingStack stack = TilingStack.firstStart(1, "/home");
        long first = stack.workspace(1).windows().get(0).id();
        long second = stack.openIn(Place.workspace(1), SHELL, false);
        stack.joinTab(second, first);

        OverviewDropPolicy.StackDrop drop =
            OverviewDropPolicy.stackDrop(StackOverviewModel.of(stack), second, 1, first);

        assertSame(OverviewDropPolicy.StackDrop.Type.NONE, drop.type);
    }

    @Test
    public void ontoAnotherRowsRoomMovesTheWindowThere() {
        TilingStack stack = TilingStack.firstStart(1, "/home");
        long first = stack.workspace(1).windows().get(0).id();
        stack.openIn(Place.workspace(2), SHELL, false);

        OverviewDropPolicy.StackDrop drop = OverviewDropPolicy.stackDrop(StackOverviewModel.of(stack),
            first, 2, TilingStack.NO_WINDOW);

        assertSame(OverviewDropPolicy.StackDrop.Type.MOVE, drop.type);
        assertEquals(Place.workspace(2), drop.place);
    }

    @Test
    public void ontoTheWaitingRowMovesItToANewWorkspace() {
        TilingStack stack = TilingStack.firstStart(1, "/home");
        long first = stack.workspace(1).windows().get(0).id();
        stack.openIn(Place.workspace(1), SHELL, false);

        OverviewDropPolicy.StackDrop drop = OverviewDropPolicy.stackDrop(StackOverviewModel.of(stack),
            first, 2, TilingStack.NO_WINDOW);

        assertSame(OverviewDropPolicy.StackDrop.Type.MOVE, drop.type);
        assertEquals(Place.workspace(2), drop.place);
        stack.moveWindow(drop.windowId, drop.place);
        assertEquals(Place.workspace(2), stack.placeOf(first));
        assertEquals(3, stack.workspaceCount());
    }

    @Test
    public void ontoAFloatsCardMovesToItsRow() {
        TilingStack stack = TilingStack.firstStart(1, "/home");
        long first = stack.workspace(1).windows().get(0).id();
        long floating = stack.openIn(Place.workspace(2), SHELL, true);

        OverviewDropPolicy.StackDrop drop =
            OverviewDropPolicy.stackDrop(StackOverviewModel.of(stack), first, -1, floating);

        assertSame(OverviewDropPolicy.StackDrop.Type.MOVE, drop.type);
        assertEquals(Place.workspace(2), drop.place);
    }

    @Test
    public void itsOwnRowItsOwnCardNowhereAndHomeChangeNothing() {
        TilingStack stack = TilingStack.firstStart(1, "/home");
        long first = stack.workspace(1).windows().get(0).id();
        long onHome = stack.openIn(Place.home(0), WindowSpec.assistant(), true);
        StackOverviewModel model = StackOverviewModel.of(stack);

        assertSame(OverviewDropPolicy.StackDrop.Type.NONE,
            OverviewDropPolicy.stackDrop(model, first, 1, TilingStack.NO_WINDOW).type);
        assertSame(OverviewDropPolicy.StackDrop.Type.NONE,
            OverviewDropPolicy.stackDrop(model, first, 1, first).type);
        assertSame(OverviewDropPolicy.StackDrop.Type.NONE,
            OverviewDropPolicy.stackDrop(model, first, -1, TilingStack.NO_WINDOW).type);
        assertSame(OverviewDropPolicy.StackDrop.Type.NONE,
            OverviewDropPolicy.stackDrop(model, first, 0, TilingStack.NO_WINDOW).type);
        assertSame("a Home Float's card takes no tab either", OverviewDropPolicy.StackDrop.Type.NONE,
            OverviewDropPolicy.stackDrop(model, first, -1, onHome).type);
        assertSame(OverviewDropPolicy.StackDrop.Type.NONE,
            OverviewDropPolicy.stackDrop(model, 999L, 2, TilingStack.NO_WINDOW).type);
    }

    @Test
    public void aHomeFloatCanBeMovedToAWorkspace() {
        TilingStack stack = TilingStack.firstStart(1, "/home");
        long onHome = stack.openIn(Place.home(0), WindowSpec.assistant(), true);

        OverviewDropPolicy.StackDrop drop = OverviewDropPolicy.stackDrop(StackOverviewModel.of(stack),
            onHome, 1, TilingStack.NO_WINDOW);

        assertSame(OverviewDropPolicy.StackDrop.Type.MOVE, drop.type);
        assertEquals(Place.workspace(1), drop.place);
    }
}
