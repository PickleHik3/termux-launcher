package com.termux.app.chrome;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

/**
 * How glass surfaces move, as values: a press, an arrival and a departure, and the scrim behind a
 * modal one. Two profiles exist. {@link #CLASSIC} is what the app has always done, so a phone on
 * the default look moves exactly as before; {@link #MIST} is Obsidian-Music's motion (Apache-2.0,
 * decompiled from 2.5.1; see project-docs/mist-preset/OBSIDIAN-VALUES.md).
 *
 * <p>Pure values and arithmetic, no {@code Context}. {@link GlassMotionPlayer} is the one place
 * that turns them into animators.</p>
 */
public final class GlassMotion {

    /**
     * A damped spring on a 0 to 1 step, mass 1 — the same two numbers Compose and
     * {@code androidx.dynamicanimation.SpringForce} take (damping ratio, stiffness), so an
     * Obsidian spring is copied across unchanged. Solved analytically rather than stepped, which
     * is what lets a plain {@code ValueAnimator} play it without the dynamicanimation dependency
     * (the project does not carry it).
     */
    public static final class Spring {
        public final float dampingRatio;
        public final float stiffness;

        public Spring(float dampingRatio, float stiffness) {
            this.dampingRatio = dampingRatio;
            this.stiffness = stiffness;
        }

        /** Position at {@code seconds}: 0 at the start, settling on 1, overshooting when under-damped. */
        public float valueAt(float seconds) {
            if (seconds <= 0f) return 0f;
            double omega = Math.sqrt(stiffness);
            double zeta = dampingRatio;
            double t = seconds;
            if (zeta < 1d) {
                double damped = omega * Math.sqrt(1d - zeta * zeta);
                double decay = Math.exp(-zeta * omega * t);
                return (float) (1d - decay * (Math.cos(damped * t)
                    + (zeta * omega / damped) * Math.sin(damped * t)));
            }
            double decay = Math.exp(-omega * t);
            return (float) (1d - decay * (1d + omega * t));
        }

        /** How long until the spring stays within 0.1% of 1, in ms; bounded so a slow one ends. */
        public long settleMillis() {
            float step = 0.004f;
            float lastOutside = 0f;
            for (float t = 0f; t < 3f; t += step)
                if (Math.abs(valueAt(t) - 1f) > 0.001f) lastOutside = t;
            return Math.round((lastOutside + step) * 1000f);
        }

        @Override public boolean equals(@Nullable Object other) {
            return other instanceof Spring && ((Spring) other).dampingRatio == dampingRatio
                && ((Spring) other).stiffness == stiffness;
        }

        @Override public int hashCode() {
            return Float.floatToIntBits(dampingRatio) * 31 + Float.floatToIntBits(stiffness);
        }
    }

    /** Scale a glass tile presses to; 1 means it has no press feedback (the classic case). */
    public final float pressScale;
    @NonNull public final Spring pressSpring;
    @NonNull public final Spring releaseSpring;

    /** Where an arriving surface's scale starts, settling on 1. */
    public final float enterScaleFrom;
    /** Null while the arrival is a fixed-duration tween; set, the scale settles on this spring. */
    @Nullable public final Spring enterScaleSpring;
    /** The blur an arriving surface starts under, in dp, sharpening to 0; 0 for none. */
    public final float enterBlurFromDp;
    @Nullable public final Spring enterBlurSpring;
    public final long enterAlphaMs;
    public final long exitAlphaMs;
    /** The scale a leaving surface shrinks to while it fades; 1 fades in place. */
    public final float exitScaleTo;
    /** The scrim behind a modal glass surface, 0..1. */
    public final float backdropDim;
    /** True for eased curves (fast-out-slow-in / fast-out-linear-in) rather than the classic ones. */
    public final boolean obsidianCurves;

    private GlassMotion(float pressScale, @NonNull Spring pressSpring, @NonNull Spring releaseSpring,
                        float enterScaleFrom, @Nullable Spring enterScaleSpring,
                        float enterBlurFromDp, @Nullable Spring enterBlurSpring,
                        long enterAlphaMs, long exitAlphaMs, float exitScaleTo, float backdropDim,
                        boolean obsidianCurves) {
        this.pressScale = pressScale;
        this.pressSpring = pressSpring;
        this.releaseSpring = releaseSpring;
        this.enterScaleFrom = enterScaleFrom;
        this.enterScaleSpring = enterScaleSpring;
        this.enterBlurFromDp = enterBlurFromDp;
        this.enterBlurSpring = enterBlurSpring;
        this.enterAlphaMs = enterAlphaMs;
        this.exitAlphaMs = exitAlphaMs;
        this.exitScaleTo = exitScaleTo;
        this.backdropDim = backdropDim;
        this.obsidianCurves = obsidianCurves;
    }

    /**
     * Today's motion, from the one glass surface that animates in and out: the terminal sheet's
     * card (170 ms in from 0.94, 110 ms out to 0.94) and its drawer's 71/255 scrim. There is no
     * press feedback and no blur on arrival, so those are neutral (scale 1, blur 0); the springs
     * are then never played and only fill the fields.
     */
    public static final GlassMotion CLASSIC = new GlassMotion(
        1f, new Spring(0.75f, 400f), new Spring(0.55f, 300f),
        0.94f, null, 0f, null,
        170L, 110L, 0.94f, 71f / 255f, false);

    /** Obsidian-Music: press 0.92, dialog enter scale 0.9 to 1 and blur 20 to 0, scrim 0.55. */
    public static final GlassMotion MIST = new GlassMotion(
        0.92f, new Spring(0.75f, 400f), new Spring(0.55f, 300f),
        0.9f, new Spring(0.8f, 400f), 20f, new Spring(0.82f, 400f),
        280L, 200L, 1f, 0.55f, true);

    /** The profile a preset names; an unknown id is classic, so a newer build's value degrades. */
    @NonNull
    public static GlassMotion forId(@Nullable String id) {
        return TERMUX_APP.GLASS_MOTION_MIST.equals(id) ? MIST : CLASSIC;
    }

    /** True when arrivals settle on springs instead of the fixed-duration tween. */
    public boolean springy() {
        return enterScaleSpring != null;
    }
}
