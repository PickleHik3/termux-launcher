package com.termux.app.chrome;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Picture;
import android.graphics.Shader;
import android.os.Build;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewParent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.annotation.VisibleForTesting;

import com.termux.shared.logger.Logger;

/**
 * The dock's icon row as a small picture for its glass to reflect
 * ({@link GlassRefraction.Program#setReflection}).
 *
 * <p>The row is recorded and rasterised once, at a quarter of its size, whenever what it shows
 * may have changed: its layout ran (a page settled, an icon was pinned, moved or resized) or one
 * of its icons redrew (an icon arrived, a badge changed). Those ask for a capture a short settle
 * after the last of them, so a burst of changes is one capture, and a capture is one rebuild of
 * the dock's effect. Nothing asks at rest, so the glass at rest costs nothing: the picture is a
 * hardware bitmap the effect samples, never redrawn per frame. A capture never invalidates the
 * row, so it cannot ask for itself.</p>
 *
 * <p>While a page swipe carries the icons sideways the picture would stay behind, so the
 * reflection fades out as the swipe sets off ({@link #onPagePosition}) and, once the page
 * settles and is captured again, fades back in: each fade is {@link #FADE_MS} of frames, a
 * rebuild each, and nothing runs once it is done, so the glass at rest still costs nothing.
 * The host aims the picture with the row's swipe while it fades out, so it leaves with its
 * icons. A drag that reorders the icons is reflected where they land.</p>
 *
 * <p>Memory: one bitmap of a quarter of the row each way, about 40 KB on a 1080 px phone.</p>
 */
@RequiresApi(Build.VERSION_CODES.TIRAMISU)
public final class DockIconReflection {

    /** What the picture holds of the row, each way. Low on purpose: a reflection is soft. */
    @VisibleForTesting static final float SCALE = 0.25f;
    /** How long after the last change the row is captured. */
    @VisibleForTesting static final long SETTLE_MS = 160L;
    /** How far off a whole page the row may be and still count as settled. */
    @VisibleForTesting static final float PAGE_SETTLED_EPSILON = 0.01f;
    /** How long the reflection takes to fade out for a swipe, and back in after it. */
    @VisibleForTesting static final long FADE_MS = 150L;

    /** Told when the picture changed or went away, so the glass can be rebuilt once. */
    public interface Listener {
        void onReflectionChanged();
    }

    @NonNull private final View mRow;
    @NonNull private final Listener mListener;
    @NonNull private final Runnable mCapture = this::capture;
    @NonNull private final Runnable mFadeStep = this::fadeStep;
    @NonNull private final View.OnLayoutChangeListener mLayoutListener =
        (v, l, t, r, b, ol, ot, or, ob) -> requestCapture();

    private boolean mEnabled;
    /**
     * Whether the row is on screen in the dock, as Dock rows announced it
     * ({@code DockRows.Listener}); the row's own view state is not read for it.
     */
    private boolean mRowOnScreen;
    /** True while a page swipe carries the icons: the reflection is put away until it settles. */
    private boolean mSuspended;
    @Nullable private Bitmap mBitmap;
    @Nullable private BitmapShader mShader;
    private float mScaleX = SCALE, mScaleY = SCALE;
    /** How strongly the reflection shows now, 0..1; 0 once it faded out for a swipe. */
    private float mStrength = 1f;
    /** A fade under way: from, to, and when it started (uptime). */
    private boolean mFading;
    private float mFadeFrom = 1f, mFadeTo = 1f;
    private long mFadeStartMs;

    public DockIconReflection(@NonNull View row, @NonNull Listener listener) {
        mRow = row;
        mListener = listener;
        row.addOnLayoutChangeListener(mLayoutListener);
    }

    /**
     * On while the dock's style reflects its icons; off lets the picture go. Turning it on asks
     * for a capture; nothing is drawn until one lands.
     */
    public void setEnabled(boolean enabled) {
        if (mEnabled == enabled) return;
        mEnabled = enabled;
        if (enabled) {
            requestCapture();
        } else {
            mRow.removeCallbacks(mCapture);
            mRow.removeCallbacks(mFadeStep);
            mFading = false;
            mStrength = 1f;
            mBitmap = null;
            mShader = null;
            mSuspended = false;
        }
    }

    /** How strongly the reflection shows now, 0..1: 1 at rest, between while it fades. */
    public float strength() {
        return mStrength;
    }

    public boolean isEnabled() {
        return mEnabled;
    }

