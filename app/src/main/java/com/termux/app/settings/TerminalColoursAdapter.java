package com.termux.app.settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.colours.ColourSchemeFile;
import com.termux.app.colours.TerminalColours;
import com.termux.shared.termux.TermuxConstants;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;
import com.termux.shared.termux.settings.registry.SettingAdapter;
import com.termux.shared.termux.settings.registry.SettingDeclaration;
import com.termux.shared.termux.settings.registry.SettingRefresh;
import com.termux.shared.termux.settings.registry.SettingsRegistry;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * The terminals' colour scheme as a setting ({@code terminal.colours}): Wallpaper colours, the
 * user's own colors.properties, or a built-in scheme. Choosing a scheme writes colors.properties,
 * so it answers through an adapter over {@link TerminalColours}, and every door (the Windows card,
 * launcherctl, terl.conf) gets the same file. Its refresh rebuilds the window, whose chrome palette
 * follows the scheme.
 */
public final class TerminalColoursAdapter implements SettingAdapter {

    /** The id the registry lists it under. */
    public static final String ID = "terminal.colours";

    @NonNull
    public static final SettingDeclaration DECLARATION = SettingDeclaration.choice(ID,
        SettingDeclaration.Store.APP_PREFERENCES, TERMUX_APP.KEY_TERMINAL_DYNAMIC_COLORS_ENABLED,
        TerminalColours.values(), TerminalColours.values(), TerminalColours.WALLPAPER, false,
        SettingRefresh.THEME).described("Colour scheme",
        "The terminal's colours: from the wallpaper, your own colors.properties, or a built-in scheme.")
        .saidAs("I want dracula colours", "change the terminal colours", "use a dark colour scheme",
            "use solarized", "stop taking colours from my wallpaper").notOffered();

    /** The choice over the device: the preferences' Wallpaper colours and ~/.termux/colors.properties. */
    @NonNull
    public static TerminalColours over(@NonNull TermuxAppSharedPreferences preferences) {
        return new TerminalColours(new TerminalColours.Source() {
            @Override public boolean fromWallpaper() {
                return preferences.isTerminalDynamicColorsEnabled();
            }

            @Override public void setFromWallpaper(boolean on) {
                preferences.setTerminalDynamicColorsEnabled(on);
            }
        }, new ColourSchemeFile(TermuxConstants.TERMUX_COLOR_PROPERTIES_FILE));
    }

    /** The device's adapter; the chrome's cached palette is dropped after a change. */
    @NonNull
    public static TerminalColoursAdapter forDevice(@NonNull TermuxAppSharedPreferences preferences) {
        return new TerminalColoursAdapter(over(preferences),
            com.termux.app.theme.LauncherSchemeTheme::invalidate);
    }

    @NonNull private final TerminalColours mColours;
    @NonNull private final Runnable mChanged;

    /** {@code changed} runs after a choice changed the colours, before the registry's refresh. */
    public TerminalColoursAdapter(@NonNull TerminalColours colours, @NonNull Runnable changed) {
        mColours = colours;
        mChanged = changed;
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
        return mColours.current();
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
        if (!old.equals(next)) {
            if (!mColours.choose(next)) {
                return new SettingsRegistry.Refusal("colors.properties could not be written", null);
            }
            mChanged.run();
        }
        return new SettingsRegistry.Change(DECLARATION, old, get(declaration));
    }

    @Nullable
    private static String parse(@NonNull String value) {
        String word = value.trim().toLowerCase(Locale.ROOT);
        return DECLARATION.choices.contains(word) ? word : null;
    }
}
