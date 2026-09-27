package com.termux.ai;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Splits text into the sentence-sized pieces speech is synthesised in. Each piece is synthesised
 * and queued for playback while the next one computes, so the first sound comes after one short
 * sentence however long the text is. The port of Google's {@code SentenceChunker.kt} (the KittenTTS
 * pip package's {@code chunk_text}), with four deliberate changes:
 *
 * <ul>
 *   <li>A sentence ends at {@code .!?…} followed by white space, not at every dot, so "3.5", "v2.1"
 *       and "example.com" stay whole instead of becoming three sentences.</li>
 *   <li>{@code ?} and {@code !} are kept as the chunk's last token instead of being replaced by a
 *       comma. Upstream consumes them; measured on the prosody graph's F0 (tts-eval, 2026-09-27)
 *       the model barely uses {@code ?} for pitch, but it does hold the ending a little longer, and
 *       keeping it costs nothing. A full stop still becomes the comma upstream tuned against.</li>
 *   <li>A single line break is a space and a blank line is a sentence end: a terminal selection
 *       wraps mid-sentence, and reading every wrapped line as its own sentence sounds broken.</li>
 *   <li>A first sentence longer than {@link #FIRST_CHUNK_SOFT_LIMIT} characters is cut at its last
 *       clause break ({@code , ; :}) before the limit, so a long opening sentence does not hold up
 *       the first audio.</li>
 * </ul>
 *
 * Every chunk ends in punctuation ({@code ,} when it had none), and none is longer than
 * {@link #MAX_CHUNK_CHARS}, the last row of the voice tables.
 */
final class TaiTtsChunker {
    static final int MAX_CHUNK_CHARS = 400;
    static final int FIRST_CHUNK_SOFT_LIMIT = 160;
    private static final String PUNCTUATION = ".!?,;:";

    private static final Pattern PARAGRAPH = Pattern.compile("\\r?\\n[ \\t]*\\r?\\n");
    private static final Pattern LINE_BREAK = Pattern.compile("[ \\t]*\\r?\\n[ \\t]*");
    private static final Pattern WHITESPACE = Pattern.compile("\\s+");
    /** A sentence end: terminal punctuation, optional closing quotes or brackets, then white space. */
    private static final Pattern SENTENCE_END = Pattern.compile("[.!?…]+[\"')\\]]*(?=\\s|$)");

    private TaiTtsChunker() {}

    @NonNull
    static List<String> chunks(@NonNull String text) {
        List<String> chunks = new ArrayList<>();
        for (String paragraph : PARAGRAPH.split(text)) {
            String flat = WHITESPACE.matcher(LINE_BREAK.matcher(paragraph).replaceAll(" ")).replaceAll(" ").trim();
            if (flat.isEmpty()) continue;
            Matcher end = SENTENCE_END.matcher(flat);
            int start = 0;
            while (end.find()) {
                addSentence(chunks, flat.substring(start, end.end()));
                start = end.end();
            }
            if (start < flat.length()) addSentence(chunks, flat.substring(start));
        }
        return chunks;
    }

    private static void addSentence(@NonNull List<String> chunks, @NonNull String raw) {
        String sentence = terminate(raw.trim());
        if (sentence.isEmpty()) return;
        if (chunks.isEmpty() && sentence.length() > FIRST_CHUNK_SOFT_LIMIT) {
            int cut = clauseBreak(sentence, FIRST_CHUNK_SOFT_LIMIT);
            if (cut > 0) {
                addBounded(chunks, sentence.substring(0, cut + 1));
                sentence = sentence.substring(cut + 1).trim();
                if (sentence.isEmpty()) return;
            }
        }
        addBounded(chunks, sentence);
    }

    /** Splits an overlong sentence on word boundaries, as the Kotlin chunker does. */
    private static void addBounded(@NonNull List<String> chunks, @NonNull String sentence) {
        if (sentence.length() <= MAX_CHUNK_CHARS) {
            chunks.add(ensurePunctuation(sentence));
            return;
        }
        StringBuilder builder = new StringBuilder();
        for (String word : WHITESPACE.split(sentence)) {
            if (word.isEmpty()) continue;
            if (builder.length() + word.length() + 1 > MAX_CHUNK_CHARS && builder.length() > 0) {
                chunks.add(ensurePunctuation(builder.toString()));
                builder.setLength(0);
            }
            if (word.length() > MAX_CHUNK_CHARS) word = word.substring(0, MAX_CHUNK_CHARS - 1);
            if (builder.length() > 0) builder.append(' ');
            builder.append(word);
        }
        if (builder.length() > 0) chunks.add(ensurePunctuation(builder.toString()));
    }

    /**
     * Strips the sentence's closing quotes and brackets and turns a final full stop or ellipsis
     * into the comma the model was tuned on; a final {@code ?} or {@code !} is kept.
     */
    @NonNull
    static String terminate(@NonNull String sentence) {
        int end = sentence.length();
        while (end > 0 && "\"')]".indexOf(sentence.charAt(end - 1)) >= 0) end--;
        int punctuationStart = end;
        while (punctuationStart > 0 && ".!?…".indexOf(sentence.charAt(punctuationStart - 1)) >= 0) punctuationStart--;
        String body = sentence.substring(0, punctuationStart).trim();
        if (body.isEmpty()) return "";
        String ending = sentence.substring(punctuationStart, end);
        if (ending.indexOf('?') >= 0) return body + "?";
        if (ending.indexOf('!') >= 0) return body + "!";
        return ensurePunctuation(body);
    }

    /** The last {@code , ; :} at or before {@code limit} that leaves a chunk of at least 20 characters; -1 when none. */
    private static int clauseBreak(@NonNull String sentence, int limit) {
        for (int i = Math.min(limit, sentence.length() - 2); i >= 20; i--) {
            char c = sentence.charAt(i);
            if ((c == ',' || c == ';' || c == ':') && Character.isWhitespace(sentence.charAt(i + 1))) return i;
        }
        return -1;
    }

    @NonNull
    private static String ensurePunctuation(@NonNull String sentence) {
        return PUNCTUATION.indexOf(sentence.charAt(sentence.length() - 1)) >= 0 ? sentence : sentence + ",";
    }
}
