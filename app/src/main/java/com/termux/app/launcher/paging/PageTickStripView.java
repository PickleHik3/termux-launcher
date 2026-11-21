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
import com.termux.app.chrome.PageMarkInk;
import com.termux.app.place.EdgeStackView;

/**
 * The page indicator a pinned-apps row carries off the dock: a short strip of capsules that rides with
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
    private boolean verticalForm;
    private int accentColor = 0xFFFFFFFF;
    /**
     * What the ticks stand on, as the chrome measured it under the dock, or
     * {@link Color#TRANSPARENT} before the wallpaper has been sampled. Pushed down from
     * {@code SuggestionBarView}; the strip is not a band of {@code GlassBackdropCache}'s own.
     */
    private int glassBackdrop = Color.TRANSPARENT;
    /** The ink the chrome settled on for that band; the ticks take their side from it. */
    private int glassInkColor = Color.TRANSPARENT;
    private int activeInk;
    private int restingInk;
    private boolean dimmed;

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
     * The colour the active page's capsule is drawn in; the rest are the band's neutral ink, muted.
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
    public void setGlassInk(int surfaceColor, int inkColor) {
        if (glassBackdrop == surfaceColor && glassInkColor == inkColor) return;
        glassBackdrop = surfaceColor;
        glassInkColor = inkColor;
        resolveInks();
        invalidate();
    }

    /** See {@link #setGlassInk}. */
    public int glassBackdrop() {
        return glassBackdrop;
    }

    /**
     * The two inks the capsules morph between: the accent for the page being shown, and the band's
     * neutral ink (the chrome's own, not a faded accent) for the rest, both solved on the ground
     * the capsules stand on, on that ground's side ({@link PageMarkInk}, the page bar's rule).
     *
     * <p>The active capsule says which page you are on: it is held to {@link PageMarkInk#CURRENT}.
     * A capsule at rest says only that there is another page there: it keeps its muted look as an
     * alpha of the neutral ink, a tier under the active one and never under the contract's
     * graphics tier ({@link PageMarkInk#OTHER_FLOOR}) while the active one reads.</p>
     */
    private void resolveInks() {
        if (Color.alpha(glassBackdrop) == 0) return;
        int neutralSeed = Color.alpha(glassInkColor) == 0 ? accentColor : glassInkColor;
        boolean pale = GlassInk.isPaleSide(neutralSeed, glassBackdrop);
        activeInk = PageMarkInk.current(glassBackdrop, accentColor, pale);
        restingInk = PageMarkInk.other(glassBackdrop, neutralSeed, activeInk);
    }

    /** The colour one capsule is drawn in at this fractional page position, alpha included. */
    public int tickColorAt(int page) {
        float proximity = PageTickStrip.proximity(page, pagePosition);
        if (Color.alpha(glassBackdrop) == 0) {
            // Nothing measured yet: the chrome's ink (white before it has one) at rest, the
            // accent at the page being shown.
            int neutral = Color.alpha(glassInkColor) == 0 ? 0xFFFFFFFF : glassInkColor;
            int rest = (neutral & 0x00FFFFFF)
                | (Math.round(255f * PageTickStrip.INACTIVE_ALPHA) << 24);
            return blend(rest, accentColor | 0xFF000000, proximity);
        }
        return blend(restingInk, activeInk, proximity);
    }

    /**
     * Dims the capsules while the page bar slides (draw-time only): the current one to
     * {@link PageTickStrip#DIMMED_ACTIVE_FACTOR} of its alpha, the others to
     * {@link PageTickStrip#DIMMED_INACTIVE_FACTOR} of theirs.
     */
    public void setDimmed(boolean dimmed) {
        if (this.dimmed == dimmed) return;
        this.dimmed = dimmed;
        invalidate();
    }

    public boolean isDimmed() {
        return dimmed;
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
        int count = Math.max(1, pageCount);
        float bounded = Math.max(0f, Math.min(position, count - 1f));
        if (this.pageCount == count
            && Math.abs(this.pagePosition - bounded) < 0.001f) return false;
        this.pageCount = count;
        this.pagePosition = bounded;
        invalidate();
        return true;
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

    private int airBelowPx;

    /**
     * The empty air at the band's foot, under the ticks: they are centred in the band less it.
     *
     * @return whether it moved
     */
    public boolean setAirBelowPx(int px) {
        int wanted = Math.max(0, px);
        if (airBelowPx == wanted) return false;
        airBelowPx = wanted;
        invalidate();
        return true;
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
        float radius = PageTickStrip.CORNER_RADIUS_DP * density;
        float across = (verticalForm ? getWidth() : getHeight() - airBelowPx) * 0.5f;
        for (int page = 0; page < centers.length; page++) {
            int color = tickColorAt(page);
            if (dimmed) {
                float factor = PageTickStrip.dimFactor(PageTickStrip.proximity(page, pagePosition));
                color = (color & 0x00FFFFFF)
                    | (Math.round(Color.alpha(color) * factor) << 24);
            }
            tickPaint.setColor(color);
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
