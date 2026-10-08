package com.termux.ai;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * {@link TaiFeaturePlan}s for this phone: its picks, device and remote routing ({@link
 * TaiFunctionModels#forContext}), the user's Parameters values ({@link TaiSettings#storedParameter})
 * and its measurements ({@link TaiEvidenceFiles}). Reads preferences, the model store and the
 * evidence files: never on the main thread.
 *
 * <p>The app process does not know what the runtime holds, so its plans see no residents; the
 * runtime process decides what depends on them (window reuse, groups, read aloud per reply).
 */
public final class TaiFeaturePlans {
    @NonNull private final TaiFunctionModels models;
    @NonNull private final TaiFeaturePlan.Parameters parameters;
    @NonNull private final TaiEvidence evidence;

    TaiFeaturePlans(@NonNull TaiFunctionModels models, @NonNull TaiFeaturePlan.Parameters parameters,
                    @NonNull TaiEvidence evidence) {
        this.models = models;
        this.parameters = parameters;
        this.evidence = evidence;
    }

    @NonNull
    public static TaiFeaturePlans forContext(@NonNull Context context) {
        return forContext(context, TaiFunctionModels.forContext(context));
    }

    /** As {@link #forContext(Context)}, over picks the caller already read. */
    @NonNull
    public static TaiFeaturePlans forContext(@NonNull Context context, @NonNull TaiFunctionModels models) {
        return new TaiFeaturePlans(models, parametersOf(new TaiSettings(context.getApplicationContext())),
            new TaiEvidenceFiles(context));
    }

    /** One stored Parameters value, as {@link TaiSettings#storedParameter} answers it; {@code null} when not stored. */
    interface StoredParameters {
        @Nullable
        Object get(@NonNull String backend, @NonNull String modelId, @NonNull String field);
    }

    /** The user's stored Parameters values, as the plan reads them. */
    @NonNull
    static TaiFeaturePlan.Parameters parametersOf(@NonNull TaiSettings settings) {
        return parametersOf(settings::storedParameter);
    }

    @NonNull
    static TaiFeaturePlan.Parameters parametersOf(@NonNull StoredParameters settings) {
        return new TaiFeaturePlan.Parameters() {
            @Nullable
            @Override
            public String accelerator(@NonNull String modelId, @NonNull String backend) {
                Object stored = settings.get(backend, modelId, TaiSettings.FIELD_ACCELERATOR);
                // MNN stores OpenCL, which is its GPU.
                String value = stored == null ? null : TaiLoadPreflight.normalizeAccelerator(String.valueOf(stored));
                return TaiTierPolicy.ACCEL_GPU.equals(value) || TaiTierPolicy.ACCEL_CPU.equals(value) ? value : null;
            }

            @Nullable
            @Override
            public Boolean speculative(@NonNull String modelId, @NonNull String backend) {
                Object stored = settings.get(backend, modelId, TaiSettings.FIELD_ENABLE_SPECULATIVE_DECODING);
                return stored instanceof Boolean ? (Boolean) stored : null;
            }
        };
    }

    @NonNull
    public TaiFunctionModels models() {
        return models;
    }

    @NonNull
    public TaiFeaturePlan plan(@NonNull TaiFunction feature) {
        return plan(feature, models);
    }

    /** {@code feature}'s plan over other picks, e.g. {@link TaiFunctionModels#without} a model about to be deleted. */
    @NonNull
    public TaiFeaturePlan plan(@NonNull TaiFunction feature, @NonNull TaiFunctionModels picks) {
        List<TaiResidency.Entry> residents = Collections.emptyList();
        return TaiFeaturePlan.of(feature, picks, parameters, evidence, residents, System.currentTimeMillis(), false);
    }
}
