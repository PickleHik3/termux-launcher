package com.termux.app.fragments.settings.termux;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.ai.TaiFunction;
import com.termux.ai.TaiFunctionModels;
import com.termux.ai.TaiFunctionModels.ModelInfo;
import com.termux.ai.TaiFunctionModels.Resolution;
import com.termux.ai.TaiModelSpec;
import com.termux.ai.TaiTierPolicy;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * What the Model Centre's Functions, Installed and Get models segments say, decided from the
 * policy and the resolver alone (tai-device-tiers spec §4.2): the device header, one row per
 * function with its pick and where it runs, "Used by" and the delete warning, the fit line and the
 * grouping of the catalogue. No Android types, so the wording rules are unit tested; the words
 * themselves come through {@link Labels}, which the fragment backs with string resources.
 */
final class TaiFunctionRows {
    private TaiFunctionRows() {}

    private static final long GIB = 1024L * 1024L * 1024L;
    private static final String VISION = "-vision";
    /** The order of the Functions rows. */
    static final List<TaiFunction> ROW_FUNCTIONS = Collections.unmodifiableList(Arrays.asList(
        TaiFunction.ASSISTANT, TaiFunction.VOICE_TYPING, TaiFunction.TIDY_DICTATION, TaiFunction.READ_ALOUD,
        TaiFunction.APP_CATEGORIES, TaiFunction.EMBEDDINGS));

    /** The sentences the rows are built from; the fragment maps each to a string resource. */
    enum Msg {
        /** "Automatic (%s)". */
        AUTOMATIC,
        /** "Automatic": the tier has no choice to name. */
        AUTOMATIC_BARE,
        RULES_ONLY, RAW_TEXT, OFF,
        /** "Not set · Add a model". */
        NOT_SET,
        /** "Remote · %s". */
        REMOTE,
        /** "Set up a remote model". */
        REMOTE_SETUP,
        GPU, CPU,
        FIT_FITS, FIT_ROOM, FIT_BIGGER,
        /** "Used by: %s". */
        USED_BY,
        /** "For: %s". */
        FOR,
        /** "If it can't load: %s". */
        CHAIN,
        /** "In use by %s. If you delete it:". */
        DELETE_IN_USE,
        /** "%1$s: %2$s". */
        DELETE_LINE,
        LEVEL_LIGHT, LEVEL_POLISHED
    }

    /** Words for the rows. {@code args} fill the {@code %s} slots of the message. */
    interface Labels {
        @NonNull String text(@NonNull Msg msg, @NonNull Object... args);

        /** "Assistant and endpoint", "Voice typing", .... */
        @NonNull String functionName(@NonNull TaiFunction function);

        /** The model's name as people read it; a {@code -vision} id and the file are the same name. */
        @NonNull String modelName(@NonNull String modelId);
    }

    // ---------------------------------------------------------------------------------- header

    /** "Tier 2 · 12 GB · Snapdragon 8+ Gen 1 · Android 16"; a part the phone does not know is left out. */
    @NonNull
    static String deviceLine(@NonNull TaiTierPolicy.Env env, @Nullable String soc, @Nullable String androidRelease) {
        List<String> parts = new ArrayList<>();
        parts.add("Tier " + env.tier.number());
        if (env.ramClassBytes > 0L) parts.add(Math.round((double) env.ramClassBytes / GIB) + " GB");
        if (soc != null && !soc.trim().isEmpty()) parts.add(soc.trim());
        if (androidRelease != null && !androidRelease.trim().isEmpty()) parts.add("Android " + androidRelease.trim());
        return join(parts, " · ");
    }

    // ----------------------------------------------------------------------------- function rows

    /** One Functions row. */
    static final class FunctionRow {
        @NonNull final TaiFunction function;
        @NonNull final String name;
        /** The pick and where it runs: "Automatic (Gemma 4 E2B) · GPU", "Rules only", "Not set · Add a model". */
        @NonNull final String summary;
        /** "May close apps running in the background" belongs under the row. */
        final boolean warnBackground;
        /** Tidy dictation's level ("Polished"), else empty; shown at the end of the title. */
        @NonNull final String badge;

