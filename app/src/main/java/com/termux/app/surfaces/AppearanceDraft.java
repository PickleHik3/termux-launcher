package com.termux.app.surfaces;

import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.settings.preferences.PreferenceDraft;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Where the Appearance editor's draft meets the rest of the launcher: which keys its session
 * writes beside the user's edits, and what a start does with a draft no session owns.
 */
public final class AppearanceDraft {

    /**
     * The saved Custom look and the cards' saved Custom looks: kept up beside the look as it is
     * edited. Done stores them and Discard drops them with the rest, but a draft holding only these
     * has nothing the user changed, so they never raise the unsaved dot.
     */
    static final Set<String> BOOKKEEPING_KEYS = Collections.unmodifiableSet(new HashSet<>(
        Arrays.asList(TERMUX_APP.KEY_SURFACE_CUSTOM_PRESET, CardCustoms.PREFS_KEY)));

    /** The entry store older builds kept on disk for a session open at the time. */
    static final String LEGACY_ENTRY_KEY = "appearance_entry_snapshot_v1";

    private AppearanceDraft() {}

    /**
     * At start: drops a draft left open by a session whose activity is gone, unless
     * {@code editorReopening} says a relaunch is carrying that session on, and forgets the entry
     * store an older build wrote. The editor's writes live only in the draft now, so a process
     * that died mid-session left nothing in the store to put back.
     */
    public static void dropUnowned(@Nullable TermuxAppSharedPreferences prefs,
                                   boolean editorReopening) {
        if (!editorReopening) {
            PreferenceDraft.discardCurrent();
            android.content.Context context = prefs == null ? null : prefs.getContext();
            if (context != null)
                com.termux.app.place.layout.LayoutStore.discardSharedDraft(context);
        }
        SharedPreferences store = prefs == null ? null : prefs.getSharedPreferences();
        if (store != null && store.contains(LEGACY_ENTRY_KEY))
            store.edit().remove(LEGACY_ENTRY_KEY).apply();
    }
}
