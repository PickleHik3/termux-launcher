package com.termux.ai;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONObject;

/**
 * {@link SttGraphChooser.Memory} on {@link TaiRuntimeHistory}: one model's GPU record on this
 * phone, under the same {@code model | device | gpu} key the chat models' GPU verdicts use (the
 * history file, its lock, its expiry: a failure lapses after the TTL or with a new app version,
 * and {@code tai runtime --clear-history} clears it). Speech model ids never collide with chat
 * ones, and the entries carry {@link #BACKEND} so a chat lookup filtered by backend skips them.
 */
final class SttGpuMemory implements SttGraphChooser.Memory {
    /** The backend name on the speech records. */
    static final String BACKEND = "litert-stt";
    private static final String GPU = "gpu";

    @NonNull private final Context context;
    @NonNull private final TaiModelSpec model;
    @NonNull private final TaiDeviceCapabilities device;

    SttGpuMemory(@NonNull Context context, @NonNull TaiModelSpec model, @NonNull TaiDeviceCapabilities device) {
        this.context = context;
        this.model = model;
        this.device = device;
    }

    @Nullable
    @Override
    public String gpuFailure() {
        JSONObject entry = TaiRuntimeHistory.failedEntry(context, model, device, GPU);
        return entry == null ? null : entry.optString("reason", "gpu failed");
    }

    @Override
    public int beginGpuAttempt() {
        return TaiRuntimeHistory.beginAttempt(context, model, device, BACKEND, GPU);
    }

    @Override
    public void endGpuAttempt() {
        TaiRuntimeHistory.endAttempt(context, model, device, GPU);
    }

    @Override
    public void recordGpu(boolean worked, @NonNull String reason) {
        if (worked) {
            if (!TaiRuntimeHistory.hasSuccessfulGpu(context, model, device)) {
                TaiRuntimeHistory.recordSuccess(context, model, device, BACKEND, GPU);
            }
        } else {
            TaiRuntimeHistory.recordFailure(context, model, device, BACKEND, GPU, reason);
        }
    }
}
