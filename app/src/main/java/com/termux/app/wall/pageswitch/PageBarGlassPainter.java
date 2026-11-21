package com.termux.app.wall.pageswitch;

import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;

import androidx.annotation.NonNull;

import com.termux.app.chrome.InsetGlass;
import com.termux.app.chrome.OnGlass;

/**
 * The cues Home's page bar draws over its glass (or its solid card), by its
 * {@link PageBarGlassStyle}:
 *
 * <ul>
 *   <li>{@link PageBarGlassStyle#WELL}: the well ({@link InsetGlass}): the floor's shade, the
 *   contact shadow drawn as rings inside the outline, and the rim of the hole outside it, dark
 *   across the top and light across the bottom arc;</li>
 *   <li>{@link PageBarGlassStyle#CONCAVE}: only the inverted rim, the same two hairlines at the
 *   concave strengths ({@link InsetGlass#concaveRimShadow}, {@link InsetGlass#concaveRimLight}).</li>
 * </ul>
 *
 * <p>Shaders and ring alphas are made by {@link #sync} on a size, ground or style change only;
 * {@link #draw} allocates nothing. The rim stands in the touch row's air around the slab, inside
 * the bar's view, so nothing is laid out for it.</p>
 */
final class PageBarGlassPainter {

    private final Paint mFloor = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mShadow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mRim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mRimLight = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final int[] mFloorColors = new int[InsetGlass.FLOOR_POSITIONS.length];
    private final Matrix mRimMatrix = new Matrix();
    private final RectF mRect = new RectF();
    @NonNull private int[] mRingAlpha = new int[0];
    private int mRings;
    private float mRingStep;
    private boolean mBuilt;
    private float mDensity;
    private float mLeft;
    private float mTop;
    private float mWidth;
    private float mHeight;
    private boolean mDark;
    @NonNull private PageBarGlassStyle mStyle = PageBarGlassStyle.WELL;

    PageBarGlassPainter() {
        mShadow.setStyle(Paint.Style.STROKE);
        mRim.setStyle(Paint.Style.STROKE);
        mRimLight.setStyle(Paint.Style.STROKE);
    }

    /** The style the cues were last built for. */
    @NonNull
    PageBarGlassStyle style() {
        return mStyle;
    }

