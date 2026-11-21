package com.termux.app.fragments.settings.termux;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.ai.TaiFunction;
import com.termux.ai.TaiFunctionModels;
import com.termux.ai.TaiTierPolicy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * What the Voice input page's Cleanup model row offers for dictation cleanup
 * ({@link TaiFunction#TIDY_DICTATION}): a short list of Automatic, the installed models that can
 * serve it and the remote model when one is set up, with the current pick marked, and where the
 * model can run. It reads the picker sheet's own candidates ({@link TaiFunctionPickerModel}), so
 * both agree on which models qualify. "Raw text" is the cleanup switch being off, so it is never
 * listed; a stored "off" is only named in the summary. Pure: the rows only draw it.
 */
final class VoiceCleanupChoices {
    private VoiceCleanupChoices() {}

    static final String ACCEL_CPU = TaiTierPolicy.ACCEL_CPU;
    static final String ACCEL_GPU = TaiTierPolicy.ACCEL_GPU;

    /** One line of the list; {@link #value} is what {@link TaiFunctionModels#set} stores. */
    static final class Option {
        @NonNull final String value;
        @NonNull final String title;
        final boolean selected;

        Option(@NonNull String value, @NonNull String title, boolean selected) {
            this.value = value;
            this.title = title;
            this.selected = selected;
        }
    }

    static final class Result {
        @NonNull final List<Option> options;
        /** The row's one fact: the pick's title, or "Raw text" while the stored pick is off. */
        @NonNull final String summary;
        /** The pick is the remote model or off: nothing runs on this phone, so "Runs on" has no say. */
        final boolean runsElsewhere;

        Result(@NonNull List<Option> options, @NonNull String summary, boolean runsElsewhere) {
            this.options = options;
            this.summary = summary;
            this.runsElsewhere = runsElsewhere;
        }
    }

    /**
     * @param automaticFormat the "Automatic · %1$s" string
     */
    @NonNull
    static Result of(@NonNull TaiFunctionModels models, @NonNull TaiFunctionModels.Remote remote,
                     @NonNull TaiFunctionRows.Labels labels, @NonNull String automaticFormat) {
        TaiFunctionPickerModel.Model model = TaiFunctionPickerModel.build(TaiFunction.TIDY_DICTATION, models, remote,
            Collections.<TaiFunctionRows.CatalogItem>emptyList(), Collections.<String>emptySet(), labels);
        List<Option> options = new ArrayList<>();
        String summary = "";
        TaiFunctionPickerModel.Entry auto = model.entries(TaiFunctionPickerModel.Section.AUTOMATIC).get(0);
        options.add(new Option("", String.format(automaticFormat, automaticName(models, remote, labels)), auto.selected));
        if (auto.selected) summary = options.get(0).title;
        for (TaiFunctionPickerModel.Entry entry : model.entries(TaiFunctionPickerModel.Section.ON_PHONE)) {
            options.add(new Option(entry.value, entry.title, entry.selected));
            if (entry.selected) summary = entry.title;
        }
        for (TaiFunctionPickerModel.Entry entry : model.entries(TaiFunctionPickerModel.Section.REMOTE)) {
            if (entry.setupLink) continue;
            options.add(new Option(entry.value, entry.title, entry.selected));
            if (entry.selected) summary = entry.title;
        }
        boolean off = false;
        for (TaiFunctionPickerModel.Entry entry : model.entries(TaiFunctionPickerModel.Section.WITHOUT)) {
            if (entry.selected) {
                off = true;
                summary = entry.title;
            }
        }
        boolean remotePick = false;
        for (Option option : options) remotePick |= option.selected && TaiFunctionModels.isRemote(option.value);
        return new Result(options, summary, off || remotePick);
    }

    /** What Automatic would use: the remote model when preferred and usable, else the tier's model or its model-free mode. */
    @NonNull
    private static String automaticName(@NonNull TaiFunctionModels models, @NonNull TaiFunctionModels.Remote remote,
                                        @NonNull TaiFunctionRows.Labels labels) {
        TaiFunction function = TaiFunction.TIDY_DICTATION;
        boolean remoteUsable = remote.configured() && !remote.modelId().isEmpty();
        if (remote.prefersRemote() && remoteUsable && TaiFunctionModels.remoteAllowed(function, remote.understandsImages())) {
            return labels.text(TaiFunctionRows.Msg.REMOTE, remote.modelId());
        }
        TaiTierPolicy.Choice choice = TaiTierPolicy.automatic(models.env(), function);
        if (choice.isModel()) return labels.modelName(choice.modelId);
        switch (choice.without) {
            case RAW_TEXT: return labels.text(TaiFunctionRows.Msg.RAW_TEXT);
            case RULES_ONLY: return labels.text(TaiFunctionRows.Msg.RULES_ONLY);
            case OFF: return labels.text(TaiFunctionRows.Msg.OFF);
            default: return labels.text(TaiFunctionRows.Msg.NOT_SET);
        }
    }

    /**
     * The segments "Runs on" offers: CPU and GPU where the phone has a GPU path (the sheet's own
     * test), nothing otherwise, as the CPU would be the only choice. The stored pick has no Auto
     * value to offer: an empty one means the tier's default, which the pill shows as what it is.
     */
    @NonNull
    static List<String> acceleratorValues(@NonNull TaiTierPolicy.Env env) {
        List<String> values = new ArrayList<>(2);
        if (TaiTierPolicy.gpuOffered(env)) {
            values.add(ACCEL_CPU);
            values.add(ACCEL_GPU);
        }
        return values;
    }

    /** The segment to check for a plan's accelerator ({@code gpu} or {@code cpu}), else the tier's default. */
    @NonNull
    static String shownAccelerator(@Nullable String planAccelerator, @NonNull TaiTierPolicy.Env env) {
        if (ACCEL_GPU.equals(planAccelerator) || ACCEL_CPU.equals(planAccelerator)) return planAccelerator;
        return TaiTierPolicy.defaultAccelerator(env);
    }
}
