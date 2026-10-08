package com.termux.ai;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * The load in progress, written by the runtime process before a model loads and cleared once it has:
 * a marker still there after the process died names the load it died in.
 *
 * <p>A file under {@code files/tai}, not preferences: the runtime runs in {@code :tai_runtime} and the
 * marker is read in the app process too (the feature load plan's evidence, the runtime client's crash
 * report), and SharedPreferences are cached per process, so the app process would never see a new
 * crash, or would keep a cleared one forever. Every read goes to the file.
 */
public final class TaiRuntimeCrashMarker {
    static final String FILE_NAME = "runtime-loading.json";

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
            // Synced before the load starts: the marker exists for the case where the process is
            // killed moments later, which an unsynced write can lose.
            TaiBenchStore.writeAtomically(file(context), marker.toString());
        } catch (JSONException | IOException ignored) {
        }
    }

    public static void clear(@NonNull Context context) {
        //noinspection ResultOfMethodCallIgnored
        file(context).delete();
    }

    @Nullable
    public static JSONObject read(@NonNull Context context) throws JSONException {
        String value;
        try {
            File file = file(context);
            if (!file.isFile()) return null;
            value = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            return null;
        }
        if (value.trim().isEmpty()) return null;
        JSONObject marker = new JSONObject(value);
        marker.put("suggestedFallback", "Try CPU or a smaller model, then disable AI auto-load if it repeats.");
        return marker;
    }

    @NonNull
    static File file(@NonNull Context context) {
        Context app = context.getApplicationContext() != null ? context.getApplicationContext() : context;
        return new File(new File(app.getFilesDir(), "tai"), FILE_NAME);
    }
}
