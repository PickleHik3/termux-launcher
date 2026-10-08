package com.termux.ai;

import android.content.Context;

import androidx.annotation.NonNull;

import java.util.Collections;
import java.util.List;

/**
 * {@link TaiFeaturePlan}s for this phone: its picks, device and remote routing ({@link
 * TaiFunctionModels#forContext}) and its measurements ({@link TaiEvidenceFiles}). Reads preferences,
 * the model store and the evidence files: never on the main thread.
 *
 * <p>The app process does not know what the runtime holds, so its plans see no residents; the
 * runtime process decides what depends on them (window reuse, groups, read aloud per reply).
 */
public final class TaiFeaturePlans {
    @NonNull private final TaiFunctionModels models;
    @NonNull private final TaiEvidence evidence;

    TaiFeaturePlans(@NonNull TaiFunctionModels models, @NonNull TaiEvidence evidence) {
        this.models = models;
        this.evidence = evidence;
    }

    @NonNull
    public static TaiFeaturePlans forContext(@NonNull Context context) {
        return new TaiFeaturePlans(TaiFunctionModels.forContext(context), new TaiEvidenceFiles(context));
    }

    @NonNull
    public TaiFunctionModels models() {
        return models;
    }

    @NonNull
    public TaiFeaturePlan plan(@NonNull TaiFunction feature) {
        List<TaiResidency.Entry> residents = Collections.emptyList();
        return TaiFeaturePlan.of(feature, models, evidence, residents, System.currentTimeMillis(), false);
    }
}
