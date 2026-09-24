package com.termux.ai;

import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** Prompt construction and the greedy loop, driven by a scripted decode step instead of the graph. */
public class WhisperDecoderTest {
    // The multilingual layout: EOT 50257, SOT 50258, languages from 50259, tasks and controls after.
    private static final int EOT = 50257;
    private static final int SOT = 50258;
    private static final int EN = 50259;
    private static final int DE = 50261;
    private static final int TRANSLATE = 50358;
    private static final int TRANSCRIBE = 50359;
    private static final int START_OF_LM = 50360;
    private static final int START_OF_PREV = 50361;
    private static final int NO_SPEECH = 50362;
    private static final int NO_TIMESTAMPS = 50363;
    private static final int FIRST_TIMESTAMP = 50364;
    private static final int SEQUENCE = 24;

    /** A tiny multilingual-shaped vocabulary: " git" is one token, " ls" is two, like the real one. */
    private static WhisperTokenizer multilingual() throws Exception {
        return WhisperTokenizer.parse(tokenizerJson(true));
    }

    private static String tokenizerJson(boolean languages) {
        StringBuilder added = new StringBuilder();
        int[][] specials = languages
            ? new int[][] {{EOT}, {SOT}, {EN}, {DE}, {TRANSLATE}, {TRANSCRIBE}, {START_OF_LM}, {START_OF_PREV}, {NO_SPEECH}, {NO_TIMESTAMPS}, {FIRST_TIMESTAMP}}
            : new int[][] {{EOT}, {SOT}, {TRANSLATE}, {TRANSCRIBE}, {START_OF_LM}, {START_OF_PREV}, {NO_SPEECH}, {NO_TIMESTAMPS}, {FIRST_TIMESTAMP}};
        String[] names = languages
            ? new String[] {"<|endoftext|>", "<|startoftranscript|>", "<|en|>", "<|de|>", "<|translate|>", "<|transcribe|>", "<|startoflm|>", "<|startofprev|>", "<|nospeech|>", "<|notimestamps|>", "<|0.00|>"}
            : new String[] {"<|endoftext|>", "<|startoftranscript|>", "<|translate|>", "<|transcribe|>", "<|startoflm|>", "<|startofprev|>", "<|nospeech|>", "<|notimestamps|>", "<|0.00|>"};
        for (int i = 0; i < names.length; i++) {
            if (i > 0) added.append(',');
            added.append("{\"id\":").append(specials[i][0]).append(",\"content\":\"").append(names[i]).append("\",\"special\":true}");
        }
        return "{\"added_tokens\":[" + added + "],"
            + "\"model\":{\"type\":\"BPE\","
            // U+0120 is GPT-2's byte-level stand-in for a space.
            + "\"vocab\":{\"Ġ\":0,\"g\":1,\"i\":2,\"t\":3,\"l\":4,\"s\":5,\"Ġg\":6,\"it\":7,\"Ġgit\":8,\"Ġl\":9,\"a\":10,\"Ġa\":11},"
            + "\"merges\":[\"Ġ g\",\"i t\",\"Ġg it\",\"Ġ l\",\"Ġ a\"]}}";
    }

