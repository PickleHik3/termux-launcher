package com.termux.app.chrome;

import androidx.annotation.NonNull;

/**
 * Living glass: a glass pane behaves as a slab with a little mass and spring. Pure: a surface
 * feeds it where it stands on every draw it already makes, and the presses on it, and reads back
 * what {@link GlassRefraction.Program#setMotionLight} hands the program: how far the slab's bend
 * lags behind it ({@link #lagXPx}, {@link #lagYPx}) and a dent under the finger
 * ({@link #dentDepthPx}). The program deforms the refraction it already draws by those; nothing
 * is painted on top.
 *
 * <p><b>Lag.</b> The bend follows the slab's centre on a spring: with the slab at {@code p} and the
 * bend at {@code o}, {@code o'' = -k(o - p) - c o'}, and the lag is {@code L = p - o}. While the
 * slab moves, {@code L} points the way it goes and grows with its speed (a steady
 * {@code c v / k}), so the leading edge bends more and the trailing edge less; when it stops the
 * bend overshoots and rocks back ({@link #LAG_HZ}, {@link #DAMPING}). {@code L} is held under
 * {@link #LAG_MAX_DP}, which a quick flick reaches and a slow drag stays far below.</p>
 *
 * <p><b>Dent.</b> A press drives a dent's depth toward {@link #DENT_DEPTH_DP} on a stiffer spring
 * of the same damping ({@link #DENT_HZ}); a release lets it spring back to 0 through a small
 * rebound. The dent stays where the finger came down.</p>
 *
 * <p>Both springs are solved exactly for each draw's interval, the slab's centre taken as moving
 * at constant speed between two draws, so the result is the same at any frame rate. Draws more
 * than {@link #MAX_GAP_MS} apart, and an edge that travels more than {@link #JUMP_DP} between two
 * draws, are placement, not motion: the bend is carried with the slab and nothing is kicked.</p>
 *
 * <p>At rest every output is exactly 0 and {@link #onDraw} answers false, so the surface asks for
 * no frames. A spring under its rest thresholds ({@link #LAG_REST_DP}, {@link #DENT_REST_DP} and
 * their rates) snaps to rest exactly.</p>
 *
 * <p>The {@link Gate} is asked once when a motion or a press starts, never per frame: its answer
 * is the Animation speed multiplier both springs' time is stretched by, or 0 for a slab that
 * stays still.</p>
 */
public final class GlassMotionLight {

    /** Whether the glass may move, asked as a motion or a press starts. */
    public interface Gate {
        /**
         * 0 for none (the phone's animations off, reduced motion, the setting off, lazy mode,
         * battery saver, Animation speed's Off); otherwise Animation speed's duration multiplier.
         */
        float multiplier();
    }

    /** The lag spring's natural frequency at Normal speed: a slightly heavy slab. */
    static final float LAG_HZ = 3.0f;
    /** The dent's natural frequency: a fingertip is lighter than the slab. */
    static final float DENT_HZ = 4.0f;
    /** Both springs' damping ratio: one clear rock back, a second barely there. */
    static final float DAMPING = 0.55f;
    /**
     * The most the bend may lag, dp: a steady {@code 2 DAMPING / omega} seconds of travel, which
     * a quick flick (about 2700 dp/s) reaches and a slow drag (200 dp/s, about 12 dp) stays far
     * below. Also what the program reads as the whole lean ({@code kLagMaxDp}).
     */
    static final float LAG_MAX_DP = 160f;
    /** How far the picture moves under the middle of a full dent, dp at its steepest. */
    static final float DENT_DEPTH_DP = 2.5f;
    /** Lag under this, at a rate under {@link #LAG_REST_DP_PER_S}, is none: well under a pixel of bend. */
    static final float LAG_REST_DP = 1.0f;
    static final float LAG_REST_DP_PER_S = 20f;
    /** A dent within this of where it is going, at a rate under {@link #DENT_REST_DP_PER_S}, is there. */
    static final float DENT_REST_DP = 0.02f;
    static final float DENT_REST_DP_PER_S = 0.5f;
    /** Two draws further apart than this are not one motion. */
    static final long MAX_GAP_MS = 100L;
    /**
     * A leading edge that travels further than this between two draws was placed there, not
     * moved: about 8700 dp/s at 90 Hz, past any page turn (a quick one peaks near 3500).
     */
    static final float JUMP_DP = 96f;
    /** Gate not asked yet this motion. */
    private static final float UNASKED = -1f;

    private static final double LAG_OMEGA = 2.0 * Math.PI * LAG_HZ;
    private static final double LAG_K = LAG_OMEGA * LAG_OMEGA;
    private static final double LAG_C = 2.0 * DAMPING * LAG_OMEGA;
    private static final double DENT_OMEGA = 2.0 * Math.PI * DENT_HZ;

    private final float mDensity;
    @NonNull private final Gate mGate;

    private boolean mHasLast;
    private long mLastMs;
    private float mLastCx, mLastCy, mLastHw, mLastHh;

    /** The Gate's answer for the motion or press under way, or {@link #UNASKED}. */
    private float mMultiplier = UNASKED;

