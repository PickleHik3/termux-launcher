package com.termux.ai;

import androidx.annotation.NonNull;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The greedy TDT loop on a scripted graph: a blank moves one frame, a duration moves that many,
 * a non-blank fills the next slot of the stateless decoder, a full slot array switches to the
 * stateful decoder carrying the state, the state is adopted only on a non-blank, and the loop
 * stops at the last frame or at {@link ParakeetTdtDecoder#MAX_TOKENS}.
 */
public class ParakeetTdtDecoderTest {
    private static final int BLANK = ParakeetTdtDecoder.BLANK;
    private static final int VOCAB = BLANK + 1;
    private static final int WIDTH = VOCAB + ParakeetTdtDecoder.NUM_DURATIONS;

    /** One scripted emission: what the next decoder call answers. */
    private static final class Emission {
        final int token;
        final int duration;

        Emission(int token, int duration) {
            this.token = token;
            this.duration = duration;
        }
    }

    /** Answers the script in order and records every call. */
    private static final class FakeGraph implements ParakeetTdtDecoder.Graph {
        final int slots;
        final List<Emission> script;
        final List<String> calls = new ArrayList<>();
        int adopted = 0;
        int next = 0;

        FakeGraph(int slots, Emission... script) {
            this.slots = slots;
            this.script = Arrays.asList(script);
        }

        @Override
        public int slots() {
            return slots;
        }

        @NonNull
        @Override
        public float[] decode(@NonNull int[] tokens, int t, int slot) {
            calls.add("decode" + Arrays.toString(tokens) + " t=" + t + " slot=" + slot);
            return logits();
        }

        @NonNull
        @Override
        public float[] decodeStateful(int token, int t) {
            calls.add("decode_1[" + token + "] t=" + t);
            return logits();
        }

        @Override
        public void adoptState() {
            adopted++;
            calls.add("adopt");
        }

        private float[] logits() {
            Emission emission = next < script.size() ? script.get(next) : new Emission(BLANK, 0);
            next++;
            float[] logits = new float[WIDTH];
            Arrays.fill(logits, -10f);
            logits[emission.token] = 5f;
            logits[VOCAB + emission.duration] = 5f;
            return logits;
        }
    }

    private static Emission blank(int duration) {
        return new Emission(BLANK, duration);
    }

    private static Emission token(int id, int duration) {
        return new Emission(id, duration);
    }

    @Test
    public void aBlankWithZeroDurationMovesOneFrameAndTheLoopEndsAtTheLastFrame() throws Exception {
        FakeGraph graph = new FakeGraph(4);
        ParakeetTdtDecoder.Result result = ParakeetTdtDecoder.greedy(graph, 5);
        assertEquals(0, result.tokens.length);
        assertEquals(5, result.steps);
        assertEquals(0, graph.adopted);
        assertEquals("decode[8192, 0, 0, 0] t=0 slot=0", graph.calls.get(0));
        assertEquals("decode[8192, 0, 0, 0] t=4 slot=0", graph.calls.get(4));
    }

    @Test
    public void theDurationHeadMovesTheTimeIndex() throws Exception {
        FakeGraph graph = new FakeGraph(4, blank(3), token(7, 2), blank(4));
        ParakeetTdtDecoder.Result result = ParakeetTdtDecoder.greedy(graph, 9);
        assertArrayEquals(new int[] {7}, result.tokens);
        assertArrayEquals(new int[] {3}, result.frames);
        assertEquals(3, result.steps);
        assertEquals(Arrays.asList(
            "decode[8192, 0, 0, 0] t=0 slot=0",
            "decode[8192, 0, 0, 0] t=3 slot=0",
            "decode[8192, 7, 0, 0] t=5 slot=1"), graph.calls);
    }

    @Test
    public void aNonBlankWithZeroDurationStaysOnTheFrameAndFillsTheNextSlot() throws Exception {
        FakeGraph graph = new FakeGraph(4, token(10, 0), token(11, 0), blank(1));
        ParakeetTdtDecoder.Result result = ParakeetTdtDecoder.greedy(graph, 1);
        assertArrayEquals(new int[] {10, 11}, result.tokens);
        assertArrayEquals(new int[] {0, 0}, result.frames);
        assertEquals(Arrays.asList(
            "decode[8192, 0, 0, 0] t=0 slot=0",
            "decode[8192, 10, 0, 0] t=0 slot=1",
            "decode[8192, 10, 11, 0] t=0 slot=2"), graph.calls);
        assertEquals(0, graph.adopted);
    }

    @Test
    public void aFullSlotArraySwitchesToTheStatefulDecoderCarryingItsState() throws Exception {
        FakeGraph graph = new FakeGraph(2, token(10, 1), token(11, 1), blank(1), token(12, 0), blank(1));
        ParakeetTdtDecoder.Result result = ParakeetTdtDecoder.greedy(graph, 4);
        assertArrayEquals(new int[] {10, 11, 12}, result.tokens);
        assertArrayEquals(new int[] {0, 1, 3}, result.frames);
        assertEquals(Arrays.asList(
            "decode[8192, 0] t=0 slot=0",
            "decode[8192, 10] t=1 slot=1",
            // The second token fills the last slot: the state that call built is adopted and
            // decode_1 takes over with that token alone.
            "adopt",
            "decode_1[11] t=2",
            // A stateful blank keeps the state and the token.
            "decode_1[11] t=3",
            "adopt",
            "decode_1[12] t=3"), graph.calls);
        assertEquals(2, graph.adopted);
        assertEquals(5, result.steps);
    }

    @Test
    public void theLoopStopsAtMaxTokens() throws Exception {
        Emission[] script = new Emission[200];
        for (int i = 0; i < script.length; i++) script[i] = token(100 + (i % 50), 0);
        FakeGraph graph = new FakeGraph(4, script);
        ParakeetTdtDecoder.Result result = ParakeetTdtDecoder.greedy(graph, 63);
        assertEquals(ParakeetTdtDecoder.MAX_TOKENS, result.tokens.length);
        assertEquals(ParakeetTdtDecoder.MAX_TOKENS, result.steps);
        assertTrue(graph.calls.get(graph.calls.size() - 1).startsWith("decode_1["));
    }

    @Test
    public void theFirstOfEqualMaximaWinsLikeArgmax() throws Exception {
        ParakeetTdtDecoder.Graph graph = new ParakeetTdtDecoder.Graph() {
            @Override public int slots() { return 4; }
            @NonNull @Override public float[] decode(@NonNull int[] tokens, int t, int slot) {
                float[] logits = new float[WIDTH];
                logits[3] = 1f;
                logits[9] = 1f;
                logits[VOCAB + 2] = 1f;
                logits[VOCAB + 4] = 1f;
                return logits;
            }
            @NonNull @Override public float[] decodeStateful(int token, int t) { return decode(new int[0], t, 0); }
            @Override public void adoptState() { }
        };
        ParakeetTdtDecoder.Result result = ParakeetTdtDecoder.greedy(graph, 2);
        assertArrayEquals(new int[] {3}, result.tokens);
        assertEquals(1, result.steps);
    }
}
