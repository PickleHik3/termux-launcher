package com.termux.ai;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

public final class TaiRuntimeCrashMarker {
    private static final String KEY_MARKER = "tai_runtime_loading_marker_json";

    private TaiRuntimeCrashMarker() {
    }

    public static void markLoad(
        @NonNull Context context,
        @NonNull TaiModelSpec model,
        @NonNull TaiRuntimeOptions options,
        @NonNull String backend
    ) {
        try {
            JSONObject marker = new JSONObject();
            marker.put("modelId", model.id);
            marker.put("backend", backend);
            marker.put("accelerator", options.accelerator == null ? "auto" : options.accelerator);
            if (options.contextWindow != null) marker.put("contextWindow", options.contextWindow);
            marker.put("startedAtMs", System.currentTimeMillis());
            marker.put("message", "AI runtime was loading model " + model.id + ".");
            // commit, not apply: the marker exists for the case where the process is killed moments
            // later, which an asynchronous write can lose.
            prefs(context).edit().putString(KEY_MARKER, marker.toString()).commit();
        } catch (JSONException ignored) {
        }
    }

    public static void clear(@NonNull Context context) {
        prefs(context).edit().remove(KEY_MARKER).apply();
    }

    @Nullable
    public static JSONObject read(@NonNull Context context) throws JSONException {
        String value = prefs(context).getString(KEY_MARKER, "");
        if (value == null || value.trim().isEmpty()) return null;
        JSONObject marker = new JSONObject(value);
        marker.put("suggestedFallback", "Try CPU or a smaller model, then disable AI auto-load if it repeats.");
        return marker;
    }

    @NonNull
    private static SharedPreferences prefs(@NonNull Context context) {
        return context.getApplicationContext().getSharedPreferences(TaiSettings.PREFS_NAME, Context.MODE_PRIVATE);
    }
}
