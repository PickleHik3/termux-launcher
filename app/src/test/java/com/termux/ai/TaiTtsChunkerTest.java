package com.termux.ai;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Sentence chunks: the streaming unit, so where they split decides when the first audio starts. */
public class TaiTtsChunkerTest {

    @Test
    public void splitsIntoSentencesEndingInTheModelsComma() {
        assertEquals(Arrays.asList("The build finished,", "Three warnings,"),
            TaiTtsChunker.chunks("The build finished. Three warnings."));
    }

    @Test
    public void keepsQuestionAndExclamationMarks() {
        assertEquals(Arrays.asList("Did you mean git status, or git stash?", "Done!", "Next,"),
            TaiTtsChunker.chunks("Did you mean git status, or git stash? Done! Next"));
        assertEquals(Collections.singletonList("Really?"), TaiTtsChunker.chunks("Really?!"));
    }

    @Test
    public void dotsInsideNumbersAndNamesDoNotEndASentence() {
        assertEquals(Collections.singletonList("Version 2.5 is on example.com now,"),
            TaiTtsChunker.chunks("Version 2.5 is on example.com now."));
    }

    @Test
    public void lineBreaksJoinAndBlankLinesSeparate() {
        assertEquals(Arrays.asList("A wrapped terminal line that goes on,", "New paragraph,"),
            TaiTtsChunker.chunks("A wrapped terminal\nline that goes on\n\nNew paragraph"));
    }

    @Test
    public void closingQuotesAndEllipsesAreHandled() {
        assertEquals(Arrays.asList("He said \"stop,", "Then left,"),
            TaiTtsChunker.chunks("He said \"stop.\" Then left..."));
    }

    @Test
    public void aLongFirstSentenceIsCutAtAClauseBreakForAFasterStart() {
        String clause = "The installer downloaded every package, checked each signature against the catalogue";
        String rest = "and then unpacked them one by one into the prefix while the progress bar crept along slowly";
        List<String> chunks = TaiTtsChunker.chunks(clause + ", " + rest + ". Done.");
        assertEquals(clause + ",", chunks.get(0));
        assertEquals(rest + ",", chunks.get(1));
        assertEquals("Done,", chunks.get(2));
        assertTrue(chunks.get(0).length() <= TaiTtsChunker.FIRST_CHUNK_SOFT_LIMIT);
    }