    /**
     * Whether the row stands on screen in the dock, as Dock rows announces it. A place without
     * the dock's apps row (a terminal window in front) hides the row's pager and leaves the row
     * itself at its stale Home offset, so off screen nothing is reflected: the icons' mirror
     * would otherwise be aimed into the keyboard's top rows through the unified glass.
     */
    public void setRowOnScreen(boolean onScreen) {
        mRowOnScreen = onScreen;
    }

    /** Whether this is the reflection of {@code row}. */
    public boolean follows(@NonNull View row) {
        return mRow == row;
    }

    /** Something the row shows may have changed: capture it once things settle. */
    public void requestCapture() {
        if (!mEnabled || mSuspended) return;
        mRow.removeCallbacks(mCapture);
        mRow.postDelayed(mCapture, SETTLE_MS);
    }

    /**
     * The row's page position as it moves: off a whole page, the reflection fades out and is
     * then let go; back on one, it is captured again and fades back in.
     */
    public void onPagePosition(float pagePosition) {
        if (!mEnabled) return;
        boolean settled = pageSettled(pagePosition);
        if (!settled && !mSuspended) {
            mSuspended = true;
            mRow.removeCallbacks(mCapture);
            if (mShader != null) {
                startFade(0f);
            } else {
                mStrength = 0f;
            }
        } else if (settled && mSuspended) {
            mSuspended = false;
            requestCapture();
        }
    }

    /** Fades from the strength shown now to {@code to}, one frame at a time. */
    private void startFade(float to) {
        mFadeFrom = mStrength;
        mFadeTo = to;
        mFadeStartMs = SystemClock.uptimeMillis();
        mFading = true;
        mRow.removeCallbacks(mFadeStep);
        mRow.postOnAnimation(mFadeStep);
    }

    /** One frame of the fade, before the frame draws: one rebuild of the glass. */
    private void fadeStep() {
        if (!mFading || !mEnabled) return;
        long elapsed = SystemClock.uptimeMillis() - mFadeStartMs;
        boolean done = elapsed >= FADE_MS;
        mStrength = done ? mFadeTo : fadeStrength(mFadeFrom, mFadeTo, elapsed);
        if (done) {
            mFading = false;
            // Faded out for a swipe: the picture is let go, and the settle captures a new one.
            if (mFadeTo <= 0f && mSuspended) {
                mBitmap = null;
                mShader = null;
            }
        }
        mListener.onReflectionChanged();
        if (!done) mRow.postOnAnimation(mFadeStep);
    }

    /** The fade's strength {@code elapsedMs} into it, from {@code from} to {@code to}, eased. */
    @VisibleForTesting
    static float fadeStrength(float from, float to, long elapsedMs) {
        float t = Math.max(0f, Math.min(1f, elapsedMs / (float) FADE_MS));
        float eased = t * t * (3f - 2f * t);
        return from + (to - from) * eased;
    }

    /** Whether a page position is a whole page. */
    @VisibleForTesting
    static boolean pageSettled(float pagePosition) {
        return Float.isFinite(pagePosition)
            && Math.abs(pagePosition - Math.round(pagePosition)) <= PAGE_SETTLED_EPSILON;
    }

    /** The picture's size for a row {@code sizePx} long: a quarter of it, never under a pixel. */
    @VisibleForTesting
    static int capturedSizePx(int sizePx) {
        return Math.max(1, (int) Math.ceil(Math.max(0, sizePx) * SCALE));
    }

    private void capture() {
        if (!mEnabled || mSuspended) return;
        View row = mRow;
        int width = row.getWidth();
        int height = row.getHeight();
        // A row mid-swipe or mid-fade shows icons in passing: the settle asks again.
        if (width <= 0 || height <= 0 || !row.isAttachedToWindow()
            || row.getTranslationX() != 0f || row.getAlpha() != 1f) return;
        int capturedWidth = capturedSizePx(width);
        int capturedHeight = capturedSizePx(height);
        try {
            Picture picture = new Picture();
            Canvas canvas = picture.beginRecording(capturedWidth, capturedHeight);
            canvas.scale(capturedWidth / (float) width, capturedHeight / (float) height);
            row.draw(canvas);
            picture.endRecording();
            // Rasterised by the renderer, which also draws the icons' hardware bitmaps.
            Bitmap bitmap = Bitmap.createBitmap(picture, capturedWidth, capturedHeight,
                Bitmap.Config.HARDWARE);
            mBitmap = bitmap;
            mShader = new BitmapShader(bitmap, Shader.TileMode.DECAL, Shader.TileMode.DECAL);
            mScaleX = capturedWidth / (float) width;
            mScaleY = capturedHeight / (float) height;
        } catch (Throwable t) {
            Logger.logStackTraceWithMessage("DockIconReflection", "Dock reflection capture failed", t);
            mBitmap = null;
            mShader = null;
        }
        // Back from a swipe (or caught mid-fade): the new picture fades in rather than pops.
        if (mShader != null && (mStrength < 1f || mFading)) {
            startFade(1f);
            return;
        }
        mListener.onReflectionChanged();
    }

