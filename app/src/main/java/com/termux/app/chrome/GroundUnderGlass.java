package com.termux.app.chrome;

import android.graphics.Canvas;
import android.graphics.Path;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.R;

/**
 * The ground of the page beneath a glass, drawn into that glass over its frame ({@link
 * GlassGround}, #283). A bar's glass draws it between its frost and its tint; the dock's backdrop
 * wears it in its overlay; a glass stack ({@link GlassStack#build}: the keyboard, the joined Docked
 * frame, the under-keyboard card) holds it as the layer right over its frame, and its view is
 * found through the drawable's callbacks. A glass whose picture already holds the strip (the
 * dock's Live picture records what is beneath it) is told so ({@link #setCarried}) and draws
 * nothing more.
 *
 * <p>Every draw reads the strip's bands where the glass stands now, over its bounds; the strip
 * redraws its riders when the bands move ({@link GroundStripView#addRider}). Allocates nothing in
 * {@link #draw}; the strip is looked up once, on the first draw that has a view.</p>
 */
public final class GroundUnderGlass extends Drawable {

    /** How deep a stack's callbacks are followed to find its view. */
    private static final int MAX_CALLBACK_DEPTH = 8;

    @Nullable private final View mFixedHost;
    /**
     * The view a stack's ground is drawn on, named where the stack is built ({@link #bind}): the
     * wrappers a stack is worn through (a layout-neutral or rounded sheet, a crossfade) do not
     * all pass their callbacks on, so the callbacks alone cannot be trusted to reach the view.
     */
    @Nullable private View mBoundHost;
    @NonNull private final String mTag;
    @Nullable private GroundStripView mStrip;
    private boolean mCarried;
    private int mAlpha = 255;
    /** The corner radius the ground is cut to, 0 for none (a bar's glass is cut by its view). */
    private float mRadiusPx;
    /** The rounded outline of the bounds, rebuilt only when they or the radius change. */
    @NonNull private final Path mShape = new Path();
    @NonNull private final android.graphics.RectF mShapeRect = new android.graphics.RectF();
    private boolean mShapeStale = true;

    /** A glass that is {@code host} itself, or its overlay: the bars and the dock's backdrop. */
    public GroundUnderGlass(@NonNull View host, @NonNull String tag) {
        mFixedHost = host;
        mTag = tag;
    }

    /** A layer of a glass stack: its view is the one its callbacks lead to. */
    public GroundUnderGlass(@NonNull String tag) {
        mFixedHost = null;
        mTag = tag;
    }

    /** The view this ground is drawn in, or null while it is not on one. */
    @Nullable
    public View host() {
        if (mFixedHost != null) return mFixedHost;
        if (mBoundHost != null) return mBoundHost;
        Callback callback = getCallback();
        for (int i = 0; i < MAX_CALLBACK_DEPTH && callback instanceof Drawable; i++)
            callback = ((Drawable) callback).getCallback();
        return callback instanceof View ? (View) callback : null;
    }

    /** Whether it still draws on a view in a window: a rider that is not may give its place. */
    boolean live() {
        View host = host();
        return host != null && host.isAttachedToWindow();
    }

    /** The strip moved its bands: draw again. */
    void redraw() {
        View named = mFixedHost != null ? mFixedHost : mBoundHost;
        if (named != null) named.invalidate();
        else invalidateSelf();
    }

    /** The view this ground was bound to ({@link #bind}), or null. */
    @Nullable
    View boundHost() {
        return mBoundHost;
    }

    /**
     * Names {@code host} as the view the grounds in {@code stack} (a {@link GlassStack#build}
     * result: the ground itself or a layer of a {@code LayerDrawable}) are drawn on, and rides the
     * strip with them. Called where a stack is built for a view, once per build, never per frame.
     */
    public static void bind(@Nullable Drawable stack, @NonNull View host) {
        if (!GlassGround.ON || stack == null) return;
        if (stack instanceof GroundUnderGlass) {
            GroundUnderGlass ground = (GroundUnderGlass) stack;
            ground.mBoundHost = host;
            ground.attach();
            return;
        }
        if (stack instanceof android.graphics.drawable.LayerDrawable) {
            android.graphics.drawable.LayerDrawable layers =
                (android.graphics.drawable.LayerDrawable) stack;
            for (int i = 0; i < layers.getNumberOfLayers(); i++) bind(layers.getDrawable(i), host);
        }
    }

