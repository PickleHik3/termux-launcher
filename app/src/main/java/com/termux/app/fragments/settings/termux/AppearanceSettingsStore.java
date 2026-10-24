package com.termux.app.fragments.settings.termux;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.termux.settings.properties.TermuxPropertyConstants;

import java.util.Set;

/**
 * The Appearance editor's door to the settings that moved into its cards (theme, wallpaper
 * colours, wallpaper, contrast, icons, widgets on glass, full screen, Home's rows). Each read and
 * write goes through the same data store the Settings rows use, under the same keys, so a write
 * from a card applies exactly as one from Settings does (and the Settings search's deep links to
 * those keys keep working), and the editor's Undo can put the old value back the same way.
 */
public final class AppearanceSettingsStore {

    private AppearanceSettingsStore() {}

    /** The full-screen key, stored in {@code termux.properties} by the terminal's data store. */
    public static final String KEY_FULLSCREEN = TermuxPropertyConstants.KEY_USE_FULLSCREEN;
    /** The terminal's padding fill: the edge colours round the text grid. */
    public static final String KEY_PADDING_FILL = "terminal_padding_fill";

    /** Whether a key is one the terminal's data store owns rather than the style store. */
    private static boolean terminalKey(@NonNull String key) {
        return KEY_FULLSCREEN.equals(key) || KEY_PADDING_FILL.equals(key);
    }

    public static boolean getBoolean(@NonNull Context context, @NonNull String key, boolean def) {
        if (terminalKey(key))
            return TerminalViewPreferencesDataStore.getInstance(context).getBoolean(key, def);
        return TermuxStylePreferencesDataStore.getInstance(context).getBoolean(key, def);
    }

    public static void putBoolean(@NonNull Context context, @NonNull String key, boolean value) {
        if (terminalKey(key)) {
            TerminalViewPreferencesDataStore.getInstance(context).putBoolean(key, value);
            return;
        }
        TermuxStylePreferencesDataStore.getInstance(context).putBoolean(key, value);
    }

    @Nullable
    public static String getString(@NonNull Context context, @NonNull String key,
                                   @Nullable String def) {
        return TermuxStylePreferencesDataStore.getInstance(context).getString(key, def);
    }

    public static void putString(@NonNull Context context, @NonNull String key,
                                 @Nullable String value) {
        TermuxStylePreferencesDataStore.getInstance(context).putString(key, value);
    }

    public static int getInt(@NonNull Context context, @NonNull String key, int def) {
        return TermuxStylePreferencesDataStore.getInstance(context).getInt(key, def);
    }

    public static void putInt(@NonNull Context context, @NonNull String key, int value) {
        TermuxStylePreferencesDataStore.getInstance(context).putInt(key, value);
    }

    @Nullable
    public static Set<String> getStringSet(@NonNull Context context, @NonNull String key) {
        return TermuxStylePreferencesDataStore.getInstance(context).getStringSet(key, null);
    }
}
