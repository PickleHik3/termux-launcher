package com.termux.app.launcher.widget;

import android.content.Context;
import android.graphics.Rect;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
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
import com.termux.app.haptics.Haptics;

/** Non-scrolling exact-cell host. Provider collections retain their own nested scrolling. */
public final class WidgetGridView extends ViewGroup {
    public interface Listener {
        void onWidgetLongPressed(int appWidgetId, float rawX, float rawY);
        default void onWidgetEditDragMove(int appWidgetId, float rawX, float rawY) { }
        default void onWidgetEditDragEnd(int appWidgetId, boolean canceled) { }
        /** Long press on cell-free grid surface (gaps, edges, empty pages). */
        default void onEmptySpaceLongPressed(float rawX, float rawY) { }
        /** A provider text editor gained (non-null) or lost (null) focus inside a cell. */
        default void onWidgetEditorFocusChanged(@Nullable View editor) { }
    }

    @Nullable private LauncherWidgetHostController controller;
    @Nullable private Listener listener;
    @NonNull private WidgetGridDefinition definition = WidgetGridDefinition.DEFAULT;
    @Nullable private BuiltinFactory builtinFactory;
    @NonNull private List<LauncherWidgetRecord> records = Collections.emptyList();
    private final Map<Integer, WidgetCellView> cells = new HashMap<>();
    private final Map<Integer, Long> committedSizes = new HashMap<>();
    private final Map<Integer, Long> deliveredSizes = new HashMap<>();
    /** Widgets whose cell changed size since the last {@link #resyncSizes}. */
    private final Set<Integer> resizedSinceResync = new HashSet<>();
    /** Widgets a resync asked to be told their size again, whatever the ledger says. */
    private final Set<Integer> retellSize = new HashSet<>();
    /** The rim round the cells, in px: Home's Margin, pushed in through {@link #setEdgePadding}. */
    private int edgePadding;
    private final int gap;
    private final int touchSlop;
    private final Runnable emptyLongPressFire = this::fireEmptyLongPress;
    private boolean emptyLongPressPending;
    /** Whether a pane corner may still claim the finger that is down; see {@link #setHoldExempt}. */
    private boolean holdExempt;
    private float emptyDownX, emptyDownY;
    private float emptyDownRawX, emptyDownRawY;

    public WidgetGridView(@NonNull Context context) {
        super(context);
        gap = Math.round(WidgetGridMetrics.GAP_DP * getResources().getDisplayMetrics().density);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        setClipChildren(true);
        setClipToPadding(true);
        setChildrenDrawingOrderEnabled(true);
        // Focus flows to provider editors, never to the grid itself.
        setDescendantFocusability(FOCUS_AFTER_DESCENDANTS);
        setFocusable(false);
    }

    public void bind(@NonNull LauncherWidgetHostController value) {
        controller = value;
        refresh(value.repository().gridDefinition(), value.repository().recordsOnPage(0));
    }

    public void setListener(@Nullable Listener value) { listener = value; }

    /**
     * The rim between the page's edge and the cells, in px: Home's Margin, which the activity
     * pushes in ({@code WidgetPaneController#setGridEdge}). A change re-lays the cells at once;
     * the sizes reach the providers through the same settle any other layout change waits for.
     */
    public void setEdgePadding(int px) {
        int wanted = Math.max(0, px);
        if (edgePadding == wanted) return;
        edgePadding = wanted;
        requestLayout();
        invalidate();
    }

    /**
     * The page's frame says a corner square may still take this finger, so the grid and its cells
     * leave the long press to it: a press that lands in a corner belongs to the corner, and the
     * grid never opens its menu underneath the tab that hold brings out. It is handed straight
     * back when the corner gives the gesture up.
     */
    public void setHoldExempt(boolean exempt) {
        if (holdExempt == exempt) return;
        holdExempt = exempt;
        if (exempt) cancelEmptyLongPress();
        for (WidgetCellView cell : cells.values()) cell.setHoldExempt(exempt);
    }

    /**
     * The widget a finger is carrying to another page. Its cell stays attached through the flip,
     * hidden and unplaced, because the gesture the user is still making is being delivered through
     * that view: removing it with the page it left would cancel the drag mid-air. -1 for none.
     */
    private int dragPinnedId = -1;

