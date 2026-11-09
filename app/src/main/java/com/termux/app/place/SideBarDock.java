package com.termux.app.place;

import android.view.Surface;

import androidx.annotation.Nullable;

import com.termux.app.place.PlaceLayout.Edge;

/**
 * Home on a phone in landscape stands its dock as a vertical bar of icons on the long edge, the
 * Launcher3 way ({@code DeviceProfile.isVerticalBarLayout}, ADR 0020 §12): on the right in
 * landscape ({@link Surface#ROTATION_90}), on the left in seascape ({@link Surface#ROTATION_270}).
 * The bar is the existing rail, forced; its icon is the one icon step. Portrait and tablets
 * (smallest width {@link #MAX_SMALLEST_WIDTH_DP} dp or more) keep the layout as stored.
 */
public final class SideBarDock {
    /** A screen this wide or wider at its narrowest is a tablet: no side bar. */
    public static final int MAX_SMALLEST_WIDTH_DP = 600;

    private SideBarDock() {}

    /**
     * The edge Home's dock stands on as a side bar, or null where it keeps its stored edge.
     *
     * @param rotation the display's rotation, a {@code Surface.ROTATION_*}
     */
    @Nullable
    public static Edge sideFor(boolean home, boolean landscape, int smallestWidthDp,
                               int rotation) {
        if (!home || !landscape || smallestWidthDp <= 0
            || smallestWidthDp >= MAX_SMALLEST_WIDTH_DP) return null;
        return rotation == Surface.ROTATION_270 ? Edge.LEFT : Edge.RIGHT;
    }
}
