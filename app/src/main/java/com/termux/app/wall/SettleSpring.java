package com.termux.app.wall;

/**
 * The settle a finger hands its motion to: the wall's slide after a border drag lets go, the
 * keyboard's reveal after a keyboard swipe lets go, and the slide a tile or {@code wall.go} asks
 * for with no finger at all. A critically damped spring on one number — the displacement from
 * where the motion comes to rest — that starts where the finger left it and at the finger's own
 * speed, so the release has no seam in position or in velocity, and that comes to rest without
 * passing it, so it lands without a wobble.
 *
 * <p>A critically damped spring released at {@code x0} moving at {@code v0} passes its rest only
 * when it is thrown at it faster than {@code omega * |x0|}. So a throw like that stiffens the
 * spring to exactly the speed it was given ({@link #of}): a flick released close to its rest
 * lands sooner rather than sailing past it. Past the stiffest spring allowed, the one throw left
 * that could still pass its rest is held there from the crossing on.
 *
 * <p>Pure, so the curve can be tested as arithmetic; the views feed it their own clock.
 */
public final class SettleSpring {

    /** How far {@link #durationMs} looks before it gives up: no settle is longer than this. */
    static final long MAX_DURATION_MS = 3000L;
    private static final long STEP_MS = 4L;

    private final float mFrom;
    private final float mVelocity;
    private final float mOmega;

    private SettleSpring(float from, float velocity, float omega) {
        mFrom = from;
        mVelocity = velocity;
        mOmega = omega;
    }

    /**
     * The spring for a motion {@code displacement} from its rest, moving at {@code velocity} (the
     * same units per second, the same sign convention), with a natural frequency of
     * {@code baseOmega} per second unless the throw needs a stiffer one, and never one stiffer
     * than {@code maxOmega}.
     */
    public static SettleSpring of(float displacement, float velocity, float baseOmega,
                                  float maxOmega) {
        float from = Float.isNaN(displacement) ? 0f : displacement;
        float v = Float.isNaN(velocity) ? 0f : velocity;
        float omega = Math.max(1f, baseOmega);
        // Thrown at its rest faster than the spring would take it there itself: stiffen it to
        // that speed, so it arrives on the throw instead of overshooting.
        if (from != 0f && v * from < 0f) omega = Math.max(omega, Math.abs(v / from));
        omega = Math.min(omega, Math.max(1f, maxOmega));
        return new SettleSpring(from, v, omega);
    }

    /** The natural frequency the spring was given, per second. */
    public float omega() {
        return mOmega;
    }

    /** The displacement from rest {@code seconds} after the release. */
    public float displacementAt(float seconds) {
        if (!(seconds > 0f)) return mFrom;
        double t = seconds;
        double x = (mFrom + (mVelocity + mOmega * mFrom) * t) * Math.exp(-mOmega * t);
        // Only a throw past the stiffest spring can cross: it is held at rest from there on.
        if (mFrom != 0f && x * mFrom < 0.0) return 0f;
        return (float) x;
    }

    /** The speed {@code seconds} after the release: the finger's own at 0. */
    public float velocityAt(float seconds) {
        if (!(seconds > 0f)) return mVelocity;
        double t = seconds;
        double x = (mFrom + (mVelocity + mOmega * mFrom) * t) * Math.exp(-mOmega * t);
        if (mFrom != 0f && x * mFrom < 0.0) return 0f;
        return (float) ((mVelocity - mOmega * (mVelocity + mOmega * mFrom) * t)
            * Math.exp(-mOmega * t));
    }

    /**
     * How long the spring takes to come within {@code restDisplacement} of its rest for good, in
     * ms: at least 1, at most {@link #MAX_DURATION_MS}. A spring thrown away from its rest goes
     * out first and comes back, so this is the last moment it is still further out than that.
     */
    public long durationMs(float restDisplacement) {
        float rest = Math.max(1e-6f, Math.abs(restDisplacement));
        // The last step still further out; one before the first when it never was.
        long last = -STEP_MS;
        for (long ms = 0L; ms <= MAX_DURATION_MS; ms += STEP_MS) {
            if (Math.abs(displacementAt(ms / 1000f)) > rest) last = ms;
        }
        return Math.max(1L, Math.min(MAX_DURATION_MS, last + STEP_MS));
    }
}
