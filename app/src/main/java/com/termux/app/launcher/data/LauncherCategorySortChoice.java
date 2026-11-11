package com.termux.app.launcher.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.ai.TaiFunctionModels;

import java.util.List;
import java.util.function.Predicate;

/**
 * What the "Re-sort apps" chooser offers: which model its on-device row runs, and whether the tier
 * or the RAM advises against running it, which puts the copy row first and adds the two notes.
 * Pure: no Android, no I/O.
 *
 * <p>On Tier 1 background sorting is off, so the plan names no model even with a chat model
 * installed. An explicit tap in this dialog may still run the smallest installed chat model; that
 * never changes what Automatic does.
 */
public final class LauncherCategorySortChoice {
    /** The model the on-device row runs, or {@code null} when none is installed. */
    @Nullable public final String onDeviceModel;
    /** True when {@link #onDeviceModel} is this dialog's own pick, not the plan's. */
    public final boolean explicit;
    /**
     * The tier rule or the RAM check advises against an on-device run: the copy row comes first and
     * says "Suggested for this phone", the on-device row says it is slow here.
     */
    public final boolean advisesAgainstOnDevice;

    private LauncherCategorySortChoice(@Nullable String onDeviceModel, boolean explicit, boolean advises) {
        this.onDeviceModel = onDeviceModel;
        this.explicit = explicit;
        this.advisesAgainstOnDevice = advises;
    }

    /**
     * @param tier1 the phone is Tier 1
     * @param planModel the plan's model ({@link LauncherCategorySortPlan#model}), or null
     * @param planRemote the plan's model is the remote provider's
     * @param candidates the installed models that can serve app sorting
     *     ({@link TaiFunctionModels#candidates})
     * @param ramAdvisesAgainst whether the RAM check says the model will not fit cleanly, by id
     */
    @NonNull
    public static LauncherCategorySortChoice decide(boolean tier1, @Nullable String planModel, boolean planRemote,
                                                    @NonNull List<TaiFunctionModels.ModelInfo> candidates,
                                                    @NonNull Predicate<String> ramAdvisesAgainst) {
        if (planRemote) return new LauncherCategorySortChoice(planModel, false, false);
        String model = planModel;
        boolean explicit = false;
        if (model == null && tier1) {
            TaiFunctionModels.ModelInfo smallest = null;
            for (TaiFunctionModels.ModelInfo info : candidates) {
                if (smallest == null || info.sizeBytes < smallest.sizeBytes
                    || (info.sizeBytes == smallest.sizeBytes && info.id.compareTo(smallest.id) < 0)) {
                    smallest = info;
                }
            }
            if (smallest != null) {
                model = smallest.id;
                explicit = true;
            }
        }
        boolean advises = tier1 || (model != null && ramAdvisesAgainst.test(model));
        return new LauncherCategorySortChoice(model, explicit, advises);
    }
}
