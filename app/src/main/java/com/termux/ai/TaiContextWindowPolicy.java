package com.termux.ai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Decides how much context a model gets on this device.
 *
 * <p>Catalog entries carry a conservative {@code endpointContextWindow} (the floor, safe on any
 * supported phone) and the model's real {@code sourceContextWindow}. The endpoint window sizes the
 * LiteRT-LM engine budget and MNN's {@code max_all_tokens}, and it is what {@code /v1/models}
 * advertises, so it should grow with device memory instead of staying at the floor forever. A
 * user setting selects a budget within the supported limit; otherwise the window is raised to the RAM tier's cap, never
 * above what the model supports and never below the catalog floor.
 */
public final class TaiContextWindowPolicy {
    private static final long GIB = 1L << 30;

    private TaiContextWindowPolicy() {
    }

    /** Largest context window this policy hands to a device with {@code memoryBytes} of RAM. */
    public static int tierCap(long memoryBytes) {
        if (memoryBytes <= 0L) return 0;
        if (memoryBytes < 5_632L * GIB / 1024L) return 4096;   // < 5.5 GiB
        if (memoryBytes < 7_680L * GIB / 1024L) return 8192;   // < 7.5 GiB
        if (memoryBytes < 11_776L * GIB / 1024L) return 16_384; // < 11.5 GiB
        return 32_768;
    }

    /**
     * @param spec          the model as stored (catalog floor + source window)
     * @param memoryBytes   device RAM, {@code 0} when unknown (keeps the catalog floor)
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
        if (userOverride != null && userOverride > 0) return Math.min(limit, Math.max(1024, userOverride));
        int cap = tierCap(memoryBytes);
        int requested = cap <= 0 ? spec.endpointContextWindow : Math.max(spec.endpointContextWindow, cap);
        return Math.min(limit, requested);
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
