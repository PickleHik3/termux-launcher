/*
 * Adapted from kitty's cursor-trail-particles.slang.
 * Copyright (C) 2026 Kovid Goyal <kovid at kovidgoyal.net>
 * Distributed under terms of the GPLv3 license.
 * SPDX-License-Identifier: GPL-3.0-only
 * Modified for Termux Launcher: run on the CPU, one result per particle instead of one coverage
 * value per pixel, fed and returning y-down pixels, and a per-move sequence number mixed into the
 * seed; only the pixiedust mode is kept, as Railgun and Torpedo are drawn by their own classes.
 * See the repository LICENSE and THIRD_PARTY_NOTICES.md.
 */
package com.termux.app.terminal;

/**
 * The Pixie dust particle trail. kitty evaluates every particle for every pixel in a fragment
 * shader; here each particle is computed once and the overlay draws it as a circle. Pure JVM, no Android types, so it is unit tested directly.
 *
 * <p>Like kitty's {@code cursor_trail_history_*} uniforms, the moves are remembered newest first
 * with the time each was made, and a particle's age is simply now minus that time: a move keeps
 * its own age however long the overlay sleeps between frames, and an old move can only ever get
 * older, so nothing replays. The maths runs in kitty's y-up space (y is negated on the way in and
 * out) so that every particle flies and falls the way kitty's do.
 */
public final class CursorTrailParticles {

    /** How many recent moves are remembered, newest first: kitty's CURSOR_TRAIL_HISTORY_SIZE. */
    public static final int MAX_MOVES = 8;
    public static final int MAX_PARTICLES = 48;
    /** Size of the {@code out} array {@link #collect} needs: 64 particles per move, 4 floats each. */
    public static final int OUT_SIZE = MAX_MOVES * 64 * 4;

    // The options at the top of cursor-trail-particles.slang, at their shipped values.
    static final float PARTICLE_DENSITY = 3.0f;
    public static final float LIFETIME = 0.8f;
    public static final float EMIT_DURATION = 0.08f;
    static final float PARTICLE_SIZE = 0.12f;
    static final float SPEED = 20.0f;
    static final float DRAG = 2.5f;
    static final float GRAVITY = 8.0f;
    static final float OPACITY = 1.0f;
    static final float TAU = 6.28318530718f;
    /** How long after its move a particle can still be alive, in milliseconds. */
    public static final long MAX_AGE_MS = (long) Math.ceil((LIFETIME + EMIT_DURATION) * 1000f);
    // Noise constant from Dave Hoskins
    private static final float MOD4_X = 0.1031f, MOD4_Y = 0.1030f, MOD4_Z = 0.0973f, MOD4_W = 0.1099f;

    // Per move, in kitty's y-up pixels: from centre x,y, to centre x,y, scale, seed.
    private static final int FIELDS = 6;
    private final float[] mMoves = new float[MAX_MOVES * FIELDS];
    /** When each move was made, on the caller's millisecond clock. */
    private final long[] mAt = new long[MAX_MOVES];
    private int mCount;
    /** Moves recorded so far; mixed into each seed so two identical moves still differ. */
    private long mSequence;

    private final float[] mR = new float[4];
    private final float[] mR2 = new float[4];

    public void reset() {
        mCount = 0;
    }

    public int moveCount() {
        return mCount;
    }

    /**
     * Remember an accepted move, made at {@code nowMs}; the oldest of {@link #MAX_MOVES} falls off.
     * Rects are in y-down pixels, as the overlay has them.
     */
    public void record(float fromL, float fromT, float fromR, float fromB,
                       float toL, float toT, float toR, float toB, long nowMs) {
        int keep = Math.min(mCount, MAX_MOVES - 1);
        System.arraycopy(mMoves, 0, mMoves, FIELDS, keep * FIELDS);
        System.arraycopy(mAt, 0, mAt, 1, keep);
        float fx = (fromL + fromR) * 0.5f, fy = -(fromT + fromB) * 0.5f;
        float tx = (toL + toR) * 0.5f, ty = -(toT + toB) * 0.5f;
        mMoves[0] = fx;
        mMoves[1] = fy;
        mMoves[2] = tx;
        mMoves[3] = ty;
        // The width of a beam cursor and the height of an underline cursor are tiny, so use the
        // larger of the two dimensions for scale.
        mMoves[4] = Math.max(Math.max(toR - toL, toB - toT), 1.0f);
        // kitty: "the seed only needs to be stable over the lifetime of the move, so derive it from
        // the end points of the move". The sequence term keeps a repeat of the very same move (two
        // panes' prompts, a key held between two places) from repeating the same pattern.
        double seed = (tx - fx) * 0.1371 + (ty - fy) * 0.2113 + tx * 0.0173 + ty * 0.0291
            + (mSequence++ % 4096L) * 0.6180339887;
        mMoves[5] = (float) ((seed - Math.floor(seed)) * 1000.0);
        mAt[0] = nowMs;
        mCount = keep + 1;
    }

