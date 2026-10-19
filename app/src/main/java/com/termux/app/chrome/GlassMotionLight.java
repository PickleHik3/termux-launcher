package com.termux.app.chrome;

import androidx.annotation.NonNull;

/**
 * Motion light: the light in a glass pane comes alive briefly while the pane moves, then returns
 * exactly to its resting look. Pure: a surface feeds it where it stands on every draw it already
 * makes, and reads back what {@link GlassRefraction.Program#setMotionLight} hands the program.
 *
 * <p>Driven by velocity, never by progress. Each draw's screen rect against the last one gives the
 * speed of the pane's leading edge (its centre's travel plus its growth), which maps through a
 * calm floor ({@link #CALM_DP_PER_S}) to full light at {@link #FULL_DP_PER_S}: a slow drag stays
 * dark, a quick flick lights fully. The light rises quickly toward that ({@link #RISE_MS}) and
 * falls linearly to nothing over {@link #FALL_MS} once the pane slows, so it peaks mid-motion and
 * is gone a quarter of a second after the pane lands. The direction it carries is the centre's
 * travel as a share of that speed: a unit vector for a pure slide, shorter as growth takes over,
 * zero for a pane that only grows, so the program can put the fringe on the leading edge, or all
 * round.</p>
 *
 * <p>A press ({@link #press}) sends one soft light along the rim instead: over {@link #SWEEP_MS} it
 * travels across the pane from the side away from the finger toward it, strongest half way.</p>
 *
 * <p>At rest nothing runs: a surface that did not move feeds a zero speed, and once the light and
 * any sweep are spent {@link #onDraw} answers false and the surface stops asking for frames. Two
 * draws more than {@link #MAX_GAP_MS} apart are not one motion, and a surface whose leading edge
 * travels more than {@link #JUMP_DP} between two draws was placed, not moved; neither lights.</p>
 *
 * <p>The {@link Gate} is asked once when a motion or a press starts, never per frame: its answer
 * is the Animation speed multiplier the light's times are stretched by, or 0 for no light.</p>
 */
public final class GlassMotionLight {

    /** Whether the light may play, asked as a motion or a press starts. */
    public interface Gate {
        /**
         * 0 for no light (the phone's animations off, reduced motion, the setting off, lazy mode,
         * battery saver, Animation speed's Off); otherwise Animation speed's duration multiplier.
         */
        float multiplier();
    }

    /** The leading edge's speed under which the pane stays dark: a slow drag stays calm. */
    static final float CALM_DP_PER_S = 250f;
    /** The leading edge's speed at which the light is full: a quick flick, a page turn. */
    static final float FULL_DP_PER_S = 2500f;
    /** The rise toward a brighter target, as a time constant at Normal speed. */
    static final float RISE_MS = 50f;
    /** The fall from full light to none at Normal speed; linear, so it ends. */
    static final float FALL_MS = 240f;
    /** A press's sweep across the pane at Normal speed. */
    static final float SWEEP_MS = 320f;
    /**
     * Where the sweep's band starts and ends, in half-extents of the pane from its centre along
     * the sweep's direction: just outside the far edge to just past the near one.
     */
    static final float SWEEP_REACH = 1.3f;
    /** Two draws further apart than this are not one motion. */
    static final long MAX_GAP_MS = 100L;
    /**
     * A leading edge that travels further than this between two draws was placed there, not
     * moved: about 8700 dp/s at 90 Hz, past any page turn (a quick one peaks near 3500).
     */
    static final float JUMP_DP = 96f;
    /** Light under this is none: the fall snaps to rest here rather than creeping. */
    static final float REST_EPSILON = 1e-3f;
    /** Gate not asked yet this motion. */
    private static final float UNASKED = -1f;

    private final float mDensity;
    @NonNull private final Gate mGate;

    private boolean mHasLast;
    private long mLastMs;
    private float mLastCx, mLastCy, mLastHw, mLastHh;

    /** The Gate's answer for the motion under way, or {@link #UNASKED}. */
    private float mMultiplier = UNASKED;
    /** The light's raw level, 0..1, before its easing. */
    private float mLevel;
    private float mDirX, mDirY;

