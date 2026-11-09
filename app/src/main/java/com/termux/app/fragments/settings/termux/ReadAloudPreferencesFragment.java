package com.termux.app.fragments.settings.termux;

import android.content.Context;
import android.os.Bundle;

import androidx.annotation.Keep;
import androidx.annotation.NonNull;
import androidx.preference.Preference;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.termux.R;
import com.termux.ai.TaiModelStore;
import com.termux.ai.TaiReadAloud;
import com.termux.ai.TaiSettings;
import com.termux.ai.TaiTtsModels;
import com.termux.ai.TaiTtsVoices;
import com.termux.app.fragments.settings.MaterialPreferenceFragment;
import com.termux.app.fragments.settings.SettingsLayoutUtils;
import com.termux.app.notice.AppNotice;

/**
 * On-device AI > Read aloud: the voice and speed Read aloud, tai speak and the API use when a
 * request names none, and a sample to hear them. The model itself comes from the Model centre's
 * Speech segment, under Voice output.
 */
@Keep
public class ReadAloudPreferencesFragment extends MaterialPreferenceFragment {
    private static final String KEY_OUTPUT_MISSING = "speech_output_missing";
    private static final String KEY_OUTPUT_VOICE = "speech_output_voice";
    private static final String KEY_OUTPUT_SPEED = "speech_output_speed";
    private static final String KEY_OUTPUT_SAMPLE = "speech_output_sample";
    /** The speeds offered: a few steps either side of the voice's own pace, inside what stays clear. */
    private static final float[] OUTPUT_SPEEDS = {0.75f, 0.9f, 1.0f, 1.15f, 1.3f, 1.5f, 1.75f};

    /** Whether the sample this screen started is still playing, so the row can offer to stop it. */
    private boolean samplePlaying;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        Context context = getContext();
        if (context == null) return;
        setPreferencesFromResource(R.xml.read_aloud_preferences, rootKey);
        SettingsLayoutUtils.applyScreenLayout(this);
        bindVoiceOutput(context);
        refresh(context);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getActivity() != null) getActivity().setTitle(R.string.read_aloud_title);
        Context context = getContext();
        if (context != null) refresh(context);
    }

    /** Redraws the rows from the stored voice and speed and whether a model is installed. */
    void refresh(@NonNull Context context) {
        refreshVoiceOutput(context, new TaiModelStore(context), new TaiSettings(context));
    }

    // ---- voice output ----

    private void bindVoiceOutput(@NonNull Context context) {
        Preference missing = findPreference(KEY_OUTPUT_MISSING);
        if (missing != null) missing.setOnPreferenceClickListener(preference -> {
            TaiModelCentreFragment.open(getActivity(), TaiModelCentreFragment.SEGMENT_SPEECH);
            return true;
        });
        Preference voice = findPreference(KEY_OUTPUT_VOICE);
        if (voice != null) voice.setOnPreferenceClickListener(preference -> {
            showVoiceDialog(context);
            return true;
        });
        Preference speed = findPreference(KEY_OUTPUT_SPEED);
        if (speed != null) speed.setOnPreferenceClickListener(preference -> {
            showSpeedDialog(context);
            return true;
        });
        Preference sample = findPreference(KEY_OUTPUT_SAMPLE);
        if (sample != null) sample.setOnPreferenceClickListener(preference -> {
            toggleSample(context);
            return true;
        });
    }

    private void refreshVoiceOutput(@NonNull Context context, @NonNull TaiModelStore store, @NonNull TaiSettings settings) {
        boolean installed = TaiTtsModels.resolveActive(context, store) != null;
        Preference missing = findPreference(KEY_OUTPUT_MISSING);
        if (missing != null) missing.setVisible(!installed);
        Preference voice = findPreference(KEY_OUTPUT_VOICE);
        if (voice != null) voice.setSummary(settings.getTtsVoice());
        Preference speed = findPreference(KEY_OUTPUT_SPEED);
        if (speed != null) speed.setSummary(getString(R.string.speech_output_speed_summary, speedLabel(settings.getTtsSpeed())));
        Preference sample = findPreference(KEY_OUTPUT_SAMPLE);
        if (sample != null) {
            sample.setEnabled(installed);
            sample.setSummary(samplePlaying ? R.string.speech_output_sample_playing : R.string.speech_output_sample_summary);
        }
    }

    private void showVoiceDialog(@NonNull Context context) {
        TaiSettings settings = new TaiSettings(context);
        String current = settings.getTtsVoice();
        int checked = 0;
        for (int i = 0; i < TaiTtsVoices.VOICES.length; i++) {
            if (TaiTtsVoices.VOICES[i].equals(current)) checked = i;
        }
        new MaterialAlertDialogBuilder(context)
            .setTitle(R.string.speech_output_voice_title)
            .setSingleChoiceItems(TaiTtsVoices.VOICES, checked, (dialog, which) -> {
                settings.setTtsVoice(TaiTtsVoices.VOICES[which]);
                refresh(context);
                dialog.dismiss();
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    private void showSpeedDialog(@NonNull Context context) {
        TaiSettings settings = new TaiSettings(context);
        float current = settings.getTtsSpeed();
        String[] labels = new String[OUTPUT_SPEEDS.length];
        int checked = 0;
        for (int i = 0; i < OUTPUT_SPEEDS.length; i++) {
            labels[i] = speedLabel(OUTPUT_SPEEDS[i]) + "×";
            if (Math.abs(OUTPUT_SPEEDS[i] - current) < Math.abs(OUTPUT_SPEEDS[checked] - current)) checked = i;
        }
        new MaterialAlertDialogBuilder(context)
            .setTitle(R.string.speech_output_speed_title)
            .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                settings.setTtsSpeed(OUTPUT_SPEEDS[which]);
                refresh(context);
                dialog.dismiss();
            })
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    /** Plays the sample in the chosen voice and speed, or stops the one playing. */
    private void toggleSample(@NonNull Context context) {
        if (samplePlaying) {
            TaiReadAloud.stop(context);
            return;
        }
        TaiSettings settings = new TaiSettings(context);
        String voice = settings.getTtsVoice();
        samplePlaying = true;
        refresh(context);
        TaiReadAloud.speak(context, getString(R.string.speech_output_sample_text, voice), voice, settings.getTtsSpeed(), error -> {
            samplePlaying = false;
            Context current = getContext();
            if (current == null || !isAdded()) return;
            if (error != null) AppNotice.show(current, error, true);
            refresh(current);
        });
    }

    @NonNull
    private static String speedLabel(float speed) {
        String text = String.format(java.util.Locale.US, "%.2f", speed);
        while (text.endsWith("0")) text = text.substring(0, text.length() - 1);
        return text.endsWith(".") ? text.substring(0, text.length() - 1) : text;
    }
}
