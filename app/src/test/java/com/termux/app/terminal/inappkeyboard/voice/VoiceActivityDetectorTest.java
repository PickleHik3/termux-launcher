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
            public void onSegment(@NonNull short[] pcm) {
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
        // 10 frames of pre-roll + 20 voiced + 10 frames of tail.
        assertEquals(40, frames(segment));
        assertTrue("pre-roll is quiet", isQuietFrame(segment, 0));
        assertTrue("pre-roll is quiet", isQuietFrame(segment, 9));
        assertFalse("speech follows the pre-roll", isQuietFrame(segment, 10));
        assertFalse("speech runs to the tail", isQuietFrame(segment, 29));
        assertTrue("tail is quiet", isQuietFrame(segment, 30));
        assertTrue("tail is quiet", isQuietFrame(segment, 39));
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
        assertEquals(10 + 15 + 10 + 15 + 10, frames(segments.get(0)));
        assertEquals(10 + 15 + 10, frames(segments.get(1)));
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
        vad.feed(quiet(84), 84 * FRAME);       // counted from the last voiced frame
        assertEquals(2, silenceTimeouts);
    }

    @Test
    public void theSilenceSettingIsHonoured() {
        VoiceActivityDetector vad = detector(600, 10, VoiceSilenceTimeout.DEFAULT_MS);
        int frames = VoiceSilenceTimeout.DEFAULT_MS / VoiceActivityDetector.FRAME_MS;
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
        assertEquals(10 + 200 + 10, frames(segments.get(0)) + frames(segments.get(1)));
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
        assertEquals(40, frames(segments.get(0)));
    }
}
