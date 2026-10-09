package com.termux.ai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Decides how much context a model gets on this device.
 *
 * <p>Catalog entries carry an {@code endpointContextWindow} and the model's real
 * {@code sourceContextWindow}. The window this class answers sizes the LiteRT-LM engine budget and
 * MNN's {@code max_all_tokens}, and it is what {@code /v1/models} advertises. A user setting
 * selects a window up to the RAM class's {@link #mostCap}; without one the window is the RAM
 * class's {@link #tierCap}, never above {@link #AUTO_MAX_CONTEXT} on any model or backend and never
 * above what the model supports. The catalog's endpoint window no longer raises an automatic one:
 * an MNN default of 8192, or an imported package that states 16k or 32k, would otherwise load at
 * that size with nobody having asked for it.
 */
public final class TaiContextWindowPolicy {
    private static final long GIB = 1L << 30;
    /**
     * The largest window an automatic setting gives any model. The launcher's own callers (voice
     * polish, app categories) are one-shot tasks that fit well inside it, and 8k cost ~0.8 GB more
     * than 4k for E4B on pong's GPU and decoded slower.
     */
    public static final int AUTO_MAX_CONTEXT = 4096;
    /** The automatic window on 6 GB and under, where every quarter gigabyte of KV cache counts. */
    public static final int SMALL_RAM_AUTO_CONTEXT = 2048;

    private TaiContextWindowPolicy() {
    }

    /**
     * The window an automatic setting gives a model on a device with {@code memoryBytes} of RAM,
     * by RAM class (see {@link TaiLoadBudget#ramClassBytes}): {@link #SMALL_RAM_AUTO_CONTEXT} up to
     * 6 GB, {@link #AUTO_MAX_CONTEXT} from 8 GB up. The window is KV cache the phone must hold for
     * as long as the model is resident, so a larger automatic window was only ever a 32k freeze
     * waiting for a day with enough free memory. Applies to MNN as much as LiteRT. {@code 0} when
     * the RAM is unknown.
     */
    public static int tierCap(long memoryBytes) {
        long ramClass = TaiLoadBudget.ramClassBytes(memoryBytes);
        if (ramClass <= 0L) return 0;
        return ramClass <= 6L * GIB ? SMALL_RAM_AUTO_CONTEXT : AUTO_MAX_CONTEXT;
    }

    /**
     * The largest window a user setting may select ("most"): 4096 up to 8 GB, 8192 at 10-12 GB,
     * 16384 from 16 GB up. {@code 0} when the RAM is unknown, which leaves a setting as chosen.
     */
    public static int mostCap(long memoryBytes) {
        long ramClass = TaiLoadBudget.ramClassBytes(memoryBytes);
        if (ramClass <= 0L) return 0;
        if (ramClass <= 8L * GIB) return 4096;
        if (ramClass <= 12L * GIB) return 8192;
        return 16_384;
    }

    /**
     * @param spec          the model as stored (source window, profile and artifact limits)
     * @param memoryBytes   device RAM, {@code 0} when unknown ({@link #AUTO_MAX_CONTEXT} for Auto,
     *                      a setting as chosen)
     * @param userOverride  the Context window setting, {@code null} for Auto
     */
    public static int effectiveEndpointContextWindow(
        @NonNull TaiModelSpec spec,
        long memoryBytes,
        @Nullable Integer userOverride
    ) {
        int limit = Math.max(1, spec.sourceContextWindow);
        int profileLimit = TaiModelProfile.forModel(spec).maxContextTokens;
        if (profileLimit > 0) limit = Math.min(limit, profileLimit);
        int artifactLimit = artifactContextLimit(spec.localPath);
        if (TaiModelSpec.BACKEND_LITERT_LM.equals(spec.backend) && artifactLimit > 0)
            limit = Math.min(limit, artifactLimit);
        if (userOverride != null && userOverride > 0) {
            int chosen = Math.max(1024, userOverride);
            int most = mostCap(memoryBytes);
            // A setting never goes past what the RAM class allows; an unknown class leaves it as chosen.
            return Math.min(limit, most > 0 ? Math.min(chosen, Math.max(most, 1024)) : chosen);
        }
        int cap = tierCap(memoryBytes);
        return Math.min(limit, cap > 0 ? cap : AUTO_MAX_CONTEXT);
    }

    /** Published fixed-cache MedGemma exports. Do not infer hard limits from arbitrary filenames. */
    public static int artifactContextLimit(String path) {
        if (path == null) return 0;
        String name = path.substring(path.lastIndexOf('/') + 1).split("[?#]", 2)[0];
        if (name.equals("medgemma-1.5-4b-it_q4_block32_ekv2048.litertlm")
            || name.equals("medgemma-1.5-4b-it_q4_block32_vision_ekv2048.litertlm")) return 2048;
        return 0;
    }

    @NonNull
    public static TaiModelSpec apply(@NonNull TaiModelSpec spec, long memoryBytes, @Nullable Integer userOverride) {
        return spec.withEndpointContextWindow(effectiveEndpointContextWindow(spec, memoryBytes, userOverride));
    }
}
