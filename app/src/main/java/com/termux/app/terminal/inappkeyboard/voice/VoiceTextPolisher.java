package com.termux.app.terminal.inappkeyboard.voice;

import androidx.annotation.NonNull;

/**
 * Rewrites one dictated text segment — punctuation, casing, obvious mis-hearings, filler words —
 * without changing what was said. {@link VoiceInputSession} calls it on its own
 * {@code voice-polish} thread, once per segment that {@link VoicePolishRules} lets through, and
 * types whatever comes back in the segment's spoken order. An implementation never throws and
 * never blocks past the deadline it is given: on any trouble it hands the raw text back with the
 * reason, and the session types that instead.
 *
 * <p>Today's only implementation is {@link LocalTaiVoiceTextPolisher} (Gemma through the TAI
 * runtime). The seam exists so a bring-your-own-key provider can be added later without the
 * session or its tests knowing which one is behind it.
 */
public interface VoiceTextPolisher {

    /** What a polish came back with: the text to type and, for the log, what happened. */
    final class Result {
        public static final String OUTCOME_POLISHED = "polished";
        public static final String OUTCOME_FALLBACK_PREFIX = "fallback:";

        @NonNull public final String text;
        /** {@code polished}, or {@code fallback:<reason>} when {@link #text} is the raw input. */
        @NonNull public final String outcome;

        private Result(@NonNull String text, @NonNull String outcome) {
            this.text = text;
            this.outcome = outcome;
        }

        @NonNull
        public static Result polished(@NonNull String text) {
            return new Result(text, OUTCOME_POLISHED);
        }

        /** The raw text, untouched, with a short machine-readable reason (never the text itself). */
        @NonNull
        public static Result fallback(@NonNull String rawText, @NonNull String reason) {
            return new Result(rawText, OUTCOME_FALLBACK_PREFIX + reason);
        }

        public boolean isPolished() {
            return OUTCOME_POLISHED.equals(outcome);
        }
    }

    /**
     * Gets ready ahead of the first segment (loads a model, opens a connection): called once as
     * the microphone opens, on the polish thread, before any {@link #polish}. Best effort; a
     * failure here shows up as a fallback per segment, never as a session failure.
     */
    void warm();

    /**
     * Rewrites {@code text}, returning within about {@code timeoutMs}. Never throws; on an error,
     * a timeout, or output that fails {@link VoicePolishRules#accept}, returns
     * {@link Result#fallback} with the raw text.
     */
    @NonNull
    Result polish(@NonNull String text, long timeoutMs);
}
