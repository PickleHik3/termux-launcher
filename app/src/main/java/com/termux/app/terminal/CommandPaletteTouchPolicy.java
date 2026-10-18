package com.termux.app.terminal;

import androidx.annotation.Nullable;

/**
 * Who a touch on the open palette's overlay belongs to. The overlay fills the activity so the
 * glass can sprout from the space bar, which puts it over the in-app keyboard and the dock too,
 * and the keyboard is how the palette is typed into: a key tap that the overlay took for a tap
 * outside would close the palette on its first keystroke.
 *
 * <p>The in-app keyboard is not always below the palette's modal bounds. Docked at the bottom it
 * is, but a floating keyboard, or one on a side edge, lies over the page or the terminal, inside
 * them. So the keyboard and the dock are named as pass-through rects, measured where they stand
 * at the touch, and a touch on one of them goes to them whatever the bounds say.
 *
 * <p>Pure: rects are packed as {@code left, top, right, bottom} quadruples in the overlay's
 * coordinates, half-open like {@link android.graphics.RectF#contains(float, float)}.
 */
final class CommandPaletteTouchPolicy {

    /** Where a touch goes. */
    enum Target {
        /** The glass or its keycap strip: the palette handles it. */
        PALETTE,
        /** The keyboard, its drag handle, the dock, or anything outside the modal bounds. */
        PASS_THROUGH,
        /** Free area inside the modal bounds: a tap there closes the palette. */
        OUTSIDE
    }

    private CommandPaletteTouchPolicy() {}

    /**
     * @param onCard whether the touch lands on the glass or one of its keycaps; the palette is
     *     drawn over everything, so that wins over a keyboard lying under the glass
     * @param modalBounds the region the overlay claims, or null / empty for all of it (no bounds
     *     yet: a full scrim rather than leaking every tap)
     * @param passThrough the in-app keyboard, the floating keyboard's frame and the dock, packed
     *     as quadruples; null for none. A trailing partial quadruple is ignored.
     */
    static Target classify(float x, float y, boolean onCard, @Nullable float[] modalBounds,
                           @Nullable float[] passThrough) {
        if (onCard) return Target.PALETTE;
        if (passThrough != null) {
            for (int i = 0; i + 3 < passThrough.length; i += 4) {
                if (contains(passThrough, i, x, y)) return Target.PASS_THROUGH;
            }
        }
        if (modalBounds == null || modalBounds.length < 4 || isEmpty(modalBounds)
            || contains(modalBounds, 0, x, y)) return Target.OUTSIDE;
        return Target.PASS_THROUGH;
    }

    private static boolean isEmpty(float[] r) {
        return r[0] >= r[2] || r[1] >= r[3];
    }

    private static boolean contains(float[] r, int i, float x, float y) {
        float left = r[i], top = r[i + 1], right = r[i + 2], bottom = r[i + 3];
        return left < right && top < bottom && x >= left && x < right && y >= top && y < bottom;
    }
}
