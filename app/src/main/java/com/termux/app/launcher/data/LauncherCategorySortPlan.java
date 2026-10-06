package com.termux.app.launcher.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.ai.TaiFunctionModels;
import com.termux.ai.TaiModelRegistry;

/**
 * What an app-category sort asks, as the APP_CATEGORIES function resolves it ({@link
 * TaiFunctionModels}): a local model on an accelerator, or the remote provider's model. Replaces the
 * dialog's own E4B-or-E2B rule. Pure: no Android, no I/O.
 */
public final class LauncherCategorySortPlan {
    /** Measured on-device throughput, used only for the "takes about N minutes" estimate. */
    static final int SECONDS_PER_APP_E4B = 3;
    static final int SECONDS_PER_APP_E2B = 1;
    static final int CPU_SLOWDOWN = 2;

    /** The local model id or {@code remote/<id>}; {@code null} when no model serves the function. */
    @Nullable public final String model;
    /** {@code gpu} or {@code cpu} for a local model, else {@code null}. */
    @Nullable public final String accelerator;
    public final boolean remote;
    /** The model file is a quarter of the RAM or more: "May close apps running in the background". */
    public final boolean warnBackground;

    LauncherCategorySortPlan(@Nullable String model, @Nullable String accelerator, boolean remote, boolean warnBackground) {
        this.model = model;
        this.accelerator = accelerator;
        this.remote = remote;
        this.warnBackground = warnBackground;
    }

    /** True when some model (local or remote) can sort. */
    public boolean hasModel() {
        return model != null;
    }

    /** The pure mapping from the function's resolution. */
    @NonNull
    public static LauncherCategorySortPlan of(@NonNull TaiFunctionModels.Resolution resolution) {
        if (resolution.isRemote()) return new LauncherCategorySortPlan(resolution.remoteModel, null, true, false);
        return new LauncherCategorySortPlan(resolution.modelId, resolution.accelerator, false, resolution.warnBackground);
    }

    /** The estimate on the plan's own accelerator, as the tier policy would pick it. */
    public int estimatedMinutes(int appCount) {
        return estimatedMinutes(appCount, accelerator);
    }

    /**
     * Rounded up and never zero: "about 0 minutes" would read as instant. The per-app figures were
     * measured on the GPU; the CPU is not measured and is assumed twice as slow, so a sort that will
     * run on the CPU (no speed test yet) is not promised at GPU speed.
     */
    public int estimatedMinutes(int appCount, @Nullable String onAccelerator) {
        int secondsPerApp = model != null && model.startsWith(TaiModelRegistry.MODEL_GEMMA_4_E4B_IT)
            ? SECONDS_PER_APP_E4B : SECONDS_PER_APP_E2B;
        if ("cpu".equals(onAccelerator)) secondsPerApp *= CPU_SLOWDOWN;
        return Math.max(1, (int) Math.ceil(appCount * secondsPerApp / 60.0));
    }

    /** The remote model's own id without the {@code remote/} prefix, or the local id. */
    @NonNull
    public String displayId() {
        if (model == null) return "";
        return remote ? model.substring(TaiFunctionModels.REMOTE_PREFIX.length()) : model;
    }
}
