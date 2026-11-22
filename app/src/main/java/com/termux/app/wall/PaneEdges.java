package com.termux.app.wall;

import android.graphics.RectF;

import androidx.annotation.NonNull;

import java.util.List;

/**
 * Where a page's panes stand along its bottom border, so the border's glow lights the air under
 * the pane a finger pressed rather than the whole page ({@link BorderGlow#belowBand}). A page
 * without panes of its own (Home, the display) has none, and the glow takes the page's frame.
 */
public interface PaneEdges {

    /**
     * The pane whose bottom border is at {@code x}, in the page's own pixels: fills {@code out}
     * with its {@code left, right, bottom}. False, {@code out} untouched, where no pane is laid out.
     */
    boolean bottomEdgeAt(float x, @NonNull float[] out);

    /**
     * The pane of {@code panes} (frames in one space) whose bottom border is at {@code x}: of those
     * whose span holds {@code x}, the lowest, so a stacked split answers with its bottom pane and a
     * side-by-side one with the pane under the finger. A finger in the air between two cards takes
     * the nearer one, the lower on a tie. Fills {@code out} with {@code left, right, bottom}.
     *
     * @return false, {@code out} untouched, where {@code panes} holds no frame with a size
     */
    static boolean pick(@NonNull List<RectF> panes, float x, @NonNull float[] out) {
        RectF best = null;
        float bestDistance = Float.POSITIVE_INFINITY;
        for (RectF pane : panes) {
            if (pane == null || !(pane.right > pane.left) || !(pane.bottom > pane.top)) continue;
            float distance = x < pane.left ? pane.left - x : x > pane.right ? x - pane.right : 0f;
            if (best == null || distance < bestDistance
                || (distance == bestDistance && pane.bottom > best.bottom)) {
                best = pane;
                bestDistance = distance;
            }
        }
        if (best == null) return false;
        out[0] = best.left;
        out[1] = best.right;
        out[2] = best.bottom;
        return true;
    }
}
