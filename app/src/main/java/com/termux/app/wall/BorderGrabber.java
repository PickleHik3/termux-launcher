package com.termux.app.wall;

import android.animation.ValueAnimator;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.android.material.color.MaterialColors;

/**
 * A border swipe's grabber: a short pill centred on the current page's bottom border for the
 * keyboard swipe, or on its top border for the status bar's, the way a sheet shows where it can
 * be pulled, so the line the swipe starts on says it can be pulled. In the rim's accent at low
 * emphasis, just outside the page's edge, so it sits on the frame line and in the air beyond it
 * and never over the page's own text. One class, two instances: which edge is fixed at
 * construction ({@link #BorderGrabber(View, boolean)}), and everything else is the same.
 *
 * <p>It answers the finger: brighter and a little wider from the moment a finger lands in its
 * swipe's band ({@link BorderDrag#KEYBOARD_REACH_DP}, {@link BorderDrag#STATUS_REACH_DP}), and
 * drawn a short way after it, with resistance, while the swipe is held; both ease back on the
 * lift. It moves and fades with its page — scaled with a sunk page, gone over the first stretch of
 * a slide as the page's outline goes ({@link PaneWallPolicy#pageOutlineAlpha}) — and is not drawn
 * where its swipe is off. Under reduced motion it is the resting pill and nothing else.
 *
 * <p>Drawn by the wall ({@link PaneWallLayout#dispatchDraw}) over its pages, so it costs a rounded
 * rect in the wall's own display list and nothing in any page's. The geometry is pure and static
 * so it can be tested without a canvas.
 */
final class BorderGrabber {

    static final float WIDTH_DP = 32f;
    /** How much wider it grows under a finger. */
    static final float PRESSED_EXTRA_WIDTH_DP = 8f;
    static final float THICKNESS_DP = 3f;
    /** How far outside the page's edge its centre sits: on the frame line, outside the page. */
    static final float DROP_DP = 2f;
    static final float REST_ALPHA = 0.3f;
    static final float PRESSED_ALPHA = 0.75f;
    /** How much of the swipe's travel it is drawn along by, and how far at most. */
    static final float TRACK_FRACTION = 0.25f;
    static final float TRACK_MAX_DP = 10f;
    static final long EMPHASIS_MS = 140L;

    @NonNull private final View mHost;
    /** Whether it marks the top border rather than the bottom one. */
    private final boolean mTop;
    private final Paint mPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF mRect = new RectF();
    private int mColor;
    /** 0 at rest, 1 under a finger. */
    private float mEmphasis;
    private boolean mPressed;
    /** The finger's travel it is drawn along by, in px; 0 at rest. */
    private float mTrackPx;
    @Nullable private ValueAnimator mEase;

    /** The keyboard swipe's grabber, on the bottom border. */
    BorderGrabber(@NonNull View host) {
        this(host, false);
    }

    /** A grabber on the top border ({@code top}), or the bottom one. */
    BorderGrabber(@NonNull View host, boolean top) {
        mHost = host;
        mTop = top;
        mPaint.setStyle(Paint.Style.FILL);
        refreshColor();
    }

    /** Re-read the accent: the scheme or the theme may have moved under it. */
    void refreshColor() {
        int color = MaterialColors.getColor(mHost.getContext(),
            com.google.android.material.R.attr.colorPrimary,
            ContextCompat.getColor(mHost.getContext(), com.termux.R.color.termux_primary));
        if (color == mColor) return;
        mColor = color;
        mHost.invalidate();
    }

    /**
     * A finger is on the band ({@code pressed}) and has moved {@code dyPx} since it landed. The
     * emphasis eases to where the press puts it, unless motion is reduced, where nothing moves.
     */
    void setPressed(boolean pressed, float dyPx, boolean reducedMotion) {
        float density = mHost.getResources().getDisplayMetrics().density;
        float track = pressed && !reducedMotion ? trackPx(dyPx, density) : 0f;
        // At rest, or easing back to it, every later event of a finger elsewhere changes nothing.
        if (pressed == mPressed && (!pressed || track == mTrackPx)) return;
        boolean pressChanged = pressed != mPressed;
        mPressed = pressed;
        if (reducedMotion) {
            stopEase();
            mEmphasis = 0f;
            mTrackPx = 0f;
            mHost.invalidate();
            return;
        }
        if (pressed) {
            mTrackPx = track;
            if (pressChanged) easeTo(1f, mTrackPx);
            else mHost.invalidate();
        } else {
            easeTo(0f, 0f);
        }
    }

