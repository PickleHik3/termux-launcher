package com.termux.app.tiling;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.wall.BorderDrag;
import com.termux.launcherctl.LauncherToolRegistry;

/**
 * The tracer's ways into the stack's moves (#418), before the control bar (#424), the leader
 * (#438) and the four-edge border hold (#432) replace them:
 *
 * <ul>
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
    public enum Command { NONE, LEFT, RIGHT, UP, DOWN, NEW_TERMINAL, NEW_TERMINAL_LEFT, SPLIT, CLOSE, TOGGLE_LAYOUT,
        FLOAT, PIN, TOGGLE_FULLSCREEN, MOVE_LEFT, MOVE_RIGHT, MOVE_UP, MOVE_DOWN, TAB,
        // Round 3's chord table (K1-K10).
        SPLIT_OTHER, CONSUME_LEFT, CONSUME_RIGHT, WIDTH_NEXT, WIDTH_PREVIOUS, NARROWER, WIDER, SHORTER, TALLER,
        WORKSPACE_UP, WORKSPACE_DOWN, POP_OUT, FULL_WIDTH, CENTRE, FOCUS_FIRST, FOCUS_LAST }

    private TilingInput() {}

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
            // Round 3: a new window left of the focused one, and Split (from Home both open the
            // waiting Workspace, as a new window does).
            case LauncherToolRegistry.TOOL_WINDOW_NEW_LEFT:
                return Command.NEW_TERMINAL_LEFT;
            case LauncherToolRegistry.TOOL_WINDOW_SPLIT:
                return Command.SPLIT;
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
            case LauncherToolRegistry.TOOL_WORKSPACE_LAYOUT_TOGGLE:
                return workspaceInFront ? Command.TOGGLE_LAYOUT : Command.NONE;
            // The focused window floats or tiles, and a Float pins or unpins (#421): on Home too,
            // where the focused window is a Home page's Float. Only the desktop runs them.
            case LauncherToolRegistry.TOOL_WINDOW_FLOAT:
                return Command.FLOAT;
            case LauncherToolRegistry.TOOL_WINDOW_PIN:
                return Command.PIN;
            case LauncherToolRegistry.TOOL_WINDOW_FULLSCREEN:
                return workspaceInFront ? Command.TOGGLE_FULLSCREEN : Command.NONE;
            // The leader's window moves and tab group (#439): a Workspace's tiles only.
            case LauncherToolRegistry.TOOL_WINDOW_MOVE_LEFT:
                return workspaceInFront ? Command.MOVE_LEFT : Command.NONE;
            case LauncherToolRegistry.TOOL_WINDOW_MOVE_RIGHT:
                return workspaceInFront ? Command.MOVE_RIGHT : Command.NONE;
            case LauncherToolRegistry.TOOL_WINDOW_MOVE_UP:
                return workspaceInFront ? Command.MOVE_UP : Command.NONE;
            case LauncherToolRegistry.TOOL_WINDOW_MOVE_DOWN:
                return workspaceInFront ? Command.MOVE_DOWN : Command.NONE;
            case LauncherToolRegistry.TOOL_WINDOW_TAB:
                return workspaceInFront ? Command.TAB : Command.NONE;
            // Round 3's chord table (K1-K10): a Workspace's windows, except that Split the other
            // way opens from Home as a new window does, and the Workspace steps work from Home.
            case LauncherToolRegistry.TOOL_WINDOW_SPLIT_OTHER:
                return Command.SPLIT_OTHER;
            case LauncherToolRegistry.TOOL_WINDOW_FOCUS_UP:
                return workspaceInFront ? Command.UP : Command.NONE;
            case LauncherToolRegistry.TOOL_WINDOW_FOCUS_DOWN:
                return Command.DOWN;
            case LauncherToolRegistry.TOOL_WORKSPACE_PREVIOUS:
                return workspaceInFront ? Command.WORKSPACE_UP : Command.NONE;
            case LauncherToolRegistry.TOOL_WORKSPACE_NEXT:
                return Command.WORKSPACE_DOWN;
            case LauncherToolRegistry.TOOL_WINDOW_CONSUME_LEFT:
                return workspaceInFront ? Command.CONSUME_LEFT : Command.NONE;
            case LauncherToolRegistry.TOOL_WINDOW_CONSUME_RIGHT:
                return workspaceInFront ? Command.CONSUME_RIGHT : Command.NONE;
            case LauncherToolRegistry.TOOL_WINDOW_WIDTH_NEXT:
                return workspaceInFront ? Command.WIDTH_NEXT : Command.NONE;
            case LauncherToolRegistry.TOOL_WINDOW_WIDTH_PREVIOUS:
                return workspaceInFront ? Command.WIDTH_PREVIOUS : Command.NONE;
            case LauncherToolRegistry.TOOL_WINDOW_NARROWER:
                return workspaceInFront ? Command.NARROWER : Command.NONE;
            case LauncherToolRegistry.TOOL_WINDOW_WIDER:
                return workspaceInFront ? Command.WIDER : Command.NONE;
            case LauncherToolRegistry.TOOL_WINDOW_SHORTER:
                return workspaceInFront ? Command.SHORTER : Command.NONE;
            case LauncherToolRegistry.TOOL_WINDOW_TALLER:
                return workspaceInFront ? Command.TALLER : Command.NONE;
            case LauncherToolRegistry.TOOL_WINDOW_POP_OUT:
                return workspaceInFront ? Command.POP_OUT : Command.NONE;
            case LauncherToolRegistry.TOOL_WINDOW_FULL_WIDTH:
                return workspaceInFront ? Command.FULL_WIDTH : Command.NONE;
            case LauncherToolRegistry.TOOL_WINDOW_CENTRE:
                return workspaceInFront ? Command.CENTRE : Command.NONE;
            case LauncherToolRegistry.TOOL_WINDOW_FOCUS_FIRST:
                return workspaceInFront ? Command.FOCUS_FIRST : Command.NONE;
            case LauncherToolRegistry.TOOL_WINDOW_FOCUS_LAST:
                return workspaceInFront ? Command.FOCUS_LAST : Command.NONE;
            default:
                return Command.NONE;
        }
    }

    /** The Workspace number 1…10 that {@code tool} ({@code workspace.go.N}) goes to, or 0 for another tool. */
    public static int workspaceToGo(@Nullable String tool) {
        return numbered(tool, LauncherToolRegistry.TOOL_WORKSPACE_GO_PREFIX);
    }

    /** The Workspace number 1…9 that {@code tool} ({@code window.send.N}) sends the window to, or 0. */
    public static int workspaceToSendTo(@Nullable String tool) {
        return numbered(tool, LauncherToolRegistry.TOOL_WINDOW_SEND_PREFIX);
    }

    private static int numbered(@Nullable String tool, @NonNull String prefix) {
        if (tool == null || !tool.startsWith(prefix)) return 0;
        String number = tool.substring(prefix.length());
        // 1 to 9, and 10 (leader+0, K7).
        if (number.equals("10")) return 10;
        if (number.length() != 1) return 0;
        char digit = number.charAt(0);
        return digit >= '1' && digit <= '9' ? digit - '0' : 0;
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
            /** The gap between a column's two stacked windows: a hold steps up or down, a drag resizes. */
            STACK_GAP,
            /**
             * Air between Dwindle tiles: a hold steps to the tile on the held side; on a split's
             * gap ({@link #split} 0 or more) a drag resizes.
             */
            TILE_GAP,
            /** Anything else, a window's body above all: the touch belongs to it. */
            CONTENT
        }

        static final Zone NONE = new Zone(Kind.CONTENT, BorderDrag.Border.NONE, -1, 0f, 0f);

        /** For a TILE_GAP on a split's gap, the split ({@link DwindleTree.Gap#split}); else -1. */
        public final int split;
        /** For that split, whether its sides stand side by side (its gap moves sideways). */
        public final boolean sideBySide;

        @NonNull public final Kind kind;
        /** The way a hold steps: the border for OUTER, the nearer side for a gap, NONE for CONTENT. */
        @NonNull public final BorderDrag.Border side;
        /** The left column of a COLUMN_GAP, the column of a STACK_GAP, the held tile's index for a TILE_GAP, else -1. */
        public final int column;
        /** The middle of a gap in the view's coordinates; 0 elsewhere. */
        public final float centreX;
        public final float centreY;

        Zone(@NonNull Kind kind, @NonNull BorderDrag.Border side, int column, float centreX, float centreY) {
            this(kind, side, column, centreX, centreY, -1, false);
        }

        Zone(@NonNull Kind kind, @NonNull BorderDrag.Border side, int column, float centreX, float centreY,
             int split, boolean sideBySide) {
            this.kind = kind;
            this.side = side;
            this.column = column;
            this.centreX = centreX;
            this.centreY = centreY;
            this.split = split;
            this.sideBySide = sideBySide;
        }

        /** Whether a drag here resizes: a gap between windows, never the air round them (round 3). */
        public boolean resizes() {
            return kind == Kind.COLUMN_GAP || kind == Kind.STACK_GAP || (kind == Kind.TILE_GAP && split >= 0);
        }

        /** Whether a drag here moves sideways (a column gap, a side-by-side split) rather than up and down. */
        public boolean dragsSideways() {
            return kind == Kind.COLUMN_GAP || (kind == Kind.TILE_GAP && sideBySide);
        }
    }

    /** A press on the gap right of column {@code leftColumn}, as a drag or a double tap reaches the desktop. */
    @NonNull
    public static Zone columnGap(int leftColumn) {
        return new Zone(Zone.Kind.COLUMN_GAP, BorderDrag.Border.LEFT, leftColumn, 0f, 0f);
    }

    /** A press on the gap between the two windows stacked in column {@code column}. */
    @NonNull
    public static Zone stackGap(int column) {
        return new Zone(Zone.Kind.STACK_GAP, BorderDrag.Border.TOP, column, 0f, 0f);
    }

    /** A press on Dwindle split {@code split}'s gap, its sides {@code sideBySide} or top over bottom. */
    @NonNull
    public static Zone splitGap(int split, boolean sideBySide) {
        return new Zone(Zone.Kind.TILE_GAP, sideBySide ? BorderDrag.Border.LEFT : BorderDrag.Border.TOP, -1,
            0f, 0f, split, sideBySide);
    }

    /** The touch strip every gap between windows takes, in dp, centred on the gap (round 3). */
    public static final float GAP_STRIP_DP = 24f;

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
        return zoneAt(x, y, width, top, bottom, roomLeft, roomTop, roomRight, roomBottom, layout, scroll, 0f);
    }

    /**
     * {@link #zoneAt} with each gap between windows taking a strip {@code stripPx} across, centred
     * on it ({@link #GAP_STRIP_DP}; never less than the gap itself), so a finger finds a gap
     * narrower than itself: the strip's edges stand over the windows' own edges.
     */
    @NonNull
    public static Zone zoneAt(float x, float y, float width, float top, float bottom,
                              float roomLeft, float roomTop, float roomRight, float roomBottom,
                              @NonNull ScrollingLayoutPolicy.Layout layout, float scroll, float stripPx) {
        float catchPx = Math.max(0f, stripPx) / 2f;
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
                // The gap right of the previous column, up to this one, with its strip.
                float mid = previous == null ? 0f : (previous.right + p.left) / 2f;
                float half = previous == null ? 0f : Math.max(catchPx, (p.left - previous.right) / 2f);
                if (previous != null && px >= mid - half && px < mid + half) {
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
                float mid = (upper.bottom + lower.top) / 2f;
                float half = Math.max(catchPx, (lower.top - upper.bottom) / 2f);
                if (py >= mid - half && py < mid + half) {
                    return new Zone(Zone.Kind.STACK_GAP,
                        py < mid ? BorderDrag.Border.TOP : BorderDrag.Border.BOTTOM, upper.column,
                        x, roomTop + mid);
                }
            }
        }
        return Zone.NONE;
    }

    /**
     * {@link #zoneAt} for a Dwindle Workspace laid out as {@code tiles}: the air round the room is
     * OUTER; inside it a press on no tile and no tab strip is a TILE_GAP, held toward the nearest
     * tile (its index in {@code tiles}' placements, the side it lies on); a tile or a tab strip is
     * CONTENT.
     */
    @NonNull
    public static Zone zoneAtTiles(float x, float y, float width, float top, float bottom,
                                   float roomLeft, float roomTop, float roomRight, float roomBottom,
                                   @NonNull DwindleTree.Tiles tiles) {
        return zoneAtTiles(x, y, width, top, bottom, roomLeft, roomTop, roomRight, roomBottom, tiles, 0f);
    }

    /**
     * {@link #zoneAtTiles} with each split's gap taking a strip {@code stripPx} across
     * ({@link #GAP_STRIP_DP}): a press in it is a TILE_GAP naming its split, held toward the tile on
     * the press's side of the gap, even where the strip lies over that tile's edge.
     */
    @NonNull
    public static Zone zoneAtTiles(float x, float y, float width, float top, float bottom,
                                   float roomLeft, float roomTop, float roomRight, float roomBottom,
                                   @NonNull DwindleTree.Tiles tiles, float stripPx) {
        BorderDrag.Border border = holdAt(x, y, width, top, bottom, roomLeft, roomTop, roomRight, roomBottom);
        if (border != BorderDrag.Border.NONE) return new Zone(Zone.Kind.OUTER, border, -1, 0f, 0f);
        if (x < roomLeft || x >= roomRight || y < roomTop || y >= roomBottom || y < top || y >= bottom) {
            return Zone.NONE;
        }
        float px = x - roomLeft;
        float py = y - roomTop;
        for (DwindleTree.TabStrip strip : tiles.strips) {
            if (px >= strip.left && px < strip.right && py >= strip.top && py < strip.bottom) return Zone.NONE;
        }
        DwindleTree.Gap gap = tiles.gapAt(px, py, Math.max(0f, stripPx) / 2f);
        java.util.List<ScrollingLayoutPolicy.Placement> laid = tiles.layout.placements;
        if (gap != null) {
            // The tile on the press's side of the gap, nearest the press: the one a hold steps to.
            boolean before = gap.sideBySide ? px < gap.centre() : py < gap.centre();
            int target = -1;
            float targetDistance = Float.MAX_VALUE;
            for (int i = 0; i < laid.size(); i++) {
                ScrollingLayoutPolicy.Placement p = laid.get(i);
                boolean onSide = gap.sideBySide ? (before ? p.right <= gap.centre() : p.left >= gap.centre())
                    : (before ? p.bottom <= gap.centre() : p.top >= gap.centre());
                if (!onSide) continue;
                float dx = px < p.left ? p.left - px : (px >= p.right ? px - p.right : 0f);
                float dy = py < p.top ? p.top - py : (py >= p.bottom ? py - p.bottom : 0f);
                float distance = Math.max(dx, dy);
                if (distance < targetDistance) {
                    targetDistance = distance;
                    target = i;
                }
            }
            BorderDrag.Border side = gap.sideBySide ? (before ? BorderDrag.Border.LEFT : BorderDrag.Border.RIGHT)
                : (before ? BorderDrag.Border.TOP : BorderDrag.Border.BOTTOM);
            return new Zone(Zone.Kind.TILE_GAP, side, target, x, y, gap.split, gap.sideBySide);
        }
        int nearest = -1;
        float nearestDistance = Float.MAX_VALUE;
        BorderDrag.Border side = BorderDrag.Border.NONE;
        java.util.List<ScrollingLayoutPolicy.Placement> placements = laid;
        for (int i = 0; i < placements.size(); i++) {
            ScrollingLayoutPolicy.Placement p = placements.get(i);
            float tileTop = p.top;
            for (DwindleTree.TabStrip strip : tiles.strips) {
                if (strip.left == p.left && strip.right == p.right && strip.bottom == p.top) tileTop = strip.top;
            }
            float dx = px < p.left ? p.left - px : (px >= p.right ? px - p.right : 0f);
            float dy = py < tileTop ? tileTop - py : (py >= p.bottom ? py - p.bottom : 0f);
            if (dx == 0f && dy == 0f) return Zone.NONE;
            float distance = Math.max(dx, dy);
            if (distance >= nearestDistance) continue;
            nearest = i;
            nearestDistance = distance;
            if (dx >= dy) side = px < p.left ? BorderDrag.Border.RIGHT : BorderDrag.Border.LEFT;
            else side = py < tileTop ? BorderDrag.Border.BOTTOM : BorderDrag.Border.TOP;
        }
        if (nearest < 0) return Zone.NONE;
        return new Zone(Zone.Kind.TILE_GAP, side, nearest, x, y);
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
