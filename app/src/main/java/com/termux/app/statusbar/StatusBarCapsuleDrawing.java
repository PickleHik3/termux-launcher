package com.termux.app.statusbar;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.util.TypedValue;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.ColorUtils;

import com.google.android.material.color.MaterialColors;
import com.termux.R;
import com.termux.shared.termux.font.NerdFontSpans;

import java.util.Date;

/**
 * What the status bar draws as its capsule ({@link StatusBarFormPolicy.Form#CAPSULE}): the glyph,
 * the clock and the dots, centred on the bar's edge. Not a view: the bar draws it as a state of
 * itself, inside the glass it has drawn in to the capsule's shape, so the capsule is the bar's own
 * glass and never a window of its own. The rules (sizes, which glyph, how many dots) are
 * {@link StatusBarFormPolicy}'s; this only measures and paints.
 */
final class StatusBarCapsuleDrawing {

    private static final float GLYPH_SP = 14f;
    private static final float CLOCK_SP = 13f;

    private final Context mContext;
    private final float mDensity;
    private final Paint mGlyphPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mClockPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mDotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    @NonNull private StatusBarFormPolicy.Content mContent =
        StatusBarFormPolicy.content(false, null, 0, 0);
    @NonNull private String mClock = "";

    StatusBarCapsuleDrawing(@NonNull Context context) {
        mContext = context;
        mDensity = context.getResources().getDisplayMetrics().density;
        mGlyphPaint.setTypeface(NerdFontSpans.typeface(context));
        mGlyphPaint.setTextSize(sp(GLYPH_SP));
        mClockPaint.setTypeface(Typeface.create(Typeface.DEFAULT, Typeface.BOLD));
        mClockPaint.setTextSize(sp(CLOCK_SP));
        mClockPaint.setFontFeatureSettings("tnum");
        refreshColors();
        refreshClock();
    }

    /** The theme's ink: the glyph and the dots in the accent, the clock on the surface. */
    void refreshColors() {
        int accent = MaterialColors.getColor(mContext, com.termux.shared.R.attr.termuxColorPrimary,
            ContextCompat.getColor(mContext, R.color.termux_primary));
        int onSurface = MaterialColors.getColor(mContext,
            com.termux.shared.R.attr.termuxColorOnSurface, 0xFFFFFFFF);
        mGlyphPaint.setColor(accent);
        mDotPaint.setColor(accent);
        mClockPaint.setColor(onSurface);
    }

    /** Re-reads the time; answers whether the text changed. */
    boolean refreshClock() {
        String clock = android.text.format.DateFormat.getTimeFormat(mContext)
            .format(new Date(System.currentTimeMillis()));
        if (clock.equals(mClock)) return false;
        mClock = clock;
        return true;
    }

    @NonNull String clock() { return mClock; }

    /** Answers whether the content changed. */
    boolean setContent(@NonNull StatusBarFormPolicy.Content content) {
        if (content.equals(mContent)) return false;
        mContent = content;
        return true;
    }

    @NonNull StatusBarFormPolicy.Content content() { return mContent; }

    /**
     * Where the capsule stands at rest in a bar {@code width} by {@code height}: centred along it
     * and across it, as wide as its content and 36 dp tall at most.
     */
    void restingBounds(int width, int height, @NonNull RectF out) {
        int capsuleHeight = StatusBarFormPolicy.capsuleHeightPx(mDensity, height);
        int capsuleWidth = StatusBarFormPolicy.capsuleWidthPx(mDensity,
            mGlyphPaint.measureText(mContent.glyph), mClockPaint.measureText(mClock),
            mContent.dots, width);
        float left = (width - capsuleWidth) / 2f;
        float top = (height - capsuleHeight) / 2f;
        out.set(left, top, left + capsuleWidth, top + capsuleHeight);
    }

    float radiusPx(@NonNull RectF bounds) {
        return StatusBarFormPolicy.capsuleRadiusPx(mDensity, Math.round(bounds.height()));
    }

    /** Paints the content laid out in {@code bounds} at {@code alpha}. */
    void draw(@NonNull Canvas canvas, @NonNull RectF bounds, float alpha) {
        if (alpha <= 0f) return;
        int a = Math.round(255 * Math.min(1f, alpha));
        float padding = StatusBarFormPolicy.CAPSULE_PADDING_DP * mDensity;
        float gap = StatusBarFormPolicy.CAPSULE_GAP_DP * mDensity;
        float centerY = bounds.centerY();
        float x = bounds.left + padding;

        int glyphColor = mGlyphPaint.getColor();
        mGlyphPaint.setColor(ColorUtils.setAlphaComponent(glyphColor,
            Math.round(Color.alpha(glyphColor) * a / 255f)));
        canvas.drawText(mContent.glyph, x, baseline(mGlyphPaint, centerY), mGlyphPaint);
        mGlyphPaint.setColor(glyphColor);
        x += mGlyphPaint.measureText(mContent.glyph) + gap;

        int clockColor = mClockPaint.getColor();
        mClockPaint.setColor(ColorUtils.setAlphaComponent(clockColor,
            Math.round(Color.alpha(clockColor) * a / 255f)));
        canvas.drawText(mClock, x, baseline(mClockPaint, centerY), mClockPaint);
        mClockPaint.setColor(clockColor);
        x += mClockPaint.measureText(mClock);

        if (mContent.dots > 0) {
            x += gap;
            float dot = StatusBarFormPolicy.DOT_DP * mDensity;
            float dotGap = StatusBarFormPolicy.DOT_GAP_DP * mDensity;
            int dotColor = mDotPaint.getColor();
            mDotPaint.setColor(ColorUtils.setAlphaComponent(dotColor,
                Math.round(Color.alpha(dotColor) * a / 255f)));
            for (int i = 0; i < mContent.dots; i++) {
                canvas.drawCircle(x + dot / 2f, centerY, dot / 2f, mDotPaint);
                x += dot + dotGap;
            }
            mDotPaint.setColor(dotColor);
        }
    }

    private static float baseline(@NonNull Paint paint, float centerY) {
        return centerY - (paint.ascent() + paint.descent()) / 2f;
    }

    private float sp(float value) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value,
            mContext.getResources().getDisplayMetrics());
    }
}
