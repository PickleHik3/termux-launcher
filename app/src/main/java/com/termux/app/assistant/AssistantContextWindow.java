package com.termux.app.assistant;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.ai.TaiContextWindowPolicy;
import com.termux.ai.TaiDeviceCapabilities;
import com.termux.ai.TaiModelRegistry;
import com.termux.ai.TaiModelSpec;
import com.termux.ai.TaiModelStore;
import com.termux.ai.TaiRuntimePresence;
import com.termux.ai.TaiSettings;

/**
 * The window the default assistant model serves. When that model is loaded, the runtime's own
 * figure; otherwise what a load would be given, worked out the way the runtime does
 * ({@link TaiContextWindowPolicy#effectiveEndpointContextWindow}) from the model's spec, the
 * phone's memory and the Context window setting.
 */
final class AssistantContextWindow implements AssistantEngine.WindowSource {
    private final Context appContext;

    AssistantContextWindow(@NonNull Context context) {
        appContext = context.getApplicationContext();
    }

    @Override
    public int servingWindowTokens() {
        try {
            TaiSettings settings = new TaiSettings(appContext);
            String modelId = settings.getDefaultAssistantModel();
            TaiRuntimePresence.Snapshot presence = TaiRuntimePresence.read(appContext);
            if (presence.loaded && presence.contextWindow > 0 && modelId.equals(presence.modelId)) {
                return presence.contextWindow;
            }
            TaiModelSpec spec = specFor(modelId);
            if (spec == null) return 0;
            long memory = TaiDeviceCapabilities.detect(appContext).memoryBytes;
            return TaiContextWindowPolicy.effectiveEndpointContextWindow(
                spec, Math.max(0L, memory), settings.getRuntimeOptions(spec).contextWindow);
        } catch (RuntimeException e) {
            return 0;
        }
    }

    @Nullable
    private TaiModelSpec specFor(@NonNull String modelId) {
        TaiModelSpec installed = new TaiModelStore(appContext).getUserModel(modelId);
        if (installed != null && installed.localPath != null && !installed.localPath.trim().isEmpty()) {
            return installed;
        }
        return new TaiModelRegistry().getModel(modelId);
    }
}
