package com.termux.ai;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * What to tell a person about a speech model that only runs right on the GPU
 * ({@link TaiModelCatalog.CatalogEntry#gpuPrecisionFp32}). It is advice only: the model can always
 * be downloaded and picked, as text models can.
 *
 * <p>Two steps. First the phone: no usable GPU ({@link TaiDeviceCapabilities#hasUsableGpu}) says
 * {@link Advice#NO_GPU}; otherwise {@link Advice#TRY}. Then the voice benchmark, once it has run
 * this model on this phone ({@code benchmarks.json}, read as it is, no second copy): the model is
 * {@link Advice#RECOMMENDED} when its GPU result is faster than the result of the speech model in
 * use and its text is sane ({@link TaiVoiceBench#textSane}), {@link Advice#SLOWER} otherwise. With no
 * result for the model, or none for the model in use to compare with, it stays {@code TRY}.
 */
public final class TaiSpeechGpuAdvice {
    public enum Advice { NO_GPU, TRY, RECOMMENDED, SLOWER }

    private TaiSpeechGpuAdvice() {
    }

    /** The advice for {@code modelId} on this phone now; reads the results file, so not for a hot path. */
    @NonNull
    public static Advice forContext(@NonNull Context context, @NonNull String modelId) {
        Context app = context.getApplicationContext();
        boolean gpu;
        try {
            gpu = TaiDeviceCapabilities.detect(app).hasUsableGpu();
        } catch (RuntimeException e) {
            gpu = false;
        }
        if (!gpu) return Advice.NO_GPU;
        TaiModelSpec current = TaiSpeechModels.resolveActive(app);
        boolean currentOnGpu = current != null && "gpu".equals(new TaiSettings(app).getSttAccelerator(current.id));
        JSONArray records = TaiBenchStore.in(app.getFilesDir()).records();
        return judge(true, records, modelId, current == null ? null : current.id, currentOnGpu);
    }

    /** The mapping itself, from the device check and the results on file. */
    @NonNull
    static Advice judge(boolean gpuUsable, @Nullable JSONArray records, @NonNull String modelId,
                        @Nullable String currentModelId, boolean currentOnGpu) {
        if (!gpuUsable) return Advice.NO_GPU;
        JSONObject ours = latest(records, modelId, TaiBenchSuite.ACCELERATOR_GPU);
        if (ours == null) return Advice.TRY;
        if (currentModelId == null || currentModelId.equals(modelId)) return Advice.TRY;
        JSONObject theirs = latest(records, currentModelId,
            currentOnGpu ? TaiBenchSuite.ACCELERATOR_GPU : TaiBenchSuite.ACCELERATOR_CPU);
        if (theirs == null) return Advice.TRY;
        boolean faster = realtime(ours) > realtime(theirs);
        return faster && TaiVoiceBench.textSane(ours) ? Advice.RECOMMENDED : Advice.SLOWER;
    }

    /** The newest complete voice record of {@code modelId} that ran on {@code ranOn} (a GPU fallback never counts as one). */
    @Nullable
    private static JSONObject latest(@Nullable JSONArray records, @NonNull String modelId, @NonNull String ranOn) {
        if (records == null) return null;
        JSONObject best = null;
        for (int i = 0; i < records.length(); i++) {
            JSONObject record = records.optJSONObject(i);
            if (record == null || !TaiVoiceBench.BENCH_VERSION.equals(record.optString("benchVersion", ""))) continue;
            if (!modelId.equals(record.optString("modelId", ""))) continue;
            if (!TaiBenchStore.STATUS_COMPLETE.equals(record.optString("status", ""))) continue;
            if (TaiVoiceBench.headline(record) == null) continue;
            if (!ranOn.equals(record.optString("ranOn", record.optString("accelerator", "")))) continue;
            if (best == null || record.optLong("timestamp", 0L) >= best.optLong("timestamp", 0L)) best = record;
        }
        return best;
    }

    private static double realtime(@NonNull JSONObject record) {
        JSONObject voice = TaiVoiceBench.headline(record);
        return voice == null ? 0.0 : voice.optDouble("realtime", 0.0);
    }
}
