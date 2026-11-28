package com.termux.app.tiling;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.view.KeyEvent;

import com.termux.app.wall.BorderDrag;
import com.termux.launcherctl.LauncherToolRegistry;

import org.junit.Test;

/** The ways into the tracer's moves (#418): chords, v1's tools, Home's gesture and the edges. */
public class TilingInputTest {

    @Test
    public void newWindowAndNewSessionOpenATerminalEverywhere() {
        for (boolean workspace : new boolean[] {true, false}) {
            assertEquals(TilingInput.Command.NEW_TERMINAL,
                TilingInput.forTool(LauncherToolRegistry.TOOL_WINDOW_NEW, workspace));
            assertEquals(TilingInput.Command.NEW_TERMINAL,
                TilingInput.forTool(LauncherToolRegistry.TOOL_SESSION_NEW, workspace));
        }
    }

    @Test
    public void windowAndSessionStepsMoveSidewaysAndUpAndDownOnAWorkspace() {
        assertEquals(TilingInput.Command.RIGHT, TilingInput.forTool(LauncherToolRegistry.TOOL_WINDOW_NEXT, true));
        assertEquals(TilingInput.Command.LEFT, TilingInput.forTool(LauncherToolRegistry.TOOL_WINDOW_PREVIOUS, true));
        assertEquals(TilingInput.Command.DOWN, TilingInput.forTool(LauncherToolRegistry.TOOL_SESSION_NEXT, true));
        assertEquals(TilingInput.Command.UP, TilingInput.forTool(LauncherToolRegistry.TOOL_SESSION_PREVIOUS, true));
        assertEquals(TilingInput.Command.CLOSE, TilingInput.forTool(LauncherToolRegistry.TOOL_WINDOW_CLOSE, true));
    }

    @Test
    public void onHomeOnlyTheStepDownIsTheStacks() {
        assertEquals(TilingInput.Command.DOWN, TilingInput.forTool(LauncherToolRegistry.TOOL_SESSION_NEXT, false));
        assertEquals(TilingInput.Command.NONE, TilingInput.forTool(LauncherToolRegistry.TOOL_SESSION_PREVIOUS, false));
        assertEquals(TilingInput.Command.NONE, TilingInput.forTool(LauncherToolRegistry.TOOL_WINDOW_NEXT, false));
        assertEquals(TilingInput.Command.NONE, TilingInput.forTool(LauncherToolRegistry.TOOL_WINDOW_PREVIOUS, false));
        assertEquals(TilingInput.Command.NONE, TilingInput.forTool(LauncherToolRegistry.TOOL_WINDOW_CLOSE, false));
    }

    @Test
    public void anyOtherToolIsNotTheStacks() {
        assertEquals(TilingInput.Command.NONE, TilingInput.forTool("pane.split.vertical", true));
        assertEquals(TilingInput.Command.NONE, TilingInput.forTool("", true));
    }

    @Test
    public void aTwoFingerSwipeUpOnHomeGoesDownWhenItIsSetToNothing() {
        assertTrue(TilingInput.homeGestureGoesDown(true, true, false));
        assertFalse("set to something else, it does that", TilingInput.homeGestureGoesDown(true, false, false));
        assertFalse("another gesture", TilingInput.homeGestureGoesDown(false, true, false));
        assertTrue("any gesture set to the Terminal", TilingInput.homeGestureGoesDown(false, false, true));
    }

