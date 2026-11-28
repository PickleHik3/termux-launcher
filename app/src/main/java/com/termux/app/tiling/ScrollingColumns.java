package com.termux.app.tiling;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * A Scrolling Workspace's columns, read off the stack (#418). The stack owns which windows a
 * Workspace has, their order and its focus; {@link ScrollingLayoutPolicy} owns the geometry. This
 * joins the two without a second copy of the order: {@link #sync} builds the policy's state from
 * the Workspace's tile groups each time, carrying each column's width and the strip's scroll over
 * from the state before, and settling the scroll on the focused column. The widths and scroll are
 * the Workspace's layout state ({@link TilingStack#setLayoutState}), saved through
 * {@link #encode} and read back through {@link #decode}.
 *
 * <p>Pure: no Android views.
 */
public final class ScrollingColumns {

    private ScrollingColumns() {}

    /**
     * The policy's state for {@code workspace}: one column per tile group in order (a stacked
     * group's windows top over bottom, a tabbed group's active window), focused where the
     * Workspace is (none while that window is minimised), each column at the width it had in
     * {@code previous} while its top window stays (a stack keeps its top share while it holds the
     * same two windows); a column new since {@code previous} opens full width
     * ({@link ScrollingLayoutPolicy#OPENED_WIDTH}) and the others keep theirs; a column left alone
     * fills the viewport again ({@link ScrollingLayoutPolicy#leftAlone}); with no {@code previous}
     * every column is full. The scroll moves only as far as needed to show the focused column whole.
     */
    @NonNull
    public static ScrollingLayoutPolicy.State sync(@NonNull TilingWorkspace workspace,
                                                   @Nullable ScrollingLayoutPolicy.State previous,
                                                   @NonNull ScrollingLayoutPolicy.Viewport viewport) {
        List<ScrollingLayoutPolicy.Column> columns = new ArrayList<>();
        for (TileGroup group : workspace.groups()) {
            long[] ids = idsOf(group);
            if (ids.length == 0) continue;
            ScrollingLayoutPolicy.Column before = previous == null ? null : columnOfGroup(previous, group, ids[0]);
            // A window opened since (a cold read has none) stands full width: round 3's new window.
            columns.add(before == null
                ? new ScrollingLayoutPolicy.Column(ids, ScrollingLayoutPolicy.OPENED_WIDTH, true)
                : new ScrollingLayoutPolicy.Column(ids, before.widthFraction, before.preset, shareOf(before, ids)));
        }
        // The others went (closed, floated, moved away): a lone window stands full width again.
        if (previous != null && previous.columnCount() > 1 && columns.size() == 1) {
            columns.set(0, ScrollingLayoutPolicy.leftAlone(columns.get(0)));
        }
        TilingWindow focused = workspace.focused;
        long focusedId = focused != null && !focused.isFloat() && !focused.isMinimised()
            ? focused.id() : ScrollingLayoutPolicy.NO_WINDOW;
        float scroll = previous == null ? 0f : previous.scroll;
        return ScrollingLayoutPolicy.relayout(new ScrollingLayoutPolicy.State(columns, focusedId, scroll),
            viewport);
    }

    /** {@code before}'s top share while it stacks the very windows {@code ids}; else even. */
    private static float shareOf(@NonNull ScrollingLayoutPolicy.Column before, @NonNull long[] ids) {
        if (ids.length != 2 || before.windowCount() != 2) return ScrollingLayoutPolicy.EVEN_SHARE;
        return before.windowAt(0) == ids[0] && before.windowAt(1) == ids[1]
            ? before.topShare : ScrollingLayoutPolicy.EVEN_SHARE;
    }

    /** The widths and scroll of {@code state}, as the Workspace's saved layout state. */
    @NonNull
    public static String encode(@NonNull ScrollingLayoutPolicy.State state) {
        try {
            JSONObject json = new JSONObject();
            json.put("scroll", (double) state.scroll);
            JSONArray columns = new JSONArray();
            for (int i = 0; i < state.columnCount(); i++) {
                ScrollingLayoutPolicy.Column column = state.column(i);
                JSONObject c = new JSONObject();
                JSONArray windows = new JSONArray();
                for (int row = 0; row < column.windowCount(); row++) windows.put(column.windowAt(row));
                c.put("windows", windows);
                c.put("width", (double) column.widthFraction);
                c.put("preset", column.preset);
                if (column.windowCount() == 2) c.put("top", (double) column.topShare);
                columns.put(c);
            }
            json.put("columns", columns);
            return json.toString();
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * A state read back from {@link #encode}'s form, to pass to {@link #sync} as the state before:
     * its columns name windows and widths only, with no focus. Null for anything unreadable;
     * widths outside the policy's range are clamped and empty columns dropped.
     */
    @Nullable
    public static ScrollingLayoutPolicy.State decode(@Nullable String saved) {
        if (saved == null || saved.isEmpty()) return null;
        try {
            JSONObject json = new JSONObject(saved);
            JSONArray array = json.optJSONArray("columns");
            List<ScrollingLayoutPolicy.Column> columns = new ArrayList<>();
            for (int i = 0; array != null && i < array.length(); i++) {
                JSONObject c = array.optJSONObject(i);
                JSONArray windows = c == null ? null : c.optJSONArray("windows");
                if (windows == null || windows.length() == 0) continue;
                int count = Math.min(windows.length(), TilingStack.MAX_STACKED);
                long[] ids = new long[count];
                for (int row = 0; row < count; row++) ids[row] = windows.optLong(row, TilingStack.NO_WINDOW);
                float width = (float) c.optDouble("width", 1d);
                if (Float.isNaN(width)) width = 1f;
                width = Math.max(ScrollingLayoutPolicy.MIN_WIDTH_FRACTION, Math.min(1f, width));
                float top = (float) c.optDouble("top", ScrollingLayoutPolicy.EVEN_SHARE);
                columns.add(new ScrollingLayoutPolicy.Column(ids, width, c.optBoolean("preset", true), top));
            }
            float scroll = (float) json.optDouble("scroll", 0d);
            if (Float.isNaN(scroll) || scroll < 0f) scroll = 0f;
            return new ScrollingLayoutPolicy.State(columns, ScrollingLayoutPolicy.NO_WINDOW, scroll);
        } catch (JSONException e) {
            return null;
        }
    }

    @NonNull
    private static long[] idsOf(@NonNull TileGroup group) {
        List<TilingWindow> windows = group.windows();
        // A minimised window stands alone in its group, and no column shows it (#425).
        if (windows.isEmpty() || group.active().isMinimised()) return new long[0];
        if (group.mode() == TileGroup.Mode.TABS) return new long[] {group.active().id()};
        int count = Math.min(windows.size(), TilingStack.MAX_STACKED);
        long[] ids = new long[count];
        for (int i = 0; i < count; i++) ids[i] = windows.get(i).id();
        return ids;
    }

    /** {@code state}'s column whose top window is {@code top}; null when it has none (#425). */
    @Nullable
    public static ScrollingLayoutPolicy.Column columnOf(@Nullable ScrollingLayoutPolicy.State state, long top) {
        return state == null ? null : columnWithTop(state, top);
    }

    /** {@code state}'s column holding window {@code id} anywhere in it; null when none does. */
    @Nullable
    public static ScrollingLayoutPolicy.Column columnContaining(@Nullable ScrollingLayoutPolicy.State state, long id) {
        if (state == null) return null;
        for (int i = 0; i < state.columnCount(); i++) {
            ScrollingLayoutPolicy.Column column = state.column(i);
            for (int row = 0; row < column.windowCount(); row++) if (column.windowAt(row) == id) return column;
        }
        return null;
    }

    /**
     * {@code state} with {@code column} added at its end: a column kept aside while its window was
     * minimised (#425), so the next {@link #sync} gives the window back the width it had.
     */
    @NonNull
    public static ScrollingLayoutPolicy.State withColumn(@NonNull ScrollingLayoutPolicy.State state,
                                                         @NonNull ScrollingLayoutPolicy.Column column) {
        List<ScrollingLayoutPolicy.Column> columns = new ArrayList<>();
        for (int i = 0; i < state.columnCount(); i++) columns.add(state.column(i));
        columns.add(column);
        return new ScrollingLayoutPolicy.State(columns, state.focusedWindow, state.scroll);
    }

    /**
     * {@code state} with the strip at {@code scroll}: a Workspace slid sideways in navigation mode
     * (#433), shown there until the slide settles, its own scroll kept.
     */
    @NonNull
    public static ScrollingLayoutPolicy.State withScroll(@NonNull ScrollingLayoutPolicy.State state, float scroll) {
        List<ScrollingLayoutPolicy.Column> columns = new ArrayList<>();
        for (int i = 0; i < state.columnCount(); i++) columns.add(state.column(i));
        return new ScrollingLayoutPolicy.State(columns, state.focusedWindow, scroll);
    }

    /**
     * {@code group}'s column in {@code previous}: the one its shown window {@code top} heads, else
     * (tabs: another tab was shown then) the one any of its windows headed. A tab switch is no
     * opening, so the column keeps its width.
     */
    @Nullable
    private static ScrollingLayoutPolicy.Column columnOfGroup(@NonNull ScrollingLayoutPolicy.State previous,
                                                             @NonNull TileGroup group, long top) {
        ScrollingLayoutPolicy.Column shown = columnWithTop(previous, top);
        if (shown != null || group.mode() != TileGroup.Mode.TABS) return shown;
        for (TilingWindow tab : group.windows()) {
            ScrollingLayoutPolicy.Column column = columnWithTop(previous, tab.id());
            if (column != null) return column;
        }
        return null;
    }

    @Nullable
    private static ScrollingLayoutPolicy.Column columnWithTop(@NonNull ScrollingLayoutPolicy.State state,
                                                            long top) {
        for (int i = 0; i < state.columnCount(); i++) {
            ScrollingLayoutPolicy.Column column = state.column(i);
            if (column.windowCount() > 0 && column.windowAt(0) == top) return column;
        }
        return null;
    }
}
