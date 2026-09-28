package com.termux.ai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The battery and thermal safety rules of {@code project-docs/benchmark/SPEC.md}'s Safety table,
 * as pure functions of a {@link Snapshot}: no Android type, no clock, no I/O, so the table of
 * cases in {@code TaiBenchGuardRulesTest} exercises the rules directly. {@link
 * TaiBenchConditionsGuard} is the only caller; it holds the state (the baseline snapshot, the
 * wait clock, which entry started warm) this class is pure of.
 */
public final class TaiBenchGuardRules {
    /** Start check: battery must be at least this or charging. */
    public static final int START_BATTERY_MIN_PERCENT = 30;
    /** While running: battery below this and not charging stops the run. */
    public static final int RUNNING_BATTERY_STOP_PERCENT = 15;

    // PowerManager.THERMAL_STATUS_* values, repeated here so this class imports no Android type.
    public static final int THERMAL_STATUS_NONE = 0;
    public static final int THERMAL_STATUS_LIGHT = 1;
    public static final int THERMAL_STATUS_MODERATE = 2;
    public static final int THERMAL_STATUS_SEVERE = 3;
    static final int THERMAL_STATUS_CRITICAL = 4;
    static final int THERMAL_STATUS_EMERGENCY = 5;
    static final int THERMAL_STATUS_SHUTDOWN = 6;

    /** While running: how often a MODERATE-thermal pause asks again. */
    static final long THERMAL_POLL_MS = 5_000L;
    /** While running: a MODERATE-thermal pause held this long in total stops the run. */
    public static final long THERMAL_TIMEOUT_MS = 10 * 60_000L;
    /** Cool-down: how often a wait asks again. */
    static final long COOLDOWN_POLL_MS = 2_000L;
    /** Cool-down: waited this long without recovering proceeds anyway, marked warm. */
    public static final long COOLDOWN_CAP_MS = 5 * 60_000L;
    /** Cool-down: the headroom is "recovered" within this much of the baseline's. */
    static final float HEADROOM_TOLERANCE = 0.05f;

    /** How often a "left the screen" hold pause asks again. */
    static final long HELD_POLL_MS = 1_000L;
    /** A hold held this long in total stops the run. */
    public static final long HELD_TIMEOUT_MS = 30 * 60_000L;

    private TaiBenchGuardRules() {
    }

    /** One reading of the phone's state; {@code -1}/{@code NaN} means unknown. */
    public static final class Snapshot {
        public final int batteryPercent;
        public final boolean charging;
        public final int thermalStatus;
        public final float headroom;

        public Snapshot(int batteryPercent, boolean charging, int thermalStatus, float headroom) {
            this.batteryPercent = batteryPercent;
            this.charging = charging;
            this.thermalStatus = thermalStatus;
            this.headroom = headroom;
        }

        public static final Snapshot UNKNOWN = new Snapshot(-1, false, -1, Float.NaN);
    }

    /** {@code beforePhase}'s decision plus whether this call resolved a cool-down as warm. */
    static final class Result {
        @NonNull final TaiBenchGuard.Decision decision;
        final boolean warmStart;

        Result(@NonNull TaiBenchGuard.Decision decision, boolean warmStart) {
            this.decision = decision;
            this.warmStart = warmStart;
        }
    }

    /**
     * Before the run starts: battery must be at or above {@link #START_BATTERY_MIN_PERCENT} or
     * charging (an unknown battery passes); thermal must be NONE or LIGHT (an unknown thermal
     * status passes). {@code null} means go ahead.
     */
    @Nullable
    public static String startCheck(@NonNull Snapshot snapshot) {
        if (snapshot.batteryPercent >= 0 && snapshot.batteryPercent < START_BATTERY_MIN_PERCENT && !snapshot.charging) {
            return "battery_low";
        }
        if (snapshot.thermalStatus > THERMAL_STATUS_LIGHT) {
            return "too_hot";
        }
        return null;
    }

