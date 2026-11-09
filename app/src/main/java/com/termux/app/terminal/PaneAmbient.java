package com.termux.app.terminal;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Random;

/**
 * The ambient parts of a CRT pane, played only under the Idle animations switch (spec decision 4
 * and 10): a gentle flicker and, at the Idle glitch rate ({@link Rate}), a short tear
 * ({@link PaneScreenEvent.Kind#AMBIENT_TEAR}). Pure arithmetic; {@link RetroEffectBinder} runs the
 * low-rate loop and {@link PaneRetroEffect} draws it from {@link RetroUniforms#flicker} and
 * {@link RetroUniforms#time}.
 */
public final class PaneAmbient {

    /** The loop's tick: about 20 frames a second while it runs. */
    public static final long TICK_MS = 50L;
    /** The flicker's depth while the loop runs: a few percent (10-22 spec decision 5). */
    public static final float FLICKER_MAX = 0.03f;
    /**
     * Where the flicker's clock wraps, in seconds. Both of the shader's sines (1.3 Hz and 0.37 Hz)
     * run a whole number of cycles in it, so the wrap never shows as a step.
     */
    public static final float TIME_WRAP_S = 1000f;
    /** The shortest and longest wait for the next ambient tear at the default, {@link Rate#RARE}. */
    public static final long TEAR_MIN_MS = 2L * 60_000L;
    public static final long TEAR_MAX_MS = 5L * 60_000L;

    /** The Idle glitch rate: how long an idle pane waits between ambient tears. */
    public enum Rate {
        RARE("rare", TEAR_MIN_MS, TEAR_MAX_MS),
        SOMETIMES("sometimes", 30_000L, 90_000L),
        OFTEN("often", 10_000L, 30_000L);

        /** The stored value ({@code idle_tear_rate}). */
        @NonNull public final String id;
        public final long minMs;
        public final long maxMs;

        Rate(@NonNull String id, long minMs, long maxMs) {
            this.id = id;
            this.minMs = minMs;
            this.maxMs = maxMs;
        }

        /** The rate stored as {@code id}; {@link #RARE} for null or anything unknown. */
        @NonNull
        public static Rate fromId(@Nullable String id) {
            for (Rate r : values()) if (r.id.equals(id)) return r;
            return RARE;
        }
    }

    private PaneAmbient() {}

    /**
     * Whether the ambient loop may play now: only on a CRT pane with Idle animations on, while
     * Android animates, outside Lazy mode and power save, with the screen on, the pane shown in a
     * visible window, and no key or scroll within {@link PaneScreenEvent#INPUT_QUIET_MS}.
     */
    public static boolean ambientAllowed(boolean crt, boolean switchOn, boolean animatorsEnabled,
                                         boolean lazy, boolean powerSave, boolean screenOn,
                                         boolean shown, boolean windowVisible,
                                         boolean inputQuiet) {
        return crt && switchOn && animatorsEnabled && !lazy && !powerSave && screenOn && shown
            && windowVisible && inputQuiet;
    }

    /** How long until the next ambient tear: uniform in [{@link #TEAR_MIN_MS}, {@link #TEAR_MAX_MS}]. */
    public static long nextTearDelayMs(@NonNull Random random) {
        return nextTearDelayMs(random, Rate.RARE);
    }

    /** How long until the next ambient tear at {@code rate}: uniform in its [min, max]. */
    public static long nextTearDelayMs(@NonNull Random random, @NonNull Rate rate) {
        return rate.minMs + (long) (random.nextDouble() * (rate.maxMs - rate.minMs));
    }

    /** The flicker's depth: {@link #FLICKER_MAX} while the loop plays, 0 otherwise. */
    public static float flickerAmplitude(boolean playing) {
        return playing ? FLICKER_MAX : 0f;
    }

    /** The flicker's clock at {@code elapsedMs} since the loop's origin, in [0, {@link #TIME_WRAP_S}). */
    public static float clockSeconds(long elapsedMs) {
        if (elapsedMs <= 0L) return 0f;
        long wrapMs = (long) (TIME_WRAP_S * 1000f);
        return (elapsedMs % wrapMs) / 1000f;
    }

    /**
     * The flicker's dimming at {@code t} seconds and depth {@code flicker}, as the shader works it
     * out: a share of brightness in [0, flicker].
     */
    public static float flickerShade(float flicker, float t) {
        double n = Math.sin(t * 2.0 * Math.PI * 1.3) * 0.6
            + Math.sin(t * 2.0 * Math.PI * 0.37 + 1.7) * 0.4;
        return (float) (flicker * (0.5 + 0.5 * n));
    }
}
