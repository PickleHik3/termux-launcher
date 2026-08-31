package com.termux.app.terminal;

import android.graphics.RectF;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;

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
}
