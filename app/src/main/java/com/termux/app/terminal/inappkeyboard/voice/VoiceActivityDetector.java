package com.termux.app.terminal.inappkeyboard.voice;

import androidx.annotation.NonNull;

import java.util.ArrayList;

/**
 * Streaming energy VAD over 30 ms frames of 16 kHz mono PCM16: the live counterpart of the
 * runtime's offline {@code WhisperSegmenter}, with the same constants. It turns the microphone
 * stream into the segments the Whisper graph decodes:
 * <ul>
 *   <li>speech starts when a frame's RMS is {@link #VOICE_OVER_FLOOR} (9 dB) over an adaptive
 *       noise floor; the segment keeps {@link #PAD_MS} of audio before that onset (a pre-roll ring)
 *       and {@link #PAD_MS} after the last voiced frame — tightly cut single words hallucinate;</li>
 *   <li>a pause of {@code pauseMs} without a voiced frame closes the segment;</li>
 *   <li>a segment with less than {@link #MIN_VOICED_MS} of voiced frames is dropped, never sent —
 *       a 0.1 s remainder produced invented text on both models;</li>
 *   <li>a segment that reaches the graph's window is cut at the quietest frame of its last second
 *       and the rest carries on as the next segment;</li>
 *   <li>{@code sessionSilenceMs} without speech tells the listener the session should end — the
 *       keyboard's "Silence auto-stop" setting, or never, for "Until tap".</li>
 * </ul>
 * The noise floor drops to any quieter non-voiced frame at once and rises slowly towards louder
 * ones, capped at {@link #NOISE_FLOOR_CAP} so continuous speech is never taken for noise. Everything
 * runs on the thread that feeds it; the listener is called on that same thread.
 */
public final class VoiceActivityDetector {

    public static final int SAMPLE_RATE = 16_000;
    public static final int FRAME_MS = 30;
    public static final int FRAME_SAMPLES = SAMPLE_RATE * FRAME_MS / 1000;
    public static final int PAD_MS = 300;
    public static final int MIN_VOICED_MS = 300;
    /** 9 dB over the floor, as a linear RMS ratio. */
    static final float VOICE_OVER_FLOOR = 2.8f;
    /** About −54 dBFS: nothing below this is ever voiced. */
    static final float ABSOLUTE_FLOOR = 0.002f;
    /** About −34 dBFS: a noise floor above this is speech being mistaken for noise. */
    static final float NOISE_FLOOR_CAP = 0.02f;
    /** How fast the floor climbs towards a louder non-voiced frame, per frame. */
    static final float FLOOR_RISE = 0.05f;
    /** The window cut is searched for within the last second of the segment. */
    private static final int CUT_SEARCH_FRAMES = 1000 / FRAME_MS;

    public interface Listener {
        /**
         * Every frame: its RMS in [0, 1], whether it counted as speech, and the adaptive noise
         * floor at that moment (for a level meter measured from the floor, not an absolute scale).
         */
        void onLevel(float rms, boolean voiced, float noiseFloor);

        /**
         * A closed segment, padding included, at least {@link #MIN_VOICED_MS} of it voiced;
         * {@code voicedFrames} is how many of its frames counted as speech, for the per-phrase log.
         */
        void onSegment(@NonNull short[] pcm, int voicedFrames);

        /** {@code sessionSilenceMs} have passed since the last voiced frame (or since the start). */
        void onSilenceTimeout();
    }

    private final Listener listener;
    private final int pauseFrames;
    private final int padFrames = PAD_MS / FRAME_MS;
    private final int minVoicedFrames = MIN_VOICED_MS / FRAME_MS;
    /** Rounded up so the timeout is never early; {@link Integer#MAX_VALUE} disables it ("Until tap"). */
    private final int silenceTimeoutFrames;
    private final int maxSegmentFrames;

    /** Frames not yet emitted: the pre-roll ring while idle, the whole segment while in speech. */
    private final ArrayList<short[]> frames = new ArrayList<>();
    private final ArrayList<Boolean> voicedFlags = new ArrayList<>();
    private final ArrayList<Float> frameLevels = new ArrayList<>();
    private final short[] partial = new short[FRAME_SAMPLES];
    private int partialFill;

    private boolean inSpeech;
    private int voicedFrames;
    private int lastVoicedIndex = -1;
    /** Frames since the last voiced one, or since the start; counts across segment boundaries. */
    private int silentFrames;
    private boolean silenceTimeoutFired;
    private float noiseFloor = NOISE_FLOOR_CAP;

    /**
     * @param pauseMs         silence that closes a segment (400–1200 ms from settings)
     * @param windowSeconds   the graph's window; segments are cut {@code 2 × PAD_MS} short of it so
     *                        the runtime's own padding never pushes speech past the window
     * @param sessionSilenceMs how long without speech ends the session ("Silence auto-stop" from
     *                        settings); {@link VoiceSilenceTimeout#UNTIL_TAP} (0) or lower disables
     *                        the timeout — the session then only ends on a tap or another cause
     */
    public VoiceActivityDetector(@NonNull Listener listener, int pauseMs, int windowSeconds,
                                 int sessionSilenceMs) {
        this.listener = listener;
        this.pauseFrames = Math.max(1, pauseMs / FRAME_MS);
        int windowFrames = Math.max(2, windowSeconds) * 1000 / FRAME_MS;
        this.maxSegmentFrames = Math.max(CUT_SEARCH_FRAMES + 1, windowFrames - 2 * padFrames);
        this.silenceTimeoutFrames = sessionSilenceMs <= VoiceSilenceTimeout.UNTIL_TAP
            ? Integer.MAX_VALUE : (sessionSilenceMs + FRAME_MS - 1) / FRAME_MS;
    }

