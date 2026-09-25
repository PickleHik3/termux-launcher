package com.termux.ai;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** Decoding Parakeet's SentencePiece ids: pieces joined, {@code ▁} as a space, specials and the blank dropped. */
public class ParakeetTokenizerTest {
    private static final String JSON = "{\"model\":{\"type\":\"BPE\",\"vocab\":{"
        + "\"<unk>\":0,\"<pad>\":1,\"▁git\":2,\"▁status\":3,\"▁he\":4,\"llo\":5,\"▁\":6,\".\":7,\"É\":8,\"▁café\":9"
        + "}},\"added_tokens\":[{\"id\":0,\"content\":\"<unk>\",\"special\":true},{\"id\":1,\"content\":\"<pad>\",\"special\":false}]}";

    @Test
    public void piecesAreJoinedWithTheWordMarkerAsASpaceAndTrimmed() throws Exception {
        ParakeetTokenizer tokenizer = ParakeetTokenizer.parse(JSON);
        assertEquals(10, tokenizer.size());
        assertEquals("git status", tokenizer.decode(new int[] {2, 3}));
        assertEquals("hello.", tokenizer.decode(new int[] {4, 5, 7}));
        assertEquals("hello", tokenizer.decode(new int[] {6, 4, 5, 6}));
        assertEquals("git café", tokenizer.decode(new int[] {2, 9}));
        assertEquals("", tokenizer.decode(new int[0]));
    }

    @Test
    public void addedTokensTheBlankAndUnknownIdsContributeNothing() throws Exception {
        ParakeetTokenizer tokenizer = ParakeetTokenizer.parse(JSON);
        // <unk> is special, <pad> is an added token that is not — both are dropped, as the reference does.
        assertEquals("hello", tokenizer.decode(new int[] {0, 4, 1, 5}));
        assertEquals("git status", tokenizer.decode(new int[] {ParakeetTdtDecoder.BLANK, 2, ParakeetTdtDecoder.BLANK, 3}));
        assertEquals("git", tokenizer.decode(new int[] {2, 9999, -1}));
    }
}
