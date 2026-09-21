package com.termux.app.terminal.inappkeyboard;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.SparseArray;
import android.view.View;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

/**
 * The single glyph shown just above the key under the user's finger.
 *
 * <p>One view draws everything: per finger, one filled character — the value that would be
 * committed right now — in the theme's accent, anchored over the cap. Nothing else. No veil, no
 * ring of alternates, no halo, no sub-label. A short grace period after the finger lands decides
 * between a tap and a swipe, so fast typing never flashes a glyph, and a swipe replaces the glyph
 * in place rather than lighting a second one beside it.
 *
 * <p>There are no child views and no layout pass: positions are worked out by
 * {@link KeyPopupGeometry} when something changes, not once a frame, and every animation has an
 * end, so the view costs nothing while a finger rests.
 *
 * <p>It never takes a touch. The keyboard underneath keeps the whole gesture, and the exit
 * animation runs after the key has already been sent.
 */
public final class KeyPopupOverlayView extends View {

    /** How long the glyph waits, so a swipe shows its target instead of the centre value first. */
    static final long SHOW_DELAY_MS = 50L;
    /** Enter: opacity and a short rise to the anchor. */
    private static final long ENTER_MS = 120L;
    /** Exit: opacity and a shorter rise past the anchor, once the key is on its way. */
    private static final long EXIT_MS = 100L;
    /** A swipe target replacing the glyph in place. */
    private static final long SWAP_MS = 60L;
    /** Reduced motion keeps every state change, just none of the travel. */
    private static final long INSTANT_MS = 1L;

    private static final float ENTER_RISE_DP = 4f;
    private static final float EXIT_RISE_DP = 3f;
    private static final float SHADOW_RADIUS_DP = 4f;
    private static final float SHADOW_DY_DP = 1f;
    /** Slack round the glyph's box when asking for a repaint: the shadow and the rise. */
    private static final float INVALIDATE_PAD_DP = 10f;

    private final Interpolator mEnterInterpolator = new PathInterpolator(0.4f, 0f, 0.2f, 1f);
    private final Interpolator mExitInterpolator = new PathInterpolator(0.4f, 0f, 1f, 1f);

    private final Paint mGlyphPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mMeasurePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Handler mHandler = new Handler(Looper.getMainLooper());

    /** Live popups, one per finger, plus the ones on their way out. */
    private final SparseArray<Popup> mPopups = new SparseArray<>();
    private final java.util.ArrayList<Popup> mExiting = new java.util.ArrayList<>(2);

    /** Weighted faces, one per (face, weight) pair actually asked for. */
    private final SparseArray<Typeface> mFaces = new SparseArray<>();

    private KeyPopupPalette mPalette;
    @Nullable private Typeface mLabelFont;
    @Nullable private Typeface mKeyFont;
    private boolean mReducedMotion;

    private final Rect mInvalidateRect = new Rect();

    public KeyPopupOverlayView(@NonNull Context context) {
        super(context);
        setWillNotDraw(false);
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        mPalette = KeyPopupPalette.resolve(context);
        mGlyphPaint.setStyle(Paint.Style.FILL);
        mGlyphPaint.setTextAlign(Paint.Align.CENTER);
    }

    /** A popup never takes the touch: the keyboard below owns the whole gesture. */
    @Override
    public boolean onTouchEvent(android.view.MotionEvent event) {
        return false;
    }

    // ------------------------------------------------------------------ host API

    public void setPalette(@NonNull KeyPopupPalette palette) {
        mPalette = palette;
        if (hasAnything()) invalidate();
    }

    /** The faces the keyboard draws its own caps in, so a popup's glyph matches its key. */
    public void setTypefaces(@Nullable Typeface labelFont, @Nullable Typeface keyFont) {
        mLabelFont = labelFont;
        mKeyFont = keyFont;
        mFaces.clear();
    }

    public void setReducedMotion(boolean reduced) {
        mReducedMotion = reduced;
    }

    private float density() {
        return getResources().getDisplayMetrics().density;
    }

    private long duration(long normal) {
        return mReducedMotion ? INSTANT_MS : normal;
    }

    // ------------------------------------------------------------------ popups

