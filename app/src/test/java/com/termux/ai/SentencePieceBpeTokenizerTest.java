package com.termux.ai;

import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class SentencePieceBpeTokenizerTest {
    private static SentencePieceBpeTokenizer tokenizerOf(List<String> pieces, float[] scores) {
        return new SentencePieceBpeTokenizer(pieces, scores);
    }

    private static List<String> withControls(String... pieces) {
        String[] all = new String[pieces.length + 4];
        all[0] = "<pad>";
        all[1] = "<eos>";
        all[2] = "<bos>";
        all[3] = "<unk>";
        System.arraycopy(pieces, 0, all, 4, pieces.length);
        return Arrays.asList(all);
    }

    private static float[] withControlScores(float... scores) {
        float[] all = new float[scores.length + 4];
        System.arraycopy(scores, 0, all, 4, scores.length);
        return all;
    }

    @Test
    public void highestScoringMergeWinsRegardlessOfPosition() {
        SentencePieceBpeTokenizer left = tokenizerOf(
            withControls("a", "b", "c", "ab", "bc"),
            withControlScores(-10f, -10f, -10f, -1f, -5f));
        assertArrayEquals(new int[]{left.pieceToId("ab"), left.pieceToId("c")}, left.encode("abc"));

        SentencePieceBpeTokenizer right = tokenizerOf(
            withControls("a", "b", "c", "ab", "bc"),
            withControlScores(-10f, -10f, -10f, -5f, -1f));
        assertArrayEquals(new int[]{right.pieceToId("a"), right.pieceToId("bc")}, right.encode("abc"));
    }

    @Test
    public void everyDigitIsItsOwnTokenEvenWhenALongerDigitPieceExists() {
        SentencePieceBpeTokenizer tokenizer = tokenizerOf(
            withControls("1", "2", "12"),
            withControlScores(-10f, -10f, -1f));
        assertArrayEquals(new int[]{tokenizer.pieceToId("1"), tokenizer.pieceToId("2")}, tokenizer.encode("12"));
    }

    @Test
    public void scriptChangeSplitsSegments() {
        SentencePieceBpeTokenizer tokenizer = tokenizerOf(
            withControls("a", "我", "a我"),
            withControlScores(-10f, -10f, -1f));
        assertArrayEquals(new int[]{tokenizer.pieceToId("a"), tokenizer.pieceToId("我")}, tokenizer.encode("a我"));
    }

    @Test
    public void whitespaceRunSplitsBeforeDigitButJoinsFollowingLetter() {
        SentencePieceBpeTokenizer tokenizer = tokenizerOf(
            withControls("a", "b", "1", "▁", "▁▁", "▁▁b"),
            withControlScores(-10f, -10f, -10f, -10f, -2f, -1f));

        assertArrayEquals(
            new int[]{tokenizer.pieceToId("a"), tokenizer.pieceToId("▁▁"), tokenizer.pieceToId("1")},
            tokenizer.encode("a  1"));
        assertArrayEquals(
            new int[]{tokenizer.pieceToId("a"), tokenizer.pieceToId("▁▁b")},
            tokenizer.encode("a  b"));
    }

    @Test
    public void unknownCharacterFallsBackToUppercaseUtf8BytePieces() {
        SentencePieceBpeTokenizer tokenizer = tokenizerOf(
            withControls("a", "<0xC3>", "<0xA9>"),
            withControlScores(-10f, -10f, -10f));
        assertArrayEquals(
            new int[]{tokenizer.pieceToId("<0xC3>"), tokenizer.pieceToId("<0xA9>")},
            tokenizer.encode("é"));
    }

    @Test
    public void unknownSymbolWithoutByteFallbackPieceMapsToUnkId() {
        SentencePieceBpeTokenizer tokenizer = tokenizerOf(
            withControls("a"),
            withControlScores(-10f));
        assertArrayEquals(new int[]{3}, tokenizer.encode("$"));
    }

    @Test
    public void emptyStringEncodesToEmptyArray() {
        SentencePieceBpeTokenizer tokenizer = tokenizerOf(
            withControls("a"),
            withControlScores(-10f));
        assertEquals(0, tokenizer.encode("").length);
    }

    @Test
    public void astralCharacterIsASingleSymbol() {
        String emoji = new String(Character.toChars(0x1F600));
        SentencePieceBpeTokenizer tokenizer = tokenizerOf(
            withControls("a", emoji),
            withControlScores(-10f, -10f));
        assertEquals(6, tokenizer.vocabularySize());
        assertArrayEquals(new int[]{tokenizer.pieceToId(emoji)}, tokenizer.encode(emoji));
    }
}
