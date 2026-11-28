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
    public void aWorkspaceRowCarriesItsNameAndLayoutAndEveryRowTakesWindows() {
        TilingStack stack = TilingStack.firstStart(1, "/home");
        stack.renameWorkspace(1, "build");
        stack.setLayout(1, WorkspaceLayout.DWINDLE);

        List<StackOverviewModel.Row> rows = StackOverviewModel.of(stack).rows();

        assertEquals("build", rows.get(1).name);
        assertEquals(WorkspaceLayout.DWINDLE, rows.get(1).layout);
        assertNull(rows.get(0).layout);
        assertTrue("Home's, as Floats", rows.get(0).takesWindows);
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
    public void aMinimisedWindowHasNoColumnOnTheCard() {
        TilingStack stack = TilingStack.firstStart(1, "/home");
        long first = stack.workspace(1).windows().get(0).id();
        long second = stack.openIn(Place.workspace(1), SHELL, false);
        stack.setMinimised(second, true);

        CardFacts.Node root = StackOverviewModel.schematic(stack.workspace(1), first).root;

        assertTrue(root instanceof CardFacts.Pane);
        assertTrue(((CardFacts.Pane) root).active);
    }

    @Test
    public void aMaximisedWindowInFocusFillsTheCard() {
        TilingStack stack = TilingStack.firstStart(1, "/home");
        stack.openIn(Place.workspace(1), SHELL, false);
        long second = stack.workspace(1).focusedWindow().id();
        stack.setMaximised(second, true);

        CardFacts.Node root = StackOverviewModel.schematic(stack.workspace(1), second).root;

        assertTrue(root instanceof CardFacts.Pane);
        assertTrue(((CardFacts.Pane) root).active);
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
    public void dwindleDrawsTheWorkspacesOwnTreeTwoTilesStackedInPortrait() {
        TilingStack stack = TilingStack.firstStart(1, "/home");
        stack.setLayout(1, WorkspaceLayout.DWINDLE);
        long second = stack.openIn(Place.workspace(1), SHELL, false);

        CardFacts.Split root = (CardFacts.Split) StackOverviewModel.schematic(stack.workspace(1), second).root;

        assertFalse("a portrait tile splits along its longer side", root.sideBySide);
        assertTrue(((CardFacts.Pane) root.b).active);
    }

    @Test
    public void dwindleKeepsTheTreesOwnRatiosAndSplits() {
        TilingStack stack = TilingStack.firstStart(1, "/home");
        stack.setLayout(1, WorkspaceLayout.DWINDLE);
        stack.openIn(Place.workspace(1), SHELL, false);
        long third = stack.openIn(Place.workspace(1), SHELL, false);
        com.termux.app.tiling.DwindleTree tree = com.termux.app.tiling.DwindleTree.empty();
        tree.sync(com.termux.app.tiling.DwindleTree.tilesOf(stack.workspace(1)), 1000f, 1600f, 0f);
        tree.setRatio(0, 0.3f);
        stack.setLayoutState(1, tree.encode());

        CardFacts.Split root = (CardFacts.Split) StackOverviewModel.schematic(stack.workspace(1), third).root;

        assertEquals(0.3f, root.weightA / (root.weightA + root.weightB), 0.01f);
        CardFacts.Split rest = (CardFacts.Split) root.b;
        assertTrue("the second split crosses the first", rest.sideBySide != root.sideBySide);
        assertTrue(((CardFacts.Pane) rest.b).active);
    }

    @Test
    public void aTabbedDwindleTileIsOnePaneLitForEachTab() {
        TilingStack stack = TilingStack.firstStart(1, "/home");
        long first = stack.workspace(1).windows().get(0).id();
        stack.setLayout(1, WorkspaceLayout.DWINDLE);
        long second = stack.openIn(Place.workspace(1), SHELL, false);
        stack.joinTab(second, first);

        CardFacts.Node root = StackOverviewModel.schematic(stack.workspace(1), first).root;

        assertTrue(root instanceof CardFacts.Pane);
        assertTrue(((CardFacts.Pane) root).active);
    }

    @Test
    public void roomsNoStraightCutSeparatesShareTheRoomEvenly() {
        // A pinwheel of four rooms around a fifth: no cut crosses the whole room.
        List<StackOverviewModel.Room> rooms = java.util.Arrays.asList(
            room(0, 0, 60, 30), room(60, 0, 100, 60), room(40, 60, 100, 100), room(0, 30, 40, 100),
            room(40, 30, 60, 60));

        CardFacts.Node node = StackOverviewModel.guillotine(rooms, 0, 0, 100, 100);

        assertTrue(node instanceof CardFacts.Split);
        assertTrue(((CardFacts.Split) node).sideBySide);
    }

    private static StackOverviewModel.Room room(float l, float t, float r, float b) {
        return new StackOverviewModel.Room(l, t, r, b, new CardFacts.Pane(false, false, null));
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
    public void itsOwnRowItsOwnCardAndNowhereChangeNothing() {
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
        assertSame("a Float of Home onto Home's row stays", OverviewDropPolicy.StackDrop.Type.NONE,
            OverviewDropPolicy.stackDrop(model, onHome, 0, TilingStack.NO_WINDOW).type);
        assertSame(OverviewDropPolicy.StackDrop.Type.NONE,
            OverviewDropPolicy.stackDrop(model, 999L, 2, TilingStack.NO_WINDOW).type);
    }

    @Test
    public void aWindowDroppedOnHomesRowBecomesAFloatOfTheHomePageInFront() {
        TilingStack stack = TilingStack.firstStart(3, "/home");
        long first = stack.workspace(1).windows().get(0).id();
        long onHome = stack.openIn(Place.home(0), WindowSpec.assistant(), true);
        stack.goTo(Place.home(2));
        stack.goTo(Place.workspace(1));
        StackOverviewModel model = StackOverviewModel.of(stack);
        assertEquals("Home's row stands for the page last in front", Place.home(2), model.rows().get(0).place);

        OverviewDropPolicy.StackDrop onRow = OverviewDropPolicy.stackDrop(model, first, 0, TilingStack.NO_WINDOW);
        OverviewDropPolicy.StackDrop onCard = OverviewDropPolicy.stackDrop(model, first, -1, onHome);

        assertSame(OverviewDropPolicy.StackDrop.Type.MOVE, onRow.type);
        assertEquals(Place.home(2), onRow.place);
        assertSame("a Home Float's card takes no tab: the window joins Home",
            OverviewDropPolicy.StackDrop.Type.MOVE, onCard.type);
        assertTrue(stack.moveWindow(first, onRow.place));
        assertEquals(Place.home(2), stack.placeOf(first));
        assertTrue("it floats there", stack.window(first).isFloat());
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

    // ---- names --------------------------------------------------------------------------------

    @Test
    public void aTerminalIsCalledByItsShellsTitleAndOtherKindsByTheirName() {
        TilingStack stack = TilingStack.firstStart(1, "/home");
        long shell = stack.workspace(1).windows().get(0).id();
        long app = stack.openIn(Place.workspace(1), WindowSpec.linuxApp("org.gimp", "GIMP"), false);
        long quiet = stack.openIn(Place.workspace(1), SHELL, false);
        stack.renameWindow(quiet, "notes");

        StackOverviewModel model = StackOverviewModel.of(stack,
            id -> id == shell ? " vim main.c " : id == app ? "not a shell" : "  ");

        assertEquals("vim main.c", model.card(shell).name);
        assertEquals("GIMP", model.card(app).name);
        assertEquals("a blank title falls back to the window's name", "notes", model.card(quiet).name);
    }

    // ---- rows found again --------------------------------------------------------------------

    @Test
    public void aRowIsFoundByItsWindowsAfterAWorkspaceAboveEmpties() {
        TilingStack stack = TilingStack.firstStart(1, "/home");
        long first = stack.workspace(1).windows().get(0).id();
        long second = stack.openIn(Place.workspace(2), SHELL, false);
        long third = stack.openIn(Place.workspace(3), SHELL, false);
        StackOverviewModel opened = StackOverviewModel.of(stack);
        assertEquals(Place.workspace(3), opened.rows().get(3).place);

        // Workspace 1's last window exits while Overview is up: 2 and 3 become 1 and 2.
        stack.close(first);
        StackOverviewModel now = StackOverviewModel.of(stack);

        int row = opened.rowIn(now, 3);
        assertEquals(Place.workspace(2), now.rows().get(row).place);
        OverviewDropPolicy.StackDrop drop =
            OverviewDropPolicy.stackDrop(now, second, row, TilingStack.NO_WINDOW);
        assertEquals("the window goes to the Workspace that was shown, now numbered 2",
            Place.workspace(2), drop.place);
        assertEquals(third, now.rows().get(row).cards.get(0).windowId);
        assertEquals(0, opened.rowIn(now, 0));
        assertTrue("the waiting row stays the waiting one", now.rows().get(opened.rowIn(now, 4)).waiting);
        assertEquals("a row whose windows are all gone is none", -1, opened.rowIn(now, 1));
        assertEquals(-1, opened.rowIn(now, -1));
    }

    // ---- drops onto a Float ------------------------------------------------------------------

    @Test
    public void ontoAFloatInItsOwnPlaceChangesNothing() {
        TilingStack stack = TilingStack.firstStart(1, "/home");
        long first = stack.workspace(1).windows().get(0).id();
        long floating = stack.openIn(Place.workspace(1), SHELL, true);

        OverviewDropPolicy.StackDrop drop =
            OverviewDropPolicy.stackDrop(StackOverviewModel.of(stack), first, 1, floating);

        assertSame(OverviewDropPolicy.StackDrop.Type.NONE, drop.type);
    }

    @Test
    public void aFloatDroppedOntoATileJoinsItAsATab() {
        TilingStack stack = TilingStack.firstStart(1, "/home");
        long first = stack.workspace(1).windows().get(0).id();
        long floating = stack.openIn(Place.workspace(2), SHELL, true);

        OverviewDropPolicy.StackDrop drop =
            OverviewDropPolicy.stackDrop(StackOverviewModel.of(stack), floating, 1, first);

        assertSame(OverviewDropPolicy.StackDrop.Type.TAB, drop.type);
    }

    // ---- scrolling while a card is held -------------------------------------------------------

    @Test
    public void nearAnEdgeTheScrollerMovesThatWayFasterTheDeeper() {
        assertEquals(0f, OverviewDropPolicy.edgeScrollPx(500f, 0f, 1000f, 100f, 20f), 0f);
        assertEquals(-10f, OverviewDropPolicy.edgeScrollPx(50f, 0f, 1000f, 100f, 20f), 0.01f);
        assertEquals(-20f, OverviewDropPolicy.edgeScrollPx(-30f, 0f, 1000f, 100f, 20f), 0.01f);
        assertEquals(10f, OverviewDropPolicy.edgeScrollPx(950f, 0f, 1000f, 100f, 20f), 0.01f);
        assertEquals(20f, OverviewDropPolicy.edgeScrollPx(1200f, 0f, 1000f, 100f, 20f), 0.01f);
        assertTrue("a short span keeps a middle", OverviewDropPolicy.edgeScrollPx(50f, 0f, 100f, 100f, 20f) == 0f);
        assertEquals(0f, OverviewDropPolicy.edgeScrollPx(0f, 0f, 0f, 100f, 20f), 0f);
    }
}
