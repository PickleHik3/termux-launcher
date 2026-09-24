package com.termux.ai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Arrays;

/**
 * The prompt and the greedy loop of the Whisper ACFT decoder, kept free of the interpreter so
 * {@code WhisperDecoderTest} can drive them with a scripted step function.
 *
 * <p>Prompt: an optional {@code <|startofprev|>} + vocabulary-line prefix (the terminal bias the
 * plan measured at 26/26 on shell commands), then {@code <|startoftranscript|>}, the language and
 * {@code <|transcribe|>} tokens when the vocabulary has them, then {@code <|notimestamps|>}. The
 * decoder's token input is a fixed 128-slot sequence, prompt included, so whatever the prompt
 * takes comes out of the output budget; the bias line is capped so the budget stays near 100.
 *
 * <p>Loop: each step re-runs the whole decoder (there is no KV cache) and reads the logits at the
 * last filled slot; timestamps, the {@code <|startof…|>}/task/no-speech tokens, and EOT on the first
 * step are suppressed; the segment ends at EOT, at the sequence limit, or when the same 4-gram
 * repeats three times, the reference decoder's guard against runaway output.
 */
final class WhisperDecoder {
    /** The shell words the terminal bias prompt carries by default; measured to turn "Get status" into "git status". */
    static final String TERMINAL_VOCABULARY = "git ls cd sudo apt pkg tab enter escape ctrl";
    /** The most tokens a bias line may take, {@code <|startofprev|>} excluded. */
    static final int MAX_BIAS_TOKENS = 24;
    static final int REPETITION_NGRAM = 4;
    static final int REPETITION_REPEATS = 3;

    private WhisperDecoder() {
    }

    /** One decoder run: the logits for the token after {@code tokens[filled - 1]}, {@code tokens} padded with EOT to the sequence length. */
    interface Step {
        @NonNull
        float[] logits(@NonNull int[] tokens, int filled) throws Exception;
    }

    /** What the greedy loop ended with: the output tokens and why it stopped. */
    static final class Result {
        @NonNull final int[] tokens;
        final int steps;
        final boolean endOfText;
        final boolean repetition;

        Result(@NonNull int[] tokens, int steps, boolean endOfText, boolean repetition) {
            this.tokens = tokens;
            this.steps = steps;
            this.endOfText = endOfText;
            this.repetition = repetition;
        }
    }

    /**
     * The task prompt: {@code <|startoftranscript|>}, {@code <|xx|>} and {@code <|transcribe|>} when
     * the vocabulary has language tokens (the reference decoder's rule, which the golden fixture
     * was produced with; every openai/whisper tokenizer.json has them, the .en ones included),
     * then {@code <|notimestamps|>}.
     *
     * @throws IllegalArgumentException for a language the vocabulary has no token for
     */
    @NonNull
    static int[] taskPrompt(@NonNull WhisperTokenizer tokenizer, @NonNull String language) {
        int sot = tokenizer.specialId(WhisperTokenizer.SOT);
        int noTimestamps = tokenizer.specialId(WhisperTokenizer.NO_TIMESTAMPS);
        if (sot < 0 || noTimestamps < 0) throw new IllegalStateException("tokenizer.json lacks Whisper's control tokens");
        if (!tokenizer.hasLanguageTokens()) return new int[] {sot, noTimestamps};
        int lang = tokenizer.languageToken(language);
        if (lang < 0) throw new IllegalArgumentException("Unknown language: " + language);
        return new int[] {sot, lang, tokenizer.specialId(WhisperTokenizer.TRANSCRIBE), noTimestamps};
    }

    /**
     * The full prompt: {@code <|startofprev|>} + the encoded bias line (Whisper prefixes prompt
     * text with a space; the line is cut to {@link #MAX_BIAS_TOKENS} tokens) ahead of the task
     * prompt when {@code biasText} is given, the task prompt alone otherwise.
     */
    @NonNull
    static int[] prompt(@NonNull WhisperTokenizer tokenizer, @NonNull String language, @Nullable String biasText) {
        int[] task = taskPrompt(tokenizer, language);
        String bias = biasText == null ? "" : biasText.trim().replaceAll("\\s+", " ");
        int startOfPrev = tokenizer.specialId(WhisperTokenizer.START_OF_PREV);
        if (bias.isEmpty() || startOfPrev < 0) return task;
        int[] encoded = tokenizer.encode(" " + bias);
        int biasTokens = Math.min(encoded.length, MAX_BIAS_TOKENS);
        int[] out = new int[1 + biasTokens + task.length];
        out[0] = startOfPrev;
        System.arraycopy(encoded, 0, out, 1, biasTokens);
        System.arraycopy(task, 0, out, 1 + biasTokens, task.length);
        return out;
    }