        FunctionRow(@NonNull TaiFunction function, @NonNull String name, @NonNull String summary,
                    boolean warnBackground, @NonNull String badge) {
            this.function = function;
            this.name = name;
            this.summary = summary;
            this.warnBackground = warnBackground;
            this.badge = badge;
        }

        @NonNull
        String signature() {
            return function + "|" + name + "|" + summary + "|" + warnBackground + "|" + badge;
        }
    }

    /**
     * The rows for the functions; image generation has none (spec §3.6). {@code tidyLevel} is the stored cleanup level ({@code light} or
     * {@code polished}).
     */
    @NonNull
    static List<FunctionRow> functionRows(@NonNull TaiFunctionModels models, @NonNull Labels labels,
                                          @Nullable String tidyLevel) {
        List<FunctionRow> rows = new ArrayList<>();
        for (TaiFunction function : ROW_FUNCTIONS) {
            Resolution resolution = models.resolve(function);
            String summary = summary(resolution, labels);
            String badge = "";
            if (function == TaiFunction.TIDY_DICTATION) {
                badge = labels.text("light".equals(tidyLevel) ? Msg.LEVEL_LIGHT : Msg.LEVEL_POLISHED);
            }
            rows.add(new FunctionRow(function, labels.functionName(function), summary, resolution.warnBackground, badge));
        }
        return rows;
    }

    /**
     * What a resolution reads as: "Automatic (Gemma 4 E2B) · GPU", "Gemma 4 E4B · CPU" for a pick,
     * "Remote · gpt-x", "Rules only", "Raw text", "Off", or "Not set · Add a model" when nothing is usable.
     */
    @NonNull
    static String summary(@NonNull Resolution resolution, @NonNull Labels labels) {
        if (resolution.source == TaiFunctionModels.Source.NONE) return labels.text(Msg.NOT_SET);
        String id = resolution.modelId;
        if (id == null) {
            switch (resolution.without) {
                case RULES_ONLY: return labels.text(Msg.RULES_ONLY);
                case RAW_TEXT: return labels.text(Msg.RAW_TEXT);
                case OFF: return labels.text(Msg.OFF);
                default: return labels.text(Msg.NOT_SET);
            }
        }
        if (TaiFunctionModels.isRemote(id)) {
            return labels.text(Msg.REMOTE, id.substring(TaiFunctionModels.REMOTE_PREFIX.length()));
        }
        String name = labels.modelName(id);
        boolean explicit = resolution.source == TaiFunctionModels.Source.PICK;
        String head = explicit ? name : labels.text(Msg.AUTOMATIC, name);
        String where = whereLine(resolution.accelerator, labels);
        return where.isEmpty() ? head : head + " · " + where;
    }

    @NonNull
    private static String whereLine(@Nullable String accelerator, @NonNull Labels labels) {
        if (TaiTierPolicy.ACCEL_GPU.equals(accelerator)) return labels.text(Msg.GPU);
        if (TaiTierPolicy.ACCEL_CPU.equals(accelerator)) return labels.text(Msg.CPU);
        return "";
    }

    // ------------------------------------------------------------------------------ fit and use

    /** How a model sits on this phone, the line under it in the picker and in Get models. */
    enum Fit { FITS, ROOM, BIGGER, NONE }

    /**
     * Spec §3 and §4.2: ticked or suggested models "fit this phone"; one whose file is a quarter of the
     * RAM or more fits "when the phone has room"; a listed one is "made for bigger phones"; one the
     * policy does not name (imported, SegFormer) has no line.
     */
    @NonNull
    static Fit fit(@NonNull TaiTierPolicy.Env env, @NonNull String modelId, long sizeBytes) {
        switch (TaiTierPolicy.offer(env, stripVision(modelId))) {
            case PRESELECTED:
                return Fit.FITS;
            case SUGGESTED:
                return TaiTierPolicy.warnsBackground(env, sizeBytes) ? Fit.ROOM : Fit.FITS;
            case LISTED:
                return Fit.BIGGER;
            default:
                return Fit.NONE;
        }
    }

