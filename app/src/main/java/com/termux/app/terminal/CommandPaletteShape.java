package com.termux.app.terminal;

import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceSlot;

/**
 * The command palette's corners: the Menus and dialogs surface's Corner radius (the SHEETS slot,
 * Base until detached), grown into from the space bar's small radius as the palette sprouts, and
 * never more than a pill. Pure.
 */
final class CommandPaletteShape {

    /** The radius the palette sprouts from: about the space bar's own. */
    static final float SEED_RADIUS_DP = 6f;

    private CommandPaletteShape() {}

    /**
     * The open radius in dp for the stored Corners {@code storedDp}: the stored -1 "follow the
     * style" stands for what {@link TermuxAppSharedPreferences#resolveAutoCornerRadiusDp} says
     * for the Sheets surface under the style (square Docked, the rounded token Floating).
     */
    static int openRadiusDp(int storedDp, boolean floating) {
        return storedDp < 0
            ? TermuxAppSharedPreferences.resolveAutoCornerRadiusDp(SurfaceSlot.SHEETS, floating)
            : storedDp;
    }

    /**
     * The radius on a frame {@code frameHeightPx} tall at sprout {@code progress}: the seed lerped
     * toward the open radius, capped at half the frame's height, so a short frame is a pill.
     */
    static float radiusPx(float seedPx, float openPx, float progress, float frameHeightPx) {
        float p = Math.max(0f, Math.min(1f, progress));
        float radius = seedPx + (openPx - seedPx) * p;
        return Math.max(0f, Math.min(radius, Math.max(0f, frameHeightPx) / 2f));
    }
}
