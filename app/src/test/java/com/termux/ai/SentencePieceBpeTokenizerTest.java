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

    /** The heap merge must pick exactly what rescanning every pair per merge picked. */
    @Test
    public void heapMergeMatchesTheRescanOnRandomText() {
        java.util.Random random = new java.util.Random(42);
        String alphabet = "abc▁";
        List<String> pieces = new java.util.ArrayList<>(Arrays.asList("<pad>", "<eos>", "<bos>", "<unk>"));
        for (char c : alphabet.toCharArray()) pieces.add(String.valueOf(c));
        java.util.Set<String> seen = new java.util.HashSet<>(pieces);
        while (pieces.size() < 60) {
            String piece = pieces.get(4 + random.nextInt(pieces.size() - 4)) + pieces.get(4 + random.nextInt(pieces.size() - 4));
            if (piece.length() <= 6 && seen.add(piece)) pieces.add(piece);
        }
        float[] scores = new float[pieces.size()];
        // Few distinct scores, so ties are common and the leftmost rule is exercised.
        for (int i = 4; i < scores.length; i++) scores[i] = -random.nextInt(5);
        SentencePieceBpeTokenizer tokenizer = tokenizerOf(pieces, scores);
        for (int round = 0; round < 500; round++) {
            StringBuilder text = new StringBuilder();
            int length = 1 + random.nextInt(40);
            for (int i = 0; i < length; i++) text.append("abc".charAt(random.nextInt(3)));
            assertArrayEquals(text.toString(), rescan(pieces, scores, text.toString()), tokenizer.encode(text.toString()));
        }
    }

    /** The old O(n^2) loop, for one Latin segment with no spaces or digits. */
    private static int[] rescan(List<String> pieces, float[] scores, String text) {
        java.util.Map<String, Integer> ids = new java.util.HashMap<>();
        for (int i = 0; i < pieces.size(); i++) ids.put(pieces.get(i), i);
        List<String> symbols = new java.util.ArrayList<>();
        for (char c : text.toCharArray()) symbols.add(String.valueOf(c));
        while (symbols.size() > 1) {
            int best = -1;
            float bestScore = 0f;
            for (int i = 0; i + 1 < symbols.size(); i++) {
                Integer id = ids.get(symbols.get(i) + symbols.get(i + 1));
                if (id == null) continue;
                if (best < 0 || scores[id] > bestScore) {
                    best = i;
                    bestScore = scores[id];
                }
            }
            if (best < 0) break;
            symbols.set(best, symbols.get(best) + symbols.get(best + 1));
            symbols.remove(best + 1);
        }
        int[] out = new int[symbols.size()];
        for (int i = 0; i < out.length; i++) out[i] = ids.get(symbols.get(i));
        return out;
    }
}
