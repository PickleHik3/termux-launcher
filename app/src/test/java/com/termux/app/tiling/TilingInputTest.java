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
    public void theEdgeBandsAreTheBordersAndTheCornersAreNone() {
        float w = 1000f, h = 2000f, band = 30f;
        assertEquals(BorderDrag.Border.LEFT, TilingInput.edgeAt(5f, 1000f, w, h, band));
        assertEquals(BorderDrag.Border.RIGHT, TilingInput.edgeAt(990f, 1000f, w, h, band));
        assertEquals(BorderDrag.Border.TOP, TilingInput.edgeAt(500f, 10f, w, h, band));
        assertEquals(BorderDrag.Border.BOTTOM, TilingInput.edgeAt(500f, 1990f, w, h, band));
        assertEquals(BorderDrag.Border.NONE, TilingInput.edgeAt(500f, 1000f, w, h, band));
        assertEquals(BorderDrag.Border.NONE, TilingInput.edgeAt(5f, 5f, w, h, band));
        assertEquals(BorderDrag.Border.NONE, TilingInput.edgeAt(995f, 1995f, w, h, band));
        assertEquals(BorderDrag.Border.NONE, TilingInput.edgeAt(-1f, 1000f, w, h, band));
        assertEquals(BorderDrag.Border.NONE, TilingInput.edgeAt(5f, 1000f, 0f, 0f, band));
    }

    private static TilingInput.Command key(int keyCode, boolean ctrl, boolean alt, boolean shift) {
        return TilingInput.forKey(keyCode, ctrl, alt, shift, false);
    }
}