    private void easeTo(final float emphasis, final float track) {
        stopEase();
        final float fromEmphasis = mEmphasis;
        final float fromTrack = mTrackPx;
        if (fromEmphasis == emphasis && fromTrack == track) {
            mHost.invalidate();
            return;
        }
        ValueAnimator ease = ValueAnimator.ofFloat(0f, 1f);
        ease.setDuration(EMPHASIS_MS);
        ease.addUpdateListener(animation -> {
            float f = animation.getAnimatedFraction();
            mEmphasis = fromEmphasis + (emphasis - fromEmphasis) * f;
            // A press keeps drawing the finger's own travel; only a release eases it home.
            if (!mPressed) mTrackPx = fromTrack + (track - fromTrack) * f;
            mHost.invalidate();
        });
        mEase = ease;
        ease.start();
    }

    private void stopEase() {
        ValueAnimator ease = mEase;
        mEase = null;
        if (ease != null) ease.cancel();
    }

    /** Drop any easing and land at rest: the wall left the window. */
    void reset() {
        stopEase();
        mPressed = false;
        mEmphasis = 0f;
        mTrackPx = 0f;
    }

    /** Whether it marks the top border. */
    boolean isTop() {
        return mTop;
    }

    /**
     * Draw the grabber for a page whose marked edge's centre is at ({@code centreX},
     * {@code edgeY}) as drawn, at {@code visibility} of its full alpha and scaled by
     * {@code scale} with its page.
     */
    void draw(@NonNull Canvas canvas, float centreX, float edgeY, float scale, float visibility) {
        if (!(visibility > 0f)) return;
        float density = mHost.getResources().getDisplayMetrics().density;
        bounds(mRect, centreX, edgeY, mEmphasis, mTrackPx, scale, density, mTop);
        float alpha = alpha(mEmphasis) * Math.min(1f, visibility);
        mPaint.setColor(mColor);
        mPaint.setAlpha(Math.round(255f * alpha * (android.graphics.Color.alpha(mColor) / 255f)));
        float radius = mRect.height() / 2f;
        canvas.drawRoundRect(mRect, radius, radius, mPaint);
    }

    /** The emphasis the grabber is drawn at, for tests. */
    float emphasis() {
        return mEmphasis;
    }

    /** The finger's travel it is drawn along by, for tests. */
    float trackOffsetPx() {
        return mTrackPx;
    }

    // ---- Geometry ----------------------------------------------------------------------------

    /** The bottom border's pill, as the keyboard swipe has always drawn it. */
    static void bounds(@NonNull RectF out, float centreX, float bottomY, float emphasis,
                       float trackPx, float scale, float density) {
        bounds(out, centreX, bottomY, emphasis, trackPx, scale, density, false);
    }

    /**
     * The pill for a page edge at ({@code centreX}, {@code edgeY}): centred on it sideways,
     * {@link #DROP_DP} outside it — below a bottom edge, above a top one ({@code top}) — and
     * {@code trackPx} along the swipe, wider with {@code emphasis}, and scaled with its page.
     */
    static void bounds(@NonNull RectF out, float centreX, float edgeY, float emphasis,
                       float trackPx, float scale, float density, boolean top) {
        float s = Float.isNaN(scale) || scale <= 0f ? 1f : scale;
        float e = clamp01(emphasis);
        float halfWidth = (WIDTH_DP + PRESSED_EXTRA_WIDTH_DP * e) * density * s / 2f;
        float halfThickness = THICKNESS_DP * density * s / 2f;
        float drop = DROP_DP * density * s;
        float centreY = edgeY + (top ? -drop : drop) + (Float.isNaN(trackPx) ? 0f : trackPx);
        out.set(centreX - halfWidth, centreY - halfThickness, centreX + halfWidth,
            centreY + halfThickness);
    }

    /** The share of full alpha the grabber is drawn at for {@code emphasis}. */
    static float alpha(float emphasis) {
        return REST_ALPHA + (PRESSED_ALPHA - REST_ALPHA) * clamp01(emphasis);
    }

    /** How far the grabber is drawn along a swipe that has travelled {@code dyPx}, with resistance. */
    static float trackPx(float dyPx, float density) {
        if (Float.isNaN(dyPx)) return 0f;
        float max = TRACK_MAX_DP * density;
        return Math.max(-max, Math.min(max, dyPx * TRACK_FRACTION));
    }

    private static float clamp01(float value) {
        return value < 0f ? 0f : value > 1f ? 1f : value;
    }
}
