/*
 * Copyright 2026 The Google AI Edge Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Modified for Termux Launcher: Java adaptation of Google AI Edge LiteRT samples'
 * SentenceChunker.kt; runtime and behaviour changes are described below.
 */
package com.termux.ai;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Arrays;
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
    /** A word: what splitting on {@code \s+} leaves, found with its place. */
    private static final Pattern WORD = Pattern.compile("\\S+");
    /** A sentence end: terminal punctuation, optional closing quotes or brackets, then white space. */
    private static final Pattern SENTENCE_END = Pattern.compile("[.!?…]+[\"')\\]]*(?=\\s|$)");

    /**
     * One chunk and the stretch of the source text it was read from, {@code [start, end)}. Read
     * aloud speaks a selection a sentence at a time and marks the one being heard, so it needs to
     * know where each chunk sits in what was selected. {@link #text} is the chunk as
     * {@link #chunks} gives it (white space collapsed, the ending rewritten); the range covers the
     * source as written, its line breaks and closing quotes included.
     */
    static final class Span {
        final int start;
        final int end;
        @NonNull final String text;

        Span(int start, int end, @NonNull String text) {
            this.start = start;
            this.end = end;
            this.text = text;
        }

        @NonNull
        @Override
        public String toString() {
            return "[" + start + "," + end + ") " + text;
        }
    }

    private TaiTtsChunker() {}

    @NonNull
    static List<String> chunks(@NonNull String text) {
        List<Span> spans = spans(text);
        List<String> chunks = new ArrayList<>(spans.size());
        for (Span span : spans) chunks.add(span.text);
        return chunks;
    }

    /**
     * The chunks of {@code text} with where each came from; their texts are {@link #chunks}. Each
     * paragraph is flattened with a note of where every flat character stood, and each cut made
     * in the flat text is carried back through that note.
     */
    @NonNull
    static List<Span> spans(@NonNull String text) {
        List<Span> spans = new ArrayList<>();
        Matcher paragraph = PARAGRAPH.matcher(text);
        int from = 0;
        while (true) {
            boolean found = paragraph.find();
            addParagraph(spans, text, from, found ? paragraph.start() : text.length());
            if (!found) break;
            from = paragraph.end();
        }
        return spans;
    }

    /**
     * One paragraph, {@code text[from, to)}: every run of white space (line breaks included) is one
     * space and the ends are trimmed as {@link String#trim} trims, then it is cut into sentences.
     */
    private static void addParagraph(@NonNull List<Span> spans, @NonNull String text, int from, int to) {
        StringBuilder flat = new StringBuilder(Math.max(0, to - from));
        // origin[k] is where the flat text's character k stood in text.
        int[] origin = new int[Math.max(0, to - from)];
        boolean inSpace = false;
        for (int i = from; i < to; i++) {
            char c = text.charAt(i);
            if (isSpace(c)) {
                if (inSpace) continue;
                inSpace = true;
                c = ' ';
            } else {
                inSpace = false;
            }
            origin[flat.length()] = i;
            flat.append(c);
        }
        int first = 0;
        int last = flat.length();
        while (first < last && flat.charAt(first) <= ' ') first++;
        while (last > first && flat.charAt(last - 1) <= ' ') last--;
        if (first == last) return;
        String paragraph = flat.substring(first, last);
        int[] map = Arrays.copyOfRange(origin, first, last);
        Matcher end = SENTENCE_END.matcher(paragraph);
        int start = 0;
        while (end.find()) {
            addSentence(spans, paragraph, map, start, end.end());
            start = end.end();
        }
        if (start < paragraph.length()) addSentence(spans, paragraph, map, start, paragraph.length());
    }

    /** {@code \s} as the regular expressions read it. */
    private static boolean isSpace(char c) {
        return c == ' ' || c == '\t' || c == '\n' || c == '\u000B' || c == '\f' || c == '\r';
    }

    /**
     * The sentence {@code paragraph[from, to)}. What {@link #terminate} gives back is the trimmed
     * sentence's own characters up to its closing punctuation, then a rewritten ending, so an index
     * into it short of that ending is the same index into the paragraph from where it starts.
     */
    private static void addSentence(@NonNull List<Span> spans, @NonNull String paragraph, @NonNull int[] map,
                                    int from, int to) {
        while (from < to && paragraph.charAt(from) <= ' ') from++;
        while (to > from && paragraph.charAt(to - 1) <= ' ') to--;
        String sentence = terminate(paragraph.substring(from, to));
        if (sentence.isEmpty()) return;
        int base = from;
        if (spans.isEmpty() && sentence.length() > FIRST_CHUNK_SOFT_LIMIT) {
            int cut = clauseBreak(sentence, FIRST_CHUNK_SOFT_LIMIT);
            if (cut > 0) {
                addBounded(spans, sentence.substring(0, cut + 1), map, base, base + cut + 1);
                String rest = sentence.substring(cut + 1);
                int lead = 0;
                while (lead < rest.length() && rest.charAt(lead) <= ' ') lead++;
                sentence = rest.trim();
                base += cut + 1 + lead;
                if (sentence.isEmpty()) return;
            }
        }
        addBounded(spans, sentence, map, base, to);
    }

    /**
     * Splits an overlong sentence on word boundaries, as the Kotlin chunker does. {@code sentence}
     * starts at {@code base} in the paragraph and its source ends at {@code to}; only the last piece
     * holds the rewritten ending, so every earlier piece's end is an index into the paragraph.
     */
    private static void addBounded(@NonNull List<Span> spans, @NonNull String sentence, @NonNull int[] map,
                                   int base, int to) {
        if (sentence.length() <= MAX_CHUNK_CHARS) {
            spans.add(span(map, base, to, ensurePunctuation(sentence)));
            return;
        }
        StringBuilder builder = new StringBuilder();
        int pieceStart = 0;
        int pieceEnd = 0;
        Matcher words = WORD.matcher(sentence);
        while (words.find()) {
            String word = words.group();
            if (builder.length() + word.length() + 1 > MAX_CHUNK_CHARS && builder.length() > 0) {
                spans.add(span(map, base + pieceStart, base + pieceEnd, ensurePunctuation(builder.toString())));
                builder.setLength(0);
            }
            if (word.length() > MAX_CHUNK_CHARS) word = word.substring(0, MAX_CHUNK_CHARS - 1);
            if (builder.length() > 0) builder.append(' ');
            else pieceStart = words.start();
            builder.append(word);
            pieceEnd = words.end();
        }
        if (builder.length() > 0) spans.add(span(map, base + pieceStart, to, ensurePunctuation(builder.toString())));
    }

    /** The paragraph's {@code [from, to)} as a range of the source text. */
    @NonNull
    private static Span span(@NonNull int[] map, int from, int to, @NonNull String text) {
        return new Span(map[from], map[to - 1] + 1, text);
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
