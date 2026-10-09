package com.termux.app.fragments.settings.termux;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.preference.Preference;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.termux.R;
import com.termux.ai.TaiDiagnostics;
import com.termux.ai.TaiLoadBudget;
import com.termux.ai.TaiSettings;
import com.termux.app.fragments.settings.MaterialPreferenceFragment;
import com.termux.app.fragments.settings.SegmentedPillPreference;
import com.termux.app.fragments.settings.SettingsLayoutUtils;
import com.termux.app.notice.AppNotice;

import java.util.ArrayList;
import java.util.List;

/**
 * On-device AI → Advanced: default model parameters, the runtime overrides grid, the memory
 * limit and the diagnostics share. The overrides read and write the page's SharedPreferences, so
 * the page uses {@link TaiSettings#PREFS_NAME}, the file the runtime reads.
 */
@Keep
public class TaiAdvancedPreferencesFragment extends MaterialPreferenceFragment {

    private static final class OverrideSpec {
        final String key;
        final int titleRes;
        final int entriesRes;
        final int valuesRes;
        final String defaultValue;

        OverrideSpec(String key, int titleRes, int entriesRes, int valuesRes, String defaultValue) {
            this.key = key;
            this.titleRes = titleRes;
            this.entriesRes = entriesRes;
            this.valuesRes = valuesRes;
            this.defaultValue = defaultValue;
        }
    }

