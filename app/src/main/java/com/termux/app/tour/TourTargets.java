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

    String STATUS_BAR = "status_bar";
    String PLUS_BUTTON = "plus_button";
    String WINDOW_CHIP = "window_chip";
    String SPLIT_KEY = "split_key";
    String PANE_CORNER = "pane_corner";
    String DOCK = "dock";
    String AZ_ROW = "az_row";
    String SPACE_BAR = "space_bar";

    /** @return the control's bounds in overlay coordinates, or null when it cannot be pointed at. */
    @Nullable
    Rect rectFor(@NonNull String targetId);
}