    /**
     * A finger went down on a key. Nothing is drawn yet: the glyph appears
     * {@link #SHOW_DELAY_MS} later, or sooner with the swipe's own value if the finger has moved
     * onto one by then, or never at all if the finger is already up.
     *
     * @param keyBounds the cap the finger is on, in this view's coordinates
     * @param clampLeft left edge the glyph must stay inside, in this view's coordinates
     * @param clampRight right edge the glyph must stay inside
     */
    public void show(int pointerId, @NonNull RectF keyBounds, float clampLeft, float clampRight,
                     @NonNull String label, boolean labelKeyFont) {
        Popup existing = mPopups.get(pointerId);
        if (existing != null) retire(existing);
        Popup popup = new Popup(pointerId, new RectF(keyBounds), clampLeft, clampRight);
        popup.setLabel(label, labelKeyFont);
        mPopups.put(pointerId, popup);
        popup.scheduleAppear();
    }

    /**
     * The finger moved onto another value; {@code slot} is -1 for the key's own character. The
     * glyph is replaced in place — the value it had never stays visible beside the new one.
     */
    public void target(int pointerId, @NonNull String label, boolean labelKeyFont, int slot) {
        Popup popup = mPopups.get(pointerId);
        if (popup == null) return;
        if (popup.slot == slot && label.equals(popup.label)) return;
        popup.slot = slot;
        if (popup.pending) {
            // The swipe beat the grace period: the target is the first thing ever drawn.
            popup.cancelTimer();
            popup.setLabel(label, labelKeyFont);
            popup.appear();
            return;
        }
        Rect before = new Rect();
        popup.bounds(before);
        popup.swapTo(label, labelKeyFont);
        popup.bounds(mInvalidateRect);
        mInvalidateRect.union(before);
        invalidate(mInvalidateRect);
    }

    /** The finger is up. The glyph fades and lifts; the key it sent is already gone. */
    public void hide(int pointerId) {
        Popup popup = mPopups.get(pointerId);
        if (popup == null) return;
        mPopups.remove(pointerId);
        if (popup.pending) {
            // A tap shorter than the grace period: nothing was ever drawn, so nothing goes away.
            popup.cancelTimer();
            popup.cancelAnimators();
            return;
        }
        mExiting.add(popup);
        popup.startExit();
        invalidatePopup(popup);
    }

    /** Every popup goes at once, without an exit: a cancel, a layout swap, a teardown. */
    public void hideAll() {
        boolean had = hasAnything();
        for (int i = 0; i < mPopups.size(); i++) mPopups.valueAt(i).stop();
        mPopups.clear();
        for (Popup popup : mExiting) popup.stop();
        mExiting.clear();
        if (had) invalidate();
    }

    private void retire(@NonNull Popup popup) {
        popup.stop();
        mPopups.remove(popup.pointerId);
    }

    /** Whether a finger's glyph is actually on screen; a popup still inside its grace period is not. */
    public boolean hasActivePopups() {
        for (int i = 0; i < mPopups.size(); i++)
            if (!mPopups.valueAt(i).pending) return true;
        return false;
    }

    /** Whether a finger is down but its glyph has not been drawn yet. */
    @androidx.annotation.VisibleForTesting
    boolean hasPendingPopups() {
        for (int i = 0; i < mPopups.size(); i++)
            if (mPopups.valueAt(i).pending) return true;
        return false;
    }

    private boolean hasAnything() {
        return mPopups.size() > 0 || !mExiting.isEmpty();
    }

    // ------------------------------------------------------------------ drawing

    @Override
    protected void onDraw(Canvas canvas) {
        for (int i = 0; i < mPopups.size(); i++) drawPopup(canvas, mPopups.valueAt(i));
        for (int i = 0; i < mExiting.size(); i++) drawPopup(canvas, mExiting.get(i));
    }

    private void drawPopup(@NonNull Canvas canvas, @NonNull Popup popup) {
        if (popup.pending || popup.metrics == null) return;
        float alpha = popup.alpha();
        if (alpha <= 0f) return;
        float density = density();
        float dy = popup.translateYPx(density);
        float swap = popup.swap;
        if (popup.previousLabel != null && swap < 1f) {
            drawGlyph(canvas, popup, popup.previousLabel, popup.previousLabelKeyFont,
                popup.previousMetrics, alpha * (1f - swap), dy, density);
        }
        drawGlyph(canvas, popup, popup.label, popup.labelKeyFont, popup.metrics,
            alpha * (popup.previousLabel == null ? 1f : swap), dy, density);
    }