    /** The ids the greedy loop never picks, from the vocabulary; {@code -1} entries are ignored. */
    static final class Suppression {
        final int endOfText;
        /** Every id at or above this is a timestamp token. */
        final int timestampBegin;
        @NonNull final int[] always;

        Suppression(int endOfText, int timestampBegin, @NonNull int[] always) {
            this.endOfText = endOfText;
            this.timestampBegin = timestampBegin;
            this.always = always;
        }

        @NonNull
        static Suppression forTokenizer(@NonNull WhisperTokenizer tokenizer) {
            int eot = tokenizer.specialId(WhisperTokenizer.EOT);
            int noTimestamps = tokenizer.specialId(WhisperTokenizer.NO_TIMESTAMPS);
            if (eot < 0 || noTimestamps < 0) throw new IllegalStateException("tokenizer.json lacks Whisper's control tokens");
            return new Suppression(eot, noTimestamps + 1, new int[] {
                tokenizer.specialId(WhisperTokenizer.SOT),
                tokenizer.specialId(WhisperTokenizer.TRANSLATE),
                tokenizer.specialId(WhisperTokenizer.TRANSCRIBE),
                tokenizer.specialId(WhisperTokenizer.START_OF_LM),
                tokenizer.specialId(WhisperTokenizer.START_OF_PREV),
                tokenizer.specialId(WhisperTokenizer.NO_CAPTIONS),
                tokenizer.specialId(WhisperTokenizer.NO_SPEECH),
            });
        }
    }

    /**
     * Runs the loop from {@code prompt} until EOT, the sequence limit or the repetition guard, and
     * returns the tokens after the prompt. Never returns EOT itself.
     */
    @NonNull
    static Result greedy(@NonNull Step step, @NonNull int[] prompt, int sequenceLength, @NonNull Suppression suppress) throws Exception {
        if (prompt.length >= sequenceLength) throw new IllegalArgumentException("Prompt fills the decoder's sequence: " + prompt.length + " of " + sequenceLength);
        int[] tokens = new int[sequenceLength];
        Arrays.fill(tokens, suppress.endOfText);
        System.arraycopy(prompt, 0, tokens, 0, prompt.length);
        int filled = prompt.length;
        int steps = 0;
        boolean endOfText = false;
        boolean repetition = false;
        while (filled < sequenceLength) {
            float[] logits = step.logits(tokens, filled);
            steps++;
            int next = argmax(logits, suppress, filled == prompt.length);
            if (next == suppress.endOfText) {
                endOfText = true;
                break;
            }
            tokens[filled++] = next;
            if (repeats(tokens, prompt.length, filled)) {
                repetition = true;
                break;
            }
        }
        return new Result(Arrays.copyOfRange(tokens, prompt.length, filled), steps, endOfText, repetition);
    }

    /** The best allowed id: timestamps, the always-suppressed set, and EOT on the first step are skipped. */
    static int argmax(@NonNull float[] logits, @NonNull Suppression suppress, boolean firstStep) {
        int limit = suppress.timestampBegin > 0 ? Math.min(logits.length, suppress.timestampBegin) : logits.length;
        int best = -1;
        float bestValue = Float.NEGATIVE_INFINITY;
        for (int id = 0; id < limit; id++) {
            if (firstStep && id == suppress.endOfText) continue;
            if (contains(suppress.always, id)) continue;
            float value = logits[id];
            if (best < 0 || value > bestValue) {
                best = id;
                bestValue = value;
            }
        }
        return best;
    }

    /** Whether the last three 4-grams of the output are identical (needs 12 output tokens). */
    static boolean repeats(@NonNull int[] tokens, int outputStart, int filled) {
        int span = REPETITION_NGRAM * REPETITION_REPEATS;
        if (filled - outputStart < span) return false;
        int last = filled - REPETITION_NGRAM;
        for (int r = 1; r < REPETITION_REPEATS; r++) {
            int earlier = filled - REPETITION_NGRAM * (r + 1);
            for (int i = 0; i < REPETITION_NGRAM; i++) {
                if (tokens[last + i] != tokens[earlier + i]) return false;
            }
        }
        return true;
    }

    private static boolean contains(@NonNull int[] ids, int id) {
        for (int candidate : ids) {
            if (candidate == id) return true;
        }
        return false;
    }
}
