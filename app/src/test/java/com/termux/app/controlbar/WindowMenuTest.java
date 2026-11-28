package com.termux.app.controlbar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.termux.app.controlbar.WindowMenu.Action;
import com.termux.app.tiling.Place;
import com.termux.app.tiling.TilingStack;
import com.termux.app.tiling.WindowSpec;
import com.termux.app.tiling.WorkspaceLayout;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Which rows a held mark's window menu holds (#425), from the stack as it stands. */
public class WindowMenuTest {

    private static final WindowSpec SHELL = WindowSpec.shell("/home");
    private static final WindowSpec APP = WindowSpec.linuxApp("org.example.app", "App");
    private static final WindowSpec DISPLAY = WindowSpec.display();

    private static List<Action> actions(List<WindowMenu.Row> rows) {
        List<Action> actions = new ArrayList<>();
        for (WindowMenu.Row row : rows) actions.add(row.action);
        return actions;
    }

    private static TilingStack stack() {
        TilingStack stack = TilingStack.empty(1);
        stack.goTo(Place.workspace(1));
        return stack;
    }

    @Test
    public void aTerminalHasNoMinimiseMaximiseOrScale() {
        TilingStack stack = stack();
        long shell = stack.open(SHELL);

        WindowMenu.Subject menu = WindowMenu.subjectOf(stack, shell, true);

        assertEquals(Arrays.asList(Action.FLOAT, Action.FULL_SCREEN, Action.MOVE_LIST, Action.EDIT_LOOK, Action.CLOSE),
            actions(menu.rows));
    }

    @Test
    public void aLinuxAppMinimisesMaximisesAndScalesWhenItTakesAScale() {
        TilingStack stack = stack();
        long app = stack.open(APP);

        assertEquals(Arrays.asList(Action.FLOAT, Action.FULL_SCREEN, Action.MINIMISE, Action.MAXIMISE,
            Action.SCALE, Action.MOVE_LIST, Action.EDIT_LOOK, Action.CLOSE), actions(WindowMenu.subjectOf(stack, app, true).rows));
        assertEquals(Arrays.asList(Action.FLOAT, Action.FULL_SCREEN, Action.MINIMISE, Action.MAXIMISE,
            Action.MOVE_LIST, Action.EDIT_LOOK, Action.CLOSE), actions(WindowMenu.subjectOf(stack, app, false).rows));
    }

    @Test
    public void theDisplayMaximisesOnly() {
        TilingStack stack = stack();
        long display = stack.open(DISPLAY);

        assertEquals(Arrays.asList(Action.FLOAT, Action.FULL_SCREEN, Action.MAXIMISE, Action.MOVE_LIST,
            Action.EDIT_LOOK, Action.CLOSE), actions(WindowMenu.subjectOf(stack, display, true).rows));
    }

    @Test
    public void aMaximisedWindowOffersToPutItBack() {
        TilingStack stack = stack();
        long app = stack.open(APP);
        stack.setMaximised(app, true);

        assertTrue(actions(WindowMenu.subjectOf(stack, app, false).rows).contains(Action.UNMAXIMISE));
    }

    @Test
    public void aFloatTilesAndPinsAndHasNoMaximise() {
        TilingStack stack = stack();
        long app = stack.open(APP);
        stack.setFloat(app, true);
        assertEquals(Arrays.asList(Action.TILE, Action.PIN, Action.FULL_SCREEN, Action.MINIMISE,
            Action.MOVE_LIST, Action.EDIT_LOOK, Action.CLOSE), actions(WindowMenu.subjectOf(stack, app, false).rows));

        stack.setPinned(app, true);
        assertTrue(actions(WindowMenu.subjectOf(stack, app, false).rows).contains(Action.UNPIN));
    }

    @Test
    public void aFullScreenWindowOffersToLeaveIt() {
        TilingStack stack = stack();
        long shell = stack.open(SHELL);
        stack.setFullScreen(shell, true);

        List<Action> rows = actions(WindowMenu.subjectOf(stack, shell, false).rows);

        assertTrue(rows.contains(Action.LEAVE_FULL_SCREEN));
        assertTrue(!rows.contains(Action.FULL_SCREEN));
    }

