package com.termux.app.fragments.settings.termux;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.ai.TaiBenchSuite;
import com.termux.ai.TaiImportFit;
import com.termux.ai.TaiImportProfiles;
import com.termux.ai.TaiModelSpec;

/**
 * The Choose screen's filter (spec "Which models are offered"), as a pure function of what is
 * known about one model and this phone: RAM through {@link TaiImportFit} (YES shown, SLOW shown
 * as "Tight", TOO_BIG hidden), storage for a download (the size plus 10% must fit), the chip a
 * build was compiled for ({@link TaiImportProfiles#socMatches}), MNN support, and the GPU. Hidden
 * models are counted and each carries its reason for "Show why".
 */
final class TaiBenchChoice {
    enum Fit { SHOWN, TIGHT, HIDDEN }

    /** Why a model is hidden; {@code null} when it is shown. */
    enum Reason { TOO_BIG, NO_SPACE, WRONG_CHIP, MNN_UNSUPPORTED }

    /** What the filter needs to know about one model, installed or in the catalogue. */
    static final class Candidate {
        @NonNull final String modelId;
        @NonNull final String displayName;
        @NonNull final String backend;
        final long sizeBytes;
        final boolean installed;
        /** A catalogue entry the curators recommend for this RAM class: "Worth a download". */
        final boolean recommended;
        /** The file the build is named after, for the chip check; the artifact path or the local path. */
        @Nullable final String fileName;

        Candidate(@NonNull String modelId, @NonNull String displayName, @NonNull String backend, long sizeBytes,
                  boolean installed, boolean recommended, @Nullable String fileName) {
            this.modelId = modelId;
            this.displayName = displayName;
            this.backend = backend;
            this.sizeBytes = sizeBytes;
            this.installed = installed;
            this.recommended = recommended;
            this.fileName = fileName;
        }
    }

    /** This phone, as the filter sees it. */
    static final class Device {
        final long memoryBytes;
        final long freeStorageBytes;
        @Nullable final String soc;
        final boolean mnnSupported;
        final boolean gpuSupported;

        Device(long memoryBytes, long freeStorageBytes, @Nullable String soc, boolean mnnSupported, boolean gpuSupported) {
            this.memoryBytes = memoryBytes;
            this.freeStorageBytes = freeStorageBytes;
            this.soc = soc;
            this.mnnSupported = mnnSupported;
            this.gpuSupported = gpuSupported;
        }
    }

    static final class Verdict {
        @NonNull final Fit fit;
        @Nullable final Reason reason;

        Verdict(@NonNull Fit fit, @Nullable Reason reason) {
            this.fit = fit;
            this.reason = reason;
        }
    }

    /** Storage must hold the download plus this share of it. */
    static final double STORAGE_RESERVE = 0.10;

    private TaiBenchChoice() {
    }

    /** The filter, rules in the spec's order: RAM, storage (downloads only), chip, MNN. */
    @NonNull
    static Verdict judge(@NonNull Candidate candidate, @NonNull Device device) {
        TaiImportFit fit = TaiImportFit.check(candidate.sizeBytes, device.memoryBytes, false);
        if (fit.verdict == TaiImportFit.Verdict.TOO_BIG) return new Verdict(Fit.HIDDEN, Reason.TOO_BIG);
        if (!candidate.installed && candidate.sizeBytes > 0L && device.freeStorageBytes >= 0L
            && candidate.sizeBytes * (1.0 + STORAGE_RESERVE) > device.freeStorageBytes) {
            return new Verdict(Fit.HIDDEN, Reason.NO_SPACE);
        }
        String target = TaiImportProfiles.socTarget(candidate.fileName);
        if (target != null && !TaiImportProfiles.socMatches(target, device.soc)) return new Verdict(Fit.HIDDEN, Reason.WRONG_CHIP);
        if (TaiModelSpec.BACKEND_MNN_LLM.equals(candidate.backend) && !device.mnnSupported) {
            return new Verdict(Fit.HIDDEN, Reason.MNN_UNSUPPORTED);
        }
        return new Verdict(fit.verdict == TaiImportFit.Verdict.SLOW ? Fit.TIGHT : Fit.SHOWN, null);
    }

    /** How many processors the preset runs a model on here: both where the phone has a GPU, else the CPU. */
    static int processors(@NonNull TaiBenchSuite.Preset preset, @NonNull Device device) {
        return preset.bothProcessors && device.gpuSupported ? 2 : 1;
    }

    /** The estimated time for one model under {@code preset} on this phone. */
    static long estimateMs(@NonNull TaiBenchSuite.Preset preset, @NonNull Device device) {
        return TaiBenchSuite.estimateMs(preset, processors(preset, device));
    }

    /** "Worth a download": a recommended catalogue entry that is not installed and passes the filter. */
    static boolean worthADownload(@NonNull Candidate candidate, @NonNull Verdict verdict) {
        return !candidate.installed && candidate.recommended && verdict.fit != Fit.HIDDEN;
    }
}
