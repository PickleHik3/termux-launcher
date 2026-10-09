package com.termux.ai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants;

import java.util.Locale;

/**
 * The things the on-device AI does for the user, each with one model pick (tai-device-tiers spec
 * §4.5). {@link TaiFunctionModels} owns every pick and resolves it; {@link TaiTierPolicy} says what
 * Automatic means for each on a given phone; {@link TaiFeaturePlan} says how each one loads.
 *
 * <p>Existing preference keys are kept, so picks made before the tiers survive; the rest follow
 * {@code tai_fn_<function>_model} and {@code tai_fn_<function>_accel}.
 *
 * <p>A request names its feature with {@code "function": "<id>"} ({@link #id()}, {@link #fromId}).
 */
public enum TaiFunction {
    /** The assistant and the {@code /v1} endpoint. */
    ASSISTANT(TaiSettings.KEY_ROLE_DEFAULT_ASSISTANT, null, Store.TAI, TaiTierPolicy.WithoutModel.NONE),
    /** Voice typing: speech to text. */
    VOICE_TYPING(TaiSettings.KEY_STT_MODEL_ID, null, Store.TAI, TaiTierPolicy.WithoutModel.NONE),
    /** Tidy dictation: the keyboard's cleanup pass after speaking. Lives in the keyboard preferences. */
    TIDY_DICTATION(TermuxPreferenceConstants.TERMUX_APP.KEY_IN_APP_KEYBOARD_VOICE_POLISH_MODEL, null, Store.KEYBOARD,
        TaiTierPolicy.WithoutModel.RAW_TEXT),
    /** Read aloud: text to speech. */
    READ_ALOUD(null, null, Store.TAI, TaiTierPolicy.WithoutModel.NONE),
    /** Sorting apps into categories. */
    APP_CATEGORIES(null, null, Store.TAI, TaiTierPolicy.WithoutModel.OFF),
    /** Dawn search: the embedder of the Dawn notes integration (Dawn notes is the only app that uses it). */
    EMBEDDINGS(null, null, Store.TAI, TaiTierPolicy.WithoutModel.NONE),
    /**
     * Dawn chat: Dawn notes asking a chat model about the notes it found. It runs on the assistant's
     * pick (the same keys, so it has no picker of its own) and differs only in how it loads: it stays
     * loaded beside Dawn search ({@link TaiFeaturePlan#groupsOf}).
     */
    DAWN_CHAT(TaiSettings.KEY_ROLE_DEFAULT_ASSISTANT, "tai_fn_assistant_accel", Store.TAI, TaiTierPolicy.WithoutModel.NONE);

    /** Which preference file holds the pick. */
    enum Store { TAI, KEYBOARD }

    /** Where the pick is stored: an existing key, or {@code tai_fn_<function>_model}. */
    @NonNull public final String modelKey;
    /** Where the accelerator pick ({@code ""}, {@code cpu}, {@code gpu}) is stored. */
    @NonNull public final String accelKey;
    final Store store;
    /** What the function does when it has no model: rules only, raw text, off, or nothing at all. */
    @NonNull public final TaiTierPolicy.WithoutModel withoutModel;

    TaiFunction(String existingKey, String existingAccelKey, Store store, TaiTierPolicy.WithoutModel withoutModel) {
        String name = name().toLowerCase(Locale.ROOT);
        this.modelKey = existingKey != null ? existingKey : "tai_fn_" + name + "_model";
        this.accelKey = existingAccelKey != null ? existingAccelKey : "tai_fn_" + name + "_accel";
        this.store = store;
        this.withoutModel = withoutModel;
    }

    /** True for the functions that run a chat model and so choose GPU or CPU. */
    public boolean usesChatModel() {
        return this == ASSISTANT || this == TIDY_DICTATION || this == APP_CATEGORIES || this == DAWN_CHAT;
    }

    /** True for a feature that runs on another's pick, and so has no picker or Model Centre row of its own. */
    public boolean sharesAnotherPick() {
        return this == DAWN_CHAT;
    }

    /** The id a request names it by: the lowercase name, e.g. {@code tidy_dictation}. */
    @NonNull
    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * The feature a request names: its {@link #id()}, or the glossary's word for it ({@code cleanup},
     * {@code app_sorting}, {@code dawn_search}); {@code null} for anything else.
     */
    @Nullable
    public static TaiFunction fromId(@Nullable String id) {
        if (id == null) return null;
        String value = id.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        switch (value) {
            case "cleanup": return TIDY_DICTATION;
            case "app_sorting": return APP_CATEGORIES;
            case "dawn_search": return EMBEDDINGS;
            default:
                for (TaiFunction function : values()) {
                    if (function.id().equals(value)) return function;
                }
                return null;
        }
    }
}
