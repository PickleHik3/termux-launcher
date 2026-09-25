package com.termux.ai;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The speech-to-text models the phone has, which one voice input uses, and what a finished
 * download does to that choice. Everything here goes by the {@code speech_to_text} capability,
 * never by a model's name: a future engine's model (a Parakeet entry, say) is just another
 * catalog entry with that capability and its own runtime, and this class lists it the same way.
 *
 * <p>Two choices are kept in {@link TaiSettings}: the model in use ({@code tai_stt_model_id}) and,
 * while a download is running, the {@link PendingDownload} that will become the model in use
 * once — and only once — it succeeds. An explicit pick from the settings screen forgets a pending
 * fresh download, so a model that finishes downloading later never overrides what the user chose
 * in the meantime. A window switch is a re-download of the same model id under a different file
 * name: the old graph is deleted only after the new one is installed, and kept when it is not.
 */
public final class TaiSpeechModels {
    private static final Object LOCK = new Object();
    /** {@code …_5s_… / …_10s_…} in a Whisper ACFT file name. */
    private static final Pattern WINDOW_IN_FILE_NAME = Pattern.compile("(?:^|_)(\\d+)s_");

    private TaiSpeechModels() {}

    public static boolean isSpeechModel(@NonNull TaiModelSpec spec) {
        return spec.capabilities.contains(TaiModelSpec.CAPABILITY_SPEECH_TO_TEXT);
    }

    /** Every readable speech model, in the same downloads-then-registry order the chat list uses
     *  (the registry wins on a shared id, so a model mid re-download keeps its installed path). */
    @NonNull
    public static List<TaiModelSpec> installed(@NonNull TaiModelStore store) {
        LinkedHashMap<String, TaiModelSpec> models = new LinkedHashMap<>(store.getDownloadedReadableModels());
        models.putAll(store.getInstalledUserModels());
        List<TaiModelSpec> speech = new ArrayList<>();
        for (TaiModelSpec spec : models.values()) {
            if (isSpeechModel(spec)) speech.add(spec);
        }
        return speech;
    }

    /**
     * The model voice input uses, given the stored choice and what is installed: the stored id
     * when it names an installed model, otherwise the first installed one, otherwise null. A
     * stored id whose files are gone is treated as unset rather than failing, and the fallback is
     * not written back — a model that is briefly unreadable (mid re-download) keeps the choice.
     */
    @Nullable
    public static TaiModelSpec chooseActive(@Nullable String storedId, @NonNull Collection<TaiModelSpec> installed) {
        if (storedId != null && !storedId.trim().isEmpty()) {
            String wanted = storedId.trim();
            for (TaiModelSpec spec : installed) {
                if (wanted.equals(spec.id)) return spec;
            }
        }
        for (TaiModelSpec spec : installed) return spec;
        return null;
    }

    @Nullable
    public static TaiModelSpec resolveActive(@NonNull TaiSettings settings, @NonNull TaiModelStore store) {
        return chooseActive(settings.getSttModelId(), installed(store));
    }

    @Nullable
    public static TaiModelSpec resolveActive(@NonNull Context context) {
        return resolveActive(new TaiSettings(context), new TaiModelStore(context));
    }

    /** {@link #resolveActive}'s id, or empty when no speech model is installed. */
    @NonNull
    public static String activeModelId(@NonNull TaiSettings settings, @NonNull TaiModelStore store) {
        TaiModelSpec active = resolveActive(settings, store);
        return active == null ? "" : active.id;
    }

    /**
     * The user's explicit pick. A fresh download that was going to become the model in use is
     * forgotten (it still installs; it just will not override this choice). A pending window
     * switch is kept: it replaces a model's graph, not the choice, and its old file must still be
     * cleaned up when it lands.
     */
    public static void activate(@NonNull TaiSettings settings, @NonNull String modelId) {
        synchronized (LOCK) {
            settings.setSttModelId(modelId);
            PendingDownload pending = PendingDownload.fromJson(settings.getSttPendingDownloadJson());
            if (pending != null && pending.previousPath == null) settings.setSttPendingDownloadJson("");
        }
    }

    // ---- naming ----

    /** The engine a model runs on, for a row's summary: "Whisper" or "Parakeet". */
    @NonNull
    public static String engineLabel(@NonNull TaiModelSpec spec) {
        return engineLabel(spec.id, spec.localPath);
    }

    @NonNull
    public static String engineLabel(@NonNull String modelId, @Nullable String path) {
        return isParakeet(modelId, path) ? "Parakeet" : "Whisper";
    }

    /** Parakeet by id or file name; the router decides by the catalog architecture, which the id carries too. */
    public static boolean isParakeet(@NonNull String modelId, @Nullable String path) {
        if (modelId.toLowerCase(Locale.ROOT).startsWith("parakeet")) return true;
        return path != null && new File(path).getName().toLowerCase(Locale.ROOT).contains("parakeet");
    }

