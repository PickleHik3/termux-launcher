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
    public void ctrlAltArrowsMoveAndCtrlAltEnterOpensATerminal() {
        assertEquals(TilingInput.Command.LEFT, key(KeyEvent.KEYCODE_DPAD_LEFT, true, true, false));
        assertEquals(TilingInput.Command.RIGHT, key(KeyEvent.KEYCODE_DPAD_RIGHT, true, true, false));
        assertEquals(TilingInput.Command.UP, key(KeyEvent.KEYCODE_DPAD_UP, true, true, false));
        assertEquals(TilingInput.Command.DOWN, key(KeyEvent.KEYCODE_DPAD_DOWN, true, true, false));
        assertEquals(TilingInput.Command.NEW_TERMINAL, key(KeyEvent.KEYCODE_ENTER, true, true, false));
        assertEquals(TilingInput.Command.NEW_TERMINAL, key(KeyEvent.KEYCODE_NUMPAD_ENTER, true, true, false));
    }

    @Test
    public void otherChordsStayTheAppsOrTheTerminals() {
        assertEquals(TilingInput.Command.NONE, key(KeyEvent.KEYCODE_DPAD_LEFT, true, false, false));
        assertEquals(TilingInput.Command.NONE, key(KeyEvent.KEYCODE_DPAD_LEFT, false, true, false));
        assertEquals(TilingInput.Command.NONE, key(KeyEvent.KEYCODE_DPAD_LEFT, true, true, true));
        assertEquals(TilingInput.Command.NONE, key(KeyEvent.KEYCODE_A, true, true, false));
        assertEquals(TilingInput.Command.NONE,
            TilingInput.forKey(KeyEvent.KEYCODE_DPAD_LEFT, true, true, false, true));
    }

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

    private static TilingInput.Command key(int keyCode, boolean ctrl, boolean alt, boolean shift) {
        return TilingInput.forKey(keyCode, ctrl, alt, shift, false);
    }
}
