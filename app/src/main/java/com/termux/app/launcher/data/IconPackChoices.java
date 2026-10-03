package com.termux.app.launcher.data;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.TermuxActivity;
import com.termux.app.launcher.model.IconPackInfo;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The icon pack choice, listed and applied in one place: the Style settings rows and the
 * wallpaper picker page's Icon pack menu both read the installed packs and write the choice
 * through here, so the two never drift apart.
 */
public final class IconPackChoices {

    /** Every launcher icon (the app drawer and the rest), "" for the system's icons. */
    public static final String KEY_GLOBAL = "app_launcher_icon_pack_package";
    /** The dock and pinned pages only, over the global pack; "" follows the global pack. */
    public static final String KEY_PINNED = "app_launcher_pinned_icon_pack_package";

    private IconPackChoices() {}

    /** One row: what it reads and the package it stores ("" for the default row). */
    public static final class Entry {
        @NonNull public final CharSequence label;
        @NonNull public final String value;

        public Entry(@NonNull CharSequence label, @NonNull String value) {
            this.label = label;
            this.value = value;
        }
    }

    /** The rows for one key and which of them is in force. */
    public static final class Listing {
        @NonNull public final List<Entry> entries;
        public final int checked;

        public Listing(@NonNull List<Entry> entries, int checked) {
            this.entries = Collections.unmodifiableList(new ArrayList<>(entries));
            this.checked = checked;
        }
    }

    /** The default row ({@code defaultLabel}, value "") first, then each pack in the given order. */
    @NonNull
    public static List<Entry> entries(@NonNull CharSequence defaultLabel, @NonNull List<IconPackInfo> packs,
                                      boolean themedOnly) {
        List<Entry> out = new ArrayList<>();
        out.add(new Entry(defaultLabel, ""));
        for (IconPackInfo pack : packs) {
            if (themedOnly && !pack.themed) continue;
            out.add(new Entry(pack.label, pack.packageName));
        }
        return out;
    }

    /** The row storing {@code value}; the default row when none does (an uninstalled pack). */
    public static int indexOf(@NonNull List<Entry> entries, @Nullable String value) {
        String v = value == null ? "" : value;
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).value.equals(v)) return i;
        }
        return 0;
    }

    /** The stored package for {@code key}, "" when unset. */
    @NonNull
    public static String current(@Nullable TermuxAppSharedPreferences prefs, @NonNull String key) {
        if (prefs == null) return "";
        String v = KEY_PINNED.equals(key) ? prefs.getAppLauncherPinnedIconPackPackage()
            : prefs.getAppLauncherIconPackPackage();
        return v == null ? "" : v;
    }

    /** The installed packs for {@code key}, with the stored one checked. */
    @NonNull
    public static Listing listing(@NonNull Context context, @Nullable TermuxAppSharedPreferences prefs,
                                  @NonNull String key, @NonNull CharSequence defaultLabel) {
        List<Entry> entries = entries(defaultLabel, new IconPackRepository(context).discoverIconPacks(), false);
        return new Listing(entries, indexOf(entries, current(prefs, key)));
    }

    /**
     * Stores {@code value} for {@code key} and has the launcher pick the new artwork up: the
     * icon cache drops the old pack's drawings and the activity restyles (at once when it is in
     * front, else on its next resume).
     */
    public static void apply(@NonNull Context context, @Nullable TermuxAppSharedPreferences prefs,
                             @NonNull String key, @NonNull String value) {
        if (prefs == null) return;
        if (KEY_PINNED.equals(key)) {
            prefs.setAppLauncherPinnedIconPackPackage(value);
        } else {
            prefs.setAppLauncherIconPackPackage(value);
        }
        // Not invalidate(): that resets catalogue state only, and the artwork the launcher is
        // still holding is the previous pack's.
        LauncherAppDataProvider.getInstance(context).invalidateIconArtwork();
        TermuxActivity.requestTermuxActivityStylingOnNextResume(context, false);
    }
}
