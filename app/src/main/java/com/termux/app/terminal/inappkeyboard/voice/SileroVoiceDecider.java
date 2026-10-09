package com.termux.app.terminal.inappkeyboard.voice;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The voiced/unvoiced decision of {@link VoiceActivityDetector} taken from Silero VAD v5's speech
 * probability instead of frame energy. Silero reads 512-sample (32 ms) chunks and the detector
 * works in 480-sample (30 ms) frames, so this class re-chunks the frames it is handed: every
 * frame's samples go into a 512-sample chunk, and when a chunk completes the model is run on it
 * with the 64 samples before it prepended as context (zeros at the start of the stream), exactly
 * as Silero's own {@code OnnxWrapper} feeds it. A frame's decision reads the chunk that has
 * <em>finished</em> by the end of that frame, so no frame waits for or sees later audio; the very
 * first frame (480 samples) has no finished chunk yet and is unvoiced. This is the alignment the
 * PC harness evaluated ({@code scripts/voice-eval/vads.py}, {@code SileroDecider}) and the decision
 * record in {@code project-docs/reference/voice-ai/voice-vad-eval-2026-09-27.md} rests on.
 *
 * <p>The hysteresis mirrors the energy detector's 9 dB onset / 6 dB hold pair: a probability over
 * {@link #ONSET} opens a segment and one over {@link #HOLD} keeps an open segment voiced, the same
 * {@code threshold} / {@code threshold − 0.15} pairing Silero's {@code get_speech_timestamps} uses.
 * Unlike the energy detector there is no warm-up: Silero needs no noise floor, so a word spoken the
 * moment the microphone opens counts from its first chunk instead of being clipped by 240 ms.
 *
 * <p>Nothing here touches LiteRT or Android: the model sits behind {@link ProbabilitySource}, so
 * tests drive the decision with a fake. If the source throws, the decider marks itself
 * {@link #failed()} and the detector goes back to the energy decision for the rest of the session.
 * Used from the one thread that feeds the detector.
 */
public final class SileroVoiceDecider {

    public static final int CHUNK_SAMPLES = 512;
    public static final int CONTEXT_SAMPLES = 64;
    public static final int WINDOW_SAMPLES = CONTEXT_SAMPLES + CHUNK_SAMPLES;
    /** Probability over this opens a segment. */
    static final float ONSET = 0.5f;
    /** Probability over this keeps an open segment voiced. */
    static final float HOLD = 0.35f;

    /** One run of the speech model: the probability that a chunk is speech. */
    public interface ProbabilitySource {
        /**
         * @param window {@link #WINDOW_SAMPLES} floats in [-1, 1]: {@link #CONTEXT_SAMPLES} of the
         *               previous chunk's tail, then the new {@link #CHUNK_SAMPLES}-sample chunk.
         *               The array is reused for the next chunk, so it must not be kept.
         * @return the speech probability of the chunk, carrying the model's recurrent state on to
         *         the next call
         */
        float probability(@NonNull float[] window);

        /** Frees the model; no further calls follow. */
        void close();
    }

    /** Opens the model; throws when it is missing or cannot be loaded. */
    public interface Opener {
        @NonNull
        ProbabilitySource open() throws Exception;
    }

    /** Where a load or run failure is reported (the log, on the device). */
    public interface FailureReporter {
        void report(@NonNull String message, @NonNull Throwable cause);
    }

    private final ProbabilitySource source;
    private final FailureReporter reporter;
    /** Context followed by the chunk being filled; the chunk starts at {@link #CONTEXT_SAMPLES}. */
    private final float[] window = new float[WINDOW_SAMPLES];
    private int chunkFill;
    private float lastProbability;
    private int chunksRun;
    private boolean failed;

    SileroVoiceDecider(@NonNull ProbabilitySource source, @NonNull FailureReporter reporter) {
        this.source = source;
        this.reporter = reporter;
    }

    /**
     * The session's Silero decider, or null when the model does not open — the caller then keeps
     * the energy detector. The failure goes to {@code reporter}; wrap it in {@link #once} so a
     * missing model is logged a single time rather than on every press of the voice key.
     */
    @Nullable
    public static SileroVoiceDecider open(@NonNull Opener opener, @NonNull FailureReporter reporter) {
        ProbabilitySource source;
        try {
            source = opener.open();
        } catch (Exception | LinkageError e) {
            // LinkageError too: a device where LiteRT's native library cannot load throws
            // UnsatisfiedLinkError, and that must fall back the same way a missing asset does.
            reporter.report("Silero VAD unavailable; using the energy detector", e);
            return null;
        }
        return new SileroVoiceDecider(source, reporter);
    }

    /** A reporter that passes on only the first report it is given, for the lifetime of the returned object. */
    @NonNull
    public static FailureReporter once(@NonNull FailureReporter delegate) {
        AtomicBoolean reported = new AtomicBoolean();
        return (message, cause) -> {
            if (reported.compareAndSet(false, true)) delegate.report(message, cause);
        };
    }

    /** Whether a probability counts as speech: {@link #ONSET} while idle, {@link #HOLD} inside a segment. */
    static boolean voiced(float probability, boolean inSpeech) {
        return probability > (inSpeech ? HOLD : ONSET);
    }

    /**
     * Takes one 30 ms frame of PCM16, runs the model if the frame completes a chunk, and returns
     * whether the frame is voiced given whether the detector is currently in a segment. Returns
     * false once {@link #failed()}; the detector checks that and decides by energy instead.
     */
    boolean decide(@NonNull short[] frame, boolean inSpeech) {
        return decide(frame, inSpeech, 1f);
    }

    /**
     * As {@link #decide(short[], boolean)}, with the frame's samples scaled by {@code gain} (and
     * held inside [-1, 1]) before the model sees them: {@link VoiceMicSensitivity#sileroGain}, the
     * lift that brings quiet speech up to where Silero's probability means something. Only the
     * model's copy is scaled.
     */
    boolean decide(@NonNull short[] frame, boolean inSpeech, float gain) {
        if (failed) return false;
        float scale = gain / 32768f;
        int offset = 0;
        while (offset < frame.length) {
            int take = Math.min(CHUNK_SAMPLES - chunkFill, frame.length - offset);
            for (int i = 0; i < take; i++) {
                float value = frame[offset + i] * scale;
                window[CONTEXT_SAMPLES + chunkFill + i] = value > 1f ? 1f : (value < -1f ? -1f : value);
            }
            chunkFill += take;
            offset += take;
            if (chunkFill == CHUNK_SAMPLES) {
                if (!runChunk()) return false;
                chunkFill = 0;
            }
        }
        return voiced(lastProbability, inSpeech);
    }

    private boolean runChunk() {
        try {
            lastProbability = source.probability(window);
        } catch (RuntimeException e) {
            failed = true;
            reporter.report("Silero VAD failed after " + chunksRun + " chunks; using the energy detector", e);
            return false;
        }
        chunksRun++;
        // The chunk's last CONTEXT_SAMPLES become the next window's context.
        System.arraycopy(window, WINDOW_SAMPLES - CONTEXT_SAMPLES, window, 0, CONTEXT_SAMPLES);
        return true;
    }

    /** True once the model has thrown; the rest of the session decides by energy. */
    boolean failed() {
        return failed;
    }

    /** The probability of the last finished chunk (0 before the first), for tests and logs. */
    float lastProbability() {
        return lastProbability;
    }

    /** Frees the model. */
    public void close() {
        source.close();
    }
}