    /**
     * Builds the cues for a slab at {@code left, top} of {@code width x height} on a dark or
     * light ground ({@link PageBarGlass.Ink#wellDark}) in {@code style}. Builds only when one of
     * those changed: on a layout or a dress pass, never per frame.
     *
     * @return whether anything was built (the caller invalidates)
     */
    boolean sync(float density, float left, float top, float width, float height, boolean dark,
                 @NonNull PageBarGlassStyle style) {
        if (mBuilt && density == mDensity && left == mLeft && top == mTop && width == mWidth
                && height == mHeight && dark == mDark && style == mStyle) {
            return false;
        }
        mBuilt = true;
        mDensity = density;
        mLeft = left;
        mTop = top;
        mWidth = width;
        mHeight = height;
        mDark = dark;
        mStyle = style;
        boolean well = style == PageBarGlassStyle.WELL;
        // The floor: darkest under the top lip, clear in the lower part, a faint light at the bottom.
        mFloor.setShader(!well ? null : new LinearGradient(0f, top, 0f, top + height,
            InsetGlass.floorColors(dark, mFloorColors), InsetGlass.FLOOR_POSITIONS,
            Shader.TileMode.CLAMP));
        // The contact shadow: rings inside the outline, their colour by height (strongest under
        // the top, the sides' share at mid height, clear at the bottom), their alpha by depth.
        int rings = well ? InsetGlass.ringCount(density, height) : 0;
        if (mRingAlpha.length < rings) mRingAlpha = new int[rings];
        for (int i = 0; i < rings; i++) mRingAlpha[i] = InsetGlass.ringAlpha(i, rings);
        mRings = rings;
        mRingStep = InsetGlass.ringStepPx(density);
        // Each ring is twice the step wide, so neighbours overlap and no seam shows between them.
        mShadow.setStrokeWidth(2f * mRingStep);
        int shadowTop = InsetGlass.innerShadow(dark);
        int shadowSide = InsetGlass.innerShadowSide(dark);
        mShadow.setShader(rings <= 0 ? null : new LinearGradient(0f, top, 0f, top + height,
            new int[] {shadowTop, shadowSide, shadowSide & 0x00FFFFFF}, new float[] {0f, 0.5f, 1f},
            Shader.TileMode.CLAMP));
        // The rim, outside the outline: a dark hairline across the top, and the bottom arc's
        // light, an ellipse round the centre bottom as wide as the slab and RIM_LIGHT_REACH_SHARE
        // of its height tall, so it is strongest there and fades up the ends. The well's subtle;
        // the concave glass's at its own strengths, as it is the only cue that glass draws.
        float rim = InsetGlass.rimWidthPx(density, height);
        int rimShadow = well ? InsetGlass.rimShadow(dark) : InsetGlass.concaveRimShadow(dark);
        int rimLight = well ? InsetGlass.rimLight(dark) : InsetGlass.concaveRimLight(dark);
        mRim.setStrokeWidth(rim);
        mRim.setShader(rim <= 0f ? null : new LinearGradient(0f, top - rim, 0f,
            top + height * InsetGlass.RIM_SHADOW_TO_SHARE,
            rimShadow, rimShadow & 0x00FFFFFF, Shader.TileMode.CLAMP));
        mRimLight.setStrokeWidth(rim);
        float reach = Math.max(1f, height * InsetGlass.RIM_LIGHT_REACH_SHARE);
        float cx = left + width / 2f;
        float bottom = top + height + rim;
        RadialGradient light = rim <= 0f ? null : new RadialGradient(cx, bottom, reach,
            new int[] {rimLight,
                OnGlass.withAlpha(rimLight, Math.round((rimLight >>> 24) * InsetGlass.RIM_LIGHT_MID)),
                rimLight & 0x00FFFFFF},
            new float[] {0f, 0.5f, 1f}, Shader.TileMode.CLAMP);
        if (light != null) {
            mRimMatrix.setScale(Math.max(1f, (width / 2f + rim) / reach), 1f, cx, bottom);
            light.setLocalMatrix(mRimMatrix);
        }
        mRimLight.setShader(light);
        return true;
    }

    /** The cues for the slab from {@code left, top} to {@code right, bottom}, cut at {@code radius}. */
    void draw(@NonNull Canvas canvas, float left, float top, float right, float bottom, float radius) {
        if (!mBuilt) return;
        if (mFloor.getShader() != null) {
            mRect.set(left, top, right, bottom);
            canvas.drawRoundRect(mRect, radius, radius, mFloor);
        }
        if (mShadow.getShader() != null) {
            // Ring i stands i steps in; its stroke reaches from the outline (ring 0) inward.
            for (int i = 0; i < mRings; i++) {
                float inset = mRingStep * (i + 1);
                if (2f * inset >= bottom - top) break;
                mRect.set(left + inset, top + inset, right - inset, bottom - inset);
                float r = Math.max(0f, radius - inset);
                mShadow.setAlpha(mRingAlpha[i]);
                canvas.drawRoundRect(mRect, r, r, mShadow);
            }
        }
        float rim = mRim.getStrokeWidth();
        if (rim > 0f && mRim.getShader() != null) {
            // Outset by half the stroke so the hairline lies just outside the outline.
            mRect.set(left - rim / 2f, top - rim / 2f, right + rim / 2f, bottom + rim / 2f);
            float r = radius + rim / 2f;
            canvas.drawRoundRect(mRect, r, r, mRim);
            if (mRimLight.getShader() != null) canvas.drawRoundRect(mRect, r, r, mRimLight);
        }
    }

    /** Whether the floor's shade or the contact shadow is drawn: the well's cues, for tests. */
    boolean drawsShade() {
        return mBuilt && (mFloor.getShader() != null || mShadow.getShader() != null);
    }

    /** The upper rim's dark hairline as last built, for tests. */
    int rimShadowColor() {
        return mStyle == PageBarGlassStyle.WELL ? InsetGlass.rimShadow(mDark)
            : InsetGlass.concaveRimShadow(mDark);
    }
}
