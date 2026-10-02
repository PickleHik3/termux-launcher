/*
 * Adapted from kitty's cursor-trail-particles.slang.
 * Copyright (C) 2026 Kovid Goyal <kovid at kovidgoyal.net>
 * Distributed under terms of the GPLv3 license.
 * SPDX-License-Identifier: GPL-3.0-only
 * Modified for Termux Launcher: run on the CPU, one result per particle instead of one coverage
 * value per pixel, in y-down pixels. See the repository LICENSE and THIRD_PARTY_NOTICES.md.
 */
package com.termux.app.terminal;

/**
 * The railgun, torpedo and pixiedust particle trails. kitty evaluates every particle for every
 * pixel in a fragment shader; here each particle is computed once and the overlay draws it as a
 * circle. Pure JVM, no Android types, so it is unit tested directly.
 */
public final class CursorTrailParticles {

    public static final int MODE_RAILGUN = 0;
    public static final int MODE_TORPEDO = 1;
    public static final int MODE_PIXIEDUST = 2;

    /** How many recent moves are remembered, newest first. */
    public static final int MAX_MOVES = 8;
    public static final int MAX_PARTICLES = 48;
    /** Size of the {@code out} array {@link #collect} needs: 64 particles per move, 4 floats each. */
    public static final int OUT_SIZE = MAX_MOVES * 64 * 4;

    static final float PARTICLE_DENSITY = 3.0f;
    public static final float LIFETIME = 0.8f;
    public static final float EMIT_DURATION = 0.08f;
    static final float PARTICLE_SIZE = 0.12f;
    static final float SPEED = 20.0f;
    static final float DRAG = 2.5f;
    static final float GRAVITY = 8.0f;
    static final float RAILGUN_PHASE = 1.5f;
    static final float TORPEDO_SPREAD = 1.0f;
    static final float OPACITY = 1.0f;
    static final float TAU = 6.28318530718f;
    // Noise constant from Dave Hoskins
    private static final float MOD4_X = 0.1031f, MOD4_Y = 0.1030f, MOD4_Z = 0.0973f, MOD4_W = 0.1099f;

    // Per move: from centre x,y, to centre x,y, scale, time, seed.
    private static final int FIELDS = 7;
    private final float[] mMoves = new float[MAX_MOVES * FIELDS];
    private int mCount;

    private final float[] mR = new float[4];
    private final float[] mR2 = new float[4];

    public void reset() {
        mCount = 0;
    }

    public int moveCount() {
        return mCount;
    }

    /** Remember an accepted move; the oldest of {@link #MAX_MOVES} falls off. */
    public void record(float fromL, float fromT, float fromR, float fromB,
                       float toL, float toT, float toR, float toB, float nowSeconds) {
        int keep = Math.min(mCount, MAX_MOVES - 1);
        System.arraycopy(mMoves, 0, mMoves, FIELDS, keep * FIELDS);
        mMoves[0] = (fromL + fromR) * 0.5f;
        mMoves[1] = (fromT + fromB) * 0.5f;
        mMoves[2] = (toL + toR) * 0.5f;
        mMoves[3] = (toT + toB) * 0.5f;
        mMoves[4] = Math.max(Math.max(toR - toL, toB - toT), 1.0f);
        mMoves[5] = nowSeconds;
        mMoves[6] = frac(nowSeconds * 0.731f) * 1000.0f;
        mCount = keep + 1;
    }

    /** Whether any remembered move is young enough to still show particles. */
    public boolean alive(float nowSeconds) {
        for (int m = 0; m < mCount; m++) {
            float time = mMoves[m * FIELDS + 5];
            if (time > 0f && nowSeconds - time < LIFETIME + EMIT_DURATION) return true;
        }
        return false;
    }

    /**
     * Writes x, y, radius, alpha for every live particle into {@code out}.
     *
     * @return how many particles were written
     */
    public int collect(float nowSeconds, int mode, float[] out) {
        int floats = 0;
        for (int m = 0; m < mCount; m++) {
            int o = m * FIELDS;
            float time = mMoves[o + 5];
            float age = nowSeconds - time;
            if (time <= 0f || age >= LIFETIME + EMIT_DURATION) break;
            float fx = mMoves[o], fy = mMoves[o + 1], tx = mMoves[o + 2], ty = mMoves[o + 3];
            float scale = mMoves[o + 4];
            float lines = length(tx - fx, ty - fy) / scale;
            floats = moveParticles(fx, fy, tx, ty, scale, lines, age, mMoves[o + 6], mode,
                out, floats);
        }
        return floats / 4;
    }

    /** @return the new write index into {@code out}, in floats */
    private int moveParticles(float fx, float fy, float tx, float ty, float scale, float lines,
                              float age, float seed, int mode, float[] out, int written) {
        float pathX = tx - fx, pathY = ty - fy;
        float pathLen = length(pathX, pathY);
        float dirX = pathLen > 0f ? pathX / pathLen : 1f;
        float dirY = pathLen > 0f ? pathY / pathLen : 0f;
        float perpX = -dirY, perpY = dirX;
        float speed = SPEED * scale;
        float radius = Math.max(PARTICLE_SIZE * scale, 0.75f);
        int count = (int) Math.ceil(lines * PARTICLE_DENSITY);
        count = Math.max(Math.min(4, MAX_PARTICLES), Math.min(count, MAX_PARTICLES));
        for (int i = 0; i < count; i++) {
            hash43(i, seed, 17.0f, mR);
            hash43(seed, i, 59.0f, mR2);
            float u = (i + mR[0]) / count;
            float t = age - u * EMIT_DURATION;
            float life = LIFETIME * lerp(0.6f, 1.0f, mR[1]);
            if (t < 0f || t >= life) continue;
            float startX = lerp(fx, tx, u) + (mR2[0] - 0.5f) * scale * 0.5f;
            float startY = lerp(fy, ty, u) + (mR2[1] - 0.5f) * scale * 0.5f;
            float velX, velY, fallY = 0f, brightness = 1f;
            if (mode == MODE_TORPEDO) {
                float angle = (mR[2] * 2f - 1f) * TORPEDO_SPREAD;
                float k = lerp(0.4f, 1.0f, mR[3]);
                velX = rotX(-dirX, -dirY, angle) * k;
                velY = rotY(-dirX, -dirY, angle) * k;
            } else if (mode == MODE_PIXIEDUST) {
                float angle = mR[2] * TAU;
                float k = lerp(0.2f, 1.0f, mR[3]);
                velX = rotX(0.4f, 0f, angle) * k;
                velY = rotY(0.4f, 0f, angle) * k;
                // y-down: gravity pulls toward +y.
                fallY = 0.5f * GRAVITY * scale * t * t;
                brightness = 0.55f + 0.45f * (float) Math.sin(t * 40.0f + mR2[2] * TAU);
            } else {
                float angle = u * lines * RAILGUN_PHASE;
                float k = lerp(0.7f, 1.0f, mR[3]);
                velX = rotX(perpX, perpY, angle) * k;
                velY = rotY(perpX, perpY, angle) * k;
            }
            float dist = speed * dragDistance(t);
            float fade = 1f - t / life;
            out[written] = startX + velX * dist;
            out[written + 1] = startY + velY * dist + fallY;
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
