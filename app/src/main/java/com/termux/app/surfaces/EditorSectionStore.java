package com.termux.app.surfaces;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;

/**
 * Which of a card's collapsible sections were left open (appearance-cards overhaul §3), per card,
 * across visits and restarts. A card is keyed by its {@link EditorCard} (and its subcard, where
 * one stands in its place), a section by its heading row's name, or {@link #FIRST} for the rows
 * before the first heading. On a card's first visit only its first section is open.
 */
final class EditorSectionStore {

    /** Its own small preferences file: nothing here belongs to a look or a setting. */
    static final String FILE = "appearance_editor_sections";
    /** The section of the rows before the first heading (titled with the card's name). */
    static final String FIRST = "_first";
    private static final String VISITED = "visited";

    @NonNull private final SharedPreferences mPrefs;

    EditorSectionStore(@NonNull Context context) {
        mPrefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    /**
     * Whether section {@code section} of {@code card} stands open: as it was left, or, never
     * touched, open only when it is the card's first ({@code index} 0) on a card never visited.
     */
    boolean isOpen(@NonNull String card, @NonNull String section, int index) {
        String key = key(card, section);
        if (mPrefs.contains(key))
            return mPrefs.getBoolean(key, false);
        return index == 0 && !mPrefs.getBoolean(key(card, VISITED), false);
    }

    /** {@code card} was shown: from now on a section never touched stands closed. */
    void markVisited(@NonNull String card) {
        String key = key(card, VISITED);
        if (!mPrefs.getBoolean(key, false))
            mPrefs.edit().putBoolean(key, true).apply();
    }

    void setOpen(@NonNull String card, @NonNull String section, boolean open) {
        mPrefs.edit().putBoolean(key(card, section), open).apply();
    }

    @NonNull
    private static String key(@NonNull String card, @NonNull String section) {
        return card + "/" + section;
    }
}