    @Test
    public void noChunkIsLongerThanTheVoiceTable() {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < 200; i++) text.append("word").append(i).append(' ');
        List<String> chunks = TaiTtsChunker.chunks(text.toString());
        assertTrue(chunks.size() > 1);
        for (String chunk : chunks) {
            assertTrue(chunk.length() <= TaiTtsChunker.MAX_CHUNK_CHARS + 1);
            assertTrue(",.!?;:".indexOf(chunk.charAt(chunk.length() - 1)) >= 0);
        }
    }

    @Test
    public void emptyAndPunctuationOnlyInputGiveNothing() {
        assertTrue(TaiTtsChunker.chunks("").isEmpty());
        assertTrue(TaiTtsChunker.chunks("   \n\n  ").isEmpty());
        assertTrue(TaiTtsChunker.chunks("... ?!").isEmpty());
    }

    // ---- spans: where each chunk sits in the source, for Read aloud's highlight

    @Test
    public void spansCoverEachSentenceAsWritten() {
        String text = "The build finished. Three warnings.";
        List<TaiTtsChunker.Span> spans = TaiTtsChunker.spans(text);
        assertEquals(2, spans.size());
        assertEquals("The build finished.", source(text, spans.get(0)));
        assertEquals("The build finished,", spans.get(0).text);
        assertEquals("Three warnings.", source(text, spans.get(1)));
    }

    @Test
    public void spansKeepLineBreaksQuotesAndLeadingSpaceInTheSource() {
        String text = "  A wrapped terminal\n   line that goes on.\n\n\tHe said \"stop.\" Then left...";
        List<TaiTtsChunker.Span> spans = TaiTtsChunker.spans(text);
        assertEquals(3, spans.size());
        assertEquals("A wrapped terminal\n   line that goes on.", source(text, spans.get(0)));
        assertEquals("He said \"stop.\"", source(text, spans.get(1)));
        assertEquals("Then left...", source(text, spans.get(2)));
    }

    @Test
    public void spansFollowTheFirstSentencesClauseCut() {
        String clause = "The installer downloaded every package, checked each signature against the catalogue";
        String rest = "and then unpacked them one by one into the prefix while the progress bar crept along slowly";
        String text = clause + ",\n  " + rest + ". Done.";
        List<TaiTtsChunker.Span> spans = TaiTtsChunker.spans(text);
        assertEquals(3, spans.size());
        assertEquals(clause + ",", source(text, spans.get(0)));
        assertEquals(rest + ".", source(text, spans.get(1)));
        assertEquals("Done.", source(text, spans.get(2)));
    }

    @Test
    public void spansOfAnOverlongSentenceTileItWordByWord() {
        StringBuilder text = new StringBuilder();
        for (int i = 0; i < 200; i++) text.append("word").append(i).append(i % 7 == 0 ? "\n" : " ");
        String source = text.toString();
        List<TaiTtsChunker.Span> spans = TaiTtsChunker.spans(source);
        assertTrue(spans.size() > 1);
        int previousEnd = 0;
        for (TaiTtsChunker.Span span : spans) {
            assertTrue(span.start >= previousEnd);
            assertTrue(source.substring(previousEnd, span.start).trim().isEmpty());
            String words = source(source, span).replaceAll("\\s+", " ");
            assertEquals(span.text.substring(0, span.text.length() - 1), words);
            previousEnd = span.end;
        }
        assertTrue(source.substring(previousEnd).trim().isEmpty());
    }

    /** The span texts are chunks() exactly as it read before spans existed, on awkward input too. */
    @Test
    public void spanTextsAreTheChunksThePreviousChunkerGave() {
        String[] samples = {
            "The build finished. Three warnings.",
            "Did you mean git status, or git stash? Done! Next",
            "Really?!",
            "Version 2.5 is on example.com now.",
            "A wrapped terminal\nline that goes on\n\nNew paragraph",
            "He said \"stop.\" Then left...",
            "  lead\r\n\r\n trail  \u000B\f x. (y.) [z!] \u0001ctl\u0001 ",
            "one\r\ntwo \t three.\n \n\n\nfour . . . five",
            "",
            "... ?!",
            "no punctuation at all",
        };
        for (String sample : samples) assertEquals(sample, legacyChunks(sample), TaiTtsChunker.chunks(sample));
        Random random = new Random(7);
        String alphabet = "abc .,!?;:…\"')]\n\r\t \u0001xyz";
        for (int round = 0; round < 3000; round++) {
            StringBuilder text = new StringBuilder();
            int length = random.nextInt(round % 10 == 0 ? 1200 : 220);
            for (int i = 0; i < length; i++) text.append(alphabet.charAt(random.nextInt(alphabet.length())));
            String sample = text.toString();
            assertEquals(sample, legacyChunks(sample), TaiTtsChunker.chunks(sample));
            int previousEnd = 0;
            for (TaiTtsChunker.Span span : TaiTtsChunker.spans(sample)) {
                assertTrue(sample, span.start >= previousEnd && span.end > span.start && span.end <= sample.length());
                previousEnd = span.end;
            }
        }
    }

    private static String source(String text, TaiTtsChunker.Span span) {
        return text.substring(span.start, span.end);
    }

    // The chunker as it was written before it tracked offsets, kept here as the reference.

    private static final Pattern LEGACY_PARAGRAPH = Pattern.compile("\\r?\\n[ \\t]*\\r?\\n");
    private static final Pattern LEGACY_LINE_BREAK = Pattern.compile("[ \\t]*\\r?\\n[ \\t]*");
    private static final Pattern LEGACY_WHITESPACE = Pattern.compile("\\s+");
    private static final Pattern LEGACY_SENTENCE_END = Pattern.compile("[.!?…]+[\"')\\]]*(?=\\s|$)");

    private static List<String> legacyChunks(String text) {
        List<String> chunks = new ArrayList<>();
        for (String paragraph : LEGACY_PARAGRAPH.split(text)) {
            String flat = LEGACY_WHITESPACE.matcher(LEGACY_LINE_BREAK.matcher(paragraph).replaceAll(" ")).replaceAll(" ").trim();
            if (flat.isEmpty()) continue;
            Matcher end = LEGACY_SENTENCE_END.matcher(flat);
            int start = 0;
            while (end.find()) {
                legacySentence(chunks, flat.substring(start, end.end()));
                start = end.end();
            }
            if (start < flat.length()) legacySentence(chunks, flat.substring(start));
        }
        return chunks;
    }

    private static void legacySentence(List<String> chunks, String raw) {
        String sentence = TaiTtsChunker.terminate(raw.trim());
        if (sentence.isEmpty()) return;
        if (chunks.isEmpty() && sentence.length() > TaiTtsChunker.FIRST_CHUNK_SOFT_LIMIT) {
            int cut = legacyClauseBreak(sentence, TaiTtsChunker.FIRST_CHUNK_SOFT_LIMIT);
            if (cut > 0) {
                legacyBounded(chunks, sentence.substring(0, cut + 1));
                sentence = sentence.substring(cut + 1).trim();
                if (sentence.isEmpty()) return;
            }
        }
        legacyBounded(chunks, sentence);
    }

    private static void legacyBounded(List<String> chunks, String sentence) {
        if (sentence.length() <= TaiTtsChunker.MAX_CHUNK_CHARS) {
            chunks.add(legacyPunctuation(sentence));
            return;
        }
        StringBuilder builder = new StringBuilder();
        for (String word : LEGACY_WHITESPACE.split(sentence)) {
            if (word.isEmpty()) continue;
            if (builder.length() + word.length() + 1 > TaiTtsChunker.MAX_CHUNK_CHARS && builder.length() > 0) {
                chunks.add(legacyPunctuation(builder.toString()));
                builder.setLength(0);
            }
            if (word.length() > TaiTtsChunker.MAX_CHUNK_CHARS) word = word.substring(0, TaiTtsChunker.MAX_CHUNK_CHARS - 1);
            if (builder.length() > 0) builder.append(' ');
            builder.append(word);
        }
        if (builder.length() > 0) chunks.add(legacyPunctuation(builder.toString()));
    }

    private static int legacyClauseBreak(String sentence, int limit) {
        for (int i = Math.min(limit, sentence.length() - 2); i >= 20; i--) {
            char c = sentence.charAt(i);
            if ((c == ',' || c == ';' || c == ':') && Character.isWhitespace(sentence.charAt(i + 1))) return i;
        }
        return -1;
    }

    private static String legacyPunctuation(String sentence) {
        return ".!?,;:".indexOf(sentence.charAt(sentence.length() - 1)) >= 0 ? sentence : sentence + ",";
    }
}
