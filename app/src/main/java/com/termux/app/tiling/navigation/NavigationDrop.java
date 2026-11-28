package com.termux.app.tiling.navigation;

import androidx.annotation.NonNull;

import com.termux.app.tiling.TilingStack;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;

/**
 * Where a window carried in navigation mode lands (#433; spec "Moving around"): dropped on another
 * window's left, right, top or bottom half it takes that side; in its centre it joins it as a tab;
 * in a gap between columns it becomes a column of its own there. A place with no windows takes it
 * as its first.
 *
 * <p>Read from the page under the finger: its windows where they stand on its strip
 * ({@link Tile}), in the strip's coordinates, and the room it shows. Under Scrolling the band along
 * each column's edge, {@code catchPx} into the windows beside it, is the gap; left and right
 * halves are the same column slots. A column already holding {@link TilingStack#MAX_STACKED}
 * windows takes no other above or below. Under Dwindle there are no columns: a finger in a gap
 * goes to the nearest tile's facing side. Each answer carries what to light while the finger is
 * there. A drop that would leave everything as it stands is {@link Type#NONE}.
 *
 * <p>Pure: no Android views.
 */
public final class NavigationDrop {

    private NavigationDrop() {}

    /** No window: a drop into a place with none. */
    public static final long NO_WINDOW = -1L;

    /** The share of a window's half-width and half-height around its middle that is its centre. */
    static final float CENTRE = 0.4f;

    /** What a drop does. */
    public enum Type {
        /** Nothing changes. */
        NONE,
        /** It takes {@link Drop#side} of {@link Drop#targetId}. */
        SIDE,
        /** It joins {@link Drop#targetId}'s tile as a tab. */
        TAB,
        /**
         * It becomes a column of its own on {@link Drop#side} (left or right) of
         * {@link Drop#targetId}'s column; with {@link #NO_WINDOW} the place's first.
         */
        COLUMN
    }

    /** A side of a window. */
    public enum Side { LEFT, TOP, RIGHT, BOTTOM }

    /** One window where it stands on its page's strip. */
    public static final class Tile {
        public final long windowId;
        /** Its column under Scrolling (stacked windows share one); its tile's index under Dwindle. */
        public final int column;
        public final float left;
        public final float top;
        public final float right;
        public final float bottom;

        public Tile(long windowId, int column, float left, float top, float right, float bottom) {
            this.windowId = windowId;
            this.column = column;
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }
    }

    /** What a drop does, and what is lit while the finger is there, in the strip's coordinates. */
    public static final class Drop {
        @NonNull public final Type type;
        public final long targetId;
        @NonNull public final Side side;
        public final float left;
        public final float top;
        public final float right;
        public final float bottom;

        Drop(@NonNull Type type, long targetId, @NonNull Side side, float left, float top, float right,
             float bottom) {
            this.type = type;
            this.targetId = targetId;
            this.side = side;
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }

        static final Drop NOTHING = new Drop(Type.NONE, NO_WINDOW, Side.LEFT, 0f, 0f, 0f, 0f);
    }

