package com.termux.ai;

import androidx.annotation.NonNull;

/**
 * "Will it run on this phone?" for a model the catalog knows nothing about, answered from its
 * file size and the phone's RAM class the way the catalog answers it for its own entries: a
 * recommended RAM tier per package, compared against {@link TaiDeviceCapabilities#memoryBytes}.
 * The tiers reproduce the catalog's (chat packages up to 2 GB are 6GB+, Gemma 4 E2B at 2.4 GB is
 * 8GB+, E4B at 3.7 GB is 12GB+, embedding models are 4GB+) and continue upward for bigger files.
 * A phone one RAM class under the tier gets "probably slow": the CPU path maps the weights and
 * usually loads, with the launcher evicted around it. Two classes under is "too big".
 */
public final class TaiImportFit {
    public enum Verdict { YES, SLOW, TOO_BIG, UNKNOWN }

    private static final long GIB = 1024L * 1024L * 1024L;
    /** {@link TaiLoadBudget}'s RAM classes; a phone's memory rounds up to one of these. */
    private static final int[] RAM_CLASSES_GB = {2, 3, 4, 6, 8, 10, 12, 16, 18, 20, 24, 32, 48, 64};

    @NonNull public final Verdict verdict;
    /** The RAM tier the model wants, in GB; {@code 0} when the size was unknown. */
    public final int neededGb;
    /** The phone's RAM class, in GB; {@code 0} when it could not be measured. */
    public final int deviceGb;

    private TaiImportFit(@NonNull Verdict verdict, int neededGb, int deviceGb) {
        this.verdict = verdict;
        this.neededGb = neededGb;
        this.deviceGb = deviceGb;
    }

    @NonNull
    public static TaiImportFit check(long sizeBytes, long deviceMemoryBytes, boolean embedding) {
        int needed = recommendedRamGb(sizeBytes, embedding);
        int device = deviceMemoryBytes <= 0L ? 0 : (int) Math.round(deviceMemoryBytes / (double) GIB);
        if (needed <= 0 || device <= 0) return new TaiImportFit(Verdict.UNKNOWN, needed, device);
        if (device >= needed) return new TaiImportFit(Verdict.YES, needed, device);
        if (device >= classBelow(needed)) return new TaiImportFit(Verdict.SLOW, needed, device);
        return new TaiImportFit(Verdict.TOO_BIG, needed, device);
    }

    /** The catalog's RAM tier for a package of this size; {@code 0} when the size is unknown. */
    public static int recommendedRamGb(long sizeBytes, boolean embedding) {
        if (sizeBytes <= 0L) return 0;
        double gb = sizeBytes / (double) GIB;
        if (embedding) return gb <= 0.5 ? 4 : gb <= 1.5 ? 6 : 8;
        if (gb <= 2.0) return 6;
        if (gb <= 3.0) return 8;
        if (gb <= 4.5) return 12;
        if (gb <= 6.5) return 16;
        if (gb <= 9.0) return 24;
        return 32;
    }

    /** The RAM class one step under {@code gb}; {@code gb} itself when it is the smallest. */
    static int classBelow(int gb) {
        int below = gb;
        for (int candidate : RAM_CLASSES_GB) {
            if (candidate >= gb) break;
            below = candidate;
        }
        return below;
    }
}
