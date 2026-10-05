package com.termux.ai;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * The text a user shares from Settings when something about the on-device AI went wrong: the
 * event log (both files), the runtime history and the last crash marker, in one file. None of it
 * holds prompts or replies; see {@link TaiEventLog}.
 */
public final class TaiDiagnostics {
    public static final String FILE_NAME = "diagnostics.txt";

    private TaiDiagnostics() {
    }

    /** Writes {@code filesDir/tai/diagnostics.txt} and returns it. */
    @NonNull
    public static File write(@NonNull Context context) throws IOException {
        File directory = new File(context.getApplicationContext().getFilesDir(), "tai");
        if (!directory.isDirectory() && !directory.mkdirs() && !directory.isDirectory()) {
            throw new IOException("Could not create " + directory);
        }
        String events = TaiEventLog.in(context.getApplicationContext().getFilesDir()).readAll();
        String history;
        String marker;
        try {
            history = TaiRuntimeHistory.summary(context).toString(2);
        } catch (JSONException e) {
            history = "unavailable: " + e.getMessage();
        }
        try {
            JSONObject crash = TaiRuntimeCrashMarker.read(context);
            marker = crash == null ? null : crash.toString(2);
        } catch (JSONException e) {
            marker = "unavailable: " + e.getMessage();
        }
        File file = new File(directory, FILE_NAME);
        try (FileOutputStream output = new FileOutputStream(file, false)) {
            String text = compose(System.currentTimeMillis(), events, history, marker);
            output.write(redactSecrets(text, remoteApiKey(context)).getBytes(StandardCharsets.UTF_8));
        }
        return file;
    }

    /**
     * The file is meant to be shared, so the remote provider's key and any {@code Authorization}
     * value are scrubbed from it even though nothing is supposed to log them.
     */
    @NonNull
    static String redactSecrets(@NonNull String text, @Nullable String... secrets) {
        return TaiRemoteClient.redact(text, secrets);
    }

    @Nullable
    private static String remoteApiKey(@NonNull Context context) {
        try {
            return new TaiRemoteSettings(context).apiKey();
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** The shared text: a header line, then one titled section per source. */
    @NonNull
    static String compose(long nowMs, @NonNull String events, @NonNull String history, @Nullable String crashMarker) {
        StringBuilder text = new StringBuilder();
        text.append("Termux Launcher on-device AI diagnostics, ")
            .append(TaiEventLog.formatLine(nowMs, "generated", null, null, null, 0, 0L, 0L, null).split(" ")[0]).append('\n');
        text.append("\n== events.log.1 + events.log ==\n");
        text.append(events.isEmpty() ? "(no events logged)\n" : events);
        text.append("\n== runtime history ==\n").append(history).append('\n');
        text.append("\n== last runtime crash marker ==\n");
        text.append(crashMarker == null ? "(none)\n" : crashMarker + "\n");
        return text.toString();
    }
}