    /**
     * The while-running and cool-down rules combined, as one function of exactly what the guard
     * knows: the phase about to start, whether {@code entry} is the run's first entry, the current
     * and baseline (run-start) snapshots, and the clock. Cool-down applies only to
     * {@link TaiBenchSuite#PHASE_LOAD} of an entry after the first; every other call only weighs
     * the running battery/thermal rules. A battery stop or a SEVERE+ thermal stop is checked first
     * and wins over a cool-down wait or a "left the screen" hold in progress; {@code held} is
     * checked next, before cool-down/thermal pauses, so the screen going away pre-empts them.
     *
     * @param waitStartedMs when the current wait (cool-down, thermal or held pause) began, or a
     *                       negative value if this call might start one, which reads as "just
     *                       started" (no time has passed yet)
     * @param nowMs          the clock's reading for this call
     * @param held           whether the in-app screen that owns this run has left the foreground
     */
    @NonNull
    static Result beforePhase(@NonNull String phase, boolean isFirstEntry, @NonNull Snapshot now,
                               @Nullable Snapshot baseline, long waitStartedMs, long nowMs, boolean skipRequested,
                               boolean held) {
        if (now.batteryPercent >= 0 && now.batteryPercent < RUNNING_BATTERY_STOP_PERCENT && !now.charging) {
            return new Result(TaiBenchGuard.Decision.stop("battery_low"), false);
        }
        if (now.thermalStatus >= THERMAL_STATUS_SEVERE) {
            return new Result(TaiBenchGuard.Decision.stop("thermal"), false);
        }
        long elapsedMs = waitStartedMs < 0L ? 0L : Math.max(0L, nowMs - waitStartedMs);
        if (held) {
            if (elapsedMs >= HELD_TIMEOUT_MS) {
                return new Result(TaiBenchGuard.Decision.stop("left"), false);
            }
            return new Result(TaiBenchGuard.Decision.pause(HELD_POLL_MS, "left"), false);
        }
        boolean cooldownApplies = TaiBenchSuite.PHASE_LOAD.equals(phase) && !isFirstEntry;
        if (cooldownApplies) {
            // An entirely unknown thermal reading (API < 29, or the baseline snapshot never got
            // one) has nothing to compare against, so there is nothing to wait for.
            if (baseline == null || baseline.thermalStatus < 0) {
                return new Result(TaiBenchGuard.Decision.proceed(), false);
            }
            if (skipRequested) {
                return new Result(TaiBenchGuard.Decision.proceed(), true);
            }
            boolean statusRecovered = now.thermalStatus <= baseline.thermalStatus;
            boolean headroomRecovered = Float.isNaN(now.headroom) || Float.isNaN(baseline.headroom)
                || now.headroom <= baseline.headroom + HEADROOM_TOLERANCE;
            if (statusRecovered && headroomRecovered) {
                return new Result(TaiBenchGuard.Decision.proceed(), false);
            }
            if (elapsedMs >= COOLDOWN_CAP_MS) {
                return new Result(TaiBenchGuard.Decision.proceed(), true);
            }
            return new Result(TaiBenchGuard.Decision.pause(COOLDOWN_POLL_MS, "cooldown"), false);
        }
        if (now.thermalStatus == THERMAL_STATUS_MODERATE) {
            if (elapsedMs >= THERMAL_TIMEOUT_MS) {
                return new Result(TaiBenchGuard.Decision.stop("thermal_timeout"), false);
            }
            return new Result(TaiBenchGuard.Decision.pause(THERMAL_POLL_MS, "thermal"), false);
        }
        return new Result(TaiBenchGuard.Decision.proceed(), false);
    }

    /** {@code none|light|moderate|severe|critical|emergency|shutdown}, or {@code null} when unknown. */
    @Nullable
    public static String thermalStatusName(int status) {
        switch (status) {
            case THERMAL_STATUS_NONE: return "none";
            case THERMAL_STATUS_LIGHT: return "light";
            case THERMAL_STATUS_MODERATE: return "moderate";
            case THERMAL_STATUS_SEVERE: return "severe";
            case THERMAL_STATUS_CRITICAL: return "critical";
            case THERMAL_STATUS_EMERGENCY: return "emergency";
            case THERMAL_STATUS_SHUTDOWN: return "shutdown";
            default: return null;
        }
    }
}
