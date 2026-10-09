package com.termux.ai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Exposes a single multimodal LiteRT-LM model as several modality-scoped virtual models on the
 * OpenAI surface, mirroring Google AI Edge Gallery's per-task model loading.
 *
 * <p>Gallery initializes the engine with exactly one modality enabled per task
 * (see {@code LlmChatTaskModule}): chat &rarr; {@code supportImage=false, supportAudio=false};
 * ask-image &rarr; {@code supportImage=true, supportAudio=false}; ask-audio &rarr;
 * {@code supportImage=false, supportAudio=true}. Loading a single modality keeps the GPU/OpenCL
 * footprint small enough to fit, instead of spinning up every encoder at once.
 *
 * <p>We reproduce that by advertising the same physical model file under up to three ids:
 * <ul>
 *   <li>{@code "<id>"} &mdash; text-only chat (the canonical id)</li>
 *   <li>{@code "<id>-vision"} &mdash; text + image</li>
 *   <li>{@code "<id>-audio"} &mdash; text + audio</li>
 * </ul>
 *
 * <p>Variants are <b>virtual</b>: they share one downloaded file. {@link TaiModelStore} keys storage
 * on the model id, so real per-modality catalog ids would download the weights multiple times. A
 * scoped spec keeps the physical {@code localPath}, runtime profile, size and sha of the base model
 * and only narrows the advertised capabilities. Those narrowed capabilities are what gate the
 * encoders in {@link LiteRtTaiRuntime} when the engine is built.
 */
final class TaiModelVariants {
    static final String SUFFIX_VISION = "-vision";
    static final String SUFFIX_AUDIO = "-audio";
    static final String SUFFIX_TEXT = "-text";

    enum Scope { TEXT, IMAGE, AUDIO }

    /** How a multimodal model is advertised on /v1/models. */
    enum Exposure {
        SPLIT,    // bare id = text; plus -vision/-audio (lowest RAM; default)
        COMBINED, // bare id = all enabled modalities at once
        BOTH;     // bare id = combined; plus -text/-vision/-audio splits
        @NonNull
        static Exposure fromValue(@Nullable String value) {
            if (TaiModelStore.EXPOSURE_COMBINED.equals(value)) return COMBINED;
            if (TaiModelStore.EXPOSURE_BOTH.equals(value)) return BOTH;
            return SPLIT;
        }
    }

    /** Resolves a base (physical) model id to its installed spec, or {@code null} when unknown. */
    interface Lookup {
        @Nullable TaiModelSpec find(@Nullable String modelId);
    }

    private TaiModelVariants() {}

    /** True when {@code spec} is a LiteRT-LM model that advertises image and/or audio input. */
    static boolean isVariantCapable(@Nullable TaiModelSpec spec) {
        if (spec == null) return false;
        if (!TaiModelSpec.BACKEND_LITERT_LM.equals(spec.backend)) return false;
        return supportsImage(spec) || supportsAudio(spec);
    }

    private static boolean supportsImage(@NonNull TaiModelSpec spec) {
        return spec.sourceCapabilities.contains(TaiModelSpec.CAPABILITY_IMAGE_INPUT)
            || spec.capabilities.contains(TaiModelSpec.CAPABILITY_IMAGE_INPUT);
    }

    private static boolean supportsAudio(@NonNull TaiModelSpec spec) {
        return spec.sourceCapabilities.contains(TaiModelSpec.CAPABILITY_AUDIO_INPUT)
            || spec.capabilities.contains(TaiModelSpec.CAPABILITY_AUDIO_INPUT);
    }

    /**
     * Expand a physical spec into the modality-scoped specs to advertise on {@code /v1/models}.
     * Non-multimodal (or non-LiteRT) models are returned unchanged as a single-element list.
     */
    @NonNull
    static List<TaiModelSpec> expand(@NonNull TaiModelSpec spec) {
        return expand(spec, Exposure.SPLIT);
    }

    @NonNull
    static List<TaiModelSpec> expand(@NonNull TaiModelSpec spec, @NonNull Exposure exposure) {
        if (!isVariantCapable(spec)) return Collections.singletonList(spec);
        List<TaiModelSpec> out = new ArrayList<>();
        if (exposure == Exposure.COMBINED) {
            out.add(spec);                                  // bare id loads every enabled modality
            return out;
        }
        if (exposure == Exposure.BOTH) {
            out.add(spec);                                  // bare id = combined
            out.add(scoped(spec, spec.id + SUFFIX_TEXT, Scope.TEXT));
        } else {
            out.add(scoped(spec, spec.id, Scope.TEXT));     // SPLIT: bare id = text
        }
        if (supportsImage(spec)) out.add(scoped(spec, spec.id + SUFFIX_VISION, Scope.IMAGE));
        if (supportsAudio(spec)) out.add(scoped(spec, spec.id + SUFFIX_AUDIO, Scope.AUDIO));
        return out;
    }

