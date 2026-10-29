package com.termux.app.setup;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;

import androidx.annotation.NonNull;

import com.termux.ai.TaiDownloadHub;
import com.termux.ai.TaiManager;
import com.termux.ai.TaiModelCatalog;
import com.termux.ai.TaiModelSpec;
import com.termux.ai.TaiModelStore;
import com.termux.ai.TaiSettings;
import com.termux.ai.TaiSpeechModels;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Starting the AI card's downloads and reading how far they are (moved from the old welcome
 * card's downloads). No view. The Wi-Fi choice lives in the old card's preference file and key, so
 * an updater keeps it.
 */
final class AiModelDownloads {
    private AiModelDownloads() {}

    private static final String PREFS = "tai_welcome_card";
    private static final String KEY_WIFI_ONLY = "wifi_only";
    private static final int WHISPER_WINDOW_SECONDS = 10;

    enum Start { STARTED, NEEDS_WIFI, NOTHING }

    /** Bytes done against bytes wanted over the ticked models; allInstalled when all are present. */
    static final class Progress {
        final long doneBytes;
        final long totalBytes;
        final boolean allInstalled;

        Progress(long doneBytes, long totalBytes, boolean allInstalled) {
            this.doneBytes = doneBytes;
            this.totalBytes = totalBytes;
            this.allInstalled = allInstalled;
        }

        float fraction() {
            return totalBytes <= 0L ? 0f : Math.min(1f, doneBytes / (float) totalBytes);
        }
    }

    private static SharedPreferences prefs(@NonNull Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static boolean wifiOnly(@NonNull Context context) {
        return prefs(context).getBoolean(KEY_WIFI_ONLY, true);
    }

    static void setWifiOnly(@NonNull Context context, boolean wifiOnly) {
        prefs(context).edit().putBoolean(KEY_WIFI_ONLY, wifiOnly).apply();
    }

    @NonNull
    static AiModelRows.Sizes catalogSizes() {
        return modelId -> {
            TaiModelCatalog.CatalogEntry entry = TaiModelCatalog.get(modelId);
            return entry == null ? 0L : entry.sizeBytes;
        };
    }

    private static final String KEY_SELECTED = "setup_ai_selected";

    /** The model ids the user chose to download on the AI card; empty until then. */
    @NonNull
    static Set<String> selected(@NonNull Context context) {
        return new HashSet<>(prefs(context).getStringSet(KEY_SELECTED, new HashSet<>()));
    }

    static void saveSelected(@NonNull Context context, @NonNull List<String> modelIds) {
        prefs(context).edit().putStringSet(KEY_SELECTED, new HashSet<>(modelIds)).apply();
    }

    /** The catalogue ids already installed on the phone. */
    @NonNull
    static Set<String> installedIds(@NonNull Context context) {
        return new HashSet<>(new TaiModelStore(context).getInstalledUserModels().keySet());
    }

    /** Queues {@code modelIds} in the order given, unless Wi-Fi only stands in the way. */
    @NonNull
    static Start start(@NonNull Context context, @NonNull List<String> modelIds, boolean wifiOnly) {
        if (modelIds.isEmpty()) return Start.NOTHING;
        if (wifiOnly && isMetered(context)) return Start.NEEDS_WIFI;
        queue(context, modelIds);
        return Start.STARTED;
    }

    /**
     * Progress over {@code modelIds}: an installed model counts whole (installed per the store or
     * a snapshot saying so); a model with a record counts its bytes read; one with none counts
     * nothing against its catalogue size.
     */
    @NonNull
    static Progress progress(@NonNull List<TaiDownloadHub.Snapshot> snapshots,
                             @NonNull List<String> modelIds, @NonNull Set<String> installedIds,
                             @NonNull AiModelRows.Sizes sizes) {
        long done = 0L;
        long total = 0L;
        boolean all = true;
        for (String id : modelIds) {
            long size = Math.max(0L, sizes.bytesOf(id));
            TaiDownloadHub.Snapshot latest = null;
            for (TaiDownloadHub.Snapshot snap : snapshots) {
                if (id.equals(snap.modelId)) latest = snap; // oldest first: the last is newest
            }
            if (latest != null && latest.totalBytes > 0L) size = latest.totalBytes;
            total += size;
            boolean installed = installedIds.contains(id)
                || (latest != null && TaiModelStore.STATE_INSTALLED.equals(latest.status));
            if (installed) {
                done += size;
            } else {
                all = false;
                if (latest != null) done += Math.max(0L, Math.min(size, latest.bytesRead));
            }
        }
        return new Progress(done, total, all);
    }

    private static boolean isMetered(@NonNull Context context) {
        ConnectivityManager manager = (ConnectivityManager)
            context.getSystemService(Context.CONNECTIVITY_SERVICE);
        return manager != null && manager.isActiveNetworkMetered();
    }

    /** Queues through the Model Centre's calls, off the main thread (starting writes a record). */
    private static void queue(@NonNull Context context, @NonNull List<String> modelIds) {
        Context app = context.getApplicationContext();
        Thread thread = new Thread(() -> {
            boolean noSpeechYet = TaiSpeechModels.installed(new TaiModelStore(app)).isEmpty();
            Set<String> startedSpeech = new HashSet<>();
            for (String id : modelIds) {
                try {
                    TaiModelCatalog.CatalogEntry entry = TaiModelCatalog.get(id);
                    boolean speech = entry != null
                        && entry.capabilities.contains(TaiModelSpec.CAPABILITY_SPEECH_TO_TEXT);
                    if (!speech) {
                        TaiManager.getInstance(app).downloadCatalogModel(id);
                    } else if (noSpeechYet && startedSpeech.isEmpty() && noPendingVoiceChoice(app)) {
                        TaiSpeechModels.startDownload(app, id, WHISPER_WINDOW_SECONDS);
                    } else {
                        TaiManager.getInstance(app).downloadSpeechModel(id, WHISPER_WINDOW_SECONDS);
                    }
                    if (speech) startedSpeech.add(id);
                } catch (Exception ignored) {
                    // One failed start must not stop the rest; its row shows in the Model Centre.
                }
            }
        }, "setup-ai-download");
        thread.setDaemon(true);
        thread.start();
    }

    private static boolean noPendingVoiceChoice(@NonNull Context context) {
        String pending = new TaiSettings(context).getSttPendingDownloadJson();
        return pending == null || pending.isEmpty();
    }
}
