package com.termux.app.terminal.inappkeyboard.voice;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * When a dictation's cleanup model is warmed. As the microphone opens only when that costs no
 * load: the model is already resident (or remote, or there is none), so early passes start at
 * phrase pauses as they always did. A cold model loads at the session's end, never mid-session: a
 * cold GPU load held the GPU for seconds and the launcher's frames, the waveform included, waited
 * on it (pong, E4B, 2026-10-14), and the first phrase pause comes about 9 s in, while the user is
 * still talking. Early passes are off for that session; the next one finds the model warm. Pure;
 * {@link VoiceSessionCleanup} runs the load it answers.
 */
final class VoiceCleanupWarmUp {

    /** What started the load, for the log. */
    enum Trigger {
        /** The microphone opened with the model resident: the warm-up loads nothing. */
        OPEN("the mic opening (the model is resident: no load)"),
        /** The session ended with the model cold as the microphone opened. */
        END("the session's end (the model was cold as the mic opened)");

        @NonNull final String why;

        Trigger(@NonNull String why) {
            this.why = why;
        }
    }

    private VoiceCleanupWarmUp() {
    }

    /** As the microphone opens: warm now when it loads nothing, else {@code null} (wait for the end). */
    @Nullable
    static Trigger atOpen(boolean warmLoadsNothing) {
        return warmLoadsNothing ? Trigger.OPEN : null;
    }

    /** As the session ends with text to clean: load now unless the session already warmed, else {@code null}. */
    @Nullable
    static Trigger atEnd(boolean warmedAlready) {
        return warmedAlready ? null : Trigger.END;
    }
}
