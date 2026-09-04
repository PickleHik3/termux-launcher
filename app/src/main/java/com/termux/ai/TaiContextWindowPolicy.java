package com.termux.ai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Decides how much context a model gets on this device.
 *
 * <p>Catalog entries carry a conservative {@code endpointContextWindow} (the floor, safe on any
 * supported phone) and the model's real {@code sourceContextWindow}. The endpoint window sizes the
 * LiteRT-LM engine budget and MNN's {@code max_all_tokens}, and it is what {@code /v1/models}
 * advertises, so it should grow with device memory instead of staying at the floor forever. An
 * explicit user setting always wins; otherwise the window is raised to the RAM tier's cap, never
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
        if (userOverride != null && userOverride > 0) return Math.max(1024, userOverride);
        int cap = tierCap(memoryBytes);
        if (cap <= 0) return spec.endpointContextWindow;
        int raised = Math.min(cap, Math.max(spec.sourceContextWindow, spec.endpointContextWindow));
        return Math.max(spec.endpointContextWindow, raised);
    }

    @NonNull
    public static TaiModelSpec apply(@NonNull TaiModelSpec spec, long memoryBytes, @Nullable Integer userOverride) {
        return spec.withEndpointContextWindow(effectiveEndpointContextWindow(spec, memoryBytes, userOverride));
    }
}