    public void setDragPinned(int appWidgetId) {
        if (dragPinnedId == appWidgetId) return;
        int released = dragPinnedId;
        dragPinnedId = appWidgetId;
        if (released < 0) return;
        for (LauncherWidgetRecord record : records) {
            if (record.appWidgetId == released) return; // it landed on the page showing now
        }
        WidgetCellView cell = cells.remove(released);
        if (cell != null) removeView(cell);
        forgetSizes(released);
    }

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
                cell.setHoldExempt(holdExempt);
                cell.setId(ViewCompat.generateViewId());
                cells.put(record.appWidgetId, cell);
                addView(cell);
            }
            final int cellWidgetId = record.appWidgetId;
            cell.setEditorFocusListener(editor -> {
                if (listener != null) listener.onWidgetEditorFocusChanged(editor);
            });
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
            if (record.isBuiltin()) {
                // The launcher's own view, asked for on every render so a resize or a settings
                // change reaches the one already on screen.
                content = builtinFactory == null ? null : builtinFactory.viewFor(record);
            } else if (record.state == LauncherWidgetRecord.State.ACTIVE && controller != null) {
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
        // The widget in the air belongs to no page while it crosses; its cell stays all the same.
        if (dragPinnedId >= 0) live.add(dragPinnedId);
        ArrayList<Integer> stale = new ArrayList<>();
        for (Map.Entry<Integer, WidgetCellView> entry : cells.entrySet()) {
            if (!live.contains(entry.getKey())) {
                removeView(entry.getValue()); stale.add(entry.getKey());
            }
        }
        for (int id : stale) {
            cells.remove(id); forgetSizes(id);
            if (id < 0 && builtinFactory != null) builtinFactory.release(id);
        }
        requestLayout();
    }

    /**
     * Cell-free surface touches end here (cells consume their own streams). The grid accepts the
     * stream only to watch for a long press: a slop move or UP releases it, and both the pane's
     * page swipe and the status pane's pull-up take over via the normal intercept CANCEL.
     */
    @Override public boolean onTouchEvent(@NonNull MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                emptyDownX = event.getX(); emptyDownY = event.getY();
                emptyDownRawX = event.getRawX(); emptyDownRawY = event.getRawY();
                if (!holdExempt) {
                    emptyLongPressPending = true;
                    postDelayed(emptyLongPressFire,
                        ViewConfiguration.getLongPressTimeout());
                }
                return true;
            case MotionEvent.ACTION_MOVE:
                if (emptyLongPressPending && Math.hypot(event.getX() - emptyDownX,
                    event.getY() - emptyDownY) > touchSlop) cancelEmptyLongPress();
                return true;
            case MotionEvent.ACTION_POINTER_DOWN:
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                cancelEmptyLongPress();
                return true;
            default:
                return true;
        }
    }

    private void cancelEmptyLongPress() {
        emptyLongPressPending = false;
        removeCallbacks(emptyLongPressFire);
    }

    private void fireEmptyLongPress() {
        if (!emptyLongPressPending) return;
        emptyLongPressPending = false;
        Haptics.tick(this, HapticFeedbackConstants.LONG_PRESS);
        if (listener != null) listener.onEmptySpaceLongPressed(emptyDownRawX, emptyDownRawY);
    }

    @Override protected void onDetachedFromWindow() {
        cancelEmptyLongPress();
        super.onDetachedFromWindow();
    }

    @NonNull public WidgetGridMetrics metrics() {
        return new WidgetGridMetrics(new Rect(0, 0, getWidth(), getHeight()), 0,
            edgePadding, gap, definition, getLayoutDirection() == LAYOUT_DIRECTION_RTL,
            reservedRows);
    }

    /** Rows kept empty under the grid for an always-on keyboard; see {@link #setReservedRows}. */
    private int reservedRows;

    /**
     * The rows an always-on keyboard took off the grid ({@code WidgetPaneController
     * #applyWantedGrid}). The grid keeps its whole height on Home, under the keyboard too, so the
     * cells are still cut as if those rows were there: every cell keeps its height and the grid
     * ends that many rows above the bottom, rather than its fewer rows stretching to fill it.
     */
    public void setReservedRows(int rows) {
        int wanted = Math.max(0, rows);
        if (reservedRows == wanted) return;
        reservedRows = wanted;
        requestLayout();
        invalidate();
    }

    public int reservedRows() {
        return reservedRows;
    }

    @Nullable public WidgetCellView cellForId(int id) { return cells.get(id); }

    /** Supplies the views of the launcher's own widgets; the grid never builds one itself. */
    public interface BuiltinFactory {
        /** The live view for {@code record}, brought up to date with its span and settings. */
        @Nullable View viewFor(@NonNull LauncherWidgetRecord record);
        /** The cell for {@code appWidgetId} left this page; its view may be dropped. */
        default void release(int appWidgetId) { }
    }

    public void setBuiltinFactory(@Nullable BuiltinFactory value) { builtinFactory = value; }

    @Override protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int height = MeasureSpec.getSize(heightMeasureSpec);
        setMeasuredDimension(resolveSize(width, widthMeasureSpec), resolveSize(height, heightMeasureSpec));
        WidgetGridMetrics metrics = new WidgetGridMetrics(new Rect(0, 0, width, height), 0,
            edgePadding, gap, definition, getLayoutDirection() == LAYOUT_DIRECTION_RTL,
            reservedRows);
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
                long packed = packSize(child.providerContentWidth(),
                    child.providerContentHeight(), orientation);
                Long previous = committedSizes.put(record.appWidgetId, packed);
                if (previous != null && previous.longValue() != packed) {
                    resizedSinceResync.add(record.appWidgetId);
                }
                if (previous == null || previous.longValue() != packed
                    || packed != deliveredSizes.getOrDefault(record.appWidgetId, Long.MIN_VALUE)) {
                    sizeDeliveryPending = true;
                }
            }
        }
        if (sizeDeliveryPending) scheduleSizeDelivery();
    }

    private boolean sizeDeliveryPending;

    /**
     * Whether the wall rests on this page. Being on screen is not enough: the terminal's keyboard
     * resizes the whole wall, so a page sliding out or in passes through sizes no one sees at
     * rest, and a provider told one re-renders for it — then again on the way back, which is the
     * widget squeezed and snapping to size after the page lands. At rest by default, for a grid
     * that stands on no wall.
     */
    private boolean pageAtRest = true;

    /** The wall came to rest on this page, or left it; a size held meanwhile goes out now. */
    public void setPageAtRest(boolean atRest) {
        boolean arrived = atRest && !pageAtRest;
        pageAtRest = atRest;
        // Held sizes go out on arrival, and on any later word that the wall rests here: a size
        // held for a reason the grid cannot see (off screen, not yet attached) is not left behind.
        if (atRest && (arrived || sizeDeliveryPending) && hasUndeliveredSize()) {
            sizeDeliveryPending = true;
            scheduleSizeDelivery();
        }
    }

    /**
     * A session that may have resized the cells several times has ended (the Appearance editor's
     * Done, Undo or Discard): every widget whose cell changed size since the last resync is told
     * its size again once the grid rests, even where the ledger says the provider has it. The
     * cells can pass through several sizes in a session (each Margin tick, a Style's new chrome,
     * the Undo back), a provider renders each asynchronously, and the widget must end on the size
     * it is shown at, not on whichever render landed last (2026-10-18: a Clock drawn for the
     * Margin before an Undo, kept until a restart). Widgets that did not move are not woken.
     */
    public void resyncSizes() {
        retellSize.addAll(resizedSinceResync);
        resizedSinceResync.clear();
        sizeDeliveryPending = true;
        scheduleSizeDelivery();
    }

    private boolean hasUndeliveredSize() {
        if (!retellSize.isEmpty()) return true;
        for (Map.Entry<Integer, Long> entry : committedSizes.entrySet()) {
            if (!entry.getValue().equals(deliveredSizes.get(entry.getKey()))) return true;
        }
        return false;
    }

    private void forgetSizes(int appWidgetId) {
        committedSizes.remove(appWidgetId);
        deliveredSizes.remove(appWidgetId);
        resizedSinceResync.remove(appWidgetId);
        retellSize.remove(appWidgetId);
    }

    /**
     * A layout pass that changed a cell's size waits this long for the next one before the size
     * reaches the provider. The grid is laid out once per frame while the wall slides or the
     * status bar animates between places, and each delivery makes the provider re-render and push
     * new RemoteViews — ~200 ms of main thread per widget on Pong. One delivery per settled size,
     * not one per frame.
     */
    static final long SIZE_DELIVERY_SETTLE_MS = 160L;
    private final Runnable deliverCommittedSizesRunnable = this::deliverCommittedSizes;

    private void scheduleSizeDelivery() {
        removeCallbacks(deliverCommittedSizesRunnable);
        postDelayed(deliverCommittedSizesRunnable, SIZE_DELIVERY_SETTLE_MS);
    }

    /**
     * Tell each provider the size its widget really has — the cell less the gutter and less the
     * host view's own padding, which is what the framework measures its layout by — once the
     * grid is on screen. The grid is first laid out while its page waits off screen, so this
     * cannot happen in that layout pass and must run again when the page comes into view — a
     * provider that never hears its size lays out for the size it assumed and shows up cut off
     * at the edges.
     */
    private void deliverCommittedSizes() {
        if (controller == null || !isShown() || !pageAtRest) return;
        sizeDeliveryPending = false;
        int orientation = getResources().getConfiguration().orientation;
        for (LauncherWidgetRecord record : records) {
            if (record.state != LauncherWidgetRecord.State.ACTIVE) continue;
            Long packed = committedSizes.get(record.appWidgetId);
            boolean retell = retellSize.contains(record.appWidgetId);
            if (packed == null || (!retell
                && packed == deliveredSizes.getOrDefault(record.appWidgetId, Long.MIN_VALUE))) {
                continue;
            }
            WidgetCellView child = cells.get(record.appWidgetId);
            if (child == null) continue;
            controller.onHostSizeCommitted(record.appWidgetId, child.providerContentWidth(),
                child.providerContentHeight(), orientation, retell);
            deliveredSizes.put(record.appWidgetId, packed);
            retellSize.remove(record.appWidgetId);
        }
    }

    @Override
    protected void onVisibilityChanged(@NonNull View changedView, int visibility) {
        super.onVisibilityChanged(changedView, visibility);
        if (visibility == VISIBLE && sizeDeliveryPending) scheduleSizeDelivery();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (sizeDeliveryPending) scheduleSizeDelivery();
    }

    private static long packSize(int width, int height, int orientation) {
        return ((long) (orientation & 0xff) << 56)
            | ((long) (width & 0x0fffffff) << 28)
            | (height & 0x0fffffffL);
    }
}