    @Test
    public void theAirAroundTheRoomIsTheBordersAndTheCornersAreNone() {
        // A 1000 by 2000 view with Android's bars over its top 50 and bottom 40, windows in 31..969 by 81..1929.
        float w = 1000f, top = 50f, bottom = 1960f;
        float l = 31f, t = 81f, r = 969f, b = 1929f;
        assertEquals(BorderDrag.Border.LEFT, TilingInput.holdAt(30.9f, 1000f, w, top, bottom, l, t, r, b));
        assertEquals(BorderDrag.Border.RIGHT, TilingInput.holdAt(969f, 1000f, w, top, bottom, l, t, r, b));
        assertEquals(BorderDrag.Border.TOP, TilingInput.holdAt(500f, 60f, w, top, bottom, l, t, r, b));
        assertEquals(BorderDrag.Border.BOTTOM, TilingInput.holdAt(500f, 1929f, w, top, bottom, l, t, r, b));
        assertEquals(BorderDrag.Border.NONE, TilingInput.holdAt(500f, 1000f, w, top, bottom, l, t, r, b));
        assertEquals(BorderDrag.Border.NONE, TilingInput.holdAt(5f, 60f, w, top, bottom, l, t, r, b));
        assertEquals(BorderDrag.Border.NONE, TilingInput.holdAt(995f, 1950f, w, top, bottom, l, t, r, b));
        assertEquals(BorderDrag.Border.NONE, TilingInput.holdAt(-1f, 1000f, w, top, bottom, l, t, r, b));
        assertEquals("under Android's bars", BorderDrag.Border.NONE,
            TilingInput.holdAt(500f, 20f, w, top, bottom, l, t, r, b));
        assertEquals(BorderDrag.Border.NONE, TilingInput.holdAt(500f, 1990f, w, top, bottom, l, t, r, b));
        assertEquals(BorderDrag.Border.NONE, TilingInput.holdAt(5f, 1000f, 0f, top, bottom, l, t, r, b));
    }

    @Test
    public void aWindowsOuterPixelIsNeverAHold() {
        float l = 31f, t = 81f, r = 969f, b = 1929f;
        assertEquals(BorderDrag.Border.NONE, TilingInput.holdAt(l, 1000f, 1000f, 50f, 1960f, l, t, r, b));
        assertEquals(BorderDrag.Border.NONE, TilingInput.holdAt(r - 0.5f, 1000f, 1000f, 50f, 1960f, l, t, r, b));
        assertEquals(BorderDrag.Border.NONE, TilingInput.holdAt(500f, t, 1000f, 50f, 1960f, l, t, r, b));
        assertEquals(BorderDrag.Border.NONE, TilingInput.holdAt(500f, b - 0.5f, 1000f, 50f, 1960f, l, t, r, b));
    }

    // ---- the zone under a press (#432) ------------------------------------------------------------

    private static final ScrollingLayoutPolicy.Viewport ROOM = new ScrollingLayoutPolicy.Viewport(938f, 1848f, 20f, false);

    /** Two half-width columns side by side; the second holds windows 2 over 3 when {@code stacked}. */
    private static ScrollingLayoutPolicy.State twoColumns(boolean stacked) {
        ScrollingLayoutPolicy.State s = ScrollingLayoutPolicy.open(ScrollingLayoutPolicy.State.EMPTY, ROOM, 1L);
        s = ScrollingLayoutPolicy.setWidthPreset(s, ROOM, 1L, 0.5f);
        s = ScrollingLayoutPolicy.open(s, ROOM, 2L);
        s = ScrollingLayoutPolicy.setWidthPreset(s, ROOM, 2L, 0.5f);
        if (stacked) {
            s = ScrollingLayoutPolicy.open(s, ROOM, 3L);
            s = ScrollingLayoutPolicy.stackInto(s, ROOM, 3L, ScrollingLayoutPolicy.LEFT);
        }
        return s;
    }

    private static TilingInput.Zone zone(ScrollingLayoutPolicy.State state, float x, float y, float scroll) {
        // The view is 1000 by 2000; the room starts at 31, 81 as in the border tests; bars 50 and 40.
        return TilingInput.zoneAt(x, y, 1000f, 50f, 1960f, 31f, 81f, 969f, 1929f,
            ScrollingLayoutPolicy.layout(state, ROOM), scroll);
    }

