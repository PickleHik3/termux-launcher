package com.termux.ai;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

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
}
