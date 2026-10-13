package com.termux.app.terminal.inappkeyboard.voice;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Takes vocalised fillers ("uh", "um", "erm", "hmm", "Uhhhhh", "äh", "euh") out of dictated text
 * before the cleanup decides what to send, so a sentence whose only trouble was a hesitation never
 * costs a model call ({@link VoiceGatedCleanup}). The model left 21–25% of them in the 2026-10-13
 * eval, and an "uh" in the anchor dictation. Words that are fillers only sometimes ("like", "so",
 * "well", "you know") are the model's, on sentences the gate sends.
 *
 * <p>The punctuation and capitals around a filler are put right: one that opened a sentence hands
 * its capital to the next word ("Um, so the" → "So the"), one that closed it leaves the full stop
 * ("Okay, um." → "Okay."), and mid-sentence the commas it was spoken between become one, or none
 * after a word a comma never follows ("because, uh, half" → "because half"). A word in capitals is
 * an acronym ("ER") and stays. The short spellings that are words in some languages ("um" in German
 * and Portuguese, "er" in German and Dutch, "eh" in German) are stripped only in the languages where
 * they are not ({@link VoiceTextLanguage}).
 */
public final class VoiceFillers {

    /** Spellings that are a filler in every language the cleanup names. */
    static final String EVERYWHERE =
        "u+h+m*|u+m{2,}|u{2,}m+|e+r+m+|h+m+|[äö]+h+m*|e+u+h+|e+h+m+|e+h{2,}|e{2,}h+|э+м*";
    /** Languages where a bare "um" is a filler: it is a word in German and Portuguese. */
    static final String UM_LANGUAGES = "en es fr it nl pl";
    /** Languages where a bare "er" is a filler: it is "he" in German and "there" in Dutch. */
    static final String ER_LANGUAGES = "en";
    /** Languages where a bare "eh" is a filler: it is "anyway" in German. */
    static final String EH_LANGUAGES = "en es fr it pt nl pl";
    /** English words a comma never follows mid-clause: after one, the comma was the filler's pause. */
    static final String BINDING_WORDS =
        "a an the my your his her its our their this that to of in on at for with by from into about and or but "
            + "because if than is are was were be am will would can could should have has had do does did i we they he "
            + "she you it not";

    private static final String BOUNDARY = "[\\p{L}\\p{N}_'’\\-]";
    /** The pause punctuation spoken with a filler, after it: commas, semicolons, ellipses, dashes. */
    private static final Pattern PAUSE_AFTER = Pattern.compile("(?:[ \\t]*(?:[,;…]|\\.{2,}|[–—]|-(?=\\s)))*[ \\t]*");
    private static final Pattern SENTENCE_BEFORE = Pattern.compile("[.?!…][\"'”’)]*$");
    private static final Pattern LAST_WORD = Pattern.compile("[\\p{L}\\p{N}_'’]+$");
    private static final String SENTENCE_OPENING_PUNCTUATION = ".?!…,;: \t";
    private static final Set<String> BINDING = words(BINDING_WORDS);
    private static final Set<String> UM = words(UM_LANGUAGES);
    private static final Set<String> ER = words(ER_LANGUAGES);
    private static final Set<String> EH = words(EH_LANGUAGES);

    private VoiceFillers() {
    }

    /** {@code text} without its vocalised fillers, for a dictation in {@code language} ({@code null}: unknown). */
    @NonNull
    public static String strip(@NonNull String text, @Nullable String language) {
        Pattern filler = pattern(language);
        int pos = 0;
        while (true) {
            Matcher m = filler.matcher(text);
            if (!m.find(pos)) return text;
            String token = m.group();
            if (token.length() >= 2 && token.equals(token.toUpperCase(Locale.ROOT))) {
                pos = m.end();
                continue;
            }
            int start = m.start();
            while (start > 0 && (text.charAt(start - 1) == ' ' || text.charAt(start - 1) == '\t')) start--;
            Matcher pause = PAUSE_AFTER.matcher(text);
            pause.region(m.end(), text.length());
            int end = pause.lookingAt() ? pause.end() : m.end();
            String left = text.substring(0, start);
            String gap = text.substring(start, m.start());
            String right = text.substring(end);
            if (left.isEmpty() || left.endsWith("\n") || SENTENCE_BEFORE.matcher(left).find()) {
                // It opened a sentence: the next word opens it now.
                right = stripLeading(right, SENTENCE_OPENING_PUNCTUATION);
                if (Character.isUpperCase(token.codePointAt(0)) && !right.isEmpty()) {
                    int first = Character.charCount(right.codePointAt(0));
                    right = right.substring(0, first).toUpperCase(Locale.ROOT) + right.substring(first);
                }
                if (left.isEmpty() || right.isEmpty()) gap = "";
                text = left + gap + right;
                pos = left.length() + gap.length();
            } else if (right.isEmpty() || ".?!".indexOf(right.charAt(0)) >= 0) {
                // It closed one: "Okay, um." is "Okay."
                left = stripTrailing(left, ",;");
                text = left + right;
                pos = left.length();
            } else {
                if (left.endsWith(",")) {
                    Matcher word = LAST_WORD.matcher(left.substring(0, left.length() - 1));
                    if (word.find() && BINDING.contains(word.group().toLowerCase(Locale.ROOT))) {
                        left = left.substring(0, left.length() - 1);
                    }
                }
                text = left + " " + right;
                pos = left.length() + 1;
            }
        }
    }

    @NonNull
    private static Pattern pattern(@Nullable String language) {
        StringBuilder alternatives = new StringBuilder(EVERYWHERE);
        if (language != null) {
            if (UM.contains(language)) alternatives.append("|um");
            if (ER.contains(language)) alternatives.append("|er");
            if (EH.contains(language)) alternatives.append("|eh");
        }
        return Pattern.compile("(?<!" + BOUNDARY + ")(?:" + alternatives + ")(?!" + BOUNDARY + ")",
            Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }

    @NonNull
    private static String stripLeading(@NonNull String text, @NonNull String chars) {
        int i = 0;
        while (i < text.length() && chars.indexOf(text.charAt(i)) >= 0) i++;
        return text.substring(i);
    }

    @NonNull
    private static String stripTrailing(@NonNull String text, @NonNull String chars) {
        int i = text.length();
        while (i > 0 && chars.indexOf(text.charAt(i - 1)) >= 0) i--;
        return text.substring(0, i);
    }

    @NonNull
    private static Set<String> words(@NonNull String list) {
        return new HashSet<>(Arrays.asList(list.split(" ")));
    }
}
