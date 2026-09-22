package com.termux.app.launcher.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.os.SystemClock;
import android.graphics.Paint;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.util.AttributeSet;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;

import com.termux.R;

import java.util.List;

/** The widget grid's own body, a page of the pane wall; it has no authority over the wall or the terminal's geometry. */
public final class WidgetPaneView extends FrameLayout {
    public interface Listener {
        /** The horizontal page swipe committed; the coordinator re-renders onto this page. */
        void onPageChangeRequested(int page);
        /** The tick on the page's border tab: keep the layout and close edit mode. */
        default void onWidgetEditCommit() { }
        /** The cross: put the layout back as it was when edit mode opened, and close. */
        default void onWidgetEditDiscard() { }
        /** The + on the page's border tab: another page, and the pane turns to it. */
        default void onWidgetAddPage() { }
    }

    /**
     * Where the finger carrying a widget out of the picker is, and where it let go.
     *
     * <p>The picker is a sheet over this pane, so a widget dragged out of it starts its life as a
     * press on a list row inside a child of this view and has to end as a drop on the grid, with
     * the sheet dismissed in between. Nothing below can follow that: the row it started on is
     * recycled away with the sheet. So the pane itself takes the stream over for the rest of the
     * gesture and reports it here in screen coordinates.
     */
    public interface CarryListener {
        void onCarryMove(float rawX, float rawY);
        void onCarryEnd(float rawX, float rawY, boolean canceled);
    }

    /** Fraction of the pane width a released drag must cross to commit a page switch. */
    private static final float PAGE_COMMIT_FRACTION = 1f / 3f;
    private static final float PAGE_COMMIT_VELOCITY_DP_PER_SEC = 900f;
    private static final float PAGE_EDGE_RESISTANCE = 0.35f;

    private final WidgetGridView grid;
    private final LinearLayout empty;
    private final TextView emptyMessage;
    private final TextView notice;
    private final WidgetPickerSheetView picker;
    private final PageDotsView dots;
    private final Runnable hideNotice;
    private final int touchSlop;
    private Listener listener;
    private boolean reducedMotion;

    private int pageCount = 1;
    private int currentPage;
    private boolean pagingTracking;
    private boolean pagingDragging;
    private float pagingDownX, pagingDownY;
    private boolean pageCommitPending;
    private int pageCommitDirection;
    private float pageCommitDragX;
    @Nullable private VelocityTracker pagingVelocity;

    public WidgetPaneView(@NonNull Context context, AttributeSet attrs) { this(context); }

