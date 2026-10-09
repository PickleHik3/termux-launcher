package com.termux.app.terminal.inappkeyboard.voice;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class VoiceGainTest {

    @Test
    public void aQuietSegmentIsRaisedToTheTargetPeak() {
        short[] pcm = {820, -410, 200, 0};  // a −32 dBFS peak
        float gain = VoiceGain.apply(pcm);
        assertEquals(VoiceGain.TARGET_PEAK * 32768f / 820f, gain, 0.01f);
        assertEquals(Math.round(820 * gain), pcm[0]);
        assertEquals(Math.round(-410 * gain), pcm[1]);
        assertEquals(0, pcm[3]);
    }

    @Test
    public void theGainIsCappedAt40dB() {
        // −52 dBFS peak, what pong's bottom mic gave for "ls" at arm's length.
        short[] pcm = {82, -41, 1};
        assertEquals(VoiceGain.MAX_GAIN, VoiceGain.apply(pcm), 0f);
        assertEquals(Math.round(82 * VoiceGain.MAX_GAIN), pcm[0]);
    }

    @Test
    public void aLoudOrSilentSegmentIsLeftAlone() {
        short[] loud = {20000, -30000};
        assertEquals(1f, VoiceGain.apply(loud), 0f);
        assertEquals(-30000, loud[1]);
        assertEquals(1f, VoiceGain.apply(new short[] {0, 0}), 0f);
    }
}
