package com.termux.app.terminal.inappkeyboard.voice;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Which sentences of a cleanup part are worth a model call: those with a cheap, surface sign of
 * trouble. The rest pass through as heard. In the 2026-10-13 eval (variant C,
 * {@code project-docs/reference/voice-ai/voice-cleanup-eval-2026-10-13.md}) this halved the time,
 * made no case worse and left every clean text alone; what it misses is a misheard word with
 * nothing odd around it. Pure; {@link VoiceGatedCleanup} sends what it flags.
 *
 * <p>The signs, per sentence, with fillers already stripped ({@link VoiceFillers}): a discourse
 * filler ("you know", "I mean", "basically", a leading "like", "okay so"), a run of one to
 * {@link #REPEAT_MAX_WORDS} words said twice in a row, a correction cue ("no wait", "actually no",
 * "scratch that", ", no,"), a spoken symbol ("slash", "dash dash", "dot com"), more than
 * {@link #RUNON_WORDS} words, a lowercase start, a function word capitalised mid-sentence ("the apps
 * The swiping": where Parakeet stitched its chunks, and where its garbles sit), and, in a part that
 * counts through items ("first… second…") or announces them ("the following"), the sentences that
 * carry them. A sentence with a correction cue also sends the one before it, which it corrects; a
 * flagged sentence under {@link VoicePolishRules#MIN_WORDS} words sends the next one too, so it has
 * its sentence to read. Neighbouring sentences with the same verdict form one group. Polished sends
 * a part with a spoken list whole, so the list is laid out in one answer.
 */
public final class VoiceCleanupGate {

    /** A sentence longer than this is likely a run-on. */
    static final int RUNON_WORDS = 30;
    /** The longest phrase whose immediate repeat ("I don't, I don't") counts as a stutter. */
    static final int REPEAT_MAX_WORDS = 3;
    /** The context a flagged group gets is cut to this many words, for a recogniser that writes no full stops. */
    static final int CONTEXT_MAX_WORDS = 80;

    static final String DISCOURSE = "discourse";
    static final String REPEAT = "repeat";
    static final String CORRECTION = "correction";
    static final String SYMBOL = "symbol";
    static final String RUNON = "runon";
    static final String STRAY_CAPITAL = "stray_capital";
    static final String LOWERCASE_START = "lowercase_start";
    static final String LIST = "list";
    /** A sentence of a Polished part with a spoken list, sent with the list though it has no sign of its own. */
    static final String LIST_PART = "list_part";

    private static final Pattern SENTENCE_BREAK = Pattern.compile("(?U)(?<=[.!?])\\s+");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern NOT_TOKEN = Pattern.compile("[^\\p{L}\\p{N}_']");
    private static final Pattern NON_SPACE = Pattern.compile("\\S+");
    private static final Pattern LIST_LINE_MARKER = Pattern.compile("^\\s*(?:\\d{1,3}[.)]|[-*•])\\s+");
    private static final Pattern DISCOURSE_FILLER = Pattern.compile(
        "(?i)\\b(you know|i mean|basically|kind of like|sort of like)\\b|(^|,\\s*)like\\b|\\blike,"
            + "|\\b(okay|ok|alright|all right),? so\\b");
    private static final Pattern CORRECTION_CUE = Pattern.compile(
        "(?i)\\b(no,? wait|wait,? no|actually,? no|actually,? scratch|scratch that|sorry,? i mean|i mean"
            + "|let me start over|start over)\\b|(?<=[\\p{L}\\p{N}_]), no, ");
    private static final Pattern SPOKEN_SYMBOL = Pattern.compile(
        "(?i)\\b(slash|backslash|dash dash|underscore|dot (com|txt|org|json|md|sh|py|java))\\b");
    /** Where an item can start: the part's start, after punctuation, or after "and" ("And third, …"). */
    private static final String CLAUSE = "(?i)(?:^|[.,;:!?]\\s+|\\band\\s+)(?:the\\s+)?";
    private static final Pattern LIST_FIRST = Pattern.compile(CLAUSE + "(?:first(?:ly)?\\b|one[,:])");
    private static final Pattern LIST_SECOND = Pattern.compile(CLAUSE + "(?:second(?:ly)?\\b|two[,:])");
    private static final Pattern LIST_ITEM = Pattern.compile(CLAUSE
        + "(?:(?:first|second|third|fourth|fifth)(?:ly)?\\b|(?:finally|lastly)\\b|(?:one|two|three|four|five)[,:])");
    private static final Pattern ENUMERATION = Pattern.compile("(?i)\\bthe following\\b");
    /** "I" and its contractions are capitalised anywhere. */
    private static final Set<String> ALWAYS_CAPITAL = new HashSet<>(Arrays.asList("i", "i'm", "i'll", "i've", "i'd"));
    /** Words Parakeet capitalised at its seams in the eval, besides the function words. */
    private static final Set<String> SEAM_WORDS = new HashSet<>(Arrays.asList("like", "last", "lose", "standing"));

    private VoiceCleanupGate() {
    }

    /** Neighbouring sentences with the same verdict: their text, whether they are sent, and why. */
    public static final class Group {
        @NonNull public final String text;
        public final boolean send;
        @NonNull public final List<String> flags;

        Group(@NonNull String text, boolean send, @NonNull List<String> flags) {
            this.text = text;
            this.send = send;
            this.flags = Collections.unmodifiableList(flags);
        }
    }

    /** {@code text}, a part with its fillers stripped, as groups in order. */
    @NonNull
    public static List<Group> groups(@NonNull String text, @NonNull String level) {
        List<String> sentences = sentences(text);
        boolean inList = spokenList(text);
        List<List<String>> flags = new ArrayList<>();
        for (String sentence : sentences) flags.add(flags(sentence, inList));
        if (inList && VoicePolishRules.LEVEL_POLISHED.equals(VoicePolishRules.normalizeLevel(level))) {
            for (List<String> f : flags) {
                if (f.isEmpty()) f.add(LIST_PART);
            }
        }
        boolean[] send = new boolean[sentences.size()];
        for (int i = 0; i < send.length; i++) send[i] = !flags.get(i).isEmpty();
        for (int i = 0; i < send.length; i++) {
            if (flags.get(i).contains(CORRECTION) && i > 0) send[i - 1] = true;
            if (send[i] && VoicePolishRules.wordCount(sentences.get(i)) < VoicePolishRules.MIN_WORDS
                && i + 1 < send.length) send[i + 1] = true;
        }
        List<Group> groups = new ArrayList<>();
        StringBuilder groupText = new StringBuilder();
        List<String> groupFlags = new ArrayList<>();
        for (int i = 0; i < sentences.size(); i++) {
            if (i > 0 && send[i] != send[i - 1]) {
                groups.add(new Group(groupText.toString(), send[i - 1], groupFlags));
                groupText.setLength(0);
                groupFlags = new ArrayList<>();
            }
            if (groupText.length() > 0) groupText.append(' ');
            groupText.append(sentences.get(i));
            groupFlags.addAll(flags.get(i));
        }
        if (!sentences.isEmpty()) groups.add(new Group(groupText.toString(), send[send.length - 1], groupFlags));
        return groups;
    }

    /** The signs of trouble in one sentence, in a part that {@code inList} counts through items. */
    @NonNull
    static List<String> flags(@NonNull String sentence, boolean inList) {
        List<String> flags = new ArrayList<>();
        if (DISCOURSE_FILLER.matcher(sentence).find()) flags.add(DISCOURSE);
        if (repeatedPhrase(sentence)) flags.add(REPEAT);
        if (CORRECTION_CUE.matcher(sentence).find()) flags.add(CORRECTION);
        if (SPOKEN_SYMBOL.matcher(sentence).find()) flags.add(SYMBOL);
        if (VoicePolishRules.wordCount(sentence) > RUNON_WORDS) flags.add(RUNON);
        if (strayCapital(sentence)) flags.add(STRAY_CAPITAL);
        if (!sentence.isEmpty() && Character.isLowerCase(sentence.codePointAt(0))) flags.add(LOWERCASE_START);
        if (inList && (LIST_ITEM.matcher(sentence).find() || ENUMERATION.matcher(sentence).find())) flags.add(LIST);
        return flags;
    }

    /** Whether {@code text} counts through items ("first… second…", "one, two,") or announces them ("the following"). */
    static boolean spokenList(@NonNull String text) {
        return (LIST_FIRST.matcher(text).find() && LIST_SECOND.matcher(text).find())
            || ENUMERATION.matcher(text).find();
    }

    /** {@code text}'s sentences: split after a full stop, question or exclamation mark. */
    @NonNull
    static List<String> sentences(@NonNull String text) {
        List<String> sentences = new ArrayList<>();
        for (String sentence : SENTENCE_BREAK.split(text.trim())) {
            if (!sentence.isEmpty()) sentences.add(sentence);
        }
        return sentences;
    }

    /**
     * The read-only context ahead of a flagged group: the last sentence of {@code text}'s last line,
     * without a list marker, cut to its last {@link #CONTEXT_MAX_WORDS} words. One sentence, as the
     * eval measured: more context is more for the model to repeat.
     */
    @NonNull
    static String lastSentence(@NonNull String text) {
        String trimmed = text.trim();
        String line = trimmed.substring(trimmed.lastIndexOf('\n') + 1);
        line = LIST_LINE_MARKER.matcher(line).replaceFirst("").trim();
        List<String> sentences = sentences(line);
        String last = sentences.isEmpty() ? "" : sentences.get(sentences.size() - 1);
        List<Integer> starts = new ArrayList<>();
        Matcher word = NON_SPACE.matcher(last);
        while (word.find()) starts.add(word.start());
        if (starts.size() <= CONTEXT_MAX_WORDS) return last;
        return last.substring(starts.get(starts.size() - CONTEXT_MAX_WORDS)).trim();
    }

    /** A run of one to {@link #REPEAT_MAX_WORDS} words said twice in a row ("the the", "I don't, I don't"). */
    static boolean repeatedPhrase(@NonNull String sentence) {
        List<String> tokens = tokens(sentence);
        for (int n = 1; n <= REPEAT_MAX_WORDS; n++) {
            for (int i = 0; i + 2 * n <= tokens.size(); i++) {
                List<String> a = tokens.subList(i, i + n);
                if (!a.contains("") && a.equals(tokens.subList(i + n, i + 2 * n))) return true;
            }
        }
        return false;
    }

    /** A function word capitalised mid-sentence: where Parakeet's chunks were stitched together. */
    static boolean strayCapital(@NonNull String sentence) {
        String trimmed = sentence.trim();
        if (trimmed.isEmpty()) return false;
        String[] words = WHITESPACE.split(trimmed);
        for (int i = 1; i < words.length; i++) {
            String previous = words[i - 1];
            if (".!?:".indexOf(previous.charAt(previous.length() - 1)) >= 0) continue;
            String core = NOT_TOKEN.matcher(words[i]).replaceAll("");
            if (core.isEmpty() || !Character.isUpperCase(core.codePointAt(0))) continue;
            String lower = core.toLowerCase(Locale.ROOT);
            if (ALWAYS_CAPITAL.contains(lower)) continue;
            String bare = lower.replace("'", "");
            if (VoicePolishRules.FUNCTION_WORDS.contains(bare) || SEAM_WORDS.contains(bare)) return true;
        }
        return false;
    }

    /** {@code sentence}'s words, lower-cased, with everything but letters, digits and apostrophes dropped. */
    @NonNull
    private static List<String> tokens(@NonNull String sentence) {
        List<String> tokens = new ArrayList<>();
        String trimmed = sentence.trim();
        if (trimmed.isEmpty()) return tokens;
        for (String word : WHITESPACE.split(trimmed)) {
            tokens.add(NOT_TOKEN.matcher(word).replaceAll("").toLowerCase(Locale.ROOT));
        }
        return tokens;
    }
}