    /** The lag {@code L = p - o}, dp, and the bend's own velocity {@code o'}, dp per spring second. */
    private double mLagX, mLagY;
    private double mBendVx, mBendVy;

    /** The dent: where (px, the surface's own), its depth and rate (dp, dp/s) and target (dp). */
    private float mDentX, mDentY;
    private double mDent, mDentV;
    private float mDentTarget;
    private long mDentMs;

    /** Scratch for one spring step: position and velocity out. */
    private final double[] mStep = new double[2];

    /** @param density px per dp, so the glass behaves the same on every screen */
    public GlassMotionLight(float density, @NonNull Gate gate) {
        mDensity = Math.max(0.1f, density);
        mGate = gate;
    }

    /**
     * One draw of the surface at frame time {@code nowMs}, standing over screen px
     * {@code (left, top, right, bottom)}. True while either spring is still moving, so the surface
     * draws again next frame; false at rest (a dent held still under a finger included).
     */
    public boolean onDraw(long nowMs, float left, float top, float right, float bottom) {
        float cx = (left + right) * 0.5f;
        float cy = (top + bottom) * 0.5f;
        float hw = Math.abs(right - left) * 0.5f;
        float hh = Math.abs(bottom - top) * 0.5f;
        advanceDent(nowMs);
        if (!mHasLast) {
            remember(nowMs, cx, cy, hw, hh);
            settleIfSpent(false);
            return isMoving();
        }
        long dt = nowMs - mLastMs;
        // A second draw in the same frame: nothing has moved in time, so nothing is measured.
        if (dt <= 0L) return isMoving();
        float dxDp = (cx - mLastCx) / mDensity;
        float dyDp = (cy - mLastCy) / mDensity;
        float travelDp = leadingTravel(cx - mLastCx, cy - mLastCy, hw - mLastHw, hh - mLastHh)
            / mDensity;
        remember(nowMs, cx, cy, hw, hh);
        boolean moved = dxDp != 0f || dyDp != 0f;
        // A pane resized or put somewhere else in one step (the keyboard, a rotation), or drawn
        // after a pause, was placed: the bend goes with it.
        boolean placed = dt > MAX_GAP_MS || travelDp > JUMP_DP;
        if (moved && !placed && lagAtRest() && mMultiplier == UNASKED) {
            mMultiplier = Math.max(0f, mGate.multiplier());
        }
        if (mMultiplier == 0f || (lagAtRest() && (placed || !moved))) {
            // Nothing to simulate: a still slab at rest, a placement from rest, or no motion allowed.
            mLagX = mLagY = 0.0;
            mBendVx = mBendVy = 0.0;
        } else {
            float m = mMultiplier > 0f ? mMultiplier : 1f;
            double seconds = dt / (1000.0 * m);
            double vx = placed ? 0.0 : dxDp / seconds;
            double vy = placed ? 0.0 : dyDp / seconds;
            stepLag(seconds, vx, vy);
            clampLag(vx, vy);
            if (Math.abs(mLagX) < LAG_REST_DP && Math.abs(mLagY) < LAG_REST_DP
                && Math.abs(vx - mBendVx) < LAG_REST_DP_PER_S
                && Math.abs(vy - mBendVy) < LAG_REST_DP_PER_S) {
                mLagX = mLagY = 0.0;
                mBendVx = vx;
                mBendVy = vy;
            }
        }
        settleIfSpent(moved && !placed);
        return isMoving();
    }

    /**
     * A finger came down on the surface at {@code (x, y)} in its own px: a dent starts pressing in
     * there. True when it did, so the surface draws again next frame.
     */
    public boolean press(long nowMs, float x, float y) {
        if (mMultiplier == UNASKED) mMultiplier = Math.max(0f, mGate.multiplier());
        if (mMultiplier == 0f) {
            settleIfSpent(false);
            return false;
        }
        advanceDent(nowMs);
        if (mDent == 0.0 && mDentV == 0.0) {
            // From rest the clock starts now, never from the last time the dent moved.
            mDentMs = nowMs;
            mDentX = x;
            mDentY = y;
        } else if (mDentTarget == 0f) {
            // A new press while the last one springs back: the dent moves to the new finger.
            mDentX = x;
            mDentY = y;
        }
        mDentTarget = DENT_DEPTH_DP;
        return true;
    }

    /**
     * The finger lifted, or the gesture went to something else: the dent springs back. True when
     * there is a dent to let go, so the surface draws again next frame.
     */
    public boolean release(long nowMs) {
        if (mDentTarget == 0f) return false;
        advanceDent(nowMs);
        mDentTarget = 0f;
        return true;
    }

    /** Back to rest at once: the surface is going, or lost its program. */
    public void reset() {
        mHasLast = false;
        mMultiplier = UNASKED;
        mLagX = mLagY = 0.0;
        mBendVx = mBendVy = 0.0;
        mDent = mDentV = 0.0;
        mDentTarget = 0f;
    }

    /** True while either spring still moves: the surface needs another frame. */
    public boolean isMoving() {
        return !lagAtRest() || !dentSettled();
    }

