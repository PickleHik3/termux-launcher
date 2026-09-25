package com.termux.app.terminal.inappkeyboard.voice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The start-tone lead-in is dropped sample-exactly, across reads, and only when asked for. */
public class VoiceLeadInDiscardTest {

    @Test
    public void dropsExactlyTheLeadInAcrossReads() {
        VoiceLeadInDiscard discard = new VoiceLeadInDiscard(150, 16_000);
        int budget = 16_000 * 150 / 1000;
        int frame = VoiceActivityDetector.FRAME_SAMPLES;
        int dropped = 0;
        for (int i = 0; i < 10; i++) dropped += discard.take(frame);
        assertEquals(budget, dropped);
        assertTrue(discard.done());
        assertEquals(0, discard.take(frame));
    }

    @Test
    public void aReadStraddlingTheEndKeepsItsTail() {
        VoiceLeadInDiscard discard = new VoiceLeadInDiscard(30, 16_000);
        assertEquals(400, discard.take(400));
        assertFalse(discard.done());
        // 80 of these 100 samples finish the lead-in; 20 go through.
        assertEquals(80, discard.take(100));
        assertTrue(discard.done());
    }

    @Test
    public void zeroDropsNothing() {
        VoiceLeadInDiscard discard = new VoiceLeadInDiscard(0, 16_000);
        assertTrue(discard.done());
        assertEquals(0, discard.take(480));
    }

    @Test
    public void theDetectorNeverSeesTheDroppedSamples() {
        int[] fed = {0};
        VoiceActivityDetector detector = new VoiceActivityDetector(new VoiceActivityDetector.Listener() {
            @Override
            public void onLevel(float rms, boolean voiced, float noiseFloor) {
                fed[0]++;
            }

            @Override
            public void onSegment(short[] pcm, int voicedFrames) {
            }

            @Override
            public void onSilenceTimeout() {
            }
        }, 600, 10, 2_500);
        VoiceLeadInDiscard discard = new VoiceLeadInDiscard(VoiceLeadInDiscard.START_TONE_MS, VoiceActivityDetector.SAMPLE_RATE);
        short[] buffer = new short[VoiceActivityDetector.FRAME_SAMPLES];
        int frames = 20;
        for (int i = 0; i < frames; i++) {
            int drop = discard.take(buffer.length);
            if (drop < buffer.length) detector.feed(buffer, drop, buffer.length - drop);
        }
        int droppedFrames = VoiceLeadInDiscard.START_TONE_MS / VoiceActivityDetector.FRAME_MS;
        assertEquals(frames - droppedFrames, fed[0]);
    }
}
