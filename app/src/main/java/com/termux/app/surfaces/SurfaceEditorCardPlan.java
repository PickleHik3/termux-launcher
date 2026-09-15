package com.termux.app.surfaces;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceSlot;

/**
 * What one editor session puts on screen: whether the pill offers the palette, and what the card
 * carries under its header.
 *
 * <p>The rule is one sentence per question, and the shared layer is the one thing that does not
 * bend: it is not a place, so it has nothing to arrange, and a card opened on it is always the
 * full one. Every other card follows the session's mode — placement alone, plus the row that opens
 * the rest, or the whole thing.
 *
 * <p>Pure: a mode and a surface in, an answer out, so every case is testable without a window.
 */
public final class SurfaceEditorCardPlan {

    private SurfaceEditorCardPlan() {}

    /**
     * Whether the resting pill offers the palette, which is the only way to the shared layer.
     */
    public static boolean paletteShown(@NonNull SurfaceEditorMode mode) {
        return mode == SurfaceEditorMode.FULL;
    }

    /**
     * Whether the card carries the surface's look rows.
     *
     * @param slot the surface the card is open on, or null for the shared layer
     */
    public static boolean lookRowsShown(@NonNull SurfaceEditorMode mode,
                                        @Nullable SurfaceSlot slot) {
        return mode == SurfaceEditorMode.FULL || slot == null;
    }

    /**
     * Whether the card ends in the row that opens the look rows. It stands only where they are
     * held back, so a card never offers both.
     *
     * @param slot the surface the card is open on, or null for the shared layer
     */
    public static boolean moreRowShown(@NonNull SurfaceEditorMode mode,
                                       @Nullable SurfaceSlot slot) {
        return !lookRowsShown(mode, slot);
    }
}
