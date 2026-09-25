package com.termux.ai;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * The greedy token-and-duration transducer loop of the Parakeet TDT LiteRT graphs, the port of
 * {@code Parakeet.window()} in {@code scripts/parakeet_replay_server.py} (itself the sample's
 * {@code TdtDecoder.kt}), kept free of the interpreter so {@code ParakeetTdtDecoderTest} can drive
 * it with a scripted {@link Graph}.
 *
 * <p>The graph has two decoders over the same encoder output: the stateless {@code decode}, whose
 * token input has {@link Graph#slots()} slots and whose LSTM state starts at zero on every call,
 * and the stateful {@code decode_1}, which takes one token and the state the previous call
 * returned. The loop starts stateless with {@code [BLANK, 0, 0, 0]}, reading the logits at the
 * slot of the last token placed; when the slots are full it switches to {@code decode_1}, carrying
 * the state that last stateless call built. The state is adopted only on a non-blank emission.
 * Each step's logits are the vocabulary (blank last) followed by {@link #NUM_DURATIONS} duration
 * logits; the duration moves the time index, at least one frame on a blank. The loop ends at the
 * encoder's last frame or after {@link #MAX_TOKENS} tokens.
 */
final class ParakeetTdtDecoder {
    /** The transducer's blank: one past the 8192-piece vocabulary. */
    static final int BLANK = 8192;
    static final int NUM_DURATIONS = 5;
    static final int MAX_TOKENS = 80;

    private ParakeetTdtDecoder() {
    }

    /** The two decoder signatures, as the loop needs them. */
    interface Graph {
        /** The slot count of the stateless decoder's token input. */
        int slots();

        /**
         * The stateless {@code decode} over {@code tokens} (length {@link #slots()}) and a zero
         * state: the logits at time {@code t}, slot {@code slot}, vocabulary then durations.
         */
        @NonNull
        float[] decode(@NonNull int[] tokens, int t, int slot) throws Exception;

        /** The stateful {@code decode_1} on {@code token} and the adopted state: the logits at time {@code t}. */
        @NonNull
        float[] decodeStateful(int token, int t) throws Exception;

        /** Makes the state the last call returned the input state of the next stateful call. */
        void adoptState();
    }

    /** What the loop ended with: the emitted tokens, the frame each was emitted at, and the step count. */
    static final class Result {
        @NonNull final int[] tokens;
        @NonNull final int[] frames;
        final int steps;

        Result(@NonNull int[] tokens, @NonNull int[] frames, int steps) {
            this.tokens = tokens;
            this.frames = frames;
            this.steps = steps;
        }
    }

    /** Runs the loop over {@code maxT} encoder frames. */
    @NonNull
    static Result greedy(@NonNull Graph graph, int maxT) throws Exception {
        int slots = graph.slots();
        if (slots < 1) throw new IllegalArgumentException("The stateless decoder needs at least one token slot");
        int[] tokens = new int[slots];
        tokens[0] = BLANK;
        List<Integer> emitted = new ArrayList<>();
        List<Integer> frames = new ArrayList<>();
        int slot = 0;
        boolean stateful = false;
        int lastToken = BLANK;
        int t = 0;
        int steps = 0;
        while (t < maxT && emitted.size() < MAX_TOKENS) {
            float[] logits = stateful ? graph.decodeStateful(lastToken, t) : graph.decode(tokens, t, slot);
            steps++;
            int vocabulary = logits.length - NUM_DURATIONS;
            if (vocabulary <= BLANK) throw new IllegalStateException("Logits too short for the vocabulary and durations: " + logits.length);
            int token = argmax(logits, 0, vocabulary);
            if (token != BLANK) {
                emitted.add(token);
                frames.add(t);
                if (stateful) {
                    graph.adoptState();
                    lastToken = token;
                } else {
                    slot++;
                    if (slot < slots) {
                        tokens[slot] = token;
                    } else {
                        // The token array is full: carry the state it built into decode_1.
                        stateful = true;
                        graph.adoptState();
                        lastToken = token;
                    }
                }
            }
            int duration = argmax(logits, vocabulary, logits.length) - vocabulary;
            t += (duration == 0 && token == BLANK) ? 1 : duration;
        }
        return new Result(toArray(emitted), toArray(frames), steps);
    }

    private static int argmax(@NonNull float[] values, int from, int to) {
        int best = from;
        for (int i = from + 1; i < to; i++) {
            if (values[i] > values[best]) best = i;
        }
        return best;
    }

    @NonNull
    private static int[] toArray(@NonNull List<Integer> values) {
        int[] out = new int[values.size()];
        for (int i = 0; i < out.length; i++) out[i] = values.get(i);
        return out;
    }
}
