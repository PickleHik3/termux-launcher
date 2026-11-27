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

    private static TilingInput.Command key(int keyCode, boolean ctrl, boolean alt, boolean shift) {
        return TilingInput.forKey(keyCode, ctrl, alt, shift, false);
    }
}
