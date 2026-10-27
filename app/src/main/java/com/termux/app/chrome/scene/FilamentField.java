package com.termux.app.chrome.scene;

/**
 * The drifting objects of the Filament scene: a fixed pool of nixie and neon tubes in three depth
 * bands, simulated in normalised screen space (x over the width, y over the height, both 0..1 on
 * screen), so the full-resolution pass and the 1/8 glass pass read the same field at their own
 * size. Pure Java, scene thread only, no allocation after construction, no Android types.
 *
 * <p>Time comes in as the scene clock, which already stands still during the typing hold, so the
 * motion continues from where it was. {@link #advanceTo} is idempotent for a time already
 * reached (two programs may call it with the same time) and never steps more than
 * {@link #MAX_STEP_S}, so a long gap cannot throw anything across the screen.</p>
 *
 * <p>Each object drifts at its band's speed, swelling and easing by a quarter either way (so
 * never below three quarters: nothing stops). A touch repels what is near it: the force adds to a
 * separate kick velocity that decays by itself, so the object is pushed and then drifts on.
 * Positions wrap past a margin that clears the biggest object.</p>
 */
final class FilamentField {

    static final int POOL = 12;
    static final int BANDS = 3;
    /** Objects per band; the pool is ordered far to near so a loop draws back to front. */
    static final int PER_BAND = POOL / BANDS;

    static final int NIXIE = 0;
    /** Neon shapes: kinds 1..4. */
    static final int NEON_BAR = 1;
    static final int NEON_ARC = 2;
    static final int NEON_S = 3;
    static final int NEON_ZIG = 4;
    /** Neon colours: pink, cyan, amber-orange. */
    static final int COLOURS = 3;

    /** Kind of each object in pool order (seven nixies, five neons, mixed through the bands). */
    private static final int[] KINDS = {
        NIXIE, NEON_ARC, NIXIE, NEON_BAR,
        NIXIE, NEON_ZIG, NIXIE, NIXIE,
        NIXIE, NEON_ARC, NIXIE, NEON_S,
    };
    static final int NIXIE_COUNT = 7;

    /** Object size unit by band (a nixie's width, a neon's half length), dp. */
    static final float[] SIZE_DP = {26f, 34f, 44f};
    /** Brightness by band. */
    static final float[] BRIGHT = {0.42f, 0.68f, 1f};
    /** Edge softness by band (1 = the sharpest). */
    static final float[] SOFT = {1.7f, 1.3f, 1f};
    /** Drift speed by band, screen heights per second. */
    static final float[] SPEED = {0.010f, 0.017f, 0.026f};
    /** How strongly a touch moves a band (parallax: near things move more). */
    static final float[] PUSH = {0.55f, 0.8f, 1f};

    /** Wrap margins past the screen edge, as a fraction of the width and height. */
    static final float MARGIN_X = 0.22f;
    static final float MARGIN_Y = 0.12f;
    /** The longest step one advance takes, seconds. */
    static final float MAX_STEP_S = 0.1f;
    /** How fast a touch accelerates what it repels at its centre, screen heights per second squared. */
    static final float REPEL_ACCEL = 0.6f;
    /** A kick fades with this time constant, seconds; its speed (screen heights per second) is capped. */
    static final float KICK_TAU_S = 1.1f;
    static final float KICK_MAX = 0.25f;
    static final int MAX_REPEL = 3;
    /** A nixie leans at most this far, radians, at this angular rate; a neon turns at most SPIN_MAX rad/s. */
    static final float TILT_MAX = 0.32f;
    static final float TILT_RATE = 0.22f;
    static final float SPIN_MAX = 0.12f;

    private final float[] mX = new float[POOL];
    private final float[] mY = new float[POOL];
    private final float[] mVx = new float[POOL];
    private final float[] mVy = new float[POOL];
    private final float[] mKx = new float[POOL];
    private final float[] mKy = new float[POOL];
    private final float[] mSpin = new float[POOL];
    private final float[] mAngle = new float[POOL];
    private final float[] mPhase = new float[POOL];
    private final float[] mRepel = new float[MAX_REPEL * 4];
    private float mAspect = 0.5f;
    private float mTime;
    private long mLastMs = Long.MIN_VALUE;

    FilamentField() {
        // A fixed arrangement from a small integer hash: the same scene every start.
        for (int i = 0; i < POOL; i++) {
            int band = band(i);
            float a = hash(i, 1) * 6.2831853f;
            mX[i] = -MARGIN_X + hash(i, 2) * (1f + 2f * MARGIN_X);
            mY[i] = -MARGIN_Y + hash(i, 3) * (1f + 2f * MARGIN_Y);
            mVx[i] = (float) Math.cos(a) * SPEED[band];
            mVy[i] = (float) Math.sin(a) * SPEED[band];
            mPhase[i] = hash(i, 4) * 6.2831853f;
            if (kind(i) != NIXIE) {
                mSpin[i] = (hash(i, 5) - 0.5f) * 2f * SPIN_MAX;
                mAngle[i] = (hash(i, 6) - 0.5f) * 6.2831853f;
            }
        }
    }