    @NonNull
    static String fitLine(@NonNull Fit fit, @NonNull Labels labels) {
        switch (fit) {
            case FITS: return labels.text(Msg.FIT_FITS);
            case ROOM: return labels.text(Msg.FIT_ROOM);
            case BIGGER: return labels.text(Msg.FIT_BIGGER);
            default: return "";
        }
    }

    /** True for a model the policy suggests on this phone. Never for a chat model on Tier 1 (spec §3). */
    static boolean suggested(@NonNull TaiTierPolicy.Env env, @NonNull String modelId, boolean chatModel) {
        if (chatModel && env.tier == com.termux.ai.TaiDeviceTier.TIER_1) return false;
        TaiTierPolicy.Offer offer = TaiTierPolicy.offer(env, stripVision(modelId));
        return offer == TaiTierPolicy.Offer.PRESELECTED || offer == TaiTierPolicy.Offer.SUGGESTED;
    }

    @NonNull
    static String stripVision(@NonNull String modelId) {
        return modelId.endsWith(VISION) ? modelId.substring(0, modelId.length() - VISION.length()) : modelId;
    }

    /** "Used by: Assistant and endpoint, Tidy dictation", or empty when no function uses the model. */
    @NonNull
    static String usedByLine(@NonNull List<TaiFunction> functions, @NonNull Labels labels) {
        Set<String> names = functionNames(functions, labels);
        return names.isEmpty() ? "" : labels.text(Msg.USED_BY, join(names, ", "));
    }

    /**
     * The functions a model can serve on this phone, for "For:" and the Use for... chooser.
     */
    @NonNull
    static List<TaiFunction> servedBy(@NonNull TaiTierPolicy.Env env, @NonNull ModelInfo info) {
        List<TaiFunction> out = new ArrayList<>();
        for (TaiFunction function : TaiFunction.values()) {
            if (function.sharesAnotherPick()) continue; // picked through the feature it shares
            if (TaiTierPolicy.platformAllows(env, function) && TaiFunctionModels.canServe(function, info)) out.add(function);
        }
        return out;
    }

    /** "For: Assistant and endpoint, Tidy dictation": what a catalogue model can serve; empty for none. */
    @NonNull
    static String forLine(@NonNull TaiTierPolicy.Env env, @NonNull ModelInfo info, @NonNull Labels labels) {
        Set<String> names = functionNames(servedBy(env, info), labels);
        return names.isEmpty() ? "" : labels.text(Msg.FOR, join(names, ", "));
    }

    @NonNull
    private static Set<String> functionNames(@NonNull Collection<TaiFunction> functions, @NonNull Labels labels) {
        Set<String> names = new LinkedHashSet<>();
        for (TaiFunction function : functions) names.add(labels.functionName(function));
        return names;
    }

    // ------------------------------------------------------------------------------ delete warning

    /**
     * What deleting {@code modelId} costs: the functions that use it and what each falls back to
     * ({@link TaiFunctionModels#resolveWithout}). Empty when no function uses it, and the plain
     * confirmation stands.
     */
    @NonNull
    static String deleteWarning(@NonNull TaiFunctionModels models, @NonNull String modelId, @NonNull Labels labels) {
        List<TaiFunction> users = models.usedBy(modelId);
        if (users.isEmpty()) return "";
        StringBuilder text = new StringBuilder(labels.text(Msg.DELETE_IN_USE, join(functionNames(users, labels), ", ")));
        for (TaiFunction function : users) {
            Resolution after = models.resolveWithout(function, modelId);
            text.append('\n').append(labels.text(Msg.DELETE_LINE, labels.functionName(function), summary(after, labels)));
        }
        return text.toString();
    }

    // ---------------------------------------------------------------------------------- Get models

    /** The catalogue groups of the Get models segment, in display order. Image generation has none. */
    enum Group { ASSISTANTS, SPEECH, VOICE_OUTPUT, SEARCH }

    /** A catalogue entry as the grouping reads it; built from a {@code CatalogEntry} by the fragment. */
    static final class CatalogItem {
        @NonNull final ModelInfo info;
        final boolean downloadAvailable;