    /** Whether any remembered move is young enough to still show particles at {@code nowMs}. */
    public boolean alive(long nowMs) {
        // Newest first, so the newest move decides: anything older is older still.
        if (mCount == 0) return false;
        long age = nowMs - mAt[0];
        return age >= 0L && age < MAX_AGE_MS;
    }

    /**
     * Writes x, y, radius, alpha for every live particle at {@code nowMs} into {@code out}, in
     * y-down pixels.
     *
     * @return how many particles were written
     */
    public int collect(long nowMs, float[] out) {
        int floats = 0;
        for (int m = 0; m < mCount; m++) {
            float age = (nowMs - mAt[m]) / 1000f;
            // Moves are most recent first, so all remaining ones are older still.
            if (age < 0f || age >= LIFETIME + EMIT_DURATION) break;
            int o = m * FIELDS;
            float fx = mMoves[o], fy = mMoves[o + 1], tx = mMoves[o + 2], ty = mMoves[o + 3];
            float scale = mMoves[o + 4];
            float lines = length(tx - fx, ty - fy) / scale;
            floats = moveParticles(fx, fy, tx, ty, scale, lines, age, mMoves[o + 5], out,
                floats);
        }
        return floats / 4;
    }

    /**
     * kitty's {@code move_particles} in its pixiedust mode, one particle at a time.
     *
     * @return the new write index
     */
    private int moveParticles(float fx, float fy, float tx, float ty, float scale, float lines,
                              float age, float seed, float[] out, int written) {
        float speed = SPEED * scale;
        float radius = Math.max(PARTICLE_SIZE * scale, 0.75f);
        int count = (int) Math.ceil(lines * PARTICLE_DENSITY);
        count = Math.max(Math.min(4, MAX_PARTICLES), Math.min(count, MAX_PARTICLES));
        for (int i = 0; i < count; i++) {
            hash43(i, seed, 17.0f, mR);
            hash43(seed, i, 59.0f, mR2);
            // Where along the path the particle starts, 0 is the old cursor position
            float u = (i + mR[0]) / count;
            float t = age - u * EMIT_DURATION;
            float life = LIFETIME * lerp(0.6f, 1.0f, mR[1]);
            if (t < 0f || t >= life) continue;
            float startX = lerp(fx, tx, u) + (mR2[0] - 0.5f) * scale * 0.5f;
            float startY = lerp(fy, ty, u) + (mR2[1] - 0.5f) * scale * 0.5f;
            float angle = mR[2] * TAU;
            float k = lerp(0.2f, 1.0f, mR[3]);
            float velX = rotX(0.4f, 0f, angle) * k;
            float velY = rotY(0.4f, 0f, angle) * k;
            float fallY = -0.5f * GRAVITY * scale * t * t;
            // Glitter
            float brightness = 0.55f + 0.45f * (float) Math.sin(t * 40.0f + mR2[2] * TAU);
            float dist = speed * dragDistance(t);
            float fade = 1f - t / life;
            out[written] = startX + velX * dist;
            // Back to y-down.
            out[written + 1] = -(startY + velY * dist + fallY);
            // Particles shrink as they fade
            out[written + 2] = radius * lerp(0.5f, 1.0f, fade);
            out[written + 3] = Math.max(0f, Math.min(1f, fade * brightness * OPACITY));
            written += 4;
        }
        return written;
    }

    private static float dragDistance(float t) {
        return DRAG > 0f ? (1f - (float) Math.exp(-DRAG * t)) / DRAG : t;
    }

    private static float rotX(float x, float y, float a) {
        return (float) Math.cos(a) * x - (float) Math.sin(a) * y;
    }

    private static float rotY(float x, float y, float a) {
        return (float) Math.sin(a) * x + (float) Math.cos(a) * y;
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static float length(float x, float y) {
        return (float) Math.sqrt((double) x * x + (double) y * y);
    }

    private static float frac(float v) {
        return v - (float) Math.floor(v);
    }

    /** Dave Hoskins' hash43: three floats in, four floats in [0, 1) out. */
    static void hash43(float px, float py, float pz, float[] out) {
        float x = frac(px * MOD4_X), y = frac(py * MOD4_Y);
        float z = frac(pz * MOD4_Z), w = frac(px * MOD4_W);
        float d = x * (w + 33.33f) + y * (z + 33.33f) + z * (x + 33.33f) + w * (y + 33.33f);
        x += d;
        y += d;
        z += d;
        w += d;
        out[0] = frac((x + y) * z);
        out[1] = frac((x + z) * y);
        out[2] = frac((y + z) * w);
        out[3] = frac((z + w) * x);
    }
}
