package com.termux.app.launcher.popup;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;

import androidx.annotation.NonNull;

/**
 * The App menu's window content: the groups in a scroll view, the shadow room around them, and the
 * arrow drawn on the edge facing the icon in the shade of the group it grows out of.
 *
 * <p>The arrow's path is set once per placement; drawing it allocates nothing.
 */
@SuppressLint("ViewConstructor")
public final class AppMenuCardView extends FrameLayout {

    @NonNull private final LinearLayout groups;
    @NonNull private final ScrollView scroller;
    private final Paint arrowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path arrow = new Path();

    public AppMenuCardView(@NonNull Context context, @NonNull LinearLayout groups) {
        super(context);
        this.groups = groups;
        scroller = new ScrollView(context);
        scroller.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroller.setVerticalScrollBarEnabled(false);
        scroller.addView(groups, new ScrollView.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        addView(scroller, new LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        setClipChildren(false);
        setClipToPadding(false);
        setWillNotDraw(false);
    }

    @NonNull
    public LinearLayout groups() {
        return groups;
    }

    /**
     * Lays the card out for {@code placement}: the scroll view inset by the shadow room and the
     * arrow, and the arrow pointing out of the edge that faces the icon.
     *
     * @param arrowShade the shade of the group the arrow grows out of
     */
    public void applyPlacement(@NonNull AppMenuGeometry.Placement placement, int shadowRoom,
                               int arrowWidth, int arrowHeight, int arrowShade) {
        int top = shadowRoom + (placement.arrowOnTop ? arrowHeight : 0);
        int bottom = shadowRoom + (placement.arrowOnTop ? 0 : arrowHeight);
        // The rows' shadows fall in the scroll view's padding; only a scrolling card clips to it,
        // so rows never slide under the arrow.
        scroller.setPadding(shadowRoom, top, shadowRoom, bottom);
        scroller.setClipToPadding(placement.scrolls);
        scroller.setVerticalFadingEdgeEnabled(placement.scrolls);
        scroller.setFadingEdgeLength(placement.scrolls ? shadowRoom * 4 : 0);

        arrowPaint.setColor(arrowShade);
        float half = arrowWidth / 2f;
        float cx = placement.arrowCenterX;
        // One pixel into the group so no seam shows between the arrow and the row it belongs to.
        arrow.reset();
        if (placement.arrowOnTop) {
            float base = top + 1f;
            arrow.moveTo(cx - half, base);
            arrow.lineTo(cx, shadowRoom);
            arrow.lineTo(cx + half, base);
        } else {
            float base = placement.windowHeight - bottom - 1f;
            arrow.moveTo(cx - half, base);
            arrow.lineTo(cx, placement.windowHeight - shadowRoom);
            arrow.lineTo(cx + half, base);
        }
        arrow.close();
        invalidate();
    }

    @Override
    protected void dispatchDraw(@NonNull Canvas canvas) {
        super.dispatchDraw(canvas);
        canvas.drawPath(arrow, arrowPaint);
    }
}
