package com.termux.app.terminal;

import android.graphics.RectF;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The geometry rules behind the {@code dwindle} pane layout, Hyprland-style: a new pane always
 * halves the pane it was spawned from along that pane's longer side, and a pane dropped onto
 * another one halves the target the same way, landing on whichever half the finger let go over.
 *
 * Pure functions on rectangles so the controller only has to apply the answer. Nothing here knows
 * about the tree, sessions or views.
 */
public final class DwindleTilingPolicy {

    /** Which half of a target pane a dropped pane takes. */
    public static final int SIDE_LEFT = 0;
    public static final int SIDE_RIGHT = 1;
    public static final int SIDE_TOP = 2;
    public static final int SIDE_BOTTOM = 3;

    private DwindleTilingPolicy() {}

    /**
     * The orientation a new split of a {@code width}×{@code height} pane takes: side by side when
     * the pane is wider than tall, stacked otherwise. A square pane stacks, so a portrait phone —
     * whose first split leaves two squarish halves — keeps reading top to bottom rather than
     * flipping to a thin column pair. Degenerate sizes (unmeasured host) fall back to stacked,
     * which is the right first split for the phone this runs on.
     */
    public static int splitOrientationFor(float width, float height) {
        if (width <= 0f || height <= 0f) return LinearLayout.VERTICAL;
        return width > height ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL;
    }

    /**
     * Where a pane dropped at ({@code x}, {@code y}) lands inside {@code target}. The axis comes
     * from the target's aspect (the same rule as {@link #splitOrientationFor}), the half from which
     * side of the target's midline the point is on. A point outside the target is projected onto
     * it, so a drop that overshoots the edge still lands on that edge's half.
     */
    public static int dropSideFor(@NonNull RectF target, float x, float y) {
        int orientation = splitOrientationFor(target.width(), target.height());
        if (orientation == LinearLayout.HORIZONTAL) {
            return x < target.centerX() ? SIDE_LEFT : SIDE_RIGHT;
        }
        return y < target.centerY() ? SIDE_TOP : SIDE_BOTTOM;
    }

    /** The split orientation that realises {@code side}. */
    public static int orientationForSide(int side) {
        return side == SIDE_LEFT || side == SIDE_RIGHT ? LinearLayout.HORIZONTAL : LinearLayout.VERTICAL;
    }

    /** True when the dropped pane becomes the first child (left or top) of the new split. */
    public static boolean droppedFirst(int side) {
        return side == SIDE_LEFT || side == SIDE_TOP;
    }

    /** The half of {@code target} that a pane dropped on {@code side} would occupy. */
    @NonNull
    public static RectF halfFor(@NonNull RectF target, int side, @NonNull RectF out) {
        out.set(target);
        switch (side) {
            case SIDE_LEFT: out.right = target.centerX(); break;
            case SIDE_RIGHT: out.left = target.centerX(); break;
            case SIDE_TOP: out.bottom = target.centerY(); break;
            default: out.top = target.centerY(); break;
        }
        return out;
    }

    /**
     * The orientation of every split in a dwindle tree built by spawning {@code count} panes one
     * after another from a {@code width}×{@code height} region, where each new pane halves the
     * previous one. Entry {@code i} is the orientation of the split that produced pane {@code i+1};
     * the array has {@code count - 1} entries. Used to lay an existing pane set into dwindle when
     * the user switches to it, so it matches what building the panes one by one would have given.
     */
    @NonNull
    public static int[] spiralOrientations(int count, float width, float height) {
        int[] orientations = new int[Math.max(0, count - 1)];
        float w = width;
        float h = height;
        for (int i = 0; i < orientations.length; i++) {
            int orientation = splitOrientationFor(w, h);
            orientations[i] = orientation;
            if (orientation == LinearLayout.HORIZONTAL) w /= 2f; else h /= 2f;
        }
        return orientations;
    }

    // ---- Tiling desktop: a new window splits, or joins as a tab (spec "Layouts", Dwindle) ----

    /** A terminal under this many columns is not usable; a split that leaves it narrower becomes a tab. */
    public static final int TERMINAL_MIN_COLUMNS = 40;

    /** What a new window does to the focused tile. */
    public static final int KIND_SPLIT = 0;
    public static final int KIND_TAB = 1;

