package com.termux.shared.termux.settings.registry;

import androidx.annotation.NonNull;

import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;
import com.termux.shared.termux.settings.preferences.TerminalContrastLevel;
import com.termux.shared.theme.NightMode;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The launcher's setting declarations. Each is a constant the store's getter clamps with, so a
 * range or a list of choices is written once. Adding a setting is one constant here plus its place
 * in {@link #ALL}; the registry's index is built from {@link #ALL} only when something asks.
 */
public final class SettingDeclarations {

    private SettingDeclarations() {}

    /** Theme: System, Light or Dark, kept in termux.properties as night-mode system, false or true. */
    @NonNull
    public static final SettingDeclaration THEME_MODE = SettingDeclaration.choice("theme.mode",
        SettingDeclaration.Store.PROPERTIES, "night-mode",
        Arrays.asList("system", "light", "dark"),
        Arrays.asList(NightMode.SYSTEM.getName(), NightMode.FALSE.getName(), NightMode.TRUE.getName()),
        "system", false, SettingRefresh.THEME).described("Theme",
        "Whether the launcher follows the system's light or dark setting, or stays light or dark.");

    /** The terminal palette's contrast, Softer to Harder ({@link TerminalContrastLevel}). */
    @NonNull
    public static final SettingDeclaration TERMINAL_CONTRAST = SettingDeclaration.choice(
        "terminal.contrast", SettingDeclaration.Store.APP_PREFERENCES,
        TERMUX_APP.KEY_TERMINAL_CONTRAST_LEVEL, contrastLevels(), contrastLevels(),
        TERMUX_APP.DEFAULT_VALUE_TERMINAL_CONTRAST_LEVEL, true, SettingRefresh.RESTYLE).resetBy(SettingDeclaration.ResetGroup.APPEARANCE)
        .described("Terminal contrast",
            "How hard the terminal's colours are, from soft pastels to the deepest background and brightest text.");

    /** The shared Text size, in percent. */
    @NonNull
    public static final SettingDeclaration TEXT_SIZE = SettingDeclaration.integer("text.size",
        SettingDeclaration.Store.APP_PREFERENCES, TERMUX_APP.KEY_TEXT_SIZE_PERCENT,
        TERMUX_APP.MIN_TEXT_SIZE_PERCENT, TERMUX_APP.MAX_TEXT_SIZE_PERCENT,
        TERMUX_APP.TEXT_SIZE_PERCENT_STEP, TERMUX_APP.DEFAULT_TEXT_SIZE_PERCENT,
        SettingRefresh.TEXT_SIZE).resetBy(SettingDeclaration.ResetGroup.APPEARANCE)
        .described("Text size", "How large text is across the launcher's own screens, in percent.");

    /** Every declaration, in the order {@code config list} shows them. */
    @NonNull
    public static final List<SettingDeclaration> ALL =
        Collections.unmodifiableList(Arrays.asList(THEME_MODE, TERMINAL_CONTRAST, TEXT_SIZE));

    /**
     * The app-preferences keys the {@code group} reset removes: its declarations' keys, and the
     * font size families' ({@link FontSizeSettingsAdapter#resetKeyPrefixes}, per display).
     */
    @NonNull
    public static Set<String> resetKeys(@NonNull SettingDeclaration.ResetGroup group) {
        Set<String> keys = new HashSet<>();
        for (SettingDeclaration declaration : ALL) {
            if (declaration.resetGroup == group
                && declaration.store == SettingDeclaration.Store.APP_PREFERENCES)
                keys.add(declaration.key);
        }
        return keys;
    }

    /** Whether the {@code group} reset removes {@code key}, a declared key or a font size's. */
    public static boolean resets(@NonNull SettingDeclaration.ResetGroup group, @NonNull String key) {
        return resetKeys(group).contains(key) || FontSizeSettingsAdapter.resets(group, key);
    }

    /**
     * The declaration whose key is {@code key} in the app preferences, else null. Font sizes are
     * not here: their range depends on the screen ({@link FontSizeSettingsAdapter}).
     */
    @androidx.annotation.Nullable
    public static SettingDeclaration forPreferenceKey(@NonNull String key) {
        for (SettingDeclaration declaration : ALL) {
            if (declaration.store == SettingDeclaration.Store.APP_PREFERENCES
                && declaration.key.equals(key)) return declaration;
        }
        return null;
    }

    @NonNull
    private static List<String> contrastLevels() {
        List<String> values = new ArrayList<>();
        for (TerminalContrastLevel level : TerminalContrastLevel.values()) values.add(level.value);
        return values;
    }
}