    private void drawGlyph(@NonNull Canvas canvas, @NonNull Popup popup, @NonNull String label,
                           boolean labelKeyFont, @NonNull KeyPopupGeometry.Metrics metrics,
                           float alpha, float dy, float density) {
        if (alpha <= 0f) return;
        Paint paint = mGlyphPaint;
        paint.setTypeface(faceFor(labelKeyFont, metrics.monospace && !usesLabelFont(label),
            metrics.weight));
        paint.setTextSize(metrics.glyphSizePx);
        paint.setColor(mPalette.primary);
        paint.setAlpha(Math.round(Color.alpha(mPalette.primary) * alpha));
        paint.setShadowLayer(SHADOW_RADIUS_DP * density, 0f, SHADOW_DY_DP * density,
            ColorUtils.setAlphaComponent(mPalette.shadow,
                Math.round(Color.alpha(mPalette.shadow) * alpha)));
        float baseline = popup.anchorY + dy - (paint.ascent() + paint.descent()) / 2f;
        canvas.drawText(label, popup.anchorX, baseline, paint);
        paint.clearShadowLayer();
    }

    /**
     * Whether a label has to be drawn with the keyboard's own label font rather than the plain
     * monospace face. Launcher tool slots (the space bar's window and session swipes, the palette)
     * are Nerd Font glyphs in the private-use planes; the caps draw them with the label font, and
     * monospace has no such glyphs, so they would come out as boxes.
     */
    static boolean usesLabelFont(@Nullable String label) {
        if (label == null) return false;
        for (int i = 0; i < label.length(); ) {
            int cp = label.codePointAt(i);
            if ((cp >= 0xE000 && cp <= 0xF8FF) || cp >= 0xF0000) return true;
            i += Character.charCount(cp);
        }
        return false;
    }

    /**
     * The face one glyph is drawn in. Cached: at most six combinations exist, and resolving a
     * weighted face on every frame of a crossfade is allocation the draw path does not need.
     */
    private Typeface faceFor(boolean keyFont, boolean monospace, int weight) {
        int cacheKey = (keyFont ? 1024 : 0) | (monospace ? 2048 : 0) | weight;
        Typeface cached = mFaces.get(cacheKey);
        if (cached != null) return cached;
        Typeface base = keyFont ? mKeyFont : (monospace ? Typeface.MONOSPACE : mLabelFont);
        if (base == null) base = monospace ? Typeface.MONOSPACE : Typeface.DEFAULT;
        Typeface face = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
            ? Typeface.create(base, weight, false) : base;
        mFaces.put(cacheKey, face);
        return face;
    }

    /** Half the width the label actually paints, so the clamp is measured, not guessed. */
    private float halfWidthPx(@NonNull String label, boolean labelKeyFont,
                              @NonNull KeyPopupGeometry.Metrics metrics) {
        mMeasurePaint.setTypeface(faceFor(labelKeyFont,
            metrics.monospace && !usesLabelFont(label), metrics.weight));
        mMeasurePaint.setTextSize(metrics.glyphSizePx);
        return mMeasurePaint.measureText(label) / 2f;
    }

    private static float lerp(float from, float to, float t) {
        return from + (to - from) * t;
    }

    private static float clamp01(float value) {
        return value < 0f ? 0f : (value > 1f ? 1f : value);
    }

    private void invalidatePopup(@NonNull Popup popup) {
        popup.bounds(mInvalidateRect);
        invalidate(mInvalidateRect);
    }

    // ------------------------------------------------------------------ one finger's popup

    /** Per-pointer state. Geometry is recomputed when the label changes, never per frame. */
    private final class Popup {
        final int pointerId;
        final RectF keyBounds;
        final float clampLeft;
        final float clampRight;

        String label = "";
        boolean labelKeyFont;
        int slot = -1;
        /** The glyph being crossfaded out, or null when nothing is. */
        @Nullable String previousLabel;
        boolean previousLabelKeyFont;
        @Nullable KeyPopupGeometry.Metrics previousMetrics;
        float previousHalfWidthPx;

        /** True while the grace period is still running and nothing has been drawn. */
        boolean pending = true;

        KeyPopupGeometry.Metrics metrics;
        float anchorX;
        float anchorY;
        float halfWidthPx;

        float enter;
        float exit;
        float swap = 1f;
        boolean exiting;
        @Nullable ValueAnimator enterAnimator;
        @Nullable ValueAnimator swapAnimator;
        @Nullable ValueAnimator exitAnimator;
        /** The grace-period timer's task; posted on down, dropped on a swipe, a lift or a reset. */
        private final Runnable appearTask = () -> {
            if (pending) appear();
        };

        Popup(int pointerId, RectF keyBounds, float clampLeft, float clampRight) {
            this.pointerId = pointerId;
            this.keyBounds = keyBounds;
            this.clampLeft = clampLeft;
            this.clampRight = clampRight;
        }

