package com.termux.app.fragments.settings.termux;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.ai.TaiFunction;
import com.termux.ai.TaiFunctionModels;
import com.termux.ai.TaiFunctionModels.ModelInfo;
import com.termux.ai.TaiFunctionModels.Resolution;
import com.termux.ai.TaiTierPolicy;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What the function picker sheet lists for one function (tai-device-tiers spec §4.3): Automatic,
 * the models on the phone, Remote, the without-a-model choice and the catalogue downloads, with
 * the current choice marked, the GPU or CPU choice for the chat functions and the fallback chain.
 * Pure: the sheet only draws it, and a tap writes the entry's {@link Entry#value}.
 */
final class TaiFunctionPickerModel {
    private TaiFunctionPickerModel() {}

    /** The five sections of the sheet, in order. */
    enum Section { AUTOMATIC, ON_PHONE, REMOTE, WITHOUT, GET }

    /** One tappable line. */
    static final class Entry {
        @NonNull final Section section;
        /**
         * What a tap writes with {@link TaiFunctionModels#set}: {@code ""}, a model id, {@code remote/<id>}
         * or {@code off}. For a {@link Section#GET} entry, the catalogue id to download. Empty for the
         * remote set-up link.
         */
        @NonNull final String value;
        @NonNull final String title;
        /** The fit line ("Fits this phone"), or a note; may be empty. */
        @NonNull final String detail;
        final boolean selected;
        /** "May close apps running in the background" belongs beside this model. */
        final boolean warnBackground;
        final boolean suggested;
        /** File size, for a download entry; 0 elsewhere. */
        final long sizeBytes;
        /** The Remote section's "Set up a remote model" link rather than a pick. */
        final boolean setupLink;

        Entry(@NonNull Section section, @NonNull String value, @NonNull String title, @NonNull String detail,
              boolean selected, boolean warnBackground, boolean suggested, long sizeBytes, boolean setupLink) {
            this.section = section;
            this.value = value;
            this.title = title;
            this.detail = detail;
            this.selected = selected;
            this.warnBackground = warnBackground;
            this.suggested = suggested;
            this.sizeBytes = sizeBytes;
            this.setupLink = setupLink;
        }
    }

    /** The GPU or CPU choice of a chat function that runs a local model. */
    static final class Accelerator {
        /** False when the phone has no GPU path at all: only the CPU shows. */
        final boolean gpuOffered;
        final boolean gpuSelected;
        /** "Not confirmed on this GPU yet", "Often fails on this GPU", or null. */
        @Nullable final String note;

        Accelerator(boolean gpuOffered, boolean gpuSelected, @Nullable String note) {
            this.gpuOffered = gpuOffered;
            this.gpuSelected = gpuSelected;
            this.note = note;
        }
    }

    /** The whole sheet body for one function. */
    static final class Model {
        @NonNull final TaiFunction function;
        @NonNull final Map<Section, List<Entry>> sections;
        /** Null unless the current pick is a local chat model. */
        @Nullable final Accelerator accelerator;
        /** "If it can't load: Gemma 4 E2B → rules only", or empty. */
        @NonNull final String chainLine;

        Model(@NonNull TaiFunction function, @NonNull Map<Section, List<Entry>> sections,
              @Nullable Accelerator accelerator, @NonNull String chainLine) {
            this.function = function;
            this.sections = sections;
            this.accelerator = accelerator;
            this.chainLine = chainLine;
        }

        @NonNull
        List<Entry> entries(@NonNull Section section) {
            List<Entry> list = sections.get(section);
            return list == null ? new ArrayList<Entry>() : list;
        }
    }

