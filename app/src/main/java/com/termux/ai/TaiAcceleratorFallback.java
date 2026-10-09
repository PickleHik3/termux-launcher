package com.termux.ai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The reason {@link TaiManager} gave for planning a load on other than the model's first
 * accelerator, handed to the runtime that performs that load. The decision and the load run in the
 * same process, so a static map is enough; each decision replaces the model's entry, which is what
 * keeps a stale reason from describing a later load that went ahead on the first choice.
 */
final class TaiAcceleratorFallback {
    private static final Map<String, String> REASONS = new ConcurrentHashMap<>();

    private TaiAcceleratorFallback() {
    }

    static void set(@NonNull String modelId, @Nullable String reason) {
        if (reason == null || reason.isEmpty()) REASONS.remove(modelId);
        else REASONS.put(modelId, reason);
    }

    /** The reason for this model's latest load decision; empty when it used the first choice. */
    @NonNull
    static String get(@Nullable String modelId) {
        String reason = modelId == null ? null : REASONS.get(modelId);
        return reason == null ? "" : reason;
    }
}
