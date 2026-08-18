package com.termux.app.launcher.drawer;

import com.termux.app.Spring;

/**
 * The A-Z column's rope: a coupled damped wave on a chain of letters, driven from one end.
 *
 * <p>State per letter {@code i} (0 = top) is a lateral offset (positive = outward, away from the
 * grid) and a velocity. The acceleration is
 * {@code K*(x[i-1]-x[i]) + K*(x[i+1]-x[i]) - Kr*x[i] - C*v[i]} with {@code x[-1]} the kinematic
 * anchor and a free lower end ({@code x[N] == x[N-1]}, so the last letter simply has no neighbour
 * pulling from below). That makes {@code x = 0, v = 0} the unique fixed point: "the column comes to
 * rest straight, on the right" is a property of the physics rather than a hard-coded end state, and
 * the per-letter lag is emergent — a disturbance at the anchor propagates down the chain, reflects
 * off the free end and decays. There is no per-letter phase offset anywhere and these are not 27
 * independent springs.
 *
 * <p><b>No time source.</b> {@code advance} is called from the drawer controller's existing
 * Choreographer loop with that loop's delta, beside the transition springs, so the growing plane and
 * the letters inside it are rendered in the same frame. The anchor is a pure function of transition
 * progress (see {@link AppDrawerRopeMetrics#anchorPx}), which is why release velocity never has to
 * be injected: the anchor's velocity already <em>is</em> the finger's.
 *
 * <p><b>Integration</b> mirrors {@link Spring}: the delta is clamped by {@link Spring#clampDelta}
 * and integrated in substeps of at most {@link #MAX_STEP} with semi-implicit Euler. Velocities for
 * the whole chain are updated from one position snapshot before any position moves — updating in
 * place would let a disturbance travel the whole chain inside a single substep, which is exactly the
 * lag this model exists to produce. Stability wants {@code k*h^2 + 2*C*h < 4}; the stiffest node
 * here sees {@code 2K + Kr = 8060}, giving {@code 0.90} at {@code h = 1/120}, comfortably inside the
 * bound (the coupling alone gives {@code 0.62}). A diverged chain would never report settled and would hold the controller's loop
 * open forever on an idle open drawer, so non-finite state is absorbed the way {@code Spring} does
 * it: collapse to rest and report settled.
 *
 * <p>The chain is always {@link #MAX_LETTERS} nodes long regardless of how many letters the current
 * catalogue shows. The arrays are allocated once, {@code advance} allocates nothing, and the
 * propagation character does not change when a work profile adds a letter.
 */
public final class AppDrawerRopeModel {

    /** A-Z plus {@code #}; the chain length and the size of every array here. */
    public static final int MAX_LETTERS = 27;

    /**
     * Neighbour coupling: sets how fast a disturbance travels down the chain, at
     * {@code sqrt(K)} nodes per second. At 900 that was 30 nodes/s — 0.9s for the head's swing to
     * reach letter 26, by which time the head had long since settled, so letters 18-26 peaked at
     * about a pixel against the head's 78 and the column read as a static list with a wobbly top.
     * At 4000 the wave travels 63 nodes/s and crosses the chain in ~0.43s, inside the settle.
     */
    public static final float COUPLING_STIFFNESS = 4000f;
    /**
     * Weak pull back to the rest line, which is also what brings the chain to rest inside the settle
     * budget rather than letting the free end ring on.
     *
     * <p>It is the constant that sets how far down the chain a disturbance carries: the static lean
     * decays over {@code sqrt(K/Kr)} nodes, which at 4000/60 is 8.2 — so letter 26 peaks at about
     * 4-7% of the head's travel. Raising the reach means lowering this, and it is a straight trade
     * against the settle: {@code Kr = 30} takes the tail to ~9% of the head and the settle to 1.5s,
     * and {@code Kr = 20} to ~11% and 2.0s, both past the 1.2s the controller's loop is allowed to
     * stay open for. 60 is the largest reach that settles in time.
     */
    public static final float REST_STIFFNESS = 60f;
    /** Deliberately <b>under</b>damped: this is the one motion in the app that should oscillate. */
    public static final float DAMPING_RATIO = 0.16f;
    /**
     * Derived from {@link #COUPLING_STIFFNESS} and {@link #DAMPING_RATIO} — never typed as a
     * literal, so retuning the coupling cannot silently change the rope's character.
     */
    public static final float DAMPING = 2f * (float) Math.sqrt(COUPLING_STIFFNESS) * DAMPING_RATIO;

    /** Tilt is the local slope of the rope, in degrees per pixel of difference to the letter above. */
    public static final float TILT_DEG_PER_PX = 0.55f;
    /** Past this a glyph reads as broken rather than as hanging. */
    public static final float TILT_MAX_DEG = 14f;

    /** Largest substep, shared with the house spring integrator. */
    public static final float MAX_STEP = Spring.MAX_STEP;
    /**
     * Settled thresholds. {@link Spring#SETTLE_EPSILON} is an epsilon on a normalised 0..1 channel
     * value and cannot be reused as a <em>number</em> here, where the state is pixels: the same
     * 4e-4 against a 78px entry offset would take two and a half seconds of exponential decay to
     * satisfy and would hold the controller's loop open long after the column had visibly stopped.
     * The rule it encodes is reused instead — settled means no frame can show a difference — which in
     * pixels is half a pixel of offset.
     */
    public static final float SETTLE_OFFSET_PX = 0.5f;
    /** The speed that moves a glyph exactly {@link #SETTLE_OFFSET_PX} in one 60fps frame. */
    public static final float SETTLE_VELOCITY_PX_PER_SEC = SETTLE_OFFSET_PX * 60f;
    /**
     * The acceleration that changes a letter's speed by {@link #SETTLE_VELOCITY_PX_PER_SEC} in one
     * 60fps frame. Residual acceleration is zero exactly at equilibrium, so this is what stops a
     * turning point — every letter momentarily at zero velocity — from being mistaken for a settled
     * chain, and it is also the only workable settle test while the anchor is held off the rest line
     * (as it is for the whole time the drawer sits closed, where the chain's equilibrium is a lean,
     * not a straight line, and an offset test alone would report motion forever).
     */
    public static final float SETTLE_ACCEL_PX_PER_SEC2 = SETTLE_VELOCITY_PX_PER_SEC * 60f;

