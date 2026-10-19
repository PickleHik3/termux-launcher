package com.termux.app.launcher.home;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * How a Home page moves while a swipe turns it (Settings › Home › Page transition). Home shows
 * one page at a time: the swipe carries the page on screen away, and once the turn commits the
 * next page comes in from the side the finger was heading for. Every effect is a pure function
 * of where that page stands, so the drag, the settle, the page coming in and the Settings
 * preview all draw the same curve.
 *
 * <p>Only view properties change — translation, scale, alpha, the turn about the vertical axis
 * — so a frame of the motion is a property change on the page's render nodes: no layout, no
 * bitmap, and glass on the page is not sampled again, since it is aimed through the wall page's
 * transform only ({@code GlassAnchor#place}), never through the page content's.
 *
 * <p>The seam into the Terminal is not a Home turn and keeps its slide; so does the rubber band
 * at either end of the pages, where there is no page to turn to.
 */
public enum HomePageTransition {
    /** The page slides with the finger: the motion Home always had. */
    SLIDE("slide"),
    /** The page leaving forward sinks back into the screen; the next one slides over it. */
    DEPTH("depth"),
    /** The page fades as it drifts a little way with the finger. */
    FADE("fade"),
    /** The pages turn about their shared edge, like the faces of a box. */
    CAROUSEL("carousel"),
    /** The page slides off the top of a stack, and the next one rises from beneath it. */
    STACK("stack");

    /** The value stored for this effect. */
    @NonNull public final String value;

    HomePageTransition(@NonNull String value) {
        this.value = value;
    }

    public static final HomePageTransition DEFAULT = SLIDE;

    /** How far, as a share of the page's width, Fade carries the page with the finger. */
    static final float FADE_DRIFT = 0.2f;
    /** How much smaller the page leaving forward is when Depth has taken it all the way back. */
    static final float DEPTH_SCALE_DROP = 0.25f;
    /** How much smaller the page waiting under the stack is. */
    static final float STACK_SCALE_DROP = 0.08f;
    /** How far Carousel turns a page that is a whole width away, in degrees. */
    static final float CAROUSEL_DEGREES = 60f;

    /**
     * The curve a page settles on once the finger lets go (and the Settings preview plays): the
     * same ease in and out the page slide always had.
     */
    @NonNull
    public static android.animation.TimeInterpolator settleInterpolator() {
        return new android.view.animation.AccelerateDecelerateInterpolator();
    }

    /** The effect a stored value names; anything unknown is the default. */
    @NonNull
    public static HomePageTransition fromValue(@Nullable String value) {
        for (HomePageTransition effect : values()) if (effect.value.equals(value)) return effect;
        return DEFAULT;
    }

    /** What a page's view properties are at one point of the motion. */
    public static final class Transform {
        public float translationX;
        public float scale = 1f;
        public float alpha = 1f;
        public float rotationY;
        /** Where the page scales and turns about, as a share of its width from its left edge. */
        public float pivotXFraction = 0.5f;

        void reset() {
            translationX = 0f;
            scale = 1f;
            alpha = 1f;
            rotationY = 0f;
            pivotXFraction = 0.5f;
        }
    }

    /**
     * Fills {@code out} for a page standing {@code position} page widths off its rest (negative:
     * to the left, as a page leaving forward goes; positive: to the right) in a pane
     * {@code widthPx} wide. {@code turning} is whether there is a page to turn to that way; a page
     * pulled against either end only slides.
     */
    public void transform(float position, float widthPx, boolean turning, @NonNull Transform out) {
        out.reset();
        float p = Math.max(-1f, Math.min(1f, position));
        float away = Math.abs(p);
        if (!turning || this == SLIDE) {
            out.translationX = position * widthPx;
            return;
        }
        switch (this) {
            case FADE:
                out.translationX = p * widthPx * FADE_DRIFT;
                out.alpha = 1f - away;
                break;
            case DEPTH:
                if (p < 0f) {
                    out.scale = 1f - DEPTH_SCALE_DROP * away;
                    out.alpha = 1f - away;
                } else {
                    out.translationX = p * widthPx;
                }
                break;
            case STACK:
                if (p > 0f) {
                    out.scale = 1f - STACK_SCALE_DROP * away;
                    out.alpha = 1f - away;
                } else {
                    out.translationX = p * widthPx;
                }
                break;
            case CAROUSEL:
                out.translationX = p * widthPx;
                out.rotationY = p * CAROUSEL_DEGREES;
                // The edge it shares with the page beside it stays put on the box.
                out.pivotXFraction = p < 0f ? 1f : 0f;
                break;
            default:
                out.translationX = position * widthPx;
                break;
        }
    }
}
