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

import com.termux.app.chrome.GlassInk;
import com.termux.app.chrome.OnGlass;
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
    /**
     * What the ticks stand on, as the chrome measured it under the dock, or
     * {@link Color#TRANSPARENT} before the wallpaper has been sampled. Pushed down from
     * {@code SuggestionBarView}; the strip is not a band of {@code GlassBackdropCache}'s own.
     */
    private int glassBackdrop = Color.TRANSPARENT;
    private int activeInk;
    private int restingInk;
    private int dynamicActiveInk;
    private int dynamicRestingInk;

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
     *
     * <p>It is the launcher's gesture accent, and nothing is done to it here any more. It used to
     * be lifted first — saturation to at least 0.42, value to at least 0.78, then boosted again —
     * so that it would survive a dark glass. On a light one that lift is what made it disappear
     * into the band. What the accent has to become for where it is actually drawn is
     * {@link GlassInk}'s answer now, and it needs {@link #setGlassBackdrop} to give it.</p>
     */
    public void setAccentColor(int gestureAccentColor) {
        if (accentColor == gestureAccentColor) return;
        accentColor = gestureAccentColor;
        resolveInks();
        invalidate();
    }

    public int getAccentColor() {
        return accentColor;
    }

    /**
     * What the ticks stand on, as the chrome measured it. {@link Color#TRANSPARENT} — the value
     * before anything has been sampled — keeps the ticks exactly as they were drawn before this
     * round: the accent, faded by proximity.
     */
    public void setGlassBackdrop(int surfaceColor) {
        if (glassBackdrop == surfaceColor) return;
        glassBackdrop = surfaceColor;
        resolveInks();
        invalidate();
    }

    /** See {@link #setGlassBackdrop}. */
    public int glassBackdrop() {
        return glassBackdrop;
    }

    /**
     * The four inks the ticks morph between: the accent and the most-used page's warm tint, each at
     * the page being shown and at rest.
     *
     * <p>The active tick says which page you are on, so it is a meaningful graphic and takes
     * {@link OnGlass#TARGET_LARGE_TEXT}. A tick at rest says only that there is another page there,
     * so it takes {@link OnGlass#TARGET_DECORATION} — and it keeps its muted look by asking for it
     * as an <em>alpha</em>, {@link PageTickStrip#INACTIVE_ALPHA}, which {@link GlassInk#legible}
     * raises only if 40% of a tick cannot clear 2.0 on this band. The mute is a preference; the
     * floor is not.</p>
     */
    private void resolveInks() {
        if (Color.alpha(glassBackdrop) == 0) return;
        int restingAlpha = Math.round(255f * PageTickStrip.INACTIVE_ALPHA);
        activeInk = GlassInk.legible(glassBackdrop, accentColor, OnGlass.TARGET_LARGE_TEXT, 0xE8);
        restingInk = GlassInk.legible(glassBackdrop, accentColor, OnGlass.TARGET_DECORATION,
            restingAlpha);
        dynamicActiveInk = GlassInk.legible(glassBackdrop, PageTickStrip.DYNAMIC_TICK_COLOR,
            OnGlass.TARGET_LARGE_TEXT, 0xE8);
        dynamicRestingInk = GlassInk.legible(glassBackdrop, PageTickStrip.DYNAMIC_TICK_COLOR,
            OnGlass.TARGET_DECORATION, restingAlpha);
    }

    /** The colour one tick is drawn in at this fractional page position, alpha included. */
    public int tickColorAt(int page) {
        float proximity = PageTickStrip.proximity(page, pagePosition);
        boolean dynamic = page == dynamicPageIndex;
        if (Color.alpha(glassBackdrop) == 0) {
            // Nothing measured yet: the accent, faded by proximity, exactly as before this round.
            float alpha = PageTickStrip.alphaFor(proximity);
            int color = dynamic ? PageTickStrip.DYNAMIC_TICK_COLOR : accentColor;
            if (dynamic) alpha *= PageTickStrip.dynamicDampFor(proximity);
            int opacity = Math.max(0, Math.min(255, Math.round(255f * alpha)));
            return (color & 0x00FFFFFF) | (opacity << 24);
        }
        // The morph is between two resolved inks rather than between two alphas: a fade towards
        // nothing is a fade towards the band, and the band is what the ticks were losing to.
        float mix = dynamic ? proximity * PageTickStrip.dynamicDampFor(proximity) : proximity;
        return blend(dynamic ? dynamicRestingInk : restingInk,
            dynamic ? dynamicActiveInk : activeInk, mix);
    }

    private static int blend(int from, int to, float amount) {
        float t = Math.max(0f, Math.min(1f, amount));
        return Color.argb(
            Math.round(Color.alpha(from) + (Color.alpha(to) - Color.alpha(from)) * t),
            Math.round(Color.red(from) + (Color.red(to) - Color.red(from)) * t),
            Math.round(Color.green(from) + (Color.green(to) - Color.green(from)) * t),
            Math.round(Color.blue(from) + (Color.blue(to) - Color.blue(from)) * t));
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

}
