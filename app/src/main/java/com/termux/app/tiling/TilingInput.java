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
 *   <li>a still finger on an edge band of a Workspace or on a gap between its tiles
 *       ({@link #zoneAt}), the border hold; a finger that drags on a gap between two columns
 *       resizes it.</li>
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

    /** The zone of nothing held: CONTENT. */
    @NonNull
    public static Zone zoneNone() {
        return Zone.NONE;
    }

    /** What lies under a press on a Workspace: see {@link #zoneAt}. */
    public static final class Zone {
        public enum Kind {
            /** The air around the windows' room: a border hold. */
            OUTER,
            /** The gap between two columns: a hold steps sideways, a drag resizes. */
            COLUMN_GAP,
            /** The gap between a column's two stacked windows: a hold steps up or down. */
            STACK_GAP,
            /** Anything else, a window's body above all: the touch belongs to it. */
            CONTENT
        }

        static final Zone NONE = new Zone(Kind.CONTENT, BorderDrag.Border.NONE, -1, 0f, 0f);

        @NonNull public final Kind kind;
        /** The way a hold steps: the border for OUTER, the nearer side for a gap, NONE for CONTENT. */
        @NonNull public final BorderDrag.Border side;
        /** The left column of a COLUMN_GAP, the column of a STACK_GAP, else -1. */
        public final int column;
        /** The middle of a gap in the view's coordinates; 0 elsewhere. */
        public final float centreX;
        public final float centreY;

        Zone(@NonNull Kind kind, @NonNull BorderDrag.Border side, int column, float centreX, float centreY) {
            this.kind = kind;
            this.side = side;
            this.column = column;
            this.centreX = centreX;
            this.centreY = centreY;
        }
    }

    /**
     * The zone a press at ({@code x}, {@code y}) lands in on a Workspace view {@code width} wide,
     * between Android's bars ({@code top} … {@code bottom}), whose windows stand in the room
     * {@code roomLeft} … {@code roomBottom} laid out as {@code layout} and scrolled by
     * {@code scroll} px. The air round the room is OUTER (as {@link #holdAt}, corners excluded);
     * inside it only the gaps between tiles are not CONTENT. A window's pixels never are a gap.
     */
    @NonNull
    public static Zone zoneAt(float x, float y, float width, float top, float bottom,
                              float roomLeft, float roomTop, float roomRight, float roomBottom,
                              @NonNull ScrollingLayoutPolicy.Layout layout, float scroll) {
        BorderDrag.Border border = holdAt(x, y, width, top, bottom, roomLeft, roomTop, roomRight, roomBottom);
        if (border != BorderDrag.Border.NONE) return new Zone(Zone.Kind.OUTER, border, -1, 0f, 0f);
        if (x < roomLeft || x >= roomRight || y < roomTop || y >= roomBottom || y < top || y >= bottom) {
            return Zone.NONE;
        }
        float px = x - roomLeft + scroll;
        float py = y - roomTop;
        ScrollingLayoutPolicy.Placement previous = null;
        for (ScrollingLayoutPolicy.Placement p : layout.placements) {
            if (p.row == 0) {
                // The gap right of the previous column, up to this one.
                if (previous != null && px >= previous.right && px < p.left) {
                    float mid = (previous.right + p.left) / 2f;
                    return new Zone(Zone.Kind.COLUMN_GAP,
                        px < mid ? BorderDrag.Border.LEFT : BorderDrag.Border.RIGHT, previous.column,
                        roomLeft + mid - scroll, y);
                }
                previous = p;
            }
        }
        for (ScrollingLayoutPolicy.Placement upper : layout.placements) {
            if (upper.row != 0 || px < upper.left || px >= upper.right) continue;
            for (ScrollingLayoutPolicy.Placement lower : layout.placements) {
                if (lower.column != upper.column || lower.row != 1) continue;
                if (py >= upper.bottom && py < lower.top) {
                    float mid = (upper.bottom + lower.top) / 2f;
                    return new Zone(Zone.Kind.STACK_GAP,
                        py < mid ? BorderDrag.Border.TOP : BorderDrag.Border.BOTTOM, upper.column,
                        x, roomTop + mid);
                }
            }
        }
        return Zone.NONE;
    }

    /**
     * The window a hold on gap {@code zone} acts on, or {@link ScrollingLayoutPolicy#NO_WINDOW}.
     * A column gap: the window in the column beside the gap on the held side (its focused row when
     * focus is already in that column, else the top one). A stack gap: the window above or below
     * that very gap. Anything else has none.
     */
    public static long gapTarget(@NonNull ScrollingLayoutPolicy.State state, @NonNull Zone zone) {
        if (zone.kind == Zone.Kind.COLUMN_GAP) {
            int index = zone.side == BorderDrag.Border.LEFT ? zone.column : zone.column + 1;
            if (index < 0 || index >= state.columnCount()) return ScrollingLayoutPolicy.NO_WINDOW;
            ScrollingLayoutPolicy.Column column = state.column(index);
            for (int row = 0; row < column.windowCount(); row++) {
                if (column.windowAt(row) == state.focusedWindow) return state.focusedWindow;
            }
            return column.windowAt(0);
        }
        if (zone.kind == Zone.Kind.STACK_GAP) {
            if (zone.column < 0 || zone.column >= state.columnCount()) return ScrollingLayoutPolicy.NO_WINDOW;
            ScrollingLayoutPolicy.Column column = state.column(zone.column);
            if (column.windowCount() < 2) return ScrollingLayoutPolicy.NO_WINDOW;
            return column.windowAt(zone.side == BorderDrag.Border.TOP ? 0 : 1);
        }
        return ScrollingLayoutPolicy.NO_WINDOW;
    }

    /** A column gap's middle in the view now ({@code layout} scrolled by {@code scroll}); the zone's own if its column is gone. */
    public static float gapCentreX(@NonNull Zone zone, @NonNull ScrollingLayoutPolicy.Layout layout,
                                   float roomLeft, float scroll) {
        ScrollingLayoutPolicy.Placement left = null;
        ScrollingLayoutPolicy.Placement right = null;
        for (ScrollingLayoutPolicy.Placement p : layout.placements) {
            if (p.row != 0) continue;
            if (p.column == zone.column) left = p;
            if (p.column == zone.column + 1) right = p;
        }
        if (left == null || right == null) return zone.centreX;
        return roomLeft + (left.right + right.left) / 2f - scroll;
    }

    /** A stack gap's middle in the view now; the zone's own if its column is gone. */
    public static float gapCentreY(@NonNull Zone zone, @NonNull ScrollingLayoutPolicy.Layout layout, float roomTop) {
        ScrollingLayoutPolicy.Placement upper = null;
        ScrollingLayoutPolicy.Placement lower = null;
        for (ScrollingLayoutPolicy.Placement p : layout.placements) {
            if (p.column != zone.column) continue;
            if (p.row == 0) upper = p;
            if (p.row == 1) lower = p;
        }
        if (upper == null || lower == null) return zone.centreY;
        return roomTop + (upper.bottom + lower.top) / 2f;
    }
}