    /**
     * The canonical id of a multimodal model loads text-only. This is cheaper than, not a copy
     * of, Gallery's chat task: Gallery's AI Chat task passes {@code supportImage}/{@code
     * supportAudio} from the model, so its chat loads both encoders for Gemma 4
     * (Gallery {@code LlmChatTaskModule}, {@code supportImage = model.supportImage, supportAudio =
     * model.supportAudio}). Every other model (text-only, embeddings, MNN, imported) is returned
     * unchanged.
     */
    @Nullable
    static TaiModelSpec chatScopedOrSelf(@Nullable TaiModelSpec spec) {
        if (spec == null) return null;
        if (!isVariantCapable(spec)) return spec;
        return scoped(spec, spec.id, Scope.TEXT);
    }

    /**
     * Resolve a {@code "<base>-vision"}/{@code "<base>-audio"} id to a scoped spec, or {@code null}
     * when the id is not a recognised variant of an installed multimodal model.
     */
    @Nullable
    static TaiModelSpec resolve(@Nullable String requestedId, @NonNull Lookup lookup) {
        if (requestedId == null) return null;
        if (requestedId.endsWith(SUFFIX_TEXT)) {
            TaiModelSpec base = lookup.find(baseId(requestedId, SUFFIX_TEXT));
            return base != null && isVariantCapable(base) ? scoped(base, requestedId, Scope.TEXT) : null;
        }
        if (requestedId.endsWith(SUFFIX_VISION)) {
            TaiModelSpec base = lookup.find(baseId(requestedId, SUFFIX_VISION));
            if (base != null && isVariantCapable(base) && supportsImage(base)) {
                return scoped(base, requestedId, Scope.IMAGE);
            }
            return null;
        }
        if (requestedId.endsWith(SUFFIX_AUDIO)) {
            TaiModelSpec base = lookup.find(baseId(requestedId, SUFFIX_AUDIO));
            if (base != null && isVariantCapable(base) && supportsAudio(base)) {
                return scoped(base, requestedId, Scope.AUDIO);
            }
            return null;
        }
        return null;
    }

    @NonNull
    private static String baseId(@NonNull String id, @NonNull String suffix) {
        return id.substring(0, id.length() - suffix.length());
    }

    /**
     * Strips the {@code -vision}/{@code -audio} modality suffix so a variant id maps back to the
     * underlying model. Returns {@code id} unchanged when it is already a base id. Used so
     * device/model state that is shared across modalities (e.g. GPU load stability) is keyed by the
     * underlying model rather than each virtual variant.
     */
    @NonNull
    static String baseModelId(@NonNull String id) {
        if (id.endsWith(SUFFIX_VISION)) return baseId(id, SUFFIX_VISION);
        if (id.endsWith(SUFFIX_AUDIO)) return baseId(id, SUFFIX_AUDIO);
        if (id.endsWith(SUFFIX_TEXT)) return baseId(id, SUFFIX_TEXT);
        return id;
    }

    @NonNull
    private static TaiModelSpec scoped(@NonNull TaiModelSpec base, @NonNull String variantId, @NonNull Scope scope) {
        Set<String> source = scopeCapabilities(base.sourceCapabilities, scope);
        Set<String> endpoint = scopeCapabilities(base.endpointCapabilities, scope);
        return new TaiModelSpec(
            variantId,
            displayName(base.displayName, scope),
            base.roleHint,
            base.source,
            base.localPath,
            base.license,
            base.sizeBytes,
            source,
            base.builtInCatalogEntry,
            base.runtimeProfile,
            base.backend,
            base.format,
            base.architecture,
            base.quantization,
            base.endpointContextWindow,
            base.sourceContextWindow,
            base.defaultMaxOutputTokens,
            base.recommendedRamGb,
            base.sha256,
            endpoint,
            base.toolMode
        );
    }

    @NonNull
    private static Set<String> scopeCapabilities(@NonNull Set<String> capabilities, @NonNull Scope scope) {
        LinkedHashSet<String> scoped = new LinkedHashSet<>(capabilities);
        if (scope != Scope.IMAGE) scoped.remove(TaiModelSpec.CAPABILITY_IMAGE_INPUT);
        if (scope != Scope.AUDIO) scoped.remove(TaiModelSpec.CAPABILITY_AUDIO_INPUT);
        return scoped;
    }

    @NonNull
    private static String displayName(@NonNull String base, @NonNull Scope scope) {
        switch (scope) {
            case IMAGE:
                return base + " (Vision)";
            case AUDIO:
                return base + " (Audio)";
            case TEXT:
            default:
                return base;
        }
    }
}