    @Test
    public void multilingualTaskPromptCarriesLanguageAndTask() throws Exception {
        WhisperTokenizer tokenizer = multilingual();
        assertArrayEquals(new int[] {SOT, EN, TRANSCRIBE, NO_TIMESTAMPS}, WhisperDecoder.taskPrompt(tokenizer, "en"));
        assertArrayEquals(new int[] {SOT, DE, TRANSCRIBE, NO_TIMESTAMPS}, WhisperDecoder.taskPrompt(tokenizer, "de"));
        try {
            WhisperDecoder.taskPrompt(tokenizer, "xx");
            fail("unknown language must be refused");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("xx"));
        }
    }

    @Test
    public void aVocabularyWithoutLanguageTokensGetsTheTwoTokenPrompt() throws Exception {
        WhisperTokenizer tokenizer = WhisperTokenizer.parse(tokenizerJson(false));
        assertFalse(tokenizer.hasLanguageTokens());
        assertArrayEquals(new int[] {SOT, NO_TIMESTAMPS}, WhisperDecoder.taskPrompt(tokenizer, "de"));
    }

    @Test
    public void theRealEnglishOnlyTokenizerPromptsLikeTheReferenceDecoder() throws Exception {
        WhisperTokenizer tokenizer = WhisperTokenizer.parse(new String(
            WhisperMelTest.readBytes("whisper/tokenizer_base_en_trimmed.json"), StandardCharsets.UTF_8));
        // expected.json task_prompt_ids: the .en tokenizer carries <|en|>, so it is prompted with it.
        assertArrayEquals(new int[] {50257, 50258, 50358, 50362}, WhisperDecoder.taskPrompt(tokenizer, "en"));
        int[] biased = WhisperDecoder.prompt(tokenizer, "en", WhisperDecoder.TERMINAL_VOCABULARY);
        assertEquals(50360, biased[0]);
        assertArrayEquals(new int[] {17606, 43979, 22927, 21061, 15409}, Arrays.copyOfRange(biased, 1, 6));
        assertArrayEquals(new int[] {50257, 50258, 50358, 50362}, Arrays.copyOfRange(biased, biased.length - 4, biased.length));
        assertEquals(1 + 12 + 4, biased.length);
        assertTrue("bias stays under the cap", biased.length - 4 - 1 <= WhisperDecoder.MAX_BIAS_TOKENS);
    }

    @Test
    public void biasLineSitsBehindStartOfPrevAheadOfTheTaskPrompt() throws Exception {
        WhisperTokenizer tokenizer = multilingual();
        assertArrayEquals(new int[] {START_OF_PREV, 8, 9, 5, SOT, EN, TRANSCRIBE, NO_TIMESTAMPS},
            WhisperDecoder.prompt(tokenizer, "en", "git ls"));
        assertArrayEquals(new int[] {START_OF_PREV, 8, 9, 5, SOT, EN, TRANSCRIBE, NO_TIMESTAMPS},
            WhisperDecoder.prompt(tokenizer, "en", "  git   ls \n"));
        assertArrayEquals(WhisperDecoder.taskPrompt(tokenizer, "en"), WhisperDecoder.prompt(tokenizer, "en", null));
        assertArrayEquals(WhisperDecoder.taskPrompt(tokenizer, "en"), WhisperDecoder.prompt(tokenizer, "en", "   "));
    }

    @Test
    public void biasLineIsCappedSoTheOutputBudgetSurvives() throws Exception {
        WhisperTokenizer tokenizer = multilingual();
        StringBuilder line = new StringBuilder();
        for (int i = 0; i < 40; i++) line.append("git ");
        int[] prompt = WhisperDecoder.prompt(tokenizer, "en", line.toString());
        assertEquals(1 + WhisperDecoder.MAX_BIAS_TOKENS + 4, prompt.length);
        assertEquals(START_OF_PREV, prompt[0]);
        assertEquals(SOT, prompt[1 + WhisperDecoder.MAX_BIAS_TOKENS]);
    }

    // --- the greedy loop -----------------------------------------------------------------------

    private static final WhisperDecoder.Suppression SUPPRESS = new WhisperDecoder.Suppression(EOT, FIRST_TIMESTAMP,
        new int[] {SOT, TRANSLATE, TRANSCRIBE, START_OF_LM, START_OF_PREV, -1, NO_SPEECH});
    private static final int[] PROMPT = {SOT, EN, TRANSCRIBE, NO_TIMESTAMPS};

    /** Step {@code i} ranks {@code preferences[i]} highest, in order; the last script entry repeats. */
    private static WhisperDecoder.Step scripted(int[]... preferences) {
        return (tokens, filled) -> {
            int step = Math.min(filled - PROMPT.length, preferences.length - 1);
            float[] logits = new float[FIRST_TIMESTAMP + 10];
            int[] wanted = preferences[step];
            for (int j = 0; j < wanted.length; j++) logits[wanted[j]] = 100f - j;
            return logits;
        };
    }

    @Test
    public void timestampsAndControlTokensAreNeverPickedAndEotEndsTheSegment() throws Exception {
        WhisperDecoder.Result result = WhisperDecoder.greedy(
            scripted(new int[] {FIRST_TIMESTAMP, FIRST_TIMESTAMP + 3, 7}, new int[] {START_OF_PREV, TRANSCRIBE, NO_SPEECH, 11}, new int[] {EOT, 5}),
            PROMPT, SEQUENCE, SUPPRESS);
        assertArrayEquals(new int[] {7, 11}, result.tokens);
        assertTrue(result.endOfText);
        assertFalse(result.repetition);
        assertEquals(3, result.steps);
    }

    @Test
    public void eotIsSuppressedOnTheFirstStepOnly() throws Exception {
        WhisperDecoder.Result result = WhisperDecoder.greedy(scripted(new int[] {EOT, 9}, new int[] {EOT, 9}), PROMPT, SEQUENCE, SUPPRESS);
        assertArrayEquals(new int[] {9}, result.tokens);
        assertTrue(result.endOfText);
        assertEquals(2, result.steps);
    }

    @Test
    public void theRepetitionGuardStopsAFourGramRepeatedThreeTimes() throws Exception {
        WhisperDecoder.Step cycle = (tokens, filled) -> {
            float[] logits = new float[FIRST_TIMESTAMP + 10];
            logits[1 + (filled - PROMPT.length) % 4] = 1f;
            return logits;
        };
        WhisperDecoder.Result result = WhisperDecoder.greedy(cycle, PROMPT, 128, SUPPRESS);
        assertTrue(result.repetition);
        assertFalse(result.endOfText);
        assertArrayEquals(new int[] {1, 2, 3, 4, 1, 2, 3, 4, 1, 2, 3, 4}, result.tokens);
        assertEquals(12, result.steps);
    }

    @Test
    public void theSequenceLimitIsTheBudgetAndTheStepSeesEotPadding() throws Exception {
        List<int[]> seen = new ArrayList<>();
        WhisperDecoder.Step counting = (tokens, filled) -> {
            if (seen.isEmpty()) {
                assertEquals(PROMPT.length, filled);
                assertEquals(SEQUENCE, tokens.length);
                for (int i = 0; i < PROMPT.length; i++) assertEquals(PROMPT[i], tokens[i]);
                for (int i = PROMPT.length; i < SEQUENCE; i++) assertEquals(EOT, tokens[i]);
            }
            seen.add(tokens.clone());
            float[] logits = new float[FIRST_TIMESTAMP + 10];
            logits[100 + filled] = 1f;
            return logits;
        };
        WhisperDecoder.Result result = WhisperDecoder.greedy(counting, PROMPT, SEQUENCE, SUPPRESS);
        assertEquals(SEQUENCE - PROMPT.length, result.tokens.length);
        assertEquals(SEQUENCE - PROMPT.length, result.steps);
        assertFalse(result.endOfText);
        assertFalse(result.repetition);
        // The last step saw every earlier pick in place.
        int[] last = seen.get(seen.size() - 1);
        assertEquals(100 + PROMPT.length, last[PROMPT.length]);
        assertEquals(EOT, last[SEQUENCE - 1]);
    }

    @Test
    public void aPromptThatFillsTheSequenceIsRefused() throws Exception {
        try {
            WhisperDecoder.greedy(scripted(new int[] {1}), new int[SEQUENCE], SEQUENCE, SUPPRESS);
            fail();
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("sequence"));
        }
    }
}
