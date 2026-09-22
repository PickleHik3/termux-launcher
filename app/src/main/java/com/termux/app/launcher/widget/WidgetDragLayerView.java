package com.termux.app.launcher.widget;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The one widget a finger is carrying between pages, drawn over the pane as a picture of its cell.
 *
 * <p>A page flip re-lays the grid under the finger, and the widget being dragged is on neither the
 * page it left nor the page it has not landed on yet. Rather than move a live
 * {@code AppWidgetHostView} between pages — which reloads the provider and drops the gesture — the
 * cell is photographed once and the photograph rides the finger, so the user sees the widget where
 * their thumb is for the whole crossing. The picture is let go the moment the widget lands.
 *
 * <p>It also draws the drop ghost for a widget carried out of the picker. A widget already on the
 * page has the edit chrome to show its snap target in; one that is still on its way to the page has
 * no chrome and no frame, so the surface carrying the picture draws the target under it instead.
 *
 * <p>Never a touch target: the stream belongs to whatever started the drag.
 */
public final class WidgetDragLayerView extends View {

    @Nullable private Bitmap picture;
    private final Rect bounds = new Rect();
    private final Rect settleFrom = new Rect();
    private final Rect settleTo = new Rect();
    @Nullable private ValueAnimator settle;
    @Nullable private Rect ghost;
    private boolean ghostValid = true;
    private final Paint ghostStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ghostFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint blockedStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint blockedFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public WidgetDragLayerView(@NonNull Context context) {
        super(context);
        // The same ghost the edit chrome draws, in both its colours, so a widget arriving from the
        // picker and a widget being moved around the page say the same thing about where they land.
        ghostStrokePaint.setStyle(Paint.Style.STROKE);
        ghostStrokePaint.setStrokeWidth(dp(1.5f));
        ghostStrokePaint.setColor(0xB3FFFFFF);
        ghostFillPaint.setStyle(Paint.Style.FILL);
        ghostFillPaint.setColor(0x1AFFFFFF);
        blockedStrokePaint.setStyle(Paint.Style.STROKE);
        blockedStrokePaint.setStrokeWidth(dp(1.5f));
        blockedStrokePaint.setColor(0xCCFF6B6B);
        blockedFillPaint.setStyle(Paint.Style.FILL);
        blockedFillPaint.setColor(0x33FF6B6B);
        setVisibility(GONE);
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    /**
     * Photographs {@code cell} and shows the picture at {@code at}, in this view's coordinates.
     * False when there is nothing to photograph yet — a cell with no size — and the caller then
     * leaves the widget where it is.
     */
    public boolean lift(@NonNull View cell, @NonNull Rect at) {
        if (cell.getWidth() <= 0 || cell.getHeight() <= 0) return false;
        Bitmap next;
        try {
            next = Bitmap.createBitmap(cell.getWidth(), cell.getHeight(), Bitmap.Config.ARGB_8888);
        } catch (RuntimeException | OutOfMemoryError failure) {
            return false;
        }
        cell.draw(new Canvas(next));
        cancelSettle();
        picture = next;
        bounds.set(at);
        setVisibility(VISIBLE);
        invalidate();
        return true;
    }

    /**
     * The cell the carried widget would land on, or null while there is none. {@code valid} false
     * is the page saying it has no room: the target turns red and the drop will be refused.
     */
    public void setGhost(@Nullable Rect bounds, boolean valid) {
        if (bounds == null && ghost == null && ghostValid == valid) return;
        ghost = bounds == null ? null : new Rect(bounds);
        ghostValid = valid;
        invalidate();
    }

    /** The drop target being shown, or null while none is. */
    @Nullable public Rect ghostBounds() { return ghost == null ? null : new Rect(ghost); }

    /** Whether the target on screen is the refusing one. */
    public boolean ghostBlocked() { return ghost != null && !ghostValid; }

    /** Whether a widget is in the air. */
    public boolean isLifted() { return picture != null; }

    /** Where the picture is now, in this view's coordinates. */
    @NonNull public Rect pictureBounds() { return new Rect(bounds); }

    public void moveTo(@NonNull Rect at) {
        if (picture == null || bounds.equals(at)) return;
        bounds.set(at);
        invalidate();
    }

    /**
     * Lets the picture go. With a {@code home} the picture settles onto it first, which is the
     * drag's own spring-back for a widget that could not land where it was taken; without one, or
     * with motion turned down, it simply goes.
     */
    public void drop(@Nullable Rect home, boolean animate) {
        if (picture == null) return;
        if (home == null || !animate || bounds.equals(home)) { release(); return; }
        cancelSettle();
        settleFrom.set(bounds);
        settleTo.set(home);
        ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
        animator.setDuration(160);
        animator.setInterpolator(new DecelerateInterpolator());
        animator.addUpdateListener(animation -> {
            float fraction = (float) animation.getAnimatedValue();
            bounds.set(lerp(settleFrom.left, settleTo.left, fraction),
                lerp(settleFrom.top, settleTo.top, fraction),
                lerp(settleFrom.right, settleTo.right, fraction),
                lerp(settleFrom.bottom, settleTo.bottom, fraction));
            invalidate();
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(@NonNull Animator animation) { release(); }
        });
        settle = animator;
        animator.start();
    }

    private void cancelSettle() {
        ValueAnimator animator = settle;
        settle = null;
        if (animator != null) animator.cancel();
    }

    private void release() {
        settle = null;
        picture = null;
        ghost = null;
        ghostValid = true;
        setVisibility(GONE);
        invalidate();
    }

    private static int lerp(int from, int to, float fraction) {
        return Math.round(from + (to - from) * fraction);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    @Override protected void onDraw(@NonNull Canvas canvas) {
        Rect target = ghost;
        if (target != null) {
            RectF rect = new RectF(target);
            float radius = dp(14f);
            canvas.drawRoundRect(rect, radius, radius,
                ghostValid ? ghostFillPaint : blockedFillPaint);
            canvas.drawRoundRect(rect, radius, radius,
                ghostValid ? ghostStrokePaint : blockedStrokePaint);
        }
        Bitmap drawn = picture;
        if (drawn == null || drawn.isRecycled()) return;
        canvas.drawBitmap(drawn, null, bounds, null);
    }

    @Override protected void onDetachedFromWindow() {
        cancelSettle();
        picture = null;
        ghost = null;
        super.onDetachedFromWindow();
    }
}
