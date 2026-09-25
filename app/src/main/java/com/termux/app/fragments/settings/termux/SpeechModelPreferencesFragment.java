package com.termux.app.fragments.settings.termux;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.util.TypedValue;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.termux.R;
import com.termux.ai.TaiDeviceCapabilities;
import com.termux.ai.TaiManager;
import com.termux.ai.TaiModelCatalog;
import com.termux.ai.TaiModelSpec;
import com.termux.ai.TaiModelStore;
import com.termux.ai.TaiSettings;
import com.termux.ai.TaiSpeechModels;
import com.termux.app.fragments.settings.MaterialPreferenceFragment;
import com.termux.app.fragments.settings.SettingsLayoutUtils;
import com.termux.app.notice.AppNotice;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Keyboard → Voice input → Speech model: every installed speech-to-text model as a row (tap to
 * use it, Delete to remove it, the tune button to change its window), downloads in progress with
 * their progress and a Cancel, and the download (engine first: Whisper or Parakeet, then the
 * engine's options) and idle-unload options. The state itself lives in
 * {@link TaiSpeechModels}; this screen shows it and asks for changes. Rows are keyed by model id
 * and updated in place on every poll, so a progress tick never rebuilds the list.
 */
@Keep
public class SpeechModelPreferencesFragment extends MaterialPreferenceFragment {
    static final String ROW_KEY_PREFIX = "speech_model_row_";
    private static final String KEY_INSTALLED_CATEGORY = "speech_model_installed";
    private static final String KEY_EMPTY = "speech_model_empty";
    private static final String KEY_DOWNLOAD = "speech_model_download";
    private static final String KEY_IDLE_UNLOAD = "speech_model_idle_unload";
    private static final long POLL_INTERVAL_MS = 700L;
    private static final long BYTES_PER_GIB = 1024L * 1024L * 1024L;
    /** Small is only offered (and then recommended) on phones with this much RAM; see the plan. */
    private static final long SMALL_MIN_MEMORY_BYTES = 8L * BYTES_PER_GIB;
    /** Parakeet's one graph is a 5 s window; there is nothing to choose. */
    static final int PARAKEET_WINDOW_SECONDS = 5;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService actionExecutor = Executors.newSingleThreadExecutor();
    private final Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            Context context = getContext();
            if (context == null || !isAdded()) return;
            refresh(context);
            if (hasActiveDownloads(context)) handler.postDelayed(this, POLL_INTERVAL_MS);
        }
    };

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        Context context = getContext();
        if (context == null) return;
        setPreferencesFromResource(R.xml.speech_model_preferences, rootKey);
        SettingsLayoutUtils.applyScreenLayout(this);
        Preference download = findPreference(KEY_DOWNLOAD);
        if (download != null) {
            download.setOnPreferenceClickListener(preference -> {
                showDownloadDialog(context);
                return true;
            });
        }
        Preference idleUnload = findPreference(KEY_IDLE_UNLOAD);
        if (idleUnload != null) idleUnload.setOnPreferenceClickListener(preference -> {
            showIdleUnloadDialog(context);
            return true;
        });
        refresh(context);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() != null) getActivity().setTitle(R.string.settings_keyboard_voice_model_title);
        Context context = getContext();
        if (context != null) {
            refresh(context);
            if (hasActiveDownloads(context)) schedulePoll();
        }
    }

    @Override
    public void onPause() {
        handler.removeCallbacks(refreshRunnable);
        super.onPause();
    }

    @Override
    public void onDestroy() {
        actionExecutor.shutdownNow();
        super.onDestroy();
    }

    private void schedulePoll() {
        handler.removeCallbacks(refreshRunnable);
        handler.postDelayed(refreshRunnable, POLL_INTERVAL_MS);
    }

    private boolean hasActiveDownloads(@NonNull Context context) {
        for (JSONObject download : speechDownloads(new TaiModelStore(context)).values()) {
            if (TaiSpeechModels.isDownloadActive(download.optString("status", ""))) return true;
        }
        return false;
    }

    /** The newest download record per speech model id. */
    @NonNull
    private static Map<String, JSONObject> speechDownloads(@NonNull TaiModelStore store) {
        LinkedHashMap<String, JSONObject> downloads = new LinkedHashMap<>();
        JSONArray all = store.getDownloads();
        for (int i = 0; i < all.length(); i++) {
            JSONObject item = all.optJSONObject(i);
            if (item == null || !TaiSpeechModels.isSpeechDownload(item)) continue;
            String modelId = item.optString("modelId", "");
            if (!modelId.isEmpty()) downloads.put(modelId, item);
        }
        return downloads;
    }

    // ---- rows ----

    /** Applies whatever a finished download decided, then redraws the rows in place. */
    void refresh(@NonNull Context context) {
        announce(context, TaiSpeechModels.settlePending(context));
        PreferenceCategory category = findPreference(KEY_INSTALLED_CATEGORY);
        if (category == null) return;
        TaiModelStore store = new TaiModelStore(context);
        TaiSettings settings = new TaiSettings(context);
        List<TaiModelSpec> installed = TaiSpeechModels.installed(store);
        TaiModelSpec active = TaiSpeechModels.chooseActive(settings.getSttModelId(), installed);
        Map<String, JSONObject> downloads = speechDownloads(store);

        Set<String> keep = new HashSet<>();
        int order = 0;
        for (TaiModelSpec spec : installed) {
            TaiModelPreference row = rowFor(context, category, spec.id);
            keep.add(row.getKey());
            row.setOrder(order++);
            bindInstalledRow(context, row, spec, active != null && active.id.equals(spec.id),
                installed, downloads.remove(spec.id));
        }
        for (Map.Entry<String, JSONObject> entry : downloads.entrySet()) {
            String status = entry.getValue().optString("status", "");
            boolean ended = TaiModelStore.STATE_FAILED.equals(status) || TaiModelStore.STATE_CANCELLED.equals(status);
            if (!TaiSpeechModels.isDownloadActive(status) && !ended) continue;
            TaiModelPreference row = rowFor(context, category, entry.getKey());
            keep.add(row.getKey());
            row.setOrder(order++);
            bindDownloadRow(context, row, entry.getKey(), entry.getValue());
        }
        for (int i = category.getPreferenceCount() - 1; i >= 0; i--) {
            Preference preference = category.getPreference(i);
            String key = preference.getKey();
            if (key != null && key.startsWith(ROW_KEY_PREFIX) && !keep.contains(key)) category.removePreference(preference);
        }
        Preference empty = findPreference(KEY_EMPTY);
        if (empty != null) empty.setVisible(keep.isEmpty());
        Preference idleUnload = findPreference(KEY_IDLE_UNLOAD);
        if (idleUnload != null) idleUnload.setSummary(idleUnloadSummary(settings.getSttIdleUnloadMinutes()));
    }

    @NonNull
    private TaiModelPreference rowFor(@NonNull Context context, @NonNull PreferenceCategory category, @NonNull String modelId) {
        String key = ROW_KEY_PREFIX + modelId;
        Preference existing = category.findPreference(key);
        if (existing instanceof TaiModelPreference) return (TaiModelPreference) existing;
        TaiModelPreference row = new TaiModelPreference(context);
        row.setKey(key);
        row.setPersistent(false);
        row.setBackendTone(TaiModelPreference.BackendTone.LITERT);
        category.addPreference(row);
        return row;
    }

    private void bindInstalledRow(@NonNull Context context, @NonNull TaiModelPreference row, @NonNull TaiModelSpec spec,
                                  boolean active, @NonNull List<TaiModelSpec> installed, @Nullable JSONObject download) {
        row.setTitle(TaiSpeechModels.plainName(spec));
        row.setPill(active ? getString(R.string.speech_model_in_use) : null, true);
        boolean switching = download != null && TaiSpeechModels.isDownloadActive(download.optString("status", ""));
        if (switching) {
            // The other window's graph is on its way; the installed one stays in use until it lands.
            row.setSummary(getString(R.string.speech_model_window_downloading,
                TaiSpeechModels.windowSeconds(download.optString("path", "")), progressText(download)));
            configureProgress(row, download);
            row.setTuneAction(null, null);
            row.setPrimaryAction(getString(R.string.speech_model_action_cancel), true, false,
                view -> cancelDownload(context, spec.id));
        } else {
            row.setSummary(installedSummary(spec));
            row.setDownloadProgress(false, false, 0);
            int otherWindow = otherWindow(spec);
            row.setTuneAction(otherWindow > 0 ? getString(R.string.speech_model_action_window) : null,
                otherWindow > 0 ? view -> showWindowDialog(context, spec, otherWindow) : null);
            row.setPrimaryAction(getString(R.string.speech_model_action_delete), true, true,
                view -> confirmDelete(context, spec, active, installed));
        }
        row.setOnPreferenceClickListener(preference -> {
            if (!active) {
                TaiSpeechModels.activate(new TaiSettings(context), spec.id);
                AppNotice.show(context, getString(R.string.speech_model_now_using, TaiSpeechModels.plainName(spec)), false);
                refresh(context);
            }
            return true;
        });
    }

    private void bindDownloadRow(@NonNull Context context, @NonNull TaiModelPreference row, @NonNull String modelId,
                                 @NonNull JSONObject download) {
        String path = download.optString("path", "");
        String status = download.optString("status", "");
        row.setTitle(TaiSpeechModels.plainName(modelId, download.optString("displayName", modelId), path));
        row.setPill(null, false);
        row.setTuneAction(null, null);
        if (TaiSpeechModels.isDownloadActive(status)) {
            if (TaiModelStore.STATE_QUEUED.equals(status)) row.setSummary(R.string.speech_model_download_waiting);
            else if (TaiModelStore.STATE_VERIFYING.equals(status)) row.setSummary(R.string.speech_model_download_verifying);
            else row.setSummary(getString(R.string.speech_model_downloading, progressText(download)));
            configureProgress(row, download);
            row.setPrimaryAction(getString(R.string.speech_model_action_cancel), true, false,
                view -> cancelDownload(context, modelId));
            row.setOnPreferenceClickListener(null);
            return;
        }
        row.setDownloadProgress(false, false, 0);
        if (TaiModelStore.STATE_FAILED.equals(status)) {
            String error = download.optString("error", "");
            row.setSummary(getString(R.string.speech_model_download_failed, error.isEmpty() ? "?" : error));
        } else {
            row.setSummary(R.string.speech_model_download_cancelled);
        }
        int window = TaiSpeechModels.windowSeconds(path);
        row.setPrimaryAction(getString(R.string.speech_model_action_retry), true, false,
            view -> startDownload(context, modelId, window > 0 ? window : new TaiSettings(context).getSttWindowSeconds()));
        row.setOnPreferenceClickListener(preference -> {
            confirmRemoveDownload(context, modelId);
            return true;
        });
    }

    /** "Whisper · 97 MB · 10-second window", "Parakeet · 586 MB · 5-second window": engine, size, window when known. */
    @NonNull
    String installedSummary(@NonNull TaiModelSpec spec) {
        int window = TaiSpeechModels.windowSeconds(spec);
        String summary = TaiSpeechModels.engineLabel(spec) + " · " + formatBytes(spec.sizeBytes);
        return window > 0 ? summary + " · " + getString(R.string.speech_model_window_summary, window) : summary;
    }

    private void configureProgress(@NonNull TaiModelPreference row, @NonNull JSONObject download) {
        long bytesRead = download.optLong("bytesRead", 0L);
        long totalBytes = download.optLong("totalBytes", 0L);
        row.setDownloadProgress(true, totalBytes <= 0L, totalBytes > 0L ? (int) (bytesRead * 10000L / totalBytes) : 0);
    }

    @NonNull
    private String progressText(@NonNull JSONObject download) {
        long bytesRead = download.optLong("bytesRead", 0L);
        long totalBytes = download.optLong("totalBytes", 0L);
        if (totalBytes <= 0L) return formatBytes(bytesRead);
        return formatPercent(bytesRead, totalBytes) + " · " + formatBytes(bytesRead) + " of " + formatBytes(totalBytes);
    }

    /** The window this model can be re-downloaded with, or 0 when it has none (or only the one). */
    private static int otherWindow(@NonNull TaiModelSpec spec) {
        int current = TaiSpeechModels.windowSeconds(spec);
        if (current <= 0) return 0;
        TaiModelCatalog.CatalogEntry entry = TaiModelCatalog.get(spec.id);
        if (entry == null) return 0;
        for (Integer window : entry.speechWindows.keySet()) {
            if (window != null && window != current) return window;
        }
        return 0;
    }

    /** What a finished download decided, as a one-line notice; nothing while it runs or when nothing was pending. */
    private void announce(@NonNull Context context, @NonNull TaiSpeechModels.Settlement settlement) {
        TaiSpeechModels.PendingDownload pending = settlement.pending;
        if (pending == null) return;
        TaiModelCatalog.CatalogEntry entry = TaiModelCatalog.get(pending.modelId);
        String name = TaiSpeechModels.plainName(pending.modelId, entry == null ? pending.modelId : entry.displayName, null);
        switch (settlement.outcome) {
            case ACTIVATED:
                AppNotice.show(context, getString(R.string.speech_model_ready, name), false);
                break;
            case WINDOW_CHANGED:
                AppNotice.show(context, getString(R.string.speech_model_window_changed, name, pending.windowSeconds), false);
                break;
            case FAILED:
                AppNotice.show(context, getString(R.string.speech_model_failed_notice, name), true);
                break;
            case WINDOW_KEPT:
                AppNotice.show(context, getString(R.string.speech_model_window_kept, pending.windowSeconds, name,
                    TaiSpeechModels.windowSeconds(pending.previousPath)), true);
                break;
            default:
                break;
        }
    }

    // ---- actions ----

    private void startDownload(@NonNull Context context, @NonNull String modelId, int windowSeconds) {
        try {
            JSONObject result = TaiSpeechModels.startDownload(context, modelId, windowSeconds);
            if (result.optBoolean("ok", false)) {
                AppNotice.show(context, R.string.speech_model_download_started, false);
                schedulePoll();
            } else {
                AppNotice.show(context, result.optString("message", getString(R.string.termux_ai_model_action_failed)), true);
            }
        } catch (JSONException e) {
            AppNotice.show(context, R.string.termux_ai_model_action_failed, true);
        }
        refresh(context);
    }

    private void startWindowSwitch(@NonNull Context context, @NonNull TaiModelSpec spec, int windowSeconds) {
        try {
            JSONObject result = TaiSpeechModels.startWindowSwitch(context, spec, windowSeconds);
            if (result.optBoolean("ok", false)) {
                schedulePoll();
            } else {
                AppNotice.show(context, result.optString("message", getString(R.string.termux_ai_model_action_failed)), true);
            }
        } catch (JSONException e) {
            AppNotice.show(context, R.string.termux_ai_model_action_failed, true);
        }
        refresh(context);
    }

    private void cancelDownload(@NonNull Context context, @NonNull String modelId) {
        try {
            TaiManager.getInstance(context).cancelDownload(new JSONObject().put("modelId", modelId).toString());
            schedulePoll();
        } catch (JSONException e) {
            AppNotice.show(context, R.string.termux_ai_model_action_failed, true);
        }
        refresh(context);
    }

    private void confirmRemoveDownload(@NonNull Context context, @NonNull String modelId) {
        new MaterialAlertDialogBuilder(context)
            .setTitle(R.string.speech_model_download_remove_title)
            .setMessage(R.string.speech_model_download_remove_message)
            .setPositiveButton(R.string.speech_model_action_remove, (dialog, which) -> deleteModel(context, modelId, null))
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private void confirmDelete(@NonNull Context context, @NonNull TaiModelSpec spec, boolean active,
                               @NonNull List<TaiModelSpec> installed) {
        TaiModelSpec next = null;
        for (TaiModelSpec other : installed) {
            if (!other.id.equals(spec.id)) {
                next = other;
                break;
            }
        }
        String message;
        if (!active) message = getString(R.string.speech_model_delete_message_plain);
        else if (next != null) message = getString(R.string.speech_model_delete_message_other, TaiSpeechModels.plainName(next));
        else message = getString(R.string.speech_model_delete_message_none);
        String nextId = active ? (next == null ? "" : next.id) : null;
        new MaterialAlertDialogBuilder(context)
            .setTitle(getString(R.string.speech_model_delete_title, TaiSpeechModels.plainName(spec)))
            .setMessage(message)
            .setPositiveButton(R.string.speech_model_action_delete, (dialog, which) -> deleteModel(context, spec.id, nextId))
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    /**
     * Deletes the model's files and records off the UI thread (the manager checks the runtime's
     * state first, which can wait on the runtime process). {@code nextId} is the model that takes
     * over as the one in use — empty for none — or null when the deleted one was not in use.
     */
    private void deleteModel(@NonNull Context context, @NonNull String modelId, @Nullable String nextId) {
        if (actionExecutor.isShutdown()) return;
        String name = TaiSpeechModels.plainName(modelId, catalogName(modelId), null);
        actionExecutor.execute(() -> {
            JSONObject result;
            try {
                TaiManager manager = TaiManager.getInstance(context);
                JSONObject download = TaiSpeechModels.findDownload(new TaiModelStore(context), modelId);
                if (download != null && TaiSpeechModels.isDownloadActive(download.optString("status", ""))) {
                    manager.cancelDownload(new JSONObject().put("modelId", modelId).toString());
                }
                result = manager.deleteModel(new JSONObject().put("modelId", modelId).put("confirm", true).toString());
            } catch (JSONException e) {
                result = null;
            }
            JSONObject finalResult = result;
            handler.post(() -> {
                if (!isAdded()) return;
                if (finalResult != null && finalResult.optBoolean("ok", false)) {
                    if (nextId != null) new TaiSettings(context).setSttModelId(nextId);
                    AppNotice.show(context, getString(R.string.speech_model_deleted, name), false);
                } else {
                    AppNotice.show(context, finalResult == null ? getString(R.string.termux_ai_model_action_failed)
                        : finalResult.optString("message", getString(R.string.termux_ai_model_action_failed)), true);
                }
                refresh(context);
            });
        });
    }

    @NonNull
    private static String catalogName(@NonNull String modelId) {
        TaiModelCatalog.CatalogEntry entry = TaiModelCatalog.get(modelId);
        return entry == null ? modelId : entry.displayName;
    }

    // ---- dialogs ----

    /**
     * The engine first — Whisper (short commands; size, language and window to choose) or
     * Parakeet (longer dictation; one 5 s graph, ~586 MB, 8 GB+ phones) — then Whisper's three
     * plain questions, shown only while Whisper is picked. Small is only offered on ≥ 8 GB phones;
     * Parakeet is always offered, with a warning under it on a phone below its RAM tier.
     */
    private void showDownloadDialog(@NonNull Context context) {
        TaiDeviceCapabilities device = TaiDeviceCapabilities.detect(context);
        boolean offerSmall = device.memoryBytes >= SMALL_MIN_MEMORY_BYTES;
        TaiSettings settings = new TaiSettings(context);
        float density = context.getResources().getDisplayMetrics().density;
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        int padH = Math.round(24 * density);
        content.setPadding(padH, Math.round(8 * density), padH, 0);

        content.addView(sectionLabel(context, R.string.speech_model_engine_label));
        RadioGroup engineGroup = new RadioGroup(context);
        engineGroup.setOrientation(RadioGroup.VERTICAL);
        RadioButton whisperButton = new RadioButton(context);
        whisperButton.setText(twoLines(context, getString(R.string.speech_model_engine_whisper),
            getString(R.string.speech_model_engine_whisper_hint)));
        RadioButton parakeetButton = new RadioButton(context);
        String parakeetHint = getString(R.string.speech_model_engine_parakeet_hint);
        String ramWarning = parakeetRamWarning(context, device.memoryBytes);
        if (ramWarning != null) parakeetHint = parakeetHint + "\n" + ramWarning;
        parakeetButton.setText(twoLines(context,
            getString(R.string.speech_model_engine_parakeet, sizeEstimate(TaiModelCatalog.PARAKEET_TDT_V3_ID)), parakeetHint));
        engineGroup.addView(whisperButton);
        engineGroup.addView(parakeetButton);
        whisperButton.setChecked(true);
        content.addView(engineGroup);

        // Whisper's questions live in their own block, hidden while Parakeet is picked.
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        content.addView(layout);
        engineGroup.setOnCheckedChangeListener((group, checkedId) ->
            layout.setVisibility(parakeetButton.isChecked() ? View.GONE : View.VISIBLE));

        layout.addView(sectionLabel(context, R.string.speech_model_size_label));
        RadioGroup sizeGroup = new RadioGroup(context);
        sizeGroup.setOrientation(RadioGroup.VERTICAL);
        RadioButton baseButton = new RadioButton(context);
        baseButton.setText(twoLines(context, getString(R.string.speech_model_size_base, sizeEstimate("whisper-acft-base")),
            getString(R.string.speech_model_size_base_hint)));
        sizeGroup.addView(baseButton);
        RadioButton smallButton = null;
        if (offerSmall) {
            smallButton = new RadioButton(context);
            smallButton.setText(twoLines(context, getString(R.string.speech_model_size_small, sizeEstimate("whisper-acft-small")),
                getString(R.string.speech_model_size_small_hint)));
            sizeGroup.addView(smallButton);
            smallButton.setChecked(true);
        } else {
            baseButton.setChecked(true);
        }
        layout.addView(sizeGroup);

        layout.addView(sectionLabel(context, R.string.speech_model_language_label));
        RadioGroup languageGroup = new RadioGroup(context);
        languageGroup.setOrientation(RadioGroup.VERTICAL);
        RadioButton englishButton = new RadioButton(context);
        englishButton.setText(R.string.speech_model_language_english_only);
        RadioButton manyButton = new RadioButton(context);
        manyButton.setText(R.string.speech_model_language_many);
        languageGroup.addView(englishButton);
        languageGroup.addView(manyButton);
        // Checked only once it is in the group: a button checked before addView is invisible to
        // RadioGroup, which then leaves it checked when "Many languages" is picked.
        englishButton.setChecked(true);
        layout.addView(languageGroup);

        layout.addView(sectionLabel(context, R.string.speech_model_window_label));
        RadioGroup windowGroup = new RadioGroup(context);
        windowGroup.setOrientation(RadioGroup.VERTICAL);
        RadioButton window10Button = new RadioButton(context);
        window10Button.setText(twoLines(context, getString(R.string.speech_model_window_10),
            getString(R.string.speech_model_window_10_hint)));
        RadioButton window5Button = new RadioButton(context);
        window5Button.setText(twoLines(context, getString(R.string.speech_model_window_5),
            getString(R.string.speech_model_window_5_hint)));
        windowGroup.addView(window10Button);
        windowGroup.addView(window5Button);
        if (settings.getSttWindowSeconds() == 5) window5Button.setChecked(true);
        else window10Button.setChecked(true);
        layout.addView(windowGroup);

        RadioButton finalSmallButton = smallButton;
        new MaterialAlertDialogBuilder(context)
            .setTitle(R.string.speech_model_download_dialog_title)
            .setView(dialogScroll(context, content))
            .setPositiveButton(R.string.speech_model_download_start, (dialog, which) -> {
                if (parakeetButton.isChecked()) {
                    requestDownload(context, TaiModelCatalog.PARAKEET_TDT_V3_ID, PARAKEET_WINDOW_SECONDS);
                    return;
                }
                boolean small = finalSmallButton != null && finalSmallButton.isChecked();
                boolean englishOnly = englishButton.isChecked();
                int windowSeconds = window5Button.isChecked() ? 5 : 10;
                requestDownload(context, whisperCatalogId(small, englishOnly), windowSeconds);
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    /**
     * The warning under the Parakeet choice on a phone below the entry's RAM tier (the same
     * threshold {@link TaiDeviceCapabilities#checkModelCapability} warns on), or null when the
     * phone is fine or its memory is unknown.
     */
    @Nullable
    String parakeetRamWarning(@NonNull Context context, long deviceMemoryBytes) {
        TaiModelCatalog.CatalogEntry entry = TaiModelCatalog.get(TaiModelCatalog.PARAKEET_TDT_V3_ID);
        if (entry == null || entry.recommendedRamGb <= 0 || deviceMemoryBytes <= 0L) return null;
        if (deviceMemoryBytes >= entry.recommendedRamGb * BYTES_PER_GIB) return null;
        return context.getString(R.string.speech_model_engine_parakeet_ram_warning, formatBytes(deviceMemoryBytes));
    }

    /** A fresh download — or, for a model that is already installed, the model itself or its other window. */
    private void requestDownload(@NonNull Context context, @NonNull String modelId, int windowSeconds) {
        TaiModelSpec already = null;
        for (TaiModelSpec spec : TaiSpeechModels.installed(new TaiModelStore(context))) {
            if (spec.id.equals(modelId)) {
                already = spec;
                break;
            }
        }
        if (already == null) {
            startDownload(context, modelId, windowSeconds);
        } else if (TaiSpeechModels.windowSeconds(already) == windowSeconds) {
            TaiSpeechModels.activate(new TaiSettings(context), already.id);
            AppNotice.show(context, getString(R.string.speech_model_now_using, TaiSpeechModels.plainName(already)), false);
            refresh(context);
        } else {
            startWindowSwitch(context, already, windowSeconds);
        }
    }

    /** The Whisper family's catalog id for a size and language; Parakeet's is {@link TaiModelCatalog#PARAKEET_TDT_V3_ID}. */
    @NonNull
    static String whisperCatalogId(boolean small, boolean englishOnly) {
        return "whisper-acft-" + (small ? "small" : "base") + (englishOnly ? "-en" : "");
    }

    @NonNull
    private String sizeEstimate(@NonNull String modelId) {
        TaiModelCatalog.CatalogEntry entry = TaiModelCatalog.get(modelId);
        if (entry == null) return "?";
        return entry.sizeEstimate == null || entry.sizeEstimate.isEmpty() ? formatBytes(entry.sizeBytes) : entry.sizeEstimate;
    }

    private void showWindowDialog(@NonNull Context context, @NonNull TaiModelSpec spec, int otherWindow) {
        int current = TaiSpeechModels.windowSeconds(spec);
        TaiModelCatalog.CatalogEntry entry = TaiModelCatalog.get(spec.id);
        TaiModelCatalog.CatalogEntry.WindowVariant variant = entry == null ? null : entry.speechWindows.get(otherWindow);
        String size = variant == null ? formatBytes(spec.sizeBytes) : formatBytes(variant.sizeBytes);
        String name = TaiSpeechModels.plainName(spec);
        new MaterialAlertDialogBuilder(context)
            .setTitle(R.string.speech_model_window_change_title)
            .setMessage(getString(R.string.speech_model_window_change_message, name, otherWindow, size, current))
            .setPositiveButton(R.string.speech_model_window_change_action,
                (dialog, which) -> startWindowSwitch(context, spec, otherWindow))
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    /** Picks how long an idle speech model stays loaded; 0 keeps it until memory pressure evicts it. */
    private void showIdleUnloadDialog(@NonNull Context context) {
        String[] labels = getResources().getStringArray(R.array.termux_ai_stt_idle_unload_entries);
        String[] values = getResources().getStringArray(R.array.termux_ai_stt_idle_unload_values);
        TaiSettings settings = new TaiSettings(context);
        String current = String.valueOf(settings.getSttIdleUnloadMinutes());
        int checked = 0;
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(current)) checked = i;
        }
        new MaterialAlertDialogBuilder(context)
            .setTitle(R.string.speech_model_idle_unload_title)
            .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                settings.setSttIdleUnloadMinutes(Integer.parseInt(values[which]));
                refresh(context);
                dialog.dismiss();
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    @NonNull
    private String idleUnloadSummary(int minutes) {
        String[] labels = getResources().getStringArray(R.array.termux_ai_stt_idle_unload_entries);
        String[] values = getResources().getStringArray(R.array.termux_ai_stt_idle_unload_values);
        String label = minutes <= 0 ? getString(R.string.termux_ai_disabled) : minutes + " min";
        for (int i = 0; i < values.length && i < labels.length; i++) {
            if (values[i].equals(String.valueOf(minutes))) label = labels[i];
        }
        return getString(R.string.speech_model_idle_unload_summary, label);
    }

    // ---- view helpers ----

    private TextView sectionLabel(@NonNull Context context, int textRes) {
        TextView label = new TextView(context);
        label.setText(textRes);
        label.setTypeface(Typeface.DEFAULT_BOLD);
        float density = context.getResources().getDisplayMetrics().density;
        label.setPadding(0, Math.round(12 * density), 0, Math.round(4 * density));
        return label;
    }

    /** A radio label with a smaller, dimmer second line saying what the choice means. */
    @NonNull
    private static CharSequence twoLines(@NonNull Context context, @NonNull String main, @NonNull String hint) {
        SpannableStringBuilder text = new SpannableStringBuilder(main).append('\n');
        int start = text.length();
        text.append(hint);
        text.setSpan(new RelativeSizeSpan(0.85f), start, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        TypedValue value = new TypedValue();
        if (context.getTheme().resolveAttribute(android.R.attr.textColorSecondary, value, true)) {
            text.setSpan(new ForegroundColorSpan(value.data), start, text.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return text;
    }

    private ScrollView dialogScroll(@NonNull Context context, @NonNull LinearLayout content) {
        ScrollView scroll = new ScrollView(context);
        scroll.addView(content);
        return scroll;
    }

    private static String formatPercent(long value, long total) {
        if (total <= 0) return "";
        return String.format(Locale.US, "%.0f%%", (double) value * 100.0 / (double) total);
    }

    private static String formatBytes(long bytes) {
        if (bytes <= 0) return "unknown size";
        double value = bytes;
        String[] units = {"B", "KB", "MB", "GB"};
        int unit = 0;
        while (value >= 1024.0 && unit < units.length - 1) {
            value /= 1024.0;
            unit++;
        }
        return String.format(Locale.US, unit == 0 ? "%.0f %s" : "%.1f %s", value, units[unit]);
    }
}
