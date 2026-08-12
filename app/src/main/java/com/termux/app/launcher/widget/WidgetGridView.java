package com.termux.app.launcher.widget;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Non-scrolling exact-cell host. Provider collections retain their own nested scrolling. */
public final class WidgetGridView extends ViewGroup {
    public interface Listener {
        void onWidgetLongPressed(int appWidgetId, float rawX, float rawY);
        default void onWidgetEditDragMove(int appWidgetId, float rawX, float rawY) { }
        default void onWidgetEditDragEnd(int appWidgetId, boolean canceled) { }
    }

    @Nullable private LauncherWidgetHostController controller;
    @Nullable private Listener listener;
    @NonNull private WidgetGridDefinition definition = WidgetGridDefinition.DEFAULT;
    @NonNull private List<LauncherWidgetRecord> records = Collections.emptyList();
    private final Map<Integer, WidgetCellView> cells = new HashMap<>();
    private final Map<Integer, Long> committedSizes = new HashMap<>();
    private final Map<Integer, Long> deliveredSizes = new HashMap<>();
    private final int edgePadding;
    private final int gap;

    public WidgetGridView(@NonNull Context context) {
        super(context);
        edgePadding = Math.round(4f * getResources().getDisplayMetrics().density);
        gap = Math.round(4f * getResources().getDisplayMetrics().density);
        setClipChildren(true);
        setClipToPadding(true);
        setChildrenDrawingOrderEnabled(true);
    }

    public void bind(@NonNull LauncherWidgetHostController value) {
        controller = value;
        refresh(value.repository().gridDefinition(), value.repository().records());
    }

    public void setListener(@Nullable Listener value) { listener = value; }

    public void refresh(@NonNull WidgetGridDefinition grid,
                        @NonNull List<LauncherWidgetRecord> snapshot) {
        definition = grid;
        ArrayList<LauncherWidgetRecord> ordered = new ArrayList<>(snapshot);
        ordered.sort(Comparator.comparingInt((LauncherWidgetRecord r) -> r.cell.top)
            .thenComparingInt(r -> r.cell.left).thenComparingInt(r -> r.appWidgetId));
        records = Collections.unmodifiableList(ordered);
        Set<Integer> live = new HashSet<>();
        for (LauncherWidgetRecord record : records) {
            live.add(record.appWidgetId);
            WidgetCellView cell = cells.get(record.appWidgetId);
            if (cell == null) {
                cell = new WidgetCellView(getContext());
                cell.setId(ViewCompat.generateViewId());
                cells.put(record.appWidgetId, cell);
                addView(cell);
            }
            final int cellWidgetId = record.appWidgetId;
            cell.setLongPressListener(new WidgetCellView.LongPressListener() {
                @Override public void onWidgetLongPress(float rawX, float rawY) {
                    if (listener != null) listener.onWidgetLongPressed(cellWidgetId, rawX, rawY);
                }
                @Override public void onEditDragMove(float rawX, float rawY) {
                    if (listener != null) listener.onWidgetEditDragMove(cellWidgetId, rawX, rawY);
                }
                @Override public void onEditDragEnd(boolean canceled) {
                    if (listener != null) listener.onWidgetEditDragEnd(cellWidgetId, canceled);
                }
            });
            View content = null;
            if (record.state == LauncherWidgetRecord.State.ACTIVE && controller != null) {
                content = controller.createHostView(record.appWidgetId);
            }
            if (content == null || record.state == LauncherWidgetRecord.State.PROVIDER_MISSING) {
                TextView placeholder = new TextView(getContext());
                placeholder.setGravity(Gravity.CENTER);
                placeholder.setText(record.state == LauncherWidgetRecord.State.PROVIDER_MISSING
                    ? "Widget unavailable" : "Widget couldn’t load");
                placeholder.setContentDescription(placeholder.getText());
                content = placeholder;
            }
            if (cell.getChildCount() != 1 || cell.getChildAt(0) != content) cell.setContent(content);
        }
        ArrayList<Integer> stale = new ArrayList<>();
        for (Map.Entry<Integer, WidgetCellView> entry : cells.entrySet()) {
            if (!live.contains(entry.getKey())) {
                removeView(entry.getValue()); stale.add(entry.getKey());
            }
        }
        for (int id : stale) { cells.remove(id); committedSizes.remove(id); deliveredSizes.remove(id); }
        requestLayout();
    }

    @NonNull public WidgetGridMetrics metrics() {
        return new WidgetGridMetrics(new Rect(0, 0, getWidth(), getHeight()), 0,
            edgePadding, gap, definition, getLayoutDirection() == LAYOUT_DIRECTION_RTL);
    }

    @Nullable public WidgetCellView cellForId(int id) { return cells.get(id); }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        setMeasuredDimension(resolveSize(width, widthMeasureSpec), resolveSize(height, heightMeasureSpec));
        WidgetGridMetrics metrics = new WidgetGridMetrics(new Rect(0, 0, width, height), 0,
            edgePadding, gap, definition, getLayoutDirection() == LAYOUT_DIRECTION_RTL);
        for (LauncherWidgetRecord record : records) {
            WidgetCellView child = cells.get(record.appWidgetId);
            if (child == null) continue;
            Rect bounds = metrics.boundsFor(record.cell);
            child.measure(MeasureSpec.makeMeasureSpec(bounds.width(), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(bounds.height(), MeasureSpec.EXACTLY));
        }
    }

    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        WidgetGridMetrics metrics = metrics();
        for (LauncherWidgetRecord record : records) {
            WidgetCellView child = cells.get(record.appWidgetId);
            if (child == null) continue;
            Rect bounds = metrics.boundsFor(record.cell);
            child.layout(bounds.left, bounds.top, bounds.right, bounds.bottom);
            if (record.state == LauncherWidgetRecord.State.ACTIVE) {
                final int orientation = getResources().getConfiguration().orientation;
                final int contentWidth = Math.max(1, child.getWidth() - child.getPaddingLeft()
                    - child.getPaddingRight());
                final int contentHeight = Math.max(1, child.getHeight() - child.getPaddingTop()
                    - child.getPaddingBottom());
                long packed = packSize(contentWidth, contentHeight, orientation);
                Long previous = committedSizes.put(record.appWidgetId, packed);
                if (previous == null || previous.longValue() != packed) {
                    final int id = record.appWidgetId;
                    post(() -> {
                        Long current = committedSizes.get(id);
                        if (current == null || current.longValue() != packed
                            || packed == deliveredSizes.getOrDefault(id, Long.MIN_VALUE)) return;
                        if (controller != null && isShown()) {
                            controller.onHostSizeCommitted(id, contentWidth, contentHeight,
                                orientation);
                            deliveredSizes.put(id, packed);
                        }
                    });
                }
            }
        }
    }

    private static long packSize(int width, int height, int orientation) {
        return ((long) (orientation & 0xff) << 56)
            | ((long) (width & 0x0fffffff) << 28)
            | (height & 0x0fffffffL);
    }
}
