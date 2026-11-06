package com.termux.app.fragments.settings.termux;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.termux.R;
import com.termux.ai.TaiFeaturePlans;
import com.termux.ai.TaiFunction;
import com.termux.ai.TaiFunctionModels;
import com.termux.ai.TaiModelSpec;
import com.termux.ai.TaiModelStore;
import com.termux.ai.TaiRemoteProvider;
import com.termux.ai.TaiSettings;
import com.termux.app.fragments.settings.SegmentedPillPreference;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * The Voice input page's Dictation cleanup rows: the model that cleans up dictation (a tap picks
 * from a short list or opens the Model centre) and where it runs (CPU or GPU). The sheet in
 * {@link TaiFunctionPickerSheet} stays the picker everywhere else; this is its short form for the
 * one function, over the same {@link TaiFunctionPickerModel}. Models and plans are read off the
 * main thread, so a redraw ({@link #refresh}) lands a moment after the call.
 */
final class VoiceCleanupRows {
    static final String KEY_MODEL = "keyboard_voice_polish_model";
    static final String KEY_ACCELERATOR = "keyboard_voice_polish_accelerator";

    private static final Executor WORKER = Executors.newSingleThreadExecutor();
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final TaiFunction FUNCTION = TaiFunction.TIDY_DICTATION;

    private final PreferenceFragmentCompat fragment;
    @Nullable private VoiceCleanupChoices.Result choices;

    VoiceCleanupRows(@NonNull PreferenceFragmentCompat fragment) {
        this.fragment = fragment;
    }

    /** Wires the clicks; the page calls {@link #refresh} now and on every resume. */
    void bind(@NonNull Context context) {
        Preference model = fragment.findPreference(KEY_MODEL);
        if (model != null) model.setOnPreferenceClickListener(preference -> {
            showPicker(context);
            return true;
        });
        SegmentedPillPreference accelerator = fragment.findPreference(KEY_ACCELERATOR);
        if (accelerator != null) accelerator.setOnPreferenceChangeListener((preference, newValue) -> {
            applyAccelerator(context, String.valueOf(newValue));
            return true;
        });
        refresh(context);
    }

    /** Reads the pick and the plan off the main thread, then redraws the two rows. */
    void refresh(@NonNull Context context) {
        final Context app = context.getApplicationContext();
        WORKER.execute(() -> {
            Loaded loaded = read(app);
            MAIN.post(() -> {
                if (!fragment.isAdded()) return;
                apply(loaded);
            });
        });
    }

    private static final class Loaded {
        VoiceCleanupChoices.Result choices;
        List<String> acceleratorValues;
        String acceleratorShown;
    }

    @NonNull
    private Loaded read(@NonNull Context app) {
        TaiFunctionModels models = TaiFunctionModels.forContext(app);
        Loaded loaded = new Loaded();
        loaded.choices = VoiceCleanupChoices.of(models, remoteOf(app), labelsOf(app), app.getString(R.string.cleanup_model_automatic_with));
        loaded.acceleratorValues = VoiceCleanupChoices.acceleratorValues(models.env());
        loaded.acceleratorShown = VoiceCleanupChoices.shownAccelerator(
            TaiFeaturePlans.forContext(app, models).plan(FUNCTION).accelerator, models.env());
        return loaded;
    }

    private void apply(@NonNull Loaded loaded) {
        choices = loaded.choices;
        Preference model = fragment.findPreference(KEY_MODEL);
        if (model != null && !loaded.choices.summary.contentEquals(String.valueOf(model.getSummary()))) {
            model.setSummary(loaded.choices.summary);
        }
        SegmentedPillPreference pill = fragment.findPreference(KEY_ACCELERATOR);
        if (pill == null) return;
        boolean shown = !loaded.acceleratorValues.isEmpty() && !loaded.choices.runsElsewhere;
        if (pill.isVisible() != shown) pill.setVisible(shown);
        if (!shown) return;
        String[] values = loaded.acceleratorValues.toArray(new String[0]);
        CharSequence[] labels = new CharSequence[values.length];
        for (int i = 0; i < values.length; i++) {
            labels[i] = fragment.getString(VoiceCleanupChoices.ACCEL_GPU.equals(values[i]) ? R.string.tai_fn_gpu : R.string.tai_fn_cpu);
        }
        // Segments are re-offered only when the set changed (a pill redraws on every swap).
        if (pill.segmentCount() != values.length) pill.setSegments(values, labels);
        if (!loaded.acceleratorShown.equals(pill.getValue())) pill.setValue(loaded.acceleratorShown);
    }

    // ---- model ----

    /** The short list, the pick checked, and "Get more models" for the Model centre. */
    private void showPicker(@NonNull Context context) {
        VoiceCleanupChoices.Result current = choices;
        if (current == null) return;
        List<VoiceCleanupChoices.Option> options = current.options;
        String[] labels = new String[options.size()];
        int checked = -1;
        for (int i = 0; i < labels.length; i++) {
            labels[i] = options.get(i).title;
            if (options.get(i).selected) checked = i;
        }
        final Context app = context.getApplicationContext();
        final int was = checked;
        new MaterialAlertDialogBuilder(context)
            .setTitle(R.string.settings_keyboard_voice_polish_model_title)
            .setSingleChoiceItems(labels, checked, (dialog, which) -> {
                if (which != was) {
                    String value = options.get(which).value;
                    WORKER.execute(() -> {
                        TaiFunctionModels.forContext(app).set(FUNCTION, value);
                        refresh(app);
                    });
                }
                dialog.dismiss();
            })
            .setNeutralButton(R.string.speech_model_get_more_models, (dialog, which) ->
                TaiModelCentreFragment.open(fragment.getActivity(), TaiModelCentreFragment.SEGMENT_CHAT))
            .setNegativeButton(android.R.string.cancel, null)
            .show();
    }

    // ---- runs on ----

    /** Stores the tap where the sheet does: the function's own pick for a model pick, else the model's Parameters value. */
    private void applyAccelerator(@NonNull Context context, @NonNull String accelerator) {
        final Context app = context.getApplicationContext();
        WORKER.execute(() -> {
            TaiFunctionModels models = TaiFunctionModels.forContext(app);
            TaiFunctionPickerModel.AcceleratorWrite write =
                TaiFunctionPickerModel.acceleratorWrite(models.resolve(FUNCTION), accelerator);
            if (write.modelId == null) {
                models.setAcceleratorPick(FUNCTION, write.value);
            } else {
                new TaiSettings(app).setModelParameter(write.modelId, TaiSettings.FIELD_ACCELERATOR, write.value);
            }
            refresh(app);
        });
    }

    // ---- reads shared with the sheet ----

    @NonNull
    private static TaiFunctionLabels labelsOf(@NonNull Context app) {
        TaiModelStore store = new TaiModelStore(app);
        Map<String, TaiModelSpec> installed = new LinkedHashMap<>(store.getDownloadedReadableModels());
        installed.putAll(store.getInstalledUserModels());
        return new TaiFunctionLabels(app, installed);
    }

    @NonNull
    private static TaiFunctionModels.Remote remoteOf(@NonNull Context app) {
        final TaiRemoteProvider provider = new TaiRemoteProvider(app);
        return new TaiFunctionModels.Remote() {
            @Override public boolean configured() { return provider.isConfigured(); }
            @NonNull @Override public String modelId() { return provider.modelId(); }
            @Override public boolean understandsImages() { return provider.understandsImages(); }
            @Override public boolean prefersRemote() { return provider.prefersRemote(); }
        };
    }
}
