package com.termux.app.firstrun;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

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
 * Starting the welcome downloads, and how far along they are: the one queue both the "Models for
 * this phone" card and the first-run setup sheet hand their ticks to.
 *
 * <p>The download layer has no per-download network rule — it resumes paused work on an unmetered
 * network but starts anything at once — so the Wi-Fi-only choice is honoured here, before anything
 * is queued: on a metered connection nothing starts.
 *
 * <p>{@link #progress} is pure, so the sum the closing card shows is a unit test.
 */
public final class TaiWelcomeDownloads {
    private TaiWelcomeDownloads() {}

    /** Shared with {@link TaiWelcomeCardHost}: the card's own small store. */
    static final String PREFS = "tai_welcome_card";
    private static final String KEY_WIFI_ONLY = "wifi_only";
    /** The window the default Whisper artifact (and the size the card shows) is. */
    private static final int WHISPER_WINDOW_SECONDS = 10;

    /** What asking for the downloads came to. */
    public enum Start {
        /** The models are queued, smallest first. */
        STARTED,
        /** Wi-Fi only is on and the phone is on a metered network: nothing was queued. */
        NEEDS_WIFI,
        /** There was nothing to queue. */
        NOTHING,
        /** Picked on the setup sheet and not started yet: the closing card offers them. */
        CHOSEN
    }

    /** Where one queued model stands, as the download layer reports it. */
    public static final class ModelState {
        /** One of the {@code TaiModelStore.STATE_*} values, or "" when there is no record yet. */
        @NonNull public final String status;
        public final long bytesRead;
        /** The full size when the record knows it, else 0 or less. */
        public final long totalBytes;

        public ModelState(@NonNull String status, long bytesRead, long totalBytes) {
            this.status = status;
            this.bytesRead = bytesRead;
            this.totalBytes = totalBytes;
        }
    }

    /** The download layer's answer for one model id, or null when it has no record of it. */
    public interface Lookup {
        @Nullable ModelState stateOf(@NonNull String modelId);
    }

    /** How far the queued models have come, in bytes. */
    public static final class Progress {
        public final long doneBytes;
        public final long totalBytes;

        Progress(long doneBytes, long totalBytes) {
            this.doneBytes = doneBytes;
            this.totalBytes = totalBytes;
        }

        /** 0..1, for the bar. */
        public float fraction() {
            return totalBytes <= 0L ? 0f : Math.min(1f, doneBytes / (float) totalBytes);
        }
    }

    /** What the setup sheet last queued, for the closing card, kept across a process death. */
    public static final class Queued {
        @NonNull public final List<String> modelIds;
        @NonNull public final Start start;

        public Queued(@NonNull List<String> modelIds, @NonNull Start start) {
            this.modelIds = java.util.Collections.unmodifiableList(
                new java.util.ArrayList<>(modelIds));
            this.start = start;
        }
    }

    private static final String KEY_QUEUED_IDS = "sheet_queued_ids";
    private static final String KEY_QUEUED_START = "sheet_queued_start";

    /** Remembers what the setup sheet asked for, so the closing card can say how it is going. */
    public static void rememberQueued(@NonNull Context context, @NonNull List<String> modelIds,
                                      @NonNull Start start) {
        prefs(context).edit()
            .putString(KEY_QUEUED_IDS, IdList.join(modelIds))
            .putString(KEY_QUEUED_START, start.name())
            .apply();
    }

    /** What the setup sheet asked for, or nothing once the run that asked has ended. */
    @NonNull
    public static Queued queued(@NonNull Context context) {
        SharedPreferences prefs = prefs(context);
        List<String> ids = IdList.split(prefs.getString(KEY_QUEUED_IDS, ""));
        Start start;
        try {
            start = Start.valueOf(prefs.getString(KEY_QUEUED_START, Start.NOTHING.name()));
        } catch (IllegalArgumentException unknown) {
            start = Start.NOTHING;
        }
        return new Queued(ids, start);
    }

    /** The run that asked has ended: the closing card will not be shown for it again. */
    public static void forgetQueued(@NonNull Context context) {
        prefs(context).edit().remove(KEY_QUEUED_IDS).remove(KEY_QUEUED_START).apply();
    }

    /** Model ids are catalogue keys, which never hold a comma. */
    static final class IdList {
        private IdList() {}

        @NonNull
        static String join(@NonNull List<String> ids) {
            StringBuilder out = new StringBuilder();
            for (String id : ids) {
                if (id == null || id.isEmpty()) continue;
                if (out.length() > 0) out.append(',');
                out.append(id);
            }
            return out.toString();
        }

        @NonNull
        static List<String> split(@Nullable String joined) {
            List<String> ids = new java.util.ArrayList<>();
            if (joined == null) return ids;
            for (String id : joined.split(",")) {
                String trimmed = id.trim();
                if (!trimmed.isEmpty()) ids.add(trimmed);
            }
            return ids;
        }
    }

    static SharedPreferences prefs(@NonNull Context context) {
        return context.getApplicationContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** The Wi-Fi-only switch's last position; on until the user turns it off. */
    public static boolean wifiOnly(@NonNull Context context) {
        return prefs(context).getBoolean(KEY_WIFI_ONLY, true);
    }

    static void setWifiOnly(@NonNull Context context, boolean wifiOnly) {
        prefs(context).edit().putBoolean(KEY_WIFI_ONLY, wifiOnly).apply();
    }

    /** The catalogue's size for each download, which is what every screen counts in. */
    @NonNull
    public static TaiWelcomeCard.Sizes catalogSizes() {
        return modelId -> {
            TaiModelCatalog.CatalogEntry entry = TaiModelCatalog.get(modelId);
            return entry == null ? 0L : entry.sizeBytes;
        };
    }

    /**
     * Queues {@code modelIds}, in the order given, unless Wi-Fi only stands in the way.
     *
     * @param wifiOnly whether to start only on an unmetered network
     */
    @NonNull
    public static Start start(@NonNull Context context, @NonNull List<String> modelIds,
                              boolean wifiOnly) {
        if (modelIds.isEmpty()) return Start.NOTHING;
        if (wifiOnly && isMetered(context)) return Start.NEEDS_WIFI;
        queue(context, modelIds);
        return Start.STARTED;
    }

    /**
     * The bytes done against the bytes wanted, over the models queued. An installed model counts
     * whole; a model with no record yet counts nothing done against its catalogue size, and a record
     * that does not know its size yet falls back to the catalogue's.
     */
    @NonNull
    public static Progress progress(@NonNull List<String> modelIds,
                                    @NonNull TaiWelcomeCard.Sizes sizes, @NonNull Lookup lookup) {
        long done = 0L;
        long total = 0L;
        for (String id : modelIds) {
            ModelState state = lookup.stateOf(id);
            long size = Math.max(0L, sizes.bytesOf(id));
            if (state != null && state.totalBytes > 0L) size = state.totalBytes;
            total += size;
            if (state == null) continue;
            if (TaiModelStore.STATE_INSTALLED.equals(state.status)) done += size;
            else done += Math.max(0L, Math.min(size, state.bytesRead));
        }
        return new Progress(done, total);
    }

    /**
     * Whether the phone is on Wi-Fi (or a cable) rather than mobile data. The transport, not the
     * metered flag: some carriers report an unlimited plan as unmetered, and a download of
     * gigabytes on mobile data is still worth a question.
     */
    public static boolean onWifi(@NonNull Context context) {
        ConnectivityManager manager = (ConnectivityManager)
            context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (manager == null) return false;
        android.net.NetworkCapabilities caps =
            manager.getNetworkCapabilities(manager.getActiveNetwork());
        return caps != null
            && !caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR)
            && (caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)
                || caps.hasTransport(android.net.NetworkCapabilities.TRANSPORT_ETHERNET));
    }

    private static boolean isMetered(@NonNull Context context) {
        ConnectivityManager manager = (ConnectivityManager)
            context.getSystemService(Context.CONNECTIVITY_SERVICE);
        return manager != null && manager.isActiveNetworkMetered();
    }

    /**
     * Queues the models through the same calls the Model Centre uses, in the order given (smallest
     * first). Off the main thread: starting a download writes its record. Ticking leaves every
     * function on Automatic, so the downloaded model becomes the pick.
     */
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
                        // The first speech model on the phone becomes the one voice input uses
                        // when it lands, as in the Model Centre.
                        TaiSpeechModels.startDownload(app, id, WHISPER_WINDOW_SECONDS);
                    } else {
                        TaiManager.getInstance(app).downloadSpeechModel(id, WHISPER_WINDOW_SECONDS);
                    }
                    if (speech) startedSpeech.add(id);
                } catch (Exception ignored) {
                    // One failed start must not stop the rest; its row shows in the Model Centre.
                }
            }
        }, "tai-welcome-download");
        thread.setDaemon(true);
        thread.start();
    }

    private static boolean noPendingVoiceChoice(@NonNull Context context) {
        String pending = new TaiSettings(context).getSttPendingDownloadJson();
        return pending == null || pending.isEmpty();
    }
}
