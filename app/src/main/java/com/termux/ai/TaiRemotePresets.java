package com.termux.ai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The providers the remote model screen offers by name: pick one, paste a key, and the address
 * and a starting model come from here. Four presets and Custom, which is the free-text address.
 * Pure: no Android, no I/O.
 */
public final class TaiRemotePresets {
    public static final String ID_GOOGLE = "google";
    public static final String ID_OPENROUTER = "openrouter";
    public static final String ID_GROQ = "groq";
    public static final String ID_MISTRAL = "mistral";
    public static final String ID_CUSTOM = "custom";

    /** Gemini's model list names each model {@code models/<id>}; its chat endpoint takes the bare id. */
    static final String GEMINI_LIST_PREFIX = "models/";

    /** One provider. Custom has no address, model or key page of its own. */
    public static final class Preset {
        @NonNull public final String id;
        /** The provider's own name; not translated. Empty for Custom, whose label is a resource. */
        @NonNull public final String displayName;
        /** The OpenAI-compatible base, without a trailing slash; empty for Custom. */
        @NonNull public final String baseUrl;
        /** The model stored when the provider lists it after the key is saved; empty for Custom. */
        @NonNull public final String model;
        /** Where the user creates a key; empty for Custom. */
        @NonNull public final String keyPageUrl;
        /** The free plan may use what is sent to train its models: the screen says so. */
        public final boolean freePlanTrains;

        Preset(@NonNull String id, @NonNull String displayName, @NonNull String baseUrl, @NonNull String model,
               @NonNull String keyPageUrl, boolean freePlanTrains) {
            this.id = id;
            this.displayName = displayName;
            this.baseUrl = baseUrl;
            this.model = model;
            this.keyPageUrl = keyPageUrl;
            this.freePlanTrains = freePlanTrains;
        }

        public boolean isCustom() {
            return ID_CUSTOM.equals(id);
        }
    }

    public static final Preset GOOGLE = new Preset(ID_GOOGLE, "Google AI Studio",
        "https://generativelanguage.googleapis.com/v1beta/openai", "gemini-flash-lite-latest",
        "https://aistudio.google.com/apikey", true);
    public static final Preset OPENROUTER = new Preset(ID_OPENROUTER, "OpenRouter",
        "https://openrouter.ai/api/v1", "openrouter/free", "https://openrouter.ai/keys", false);
    public static final Preset GROQ = new Preset(ID_GROQ, "Groq",
        "https://api.groq.com/openai/v1", "llama-3.3-70b-versatile", "https://console.groq.com/keys", false);
    public static final Preset MISTRAL = new Preset(ID_MISTRAL, "Mistral",
        "https://api.mistral.ai/v1", "mistral-small-latest", "https://console.mistral.ai/api-keys", true);
    public static final Preset CUSTOM = new Preset(ID_CUSTOM, "", "", "", "", false);

    /** In the order the provider list shows them, Custom last. */
    public static final List<Preset> ALL =
        Collections.unmodifiableList(Arrays.asList(GOOGLE, OPENROUTER, GROQ, MISTRAL, CUSTOM));

    private TaiRemotePresets() {}

    /** The preset with this id, or {@code null} for an unknown or empty one. */
    @Nullable
    public static Preset byId(@Nullable String id) {
        if (id == null) return null;
        for (Preset preset : ALL) if (preset.id.equals(id)) return preset;
        return null;
    }

    /**
     * What a saved address reads as when no provider was chosen yet (installs from before this
     * screen had providers): the preset with that address, ignoring a trailing slash and case;
     * Custom for any other address; {@code null} when there is no address at all.
     */
    @Nullable
    public static Preset forBaseUrl(@Nullable String url) {
        String value = TaiRemoteClient.normalizeBaseUrl(url);
        if (value.isEmpty()) return null;
        for (Preset preset : ALL) {
            if (!preset.isCustom() && preset.baseUrl.equalsIgnoreCase(value)) return preset;
        }
        return CUSTOM;
    }

    /**
     * The id to store for {@code model} when the provider's model list carries it, else
     * {@code null}. Gemini lists {@code models/<id>}; either spelling counts, and the bare id is
     * what is stored because the chat endpoint takes it.
     */
    @Nullable
    public static String listedModel(@NonNull List<String> listedIds, @NonNull String model) {
        if (model.isEmpty()) return null;
        for (String id : listedIds) {
            if (id.equals(model) || id.equals(GEMINI_LIST_PREFIX + model)) return model;
        }
        return null;
    }
}
