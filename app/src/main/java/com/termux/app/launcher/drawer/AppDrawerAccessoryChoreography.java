package com.termux.app.launcher.drawer;

import androidx.annotation.NonNull;

import static com.termux.app.launcher.drawer.AppDrawerTransitionGeometry.clamp01;
import static com.termux.app.launcher.drawer.AppDrawerTransitionGeometry.lerp;
import static com.termux.app.launcher.drawer.AppDrawerTransitionGeometry.ramp;

/**
 * Pure choreography for the two accessory bands the drawer plane passes over — the extra-keys row
 * and the in-app keyboard — for both dock styles.
 *
 * <p>Nothing here relayouts anything. While the drawer is engaged the accessory stack's layout is
 * frozen (the flush-padding solver plus {@code updateSize()} means a {@code SIGWINCH} per frame),
 * so every value returned is a {@code translationY}, a clip inset or an alpha applied to views
 * that keep their measured bounds. The band rectangles and the dock↔keyboard gap are captured
 * once at drag begin and passed back in on every frame; re-measuring per frame is what reopens
 * the accessory feedback loop.
 *
 * <p>The two styles are genuinely different recipes, not one recipe with different constants:
 *
 * <ul>
 *   <li><b>Default</b> — extra keys and keyboard are one entity. They share a single
 *       {@code translationY} of their combined height and fade out together. Sliding them apart
 *       would show the seam between two surfaces that read as one slab.
 *   <li><b>Rounded</b> — the keyboard is a floating capsule with a visible gap under the dock, so
 *       the gap is the thing that must not move. Its top is pinned to
 *       {@code planeBottom + capturedGap} and the capsule shrinks from the top as the plane's
 *       bottom edge descends; the extra keys ride the same clip. Nothing translates, which is why
 *       the capsule's bottom padding survives by construction, and the fade is held back until
 *       the plane has nearly swallowed it.
 * </ul>
 */
public final class AppDrawerAccessoryChoreography {

    /** Default style: the combined slide-out. */
    private static final float DEFAULT_SLIDE_START = 0.05f;
    private static final float DEFAULT_SLIDE_END = 0.55f;
    private static final float DEFAULT_FADE_START = 0.35f;
    private static final float DEFAULT_FADE_END = 0.60f;

    /** Rounded style: the capsule is still there, shrinking, long after the default slab has gone. */
    private static final float ROUNDED_FADE_START = 0.60f;
    private static final float ROUNDED_FADE_END = 0.92f;

    private AppDrawerAccessoryChoreography() {}

    /** A measured band, in host coordinates, captured once at drag begin. */
    public static final class Band {

        public final float topPx;
        public final float heightPx;

        public Band(float topPx, float heightPx) {
            this.topPx = topPx;
            this.heightPx = Math.max(0f, heightPx);
        }

        public float bottomPx() {
            return topPx + heightPx;
        }
    }

    /** Immutable per-band output for one frame. */
    public static final class Result {

        public final float extraKeysTranslationY;
        /** Pixels clipped off the top of the band; 0 means the band draws whole. */
        public final float extraKeysClipTopPx;
        /** Absolute top of the still-visible part of the band. */
        public final float extraKeysVisibleTopPx;
        /** Never negative. */
        public final float extraKeysVisibleHeightPx;
        public final float extraKeysAlpha;

        public final float keyboardTranslationY;
        public final float keyboardClipTopPx;
        public final float keyboardVisibleTopPx;
        /** Never negative. */
        public final float keyboardVisibleHeightPx;
        public final float keyboardAlpha;

        Result(float extraKeysTranslationY, float extraKeysClipTopPx, float extraKeysVisibleTopPx,
               float extraKeysVisibleHeightPx, float extraKeysAlpha,
               float keyboardTranslationY, float keyboardClipTopPx, float keyboardVisibleTopPx,
               float keyboardVisibleHeightPx, float keyboardAlpha) {
            this.extraKeysTranslationY = extraKeysTranslationY;
            this.extraKeysClipTopPx = extraKeysClipTopPx;
            this.extraKeysVisibleTopPx = extraKeysVisibleTopPx;
            this.extraKeysVisibleHeightPx = extraKeysVisibleHeightPx;
            this.extraKeysAlpha = extraKeysAlpha;
            this.keyboardTranslationY = keyboardTranslationY;
            this.keyboardClipTopPx = keyboardClipTopPx;
            this.keyboardVisibleTopPx = keyboardVisibleTopPx;
            this.keyboardVisibleHeightPx = keyboardVisibleHeightPx;
            this.keyboardAlpha = keyboardAlpha;
        }
    }

