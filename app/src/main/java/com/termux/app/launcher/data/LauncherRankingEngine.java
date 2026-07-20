package com.termux.app.launcher.data;

import androidx.annotation.NonNull;

import com.termux.app.launcher.model.LauncherAppEntry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class LauncherRankingEngine {
    private static final int FUZZY_QUERY_MIN_LENGTH = 3;
    private static final int LAST_DIRECT_PREFIX_TIER = 3;
    private static final int FUZZY_MATCH_TIER = 7;
    private static final int PERFECT_MATCH_SCORE = 100;
    private static final ThreadLocal<LevenshteinBuffers> LEVENSHTEIN_BUFFERS =
        ThreadLocal.withInitial(LevenshteinBuffers::new);

    private LauncherRankingEngine() {}

    public static List<LauncherAppEntry> filterAndRank(@NonNull List<LauncherAppEntry> entries, @NonNull String query, int tolerance) {
        String trimmed = query.trim();
        String input = trimmed.toLowerCase(Locale.US);
        String normalizedInput = normalizeLookupValue(trimmed);
        if (input.isEmpty()) {
            return new ArrayList<>(entries);
        }

        final boolean fuzzy = input.length() >= FUZZY_QUERY_MIN_LENGTH
            || normalizedInput.length() >= FUZZY_QUERY_MIN_LENGTH;
        List<ScoredEntry> scored = new ArrayList<>();
        for (LauncherAppEntry entry : entries) {
            int tier = matchTier(entry, input, normalizedInput);
            int score = PERFECT_MATCH_SCORE;
            if (fuzzy) {
                score = computeScore(entry, input, normalizedInput);
                if (tier < 0) {
                    if (score < tolerance) continue;
                    tier = FUZZY_MATCH_TIER;
                } else if (tier <= LAST_DIRECT_PREFIX_TIER) {
                    score = Math.max(score, tolerance);
                } else if (score < tolerance) {
                    continue;
                }
            } else if (tier < 0) {
                continue;
            }
            scored.add(new ScoredEntry(entry, score, tier));
        }

        Collections.sort(scored, new Comparator<ScoredEntry>() {
            @Override
            public int compare(ScoredEntry a, ScoredEntry b) {
                if (a.tier != b.tier) return Integer.compare(a.tier, b.tier);
                if (a.score != b.score) return Integer.compare(b.score, a.score);
                return a.entry.label.compareToIgnoreCase(b.entry.label);
            }
        });

        List<LauncherAppEntry> out = new ArrayList<>(scored.size());
        for (ScoredEntry item : scored) {
            out.add(item.entry);
        }
        return out;
    }

    private static int computeScore(@NonNull LauncherAppEntry entry, @NonNull String input, @NonNull String normalizedInput) {
        int best = similarity(input, entry.labelLower);
        best = Math.max(best, similarity(input, entry.packageLower));
        best = Math.max(best, similarity(input, entry.activityLower));
        if (!normalizedInput.isEmpty()) {
            best = Math.max(best, similarity(normalizedInput, entry.labelNormalized));
            for (String word : entry.normalizedWords) {
                best = Math.max(best, similarity(normalizedInput, word));
            }
        }
        return best;
    }

    /**
     * Levenshtein similarity with an exact-substring fast path. Existing substring matches retain
     * their previous perfect score, while typo-only matches use the full label or an individual
     * label word. Launcher strings are short, so O(query * candidate) has predictable low cost.
     */
    static int similarity(@NonNull String query, @NonNull String candidate) {
        if (query.isEmpty()) return PERFECT_MATCH_SCORE;
        if (candidate.isEmpty()) return 0;
        if (candidate.contains(query)) return PERFECT_MATCH_SCORE;

        int queryLength = query.length();
        int candidateLength = candidate.length();
        LevenshteinBuffers buffers = LEVENSHTEIN_BUFFERS.get();
        buffers.ensureCapacity(candidateLength + 1);
        int[] previous = buffers.previous;
        int[] current = buffers.current;
        for (int candidateIndex = 0; candidateIndex <= candidateLength; candidateIndex++) {
            previous[candidateIndex] = candidateIndex;
        }

        for (int queryIndex = 1; queryIndex <= queryLength; queryIndex++) {
            current[0] = queryIndex;
            char queryChar = query.charAt(queryIndex - 1);
            for (int candidateIndex = 1; candidateIndex <= candidateLength; candidateIndex++) {
                int substitution = previous[candidateIndex - 1]
                    + (queryChar == candidate.charAt(candidateIndex - 1) ? 0 : 1);
                int deletion = previous[candidateIndex] + 1;
                int insertion = current[candidateIndex - 1] + 1;
                current[candidateIndex] = Math.min(substitution, Math.min(deletion, insertion));
            }
            int[] swap = previous;
            previous = current;
            current = swap;
        }

        buffers.previous = previous;
        buffers.current = current;

        int distance = previous[candidateLength];
        int longest = Math.max(queryLength, candidateLength);
        return Math.max(0, Math.round((float) PERFECT_MATCH_SCORE * (longest - distance) / longest));
    }

    private static int matchTier(@NonNull LauncherAppEntry entry, @NonNull String input, @NonNull String normalizedInput) {
        if (entry.packageLower.equals(input) || entry.activityLower.equals(input) || entry.stableIdLower.equals(input)) return 0;
        if (entry.labelLower.equals(input) || (!normalizedInput.isEmpty() && entry.labelNormalized.equals(normalizedInput))) return 1;
        if (entry.packageLower.startsWith(input) || entry.activityLower.startsWith(input)) return 2;
        if (entry.labelLower.startsWith(input) || (!normalizedInput.isEmpty() && entry.labelNormalized.startsWith(normalizedInput))) return 3;
        for (String word : entry.normalizedWords) {
            if (!normalizedInput.isEmpty() && word.startsWith(normalizedInput)) return 4;
        }
        if (entry.packageLower.contains(input) || entry.activityLower.contains(input)) return 5;
        if (entry.labelLower.contains(input) || (!normalizedInput.isEmpty() && entry.labelNormalized.contains(normalizedInput))) return 6;
        return -1;
    }

    static String normalizeLookupValue(String value) {
        if (value == null || value.isEmpty()) return "";
        StringBuilder normalized = new StringBuilder(value.length());
        boolean previousWasSpace = true;
        for (int i = 0; i < value.length(); i++) {
            char c = Character.toLowerCase(value.charAt(i));
            if (Character.isLetterOrDigit(c)) {
                normalized.append(c);
                previousWasSpace = false;
            } else if (!previousWasSpace) {
                normalized.append(' ');
                previousWasSpace = true;
            }
        }
        int length = normalized.length();
        if (length > 0 && normalized.charAt(length - 1) == ' ') {
            normalized.setLength(length - 1);
        }
        return normalized.toString();
    }

    private static final class ScoredEntry {
        final LauncherAppEntry entry;
        final int score;
        final int tier;

        ScoredEntry(LauncherAppEntry entry, int score, int tier) {
            this.entry = entry;
            this.score = score;
            this.tier = tier;
        }
    }

    /** Per-thread scratch keeps concurrent UI/background searches independent. */
    private static final class LevenshteinBuffers {
        int[] previous = new int[0];
        int[] current = new int[0];

        void ensureCapacity(int capacity) {
            if (previous.length >= capacity) return;
            previous = new int[capacity];
            current = new int[capacity];
        }
    }
}
