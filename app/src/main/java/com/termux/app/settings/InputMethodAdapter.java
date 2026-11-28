package com.termux.app.settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;
import com.termux.shared.termux.settings.registry.SettingAdapter;
import com.termux.shared.termux.settings.registry.SettingDeclaration;
import com.termux.shared.termux.settings.registry.SettingRefresh;
import com.termux.shared.termux.settings.registry.SettingsRegistry;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * The keyboard Terl types with: the launcher's own, the phone's (the system IME), or none. v1's
 * Keyboard page keeps it as two switches (the in-app keyboard, the soft keyboard), so it answers
 * through an adapter that reads and writes both, as that page's row does; Terl's Settings row,
 * launcherctl and terl.conf then get the same effect.
 */
public final class InputMethodAdapter implements SettingAdapter {

    /** The id the registry lists it under. */
    public static final String ID = "keyboard.input_method";

    public static final String BUILT_IN = "built_in";
    public static final String ANDROID = "android";
    public static final String OFF = "off";

    /** The two switches the choice is kept in; the device's are the app's preferences. */
    public interface Store {
        boolean builtIn();

        boolean android();

        void write(boolean builtIn, boolean android);
    }

    @NonNull
    public static final SettingDeclaration DECLARATION = SettingDeclaration.choice(ID,
        SettingDeclaration.Store.APP_PREFERENCES, TERMUX_APP.KEY_IN_APP_KEYBOARD_ENABLED,
        Arrays.asList(BUILT_IN, ANDROID, OFF), Arrays.asList(BUILT_IN, ANDROID, OFF), BUILT_IN,
        false, SettingRefresh.RESTYLE).described("Keyboard",
        "Type with Terl's own keyboard, your phone's keyboard, or neither.")
        .saidAs("use my phone's keyboard", "use Gboard", "I want the normal Android keyboard",
            "bring back the launcher keyboard", "turn off the on-screen keyboard")
        .notOffered();

    @NonNull private final Store mStore;

    public InputMethodAdapter(@NonNull Store store) {
        mStore = store;
    }

    /** The choice over the app's preferences. */
    @NonNull
    public static Store over(@NonNull TermuxAppSharedPreferences preferences) {
        return new Store() {
            @Override public boolean builtIn() { return preferences.isInAppKeyboardEnabled(); }
            @Override public boolean android() { return preferences.isSoftKeyboardEnabled(); }
            @Override public void write(boolean builtIn, boolean android) {
                preferences.setInAppKeyboardEnabled(builtIn);
                preferences.setSoftKeyboardEnabled(android);
            }
        };
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

    /** The launcher's keyboard wins while on, as v1's page reads the two switches. */
    @NonNull
    @Override
    public String get(@NonNull SettingDeclaration declaration) {
        if (mStore.builtIn()) return BUILT_IN;
        return mStore.android() ? ANDROID : OFF;
    }

    @Nullable
    @Override
    public SettingsRegistry.Refusal check(@NonNull SettingDeclaration declaration,
                                          @NonNull String value) {
        return parse(value) == null ? new SettingsRegistry.Refusal(
            ID + " is one of " + DECLARATION.allowed(), DECLARATION.allowed()) : null;
    }

    @NonNull
    @Override
    public SettingsRegistry.Outcome set(@NonNull SettingDeclaration declaration,
                                        @NonNull String value) {
        String next = parse(value);
        if (next == null) return check(declaration, value);
        String old = get(declaration);
        if (!old.equals(next)) mStore.write(BUILT_IN.equals(next), ANDROID.equals(next));
        return new SettingsRegistry.Change(DECLARATION, old, next);
    }

    /** A choice's name; v1's word for off ("none") reads as off. */
    @Nullable
    private static String parse(@NonNull String value) {
        String word = value.trim().toLowerCase(Locale.ROOT);
        if ("none".equals(word)) return OFF;
        return DECLARATION.choices.contains(word) ? word : null;
    }
}