    private static final OverrideSpec[] OVERRIDE_SPECS = {
        new OverrideSpec("tai_max_tokens", R.string.termux_ai_max_tokens_title,
            R.array.termux_ai_max_tokens_entries, R.array.termux_ai_max_tokens_values, "auto"),
        new OverrideSpec("tai_top_k", R.string.termux_ai_top_k_title,
            R.array.termux_ai_top_k_entries, R.array.termux_ai_top_k_values, "auto"),
        new OverrideSpec("tai_top_p", R.string.termux_ai_top_p_title,
            R.array.termux_ai_top_p_entries, R.array.termux_ai_top_p_values, "auto"),
        new OverrideSpec("tai_temperature", R.string.termux_ai_temperature_title,
            R.array.termux_ai_temperature_entries, R.array.termux_ai_temperature_values, "auto"),
        new OverrideSpec("tai_accelerator", R.string.termux_ai_accelerator_title,
            R.array.termux_ai_accelerator_entries, R.array.termux_ai_accelerator_values, "auto"),
        new OverrideSpec("tai_thinking", R.string.termux_ai_thinking_title,
            R.array.termux_ai_auto_boolean_entries, R.array.termux_ai_auto_boolean_values, "auto"),
        new OverrideSpec("tai_speculative_decoding", R.string.termux_ai_speculative_decoding_title,
            R.array.termux_ai_auto_boolean_entries, R.array.termux_ai_auto_boolean_values, "auto"),
        new OverrideSpec("tai_idle_unload_minutes", R.string.termux_ai_idle_unload_title,
            R.array.termux_ai_idle_unload_entries, R.array.termux_ai_idle_unload_values, "10"),
    };

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final TaiRuntimePoll worker = new TaiRuntimePoll(this::isAdded);

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        Context context = getContext();
        if (context == null) return;
        getPreferenceManager().setSharedPreferencesName(TaiSettings.PREFS_NAME);
        setPreferencesFromResource(R.xml.tai_advanced_preferences, rootKey);
        SettingsLayoutUtils.applyScreenLayout(this);
        Preference parameters = findPreference("tai_parameters_defaults");
        if (parameters != null) {
            parameters.setOnPreferenceClickListener(preference -> {
                openParameterScreen();
                return true;
            });
        }
        TaiOverridesPreference overrides = findPreference("tai_runtime_overrides");
        if (overrides != null) overrides.setOnOverrideClickListener(index -> showOverrideDialog(context, index));
        configureMemoryLimits(context);
        Preference diagnostics = findPreference("tai_share_diagnostics");
        if (diagnostics != null) {
            diagnostics.setOnPreferenceClickListener(preference -> {
                shareDiagnostics(context);
                return true;
            });
        }
        refreshOverrides();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() != null) getActivity().setTitle(R.string.termux_ai_zone_advanced_title);
        refreshOverrides();
    }

    @Override
    public void onDestroy() {
        worker.shutdown();
        super.onDestroy();
    }

    private void openParameterScreen() {
        getParentFragmentManager().beginTransaction()
            .replace(R.id.settings, new TaiParameterPreferencesFragment())
            .addToBackStack(null)
            .commit();
    }

    private void refreshOverrides() {
        TaiOverridesPreference overrides = findPreference("tai_runtime_overrides");
        if (overrides == null) return;
        SharedPreferences preferences = getPreferenceManager().getSharedPreferences();
        if (preferences == null) return;
        List<TaiOverridesPreference.Item> items = new ArrayList<>();
        for (OverrideSpec spec : OVERRIDE_SPECS) {
            String value = preferences.getString(spec.key, spec.defaultValue);
            items.add(new TaiOverridesPreference.Item(getString(spec.titleRes), overrideValueLabel(spec.key, value)));
        }
        overrides.setItems(items);
    }

    private static String overrideValueLabel(String key, String value) {
        if (value == null || value.isEmpty()) return "auto";
        if ("tai_accelerator".equals(key) || TaiSettings.FIELD_ACCELERATOR.equals(key)) {
            return "auto".equals(value) ? "profile" : value;
        }
        if ("tai_idle_unload_minutes".equals(key)) {
            return "0".equals(value) ? "off" : value + " min";
        }
        if ("tai_thinking".equals(key) || "tai_speculative_decoding".equals(key)
            || TaiSettings.FIELD_ENABLE_THINKING.equals(key)
            || TaiSettings.FIELD_ENABLE_SPECULATIVE_DECODING.equals(key)) {
            if ("true".equals(value)) return "on";
            if ("false".equals(value)) return "off";
            return "auto";
        }
        return value;
    }

    private void showOverrideDialog(Context context, int index) {
        if (index < 0 || index >= OVERRIDE_SPECS.length) return;
        OverrideSpec spec = OVERRIDE_SPECS[index];
        SharedPreferences preferences = getPreferenceManager().getSharedPreferences();
        if (preferences == null) return;
        String[] entries = getResources().getStringArray(spec.entriesRes);
        String[] values = getResources().getStringArray(spec.valuesRes);
        String current = preferences.getString(spec.key, spec.defaultValue);
        int checked = -1;
        for (int i = 0; i < values.length; i++) {
            if (values[i].equals(current)) {
                checked = i;
                break;
            }
        }
        new MaterialAlertDialogBuilder(context)
            .setTitle(spec.titleRes)
            .setSingleChoiceItems(entries, checked, (dialog, which) -> {
                preferences.edit().putString(spec.key, values[which]).apply();
                dialog.dismiss();
                refreshOverrides();
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    /**
     * Relaxed / Unrestricted, backed by {@link TaiSettings#setMemoryMode} (a file both processes
     * read) rather than the page's preferences, which the runtime process would not see change.
     */
    private void configureMemoryLimits(Context context) {
        SegmentedPillPreference limits = findPreference("tai_memory_limits");
        if (limits == null) return;
        limits.setSegments(
            new String[] {TaiLoadBudget.MemoryMode.RELAXED.id, TaiLoadBudget.MemoryMode.UNRESTRICTED.id},
            new int[] {R.string.termux_ai_memory_limits_relaxed, R.string.termux_ai_memory_limits_unrestricted});
        TaiLoadBudget.MemoryMode current = new TaiSettings(context).getMemoryMode();
        limits.setValue(current.id);
        limits.setSummary(memoryLimitsSummary(current));
        limits.setOnPreferenceChangeListener((preference, newValue) -> {
            TaiLoadBudget.MemoryMode mode = TaiLoadBudget.MemoryMode.fromId(String.valueOf(newValue));
            new TaiSettings(context).setMemoryMode(mode);
            preference.setSummary(memoryLimitsSummary(mode));
            return true;
        });
    }

    private static int memoryLimitsSummary(@NonNull TaiLoadBudget.MemoryMode mode) {
        return mode == TaiLoadBudget.MemoryMode.UNRESTRICTED
            ? R.string.termux_ai_memory_limits_unrestricted_summary : R.string.termux_ai_memory_limits_relaxed_summary;
    }

    /**
     * Builds {@code files/tai/diagnostics.txt} off the UI thread and hands it to the share sheet
     * through the FileProvider the app already declares (the cropper's, which covers {@code files/}).
     */
    private void shareDiagnostics(Context context) {
        worker.run(() -> {
            Uri uri;
            try {
                java.io.File file = TaiDiagnostics.write(context);
                uri = androidx.core.content.FileProvider.getUriForFile(context,
                    context.getPackageName() + ".cropper.fileprovider", file);
            } catch (java.io.IOException | IllegalArgumentException e) {
                uri = null;
            }
            final Uri shared = uri;
            handler.post(() -> {
                if (!isAdded()) return;
                if (shared == null) {
                    AppNotice.show(context, R.string.termux_ai_share_diagnostics_failed, true);
                    return;
                }
                Intent send = new Intent(Intent.ACTION_SEND);
                send.setType("text/plain");
                send.putExtra(Intent.EXTRA_STREAM, shared);
                send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                try {
                    startActivity(Intent.createChooser(send, getString(R.string.termux_ai_share_diagnostics_chooser)));
                } catch (RuntimeException e) {
                    AppNotice.show(context, R.string.termux_ai_share_diagnostics_failed, true);
                }
            });
        });
    }
}
