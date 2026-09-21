package com.termux.app.terminal.inappkeyboard;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Build;
import android.util.SparseArray;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

/**
 * The floating glyph shown above the key under the user's finger.
 *
 * <p>One view draws everything: the veil over the whole surface, and one containerless popup per
 * finger — a haloed outline of the character that would be committed, with the key's configured
 * alternates orbiting it and the targeted one lit and flung outward. There are no child views and
 * no layout pass: positions are worked out by {@link KeyPopupGeometry} when something changes, not
 * once a frame, and every animation has an end, so the view costs nothing while a finger rests.
 *
 * <p>It never takes a touch. The keyboard underneath keeps the whole gesture, and the exit
 * animation runs after the key has already been sent.
 */
public final class KeyPopupOverlayView extends View {

    /** Enter: opacity and a scale that overshoots a little, about the anchor. */
    private static final long ENTER_MS = 170L;
    /** Exit: the popup swells and fades once the key is on its way. */
    private static final long EXIT_MS = 145L;
    /** An alternate sliding out to the target position, or back. */
    private static final long RING_MS = 160L;
    /** How much of the ring transition the colour and opacity take. */
    private static final float RING_COLOR_FRACTION = 90f / 160f;
    /** The veil fading in and out with the first and last popup. */
    private static final long DIM_MS = 110L;
    /** Reduced motion keeps every state change, just none of the travel. */
    private static final long INSTANT_MS = 1L;

    private static final float ENTER_FROM_SCALE = 0.72f;
    private static final float EXIT_TO_SCALE = 1.28f;
    private static final float EXIT_FROM_ALPHA = 0.9f;

    private static final float HALO_INNER_ALPHA = 0.20f;
    private static final float HALO_MID_ALPHA = 0.09f;
    private static final float GLYPH_GLOW_ALPHA = 0.50f;
    private static final float GLYPH_GLOW_RADIUS_DP = 15f;
    private static final float TARGET_GLOW_ALPHA = 0.80f;
    private static final float TARGET_GLOW_RADIUS_DP = 14f;
    private static final float SUB_LABEL_SIZE_DP = 8f;
    private static final float SUB_LABEL_GAP_DP = 3f;
    private static final float SUB_LABEL_TRACKING = 0.14f;
    /** Half the line box of the centre glyph, for placing the sub-label under it. */
    private static final float GLYPH_HALF_LINE = 0.58f;

    private final Interpolator mEnterInterpolator = new PathInterpolator(0.2f, 1.6f, 0.45f, 1f);
    private final Interpolator mRingInterpolator = new PathInterpolator(0.2f, 1.5f, 0.45f, 1f);
    private final Interpolator mExitInterpolator = new DecelerateInterpolator();

    private final Paint mDimPaint = new Paint();
    /** The keyboard's own rectangle in this view's coordinates; the veil covers it and fades out above it. */
    private final RectF mVeilBounds = new RectF();
    private final Paint mVeilFadePaint = new Paint();
    @Nullable private android.graphics.LinearGradient mVeilFade;
    private float mVeilFadeTop = Float.NaN;
    private int mVeilFadeColor;
    /** How far above the keyboard the veil fades to nothing: room for a top-row popup and its ring. */
    private static final float VEIL_FADE_DP = 132f;
    private final Paint mHaloPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mGlyphPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mRingPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint mSubPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    /** Live popups, one per finger, plus the ones on their way out. */
    private final SparseArray<Popup> mPopups = new SparseArray<>();
    private final java.util.ArrayList<Popup> mExiting = new java.util.ArrayList<>(2);

    /** Halo shaders, one per distinct radius: four label tiers at most. */
    private final SparseArray<RadialGradient> mHaloShaders = new SparseArray<>();
    /** Weighted faces, one per (face, weight) pair actually asked for. */
    private final SparseArray<Typeface> mFaces = new SparseArray<>();

    private KeyPopupPalette mPalette;
    @Nullable private Typeface mLabelFont;
    @Nullable private Typeface mKeyFont;
    private float mRingRadiusPx;
    private boolean mReducedMotion;

