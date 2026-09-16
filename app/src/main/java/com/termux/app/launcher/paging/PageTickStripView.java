package com.termux.app.launcher.paging;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The page indicator a pinned-apps row carries off the dock: a short strip of ticks that rides with
 * the row, lying down under it on the top edge and standing beside it on a rail.
 *
 * <p>On the dock itself the ticks are drawn by {@code LauncherAzGestureFxView}, which paints them
 * over the dock's glass while a gesture owns the row. Off the dock there is no such layer — which
 * is why a top row paged by swipe and showed nothing at all — so the strip is a band of the row's
 * own host instead, and it is drawn whenever there is more than one page rather than only under a
 * finger: a rail's pages are otherwise invisible.
 *
 * <p>The shape and the sizes are {@link PageTickStrip}'s, so the indicator is the same object on
 * every edge; only the axis changes, and it is taken from the strip's own proportions.
 */
public class PageTickStripView extends View {

    @NonNull private final Paint tickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int pageCount = 1;
    private float pagePosition;
    private boolean verticalForm;

    public PageTickStripView(@NonNull Context context) {
        this(context, null);
    }

    public PageTickStripView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        tickPaint.setStyle(Paint.Style.FILL);
        tickPaint.setColor(0x66FFFFFF);
    }

    /** Which way the ticks run: down the strip for a rail, across it for a row. */
    public void setVerticalForm(boolean vertical) {
        if (verticalForm == vertical) return;
        verticalForm = vertical;
        invalidate();
    }

    public boolean isVerticalForm() {
        return verticalForm;
    }

    /** The colour the ticks are drawn in — the row's own launcher text colour, dimmed. */
    public void setTickColor(int color) {
        if (tickPaint.getColor() == color) return;
        tickPaint.setColor(color);
        invalidate();
    }

    /**
     * How many pages the row has and where it currently stands between them.
     *
     * @param position the fractional page position, so a drag moves the active tick with the finger
     * @return whether anything changed, so the caller can skip an invalidate it does not need
     */
    public boolean setPages(int pageCount, float position) {
        int count = Math.max(1, pageCount);
        if (this.pageCount == count && Math.abs(this.pagePosition - position) < 0.001f) return false;
        this.pageCount = count;
        this.pagePosition = position;
        invalidate();
        return true;
    }

    public int getPageCount() {
        return pageCount;
    }

    public float getPagePosition() {
        return pagePosition;
    }

    @Override
    protected void onDraw(@NonNull Canvas canvas) {
        super.onDraw(canvas);
        if (pageCount <= 1 || getWidth() <= 0 || getHeight() <= 0) return;
        float density = getResources().getDisplayMetrics().density;
        float stripLengthPx = verticalForm ? getHeight() : getWidth();
        float[] lengths = PageTickStrip.lengthsPx(pageCount, pagePosition, density);
        float gap = PageTickStrip.gapPx(lengths, stripLengthPx, density);
        float[] centers = PageTickStrip.centersPx(lengths, gap, stripLengthPx);
        float thickness = PageTickStrip.THICKNESS_DP * density;
        float radius = thickness * 0.5f;
        float across = (verticalForm ? getWidth() : getHeight()) * 0.5f;
        RectF tick = new RectF();
        for (int page = 0; page < centers.length; page++) {
            float half = lengths[page] * 0.5f;
            if (verticalForm) {
                tick.set(across - radius, centers[page] - half, across + radius, centers[page] + half);
            } else {
                tick.set(centers[page] - half, across - radius, centers[page] + half, across + radius);
            }
            canvas.drawRoundRect(tick, radius, radius, tickPaint);
        }
    }
}
