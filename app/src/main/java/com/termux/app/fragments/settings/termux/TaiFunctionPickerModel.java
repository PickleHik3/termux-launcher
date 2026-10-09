package com.termux.app.fragments.settings.termux;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.ai.TaiEvidence;
import com.termux.ai.TaiFeaturePlan;
import com.termux.ai.TaiFunction;
import com.termux.ai.TaiFunctionModels;
import com.termux.ai.TaiFunctionModels.ModelInfo;
import com.termux.ai.TaiFunctionModels.Resolution;
import com.termux.ai.TaiModelSpec;
import com.termux.ai.TaiResidency;
import com.termux.ai.TaiTierPolicy;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * What the function picker sheet lists for one function (tai-device-tiers spec §4.3): Automatic,
 * the models on the phone, Remote, the without-a-model choice and the catalogue downloads, with
 * the current choice marked, the GPU or CPU choice for the chat functions and the fallback chain,
 * and on top how the function runs now: its {@link TaiFeaturePlan} in one line, with why.
 * Pure: the sheet only draws it, and a tap writes the entry's {@link Entry#value}.
 */
final class TaiFunctionPickerModel {
    private TaiFunctionPickerModel() {}

    /**
     * Where a GPU or CPU tap is stored. The plan reads the function's pick only when the model itself
     * was picked; with Automatic it reads the model file's Parameters value, so that is where the tap goes.
     * {@link #modelId} is {@code null} for the function pick.
     */
    static final class AcceleratorWrite {
        @Nullable final String modelId;
        /** The value to store: the pick as is, or the Parameters option that backend names the choice. */
        @NonNull final String value;

        AcceleratorWrite(@Nullable String modelId, @NonNull String value) {
            this.modelId = modelId;
            this.value = value;
        }
    }

    @NonNull
    static AcceleratorWrite acceleratorWrite(@NonNull Resolution resolution, @NonNull String accelerator) {
        if (resolution.source == TaiFunctionModels.Source.PICK || resolution.modelId == null || resolution.info == null) {
            return new AcceleratorWrite(null, accelerator);
        }
        boolean gpu = TaiTierPolicy.ACCEL_GPU.equals(accelerator);
        // MNN's option names its GPU OpenCL; LiteRT-LM's is GPU. Both name the CPU CPU.
        String value = !gpu ? "CPU" : TaiModelSpec.BACKEND_MNN_LLM.equals(resolution.info.backend) ? "OpenCL" : "GPU";
        return new AcceleratorWrite(resolution.modelId, value);
    }

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

    /**
     * The feature load plan in one line (decision 10): "Gemma 4 E2B · GPU · 1.6× faster" and why,
     * plus the one-tap offer of a setup measured faster than the user's pick.
     */
    static final class PlanLine {
        @NonNull final String line;
        /** "Your choice", "Measured on this phone", "Suggested for this phone" or "Remote · your provider". */
        @NonNull final String reason;
        /** "Use GPU: 2.5× faster on this phone", or null when nothing measured faster than the pick. */
        @Nullable final String offer;
        /** The accelerator the offer sets ({@code gpu} or {@code cpu}); null without an offer. */
        @Nullable final String offerAccelerator;

        PlanLine(@NonNull String line, @NonNull String reason, @Nullable String offer, @Nullable String offerAccelerator) {
            this.line = line;
            this.reason = reason;
            this.offer = offer;
            this.offerAccelerator = offerAccelerator;
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
        /** How the function runs now, from its feature load plan. */
        @NonNull final PlanLine plan;

        Model(@NonNull TaiFunction function, @NonNull Map<Section, List<Entry>> sections,
              @Nullable Accelerator accelerator, @NonNull String chainLine, @NonNull PlanLine plan) {
            this.function = function;
            this.sections = sections;
            this.accelerator = accelerator;
            this.chainLine = chainLine;
            this.plan = plan;
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
        TaiFeaturePlan plan = TaiFeaturePlan.of(function, models, TaiEvidence.NONE,
            Collections.<TaiResidency.Entry>emptyList(), 0L, false);
        return build(function, models, remote, catalogue, busy, labels, plan);
    }

    /** As above, with the function's feature load plan as this phone's evidence has it. */
    @NonNull
    static Model build(@NonNull TaiFunction function, @NonNull TaiFunctionModels models,
                       @NonNull TaiFunctionModels.Remote remote, @NonNull Collection<TaiFunctionRows.CatalogItem> catalogue,
                       @NonNull Set<String> busy, @NonNull TaiFunctionRows.Labels labels, @NonNull TaiFeaturePlan plan) {
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
            // The control shows what a load uses: the plan's accelerator (the pick, a Parameters value,
            // a measurement or the GPU verdict), not the tier's default.
            boolean gpu = gpuOffered && TaiTierPolicy.ACCEL_GPU.equals(plan.accelerator);
            accelerator = new Accelerator(gpuOffered, gpu, gpuOffered ? TaiTierPolicy.gpuNote(env) : null);
        }
        return new Model(function, sections, accelerator, TaiFunctionRows.chainLine(resolution.chain, labels),
            planLine(plan, labels));
    }

    /**
     * The plan as one line and its reason (decision 10): the model, where it runs and the speed-up when
     * measured; "Remote · gpt-x"; or what the function does without a model.
     */
    @NonNull
    static PlanLine planLine(@NonNull TaiFeaturePlan plan, @NonNull TaiFunctionRows.Labels labels) {
        List<String> parts = new ArrayList<>();
        String model = plan.requestModel();
        if (plan.isRemote() && model != null) {
            parts.add(labels.text(TaiFunctionRows.Msg.REMOTE, model.substring(TaiFunctionModels.REMOTE_PREFIX.length())));
        } else if (plan.where == TaiFeaturePlan.Where.ON_DEVICE && model != null) {
            parts.add(labels.modelName(model));
            if (TaiTierPolicy.ACCEL_GPU.equals(plan.accelerator)) parts.add(labels.text(TaiFunctionRows.Msg.GPU));
            else if (TaiTierPolicy.ACCEL_CPU.equals(plan.accelerator)) parts.add(labels.text(TaiFunctionRows.Msg.CPU));
            if (plan.speedup > 0.0) {
                parts.add(labels.text(TaiFunctionRows.Msg.PLAN_FASTER, TaiFunctionRows.ratio(plan.speedup)));
            }
        } else {
            TaiFunctionRows.Msg without = withoutMessage(plan.without);
            parts.add(labels.text(without == null ? TaiFunctionRows.Msg.NOT_SET : without));
        }

        String offer = null;
        String offerAccelerator = null;
        if (plan.faster != null) {
            offerAccelerator = plan.faster.accelerator;
            String where = labels.text(TaiTierPolicy.ACCEL_GPU.equals(offerAccelerator)
                ? TaiFunctionRows.Msg.GPU : TaiFunctionRows.Msg.CPU);
            offer = labels.text(TaiFunctionRows.Msg.PLAN_OFFER, where, TaiFunctionRows.ratio(plan.faster.ratio));
        }
        return new PlanLine(TaiFunctionRows.join(parts, " · "), TaiFunctionRows.reason(plan, labels), offer, offerAccelerator);
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
