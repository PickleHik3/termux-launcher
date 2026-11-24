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
    /**
     * The height left in the column under its chips ({@code chipCount} of {@code chipPx} with
     * {@code padPx} around and between), where the hint may grow upward from the foot.
     */
    static int hintRoomPx(int columnHeightPx, int chipCount, int chipPx, int padPx) {
        return Math.max(0, columnHeightPx - (2 * padPx + chipCount * (chipPx + padPx)));
    }

    @NonNull
    static int[] rect(@NonNull int[] frame, int windowWidthPx, int gapPx, int minWidthPx) {
        int left = Math.min(frame[2] + gapPx, windowWidthPx - minWidthPx);
        return new int[] {left, frame[1], left + minWidthPx, frame[3]};
    }
}
