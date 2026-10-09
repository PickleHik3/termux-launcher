package com.termux.app.fragments.settings.termux;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.termux.R;
import com.termux.ai.TaiModelCatalog;
import com.termux.ai.TaiModelSpec;
import com.termux.ai.TaiSpeechModels;
import com.termux.app.notice.AppNotice;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.List;

/**
 * What the two speech-model screens share: the Model centre (which installs and deletes) and the
 * Keyboard's speech model picker (which chooses). Both describe a model the same way and both
 * change its window through the same dialog, so the wording and the rule for "which other window
 * is there" live once, here.
 */
final class TaiSpeechActions {
    private static final long BYTES_PER_GIB = 1024L * 1024L * 1024L;
    /** Small is recommended only on phones with this much RAM; below it the row says so. */
    static final long SMALL_MIN_MEMORY_BYTES = 8L * BYTES_PER_GIB;
    /** Parakeet's one graph is a 5 s window; there is nothing to choose. */
    static final int PARAKEET_WINDOW_SECONDS = 5;

    private TaiSpeechActions() {}

    /** "Whisper · 97 MB · 10-second window", "Parakeet · 586 MB · 5-second window": engine, size, window when known. */
    @NonNull
    static String installedSummary(@NonNull Context context, @NonNull TaiModelSpec spec) {
        int window = TaiSpeechModels.windowSeconds(spec);
        String summary = TaiSpeechModels.engineLabel(spec) + " · " + TaiModelCentreRows.formatBytes(spec.sizeBytes);
        return window > 0 ? summary + " · " + context.getString(R.string.speech_model_window_summary, window) : summary;
    }

    /** The window this model can be re-downloaded with, or 0 when it has none (or only the one). */
    static int otherWindow(@NonNull TaiModelSpec spec) {
        int current = TaiSpeechModels.windowSeconds(spec);
        if (current <= 0) return 0;
        TaiModelCatalog.CatalogEntry entry = TaiModelCatalog.get(spec.id);
        if (entry == null) return 0;
        for (Integer window : entry.speechWindows.keySet()) {
            if (window != null && window != current) return window;
        }
        return 0;
    }

    /** The Whisper family's catalog id for a size and language; Parakeet's is {@link TaiModelCatalog#PARAKEET_TDT_V3_ID}. */
    @NonNull
    static String whisperCatalogId(boolean small, boolean englishOnly) {
        return "whisper-acft-" + (small ? "small" : "base") + (englishOnly ? "-en" : "");
    }

    static boolean isWhisper(@NonNull String modelId) {
        return modelId.startsWith("whisper-");
    }

    static boolean isSmall(@NonNull String modelId) {
        return modelId.startsWith("whisper-acft-small");
    }

    /**
     * The warning under Parakeet on a phone below the entry's RAM tier (the same threshold
     * {@link com.termux.ai.TaiDeviceCapabilities#checkModelCapability} warns on), or null when the
     * phone is fine or its memory is unknown.
     */
    @Nullable
    static String parakeetRamWarning(@NonNull Context context, long deviceMemoryBytes) {
        TaiModelCatalog.CatalogEntry entry = TaiModelCatalog.get(TaiModelCatalog.PARAKEET_TDT_V3_ID);
        if (entry == null || entry.recommendedRamGb <= 0 || deviceMemoryBytes <= 0L) return null;
        if (deviceMemoryBytes >= entry.recommendedRamGb * BYTES_PER_GIB) return null;
        return context.getString(R.string.speech_model_engine_parakeet_ram_warning,
            TaiModelCentreRows.formatBytes(deviceMemoryBytes));
    }

    /** The first installed model other than {@code spec}: the one voice input falls back to. */
    @Nullable
    static TaiModelSpec nextAfter(@NonNull TaiModelSpec spec, @NonNull List<TaiModelSpec> installed) {
        for (TaiModelSpec other : installed) {
            if (!other.id.equals(spec.id)) return other;
        }
        return null;
    }

    /**
     * Asks before downloading the other window's graph for an installed model; the installed one
     * stays in use until the new one is verified. {@code onStarted} runs once the download is in
     * line, so the calling screen can redraw.
     */
    static void showWindowDialog(@NonNull Context context, @NonNull TaiModelSpec spec, int otherWindow,
                                 @Nullable Runnable onStarted) {
        int current = TaiSpeechModels.windowSeconds(spec);
        TaiModelCatalog.CatalogEntry entry = TaiModelCatalog.get(spec.id);
        TaiModelCatalog.CatalogEntry.WindowVariant variant = entry == null ? null : entry.speechWindows.get(otherWindow);
        String size = TaiModelCentreRows.formatBytes(variant == null ? spec.sizeBytes : variant.sizeBytes);
        String name = TaiSpeechModels.plainName(spec);
        new MaterialAlertDialogBuilder(context)
            .setTitle(R.string.speech_model_window_change_title)
            .setMessage(context.getString(R.string.speech_model_window_change_message, name, otherWindow, size, current))
            .setPositiveButton(R.string.speech_model_window_change_action, (dialog, which) -> {
                try {
                    JSONObject result = TaiSpeechModels.startWindowSwitch(context, spec, otherWindow);
                    if (!result.optBoolean("ok", false)) {
                        AppNotice.show(context, result.optString("message",
                            context.getString(R.string.termux_ai_model_action_failed)), true);
                    }
                } catch (JSONException e) {
                    AppNotice.show(context, R.string.termux_ai_model_action_failed, true);
                }
                if (onStarted != null) onStarted.run();
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    /** What a finished download decided, as a one-line notice; nothing while it runs or when nothing was pending. */
    static void announce(@NonNull Context context, @NonNull TaiSpeechModels.Settlement settlement) {
        TaiSpeechModels.PendingDownload pending = settlement.pending;
        if (pending == null) return;
        TaiModelCatalog.CatalogEntry entry = TaiModelCatalog.get(pending.modelId);
        String name = TaiSpeechModels.plainName(pending.modelId, entry == null ? pending.modelId : entry.displayName, null);
        switch (settlement.outcome) {
            case ACTIVATED:
                AppNotice.show(context, context.getString(R.string.speech_model_ready, name), false);
                break;
            case WINDOW_CHANGED:
                AppNotice.show(context, context.getString(R.string.speech_model_window_changed, name, pending.windowSeconds), false);
                break;
            case FAILED:
                AppNotice.show(context, context.getString(R.string.speech_model_failed_notice, name), true);
                break;
            case WINDOW_KEPT:
                AppNotice.show(context, context.getString(R.string.speech_model_window_kept, pending.windowSeconds, name,
                    TaiSpeechModels.windowSeconds(pending.previousPath)), true);
                break;
            default:
                break;
        }
    }
}