    private float mDimProgress;
    @Nullable private ValueAnimator mDimAnimator;

    private final Rect mInvalidateRect = new Rect();

    public KeyPopupOverlayView(@NonNull Context context) {
        super(context);
        setWillNotDraw(false);
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        float density = context.getResources().getDisplayMetrics().density;
        mRingRadiusPx = KeyPopupGeometry.RING_RADIUS_DP * density;
        mPalette = KeyPopupPalette.resolve(context);
        mGlyphPaint.setStyle(Paint.Style.STROKE);
        mRingPaint.setStyle(Paint.Style.FILL);
        mRingPaint.setTextAlign(Paint.Align.CENTER);
        mSubPaint.setStyle(Paint.Style.FILL);
        mSubPaint.setTextAlign(Paint.Align.CENTER);
        mSubPaint.setLetterSpacing(SUB_LABEL_TRACKING);
        mGlyphPaint.setTextAlign(Paint.Align.CENTER);
    }

    /** A popup never takes the touch: the keyboard below owns the whole gesture. */
    @Override
    public boolean onTouchEvent(android.view.MotionEvent event) {
        return false;
    }

    // ------------------------------------------------------------------ host API

    /**
     * Where the keyboard sits in this view. The veil dims that surface only, with a soft fade
     * above it, so the terminal, status bar and dock stay lit while a key is down.
     */
    public void setVeilBounds(@NonNull RectF keyboardBounds) {
        if (mVeilBounds.equals(keyboardBounds)) return;
        mVeilBounds.set(keyboardBounds);
        mVeilFade = null;
        if (mDimProgress > 0f) invalidate();
    }

    @NonNull RectF veilBounds() { return new RectF(mVeilBounds); }

