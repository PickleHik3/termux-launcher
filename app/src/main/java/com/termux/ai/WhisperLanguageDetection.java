package com.termux.ai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Arrays;
import java.util.Locale;

/**
 * Which language a multilingual Whisper graph decodes a request in, and how that was decided.
 *
 * <p>With no language in the request the graph is asked, the way the reference decoder's
 * {@code detect_language} does it: one decoder run over {@code <|startoftranscript|>} alone, a
 * softmax over the logits of the {@code <|xx|>} tokens only, and the top language is the guess,
 * its probability the confidence. The run reads slot 0 of the 128-slot sequence; the causal mask
 * keeps the EOT padding behind it out of view, so it is the reference's one-token prompt exactly.
 * It costs one decoder step on top of the request (a step re-runs the whole decoder, there is
 * no KV cache: ~80 ms on small, a few tens on base, on pong) and runs on the first piece only;
 * the whole request is decoded in the language it picked.
 *
 * <p>A request may also carry a hint ({@link #REQUEST_HINT}: voice input sends the keyboard
 * layout's or the locale's language). A guess below {@link #MIN_PROBABILITY} then gives way to
 * the hint; without a hint the top guess is taken whatever its probability, as the reference does.
 *
 * <p>The answer says which it was: {@code language} is what the text was decoded in,
 * {@link #RESPONSE_SOURCE} is {@link #SOURCE_FORCED} (the request or an {@code .en} graph named
 * it), {@link #SOURCE_DETECTED} (heard) or {@link #SOURCE_FALLBACK} (not heard surely enough, or
 * no speech to hear: the hint, or English without one); whenever the graph was asked,
 * {@link #RESPONSE_DETECTED} and {@link #RESPONSE_PROBABILITY} carry its top guess.
 */
public final class WhisperLanguageDetection {
    /** Request field: the language to fall back to when detection is not sure. */
    public static final String REQUEST_HINT = "language_hint";
    /** A request's {@code language} of {@code auto} means the same as none: listen for it. */
    public static final String AUTO = "auto";
    public static final String RESPONSE_SOURCE = "languageSource";
    public static final String RESPONSE_DETECTED = "detectedLanguage";
    public static final String RESPONSE_PROBABILITY = "languageProbability";
    public static final String SOURCE_FORCED = "forced";
    public static final String SOURCE_DETECTED = "detected";
    public static final String SOURCE_FALLBACK = "fallback";
    /** What a request with nothing to go on is decoded in. */
    static final String DEFAULT_LANGUAGE = "en";

    /**
     * The probability the top language needs to win over the hint. A wrong pick costs the caller
     * a whole dictation in the wrong language (voice input locks the first sure answer for the
     * session), while a pick that falls short costs one more decoder step on the next phrase and
     * decodes this one in the hint, which is right for most people. So the bar is well above
     * faster-whisper's 0.5 for a guess with nothing to fall back on. A short command is where
     * the guess strays, and it is the phrase where waiting one more costs least.
     */
    static final float MIN_PROBABILITY = 0.8f;

    private WhisperLanguageDetection() {
    }

    /** The graph's top language and how sure it is, from 0 to 1. */
    static final class Guess {
        @NonNull final String code;
        final float probability;

        Guess(@NonNull String code, float probability) {
            this.code = code;
            this.probability = probability;
        }
    }

    /** The language a request is decoded in, why, and the guess when the graph was asked. */
    static final class Choice {
        @NonNull final String language;
        @NonNull final String source;
        @Nullable final Guess guess;

        Choice(@NonNull String language, @NonNull String source, @Nullable Guess guess) {
            this.language = language;
            this.source = source;
            this.guess = guess;
        }
    }

    /** A request's language as a lower-case code; {@code null} for none, blank or {@link #AUTO}. */
    @Nullable
    static String normalize(@Nullable String code) {
        if (code == null) return null;
        String trimmed = code.trim().toLowerCase(Locale.ROOT);
        return trimmed.isEmpty() || AUTO.equals(trimmed) ? null : trimmed;
    }

    /** The language the request named, or that an {@code .en} graph always decodes. */
    @NonNull
    static Choice forced(@NonNull String language) {
        return new Choice(language, SOURCE_FORCED, null);
    }

    /**
     * The guess when it is sure enough or there is no hint to prefer; the hint (English without
     * one) when it is not, or when the graph was never asked (no speech in the audio).
     */
    @NonNull
    static Choice choose(@Nullable Guess guess, @Nullable String hint) {
        if (guess != null && (hint == null || guess.probability >= MIN_PROBABILITY)) {
            return new Choice(guess.code, SOURCE_DETECTED, guess);
        }
        return new Choice(hint != null ? hint : DEFAULT_LANGUAGE, SOURCE_FALLBACK, guess);
    }

    /**
     * One decoder run over {@code <|startoftranscript|>} alone, padded with EOT to the sequence
     * length, and the guess from the logits at slot 0; {@code null} when the vocabulary has no
     * language tokens.
     */
    @Nullable
    static Guess detect(@NonNull WhisperDecoder.Step step, @NonNull WhisperTokenizer tokenizer,
                        int sequenceLength) throws Exception {
        WhisperTokenizer.Languages languages = tokenizer.languages();
        int sot = tokenizer.specialId(WhisperTokenizer.SOT);
        int eot = tokenizer.specialId(WhisperTokenizer.EOT);
        if (languages.ids.length == 0 || sot < 0 || eot < 0 || sequenceLength < 1) return null;
        int[] tokens = new int[sequenceLength];
        Arrays.fill(tokens, eot);
        tokens[0] = sot;
        return guess(step.logits(tokens, 1), languages.codes, languages.ids);
    }

    /**
     * The softmax over the language tokens' logits only (every other token masked out, as the
     * reference does) and its top entry; {@code null} when none of {@code ids} is in range or
     * the top logit is not finite.
     */
    @Nullable
    static Guess guess(@NonNull float[] logits, @NonNull String[] codes, @NonNull int[] ids) {
        int best = -1;
        float max = Float.NEGATIVE_INFINITY;
        for (int i = 0; i < ids.length; i++) {
            int id = ids[i];
            if (id < 0 || id >= logits.length || Float.isNaN(logits[id])) continue;
            if (best < 0 || logits[id] > max) {
                best = i;
                max = logits[id];
            }
        }
        if (best < 0 || Float.isInfinite(max)) return null;
        // Shifted by the maximum so exp never overflows; the top term is exp(0) = 1.
        double sum = 0.0;
        for (int id : ids) {
            if (id < 0 || id >= logits.length || Float.isNaN(logits[id])) continue;
            sum += Math.exp(logits[id] - (double) max);
        }
        return new Guess(codes[best], (float) (1.0 / sum));
    }
}
