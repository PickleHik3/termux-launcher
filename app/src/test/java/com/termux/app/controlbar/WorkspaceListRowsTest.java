package com.termux.app.controlbar;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.termux.app.tiling.Place;
import com.termux.app.tiling.TilingStack;
import com.termux.app.tiling.WindowKind;
import com.termux.app.tiling.WindowSpec;

import org.junit.Test;

import java.util.List;

/** The Workspace list's rows (#429). */
public class WorkspaceListRowsTest {

    private static final WindowSpec SHELL = WindowSpec.shell("/home");

    @Test
    public void oneRowPerWorkspaceInOrderEndingWithTheWaitingOne() {
        TilingStack stack = TilingStack.firstStart(1, "/home");
        stack.openIn(Place.workspace(2), SHELL, false);

        List<WorkspaceListRows.Row> rows = WorkspaceListRows.of(stack);

        assertEquals(3, rows.size());
        assertEquals(1, rows.get(0).number);
        assertEquals(2, rows.get(1).number);
        assertEquals(3, rows.get(2).number);
        assertTrue(rows.get(2).waiting);
        assertNull(rows.get(2).kind);
        assertFalse(rows.get(0).waiting);
    }

    @Test
    public void aRowCarriesTheMainWindowsKindAndTheWorkspacesName() {
        TilingStack stack = TilingStack.firstStart(1, "/home");
        stack.renameWorkspace(1, "Build");

        WorkspaceListRows.Row row = WorkspaceListRows.of(stack).get(0);

        assertEquals("Build", row.name);
        assertEquals(WindowKind.SHELL, row.kind);
    }

    @Test
    public void aWorkspaceWithoutANameHasNone() {
        TilingStack stack = TilingStack.firstStart(1, "/home");
        assertNull(WorkspaceListRows.of(stack).get(0).name);
    }

    @Test
    public void theWorkspaceInFrontIsMarkedAndHomeMarksNone() {
        TilingStack stack = TilingStack.firstStart(1, "/home");
        stack.openIn(Place.workspace(2), SHELL, false);

        for (WorkspaceListRows.Row row : WorkspaceListRows.of(stack)) assertFalse(row.front);

        stack.goTo(Place.workspace(2));
        List<WorkspaceListRows.Row> rows = WorkspaceListRows.of(stack);
        assertFalse(rows.get(0).front);
        assertTrue(rows.get(1).front);
    }

    @Test
    public void lettingGoOnARowGoesToItsWorkspace() {
        TilingStack stack = TilingStack.firstStart(1, "/home");
        stack.openIn(Place.workspace(2), SHELL, false);
        WorkspaceListRows.Row row = WorkspaceListRows.of(stack).get(1);
        assertEquals(Place.workspace(2), row.place());
    }

    @Test
    public void onlyAWorkspaceWithWindowsCanBeRenamed() {
        TilingStack stack = TilingStack.firstStart(1, "/home");
        List<WorkspaceListRows.Row> rows = WorkspaceListRows.of(stack);
        assertTrue(rows.get(0).renamable());
        assertFalse(rows.get(1).renamable());
    }

    @Test
    public void theRowUnderAFingerIsFoundByItsSpan() {
        assertEquals(0, WorkspaceListRows.indexAt(100f, 100f, 50f, 3));
        assertEquals(1, WorkspaceListRows.indexAt(150f, 100f, 50f, 3));
        assertEquals(2, WorkspaceListRows.indexAt(249f, 100f, 50f, 3));
        assertEquals(-1, WorkspaceListRows.indexAt(250f, 100f, 50f, 3));
        assertEquals(-1, WorkspaceListRows.indexAt(99f, 100f, 50f, 3));
        assertEquals(-1, WorkspaceListRows.indexAt(120f, 100f, 50f, 0));
    }

    @Test
    public void aListIsStaleOnceAWorkspaceIsRenamedAddedOrGone() {
        TilingStack stack = TilingStack.firstStart(1, "/home");
        stack.openIn(Place.workspace(2), SHELL, false);
        List<WorkspaceListRows.Row> shown = WorkspaceListRows.of(stack);
        assertTrue(WorkspaceListRows.same(shown, WorkspaceListRows.of(stack)));

        stack.renameWorkspace(2, "Mail");
        assertFalse(WorkspaceListRows.same(shown, WorkspaceListRows.of(stack)));

        List<WorkspaceListRows.Row> named = WorkspaceListRows.of(stack);
        stack.openIn(Place.workspace(3), SHELL, false);
        assertFalse(WorkspaceListRows.same(named, WorkspaceListRows.of(stack)));
    }
}
