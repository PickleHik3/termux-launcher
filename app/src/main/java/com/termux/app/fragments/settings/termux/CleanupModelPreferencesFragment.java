package com.termux.app.fragments.settings.termux;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;

import com.termux.R;
import com.termux.ai.TaiDeviceCapabilities;
import com.termux.ai.TaiModelRegistry;
import com.termux.ai.TaiModelSpec;
import com.termux.ai.TaiModelStore;
import com.termux.app.fragments.settings.MaterialPreferenceFragment;
import com.termux.app.fragments.settings.SettingsLayoutUtils;
import com.termux.app.notice.AppNotice;
import com.termux.app.terminal.inappkeyboard.voice.LocalTaiVoiceTextPolisher;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Keyboard → Voice input → Polish dictation → Cleanup model: which installed chat model rewrites
 * dictated text before it is typed. "Automatic" (the default, an empty stored id) picks Gemma 4
 * E4B when the phone meets its RAM recommendation or E2B is not installed, else E2B, the same rule
 * {@link LocalTaiVoiceTextPolisher} falls back to; every other row is one installed chat model, in
 * the same shape {@link SpeechModelPreferencesFragment} lists speech models. No download dialog
 * here — models are downloaded from the TAI settings page, this screen only chooses among what is
 * already installed.
 */
@Keep
public class CleanupModelPreferencesFragment extends MaterialPreferenceFragment {
    private static final String ROW_KEY_PREFIX = "cleanup_model_row_";
    private static final String KEY_AUTOMATIC = "cleanup_model_automatic";
    private static final String KEY_INSTALLED_CATEGORY = "cleanup_model_installed";
    private static final String KEY_EMPTY = "cleanup_model_empty";
    private static final String KEY_OPEN_TAI_SETTINGS = "cleanup_model_open_tai_settings";

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        Context context = getContext();
        if (context == null) return;
        setPreferencesFromResource(R.xml.cleanup_model_preferences, rootKey);
        SettingsLayoutUtils.applyScreenLayout(this);
        Preference openTaiSettings = findPreference(KEY_OPEN_TAI_SETTINGS);
        if (openTaiSettings != null) openTaiSettings.setOnPreferenceClickListener(preference -> {
            // Chat models are got in the Model centre now; it opens on its Chat segment, pushed
            // in place so Back returns to this screen instead of closing Settings.
            TaiModelCentreFragment.open(getActivity(), TaiModelCentreFragment.SEGMENT_CHAT);
            return true;
        });
        refresh(context);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() != null) getActivity().setTitle(R.string.settings_keyboard_voice_polish_model_title);
        Context context = getContext();
        if (context != null) refresh(context);
    }

    /** Redraws the Automatic row and one row per installed chat model, in place. */
    void refresh(@NonNull Context context) {
        PreferenceCategory category = findPreference(KEY_INSTALLED_CATEGORY);
        if (category == null) return;
        TermuxAppSharedPreferences prefs = TermuxAppSharedPreferences.build(context, true);
        String selectedId = prefs.getInAppKeyboardVoicePolishModelId();
        Map<String, TaiModelSpec> installed = LocalTaiVoiceTextPolisher.installedChatModels(context);

        Preference automatic = findPreference(KEY_AUTOMATIC);
        if (automatic != null) {
            TaiModelSpec automaticChoice = automaticModel(context);
            String summary = automaticChoice == null
                ? getString(R.string.cleanup_model_automatic_summary_none)
                : getString(R.string.cleanup_model_automatic_summary_model, automaticChoice.displayName);
            boolean automaticSelected = selectedId.isEmpty();
            automatic.setSummary(automaticSelected
                ? summary + " · " + getString(R.string.cleanup_model_in_use) : summary);
            automatic.setOnPreferenceClickListener(preference -> {
                select(context, "");
                return true;
            });
        }

        Set<String> keep = new HashSet<>();
        int order = 0;
        for (Map.Entry<String, TaiModelSpec> entry : installed.entrySet()) {
            TaiModelSpec spec = entry.getValue();
            TaiModelPreference row = rowFor(context, category, spec.id);
            keep.add(row.getKey());
            row.setOrder(order++);
            bindRow(context, row, spec, spec.id.equals(selectedId));
        }
        for (int i = category.getPreferenceCount() - 1; i >= 0; i--) {
            Preference preference = category.getPreference(i);
            String key = preference.getKey();
            if (key != null && key.startsWith(ROW_KEY_PREFIX) && !keep.contains(key)) category.removePreference(preference);
        }
        Preference empty = findPreference(KEY_EMPTY);
        if (empty != null) empty.setVisible(installed.isEmpty());
        Preference openTaiSettings = findPreference(KEY_OPEN_TAI_SETTINGS);
        if (openTaiSettings != null) openTaiSettings.setVisible(installed.isEmpty());
    }

    @NonNull
    private TaiModelPreference rowFor(@NonNull Context context, @NonNull PreferenceCategory category, @NonNull String modelId) {
        String key = ROW_KEY_PREFIX + modelId;
        Preference existing = category.findPreference(key);
        if (existing instanceof TaiModelPreference) return (TaiModelPreference) existing;
        TaiModelPreference row = new TaiModelPreference(context);
        row.setKey(key);
        row.setPersistent(false);
        row.setBackendTone(TaiModelPreference.BackendTone.NEUTRAL);
        category.addPreference(row);
        return row;
    }

    private void bindRow(@NonNull Context context, @NonNull TaiModelPreference row, @NonNull TaiModelSpec spec, boolean selected) {
        row.setTitle(spec.displayName);
        row.setPill(selected ? getString(R.string.cleanup_model_in_use) : null, true);
        row.setDownloadProgress(false, false, 0);
        row.setTuneAction(null, null);
        String size = formatBytes(spec.sizeBytes);
        String warning = TaiDeviceCapabilities.detect(context).checkModelCapability(spec).warning;
        row.setSummary(warning == null ? size : getString(R.string.cleanup_model_ram_warning, size));
        row.setOnPreferenceClickListener(preference -> {
            if (!selected) select(context, spec.id);
            return true;
        });
    }

    private void select(@NonNull Context context, @NonNull String modelId) {
        TermuxAppSharedPreferences.build(context, true).setInAppKeyboardVoicePolishModelId(modelId);
        AppNotice.show(context, modelId.isEmpty() ? getString(R.string.cleanup_model_now_using_automatic)
            : getString(R.string.cleanup_model_now_using, displayNameOf(context, modelId)), false);
        refresh(context);
    }

    @NonNull
    private String displayNameOf(@NonNull Context context, @NonNull String modelId) {
        TaiModelSpec spec = LocalTaiVoiceTextPolisher.installedChatModels(context).get(modelId);
        return spec == null ? modelId : spec.displayName;
    }

    /** The model "Automatic" resolves to right now, or {@code null} when no Gemma is installed. */
    @Nullable
    private static TaiModelSpec automaticModel(@NonNull Context context) {
        Map<String, TaiModelSpec> installed = new TaiModelStore(context).getDownloadedReadableModels();
        TaiModelSpec e4b = installed.get(TaiModelRegistry.MODEL_GEMMA_4_E4B_IT);
        TaiModelSpec e2b = installed.get(TaiModelRegistry.MODEL_GEMMA_4_E2B_IT);
        if (e4b != null && (e2b == null
                || TaiDeviceCapabilities.detect(context).checkModelCapability(e4b).warning == null)) return e4b;
        if (e2b != null) return e2b;
        return e4b;
    }

    @NonNull
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
