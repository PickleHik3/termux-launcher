package com.termux.app.chrome;

import android.animation.ValueAnimator;
import android.content.Context;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.VisibleForTesting;

import com.termux.app.ReducedMotion;

import java.util.function.BooleanSupplier;

/**
 * The touch light on a dock icon, for glass that refracts through a {@code RenderEffect}. Such an
 * effect copies the program's uniforms when it is built, so every change of the light's strength
 * is one rebuild of the effect; {@link GlassTouchLight}'s per-frame rise and fall would be a
 * rebuild on every frame. This one is stepped instead, and bounded: the light comes on at full
 * strength on the finger's down (one rebuild, no rise), holds while the finger rests (an icon does
 * not move under it, so a move rebuilds nothing), and on the finger's lift falls in
 * {@value #STEP_COUNT} steps {@value #STEP_MS} ms apart, to 0.75, 0.5, 0.25 and off. At most five
 * rebuilds a tap and none while idle. A new down cancels a fall still running and lights again.
 *
 * <p>When motion is stopped (the system's animations off, reduced motion, or the caller's rule
 * for the Fancier Glass motions) a down does nothing and the sink is never called.</p>
 */
public final class DockTouchFade {

    /** Where the light goes: the dock's program, then one rebuild of its effect per call. */
    public interface Sink {
        /** The light at {@code (x, y)} in the effect's coordinates, at {@code intensity} (0, 1]. */
        void light(float x, float y, float intensity);

        /** No light: the resting look. */
        void clear();
    }

    /** Runs the fall's steps on the main thread. */
    @VisibleForTesting
    interface Scheduler {
        void postDelayed(@NonNull Runnable step, long delayMs);

        void cancel(@NonNull Runnable step);
    }

    /** The strengths the fall steps through before the light goes off. */
    @VisibleForTesting
    static final float[] FALL_STEPS = {0.75f, 0.5f, 0.25f};
    /** How many rebuilds a fall costs: each strength above, then off. */
    public static final int STEP_COUNT = 4;
    /** The time between two steps of the fall, and from the lift to the first. */
    public static final long STEP_MS = 60L;

    @NonNull private final Sink mSink;
    @NonNull private final Scheduler mScheduler;
    @NonNull private final BooleanSupplier mAllowed;
    @NonNull private final Runnable mStep = this::step;

    /** The sink holds a light: it was handed one since its last clear. */
    private boolean mLit;
    /** A fall is scheduled. */
    private boolean mFalling;
    /** The next step of the fall: an index into {@link #FALL_STEPS}, or its length for off. */
    private int mNextStep;
    private float mX, mY;

    /**
     * Steps on {@code view}'s message queue; {@code motionAllowed} is the caller's rule for the
     * Fancier Glass motions (lazy mode, battery saver, the look being on).
     */
    public DockTouchFade(@NonNull Context context, @NonNull View view, @NonNull Sink sink,
                         @NonNull BooleanSupplier motionAllowed) {
        this(sink, new Scheduler() {
            @Override public void postDelayed(@NonNull Runnable step, long delayMs) {
                view.postDelayed(step, delayMs);
            }

            @Override public void cancel(@NonNull Runnable step) {
                view.removeCallbacks(step);
            }
        }, () -> GlassTouchLight.allowed(ValueAnimator.areAnimatorsEnabled(),
            ReducedMotion.isEnabled(context), motionAllowed.getAsBoolean()));
    }

    @VisibleForTesting
    DockTouchFade(@NonNull Sink sink, @NonNull Scheduler scheduler,
                  @NonNull BooleanSupplier allowed) {
        mSink = sink;
        mScheduler = scheduler;
        mAllowed = allowed;
    }

    /** A finger lands on an icon at {@code (x, y)}: the light comes on there, at once. */
    public void down(float x, float y) {
        cancelFall();
        if (!mAllowed.getAsBoolean()) {
            // Motion stopped while a light was still up: put the glass back at rest.
            if (mLit) clearNow();
            return;
        }
        mX = x;
        mY = y;
        mLit = true;
        mSink.light(x, y, 1f);
    }

    /** The finger lifts or the gesture is cancelled: the light falls in steps. */
    public void up() {
        if (!mLit || mFalling) return;
        mFalling = true;
        mNextStep = 0;
        mScheduler.postDelayed(mStep, STEP_MS);
    }

    /** Off at once, with no fall: the dock went away, or the activity is leaving. */
    public void reset() {
        cancelFall();
        if (mLit) clearNow();
    }

    /** True while the sink holds a light, falling or not. */
    public boolean isLit() {
        return mLit;
    }

    private void step() {
        if (!mFalling) return;
        if (mNextStep < FALL_STEPS.length) {
            float intensity = FALL_STEPS[mNextStep++];
            mSink.light(mX, mY, intensity);
            mScheduler.postDelayed(mStep, STEP_MS);
            return;
        }
        mFalling = false;
        clearNow();
    }

    private void cancelFall() {
        if (!mFalling) return;
        mFalling = false;
        mScheduler.cancel(mStep);
    }

    private void clearNow() {
        mLit = false;
        mSink.clear();
    }
}
