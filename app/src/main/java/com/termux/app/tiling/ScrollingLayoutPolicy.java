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
 * A new window opens as a full-width column, right of the focused one ({@link #open}) or left of
 * it ({@link #openLeft}), and the strip scrolls to it; the column it opens beside keeps its width.
 * A lone window fills the viewport again when the others go ({@link #leftAlone}). <b>Split</b> is
 * its own action ({@link #splitWay}): side by side halves the focused column into two columns
 * ({@link #splitBeside}), top over bottom stacks the new window in it (the stack's top share is
 * {@link Column#topShare}, dragged on the gap between the two, {@link #resizeStackGap}).
 */
public final class ScrollingLayoutPolicy {

    /** Direction of a neighbouring column. */
    public static final int LEFT = -1;
    public static final int RIGHT = 1;

    /** The focused window of a Workspace with no windows. */
    public static final long NO_WINDOW = -1;

    /** The narrowest a gap drag may make a column, as a width fraction. */
    public static final float MIN_WIDTH_FRACTION = 0.25f;

    /** The least share of a stacked column's height either window keeps under a gap drag. */
    public static final float MIN_TOP_SHARE = 0.2f;

    /** The top window's share of a stacked column at first: half. */
    public static final float EVEN_SHARE = 0.5f;

    /** The shares a double tap on a stack gap snaps to. */
    private static final float[] SHARE_PRESETS = {1f / 3f, EVEN_SHARE, 2f / 3f};

    /** How a split divides the focused window. */
    public enum SplitWay {
        /** Top over bottom: the new window stacks under the focused one in its column. */
        STACK,
        /** Side by side: the focused column halves into two columns. */
        BESIDE
    }

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
        /**
         * The top window's share of the column's height (less the gap) when two are stacked,
         * between {@link #MIN_TOP_SHARE} and 1 less it; {@link #EVEN_SHARE} until a drag moves it.
         */
        public final float topShare;

        Column(long[] windows, float widthFraction, boolean preset) {
            this(windows, widthFraction, preset, EVEN_SHARE);
        }

        Column(long[] windows, float widthFraction, boolean preset, float topShare) {
            this.windows = windows.clone();
            this.widthFraction = widthFraction;
            this.preset = preset;
            this.topShare = clampShare(topShare);
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
            return new Column(windows, f, preset, topShare);
        }

        Column withTopShare(float share) {
            return new Column(windows, widthFraction, preset, share);
        }
    }

    private static float clampShare(float share) {
        if (Float.isNaN(share)) return EVEN_SHARE;
        return Math.max(MIN_TOP_SHARE, Math.min(1f - MIN_TOP_SHARE, share));
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
                float cut = (h - g) * c.topShare;
                out.add(new Placement(c.windows[0], i, 0, x, 0f, x + w, cut));
                out.add(new Placement(c.windows[1], i, 1, x, cut + g, x + w, h));
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
     * Opens {@code id} as a full-width column right after the focused window's column (last when
     * there is no focus), focuses it and scrolls it into view. The focused column keeps its width.
     */
    public static State open(State state, Viewport v, long id) {
        return openBeside(state, v, id, RIGHT);
    }

    /**
     * Opens {@code id} as a full-width column just before the focused window's column (first when
     * there is no focus), focuses it and scrolls it into view (leader+Shift+Enter).
     */
    public static State openLeft(State state, Viewport v, long id) {
        return openBeside(state, v, id, LEFT);
    }

    private static State openBeside(State state, Viewport v, long id, int side) {
        List<Column> cols = copy(state);
        int focusedIndex = state.columnIndexOf(state.focusedWindow);
        int at = side == LEFT ? Math.max(0, focusedIndex) : focusedIndex >= 0 ? focusedIndex + 1 : cols.size();
        cols.add(at, new Column(new long[] {id}, OPENED_WIDTH, true));
        return settled(cols, id, state.scroll, v);
    }

    /** The width a newly opened window's column takes: the whole viewport, beside others too. */
    public static final float OPENED_WIDTH = PRESET_FULL;

    /**
     * The one column left when the others have gone: a preset width fills the viewport again (a
     * lone window stands full width); a dragged width is kept.
     */
    public static Column leftAlone(Column column) {
        return column.preset ? column.withWidth(PRESET_FULL, true) : column;
    }

    // -- resizing by keys (round 3, K1) -----------------------------------------------------------

    /** The widths leader+R steps through, narrowest first. */
    private static final float[] CYCLE = {PRESET_THIRD, PRESET_HALF, PRESET_TWO_THIRDS, PRESET_FULL};

    /** How far leader+minus and leader+equals move a width or a stack's share. */
    public static final float NUDGE = 0.1f;

    /**
     * Leader+R ({@code step} 1) and leader+Shift+R (-1): the column holding {@code id} takes the
     * next width of a third, a half, two thirds and full, wrapping round; a width between them goes
     * to the next one that way. Same state when {@code id} has no column.
     */
    public static State cycleWidth(State state, Viewport v, long id, int step) {
        int index = state.columnIndexOf(id);
        if (index < 0) return state;
        float f = state.columns.get(index).widthFraction;
        float next;
        if (step >= 0) {
            next = CYCLE[0];
            for (float p : CYCLE) if (p > f + EPSILON) { next = p; break; }
        } else {
            next = CYCLE[CYCLE.length - 1];
            for (int i = CYCLE.length - 1; i >= 0; i--) if (CYCLE[i] < f - EPSILON) { next = CYCLE[i]; break; }
        }
        return withColumnWidth(state, v, index, next, true, true);
    }

    /**
     * Leader+minus and leader+equals: the column holding {@code id} narrows or widens by
     * {@code delta} of the viewport, between {@link #MIN_WIDTH_FRACTION} and full.
     */
    public static State nudgeWidth(State state, Viewport v, long id, float delta) {
        int index = state.columnIndexOf(id);
        if (index < 0) return state;
        float f = state.columns.get(index).widthFraction + delta;
        f = Math.max(MIN_WIDTH_FRACTION, Math.min(1f, f));
        if (Math.abs(f - state.columns.get(index).widthFraction) < EPSILON) return state;
        return withColumnWidth(state, v, index, f, isPreset(f, v.landscape), true);
    }

    /**
     * Leader+Shift+minus and leader+Shift+equals: window {@code id}, stacked, grows ({@code delta}
     * above 0) or shrinks by that share of its column's height, its mate taking the rest. Same state
     * for a window alone in its column.
     */
    public static State nudgeHeight(State state, Viewport v, long id, float delta) {
        int index = state.columnIndexOf(id);
        if (index < 0) return state;
        Column c = state.columns.get(index);
        if (c.windows.length != 2) return state;
        float share = c.topShare + (c.rowOf(id) == 0 ? delta : -delta);
        Column next = c.withTopShare(share);
        if (Math.abs(next.topShare - c.topShare) < EPSILON) return state;
        return withColumn(state, index, next);
    }

    /** Leader+C (K9): the strip scrolls so the column holding {@code id} stands in the middle, as far as the strip allows. */
    public static State centre(State state, Viewport v, long id) {
        int index = state.columnIndexOf(id);
        if (index < 0) return state;
        float left = columnLeft(state, v, index);
        float width = pixelWidth(state.columns.get(index).widthFraction, v);
        float scroll = Math.max(0f, Math.min(maxScroll(state, v), left + width / 2f - v.width / 2f));
        return new State(state.columns, state.focusedWindow, scroll);
    }

    /** The column holding {@code id} at {@code fraction} ({@code preset} or dragged); same state when it has none. */
    public static State withWidthOf(State state, Viewport v, long id, float fraction, boolean preset) {
        int index = state.columnIndexOf(id);
        if (index < 0) return state;
        return withColumnWidth(state, v, index, Math.max(MIN_WIDTH_FRACTION, Math.min(1f, fraction)), preset, true);
    }

    // -- split ---------------------------------------------------------------------------------

    /**
     * How Split divides window {@code id}: along its longer side, top over bottom in portrait and
     * side by side in landscape. Where that way has no room it takes the other: a column already
     * holding two windows splits side by side, and a landscape column whose halves would fall
     * under {@link #MIN_WIDTH_FRACTION} stacks. Null when {@code id} has no column.
     */
    public static SplitWay splitWay(State state, Viewport v, long id) {
        int index = state.columnIndexOf(id);
        if (index < 0) return null;
        Column c = state.columns.get(index);
        boolean stackFull = c.windows.length >= 2;
        boolean halvesFit = c.widthFraction / 2f >= MIN_WIDTH_FRACTION - EPSILON;
        if (stackFull) return SplitWay.BESIDE;
        if (v.landscape) return halvesFit ? SplitWay.BESIDE : SplitWay.STACK;
        return SplitWay.STACK;
    }

    /**
     * Split the other way (leader+Shift+J): the way {@code way} is not, where it has room (a stack
     * of two cannot take a third; a column too narrow to halve stacks); else {@code way}.
     */
    public static SplitWay otherWay(State state, Viewport v, long id, SplitWay way) {
        int index = state.columnIndexOf(id);
        if (index < 0 || way == null) return way;
        Column c = state.columns.get(index);
        if (way == SplitWay.BESIDE) return c.windows.length >= 2 ? SplitWay.BESIDE : SplitWay.STACK;
        return c.widthFraction / 2f >= MIN_WIDTH_FRACTION - EPSILON ? SplitWay.BESIDE : SplitWay.STACK;
    }

    /**
     * Split side by side: the column holding {@code id} halves, and {@code newId}, a column of its
     * own just after it (opened there already, at any width), takes the other half. The halves
     * keep a preset when the column had one; neither goes under {@link #MIN_WIDTH_FRACTION}.
     * Focus goes to {@code newId}. The same state when either is missing or they share a column.
     */
    public static State splitBeside(State state, Viewport v, long id, long newId) {
        int index = state.columnIndexOf(id);
        int added = state.columnIndexOf(newId);
        if (index < 0 || added != index + 1 || state.columns.get(added).windows.length != 1) return state;
        Column c = state.columns.get(index);
        float half = Math.max(MIN_WIDTH_FRACTION, c.widthFraction / 2f);
        boolean preset = c.preset && isPreset(half, v.landscape);
        List<Column> cols = copy(state);
        cols.set(index, c.withWidth(half, preset));
        cols.set(added, cols.get(added).withWidth(half, preset));
        return settled(cols, newId, state.scroll, v);
    }

    /**
     * {@code newId}, a column of its own just {@code side} of the column holding {@code id}, takes
     * that column's width (a window that left a stack keeps the stack's width). The same state
     * when either is missing.
     */
    public static State matchWidth(State state, Viewport v, long id, long newId) {
        int index = state.columnIndexOf(id);
        int added = state.columnIndexOf(newId);
        if (index < 0 || added < 0 || index == added) return state;
        Column c = state.columns.get(index);
        List<Column> cols = copy(state);
        cols.set(added, cols.get(added).withWidth(c.widthFraction, c.preset));
        return settled(cols, state.focusedWindow, state.scroll, v);
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
     * A drag on the gap between the two windows stacked in column {@code column}: the top window
     * grows or shrinks by {@code deltaPixels} (the gap follows the finger), each keeping at least
     * {@link #MIN_TOP_SHARE} of the column. The same state for a column that holds one window.
     */
    public static State resizeStackGap(State state, Viewport v, int column, float deltaPixels) {
        if (column < 0 || column >= state.columns.size()) return state;
        Column c = state.columns.get(column);
        if (c.windows.length != 2) return state;
        float room = Math.max(0f, v.height) - Math.min(Math.max(0f, v.gap), Math.max(0f, v.height));
        if (room <= 0f) return state;
        return withColumn(state, column, c.withTopShare(c.topShare + deltaPixels / room));
    }

    /** A double tap on the gap in stacked column {@code column}: the top share snaps to the nearest of a third, a half, two thirds. */
    public static State doubleTapStackGap(State state, Viewport v, int column) {
        if (column < 0 || column >= state.columns.size()) return state;
        Column c = state.columns.get(column);
        if (c.windows.length != 2) return state;
        float best = EVEN_SHARE;
        for (float p : SHARE_PRESETS) if (Math.abs(p - c.topShare) < Math.abs(best - c.topShare)) best = p;
        return withColumn(state, column, c.withTopShare(best));
    }

    private static State withColumn(State state, int index, Column column) {
        List<Column> cols = copy(state);
        cols.set(index, column);
        return new State(cols, state.focusedWindow, state.scroll);
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