    /**
     * Builds the sheet for {@code function}. {@code busy} are catalogue ids whose download is already
     * on its way (left out of Get a model); installed ones are left out by themselves.
     */
    @NonNull
    static Model build(@NonNull TaiFunction function, @NonNull TaiFunctionModels models,
                       @NonNull TaiFunctionModels.Remote remote, @NonNull Collection<TaiFunctionRows.CatalogItem> catalogue,
                       @NonNull Set<String> busy, @NonNull TaiFunctionRows.Labels labels) {
        TaiTierPolicy.Env env = models.env();
        Resolution resolution = models.resolve(function);
        boolean pickIsRemote = resolution.modelId != null && TaiFunctionModels.isRemote(resolution.modelId)
            && resolution.source == TaiFunctionModels.Source.PICK;
        boolean pickIsOff = TaiFunctionModels.VALUE_OFF.equals(models.pick(function)) && resolution.modelId == null
            && resolution.source == TaiFunctionModels.Source.PICK;
        boolean pickIsLocal = resolution.modelId != null && !TaiFunctionModels.isRemote(resolution.modelId)
            && resolution.source == TaiFunctionModels.Source.PICK;
        String pickedBase = pickIsLocal ? TaiFunctionRows.stripVision(resolution.modelId) : "";
        boolean automaticSelected = !pickIsRemote && !pickIsOff && !pickIsLocal;

        Map<Section, List<Entry>> sections = new EnumMap<>(Section.class);
        boolean remoteUsable = remote.configured() && !remote.modelId().isEmpty();

        // 1. Automatic, with the tier's choice named.
        sections.put(Section.AUTOMATIC, single(new Entry(Section.AUTOMATIC, "",
            automaticTitle(function, env, remote, remoteUsable, labels), "", automaticSelected, false, false, 0L, false)));

        // 2. On this phone: what is installed and can serve the function.
        List<Entry> onPhone = new ArrayList<>();
        Set<String> skip = new HashSet<>(busy);
        for (ModelInfo info : models.candidates(function)) {
            String base = TaiFunctionRows.stripVision(info.id);
            skip.add(base);
            TaiFunctionRows.Fit fit = TaiFunctionRows.fit(env, base, info.sizeBytes);
            onPhone.add(new Entry(Section.ON_PHONE, base, labels.modelName(base), TaiFunctionRows.fitLine(fit, labels),
                base.equals(pickedBase), TaiTierPolicy.warnsBackground(env, info.sizeBytes), false, info.sizeBytes, false));
        }
        sections.put(Section.ON_PHONE, onPhone);

        // 3. Remote: one entry when set up (and able), else the set-up link; none for the local-only functions.
        List<Entry> remoteEntries = new ArrayList<>();
        if (remoteUsable && TaiFunctionModels.remoteAllowed(function, remote.understandsImages())) {
            remoteEntries.add(new Entry(Section.REMOTE, TaiFunctionModels.REMOTE_PREFIX + remote.modelId(),
                labels.text(TaiFunctionRows.Msg.REMOTE, remote.modelId()), "", pickIsRemote, false, false, 0L, false));
        } else if (!remote.configured() && TaiFunctionModels.remoteAllowed(function, true)) {
            remoteEntries.add(new Entry(Section.REMOTE, "", labels.text(TaiFunctionRows.Msg.REMOTE_SETUP), "",
                false, false, false, 0L, true));
        }
        sections.put(Section.REMOTE, remoteEntries);

        // 4. Without a model, where the function has one.
        List<Entry> without = new ArrayList<>();
        TaiFunctionRows.Msg withoutMsg = withoutMessage(function.withoutModel);
        if (withoutMsg != null) {
            without.add(new Entry(Section.WITHOUT, TaiFunctionModels.VALUE_OFF, labels.text(withoutMsg), "",
                pickIsOff, false, false, 0L, false));
        }
        sections.put(Section.WITHOUT, without);

        // 5. Get a model: compatible catalogue entries that are not here yet.
        List<Entry> get = new ArrayList<>();
        if (TaiTierPolicy.platformAllows(env, function)) {
            for (TaiFunctionRows.CatalogItem item : catalogue) {
                ModelInfo info = item.info;
                if (!item.downloadAvailable || skip.contains(info.id) || !TaiFunctionModels.canServe(function, info)) continue;
                TaiFunctionRows.Fit fit = TaiFunctionRows.fit(env, info.id, info.sizeBytes);
                boolean chat = function.usesChatModel();
                get.add(new Entry(Section.GET, info.id, labels.modelName(info.id), TaiFunctionRows.fitLine(fit, labels),
                    false, TaiTierPolicy.warnsBackground(env, info.sizeBytes),
                    TaiFunctionRows.suggested(env, info.id, chat), info.sizeBytes, false));
            }
        }
        sections.put(Section.GET, get);

        Accelerator accelerator = null;
        if (function.usesChatModel() && pickIsLocal) {
            boolean gpuOffered = TaiTierPolicy.gpuOffered(env);
            String stored = models.acceleratorPick(function);
            String effective = stored.isEmpty() ? TaiTierPolicy.defaultAccelerator(env) : stored;
            boolean gpu = gpuOffered && TaiTierPolicy.ACCEL_GPU.equals(effective);
            accelerator = new Accelerator(gpuOffered, gpu, gpuOffered ? TaiTierPolicy.gpuNote(env) : null);
        }
        return new Model(function, sections, accelerator, TaiFunctionRows.chainLine(resolution.chain, labels));
    }

    @NonNull
    private static String automaticTitle(@NonNull TaiFunction function, @NonNull TaiTierPolicy.Env env,
                                         @NonNull TaiFunctionModels.Remote remote, boolean remoteUsable,
                                         @NonNull TaiFunctionRows.Labels labels) {
        if (remote.prefersRemote() && remoteUsable && TaiFunctionModels.remoteAllowed(function, remote.understandsImages())) {
            return labels.text(TaiFunctionRows.Msg.AUTOMATIC, labels.text(TaiFunctionRows.Msg.REMOTE, remote.modelId()));
        }
        TaiTierPolicy.Choice choice = TaiTierPolicy.automatic(env, function);
        if (choice.isModel()) return labels.text(TaiFunctionRows.Msg.AUTOMATIC, labels.modelName(choice.modelId));
        TaiFunctionRows.Msg without = withoutMessage(choice.without);
        return without == null ? labels.text(TaiFunctionRows.Msg.AUTOMATIC_BARE)
            : labels.text(TaiFunctionRows.Msg.AUTOMATIC, labels.text(without));
    }

    @Nullable
    private static TaiFunctionRows.Msg withoutMessage(@NonNull TaiTierPolicy.WithoutModel without) {
        switch (without) {
            case RULES_ONLY: return TaiFunctionRows.Msg.RULES_ONLY;
            case RAW_TEXT: return TaiFunctionRows.Msg.RAW_TEXT;
            case OFF: return TaiFunctionRows.Msg.OFF;
            default: return null;
        }
    }

    @NonNull
    private static List<Entry> single(@NonNull Entry entry) {
        List<Entry> list = new ArrayList<>();
        list.add(entry);
        return list;
    }
}
