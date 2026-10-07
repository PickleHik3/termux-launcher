package com.termux.ai;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * What each tier offers and preselects, as one pure table (tai-device-tiers spec §3, §4.4, §5.3).
 * It decides what is <i>suggested</i>; it never forces a load (the live gate still decides each
 * one) and never blocks a model the user added.
 *
 * <pre>
 * offer(function, model) = tierPolicy(tier, ramClass, function, model)  ∩  platform(abi, sdk, gpuPath)
 * </pre>
 *
 * <p>The platform only takes things away: with no usable
 * ABI there are no local models; with no GPU path the Tier 2 and 3 assistant runs E2B on the CPU,
 * and categories run E4B on the CPU; a CPU-first GPU preselects the CPU.
 *
 * <p>Image generation is deliberately absent: it has no function, no catalogue row, no welcome row
 * and no suggestion (spec §3.6). It stays reachable through the API, {@code tai image} and import.
 *
 * <p>Nothing here reads the phone, apart from {@link Env#forDevice}: every rule takes an {@link Env}.
 */
public final class TaiTierPolicy {
    private TaiTierPolicy() {}

    private static final long GIB = 1024L * 1024L * 1024L;

    public static final String ACCEL_GPU = "gpu";
    public static final String ACCEL_CPU = "cpu";

    /** The picker's note for a GPU that is preselected but not confirmed (decision 3). */
    public static final String NOTE_GPU_UNCONFIRMED = "Not confirmed on this GPU yet";
    /** The picker's note on the GPU choice of a CPU-first phone. */
    public static final String NOTE_GPU_OFTEN_FAILS = "Often fails on this GPU";
    /** The subtext under a function whose model may make Android close cached apps (spec §4.4). */
    public static final String NOTE_BACKGROUND_APPS = "May close apps running in the background";

    /** Whisper ACFT ids; the {@code -en} files are English-only. */
    public static final String WHISPER_BASE = "whisper-acft-base";
    public static final String WHISPER_BASE_EN = "whisper-acft-base-en";
    public static final String WHISPER_SMALL = "whisper-acft-small";
    public static final String WHISPER_SMALL_EN = "whisper-acft-small-en";

    private static final String E2B = TaiModelRegistry.MODEL_GEMMA_4_E2B_IT;
    private static final String E4B = TaiModelRegistry.MODEL_GEMMA_4_E4B_IT;

    /** How a model is shown for this phone: ticked, listed as a fit, listed as "for bigger phones", or not shown. */
    public enum Offer { PRESELECTED, SUGGESTED, LISTED, HIDDEN }

    /** What a function does with no model at all. {@code NONE}: it simply does not work. */
    public enum WithoutModel { NONE, RULES_ONLY, RAW_TEXT, OFF }

    // ------------------------------------------------------------------------------------ Env

    /** Everything the policy reads about a phone, so the table is a pure function of it. */
    public static final class Env {
        @NonNull public final TaiDeviceTier tier;
        /** The RAM class ({@link TaiLoadBudget#ramClassBytes}), the real RAM even under a tier override. */
        public final long ramClassBytes;
        public final int sdkInt;
        public final boolean liteRtAbiOk;
        public final boolean mnnAbiOk;
        @NonNull public final TaiPlatformCaps.GpuPath gpuPath;
        /** The system locale's language is {@code en}: Whisper then gets the {@code .en} files. */
        public final boolean english;

        public Env(@NonNull TaiDeviceTier tier, long ramClassBytes, int sdkInt, boolean liteRtAbiOk, boolean mnnAbiOk,
                   @NonNull TaiPlatformCaps.GpuPath gpuPath, boolean english) {
            this.tier = tier;
            this.ramClassBytes = ramClassBytes;
            this.sdkInt = sdkInt;
            this.liteRtAbiOk = liteRtAbiOk;
            this.mnnAbiOk = mnnAbiOk;
            this.gpuPath = gpuPath;
            this.english = english;
        }

        /** The tier, the RAM class and the platform caps put together. */
        @NonNull
        public static Env of(@NonNull TaiDeviceTier tier, long ramClassBytes, @NonNull TaiPlatformCaps caps, boolean english) {
            return new Env(tier, ramClassBytes, caps.sdkInt, caps.liteRtAbiOk, caps.mnnAbiOk, caps.gpuPath, english);
        }

        /**
         * This phone: its RAM, the developer tier override, the cached GPU probe ({@link
         * TaiPlatformCaps#cached(Context)}, unknown until probed) and the system language. Reads
         * preferences; not for a hot path.
         */
        @NonNull
        public static Env forDevice(@NonNull Context context) {
            long ram = TaiDeviceTier.deviceRamClassBytes(context);
            TaiDeviceTier tier = TaiDeviceTier.forDevice(context);
            boolean english = "en".equals(Locale.getDefault().getLanguage());
            return of(tier, ram, TaiPlatformCaps.cached(context), english);
        }

        /** The 8 GB row of Tier 2: it never gets E4B by default, and uses E2B wherever 10 and 12 GB use E4B. */
        public boolean isEightGb() {
            return tier == TaiDeviceTier.TIER_2 && ramClassBytes <= 8L * GIB;
        }

        /** With neither LiteRT-LM nor MNN usable, no local model is offered. */
        public boolean localModelsSupported() {
            return liteRtAbiOk || mnnAbiOk;
        }

        /** True when the GPU is not an option at all (no OpenCL, or the Pixel 10 rule). */
        public boolean noGpu() {
            return gpuPath == TaiPlatformCaps.GpuPath.NO;
        }
    }

    // --------------------------------------------------------------------------------- Choice

    /**
     * One link of a function's choice: a model on an accelerator, or a without-model end. Model ids
     * are catalogue ids.
     */
    public static final class Choice {
        /** {@code null} for a without-model choice, or "nothing" ({@link #NOTHING}). */
        @Nullable public final String modelId;
        /** {@code "gpu"} or {@code "cpu"} for a chat model, {@code null} where it does not apply. */
        @Nullable public final String accelerator;
        @NonNull public final WithoutModel without;

        private Choice(@Nullable String modelId, @Nullable String accelerator, @NonNull WithoutModel without) {
            this.modelId = modelId;
            this.accelerator = accelerator;
            this.without = without;
        }

        static Choice model(@NonNull String modelId, @Nullable String accelerator) {
            return new Choice(modelId, accelerator, WithoutModel.NONE);
        }

        static Choice without(@NonNull WithoutModel without) {
            return new Choice(null, null, without);
        }

        /** No choice at all: the function has no default on this phone. */
        public static final Choice NOTHING = new Choice(null, null, WithoutModel.NONE);

        public boolean isModel() {
            return modelId != null;
        }

        @Override
        public String toString() {
            return modelId != null ? modelId + (accelerator == null ? "" : " " + accelerator) : without.name();
        }
    }

    // ------------------------------------------------------------------- availability and platform

    /**
     * Whether the platform allows {@code modelId} to serve {@code function}: some local
     * backend runs. (The model's own backend is checked where the installed
     * model is known: {@link TaiFunctionModels}.)
     */
    public static boolean platformAllows(@NonNull Env env, @NonNull TaiFunction function) {
        return env.localModelsSupported();
    }

    /** {@code "gpu"} when the GPU path is yes or unconfirmed, {@code "cpu"} when it is CPU-first or absent. */
    @NonNull
    public static String defaultAccelerator(@NonNull Env env) {
        switch (env.gpuPath) {
            case YES:
            case UNKNOWN:
                return ACCEL_GPU;
            default:
                return ACCEL_CPU;
        }
    }

    /**
     * The note the picker prints beside the GPU: unconfirmed for {@code UNKNOWN}, "often fails" for
     * {@code CPU_FIRST}, none otherwise.
     */
    @Nullable
    public static String gpuNote(@NonNull Env env) {
        switch (env.gpuPath) {
            case UNKNOWN: return NOTE_GPU_UNCONFIRMED;
            case CPU_FIRST: return NOTE_GPU_OFTEN_FAILS;
            default: return null;
        }
    }

    /** Whether the GPU is offered at all as a choice. */
    public static boolean gpuOffered(@NonNull Env env) {
        return !env.noGpu();
    }

    /**
     * Spec §4.4: a model file of at least 25 % of the RAM class may make Android close cached
     * background apps, and the user is told. E4B (3.66 GB) warns on 12 GB (30 %) and not on 16 GB
     * (23 %); E2B (2.59 GB) warns on 8 GB (32 %) and not on 12 GB.
     */
    public static boolean warnsBackground(@NonNull Env env, long modelFileBytes) {
        if (env.ramClassBytes <= 0L || modelFileBytes <= 0L) return false;
        return modelFileBytes * 4L >= env.ramClassBytes;
    }

    // --------------------------------------------------------------------------------- Automatic

    /**
     * What Automatic means for {@code function} (spec §3). The accelerator is chosen from the GPU
     * path. {@link Choice#NOTHING} when the tier has no default.
     */
    @NonNull
    public static Choice automatic(@NonNull Env env, @NonNull TaiFunction function) {
        String accel = defaultAccelerator(env);
        TaiDeviceTier tier = env.tier;
        boolean t1 = tier == TaiDeviceTier.TIER_1;
        switch (function) {
            case ASSISTANT:
                if (t1) return Choice.NOTHING;
                // No GPU path: E2B on the CPU even on Tier 3 (spec §2.2); E4B stays selectable.
                if (tier == TaiDeviceTier.TIER_3 && !env.noGpu()) return Choice.model(E4B, accel);
                return Choice.model(E2B, accel);
            case VOICE_TYPING:
                return Choice.model(t1 ? whisper("base", env) : whisper("small", env), null);
            case TIDY_DICTATION:
                return t1 ? Choice.without(WithoutModel.RAW_TEXT) : Choice.model(E2B, accel);
            case READ_ALOUD:
                return Choice.model(TaiModelCatalog.KITTEN_TTS_NANO_ID, null);
            case APP_CATEGORIES:
                if (t1) return Choice.without(WithoutModel.OFF);
                // The pong benchmark (2026-10-05): E4B sorted 11 of 12 apps, as E2B did, at twice the time
                // and three times the memory. Only Tier 3, where E4B is the resident assistant, keeps it.
                return Choice.model(tier == TaiDeviceTier.TIER_3 ? E4B : E2B, accel);
            case EMBEDDINGS:
                // EmbeddingGemma 2 (2026-10-06): the 440M text+vision file everywhere it fits, the
                // 270M text-only one on Tier 1; the v1 300M stays installable but is no longer chosen.
                return Choice.model(t1 ? TaiModelCatalog.EMBEDDING_GEMMA_2_TEXT_270M_ID
                    : TaiModelCatalog.EMBEDDING_GEMMA_2_TEXT_VISION_440M_ID, null);
            default:
                return Choice.NOTHING;
        }
    }

    /**
     * What {@code function} falls back to, in order, after Automatic (spec §3, §4.3's "If it can't
     * load" line). A chain ends with the function's without-model choice where it has one. No chain
     * falls back from GPU to CPU for Gemma 4: the CPU saves no memory and costs 3 to 4 times the time.
     */
    @NonNull
    public static List<Choice> fallbackChain(@NonNull Env env, @NonNull TaiFunction function) {
        List<Choice> chain = new ArrayList<>();
        String accel = defaultAccelerator(env);
        TaiDeviceTier tier = env.tier;
        boolean t1 = tier == TaiDeviceTier.TIER_1;
        switch (function) {
            case ASSISTANT:
                if (tier == TaiDeviceTier.TIER_3 && !env.noGpu()) chain.add(Choice.model(E2B, accel));
                break;
            case VOICE_TYPING:
                // Today's behaviour kept as the last resort: any installed speech model serves.
                chain.add(Choice.model(t1 ? whisper("small", env) : whisper("base", env), null));
                chain.add(Choice.model(TaiModelCatalog.PARAKEET_TDT_V3_ID, null));
                break;
            case TIDY_DICTATION:
                if (!t1) {
                    chain.add(Choice.model(E4B, accel));
                    chain.add(Choice.without(WithoutModel.RAW_TEXT));
                }
                break;
            case APP_CATEGORIES:
                if (!t1) {
                    if (tier == TaiDeviceTier.TIER_3) chain.add(Choice.model(E2B, accel));
                    chain.add(Choice.without(WithoutModel.OFF));
                }
                break;
            case EMBEDDINGS:
                // Any installed EmbeddingGemma serves: the other v2 file, then the v1 300M a phone
                // may still carry from before 2026-10-06.
                chain.add(Choice.model(t1 ? TaiModelCatalog.EMBEDDING_GEMMA_2_TEXT_VISION_440M_ID
                    : TaiModelCatalog.EMBEDDING_GEMMA_2_TEXT_270M_ID, null));
                chain.add(Choice.model(TaiModelCatalog.EMBEDDING_GEMMA_300M_ID, null));
                break;
            default:
                break;
        }
        return chain;
    }

    /** The English-only file when the phone is set to English, else the multilingual one. */
    @NonNull
    static String whisper(@NonNull String size, @NonNull Env env) {
        return "whisper-acft-" + size + (env.english ? "-en" : "");
    }

    // --------------------------------------------------------------------------------- Offer

    /**
     * How the Model Centre and the welcome card treat {@code modelId} on this phone (spec §3). An id
     * the policy does not name (an imported model, an image model) is {@code HIDDEN}: it
     * stays installable by import and shows once installed, but is never suggested.
     */
    @NonNull
    public static Offer offer(@NonNull Env env, @Nullable String modelId) {
        if (modelId == null || !env.localModelsSupported()) return Offer.HIDDEN;
        TaiDeviceTier tier = env.tier;
        boolean t1 = tier == TaiDeviceTier.TIER_1;
        boolean t3 = tier == TaiDeviceTier.TIER_3;
        boolean eight = env.isEightGb();
        switch (modelId) {
            case E2B:
                // Tier 3 ticks it too: it tidies dictation there, beside the E4B assistant (§5.3).
                return t1 ? Offer.LISTED : Offer.PRESELECTED;
            case E4B:
                if (t1 || eight) return Offer.LISTED;
                // On 10 and 12 GB it is offered but not ticked; on Tier 3 it is the assistant.
                if (t3) return env.noGpu() ? Offer.SUGGESTED : Offer.PRESELECTED;
                return Offer.SUGGESTED;
            case TaiModelRegistry.MODEL_MOBILE_ACTIONS_270M:
                return Offer.LISTED;
            case WHISPER_SMALL:
            case WHISPER_SMALL_EN:
                if (env.english != modelId.endsWith("-en")) return Offer.LISTED;
                return t1 ? Offer.SUGGESTED : Offer.PRESELECTED;
            case WHISPER_BASE:
            case WHISPER_BASE_EN:
                if (env.english != modelId.endsWith("-en")) return Offer.LISTED;
                return t3 ? Offer.LISTED : Offer.SUGGESTED;
            case TaiModelCatalog.PARAKEET_TDT_V3_ID:
                return t1 ? Offer.LISTED : Offer.SUGGESTED;
            case TaiModelCatalog.KITTEN_TTS_NANO_ID:
                return t1 ? Offer.SUGGESTED : Offer.PRESELECTED;
            case TaiModelCatalog.EMBEDDING_GEMMA_2_TEXT_VISION_440M_ID:
                return t1 ? Offer.LISTED : Offer.PRESELECTED;
            case TaiModelCatalog.EMBEDDING_GEMMA_2_TEXT_270M_ID:
                return t1 ? Offer.SUGGESTED : Offer.LISTED;
            case TaiModelCatalog.EMBEDDING_GEMMA_300M_ID:
                return Offer.LISTED;
            default:
                return Offer.HIDDEN;
        }
    }

    // ----------------------------------------------------------------------------- Welcome card

    /** One row of the welcome card "What runs on this phone" (spec §5.2, §5.3). */
    public static final class WelcomeRow {
        /** A stable key: {@code voice_typing}, {@code read_aloud}, {@code e4b_assistant}, {@code assistant}, {@code dawn_notes}. */
        @NonNull public final String id;
        /** The downloads the row stands for, in order. */
        @NonNull public final List<String> modelIds;
        /** The functions the row's ticked download serves. */
        @NonNull public final List<TaiFunction> functions;
        /** Ticked when the card opens. */
        public final boolean preselected;

        WelcomeRow(@NonNull String id, @NonNull List<String> modelIds, @NonNull List<TaiFunction> functions, boolean preselected) {
            this.id = id;
            this.modelIds = Collections.unmodifiableList(modelIds);
            this.functions = Collections.unmodifiableList(functions);
            this.preselected = preselected;
        }
    }

    /**
     * The card's rows for this phone, in display order, with the fixed preselection of spec §5.3.
     * Depends only on the device: nothing on how full the phone is today (storage can disable
     * Download, never change a tick). Tier 1 has no assistant
     * rows (the card adds its own Model Centre line); Tier 3's E4B assistant row is unticked on a
     * phone with no GPU path. E4B has no row on Tier 2.
     */
    @NonNull
    public static List<WelcomeRow> welcomeRows(@NonNull Env env) {
        List<WelcomeRow> rows = new ArrayList<>();
        if (!env.localModelsSupported()) return rows;
        TaiDeviceTier tier = env.tier;
        boolean t1 = tier == TaiDeviceTier.TIER_1;
        boolean t3 = tier == TaiDeviceTier.TIER_3;

        rows.add(new WelcomeRow("voice_typing", ids(whisper(t1 ? "base" : "small", env)),
            fns(TaiFunction.VOICE_TYPING), !t1));
        rows.add(new WelcomeRow("read_aloud", ids(TaiModelCatalog.KITTEN_TTS_NANO_ID),
            fns(TaiFunction.READ_ALOUD), !t1));
        if (t3) {
            // Tier 3's assistant is E4B; E2B is the tidy-dictation helper only.
            rows.add(new WelcomeRow("e4b_assistant", ids(E4B),
                fns(TaiFunction.ASSISTANT, TaiFunction.APP_CATEGORIES), !env.noGpu()));
            rows.add(new WelcomeRow("assistant", ids(E2B), fns(TaiFunction.TIDY_DICTATION), true));
        } else if (!t1) {
            rows.add(new WelcomeRow("assistant", ids(E2B),
                fns(TaiFunction.ASSISTANT, TaiFunction.TIDY_DICTATION, TaiFunction.APP_CATEGORIES), true));
        }
        rows.add(new WelcomeRow("dawn_notes", ids(t1 ? TaiModelCatalog.EMBEDDING_GEMMA_2_TEXT_270M_ID
                : TaiModelCatalog.EMBEDDING_GEMMA_2_TEXT_VISION_440M_ID),
            fns(TaiFunction.EMBEDDINGS), !t1));
        return rows;
    }

    private static List<String> ids(String... ids) {
        List<String> list = new ArrayList<>();
        Collections.addAll(list, ids);
        return list;
    }

    private static List<TaiFunction> fns(TaiFunction... functions) {
        List<TaiFunction> list = new ArrayList<>();
        Collections.addAll(list, functions);
        return list;
    }
}