    /**
     * Finds the window's ground strip from the host and rides it. Nothing when the gate is off,
     * there is no host yet or the window has no strip.
     */
    public void attach() {
        if (!GlassGround.ON) return;
        if (mStrip == null) {
            View host = host();
            if (host == null) return;
            View found = host.getRootView().findViewById(R.id.ground_strip);
            if (!(found instanceof GroundStripView)) return;
            mStrip = (GroundStripView) found;
        }
        mStrip.addRider(this);
    }

    public void detach() {
        if (mStrip != null) mStrip.removeRider(this);
    }

    /**
     * Cuts the grounds in {@code stack} to a round rect of {@code radiusPx} over their bounds, so
     * a glass worn by a plain view (the marks' title capsule) paints nothing outside its own
     * outline: the ground is a flat strip over a rectangle and has no shape of its own.
     */
    public static void round(@Nullable Drawable stack, float radiusPx) {
        if (stack instanceof GroundUnderGlass) {
            GroundUnderGlass ground = (GroundUnderGlass) stack;
            if (ground.mRadiusPx == radiusPx) return;
            ground.mRadiusPx = radiusPx;
            ground.mShapeStale = true;
            ground.invalidateSelf();
        } else if (stack instanceof android.graphics.drawable.LayerDrawable) {
            android.graphics.drawable.LayerDrawable layers =
                (android.graphics.drawable.LayerDrawable) stack;
            for (int i = 0; i < layers.getNumberOfLayers(); i++) round(layers.getDrawable(i), radiusPx);
        }
    }

    /** The radius the ground is cut to; 0 for none. */
    float cornerRadiusPx() {
        return mRadiusPx;
    }

    /** {@code radiusPx} held to what a {@code width} by {@code height} box can round: its half. */
    static float fittedRadiusPx(float radiusPx, float width, float height) {
        return Math.max(0f, Math.min(radiusPx, Math.min(width, height) / 2f));
    }

    @Override
    protected void onBoundsChange(@NonNull Rect bounds) {
        mShapeStale = true;
    }

    /** The glass's picture already holds the strip (a Live recording): draw nothing more. */
    public void setCarried(boolean carried) {
        if (carried == mCarried) return;
        mCarried = carried;
        redraw();
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        if (!GlassGround.ON || mCarried || mAlpha == 0) return;
        if (mStrip == null) attach();
        GroundStripView strip = mStrip;
        View host = host();
        if (strip == null || host == null) return;
        Rect bounds = getBounds();
        if (bounds.isEmpty()) return;
        if (mRadiusPx <= 0f) {
            strip.drawUnder(canvas, host, bounds.left, bounds.top, bounds.right, bounds.bottom,
                mAlpha);
            return;
        }
        if (mShapeStale) {
            float r = fittedRadiusPx(mRadiusPx, bounds.width(), bounds.height());
            mShapeRect.set(bounds);
            mShape.rewind();
            mShape.addRoundRect(mShapeRect, r, r, Path.Direction.CW);
            mShapeStale = false;
        }
        int save = canvas.save();
        canvas.clipPath(mShape);
        strip.drawUnder(canvas, host, bounds.left, bounds.top, bounds.right, bounds.bottom,
            mAlpha);
        canvas.restoreToCount(save);
    }

    /** A stack's one alpha (a fade, {@link GlassStack#applyStackAlpha}) fades the ground too. */
    @Override public void setAlpha(int alpha) {
        int clamped = Math.max(0, Math.min(255, alpha));
        if (clamped == mAlpha) return;
        mAlpha = clamped;
        invalidateSelf();
    }

    @Override public int getAlpha() {
        return mAlpha;
    }

    @Override public void setColorFilter(@Nullable ColorFilter colorFilter) {}

    @Override public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
