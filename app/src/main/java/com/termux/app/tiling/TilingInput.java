package com.termux.app.tiling;

import android.view.KeyEvent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.wall.BorderDrag;
import com.termux.launcherctl.LauncherToolRegistry;

/**
 * The tracer's ways into the stack's moves (#418), before the control bar (#424), the leader
 * (#438) and the four-edge border hold (#432) replace them:
 *
 * <ul>
 *   <li>a hardware keyboard's Ctrl+Alt with an arrow (focus that way) or Enter (a new terminal),
 *       the spec's leader bindings with the hardware leader;</li>
 *   <li>v1's own window and session tools, wherever they come from (a key binding, the space
 *       bar's swipes, the palette, launcherctl): New window and New session open a terminal,
 *       Next and Previous window move sideways, Next and Previous session move down and up, Close
 *       window closes the focused one;</li>
 *   <li>on Home, a two-finger swipe up while that gesture is set to nothing, or any Home gesture
 *       set to the Terminal, goes down to the Workspace below;</li>
 *   <li>a still finger on an edge band of a Workspace ({@link #holdAt}), the border hold.</li>
 * </ul>
 *
 * <p>Pure: no Android views.
 */
public final class TilingInput {

    /** A move of the stack, or nothing for input that stays where it was going. */
    public enum Command { NONE, LEFT, RIGHT, UP, DOWN, NEW_TERMINAL, CLOSE }

    private TilingInput() {}

    /** The command a key press is, with the modifiers held; Ctrl+Alt alone, no Shift or Meta. */
    @NonNull
    public static Command forKey(int keyCode, boolean ctrl, boolean alt, boolean shift, boolean meta) {
        if (!ctrl || !alt || shift || meta) return Command.NONE;
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT: return Command.LEFT;
            case KeyEvent.KEYCODE_DPAD_RIGHT: return Command.RIGHT;
            case KeyEvent.KEYCODE_DPAD_UP: return Command.UP;
            case KeyEvent.KEYCODE_DPAD_DOWN: return Command.DOWN;
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER: return Command.NEW_TERMINAL;
            default: return Command.NONE;
        }
    }

    /**
     * The command v1's tool {@code tool} is while the tiling desktop is on. On Home the stack takes
     * only the new terminal and the step down; the rest stay Home's.
     */
    @NonNull
    public static Command forTool(@Nullable String tool, boolean workspaceInFront) {
        if (tool == null) return Command.NONE;
        switch (tool) {
            case LauncherToolRegistry.TOOL_WINDOW_NEW:
            case LauncherToolRegistry.TOOL_SESSION_NEW:
                return Command.NEW_TERMINAL;
            case LauncherToolRegistry.TOOL_SESSION_NEXT:
                return Command.DOWN;
            case LauncherToolRegistry.TOOL_SESSION_PREVIOUS:
                return workspaceInFront ? Command.UP : Command.NONE;
            case LauncherToolRegistry.TOOL_WINDOW_NEXT:
                return workspaceInFront ? Command.RIGHT : Command.NONE;
            case LauncherToolRegistry.TOOL_WINDOW_PREVIOUS:
                return workspaceInFront ? Command.LEFT : Command.NONE;
            case LauncherToolRegistry.TOOL_WINDOW_CLOSE:
                return workspaceInFront ? Command.CLOSE : Command.NONE;
            default:
                return Command.NONE;
        }
    }

    /**
     * Whether a gesture on Home's free area goes down to the Workspace below: a two-finger swipe
     * up set to nothing ({@code twoFingerUp}, {@code setToNothing}), or any gesture set to the
     * Terminal ({@code setToTerminal}). Anything else runs what it is set to.
     */
    public static boolean homeGestureGoesDown(boolean twoFingerUp, boolean setToNothing,
                                              boolean setToTerminal) {
        return setToTerminal || (twoFingerUp && setToNothing);
    }

    /**
     * The border a press at ({@code x}, {@code y}) holds on a Workspace view {@code width} wide:
     * the air around the windows' room ({@code roomLeft} … {@code roomBottom}, the very bounds the
     * windows are laid out in, so no window's pixel is ever a hold), between Android's bars
     * ({@code top} … {@code bottom}). The corners, where two bands meet, under the bars, and
     * everything inside the room are {@link BorderDrag.Border#NONE}.
     */
    @NonNull
    public static BorderDrag.Border holdAt(float x, float y, float width, float top, float bottom,
                                           float roomLeft, float roomTop, float roomRight, float roomBottom) {
        if (width <= 0f || bottom <= top || x < 0f || x >= width || y < top || y >= bottom) {
            return BorderDrag.Border.NONE;
        }
        boolean left = x < roomLeft;
        boolean right = x >= roomRight;
        boolean above = y < roomTop;
        boolean below = y >= roomBottom;
        if ((left || right) && (above || below)) return BorderDrag.Border.NONE;
        if (above) return BorderDrag.Border.TOP;
        if (below) return BorderDrag.Border.BOTTOM;
        if (left) return BorderDrag.Border.LEFT;
        if (right) return BorderDrag.Border.RIGHT;
        return BorderDrag.Border.NONE;
    }
}
