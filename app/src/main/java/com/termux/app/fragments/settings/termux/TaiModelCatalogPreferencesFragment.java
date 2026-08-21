package com.termux.app.fragments.settings.termux;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.termux.app.notice.AppNotice;
import com.termux.R;
import com.termux.ai.TaiDeviceCapabilities;
import com.termux.ai.TaiManager;
import com.termux.ai.TaiModelCatalog;
import com.termux.ai.TaiModelSpec;
import com.termux.ai.TaiModelStore;
import com.termux.ai.TaiSettings;
import com.termux.app.fragments.settings.MaterialPreferenceFragment;
import com.termux.app.fragments.settings.SettingsLayoutUtils;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Keep
public class TaiModelCatalogPreferencesFragment extends MaterialPreferenceFragment {
    private static final String ROW_KEY_PREFIX = "tai_catalog_model_";
    private static final LinkedHashSet<String> ALLOWED_CAPABILITY_TAGS = new LinkedHashSet<>(Arrays.asList(
        "Text", "Embeddings", "Code", "Vision", "Audio", "Reasoning", "Tools", "Multilingual"));

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable refreshRunnable = new Runnable() {
        @Override
        public void run() {
            Context context = getContext();
            if (context == null) return;
            // Update only the live rows in place so the progress bar animates smoothly without
            // tearing down and rebuilding the whole list (which caused the visible screen flash).
            updateRowsInPlace(context);
            if (hasActiveDownloads(context)) {
                handler.postDelayed(this, POLL_INTERVAL_MS);
            } else {
                // Downloads finished: do one settling rebuild so completed models appear/disappear
                // correctly under the Installed/Usable filters.
                refreshCatalogRows(context);
            }
        }
    };

    private static final long POLL_INTERVAL_MS = 700L;