    private final float[] mOffsetPx = new float[MAX_LETTERS];
    private final float[] mVelocityPxPerSec = new float[MAX_LETTERS];
    private float mAnchorPx;
    private boolean mMoving;

    /**
     * Integrates one frame.
     *
     * @param anchorPx the driver's position, {@code x[-1]}
     * @param dt       the frame delta in seconds, clamped here
     * @param reduced  true when the animator duration scale is 0: collapse to rest in this one call
     *                 and report settled, rather than snapping to the anchor. The anchor is dropped
     *                 with the rest of the state so the column draws dead straight.
     * @return true while the chain is still in motion and needs another frame
     */
    public boolean advance(float anchorPx, float dt, boolean reduced) {
        if (reduced) {
            reset();
            return false;
        }
        mAnchorPx = anchorPx;
        float delta = Spring.clampDelta(dt);
        int steps = Math.max(1, (int) Math.ceil(delta / MAX_STEP));
        float step = delta / steps;
        for (int s = 0; s < steps; s++) {
            for (int i = 0; i < MAX_LETTERS; i++) {
                float here = mOffsetPx[i];
                float above = i == 0 ? anchorPx : mOffsetPx[i - 1];
                // Free lower end: the last letter's phantom neighbour sits exactly where it does.
                float below = i == MAX_LETTERS - 1 ? here : mOffsetPx[i + 1];
                float accel = (COUPLING_STIFFNESS * (above - here))
                    + (COUPLING_STIFFNESS * (below - here))
                    - (REST_STIFFNESS * here)
                    - (DAMPING * mVelocityPxPerSec[i]);
                mVelocityPxPerSec[i] += accel * step;
            }
            for (int i = 0; i < MAX_LETTERS; i++) {
                mOffsetPx[i] += mVelocityPxPerSec[i] * step;
            }
        }
        return finish();
    }

    /** True while the last {@link #advance} left the chain in motion. */
    public boolean isMoving() {
        return mMoving;
    }

    /** Lateral offset of letter {@code i} in pixels; 0 for an index outside the chain. */
    public float offsetPx(int i) {
        if (i < 0 || i >= MAX_LETTERS) return 0f;
        return mOffsetPx[i];
    }

    /**
     * Rotation for letter {@code i} in degrees: the local slope against the letter above it, which
     * for letter 0 is the anchor. This is what sells "rope" rather than "wobbling letters", and it
     * lives in the model so it is testable.
     */
    public float tiltDeg(int i) {
        if (i < 0 || i >= MAX_LETTERS) return 0f;
        float above = i == 0 ? mAnchorPx : mOffsetPx[i - 1];
        float tilt = TILT_DEG_PER_PX * (mOffsetPx[i] - above);
        if (tilt > TILT_MAX_DEG) return TILT_MAX_DEG;
        if (tilt < -TILT_MAX_DEG) return -TILT_MAX_DEG;
        return tilt;
    }

    /** Drops the whole chain, anchor included, back to the straight rest line. */
    public void reset() {
        for (int i = 0; i < MAX_LETTERS; i++) {
            mOffsetPx[i] = 0f;
            mVelocityPxPerSec[i] = 0f;
        }
        mAnchorPx = 0f;
        mMoving = false;
    }

    /** Absorbs non-finite state and answers whether the chain still needs another frame. */
    private boolean finish() {
        if (!isFinite(mAnchorPx)) {
            reset();
            return false;
        }
        float maxOffset = 0f;
        float maxVelocity = 0f;
        float maxAccel = 0f;
        for (int i = 0; i < MAX_LETTERS; i++) {
            float here = mOffsetPx[i];
            float velocity = mVelocityPxPerSec[i];
            if (!isFinite(here) || !isFinite(velocity)) {
                reset();
                return false;
            }
            float above = i == 0 ? mAnchorPx : mOffsetPx[i - 1];
            float below = i == MAX_LETTERS - 1 ? here : mOffsetPx[i + 1];
            float accel = (COUPLING_STIFFNESS * (above - here))
                + (COUPLING_STIFFNESS * (below - here))
                - (REST_STIFFNESS * here)
                - (DAMPING * velocity);
            maxOffset = Math.max(maxOffset, Math.abs(here));
            maxVelocity = Math.max(maxVelocity, Math.abs(velocity));
            maxAccel = Math.max(maxAccel, Math.abs(accel));
        }
        // With the anchor home the equilibrium is the straight rest line, so the strict "straight and
        // still" test applies; with the anchor held out it is a lean, and only velocity and residual
        // acceleration can say whether the chain has reached it.
        boolean anchorHome = Math.abs(mAnchorPx) <= SETTLE_OFFSET_PX;
        mMoving = maxVelocity > SETTLE_VELOCITY_PX_PER_SEC
            || maxAccel > SETTLE_ACCEL_PX_PER_SEC2
            || (anchorHome && maxOffset > SETTLE_OFFSET_PX);
        return mMoving;
    }

    private static boolean isFinite(float value) {
        return !Float.isNaN(value) && !Float.isInfinite(value);
    }
}