    /** The sweep's start, and its length at the speed it was asked at; 0 length for none. */
    private long mSweepStartMs;
    private float mSweepMs;
    private float mSweepDirX, mSweepDirY = 1f;
    private float mSweepPosition;
    private float mSweepStrength;

    /** @param density px per dp, so speeds are judged the same on every screen */
    public GlassMotionLight(float density, @NonNull Gate gate) {
        mDensity = Math.max(0.1f, density);
        mGate = gate;
    }

    /**
     * One draw of the surface at frame time {@code nowMs}, standing over screen px
     * {@code (left, top, right, bottom)}. True while the light or a sweep is still lit, so the
     * surface draws again next frame; false at rest.
     */
    public boolean onDraw(long nowMs, float left, float top, float right, float bottom) {
        float cx = (left + right) * 0.5f;
        float cy = (top + bottom) * 0.5f;
        float hw = Math.abs(right - left) * 0.5f;
        float hh = Math.abs(bottom - top) * 0.5f;
        if (!mHasLast) {
            remember(nowMs, cx, cy, hw, hh);
            updateSweep(nowMs);
            return isLit();
        }
        long dt = nowMs - mLastMs;
        // A second draw in the same frame: nothing has moved in time, so nothing is measured.
        if (dt <= 0L) return isLit();
        float target = 0f;
        float dirX = 0f, dirY = 0f;
        if (dt <= MAX_GAP_MS) {
            float scale = 1000f / (dt * mDensity);   // px per frame to dp per second
            float vcx = (cx - mLastCx) * scale;
            float vcy = (cy - mLastCy) * scale;
            float vhx = (hw - mLastHw) * scale;
            float vhy = (hh - mLastHh) * scale;
            // The leading edge's own travel since the last draw: a pane resized or put
            // somewhere else in one step (the keyboard, a rotation) was placed, not moved.
            float travelDp = leadingSpeed(cx - mLastCx, cy - mLastCy, hw - mLastHw, hh - mLastHh)
                / mDensity;
            float speed = leadingSpeed(vcx, vcy, vhx, vhy);
            if (travelDp <= JUMP_DP && speed > 0f) {
                target = levelForSpeed(speed);
                dirX = vcx / speed;
                dirY = vcy / speed;
            }
        }
        remember(nowMs, cx, cy, hw, hh);
        float raw = target;
        if (target > 0f) {
            if (mMultiplier == UNASKED) mMultiplier = Math.max(0f, mGate.multiplier());
            if (mMultiplier == 0f) target = 0f;
        }
        if (target > 0f) {
            mDirX = dirX;
            mDirY = dirY;
        }
        mLevel = advance(mLevel, target, dt, mMultiplier > 0f ? mMultiplier : 1f);
        updateSweep(nowMs);
        // The pane has stopped and the light is spent: the next motion asks the gate afresh.
        if (raw == 0f && mLevel == 0f) mMultiplier = UNASKED;
        return isLit();
    }

    /**
     * A finger pressed the pane at {@code (x, y)} in its own px, the pane {@code width} by
     * {@code height}: one sweep runs toward the finger. True when it started, so the surface
     * draws again next frame.
     */
    public boolean press(long nowMs, float x, float y, float width, float height) {
        float multiplier = Math.max(0f, mGate.multiplier());
        if (multiplier == 0f) return false;
        float dx = x - width * 0.5f;
        float dy = y - height * 0.5f;
        float length = (float) Math.hypot(dx, dy);
        if (length < 1f) {
            // A press dead centre has no side: the sweep falls as the light does, downward.
            mSweepDirX = 0f;
            mSweepDirY = 1f;
        } else {
            mSweepDirX = dx / length;
            mSweepDirY = dy / length;
        }
        mSweepStartMs = nowMs;
        mSweepMs = SWEEP_MS * multiplier;
        updateSweep(nowMs);
        return true;
    }

