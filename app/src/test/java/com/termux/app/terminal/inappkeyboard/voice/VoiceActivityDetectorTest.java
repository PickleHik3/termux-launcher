package com.termux.app.terminal.inappkeyboard.voice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * The streaming VAD on synthetic PCM: a tone is speech, digital near-silence is not. Segments are
 * measured in 30 ms frames ({@link VoiceActivityDetector#FRAME_SAMPLES} samples each).
 */
public class VoiceActivityDetectorTest {

    private static final int FRAME = VoiceActivityDetector.FRAME_SAMPLES;
    /** The smoothed level stays over the threshold this many frames after the last loud one. */
    private static final int LAG = VoiceActivityDetector.SMOOTH_FRAMES - 1;

    private final List<short[]> segments = new ArrayList<>();
    private int silenceTimeouts;

    /** The 2.5 s the pre-setting behaviour used; kept so existing tests time out where they always did. */
    private static final int DEFAULT_TEST_SILENCE_MS = 2_500;

    private VoiceActivityDetector detector(int pauseMs, int windowSeconds) {
        return detector(pauseMs, windowSeconds, DEFAULT_TEST_SILENCE_MS);
    }

    private VoiceActivityDetector detector(int pauseMs, int windowSeconds, int silenceMs) {
        return new VoiceActivityDetector(new VoiceActivityDetector.Listener() {
            @Override
            public void onLevel(float rms, boolean voiced, float noiseFloor) {
            }

            @Override
            public void onSegment(@NonNull short[] pcm, int voicedFrames) {
                segments.add(pcm);
            }

            @Override
            public void onSilenceTimeout() {
                silenceTimeouts++;
            }
        }, pauseMs, windowSeconds, silenceMs);
    }

    /** {@code frames} frames of a 440 Hz tone at about −12 dBFS. */
    private static short[] tone(int frames) {
        short[] out = new short[frames * FRAME];
        for (int i = 0; i < out.length; i++) {
            out[i] = (short) (8000 * Math.sin(2 * Math.PI * 440 * i / VoiceActivityDetector.SAMPLE_RATE));
        }
        return out;
    }

    /** {@code frames} frames of faint noise, about −70 dBFS: a quiet room, well under the floor. */
    private static short[] quiet(int frames) {
        short[] out = new short[frames * FRAME];
        for (int i = 0; i < out.length; i++) out[i] = (short) ((i * 7919) % 17 - 8);
        return out;
    }

    private static int frames(short[] pcm) {
        return pcm.length / FRAME;
    }

    private static boolean isQuietFrame(short[] pcm, int frame) {
        short[] one = new short[FRAME];
        System.arraycopy(pcm, frame * FRAME, one, 0, FRAME);
        return VoiceActivityDetector.rms(one) < VoiceActivityDetector.ABSOLUTE_FLOOR;
    }

    @Test
    public void aPauseClosesTheSegmentWithPreRollAndTail() {
        VoiceActivityDetector vad = detector(600, 10);
        vad.feed(quiet(30), 30 * FRAME);       // 900 ms of room tone settles the floor
        vad.feed(tone(20), 20 * FRAME);        // 600 ms of speech
        vad.feed(quiet(30), 30 * FRAME);       // 900 ms pause: closes at 600 ms

        assertEquals(1, segments.size());
        short[] segment = segments.get(0);
        // 10 frames of pre-roll + 20 voiced (+ the smoothing lag) + 10 frames of tail.
        assertEquals(40 + LAG, frames(segment));
        assertTrue("pre-roll is quiet", isQuietFrame(segment, 0));
        assertTrue("pre-roll is quiet", isQuietFrame(segment, 9));
        assertFalse("speech follows the pre-roll", isQuietFrame(segment, 10));
        assertFalse("speech runs to the tail", isQuietFrame(segment, 29));
        assertTrue("tail is quiet", isQuietFrame(segment, 30));
        assertTrue("tail is quiet", isQuietFrame(segment, 39 + LAG));
    }

    @Test
    public void twoPhrasesSeparatedByAPauseAreTwoSegmentsAndAShortPauseIsNot() {
        VoiceActivityDetector vad = detector(600, 10);
        vad.feed(quiet(30), 30 * FRAME);
        vad.feed(tone(15), 15 * FRAME);
        vad.feed(quiet(10), 10 * FRAME);       // 300 ms: within one phrase
        vad.feed(tone(15), 15 * FRAME);
        vad.feed(quiet(25), 25 * FRAME);       // 750 ms: the phrase ends
        vad.feed(tone(15), 15 * FRAME);
        vad.feed(quiet(25), 25 * FRAME);

        assertEquals(2, segments.size());
        assertEquals(10 + 15 + 10 + 15 + LAG + 10, frames(segments.get(0)));
        assertEquals(10 + 15 + LAG + 10, frames(segments.get(1)));
    }

    @Test
    public void lessThanThreeHundredMillisecondsOfVoiceIsDropped() {
        VoiceActivityDetector vad = detector(600, 10);
        vad.feed(quiet(30), 30 * FRAME);
        vad.feed(tone(6), 6 * FRAME);          // 180 ms: a click, not a word
        vad.feed(quiet(30), 30 * FRAME);
        assertEquals(0, segments.size());

        vad.feed(tone(10), 10 * FRAME);        // exactly 300 ms is kept
        vad.feed(quiet(30), 30 * FRAME);
        assertEquals(1, segments.size());
    }

    @Test
    public void thePauseSettingIsHonoured() {
        VoiceActivityDetector vad = detector(1200, 10);
        vad.feed(quiet(30), 30 * FRAME);
        vad.feed(tone(15), 15 * FRAME);
        vad.feed(quiet(30), 30 * FRAME);       // 900 ms: not yet a pause at 1200
        assertEquals(0, segments.size());
        vad.feed(quiet(15), 15 * FRAME);       // 1350 ms: now it is
        assertEquals(1, segments.size());
    }

    @Test
    public void silenceTimesOutOnceAfterTwoAndAHalfSeconds() {
        VoiceActivityDetector vad = detector(600, 10);
        vad.feed(quiet(83), 83 * FRAME);       // 2490 ms
        assertEquals(0, silenceTimeouts);
        vad.feed(quiet(1), FRAME);             // 2520 ms
        assertEquals(1, silenceTimeouts);
        vad.feed(quiet(100), 100 * FRAME);
        assertEquals("fires once until speech resumes", 1, silenceTimeouts);

        vad.feed(tone(15), 15 * FRAME);
        vad.feed(quiet(84 + LAG), (84 + LAG) * FRAME);   // counted from the last voiced frame
        assertEquals(2, silenceTimeouts);
    }

    @Test
    public void theSilenceSettingIsHonoured() {
        VoiceActivityDetector vad = detector(600, 10, VoiceSilenceTimeout.DEFAULT_MS);
        // Rounded up to whole frames, as the detector does.
        int frames = (VoiceSilenceTimeout.DEFAULT_MS + VoiceActivityDetector.FRAME_MS - 1) / VoiceActivityDetector.FRAME_MS;
        vad.feed(quiet(frames - 1), (frames - 1) * FRAME);
        assertEquals(0, silenceTimeouts);
        vad.feed(quiet(1), FRAME);
        assertEquals(1, silenceTimeouts);
    }

    @Test
    public void untilTapNeverTimesOut() {
        VoiceActivityDetector vad = detector(600, 10, VoiceSilenceTimeout.UNTIL_TAP);
        vad.feed(quiet(2_000), 2_000 * FRAME);  // 60 s of silence
        assertEquals(0, silenceTimeouts);
    }

    @Test
    public void aLongPhraseIsCutShortOfTheWindowAndContinues() {
        VoiceActivityDetector vad = detector(600, 5);
        vad.feed(quiet(30), 30 * FRAME);
        vad.feed(tone(200), 200 * FRAME);      // 6 s of speech against a 5 s window
        vad.feed(quiet(30), 30 * FRAME);

        assertEquals(2, segments.size());
        int cutFrames = 5 * 1000 / 30 - 2 * 10;
        assertTrue("first piece fits the window with room for padding",
            frames(segments.get(0)) <= cutFrames);
        assertTrue(frames(segments.get(0)) > cutFrames - 34);
        assertEquals(10 + 200 + LAG + 10, frames(segments.get(0)) + frames(segments.get(1)));
    }

    @Test
    public void finishDeliversAPhraseCutOffByTheUser() {
        VoiceActivityDetector vad = detector(600, 10);
        vad.feed(quiet(30), 30 * FRAME);
        vad.feed(tone(20), 20 * FRAME);
        vad.feed(quiet(5), 5 * FRAME);
        assertEquals(0, segments.size());
        vad.finish();
        assertEquals(1, segments.size());
        assertEquals(10 + 20 + 5, frames(segments.get(0)));
    }

    /** {@code frames} frames of pseudo-random noise with the given RMS in dBFS, plus {@code toneDbfs} of 440 Hz (or none). */
    private static short[] noisy(int frames, double noiseDbfs, double toneDbfs, int seed) {
        short[] out = new short[frames * FRAME];
        double noiseAmp = Math.pow(10, noiseDbfs / 20) * Math.sqrt(3) * 32768;
        double toneAmp = Double.isNaN(toneDbfs) ? 0 : Math.pow(10, toneDbfs / 20) * Math.sqrt(2) * 32768;
        long state = seed;
        for (int i = 0; i < out.length; i++) {
            state = state * 6364136223846793005L + 1442695040888963407L;
            double uniform = ((state >>> 11) / (double) (1L << 53)) * 2 - 1;
            out[i] = (short) (noiseAmp * uniform + toneAmp * Math.sin(2 * Math.PI * 440 * i / VoiceActivityDetector.SAMPLE_RATE));
        }
        return out;
    }

    @Test
    public void aTvInTheRoomDoesNotHoldAPhraseOpen() {
        // pong, 2026-09-25: one near-silent frame at mic-open, then a TV at −62 dBFS; "enter key"
        // and "tab key" at about −45 dBFS with a second's pause. The old floor stuck at the silent
        // frame and the whole take came out as one 8.5 s segment.
        VoiceActivityDetector vad = detector(600, 10);
        vad.feed(quiet(2), 2 * FRAME);
        vad.feed(noisy(130, -62, Double.NaN, 1), 130 * FRAME);   // ~4 s: the floor finds the TV
        segments.clear();
        vad.feed(noisy(25, -62, -45, 2), 25 * FRAME);            // "enter key"
        vad.feed(noisy(35, -62, Double.NaN, 3), 35 * FRAME);     // ~1 s pause
        vad.feed(noisy(25, -62, -45, 4), 25 * FRAME);            // "tab key"
        vad.feed(noisy(35, -62, Double.NaN, 5), 35 * FRAME);

        assertEquals(2, segments.size());
        assertTrue(frames(segments.get(0)) < 25 + 2 * 10 + 20);
    }

    /** Fan-like noise: {@link #noisy} whose level jumps ±{@code swingDb} from frame to frame. */
    private static short[] fan(int frames, double meanDbfs, double swingDb, int seed) {
        short[] out = new short[frames * FRAME];
        java.util.Random random = new java.util.Random(seed);
        for (int f = 0; f < frames; f++) {
            double db = meanDbfs + (random.nextDouble() * 2 - 1) * swingDb;
            System.arraycopy(noisy(1, db, Double.NaN, seed * 1000 + f), 0, out, f * FRAME, FRAME);
        }
        return out;
    }

    @Test
    public void aFanDoesNotOpenSegments() {
        // pong, 2026-09-25: a fan at about −59 dBFS mean whose frames swung ±5.5 dB; 9–17 frames a
        // second crossed the onset and held "ls" inside a 6.9 s segment.
        VoiceActivityDetector vad = detector(600, 10);
        vad.feed(fan(170, -59, 5.5, 1), 170 * FRAME);           // ~5 s of fan alone
        assertEquals(0, segments.size());
        vad.feed(noisy(12, -59, -46, 2), 12 * FRAME);            // "ls"
        vad.feed(fan(40, -59, 5.5, 3), 40 * FRAME);
        assertEquals(1, segments.size());
        assertTrue(frames(segments.get(0)) < 12 + LAG + 2 * 10 + 10);
    }

    @Test
    public void partialReadsAreReassembledIntoFrames() {
        VoiceActivityDetector vad = detector(600, 10);
        short[] room = quiet(30);
        for (int offset = 0; offset < room.length; offset += 100) {
            short[] chunk = new short[Math.min(100, room.length - offset)];
            System.arraycopy(room, offset, chunk, 0, chunk.length);
            vad.feed(chunk, chunk.length);
        }
        vad.feed(tone(20), 20 * FRAME);
        vad.feed(quiet(30), 30 * FRAME);
        assertEquals(1, segments.size());
        assertEquals(40 + LAG, frames(segments.get(0)));
    }
}
