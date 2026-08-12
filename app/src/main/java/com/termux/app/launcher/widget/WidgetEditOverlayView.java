package com.termux.app.launcher.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Launcher-owned widget edit chrome drawn over the whole pane: selection frame, per-axis resize
 * handles, a remove chip, and the snap-target ghost shown while a move drag is live. All input
 * on this overlay is consumed; a press outside the frame dismisses edit mode.
 */
public final class WidgetEditOverlayView extends View {
    public interface Listener {
        void onMoveDragStart(float rawX, float rawY);
        void onMoveDragMove(float rawX, float rawY);
        void onMoveDragEnd(boolean canceled);
        void onResizeDrag(@NonNull WidgetEditPolicy.Handle handle, int desiredEdgePx);
        void onResizeDragEnd();
        void onRemove();
        void onDismiss();
    }

    private enum Mode { NONE, MOVE, RESIZE, CHIP }

    private final Paint framePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint handlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint handleRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ghostStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ghostFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint chipPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint chipCrossPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Rect frame = new Rect();
    @Nullable private Rect ghost;
    private boolean frameVisible;
    private boolean dragging;
    private boolean horizontalResizable;
    private boolean verticalResizable;
    @Nullable private Listener listener;
    private Mode mode = Mode.NONE;
    @Nullable private WidgetEditPolicy.Handle activeHandle;

    public WidgetEditOverlayView(@NonNull Context context) {
        super(context);
        setVisibility(GONE);
        // Advertises interactivity to ancestor hit tests (the status pane's pull-up must never
        // steal a stream this overlay is using for move/resize drags).
        setClickable(true);
        framePaint.setStyle(Paint.Style.STROKE);
        framePaint.setStrokeWidth(dp(2f));
        framePaint.setColor(0xE6FFFFFF);
        handlePaint.setStyle(Paint.Style.FILL);
        handlePaint.setColor(0xFFFFFFFF);
        handleRingPaint.setStyle(Paint.Style.STROKE);
        handleRingPaint.setStrokeWidth(dp(1.5f));
        handleRingPaint.setColor(0x66000000);
        ghostStrokePaint.setStyle(Paint.Style.STROKE);
        ghostStrokePaint.setStrokeWidth(dp(1.5f));
        ghostStrokePaint.setColor(0xB3FFFFFF);
        ghostFillPaint.setStyle(Paint.Style.FILL);
        ghostFillPaint.setColor(0x1AFFFFFF);
        chipPaint.setStyle(Paint.Style.FILL);
        chipPaint.setColor(0xE6202124);
        chipCrossPaint.setStyle(Paint.Style.STROKE);
        chipCrossPaint.setStrokeWidth(dp(1.8f));
        chipCrossPaint.setStrokeCap(Paint.Cap.ROUND);
        chipCrossPaint.setColor(0xFFFFFFFF);
    }

    public void setListener(@Nullable Listener value) { listener = value; }

    public void show(@NonNull Rect frameBounds, boolean horizontal, boolean vertical) {
        frame.set(frameBounds);
        horizontalResizable = horizontal;
        verticalResizable = vertical;
        frameVisible = true;
        dragging = false;
        ghost = null;
        setVisibility(VISIBLE);
        invalidate();
    }

    public void hide() {
        frameVisible = false;
        dragging = false;
        ghost = null;
        mode = Mode.NONE;
        setVisibility(GONE);
    }

    public boolean isShowing() { return frameVisible; }

    /** While a move drag is live the frame chrome hides and only the snap ghost renders. */
    public void setDragging(boolean value) {
        dragging = value;
        if (!value) ghost = null;
        invalidate();
    }

    public void setFrameBounds(@NonNull Rect bounds) { frame.set(bounds); invalidate(); }

    @NonNull public Rect frameBounds() { return new Rect(frame); }

    public void setGhostBounds(@Nullable Rect bounds) {
        ghost = bounds == null ? null : new Rect(bounds);
        invalidate();
    }