    /** Back to rest at once, no fall: the surface is going, or lost its program. */
    public void reset() {
        mHasLast = false;
        mLevel = 0f;
        mMultiplier = UNASKED;
        mSweepMs = 0f;
        mSweepStrength = 0f;
    }

    /** True while there is any light to draw. */
    public boolean isLit() {
        return mLevel > 0f || mSweepStrength > 0f || mSweepMs > 0f;
    }

    /** {@code uMotion}: the light's strength now, 0..1, eased at both ends. */
    public float motion() {
        return ease(mLevel);
    }

    /** {@code uMotionDir}: the centre's travel as a share of the leading edge's speed; length 0..1. */
    public float dirX() {
        return mDirX;
    }

    public float dirY() {
        return mDirY;
    }

    /** {@code uSweep.xy}: the way the sweep travels across the pane, a unit vector. */
    public float sweepDirX() {
        return mSweepDirX;
    }

    public float sweepDirY() {
        return mSweepDirY;
    }

    /** {@code uSweep.z}: where the sweep's band is, in half-extents from the centre along its way. */
    public float sweepPosition() {
        return mSweepPosition;
    }

    /** {@code uSweep.w}: the sweep's strength now, 0..1; 0 when none runs. */
    public float sweepStrength() {
        return mSweepStrength;
    }

    private void remember(long nowMs, float cx, float cy, float hw, float hh) {
        mHasLast = true;
        mLastMs = nowMs;
        mLastCx = cx;
        mLastCy = cy;
        mLastHw = hw;
        mLastHh = hh;
    }

    private void updateSweep(long nowMs) {
        if (mSweepMs <= 0f) {
            mSweepStrength = 0f;
            return;
        }
        float t = (nowMs - mSweepStartMs) / mSweepMs;
        if (t >= 1f || t < 0f) {
            mSweepMs = 0f;
            mSweepStrength = 0f;
            return;
        }
        mSweepPosition = sweepPositionAt(t);
        mSweepStrength = sweepStrengthAt(t);
    }

    // ------------------------------------------------------------------------------ the policy

    /**
     * How fast the pane's fastest-moving edge travels, dp/s: the centre's speed plus the growth's
     * along each axis. A pane that only slides answers its centre's speed; one that only grows,
     * its edges'.
     */
    static float leadingSpeed(float vcx, float vcy, float vhx, float vhy) {
        return (float) Math.hypot(Math.abs(vcx) + Math.abs(vhx), Math.abs(vcy) + Math.abs(vhy));
    }

    /** The light a leading edge at {@code speedDpPerS} asks: 0 up to the calm floor, 1 from full. */
    static float levelForSpeed(float speedDpPerS) {
        float t = (speedDpPerS - CALM_DP_PER_S) / (FULL_DP_PER_S - CALM_DP_PER_S);
        return t <= 0f ? 0f : Math.min(1f, t);
    }

    /**
     * The light after {@code dtMs} more, from {@code level} toward {@code target}, at Animation
     * speed's {@code multiplier}: a quick exponential rise, a linear fall that reaches rest.
     */
    static float advance(float level, float target, long dtMs, float multiplier) {
        float m = Math.max(1e-3f, multiplier);
        float next;
        if (target > level) {
            next = level + (target - level) * (float) (1.0 - Math.exp(-dtMs / (RISE_MS * m)));
        } else {
            next = Math.max(target, level - dtMs / (FALL_MS * m));
        }
        return next < REST_EPSILON ? 0f : Math.min(1f, next);
    }

    /** Smooth at both ends, so the light neither steps on nor steps off. */
    static float ease(float level) {
        float c = level <= 0f ? 0f : Math.min(1f, level);
        return c * c * (3f - 2f * c);
    }

    /** The sweep's band at {@code t} of its run: from the far side, past the centre, off the near one. */
    static float sweepPositionAt(float t) {
        return -SWEEP_REACH + 2f * SWEEP_REACH * ease(t);
    }

    /** The sweep's strength at {@code t} of its run: nothing at either end, all of it half way. */
    static float sweepStrengthAt(float t) {
        if (t <= 0f || t >= 1f) return 0f;
        return (float) Math.sin(Math.PI * t);
    }
}
