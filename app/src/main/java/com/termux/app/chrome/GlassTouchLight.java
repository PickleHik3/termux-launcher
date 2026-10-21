package com.termux.app.chrome;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.content.Context;
import android.view.animation.LinearInterpolator;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.termux.app.ReducedMotion;

import java.util.function.BooleanSupplier;

/**
 * A finger resting on glass, as light: while it is down the glass under it lights up (the rim
 * nearest it catches more light, a faint glow scatters under the fingertip, the picture is drawn a
 * tenth of the way toward it; all of that is {@link GlassRefraction.Program#setTouch}'s), fading
 * in quickly and out gently. Nothing moves; only the light's strength animates.
 *
 * <p>The light's strength rises 0 → 1 over {@value #RISE_MS} ms (decelerating) on {@link #down},
 * holds while the finger rests, and falls back to 0 over {@value #FALL_MS} ms (easing in and out)
 * on {@link #up}, after which the sink is {@linkplain Sink#clear cleared}. A move restates the
 * position at the strength it has. A new down during the fall rises again from the strength the
 * fall had reached, and a release during the rise falls from where the rise had got, each over
 * the share of its time that distance needs, so the light never jumps. One animator, reused.</p>
 *
 * <p>When motion is stopped (the system's animations off, reduced motion, or the caller's rule
 * for the Fancier Glass motions: lazy mode, battery saver) a down does nothing at all and the sink
 * is never called. The glass shows its resting look.</p>
 */
public final class GlassTouchLight {

    /** Where the light goes: a surface's program, in that surface's own coordinates. */
    public interface Sink {
        /** A finger at {@code (x, y)}, its light reaching {@code radiusPx}, at {@code intensity} 0..1. */
        void touch(float x, float y, float radiusPx, float intensity);

        /** No finger: the resting look. */
        void clear();
    }

    /** How far the light reaches from the fingertip: about a fingertip and a half. */
    public static final float RADIUS_DP = 56f;
    /** The rise to full strength from nothing: quick, so the glass answers the finger at once. */
    public static final long RISE_MS = 110L;
    /** The fall to nothing from full strength: gentle, so the light lingers as the finger leaves. */
    public static final long FALL_MS = 240L;

    /** Plays one linear 0..1 fraction over a duration at a time; a new run replaces the last. */
    @VisibleForTesting
    interface Ticker {
        void start(long durationMs, @NonNull Frames frames);

        /** Stops the run without its end. */
        void cancel();
    }

    /** One run's frames: the linear fraction on each, then its end unless cancelled. */
    @VisibleForTesting
    interface Frames {
        void onFraction(float fraction);

        void onEnd();
    }

    @NonNull private final Sink mSink;
    private final float mRadiusPx;
    @NonNull private final BooleanSupplier mAllowed;
    @NonNull private final Ticker mTicker;

    /** A finger is down. */
    private boolean mHeld;
    /** The sink holds a touch: it was handed one since its last clear. */
    private boolean mLit;
    private float mX, mY;
    /** The light's strength now, 0..1. */
    private float mValue;

    /**
     * @param motionAllowed the rule that stops the Fancier Glass motions (lazy mode, battery saver,
     *     Fancier Glass itself off), asked on every down; the system's animator setting and
     *     reduced motion are asked here as well
     */
    public GlassTouchLight(@NonNull Context context, @NonNull Sink sink,
                           @NonNull BooleanSupplier motionAllowed) {
        this(sink, RADIUS_DP * context.getResources().getDisplayMetrics().density,
            gate(context.getApplicationContext() != null ? context.getApplicationContext() : context,
                motionAllowed),
            new AnimatorTicker());
    }

    @VisibleForTesting
    GlassTouchLight(@NonNull Sink sink, float radiusPx, @NonNull BooleanSupplier allowed,
                    @NonNull Ticker ticker) {
        mSink = sink;
        mRadiusPx = radiusPx;
        mAllowed = allowed;
        mTicker = ticker;
    }

    @NonNull
    private static BooleanSupplier gate(@NonNull Context context,
                                        @NonNull BooleanSupplier motionAllowed) {
        return () -> allowed(ValueAnimator.areAnimatorsEnabled(),
            ReducedMotion.isEnabled(context), motionAllowed.getAsBoolean());
    }

    /** Whether the light plays: animations on, no reduced motion, and the motions' rule allows it. */
    @VisibleForTesting
    static boolean allowed(boolean animatorsEnabled, boolean reducedMotion, boolean motionAllowed) {
        return animatorsEnabled && !reducedMotion && motionAllowed;
    }

    /** A finger lands at {@code (x, y)} in the sink's coordinates: the light rises under it. */
    public void down(float x, float y) {
        if (!mAllowed.getAsBoolean()) {
            mHeld = false;
            dropNow();
            return;
        }
        mHeld = true;
        mX = x;
        mY = y;
        final float from = mValue;
        if (from >= 1f) {
            mTicker.cancel();
            emit();
            return;
        }
        // A finger back during the fall lights from where the fall had got, at once in place.
        if (mLit) emit();
        mTicker.start(share(RISE_MS, 1f - from), new Frames() {
            @Override public void onFraction(float t) {
                mValue = from + (1f - from) * decelerate(t);
                emit();
            }

            @Override public void onEnd() {
                mValue = 1f;
                emit();
            }
        });
    }

