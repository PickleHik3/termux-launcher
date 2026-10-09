package com.termux.app.fragments.settings.termux;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What a repository's model card says about one of its files, quoted. The import picker used to
 * call builds "compact · a good balance for phones" on its own authority; the only place such a
 * judgement may come from is the publisher, so this finds the sentence in the README where the
 * card recommends, picks or prefers a file by name ("On phones we recommend int8") and hands it
 * back verbatim, trimmed to its clause. A card that says nothing about a file yields nothing.
 */
final class TaiImportCard {
    /** A clause from the card and whether it is an outright recommendation of the file. */
    static final class Quote {
        @NonNull final String text;
        /** The card recommends this file ("we recommend int8"), as opposed to picking it for one purpose. */
        final boolean recommends;

        Quote(@NonNull String text, boolean recommends) {
            this.text = text;
            this.recommends = recommends;
        }
    }

    private static final Pattern CUE = Pattern.compile("\\b(recommend(?:s|ed)?|pick|prefer|choose)\\b");
    /** How far after the cue the file may be named: "we recommend the int8 build" fits, a clause later does not. */
    private static final int MAX_GAP = 14;
    private static final int MAX_QUOTE = 140;

    private TaiImportCard() {
    }

    /**
     * Adds {@code cardQuote} and {@code cardRecommends} to each candidate the card talks about.
     * Edits {@code candidates} in place; an empty card changes nothing.
     */
    static void annotate(@Nullable JSONArray candidates, @Nullable String readme) {
        if (candidates == null || readme == null || readme.trim().isEmpty()) return;
        List<String> sentences = sentences(readme);
        for (int i = 0; i < candidates.length(); i++) {
            JSONObject candidate = candidates.optJSONObject(i);
            if (candidate == null) continue;
            Quote quote = quoteFor(sentences, candidate.optString("file", ""));
            if (quote == null) continue;
            try {
                candidate.put("cardQuote", quote.text);
                candidate.put("cardRecommends", quote.recommends);
            } catch (JSONException ignored) {
            }
        }
    }

    /** The card's clause about {@code fileName}, or {@code null} when the card names it with no cue. */
    @Nullable
    static Quote quoteFor(@Nullable String readme, @Nullable String fileName) {
        if (readme == null || readme.trim().isEmpty()) return null;
        return quoteFor(sentences(readme), fileName);
    }

    @Nullable
    private static Quote quoteFor(@NonNull List<String> sentences, @Nullable String fileName) {
        List<String> keys = keys(fileName);
        if (keys.isEmpty()) return null;
        Quote first = null;
        for (String sentence : sentences) {
            String lower = lowerSameLength(sentence);
            Matcher cue = CUE.matcher(lower);
            while (cue.find()) {
                int[] hit = keyAfter(lower, keys, cue.end());
                if (hit == null) continue;
                boolean recommends = cue.group(1).startsWith("recommend") && !negated(lower, cue.start());
                Quote quote = new Quote(clause(sentence, cue.start(), hit[1]), recommends);
                // An outright recommendation wins over an earlier "pick X for Y".
                if (recommends) return quote;
                if (first == null) first = quote;
            }
        }
        return first;
    }

    /**
     * The spellings of a file a card uses: its full name, its name without the extension, and the
     * build part ("int8_gpu", "q4_block32"), the last only when it could not be an ordinary word
     * (it has a digit or a separator), so a sentence about "GPU" never lands on a "-gpu" file.
     */
    @NonNull
    static List<String> keys(@Nullable String fileName) {
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        String base = fileName == null ? "" : fileName.trim();
        base = base.substring(base.lastIndexOf('/') + 1).toLowerCase(Locale.ROOT);
        if (base.isEmpty() || base.equals("config.json")) return new ArrayList<>(keys);
        keys.add(base);
        int dot = base.lastIndexOf('.');
        if (dot > 0) keys.add(base.substring(0, dot));
        String suffix = TaiImportFacts.parse(fileName).buildSuffix;
        if (suffix.length() >= 2 && suffix.matches(".*[0-9_-].*")) keys.add(suffix);
        return new ArrayList<>(keys);
    }

    /** The first key named within {@link #MAX_GAP} of {@code from}, as {start, end}, with no clause break between. */
    @Nullable
    private static int[] keyAfter(@NonNull String lower, @NonNull List<String> keys, int from) {
        int[] best = null;
        for (String key : keys) {
            int at = lower.indexOf(key, from);
            while (at >= 0 && at - from <= MAX_GAP) {
                int end = at + key.length();
                if (bounded(lower, at, end) && !lower.substring(from, at).matches(".*[.;:!?].*")) {
                    if (best == null || at < best[0] || at == best[0] && end > best[1]) best = new int[]{at, end};
                    break;
                }
                at = lower.indexOf(key, at + 1);
            }
        }
        return best;
    }