    @Test
    public void theOuterAirIsAnOuterZoneWithItsBorder() {
        ScrollingLayoutPolicy.State s = twoColumns(false);
        TilingInput.Zone left = zone(s, 10f, 1000f, 0f);
        assertEquals(TilingInput.Zone.Kind.OUTER, left.kind);
        assertEquals(BorderDrag.Border.LEFT, left.side);
        assertEquals(BorderDrag.Border.TOP, zone(s, 500f, 60f, 0f).side);
        assertEquals(BorderDrag.Border.BOTTOM, zone(s, 500f, 1940f, 0f).side);
        assertEquals(BorderDrag.Border.RIGHT, zone(s, 990f, 1000f, 0f).side);
    }

    @Test
    public void aWindowsBodyIsAlwaysContent() {
        ScrollingLayoutPolicy.State s = twoColumns(true);
        ScrollingLayoutPolicy.Placement one = ScrollingLayoutPolicy.placementOf(s, ROOM, 1L);
        ScrollingLayoutPolicy.Placement two = ScrollingLayoutPolicy.placementOf(s, ROOM, 2L);
        assertEquals(TilingInput.Zone.Kind.CONTENT, zone(s, 31f + one.right - 0.5f, 1000f, 0f).kind);
        assertEquals(TilingInput.Zone.Kind.CONTENT, zone(s, 31f + two.left, 1000f - 80f, 0f).kind);
        assertEquals(TilingInput.Zone.Kind.CONTENT, zone(s, 31f + one.left, 81f + one.top + 5f, 0f).kind);
    }

    @Test
    public void theGapBetweenTwoColumnsNamesTheLeftColumnAndTheNearerSide() {
        ScrollingLayoutPolicy.State s = twoColumns(false);
        ScrollingLayoutPolicy.Placement one = ScrollingLayoutPolicy.placementOf(s, ROOM, 1L);
        float gapLeft = 31f + one.right;
        TilingInput.Zone nearLeft = zone(s, gapLeft + 1f, 1000f, 0f);
        assertEquals(TilingInput.Zone.Kind.COLUMN_GAP, nearLeft.kind);
        assertEquals(0, nearLeft.column);
        assertEquals(BorderDrag.Border.LEFT, nearLeft.side);
        assertEquals(gapLeft + ROOM.gap / 2f, nearLeft.centreX, 0.01f);
        assertEquals(BorderDrag.Border.RIGHT, zone(s, gapLeft + ROOM.gap - 1f, 1000f, 0f).side);
        assertEquals("the gap's first pixel", TilingInput.Zone.Kind.COLUMN_GAP, zone(s, gapLeft, 1000f, 0f).kind);
        assertEquals("the next column's first pixel", TilingInput.Zone.Kind.CONTENT,
            zone(s, gapLeft + ROOM.gap, 1000f, 0f).kind);
    }

    @Test
    public void theStripsScrollMovesTheGaps() {
        ScrollingLayoutPolicy.State s = twoColumns(false);
        ScrollingLayoutPolicy.Placement one = ScrollingLayoutPolicy.placementOf(s, ROOM, 1L);
        float gapLeft = 31f + one.right - 100f;
        assertEquals(TilingInput.Zone.Kind.COLUMN_GAP, zone(s, gapLeft + 2f, 1000f, 100f).kind);
        assertEquals(TilingInput.Zone.Kind.CONTENT, zone(s, 31f + one.right + 2f, 1000f, 100f).kind);
    }

    @Test
    public void theGapBetweenStackedWindowsNamesItsColumnAndStepsUpOrDown() {
        ScrollingLayoutPolicy.State s = twoColumns(true);
        ScrollingLayoutPolicy.Placement upper = ScrollingLayoutPolicy.placementOf(s, ROOM, 2L);
        float gapTop = 81f + upper.bottom;
        float x = 31f + upper.left + 50f;
        TilingInput.Zone high = zone(s, x, gapTop + 1f, 0f);
        assertEquals(TilingInput.Zone.Kind.STACK_GAP, high.kind);
        assertEquals(1, high.column);
        assertEquals(BorderDrag.Border.TOP, high.side);
        assertEquals(gapTop + ROOM.gap / 2f, high.centreY, 0.01f);
        assertEquals(BorderDrag.Border.BOTTOM, zone(s, x, gapTop + ROOM.gap - 1f, 0f).side);
    }