        CatalogItem(@NonNull ModelInfo info, boolean downloadAvailable) {
            this.info = info;
            this.downloadAvailable = downloadAvailable;
        }
    }

    /** One Get models row. */
    static final class GetEntry {
        @NonNull final Group group;
        @NonNull final CatalogItem item;
        @NonNull final Fit fit;
        final boolean suggested;

        GetEntry(@NonNull Group group, @NonNull CatalogItem item, @NonNull Fit fit, boolean suggested) {
            this.group = group;
            this.item = item;
            this.fit = fit;
            this.suggested = suggested;
        }
    }

    /** The group a model belongs to, or {@code null} for one that never shows (image generation, vision tools). */
    @Nullable
    static Group groupOf(@NonNull ModelInfo info) {
        Set<String> caps = info.capabilities;
        if (caps.contains(TaiModelSpec.CAPABILITY_IMAGE_GENERATION) || TaiModelSpec.BACKEND_MNN_DIFFUSION.equals(info.backend)) return null;
        if (caps.contains(TaiModelSpec.CAPABILITY_SPEECH_TO_TEXT)) return Group.SPEECH;
        if (caps.contains(TaiModelSpec.CAPABILITY_TEXT_TO_SPEECH)) return Group.VOICE_OUTPUT;
        if (TaiModelSpec.isVisionTool(caps)) return null;
        if (caps.contains(TaiModelSpec.CAPABILITY_TEXT_EMBEDDINGS) && !caps.contains(TaiModelSpec.CAPABILITY_TEXT_CHAT)) return Group.SEARCH;
        return caps.contains(TaiModelSpec.CAPABILITY_TEXT_CHAT) ? Group.ASSISTANTS : null;
    }

    /**
     * The catalogue for the Get models segment: grouped, in the catalogue's order inside a group,
     * without what is installed or on its way ({@code skip}), without image generation. Empty
     * groups are absent.
     */
    @NonNull
    static Map<Group, List<GetEntry>> getModels(@NonNull TaiTierPolicy.Env env, @NonNull Collection<CatalogItem> catalogue,
                                                @NonNull Set<String> skip) {
        Map<Group, List<GetEntry>> out = new EnumMap<>(Group.class);
        if (!env.localModelsSupported()) return out;
        for (CatalogItem item : catalogue) {
            ModelInfo info = item.info;
            Group group = groupOf(info);
            if (group == null || skip.contains(info.id)) continue;
            boolean chat = group == Group.ASSISTANTS;
            GetEntry entry = new GetEntry(group, item, fit(env, info.id, info.sizeBytes), suggested(env, info.id, chat));
            List<GetEntry> list = out.get(group);
            if (list == null) {
                list = new ArrayList<>();
                out.put(group, list);
            }
            list.add(entry);
        }
        return out;
    }

    // --------------------------------------------------------------------------------- the chain

    /** "If it can't load: Gemma 4 E2B → rules only", or empty when the function has no chain. */
    @NonNull
    static String chainLine(@NonNull List<TaiTierPolicy.Choice> chain, @NonNull Labels labels) {
        List<String> links = new ArrayList<>();
        for (TaiTierPolicy.Choice link : chain) {
            if (link.isModel()) {
                links.add(labels.modelName(link.modelId));
                continue;
            }
            switch (link.without) {
                case RULES_ONLY: links.add(lower(labels.text(Msg.RULES_ONLY))); break;
                case RAW_TEXT: links.add(lower(labels.text(Msg.RAW_TEXT))); break;
                case OFF: links.add(lower(labels.text(Msg.OFF))); break;
                default: break;
            }
        }
        return links.isEmpty() ? "" : labels.text(Msg.CHAIN, join(links, " → "));
    }

    @NonNull
    private static String lower(@NonNull String text) {
        return text.toLowerCase(Locale.getDefault());
    }

    @NonNull
    static String join(@NonNull Collection<String> parts, @NonNull String separator) {
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            if (out.length() > 0) out.append(separator);
            out.append(part);
        }
        return out.toString();
    }
}