    /**
     * Hands the picture to {@code program}, aimed at where the row stands in the glass's own
     * coordinates: the row's laid-out offset from {@code glassHost} (both under
     * {@code commonAncestor}) plus {@code originX}/{@code originY}, where the host's origin sits
     * in the glass. Clears the program's reflection and answers false when there is no picture
     * or the row does not stand under that ancestor.
     */
    public boolean bind(@NonNull GlassRefraction.Program program, @NonNull View glassHost,
                        @NonNull View commonAncestor, float originX, float originY) {
        return bind(program, glassHost, commonAncestor, originX, originY, 0f, 1f);
    }

    /**
     * As {@link #bind(GlassRefraction.Program, View, View, float, float)}, for a slab that carries
     * a band of its own under the icons (the Prompt line's field, {@code belowPx} tall, more than
     * zero): the picture is aimed that much lower, so the lower bevel shows the icons as it would
     * with the band away, and only there ({@link GlassRefraction.Program#setReflection(
     * android.graphics.Shader, float, float, float, float, float, boolean)}), at
     * {@code strengthScale} of the strength it shows now.
     */
    public boolean bind(@NonNull GlassRefraction.Program program, @NonNull View glassHost,
                        @NonNull View commonAncestor, float originX, float originY,
                        float belowPx, float strengthScale) {
        return bind(program, glassHost, commonAncestor, originX, originY, belowPx, strengthScale,
            false);
    }

    /**
     * As {@link #bind(GlassRefraction.Program, View, View, float, float, float, float)}; with
     * {@code lowerBevelOnly} the picture shows only in the lower bevel even with no band under
     * the icons ({@code belowPx} zero): the Wall strip's slab carries its row of tiles over the
     * icons, where the face's mirror and the top bevel's copy would stand over the tiles.
     */
    public boolean bind(@NonNull GlassRefraction.Program program, @NonNull View glassHost,
                        @NonNull View commonAncestor, float originX, float originY,
                        float belowPx, float strengthScale, boolean lowerBevelOnly) {
        BitmapShader shader = mShader;
        // While a swipe fades it out the picture is still bound, at the fade's strength. The row
        // has to be on screen in the dock (setRowOnScreen), not only visible itself.
        if (!mEnabled || shader == null || mStrength <= 0f || !mRowOnScreen) {
            program.clearReflection();
            return false;
        }
        float[] row = offsetIn(mRow, commonAncestor);
        float[] host = offsetIn(glassHost, commonAncestor);
        if (row == null || host == null) {
            program.clearReflection();
            return false;
        }
        float left = row[0] - host[0] + originX;
        float top = row[1] - host[1] + originY;
        boolean below = belowPx > 0f;
        if (below) top += belowPx;
        float scale = Float.isNaN(strengthScale) ? 1f : Math.max(0f, Math.min(1f, strengthScale));
        program.setReflectionStrength(mStrength * scale);
        program.setReflection(shader, left, top, top + mRow.getHeight(), mScaleX, mScaleY,
            below || lowerBevelOnly);
        return true;
    }

    /**
     * A view's laid-out offset inside {@code ancestor}, summed from layout values only (a
     * transform in flight would otherwise be baked into the aim), or null when it is not under
     * it.
     */
    @Nullable
    private static float[] offsetIn(@NonNull View view, @NonNull View ancestor) {
        float left = 0f;
        float top = 0f;
        for (View v = view; v != null; ) {
            if (v == ancestor) return new float[] {left, top};
            left += v.getLeft();
            top += v.getTop();
            ViewParent parent = v.getParent();
            if (parent instanceof View) {
                View p = (View) parent;
                left -= p.getScrollX();
                top -= p.getScrollY();
                v = p;
            } else {
                v = null;
            }
        }
        return null;
    }
}
