package com.termux.app.surfaces;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.fragments.settings.termux.AppearanceSettingsStore;
import com.termux.app.launcher.home.HomeSettings;
import com.termux.app.surfaces.cards.DockCard;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * The app-wide choices the Appearance card, the Home card and the Dock card write (theme,
 * wallpaper colours, the wallpaper shown, icons, widgets on glass, full screen, Home's rows, the
 * dock's options and the A–Z strip's switches), captured
 * through the same store Settings writes them with, so the editor's Undo and Discard put each
 * back exactly as Settings would, with the same live reload. {@link AppearanceSnapshot} holds the
 * look itself; this holds what sits beside it.
 */
final class EditorSettingsSnapshot {

    /** Choices kept as strings: the theme, the icon pack, the numerals and Home's lists. */
    static final List<String> STRING_KEYS = Arrays.asList(
        "theme_mode",
        "app_launcher_icon_pack_package",
        "app_launcher_pinned_icon_pack_package",
        "app_launcher_builtin_widget_style",
        HomeSettings.KEY_ICON_SCALE,
        HomeSettings.KEY_LABEL_SIZE,
        HomeSettings.KEY_FOLDER_DENSITY,
        HomeSettings.KEY_PAGE_INDICATOR,
        DockCard.KEY_AZ_SHOW);

    /** Choices kept as switches. */
    static final List<String> BOOLEAN_KEYS = Arrays.asList(
        "terminal_dynamic_colors_enabled",
        "use_system_wallpaper",
        "widgets_on_glass",
        "app_launcher_bw_icons",
        "app_launcher_most_used_page",
        "app_launcher_notification_dots",
        "app_launcher_quick_reply_swipe",
        "wallpaper_parallax",
        HomeSettings.KEY_LABEL_TWO_LINES,
        HomeSettings.KEY_LABEL_SHADOW,
        AppearanceSettingsStore.KEY_FULLSCREEN,
        DockCard.KEY_AZ_GLASS_BACKGROUND,
        DockCard.KEY_COMMAND_PALETTE_BUTTON);

    /**
     * The Dock card's own switches, which Settings has no row for: kept in the app's preference
     * store as the card writes them, not through Settings' store (which ignores keys it has no row
     * for).
     */
    static final List<String> APP_STORE_KEYS = Arrays.asList(
        DockCard.KEY_AZ_SHOW,
        DockCard.KEY_AZ_GLASS_BACKGROUND,
        DockCard.KEY_COMMAND_PALETTE_BUTTON);

    /** Where the values are read and written; Settings' own store in the app, a map in a test. */
    interface Store {
        @Nullable String getString(@NonNull String key);
        void putString(@NonNull String key, @Nullable String value);
        boolean getBoolean(@NonNull String key);
        void putBoolean(@NonNull String key, boolean value);
    }

    /** Settings' own store, which applies every write as its rows do. */
    @NonNull
    static Store settings(@NonNull Context context) {
        return settings(context, null);
    }

    /**
     * Settings' own store, with the {@link #APP_STORE_KEYS} read and written in {@code app}, the
     * app's preference store the Dock card writes them to (left out where it is null).
     */
    @NonNull
    static Store settings(@NonNull Context context, @Nullable SharedPreferences app) {
        return new Store() {
            @Nullable @Override public String getString(@NonNull String key) {
                if (APP_STORE_KEYS.contains(key))
                    return app == null ? null : app.getString(key, null);
                return AppearanceSettingsStore.getString(context, key, null);
            }

            @Override public void putString(@NonNull String key, @Nullable String value) {
                if (APP_STORE_KEYS.contains(key)) {
                    if (app == null) return;
                    if (value == null) app.edit().remove(key).apply();
                    else app.edit().putString(key, value).apply();
                    return;
                }
                AppearanceSettingsStore.putString(context, key, value);
            }

            @Override public boolean getBoolean(@NonNull String key) {
                if (APP_STORE_KEYS.contains(key))
                    return app == null ? defaultOf(key) : app.getBoolean(key, defaultOf(key));
                return AppearanceSettingsStore.getBoolean(context, key, defaultOf(key));
            }

            @Override public void putBoolean(@NonNull String key, boolean value) {
                if (APP_STORE_KEYS.contains(key)) {
                    if (app != null) app.edit().putBoolean(key, value).apply();
                    return;
                }
                AppearanceSettingsStore.putBoolean(context, key, value);
            }
        };
    }

    /** The shipped value of a switch, as its Settings row states it. */
    static boolean defaultOf(@NonNull String key) {
        switch (key) {
            case "wallpaper_parallax":
            case "app_launcher_quick_reply_swipe":
            case HomeSettings.KEY_LABEL_SHADOW:
                return true;
            case DockCard.KEY_COMMAND_PALETTE_BUTTON:
                return DockCard.DEFAULT_COMMAND_PALETTE_BUTTON;
            case DockCard.KEY_AZ_GLASS_BACKGROUND:
                return DockCard.DEFAULT_AZ_GLASS_BACKGROUND;
            default:
                return false;
        }
    }

    @NonNull private final String[] mStrings;
    @NonNull private final boolean[] mBooleans;

    private EditorSettingsSnapshot(@NonNull Store store) {
        mStrings = new String[STRING_KEYS.size()];
        for (int i = 0; i < mStrings.length; i++)
            mStrings[i] = store.getString(STRING_KEYS.get(i));
        mBooleans = new boolean[BOOLEAN_KEYS.size()];
        for (int i = 0; i < mBooleans.length; i++)
            mBooleans[i] = store.getBoolean(BOOLEAN_KEYS.get(i));
    }

    @NonNull
    static EditorSettingsSnapshot capture(@NonNull Store store) {
        return new EditorSettingsSnapshot(store);
    }

    /**
     * Writes back every value that differs from the store's now, and says whether one of them
     * needs the activity rebuilt (the theme, or the wallpaper colours, which the palette is
     * loaded with). Unchanged values are left alone, so Undo reloads nothing it did not touch.
     */
    @NonNull
    List<String> restore(@NonNull Store store) {
        java.util.ArrayList<String> written = new java.util.ArrayList<>();
        for (int i = 0; i < mStrings.length; i++) {
            String key = STRING_KEYS.get(i);
            if (!Objects.equals(store.getString(key), mStrings[i])) {
                store.putString(key, mStrings[i]);
                written.add(key);
            }
        }
        for (int i = 0; i < mBooleans.length; i++) {
            String key = BOOLEAN_KEYS.get(i);
            if (store.getBoolean(key) != mBooleans[i]) {
                store.putBoolean(key, mBooleans[i]);
                written.add(key);
            }
        }
        return written;
    }

    /** One string for the dirty test: equal strings, nothing changed. */
    @NonNull
    String signature() {
        return Arrays.toString(mStrings) + '|' + Arrays.toString(mBooleans);
    }

    @NonNull
    static String signatureOf(@NonNull Store store) {
        return capture(store).signature();
    }
}