    /**
     * Whether {@code [start, end)} is a whole mention: "int8" in "_int8" or "int8:" but not in
     * "int8_gpu", "int8-gpu" or "int8.litertlm" (those name another file, or this file by its
     * full name, which is a key of its own).
     */
    private static boolean bounded(@NonNull String text, int start, int end) {
        if (start > 0 && Character.isLetterOrDigit(text.charAt(start - 1))) return false;
        if (end >= text.length()) return true;
        char next = text.charAt(end);
        if (Character.isLetterOrDigit(next) || next == '_') return false;
        boolean joined = (next == '-' || next == '.') && end + 1 < text.length()
            && Character.isLetterOrDigit(text.charAt(end + 1));
        return !joined;
    }

    private static boolean negated(@NonNull String lower, int cueStart) {
        String before = lower.substring(Math.max(0, cueStart - 12), cueStart);
        return before.contains("not ") || before.contains("n't ") || before.contains("never ");
    }

    /**
     * The clause that carries the mention: from the sentence start (or the last comma or
     * semicolon before the cue, when the sentence is long) to the first break after the file's
     * name. "On phones we recommend int8: the CPU runtime..." gives "On phones we recommend int8".
     */
    @NonNull
    private static String clause(@NonNull String sentence, int cueStart, int keyEnd) {
        int end = sentence.length();
        for (String stop : new String[]{":", ";", " (", ", ", " — ", " - "}) {
            int at = sentence.indexOf(stop, keyEnd);
            if (at >= 0 && at < end) end = at;
        }
        int start = 0;
        if (end - start > MAX_QUOTE) {
            int comma = Math.max(sentence.lastIndexOf(", ", cueStart), sentence.lastIndexOf("; ", cueStart));
            start = comma >= 0 ? comma + 2 : cueStart;
        }
        if (end - start > MAX_QUOTE) end = start + MAX_QUOTE;
        String text = sentence.substring(start, end).trim().replaceAll("[\\s,.;:]+$", "");
        return start > 0 ? "…" + text : text;
    }

    /** Lower-cases one character at a time, so indexes into the result are indexes into the original. */
    @NonNull
    private static String lowerSameLength(@NonNull String text) {
        char[] chars = text.toCharArray();
        for (int i = 0; i < chars.length; i++) chars[i] = Character.toLowerCase(chars[i]);
        return new String(chars);
    }

    // ---- README to sentences ----

    /**
     * The card's prose as plain sentences: front matter, code blocks, tables, headings and HTML
     * dropped, links reduced to their text, emphasis and code marks removed (file names keep their
     * underscores), soft-wrapped lines joined into paragraphs.
     */
    @NonNull
    static List<String> sentences(@NonNull String readme) {
        String text = readme.replace("\r\n", "\n").replace('\r', '\n');
        if (text.startsWith("---\n")) {
            int close = text.indexOf("\n---", 4);
            if (close > 0) text = text.substring(close + 4);
        }
        text = text.replaceAll("(?s)```.*?(```|$)", "\n");
        text = text.replaceAll("(?s)<!--.*?-->", " ").replaceAll("<[^>\\n]+>", " ");
        List<String> paragraphs = new ArrayList<>();
        StringBuilder paragraph = new StringBuilder();
        for (String rawLine : text.split("\n")) {
            String line = rawLine.trim();
            boolean item = line.matches("([-*+]|\\d+\\.)\\s+.*");
            if (line.isEmpty() || line.startsWith("|") || line.startsWith("#") || item) {
                if (paragraph.length() > 0) paragraphs.add(paragraph.toString());
                paragraph.setLength(0);
                if (line.isEmpty() || line.startsWith("|") || line.startsWith("#")) continue;
                line = line.replaceFirst("^([-*+]|\\d+\\.)\\s+", "");
            }
            line = line.replaceFirst("^>\\s*", "");
            if (paragraph.length() > 0) paragraph.append(' ');
            paragraph.append(line);
        }
        if (paragraph.length() > 0) paragraphs.add(paragraph.toString());
        List<String> sentences = new ArrayList<>();
        for (String raw : paragraphs) {
            String clean = raw.replaceAll("!\\[[^\\]]*\\]\\([^)]*\\)", "")
                .replaceAll("\\[([^\\]]*)\\]\\([^)]*\\)", "$1")
                .replace("**", "").replace("__", "").replace("`", "").replace("*", "")
                .replaceAll("\\s+", " ").trim();
            for (String sentence : clean.split("(?<=[.!?])\\s+(?=[A-Z0-9\"“(])")) {
                if (!sentence.trim().isEmpty()) sentences.add(sentence.trim());
            }
        }
        return sentences;
    }
}
