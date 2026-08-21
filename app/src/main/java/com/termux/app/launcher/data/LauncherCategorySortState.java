package com.termux.app.launcher.data;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.termux.TermuxConstants;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Remembers the last app-categorization run: when it happened, how many apps it covered and which
 * source produced the assignment. Sources are free-form ids rather than an enum so a future source
 * is a new constant, not a stored-data migration. Nothing here formats the timestamp — the epoch
 * millis stay raw so the UI can render them with its own locale and context.
 */
public final class LauncherCategorySortState {

    private static final String PREFS_KEY_CATEGORY_SORT_STATE_V1 =
        "app_launcher_category_sort_state_v1";

    private static final String JSON_KEY_LAST_RUN_EPOCH_MS = "last_run_epoch_ms";
    private static final String JSON_KEY_APP_COUNT = "app_count";
    private static final String JSON_KEY_SOURCE = "source";
    private static final String JSON_KEY_MODEL_ID = "model_id";

    /** Assignment produced by the on-device model; {@link #getModelId()} names which one. */
    public static final String SOURCE_ON_DEVICE_MODEL = "on_device_model";
    /** Assignment pasted in by the user from an external tool. */
    public static final String SOURCE_PASTED = "pasted";
    /** Assignment the user made by hand, one app at a time. */
    public static final String SOURCE_MANUAL = "manual";

    private final SharedPreferences sharedPreferences;

    private long lastRunEpochMs;
    private int appCount;
    @Nullable private String source;
    @Nullable private String modelId;
    private boolean loaded;

    public LauncherCategorySortState(@NonNull Context context) {
        this.sharedPreferences = context.getApplicationContext().getSharedPreferences(
            TermuxConstants.TERMUX_DEFAULT_PREFERENCES_FILE_BASENAME_WITHOUT_EXTENSION,
            Context.MODE_PRIVATE
        );
    }

    /** Replaces any previously recorded run. */
    public synchronized void recordRun(long epochMs, int appCount, @NonNull String source,
                                       @Nullable String modelId) {
        ensureLoaded();
        this.lastRunEpochMs = epochMs;
        this.appCount = appCount;
        this.source = source;
        this.modelId = modelId;
        persist();
    }

    /** @return true once a run has been recorded and not cleared since. */
    public synchronized boolean hasRun() {
        ensureLoaded();
        return lastRunEpochMs > 0;
    }

    /** @return epoch millis of the last run, or 0 when categorization has never run. */
    public synchronized long getLastRunEpochMs() {
        ensureLoaded();
        return lastRunEpochMs;
    }

    /** @return how many apps the last run covered, or 0 when it has never run. */
    public synchronized int getAppCount() {
        ensureLoaded();
        return appCount;
    }

    /** @return the {@code SOURCE_*} id that produced the last run, or null when never run. */
    @Nullable
    public synchronized String getSource() {
        ensureLoaded();
        return source;
    }

    /** @return the model used by the last run, or null when the source was not a model. */
    @Nullable
    public synchronized String getModelId() {
        ensureLoaded();
        return modelId;
    }

    public synchronized void clear() {
        ensureLoaded();
        lastRunEpochMs = 0;
        appCount = 0;
        source = null;
        modelId = null;
        sharedPreferences.edit().remove(PREFS_KEY_CATEGORY_SORT_STATE_V1).apply();
    }

    private void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        lastRunEpochMs = 0;
        appCount = 0;
        source = null;
        modelId = null;
        String raw = sharedPreferences.getString(PREFS_KEY_CATEGORY_SORT_STATE_V1, "");
        if (raw == null || raw.trim().isEmpty()) return;
        try {
            JSONObject root = new JSONObject(raw);
            lastRunEpochMs = root.optLong(JSON_KEY_LAST_RUN_EPOCH_MS, 0);
            appCount = root.optInt(JSON_KEY_APP_COUNT, 0);
            String storedSource = root.optString(JSON_KEY_SOURCE, "");
            if (!storedSource.isEmpty()) source = storedSource;
            String storedModelId = root.optString(JSON_KEY_MODEL_ID, "");
            if (!storedModelId.isEmpty()) modelId = storedModelId;
        } catch (JSONException ignored) {
            lastRunEpochMs = 0;
            appCount = 0;
            source = null;
            modelId = null;
        }
    }

    private void persist() {
        JSONObject root = new JSONObject();
        try {
            root.put(JSON_KEY_LAST_RUN_EPOCH_MS, lastRunEpochMs);
            root.put(JSON_KEY_APP_COUNT, appCount);
            if (source != null) root.put(JSON_KEY_SOURCE, source);
            if (modelId != null) root.put(JSON_KEY_MODEL_ID, modelId);
        } catch (JSONException ignored) {
        }
        sharedPreferences.edit()
            .putString(PREFS_KEY_CATEGORY_SORT_STATE_V1, root.toString()).apply();
    }
}