    @Test
    public void pastTheLastColumnAndOffTheRoomIsNotAGap() {
        ScrollingLayoutPolicy.State s = ScrollingLayoutPolicy.open(ScrollingLayoutPolicy.State.EMPTY, ROOM, 1L);
        s = ScrollingLayoutPolicy.setWidthPreset(s, ROOM, 1L, 0.5f);
        assertEquals(TilingInput.Zone.Kind.CONTENT, zone(s, 31f + 469f + 5f, 1000f, 0f).kind);
        assertEquals("under the bars", TilingInput.Zone.Kind.CONTENT, zone(s, 500f, 20f, 0f).kind);
        assertEquals(TilingInput.Zone.Kind.CONTENT, zone(ScrollingLayoutPolicy.State.EMPTY, 500f, 1000f, 0f).kind);
    }

    @Test
    public void aColumnGapHoldTargetsTheWindowBesideTheGapOnTheHeldSide() {
        ScrollingLayoutPolicy.State s = twoColumns(true); // column 0: window 1; column 1: 2 over 3
        ScrollingLayoutPolicy.Placement one = ScrollingLayoutPolicy.placementOf(s, ROOM, 1L);
        float gapLeft = 31f + one.right;
        assertEquals(1L, TilingInput.gapTarget(s, zone(s, gapLeft + 1f, 1000f, 0f)));
        long right = TilingInput.gapTarget(s, zone(s, gapLeft + ROOM.gap - 1f, 1000f, 0f));
        assertTrue("a window of the right column", right == 2L || right == 3L);
    }

    @Test
    public void aColumnGapHoldKeepsTheFocusedRowOfAStackedColumn() {
        ScrollingLayoutPolicy.State s = twoColumns(true);
        ScrollingLayoutPolicy.State focused3 = ScrollingLayoutPolicy.focusWindow(s, ROOM, 3L);
        ScrollingLayoutPolicy.Placement one = ScrollingLayoutPolicy.placementOf(focused3, ROOM, 1L);
        float gapLeft = 31f + one.right;
        assertEquals(3L, TilingInput.gapTarget(focused3, zone(focused3, gapLeft + ROOM.gap - 1f, 1000f, 0f)));
        ScrollingLayoutPolicy.State focused2 = ScrollingLayoutPolicy.focusWindow(s, ROOM, 2L);
        assertEquals(2L, TilingInput.gapTarget(focused2, zone(focused2, gapLeft + ROOM.gap - 1f, 1000f, 0f)));
    }

    @Test
    public void aStackGapHoldTargetsTheWindowAboveOrBelowThatGap() {
        ScrollingLayoutPolicy.State s = twoColumns(true);
        ScrollingLayoutPolicy.Placement upper = ScrollingLayoutPolicy.placementOf(s, ROOM, 2L);
        float gapTop = 81f + upper.bottom;
        float x = 31f + upper.left + 50f;
        long above = TilingInput.gapTarget(s, zone(s, x, gapTop + 1f, 0f));
        long below = TilingInput.gapTarget(s, zone(s, x, gapTop + ROOM.gap - 1f, 0f));
        assertEquals(upper.windowId, above);
        assertEquals(ScrollingLayoutPolicy.placementOf(s, ROOM, 3L).windowId, below);
    }