        void setLabel(@NonNull String newLabel, boolean keyFont) {
            label = newLabel;
            labelKeyFont = keyFont;
            layout();
        }

        void layout() {
            float density = density();
            metrics = KeyPopupGeometry.metricsFor(label, density);
            halfWidthPx = halfWidthPx(label, labelKeyFont, metrics);
            anchorX = KeyPopupGeometry.anchorX(keyBounds.centerX(), halfWidthPx, clampLeft,
                clampRight, density);
            anchorY = KeyPopupGeometry.anchorY(keyBounds.top, metrics.glyphSizePx, 0f, density);
        }

        /** Wait out the grace period before anything is drawn. */
        void scheduleAppear() {
            pending = true;
            mHandler.postDelayed(appearTask, SHOW_DELAY_MS);
        }

        void cancelTimer() {
            mHandler.removeCallbacks(appearTask);
            pending = false;
        }

        /** The glyph is drawn from now on; it fades and rises into its anchor. */
        void appear() {
            cancelTimer();
            previousLabel = null;
            previousMetrics = null;
            swap = 1f;
            enter = 0f;
            enterAnimator = run(enterAnimator, ENTER_MS, value -> enter = value, () -> enter = 1f);
            invalidatePopup(this);
        }

        /** A swipe target takes the glyph's place: a crossfade at the same anchor. */
        void swapTo(@NonNull String newLabel, boolean keyFont) {
            previousLabel = label;
            previousLabelKeyFont = labelKeyFont;
            previousMetrics = metrics;
            previousHalfWidthPx = halfWidthPx;
            setLabel(newLabel, keyFont);
            swap = 0f;
            swapAnimator = run(swapAnimator, SWAP_MS, value -> swap = value, () -> {
                swap = 1f;
                previousLabel = null;
                previousMetrics = null;
                previousHalfWidthPx = 0f;
            });
        }

        void startExit() {
            exiting = true;
            exit = 0f;
            exitAnimator = run(exitAnimator, EXIT_MS, value -> exit = value, () -> {
                exit = 1f;
                mExiting.remove(Popup.this);
                invalidatePopup(Popup.this);
            });
        }

        private ValueAnimator run(@Nullable ValueAnimator previous, long ms,
                                  FloatSetter setter, Runnable onEnd) {
            if (previous != null) previous.cancel();
            ValueAnimator animator = ValueAnimator.ofFloat(0f, 1f);
            animator.setDuration(duration(ms));
            animator.addUpdateListener(a -> {
                setter.set((Float) a.getAnimatedValue());
                invalidatePopup(Popup.this);
            });
            animator.addListener(new AnimatorListenerAdapter() {
                private boolean cancelled;
                @Override public void onAnimationCancel(Animator a) { cancelled = true; }
                @Override public void onAnimationEnd(Animator a) {
                    if (!cancelled) onEnd.run();
                }
            });
            animator.start();
            return animator;
        }

        void cancelAnimators() {
            if (enterAnimator != null) enterAnimator.cancel();
            if (swapAnimator != null) swapAnimator.cancel();
            if (exitAnimator != null) exitAnimator.cancel();
            enterAnimator = swapAnimator = exitAnimator = null;
        }

        /** Everything this popup could still do, undone. */
        void stop() {
            mHandler.removeCallbacks(appearTask);
            pending = false;
            cancelAnimators();
        }

        float alpha() {
            if (exiting) return clamp01(1f - mExitInterpolator.getInterpolation(exit));
            return clamp01(mEnterInterpolator.getInterpolation(enter));
        }

        /** The short travel into the anchor on the way in, and past it on the way out. */
        float translateYPx(float density) {
            if (exiting)
                return lerp(0f, -EXIT_RISE_DP * density, mExitInterpolator.getInterpolation(exit));
            return lerp(ENTER_RISE_DP * density, 0f, mEnterInterpolator.getInterpolation(enter));
        }

        /** The box this popup can paint into, grown for the shadow and the travel. */
        void bounds(Rect out) {
            float pad = INVALIDATE_PAD_DP * density();
            float half = Math.max(halfWidthPx, previousHalfWidthPx) + pad;
            float glyph = Math.max(metrics.glyphSizePx,
                previousMetrics == null ? 0f : previousMetrics.glyphSizePx);
            float vertical = glyph + pad;
            out.set((int) Math.floor(anchorX - half), (int) Math.floor(anchorY - vertical),
                (int) Math.ceil(anchorX + half), (int) Math.ceil(anchorY + vertical));
        }
    }

    private interface FloatSetter {
        void set(float value);
    }
}
