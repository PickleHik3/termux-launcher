package com.termux.app.chrome;

import android.graphics.RectF;

import androidx.annotation.NonNull;

import java.util.List;

/**
 * Where a pane in a split is held for its controls: the bottom-end corner arc, about
 * {@link #ARC_DP} square at the pane's bottom-right (bottom-left in a right-to-left layout), and
 * nowhere else. A window with one pane has no such place: its mark covers it and the corner stays
 * free for text selection. Pure; the overlay feeds it the pane frames and a touch.
 */
public final class PaneCornerHold {

    /** The held square's side, clamped on a small pane to half of its shorter side. */
    public static final float ARC_DP = 32f;

    private PaneCornerHold() {
    }

    /** The corner that opens a pane's controls in this layout direction. */
    public static int corner(boolean rtl) {
        return CornerZones.corner(false, false, rtl);
    }

    /** The held square's side in pixels at this density. */
    public static float arcPx(float density) {
        return ARC_DP * density;
    }

    /** The held square on one pane, in the frame's own coordinate system. */
    public static void rect(@NonNull RectF pane, float density, boolean rtl, @NonNull RectF out) {
        CornerZones.cornerRect(corner(rtl), pane, arcPx(density), out);
    }

    /** Whether a window with this many panes (shown or not) offers the hold at all. */
    public static boolean offered(int paneCount) {
        return paneCount >= 2;
    }

    /**
     * The index of the pane whose arc a touch landed in, or -1.
     *
     * @param paneCount how many panes the window holds, shown or not; under two there is no hold
     * @param preferredIndex the pane that wins where two arcs reach the same divider pixels
     * @param slopPx how far outside a frame still counts: the divider's empty pixels
     */
    public static int pick(@NonNull List<RectF> frames, int paneCount, int preferredIndex,
                           float x, float y, float density, boolean rtl, float slopPx) {
        if (!offered(paneCount)) return -1;
        int want = corner(rtl);
        float size = arcPx(density);
        int slopHit = -1;
        for (int i = 0; i < frames.size(); i++) {
            RectF frame = frames.get(i);
            if (frame == null) continue;
            if (CornerZones.cornerAt(x, y, frame, size, 0f) == want) return i;
            if (CornerZones.cornerAt(x, y, frame, size, slopPx) == want
                && (slopHit < 0 || i == preferredIndex)) {
                slopHit = i;
            }
        }
        return slopHit;
    }
}
