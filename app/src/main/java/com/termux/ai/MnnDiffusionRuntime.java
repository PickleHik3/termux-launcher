package com.termux.ai;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.alibaba.mnnllm.android.llm.TaiDiffusionSession;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Text-to-image for MNN diffusion packages (Stable Diffusion 1.5, Taiyi, Sana), backed by the MNN
 * Diffusion engine through {@link TaiDiffusionSession}. Follows {@link MnnEmbeddingRuntime}: it loads
 * lazily on the first generation, registers in {@link TaiResidency} as {@link TaiResidency.Kind#IMAGE}
 * and deregisters on close; an {@link UnsatisfiedLinkError} (a {@code libmnnllmapp.so} without the TAI
 * diffusion JNI) becomes a clear 501 instead of a crash.
 *
 * <p>Residency. The model stays loaded after a run only in memory mode 1 and only for Stable
 * Diffusion/Taiyi ({@link TaiImageAdmission#staysResident}): every other combination frees the
 * engine after each run, exactly as upstream does, and the idle-unload only ever has a mode-1 model
 * to close. The registry entry exists for the whole run either way, so the budget and the pressure
 * watch see the memory while it is in use.
 *
 * <p>One generation at a time ({@code image_generation_active}). The engine ignores the progress
 * callback's return value, so a run cannot be stopped mid-way: {@link #requestCancel} raises a flag
 * that is checked between runs and discards the result of the run in flight.
 */
final class MnnDiffusionRuntime implements AutoCloseable {
    /** One run's inputs, after admission chose the memory mode. */
    static final class Params {
        @NonNull final TaiModelSpec spec;
        @NonNull final String modelDir;
        final int type;
        @NonNull final String backend;
        final int memoryMode;
        final long peakBytes;
        @NonNull final String prompt;
        @Nullable final String inputImage;
        @NonNull final File output;
        final int width;
        final int height;
        final int steps;
        /** -1 picks a random seed, which the result reports. */
        final int seed;
        /** 0 turns classifier-free guidance off (Sana only). */
        final float cfgScale;

        Params(@NonNull TaiModelSpec spec, @NonNull String modelDir, int type, @NonNull String backend, int memoryMode,
               long peakBytes, @NonNull String prompt, @Nullable String inputImage, @NonNull File output, int width,
               int height, int steps, int seed, float cfgScale) {
            this.spec = spec;
            this.modelDir = modelDir;
            this.type = type;
            this.backend = backend;
            this.memoryMode = memoryMode;
            this.peakBytes = peakBytes;
            this.prompt = prompt;
            this.inputImage = inputImage;
            this.output = output;
            this.width = width;
            this.height = height;
            this.steps = steps;
            this.seed = seed;
            this.cfgScale = cfgScale;
        }
    }

    interface Progress {
        void onProgress(int percent);
    }

    private final TaiResidency residency;
    @Nullable private final Context appContext;
    private final AtomicBoolean active = new AtomicBoolean();
    private volatile boolean cancelRequested;
    @Nullable private TaiDiffusionSession session;
    @Nullable private String loadedModelId;
    @Nullable private String loadedKey;

    MnnDiffusionRuntime(@NonNull TaiResidency residency, @Nullable Context context) {
        this.residency = residency;
        this.appContext = context == null ? null : context.getApplicationContext();
    }

    boolean isActive() {
        return active.get();
    }

    /** Asks the run in flight to be discarded when the engine returns; false when nothing is running. */
    boolean requestCancel() {
        if (!active.get()) return false;
        cancelRequested = true;
        return true;
    }

    @NonNull
    JSONObject generate(@NonNull Params p, @NonNull Progress progress) throws JSONException {
        if (!active.compareAndSet(false, true)) {
            return error(409, "image_generation_active", "An image is already being generated. Wait for it to finish.");
        }
        try {
            return generateLocked(p, progress);
        } finally {
            active.set(false);
        }
    }

    @NonNull
    private synchronized JSONObject generateLocked(@NonNull Params p, @NonNull Progress progress) throws JSONException {
        cancelRequested = false;
        int seed = p.seed >= 0 ? p.seed : new Random().nextInt(Integer.MAX_VALUE);
        boolean resident = TaiImageAdmission.staysResident(p.memoryMode, p.type);
        String key = p.modelDir + "|" + p.type + "|" + p.backend + "|" + p.memoryMode;
        TaiLoadMeter meter = null;
        boolean registered = false;
        try {
            if (session == null || !key.equals(loadedKey) || !session.isLoaded()) {
                closeSession();
                TaiDiffusionSession created = new TaiDiffusionSession();
                created.load(p.modelDir, p.type, p.backend, p.memoryMode, cacheDirFor(p.spec.id).getAbsolutePath());
                session = created;
                loadedKey = key;
            }
            loadedModelId = p.spec.id;
            residency.register(TaiResidency.Entry.image(p.spec.id, p.backend, p.memoryMode,
                TaiResidency.imageEstimateBytes(p.peakBytes, p.memoryMode)));
            registered = true;
            residency.setBusy(TaiResidency.Kind.IMAGE, p.spec.id, true);
            File parent = p.output.getParentFile();
            if (parent != null && !parent.isDirectory() && !parent.mkdirs()) {
                return error(500, "image_output_unwritable", "The image could not be saved.");
            }
            meter = TaiLoadMeter.start(appContext);
            long started = System.nanoTime();
            TaiDiffusionSession.Result result = session.generate(p.prompt, p.inputImage == null ? "" : p.inputImage,
                p.output.getAbsolutePath(), p.width, p.height, p.steps, seed, p.cfgScale > 0f, p.cfgScale,
                progressText -> {
                    try {
                        progress.onProgress(Math.max(0, Math.min(100, Integer.parseInt(progressText == null ? "" : progressText.trim()))));
                    } catch (NumberFormatException ignored) {
                        // The engine only sends integer percentages; anything else is not progress.
                    }
                    return false;
                });
            long generateMs = (System.nanoTime() - started) / 1_000_000L;
            long measured = meter.stop();
            meter = null;
            if (cancelRequested) {
                //noinspection ResultOfMethodCallIgnored
                p.output.delete();
                return error(409, "image_generation_cancelled", "Image generation was cancelled.");
            }
            if (!result.ok || !p.output.isFile() || p.output.length() == 0L) {
                //noinspection ResultOfMethodCallIgnored
                p.output.delete();
                closeSession();
                registered = false;
                return error(500, "image_generation_failed", result.message.isEmpty()
                    ? "The image engine could not generate this image." : result.message);
            }
            recordMeasured(p, measured);
            long loadMs = result.loadUs / 1000L;
            JSONObject out = new JSONObject();
            out.put("file", p.output.getAbsolutePath());
            out.put("width", p.width);
            out.put("height", p.height);
            out.put("steps", p.steps);
            out.put("seed", seed);
            out.put("backend", p.backend);
            out.put("memoryMode", p.memoryMode);
            out.put("modelType", TaiDiffusionPackage.typeName(p.type));
            out.put("loadMs", loadMs);
            out.put("generateMs", Math.max(0L, generateMs - loadMs));
            return out;
        } catch (UnsatisfiedLinkError e) {
            closeSession();
            registered = false;
            return error(501, "mnn_image_unavailable",
                "This build's MNN native library does not include image generation. Update to a build with MNN image support.");
        } catch (Throwable t) {
            closeSession();
            registered = false;
            return error(500, "image_generation_failed",
                "Image generation failed: " + (t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage()));
        } finally {
            if (meter != null) meter.stop();
            if (registered) {
                if (resident) residency.setBusy(TaiResidency.Kind.IMAGE, p.spec.id, false);
                else closeSession();
            }
        }
    }

    private void recordMeasured(@NonNull Params p, long measured) {
        if (measured <= 0L || appContext == null) return;
        TaiRuntimeHistory.recordMeasuredLoad(appContext, p.spec, TaiDeviceCapabilities.detect(appContext),
            TaiModelSpec.BACKEND_MNN_DIFFUSION, p.backend, p.memoryMode + 1, measured);
    }

    /**
     * The engine's OpenCL tuning cache is written relative to the working directory; the bridge
     * enters this per-model directory before it loads, so the first load tunes and later loads reuse.
     */
    @NonNull
    private File cacheDirFor(@NonNull String modelId) {
        File base = appContext == null ? new File(System.getProperty("java.io.tmpdir", "/tmp")) : appContext.getCacheDir();
        String safe = modelId.replaceAll("[^A-Za-z0-9._-]", "_");
        return new File(new File(base, "tai-diffusion-cache"), safe);
    }

    private void closeSession() {
        if (session != null) {
            try { session.release(); } catch (Throwable ignored) { }
            session = null;
        }
        if (loadedModelId != null) residency.deregister(TaiResidency.Kind.IMAGE, loadedModelId);
        loadedModelId = null;
        loadedKey = null;
    }

    @NonNull
    static JSONObject error(int status, @NonNull String code, @NonNull String message) throws JSONException {
        JSONObject error = new JSONObject();
        error.put("message", message);
        error.put("type", status >= 500 ? "server_error" : "invalid_request_error");
        error.put("code", code);
        JSONObject response = new JSONObject();
        response.put("error", error);
        response.put("_statusCode", status);
        return response;
    }

    @Override
    public synchronized void close() {
        closeSession();
    }
}
