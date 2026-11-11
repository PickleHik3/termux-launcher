package com.termux.app.assistant;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.ai.ModelAvailability;
import com.termux.ai.TaiDownloadHub;

/**
 * The real {@link AssistantModelStatus.Source}: a thin view over {@link ModelAvailability}, which
 * owns the downloaded list, the runtime presence and the download records. It keeps no cache.
 * Read from the poller's thread, never the main one.
 */
final class AssistantModelSource implements AssistantModelStatus.Source {
    private final ModelAvailability availability;

    AssistantModelSource(@NonNull Context context) {
        availability = ModelAvailability.get(context.getApplicationContext());
    }

    @NonNull
    @Override
    public String defaultModelId() {
        return availability.defaultModelId();
    }

    @NonNull
    @Override
    public ModelAvailability.State stateOf(@NonNull String modelId) {
        return availability.stateOf(modelId);
    }

    @Nullable
    @Override
    public AssistantModelStatus.Download download(@NonNull String modelId) {
        TaiDownloadHub.Snapshot record = availability.download(modelId);
        if (record == null) return null;
        return new AssistantModelStatus.Download(record.isLive() || record.isPaused(), record.bytesRead,
            record.totalBytes);
    }

    @NonNull
    @Override
    public Runnable subscribe(@NonNull Runnable onChange) {
        // addListener and removeListener belong to the main thread; the poller's caller may not be on it.
        final ModelAvailability.Listener listener = onChange::run;
        final Handler main = new Handler(Looper.getMainLooper());
        main.post(() -> availability.addListener(listener));
        return () -> main.post(() -> availability.removeListener(listener));
    }
}
