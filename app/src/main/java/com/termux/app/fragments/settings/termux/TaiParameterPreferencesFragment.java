package com.termux.app.fragments.settings.termux;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreferenceCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.termux.app.notice.AppNotice;
import com.termux.R;
import com.termux.ai.TaiModelCatalog;
import com.termux.ai.TaiModelProfile;
import com.termux.ai.TaiModelRegistry;
import com.termux.ai.TaiModelStore;
import com.termux.ai.TaiModelSpec;
import com.termux.ai.TaiSettings;
import com.termux.app.fragments.settings.MaterialPreferenceFragment;
import com.termux.app.fragments.settings.SettingsLayoutUtils;

import java.util.ArrayList;
import java.util.Map;

@Keep
public class TaiParameterPreferencesFragment extends MaterialPreferenceFragment {
    public static final String ARG_MODEL_ID = "modelId";
    public static final String ARG_MODEL_NAME = "modelName";
    public static final String ARG_BACKEND = "backend";

    private String modelId;
    private String modelName;
    private String backend;
    private boolean modelScreen;
    private String selectedGlobalBackend = TaiModelSpec.BACKEND_LITERT_LM;

    @NonNull
    public static Bundle argumentsForModel(@NonNull TaiModelSpec model) {
        Bundle args = new Bundle();
        args.putString(ARG_MODEL_ID, model.id);
        args.putString(ARG_MODEL_NAME, model.displayName);
        args.putString(ARG_BACKEND, model.backend);
        return args;
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        Context context = getContext();
        if (context == null) return;
        PreferenceManager preferenceManager = getPreferenceManager();
        preferenceManager.setSharedPreferencesName(TaiSettings.PREFS_NAME);

        Bundle args = getArguments();
        modelId = args == null ? null : args.getString(ARG_MODEL_ID);
        modelName = args == null ? null : args.getString(ARG_MODEL_NAME);
        backend = args == null ? null : args.getString(ARG_BACKEND);
        modelScreen = modelId != null && !modelId.isEmpty();

        PreferenceScreen screen = preferenceManager.createPreferenceScreen(context);
        setPreferenceScreen(screen);
        if (modelScreen) buildModelScreen(context, screen);
        else buildGlobalScreen(context, screen);
        SettingsLayoutUtils.applyScreenLayout(this);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() != null) {
            getActivity().setTitle(modelScreen
                ? getString(R.string.termux_ai_model_tune_title, modelName == null ? modelId : modelName)
                : getString(R.string.termux_ai_parameters_defaults_title));
            getActivity().getWindow().setFlags(
                android.view.WindowManager.LayoutParams.FLAG_SECURE,
                android.view.WindowManager.LayoutParams.FLAG_SECURE);
        }
    }

    @Override
    public void onPause() {
        if (getActivity() != null) {
            getActivity().getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE);
        }
        super.onPause();
    }

    @NonNull
    private String buildModelHeaderSummary(@NonNull Context context) {
        TaiModelSpec model = currentModelSpec(context);
        String format = TaiModelSpec.BACKEND_MNN_LLM.equals(backend) ? TaiModelSpec.FORMAT_MNN : TaiModelSpec.FORMAT_LITERTLM;
        boolean runtimeSupported = TaiModelSpec.isSupportedBackendFormat(backend, format);
        StringBuilder summary = new StringBuilder();
        summary.append(TaiModelSpec.BACKEND_MNN_LLM.equals(backend)
                ? getString(R.string.termux_ai_backend_label_mnn)
                : getString(R.string.termux_ai_backend_label_litert));
        summary.append(" · ").append(backend);
        summary.append("\n").append(getString(R.string.termux_ai_parameters_header_runtime_support,
            runtimeSupported
                ? getString(R.string.termux_ai_parameters_runtime_supported)
                : getString(R.string.termux_ai_parameters_runtime_unsupported)));
        if (model != null) {
            summary.append("\n").append(getString(R.string.termux_ai_parameters_header_endpoint_capabilities,
                model.endpointCapabilities.toString()));
            if (!model.sourceCapabilities.equals(model.endpointCapabilities)) {
                summary.append("\n").append(getString(R.string.termux_ai_parameters_header_source_capabilities,
                    model.sourceCapabilities.toString()));
            }
            summary.append("\n").append(getString(R.string.termux_ai_parameters_header_context,
                model.endpointContextWindow, model.sourceContextWindow));
            try {
                com.termux.ai.TaiModelProfile profile = com.termux.ai.TaiModelProfile.forModel(model);
                summary.append("\n").append(getString(R.string.termux_ai_parameters_header_accelerator,
                    profile.compatibleAccelerators.toString()));
            } catch (Exception ignored) {
            }
        }
        return summary.toString();
    }

    private void buildModelScreen(@NonNull Context context, @NonNull PreferenceScreen screen) {
        TaiSettings.ParameterSchema schema = TaiSettings.getParameterSchema(backend);
        Preference header = new Preference(context);
        header.setKey("tai_model_parameter_header");
        header.setTitle(modelName == null ? modelId : modelName);
        header.setSummary(buildModelHeaderSummary(context));
        header.setPersistent(false);
        header.setSelectable(false);
        screen.addPreference(header);

        addCapabilitySection(context, screen);

        PreferenceCategory configs = category(context, R.string.termux_ai_parameters_model_configs_title);
        screen.addPreference(configs);
        addParameterRows(context, configs, schema.backend, schema.fields(), true);

        PreferenceCategory prompts = category(context, R.string.termux_ai_parameters_system_prompt_title);
        screen.addPreference(prompts);
        prompts.addPreference(systemPromptPreference(context, true));

        Preference reset = new Preference(context);
        reset.setTitle(R.string.termux_ai_parameters_reset_model_title);
        reset.setSummary(R.string.termux_ai_parameters_reset_model_summary);
        reset.setPersistent(false);
        reset.setOnPreferenceClickListener(preference -> {
            new TaiSettings(context).resetModelParametersToGlobal(modelId);
            AppNotice.show(context, R.string.termux_ai_model_tune_reset_done, false);
            refreshSummaries();
            return true;
        });
        screen.addPreference(reset);
    }

    /** Lets the user declare which modalities/tools a model supports; drives the split/combined
     *  variants on /v1/models. Vision is enabled on both backends (real on LiteRT, best-effort on
     *  MNN VL models); audio is LiteRT-only; video is declared-only intent with no runtime yet. */
    private void addCapabilitySection(@NonNull Context context, @NonNull PreferenceScreen screen) {
        TaiModelSpec model = currentModelSpec(context);
        if (model == null) return;
        boolean liteRt = TaiModelSpec.BACKEND_LITERT_LM.equals(model.backend);
        PreferenceCategory caps = category(context, R.string.termux_ai_caps_title);
        screen.addPreference(caps);

        // Vision is real on LiteRT and best-effort on MNN VL models, so it is enabled on both.
        // Audio has only a LiteRT runtime path. Video is declared-only intent (no runtime yet) and
        // rides on source capabilities, so it stays out of the endpoint capability set.
        SwitchPreferenceCompat vision = capabilitySwitch(context, "vision", R.string.termux_ai_caps_vision,
            model.sourceCapabilities.contains(TaiModelSpec.CAPABILITY_IMAGE_INPUT), true);
        SwitchPreferenceCompat audio = capabilitySwitch(context, "audio", R.string.termux_ai_caps_audio,
            model.sourceCapabilities.contains(TaiModelSpec.CAPABILITY_AUDIO_INPUT), liteRt);
        SwitchPreferenceCompat video = capabilitySwitch(context, "video", R.string.termux_ai_caps_video,
            model.sourceCapabilities.contains(TaiModelSpec.CAPABILITY_VIDEO_INPUT), true);
        video.setSummary(R.string.termux_ai_caps_video_summary);
        SwitchPreferenceCompat tools = capabilitySwitch(context, "tools", R.string.termux_ai_caps_tools,
            model.sourceCapabilities.contains(TaiModelSpec.CAPABILITY_TOOL_USE), true);
        if (!liteRt) {
            vision.setSummary(R.string.termux_ai_caps_mnn_vision);
            audio.setSummary(R.string.termux_ai_caps_litert_only);
        }
        Runnable persist = () -> {
            java.util.LinkedHashSet<String> set = new java.util.LinkedHashSet<>();
            set.add(TaiModelSpec.CAPABILITY_TEXT_CHAT);
            if (vision.isChecked()) set.add(TaiModelSpec.CAPABILITY_IMAGE_INPUT);
            if (audio.isChecked()) set.add(TaiModelSpec.CAPABILITY_AUDIO_INPUT);
            if (video.isChecked()) set.add(TaiModelSpec.CAPABILITY_VIDEO_INPUT);
            if (tools.isChecked()) set.add(TaiModelSpec.CAPABILITY_TOOL_USE);
            new TaiModelStore(context).setCapabilityOverride(modelId, set);
        };
        Preference.OnPreferenceChangeListener onChange = (preference, newValue) -> {
            ((SwitchPreferenceCompat) preference).setChecked(Boolean.TRUE.equals(newValue));
            persist.run();
            return false;
        };
        vision.setOnPreferenceChangeListener(onChange);
        audio.setOnPreferenceChangeListener(onChange);
        video.setOnPreferenceChangeListener(onChange);
        tools.setOnPreferenceChangeListener(onChange);
        caps.addPreference(vision);
        caps.addPreference(audio);
        caps.addPreference(video);
        caps.addPreference(tools);

        if (!liteRt) return; // MNN has no modality variants; exposure choice is LiteRT-only
        ListPreference exposure = new ListPreference(context);
        exposure.setKey("tai_caps_exposure_" + modelId);
        exposure.setPersistent(false);
        exposure.setTitle(R.string.termux_ai_caps_exposure_title);
        exposure.setDialogTitle(R.string.termux_ai_caps_exposure_title);
        exposure.setEntries(new CharSequence[]{
            getString(R.string.termux_ai_exposure_split),
            getString(R.string.termux_ai_exposure_combined),
            getString(R.string.termux_ai_exposure_both)});
        exposure.setEntryValues(new CharSequence[]{
            TaiModelStore.EXPOSURE_SPLIT, TaiModelStore.EXPOSURE_COMBINED, TaiModelStore.EXPOSURE_BOTH});
        exposure.setValue(new TaiModelStore(context).getExposure(modelId));
        exposure.setSummaryProvider(ListPreference.SimpleSummaryProvider.getInstance());
        exposure.setOnPreferenceChangeListener((preference, newValue) -> {
            new TaiModelStore(context).setExposure(modelId, String.valueOf(newValue));
            return true;
        });
        caps.addPreference(exposure);
    }

    @NonNull
    private SwitchPreferenceCompat capabilitySwitch(@NonNull Context context, @NonNull String key,
                                                    int titleRes, boolean checked, boolean enabled) {
        SwitchPreferenceCompat preference = new SwitchPreferenceCompat(context);
        preference.setKey("tai_caps_" + key + "_" + modelId);
        preference.setPersistent(false);
        preference.setTitle(titleRes);
        preference.setChecked(checked);
        preference.setEnabled(enabled);
        return preference;
    }

    private void buildGlobalScreen(@NonNull Context context, @NonNull PreferenceScreen screen) {
        TaiCatalogFilterPreference filter = new TaiCatalogFilterPreference(context);
        filter.setKey("tai_parameters_backend_filter");
        filter.setIncludeAllOption(false);
        filter.setSelectedValue(selectedGlobalBackend);
        filter.setOnFilterSelectedListener(value -> {
            selectedGlobalBackend = TaiModelSpec.BACKEND_MNN_LLM.equals(value)
                ? TaiModelSpec.BACKEND_MNN_LLM
                : TaiModelSpec.BACKEND_LITERT_LM;
            rebuildGlobalScreen(context);
        });
        screen.addPreference(filter);

        TaiSettings.ParameterSchema schema = TaiSettings.getParameterSchema(selectedGlobalBackend);
        PreferenceCategory backendCategory = category(context,
            TaiModelSpec.BACKEND_MNN_LLM.equals(schema.backend)
                ? R.string.termux_ai_parameters_mnn_title
                : R.string.termux_ai_parameters_litert_title);
        screen.addPreference(backendCategory);
        addParameterRows(context, backendCategory, schema.backend, schema.fields(), false);

        PreferenceCategory prompts = category(context, R.string.termux_ai_parameters_system_prompt_title);
        screen.addPreference(prompts);
        prompts.addPreference(systemPromptPreference(context, false));
    }

    private void rebuildGlobalScreen(@NonNull Context context) {
        if (modelScreen) return;
        PreferenceScreen screen = getPreferenceManager().createPreferenceScreen(context);
        setPreferenceScreen(screen);
        buildGlobalScreen(context, screen);
        SettingsLayoutUtils.applyScreenLayout(this);
    }

    @NonNull
    private PreferenceCategory category(@NonNull Context context, int titleRes) {
        PreferenceCategory category = new PreferenceCategory(context);
        category.setTitle(titleRes);
        category.setPersistent(false);
        return category;
    }

    private void addParameterRows(
        @NonNull Context context,
        @NonNull PreferenceCategory category,
        @NonNull String rowBackend,
        @NonNull Map<String, TaiSettings.ParameterSpec> specs,
        boolean modelRows
    ) {
        for (TaiSettings.ParameterSpec spec : specs.values()) {
            if (!shouldShowParameter(context, rowBackend, spec.field, modelRows)) continue;
            Preference preference = new Preference(context);
            preference.setKey(parameterPreferenceKey(rowBackend, spec.field, modelRows));
            preference.setTitle(parameterLabel(spec.field));
            preference.setPersistent(false);
            preference.setSummary(parameterSummary(rowBackend, spec, modelRows));
            preference.setOnPreferenceClickListener(clicked -> {
                showParameterDialog(context, rowBackend, spec, modelRows);
                return true;
            });
            category.addPreference(preference);
        }
    }

    private boolean shouldShowParameter(@NonNull Context context, @NonNull String rowBackend,
                                        @NonNull String field, boolean modelRows) {
        TaiModelSpec model = modelRows ? currentModelSpec(context) : null;
        return shouldShowParameter(model, modelId, field, modelRows);
    }

    static boolean shouldShowParameter(@Nullable TaiModelSpec model, @Nullable String modelId,
                                        @NonNull String field, boolean modelRows) {
        if (TaiSettings.FIELD_ENABLE_THINKING.equals(field)) {
            if (!modelRows || model == null
                || !model.sourceCapabilities.contains(TaiModelSpec.CAPABILITY_LLM_THINKING)) return false;
            return TaiModelProfile.THINKING_TOGGLEABLE.equals(TaiModelProfile.forModel(model).thinkingMode);
        }
        if (TaiSettings.FIELD_ENABLE_SPECULATIVE_DECODING.equals(field)) {
            // LiteRT exposes a runtime flag. MNN's speculative_type is package-fixed metadata,
            // so showing a switch there would imply an override the upstream engine does not offer.
            return model != null && TaiModelSpec.BACKEND_LITERT_LM.equals(model.backend)
                && model.capabilities.contains(TaiModelSpec.CAPABILITY_SPECULATIVE_DECODING);
        }
        if (modelRows && TaiSettings.FIELD_ACCELERATOR.equals(field)
            && TaiModelRegistry.MODEL_MOBILE_ACTIONS_270M.equals(modelId)) {
            return false;
        }
        return true;
    }

    @Nullable
    private TaiModelSpec currentModelSpec(@NonNull Context context) {
        if (modelId == null || modelId.isEmpty()) return null;
        TaiModelSpec model = new TaiModelStore(context).getUserModel(modelId);
        if (model != null) return model;
        TaiModelCatalog.CatalogEntry entry = TaiModelCatalog.get(modelId);
        if (entry == null) return null;
        return new TaiModelRegistry().getModel(entry.modelId);
    }

    @NonNull
    public static String parameterPreferenceKey(@NonNull String backend, @NonNull String field, boolean modelRows) {
        return (modelRows ? "tai_model_parameter_screen." : "tai_global_parameter_screen.") + backend + "." + field;
    }

    private void showParameterDialog(
        @NonNull Context context,
        @NonNull String rowBackend,
        @NonNull TaiSettings.ParameterSpec spec,
        boolean modelRows
    ) {
        if (spec.options.length > 0 || spec.fallbackValue instanceof Boolean) {
            showChoiceParameterDialog(context, rowBackend, spec, modelRows);
        } else {
            showTextParameterDialog(context, rowBackend, spec, modelRows);
        }
    }

    private void showChoiceParameterDialog(
        @NonNull Context context,
        @NonNull String rowBackend,
        @NonNull TaiSettings.ParameterSpec spec,
        boolean modelRows
    ) {
        ArrayList<String> values = new ArrayList<>();
        if (modelRows) values.add("auto");
        if (spec.fallbackValue instanceof Boolean) {
            values.add("true");
            values.add("false");
        } else {
            for (String option : spec.options) values.add(option);
        }
        String[] labels = values.toArray(new String[0]);
        String current = currentRawValue(rowBackend, spec.field, modelRows, spec.defaultValue);
        int checked = 0;
        for (int i = 0; i < labels.length; i++) {
            if (labels[i].equalsIgnoreCase(current)) {
                checked = i;
                break;
            }
        }
        new MaterialAlertDialogBuilder(context)
            .setTitle(parameterLabel(spec.field))
            .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                saveParameter(context, rowBackend, spec, modelRows, labels[which]);
                dialog.dismiss();
            })
            .setNeutralButton(modelRows
                ? R.string.termux_ai_model_tune_reset_one_action
                : R.string.termux_ai_parameters_restore_backend_default_action,
                (dialog, which) -> saveParameter(context, rowBackend, spec, modelRows, ""))
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private void showTextParameterDialog(
        @NonNull Context context,
        @NonNull String rowBackend,
        @NonNull TaiSettings.ParameterSpec spec,
        boolean modelRows
    ) {
        EditText input = buildDialogEditText(context, currentRawValue(rowBackend, spec.field, modelRows, spec.defaultValue), false);
        input.setInputType(InputType.TYPE_CLASS_NUMBER | (spec.fallbackValue instanceof Double ? InputType.TYPE_NUMBER_FLAG_DECIMAL : 0));
        String range = spec.minValue == null || spec.maxValue == null
            ? null
            : getString(R.string.termux_ai_model_tune_range, spec.minValue, spec.maxValue);
        new MaterialAlertDialogBuilder(context)
            .setTitle(parameterLabel(spec.field))
            .setView(wrapDialogView(context, range, input))
            .setNeutralButton(modelRows
                ? R.string.termux_ai_model_tune_reset_one_action
                : R.string.termux_ai_parameters_restore_backend_default_action,
                (dialog, which) -> saveParameter(context, rowBackend, spec, modelRows, ""))
            .setPositiveButton(R.string.termux_ai_dialog_save, (dialog, which) ->
                saveParameter(context, rowBackend, spec, modelRows, input.getText().toString()))
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private void saveParameter(
        @NonNull Context context,
        @NonNull String rowBackend,
        @NonNull TaiSettings.ParameterSpec spec,
        boolean modelRows,
        @Nullable String rawValue
    ) {
        String value = rawValue == null ? "" : rawValue.trim();
        TaiSettings settings = new TaiSettings(context);
        if (value.isEmpty() || "auto".equalsIgnoreCase(value)) {
            if (modelRows) settings.resetModelParameterToGlobal(modelId, spec.field);
            else settings.setGlobalParameter(rowBackend, spec.field, null);
            AppNotice.show(context, R.string.termux_ai_model_tune_saved, false);
            refreshSummaries();
            return;
        }
        Object parsed = spec.parse(value);
        if (parsed == null) {
            AppNotice.show(context, R.string.termux_ai_model_tune_invalid, true);
            return;
        }
        if (modelRows) settings.setModelParameter(modelId, spec.field, parsed);
        else settings.setGlobalParameter(rowBackend, spec.field, parsed);
        AppNotice.show(context, R.string.termux_ai_model_tune_saved, false);
        refreshSummaries();
    }

    @NonNull
    private Preference systemPromptPreference(@NonNull Context context, boolean modelPrompt) {
        Preference preference = new Preference(context);
        preference.setKey(modelPrompt ? TaiSettings.modelSystemPromptPreferenceKey(modelId) : TaiSettings.KEY_SYSTEM_PROMPT_GENERAL);
        preference.setTitle(modelPrompt ? R.string.termux_ai_model_system_prompt_title : R.string.termux_ai_general_prompt_title);
        preference.setPersistent(false);
        preference.setSummary(systemPromptSummary(modelPrompt));
        preference.setOnPreferenceClickListener(clicked -> {
            showSystemPromptDialog(context, modelPrompt);
            return true;
        });
        return preference;
    }

    private void showSystemPromptDialog(@NonNull Context context, boolean modelPrompt) {
        String current = modelPrompt ? currentModelSystemPrompt() : new TaiSettings(context).getGeneralSystemPrompt();
        EditText input = buildDialogEditText(context, current, true);
        MaterialAlertDialogBuilder builder = new MaterialAlertDialogBuilder(context)
            .setTitle(modelPrompt ? R.string.termux_ai_model_system_prompt_title : R.string.termux_ai_general_prompt_title)
            .setView(wrapDialogView(context, modelPrompt ? getString(R.string.termux_ai_model_system_prompt_summary) : null, input))
            .setPositiveButton(R.string.termux_ai_dialog_save, (dialog, which) -> {
                if (modelPrompt) new TaiSettings(context).setModelSystemPrompt(modelId, input.getText().toString());
                else getPreferenceManager().getSharedPreferences().edit()
                    .putString(TaiSettings.KEY_SYSTEM_PROMPT_GENERAL, input.getText().toString()).apply();
                refreshSummaries();
            })
            .setNegativeButton(android.R.string.cancel, null);
        if (modelPrompt) {
            builder.setNeutralButton(R.string.termux_ai_model_tune_reset_one_action, (dialog, which) -> {
                new TaiSettings(context).resetModelSystemPromptToGlobal(modelId);
                refreshSummaries();
            });
        } else {
            builder.setNeutralButton(R.string.termux_ai_parameters_restore_backend_default_action, (dialog, which) -> {
                getPreferenceManager().getSharedPreferences().edit().remove(TaiSettings.KEY_SYSTEM_PROMPT_GENERAL).apply();
                refreshSummaries();
            });
        }
        builder.show();
    }

    private void refreshSummaries() {
        PreferenceScreen screen = getPreferenceScreen();
        if (screen == null) return;
        refreshGroupSummaries(screen);
    }

    private void refreshGroupSummaries(@NonNull PreferenceScreen screen) {
        for (int i = 0; i < screen.getPreferenceCount(); i++) {
            Preference preference = screen.getPreference(i);
            if (preference instanceof PreferenceCategory) refreshCategorySummaries((PreferenceCategory) preference);
        }
    }

    private void refreshCategorySummaries(@NonNull PreferenceCategory category) {
        for (int i = 0; i < category.getPreferenceCount(); i++) {
            Preference preference = category.getPreference(i);
            String key = preference.getKey();
            if (key == null) continue;
            if (key.equals(TaiSettings.KEY_SYSTEM_PROMPT_GENERAL)) {
                preference.setSummary(systemPromptSummary(false));
            } else if (modelId != null && key.equals(TaiSettings.modelSystemPromptPreferenceKey(modelId))) {
                preference.setSummary(systemPromptSummary(true));
            } else {
                refreshParameterSummary(preference, key);
            }
        }
    }

    private void refreshParameterSummary(@NonNull Preference preference, @NonNull String key) {
        String[] parts = key.split("\\.", 3);
        if (parts.length < 3) return;
        boolean modelRows = key.startsWith("tai_model_parameter_screen.");
        String rowBackend = parts[1];
        String field = parts[2];
        TaiSettings.ParameterSpec spec = TaiSettings.getParameterSchema(rowBackend).get(field);
        if (spec != null) preference.setSummary(parameterSummary(rowBackend, spec, modelRows));
    }

    @NonNull
    private String currentRawValue(@NonNull String rowBackend, @NonNull String field, boolean modelRows, @NonNull String defaultValue) {
        SharedPreferences preferences = getPreferenceManager().getSharedPreferences();
        if (preferences == null) return modelRows ? "auto" : defaultValue;
        if (modelRows) return preferences.getString(TaiSettings.modelParameterPreferenceKey(modelId, field), "auto");
        return preferences.getString(TaiSettings.globalParameterKey(rowBackend, field), defaultValue);
    }

    @NonNull
    private CharSequence parameterSummary(@NonNull String rowBackend, @NonNull TaiSettings.ParameterSpec spec, boolean modelRows) {
        SharedPreferences preferences = getPreferenceManager().getSharedPreferences();
        String raw = currentRawValue(rowBackend, spec.field, modelRows, spec.defaultValue);
        if (modelRows && (preferences == null || !preferences.contains(TaiSettings.modelParameterPreferenceKey(modelId, spec.field)))) {
            return getString(R.string.termux_ai_parameters_summary_global, resolvedValue(rowBackend, spec));
        }
        if (!modelRows && (preferences == null || !preferences.contains(TaiSettings.globalParameterKey(rowBackend, spec.field)))) {
            return getString(R.string.termux_ai_parameters_summary_backend_default, overrideValueLabel(spec.field, spec.defaultValue));
        }
        return getString(modelRows ? R.string.termux_ai_parameters_summary_override : R.string.termux_ai_parameters_summary_default,
            overrideValueLabel(spec.field, raw));
    }

    @NonNull
    private String resolvedValue(@NonNull String rowBackend, @NonNull TaiSettings.ParameterSpec spec) {
        TaiSettings settings = new TaiSettings(requireContext());
        Object value = resolvedRuntimeValue(settings, rowBackend, spec.field);
        return overrideValueLabel(spec.field, value == null ? spec.defaultValue : String.valueOf(value));
    }

    @Nullable
    private Object resolvedRuntimeValue(@NonNull TaiSettings settings, @NonNull String rowBackend, @NonNull String field) {
        if (TaiSettings.FIELD_MAX_TOKENS.equals(field)) return settings.getRuntimeOptions(rowBackend, modelId).maxTokens;
        if (TaiSettings.FIELD_TOP_K.equals(field)) return settings.getRuntimeOptions(rowBackend, modelId).topK;
        if (TaiSettings.FIELD_TOP_P.equals(field)) return settings.getRuntimeOptions(rowBackend, modelId).topP;
        if (TaiSettings.FIELD_TEMPERATURE.equals(field)) return settings.getRuntimeOptions(rowBackend, modelId).temperature;
        if (TaiSettings.FIELD_ACCELERATOR.equals(field)) return settings.getRuntimeOptions(rowBackend, modelId).accelerator;
        if (TaiSettings.FIELD_CONTEXT_WINDOW.equals(field)) return settings.getRuntimeOptions(rowBackend, modelId).contextWindow;
        if (TaiSettings.FIELD_THREAD_COUNT.equals(field)) return settings.getRuntimeOptions(rowBackend, modelId).threadCount;
        if (TaiSettings.FIELD_PRECISION.equals(field)) return settings.getRuntimeOptions(rowBackend, modelId).precision;
        if (TaiSettings.FIELD_MEMORY_MODE.equals(field)) return settings.getRuntimeOptions(rowBackend, modelId).memoryMode;
        if (TaiSettings.FIELD_ENABLE_THINKING.equals(field)) return settings.getRuntimeOptions(rowBackend, modelId).thinkingEnabled;
        if (TaiSettings.FIELD_ENABLE_SPECULATIVE_DECODING.equals(field)) return settings.getRuntimeOptions(rowBackend, modelId).speculativeDecodingEnabled;
        return null;
    }

    @NonNull
    private CharSequence systemPromptSummary(boolean modelPrompt) {
        if (modelPrompt) {
            SharedPreferences preferences = getPreferenceManager().getSharedPreferences();
            boolean hasOverride = preferences != null && preferences.contains(TaiSettings.modelSystemPromptPreferenceKey(modelId));
            return hasOverride
                ? getString(R.string.termux_ai_parameters_summary_override, trimForSummary(currentModelSystemPrompt()))
                : getString(R.string.termux_ai_parameters_summary_global, trimForSummary(new TaiSettings(requireContext()).getGeneralSystemPrompt()));
        }
        return trimForSummary(new TaiSettings(requireContext()).getGeneralSystemPrompt());
    }

    @NonNull
    private String currentModelSystemPrompt() {
        SharedPreferences preferences = getPreferenceManager().getSharedPreferences();
        return preferences == null ? "" : preferences.getString(TaiSettings.modelSystemPromptPreferenceKey(modelId), "");
    }

    @NonNull
    private String trimForSummary(@Nullable String value) {
        if (value == null || value.trim().isEmpty()) return getString(R.string.termux_ai_parameters_prompt_empty);
        String compact = value.trim().replace('\n', ' ');
        return compact.length() > 120 ? compact.substring(0, 117) + "..." : compact;
    }

    @NonNull
    private String overrideValueLabel(@NonNull String field, @Nullable String value) {
        if (value == null || value.isEmpty() || "auto".equalsIgnoreCase(value)) return "auto";
        if (TaiSettings.FIELD_ENABLE_THINKING.equals(field) || TaiSettings.FIELD_ENABLE_SPECULATIVE_DECODING.equals(field)) {
            if ("true".equalsIgnoreCase(value)) return getString(R.string.termux_ai_enabled);
            if ("false".equalsIgnoreCase(value)) return getString(R.string.termux_ai_disabled);
        }
        return value;
    }

    @NonNull
    private String parameterLabel(@NonNull String field) {
        if (TaiSettings.FIELD_MAX_TOKENS.equals(field)) return getString(R.string.termux_ai_max_tokens_title);
        if (TaiSettings.FIELD_TOP_K.equals(field)) return getString(R.string.termux_ai_top_k_title);
        if (TaiSettings.FIELD_TOP_P.equals(field)) return getString(R.string.termux_ai_top_p_title);
        if (TaiSettings.FIELD_TEMPERATURE.equals(field)) return getString(R.string.termux_ai_temperature_title);
        if (TaiSettings.FIELD_ACCELERATOR.equals(field)) return getString(R.string.termux_ai_accelerator_title);
        if (TaiSettings.FIELD_ENABLE_THINKING.equals(field)) return getString(R.string.termux_ai_thinking_title);
        if (TaiSettings.FIELD_ENABLE_SPECULATIVE_DECODING.equals(field)) return getString(R.string.termux_ai_speculative_decoding_title);
        if (TaiSettings.FIELD_CONTEXT_WINDOW.equals(field)) return getString(R.string.termux_ai_context_window_title);
        if (TaiSettings.FIELD_THREAD_COUNT.equals(field)) return getString(R.string.termux_ai_thread_count_title);
        if (TaiSettings.FIELD_PRECISION.equals(field)) return getString(R.string.termux_ai_precision_title);
        if (TaiSettings.FIELD_MEMORY_MODE.equals(field)) return getString(R.string.termux_ai_memory_mode_title);
        return field;
    }

    @NonNull
    private EditText buildDialogEditText(@NonNull Context context, @Nullable String value, boolean multiline) {
        EditText input = new EditText(context);
        input.setInputType(InputType.TYPE_CLASS_TEXT | (multiline ? InputType.TYPE_TEXT_FLAG_MULTI_LINE : 0));
        input.setSingleLine(!multiline);
        if (multiline) {
            input.setMinLines(4);
            input.setGravity(Gravity.TOP | Gravity.START);
        }
        if (value != null) {
            input.setText(value);
            input.setSelection(value.length());
        }
        return input;
    }

    @NonNull
    private View wrapDialogView(@NonNull Context context, @Nullable CharSequence header, @NonNull View input) {
        float density = context.getResources().getDisplayMetrics().density;
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        int padH = Math.round(24 * density);
        layout.setPadding(padH, Math.round(8 * density), padH, 0);
        if (header != null) {
            TextView headerView = new TextView(context);
            headerView.setText(header);
            headerView.setTextIsSelectable(true);
            headerView.setPadding(0, 0, 0, Math.round(12 * density));
            layout.addView(headerView);
        }
        layout.addView(input);
        return layout;
    }
}
