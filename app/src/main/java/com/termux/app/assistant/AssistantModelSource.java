package com.termux.app.assistant;

import android.content.Context;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.ai.TaiModelSpec;
import com.termux.ai.TaiModelStore;
import com.termux.ai.TaiRuntimePresence;
import com.termux.ai.TaiSettings;

import org.json.JSONObject;

import java.util.HashSet;
import java.util.Set;

/**
 * The real {@link AssistantModelStatus.Source}: the runtime's published presence file, the model
 * store's download records and what is on disk. Read from the poller's thread, never the main one.
 */
final class AssistantModelSource implements AssistantModelStatus.Source {
    /** Listing the downloaded models inspects every package on disk, so it is not done every second. */
    private static final long DOWNLOADED_CACHE_MS = 5_000L;

    private final Context appContext;
    private final TaiSettings settings;
    private final TaiModelStore store;
    private TaiRuntimePresence.Snapshot presence;
    private long presenceReadAt = Long.MIN_VALUE;
    private Set<String> downloaded = new HashSet<>();
    private long downloadedReadAt = Long.MIN_VALUE;

    AssistantModelSource(@NonNull Context context) {
        appContext = context.getApplicationContext();
        settings = new TaiSettings(appContext);
        store = new TaiModelStore(appContext);
    }

    @NonNull
    private synchronized TaiRuntimePresence.Snapshot presence() {
        long now = SystemClock.elapsedRealtime();
        // One poll asks several questions; they should all see the same file.
        if (presence == null || now - presenceReadAt > 200L) {
            presence = TaiRuntimePresence.read(appContext);
            presenceReadAt = now;
        }
        return presence;
    }

    @NonNull
    @Override
    public String defaultModelId() {
        return settings.getDefaultAssistantModel();
    }

    @Override
    public boolean runtimeLoaded() {
        return presence().loaded;
    }

    @Override
    public boolean runtimeLoading() {
        return presence().loading;
    }

    @Nullable
    @Override
    public String runtimeModelId() {
        return presence().modelId;
    }

    /**
     * Whether a cache read at {@code readAt} is older than {@code ttlMs} at {@code now}. A cache never
     * read ({@code Long.MIN_VALUE}) is stale: {@code now - Long.MIN_VALUE} overflows to a negative
     * number, which once kept the downloaded set empty for good and every installed model asked to
     * be downloaded.
     */
    static boolean isStale(long now, long readAt, long ttlMs) {
        return readAt == Long.MIN_VALUE || now - readAt > ttlMs;
    }

    @Override
    public synchronized boolean isDownloaded(@NonNull String modelId) {
        long now = SystemClock.elapsedRealtime();
        if (isStale(now, downloadedReadAt, DOWNLOADED_CACHE_MS)) {
            Set<String> ids = new HashSet<>();
            for (java.util.Map.Entry<String, TaiModelSpec> e : store.getDownloadedReadableModels().entrySet()) {
                ids.add(e.getKey());
            }
            ids.addAll(store.getInstalledUserModels().keySet());
            downloaded = ids;
            downloadedReadAt = now;
        }
        return downloaded.contains(modelId);
    }

    @Nullable
    @Override
    public AssistantModelStatus.Download download(@NonNull String modelId) {
        JSONObject record = store.findDownloadForModel(modelId);
        if (record == null) return null;
        String status = record.optString("status", "");
        boolean inProgress = TaiModelStore.isLiveDownloadState(status)
            || TaiModelStore.STATE_PAUSED.equals(status);
        return new AssistantModelStatus.Download(inProgress, record.optLong("bytesRead", 0L),
            record.optLong("totalBytes", 0L));
    }
}