    /** {@code uLag.x}: how far the slab is ahead of its bend, px on screen; 0 at rest. */
    public float lagXPx() {
        return (float) (mLagX * mDensity);
    }

    /** {@code uLag.y}. */
    public float lagYPx() {
        return (float) (mLagY * mDensity);
    }

    /** {@code uDent.xy}: where the dent is, the surface's own px. */
    public float dentXPx() {
        return mDentX;
    }

    public float dentYPx() {
        return mDentY;
    }

    /**
     * {@code uDent.z}: how far the picture moves under the dent at its steepest, px; positive for
     * a dent, briefly negative as it rebounds, 0 at rest.
     */
    public float dentDepthPx() {
        return (float) (mDent * mDensity);
    }

    // ------------------------------------------------------------------------------- the springs

    private boolean lagAtRest() {
        return mLagX == 0.0 && mLagY == 0.0;
    }

    private boolean dentSettled() {
        return mDent == mDentTarget && mDentV == 0.0;
    }

    /** Everything at rest and nothing measured moving: the next motion asks the gate afresh. */
    private void settleIfSpent(boolean moving) {
        if (!moving && lagAtRest() && mDent == 0.0 && mDentV == 0.0 && mDentTarget == 0f) {
            mMultiplier = UNASKED;
            mBendVx = mBendVy = 0.0;
        }
    }

    /**
     * {@code seconds} of the lag spring with the slab moving at {@code (vx, vy)} dp/s throughout:
     * {@code L'' = -k L - c L' + c v}, whose rest is {@code c v / k}; around that rest it is the
     * free spring, solved exactly.
     */
    private void stepLag(double seconds, double vx, double vy) {
        double restX = LAG_C * vx / LAG_K;
        double restY = LAG_C * vy / LAG_K;
        freeSpring(mLagX - restX, vx - mBendVx, seconds, LAG_OMEGA, mStep);
        mLagX = mStep[0] + restX;
        mBendVx = vx - mStep[1];
        freeSpring(mLagY - restY, vy - mBendVy, seconds, LAG_OMEGA, mStep);
        mLagY = mStep[0] + restY;
        mBendVy = vy - mStep[1];
    }

    /**
     * Holds the lag at {@link #LAG_MAX_DP}: past it the bend is dragged along, and whatever of its
     * rate would take it further out is dropped, so a long fling settles from the hold.
     */
    private void clampLag(double vx, double vy) {
        double length = Math.hypot(mLagX, mLagY);
        if (length <= LAG_MAX_DP) return;
        double ux = mLagX / length;
        double uy = mLagY / length;
        mLagX = ux * LAG_MAX_DP;
        mLagY = uy * LAG_MAX_DP;
        double outward = (vx - mBendVx) * ux + (vy - mBendVy) * uy;
        if (outward > 0.0) {
            mBendVx += outward * ux;
            mBendVy += outward * uy;
        }
    }

    /** The dent up to {@code nowMs}, toward its target, at the motion's multiplier. */
    private void advanceDent(long nowMs) {
        long dt = nowMs - mDentMs;
        if (dt <= 0L) return;
        mDentMs = nowMs;
        if (dentSettled()) return;
        float m = mMultiplier > 0f ? mMultiplier : 1f;
        freeSpring(mDent - mDentTarget, mDentV, dt / (1000.0 * m), DENT_OMEGA, mStep);
        mDent = mStep[0] + mDentTarget;
        mDentV = mStep[1];
        if (Math.abs(mDent - mDentTarget) < DENT_REST_DP && Math.abs(mDentV) < DENT_REST_DP_PER_S) {
            mDent = mDentTarget;
            mDentV = 0.0;
        }
    }

    private void remember(long nowMs, float cx, float cy, float hw, float hh) {
        mHasLast = true;
        mLastMs = nowMs;
        mLastCx = cx;
        mLastCy = cy;
        mLastHw = hw;
        mLastHh = hh;
    }

    /**
     * The free underdamped spring {@code x'' = -omega² x - 2 DAMPING omega x'} from {@code x0},
     * {@code v0} after {@code t} seconds, exactly: position and velocity into {@code out}.
     */
    static void freeSpring(double x0, double v0, double t, double omega, double[] out) {
        double decay = DAMPING * omega;
        double omegaD = omega * Math.sqrt(1.0 - DAMPING * DAMPING);
        double e = Math.exp(-decay * t);
        double cos = Math.cos(omegaD * t);
        double sin = Math.sin(omegaD * t);
        out[0] = e * (x0 * cos + (v0 + decay * x0) / omegaD * sin);
        out[1] = e * (v0 * cos - (omega * omega * x0 + decay * v0) / omegaD * sin);
    }

    /**
     * How far the slab's furthest-travelling edge went, px: the centre's travel plus the growth's
     * along each axis.
     */
    static float leadingTravel(float dcx, float dcy, float dhw, float dhh) {
        return (float) Math.hypot(Math.abs(dcx) + Math.abs(dhw), Math.abs(dcy) + Math.abs(dhh));
    }
}
