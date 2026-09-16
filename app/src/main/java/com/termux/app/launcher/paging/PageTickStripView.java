package com.termux.app.launcher.paging;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.place.EdgeStackView;

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
public class PageTickStripView extends View implements EdgeStackView.Air {

    @NonNull private final Paint tickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    @NonNull private final RectF tick = new RectF();
    private int pageCount = 1;
    private float pagePosition;
    private int dynamicPageIndex = -1;
    private boolean verticalForm;
    private int accentColor = 0xFFFFFFFF;

    public PageTickStripView(@NonNull Context context) {
        this(context, null);
    }

    public PageTickStripView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        tickPaint.setStyle(Paint.Style.FILL);
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

    /**
     * The colour the active page's tick is drawn in; the rest of them are the same colour muted.
     * It is the launcher's gesture accent put through the two steps the dock's own ticks took to
     * reach it, so moving the row does not change the indicator's colour.
     */
    public void setAccentColor(int gestureAccentColor) {
        int resolved = accentFrom(gestureAccentColor);
        if (accentColor == resolved) return;
        accentColor = resolved;
        invalidate();
    }

    public int getAccentColor() {
        return accentColor;
    }

    /** The colour one tick is drawn in at this fractional page position, alpha included. */
    public int tickColorAt(int page) {
        float proximity = PageTickStrip.proximity(page, pagePosition);
        float alpha = PageTickStrip.alphaFor(proximity);
        int color = accentColor;
        if (page == dynamicPageIndex) {
            color = PageTickStrip.DYNAMIC_TICK_COLOR;
            alpha *= PageTickStrip.dynamicDampFor(proximity);
        }
        int opacity = Math.max(0, Math.min(255, Math.round(255f * alpha)));
        return (color & 0x00FFFFFF) | (opacity << 24);
    }

    /**
     * How many pages the row has and where it currently stands between them.
     *
     * @param position the fractional page position, so a drag moves the active tick with the finger
     * @return whether anything changed, so the caller can skip an invalidate it does not need
     */
    public boolean setPages(int pageCount, float position) {
        return setPages(pageCount, position, -1);
    }

    /**
     * As {@link #setPages(int, float)}, plus which page (if any) is the dynamic "most-used" one,
     * whose tick carries its own warm tint instead of the accent.
     */
    public boolean setPages(int pageCount, float position, int dynamicPage) {
        int count = Math.max(1, pageCount);
        float bounded = Math.max(0f, Math.min(position, count - 1f));
        int dynamic = dynamicPage >= 0 && dynamicPage < count ? dynamicPage : -1;
        if (this.pageCount == count && this.dynamicPageIndex == dynamic
            && Math.abs(this.pagePosition - bounded) < 0.001f) return false;
        this.pageCount = count;
        this.pagePosition = bounded;
        this.dynamicPageIndex = dynamic;
        invalidate();
        return true;
    }

    public int getDynamicPageIndex() {
        return dynamicPageIndex;
    }

    public int getPageCount() {
        return pageCount;
    }

    public float getPagePosition() {
        return pagePosition;
    }

    /**
     * The ticks are the only thing the strip draws, so this is their thickness across the band —
     * and nothing at all while there is one page and no strip to draw. The hairline that splits
     * the gap the strip stands in keeps clear of it.
     */
    @Override
    public int airMarkThicknessPx() {
        if (pageCount <= 1) return 0;
        return Math.round(PageTickStrip.THICKNESS_DP
            * getResources().getDisplayMetrics().density);
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
        for (int page = 0; page < centers.length; page++) {
            tickPaint.setColor(tickColorAt(page));
            float half = lengths[page] * 0.5f;
            if (verticalForm) {
                tick.set(across - radius, centers[page] - half, across + radius, centers[page] + half);
            } else {
                tick.set(centers[page] - half, across - radius, centers[page] + half, across + radius);
            }
            canvas.drawRoundRect(tick, radius, radius, tickPaint);
        }
    }

    /**
     * The accent the ticks are drawn from: the launcher's gesture accent lifted to stay legible on
     * glass and then warmed the way the dock's FX layer warmed it, so nothing about the colour
     * changed when the ticks stopped being the dock's.
     */
    private static int accentFrom(int gestureAccentColor) {
        return boost(glassVisible(gestureAccentColor, 0.78f), 1.0f, 1.18f);
    }

    private static int glassVisible(int color, float minValue) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[1] = Math.max(0.42f, hsv[1]);
        hsv[2] = Math.max(minValue, hsv[2]);
        return Color.HSVToColor((color >>> 24) == 0 ? 0xE8 : (color >>> 24), hsv);
    }

    private static int boost(int color, float satMul, float valMul) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[1] = Math.max(0f, Math.min(1f, hsv[1] * satMul));
        hsv[2] = Math.max(0f, Math.min(1f, hsv[2] * valMul));
        return Color.HSVToColor((color >>> 24) == 0 ? 0xFF : (color >>> 24), hsv);
    }
}
