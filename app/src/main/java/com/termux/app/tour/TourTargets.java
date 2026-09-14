package com.termux.app.tour;

import android.graphics.Rect;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The host's answer to "where is that control right now", in the overlay's own coordinates.
 *
 * <p>Asked again on every layout pass rather than cached: the keyboard, both dock styles, a
 * rotation and a font scale all move these, and a card glowing where the dock used to be is worse
 * than a card with no glow at all.
 *
 * <p>A null rect is a normal answer — the control is not on screen, or its adapter has not landed
 * yet — and the overlay draws the card without a glow rather than guessing.
 */
public interface TourTargets {

    /** A card that points at nothing: the closing card, and the halves performed on a full-screen
     * surface that has already covered the control that opened it. */
    String NONE = "";

    String STATUS_BAR = "status_bar";
    String PLUS_BUTTON = "plus_button";
    String WINDOW_CHIP = "window_chip";
    /** The x the selected chip reveals; only ever on screen once that chip has been tapped. */
    String WINDOW_CLOSE = "window_close";
    String SPLIT_KEY = "split_key";
    String PANE_CORNER = "pane_corner";
    String DOCK = "dock";
    String AZ_ROW = "az_row";
    String SPACE_BAR = "space_bar";

    /** @return the control's bounds in overlay coordinates, or null when it cannot be pointed at. */
    @Nullable
    Rect rectFor(@NonNull String targetId);

    /**
     * Why the last {@link #rectFor} answered null, for the debug log. A card with no glow is the
     * hardest thing to diagnose from a device pass — "the dock is not on screen" and "the dock has
     * not been laid out yet" look identical on the phone — so the answer says which.
     */
    @NonNull
    default String lastMissReason() {
        return "unknown";
    }
}
