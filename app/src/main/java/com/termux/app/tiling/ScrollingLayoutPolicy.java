package com.termux.app.tiling;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The geometry rules behind the <b>Scrolling</b> layout, the default of a new Workspace: windows
 * are columns on a strip wider than the screen, a window opens as the column after the focused
 * one, a window can be stacked into the column beside it (top over bottom), and focusing a column
 * scrolls only as far as needed to show it whole.
 *
 * Pure functions on immutable values, in plain floats: no Android types, no model classes, no
 * views. Every operation takes a {@link State} and returns a new one (the same instance when the
 * operation does not apply), and the strip's scroll offset is part of the state, so the caller
 * only draws {@link #layout}.
 *
 * Coordinates are strip coordinates: x grows along the strip from the first column's left edge
 * (no outer margin; the gap lies only between columns and between a stack's two windows), y from
 * the top of the viewport. A placement's on-screen x is {@code left - state.scroll}.
 *
 * A column's width is stored as a fraction f of the viewport: its pixel width is
 * {@code f * (viewport + gap) - gap}, so 1 fills the viewport and 1/n makes n columns plus their
 * n-1 gaps exactly fill it. Presets are 1 and 1/2 in portrait, plus 1/3 and 2/3 in landscape; a
 * gap drag stores any fraction between {@link #MIN_WIDTH_FRACTION} and 1.
 *
 * A lone window fills the viewport (and fills it again when the others go, {@link #leftAlone}); a
 * window opened beside others opens at a half in portrait and
 * a third in landscape, and a full-width column it opens beside narrows to the rest (a half, or
 * two thirds), so the two stand side by side ({@link #openedWidth}, {@link #besideOpened}).
 */
public final class ScrollingLayoutPolicy {

    /** Direction of a neighbouring column. */
    public static final int LEFT = -1;
    public static final int RIGHT = 1;

    /** The focused window of a Workspace with no windows. */
    public static final long NO_WINDOW = -1;

    /** The narrowest a gap drag may make a column, as a width fraction. */
    public static final float MIN_WIDTH_FRACTION = 0.25f;

    private static final float PRESET_FULL = 1f;
    private static final float PRESET_HALF = 0.5f;
    private static final float PRESET_THIRD = 1f / 3f;
    private static final float PRESET_TWO_THIRDS = 2f / 3f;
    /**
     * The thirds stand in portrait too: the window menu offers 1/3, 1/2, 2/3 and Full in both
     * orientations, as the prototype (review of round 3: a portrait third did nothing).
     */
    private static final float[] PORTRAIT_PRESETS = {PRESET_FULL, PRESET_HALF, PRESET_THIRD,
        PRESET_TWO_THIRDS};
    private static final float[] LANDSCAPE_PRESETS = {PRESET_FULL, PRESET_HALF, PRESET_THIRD,
        PRESET_TWO_THIRDS};
    private static final float EPSILON = 0.0005f;

    private ScrollingLayoutPolicy() {}

    // -- value types ---------------------------------------------------------------------------

    /** The window area a Workspace lays out in, the gap between tiles, and whether it is landscape. */
    public static final class Viewport {
        public final float width;
        public final float height;
        public final float gap;
        public final boolean landscape;

        public Viewport(float width, float height, float gap, boolean landscape) {
            this.width = width;
            this.height = height;
            this.gap = gap;
            this.landscape = landscape;
        }
    }

    /** One column: one window, or two stacked (top over bottom), at a width fraction. */
    public static final class Column {
        /** Window ids, top first; one or two. */
        private final long[] windows;
        public final float widthFraction;
        /** True when the width was set by a preset (or a snap), false when a gap drag made it. */
        public final boolean preset;

        Column(long[] windows, float widthFraction, boolean preset) {
            this.windows = windows.clone();
            this.widthFraction = widthFraction;
            this.preset = preset;
        }

        public int windowCount() {
            return windows.length;
        }

        public long windowAt(int row) {
            return windows[row];
        }

        boolean contains(long id) {
            return rowOf(id) >= 0;
        }

        int rowOf(long id) {
            for (int i = 0; i < windows.length; i++) if (windows[i] == id) return i;
            return -1;
        }

        Column withWidth(float f, boolean preset) {
            return new Column(windows, f, preset);
        }
    }

    /** The Workspace's ordered columns, the focused window and the strip's scroll offset. */
    public static final class State {
        public static final State EMPTY = new State(Collections.<Column>emptyList(), NO_WINDOW, 0f);

        private final List<Column> columns;
        public final long focusedWindow;
        public final float scroll;

        State(List<Column> columns, long focusedWindow, float scroll) {
            this.columns = Collections.unmodifiableList(new ArrayList<>(columns));
            this.focusedWindow = focusedWindow;
            this.scroll = scroll;
        }

        public int columnCount() {
            return columns.size();
        }

        public Column column(int index) {
            return columns.get(index);
        }

        /** Window ids left to right, top before bottom. */
        public long[] windowIdsInOrder() {
            int n = 0;
            for (Column c : columns) n += c.windows.length;
            long[] out = new long[n];
            int i = 0;
            for (Column c : columns) for (long id : c.windows) out[i++] = id;
            return out;
        }

        int columnIndexOf(long id) {
            for (int i = 0; i < columns.size(); i++) if (columns.get(i).contains(id)) return i;
            return -1;
        }
    }

    /** A window's rectangle in strip coordinates. */
    public static final class Placement {
        public final long windowId;
        public final int column;
        /** 0 for the top (or only) window of the column, 1 for the bottom of a stack. */
        public final int row;
        public final float left;
        public final float top;
        public final float right;
        public final float bottom;

        Placement(long windowId, int column, int row, float left, float top, float right, float bottom) {
            this.windowId = windowId;
            this.column = column;
            this.row = row;
            this.left = left;
            this.top = top;
            this.right = right;
            this.bottom = bottom;
        }

        public float width() {
            return right - left;
        }

        public float height() {
            return bottom - top;
        }
    }

    /** Every window's placement and the strip's full width. */
    public static final class Layout {
        public final List<Placement> placements;
        public final float stripWidth;

        Layout(List<Placement> placements, float stripWidth) {
            this.placements = Collections.unmodifiableList(placements);
            this.stripWidth = stripWidth;
        }
    }

    /** One window filling {@code width} by {@code height} (full screen, #426). */
    public static Layout single(long windowId, float width, float height) {
        List<Placement> out = new ArrayList<>();
        out.add(new Placement(windowId, 0, 0, 0f, 0f, width, height));
        return new Layout(out, width);
    }

    // -- geometry ------------------------------------------------------------------------------

    /** The width presets (fractions) for an orientation, widest first. */
    public static float[] presets(boolean landscape) {
        return (landscape ? LANDSCAPE_PRESETS : PORTRAIT_PRESETS).clone();
    }

    private static float pixelWidth(float fraction, Viewport v) {
        return Math.max(0f, fraction * (v.width + v.gap) - v.gap);
    }

    private static float fractionFor(float pixels, Viewport v) {
        float total = v.width + v.gap;
        if (total <= 0f) return 1f;
        float f = (pixels + v.gap) / total;
        return Math.max(MIN_WIDTH_FRACTION, Math.min(1f, f));
    }

    private static float stripWidth(State s, Viewport v) {
        float x = 0f;
        for (int i = 0; i < s.columns.size(); i++) {
            if (i > 0) x += v.gap;
            x += pixelWidth(s.columns.get(i).widthFraction, v);
        }
        return x;
    }

    private static float columnLeft(State s, Viewport v, int index) {
        float x = 0f;
        for (int i = 0; i < index; i++) {
            x += pixelWidth(s.columns.get(i).widthFraction, v) + v.gap;
        }
        return x;
    }

    private static float maxScroll(State s, Viewport v) {
        return Math.max(0f, stripWidth(s, v) - v.width);
    }

    /** All placements of {@code state} laid out in {@code v}. */
    public static Layout layout(State state, Viewport v) {
        List<Placement> out = new ArrayList<>();
        float x = 0f;
        for (int i = 0; i < state.columns.size(); i++) {
            Column c = state.columns.get(i);
            float w = pixelWidth(c.widthFraction, v);
            float h = Math.max(0f, v.height);
            if (c.windows.length == 1) {
                out.add(new Placement(c.windows[0], i, 0, x, 0f, x + w, h));
            } else {
                float g = Math.min(Math.max(0f, v.gap), h);
                float half = (h - g) / 2f;
                out.add(new Placement(c.windows[0], i, 0, x, 0f, x + w, half));
                out.add(new Placement(c.windows[1], i, 1, x, half + g, x + w, h));
            }
            x += w + v.gap;
        }
        return new Layout(out, stripWidth(state, v));
    }

    /** The placement of one window, or null when it is not in the state. */
    public static Placement placementOf(State state, Viewport v, long windowId) {
        for (Placement p : layout(state, v).placements) if (p.windowId == windowId) return p;
        return null;
    }

    // -- scroll --------------------------------------------------------------------------------

    /** The least scroll from {@code current} that shows column {@code index} whole, within the strip. */
    private static float scrollToShow(State s, Viewport v, int index, float current) {
        float left = columnLeft(s, v, index);
        float right = left + pixelWidth(s.columns.get(index).widthFraction, v);
        float scroll = current;
        if (left < scroll) scroll = left;
        else if (right > scroll + v.width) scroll = right - v.width;
        return Math.max(0f, Math.min(maxScroll(s, v), scroll));
    }

    /** Builds a state and settles its scroll: the focused column shown whole, the offset inside the strip. */
    private static State settled(List<Column> columns, long focused, float scroll, Viewport v) {
        State s = new State(columns, focused, scroll);
        int index = s.columnIndexOf(focused);
        float next = index >= 0 ? scrollToShow(s, v, index, scroll)
            : Math.max(0f, Math.min(maxScroll(s, v), scroll));
        return new State(columns, focused, next);
    }

    private static List<Column> copy(State s) {
        return new ArrayList<>(s.columns);
    }

    // -- operations ----------------------------------------------------------------------------

    /**
     * Opens {@code id} as a column right after the focused window's column (last when there is no
     * focus), focuses it and scrolls it into view: full width alone, else at {@link #openedWidth},
     * the focused column narrowed beside it ({@link #besideOpened}).
     */
    public static State open(State state, Viewport v, long id) {
        List<Column> cols = copy(state);
        int focusedIndex = state.columnIndexOf(state.focusedWindow);
        int at = focusedIndex >= 0 ? focusedIndex + 1 : cols.size();
        if (focusedIndex >= 0) cols.set(focusedIndex, besideOpened(cols.get(focusedIndex), v.landscape));
        cols.add(at, new Column(new long[] {id}, openedWidth(cols.isEmpty(), v.landscape), true));
        return settled(cols, id, state.scroll, v);
    }

    /**
     * The width a newly opened window's column takes: the whole viewport when it is the
     * Workspace's only column ({@code lone}), else a half in portrait and a third in landscape.
     */
    public static float openedWidth(boolean lone, boolean landscape) {
        if (lone) return PRESET_FULL;
        return landscape ? PRESET_THIRD : PRESET_HALF;
    }

    /**
     * The one column left when the others have gone: a preset width fills the viewport again (a
     * lone window stands full width); a dragged width is kept.
     */
    public static Column leftAlone(Column column) {
        return column.preset ? column.withWidth(PRESET_FULL, true) : column;
    }

    /**
     * The focused column a window opens beside: a full-width preset narrows to what the new column
     * leaves (a half in portrait, two thirds in landscape) so both stand in view; any other width
     * is kept.
     */
    public static Column besideOpened(Column focused, boolean landscape) {
        if (!focused.preset || Math.abs(focused.widthFraction - PRESET_FULL) >= EPSILON) return focused;
        return focused.withWidth(landscape ? PRESET_TWO_THIRDS : PRESET_HALF, true);
    }

    /**
     * Closes {@code id}. Its column mate, if any, takes the whole column and the focus; otherwise
     * the focus moves to the next column, or the previous when it was last. Closing an unfocused
     * window leaves the focus alone.
     */
    public static State close(State state, Viewport v, long id) {
        int index = state.columnIndexOf(id);
        if (index < 0) return state;
        List<Column> cols = copy(state);
        Column c = cols.get(index);
        long focused = state.focusedWindow;
        if (c.windows.length == 2) {
            long mate = c.windows[1 - c.rowOf(id)];
            cols.set(index, new Column(new long[] {mate}, c.widthFraction, c.preset));
            if (focused == id) focused = mate;
        } else {
            cols.remove(index);
            if (focused == id) {
                if (cols.isEmpty()) focused = NO_WINDOW;
                else focused = cols.get(Math.min(index, cols.size() - 1)).windows[0];
            }
            if (cols.size() == 1) cols.set(0, leftAlone(cols.get(0)));
        }
        return settled(cols, focused, state.scroll, v);
    }

    /** Focuses {@code id}, scrolling the least distance that shows its column whole. */
    public static State focusWindow(State state, Viewport v, long id) {
        if (state.columnIndexOf(id) < 0) return state;
        return settled(copy(state), id, state.scroll, v);
    }

    /**
     * Sets the column of {@code id} to a preset fraction. A fraction that is not a preset for the
     * orientation is ignored.
     */
    public static State setWidthPreset(State state, Viewport v, long id, float fraction) {
        int index = state.columnIndexOf(id);
        if (index < 0 || !isPreset(fraction, v.landscape)) return state;
        return withColumnWidth(state, v, index, fraction, true, true);
    }

    /**
     * A drag on the gap right of column {@code leftColumn}: that column grows or shrinks by
     * {@code deltaPixels} (the gap follows the finger), clamped between the minimum and the full
     * viewport. The other columns keep their widths and shift with the gap.
     */
    public static State resizeFromGap(State state, Viewport v, int leftColumn, float deltaPixels) {
        if (leftColumn < 0 || leftColumn >= state.columns.size() - 1) return state;
        Column c = state.columns.get(leftColumn);
        float f = fractionFor(pixelWidth(c.widthFraction, v) + deltaPixels, v);
        return withColumnWidth(state, v, leftColumn, f, false, false);
    }

    /** A double tap on the gap right of column {@code leftColumn}: that column snaps to the nearest preset. */
    public static State doubleTapGap(State state, Viewport v, int leftColumn) {
        if (leftColumn < 0 || leftColumn >= state.columns.size() - 1) return state;
        float f = state.columns.get(leftColumn).widthFraction;
        float best = PRESET_FULL;
        float[] presets = presets(v.landscape);
        for (float p : presets) if (Math.abs(p - f) < Math.abs(best - f)) best = p;
        return withColumnWidth(state, v, leftColumn, best, true, true);
    }

    /**
     * {@code showFocused} true pulls the focused column whole after the change (presets); false
     * only clamps the scroll into the strip, so a gap drag leaves the gap under the finger.
     */
    private static State withColumnWidth(State state, Viewport v, int index, float fraction,
        boolean preset, boolean showFocused) {
        List<Column> cols = copy(state);
        cols.set(index, cols.get(index).withWidth(fraction, preset));
        if (showFocused) return settled(cols, state.focusedWindow, state.scroll, v);
        State next = new State(cols, state.focusedWindow, state.scroll);
        return new State(cols, state.focusedWindow,
            Math.max(0f, Math.min(maxScroll(next, v), state.scroll)));
    }

    /**
     * Stacks {@code id} (alone in its column) into the neighbouring column on {@code direction}:
     * under that column's window when going left, over it when going right, so the reading order
     * stays left to right, top to bottom. The column keeps the neighbour's width. Refused (same
     * state) when there is no neighbour, the neighbour already holds two, or {@code id} is itself
     * stacked.
     */
    public static State stackInto(State state, Viewport v, long id, int direction) {
        int index = state.columnIndexOf(id);
        if (index < 0 || state.columns.get(index).windows.length != 1) return state;
        int target = index + (direction < 0 ? -1 : 1);
        if (target < 0 || target >= state.columns.size()) return state;
        Column t = state.columns.get(target);
        if (t.windows.length != 1) return state;
        long[] merged = direction < 0 ? new long[] {t.windows[0], id} : new long[] {id, t.windows[0]};
        List<Column> cols = copy(state);
        cols.set(target, new Column(merged, t.widthFraction, t.preset));
        cols.remove(index);
        return settled(cols, state.focusedWindow, state.scroll, v);
    }

    /**
     * Takes {@code id} out of its two-window column into a column of its own on {@code direction}
     * of the one it leaves, at the same width. Refused (same state) for a window that is alone.
     */
    public static State unstack(State state, Viewport v, long id, int direction) {
        int index = state.columnIndexOf(id);
        if (index < 0) return state;
        Column c = state.columns.get(index);
        if (c.windows.length != 2) return state;
        long mate = c.windows[1 - c.rowOf(id)];
        List<Column> cols = copy(state);
        Column mateColumn = new Column(new long[] {mate}, c.widthFraction, c.preset);
        Column own = new Column(new long[] {id}, c.widthFraction, c.preset);
        if (direction < 0) {
            cols.set(index, own);
            cols.add(index + 1, mateColumn);
        } else {
            cols.set(index, mateColumn);
            cols.add(index + 1, own);
        }
        return settled(cols, state.focusedWindow, state.scroll, v);
    }

    /**
     * The same columns in another viewport (rotation, a resized window area): fractions are kept
     * (a third stays a third, a width the menu offers in both orientations), and the scroll is
     * pulled back inside the strip with the focused column whole.
     */
    public static State relayout(State state, Viewport v) {
        return settled(copy(state), state.focusedWindow, state.scroll, v);
    }

    private static boolean isPreset(float fraction, boolean landscape) {
        for (float p : presets(landscape)) if (Math.abs(p - fraction) < EPSILON) return true;
        return false;
    }
}
