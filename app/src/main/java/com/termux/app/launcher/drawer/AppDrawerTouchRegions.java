package com.termux.app.launcher.drawer;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.launcher.drawer.AppDrawerTransitionGeometry.Frame;

/**
 * Which of the three surfaces owns a touch stream, decided once at {@code ACTION_DOWN}.
 *
 * <p>B-1/B-2 had one boolean: the grid owns a point or the plane's close drag does. The A-Z column
 * is neither, and it cannot be told apart from a close drag by motion — a scrub <em>is</em> a
 * sustained downward drag in the same place at the same speed — so the split has to be made from
 * geometry at the down point and never revisited. That is the whole reason this is a pure function
 * of two rectangles and two booleans rather than anything that watches the stream.
 *
 * <p>{@link Region#GRID} and {@link Region#COLUMN} both mean "the content owns it, the plane must
 * defer"; only {@link Region#CHROME} arms B-1's close-drag arbiter. The column is tested first so
 * that if the two rectangles ever overlap — they are laid out not to, the grid carries a right
 * margin of exactly the column width — the letters win rather than silently becoming dead strip.
 *
 * <p>Rectangles are the house {@link Frame} of floats, not {@code Rect}, so this class and its test
 * run under bare JUnit with no Robolectric. Bounds are half-open on the right and bottom, matching
 * the view layer's own {@code getLeft()..getRight()} test.
 */
public final class AppDrawerTouchRegions {

    /** The three touch categories. Exactly one applies to any point. */
    public enum Region { GRID, COLUMN, CONTROL, CHROME }

    private AppDrawerTouchRegions() {}

    /**
     * @param x             the content view's local X
     * @param y             the content view's local Y
     * @param grid          the grid's bounds, or null before layout
     * @param column        the A-Z column's bounds, or null when there is no column
     * @param interactive   false while the drawer is mid-transition; nothing inside is touchable yet
     * @param columnActive  false when a query is up or fewer than two letters exist, in which case
     *                      the column's strip resolves to {@link Region#CHROME} so the close drag
     *                      works there instead of hitting an invisible scrubber
     */
    @NonNull
    public static Region resolve(float x, float y, @Nullable Frame grid, @Nullable Frame column,
                                 boolean interactive, boolean columnActive) {
        return resolve(x, y, grid, column, null, interactive, columnActive);
    }

    @NonNull
    public static Region resolve(float x, float y, @Nullable Frame grid, @Nullable Frame column,
                                 @Nullable Frame control, boolean interactive,
                                 boolean columnActive) {
        // Nothing inside the plane takes a stream until the plane has finished arriving.
        if (!interactive) return Region.CHROME;
        if (contains(control, x, y)) return Region.CONTROL;
        if (columnActive && contains(column, x, y)) return Region.COLUMN;
        if (contains(grid, x, y)) return Region.GRID;
        return Region.CHROME;
    }

    /** Half-open containment; a null or degenerate frame contains nothing. */
    private static boolean contains(@Nullable Frame frame, float x, float y) {
        if (frame == null) return false;
        if (frame.width() <= 0f || frame.height() <= 0f) return false;
        return x >= frame.left && x < frame.right && y >= frame.top && y < frame.bottom;
    }
}
