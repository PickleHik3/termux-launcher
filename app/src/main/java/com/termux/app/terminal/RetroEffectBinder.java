package com.termux.app.terminal;

import android.os.Build;
import android.view.View;
import android.view.ViewParent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

/**
 * Keeps one view drawn through the retro effect. Before every frame the view's window draws it
 * re-reads the view's size, its card and where it stands on the screen, and wraps a new effect
 * only when one of those has moved — so a surface at rest costs a comparison, and one that slides
 * (the keyboard rising, a dragged card, a wall page) keeps its scanlines fixed to the screen
 * instead of carrying them along.
 *
 * <p>Nothing else may set a render effect on the view this binds; NONE, or a phone below API 33,
 * takes off only the effect this binder put on.
 */
public final class RetroEffectBinder {

    /** The card a view's effect is cut to, in its own px. */
    public interface Shape {
        /**
         * Writes {@code left, top, right, bottom} of the card into {@code rect} and returns its
         * corner radius.
         */
        float card(@NonNull View view, @NonNull float[] rect);
    }

    /**
     * The view's own bounds, square: for a container whose children clip their own shapes. The
     * effect keeps every pixel's coverage, so the corners those children cut stay cut.
     */
    public static final Shape BOUNDS = (view, rect) -> {
        rect[0] = 0f;
        rect[1] = 0f;
        rect[2] = view.getWidth();
        rect[3] = view.getHeight();
        return 0f;
    };

    @NonNull private final View mView;
    @NonNull private final Shape mShape;
    private final float mBend;
    private final float mVignette;
    @NonNull private final PaneRetroEffect mEffect = new PaneRetroEffect();
    @NonNull private final PreDrawHook mHook;

    @NonNull private PaneRetroStyle mStyle = PaneRetroStyle.NONE;
    /** What the effect on the view was made from; meaningful only while {@link #mApplied}. */
    @NonNull private final RetroUniforms mOnScreen = new RetroUniforms();
    @NonNull private PaneRetroStyle mOnScreenStyle = PaneRetroStyle.NONE;
    private boolean mApplied;
    /** Scratch, so a frame where nothing moved allocates nothing. */
    @NonNull private final RetroUniforms mScratch = new RetroUniforms();
    @NonNull private final int[] mLocation = new int[2];
    @NonNull private final float[] mCard = new float[4];

    /**
     * @param bend     the CRT's barrel bend: {@link PaneRetroEffect#CRT_BEND} on a pane card, 0 on
     *                 chrome, whose keys and icons must stay under the finger
     * @param vignette how far the corners darken, 0 for none
     */
    public RetroEffectBinder(@NonNull View view, @NonNull Shape shape, float bend, float vignette) {
        mView = view;
        mShape = shape;
        mBend = bend;
        mVignette = vignette;
        mHook = new PreDrawHook(view, this::refresh);
    }

    /** A flat card cut to the view's bounds: what every chrome surface is drawn through. */
    @NonNull
    public static RetroEffectBinder flat(@NonNull View view) {
        return new RetroEffectBinder(view, BOUNDS, 0f, 0f);
    }

    @NonNull
    public View view() {
        return mView;
    }

    @NonNull
    public PaneRetroStyle style() {
        return mStyle;
    }

    /** The look to draw the view through, from the next frame on; NONE takes it off now. */
    public void setStyle(@Nullable PaneRetroStyle style) {
        PaneRetroStyle next = style == null || !PaneRetroEffect.available()
            ? PaneRetroStyle.NONE : style;
        mStyle = next;
        mHook.setOn(next != PaneRetroStyle.NONE);
        refresh();
    }

    /** Off for good: the effect comes off and the view keeps no reference to this binder. */
    public void release() {
        setStyle(PaneRetroStyle.NONE);
        mHook.release();
    }

    /**
     * Re-reads the view now rather than on its next frame: for an owner that has just changed
     * what {@link Shape} answers. Cheap when nothing moved.
     */
    public void refresh() {
        if (mStyle == PaneRetroStyle.NONE || !PaneRetroEffect.available()) {
            clear();
            return;
        }
        int width = mView.getWidth();
        int height = mView.getHeight();
        if (width <= 0 || height <= 0) {
            clear();
            return;
        }
        // Where the view is, is only known in a window; the first frame after attaching asks again.
        if (!mView.isAttachedToWindow()) return;
        mView.getLocationInWindow(mLocation);
        float scaleX = 1f;
        float scaleY = 1f;
        for (View v = mView; v != null; ) {
            scaleX *= v.getScaleX();
            scaleY *= v.getScaleY();
            ViewParent parent = v.getParent();
            v = parent instanceof View ? (View) parent : null;
        }
        float radius = mShape.card(mView, mCard);
        mScratch.set(width, height, mLocation[0], mLocation[1], scaleX, scaleY,
            mView.getResources().getDisplayMetrics().density,
            mCard[0], mCard[1], mCard[2], mCard[3], radius, mBend, mVignette);
        if (mApplied && mOnScreenStyle == mStyle && mOnScreen.equals(mScratch)) return;
        mOnScreen.copyFrom(mScratch);
        mOnScreenStyle = mStyle;
        mApplied = true;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Api33.apply(mView, mEffect, mStyle, mOnScreen);
    }

    private void clear() {
        if (!mApplied) return;
        mApplied = false;
        mOnScreenStyle = PaneRetroStyle.NONE;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) mView.setRenderEffect(null);
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private static final class Api33 {
        static void apply(@NonNull View view, @NonNull PaneRetroEffect effect,
                          @NonNull PaneRetroStyle style, @NonNull RetroUniforms uniforms) {
            // Null when the shader failed to compile: the view goes back to plain, and stays so
            // without retrying every frame, since the uniforms are recorded as on screen.
            view.setRenderEffect(effect.effectFor(style, uniforms));
        }
    }
}
