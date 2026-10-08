package com.termux.ai;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * {@link TaiEvidence} from this phone's files: the runtime history's failure records and the crash
 * marker, the GPU verdict file, the bench results file and the feature check's. Reads files: not for
 * the main thread. The two results files are parsed once per change of their size or modification time,
 * so a plan per request costs a stat; a feature check's staleness is judged against the model store, read
 * once per instance.
 */
public final class TaiEvidenceFiles implements TaiEvidence {

    /** The last parse of the bench file and the stamp it was parsed at; shared by every instance. */
    private static final Object BENCH_LOCK = new Object();
    private static long benchStamp = Long.MIN_VALUE;
    private static long benchLength = -1L;
    @NonNull private static List<ChatResult> benchRows = Collections.emptyList();
    /** The same for the feature check's file: its latest record of each run. */
    private static final Object CHECKS_LOCK = new Object();
    private static long checksStamp = Long.MIN_VALUE;
    private static long checksLength = -1L;
    @NonNull private static List<JSONObject> checkRows = Collections.emptyList();

    @NonNull private final Context context;
    @Nullable private TaiDeviceCapabilities device;
    /** The installed models, for each result's staleness; read on first use. */
    @Nullable private Map<String, TaiModelSpec> installed;
    private final Map<String, String> currentKeys = new HashMap<>();

    public TaiEvidenceFiles(@NonNull Context context) {
        this.context = context.getApplicationContext();
    }

    @Override
    public boolean failed(@NonNull String modelId, @NonNull String backend, @NonNull String accelerator) {
        try {
            if (TaiRuntimeHistory.hasFailure(context, device(), modelId, backend, accelerator)) return true;
            // A load the process died in counts as a crash of that model on that accelerator.
            JSONObject marker = TaiRuntimeCrashMarker.read(context);
            return marker != null && TaiModelVariants.baseModelId(modelId).equals(
                    TaiModelVariants.baseModelId(marker.optString("modelId", "")))
                && backend.equals(marker.optString("backend", backend))
                && accelerator.equals(TaiLoadPreflight.normalizeAccelerator(marker.optString("accelerator", null)));
        } catch (Exception e) {
            return false;
        }
    }

    @NonNull
    @Override
    public TaiGpuVerdict.State gpuVerdict() {
        try {
            return TaiGpuVerdict.current(context, TaiPlatformCaps.rawCached(context));
        } catch (RuntimeException e) {
            return TaiGpuVerdict.State.UNKNOWN;
        }
    }

    @NonNull
    @Override
    public List<ChatResult> chatBench(@NonNull String modelId, @NonNull String backend) {
        String base = TaiModelVariants.baseModelId(modelId);
        List<ChatResult> out = new ArrayList<>();
        for (ChatResult result : benchRows()) {
            if (backend.equals(result.backend) && base.equals(TaiModelVariants.baseModelId(result.modelId))) out.add(result);
        }
        return out;
    }

    /** The feature check's latest result of each setup ({@link TaiFeatureCheckStore}), stale ones flagged. */
    @NonNull
    @Override
    public List<FeatureResult> featureChecks(@NonNull TaiFunction feature, @NonNull String modelId,
                                             @NonNull String backend) {
        String base = TaiModelVariants.baseModelId(modelId);
        String current = currentKey(base);
        List<FeatureResult> out = new ArrayList<>();
        for (JSONObject record : checkRows()) {
            if (!feature.id().equals(record.optString("feature", "")) || !backend.equals(record.optString("backend", ""))) continue;
            if (!base.equals(TaiModelVariants.baseModelId(record.optString("modelId", "")))) continue;
            FeatureResult result = TaiFeatureCheck.resultOf(record, current);
            if (result != null) out.add(result);
        }
        return out;
    }

    /** The staleness key of the installed file of {@code baseId} now; {@code null} when it is not installed. */
    @Nullable
    private String currentKey(@NonNull String baseId) {
        if (currentKeys.containsKey(baseId)) return currentKeys.get(baseId);
        String key = null;
        try {
            if (installed == null) {
                TaiModelStore store = new TaiModelStore(context);
                Map<String, TaiModelSpec> specs = new HashMap<>(store.getDownloadedReadableModels());
                specs.putAll(store.getInstalledUserModels());
                installed = specs;
            }
            TaiModelSpec spec = installed.get(baseId);
            if (spec == null) {
                for (TaiModelSpec candidate : installed.values()) {
                    if (baseId.equals(TaiModelVariants.baseModelId(candidate.id))) spec = candidate;
                }
            }
            if (spec != null) key = TaiFeatureCheckStore.stalenessKey(spec);
        } catch (RuntimeException ignored) {
            // Unknown reads as stale: the plan ignores the result rather than trusting it.
        }
        currentKeys.put(baseId, key);
        return key;
    }

    @NonNull
    private List<JSONObject> checkRows() {
        TaiFeatureCheckStore store = TaiFeatureCheckStore.in(context.getFilesDir());
        File file = store.file();
        long stamp = file.lastModified();
        long length = file.length();
        synchronized (CHECKS_LOCK) {
            if (stamp == checksStamp && length == checksLength) return checkRows;
            List<JSONObject> rows;
            try {
                rows = TaiFeatureCheckStore.latest(store.records());
            } catch (RuntimeException e) {
                rows = Collections.emptyList();
            }
            checkRows = Collections.unmodifiableList(rows);
            checksStamp = stamp;
            checksLength = length;
            return checkRows;
        }
    }

    @NonNull
    private TaiDeviceCapabilities device() {
        if (device == null) device = TaiDeviceCapabilities.detect(context);
        return device;
    }

    @NonNull
    private List<ChatResult> benchRows() {
        TaiBenchStore store = TaiBenchStore.in(context.getFilesDir());
        File file = store.file();
        long stamp = file.lastModified();
        long length = file.length();
        synchronized (BENCH_LOCK) {
            if (stamp == benchStamp && length == benchLength) return benchRows;
            List<ChatResult> rows;
            try {
                rows = TaiEvidence.chatResultsFrom(store.toJson(TaiBenchSuite.BENCH_VERSION));
            } catch (Exception e) {
                rows = Collections.emptyList();
            }
            benchRows = Collections.unmodifiableList(rows);
            benchStamp = stamp;
            benchLength = length;
            return benchRows;
        }
    }
}
