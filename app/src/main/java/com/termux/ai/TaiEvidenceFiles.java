package com.termux.ai;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * {@link TaiEvidence} from this phone's files: the runtime history's failure records and the crash
 * marker, the GPU verdict file, and the bench results file. Reads files: not for the main thread.
 * The bench file is parsed once per change of its size or modification time, so a plan per request
 * costs a stat.
 */
public final class TaiEvidenceFiles implements TaiEvidence {

    /** The last parse of the bench file and the stamp it was parsed at; shared by every instance. */
    private static final Object BENCH_LOCK = new Object();
    private static long benchStamp = Long.MIN_VALUE;
    private static long benchLength = -1L;
    @NonNull private static List<ChatResult> benchRows = Collections.emptyList();

    @NonNull private final Context context;
    @Nullable private TaiDeviceCapabilities device;

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

    /** Step 2 (the feature check) stores its results and reads them back here; until then there are none. */
    @NonNull
    @Override
    public List<FeatureResult> featureChecks(@NonNull TaiFunction feature, @NonNull String modelId,
                                             @NonNull String backend) {
        return Collections.emptyList();
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