    static int band(int i) {
        return i / PER_BAND;
    }

    static int kind(int i) {
        return KINDS[i];
    }

    /** The glyph slot of a nixie among the nixies (0..6), or -1 for a neon. */
    static int nixieSlot(int i) {
        if (KINDS[i] != NIXIE) return -1;
        int n = 0;
        for (int k = 0; k < i; k++) if (KINDS[k] == NIXIE) n++;
        return n;
    }

    /** A neon's colour 0..2, stable per object. */
    static int colour(int i) {
        return (i * 5 + 1) % COLOURS;
    }

    private static float hash(int i, int salt) {
        int h = i * 374761393 + salt * 668265263;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= h >>> 16;
        return (h & 0xFFFFFF) / (float) 0x1000000;
    }

    /** The screen's width over its height, for distances in the field. */
    void setAspect(float aspect) {
        if (aspect > 0.05f) mAspect = aspect;
    }

    /** Touch {@code i}: a repeller at normalised (x, y), strength {@code amp} (0 is off), radius in screen heights. */
    void setRepel(int i, float x, float y, float amp, float radius) {
        mRepel[i * 4] = x;
        mRepel[i * 4 + 1] = y;
        mRepel[i * 4 + 2] = amp;
        mRepel[i * 4 + 3] = radius;
    }

    /** Moves everything on to scene time {@code ms}; a time not after the last one moves nothing. */
    void advanceTo(long ms) {
        if (mLastMs == Long.MIN_VALUE) {
            mLastMs = ms;
            return;
        }
        if (ms <= mLastMs) {
            mLastMs = ms;
            return;
        }
        float dt = Math.min(MAX_STEP_S, (ms - mLastMs) / 1000f);
        mLastMs = ms;
        step(dt);
    }

    private void step(float dt) {
        mTime += dt;
        float decay = (float) Math.exp(-dt / KICK_TAU_S);
        float w = 1f + 2f * MARGIN_X, h = 1f + 2f * MARGIN_Y;
        for (int i = 0; i < POOL; i++) {
            int band = band(i);
            float x = mX[i], y = mY[i];
            for (int r = 0; r < MAX_REPEL; r++) {
                float amp = mRepel[r * 4 + 2];
                if (amp <= 0f) continue;
                float rad = mRepel[r * 4 + 3];
                float dx = (x - mRepel[r * 4]) * mAspect;
                float dy = y - mRepel[r * 4 + 1];
                float d2 = dx * dx + dy * dy;
                if (d2 >= rad * rad) continue;
                float d = (float) Math.sqrt(d2);
                float f = 1f - d / rad;
                float k = amp * f * f * REPEL_ACCEL * PUSH[band] * dt;
                // Straight out from the finger; right on top of it, up.
                float nx = d > 1e-4f ? dx / d : 0f;
                float ny = d > 1e-4f ? dy / d : -1f;
                mKx[i] += nx * k / mAspect;
                mKy[i] += ny * k;
            }
            float kx = mKx[i] * decay, ky = mKy[i] * decay;
            float kxh = kx * mAspect;
            float sp = (float) Math.sqrt(kxh * kxh + ky * ky);
            if (sp > KICK_MAX) {
                float s = KICK_MAX / sp;
                kx *= s;
                ky *= s;
            }
            mKx[i] = kx;
            mKy[i] = ky;
            // The drift swells and eases between 0.75x and 1.25x of the band's speed. Its
            // velocity is in screen heights per second, so x divides by the aspect.
            float swell = 1f + 0.25f * (float) Math.sin(0.35f * mTime + mPhase[i]);
            x += mVx[i] * swell * dt / mAspect + kx * dt;
            y += mVy[i] * swell * dt + ky * dt;
            if (x > 1f + MARGIN_X) x -= w;
            else if (x < -MARGIN_X) x += w;
            if (y > 1f + MARGIN_Y) y -= h;
            else if (y < -MARGIN_Y) y += h;
            mX[i] = x;
            mY[i] = y;
            if (kind(i) != NIXIE) {
                float a = mAngle[i] + mSpin[i] * dt;
                if (a > 3.1415927f) a -= 6.2831853f;
                else if (a < -3.1415927f) a += 6.2831853f;
                mAngle[i] = a;
            }
        }
    }

    float x(int i) {
        return mX[i];
    }

    float y(int i) {
        return mY[i];
    }

    /** The object's rotation, radians: a nixie leans slowly either way, a neon turns freely. */
    float angle(int i) {
        if (kind(i) == NIXIE) return TILT_MAX * (float) Math.sin(TILT_RATE * mTime + mPhase[i]);
        return mAngle[i];
    }

    /** The kick's speed now, screen heights per second. */
    float kickSpeed(int i) {
        return (float) Math.hypot(mKx[i] * mAspect, mKy[i]);
    }
}