    /** "Base · Many languages", "Small · English", "Parakeet · Many languages": the size word from
     *  the id when it is one of Whisper's, "Parakeet" for that engine, otherwise the catalog name,
     *  followed by the language kind. */
    @NonNull
    public static String plainName(@NonNull TaiModelSpec spec) {
        return plainName(spec.id, spec.displayName, spec.localPath);
    }

    @NonNull
    public static String plainName(@NonNull String modelId, @Nullable String displayName, @Nullable String path) {
        String size = sizeWord(modelId);
        String head = size != null ? size : (displayName == null || displayName.trim().isEmpty() ? modelId : displayName.trim());
        return head + " · " + (isEnglishOnly(modelId, path) ? "English" : "Many languages");
    }

    @Nullable
    private static String sizeWord(@NonNull String modelId) {
        String id = modelId.toLowerCase(Locale.ROOT);
        if (id.startsWith("parakeet")) return "Parakeet";
        if (!id.startsWith("whisper")) return null;
        for (String word : new String[] {"tiny", "base", "small", "medium", "large"}) {
            if (id.contains("-" + word)) return Character.toUpperCase(word.charAt(0)) + word.substring(1);
        }
        return null;
    }

    public static boolean isEnglishOnly(@NonNull TaiModelSpec spec) {
        return isEnglishOnly(spec.id, spec.localPath);
    }

    /** Whisper's English-only graphs carry {@code -en} in the id and {@code .en_} in the file. */
    public static boolean isEnglishOnly(@NonNull String modelId, @Nullable String path) {
        if (modelId.toLowerCase(Locale.ROOT).endsWith("-en")) return true;
        return path != null && new File(path).getName().toLowerCase(Locale.ROOT).contains(".en_");
    }

    /** The window the installed graph was exported with, read off its file name
     *  ({@code …_5s_… / …_10s_…} for Whisper ACFT, {@code …_v3_5s_…} for Parakeet); 0 when the
     *  name does not say (a model without windows). */
    public static int windowSeconds(@NonNull TaiModelSpec spec) {
        return windowSeconds(spec.localPath);
    }

