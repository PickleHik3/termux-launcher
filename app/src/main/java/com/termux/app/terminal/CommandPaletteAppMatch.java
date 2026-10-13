package com.termux.app.terminal;

import androidx.annotation.NonNull;

import com.termux.app.launcher.model.LauncherAppEntry;

import java.util.Locale;

/**
 * Places an app the launcher's search returned on the palette's score scale, so it can be ranked
 * against action and contact rows ({@link CommandPaletteRanking}).
 *
 * <p>Mirrors {@code LauncherRankingEngine.matchTier} tier by tier. That method is private and the
 * drawer's ranking is not the palette's to change, so the tiers are re-derived here; keep the two
 * in step:
 *
 * <ul>
 *   <li>0, the whole package, activity or stable id: exact, 100. Spelling out an id is as
 *       unambiguous as typing the name.
 *   <li>1, the label: exact, 100.
 *   <li>2, a label prefix: 90.
 *   <li>3, a label word prefix: 80.
 *   <li>4, a label substring: 70.
 *   <li>5 and 6, a package or activity prefix or substring: an id match, 60.
 *   <li>7, the Levenshtein typo pass: 65 when every typed letter still appears in order in the
 *       label, the very test an action's subsequence tier passes; otherwise 55. That pass admits
 *       a 70% likeness to the label, the package or the activity, in any word, so it is the
 *       loosest match the app search makes, and a plain typo sits below an action's id match but
 *       above a category or shortcut match.
 * </ul>
 *
 * <p>Only called for apps the engine already returned, so anything below tier 6 is the typo pass.
 */
final class CommandPaletteAppMatch {

    /** A typo match that is not even a subsequence of the label. */
    static final int SCORE_APP_TYPO = 55;

    private CommandPaletteAppMatch() {
    }

    static int score(@NonNull LauncherAppEntry app, @NonNull String query) {
        String trimmed = query.trim();
        String input = trimmed.toLowerCase(Locale.US);
        if (input.isEmpty()) return CommandPaletteFilter.SCORE_NONE;
        String normalized = normalize(trimmed);
        boolean hasNormalized = !normalized.isEmpty();

        if (app.packageLower.equals(input) || app.activityLower.equals(input)
            || app.stableIdLower.equals(input)) return CommandPaletteFilter.SCORE_EXACT_TITLE;
        if (app.labelLower.equals(input)
            || (hasNormalized && app.labelNormalized.equals(normalized)))
            return CommandPaletteFilter.SCORE_EXACT_TITLE;
        if (app.labelLower.startsWith(input)
            || (hasNormalized && app.labelNormalized.startsWith(normalized)))
            return CommandPaletteFilter.SCORE_TITLE_PREFIX;
        if (hasNormalized) {
            for (String word : app.normalizedWords) {
                if (word.startsWith(normalized)) return CommandPaletteFilter.SCORE_WORD_PREFIX;
            }
        }
        if (app.labelLower.contains(input)
            || (hasNormalized && app.labelNormalized.contains(normalized)))
            return CommandPaletteFilter.SCORE_TITLE_SUBSTRING;
        if (app.packageLower.contains(input) || app.activityLower.contains(input))
            return CommandPaletteFilter.SCORE_ID_SUBSTRING;
        return CommandPaletteFilter.isSubsequence(input, app.labelLower)
            ? CommandPaletteFilter.SCORE_TITLE_SUBSEQUENCE : SCORE_APP_TYPO;
    }

    /** {@code LauncherRankingEngine.normalizeLookupValue}: letters and digits, single spaces. */
    @NonNull
    static String normalize(@NonNull String value) {
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
        if (length > 0 && normalized.charAt(length - 1) == ' ') normalized.setLength(length - 1);
        return normalized.toString();
    }
}
