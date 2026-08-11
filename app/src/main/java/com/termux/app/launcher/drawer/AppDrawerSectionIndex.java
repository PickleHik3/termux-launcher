package com.termux.app.launcher.drawer;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.launcher.data.LauncherAppDataProvider;
import com.termux.app.launcher.model.LauncherAppEntry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The A-Z index over a drawer catalogue: which letters exist, and where each one's run begins.
 *
 * <p><b>Sorting is not optional.</b> {@code LauncherAppDataProvider.getAllApps()} is not
 * alphabetical: the primary user is sorted with {@code ResolveInfo.DisplayNameComparator} and work
 * and clone entries are then <em>appended</em>, so an index built straight over it would point at the
 * wrong place for every letter that also has a profile app. {@link #sortByLabel} is therefore run on
 * the catalogue before it reaches the search model, which makes each letter a single contiguous run —
 * and that is what lets {@link #firstPositionForLetter} be one number and the scrub highlight's
 * matching cells always be adjacent.
 *
 * <p>Letters come from {@link LauncherAppDataProvider#normalizeLetter}, the same normalisation the
 * dock's A-Z row and the provider's own letter buckets use, so the two A-Z surfaces can never
 * disagree about which bucket an app is in. There is deliberately no new normalisation logic here.
 *
 * <p>{@code LauncherUsageStatsStore.rankForAz} is deliberately <b>not</b> used: it reorders a bucket
 * by launch count, which would break the contiguity this index depends on.
 */
public final class AppDrawerSectionIndex {

    /** Draw and scrub order. {@code #} sorts last, after Z, as it does in the dock row. */
    public static final String AZ_ORDER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ#";

    private static final char[] EMPTY_LETTERS = new char[0];

    private final char[] mLetters;
    private final char[] mPositionLetters;
    private final int[] mFirstPosition = new int[AZ_ORDER.length()];

    private AppDrawerSectionIndex(@NonNull char[] letters, @NonNull char[] positionLetters,
                                 @NonNull int[] firstPosition) {
        mLetters = letters;
        mPositionLetters = positionLetters;
        System.arraycopy(firstPosition, 0, mFirstPosition, 0, firstPosition.length);
    }

    /**
     * Alphabetical by label, case-insensitively, in a new list. Stable, so two apps sharing a label —
     * the personal and work copies of the same app, the commonest case — keep the provider's own
     * order relative to each other rather than swapping about between refreshes.
     */
    @NonNull
    public static List<LauncherAppEntry> sortByLabel(@Nullable List<LauncherAppEntry> entries) {
        if (entries == null || entries.isEmpty()) return new ArrayList<>();
        List<LauncherAppEntry> sorted = new ArrayList<>(entries);
        Collections.sort(sorted, (a, b) -> a.label.compareToIgnoreCase(b.label));
        return sorted;
    }

    /** The bucket an entry belongs to. */
    public static char letterOf(@NonNull LauncherAppEntry entry) {
        return LauncherAppDataProvider.normalizeLetter(entry.label);
    }

    /**
     * One pass over the submitted list.
     *
     * <p>The list is expected to have been through {@link #sortByLabel}; a list that has not is still
     * indexed without complaint — every letter simply points at its first occurrence — because a
     * wrong scroll target is a better failure than a crash on a catalogue refresh.
     */
    @NonNull
    public static AppDrawerSectionIndex build(@Nullable List<LauncherAppEntry> entries) {
        int count = entries == null ? 0 : entries.size();
        int[] firstPosition = new int[AZ_ORDER.length()];
        for (int i = 0; i < firstPosition.length; i++) {
            firstPosition[i] = -1;
        }
        if (count == 0) {
            return new AppDrawerSectionIndex(EMPTY_LETTERS, EMPTY_LETTERS, firstPosition);
        }
        char[] positionLetters = new char[count];
        for (int position = 0; position < count; position++) {
            char letter = letterOf(entries.get(position));
            positionLetters[position] = letter;
            int slot = AZ_ORDER.indexOf(letter);
            if (slot >= 0 && firstPosition[slot] < 0) {
                firstPosition[slot] = position;
            }
        }
        int letterCount = 0;
        for (int value : firstPosition) {
            if (value >= 0) letterCount++;
        }
        char[] letters = new char[letterCount];
        int next = 0;
        for (int slot = 0; slot < firstPosition.length; slot++) {
            if (firstPosition[slot] >= 0) {
                letters[next++] = AZ_ORDER.charAt(slot);
            }
        }
        return new AppDrawerSectionIndex(letters, positionLetters, firstPosition);
    }

    /** Letters with at least one app, in {@link #AZ_ORDER}. */
    public int letterCount() {
        return mLetters.length;
    }

    /** The visible letter at a draw slot; {@code 0} for an index outside the set. */
    public char letterAt(int letterIndex) {
        if (letterIndex < 0 || letterIndex >= mLetters.length) return '\0';
        return mLetters[letterIndex];
    }

    /** Draw slot of a letter, or -1 when the catalogue has none. */
    public int indexOfLetter(char letter) {
        char upper = Character.toUpperCase(letter);
        for (int i = 0; i < mLetters.length; i++) {
            if (mLetters[i] == upper) return i;
        }
        return -1;
    }

    /** Adapter positions indexed. */
    public int entryCount() {
        return mPositionLetters.length;
    }

    /**
     * The scroll target for a letter: the first adapter position in its run, or -1 when the
     * catalogue has no app under it.
     */
    public int firstPositionForLetter(char letter) {
        int slot = AZ_ORDER.indexOf(Character.toUpperCase(letter));
        if (slot < 0) return -1;
        return mFirstPosition[slot];
    }

    /** The letter of an adapter position; {@code 0} outside the list. */
    public char letterForPosition(int position) {
        if (position < 0 || position >= mPositionLetters.length) return '\0';
        return mPositionLetters[position];
    }

    /**
     * A copy of the per-position letters for the adapter to hold onto, so the per-frame highlight
     * walk can go from a child's adapter position to its letter without touching an entry list.
     */
    @NonNull
    public char[] copyPositionLetters() {
        return mPositionLetters.clone();
    }
}