    public WidgetPaneView(@NonNull Context context) {
        super(context); setId(R.id.widget_pane); setClipChildren(true); setClipToPadding(true);
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();
        // Provider text inputs may take focus; the pane and its hosts never keep it themselves.
        setDescendantFocusability(FOCUS_AFTER_DESCENDANTS);
        setFocusable(false);

        grid = new WidgetGridView(context); grid.setId(R.id.widget_grid);
        addView(grid, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        empty = new LinearLayout(context); empty.setOrientation(LinearLayout.VERTICAL);
        empty.setGravity(Gravity.CENTER);
        emptyMessage = new TextView(context); emptyMessage.setId(R.id.widget_empty_message);
        emptyMessage.setText(R.string.widget_empty_hint); emptyMessage.setGravity(Gravity.CENTER);
        empty.addView(emptyMessage, new LinearLayout.LayoutParams(
            LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        addView(empty, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        dots = new PageDotsView(context); dots.setId(R.id.widget_page_dots);
        dots.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        LayoutParams dotsParams = new LayoutParams(LayoutParams.MATCH_PARENT, dp(14),
            Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        dotsParams.bottomMargin = dp(4);
        addView(dots, dotsParams);
        dots.setVisibility(GONE);

        notice = new TextView(context); notice.setId(R.id.widget_pane_notice); notice.setGravity(Gravity.CENTER);
        hideNotice = () -> notice.setVisibility(GONE);
        notice.setVisibility(GONE); notice.setPadding(dp(12), dp(6), dp(12), dp(6));
        LayoutParams noticeParams = new LayoutParams(LayoutParams.WRAP_CONTENT, dp(48),
            Gravity.TOP | Gravity.CENTER_HORIZONTAL); addView(notice, noticeParams);

        picker = new WidgetPickerSheetView(context, new WidgetPickerAdapter.Listener() {
            @Override public void onProviderSelected(@NonNull WidgetProviderItem item) {
                if (providerListener != null) providerListener.onProviderSelected(item);
            }
            @Override public void onProviderHeld(@NonNull WidgetProviderItem item,
                                                 @NonNull View card, float rawX, float rawY) {
                if (providerListener != null) providerListener.onProviderHeld(item, card, rawX, rawY);
            }
        });
        picker.setId(R.id.widget_picker_sheet);
        addView(picker, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));
    }

    private WidgetPickerAdapter.Listener providerListener;
    public void setListener(@NonNull Listener value,
                            @NonNull WidgetPickerAdapter.Listener providers) {
        listener = value; providerListener = providers;
    }
    public void setReducedMotion(boolean value) { reducedMotion = value; }
    @NonNull public WidgetGridView grid() { return grid; }

    /**
     * A pane corner may still claim the finger that is down, so nothing in the grid runs a long
     * press of its own until it cannot. The page's frame decides that on the landing point and
     * says so here; two long presses on one finger is the bug this closes, where a hold in a
     * corner opened the corner tab and the grid's menu on top of it.
     */
    public void setHoldExempt(boolean exempt) { grid.setHoldExempt(exempt); }
    @NonNull public WidgetPickerSheetView picker() { return picker; }
    public boolean onBackPressed() {
        if (!picker.isOpen()) return false;
        picker.close();
        return true;
    }

    /** Lazily created edit chrome; always the last child so it draws over every pane surface. */
    @NonNull public WidgetEditOverlayView widgetEditOverlay() {
        if (editOverlay == null) {
            editOverlay = new WidgetEditOverlayView(getContext());
            editOverlay.setId(R.id.widget_edit_overlay);
            addView(editOverlay, new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT));
        } else if (indexOfChild(editOverlay) != getChildCount() - 1) {
            editOverlay.bringToFront();
        }
        return editOverlay;
    }

    public void hideWidgetEditOverlay() {
        if (editOverlay != null) editOverlay.hide();
    }

    /**
     * Where a widget crossing between pages is drawn. Lazily created like the edit chrome, and
     * kept under it so the snap ghost on the page below the finger is never hidden by the widget
     * the finger is carrying.
     */
    @NonNull public WidgetDragLayerView widgetDragLayer() {
        if (dragLayer == null) {
            dragLayer = new WidgetDragLayerView(getContext());
            dragLayer.setId(R.id.widget_drag_layer);
            addView(dragLayer, new LayoutParams(LayoutParams.MATCH_PARENT,
                LayoutParams.MATCH_PARENT));
        }
        if (editOverlay != null && indexOfChild(editOverlay) != getChildCount() - 1) {
            editOverlay.bringToFront();
        }
        return dragLayer;
    }

    /** Lets go of anything the drag layer is carrying; nothing to do when it was never made. */
    public void releaseWidgetDragLayer() {
        if (dragLayer != null) dragLayer.drop(null, false);
    }

    @Nullable private WidgetDragLayerView dragLayer;
    @Nullable private CarryListener carry;

    /**
     * Takes the finger that is currently on the picker over. Everything under this pane is sent
     * the cancel an intercepting parent would have sent it, so the row the press began on lets go
     * cleanly and performs no click, and every later event of that gesture goes to {@code listener}
     * instead of to a child. Ancestors are asked to keep their hands off it for the same reason.
     */
    public void beginCarry(@NonNull CarryListener listener) {
        if (carry != null) return;
        carry = listener;
        ViewParent parent = getParent();
        if (parent != null) parent.requestDisallowInterceptTouchEvent(true);
        long now = SystemClock.uptimeMillis();
        MotionEvent cancel = MotionEvent.obtain(now, now, MotionEvent.ACTION_CANCEL, 0f, 0f, 0);
        super.dispatchTouchEvent(cancel);
        cancel.recycle();
    }

    /**
     * Gives the stream back without a drop. The finger may still be down; it simply owns nothing
     * from here, which is what a picker closing under it or a pane going away amounts to.
     */
    public void endCarry() { carry = null; }

    /** Whether a widget is being carried out of the picker right now. */
    public boolean carrying() { return carry != null; }

    @Override public boolean dispatchTouchEvent(@NonNull MotionEvent event) {
        CarryListener carried = carry;
        if (carried != null) {
            switch (event.getActionMasked()) {
                case MotionEvent.ACTION_MOVE:
                    carried.onCarryMove(event.getRawX(), event.getRawY());
                    return true;
                case MotionEvent.ACTION_UP:
                    carry = null;
                    carried.onCarryEnd(event.getRawX(), event.getRawY(), false);
                    return true;
                case MotionEvent.ACTION_CANCEL:
                    carry = null;
                    carried.onCarryEnd(event.getRawX(), event.getRawY(), true);
                    return true;
                case MotionEvent.ACTION_DOWN:
                    // A press with no lift before it: the carrying finger was lost somewhere this
                    // view never heard about. End the carry and let the new press through.
                    carry = null;
                    carried.onCarryEnd(event.getRawX(), event.getRawY(), true);
                    break;
                default:
                    // Second fingers belong to nobody while one is carrying a widget.
                    return true;
            }
        }
        return super.dispatchTouchEvent(event);
    }

    /**
     * Slides the page that has just been rendered in from one side, exactly as a committed swipe
     * does: a widget dragged into the edge band turns the page, and the turn has to read as the
     * same movement the finger already knows.
     */
    public void slideInFrom(int direction) {
        enterFromSide(direction, 0f);
    }

    /** The commit and discard buttons on the page's own border tab, relayed to the coordinator. */
    public void commitWidgetEdit() {
        if (listener != null) listener.onWidgetEditCommit();
    }

    public void discardWidgetEdit() {
        if (listener != null) listener.onWidgetEditDiscard();
    }

    /** The + on the page's border tab, relayed the same way. */
    public void addWidgetPage() {
        if (listener != null) listener.onWidgetAddPage();
    }

    public boolean widgetEditActive() { return editOverlay != null && editOverlay.isShowing(); }

    /**
     * Whether a point, in this view's coordinates, is on the widget edit chrome. The page's frame
     * asks before it takes a tap for its own border band: a top-row widget's remove chip sits
     * inside that band, and without this the page swallowed the press and the widget could not be
     * removed.
     */
    public boolean widgetEditWantsPoint(float x, float y) {
        return editOverlay != null && editOverlay.isShowing() && editOverlay.wantsPoint(x, y);
    }

    private WidgetEditOverlayView editOverlay;

    public void render(@NonNull LauncherWidgetRepository repository,
                       @NonNull LauncherWidgetHostController.Capability capability) {
        render(repository, capability, 0);
    }

    public void render(@NonNull LauncherWidgetRepository repository,
                       @NonNull LauncherWidgetHostController.Capability capability, int page) {
        hideWidgetEditOverlay();
        List<LauncherWidgetRecord> pageRecords = repository.recordsOnPage(page);
        boolean populated = !pageRecords.isEmpty();
        grid.refresh(repository.gridDefinition(), pageRecords);
        empty.setVisibility(populated ? GONE : VISIBLE);
        // The grid stays visible even empty: cell-free surface owns the long-press menu, and the
        // picker span/fit is derived from its real measured pixels.
        grid.setVisibility(VISIBLE);
        boolean supported = capability == LauncherWidgetHostController.Capability.AVAILABLE;
        emptyMessage.setText(supported ? R.string.widget_empty_hint : R.string.widget_unsupported);
        setPageState(repository.pageCount(), page);
    }

    /** Updates the indicator and, after a committed swipe, slides the new page's content in. */
    public void setPageState(int count, int current) {
        int previousPage = currentPage;
        pageCount = Math.max(1, count);
        currentPage = Math.max(0, Math.min(pageCount - 1, current));
        dots.setVisibility(pageCount > 1 ? VISIBLE : GONE);
        dots.invalidate();
        if (pageCommitPending && currentPage != previousPage) {
            pageCommitPending = false;
            enterFromSide(pageCommitDirection, pageCommitDragX);
        } else if (!pagingDragging) {
            setContentTranslationX(0f);
        }
    }

    public int currentPage() { return currentPage; }

    public void showNotice(@NonNull String message) {
        notice.setText(message); notice.setContentDescription(message); notice.setVisibility(VISIBLE);
        notice.removeCallbacks(hideNotice); notice.postDelayed(hideNotice, 3500);
    }

    // ---- Horizontal page swipe -------------------------------------------------------------
    // The wall's own sideways drag is arbitrated above this page, from the status bar. This
    // intercept claims a stream only when horizontal travel wins the slop race inside the grid,
    // so the grid's own pages move without stealing a vertical scroll or a widget tap.

    @Override public boolean onInterceptTouchEvent(@NonNull MotionEvent event) {
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                pagingDragging = false;
                pagingTracking = pageCount > 1 && !picker.isOpen() && !widgetEditActive()
                    && !insideNestedScrollingChild(this, event);
                pagingDownX = event.getX(); pagingDownY = event.getY();
                if (pagingTracking) {
                    if (pagingVelocity == null) pagingVelocity = VelocityTracker.obtain();
                    pagingVelocity.clear();
                    pagingVelocity.addMovement(event);
                }
                break;
            case MotionEvent.ACTION_MOVE:
                if (!pagingTracking || pagingDragging) break;
                if (widgetEditActive()) {
                    // A hold turned this press into a widget drag after the page had started
                    // watching it; the drag owns the rest of the gesture, sideways included.
                    pagingTracking = false;
                    break;
                }
                if (pagingVelocity != null) pagingVelocity.addMovement(event);
                float dx = event.getX() - pagingDownX;
                float dy = event.getY() - pagingDownY;
                if (Math.abs(dy) > touchSlop && Math.abs(dy) >= Math.abs(dx)) {
                    pagingTracking = false; // vertical belongs to the pane pull gesture
                } else if (Math.abs(dx) > touchSlop && Math.abs(dx) > Math.abs(dy)) {
                    pagingDragging = true;
                    return true;
                }
                break;
            case MotionEvent.ACTION_POINTER_DOWN:
                pagingTracking = false;
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                pagingTracking = false;
                break;
            default:
                break;
        }
        return pagingDragging;
    }

    @Override public boolean onTouchEvent(@NonNull MotionEvent event) {
        if (!pagingDragging) return false;
        if (pagingVelocity != null) pagingVelocity.addMovement(event);
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
                setContentTranslationX(resistedDrag(event.getX() - pagingDownX));
                return true;
            case MotionEvent.ACTION_UP:
                finishPageDrag(event.getX() - pagingDownX);
                return true;
            case MotionEvent.ACTION_CANCEL:
                pagingDragging = false;
                pagingTracking = false;
                settleContentBack();
                return true;
            default:
                return true;
        }
    }

    private float resistedDrag(float dx) {
        boolean pastStart = dx > 0 && currentPage == 0;
        boolean pastEnd = dx < 0 && currentPage == pageCount - 1;
        return pastStart || pastEnd ? dx * PAGE_EDGE_RESISTANCE : dx;
    }

    private void finishPageDrag(float dx) {
        pagingDragging = false;
        pagingTracking = false;
        float velocityX = 0f;
        if (pagingVelocity != null) {
            pagingVelocity.computeCurrentVelocity(1000);
            velocityX = pagingVelocity.getXVelocity();
        }
        float commitVelocity = PAGE_COMMIT_VELOCITY_DP_PER_SEC
            * getResources().getDisplayMetrics().density;
        int width = Math.max(1, getWidth());
        int direction = 0;
        if (dx < 0 && (-dx > width * PAGE_COMMIT_FRACTION || velocityX < -commitVelocity)) {
            direction = 1;
        } else if (dx > 0 && (dx > width * PAGE_COMMIT_FRACTION || velocityX > commitVelocity)) {
            direction = -1;
        }
        int target = Math.max(0, Math.min(pageCount - 1, currentPage + direction));
        if (target != currentPage && listener != null) {
            pageCommitPending = true;
            pageCommitDirection = direction;
            pageCommitDragX = resistedDrag(dx);
            listener.onPageChangeRequested(target);
            if (pageCommitPending) { // the coordinator did not re-render; recover in place
                pageCommitPending = false;
                settleContentBack();
            }
        } else {
            settleContentBack();
        }
    }

    private void enterFromSide(int direction, float dragX) {
        if (reducedMotion) { setContentTranslationX(0f); return; }
        float start = direction > 0 ? getWidth() + dragX : dragX - getWidth();
        setContentTranslationX(start);
        animateContentTranslationX();
    }

    private void settleContentBack() {
        if (reducedMotion || grid.getTranslationX() == 0f) { setContentTranslationX(0f); return; }
        animateContentTranslationX();
    }

    private void setContentTranslationX(float value) {
        grid.setTranslationX(value);
        empty.setTranslationX(value);
    }

    private void animateContentTranslationX() {
        grid.animate().translationX(0f).setDuration(160).start();
        empty.animate().translationX(0f).setDuration(160).start();
    }

    private static boolean insideNestedScrollingChild(@NonNull ViewGroup parent,
                                                      @NonNull MotionEvent event) {
        for (int i = parent.getChildCount() - 1; i >= 0; i--) {
            View child = parent.getChildAt(i);
            if (child.getVisibility() != VISIBLE || !insideView(child, event)) continue;
            if (ViewCompat.isNestedScrollingEnabled(child)) return true;
            if (child instanceof ViewGroup
                && insideNestedScrollingChild((ViewGroup) child, event)) return true;
        }
        return false;
    }

    private static boolean insideView(@NonNull View view, @NonNull MotionEvent event) {
        if (view.getWidth() <= 0 || view.getHeight() <= 0) return false;
        int[] location = new int[2];
        view.getLocationOnScreen(location);
        return event.getRawX() >= location[0] && event.getRawX() < location[0] + view.getWidth()
            && event.getRawY() >= location[1] && event.getRawY() < location[1] + view.getHeight();
    }

    @Override protected void onDetachedFromWindow() {
        if (pagingVelocity != null) { pagingVelocity.recycle(); pagingVelocity = null; }
        super.onDetachedFromWindow();
    }

    /** Low-emphasis page indicator: small centered dots, only meaningful past one page. */
    private final class PageDotsView extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        PageDotsView(@NonNull Context context) { super(context); }

        @Override protected void onDraw(@NonNull Canvas canvas) {
            if (pageCount <= 1) return;
            float radius = dp(2.5f);
            float gap = dp(8);
            float step = radius * 2f + gap;
            float total = pageCount * radius * 2f + (pageCount - 1) * gap;
            float x = (getWidth() - total) / 2f + radius;
            float y = getHeight() / 2f;
            for (int page = 0; page < pageCount; page++) {
                // The dots are chrome over the wall, so they follow the chrome's polarity: white
                // light on the dark band, shadow on the light one, where white dots are no dots.
                paint.setColor(page == currentPage
                    ? com.termux.app.chrome.ChromeShade.structural(0xE6FFFFFF,
                        com.termux.app.chrome.ChromeShade.TARGET_RIM)
                    : com.termux.app.chrome.ChromeShade.fill(0x4DFFFFFF));
                canvas.drawCircle(x, y, radius, paint);
                x += step;
            }
        }

        private float dp(float value) {
            return value * getResources().getDisplayMetrics().density;
        }
    }

    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
}
