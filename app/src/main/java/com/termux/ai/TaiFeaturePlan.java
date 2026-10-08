package com.termux.ai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * The feature load plan: the one answer to "how does this feature run on this phone"
 * (project-docs/active/tai-feature-load-plan-2026-10-08.md, step 1). On-device or remote, the
 * model, the accelerator, speculative decoding, the context window and how long the model stays
 * loaded, each with the {@link Reason} it was chosen. Requests name their feature and
 * {@code TaiManager} applies the plan ({@link #applyTo}); the Model Centre's picker shows it.
 *
 * <p>Precedence (decision 3): the user's pick, then what this phone measured for this feature,
 * then the tier and GPU verdict default. Only a pick is sent as an explicit accelerator; a default
 * or measured one is sent as the automatic load's preference, so a failure record still demotes it.
 * A pick that measured slower stays, and {@link #faster} names the faster setup for a one-tap offer.
 *
 * <p>Measured means the feature's own workload (decision 11): a feature check of this feature, and
 * for the assistant and Dawn chat the generic chat bench too. Load failures and crashes count for
 * every feature. Pure: the picks, the device, the evidence and the residents are inputs.
 */
public final class TaiFeaturePlan {

    /** Why a value is what it is. */
    public enum Reason {
        /** The user chose it. */
        PICK,
        /** A feature check on this phone, or a failure record, decided it. */
        MEASURED,
        /** The tier or the GPU verdict. */
        DEFAULT,
        /** The remote provider's "When to use it" routing. */
        REMOTE
    }

    /** Where the feature runs. {@code NONE}: no model at all, so it does what {@link #without} says. */
    public enum Where { ON_DEVICE, REMOTE, NONE }

    /** How long the model stays loaded once the feature has used it. */
    public enum Residency {
        /** Until the runtime's idle unload, as every model always did. */
        UNTIL_IDLE,
        /** Until the run that loaded it finishes: app sorting. */
        UNTIL_RUN_ENDS,
        /** Loaded for each reply and closed after it: read aloud when its group does not fit. */
        PER_REPLY
    }

    /** Cleanup's window: the Light and Polished prompts over a long dictation and its answer fit in it. */
    public static final int CLEANUP_WINDOW = 2048;
    /** App sorting's window, the smallest the runtime accepts: one app's prompt and a one-word answer. */
    public static final int SORTING_WINDOW = 1024;
    /** A group member used this recently stays loaded for its group (decision 8). */
    public static final long GROUP_RECENT_MS = TimeUnit.MINUTES.toMillis(2);
    /** Two measurements within this ratio of each other are noise on a phone and decide nothing. */
    static final double MEASURED_MARGIN = 1.10;

    /** The feature groups of decision 8: features that stay loaded together while in use. */
    private static final List<Set<TaiFunction>> GROUPS = Collections.unmodifiableList(Arrays.asList(
        Collections.unmodifiableSet(EnumSet.of(TaiFunction.VOICE_TYPING, TaiFunction.TIDY_DICTATION)),
        Collections.unmodifiableSet(EnumSet.of(TaiFunction.EMBEDDINGS, TaiFunction.DAWN_CHAT)),
        Collections.unmodifiableSet(EnumSet.of(TaiFunction.VOICE_TYPING, TaiFunction.EMBEDDINGS,
            TaiFunction.DAWN_CHAT, TaiFunction.READ_ALOUD))));

    /**
     * The user's own Parameters values for a model file ({@link TaiSettings#storedParameter}): a value
     * the user stored is a pick (decision 3), the model's own over the global one. A schema default the
     * user never set is not stored, and answers {@code null}.
     */
    public interface Parameters {
        /** {@code gpu} or {@code cpu} as the user stored it for this model file; {@code null} when not stored. */
        @Nullable
        String accelerator(@NonNull String modelId, @NonNull String backend);

        /** Speculative decoding as the user stored it for this model file; {@code null} when not stored. */
        @Nullable
        Boolean speculative(@NonNull String modelId, @NonNull String backend);

        /** Nothing stored. */
        Parameters NONE = new Parameters() {
            @Nullable @Override public String accelerator(@NonNull String modelId, @NonNull String backend) { return null; }
            @Nullable @Override public Boolean speculative(@NonNull String modelId, @NonNull String backend) { return null; }
        };
    }

    /** A setup this phone measured faster than the user's pick. */
    public static final class Faster {
        /** {@code gpu} or {@code cpu}. */
        @NonNull public final String accelerator;
        /** How many times faster, e.g. {@code 1.6}. */
        public final double ratio;

        Faster(@NonNull String accelerator, double ratio) {
            this.accelerator = accelerator;
            this.ratio = ratio;
        }
    }

    @NonNull public final TaiFunction feature;
    @NonNull public final Where where;
    @NonNull public final Reason whereReason;
    /** The local model id, or {@code remote/<id>}; {@code null} for {@link Where#NONE}. */
    @Nullable public final String modelId;
    @NonNull public final Reason modelReason;
    /** The local model's backend; {@code null} when nothing loads here. */
    @Nullable public final String backend;
    /** {@code gpu} or {@code cpu} for a local model; {@code null} for a remote or no model. */
    @Nullable public final String accelerator;
    @NonNull public final Reason acceleratorReason;
    /** For a local chat model: whether to ask for speculative decoding; {@code null} where it does not apply. */
    @Nullable public final Boolean speculative;
    @NonNull public final Reason speculativeReason;
    /** The context window in tokens; {@code 0} is the automatic window ({@link TaiContextWindowPolicy}). */
    public final int contextWindow;
    @NonNull public final Reason windowReason;
    @NonNull public final Residency residency;
    @NonNull public final Reason residencyReason;
    /** What the feature does with no model; {@code NONE} when it has one. */
    @NonNull public final TaiTierPolicy.WithoutModel without;
    /** The routing's fallback, {@code remote/<id>}, for when the local model cannot load; {@code null} without one. */
    @Nullable public final String remoteFallback;
    /** The model file is a quarter of the RAM class or more: "May close apps running in the background". */
    public final boolean warnBackground;
    /** How much faster this accelerator measured than the other, e.g. {@code 1.6}; {@code 0} when not measured. */
    public final double speedup;
    /** A faster setup than the user's pick, when this phone measured one; {@code null} otherwise. */
    @Nullable public final Faster faster;

    private TaiFeaturePlan(@NonNull TaiFunction feature, @NonNull Where where, @NonNull Reason whereReason,
                           @Nullable String modelId, @NonNull Reason modelReason, @Nullable String backend,
                           @Nullable String accelerator, @NonNull Reason acceleratorReason,
                           @Nullable Boolean speculative, @NonNull Reason speculativeReason,
                           int contextWindow, @NonNull Reason windowReason,
                           @NonNull Residency residency, @NonNull Reason residencyReason,
                           @NonNull TaiTierPolicy.WithoutModel without, @Nullable String remoteFallback,
                           boolean warnBackground, double speedup, @Nullable Faster faster) {
        this.feature = feature;
        this.where = where;
        this.whereReason = whereReason;
        this.modelId = modelId;
        this.modelReason = modelReason;
        this.backend = backend;
        this.accelerator = accelerator;
        this.acceleratorReason = acceleratorReason;
        this.speculative = speculative;
        this.speculativeReason = speculativeReason;
        this.contextWindow = contextWindow;
        this.windowReason = windowReason;
        this.residency = residency;
        this.residencyReason = residencyReason;
        this.without = without;
        this.remoteFallback = remoteFallback;
        this.warnBackground = warnBackground;
        this.speedup = speedup;
        this.faster = faster;
    }

    // ------------------------------------------------------------------------------------ plan

    /**
     * The plan for {@code feature}.
     *
     * @param models the user's picks, the device (tier, RAM class, GPU path) and the remote routing
     * @param residents what the runtime holds now; empty where it is not known (the app process)
     * @param memoryTight free memory is under the hold floor, so a group cannot all stay loaded
     */
    @NonNull
    public static TaiFeaturePlan of(@NonNull TaiFunction feature, @NonNull TaiFunctionModels models,
                                    @NonNull TaiEvidence evidence, @NonNull List<TaiResidency.Entry> residents,
                                    long nowMs, boolean memoryTight) {
        return of(feature, models, Parameters.NONE, evidence, residents, nowMs, memoryTight);
    }

    /**
     * As above, with the user's Parameters values: a stored accelerator or speculative decoding is a pick,
     * below the function's own accelerator pick and above anything measured or defaulted. Speculative
     * decoding is the exception: a file that does not declare it, or a check that found it never ran,
     * turns it off whatever is stored.
     */
    @NonNull
    public static TaiFeaturePlan of(@NonNull TaiFunction feature, @NonNull TaiFunctionModels models,
                                    @NonNull Parameters parameters, @NonNull TaiEvidence evidence,
                                    @NonNull List<TaiResidency.Entry> residents, long nowMs, boolean memoryTight) {
        TaiFunctionModels.Resolution resolution = models.resolve(feature);
        Reason modelReason = resolution.source == TaiFunctionModels.Source.PICK ? Reason.PICK : Reason.DEFAULT;
        // Nothing resolved at all reads as "not set", not as the feature's model-less choice.
        TaiTierPolicy.WithoutModel without = resolution.source == TaiFunctionModels.Source.NONE
            ? TaiTierPolicy.WithoutModel.NONE : resolution.without;
        boolean pickedOff = resolution.source == TaiFunctionModels.Source.PICK
            && resolution.modelId == null && !resolution.isRemote();
        // "Only when no local model fits": the provider is the fallback of a local-first routing.
        String routedFallback = models.remote().prefersRemote() || pickedOff ? null : models.remoteModelFor(feature);
        Residency residency = residencyFor(feature, residents, nowMs, memoryTight);

        if (resolution.isRemote()) {
            // A remote pick, or "Prefer remote" turning Automatic into the remote model.
            Reason reason = modelReason == Reason.PICK ? Reason.PICK : Reason.REMOTE;
            return remote(feature, resolution.remoteModel, reason);
        }
        if (resolution.modelId == null || resolution.info == null) {
            if (routedFallback != null) return remote(feature, routedFallback, Reason.REMOTE);
            return new TaiFeaturePlan(feature, Where.NONE, modelReason, null, modelReason, null,
                null, Reason.DEFAULT, null, Reason.DEFAULT, 0, Reason.DEFAULT, residency, Reason.DEFAULT,
                without, null, false, 0.0, null);
        }

        TaiFunctionModels.ModelInfo info = resolution.info;
        if (!feature.usesChatModel()) {
            // Voice typing, read aloud and Dawn search run their models on the CPU, always.
            return new TaiFeaturePlan(feature, Where.ON_DEVICE, Reason.DEFAULT, info.id, modelReason, info.backend,
                TaiTierPolicy.ACCEL_CPU, Reason.DEFAULT, null, Reason.DEFAULT, 0, Reason.DEFAULT,
                residency, Reason.DEFAULT, TaiTierPolicy.WithoutModel.NONE, null, resolution.warnBackground, 0.0, null);
        }
        TaiPlatformCaps.GpuPath path = TaiGpuVerdict.apply(models.env().gpuPath, evidence.gpuVerdict());

        String pick = resolution.source == TaiFunctionModels.Source.PICK ? models.acceleratorPick(feature) : "";
        if (pick.isEmpty()) {
            String stored = parameters.accelerator(info.id, info.backend);
            if (TaiTierPolicy.ACCEL_GPU.equals(stored) || TaiTierPolicy.ACCEL_CPU.equals(stored)) pick = stored;
        }
        Map<String, Double> measured = comparableSpeeds(feature, info, evidence);
        Map<String, Double> plain = new HashMap<>(measured);
        // A measured GPU never promotes a GPU whose verdict is that it answers wrongly.
        if (path == TaiPlatformCaps.GpuPath.CPU_FIRST || path == TaiPlatformCaps.GpuPath.NO) {
            plain.remove(TaiTierPolicy.ACCEL_GPU);
        }
        String accelerator;
        Reason acceleratorReason;
        if (!pick.isEmpty()) {
            boolean gpuGone = TaiTierPolicy.ACCEL_GPU.equals(pick) && path == TaiPlatformCaps.GpuPath.NO;
            accelerator = gpuGone ? TaiTierPolicy.ACCEL_CPU : pick;
            acceleratorReason = gpuGone ? Reason.DEFAULT : Reason.PICK;
        } else {
            boolean gpuAllowed = path == TaiPlatformCaps.GpuPath.YES || path == TaiPlatformCaps.GpuPath.UNKNOWN;
            String fallback = gpuAllowed ? TaiTierPolicy.ACCEL_GPU : TaiTierPolicy.ACCEL_CPU;
            // CPU-first (a failed verdict) or no GPU: the plan names the CPU, even when the CPU has failed
            // here; the preflight still moves a load off a failed accelerator, and the plan does not call
            // that measured.
            List<String> usable = new ArrayList<>();
            for (String option : gpuAllowed ? new String[] {fallback, other(fallback)} : new String[] {TaiTierPolicy.ACCEL_CPU}) {
                if (!evidence.failed(info.id, info.backend, option)) usable.add(option);
            }
            String fastest = fastest(plain, usable);
            if (fastest != null) {
                accelerator = fastest;
                acceleratorReason = Reason.MEASURED;
            } else if (!usable.isEmpty() && !usable.contains(fallback)) {
                // The default failed to load or crashed on this phone; the other one did not.
                accelerator = usable.get(0);
                acceleratorReason = Reason.MEASURED;
            } else {
                accelerator = fallback;
                acceleratorReason = Reason.DEFAULT;
            }
        }

        Boolean speculative;
        Reason speculativeReason;
        Boolean storedSpeculative = parameters.speculative(info.id, info.backend);
        // Never asked for on a file that does not declare it, or where a check found it never ran
        // (decision 4.2), whatever the user stored: only then is a stored value a pick.
        if (!info.speculative) {
            speculative = Boolean.FALSE;
            speculativeReason = Reason.DEFAULT;
        } else if (neverRan(feature, info, evidence, accelerator)) {
            speculative = Boolean.FALSE;
            speculativeReason = Reason.MEASURED;
        } else if (storedSpeculative != null) {
            speculative = storedSpeculative;
            speculativeReason = Reason.PICK;
        } else {
            Double on = decodeSpeeds(feature, info, evidence, true).get(accelerator);
            Double off = decodeSpeeds(feature, info, evidence, false).get(accelerator);
            if (on != null && off != null && off / on >= MEASURED_MARGIN) {
                // On unless measured slower (decision 4), by the same margin as the accelerator;
                // judged on decode speed, never first token.
                speculative = Boolean.FALSE;
                speculativeReason = Reason.MEASURED;
            } else if (on != null && off != null && on / off >= MEASURED_MARGIN) {
                speculative = Boolean.TRUE;
                speculativeReason = Reason.MEASURED;
            } else {
                // Not measured, or within noise: on, as the default would have it.
                speculative = Boolean.TRUE;
                speculativeReason = Reason.DEFAULT;
            }
        }

        double speedup = 0.0;
        Faster faster = null;
        Double mine = plain.get(accelerator);
        Double theirs = plain.get(other(accelerator));
        if (mine != null && theirs != null && mine > 0.0 && theirs > 0.0) {
            if (mine / theirs >= MEASURED_MARGIN) speedup = mine / theirs;
            if (acceleratorReason == Reason.PICK && theirs / mine >= MEASURED_MARGIN
                    && !evidence.failed(info.id, info.backend, other(accelerator))) {
                faster = new Faster(other(accelerator), theirs / mine);
            }
        }

        return new TaiFeaturePlan(feature, Where.ON_DEVICE, Reason.DEFAULT, info.id, modelReason, info.backend,
            accelerator, acceleratorReason, speculative, speculativeReason, windowFor(feature), Reason.DEFAULT,
            residency, Reason.DEFAULT, TaiTierPolicy.WithoutModel.NONE, routedFallback, resolution.warnBackground,
            speedup, faster);
    }

    @NonNull
    private static TaiFeaturePlan remote(@NonNull TaiFunction feature, @NonNull String remoteModel, @NonNull Reason reason) {
        return new TaiFeaturePlan(feature, Where.REMOTE, reason, remoteModel, reason, null, null, Reason.REMOTE,
            null, Reason.REMOTE, 0, Reason.REMOTE, Residency.UNTIL_IDLE, Reason.REMOTE,
            TaiTierPolicy.WithoutModel.NONE, null, false, 0.0, null);
    }

    /** The feature's window (decision 5): cleanup 2048, app sorting 1024, everything else automatic ({@code 0}). */
    public static int windowFor(@NonNull TaiFunction feature) {
        switch (feature) {
            case TIDY_DICTATION: return CLEANUP_WINDOW;
            case APP_CATEGORIES: return SORTING_WINDOW;
            default: return 0;
        }
    }

    @NonNull
    private static Residency residencyFor(@NonNull TaiFunction feature, @NonNull List<TaiResidency.Entry> residents,
                                          long nowMs, boolean memoryTight) {
        if (feature == TaiFunction.APP_CATEGORIES) return Residency.UNTIL_RUN_ENDS;
        if (feature == TaiFunction.READ_ALOUD) return readAloudResidency(residents, nowMs, memoryTight);
        return Residency.UNTIL_IDLE;
    }

    /**
     * Read aloud's residency (decision 8, the second yield): when free memory is under the hold floor and
     * another member of a group read aloud belongs to is in use, it loads per reply instead of staying.
     * The chat window, which yields first, is the memory budget's own ladder.
     */
    @NonNull
    public static Residency readAloudResidency(@NonNull List<TaiResidency.Entry> residents, long nowMs, boolean memoryTight) {
        if (!memoryTight) return Residency.UNTIL_IDLE;
        for (TaiResidency.Entry entry : residents) {
            TaiFunction member = featureOf(entry);
            if (member == null || member == TaiFunction.READ_ALOUD) continue;
            if (nowMs - entry.lastUsedMs >= GROUP_RECENT_MS && !entry.busy) continue;
            for (Set<TaiFunction> group : groupsOf(TaiFunction.READ_ALOUD)) {
                if (group.contains(member)) return Residency.PER_REPLY;
            }
        }
        return Residency.UNTIL_IDLE;
    }

    // --------------------------------------------------------------------------------- groups

    /** The feature groups {@code feature} belongs to; empty for one that belongs to none. */
    @NonNull
    public static List<Set<TaiFunction>> groupsOf(@NonNull TaiFunction feature) {
        List<Set<TaiFunction>> out = new ArrayList<>();
        for (Set<TaiFunction> group : GROUPS) {
            if (group.contains(feature)) out.add(group);
        }
        return out;
    }

    /**
     * The feature a resident serves: the one its last featured request named for a chat model, and
     * the kind's own feature for speech, voice and embedding models. {@code null} when unknown.
     */
    @Nullable
    public static TaiFunction featureOf(@NonNull TaiResidency.Entry entry) {
        if (entry.feature != null) return entry.feature;
        switch (entry.kind) {
            case STT: return TaiFunction.VOICE_TYPING;
            case TTS: return TaiFunction.READ_ALOUD;
            case EMBEDDING: return TaiFunction.EMBEDDINGS;
            default: return null;
        }
    }

    /**
     * Whether {@code entry} stays loaded for its group: it serves a feature that belongs to a group and was
     * used within {@link #GROUP_RECENT_MS}, and the load asking for room is another feature's
     * ({@code loading}; {@code null} for the memory watch, which no feature is).
     */
    public static boolean keptForItsGroup(@NonNull TaiResidency.Entry entry, @Nullable TaiFunction loading, long nowMs) {
        TaiFunction served = featureOf(entry);
        if (served == null || served == loading || groupsOf(served).isEmpty()) return false;
        return nowMs - entry.lastUsedMs < GROUP_RECENT_MS;
    }

    // ------------------------------------------------------------------------------- evidence

    /**
     * Measured speeds of this model file by accelerator, larger is faster, with speculative decoding on
     * or off: the feature's own checks, else for the assistant and Dawn chat the generic chat bench's
     * decode speed. Only passing, fresh results count; the best of each accelerator wins.
     */
    @NonNull
    private static Map<String, Double> speeds(@NonNull TaiFunction feature, @NonNull TaiFunctionModels.ModelInfo info,
                                              @NonNull TaiEvidence evidence, boolean speculative) {
        Map<String, Double> out = new HashMap<>();
        for (TaiEvidence.FeatureResult result : evidence.featureChecks(feature, info.id, info.backend)) {
            if (!result.passed || result.stale || result.speculative != speculative || result.speed <= 0.0) continue;
            // Asked for and never ran: not a measurement of speculative decoding.
            if (speculative && Boolean.FALSE.equals(result.speculativeRan)) continue;
            put(out, result.accelerator, result.speed);
        }
        if (!out.isEmpty() || !chatBenchCounts(feature)) return out;
        for (TaiEvidence.ChatResult result : evidence.chatBench(info.id, info.backend)) {
            if (!result.passed || result.speculative != speculative || result.decodeTps <= 0.0) continue;
            put(out, result.accelerator, result.decodeTps);
        }
        return out;
    }

    /**
     * Speeds to compare the two accelerators on, both with speculative decoding the same way: on when both
     * were measured with it (the feature check runs both accelerators at the plan's own setting, which is on
     * for a file that declares it), else off.
     */
    @NonNull
    private static Map<String, Double> comparableSpeeds(@NonNull TaiFunction feature, @NonNull TaiFunctionModels.ModelInfo info,
                                                        @NonNull TaiEvidence evidence) {
        Map<String, Double> on = speeds(feature, info, evidence, true);
        if (on.containsKey(TaiTierPolicy.ACCEL_GPU) && on.containsKey(TaiTierPolicy.ACCEL_CPU)) return on;
        return speeds(feature, info, evidence, false);
    }

    /**
     * Measured decode speeds by accelerator with speculative decoding on or off, the only figure speculative
     * decoding is judged on (never the wait for the first token): the feature's own fresh, passing checks
     * that decoded, else for the chat features the chat bench.
     */
    @NonNull
    private static Map<String, Double> decodeSpeeds(@NonNull TaiFunction feature, @NonNull TaiFunctionModels.ModelInfo info,
                                                    @NonNull TaiEvidence evidence, boolean speculative) {
        Map<String, Double> out = new HashMap<>();
        boolean checked = false;
        for (TaiEvidence.FeatureResult result : evidence.featureChecks(feature, info.id, info.backend)) {
            if (!result.passed || result.stale) continue;
            // Checked at all: both sides then come from the check, never one from the chat bench.
            checked = true;
            if (result.speculative != speculative || result.decodeTps <= 0.0) continue;
            if (speculative && Boolean.FALSE.equals(result.speculativeRan)) continue;
            put(out, result.accelerator, result.decodeTps);
        }
        if (checked || !chatBenchCounts(feature)) return out;
        for (TaiEvidence.ChatResult result : evidence.chatBench(info.id, info.backend)) {
            if (!result.passed || result.speculative != speculative || result.decodeTps <= 0.0) continue;
            put(out, result.accelerator, result.decodeTps);
        }
        return out;
    }

    /** Decision 11: the generic chat bench speaks for the chat features only. */
    static boolean chatBenchCounts(@NonNull TaiFunction feature) {
        return feature == TaiFunction.ASSISTANT || feature == TaiFunction.DAWN_CHAT;
    }

    private static void put(@NonNull Map<String, Double> speeds, @NonNull String accelerator, double speed) {
        if (!TaiTierPolicy.ACCEL_GPU.equals(accelerator) && !TaiTierPolicy.ACCEL_CPU.equals(accelerator)) return;
        Double known = speeds.get(accelerator);
        if (known == null || speed > known) speeds.put(accelerator, speed);
    }

    /** A feature check asked for speculative decoding on this accelerator and the runtime said it never ran. */
    private static boolean neverRan(@NonNull TaiFunction feature, @NonNull TaiFunctionModels.ModelInfo info,
                                    @NonNull TaiEvidence evidence, @NonNull String accelerator) {
        for (TaiEvidence.FeatureResult result : evidence.featureChecks(feature, info.id, info.backend)) {
            if (result.stale) continue;
            if (result.speculative && accelerator.equals(result.accelerator) && Boolean.FALSE.equals(result.speculativeRan)) {
                return true;
            }
        }
        return false;
    }

    /** The accelerator of {@code usable} measured fastest by a clear margin over the other; {@code null} when unclear. */
    @Nullable
    private static String fastest(@NonNull Map<String, Double> speeds, @NonNull List<String> usable) {
        if (usable.size() < 2) return null;
        Double first = speeds.get(usable.get(0));
        Double second = speeds.get(usable.get(1));
        if (first == null || second == null || first <= 0.0 || second <= 0.0) return null;
        if (first / second >= MEASURED_MARGIN) return usable.get(0);
        if (second / first >= MEASURED_MARGIN) return usable.get(1);
        return null;
    }

    @NonNull
    private static String other(@NonNull String accelerator) {
        return TaiTierPolicy.ACCEL_GPU.equals(accelerator) ? TaiTierPolicy.ACCEL_CPU : TaiTierPolicy.ACCEL_GPU;
    }

    // ------------------------------------------------------------------------------- applying

    /** The {@code model} a request for this plan names: the local id or {@code remote/<id>}; {@code null} for none. */
    @Nullable
    public String requestModel() {
        return modelId;
    }

    public boolean isRemote() {
        return where == Where.REMOTE;
    }

    /** True when any value came from a measurement on this phone. */
    public boolean isMeasured() {
        return acceleratorReason == Reason.MEASURED || speculativeReason == Reason.MEASURED;
    }

    /**
     * The load options for a request that names this feature, over {@code options} (the settings'): the
     * plan's window, and for its own model its speculative decoding and accelerator. A picked accelerator
     * goes as explicit; a default or measured one as the automatic load's preference. {@code
     * requestModelId} is the model the request ended up naming; when an outside caller named another, the
     * model-specific values are left to the settings.
     */
    @NonNull
    public TaiRuntimeOptions applyTo(@NonNull TaiRuntimeOptions options, @Nullable String requestModelId) {
        if (where != Where.ON_DEVICE || modelId == null) return options.withFeature(feature.id());
        boolean own = requestModelId != null
            && TaiModelVariants.baseModelId(modelId).equals(TaiModelVariants.baseModelId(requestModelId));
        Integer window = contextWindow > 0 ? Integer.valueOf(contextWindow) : options.contextWindow;
        if (!own || accelerator == null) {
            return options.withFeature(feature.id()).withContextWindow(window);
        }
        boolean explicit = acceleratorReason == Reason.PICK;
        return options.withFeature(feature.id())
            .withContextWindow(window)
            .withAccelerator(explicit ? accelerator : "auto")
            .withPreferredAccelerator(explicit ? null : accelerator)
            .withSpeculativeDecoding(speculative != null ? speculative : options.speculativeDecodingEnabled);
    }

    // ---------------------------------------------------------------------------------- json

    /** What the runtime process needs of the plan; {@link #fromJson} reads it back. */
    @NonNull
    public JSONObject toJson() throws JSONException {
        JSONObject json = new JSONObject();
        json.put("feature", feature.id());
        json.put("where", where.name().toLowerCase(Locale.ROOT));
        json.put("model", modelId == null ? JSONObject.NULL : modelId);
        json.put("modelReason", reason(modelReason));
        json.put("backend", backend == null ? JSONObject.NULL : backend);
        json.put("accelerator", accelerator == null ? JSONObject.NULL : accelerator);
        json.put("acceleratorReason", reason(acceleratorReason));
        json.put("speculative", speculative == null ? JSONObject.NULL : speculative);
        json.put("speculativeReason", reason(speculativeReason));
        json.put("contextWindow", contextWindow);
        json.put("residency", residency.name().toLowerCase(Locale.ROOT));
        if (remoteFallback != null) json.put("remoteFallback", remoteFallback);
        if (speedup > 0.0) json.put("speedup", speedup);
        return json;
    }

    /** The plan {@link #toJson} wrote; {@code null} for anything that does not read as one. */
    @Nullable
    public static TaiFeaturePlan fromJson(@Nullable JSONObject json) {
        if (json == null) return null;
        TaiFunction feature = TaiFunction.fromId(json.optString("feature", ""));
        if (feature == null) return null;
        try {
            Where where = Where.valueOf(json.optString("where", "none").toUpperCase(Locale.ROOT));
            Residency residency = Residency.valueOf(json.optString("residency", "until_idle").toUpperCase(Locale.ROOT));
            Boolean speculative = json.isNull("speculative") || !json.has("speculative")
                ? null : json.optBoolean("speculative");
            return new TaiFeaturePlan(feature, where, Reason.DEFAULT, string(json, "model"),
                reasonOf(json, "modelReason"), string(json, "backend"), string(json, "accelerator"),
                reasonOf(json, "acceleratorReason"), speculative, reasonOf(json, "speculativeReason"),
                Math.max(0, json.optInt("contextWindow", 0)), Reason.DEFAULT, residency, Reason.DEFAULT,
                TaiTierPolicy.WithoutModel.NONE, string(json, "remoteFallback"), false,
                json.optDouble("speedup", 0.0), null);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    @NonNull
    private static String reason(@NonNull Reason reason) {
        return reason.name().toLowerCase(Locale.ROOT);
    }

    @NonNull
    private static Reason reasonOf(@NonNull JSONObject json, @NonNull String key) {
        try {
            return Reason.valueOf(json.optString(key, "default").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return Reason.DEFAULT;
        }
    }

    @Nullable
    private static String string(@NonNull JSONObject json, @NonNull String key) {
        if (!json.has(key) || json.isNull(key)) return null;
        String value = json.optString(key, "").trim();
        return value.isEmpty() ? null : value;
    }
}