    /**
     * @param roundedDockStyle {@code TermuxActivity.isRoundedDockStyle()}
     * @param progress         0 = dock, 1 = full drawer
     * @param extraKeys        measured extra-keys band, captured at drag begin
     * @param keyboard         measured in-app keyboard band, captured at drag begin
     * @param capturedGapPx    keyboard top minus dock glass bottom, captured at drag begin
     * @param planeBottomPx    the plane's current bottom edge (rounded style only)
     */
    @NonNull
    public static Result resolve(boolean roundedDockStyle, float progress,
                                 @NonNull Band extraKeys, @NonNull Band keyboard,
                                 float capturedGapPx, float planeBottomPx) {
        float p = clamp01(progress);
        return roundedDockStyle
            ? resolveRounded(p, extraKeys, keyboard, capturedGapPx, planeBottomPx)
            : resolveDefault(p, extraKeys, keyboard);
    }

    /**
     * Blends only the keyboard band back toward its untouched state as drawer search is revealed.
     *
     * <p>The reveal is a second transition running independently of the drawer's own: the drawer is
     * already open, its progress pinned at 1, when a keystroke brings the keyboard up
     * <em>through</em> the plane. The keyboard has to come back without {@code p} moving at all;
     * terminal extra keys do not belong to app search and remain at the drawer choreography's
     * original result. This is temporary render state only — no toolbar preference is read or
     * written here — and {@code k == 0} restores that original result exactly.
     *
     * <p>Two endpoints matter. At {@code k == 0} the result is the input bit for bit — every term is
     * a multiply by one or an add of zero — which is what lets the controller pipe every frame
     * through here unconditionally rather than branching on whether a keyboard exists, and is why
     * the four cases pinning {@link #resolve} still describe what the drawer does. At {@code k == 1}
     * the keyboard is untouched again: nothing translated, nothing clipped, full alpha, and its
     * visible top back at the band's captured top. Every extra-keys field remains the input field
     * bit for bit at every reveal value.
     *
     * <p>The visible <em>height</em> is the clipped pixels handed back rather than a recomputed
     * rectangle, because {@link Result} floors it at zero and a band the rounded style has swallowed
     * whole — which the extra-keys row is, for most of the transition — no longer carries its own
     * height. Giving the clip back therefore describes a region at least as tall as the band, which
     * is the same instruction: draw all of it.
     *
     * @param reveal 0 = no keyboard, 1 = keyboard fully revealed
     */
    @NonNull
    public static Result blendTowardIdentity(@NonNull Result result, float reveal) {
        float k = clamp01(reveal);
        float keep = 1f - k;
        // A clip is a band's top edge pushed down by exactly that many pixels, so giving the pixels
        // back moves the visible top up and the visible height down by the same amount.
        return new Result(
            result.extraKeysTranslationY,
            result.extraKeysClipTopPx,
            result.extraKeysVisibleTopPx,
            result.extraKeysVisibleHeightPx,
            result.extraKeysAlpha,
            result.keyboardTranslationY * keep,
            result.keyboardClipTopPx * keep,
            result.keyboardVisibleTopPx - (result.keyboardClipTopPx * k),
            result.keyboardVisibleHeightPx + (result.keyboardClipTopPx * k),
            lerp(result.keyboardAlpha, 1f, k));
    }

    private static Result resolveDefault(float p, Band extraKeys, Band keyboard) {
        float translationY = (extraKeys.heightPx + keyboard.heightPx)
            * ramp(p, DEFAULT_SLIDE_START, DEFAULT_SLIDE_END);
        float alpha = 1f - ramp(p, DEFAULT_FADE_START, DEFAULT_FADE_END);
        return new Result(
            translationY, 0f, extraKeys.topPx, extraKeys.heightPx, alpha,
            translationY, 0f, keyboard.topPx, keyboard.heightPx, alpha);
    }

    private static Result resolveRounded(float p, Band extraKeys, Band keyboard,
                                         float capturedGapPx, float planeBottomPx) {
        // The gap is the invariant: the keyboard's top follows the plane's bottom edge exactly one
        // captured gap behind it, which is why this is computed as an absolute pin and not as a
        // fraction of p. At p = 0 the plane bottom is the dock bottom, so the shift is exactly 0
        // and the bands are untouched.
        float pinTopPx = planeBottomPx + capturedGapPx;
        float shiftPx = Math.max(0f, pinTopPx - keyboard.topPx);

        float keyboardVisibleTop = keyboard.topPx + shiftPx;
        float keyboardVisibleHeight = Math.max(0f, keyboard.bottomPx() - keyboardVisibleTop);

        // "Rides the same clip": the extra keys lose the same number of pixels off their top, so
        // the two bands shrink in step instead of the row hanging below a shrunken capsule.
        float extraVisibleTop = extraKeys.topPx + shiftPx;
        float extraVisibleHeight = Math.max(0f, extraKeys.bottomPx() - extraVisibleTop);

        float alpha = 1f - ramp(p, ROUNDED_FADE_START, ROUNDED_FADE_END);
        return new Result(
            0f, shiftPx, extraVisibleTop, extraVisibleHeight, alpha,
            0f, shiftPx, keyboardVisibleTop, keyboardVisibleHeight, alpha);
    }
}