    private BackendFilter backendFilter = BackendFilter.ALL;
    private String installFilter = TaiCatalogControlsPreference.INSTALL_ALL;
    private String searchQuery = "";

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        Context context = getContext();
        if (context == null) return;
        PreferenceManager preferenceManager = getPreferenceManager();
        preferenceManager.setSharedPreferencesName(TaiSettings.PREFS_NAME);
        setPreferencesFromResource(R.xml.termux_ai_model_catalog_preferences, rootKey);
        SettingsLayoutUtils.applyScreenLayout(this);
        configureControls(context);
        refreshCatalogRows(context);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() != null) {
            getActivity().setTitle(R.string.termux_ai_models_browse_catalog_title);
        }
        Context context = getContext();
        if (context != null) {
            refreshCatalogRows(context);
            if (hasActiveDownloads(context)) handler.postDelayed(refreshRunnable, 2000L);
        }
    }

    @Override
    public void onPause() {
        handler.removeCallbacks(refreshRunnable);
        super.onPause();
    }

    private void configureControls(Context context) {
        TaiCatalogControlsPreference controls = findPreference("tai_catalog_controls");
        if (controls == null) return;
        controls.setOnControlsListener(new TaiCatalogControlsPreference.OnControlsListener() {
            @Override
            public void onBackendSelected(@NonNull String backend) {
                backendFilter = BackendFilter.fromValue(backend);
                refreshCatalogRows(context);
            }

            @Override
            public void onInstallSelected(@NonNull String install) {
                installFilter = install;
                refreshCatalogRows(context);
            }

            @Override
            public void onSearchSubmitted(@NonNull String query) {
                searchQuery = query;
                refreshCatalogRows(context);
            }
        });
    }

    private void refreshCatalogRows(Context context) {
        PreferenceCategory results = findPreference("tai_catalog_results_category");
        if (results == null) return;
        results.removeAll();

        TaiModelStore store = new TaiModelStore(context);
        // Same union /v1/models advertises: registered user models plus completed downloads.
        Map<String, TaiModelSpec> installed = new java.util.LinkedHashMap<>();
        installed.putAll(store.getDownloadedReadableModels());
        installed.putAll(store.getInstalledUserModels());
        JSONArray downloads = store.getDownloads();
        String activeModelId = new TaiSettings(context).getDefaultAssistantModel();
        TaiDeviceCapabilities capabilities = TaiDeviceCapabilities.detect(context);

        // Surface imported / URL-downloaded models that aren't curated catalog entries.
        List<TaiModelCatalog.CatalogEntry> all = new ArrayList<>(TaiModelCatalog.entries().values());
        for (TaiModelSpec spec : installed.values()) {
            if (!TaiModelCatalog.entries().containsKey(spec.id)) all.add(TaiModelCatalog.installedModelEntry(spec));
        }
        for (int i = 0; i < downloads.length(); i++) {
            JSONObject download = downloads.optJSONObject(i);
            if (download == null || !isActiveDownload(download.optString("status", ""))) continue;
            String modelId = download.optString("modelId", "");
            if (modelId.isEmpty() || installed.containsKey(modelId) || TaiModelCatalog.entries().containsKey(modelId)) continue;
            TaiModelCatalog.CatalogEntry entry = TaiModelCatalog.downloadEntry(download);
            if (entry != null) all.add(entry);
        }
        List<TaiModelCatalog.CatalogEntry> entries = sortForDisplay(
            filterByInstallStatus(
                filterEntries(all, backendFilter, searchQuery, installed, capabilities),
                installed));
        if (entries.isEmpty()) {
            Preference empty = new Preference(context);
            empty.setIconSpaceReserved(false);
            empty.setTitle(R.string.termux_ai_catalog_empty_title);
            empty.setSummary(R.string.termux_ai_catalog_empty_summary);
            empty.setSelectable(false);
            results.addPreference(empty);
            return;
        }
        for (TaiModelCatalog.CatalogEntry entry : entries) {
            results.addPreference(buildRow(context, entry, installed.get(entry.modelId),
                findDownload(downloads, entry.modelId), activeModelId));
        }
    }

    /** Recommended models first, then smallest download first; name as a stable tiebreaker. */
    static List<TaiModelCatalog.CatalogEntry> sortForDisplay(List<TaiModelCatalog.CatalogEntry> entries) {
        List<TaiModelCatalog.CatalogEntry> sorted = new ArrayList<>(entries);
        Collections.sort(sorted, new Comparator<TaiModelCatalog.CatalogEntry>() {
            @Override
            public int compare(TaiModelCatalog.CatalogEntry a, TaiModelCatalog.CatalogEntry b) {
                if (a.recommended != b.recommended) return a.recommended ? -1 : 1;
                if (a.sizeBytes != b.sizeBytes) return Long.compare(a.sizeBytes, b.sizeBytes);
                return a.displayName.compareToIgnoreCase(b.displayName);
            }
        });
        return sorted;
    }

    /** Applies the install-status dropdown (All / Installed / Not installed) after backend+search. */
    private List<TaiModelCatalog.CatalogEntry> filterByInstallStatus(
            List<TaiModelCatalog.CatalogEntry> entries, Map<String, TaiModelSpec> installed) {
        if (TaiCatalogControlsPreference.INSTALL_ALL.equals(installFilter)) return entries;
        boolean wantInstalled = TaiCatalogControlsPreference.INSTALL_INSTALLED.equals(installFilter);
        List<TaiModelCatalog.CatalogEntry> kept = new ArrayList<>();
        for (TaiModelCatalog.CatalogEntry entry : entries) {
            if (installed.containsKey(entry.modelId) == wantInstalled) kept.add(entry);
        }
        return kept;
    }

    private TaiModelPreference buildRow(Context context, TaiModelCatalog.CatalogEntry entry,
                                       @Nullable TaiModelSpec installed, @Nullable JSONObject download,
                                       String activeModelId) {
        TaiModelPreference row = new TaiModelPreference(context);
        row.setKey(ROW_KEY_PREFIX + entry.modelId);
        row.setTitle(entry.displayName + "  [" + backendLabel(entry.backend) + "]");
        row.setRecommended(entry.recommended);
        row.setSummary(buildSummary(entry));
        row.setMetaLine(buildMetaLine(entry));
        row.setBackendTone(TaiModelSpec.BACKEND_MNN_LLM.equals(entry.backend)
            ? TaiModelPreference.BackendTone.MNN : TaiModelPreference.BackendTone.LITERT);
        row.setPersistent(false);
        row.setOnPreferenceClickListener(preference -> {
            showCatalogDetails(context, entry);
            return true;
        });
        applyRowState(context, row, entry, installed, download, activeModelId);
        return row;
    }

    /** Sets the state-dependent bits of a row (pill, progress, actions) so it can be rebuilt or
     *  refreshed in place without tearing down the whole list. */
    private void applyRowState(Context context, TaiModelPreference row, TaiModelCatalog.CatalogEntry entry,
                               @Nullable TaiModelSpec installed, @Nullable JSONObject download, String activeModelId) {
        CatalogActionState state = actionStateFor(entry, installed != null, download, activeModelId);
        row.setPill(state.pill, state.accentPill);
        configureProgress(row, download);
        final TaiModelSpec tuneSpec = installed;
        row.setTuneAction(tuneSpec == null ? null : getString(R.string.termux_ai_model_tune_action),
            tuneSpec == null ? null : view -> openParameterScreen(tuneSpec));
        row.setPrimaryAction(actionText(context, state), state.enabled, false,
            view -> handlePrimaryAction(context, entry, tuneSpec, state));
    }

    /** Refreshes the live rows already on screen in place (no add/remove) — used while a download
     *  is in flight so the progress bar animates smoothly instead of flashing the whole list. */
    private void updateRowsInPlace(Context context) {
        PreferenceCategory results = findPreference("tai_catalog_results_category");
        if (results == null) return;
        TaiModelStore store = new TaiModelStore(context);
        Map<String, TaiModelSpec> installed = new java.util.LinkedHashMap<>();
        installed.putAll(store.getDownloadedReadableModels());
        installed.putAll(store.getInstalledUserModels());
        JSONArray downloads = store.getDownloads();
        String activeModelId = new TaiSettings(context).getDefaultAssistantModel();
        int count = results.getPreferenceCount();
        for (int i = 0; i < count; i++) {
            Preference preference = results.getPreference(i);
            if (!(preference instanceof TaiModelPreference)) continue;
            String key = preference.getKey();
            if (key == null || !key.startsWith(ROW_KEY_PREFIX)) continue;
            String modelId = key.substring(ROW_KEY_PREFIX.length());
            TaiModelCatalog.CatalogEntry entry = TaiModelCatalog.get(modelId);
            JSONObject download = findDownload(downloads, modelId);
            if (entry == null && download != null) entry = TaiModelCatalog.downloadEntry(download);
            if (entry == null) continue;
            applyRowState(context, (TaiModelPreference) preference, entry,
                installed.get(entry.modelId), download, activeModelId);
        }
    }

    private void handlePrimaryAction(Context context, TaiModelCatalog.CatalogEntry entry,
                                     @Nullable TaiModelSpec installed, CatalogActionState state) {
        switch (state.type) {
            case INSTALL:
                confirmStartCatalogDownload(context, entry);
                break;
            case DOWNLOADING:
                cancelModelDownload(context, entry.modelId);
                break;
            case INSTALLED:
                setActiveModel(context, entry.modelId);
                break;
            case ACTIVE:
                break;
            case IMPORT_ONLY:
                showImportOnlyGuidance(context, entry);
                break;
            default:
                break;
        }
    }

    private void showCatalogDetails(Context context, TaiModelCatalog.CatalogEntry entry) {
        TaiModelStore store = new TaiModelStore(context);
        TaiModelSpec installed = store.getInstalledUserModels().get(entry.modelId);
        JSONObject download = findDownload(store.getDownloads(), entry.modelId);
        StringBuilder message = new StringBuilder();
        message.append(buildSummary(entry)).append('\n').append(buildMetaLine(entry));
        message.append("\n\nID: ").append(entry.modelId);
        message.append("\nProvider: ").append(entry.repositoryId);
        String endpointCaps = joinTags(new LinkedHashSet<>(entry.endpointCapabilities));
        String sourceCaps = joinTags(new LinkedHashSet<>(entry.sourceCapabilities));
        message.append("\nEndpoint capabilities: ").append(endpointCaps.isEmpty() ? "—" : endpointCaps);
        if (!sourceCaps.isEmpty() && !sourceCaps.equals(endpointCaps)) {
            message.append("\nSource capabilities: ").append(sourceCaps);
        }
        message.append("\nEndpoint context: ").append(entry.endpointContextWindow);
        if (entry.sourceContextWindow != entry.endpointContextWindow) {
            message.append(" · source ").append(entry.sourceContextWindow);
        }
        if (!entry.downloadAvailable) message.append("\n\n").append(entry.unavailableReason);
        if (download != null) message.append("\n\nDownload: ").append(download.optString("status", "unknown"));
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context)
            .setTitle(entry.displayName)
            .setMessage(message.toString())
            .setNeutralButton(R.string.termux_ai_model_open_provider, (dialog, which) -> openUrl(context, entry.providerPageUrl))
            .setNegativeButton(android.R.string.cancel, null);
        if (installed != null) {
            builder.setPositiveButton(R.string.termux_ai_model_tune_action, (dialog, which) -> openParameterScreen(installed));
        }
        builder.show();
    }

    private void confirmStartCatalogDownload(Context context, TaiModelCatalog.CatalogEntry entry) {
        if (!entry.downloadAvailable) {
            showImportOnlyGuidance(context, entry);
            return;
        }
        new MaterialAlertDialogBuilder(context)
            .setTitle(context.getString(R.string.termux_ai_model_download_title, entry.displayName))
            .setMessage(context.getString(R.string.termux_ai_model_download_message,
                entry.displayName, formatBytes(entry.sizeBytes), entry.license))
            .setPositiveButton(R.string.termux_ai_model_download_start, (dialog, which) -> startCatalogDownload(context, entry))
            .setNeutralButton(R.string.termux_ai_model_open_provider, (dialog, which) -> openUrl(context, entry.providerPageUrl))
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private void showImportOnlyGuidance(Context context, TaiModelCatalog.CatalogEntry entry) {
        new MaterialAlertDialogBuilder(context)
            .setTitle(R.string.termux_ai_catalog_import_only_title)
            .setMessage(context.getString(R.string.termux_ai_catalog_import_only_message,
                entry.displayName, entry.unavailableReason))
            .setPositiveButton(R.string.termux_ai_model_open_provider, (dialog, which) -> openUrl(context, entry.providerPageUrl))
            .setNeutralButton(R.string.termux_ai_dialog_copy, (dialog, which) -> copyToClipboard(context, entry.providerPageUrl))
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private String actionText(Context context, CatalogActionState state) {
        switch (state.type) {
            case INSTALL:
                return getString(R.string.termux_ai_catalog_action_install);
            case DOWNLOADING:
                return getString(R.string.termux_ai_model_download_cancel);
            case INSTALLED:
                return getString(R.string.termux_ai_model_set_active_action);
            case ACTIVE:
                return getString(R.string.termux_ai_catalog_action_active);
            case IMPORT_ONLY:
                return getString(R.string.termux_ai_catalog_action_import_only);
            default:
                return "";
        }
    }

    private String buildSummary(TaiModelCatalog.CatalogEntry entry) {
        return entry.roleHint;
    }

    private String buildMetaLine(TaiModelCatalog.CatalogEntry entry) {
        StringBuilder builder = new StringBuilder();
        builder.append(!entry.sizeEstimate.isEmpty() ? entry.sizeEstimate : formatBytes(entry.sizeBytes));
        if (!entry.ramTier.isEmpty()) builder.append(" · ").append(entry.ramTier);
        if (entry.quantization != null && !entry.quantization.isEmpty()) builder.append(" · ").append(entry.quantization);
        String tags = joinTags(displayTags(entry));
        if (!tags.isEmpty()) builder.append(" · ").append(tags);
        return builder.toString();
    }

    static List<TaiModelCatalog.CatalogEntry> filterEntries(Iterable<TaiModelCatalog.CatalogEntry> entries,
                                                            BackendFilter filter, @Nullable String query) {
        return filterEntries(entries, filter, query, null, null);
    }

    static List<TaiModelCatalog.CatalogEntry> filterEntries(Iterable<TaiModelCatalog.CatalogEntry> entries,
                                                            BackendFilter filter, @Nullable String query,
                                                            @Nullable Map<String, TaiModelSpec> installed,
                                                            @Nullable TaiDeviceCapabilities device) {
        ArrayList<TaiModelCatalog.CatalogEntry> result = new ArrayList<>();
        String normalizedQuery = normalize(query);
        for (TaiModelCatalog.CatalogEntry entry : entries) {
            if (!matchesFilter(entry, filter, installed, device)) continue;
            if (!normalizedQuery.isEmpty() && !matchesSearch(entry, normalizedQuery)) continue;
            result.add(entry);
        }
        return result;
    }

    private static boolean matchesFilter(TaiModelCatalog.CatalogEntry entry, BackendFilter filter,
                                         @Nullable Map<String, TaiModelSpec> installed,
                                         @Nullable TaiDeviceCapabilities device) {
        if (filter == BackendFilter.ALL) return true;
        if (filter == BackendFilter.INSTALLED) return installed != null && installed.containsKey(entry.modelId);
        if (filter == BackendFilter.USABLE) return isUsableOnDevice(entry, device);
        return filter.backend.equals(entry.backend);
    }

    private static boolean isUsableOnDevice(TaiModelCatalog.CatalogEntry entry, @Nullable TaiDeviceCapabilities device) {
        if (!TaiModelSpec.isSupportedBackendFormat(entry.backend, entry.format)) return false;
        if (device == null) return true;
        if (TaiModelSpec.BACKEND_MNN_LLM.equals(entry.backend)) return device.mnnSupported;
        return device.liteRtLmAbiSupported && device.liteRtLmNativeLibrariesAvailable;
    }

    static CatalogActionState actionStateFor(TaiModelCatalog.CatalogEntry entry, boolean installed,
                                             @Nullable JSONObject download, @Nullable String activeModelId) {
        if (installed) {
            boolean active = entry.modelId.equals(activeModelId);
            return active
                ? CatalogActionState.of(CatalogActionType.ACTIVE, "Default", true, false, null, "")
                : CatalogActionState.of(CatalogActionType.INSTALLED, "Installed", true, true, null, "");
        }
        if (download != null && isActiveDownload(download.optString("status", ""))) {
            return CatalogActionState.of(CatalogActionType.DOWNLOADING, "Downloading", false, true,
                null, progressLabel(download));
        }
        if (!entry.downloadAvailable) {
            return CatalogActionState.of(CatalogActionType.IMPORT_ONLY, "Import only", false, true,
                entry.unavailableReason, "");
        }
        return CatalogActionState.of(CatalogActionType.INSTALL, null, false, true, null, "");
    }

    static LinkedHashSet<String> displayTags(TaiModelCatalog.CatalogEntry entry) {
        LinkedHashSet<String> tags = new LinkedHashSet<>();
        for (String tag : entry.displayCapabilityTags) {
            if (ALLOWED_CAPABILITY_TAGS.contains(tag)) tags.add(tag);
        }
        return tags;
    }

    private static boolean matchesSearch(TaiModelCatalog.CatalogEntry entry, String query) {
        if (contains(entry.displayName, query) || contains(entry.modelId, query) || contains(entry.backend, query)
            || contains(entry.jobGroup, query)) return true;
        for (String tag : entry.displayCapabilityTags) if (contains(tag, query)) return true;
        return false;
    }

    private static String normalize(@Nullable String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.US).replace('_', '-');
    }

    private static boolean contains(@Nullable String value, String query) {
        return value != null && value.toLowerCase(Locale.US).replace('_', '-').contains(query);
    }

    private static boolean isActiveDownload(String status) {
        return TaiModelStore.STATE_QUEUED.equals(status)
            || TaiModelStore.STATE_DOWNLOADING.equals(status)
            || TaiModelStore.STATE_VERIFYING.equals(status);
    }

    private static String progressLabel(@Nullable JSONObject download) {
        if (download == null) return "…";
        long total = download.optLong("totalBytes", 0L);
        if (total <= 0L) {
            String currentFile = download.optString("currentFile", "");
            long bytesRead = download.optLong("bytesRead", 0L);
            if (!currentFile.isEmpty()) return currentFile + " - " + formatBytes(bytesRead);
            return bytesRead > 0L ? formatBytes(bytesRead) : "…";
        }
        return String.format(Locale.US, "%.0f%%", Math.max(0d, Math.min(100d,
            download.optLong("bytesRead", 0L) * 100d / total)));
    }

    @Nullable
    private static JSONObject findDownload(JSONArray downloads, String modelId) {
        if (downloads == null) return null;
        for (int i = downloads.length() - 1; i >= 0; i--) {
            JSONObject item = downloads.optJSONObject(i);
            if (item != null && modelId.equals(item.optString("modelId", ""))) return item;
        }
        return null;
    }

    private void configureProgress(TaiModelPreference row, @Nullable JSONObject download) {
        if (download == null || !isActiveDownload(download.optString("status", ""))) {
            row.setDownloadProgress(false, false, 0);
            return;
        }
        long bytesRead = download.optLong("bytesRead", 0L);
        long totalBytes = download.optLong("totalBytes", 0L);
        row.setDownloadProgress(true, totalBytes <= 0L,
            totalBytes > 0L ? (int) (bytesRead * 10000L / totalBytes) : 0);
    }

    private void startCatalogDownload(Context context, TaiModelCatalog.CatalogEntry entry) {
        try {
            JSONObject result = TaiManager.getInstance(context).downloadCatalogModel(entry.modelId);
            AppNotice.show(context, result.optBoolean("ok", false)
                ? R.string.termux_ai_model_download_started : R.string.termux_ai_model_action_failed, false);
            // Flip just this row to its downloading state in place (no full rebuild → no flash).
            updateRowsInPlace(context);
            handler.removeCallbacks(refreshRunnable);
            handler.postDelayed(refreshRunnable, POLL_INTERVAL_MS);
        } catch (JSONException e) {
            AppNotice.show(context, R.string.termux_ai_model_action_failed, true);
        }
    }

    private void cancelModelDownload(Context context, String modelId) {
        try {
            JSONObject result = TaiManager.getInstance(context).cancelDownload(
                new JSONObject().put("modelId", modelId).toString());
            AppNotice.show(context, result.optBoolean("ok", false)
                ? R.string.termux_ai_model_download_cancelled : R.string.termux_ai_model_action_failed, false);
            updateRowsInPlace(context);
        } catch (JSONException e) {
            AppNotice.show(context, R.string.termux_ai_model_action_failed, true);
        }
    }

    private void setActiveModel(Context context, String modelId) {
        TaiModelSpec model = new TaiModelStore(context).getUserModels().get(modelId);
        TaiDeviceCapabilities capabilities = TaiDeviceCapabilities.detect(context);
        if (model != null && TaiModelSpec.BACKEND_MNN_LLM.equals(model.backend) && !capabilities.mnnSupported) {
            String reason = capabilities.mnnUnsupportedReason;
            AppNotice.show(context, reason == null ? getString(R.string.termux_ai_mnn_runtime_pending) : reason, true);
            return;
        }
        SharedPreferences preferences = getPreferenceManager().getSharedPreferences();
        if (preferences == null) return;
        preferences.edit().putString(TaiSettings.KEY_ROLE_DEFAULT_ASSISTANT, modelId).apply();
        AppNotice.show(context, R.string.termux_ai_model_active_saved, false);
        updateRowsInPlace(context);
    }

    private void openParameterScreen(@NonNull TaiModelSpec model) {
        TaiParameterPreferencesFragment fragment = new TaiParameterPreferencesFragment();
        fragment.setArguments(TaiParameterPreferencesFragment.argumentsForModel(model));
        getParentFragmentManager().beginTransaction()
            .replace(R.id.settings, fragment)
            .addToBackStack(null)
            .commit();
    }

    private boolean hasActiveDownloads(Context context) {
        JSONArray downloads = new TaiModelStore(context).getDownloads();
        for (int i = 0; i < downloads.length(); i++) {
            JSONObject item = downloads.optJSONObject(i);
            if (item != null && isActiveDownload(item.optString("status", ""))) return true;
        }
        return false;
    }

    private void copyToClipboard(Context context, String text) {
        ClipboardManager clipboard = (ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard == null) return;
        clipboard.setPrimaryClip(ClipData.newPlainText("TAI provider", text));
        AppNotice.show(context, text, false);
    }

    private void openUrl(Context context, String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            AppNotice.show(context, url, true);
        }
    }

    private String backendLabel(String backend) {
        if (TaiModelSpec.BACKEND_MNN_LLM.equals(backend)) return getString(R.string.termux_ai_backend_label_mnn);
        return getString(R.string.termux_ai_backend_label_litert);
    }

    private static String joinTags(LinkedHashSet<String> tags) {
        StringBuilder builder = new StringBuilder();
        for (String tag : tags) {
            if (builder.length() > 0) builder.append(" · ");
            builder.append(tag);
        }
        return builder.toString();
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

    enum BackendFilter {
        ALL("all", ""),
        LITERT(TaiModelSpec.BACKEND_LITERT_LM, TaiModelSpec.BACKEND_LITERT_LM),
        MNN(TaiModelSpec.BACKEND_MNN_LLM, TaiModelSpec.BACKEND_MNN_LLM),
        INSTALLED("installed", ""),
        USABLE("usable", "");
        final String value;
        final String backend;
        BackendFilter(String value, String backend) { this.value = value; this.backend = backend; }
        static BackendFilter fromValue(String value) {
            for (BackendFilter filter : values()) if (filter.value.equals(value)) return filter;
            return ALL;
        }
    }

    enum CatalogActionType { INSTALL, DOWNLOADING, INSTALLED, ACTIVE, IMPORT_ONLY }

    static final class CatalogActionState {
        final CatalogActionType type;
        @Nullable final String pill;
        final boolean accentPill;
        final boolean enabled;
        @Nullable final String disabledReason;
        final String progressLabel;

        private CatalogActionState(CatalogActionType type, @Nullable String pill, boolean accentPill,
                                   boolean enabled, @Nullable String disabledReason, String progressLabel) {
            this.type = type;
            this.pill = pill;
            this.accentPill = accentPill;
            this.enabled = enabled;
            this.disabledReason = disabledReason;
            this.progressLabel = progressLabel;
        }

        static CatalogActionState of(CatalogActionType type, @Nullable String pill, boolean accentPill,
                                     boolean enabled, @Nullable String disabledReason, String progressLabel) {
            return new CatalogActionState(type, pill, accentPill, enabled, disabledReason, progressLabel);
        }
    }
}
