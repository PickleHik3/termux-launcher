package com.termux.ai;

import android.content.Context;
import android.content.Intent;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;

/**
 * A one-line, cross-process answer to "is a model loaded right now, and for how much longer".
 *
 * <p>The runtime lives in its own {@code :tai_runtime} process, and the only way to ask it directly
 * is an IPC that <em>starts</em> that process — so a status-bar glyph polling it would keep alive
 * exactly the thing it is reporting on. Instead the runtime publishes a tiny snapshot file whenever
 * its state changes and broadcasts a nudge; readers get the state for the price of one small read,
 * and get nothing at all when the runtime has never run.
 */
public final class TaiRuntimePresence {

    /** Package-internal broadcast telling the UI process that the snapshot file changed. */
    public static final String ACTION_PRESENCE_CHANGED = "com.termux.ai.action.RUNTIME_PRESENCE";

    private static final String FILE_NAME = "tai-runtime-presence.json";

    private TaiRuntimePresence() {
    }

    /** Immutable read of the last published runtime state. */
    public static final class Snapshot {
        public final boolean loaded;
        public final boolean loading;
        public final boolean generating;
        @Nullable public final String modelId;
        /** Wall-clock ms at which the idle timer will unload the model, or 0 when it is off. */
        public final long idleUnloadAtMs;
        /** Wall-clock ms this snapshot was written, used to notice a runtime that was killed. */
        public final long publishedAtMs;

        Snapshot(boolean loaded, boolean loading, boolean generating, @Nullable String modelId,
                 long idleUnloadAtMs, long publishedAtMs) {
            this.loaded = loaded;
            this.loading = loading;
            this.generating = generating;
            this.modelId = modelId;
            this.idleUnloadAtMs = idleUnloadAtMs;
            this.publishedAtMs = publishedAtMs;
        }

        /** True while the runtime holds a model, is pulling one in, or is mid-generation. */
        public boolean active() {
            return loaded || loading || generating;
        }

        static Snapshot none() {
            return new Snapshot(false, false, false, null, 0L, 0L);
        }
    }

    /** @return the "nothing was ever published" snapshot, for readers starting cold. */
    @NonNull
    public static Snapshot empty() {
        return Snapshot.none();
    }

    /** Writes the current runtime state and nudges the UI process. Runtime-process side. */
    public static void publish(@NonNull Context context, @NonNull TaiRuntimeState state) {
        Context app = context.getApplicationContext();
        JSONObject json = new JSONObject();
        try {
            json.put("loaded", state.loaded);
            json.put("loading", "loading".equals(state.state));
            json.put("generating", state.activeGeneration);
            json.put("modelId", state.loadedModelId == null ? JSONObject.NULL : state.loadedModelId);
            json.put("idleUnloadAtMs", state.idleUnloadAtMs);
            json.put("publishedAtMs", System.currentTimeMillis());
        } catch (Exception ignored) {
            return;
        }
        if (!write(app, json.toString())) return;
        // Explicit and package-scoped: this is an internal nudge, never an app-to-app broadcast.
        Intent intent = new Intent(ACTION_PRESENCE_CHANGED).setPackage(app.getPackageName());
        app.sendBroadcast(intent);
    }

    /** @return the last published state, or an all-false snapshot when nothing was ever written. */
    @NonNull
    public static Snapshot read(@NonNull Context context) {
        File file = file(context.getApplicationContext());
        if (!file.isFile()) return Snapshot.none();
        String raw = readFully(file);
        if (raw == null || raw.isEmpty()) return Snapshot.none();
        try {
            JSONObject json = new JSONObject(raw);
            return new Snapshot(
                json.optBoolean("loaded", false),
                json.optBoolean("loading", false),
                json.optBoolean("generating", false),
                json.isNull("modelId") ? null : json.optString("modelId", null),
                json.optLong("idleUnloadAtMs", 0L),
                json.optLong("publishedAtMs", 0L));
        } catch (Exception ignored) {
            return Snapshot.none();
        }
    }

    private static boolean write(@NonNull Context context, @NonNull String payload) {
        File file = file(context);
        File parent = file.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) return false;
        // Written whole under a lock rather than through a temp-file rename: the reader is a status
        // glyph, and a half-written file it can simply fail to parse is cheaper than the rename
        // dance on every progress tick.
        try (RandomAccessFile handle = new RandomAccessFile(file, "rw")) {
            byte[] bytes = payload.getBytes(StandardCharsets.UTF_8);
            handle.getChannel().lock();
            handle.setLength(0);
            handle.write(bytes);
            return true;
        } catch (IOException | RuntimeException ignored) {
            return false;
        }
    }

    @Nullable
    private static String readFully(@NonNull File file) {
        try (RandomAccessFile handle = new RandomAccessFile(file, "r")) {
            int length = (int) Math.min(handle.length(), 8192L);
            if (length <= 0) return null;
            byte[] bytes = new byte[length];
            handle.readFully(bytes);
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException ignored) {
            return null;
        }
    }

    @NonNull
    private static File file(@NonNull Context context) {
        return new File(context.getFilesDir(), FILE_NAME);
    }

    /** Test seam: a snapshot with no file and no runtime behind it. */
    @NonNull
    public static Snapshot snapshotForTest(boolean loaded, boolean loading, boolean generating,
                                           @Nullable String modelId, long idleUnloadAtMs,
                                           long publishedAtMs) {
        return new Snapshot(loaded, loading, generating, modelId, idleUnloadAtMs, publishedAtMs);
    }

    /** Test seam: writes a snapshot without a runtime behind it. */
    static boolean writeRawForTest(@NonNull Context context, @NonNull String payload) {
        return write(context.getApplicationContext(), payload);
    }
}
