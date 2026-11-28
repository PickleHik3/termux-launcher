package com.termux.app.tiling;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** The stack's rules behind a held mark's window menu (#425): reorder, minimise, maximise. */
public class TilingStackWindowMenuTest {

    private static final WindowSpec SHELL = WindowSpec.shell("/home");

    /** Workspace 1 with {@code count} tiled shells in order, focused on the last. */
    private static long[] workspaceWith(TilingStack stack, int count) {
        stack.goTo(Place.workspace(1));
        long[] ids = new long[count];
        for (int i = 0; i < count; i++) ids[i] = stack.open(SHELL);
        return ids;
    }

    private static List<Long> order(TilingStack stack) {
        List<Long> ids = new ArrayList<>();
        for (TilingWindow window : stack.workspace(1).windows()) ids.add(window.id());
        return ids;
    }

    private static List<List<Long>> groups(TilingStack stack) {
        List<List<Long>> groups = new ArrayList<>();
        for (TileGroup group : stack.workspace(1).groups()) {
            List<Long> ids = new ArrayList<>();
            for (TilingWindow window : group.windows()) ids.add(window.id());
            groups.add(ids);
        }
        return groups;
    }

    // ---- moveWithin ------------------------------------------------------------------------------

    @Test
    public void aWindowMovesToAnotherSlotOfItsWorkspaceAndTheRestKeepTheirOrder() {
        TilingStack stack = TilingStack.empty(1);
        long[] w = workspaceWith(stack, 4);

        assertTrue(stack.moveWithin(w[0], 2));
        assertEquals(Arrays.asList(w[1], w[2], w[0], w[3]), order(stack));

        assertTrue(stack.moveWithin(w[3], 0));
        assertEquals(Arrays.asList(w[3], w[1], w[2], w[0]), order(stack));
    }

    @Test
    public void aSlotPastTheEndIsTheLastAndTheSameSlotChangesNothing() {
        TilingStack stack = TilingStack.empty(1);
        long[] w = workspaceWith(stack, 3);

        assertTrue(stack.moveWithin(w[0], 99));
        assertEquals(Arrays.asList(w[1], w[2], w[0]), order(stack));
        assertFalse(stack.moveWithin(w[0], 2));
        assertFalse(stack.moveWithin(w[0], 5));
    }

    @Test
    public void focusStaysWhereItWasWhenAWindowMoves() {
        TilingStack stack = TilingStack.empty(1);
        long[] w = workspaceWith(stack, 3);
        stack.focusWindow(w[1]);

        stack.moveWithin(w[0], 2);

        assertEquals(w[1], stack.focusedWindowId());
    }

    @Test
    public void aWindowSharingATileLeavesItAsATileOfItsOwn() {
        TilingStack stack = TilingStack.empty(1);
        long[] w = workspaceWith(stack, 3);
        assertTrue(stack.stack(w[1], w[0]));
        assertEquals(Arrays.asList(Arrays.asList(w[0], w[1]), Arrays.asList(w[2])), groups(stack));

        assertTrue(stack.moveWithin(w[1], 1));

        assertEquals(Arrays.asList(Arrays.asList(w[0]), Arrays.asList(w[1]), Arrays.asList(w[2])),
            groups(stack));
    }

    @Test
    public void aFloatOrAHomeFloatHasNoSlotToMoveTo() {
        TilingStack stack = TilingStack.empty(1);
        long[] w = workspaceWith(stack, 2);
        stack.setFloat(w[1], true);
        assertFalse(stack.moveWithin(w[1], 0));

        long home = stack.openIn(Place.home(0), SHELL, true);
        assertFalse(stack.moveWithin(home, 0));
        assertFalse(stack.moveWithin(12345L, 0));
    }

    // ---- minimise --------------------------------------------------------------------------------

    @Test
    public void aMinimisedWindowKeepsItsPlaceButNoSidewaysStepLandsOnIt() {
        TilingStack stack = TilingStack.empty(1);
        long[] w = workspaceWith(stack, 3);
        stack.focusWindow(w[0]);

        assertTrue(stack.setMinimised(w[1], true));

        assertTrue(stack.window(w[1]).isMinimised());
        assertEquals(Arrays.asList(w[0], w[1], w[2]), order(stack));
        assertTrue(stack.focusRight());
        assertEquals(w[2], stack.focusedWindowId());
        assertTrue(stack.focusLeft());
        assertEquals(w[0], stack.focusedWindowId());
    }

    @Test
    public void minimisingTheFocusedWindowHandsFocusToTheNextShownOne() {
        TilingStack stack = TilingStack.empty(1);
        long[] w = workspaceWith(stack, 3);
        stack.focusWindow(w[1]);

        stack.setMinimised(w[1], true);

        assertEquals(w[2], stack.focusedWindowId());
        stack.setMinimised(w[2], true);
        assertEquals(w[0], stack.focusedWindowId());
    }

    @Test
    public void theOnlyShownWindowMinimisedKeepsFocusAndItsWorkspace() {
        TilingStack stack = TilingStack.empty(1);
        long[] w = workspaceWith(stack, 1);

        assertTrue(stack.setMinimised(w[0], true));

        assertEquals(w[0], stack.focusedWindowId());
        assertEquals(2, stack.workspaceCount());
        assertFalse(stack.workspace(1).isEmpty());
    }

    @Test
    public void focusingAMinimisedWindowRestoresIt() {
        TilingStack stack = TilingStack.empty(1);
        long[] w = workspaceWith(stack, 2);
        stack.setMinimised(w[0], true);

        assertTrue(stack.focusWindow(w[0]));

        assertFalse(stack.window(w[0]).isMinimised());
        assertEquals(w[0], stack.focusedWindowId());
    }

    @Test
    public void aMinimisedWindowLeavesItsTabsOrStackFirst() {
        TilingStack stack = TilingStack.empty(1);
        long[] w = workspaceWith(stack, 2);
        stack.joinTab(w[1], w[0]);

        assertTrue(stack.setMinimised(w[1], true));

        assertEquals(Arrays.asList(Arrays.asList(w[0]), Arrays.asList(w[1])), groups(stack));
        assertFalse(stack.joinTab(w[0], w[1]));
        assertFalse(stack.stack(w[0], w[1]));
    }

    @Test
    public void movingOrFloatingAMinimisedWindowShowsItAgain() {
        TilingStack stack = TilingStack.empty(1);
        long[] w = workspaceWith(stack, 2);
        stack.setMinimised(w[0], true);
        stack.setFloat(w[0], true);
        assertFalse(stack.window(w[0]).isMinimised());

        stack.setMinimised(w[1], true);
        stack.moveWindow(w[1], Place.workspace(2));
        assertFalse(stack.window(w[1]).isMinimised());
    }

    @Test
    public void minimisingTwiceOrAnUnknownWindowChangesNothing() {
        TilingStack stack = TilingStack.empty(1);
        long[] w = workspaceWith(stack, 2);
        assertTrue(stack.setMinimised(w[0], true));
        assertFalse(stack.setMinimised(w[0], true));
        assertFalse(stack.setMinimised(w[1], false));
        assertFalse(stack.setMinimised(999L, true));
    }

    // ---- maximise --------------------------------------------------------------------------------

    @Test
    public void aTileIsMaximisedAndBackAndAFloatIsNot() {
        TilingStack stack = TilingStack.empty(1);
        long[] w = workspaceWith(stack, 2);

        assertTrue(stack.setMaximised(w[0], true));
        assertTrue(stack.window(w[0]).isMaximised());
        assertFalse(stack.setMaximised(w[0], true));
        assertTrue(stack.setMaximised(w[0], false));
        assertFalse(stack.window(w[0]).isMaximised());

        stack.setFloat(w[1], true);
        assertFalse(stack.setMaximised(w[1], true));
    }

    @Test
    public void floatingOrMinimisingAMaximisedWindowEndsItsMaximise() {
        TilingStack stack = TilingStack.empty(1);
        long[] w = workspaceWith(stack, 2);
        stack.setMaximised(w[0], true);
        stack.setMinimised(w[0], true);
        assertFalse(stack.window(w[0]).isMaximised());

        stack.focusWindow(w[0]);
        stack.setMaximised(w[0], true);
        stack.setFloat(w[0], true);
        assertFalse(stack.window(w[0]).isMaximised());
    }

    // ---- the saved form --------------------------------------------------------------------------

    @Test
    public void minimisedAndMaximisedComeBackFromTheSavedForm() throws Exception {
        TilingStack stack = TilingStack.empty(1);
        long[] w = workspaceWith(stack, 3);
        stack.setMinimised(w[0], true);
        stack.setMaximised(w[1], true);

        TilingStack back = TilingStack.fromJson(stack.toJson());

        assertTrue(back.window(w[0]).isMinimised());
        assertFalse(back.window(w[0]).isMaximised());
        assertTrue(back.window(w[1]).isMaximised());
        assertFalse(back.window(w[2]).isMinimised());
    }
}