    /** The smallest width in px a terminal of {@code columns} columns needs, from the caller's cell width. */
    public static float minWidthForColumns(int columns, float cellWidthPx) {
        return Math.max(0, columns) * Math.max(0f, cellWidthPx);
    }

    /**
     * The answer for a new window: {@link #KIND_SPLIT} with the {@code orientation}, the {@code side}
     * the new window takes (always right or bottom, so it opens after the focused one) and
     * {@code newFirst} false; or {@link #KIND_TAB}, where side and orientation are -1.
     */
    public static final class Placement {
        public final int kind;
        public final int orientation;
        public final int side;
        public final boolean newFirst;

        private Placement(int kind, int orientation, int side) {
            this.kind = kind;
            this.orientation = orientation;
            this.side = side;
            this.newFirst = side >= 0 && droppedFirst(side);
        }

        public boolean isTab() { return kind == KIND_TAB; }
    }

    /**
     * Where a new window goes when the focused tile is {@code width}×{@code height}: it splits the
     * tile along its longer side ({@link #splitOrientationFor}), unless either half would fall under
     * its window's minimum in either dimension, in which case it joins the tile as a tab. The focused
     * minimum is the largest minimum among the tile's tabs (the caller takes it). The caller turns
     * font metrics and app minimums into pixels, so this stays pure. A minimum of 0 means none; an
     * unmeasured tile (a zero size) cannot be judged and splits.
     */
    @NonNull
    public static Placement placeNewWindow(float width, float height,
                                           float focusedMinWidth, float focusedMinHeight,
                                           float newMinWidth, float newMinHeight) {
        int orientation = splitOrientationFor(width, height);
        int side = orientation == LinearLayout.HORIZONTAL ? SIDE_RIGHT : SIDE_BOTTOM;
        if (width > 0f && height > 0f) {
            float halfW = orientation == LinearLayout.HORIZONTAL ? width / 2f : width;
            float halfH = orientation == LinearLayout.HORIZONTAL ? height : height / 2f;
            if (halfW < focusedMinWidth || halfH < focusedMinHeight
                || halfW < newMinWidth || halfH < newMinHeight) {
                return new Placement(KIND_TAB, -1, -1);
            }
        }
        return new Placement(KIND_SPLIT, orientation, side);
    }

    /**
     * The windows sharing one tile and which of them shows. Immutable; every change returns a new
     * tile. A tile of one window is the ordinary untabbed case.
     */
    public static final class Tile {
        private final List<String> windows;
        private final int shown;

        private Tile(List<String> windows, int shown) {
            this.windows = Collections.unmodifiableList(windows);
            this.shown = shown;
        }

        @NonNull
        public static Tile single(@NonNull String window) {
            List<String> l = new ArrayList<>();
            l.add(window);
            return new Tile(l, 0);
        }

        /** Window ids in tab order. */
        @NonNull
        public List<String> windows() { return windows; }

        /** The window whose tab is shown. */
        @NonNull
        public String shownWindow() { return windows.get(shown); }

        public int shownIndex() { return shown; }

        public boolean isTabbed() { return windows.size() > 1; }

        /** The tile after {@code window} joins as a tab right after the shown one, and shows. */
        @NonNull
        public Tile withTab(@NonNull String window) {
            List<String> l = new ArrayList<>(windows);
            l.remove(window);
            int at = Math.min(l.indexOf(shownWindow()) + 1, l.size());
            if (!l.contains(shownWindow())) at = l.size();
            l.add(at, window);
            return new Tile(l, at);
        }

        /** The tile showing {@code window}'s tab; unchanged when it is not in the tile. */
        @NonNull
        public Tile show(@NonNull String window) {
            int i = windows.indexOf(window);
            return i < 0 ? this : new Tile(new ArrayList<>(windows), i);
        }

        /**
         * The tile after {@code window} leaves, or null when it was the last. When the shown tab
         * leaves, the tab after it shows, else the one before.
         */
        @Nullable
        public Tile without(@NonNull String window) {
            int i = windows.indexOf(window);
            if (i < 0) return this;
            if (windows.size() == 1) return null;
            List<String> l = new ArrayList<>(windows);
            l.remove(i);
            int next = shown;
            if (i < shown) next = shown - 1;
            else if (i == shown) next = Math.min(i, l.size() - 1);
            return new Tile(l, next);
        }
    }
}
