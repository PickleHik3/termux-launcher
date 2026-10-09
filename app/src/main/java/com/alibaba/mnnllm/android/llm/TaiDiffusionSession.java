package com.alibaba.mnnllm.android.llm;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONObject;

/**
 * VAJ Terminal / TAI addition - thin wrapper over the MNN Diffusion engine (text-to-image).
 *
 * <p>Stable Diffusion 1.5, Taiyi and Sana go through one session. The native symbols come from the
 * TAI diffusion JNI ({@code ci/mnn-patch/tai_diffusion_jni.cpp}) compiled into
 * {@code libmnnllmapp.so}; older builds without them raise {@link UnsatisfiedLinkError} on first
 * use, which callers treat as "MNN image generation unavailable in this build".
 *
 * <p>The engine cannot abort a run mid-way (it ignores the progress callback's return value), so a
 * session offers no cancel; cancellation is the caller's flag between runs plus a discarded result.
 */
public final class TaiDiffusionSession {
    public static final int TYPE_SD15 = 0;
    public static final int TYPE_TAIYI = 1;
    public static final int TYPE_SANA = 2;

    /** Parsed outcome of one native run. */
    public static final class Result {
        public final boolean ok;
        public final long totalUs;
        public final long loadUs;
        @NonNull public final String message;

        Result(boolean ok, long totalUs, long loadUs, @NonNull String message) {
            this.ok = ok;
            this.totalUs = totalUs;
            this.loadUs = loadUs;
            this.message = message;
        }
    }

    private long nativePtr;

    static {
        System.loadLibrary("MNN");
        System.loadLibrary("mnnllmapp");
    }

    /**
     * Records the model and engine settings; the engine itself loads lazily inside the first
     * {@link #generate} so load time is reported with the run.
     */
    public synchronized void load(@NonNull String modelDir, int modelType, @NonNull String backend,
                                  int memoryMode, @NonNull String cacheDir) {
        if (nativePtr != 0L) release();
        nativePtr = initNative(modelDir, modelType, backend, memoryMode, cacheDir);
        if (nativePtr == 0L) throw new IllegalStateException("MNN image model setup failed.");
    }

    public synchronized boolean isLoaded() {
        return nativePtr != 0L;
    }

    @NonNull
    public synchronized Result generate(@NonNull String prompt, @NonNull String inputImagePath,
                                        @NonNull String outputPath, int width, int height, int steps,
                                        int seed, boolean useCfg, float cfgScale,
                                        @Nullable GenerateProgressListener listener) {
        if (nativePtr == 0L) throw new IllegalStateException("MNN image model is not loaded.");
        String json = generateNative(nativePtr, prompt, inputImagePath, outputPath, width, height,
                steps, seed, useCfg, cfgScale, listener);
        return parseResult(json);
    }

    public synchronized void release() {
        if (nativePtr != 0L) {
            releaseNative(nativePtr);
            nativePtr = 0L;
        }
    }

    @NonNull
    static Result parseResult(@Nullable String json) {
        if (json == null || json.isEmpty()) return new Result(false, 0L, 0L, "The image engine returned nothing.");
        try {
            JSONObject o = new JSONObject(json);
            return new Result(o.optBoolean("ok", false), o.optLong("totalUs", 0L),
                    o.optLong("loadUs", 0L), o.optString("message", ""));
        } catch (Exception e) {
            return new Result(false, 0L, 0L, "The image engine returned an unreadable result.");
        }
    }

    private native long initNative(String modelDir, int modelType, String backend, int memoryMode,
                                   String cacheDir);
    private native String generateNative(long ptr, String prompt, String inputImagePath,
                                         String outputPath, int width, int height, int steps,
                                         int seed, boolean useCfg, float cfgScale,
                                         GenerateProgressListener listener);
    private native void releaseNative(long ptr);
}
