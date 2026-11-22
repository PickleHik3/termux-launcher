package com.termux.app.statusbar;

import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.PathParser;

/**
 * The battery the status bar's battery chip shows before its percentage, in the Roundline family:
 * the {@code ic_rl_battery} outline (one {@link #STROKE}-unit stroke, round caps and joins, in a
 * 24-unit viewport centred in the bounds), the level as a bar inside it {@link #CLEAR} units clear
 * of the stroke and as long as the charge, and for the states that have one the mark of
 * {@code ic_rl_battery_low}, {@code _charging}, {@code _full} or {@code _hot}. Hot is that
 * drawable whole, its shorter body beside a thermometer and no bar: a bar in that body reads as a
 * zero at the status bar's size, and the percentage beside it says the level. Where a mark
 * crosses the bar, the bar stands {@link #MARK_GAP} clear of it. It is drawn from a {@link BatteryChipState} and nothing else; the
 * ink is the widget's own icon colour (it arrives as the image tint, so the role colours, the
 * themes and the scheme chrome all reach it) and {@link #setErrorColor} is the colour for the low
 * and hot states.
 *
 * <p>The paths are parsed once, here; geometry follows the bounds once per bounds change, so
 * {@link #draw} allocates nothing. It invalidates only when the state or the bar's pixel width
 * changes, so a level ticking inside one pixel costs nothing.</p>
 */
final class BatteryIconDrawable extends Drawable {

    static final float VIEWPORT = 24f;
    static final float STROKE = 2f;
    /** Ground between the outline's stroke and the level bar, in units. */
    static final float CLEAR = 2f;
    /** Ground between a mark's stroke and the level bar, in units. */
    static final float MARK_GAP = 1f;
    static final float BAR_RADIUS = 1f;

    // The drawables' path data: each one's stroked path is its body, nub and mark joined by a
    // space, its filled path the dot (a test holds them equal to the XML).
    static final String BODY = "M4.5 6 H17.5 Q20 6 20 8.5 V15.5 Q20 18 17.5 18 H4.5 Q2 18 2 15.5"
        + " V8.5 Q2 6 4.5 6 Z";
    static final String NUB = "M22 10 V14";
    static final String LOW_MARK = "M11 9 V12";
    static final String LOW_DOT = "M10 15 A1 1 0 1 0 12 15 A1 1 0 1 0 10 15 Z";
    static final String CHARGING_MARK = "M12 8 L8 12 H14 L10 16";
    static final String FULL_MARK = "M6.5 12 L9.5 14.5 L15.5 9.5";
    static final String HOT_BODY = "M4.5 6 H10.5 Q13 6 13 8.5 V15.5 Q13 18 10.5 18 H4.5 Q2 18 2"
        + " 15.5 V8.5 Q2 6 4.5 6 Z";
    static final String HOT_NUB = "M15 10 V14";
    static final String HOT_MARK = "M20 5 V16";
    static final String HOT_DOT = "M17.5 17 A2.5 2.5 0 1 0 22.5 17 A2.5 2.5 0 1 0 17.5 17 Z";

    /** The body's outline as a rect (left, top, right, bottom), the stroke centred on it. */
    static final float[] BODY_RECT = {2f, 6f, 20f, 18f};

    private static final int KINDS = BatteryChipState.Kind.values().length;

    private final Path mBody;
    private final Path mHotBody;
    /** Per {@link BatteryChipState.Kind}: the stroked mark, the filled dot, the ground the bar keeps off. */
    private final Path[] mMark = new Path[KINDS];
    private final Path[] mDot = new Path[KINDS];
    private final Path[] mHalo = new Path[KINDS];
    private final float[] mBar = new float[4];
    private final Paint mStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mFill = new Paint(Paint.ANTI_ALIAS_FLAG);

    @ColorInt private int mInk = 0xFFFFFFFF;
    @ColorInt private int mErrorInk = 0xFFFF5449;
    private int mAlpha = 255;
    @NonNull private BatteryChipState mState = BatteryChipState.UNKNOWN;
    /** The bar's width in whole pixels as last laid out, so a sub-pixel change redraws nothing. */
    private int mFillPx;
    private float mUnit;
    private float mLeft;
    private float mTop;

    BatteryIconDrawable() {
        mBody = PathParser.createPathFromPathData(BODY + " " + NUB);
        mHotBody = PathParser.createPathFromPathData(HOT_BODY + " " + HOT_NUB);
        mark(BatteryChipState.Kind.LOW, LOW_MARK, LOW_DOT);
        mark(BatteryChipState.Kind.CHARGING, CHARGING_MARK, null);
        mark(BatteryChipState.Kind.FULL, FULL_MARK, null);
        mark(BatteryChipState.Kind.HOT, HOT_MARK, HOT_DOT);
        barInside(BODY_RECT, mBar);
        mStroke.setStyle(Paint.Style.STROKE);
        mStroke.setStrokeWidth(STROKE);
        mStroke.setStrokeCap(Paint.Cap.ROUND);
        mStroke.setStrokeJoin(Paint.Join.ROUND);
        mFill.setStyle(Paint.Style.FILL);
    }