    public static int windowSeconds(@Nullable String path) {
        if (path == null) return 0;
        Matcher matcher = WINDOW_IN_FILE_NAME.matcher(new File(path).getName().toLowerCase(Locale.ROOT));
        if (!matcher.find()) return 0;
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    // ---- downloads ----

    public static boolean isDownloadActive(@Nullable String status) {
        return TaiModelStore.STATE_QUEUED.equals(status) || TaiModelStore.STATE_DOWNLOADING.equals(status)
            || TaiModelStore.STATE_VERIFYING.equals(status);
    }

    /** A download record is a speech model's when it says so, or when the catalog says so of its id. */
    public static boolean isSpeechDownload(@NonNull JSONObject download) {
        JSONArray capabilities = download.optJSONArray("capabilities");
        if (capabilities != null) {
            for (int i = 0; i < capabilities.length(); i++) {
                if (TaiModelSpec.CAPABILITY_SPEECH_TO_TEXT.equals(capabilities.optString(i, ""))) return true;
            }
        }
        return TaiModelCatalog.speechEntries().containsKey(download.optString("modelId", ""));
    }

    /** The newest download record for {@code modelId}, or null. */
    @Nullable
    public static JSONObject findDownload(@NonNull TaiModelStore store, @NonNull String modelId) {
        JSONArray downloads = store.getDownloads();
        for (int i = downloads.length() - 1; i >= 0; i--) {
            JSONObject item = downloads.optJSONObject(i);
            if (item != null && modelId.equals(item.optString("modelId", ""))) return item;
        }
        return null;
    }

    /**
     * Starts a fresh download. The model becomes the one in use when the download succeeds — not
     * before, so a failed download never leaves voice input pointing at files that do not exist.
     */
    @NonNull
    public static JSONObject startDownload(@NonNull Context context, @NonNull String modelId, int windowSeconds)
            throws JSONException {
        JSONObject result = TaiManager.getInstance(context).downloadSpeechModel(modelId, windowSeconds);
        if (result.optBoolean("ok", false)) {
            synchronized (LOCK) {
                new TaiSettings(context).setSttPendingDownloadJson(
                    new PendingDownload(modelId, windowSeconds, null).toJson().toString());
            }
        }
        return result;
    }

    /**
     * Downloads the other window's graph for an installed model. The installed file stays until
     * the new one is verified and registered; {@link #settlePending} deletes it then, or keeps it
     * when the download fails or is cancelled.
     */
    @NonNull
    public static JSONObject startWindowSwitch(@NonNull Context context, @NonNull TaiModelSpec installed, int windowSeconds)
            throws JSONException {
        JSONObject result = TaiManager.getInstance(context).downloadSpeechModel(installed.id, windowSeconds);
        if (result.optBoolean("ok", false)) {
            synchronized (LOCK) {
                new TaiSettings(context).setSttPendingDownloadJson(
                    new PendingDownload(installed.id, windowSeconds, installed.localPath).toJson().toString());
            }
        }
        return result;
    }

    @NonNull
    public static Settlement settlePending(@NonNull Context context) {
        return settlePending(new TaiSettings(context), new TaiModelStore(context));
    }

    /**
     * Applies the pending download's outcome, if it has one yet. Called from the download service
     * when a speech download ends and from the settings screen's refresh, so the choice settles
     * whether or not the screen is open; idempotent, and a no-op while the download runs.
     */
    @NonNull
    public static Settlement settlePending(@NonNull TaiSettings settings, @NonNull TaiModelStore store) {
        synchronized (LOCK) {
            PendingDownload pending = PendingDownload.fromJson(settings.getSttPendingDownloadJson());
            if (pending == null) return new Settlement(Outcome.NONE, null, "");
            JSONObject download = findDownload(store, pending.modelId);
            if (download == null) {
                // The record is gone: the model was deleted under the download. Nothing to apply.
                settings.setSttPendingDownloadJson("");
                return new Settlement(Outcome.NONE, pending, "");
            }
            String status = download.optString("status", "");
            if (isDownloadActive(status)) return new Settlement(Outcome.IN_PROGRESS, pending, "");

            boolean installedNow = TaiModelStore.STATE_INSTALLED.equals(status) || "complete".equals(status);
            TaiModelSpec spec = installedNow ? chooseActive(pending.modelId, installed(store)) : null;
            if (spec != null && pending.modelId.equals(spec.id)) {
                settings.setSttPendingDownloadJson("");
                settings.setSttWindowSeconds(pending.windowSeconds);
                if (pending.previousPath == null) {
                    settings.setSttModelId(pending.modelId);
                    return new Settlement(Outcome.ACTIVATED, pending, "");
                }
                if (!pending.previousPath.equals(spec.localPath)) deleteGraphFiles(pending.previousPath);
                return new Settlement(Outcome.WINDOW_CHANGED, pending, "");
            }

            settings.setSttPendingDownloadJson("");
            String error = download.optString("error", "");
            if (installedNow) error = "Downloaded files are not readable.";
            if (pending.previousPath != null) {
                // The old graph is still registered and readable; drop the failed record and the
                // partial so the model reads as plainly installed again.
                store.removeDownload(pending.modelId);
                String newPath = download.optString("path", "");
                if (!newPath.isEmpty() && !newPath.equals(pending.previousPath)) deleteGraphFiles(newPath);
                return new Settlement(Outcome.WINDOW_KEPT, pending, error);
            }
            return new Settlement(Outcome.FAILED, pending, error);
        }
    }

    /** One graph file and the downloader's partial/resume marker for it; never the shared sidecars. */
    private static void deleteGraphFiles(@NonNull String path) {
        new File(path).delete();
        new File(path + ".part").delete();
        new File(path + ".part.source").delete();
    }

    public enum Outcome {
        /** Nothing was pending (or its download record is gone). */
        NONE,
        /** The download is still running. */
        IN_PROGRESS,
        /** A fresh download succeeded and is now the model in use. */
        ACTIVATED,
        /** A window switch succeeded; the old graph was deleted. */
        WINDOW_CHANGED,
        /** A fresh download failed or was cancelled; the model in use did not change. */
        FAILED,
        /** A window switch failed or was cancelled; the old graph stays in use. */
        WINDOW_KEPT
    }

    public static final class Settlement {
        @NonNull public final Outcome outcome;
        @Nullable public final PendingDownload pending;
        @NonNull public final String error;

        Settlement(@NonNull Outcome outcome, @Nullable PendingDownload pending, @NonNull String error) {
            this.outcome = outcome;
            this.pending = pending;
            this.error = error;
        }
    }

    /** A download that will become (or re-file) the model in use when it succeeds. */
    public static final class PendingDownload {
        @NonNull public final String modelId;
        public final int windowSeconds;
        /** The installed graph a window switch replaces; null for a fresh download. */
        @Nullable public final String previousPath;

        public PendingDownload(@NonNull String modelId, int windowSeconds, @Nullable String previousPath) {
            this.modelId = modelId;
            this.windowSeconds = windowSeconds;
            this.previousPath = previousPath == null || previousPath.trim().isEmpty() ? null : previousPath;
        }

        public boolean isWindowSwitch() {
            return previousPath != null;
        }

        @NonNull
        public JSONObject toJson() throws JSONException {
            JSONObject json = new JSONObject();
            json.put("modelId", modelId);
            json.put("windowSeconds", windowSeconds);
            if (previousPath != null) json.put("previousPath", previousPath);
            return json;
        }

        @Nullable
        public static PendingDownload fromJson(@Nullable String raw) {
            if (raw == null || raw.trim().isEmpty()) return null;
            try {
                JSONObject json = new JSONObject(raw);
                String modelId = json.optString("modelId", "").trim();
                if (modelId.isEmpty()) return null;
                return new PendingDownload(modelId, json.optInt("windowSeconds", 0),
                    json.optString("previousPath", null));
            } catch (JSONException e) {
                return null;
            }
        }
    }
}
