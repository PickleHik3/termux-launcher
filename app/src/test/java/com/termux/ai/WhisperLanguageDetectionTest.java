package com.termux.ai;

import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

/** Language identification from the decoder's slot-0 logits, and which language a request is decoded in. */
public class WhisperLanguageDetectionTest {
    // The multilingual layout: EOT 50257, SOT 50258, languages from 50259, tasks and controls after.
    private static final int EOT = 50257;
    private static final int SOT = 50258;
    private static final int EN = 50259;
    private static final int DE = 50261;
    private static final int ES = 50262;
    private static final int TRANSCRIBE = 50359;
    private static final int NO_CAPTIONS = 50362;
    private static final int NO_TIMESTAMPS = 50363;
    private static final int FIRST_TIMESTAMP = 50364;
    private static final int SEQUENCE = 24;
    private static final float DELTA = 1e-5f;

    private static WhisperTokenizer tokenizer() throws Exception {
        int[] ids = {EOT, SOT, ES, EN, DE, TRANSCRIBE, NO_CAPTIONS, NO_TIMESTAMPS, FIRST_TIMESTAMP};
        String[] names = {"<|endoftext|>", "<|startoftranscript|>", "<|es|>", "<|en|>", "<|de|>",
            "<|transcribe|>", "<|nocaptions|>", "<|notimestamps|>", "<|0.00|>"};
        StringBuilder added = new StringBuilder();
        for (int i = 0; i < ids.length; i++) {
            if (i > 0) added.append(',');
            added.append("{\"id\":").append(ids[i]).append(",\"content\":\"").append(names[i]).append("\",\"special\":true}");
        }
        return WhisperTokenizer.parse("{\"added_tokens\":[" + added + "],"
            + "\"model\":{\"type\":\"BPE\",\"vocab\":{\"a\":0,\"b\":1},\"merges\":[]}}");
    }

    private static float[] logits() {
        return new float[FIRST_TIMESTAMP + 10];
    }

    @Test
    public void theLanguagesAreTheTwoAndThreeLetterSpecialsInIdOrder() throws Exception {
        WhisperTokenizer.Languages languages = tokenizer().languages();
        assertArrayEquals(new String[] {"en", "de", "es"}, languages.codes);
        assertArrayEquals(new int[] {EN, DE, ES}, languages.ids);
    }

    @Test
    public void theSoftmaxRunsOverTheLanguageTokensOnly() throws Exception {
        WhisperTokenizer.Languages languages = tokenizer().languages();
        float[] logits = logits();
        logits[EN] = 1f;
        logits[DE] = 3f;
        logits[ES] = 2f;
        // A text token far ahead of every language must not count: the reference masks it out.
        logits[1] = 100f;
        logits[TRANSCRIBE] = 50f;
        WhisperLanguageDetection.Guess guess = WhisperLanguageDetection.guess(logits, languages.codes, languages.ids);
        assertNotNull(guess);
        assertEquals("de", guess.code);
        double e1 = Math.exp(1), e2 = Math.exp(2), e3 = Math.exp(3);
        assertEquals((float) (e3 / (e1 + e2 + e3)), guess.probability, DELTA);
    }

    @Test
    public void largeLogitsDoNotOverflow() throws Exception {
        WhisperTokenizer.Languages languages = tokenizer().languages();
        float[] logits = logits();
        logits[EN] = 1000f;
        logits[DE] = 999f;
        logits[ES] = -1000f;
        WhisperLanguageDetection.Guess guess = WhisperLanguageDetection.guess(logits, languages.codes, languages.ids);
        assertNotNull(guess);
        assertEquals("en", guess.code);
        assertEquals((float) (1.0 / (1.0 + Math.exp(-1))), guess.probability, DELTA);
    }

    @Test
    public void languagesOutsideTheLogitsOrNotANumberAreSkipped() {
        float[] small = new float[10];
        assertNull(WhisperLanguageDetection.guess(small, new String[] {"en", "de"}, new int[] {EN, DE}));
        float[] logits = logits();
        logits[EN] = Float.NaN;
        logits[DE] = 0f;
        WhisperLanguageDetection.Guess guess = WhisperLanguageDetection.guess(logits, new String[] {"en", "de", "xx"}, new int[] {EN, DE, logits.length});
        assertNotNull(guess);
        assertEquals("de", guess.code);
        assertEquals(1f, guess.probability, DELTA);
    }