    /** The level bar's full extent inside a body: the stroke's inner edge, then {@link #CLEAR}. */
    static void barInside(@NonNull float[] body, @NonNull float[] out) {
        float inset = STROKE / 2f + CLEAR;
        out[0] = body[0] + inset;
        out[1] = body[1] + inset;
        out[2] = body[2] - inset;
        out[3] = body[3] - inset;
    }

    private void mark(BatteryChipState.Kind kind, String mark, @Nullable String dot) {
        int k = kind.ordinal();
        mMark[k] = PathParser.createPathFromPathData(mark);
        Paint grow = new Paint(Paint.ANTI_ALIAS_FLAG);
        grow.setStyle(Paint.Style.STROKE);
        grow.setStrokeCap(Paint.Cap.ROUND);
        grow.setStrokeJoin(Paint.Join.ROUND);
        grow.setStrokeWidth(STROKE + 2f * MARK_GAP);
        Path halo = new Path();
        grow.getFillPath(mMark[k], halo);
        if (dot != null) {
            mDot[k] = PathParser.createPathFromPathData(dot);
            Path band = new Path();
            grow.setStrokeWidth(2f * MARK_GAP);
            grow.getFillPath(mDot[k], band);
            union(halo, band);
            union(halo, mDot[k]);
        }
        mHalo[k] = halo;
    }

    private static void union(Path into, Path add) {
        if (!into.op(add, Path.Op.UNION)) into.addPath(add);
    }

    /** The colour of the low and hot states; the theme's error. */
    void setErrorColor(@ColorInt int color) {
        if (mErrorInk == color) return;
        mErrorInk = color;
        invalidateSelf();
    }

    /** Shows {@code state}; redraws only when the picture would change. */
    void setState(@NonNull BatteryChipState state) {
        BatteryChipState.Kind before = mState.kind();
        mState = state;
        int fillPx = fillPixels();
        if (before == state.kind() && fillPx == mFillPx) return;
        mFillPx = fillPx;
        invalidateSelf();
    }

    /** The image tint the widget sets for its ink. */
    @Override public void setTintList(@Nullable ColorStateList tint) {
        if (tint == null) return;
        int ink = tint.getColorForState(getState(), tint.getDefaultColor());
        if (ink == mInk) return;
        mInk = ink;
        invalidateSelf();
    }

    @Override protected void onBoundsChange(@NonNull Rect bounds) {
        float side = Math.min(bounds.width(), bounds.height());
        if (side <= 0f) {
            mUnit = 0f;
            return;
        }
        mUnit = side / VIEWPORT;
        mLeft = bounds.left + (bounds.width() - side) / 2f;
        mTop = bounds.top + (bounds.height() - side) / 2f;
        mFillPx = fillPixels();
    }

    /** The bar's width in pixels for the current state and bounds; none when hot. */
    int fillPixels() {
        if (mState.kind() == BatteryChipState.Kind.HOT) return 0;
        return Math.round((mBar[2] - mBar[0]) * mUnit * mState.fill());
    }

    @Override public void draw(@NonNull Canvas canvas) {
        if (mUnit <= 0f) return;
        BatteryChipState.Kind kind = mState.kind();
        int k = kind.ordinal();
        int ink = kind == BatteryChipState.Kind.LOW || kind == BatteryChipState.Kind.HOT
            ? mErrorInk : mInk;
        ink = (ink & 0x00FFFFFF) | (Math.round((ink >>> 24) * (mAlpha / 255f)) << 24);
        mStroke.setColor(ink);
        mFill.setColor(ink);
        int save = canvas.save();
        canvas.translate(mLeft, mTop);
        canvas.scale(mUnit, mUnit);
        canvas.drawPath(kind == BatteryChipState.Kind.HOT ? mHotBody : mBody, mStroke);
        if (mFillPx > 0) {
            float[] bar = mBar;
            int clip = canvas.save();
            Path halo = mHalo[k];
            if (halo != null) canvas.clipOutPath(halo);
            canvas.drawRoundRect(bar[0], bar[1], bar[0] + mFillPx / mUnit, bar[3], BAR_RADIUS,
                BAR_RADIUS, mFill);
            canvas.restoreToCount(clip);
        }
        Path mark = mMark[k];
        if (mark != null) canvas.drawPath(mark, mStroke);
        Path dot = mDot[k];
        if (dot != null) canvas.drawPath(dot, mFill);
        canvas.restoreToCount(save);
    }

    @Override public void setAlpha(int alpha) {
        if (mAlpha == alpha) return;
        mAlpha = alpha;
        invalidateSelf();
    }

    @Override public void setColorFilter(@Nullable ColorFilter colorFilter) {
        mStroke.setColorFilter(colorFilter);
        mFill.setColorFilter(colorFilter);
    }

    @Override public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
