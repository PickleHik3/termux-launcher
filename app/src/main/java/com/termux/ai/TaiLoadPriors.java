package com.termux.ai;

import androidx.annotation.NonNull;

import java.util.Locale;

/**
 * Measured load costs for the bundled Gemma 4 files, used by the load budget when this phone has
 * no measurement of its own yet. The budget's seed guesses a LiteRT GPU load at three quarters of
 * the file plus a tenth for the encoders, which is right for E4B and almost twice the truth for
 * E2B (most of its file is per-layer embeddings the GPU path keeps file-backed). On a phone with
 * 3.5–4 GB free, the seed therefore refused every E2B load — and a load that is never allowed is
 * never measured, so the seed never corrected itself. These figures are pong's worst
 * {@code load_ok mem=} drops of 2026-10-05 (Nothing Phone 2, Adreno 730, LiteRT-LM 1.4): the
 * text and vision variants measured the same, so both read one row. Only the GPU is seeded; the
 * first real load on this phone records its own sample and the history takes over.
 */
final class TaiLoadPriors {

    private static final long MIB = 1024L * 1024L;
    /** The windows a row knows, ascending; {@link #ROWS} values are parallel to it, in MiB. */
    private static final int[] WINDOWS = {1024, 2048, 4096};
    private static final long[] E2B_GPU_MIB = {1235L, 1319L, 1477L};
    private static final long[] E4B_GPU_MIB = {2526L, 3166L, 3292L};

    private TaiLoadPriors() {
    }

    /**
     * The prior drop in bytes for a load of {@code model} on {@code accelerator} with
     * {@code contextTokens}, or {@code 0} when nothing is known: the smallest measured window at
     * or above the request (an upper bound), or the largest one plus {@code slopeBytesPerToken}
     * for every token past it.
     */
    static long bytes(@NonNull TaiModelSpec model, @NonNull String accelerator, int contextTokens,
                      long slopeBytesPerToken) {
        if (!TaiModelSpec.BACKEND_LITERT_LM.equals(model.backend)) return 0L;
        if (!"gpu".equals(accelerator == null ? "" : accelerator.toLowerCase(Locale.ROOT))) return 0L;
        long[] row = rowFor(TaiFunctionModels.baseId(model.id));
        if (row == null) return 0L;
        int wanted = Math.max(0, contextTokens);
        for (int i = 0; i < WINDOWS.length; i++) {
            if (WINDOWS[i] >= wanted) return row[i] * MIB;
        }
        int last = WINDOWS.length - 1;
        return row[last] * MIB + Math.max(0L, slopeBytesPerToken) * (wanted - WINDOWS[last]);
    }

    private static long[] rowFor(@NonNull String baseId) {
        if (TaiModelRegistry.MODEL_GEMMA_4_E2B_IT.equals(baseId)) return E2B_GPU_MIB;
        if (TaiModelRegistry.MODEL_GEMMA_4_E4B_IT.equals(baseId)) return E4B_GPU_MIB;
        return null;
    }
}
