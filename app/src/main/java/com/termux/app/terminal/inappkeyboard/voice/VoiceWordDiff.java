package com.termux.app.terminal.inappkeyboard.voice;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * What the cleanup changed, word by word, for the panel to mark: kept words as they are, a word
 * whose spelling, case or punctuation changed and every added word flash in the accent colour,
 * and every removed word shows struck through before it fades (agreed design, "Cleanup"). Words
 * are compared on their letters and digits only, so "readme" → "README." is one changed word, not
 * a removal and an addition. A longest common subsequence over the two word lists, which is
 * plenty for a dictation's few hundred words; past {@link #MAX_WORDS} either side the diff is
 * skipped and the cleaned text is shown as it is.
 */
public final class VoiceWordDiff {

    /** Past this many words either side the quadratic table is not worth it. */
    static final int MAX_WORDS = 800;

    public enum Kind { SAME, CHANGED, ADDED, REMOVED }

    /** One word of the merged view: the cleaned word for SAME/CHANGED/ADDED, the raw one for REMOVED. */
    public static final class Op {
        @NonNull public final Kind kind;
        @NonNull public final String word;

        Op(@NonNull Kind kind, @NonNull String word) {
            this.kind = kind;
            this.word = word;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof Op)) return false;
            Op op = (Op) other;
            return kind == op.kind && word.equals(op.word);
        }

        @Override
        public int hashCode() {
            return kind.hashCode() * 31 + word.hashCode();
        }

        @NonNull
        @Override
        public String toString() {
            return kind + ":" + word;
        }
    }

    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern NOT_ALPHANUMERIC = Pattern.compile("[^\\p{L}\\p{Nd}]+");

    private VoiceWordDiff() {
    }

    /**
     * The merged word list: the cleaned text in order, with each removed raw word placed where it
     * stood, before whatever replaced it.
     */
    @NonNull
    public static List<Op> diff(@NonNull String raw, @NonNull String cleaned) {
        List<String> before = split(raw);
        List<String> after = split(cleaned);
        if (before.size() > MAX_WORDS || after.size() > MAX_WORDS) {
            List<Op> plain = new ArrayList<>(after.size());
            for (String word : after) plain.add(new Op(Kind.SAME, word));
            return plain;
        }
        String[] a = keys(before);
        String[] b = keys(after);
        int n = a.length, m = b.length;
        // lcs[i][j]: the common subsequence length of a[i..] and b[j..].
        int[][] lcs = new int[n + 1][m + 1];
        for (int i = n - 1; i >= 0; i--) {
            for (int j = m - 1; j >= 0; j--) {
                lcs[i][j] = a[i].equals(b[j]) ? lcs[i + 1][j + 1] + 1 : Math.max(lcs[i + 1][j], lcs[i][j + 1]);
            }
        }
        List<Op> ops = new ArrayList<>(n + m);
        int i = 0, j = 0;
        while (i < n && j < m) {
            if (a[i].equals(b[j])) {
                String word = after.get(j);
                ops.add(new Op(word.equals(before.get(i)) ? Kind.SAME : Kind.CHANGED, word));
                i++;
                j++;
            } else if (lcs[i + 1][j] >= lcs[i][j + 1]) {
                ops.add(new Op(Kind.REMOVED, before.get(i++)));
            } else {
                ops.add(new Op(Kind.ADDED, after.get(j++)));
            }
        }
        while (i < n) ops.add(new Op(Kind.REMOVED, before.get(i++)));
        while (j < m) ops.add(new Op(Kind.ADDED, after.get(j++)));
        return ops;
    }

    @NonNull
    private static List<String> split(@NonNull String text) {
        String trimmed = text.trim();
        if (trimmed.isEmpty()) return Collections.emptyList();
        List<String> words = new ArrayList<>();
        Collections.addAll(words, WHITESPACE.split(trimmed));
        return words;
    }

    /** Letters and digits only, lower-cased; a word of punctuation alone keys as itself. */
    @NonNull
    private static String[] keys(@NonNull List<String> words) {
        String[] keys = new String[words.size()];
        for (int k = 0; k < keys.length; k++) {
            String word = words.get(k);
            String key = NOT_ALPHANUMERIC.matcher(word.toLowerCase(Locale.ROOT)).replaceAll("");
            keys[k] = key.isEmpty() ? word : key;
        }
        return keys;
    }
}
