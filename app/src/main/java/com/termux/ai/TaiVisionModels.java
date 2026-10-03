package com.termux.ai;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The wallpaper vision models on the phone and which of them the analysis needs. Everything goes
 * by capability and catalogue id, the way {@link TaiTtsModels} does for speech output: depth is
 * either Depth Anything entry (the {@code wallpaper_depth_model} preference, else DA3 when it is
 * installed, else DA2), scene is SegFormer, subject is U-2-Net. {@link #missing} is what the
 * wallpaper picker offers to download before an analysis can run.
 */
public final class TaiVisionModels {
    /** The preference (in the same file as the TAI settings) naming the chosen depth model. */
    public static final String PREF_DEPTH_MODEL = "wallpaper_depth_model";

    private TaiVisionModels() {}

    /** One model the analysis needs and the phone lacks. */
    public static final class Missing {
        @NonNull public final String id;
        @NonNull public final String displayName;
        public final long sizeBytes;

        public Missing(@NonNull String id, @NonNull String displayName, long sizeBytes) {
            this.id = id;
            this.displayName = displayName;
            this.sizeBytes = sizeBytes;
        }
    }

    public static boolean isVisionModel(@NonNull TaiModelSpec spec) {
        return spec.isVisionTool();
    }

    /** Ids of every readable vision model: downloads first then the registry (which wins on a shared id). */
    @NonNull
    public static List<String> installedIds(@NonNull TaiModelStore store) {
        LinkedHashMap<String, TaiModelSpec> models = new LinkedHashMap<>(store.getDownloadedReadableModels());
        models.putAll(store.getInstalledUserModels());
        List<String> ids = new ArrayList<>();
        for (TaiModelSpec spec : models.values()) {
            if (isVisionModel(spec)) ids.add(spec.id);
        }
        return ids;
    }

    /**
     * What the analysis still needs: one depth model (the chosen one when it is missing and no depth
     * model is installed, else nothing), SegFormer and U-2-Net, each only when it is not installed.
     * The depth row offered is the chosen model, or DA3 when nothing is chosen.
     */
    @NonNull
    public static List<Missing> missing(@NonNull Context context) {
        return missing(installedIds(new TaiModelStore(context)), chosenDepth(context));
    }

    /** {@link #missing(Context)} on a known installed set; {@code chosenDepth} may be {@code null}. */
    @NonNull
    static List<Missing> missing(@NonNull Collection<String> installed, @Nullable String chosenDepth) {
        List<Missing> missing = new ArrayList<>();
        boolean anyDepth = installed.contains(TaiModelCatalog.DEPTH_ANYTHING_3_SMALL_ID)
            || installed.contains(TaiModelCatalog.DEPTH_ANYTHING_V2_SMALL_ID);
        if (!anyDepth) {
            String wanted = isDepthId(chosenDepth) ? chosenDepth : TaiModelCatalog.DEPTH_ANYTHING_3_SMALL_ID;
            addMissing(missing, wanted);
        }
        if (!installed.contains(TaiModelCatalog.SEGFORMER_B0_ADE20K_ID)) addMissing(missing, TaiModelCatalog.SEGFORMER_B0_ADE20K_ID);
        if (!installed.contains(TaiModelCatalog.U2NET_ID)) addMissing(missing, TaiModelCatalog.U2NET_ID);
        return missing;
    }

    /**
     * The depth model the analysis uses: the preference when it names an installed one, else DA3 when
     * it is installed, else DA2, else the preference or DA3 (nothing installed yet, so the caller
     * learns the missing id from {@link #missing}).
     */
    @NonNull
    public static String depthModel(@NonNull Context context) {
        return chooseDepth(installedIds(new TaiModelStore(context)), chosenDepth(context));
    }

    @NonNull
    static String chooseDepth(@NonNull Collection<String> installed, @Nullable String chosen) {
        if (isDepthId(chosen) && installed.contains(chosen)) return chosen;
        if (installed.contains(TaiModelCatalog.DEPTH_ANYTHING_3_SMALL_ID)) return TaiModelCatalog.DEPTH_ANYTHING_3_SMALL_ID;
        if (installed.contains(TaiModelCatalog.DEPTH_ANYTHING_V2_SMALL_ID)) return TaiModelCatalog.DEPTH_ANYTHING_V2_SMALL_ID;
        return isDepthId(chosen) ? chosen : TaiModelCatalog.DEPTH_ANYTHING_3_SMALL_ID;
    }

    /** Stores the depth model the analysis should prefer ({@code null} clears the choice). */
    public static void setDepthModel(@NonNull Context context, @Nullable String modelId) {
        SharedPreferences.Editor edit = prefs(context).edit();
        if (isDepthId(modelId)) edit.putString(PREF_DEPTH_MODEL, modelId);
        else edit.remove(PREF_DEPTH_MODEL);
        edit.apply();
    }

    @Nullable
    private static String chosenDepth(@NonNull Context context) {
        String stored = prefs(context).getString(PREF_DEPTH_MODEL, null);
        return isDepthId(stored) ? stored : null;
    }

    @NonNull
    private static SharedPreferences prefs(@NonNull Context context) {
        return context.getApplicationContext().getSharedPreferences(TaiSettings.PREFS_NAME, Context.MODE_PRIVATE);
    }

    private static boolean isDepthId(@Nullable String id) {
        if (id == null) return false;
        TaiModelCatalog.CatalogEntry entry = TaiModelCatalog.get(id);
        return entry != null && entry.endpointCapabilities.contains(TaiModelSpec.CAPABILITY_DEPTH_ESTIMATION);
    }

    private static void addMissing(@NonNull List<Missing> out, @NonNull String id) {
        TaiModelCatalog.CatalogEntry entry = TaiModelCatalog.get(id);
        if (entry != null) out.add(new Missing(entry.modelId, entry.displayName, entry.sizeBytes));
    }

    /** The catalogue's vision entries by id, for callers that list them. */
    @NonNull
    public static Map<String, TaiModelCatalog.CatalogEntry> catalogue() {
        return TaiModelCatalog.visionEntries();
    }
}