    public void setPalette(@NonNull KeyPopupPalette palette) {
        mPalette = palette;
        mHaloShaders.clear();
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

    /** Orbit radius of the alternates, in px; exposed for device tuning. */
    public void setRingRadiusPx(float radiusPx) {
        mRingRadiusPx = radiusPx;
    }

    private float density() {
        return getResources().getDisplayMetrics().density;
    }

    private long duration(long normal) {
        return mReducedMotion ? INSTANT_MS : normal;
    }

    // ------------------------------------------------------------------ popups

    /**
     * Show the popup for one finger.
     *
     * @param keyBounds the cap the finger is on, in this view's coordinates
     * @param clampLeft left edge the popup must stay inside, in this view's coordinates
     * @param clampRight right edge the popup must stay inside
     */
    public void show(int pointerId, @NonNull RectF keyBounds, float clampLeft, float clampRight,
                     @NonNull String label, boolean labelKeyFont, @NonNull String[] ringLabels,
                     @NonNull boolean[] ringKeyFont, boolean modifier) {
        Popup existing = mPopups.get(pointerId);
        if (existing != null) retire(existing);
        Popup popup = new Popup(pointerId, new RectF(keyBounds), clampLeft, clampRight,
            ringLabels, ringKeyFont, modifier);
        popup.label = label;
        popup.labelKeyFont = labelKeyFont;
        popup.layout();
        mPopups.put(pointerId, popup);
        popup.startEnter();
        raiseDim();
        invalidatePopup(popup);
    }

    /** The finger moved onto another value; {@code slot} is -1 for the key's own character. */
    public void target(int pointerId, @NonNull String label, boolean labelKeyFont, int slot) {
        Popup popup = mPopups.get(pointerId);
        if (popup == null) return;
        if (popup.slot == slot && label.equals(popup.label)) return;
        Rect before = new Rect();
        popup.bounds(before);
        popup.previousSlot = popup.slot;
        popup.slot = slot;
        popup.label = label;
        popup.labelKeyFont = labelKeyFont;
        popup.layout();
        popup.startRing();
        popup.bounds(mInvalidateRect);
        mInvalidateRect.union(before);
        invalidate(mInvalidateRect);
    }

    /** A latchable key latched or unlatched under the finger. */
    public void latch(int pointerId, boolean latched) {
        Popup popup = mPopups.get(pointerId);
        if (popup == null || popup.latched == latched) return;
        popup.latched = latched;
        invalidatePopup(popup);
    }

    /** The finger is up. The popup swells and fades; the key it sent is already gone. */
    public void hide(int pointerId) {
        Popup popup = mPopups.get(pointerId);
        if (popup == null) return;
        mPopups.remove(pointerId);
        mExiting.add(popup);
        popup.startExit();
        lowerDimIfIdle();
        invalidatePopup(popup);
    }

    /** Every popup goes at once, without an exit: a cancel, a layout swap, a teardown. */
    public void hideAll() {
        boolean had = hasAnything();
        for (int i = 0; i < mPopups.size(); i++) mPopups.valueAt(i).cancelAnimators();
        mPopups.clear();
        for (Popup popup : mExiting) popup.cancelAnimators();
        mExiting.clear();
        lowerDimIfIdle();
        if (had) invalidate();
    }

    private void retire(@NonNull Popup popup) {
        popup.cancelAnimators();
        mPopups.remove(popup.pointerId);
    }

    /** Whether a finger currently has a popup up, ignoring the ones fading out. */
    public boolean hasActivePopups() {
        return mPopups.size() > 0;
    }

    /** How far the veil has come up, 0 when it is not there at all. */
    @androidx.annotation.VisibleForTesting
    float dimProgress() {
        return mDimProgress;
    }

    private boolean hasAnything() {
        return mPopups.size() > 0 || !mExiting.isEmpty();
    }

    // ------------------------------------------------------------------ the veil

    private void raiseDim() {
        animateDim(1f);
    }

    private void lowerDimIfIdle() {
        if (!hasAnything()) animateDim(0f);
    }

    private void animateDim(float to) {
        if (mDimAnimator != null) {
            mDimAnimator.cancel();
            mDimAnimator = null;
        }
        if (mDimProgress == to) return;
        ValueAnimator animator = ValueAnimator.ofFloat(mDimProgress, to);
        animator.setDuration(duration(DIM_MS));
        animator.addUpdateListener(a -> {
            mDimProgress = (Float) a.getAnimatedValue();
            invalidate();
        });
        animator.addListener(new AnimatorListenerAdapter() {
            @Override public void onAnimationEnd(Animator a) {
                mDimProgress = to;
                mDimAnimator = null;
                invalidate();
            }
        });
        mDimAnimator = animator;
        animator.start();
    }

    // ------------------------------------------------------------------ drawing

    @Override
    protected void onDraw(Canvas canvas) {
        if (mDimProgress > 0f) {
            mDimPaint.setColor(mPalette.dim);
            mDimPaint.setAlpha(Math.round(Color.alpha(mPalette.dim) * mDimProgress));
            if (mVeilBounds.isEmpty()) {
                canvas.drawRect(0f, 0f, getWidth(), getHeight(), mDimPaint);
            } else {
                canvas.drawRect(mVeilBounds.left, mVeilBounds.top, mVeilBounds.right,
                    Math.max(mVeilBounds.bottom, getHeight()), mDimPaint);
                drawVeilFade(canvas);
            }
        }
        for (int i = 0; i < mPopups.size(); i++) drawPopup(canvas, mPopups.valueAt(i));
        for (int i = 0; i < mExiting.size(); i++) drawPopup(canvas, mExiting.get(i));
    }

    /** The veil's soft upper edge: full strength at the keyboard's top, gone one fade-height above. */
    private void drawVeilFade(@NonNull Canvas canvas) {
        float fade = VEIL_FADE_DP * density();
        float top = mVeilBounds.top;
        if (mVeilFade == null || mVeilFadeTop != top || mVeilFadeColor != mPalette.dim) {
            int solid = mPalette.dim;
            int clear = solid & 0x00FFFFFF;
            mVeilFade = new android.graphics.LinearGradient(0f, top - fade, 0f, top, clear, solid,
                android.graphics.Shader.TileMode.CLAMP);
            mVeilFadeTop = top;
            mVeilFadeColor = solid;
            mVeilFadePaint.setShader(mVeilFade);
        }
        mVeilFadePaint.setAlpha(Math.round(255 * mDimProgress));
        canvas.drawRect(mVeilBounds.left, top - fade, mVeilBounds.right, top, mVeilFadePaint);
    }

    private void drawPopup(@NonNull Canvas canvas, @NonNull Popup popup) {
        if (popup.metrics == null) return;
        float alpha = popup.alpha();
        if (alpha <= 0f) return;
        float density = density();
        float scale = popup.scale();
        int save = canvas.save();
        canvas.scale(scale, scale, popup.anchorX, popup.anchorY);

        drawHalo(canvas, popup, alpha);
        drawCentreGlyph(canvas, popup, alpha, density);
        if (popup.modifier) drawSubLabel(canvas, popup, alpha, density);
        drawRing(canvas, popup, alpha, density);

        canvas.restoreToCount(save);
    }

    private void drawHalo(@NonNull Canvas canvas, @NonNull Popup popup, float alpha) {
        RadialGradient shader = haloShader(popup.haloRadiusPx);
        if (shader == null) return;
        mHaloPaint.setShader(shader);
        mHaloPaint.setAlpha(Math.round(255f * alpha));
        int save = canvas.save();
        canvas.translate(popup.anchorX, popup.anchorY);
        canvas.drawCircle(0f, 0f, popup.haloRadiusPx, mHaloPaint);
        canvas.restoreToCount(save);
        mHaloPaint.setShader(null);
    }

    @Nullable
    private RadialGradient haloShader(float radiusPx) {
        int key = Math.round(radiusPx);
        if (key <= 0) return null;
        RadialGradient cached = mHaloShaders.get(key);
        if (cached != null) return cached;
        int inner = KeyPopupPalette.withAlpha(mPalette.primary, HALO_INNER_ALPHA * mPalette.glow);
        int mid = KeyPopupPalette.withAlpha(mPalette.primary, HALO_MID_ALPHA * mPalette.glow);
        int outer = ColorUtils.setAlphaComponent(mPalette.primary, 0);
        RadialGradient shader = new RadialGradient(0f, 0f, key,
            new int[] {inner, mid, outer}, new float[] {0f, 0.34f, 0.70f}, Shader.TileMode.CLAMP);
        // Four label tiers is the whole range; a layout swap or a palette change clears it.
        if (mHaloShaders.size() > 8) mHaloShaders.clear();
        mHaloShaders.put(key, shader);
        return shader;
    }

    private void drawCentreGlyph(@NonNull Canvas canvas, @NonNull Popup popup, float alpha,
                                 float density) {
        Paint paint = mGlyphPaint;
        paint.setTypeface(faceFor(popup.labelKeyFont, popup.metrics.monospace && !usesLabelFont(popup.label), popup.metrics.weight));
        paint.setTextSize(popup.metrics.glyphSizePx);
        paint.setStrokeWidth(popup.metrics.strokeWidthPx);
        int ink = popup.slot >= 0 ? mPalette.primary : mPalette.glyphStroke;
        paint.setColor(ink);
        paint.setAlpha(Math.round(Color.alpha(ink) * alpha));
        paint.setShadowLayer(GLYPH_GLOW_RADIUS_DP * density, 0f, 0f,
            KeyPopupPalette.withAlpha(mPalette.primary, GLYPH_GLOW_ALPHA * mPalette.glow * alpha));
        float baseline = popup.anchorY - (paint.ascent() + paint.descent()) / 2f;
        canvas.drawText(popup.label, popup.anchorX, baseline, paint);
        paint.clearShadowLayer();
    }

    private void drawSubLabel(@NonNull Canvas canvas, @NonNull Popup popup, float alpha,
                              float density) {
        Paint paint = mSubPaint;
        paint.setTypeface(faceFor(false, true, 500));
        paint.setTextSize(SUB_LABEL_SIZE_DP * density);
        int ink = popup.latched ? mPalette.primary : mPalette.subLabel;
        paint.setColor(ink);
        paint.setAlpha(Math.round(Color.alpha(ink) * alpha));
        float top = popup.anchorY + popup.metrics.glyphSizePx * GLYPH_HALF_LINE
            + SUB_LABEL_GAP_DP * density;
        canvas.drawText(popup.latched ? "LATCH" : "HELD", popup.anchorX, top - paint.ascent(), paint);
    }

    private void drawRing(@NonNull Canvas canvas, @NonNull Popup popup, float alpha,
                          float density) {
        String[] labels = popup.ringLabels;
        float travel = popup.ringProgress(mRingInterpolator);
        float tint = Math.min(1f, popup.rawRingProgress() / RING_COLOR_FRACTION);
        for (int slot = 1; slot < labels.length; slot++) {
            String label = labels[slot];
            if (label == null) continue;
            boolean wasTarget = slot == popup.previousSlot;
            boolean isTarget = slot == popup.slot;
            float radius = lerp(popup.orbit[slot][wasTarget ? 1 : 0],
                popup.orbit[slot][isTarget ? 1 : 0], travel);
            float scale = lerp(wasTarget ? KeyPopupGeometry.TARGET_SCALE : 1f,
                isTarget ? KeyPopupGeometry.TARGET_SCALE : 1f, travel);
            float glyphAlpha = lerp(slotAlpha(popup.previousSlot, slot, wasTarget),
                slotAlpha(popup.slot, slot, isTarget), tint) * alpha;
            if (glyphAlpha <= 0f) continue;
            int ink = isTarget ? mPalette.primary : mPalette.ringIdle;
            Paint paint = mRingPaint;
            paint.setTypeface(faceFor(popup.ringKeyFont[slot], !usesLabelFont(label), 500));
            paint.setTextSize(KeyPopupGeometry.ringGlyphSizePx(label, density) * scale);
            paint.setColor(ink);
            paint.setAlpha(Math.round(Color.alpha(ink) * glyphAlpha));
            if (isTarget)
                paint.setShadowLayer(TARGET_GLOW_RADIUS_DP * density, 0f, 0f,
                    KeyPopupPalette.withAlpha(mPalette.primary,
                        TARGET_GLOW_ALPHA * mPalette.glow * glyphAlpha));
            float cx = popup.anchorX + KeyPopupGeometry.DIRECTION_X[slot] * radius;
            float cy = popup.anchorY + KeyPopupGeometry.DIRECTION_Y[slot] * radius;
            canvas.drawText(label, cx, cy - (paint.ascent() + paint.descent()) / 2f, paint);
            if (isTarget) paint.clearShadowLayer();
        }
    }

    /** An alternate is full strength until a direction is targeted; then only the target is. */
    private static float slotAlpha(int targetSlot, int slot, boolean isTarget) {
        if (targetSlot < 0) return 1f;
        return isTarget ? 1f : KeyPopupGeometry.NON_TARGET_ALPHA;
    }

    /**
     * The face one glyph is drawn in. Cached: at most six combinations exist, and resolving a
     * weighted face on every frame of a ring animation is allocation the draw path does not need.
     */
    /**
     * Whether a label has to be drawn with the keyboard's own label font rather than the plain
     * monospace face. Launcher tool slots (the space bar's window and session swipes, the palette)
     * are Nerd Font glyphs in the private-use planes; the caps draw them with the label font, and
     * monospace has no such glyphs, so they came out as boxes in the ring.
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

    /** Per-pointer state. Geometry is recomputed when something changes, never per frame. */
    private final class Popup {
        final int pointerId;
        final RectF keyBounds;
        final float clampLeft;
        final float clampRight;
        final String[] ringLabels;
        final boolean[] ringKeyFont;
        final boolean modifier;

        String label = "";
        boolean labelKeyFont;
        int slot = -1;
        int previousSlot = -1;
        boolean latched;

        KeyPopupGeometry.Metrics metrics;
        float anchorX;
        float anchorY;
        float haloRadiusPx;
        /** Per slot: [0] at rest, [1] when it is the target. */
        final float[][] orbit = new float[9][2];

        float enter;
        float exit;
        float ring = 1f;
        boolean exiting;
        @Nullable ValueAnimator enterAnimator;
        @Nullable ValueAnimator ringAnimator;
        @Nullable ValueAnimator exitAnimator;

        Popup(int pointerId, RectF keyBounds, float clampLeft, float clampRight,
              String[] ringLabels, boolean[] ringKeyFont, boolean modifier) {
            this.pointerId = pointerId;
            this.keyBounds = keyBounds;
            this.clampLeft = clampLeft;
            this.clampRight = clampRight;
            this.ringLabels = ringLabels;
            this.ringKeyFont = ringKeyFont;
            this.modifier = modifier;
        }

        void layout() {
            float density = density();
            metrics = KeyPopupGeometry.metricsFor(label, density);
            boolean wideRing = KeyPopupGeometry.hasWideRing(ringLabels);
            float side = KeyPopupGeometry.sideExtentPx(metrics, mRingRadiusPx, wideRing, density);
            float below = KeyPopupGeometry.belowPx(metrics, mRingRadiusPx, modifier, density);
            float above = KeyPopupGeometry.abovePx(metrics, mRingRadiusPx, density);
            anchorX = KeyPopupGeometry.anchorX(keyBounds.centerX(), side, clampLeft, clampRight,
                density);
            anchorY = KeyPopupGeometry.anchorY(keyBounds.top, below, above, 0f, density);
            haloRadiusPx = KeyPopupGeometry.haloRadiusPx(metrics, mRingRadiusPx);
            for (int i = 1; i < ringLabels.length; i++) {
                if (ringLabels[i] == null) continue;
                orbit[i][0] = KeyPopupGeometry.orbitRadiusPx(metrics, mRingRadiusPx, ringLabels[i],
                    false, density);
                orbit[i][1] = KeyPopupGeometry.orbitRadiusPx(metrics, mRingRadiusPx, ringLabels[i],
                    true, density);
            }
        }

        void startEnter() {
            enter = 0f;
            enterAnimator = run(enterAnimator, ENTER_MS, value -> enter = value, () -> enter = 1f);
        }

        void startRing() {
            ring = 0f;
            ringAnimator = run(ringAnimator, RING_MS, value -> ring = value, () -> {
                ring = 1f;
                previousSlot = slot;
            });
        }

        void startExit() {
            exiting = true;
            exit = 0f;
            exitAnimator = run(exitAnimator, EXIT_MS, value -> exit = value, () -> {
                exit = 1f;
                mExiting.remove(Popup.this);
                lowerDimIfIdle();
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
            if (ringAnimator != null) ringAnimator.cancel();
            if (exitAnimator != null) exitAnimator.cancel();
            enterAnimator = ringAnimator = exitAnimator = null;
        }

        float alpha() {
            if (exiting) return EXIT_FROM_ALPHA * (1f - mExitInterpolator.getInterpolation(exit));
            return clamp01(mEnterInterpolator.getInterpolation(enter));
        }

        float scale() {
            if (exiting)
                return lerp(1f, EXIT_TO_SCALE, mExitInterpolator.getInterpolation(exit));
            return lerp(ENTER_FROM_SCALE, 1f, mEnterInterpolator.getInterpolation(enter));
        }

        float rawRingProgress() {
            return ring;
        }

        float ringProgress(Interpolator interpolator) {
            return interpolator.getInterpolation(ring);
        }

        /** The box this popup can paint into, grown for the overshoot, the swell and the glow. */
        void bounds(Rect out) {
            float reach = Math.max(haloRadiusPx, mRingRadiusPx + metrics.halfWidthPx)
                + 40f * density();
            reach *= EXIT_TO_SCALE;
            out.set((int) Math.floor(anchorX - reach), (int) Math.floor(anchorY - reach),
                (int) Math.ceil(anchorX + reach), (int) Math.ceil(anchorY + reach));
        }
    }

    private interface FloatSetter {
        void set(float value);
    }
}
