package com.termux.ai;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One-time cleanup of the wallpaper vision models. The wallpaper analysis they served is gone, so
 * any copy still under {@code files/tai/models} is dead weight: it is deleted once, with the picks
 * that named the removed wallpaper functions. Reads and deletes files: never on the main thread.
 */
final class TaiVisionLeftovers {
    /** The four catalogue ids that shipped, for a download whose record carries no capabilities. */
    private static final Set<String> LEGACY_IDS = Collections.unmodifiableSet(new LinkedHashSet<>(Arrays.asList(
        "depth-anything-3-small", "depth-anything-v2-small", "segformer-b0-ade20k", "u2net")));
    /** Picks and accelerators of the removed wallpaper functions, in the TAI settings file. */
    private static final List<String> LEGACY_KEYS = Collections.unmodifiableList(Arrays.asList(
        "wallpaper_depth_model", "tai_fn_wallpaper_reader_model", "tai_fn_wallpaper_reader_accel",
        "tai_fn_wallpaper_depth_accel"));
    static final String KEY_DONE = "tai_vision_leftovers_cleaned_v1";

    private TaiVisionLeftovers() {}

    /**
     * The model ids to delete: every spec that is a vision tool or a legacy id, and every legacy id
     * the store still has a download record for.
     */
    @NonNull
    static Set<String> idsToDelete(@NonNull Collection<TaiModelSpec> specs, @NonNull Collection<String> recordIds) {
        Set<String> ids = new LinkedHashSet<>();
        for (TaiModelSpec spec : specs) {
            if (spec.isVisionTool() || LEGACY_IDS.contains(spec.id)) ids.add(spec.id);
        }
        for (String id : recordIds) {
            if (LEGACY_IDS.contains(id)) ids.add(id);
        }
        return ids;
    }

    /** Runs the cleanup the first time only; a failure leaves the flag unset so the next start retries. */
    static void cleanOnce(@NonNull Context context) {
        Context app = context.getApplicationContext() == null ? context : context.getApplicationContext();
        SharedPreferences prefs = app.getSharedPreferences(TaiSettings.PREFS_NAME, Context.MODE_PRIVATE);
        if (prefs.getBoolean(KEY_DONE, false)) return;
        try {
            TaiModelStore store = new TaiModelStore(app);
            Map<String, TaiModelSpec> specs = new LinkedHashMap<>(store.getUserModels());
            specs.putAll(store.getDownloadedReadableModels());
            Set<String> recordIds = new LinkedHashSet<>();
            JSONArray downloads = store.getDownloads();
            for (int i = 0; i < downloads.length(); i++) {
                JSONObject item = downloads.optJSONObject(i);
                if (item != null) recordIds.add(item.optString("modelId", ""));
            }
            for (String id : idsToDelete(specs.values(), recordIds)) store.deleteUserModel(id);
            SharedPreferences.Editor edit = prefs.edit();
            for (String key : LEGACY_KEYS) edit.remove(key);
            edit.putBoolean(KEY_DONE, true).apply();
        } catch (RuntimeException ignored) {
            // Retried on the next start.
        }
    }
}
