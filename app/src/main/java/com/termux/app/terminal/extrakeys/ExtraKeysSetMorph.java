package com.termux.app.terminal.extrakeys;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * The plan for changing a page from one key set to another: a key found in both sets stays and
 * glides to its new cell, a key only in the new set enters, a key only in the old set leaves. Pure
 * numbers, no views: the page applies the values to translation, scale and alpha, which draw
 * without a layout pass.
 */
public final class ExtraKeysSetMorph {

    public static final int MORPH_MS = 180;
    public static final float LEAVE_SCALE = 0.5f;
    /** Control points of the ease the morph runs on. */
    public static final float EASE_X1 = 0.3f;
    public static final float EASE_Y1 = 0.2f;
    public static final float EASE_X2 = 0.05f;
    public static final float EASE_Y2 = 1f;

    /** Marks a new key with no old one. */
    public static final int ENTER = -1;

    private ExtraKeysSetMorph() {}

    /** What the page does with each key; arrays are indexed by new key or by old key as named. */
    public static final class Plan {
        /** Per new key: the old key it continues, or {@link #ENTER}. */
        @NonNull public final int[] oldOfNew;
        /** Per old key: true when no new key continues it. */
        @NonNull public final boolean[] leaving;
        /** Per new key, at t=0: translation of its centre and scale that put it on the old rect. */
        @NonNull public final float[] fromDx;
        @NonNull public final float[] fromDy;
        @NonNull public final float[] fromScaleX;
        @NonNull public final float[] fromScaleY;

        Plan(int newCount, int oldCount) {
            oldOfNew = new int[newCount];
            leaving = new boolean[oldCount];
            fromDx = new float[newCount];
            fromDy = new float[newCount];
            fromScaleX = new float[newCount];
            fromScaleY = new float[newCount];
        }
    }

    /**
     * For each new key the old key with an equal {@link KeyId}, or {@link #ENTER}. Each old key is
     * used once; the occurrence inside the id keeps duplicate keys apart.
     */
    @NonNull
    public static int[] match(@NonNull KeyId[] oldIds, @NonNull KeyId[] newIds) {
        Map<KeyId, Integer> byId = new HashMap<>();
        for (int i = 0; i < oldIds.length; i++) byId.put(oldIds[i], i);
        int[] result = new int[newIds.length];
        for (int i = 0; i < newIds.length; i++) {
            Integer old = byId.remove(newIds[i]);
            result[i] = old == null ? ENTER : old;
        }
        return result;
    }

    /**
     * @param oldRects {@code left, top, right, bottom} per old key, in the parent's coordinates
     * @param newRects the same per new key
     */
    @NonNull
    public static Plan plan(@NonNull KeyId[] oldIds, @NonNull float[] oldRects,
                            @NonNull KeyId[] newIds, @NonNull float[] newRects) {
        Plan plan = new Plan(newIds.length, oldIds.length);
        int[] match = match(oldIds, newIds);
        java.util.Arrays.fill(plan.leaving, true);
        for (int n = 0; n < newIds.length; n++) {
            int o = match[n];
            plan.oldOfNew[n] = o;
            plan.fromScaleX[n] = 1f;
            plan.fromScaleY[n] = 1f;
            if (o == ENTER) continue;
            plan.leaving[o] = false;
            float oldW = oldRects[o * 4 + 2] - oldRects[o * 4];
            float oldH = oldRects[o * 4 + 3] - oldRects[o * 4 + 1];
            float newW = newRects[n * 4 + 2] - newRects[n * 4];
            float newH = newRects[n * 4 + 3] - newRects[n * 4 + 1];
            plan.fromDx[n] = (oldRects[o * 4] + oldRects[o * 4 + 2]) / 2f
                - (newRects[n * 4] + newRects[n * 4 + 2]) / 2f;
            plan.fromDy[n] = (oldRects[o * 4 + 1] + oldRects[o * 4 + 3]) / 2f
                - (newRects[n * 4 + 1] + newRects[n * 4 + 3]) / 2f;
            if (newW > 0f) plan.fromScaleX[n] = oldW / newW;
            if (newH > 0f) plan.fromScaleY[n] = oldH / newH;
        }
        return plan;
    }

    private static float clamp(float t) {
        return t < 0f ? 0f : Math.min(t, 1f);
    }

    /** A staying key's translation at eased fraction t: {@code from} at 0, none at 1. */
    public static float stayTranslation(float from, float t) {
        return from * (1f - clamp(t));
    }

    /** A staying key's scale at eased fraction t: {@code from} at 0, 1 at 1. */
    public static float stayScale(float from, float t) {
        float f = clamp(t);
        return from + (1f - from) * f;
    }

    public static float enterScale(float t) {
        return LEAVE_SCALE + (1f - LEAVE_SCALE) * clamp(t);
    }

    public static float enterAlpha(float t) {
        return clamp(t);
    }

    public static float leaveScale(float t) {
        return 1f - (1f - LEAVE_SCALE) * clamp(t);
    }

    public static float leaveAlpha(float t) {
        return 1f - clamp(t);
    }
}