    /**
     * What letting go of {@code carried} at ({@code x}, {@code y}) on a page does: its windows
     * {@code tiles}, laid out by Dwindle when {@code dwindle}, the room it shows {@code span}
     * ({left, top, right, bottom}), and gaps that catch {@code catchPx} into the windows beside them.
     */
    @NonNull
    public static Drop at(@NonNull List<Tile> tiles, boolean dwindle, long carried, float x, float y,
                          float catchPx, @NonNull float[] span) {
        if (tiles.isEmpty()) return new Drop(Type.COLUMN, NO_WINDOW, Side.LEFT, span[0], span[1], span[2], span[3]);
        if (dwindle) {
            Tile target = nearest(tiles, x, y);
            return target.windowId == carried ? Drop.NOTHING : onWindow(target, x, y);
        }
        // The columns left to right; each one's windows top to bottom.
        TreeMap<Integer, List<Tile>> columns = new TreeMap<>();
        for (Tile tile : tiles) {
            List<Tile> column = columns.get(tile.column);
            if (column == null) columns.put(tile.column, column = new ArrayList<>());
            column.add(tile);
        }
        List<List<Tile>> ordered = new ArrayList<>(columns.values());
        int n = ordered.size();
        int carriedColumn = -1;
        boolean carriedAlone = false;
        for (int c = 0; c < n; c++) {
            for (Tile tile : ordered.get(c)) {
                if (tile.windowId != carried) continue;
                carriedColumn = c;
                carriedAlone = ordered.get(c).size() == 1;
            }
        }
        // The gaps: before each column, and after the last.
        for (int slot = 0; slot <= n; slot++) {
            float from = slot == 0 ? -Float.MAX_VALUE : ordered.get(slot - 1).get(0).right - catchPx;
            float to = slot == n ? Float.MAX_VALUE : ordered.get(slot).get(0).left + catchPx;
            if (x < from || x > to) continue;
            float centre = slot == 0 ? ordered.get(0).get(0).left
                : slot == n ? ordered.get(n - 1).get(0).right
                : (ordered.get(slot - 1).get(0).right + ordered.get(slot).get(0).left) / 2f;
            if (carriedAlone && (slot == carriedColumn || slot == carriedColumn + 1)) return Drop.NOTHING;
            // Named by the column before the gap; the first gap by the column after it. The window
            // naming it is one that stays there, never the carried one leaving its stack.
            boolean before = slot == 0;
            Tile neighbour = staying(before ? ordered.get(0) : ordered.get(slot - 1), carried);
            return new Drop(Type.COLUMN, neighbour.windowId, before ? Side.LEFT : Side.RIGHT,
                centre - catchPx / 2f, span[1], centre + catchPx / 2f, span[3]);
        }
        // Inside a column: the window there, else the nearest of its windows.
        int c = 0;
        while (c < n - 1 && x > ordered.get(c).get(0).right) c++;
        List<Tile> column = ordered.get(c);
        Tile target = nearest(column, x, y);
        if (target.windowId == carried) return Drop.NOTHING;
        Drop drop = onWindow(target, x, y);
        switch (drop.side) {
            case LEFT:
            case RIGHT: {
                if (drop.type != Type.SIDE) return drop;
                int slot = drop.side == Side.LEFT ? c : c + 1;
                if (carriedAlone && (slot == carriedColumn || slot == carriedColumn + 1)) return Drop.NOTHING;
                return drop;
            }
            default: {
                if (drop.type != Type.SIDE) return drop;
                boolean full = column.size() >= TilingStack.MAX_STACKED && carriedColumn != c;
                return full ? Drop.NOTHING : drop;
            }
        }
    }

    /** What a drop on {@code target} does: its centre a tab, else the half the finger is in. */
    @NonNull
    private static Drop onWindow(@NonNull Tile target, float x, float y) {
        float cx = (target.left + target.right) / 2f;
        float cy = (target.top + target.bottom) / 2f;
        float halfW = Math.max(1f, (target.right - target.left) / 2f);
        float halfH = Math.max(1f, (target.bottom - target.top) / 2f);
        float u = (x - cx) / halfW;
        float v = (y - cy) / halfH;
        if (Math.abs(u) < CENTRE && Math.abs(v) < CENTRE) {
            return new Drop(Type.TAB, target.windowId, Side.LEFT, target.left, target.top, target.right,
                target.bottom);
        }
        Side side = Math.abs(u) >= Math.abs(v) ? (u < 0f ? Side.LEFT : Side.RIGHT)
            : (v < 0f ? Side.TOP : Side.BOTTOM);
        float l = target.left;
        float t = target.top;
        float r = target.right;
        float b = target.bottom;
        switch (side) {
            case LEFT: r = cx; break;
            case RIGHT: l = cx; break;
            case TOP: b = cy; break;
            default: t = cy; break;
        }
        return new Drop(Type.SIDE, target.windowId, side, l, t, r, b);
    }

    /** The first of {@code column}'s windows other than {@code carried}; a gap beside a lone carried one is NONE before this. */
    @NonNull
    private static Tile staying(@NonNull List<Tile> column, long carried) {
        for (Tile tile : column) if (tile.windowId != carried) return tile;
        return column.get(0);
    }

    /** The tile holding ({@code x}, {@code y}), else the one whose edge is nearest. */
    @NonNull
    private static Tile nearest(@NonNull List<Tile> tiles, float x, float y) {
        Tile best = tiles.get(0);
        float bestDistance = Float.MAX_VALUE;
        for (Tile tile : tiles) {
            float dx = Math.max(0f, Math.max(tile.left - x, x - tile.right));
            float dy = Math.max(0f, Math.max(tile.top - y, y - tile.bottom));
            float distance = dx * dx + dy * dy;
            if (distance < bestDistance) {
                best = tile;
                bestDistance = distance;
            }
        }
        return best;
    }
}