    @Override protected void onDraw(@NonNull Canvas canvas) {
        if (!frameVisible) return;
        if (ghost != null) {
            RectF ghostRect = new RectF(ghost);
            float radius = dp(14f);
            canvas.drawRoundRect(ghostRect, radius, radius, ghostFillPaint);
            canvas.drawRoundRect(ghostRect, radius, radius, ghostStrokePaint);
        }
        if (dragging) return;
        RectF frameRect = new RectF(frame);
        float radius = dp(14f);
        canvas.drawRoundRect(frameRect, radius, radius, framePaint);
        float handleRadius = dp(5f);
        if (horizontalResizable) {
            drawHandle(canvas, frame.left, frame.centerY(), handleRadius);
            drawHandle(canvas, frame.right, frame.centerY(), handleRadius);
        }
        if (verticalResizable) {
            drawHandle(canvas, frame.centerX(), frame.top, handleRadius);
            drawHandle(canvas, frame.centerX(), frame.bottom, handleRadius);
        }
        float chipRadius = chipRadius();
        float chipX = chipCenterX(), chipY = chipCenterY();
        canvas.drawCircle(chipX, chipY, chipRadius, chipPaint);
        canvas.drawCircle(chipX, chipY, chipRadius, handleRingPaint);
        float arm = chipRadius * 0.42f;
        canvas.drawLine(chipX - arm, chipY - arm, chipX + arm, chipY + arm, chipCrossPaint);
        canvas.drawLine(chipX - arm, chipY + arm, chipX + arm, chipY - arm, chipCrossPaint);
    }

    private void drawHandle(Canvas canvas, float x, float y, float radius) {
        canvas.drawCircle(x, y, radius, handlePaint);
        canvas.drawCircle(x, y, radius, handleRingPaint);
    }

    private float chipRadius() { return dp(13f); }
    /** Corner-anchored but clamped inside the overlay so a full-width frame never clips it. */
    private float chipCenterX() {
        return Math.min(frame.right - dp(2f), getWidth() - chipRadius() - dp(2f));
    }
    private float chipCenterY() {
        return Math.max(frame.top + dp(2f), chipRadius() + dp(2f));
    }

    @Override public boolean onTouchEvent(@NonNull MotionEvent event) {
        if (!frameVisible) return false;
        float x = event.getX(), y = event.getY();
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (hitChip(x, y)) {
                    mode = Mode.CHIP;
                } else if ((activeHandle = hitHandle(x, y)) != null) {
                    mode = Mode.RESIZE;
                } else if (frame.contains(Math.round(x), Math.round(y))) {
                    mode = Mode.MOVE;
                    if (listener != null) listener.onMoveDragStart(event.getRawX(), event.getRawY());
                } else {
                    mode = Mode.NONE;
                    if (listener != null) listener.onDismiss();
                }
                return true;
            case MotionEvent.ACTION_MOVE:
                if (mode == Mode.MOVE && listener != null) {
                    listener.onMoveDragMove(event.getRawX(), event.getRawY());
                } else if (mode == Mode.RESIZE && listener != null && activeHandle != null) {
                    boolean horizontal = activeHandle == WidgetEditPolicy.Handle.LEFT
                        || activeHandle == WidgetEditPolicy.Handle.RIGHT;
                    listener.onResizeDrag(activeHandle, Math.round(horizontal ? x : y));
                }
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                boolean canceled = event.getActionMasked() == MotionEvent.ACTION_CANCEL;
                Mode ended = mode;
                mode = Mode.NONE;
                activeHandle = null;
                if (listener == null) return true;
                if (ended == Mode.MOVE) listener.onMoveDragEnd(canceled);
                else if (ended == Mode.RESIZE) listener.onResizeDragEnd();
                else if (ended == Mode.CHIP && !canceled && hitChip(x, y)) listener.onRemove();
                return true;
            default:
                return true;
        }
    }

    private boolean hitChip(float x, float y) {
        float slop = dp(8f);
        return Math.hypot(x - chipCenterX(), y - chipCenterY()) <= chipRadius() + slop;
    }

    @Nullable private WidgetEditPolicy.Handle hitHandle(float x, float y) {
        float slop = dp(18f);
        if (horizontalResizable) {
            if (Math.hypot(x - frame.left, y - frame.centerY()) <= slop) {
                return WidgetEditPolicy.Handle.LEFT;
            }
            if (Math.hypot(x - frame.right, y - frame.centerY()) <= slop) {
                return WidgetEditPolicy.Handle.RIGHT;
            }
        }
        if (verticalResizable) {
            if (Math.hypot(x - frame.centerX(), y - frame.top) <= slop) {
                return WidgetEditPolicy.Handle.TOP;
            }
            if (Math.hypot(x - frame.centerX(), y - frame.bottom) <= slop) {
                return WidgetEditPolicy.Handle.BOTTOM;
            }
        }
        return null;
    }

    private float dp(float value) { return value * getResources().getDisplayMetrics().density; }
}
