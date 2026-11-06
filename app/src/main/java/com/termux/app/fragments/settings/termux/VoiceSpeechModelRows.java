package com.termux.app.fragments.settings.termux;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.termux.R;
import com.termux.ai.SttAcceleratorChoices;
import com.termux.ai.TaiModelSpec;
import com.termux.ai.TaiModelStore;
import com.termux.ai.TaiSettings;
import com.termux.ai.TaiSpeechModels;
import com.termux.app.fragments.settings.SegmentedPillPreference;
import com.termux.app.notice.AppNotice;

import java.util.ArrayList;
import java.util.List;

/**
 * The Voice input page's Speech model rows: the model in use (a tap picks another installed one or
 * opens the Model centre), where it runs (Auto, CPU or GPU, remembered per model) and how long it
 * stays loaded when idle. The pill reads and writes its value through
 * {@code KeyboardPreferencesDataStore}, which asks {@link TaiSettings} for the model in use, so a
 * model switch only has to re-offer the segments ({@link #refresh}).
 */
final class VoiceSpeechModelRows {
    static final String KEY_PICK = "speech_model_pick";
    static final String KEY_ACCELERATOR = "speech_model_accelerator";
    static final String KEY_IDLE_UNLOAD = "speech_model_idle_unload";

    private final PreferenceFragmentCompat fragment;

    VoiceSpeechModelRows(@NonNull PreferenceFragmentCompat fragment) {
        this.fragment = fragment;
    }

    /** Wires the clicks; the page calls {@link #refresh} now and on every resume. */
    void bind(@NonNull Context context) {
        Preference pick = fragment.findPreference(KEY_PICK);
        if (pick != null) pick.setOnPreferenceClickListener(preference -> {
            showPicker(context);
            return true;
        });
        Preference idleUnload = fragment.findPreference(KEY_IDLE_UNLOAD);
        if (idleUnload != null) idleUnload.setOnPreferenceClickListener(preference -> {
            showIdleUnloadDialog(context);
            return true;
        });
        refresh(context);
    }

    /** Applies whatever a finished download decided, then redraws the three rows from the stored state. */
    void refresh(@NonNull Context context) {
        TaiSpeechActions.announce(context, TaiSpeechModels.settlePending(context));
        TaiModelSpec active = TaiSpeechModels.resolveActive(context);
        Preference pick = fragment.findPreference(KEY_PICK);
        if (pick != null) {
            CharSequence title = active == null
                ? fragment.getString(R.string.speech_model_empty_title) : modelLabel(active);
            CharSequence summary = active == null ? fragment.getString(R.string.speech_model_empty_summary) : null;
            if (!title.equals(pick.getTitle())) pick.setTitle(title);
            if (!java.util.Objects.equals(summary, pick.getSummary())) pick.setSummary(summary);
        }
        SegmentedPillPreference accelerator = fragment.findPreference(KEY_ACCELERATOR);
        if (accelerator != null) bindAccelerator(context, accelerator, active);
        Preference idleUnload = fragment.findPreference(KEY_IDLE_UNLOAD);
        if (idleUnload != null) idleUnload.setSummary(idleUnloadLabel(new TaiSettings(context).getSttIdleUnloadMinutes()));
    }

    // ---- model ----

    /** "Whisper Base · 97 MB": the plain name and the size on disk. */
    @NonNull
    private static String modelLabel(@NonNull TaiModelSpec spec) {
        return TaiSpeechModels.plainName(spec) + " · " + TaiModelCentreRows.formatBytes(spec.sizeBytes);
    }

    /**
     * Lists the installed speech models, the one in use checked, and "Get more models" for the
     * Model centre's Speech segment. With none installed the tap goes straight there.
     */
    private void showPicker(@NonNull Context context) {
        List<TaiModelSpec> installed = TaiSpeechModels.installed(new TaiModelStore(context));
        if (installed.isEmpty()) {
            openModelCentre();
            return;
        }
        TaiModelSpec active = TaiSpeechModels.resolveActive(context);
        String[] labels = new String[installed.size()];
        int checked = -1;
        for (int i = 0; i < labels.length; i++) {
            labels[i] = modelLabel(installed.get(i));
            if (active != null && active.id.equals(installed.get(i).id)) checked = i;
        }
        new MaterialAlertDialogBuilder(context)
            .setTitle(R.string.settings_keyboard_voice_model_title)
            .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                TaiModelSpec chosen = installed.get(which);
                if (active == null || !active.id.equals(chosen.id)) {
                    TaiSpeechModels.activate(new TaiSettings(context), chosen.id);
                    AppNotice.show(context, fragment.getString(R.string.speech_model_now_using,
                        TaiSpeechModels.plainName(chosen)), false);
                    refresh(context);
                }
                dialog.dismiss();
            })
            .setNeutralButton(R.string.speech_model_get_more_models, (dialog, which) -> openModelCentre())
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private void openModelCentre() {
        TaiModelCentreFragment.open(fragment.getActivity(), TaiModelCentreFragment.SEGMENT_SPEECH);
    }

    // ---- runs on ----

    /**
     * Offers the segments the model in use can run on (GPU only when {@link SttAcceleratorChoices}
     * says so) and re-reads the pill's value, which is the model's own pick. Without a model the
     * pill is dimmed.
     */
    private void bindAccelerator(@NonNull Context context, @NonNull SegmentedPillPreference pill, @Nullable TaiModelSpec active) {
        List<String> offered = SttAcceleratorChoices.options(SttAcceleratorChoices.deviceSupportsGpu(context),
            SttAcceleratorChoices.gpuFailure(context, active));
        String[] labels = fragment.getResources().getStringArray(R.array.speech_model_accelerator_entries);
        String[] values = fragment.getResources().getStringArray(R.array.speech_model_accelerator_values);
        List<String> shownValues = new ArrayList<>(values.length);
        List<CharSequence> shownLabels = new ArrayList<>(values.length);
        for (int i = 0; i < values.length && i < labels.length; i++) {
            if (!offered.contains(values[i])) continue;
            shownValues.add(values[i]);
            shownLabels.add(labels[i]);
        }
        pill.setSegments(shownValues.toArray(new String[0]), shownLabels.toArray(new CharSequence[0]));
        pill.setEnabled(active != null);
        boolean gpu = shownValues.contains("gpu");
        pill.setSummary(gpu ? fragment.getString(R.string.speech_model_accelerator_hint) : null);
    }

    // ---- idle unload ----

    /** Picks how long an idle speech model stays loaded; 0 keeps it until memory pressure evicts it. */
    private void showIdleUnloadDialog(@NonNull Context context) {
        String[] labels = fragment.getResources().getStringArray(R.array.termux_ai_stt_idle_unload_entries);
        String[] values = fragment.getResources().getStringArray(R.array.termux_ai_stt_idle_unload_values);
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
    private String idleUnloadLabel(int minutes) {
        String[] labels = fragment.getResources().getStringArray(R.array.termux_ai_stt_idle_unload_entries);
        String[] values = fragment.getResources().getStringArray(R.array.termux_ai_stt_idle_unload_values);
        String label = minutes <= 0 ? fragment.getString(R.string.termux_ai_disabled) : minutes + " min";
        for (int i = 0; i < values.length && i < labels.length; i++) {
            if (values[i].equals(String.valueOf(minutes))) label = labels[i];
        }
        return label;
    }
}