    @Test
    public void noGapTargetOutsideAGapOrWhenItsColumnIsGone() {
        ScrollingLayoutPolicy.State s = twoColumns(false);
        assertEquals(ScrollingLayoutPolicy.NO_WINDOW, TilingInput.gapTarget(s, zone(s, 10f, 1000f, 0f)));
        assertEquals(ScrollingLayoutPolicy.NO_WINDOW, TilingInput.gapTarget(s, zone(s, 300f, 1000f, 0f)));
        ScrollingLayoutPolicy.State one = ScrollingLayoutPolicy.State.EMPTY;
        TilingInput.Zone gone = new TilingInput.Zone(TilingInput.Zone.Kind.COLUMN_GAP, BorderDrag.Border.RIGHT, 4, 0f, 0f);
        assertEquals(ScrollingLayoutPolicy.NO_WINDOW, TilingInput.gapTarget(one, gone));
    }

    @Test
    public void aGapsCentreFollowsTheCurrentLayoutAndScroll() {
        ScrollingLayoutPolicy.State s = twoColumns(true);
        TilingInput.Zone col = zone(s, 31f + ScrollingLayoutPolicy.placementOf(s, ROOM, 1L).right + 1f, 1000f, 0f);
        ScrollingLayoutPolicy.Layout layout = ScrollingLayoutPolicy.layout(s, ROOM);
        assertEquals(col.centreX, TilingInput.gapCentreX(col, layout, 31f, 0f), 0.01f);
        assertEquals("the strip slid 100px", col.centreX - 100f, TilingInput.gapCentreX(col, layout, 31f, 100f), 0.01f);
        ScrollingLayoutPolicy.Placement upper = ScrollingLayoutPolicy.placementOf(s, ROOM, 2L);
        TilingInput.Zone stack = zone(s, 31f + upper.left + 50f, 81f + upper.bottom + 1f, 0f);
        assertEquals(stack.centreY, TilingInput.gapCentreY(stack, layout, 81f), 0.01f);
        assertEquals("no such column: keeps the zone's own", col.centreX,
            TilingInput.gapCentreX(new TilingInput.Zone(TilingInput.Zone.Kind.COLUMN_GAP, BorderDrag.Border.LEFT, 9,
                col.centreX, 0f), ScrollingLayoutPolicy.layout(ScrollingLayoutPolicy.State.EMPTY, ROOM), 31f, 0f), 0.01f);
    }

    @Test
    public void theLayoutToggleIsTheWorkspacesOnly() {
        assertEquals(TilingInput.Command.TOGGLE_LAYOUT,
            TilingInput.forTool(LauncherToolRegistry.TOOL_WORKSPACE_LAYOUT_TOGGLE, true));
        assertEquals(TilingInput.Command.NONE,
            TilingInput.forTool(LauncherToolRegistry.TOOL_WORKSPACE_LAYOUT_TOGGLE, false));
    }

    @Test
    public void floatAndPinAreTheStacksWhereverTheyComeFrom() {
        // On Home the focused window is a Home page's Float: Pin applies, and Float has nothing to tile.
        for (boolean workspace : new boolean[] {true, false}) {
            assertEquals(TilingInput.Command.FLOAT,
                TilingInput.forTool(LauncherToolRegistry.TOOL_WINDOW_FLOAT, workspace));
            assertEquals(TilingInput.Command.PIN,
                TilingInput.forTool(LauncherToolRegistry.TOOL_WINDOW_PIN, workspace));
        }
    }

    @Test
    public void theFullScreenToolIsTheDesktopsOnlyOnAWorkspace() {
        assertEquals(TilingInput.Command.TOGGLE_FULLSCREEN,
            TilingInput.forTool(LauncherToolRegistry.TOOL_WINDOW_FULLSCREEN, true));
        assertEquals(TilingInput.Command.NONE,
            TilingInput.forTool(LauncherToolRegistry.TOOL_WINDOW_FULLSCREEN, false));
    }