    @Test
    public void detectionRunsTheDecoderOnStartOfTranscriptAloneAndReadsSlotZero() throws Exception {
        int[] calls = {0};
        WhisperDecoder.Step step = (tokens, filled) -> {
            calls[0]++;
            assertEquals(1, filled);
            assertEquals(SEQUENCE, tokens.length);
            assertEquals(SOT, tokens[0]);
            for (int i = 1; i < SEQUENCE; i++) assertEquals(EOT, tokens[i]);
            float[] logits = logits();
            logits[ES] = 8f;
            logits[EN] = 2f;
            return logits;
        };
        WhisperLanguageDetection.Guess guess = WhisperLanguageDetection.detect(step, tokenizer(), SEQUENCE);
        assertEquals(1, calls[0]);
        assertNotNull(guess);
        assertEquals("es", guess.code);
        assertEquals((float) (Math.exp(8) / (Math.exp(8) + Math.exp(2) + 1.0)), guess.probability, DELTA);
    }

    @Test
    public void aSureGuessIsDetectedEvenOverTheHint() {
        WhisperLanguageDetection.Guess guess = new WhisperLanguageDetection.Guess("es", 0.93f);
        WhisperLanguageDetection.Choice choice = WhisperLanguageDetection.choose(guess, "en");
        assertEquals("es", choice.language);
        assertEquals(WhisperLanguageDetection.SOURCE_DETECTED, choice.source);
        assertSame(guess, choice.guess);
        WhisperLanguageDetection.Choice atTheBar = WhisperLanguageDetection.choose(
            new WhisperLanguageDetection.Guess("es", WhisperLanguageDetection.MIN_PROBABILITY), "en");
        assertEquals(WhisperLanguageDetection.SOURCE_DETECTED, atTheBar.source);
    }

    @Test
    public void anUnsureGuessGivesWayToTheHintAndIsStillReported() {
        WhisperLanguageDetection.Guess guess = new WhisperLanguageDetection.Guess("nn", 0.55f);
        WhisperLanguageDetection.Choice choice = WhisperLanguageDetection.choose(guess, "en");
        assertEquals("en", choice.language);
        assertEquals(WhisperLanguageDetection.SOURCE_FALLBACK, choice.source);
        assertSame(guess, choice.guess);
    }

    @Test
    public void withoutAHintTheTopGuessIsTakenWhateverItsProbability() {
        WhisperLanguageDetection.Choice choice = WhisperLanguageDetection.choose(
            new WhisperLanguageDetection.Guess("de", 0.3f), null);
        assertEquals("de", choice.language);
        assertEquals(WhisperLanguageDetection.SOURCE_DETECTED, choice.source);
    }

    @Test
    public void nothingHeardFallsBackToTheHintThenEnglish() {
        WhisperLanguageDetection.Choice hinted = WhisperLanguageDetection.choose(null, "fr");
        assertEquals("fr", hinted.language);
        assertEquals(WhisperLanguageDetection.SOURCE_FALLBACK, hinted.source);
        assertNull(hinted.guess);
        WhisperLanguageDetection.Choice bare = WhisperLanguageDetection.choose(null, null);
        assertEquals("en", bare.language);
        assertEquals(WhisperLanguageDetection.SOURCE_FALLBACK, bare.source);
    }

    @Test
    public void aNamedLanguageIsForcedAndAutoMeansNone() {
        WhisperLanguageDetection.Choice forced = WhisperLanguageDetection.forced("de");
        assertEquals("de", forced.language);
        assertEquals(WhisperLanguageDetection.SOURCE_FORCED, forced.source);
        assertNull(forced.guess);
        assertNull(WhisperLanguageDetection.normalize(null));
        assertNull(WhisperLanguageDetection.normalize("  "));
        assertNull(WhisperLanguageDetection.normalize(" Auto "));
        assertEquals("es", WhisperLanguageDetection.normalize(" ES "));
    }
}
