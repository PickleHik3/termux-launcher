package com.termux.ai;

import androidx.annotation.NonNull;

import java.util.List;

/**
 * Picks the diffusion engine's memory mode for one image run and checks it against free memory.
 * Pure: the caller supplies the device numbers, the evictable residents and, when a load on this
 * device has been measured before, what each mode cost.
 *
 * <p>Modes are the engine's own: 1 keeps every module resident (fastest), 2 is the balance, 0 is
 * memory saving. Left to choose, the fastest mode that fits wins: mode 1 when the whole-model
 * estimate fits with the evictable residents (idle embeddings, speech, chat) closed, else mode 2,
 * else mode 0, else the load is refused, or, with unrestricted memory limits, runs in mode 0
 * anyway. A request that forces a mode is judged for that mode only.
 */
final class TaiImageAdmission {
    /** Fastest first. */
    static final int[] AUTO_ORDER = {1, 2, 0};

    /** What a recorded load of this package cost in {@code memoryMode} on this device; 0 when unknown. */
    interface Measured {
        long bytes(int memoryMode);
    }

    static final class Decision {
        final boolean fits;
        final int memoryMode;
        @NonNull final TaiLoadBudget.Plan plan;

        Decision(boolean fits, int memoryMode, @NonNull TaiLoadBudget.Plan plan) {
            this.fits = fits;
            this.memoryMode = memoryMode;
            this.plan = plan;
        }
    }

    private TaiImageAdmission() {}

    /**
     * @param peakBytes     {@link TaiDiffusionPackage.Result#peakBytes}
     * @param requestedMode 0, 1 or 2 to force a mode; {@link TaiImageRequest#MEMORY_MODE_AUTO} to choose
     * @param availableBytes free memory already credited with the image model a new load replaces
     * @param conditions     the memory limits; unrestricted, the last mode tried goes ahead when none fits
     */
    @NonNull
    static Decision decide(long peakBytes, int requestedMode, @NonNull String accelerator, long physicalBytes,
                           long availableBytes, @NonNull List<TaiResidency.Entry> evictable,
                           @NonNull Measured measured, @NonNull TaiLoadBudget.Conditions conditions) {
        int[] order = requestedMode >= 0 ? new int[] {requestedMode} : AUTO_ORDER;
        TaiLoadBudget.Plan last = null;
        int lastMode = order[0];
        for (int mode : order) {
            long worst = measured.bytes(mode);
            TaiLoadBudget.Estimate estimate = worst > 0L ? TaiLoadBudget.Estimate.measured(worst)
                : TaiLoadBudget.Estimate.ratio(TaiResidency.imageEstimateBytes(peakBytes, mode), 0L);
            TaiLoadBudget.Plan plan = TaiLoadBudget.planFixed(estimate, accelerator, physicalBytes, availableBytes,
                evictable, conditions);
            last = plan;
            lastMode = mode;
            // An overcommitted plan fits only because nothing else would: a cheaper mode may still fit outright.
            if (plan.fits && !plan.overcommitted) return new Decision(true, mode, plan);
        }
        return new Decision(last.fits, lastMode, last);
    }

    /** Only mode 1 keeps the engine's modules loaded between runs, and only for Stable Diffusion/Taiyi. */
    static boolean staysResident(int memoryMode, int modelType) {
        return memoryMode == 1 && modelType != TaiDiffusionPackage.TYPE_SANA;
    }
}
