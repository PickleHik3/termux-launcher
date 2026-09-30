package com.termux.app.fragments.settings.termux;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.ai.TaiBenchStore;
import com.termux.ai.TaiBenchSuite;
import com.termux.ai.TaiImportFit;
import com.termux.ai.TaiImportProfiles;
import com.termux.ai.TaiModelSpec;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

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

    /** How many processors a model runs on here: both when comparing and the phone has a GPU, else one. */
    static int processors(boolean compare, @NonNull Device device) {
        return compare && device.gpuSupported ? 2 : 1;
    }

    /** The estimated time for one model under {@code preset} on this phone. */
    static long estimateMs(@NonNull TaiBenchSuite.Preset preset, boolean compare, @NonNull Device device) {
        return TaiBenchSuite.estimateMs(preset, processors(compare, device));
    }

    /** "Worth a download": a recommended catalogue entry that is not installed and passes the filter. */
    static boolean worthADownload(@NonNull Candidate candidate, @NonNull Verdict verdict) {
        return !candidate.installed && candidate.recommended && verdict.fit != Fit.HIDDEN;
    }

    /**
     * The models whose latest record for {@code benchVersion} (on whichever processor) is a
     * crash: the runtime died while it loaded or ran. They start unselected and say so, until a
     * later run of the model comes back with anything else. {@code benchmarks} is
     * {@code TaiManager.benchmarks()}' JSON.
     */
    @NonNull
    static Set<String> crashedModels(@Nullable JSONObject benchmarks, @NonNull String benchVersion) {
        Set<String> crashed = new HashSet<>();
        JSONArray records = benchmarks == null ? null : benchmarks.optJSONArray("records");
        if (records == null) return crashed;
        Map<String, JSONObject> latest = new HashMap<>();
        for (int i = 0; i < records.length(); i++) {
            JSONObject record = records.optJSONObject(i);
            if (record == null || !benchVersion.equals(record.optString("benchVersion", ""))) continue;
            String modelId = record.optString("modelId", "");
            JSONObject seen = latest.get(modelId);
            if (seen == null || record.optLong("timestamp", 0L) >= seen.optLong("timestamp", 0L)) latest.put(modelId, record);
        }
        for (Map.Entry<String, JSONObject> entry : latest.entrySet()) {
            if (TaiBenchStore.STATUS_CRASHED.equals(entry.getValue().optString("status", ""))) crashed.add(entry.getKey());
        }
        return crashed;
    }
}
