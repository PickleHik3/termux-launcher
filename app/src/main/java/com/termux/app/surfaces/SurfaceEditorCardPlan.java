package com.termux.app.surfaces;

import androidx.annotation.Nullable;

import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceSlot;

/**
 * What the card carries under its header.
 *
 * <p>Every card is the same shape now — the surface's look rows, and nothing above them. The one
 * thing that still differs is the strip between the header and the rows: the presets and the two
 * style pills belong to the shared layer, which is what "all surfaces" means, and a card open on
 * one surface has no business offering them.
 *
 * <p>Pure: a surface in, an answer out, so every case is testable without a window.
 */
public final class SurfaceEditorCardPlan {

    private SurfaceEditorCardPlan() {}

    /**
     * Whether the card carries the shared layer's own strip: the preset mocks and the style and
     * material pills.
     *
     * @param slot the surface the card is open on, or null for the shared layer
     */
    public static boolean sharedStripShown(@Nullable SurfaceSlot slot) {
        return slot == null;
    }
}
