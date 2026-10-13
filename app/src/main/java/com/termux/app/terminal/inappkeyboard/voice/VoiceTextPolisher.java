package com.termux.app.terminal.inappkeyboard.voice;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Rewrites a dictation session's text — punctuation, casing, obvious mis-hearings, filler words —
 * without changing what was said. {@link VoiceSessionCleanup} calls it on its own
 * {@code voice-cleanup} thread for the sentences {@link VoiceGatedCleanup} flags in each part
 * {@link VoiceCleanupPlan} hands it: at phrase pauses while the user speaks when
 * {@link #cleansWhileListening} allows, and for the rest once the session has ended. An implementation never throws and never blocks past the deadline it is given: on any
 * trouble it hands the raw text back with the reason, and the raw text stays.
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
     * Gets ready ahead of the cleanup (loads a model, opens a connection): called once as the
     * microphone opens, on the cleanup thread, before any {@link #polish}. Best effort; a failure
     * here shows up as a fallback at the end, never as a session failure.
     */
    void warm();

    /**
     * Rewrites {@code text}, returning within about {@code timeoutMs}. Never throws; on an error,
     * a timeout, or output that fails {@link VoicePolishRules#accept}, returns
     * {@link Result#fallback} with the raw text.
     */
    @NonNull
    Result polish(@NonNull String text, long timeoutMs);

    /**
     * {@link #polish(String, long)} for a part of a dictation that follows {@code context}, the
     * text before it as already cleaned, which the answer must not repeat ({@code ""} for none). One
     * that cannot use context cleans the part alone.
     */
    @NonNull
    default Result polish(@NonNull String text, @NonNull String context, long timeoutMs) {
        return polish(text, timeoutMs);
    }

    /**
     * {@link #polish(String, String, long)} for a group of sentences the gate flagged
     * ({@link VoiceGatedCleanup}): {@code context} is the sentence before it, already cleaned
     * ({@code ""} for none), and {@code language} the ISO 639 code of the language to write in
     * ({@code null}: not known). One that cannot use the language cleans the group without it.
     */
    @NonNull
    default Result polish(@NonNull String text, @NonNull String context, @Nullable String language, long timeoutMs) {
        return polish(text, context, timeoutMs);
    }

    /**
     * Whether parts are worth cleaning while the user still speaks, asked once {@link #warm} has
     * returned: true for a model that answers quickly beside speech-to-text (a remote one, or a
     * local one on the GPU), false for one on the CPU, which keeps the single pass at the end.
     */
    default boolean cleansWhileListening() {
        return false;
    }
}