    /** Feeds {@code count} samples of {@code pcm}; whole 30 ms frames are processed as they complete. */
    public void feed(@NonNull short[] pcm, int count) {
        feed(pcm, 0, count);
    }

    /** As {@link #feed(short[], int)}, from {@code offset} — the capture loop skips a discarded lead-in this way. */
    public void feed(@NonNull short[] pcm, int offset, int count) {
        int end = offset + count;
        while (offset < end) {
            int take = Math.min(FRAME_SAMPLES - partialFill, end - offset);
            System.arraycopy(pcm, offset, partial, partialFill, take);
            partialFill += take;
            offset += take;
            if (partialFill == FRAME_SAMPLES) {
                processFrame(partial.clone());
                partialFill = 0;
            }
        }
    }

    /** Closes whatever is in progress; a voiced-enough segment is still delivered. */
    public void finish() {
        if (inSpeech) closeSegment();
    }

    /** The current noise floor, for tests and the level meter. */
    float noiseFloor() {
        return noiseFloor;
    }

    private void processFrame(@NonNull short[] frame) {
        float rms = rms(frame);
        boolean voiced = rms > Math.max(noiseFloor * VOICE_OVER_FLOOR, ABSOLUTE_FLOOR);
        if (!voiced) {
            if (rms < noiseFloor) noiseFloor = rms;
            else noiseFloor = Math.min(NOISE_FLOOR_CAP, noiseFloor + (rms - noiseFloor) * FLOOR_RISE);
        }
        listener.onLevel(rms, voiced, noiseFloor);
        frames.add(frame);
        voicedFlags.add(voiced);
        frameLevels.add(rms);
        if (!inSpeech) {
            if (voiced) {
                inSpeech = true;
                voicedFrames = 1;
                lastVoicedIndex = frames.size() - 1;
                silentFrames = 0;
                silenceTimeoutFired = false;
                return;
            }
            trimToPreRoll();
            silentFrames++;
            if (silentFrames >= silenceTimeoutFrames && !silenceTimeoutFired) {
                silenceTimeoutFired = true;
                listener.onSilenceTimeout();
            }
            return;
        }
        if (voiced) {
            voicedFrames++;
            lastVoicedIndex = frames.size() - 1;
            silentFrames = 0;
        } else {
            silentFrames++;
        }
        if (silentFrames >= pauseFrames) {
            closeSegment();
        } else if (frames.size() >= maxSegmentFrames) {
            cutAtQuietestRecentFrame();
        }
    }

    /** Emits the segment up to {@link #PAD_MS} after its last voiced frame and goes back to idle. */
    private void closeSegment() {
        int end = Math.min(frames.size(), lastVoicedIndex + 1 + padFrames);
        if (voicedFrames >= minVoicedFrames) listener.onSegment(concat(0, end), voicedFrames);
        dropFrames(end);
        inSpeech = false;
        voicedFrames = 0;
        lastVoicedIndex = -1;
        trimToPreRoll();
    }

    /**
     * The segment has reached the window: it is cut at the quietest frame of its last second and
     * the remainder continues as the next segment, still in speech.
     */
    private void cutAtQuietestRecentFrame() {
        int size = frames.size();
        int from = Math.max(1, size - CUT_SEARCH_FRAMES);
        int cut = from;
        float quietest = Float.MAX_VALUE;
        for (int i = from; i < size; i++) {
            if (frameLevels.get(i) < quietest) {
                quietest = frameLevels.get(i);
                cut = i;
            }
        }
        int voicedBefore = 0;
        for (int i = 0; i < cut; i++) {
            if (voicedFlags.get(i)) voicedBefore++;
        }
        if (voicedBefore >= minVoicedFrames) listener.onSegment(concat(0, cut), voicedBefore);
        dropFrames(cut);
        voicedFrames = 0;
        lastVoicedIndex = -1;
        for (int i = 0; i < frames.size(); i++) {
            if (voicedFlags.get(i)) {
                voicedFrames++;
                lastVoicedIndex = i;
            }
        }
        if (lastVoicedIndex < 0) {
            // The tail was all quiet: it is the pre-roll of whatever comes next.
            inSpeech = false;
            trimToPreRoll();
        }
    }

    private void trimToPreRoll() {
        int excess = frames.size() - padFrames;
        if (excess > 0) dropFrames(excess);
    }

    private void dropFrames(int count) {
        frames.subList(0, count).clear();
        voicedFlags.subList(0, count).clear();
        frameLevels.subList(0, count).clear();
    }

    @NonNull
    private short[] concat(int from, int to) {
        short[] out = new short[(to - from) * FRAME_SAMPLES];
        for (int i = from; i < to; i++) {
            System.arraycopy(frames.get(i), 0, out, (i - from) * FRAME_SAMPLES, FRAME_SAMPLES);
        }
        return out;
    }

    /** RMS of one frame in [0, 1]. */
    static float rms(@NonNull short[] frame) {
        double energy = 0.0;
        for (short sample : frame) {
            double value = sample / 32768.0;
            energy += value * value;
        }
        return (float) Math.sqrt(energy / frame.length);
    }
}
