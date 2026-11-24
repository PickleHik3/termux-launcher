package com.termux.app.surfaces;

import androidx.annotation.NonNull;

/**
 * Where the Hidden column stands: beside the frame, at the frame's top and height, whatever pose
 * the card sheet's height gives the frame (the pose changes the frame's rect, so the column is a
 * function of that rect alone). Pure px arithmetic; the view applies the answer.
 */
final class HiddenColumnGeometry {

    private HiddenColumnGeometry() {}

    /**
     * The column's rect {left, top, right, bottom} for the frame's rect: {@code minWidthPx} wide,
     * {@code gapPx} right of the frame, or against the window's edge where the frame leaves less
     * room (it fills the window at rest). Its width never changes with the pose, so the view moves
     * by translation alone.
     */
    @NonNull
    static int[] rect(@NonNull int[] frame, int windowWidthPx, int gapPx, int minWidthPx) {
        int left = Math.min(frame[2] + gapPx, windowWidthPx - minWidthPx);
        return new int[] {left, frame[1], left + minWidthPx, frame[3]};
    }
}
