package com.termux.ai;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/** Pause-based cutting from a table: the split point, the voiced-audio minimum and the padding. */
public class WhisperSegmenterTest {
    private static final int SR = WhisperSegmenter.SAMPLE_RATE;
    private static final int FRAME = WhisperSegmenter.FRAME_SAMPLES;
    private static final int WINDOW_10S = 10 * SR;
    private static final int PAD = (int) (WhisperSegmenter.PAD_SECONDS * SR);

    /** A square wave of the given amplitude (its RMS), so energy per frame is exact. */
    private static float[] tone(int samples, float amplitude) {
        float[] out = new float[samples];
        for (int i = 0; i < samples; i++) out[i] = (i & 1) == 0 ? amplitude : -amplitude;
        return out;
    }

    private static void fill(float[] audio, int from, int to, float amplitude) {
        for (int i = from; i < to; i++) audio[i] = (i & 1) == 0 ? amplitude : -amplitude;
    }

    @Test
    public void longAudioIsCutAtTheQuietestFrameInTheLastSecondBeforeEachBoundary() {
        int effectiveWindow = WINDOW_10S - 2 * PAD;
        float[] audio = tone(25 * SR, 0.1f);
        // One silent frame 0.4 s before the first boundary and one 0.4 s before the second.
        int firstDip = effectiveWindow - (int) (0.4 * SR);
        int secondDip = firstDip + effectiveWindow - (int) (0.4 * SR);
        fill(audio, firstDip, firstDip + FRAME, 0f);
        fill(audio, secondDip, secondDip + FRAME, 0f);

        List<int[]> ranges = WhisperSegmenter.splitPoints(audio, effectiveWindow);

        assertEquals(3, ranges.size());
        assertArrayEquals(new int[] {0, firstDip}, ranges.get(0));
        assertArrayEquals(new int[] {firstDip, secondDip}, ranges.get(1));
        assertArrayEquals(new int[] {secondDip, audio.length}, ranges.get(2));
        for (int[] range : ranges) assertTrue(range[1] - range[0] <= effectiveWindow);
    }

    @Test
    public void aDipOutsideTheLastSecondIsIgnoredAndAFlatWindowCutsAtTheBoundaryFrame() {
        float[] audio = tone(12 * SR, 0.1f);
        fill(audio, 3 * SR, 3 * SR + FRAME, 0f);
        List<int[]> ranges = WhisperSegmenter.splitPoints(audio, WINDOW_10S);
        assertEquals(2, ranges.size());
        // All frames in [9 s, 10 s) are equal; the first of them wins, so the cut is at 9 s.
        assertEquals(9 * SR, ranges.get(0)[1]);
        // A range without a whole frame cuts at its end.
        assertEquals(WINDOW_10S + 10, WhisperSegmenter.quietestFrameStart(audio, WINDOW_10S, WINDOW_10S + 10));
    }

    @Test
    public void audioWithinTheWindowIsOnePiece() {
        float[] audio = new float[2 * SR];
        fill(audio, PAD, PAD + SR, 0.1f);
        List<WhisperSegmenter.Piece> pieces = WhisperSegmenter.pieces(audio, WINDOW_10S);
        assertEquals(1, pieces.size());
        assertEquals(0, pieces.get(0).start);
        assertEquals(audio.length, pieces.get(0).end);
        assertEquals(audio.length, pieces.get(0).samples.length);
    }

    @Test
    public void lessThanThreeTenthsOfASecondOfVoiceIsDroppedNotDecoded() {
        assertFalse("0.1 s remainder", WhisperSegmenter.hasVoice(tone((int) (0.1 * SR), 0.1f)));
        assertFalse("digital silence", WhisperSegmenter.hasVoice(new float[2 * SR]));
        assertFalse("room noise only", WhisperSegmenter.hasVoice(tone(2 * SR, 0.001f)));
        // Voicing is counted in whole 30 ms frames, so 0.25 s spans nine frames (0.27 s), under the ten needed.
        float[] shortWord = new float[SR];
        fill(shortWord, PAD, PAD + (int) (0.25 * SR), 0.1f);
        assertFalse("0.25 s of speech", WhisperSegmenter.hasVoice(shortWord));
        float[] word = new float[SR];
        fill(word, PAD, PAD + (int) (0.33 * SR), 0.1f);
        assertTrue("0.33 s of speech", WhisperSegmenter.hasVoice(word));
        assertTrue("tightly cut speech with no quiet at all", WhisperSegmenter.hasVoice(tone(SR, 0.1f)));
        assertTrue(WhisperSegmenter.pieces(tone((int) (0.1 * SR), 0.1f), WINDOW_10S).isEmpty());
    }

    @Test
    public void speechAtTheEdgesGetsThreeHundredMillisecondsOfSilenceEachSide() {
        float[] tight = tone(SR / 2, 0.1f);
        float[] padded = WhisperSegmenter.padded(tight, PAD);
        assertEquals(PAD + tight.length + PAD, padded.length);
        for (int i = 0; i < PAD; i++) {
            assertEquals(0f, padded[i], 0f);
            assertEquals(0f, padded[padded.length - 1 - i], 0f);
        }
        assertEquals(tight[0], padded[PAD], 0f);

        // 0.4 s of quiet on both sides already: nothing is added.
        float[] roomy = new float[SR / 2 + 2 * (int) (0.4 * SR)];
        fill(roomy, (int) (0.4 * SR), (int) (0.4 * SR) + SR / 2, 0.1f);
        assertSame(roomy, WhisperSegmenter.padded(roomy, PAD));

        // 0.1 s of lead-in: the difference is added at the front only. The onset frame counts as
        // voiced from its start, so the lead-in is the three whole quiet frames before it (90 ms).
        float[] lopsided = new float[SR / 2 + (int) (0.1 * SR) + (int) (0.5 * SR)];
        fill(lopsided, (int) (0.1 * SR), (int) (0.1 * SR) + SR / 2, 0.1f);
        int quietFrames = (int) (0.1 * SR) / FRAME;
        assertEquals(lopsided.length + PAD - quietFrames * FRAME, WhisperSegmenter.padded(lopsided, PAD).length);
    }

    @Test
    public void theVoiceThresholdSitsNineDecibelsOverTheNoiseFloor() {
        assertEquals(0.001f * WhisperSegmenter.VOICE_OVER_FLOOR, WhisperSegmenter.voiceThreshold(new float[] {0.001f, 0.001f, 0.001f, 0.2f, 0.3f}), 1e-6f);
        assertEquals(WhisperSegmenter.ABSOLUTE_FLOOR, WhisperSegmenter.voiceThreshold(new float[] {0f, 0f, 0f, 0.2f}), 0f);
        assertEquals(WhisperSegmenter.NOISE_FLOOR_CAP * WhisperSegmenter.VOICE_OVER_FLOOR, WhisperSegmenter.voiceThreshold(new float[] {0.1f, 0.1f, 0.1f}), 1e-6f);
        assertEquals(WhisperSegmenter.ABSOLUTE_FLOOR, WhisperSegmenter.voiceThreshold(new float[0]), 0f);
    }
}
