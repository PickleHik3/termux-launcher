package com.termux.app.statusbar;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.widget.LinearLayout;

/**
 * The part of the status row that belongs to the place on screen — the badge and the chips. It
 * slides with the pane wall and dissolves at the bar's edges, so a place's row leaves through the
 * lens and the next one arrives through it, while the clock and the stat widgets hold still.
 */
public final class PlaceContentStrip extends LinearLayout {

    /**
     * How far the row's content starts from the bar's edge: just past the visible half of the
     * icon peeking there. Portrait width is scarce, so the two sit close.
     */
    public static final float LENS_WIDTH_DP = 10f;

    /** What a column keeps clear under its chips, before the readings below them. */
    public static final float COLUMN_FOOT_DP = 8f;

    /** Masks the band at the slot's right edge: a 0..fade ramp from opaque to clear, drawn DST_IN. */
    private final Paint fadePaint = new Paint();
    private final float fadeWidthPx;

    public PlaceContentStrip(Context context, AttributeSet attrs) {
        super(context, attrs);
        fadeWidthPx = SlotFade.FADE_WIDTH_DP * getResources().getDisplayMetrics().density;
        fadePaint.setShader(new LinearGradient(0f, 0f, fadeWidthPx, 0f, 0xFF000000, 0x00000000,
                Shader.TileMode.CLAMP));
        fadePaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
        setOrientation(HORIZONTAL);
        setClipChildren(true);
        setClipToPadding(false);
        setFadingEdgeLength(Math.round(LENS_WIDTH_DP * getResources().getDisplayMetrics().density));
        setHorizontalFadingEdgeEnabled(true);
    }

    /**
     * Whether the strip stacks its content down a column rather than along a row. The dissolve
     * follows: the strip always fades at the two ends the places arrive and leave through.
     */
    public void setVertical(boolean vertical) {
        setOrientation(vertical ? VERTICAL : HORIZONTAL);
        setHorizontalFadingEdgeEnabled(!vertical);
        setVerticalFadingEdgeEnabled(vertical);
    }

    /**
     * The end facing the readings feathers only while the place slides, growing over the first
     * fade-length of travel; the screen-right end is feathered by the slot mask instead. At rest that edge belongs to the window chips, which feather there
     * themselves exactly when they run past it and not otherwise; a second, constant fade on top
     * dimmed the last chip of a row that fit.
     */
    @Override protected float getLeftFadingEdgeStrength() {
        return isRtl() ? travelStrength() : 1f;
    }

    @Override protected float getRightFadingEdgeStrength() {
        return isRtl() ? 1f : 0f;
    }

    @Override public void setTranslationX(float translationX) {
        float before = getTranslationX();
        float strengthBefore = travelStrength();
        super.setTranslationX(translationX);
        float after = getTranslationX();
        // The slot-edge mask is drawn with the strip's own content and moves with the slide, which
        // a translation alone does not redraw; at rest nothing is drawn extra, so nothing redraws.
        boolean sliding = after != before && (before != 0f || after != 0f);
        if (sliding || travelStrength() != strengthBefore) invalidate();
    }

    /**
     * While the strip is translated its slot's right edge sits inside it, at the width minus the
     * translation; the chips feather there ({@link SlotFade}) instead of being cut by the clip.
     */
    @Override protected void dispatchDraw(Canvas canvas) {
        float translation = getTranslationX();
        int width = getWidth();
        if (translation == 0f || width <= 0) {
            super.dispatchDraw(canvas);
            return;
        }
        int height = getHeight();
        int checkpoint = canvas.saveLayer(0f, 0f, width, height, null);
        super.dispatchDraw(canvas);
        float bandLeft = width - translation - fadeWidthPx;
        canvas.translate(bandLeft, 0f);
        canvas.drawRect(0f, 0f, width - bandLeft, height, fadePaint);
        canvas.restoreToCount(checkpoint);
    }

    private float travelStrength() {
        int length = getHorizontalFadingEdgeLength();
        if (length <= 0) return 1f;
        return Math.min(1f, Math.abs(getTranslationX()) / length);
    }

    private boolean isRtl() {
        return getLayoutDirection() == LAYOUT_DIRECTION_RTL;
    }

    @Override protected float getTopFadingEdgeStrength() { return 1f; }

    @Override protected float getBottomFadingEdgeStrength() { return 1f; }
}
