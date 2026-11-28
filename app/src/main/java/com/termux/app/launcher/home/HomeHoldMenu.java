package com.termux.app.launcher.home;

import androidx.annotation.NonNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The small menu a hold on empty Home opens under the Terl desktop (decided 2026-11-28: a hold no
 * longer opens edit mode directly). Pure: its rows, in order, and where the card stands for a
 * press.
 *
 * <p>The rows are the developer's three (Edit Home &amp; looks, Settings, New terminal) and one
 * more of the highest value, Wallpaper: the picture is what a hold on a home screen changes most
 * often, and it is the one look edit mode cannot undo, so it gets its own door.
 */
public final class HomeHoldMenu {

    private HomeHoldMenu() { }

    /** What a row does. */
    public enum Row {
        /** Opens edit mode at rest: Home's card and every look are a tap away. */
        EDIT,
        /** Opens a new terminal, as the bar's + tool does. */
        NEW_TERMINAL,
        /** Opens the wallpaper picker. */
        WALLPAPER,
        /** Opens Settings. */
        SETTINGS
    }

    private static final List<Row> ROWS = Collections.unmodifiableList(
        Arrays.asList(Row.EDIT, Row.NEW_TERMINAL, Row.WALLPAPER, Row.SETTINGS));

    /** The rows, top to bottom. */
    @NonNull
    public static List<Row> rows() {
        return ROWS;
    }

    /**
     * Where a {@code widthPx} x {@code heightPx} card stands for a press at {@code pressX},
     * {@code pressY} in a room {@code roomWidthPx} x {@code roomHeightPx} (all in the room's
     * pixels): its top-left corner at the press, as a context menu opens under the finger; flipped
     * to the press's left or above it where it would run off that side, and kept {@code gapPx}
     * inside the room. Returns {left, top}.
     */
    @NonNull
    public static int[] place(float pressX, float pressY, int widthPx, int heightPx,
                              int roomWidthPx, int roomHeightPx, int gapPx) {
        int x = Math.round(pressX);
        int y = Math.round(pressY);
        int left = x + widthPx + gapPx <= roomWidthPx ? x : x - widthPx;
        int top = y + heightPx + gapPx <= roomHeightPx ? y : y - heightPx;
        left = clamp(left, gapPx, roomWidthPx - widthPx - gapPx);
        top = clamp(top, gapPx, roomHeightPx - heightPx - gapPx);
        return new int[] {left, top};
    }

    private static int clamp(int value, int min, int max) {
        if (max < min) return Math.max(0, min);
        return Math.max(min, Math.min(max, value));
    }
}
