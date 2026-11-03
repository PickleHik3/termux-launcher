package com.termux.app.setup;

import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.termux.R;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** The "Learn the basics" chapters, their order, and which ones the answers keep. Pure. */
final class GuideChapters {
    private GuideChapters() {}

    static final String KEY_WATCHED = "setup_guide_watched";

    static final String PAGES = "pages";
    static final String APPS = "apps";
    static final String DOCK = "dock";
    static final String KEYBOARD = "keyboard";
    static final String STATUS = "status";
    static final String PALETTE = "palette";
    static final String HELP = "help";
    static final String PANES = "panes";
    static final String DESKTOP = "desktop";

    /** Every chapter id, for the one-shot carry-over from the old tour. */
    static final String[] ALL_IDS = {PAGES, APPS, DOCK, KEYBOARD, STATUS, PALETTE, HELP, PANES,
        DESKTOP};

    static final class Chapter {
        @NonNull final String id;
        @StringRes final int title;
        @StringRes final int caption;

        Chapter(@NonNull String id, int title, int caption) {
            this.id = id;
            this.title = title;
            this.caption = caption;
        }
    }

    private static final Chapter[] HOME_ONLY = {
        new Chapter(PAGES, R.string.setup_guide_pages_title, R.string.setup_guide_pages_caption),
        new Chapter(APPS, R.string.setup_guide_apps_title, R.string.setup_guide_apps_caption),
        new Chapter(DOCK, R.string.setup_guide_dock_title, R.string.setup_guide_dock_caption),
    };
    private static final Chapter[] EVERYONE = {
        new Chapter(KEYBOARD, R.string.setup_guide_keyboard_title,
            R.string.setup_guide_keyboard_caption),
        new Chapter(STATUS, R.string.setup_guide_status_title, R.string.setup_guide_status_caption),
        new Chapter(PALETTE, R.string.setup_guide_palette_title,
            R.string.setup_guide_palette_caption),
        new Chapter(HELP, R.string.setup_guide_help_title, R.string.setup_guide_help_caption),
    };
    private static final Chapter PANES_CHAPTER =
        new Chapter(PANES, R.string.setup_guide_panes_title, R.string.setup_guide_panes_caption);
    private static final Chapter DESKTOP_CHAPTER =
        new Chapter(DESKTOP, R.string.setup_guide_desktop_title,
            R.string.setup_guide_desktop_caption);

    /** The chapters for these answers, in teaching order. */
    @NonNull
    static List<Chapter> forFacts(boolean home, boolean multiplexer, boolean desktopApps) {
        List<Chapter> out = new ArrayList<>();
        if (home) for (Chapter c : HOME_ONLY) out.add(c);
        for (Chapter c : EVERYONE) out.add(c);
        if (multiplexer) out.add(PANES_CHAPTER);
        if (desktopApps) out.add(DESKTOP_CHAPTER);
        return out;
    }

    @NonNull
    static Set<String> watched(@Nullable SharedPreferences prefs) {
        Set<String> out = new HashSet<>();
        if (prefs != null) {
            Set<String> stored = prefs.getStringSet(KEY_WATCHED, null);
            if (stored != null) out.addAll(stored);
        }
        return out;
    }

    static void markWatched(@Nullable SharedPreferences prefs, @NonNull String id) {
        if (prefs == null) return;
        Set<String> set = watched(prefs);
        if (set.add(id)) prefs.edit().putStringSet(KEY_WATCHED, set).apply();
    }

    /**
     * Marks every chapter watched, once: someone who already did the old tour has seen what the
     * chapters teach. Does nothing when the flag is already set.
     */
    static void migrateFromOldTour(@Nullable SharedPreferences prefs) {
        if (prefs == null || prefs.getBoolean(SetupWizardGate.KEY_MIGRATED_V1, false)) return;
        Set<String> set = watched(prefs);
        for (String id : ALL_IDS) set.add(id);
        prefs.edit().putStringSet(KEY_WATCHED, set)
            .putBoolean(SetupWizardGate.KEY_MIGRATED_V1, true).apply();
    }

    /** True when every chapter in the list is in the watched set (an empty list is not done). */
    static boolean allWatched(@NonNull List<Chapter> chapters, @NonNull Set<String> watched) {
        if (chapters.isEmpty()) return false;
        for (Chapter c : chapters) if (!watched.contains(c.id)) return false;
        return true;
    }
}
