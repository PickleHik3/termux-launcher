package com.termux.app.settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;
import com.termux.shared.termux.settings.registry.SettingAdapter;
import com.termux.shared.termux.settings.registry.SettingDeclaration;
import com.termux.shared.termux.settings.registry.SettingRefresh;
import com.termux.shared.termux.settings.registry.SettingsRegistry;

import java.util.Collections;
import java.util.List;

/**
 * The launcher keyboard's Key spacing as a setting ({@code keyboard.key_spacing}), in tenths of the
 * key margin scale the keyboard keeps as a decimal: the Keyboard card's slider and launcherctl write
 * the same number. An adapter, as the store holds a float the registry's integers cannot name.
 */
public final class KeySpacingAdapter implements SettingAdapter {

    /** The id the registry lists it under. */
    public static final String ID = "keyboard.key_spacing";

    /** Where the margin scale is kept. */
    public interface Store {
        float scale();

        void setScale(float scale);
    }

    @NonNull
    public static final SettingDeclaration DECLARATION = SettingDeclaration.integer(ID,
        SettingDeclaration.Store.APP_PREFERENCES, TERMUX_APP.KEY_IN_APP_KEYBOARD_KEY_MARGIN_SCALE,
        tenths(TERMUX_APP.MIN_IN_APP_KEYBOARD_KEY_MARGIN_SCALE),
        tenths(TERMUX_APP.MAX_IN_APP_KEYBOARD_KEY_MARGIN_SCALE), 1,
        tenths(TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_KEY_MARGIN_SCALE), SettingRefresh.RESTYLE)
        .described("Key spacing", "The space between the launcher keyboard's keys.")
        .saidAs("the keys are too close together", "I keep hitting the wrong key",
            "the keys have too much space between them")
        .resetBy(SettingDeclaration.ResetGroup.APPEARANCE).notOffered();

    /** A margin scale as the slider's tenths. */
    public static int tenths(float scale) {
        return Math.round(scale * 10f);
    }

    /** The store over the app's preferences. */
    @NonNull
    public static Store over(@NonNull TermuxAppSharedPreferences preferences) {
        return new Store() {
            @Override public float scale() {
                return preferences.getInAppKeyboardKeyMarginScale();
            }

            @Override public void setScale(float scale) {
                preferences.setInAppKeyboardKeyMarginScale(scale);
            }
        };
    }

    @NonNull private final Store mStore;

    public KeySpacingAdapter(@NonNull Store store) {
        mStore = store;
    }

    @Nullable
    @Override
    public SettingDeclaration declaration(@NonNull String id) {
        return ID.equals(id) ? DECLARATION : null;
    }

    @NonNull
    @Override
    public List<SettingDeclaration> declarations() {
        return Collections.singletonList(DECLARATION);
    }

    @NonNull
    @Override
    public String get(@NonNull SettingDeclaration declaration) {
        return Integer.toString(DECLARATION.clamp(tenths(mStore.scale())));
    }

    @Nullable
    @Override
    public SettingsRegistry.Refusal check(@NonNull SettingDeclaration declaration,
                                          @NonNull String value) {
        Integer parsed = parse(value);
        return parsed == null || parsed != DECLARATION.clamp(parsed)
            ? new SettingsRegistry.Refusal(ID + " is " + DECLARATION.allowed(), DECLARATION.allowed())
            : null;
    }

    @NonNull
    @Override
    public SettingsRegistry.Outcome set(@NonNull SettingDeclaration declaration,
                                        @NonNull String value) {
        SettingsRegistry.Refusal refusal = check(declaration, value);
        if (refusal != null) return refusal;
        String old = get(declaration);
        int next = parse(value);
        mStore.setScale(next / 10f);
        return new SettingsRegistry.Change(DECLARATION, old, get(declaration));
    }

    @Nullable
    private static Integer parse(@NonNull String value) {
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
