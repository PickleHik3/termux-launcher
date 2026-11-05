package com.termux.ai;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * What the Speech accelerator row offers and says (Settings > Keyboard > Voice input > Speech
 * model). Auto (which runs on the CPU) and CPU are always offered. GPU, the only way onto the GPU, is hidden when {@link TaiDeviceCapabilities#supportsAccelerator}
 * says this phone cannot run it, or when {@link TaiRuntimeHistory} holds an unexpired
 * {@code crash:} GPU failure ({@link SttGraphChooser#CRASH_REASON_PREFIX}) for the selected speech
 * model on this phone: the chooser refuses an explicit GPU request in that case, so offering it
 * would be a pick that cannot take effect. Any other GPU failure leaves the option visible; the
 * chooser retries the GPU on a forced request and falls back to CPU if it fails again.
 */
public final class SttAcceleratorChoices {
    private SttAcceleratorChoices() {
    }

    /** Option values in display order: {@code auto}, {@code cpu}, then {@code gpu} when it can run. */
    @NonNull
    public static List<String> options(boolean deviceSupportsGpu, @Nullable String gpuFailure) {
        ArrayList<String> options = new ArrayList<>(3);
        options.add(SttAccelerator.AUTO.id);
        options.add(SttAccelerator.CPU.id);
        if (gpuOffered(deviceSupportsGpu, gpuFailure)) options.add(SttAccelerator.GPU.id);
        return options;
    }

    /** Whether the GPU option is shown. */
    public static boolean gpuOffered(boolean deviceSupportsGpu, @Nullable String gpuFailure) {
        return deviceSupportsGpu && (gpuFailure == null || !gpuFailure.startsWith(SttGraphChooser.CRASH_REASON_PREFIX));
    }

    /**
     * Whether a forced GPU pick is running on the CPU: the pick is GPU and the GPU is either not
     * offered or has an unexpired failure on record (the chooser's fallback writes that record).
     */
    public static boolean gpuForcedButUnavailable(@NonNull String selected, boolean deviceSupportsGpu, @Nullable String gpuFailure) {
        return SttAccelerator.GPU.id.equals(selected) && (!deviceSupportsGpu || gpuFailure != null);
    }

    /** The reason of an unexpired GPU failure for {@code spec} on this phone, or {@code null}. */
    @Nullable
    public static String gpuFailure(@NonNull Context context, @Nullable TaiModelSpec spec) {
        if (spec == null) return null;
        JSONObject entry = TaiRuntimeHistory.failedEntry(context, spec, TaiDeviceCapabilities.detect(context), "gpu");
        return entry == null ? null : entry.optString("reason", "gpu failed");
    }

    public static boolean deviceSupportsGpu(@NonNull Context context) {
        return TaiDeviceCapabilities.detect(context).supportsAccelerator("gpu");
    }
}
