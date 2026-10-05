package com.termux.ai;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Every function's model pick, and the one rule that turns a pick into a model (tai-device-tiers
 * spec §4.5). A stored value is {@code ""} (Automatic), a model id, {@code remote/<id>} (the BYO-key
 * provider's model) or {@code off}.
 *
 * <p>{@link #resolve} goes: the pick, if it is installed and the platform allows it; else Automatic
 * ({@link TaiTierPolicy#automatic}); else the first installed link of the chain ({@link
 * TaiTierPolicy#fallbackChain}); else nothing. The live gate still decides each load, and a refusal
 * walks the chain from the caller's side.
 *
 * <p>Storage and the installed-model lookup sit behind small interfaces so tests can fake them.
 * The callers (categories, reader, tidy dictation, speech, voice, depth, embeddings) resolve here.
 */
public final class TaiFunctionModels {
    /** The stored value for "turn this function off" (or its without-model choice). */
    public static final String VALUE_OFF = "off";
    /** The stored-value prefix reserved for the remote provider. */
    public static final String REMOTE_PREFIX = "remote/";

    /** True for a stored value that names a remote model ({@code remote/<id>}). */
    public static boolean isRemote(@Nullable String value) {
        return value != null && value.startsWith(REMOTE_PREFIX);
    }

    /** Where a resolved model came from. */
    public enum Source {
        /** The user's pick. */
        PICK,
        /** Automatic: the tier's own choice. */
        AUTOMATIC,
        /** A later link of the chain, because the pick and Automatic were not usable. */
        FALLBACK,
        /** Nothing usable, and the function has no model-less mode that applies. */
        NONE
    }

    /** What the resolver needs to know about an installed model; built from a {@link TaiModelSpec}. */
    public static final class ModelInfo {
        @NonNull public final String id;
        public final long sizeBytes;
        /** {@link TaiModelSpec} capability names, of the model's file (source and advertised together). */
        @NonNull public final Set<String> capabilities;
        /** {@link TaiModelSpec#backend}, e.g. {@code litert-lm} or {@code mnn-llm}. */
        @NonNull public final String backend;

        public ModelInfo(@NonNull String id, long sizeBytes, @NonNull Set<String> capabilities, @NonNull String backend) {
            this.id = id;
            this.sizeBytes = sizeBytes;
            this.capabilities = Collections.unmodifiableSet(new LinkedHashSet<>(capabilities));
            this.backend = backend;
        }

        @NonNull
        static ModelInfo of(@NonNull TaiModelSpec spec) {
            Set<String> caps = new LinkedHashSet<>(spec.capabilities);
            caps.addAll(spec.sourceCapabilities);
            return new ModelInfo(spec.id, spec.sizeBytes, caps, spec.backend);
        }

        /** The same file advertised as its vision-enabled load ({@code <id>-vision}). */
        @NonNull
        ModelInfo asVision() {
            return new ModelInfo(id + TaiModelVariants.SUFFIX_VISION, sizeBytes, capabilities, backend);
        }
    }

    /** The installed models, keyed by base id (no modality suffix), in a stable order. */
    public interface Installed {
        @NonNull Map<String, ModelInfo> models();
    }

    /** Raw string storage by preference key; a missing value reads as {@code ""}. */
    public interface Store {
        @NonNull String get(@NonNull String key);

        void put(@NonNull String key, @NonNull String value);
    }

    /**
     * The BYO-key provider as the resolver sees it, behind a seam so tests can fake it. A remote model has
     * no file, so it never goes through {@link Installed}. {@link #NONE} is "not configured".
     */
    public interface Remote {
        boolean configured();

        /** The server's own model id, without the {@code remote/} prefix. */
        @NonNull String modelId();

        boolean understandsImages();

        /** The "Prefer remote" routing setting: Automatic then resolves to the remote model. */
        boolean prefersRemote();

        Remote NONE = new Remote() {
            @Override public boolean configured() { return false; }
            @NonNull @Override public String modelId() { return ""; }
            @Override public boolean understandsImages() { return false; }
            @Override public boolean prefersRemote() { return false; }
        };
    }

    /**
     * Whether {@code function} can run on a remote model at all: not voice typing, read aloud or
     * embeddings (the app's audio and embedding paths are local), and not the depth model. The
     * reader needs a remote model that understands images ({@code understandsImages}).
     */
    public static boolean remoteAllowed(@NonNull TaiFunction function, boolean understandsImages) {
        switch (function) {
            case VOICE_TYPING:
            case READ_ALOUD:
            case EMBEDDINGS:
            case WALLPAPER_DEPTH:
                return false;
            case WALLPAPER_READER:
                return understandsImages;
            default:
                return true;
        }
    }

    /** The outcome of {@link #resolve}; immutable. */
    public static final class Resolution {
        /** The model to load (the reader's carries the {@code -vision} suffix), {@code null} without one. */
        @Nullable public final String modelId;
        /** {@code "gpu"} or {@code "cpu"} for a chat function with a model, else {@code null}. */
        @Nullable public final String accelerator;
        @NonNull public final Source source;
        /** What the function does with no model: rules only, raw text, off; {@code NONE} when a model is set or the function has no such mode. */
        @NonNull public final TaiTierPolicy.WithoutModel without;
        /** What it falls back to after Automatic, for the picker's "If it can't load" line. */
        @NonNull public final List<TaiTierPolicy.Choice> chain;
        /** The model file is at least a quarter of the RAM class: show "May close apps running in the background". */
        public final boolean warnBackground;
        /**
         * {@code remote/<id>} when the pick is the remote provider's model and it is set up: the request
         * then carries this as its {@code model} and no local model loads ({@link #modelId} is {@code null}).
         */
        @Nullable public final String remoteModel;

        Resolution(@Nullable String modelId, @Nullable String accelerator, @NonNull Source source,
                   @NonNull TaiTierPolicy.WithoutModel without, @NonNull List<TaiTierPolicy.Choice> chain,
                   boolean warnBackground) {
            this(modelId, accelerator, source, without, chain, warnBackground, null);
        }

        public Resolution(@Nullable String modelId, @Nullable String accelerator, @NonNull Source source,
                   @NonNull TaiTierPolicy.WithoutModel without, @NonNull List<TaiTierPolicy.Choice> chain,
                   boolean warnBackground, @Nullable String remoteModel) {
            this.remoteModel = remoteModel;
            this.modelId = modelId;
            this.accelerator = accelerator;
            this.source = source;
            this.without = without;
            this.chain = Collections.unmodifiableList(new ArrayList<>(chain));
            this.warnBackground = warnBackground;
        }

        /** True when the request goes to the remote provider. */
        public boolean isRemote() {
            return remoteModel != null;
        }

        /** The {@code model} a request for this resolution names: the remote name, else the local id, else {@code null}. */
        @Nullable
        public String requestModel() {
            return remoteModel != null ? remoteModel : modelId;
        }
    }

    private final TaiTierPolicy.Env env;
    private final Store store;
    private final Installed installed;
    private final Remote remote;

    public TaiFunctionModels(@NonNull TaiTierPolicy.Env env, @NonNull Store store, @NonNull Installed installed) {
        this(env, store, installed, Remote.NONE);
    }

    public TaiFunctionModels(@NonNull TaiTierPolicy.Env env, @NonNull Store store, @NonNull Installed installed,
                             @NonNull Remote remote) {
        this.env = env;
        this.store = store;
        this.installed = installed;
        this.remote = remote;
    }

    /**
     * The live instance for this phone: its tier and platform, the TAI and keyboard preferences, the
     * installed models of the model store. Reads preferences and the model store: not for the main thread.
     */
    @NonNull
    public static TaiFunctionModels forContext(@NonNull Context context) {
        final Context app = context.getApplicationContext();
        final TaiSettings settings = new TaiSettings(app);
        final TaiModelStore models = new TaiModelStore(app);
        Store store = new Store() {
            @NonNull
            @Override
            public String get(@NonNull String key) {
                if (isKeyboardKey(key)) {
                    TermuxAppSharedPreferences prefs = TermuxAppSharedPreferences.build(app, true);
                    String value = prefs == null ? null : prefs.getInAppKeyboardVoicePolishModelId();
                    return value == null ? "" : value.trim();
                }
                return settings.getFunctionValue(key);
            }

            @Override
            public void put(@NonNull String key, @NonNull String value) {
                if (isKeyboardKey(key)) {
                    TermuxAppSharedPreferences prefs = TermuxAppSharedPreferences.build(app, true);
                    if (prefs != null) prefs.setInAppKeyboardVoicePolishModelId(value);
                    return;
                }
                settings.setFunctionValue(key, value);
            }

            private boolean isKeyboardKey(String key) {
                return TaiFunction.TIDY_DICTATION.modelKey.equals(key);
            }
        };
        Installed installed = () -> {
            // Downloads and imports, as the speech and voice-output models read them.
            LinkedHashMap<String, TaiModelSpec> specs = new LinkedHashMap<>(models.getDownloadedReadableModels());
            specs.putAll(models.getInstalledUserModels());
            Map<String, ModelInfo> out = new LinkedHashMap<>();
            for (TaiModelSpec spec : specs.values()) out.put(spec.id, ModelInfo.of(spec));
            return out;
        };
        final TaiRemoteProvider provider = new TaiRemoteProvider(app);
        Remote remote = new Remote() {
            @Override public boolean configured() { return provider.isConfigured(); }
            @NonNull @Override public String modelId() { return provider.modelId(); }
            @Override public boolean understandsImages() { return provider.understandsImages(); }
            @Override public boolean prefersRemote() { return provider.prefersRemote(); }
        };
        return new TaiFunctionModels(TaiTierPolicy.Env.forDevice(app), store, installed, remote);
    }

    @NonNull
    public TaiTierPolicy.Env env() {
        return env;
    }

    // ------------------------------------------------------------------------------------ pick

    /** The stored pick: {@code ""} for Automatic, a model id, {@code remote/<id>} or {@code off}. */
    @NonNull
    public String pick(@NonNull TaiFunction function) {
        return normalizePick(function, store.get(function.modelKey));
    }

    /**
     * Stores a pick: {@code ""} or {@code null} for Automatic, a model id, {@code remote/<id>} or
     * {@code off}. The reader's {@code -vision} suffix is dropped (the pick is the file).
     */
    public void set(@NonNull TaiFunction function, @Nullable String value) {
        store.put(function.modelKey, normalizePick(function, value));
    }

    /** The stored accelerator pick for a chat function: {@code ""} (Automatic), {@code cpu} or {@code gpu}. */
    @NonNull
    public String acceleratorPick(@NonNull TaiFunction function) {
        String value = store.get(function.accelKey).trim().toLowerCase(java.util.Locale.ROOT);
        return TaiTierPolicy.ACCEL_GPU.equals(value) || TaiTierPolicy.ACCEL_CPU.equals(value) ? value : "";
    }

    public void setAcceleratorPick(@NonNull TaiFunction function, @Nullable String accelerator) {
        String value = accelerator == null ? "" : accelerator.trim().toLowerCase(java.util.Locale.ROOT);
        if (!TaiTierPolicy.ACCEL_GPU.equals(value) && !TaiTierPolicy.ACCEL_CPU.equals(value)) value = "";
        store.put(function.accelKey, value);
    }

    // --------------------------------------------------------------------------------- resolve

    /** The model, accelerator and chain for {@code function}, in the order the class comment gives. */
    @NonNull
    public Resolution resolve(@NonNull TaiFunction function) {
        return resolve(function, installed.models());
    }

    /**
     * What {@code function} would resolve to if {@code removedModelId} were deleted: the Model Centre's
     * delete confirmation names it for each function that uses the model.
     */
    @NonNull
    public Resolution resolveWithout(@NonNull TaiFunction function, @NonNull String removedModelId) {
        Map<String, ModelInfo> rest = new LinkedHashMap<>(installed.models());
        rest.remove(baseId(removedModelId));
        return resolve(function, rest);
    }

    private Resolution resolve(TaiFunction function, Map<String, ModelInfo> models) {
        List<TaiTierPolicy.Choice> chain = TaiTierPolicy.fallbackChain(env, function);
        if (!TaiTierPolicy.functionAvailable(env, function)) {
            return new Resolution(null, null, Source.NONE, TaiTierPolicy.WithoutModel.NONE, chain, false);
        }

        String pick = pick(function);
        if (VALUE_OFF.equals(pick)) {
            TaiTierPolicy.WithoutModel off = function.withoutModel != TaiTierPolicy.WithoutModel.NONE
                ? function.withoutModel : TaiTierPolicy.WithoutModel.OFF;
            return new Resolution(null, null, Source.PICK, off, chain, false);
        }
        // remote/<id> resolves while the provider is set up and the function can run remotely; else it
        // falls through to Automatic, as a pick that is not installed does.
        if (isRemote(pick) && remoteUsable(function)) {
            return remoteResolution(Source.PICK, chain);
        }
        if (!pick.isEmpty() && !isRemote(pick)) {
            ModelInfo info = serving(function, models, pick);
            if (info != null) {
                String stored = acceleratorPick(function);
                String accel = stored.isEmpty() ? TaiTierPolicy.defaultAccelerator(env) : stored;
                return withModel(function, info, accel, Source.PICK, chain);
            }
        }

        // "Prefer remote": Automatic itself means the remote model, wherever the function can use it.
        if (remote.prefersRemote() && remoteUsable(function)) {
            return remoteResolution(Source.AUTOMATIC, chain);
        }

        TaiTierPolicy.Choice auto = TaiTierPolicy.automatic(env, function);
        if (auto.isModel()) {
            ModelInfo info = serving(function, models, baseId(auto.modelId));
            if (info != null) return withModel(function, info, auto.accelerator, Source.AUTOMATIC, chain);
        } else if (auto.without != TaiTierPolicy.WithoutModel.NONE) {
            return new Resolution(null, null, Source.AUTOMATIC, auto.without, chain, false);
        }

        for (TaiTierPolicy.Choice link : chain) {
            if (link.isModel()) {
                ModelInfo info = serving(function, models, baseId(link.modelId));
                if (info != null) return withModel(function, info, link.accelerator, Source.FALLBACK, chain);
            } else if (link.without != TaiTierPolicy.WithoutModel.NONE) {
                return new Resolution(null, null, Source.FALLBACK, link.without, chain, false);
            }
        }
        return new Resolution(null, null, Source.NONE, function.withoutModel, chain, false);
    }

    private boolean remoteUsable(TaiFunction function) {
        return remote.configured() && !remote.modelId().isEmpty()
            && remoteAllowed(function, remote.understandsImages());
    }

    /** {@code remote/<id>}: no accelerator, and no background warning (nothing loads here). */
    private Resolution remoteResolution(Source source, List<TaiTierPolicy.Choice> chain) {
        String name = REMOTE_PREFIX + remote.modelId();
        return new Resolution(name, null, source, TaiTierPolicy.WithoutModel.NONE, chain, false, name);
    }

    private Resolution withModel(TaiFunction function, ModelInfo info, @Nullable String accelerator, Source source,
                                 List<TaiTierPolicy.Choice> chain) {
        String id = function == TaiFunction.WALLPAPER_READER ? info.id + TaiModelVariants.SUFFIX_VISION : info.id;
        String accel = function.usesChatModel() ? accelerator : null;
        // A GPU pick on a phone with no GPU path runs on the CPU.
        if (TaiTierPolicy.ACCEL_GPU.equals(accel) && env.noGpu()) accel = TaiTierPolicy.ACCEL_CPU;
        return new Resolution(id, accel, source, TaiTierPolicy.WithoutModel.NONE, chain,
            TaiTierPolicy.warnsBackground(env, info.sizeBytes));
    }

    // ------------------------------------------------------------------------------ usedBy / candidates

    /**
     * The functions whose resolved model is {@code modelId} (a base id or its {@code -vision} variant),
     * for the Model Centre's "Used by" line and the delete warning.
     */
    @NonNull
    public List<TaiFunction> usedBy(@NonNull String modelId) {
        Map<String, ModelInfo> models = installed.models();
        String base = baseId(modelId);
        List<TaiFunction> users = new ArrayList<>();
        for (TaiFunction function : TaiFunction.values()) {
            Resolution resolution = resolve(function, models);
            if (resolution.modelId != null && baseId(resolution.modelId).equals(base)) users.add(function);
        }
        return users;
    }

    /**
     * The installed models that can serve {@code function} on this platform, for the picker's "On this
     * phone" section: vision variants (ids with {@code -vision}) for the reader, embedders for
     * embeddings, speech models for voice typing, voice models for read aloud, depth models for depth,
     * chat models for the assistant, tidy dictation and categories. Image models never qualify.
     */
    @NonNull
    public List<ModelInfo> candidates(@NonNull TaiFunction function) {
        List<ModelInfo> out = new ArrayList<>();
        if (!TaiTierPolicy.platformAllows(env, function)) return out;
        for (ModelInfo info : installed.models().values()) {
            if (!backendRuns(info) || !canServe(function, info)) continue;
            out.add(function == TaiFunction.WALLPAPER_READER ? info.asVision() : info);
        }
        return out;
    }

    // ------------------------------------------------------------------------------------ rules

    /** The installed model for {@code baseId} when it can serve {@code function} here, else {@code null}. */
    @Nullable
    private ModelInfo serving(TaiFunction function, Map<String, ModelInfo> models, String baseId) {
        if (!TaiTierPolicy.platformAllows(env, function)) return null;
        ModelInfo info = models.get(baseId);
        if (info == null || !backendRuns(info) || !canServe(function, info)) return null;
        return info;
    }

    /** MNN needs its own ABI and API level; anything else runs on the LiteRT side's ABI. */
    private boolean backendRuns(ModelInfo info) {
        return TaiModelSpec.BACKEND_MNN_LLM.equals(info.backend) ? env.mnnAbiOk : env.liteRtAbiOk;
    }

    /** Whether a model with these capabilities can serve {@code function}; the Model Centre's "For:" and "Use for" lists read it. */
    public static boolean canServe(@NonNull TaiFunction function, @NonNull ModelInfo info) {
        Set<String> caps = info.capabilities;
        switch (function) {
            case VOICE_TYPING:
                return caps.contains(TaiModelSpec.CAPABILITY_SPEECH_TO_TEXT);
            case READ_ALOUD:
                return caps.contains(TaiModelSpec.CAPABILITY_TEXT_TO_SPEECH);
            case EMBEDDINGS:
                return caps.contains(TaiModelSpec.CAPABILITY_TEXT_EMBEDDINGS);
            case WALLPAPER_DEPTH:
                return caps.contains(TaiModelSpec.CAPABILITY_DEPTH_ESTIMATION);
            case WALLPAPER_READER:
                return isChat(caps) && caps.contains(TaiModelSpec.CAPABILITY_IMAGE_INPUT);
            default:
                return isChat(caps);
        }
    }

    /** A chat model: not a speech, voice, embedding, depth or image-generation model. */
    private static boolean isChat(Set<String> caps) {
        return caps.contains(TaiModelSpec.CAPABILITY_TEXT_CHAT)
            && !caps.contains(TaiModelSpec.CAPABILITY_SPEECH_TO_TEXT)
            && !caps.contains(TaiModelSpec.CAPABILITY_TEXT_TO_SPEECH)
            && !caps.contains(TaiModelSpec.CAPABILITY_IMAGE_GENERATION);
    }

    @NonNull
    private static String normalizePick(TaiFunction function, @Nullable String value) {
        if (value == null) return "";
        String trimmed = value.trim();
        if (trimmed.isEmpty() || VALUE_OFF.equals(trimmed) || isRemote(trimmed)) return trimmed;
        return TaiSettings.migrateBuiltInModelId(baseId(trimmed));
    }

    /** The id without a {@code -vision} suffix: the file the variant shares. */
    @NonNull
    static String baseId(@NonNull String modelId) {
        return modelId.endsWith(TaiModelVariants.SUFFIX_VISION)
            ? modelId.substring(0, modelId.length() - TaiModelVariants.SUFFIX_VISION.length()) : modelId;
    }
}
