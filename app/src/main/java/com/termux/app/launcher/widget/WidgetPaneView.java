package com.termux.app.launcher.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.util.AttributeSet;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.view.ViewCompat;

import com.termux.R;

import java.util.List;

/** Fixed middle-body UI; it has no authority over FULL or terminal geometry. */
public final class WidgetPaneView extends FrameLayout {
    public interface Listener {
        /** The horizontal page swipe committed; the coordinator re-renders onto this page. */
        void onPageChangeRequested(int page);
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

        picker = new WidgetPickerSheetView(context, item -> {
            if (providerListener != null) providerListener.onProviderSelected(item);
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

    public boolean widgetEditActive() { return editOverlay != null && editOverlay.isShowing(); }

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
                paint.setColor(page == currentPage ? 0xE6FFFFFF : 0x4DFFFFFF);
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
