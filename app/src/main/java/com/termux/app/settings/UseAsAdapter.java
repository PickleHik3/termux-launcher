package com.termux.app.settings;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.wall.UseAs;
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
 * Use Terl as (Home screen or Terminal) as a setting. Switching it is more than storing the word:
 * Terminal sets the Home pages aside and Home screen brings them back ({@link UseAsSetting#write}),
 * so it answers through an adapter, and every door (Terl's Settings row, launcherctl, terl.conf)
 * gets the same effect.
 */
public final class UseAsAdapter implements SettingAdapter {

    /** The id the registry lists it under. */
    public static final String ID = "home.use_as";

    /** Where Use as is kept; the device's goes through {@link UseAsSetting}. */
    public interface Store {
        /** The stored word, or null when nothing is stored. */
        @Nullable
        String read();

        void write(@NonNull String value);
    }

    @NonNull
    public static final SettingDeclaration DECLARATION = SettingDeclaration.choice(ID,
        SettingDeclaration.Store.APP_PREFERENCES, TERMUX_APP.KEY_USE_AS,
        Arrays.asList(TERMUX_APP.USE_AS_HOME, TERMUX_APP.USE_AS_TERMINAL),
        Arrays.asList(TERMUX_APP.USE_AS_HOME, TERMUX_APP.USE_AS_TERMINAL), TERMUX_APP.DEFAULT_USE_AS,
        false, SettingRefresh.LISTENER).described("Use Terl as",
        "Whether Terl is your home screen with Home pages, or a terminal that opens on its windows.")
        .saidAs("I don't want a home screen", "only use it as a terminal",
            "bring back my home screen").notOffered();

    @NonNull private final Store mStore;

    public UseAsAdapter(@NonNull Store store) {
        mStore = store;
    }

    /** The device's Use as, over the app's preferences and the Home pages. */
    @NonNull
    public static Store over(@NonNull Context context) {
        return new Store() {
            @Nullable @Override public String read() {
                return UseAsSetting.read(context).value;
            }

            @Override public void write(@NonNull String value) {
                UseAsSetting.write(context, UseAs.read(value));
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

    @NonNull
    @Override
    public String get(@NonNull SettingDeclaration declaration) {
        return UseAs.read(mStore.read()).value;
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
        if (!old.equals(next)) mStore.write(next);
        return new SettingsRegistry.Change(DECLARATION, old, next);
    }

    @Nullable
    private static String parse(@NonNull String value) {
        String word = value.trim().toLowerCase(Locale.ROOT);
        return DECLARATION.choices.contains(word) ? word : null;
    }
}