    /** The finger moved to {@code (x, y)}: the light follows it at the strength it has. */
    public void move(float x, float y) {
        if (!mHeld) return;
        if (x == mX && y == mY) return;
        mX = x;
        mY = y;
        if (mLit) emit();
    }

    /** The finger lifted, or the gesture was taken away: the light fades, then the sink clears. */
    public void up() {
        if (!mHeld) return;
        mHeld = false;
        if (!mLit || !mAllowed.getAsBoolean()) {
            // Never shown (a release before the first frame), or motion stopped meanwhile.
            dropNow();
            return;
        }
        final float from = mValue;
        mTicker.start(share(FALL_MS, from), new Frames() {
            @Override public void onFraction(float t) {
                mValue = from * (1f - easeInOut(t));
                emit();
            }

            @Override public void onEnd() {
                mValue = 0f;
                clearSink();
            }
        });
    }

    /** Stops at once with no fade, leaving the surface at its resting look: the surface is going. */
    public void reset() {
        mHeld = false;
        dropNow();
    }

    /** The light's strength now, 0..1. */
    public float intensity() {
        return mValue;
    }

    /** Whether a finger is down on this light. */
    public boolean isHeld() {
        return mHeld;
    }

    /** The radius the light reaches, in the sink's pixels. */
    public float radiusPx() {
        return mRadiusPx;
    }

    private void dropNow() {
        mTicker.cancel();
        mValue = 0f;
        clearSink();
    }

    private void emit() {
        mLit = true;
        mSink.touch(mX, mY, mRadiusPx, mValue);
    }

    private void clearSink() {
        if (!mLit) return;
        mLit = false;
        mSink.clear();
    }

    /** The share of {@code fullMs} a distance of {@code distance} (of the whole 0..1) takes, at least a frame. */
    @VisibleForTesting
    static long share(long fullMs, float distance) {
        float d = Math.max(0f, Math.min(1f, distance));
        return Math.max(16L, Math.round(fullMs * d));
    }

    /** Fast out, slow in: the light is there at once and settles into full strength. */
    @VisibleForTesting
    static float decelerate(float t) {
        float u = 1f - clamp01(t);
        return 1f - u * u;
    }

    /** Smooth at both ends: the light leaves without a step at the start or at the finish. */
    @VisibleForTesting
    static float easeInOut(float t) {
        float c = clamp01(t);
        return c * c * (3f - 2f * c);
    }

    private static float clamp01(float t) {
        return t < 0f ? 0f : (t > 1f ? 1f : t);
    }

    /**
     * A sink onto the frost a view holds: the {@link SharedFrameDrawable} inside its drawable,
     * looked up at every touch because a surface swaps or drops its frost while it opens and shuts.
     * The drawable last touched is the one cleared, and a swap clears the old one first. Its
     * coordinates are the view's own. A frost without the program (the default mode, a phone below
     * Android 13) ignores the touch.
     */
    @NonNull
    public static Sink frostSink(@NonNull ImageView view) {
        return new Sink() {
            @Nullable private SharedFrameDrawable mTouched;

            @Override public void touch(float x, float y, float radiusPx, float intensity) {
                SharedFrameDrawable frost = SharedFrameDrawable.of(view.getDrawable());
                if (frost != mTouched) {
                    if (mTouched != null) mTouched.clearTouch();
                    mTouched = frost;
                }
                if (frost != null) frost.setTouch(x, y, radiusPx, intensity);
            }

            @Override public void clear() {
                SharedFrameDrawable touched = mTouched;
                mTouched = null;
                if (touched != null) touched.clearTouch();
            }
        };
    }

    /** The one {@link ValueAnimator} behind a light, linear; the easing is the light's own. */
    private static final class AnimatorTicker implements Ticker {
        private final ValueAnimator mAnimator = ValueAnimator.ofFloat(0f, 1f);
        @Nullable private Frames mFrames;

        AnimatorTicker() {
            mAnimator.setInterpolator(new LinearInterpolator());
            mAnimator.addUpdateListener(animator -> {
                Frames frames = mFrames;
                if (frames != null) frames.onFraction(animator.getAnimatedFraction());
            });
            mAnimator.addListener(new AnimatorListenerAdapter() {
                @Override public void onAnimationEnd(Animator animator) {
                    // A cancel reaches here too, after mFrames was taken away.
                    Frames frames = mFrames;
                    mFrames = null;
                    if (frames != null) frames.onEnd();
                }
            });
        }

        @Override public void start(long durationMs, @NonNull Frames frames) {
            cancel();
            mFrames = frames;
            mAnimator.setDuration(durationMs);
            mAnimator.start();
        }

        @Override public void cancel() {
            mFrames = null;
            mAnimator.cancel();
        }
    }
}
