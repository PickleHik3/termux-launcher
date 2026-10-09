package com.termux.ai;

import org.junit.BeforeClass;
import org.junit.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Against {@code tokenizer_base_en_trimmed.json}: the real {@code openai/whisper-base.en}
 * tokenizer.json with every added token kept and the vocab/merges cut down to what these strings
 * need (a trimmed merge list encodes them identically: at every step the merge the full list would
 * pick is the lowest-ranked one present). The expected ids come from the GPT-2 BPE algorithm run
 * over the full file in Python.
 */
public class WhisperTokenizerTest {
    private static WhisperTokenizer tokenizer;

    @BeforeClass
    public static void load() throws Exception {
        tokenizer = WhisperTokenizer.parse(new String(WhisperMelTest.readBytes("whisper/tokenizer_base_en_trimmed.json"), StandardCharsets.UTF_8));
    }

    @Test
    public void specialTokensComeFromAddedTokensNotConstants() {
        assertEquals(50256, tokenizer.specialId(WhisperTokenizer.EOT));
        assertEquals(50257, tokenizer.specialId(WhisperTokenizer.SOT));
        assertEquals(50258, tokenizer.languageToken("en"));
        assertEquals(50258, tokenizer.languageToken(" EN "));
        assertEquals(50358, tokenizer.specialId(WhisperTokenizer.TRANSCRIBE));
        assertEquals(50360, tokenizer.specialId(WhisperTokenizer.START_OF_PREV));
        assertEquals(50362, tokenizer.specialId(WhisperTokenizer.NO_TIMESTAMPS));
        assertEquals(50363, tokenizer.specialId("<|0.00|>"));
        assertTrue(tokenizer.hasLanguageTokens());
        assertEquals(-1, tokenizer.languageToken("xx"));
        assertEquals(-1, tokenizer.languageToken("transcribe"));
        assertEquals(-1, tokenizer.languageToken(null));
        assertTrue(tokenizer.isSpecial(50362));
        assertFalse(tokenizer.isSpecial(3855));
    }

    @Test
    public void decodesTheFixtureTranscripts() {
        assertEquals("Get status", tokenizer.decode(new int[] {3855, 3722}));
        assertEquals("git status", tokenizer.decode(new int[] {18300, 3722}));
        // Specials are dropped from text, wherever they sit.
        assertEquals("git status", tokenizer.decode(new int[] {50257, 50258, 18300, 3722, 50256}));
        // Multi-byte UTF-8 comes back through the byte-level table.
        assertEquals(" naïve café", tokenizer.decode(new int[] {12385, 26884, 303, 19945, 2634}));
    }

    @Test
    public void encodesAShellBiasLineLikeTheReferenceBpe() {
        String line = "git ls cd sudo apt pkg tab enter escape ctrl key";
        int[] ids = tokenizer.encode(" " + line);
        // " git ls cd sudo apt", " tab enter escape" and " key" are single tokens (the fixture's bias ids);
        // "pkg" and "ctrl" are not in the vocab and split into Ġp+kg and Ġc+trl.
        assertArrayEquals(new int[] {17606, 43979, 22927, 21061, 15409, 279, 10025, 7400, 3802, 6654, 269, 14859, 1994}, ids);
        assertEquals(" " + line, tokenizer.decode(ids));
    }

    @Test
    public void encodeRoundTripsThroughDecode() {
        for (String text : new String[] {"Get status", "git status", " naïve café", ""}) {
            assertEquals(text, tokenizer.decode(tokenizer.encode(text)));
        }
        assertArrayEquals(new int[] {3855, 3722}, tokenizer.encode("Get status"));
        assertArrayEquals(new int[] {18300, 3722}, tokenizer.encode("git status"));
        assertArrayEquals(new int[] {12385, 26884, 303, 19945, 2634}, tokenizer.encode(" naïve café"));
    }
}