    @Test
    public void aMinimisedWindowOffersRestoreAndNothingThatLaysItOut() {
        TilingStack stack = stack();
        long app = stack.open(APP);
        stack.open(SHELL);
        stack.setMinimised(app, true);

        assertEquals(Arrays.asList(Action.RESTORE, Action.MOVE_LIST, Action.EDIT_LOOK, Action.CLOSE),
            actions(WindowMenu.subjectOf(stack, app, true).rows));
    }

    @Test
    public void moveListsEveryOtherWorkspaceTheNewOneAndHome() {
        TilingStack stack = stack();
        long a = stack.open(SHELL);
        stack.open(SHELL);
        stack.moveWindow(stack.open(SHELL), Place.workspace(2));

        WindowMenu.Subject menu = WindowMenu.subjectOf(stack, a, false);

        List<WindowMenu.Row> rows = menu.moveRows;
        assertEquals(3, rows.size());
        assertEquals(Place.workspace(2), rows.get(0).place);
        assertEquals(false, rows.get(0).newWorkspace);
        assertEquals(Place.workspace(3), rows.get(1).place);
        assertEquals(true, rows.get(1).newWorkspace);
        assertEquals(Place.home(0), rows.get(2).place);
    }

    @Test
    public void aWindowAloneInItsWorkspaceHasNoNewWorkspaceToMoveTo() {
        TilingStack stack = TilingStack.empty(0);
        long a = stack.open(SHELL);

        assertEquals(Arrays.asList(Action.FLOAT, Action.FULL_SCREEN, Action.EDIT_LOOK, Action.CLOSE),
            actions(WindowMenu.subjectOf(stack, a, false).rows));
    }

    @Test
    public void tabWithListsTheOtherTilesAndStackWithTheColumnsBesideIt() {
        TilingStack stack = stack();
        long a = stack.open(SHELL);
        long b = stack.open(SHELL);
        long c = stack.open(SHELL);
        long d = stack.open(SHELL);
        stack.focusWindow(b);

        WindowMenu.Subject menu = WindowMenu.subjectOf(stack, b, false);

        assertEquals(Arrays.asList(a, c, d), windows(menu.tabRows));
        assertEquals(Arrays.asList(a, c), windows(menu.stackRows));
        assertTrue(actions(menu.rows).containsAll(Arrays.asList(Action.TAB_LIST, Action.STACK_LIST)));
    }

    @Test
    public void aFullColumnAndDwindleTakeNoStack() {
        TilingStack stack = stack();
        long a = stack.open(SHELL);
        long b = stack.open(SHELL);
        long c = stack.open(SHELL);
        stack.stack(b, a);
        assertEquals(Arrays.<Long>asList(), windows(WindowMenu.subjectOf(stack, c, false).stackRows));

        stack.setLayout(1, WorkspaceLayout.DWINDLE);
        assertEquals(Arrays.<Long>asList(), windows(WindowMenu.subjectOf(stack, a, false).stackRows));
    }

    @Test
    public void aWindowSharingATileCanBeTakenOut() {
        TilingStack stack = stack();
        long a = stack.open(SHELL);
        long b = stack.open(SHELL);
        stack.joinTab(b, a);

        WindowMenu.Subject menu = WindowMenu.subjectOf(stack, b, false);

        WindowMenu.Row last = menu.rows.get(menu.rows.size() - 3);
        assertEquals(Action.SEPARATE, last.action);
        assertEquals(true, last.tabs);
        assertEquals(Action.CLOSE, menu.rows.get(menu.rows.size() - 1).action);
    }

    @Test
    public void theFlatRowsPutEachListsRowsInItsPlace() {
        TilingStack stack = stack();
        long a = stack.open(SHELL);
        long b = stack.open(SHELL);

        List<Action> flat = actions(WindowMenu.subjectOf(stack, a, false).flat());

        assertEquals(Arrays.asList(Action.FLOAT, Action.FULL_SCREEN, Action.MOVE, Action.MOVE, Action.TAB,
            Action.STACK, Action.EDIT_LOOK, Action.CLOSE), flat);
        assertEquals(b, WindowMenu.subjectOf(stack, a, false).tabRows.get(0).window);
    }

    @Test
    public void anUnknownWindowHasNoMenu() {
        assertNull(WindowMenu.subjectOf(stack(), 42L, false));
    }

    private static List<Long> windows(List<WindowMenu.Row> rows) {
        List<Long> ids = new ArrayList<>();
        for (WindowMenu.Row row : rows) ids.add(row.window);
        return ids;
    }
}