    @Test
    public void betweenDwindleTilesAHoldGoesToTheNearestTileAndATabStripIsContent() {
        TilingStack stack = TilingStack.empty(1);
        for (int i = 0; i < 3; i++) stack.openIn(Place.workspace(1), WindowSpec.shell(null), false);
        stack.joinTab(3, 2);
        DwindleTree tree = DwindleTree.empty();
        tree.sync(DwindleTree.tilesOf(stack.workspace(1)), 1000f, 2000f, 20f);
        DwindleTree.Tiles tiles = tree.layout(1000f, 2000f, 20f, 30f);
        // Room at (10, 10); window 1 above (0..990), the tabbed tile below (1010..2000, strip to 1040).

        TilingInput.Zone above = TilingInput.zoneAtTiles(500f, 10f + 995f, 1020f, 0f, 2020f,
            10f, 10f, 1010f, 2010f, tiles);
        assertEquals(TilingInput.Zone.Kind.TILE_GAP, above.kind);
        assertEquals(BorderDrag.Border.TOP, above.side);
        assertEquals(1, tiles.layout.placements.get(above.column).windowId);

        TilingInput.Zone below = TilingInput.zoneAtTiles(500f, 10f + 1005f, 1020f, 0f, 2020f,
            10f, 10f, 1010f, 2010f, tiles);
        assertEquals(BorderDrag.Border.BOTTOM, below.side);
        assertEquals(3, tiles.layout.placements.get(below.column).windowId);

        assertEquals(TilingInput.Zone.Kind.CONTENT, TilingInput.zoneAtTiles(500f, 10f + 1020f, 1020f, 0f,
            2020f, 10f, 10f, 1010f, 2010f, tiles).kind);
        assertEquals(TilingInput.Zone.Kind.CONTENT, TilingInput.zoneAtTiles(500f, 500f, 1020f, 0f,
            2020f, 10f, 10f, 1010f, 2010f, tiles).kind);
        assertEquals(TilingInput.Zone.Kind.OUTER, TilingInput.zoneAtTiles(5f, 500f, 1020f, 0f,
            2020f, 10f, 10f, 1010f, 2010f, tiles).kind);
    }

    @Test
    public void theLeadersMovesAndTabGroupAreTheWorkspacesOnly() {
        assertEquals(TilingInput.Command.MOVE_LEFT,
            TilingInput.forTool(LauncherToolRegistry.TOOL_WINDOW_MOVE_LEFT, true));
        assertEquals(TilingInput.Command.MOVE_RIGHT,
            TilingInput.forTool(LauncherToolRegistry.TOOL_WINDOW_MOVE_RIGHT, true));
        assertEquals(TilingInput.Command.MOVE_UP,
            TilingInput.forTool(LauncherToolRegistry.TOOL_WINDOW_MOVE_UP, true));
        assertEquals(TilingInput.Command.MOVE_DOWN,
            TilingInput.forTool(LauncherToolRegistry.TOOL_WINDOW_MOVE_DOWN, true));
        assertEquals(TilingInput.Command.TAB, TilingInput.forTool(LauncherToolRegistry.TOOL_WINDOW_TAB, true));
        assertEquals(TilingInput.Command.NONE, TilingInput.forTool(LauncherToolRegistry.TOOL_WINDOW_TAB, false));
        assertEquals(TilingInput.Command.NONE,
            TilingInput.forTool(LauncherToolRegistry.TOOL_WINDOW_MOVE_UP, false));
    }

    @Test
    public void workspaceNumbersComeOutOfTheNumberedTools() {
        assertEquals(1, TilingInput.workspaceToGo("workspace.go.1"));
        assertEquals(9, TilingInput.workspaceToGo("workspace.go.9"));
        assertEquals(0, TilingInput.workspaceToGo("workspace.go.0"));
        assertEquals(0, TilingInput.workspaceToGo("workspace.go.10"));
        assertEquals(0, TilingInput.workspaceToGo("window.send.2"));
        assertEquals(0, TilingInput.workspaceToGo(null));
        assertEquals(3, TilingInput.workspaceToSendTo("window.send.3"));
        assertEquals(0, TilingInput.workspaceToSendTo("workspace.go.3"));
    }
}
