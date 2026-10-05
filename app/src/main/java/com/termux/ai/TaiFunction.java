package com.termux.ai;

import androidx.annotation.NonNull;

import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants;

import java.util.Locale;

/**
 * The things the on-device AI does for the user, each with one model pick (tai-device-tiers spec
 * §4.5). {@link TaiFunctionModels} owns every pick and resolves it; {@link TaiTierPolicy} says what
 * Automatic means for each on a given phone.
 *
 * <p>Existing preference keys are kept, so picks made before the tiers survive; the rest follow
 * {@code tai_fn_<function>_model} and {@code tai_fn_<function>_accel}.
 */
public enum TaiFunction {
    /** The assistant and the {@code /v1} endpoint. */
    ASSISTANT(TaiSettings.KEY_ROLE_DEFAULT_ASSISTANT, Store.TAI, TaiTierPolicy.WithoutModel.NONE),
    /** Voice typing: speech to text. */
    VOICE_TYPING(TaiSettings.KEY_STT_MODEL_ID, Store.TAI, TaiTierPolicy.WithoutModel.NONE),
    /** Tidy dictation: the keyboard's cleanup pass after speaking. Lives in the keyboard preferences. */
    TIDY_DICTATION(TermuxPreferenceConstants.TERMUX_APP.KEY_IN_APP_KEYBOARD_VOICE_POLISH_MODEL, Store.KEYBOARD,
        TaiTierPolicy.WithoutModel.RAW_TEXT),
    /** Read aloud: text to speech. */
    READ_ALOUD(null, Store.TAI, TaiTierPolicy.WithoutModel.NONE),
    /** Sorting apps into categories. */
    APP_CATEGORIES(null, Store.TAI, TaiTierPolicy.WithoutModel.OFF),
    /** The wallpaper creator's reader, which looks at the photo (a {@code -vision} model). API 34+. */
    WALLPAPER_READER(null, Store.TAI, TaiTierPolicy.WithoutModel.RULES_ONLY),
    /** The wallpaper creator's depth model. API 34+. */
    WALLPAPER_DEPTH(TaiVisionModels.PREF_DEPTH_MODEL, Store.TAI, TaiTierPolicy.WithoutModel.NONE),
    /** Dawn notes integration: the embedder (Dawn notes is the only app that uses it). */
    EMBEDDINGS(null, Store.TAI, TaiTierPolicy.WithoutModel.NONE);

    /** Which preference file holds the pick. */
    enum Store { TAI, KEYBOARD }

    /** Where the pick is stored: an existing key, or {@code tai_fn_<function>_model}. */
    @NonNull public final String modelKey;
    /** Where the accelerator pick ({@code ""}, {@code cpu}, {@code gpu}) is stored. */
    @NonNull public final String accelKey;
    final Store store;
    /** What the function does when it has no model: rules only, raw text, off, or nothing at all. */
    @NonNull public final TaiTierPolicy.WithoutModel withoutModel;

    TaiFunction(String existingKey, Store store, TaiTierPolicy.WithoutModel withoutModel) {
        String name = name().toLowerCase(Locale.ROOT);
        this.modelKey = existingKey != null ? existingKey : "tai_fn_" + name + "_model";
        this.accelKey = "tai_fn_" + name + "_accel";
        this.store = store;
        this.withoutModel = withoutModel;
    }

    /** The wallpaper functions need API 34 (the renderer is HardwareBufferRenderer) and are removed below it. */
    public boolean needsApi34() {
        return this == WALLPAPER_READER || this == WALLPAPER_DEPTH;
    }

    /** True for the functions that run a chat model and so choose GPU or CPU. */
    public boolean usesChatModel() {
        return this == ASSISTANT || this == TIDY_DICTATION || this == APP_CATEGORIES || this == WALLPAPER_READER;
    }
}
