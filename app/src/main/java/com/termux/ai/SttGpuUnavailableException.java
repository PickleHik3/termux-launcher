package com.termux.ai;

import androidx.annotation.NonNull;

/**
 * A speech model that needs the GPU ({@link TaiModelCatalog.CatalogEntry#gpuPrecisionFp32}) could not
 * load or prove itself on it. Nothing falls back to the CPU on its own: the person is told, and
 * choosing CPU under Accelerator runs the model there anyway.
 */
final class SttGpuUnavailableException extends Exception {
    static final String CODE = "stt_gpu_unavailable";

    SttGpuUnavailableException(@NonNull String reason, @NonNull Throwable cause) {
        super(reason, cause);
    }

    SttGpuUnavailableException(@NonNull String reason) {
        super(reason);
    }
}
