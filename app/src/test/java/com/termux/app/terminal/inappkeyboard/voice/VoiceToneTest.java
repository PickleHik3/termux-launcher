package com.termux.app.terminal.inappkeyboard.voice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

/** The start/stop blips: length, softness, click-free ends and the pitch that was asked for. */
public class VoiceToneTest {

    @Test
    public void theBlipsAreTheDocumentedLength() {
        assertEquals(VoiceTone.SAMPLE_RATE * VoiceTone.DURATION_MS / 1000, VoiceTone.start().length);
        assertEquals(VoiceTone.SAMPLE_RATE * VoiceTone.DURATION_MS / 1000, VoiceTone.stop().length);
        // Two 60 ms blips around a 50 ms gap.
        assertEquals(VoiceTone.SAMPLE_RATE * 170 / 1000, VoiceTone.error().length);
    }

    @Test
    public void bothEndsFadeToSilenceSoThereIsNoClick() {
        short[] pcm = VoiceTone.sine(16_000, 347.0, 125, 0.16, 10);
        assertEquals(0, pcm[0]);
        assertEquals(0, pcm[pcm.length - 1]);
        // Inside the 10 ms ramps the envelope is still below the body's level.
        int fade = 16_000 * 10 / 1000;
        int rampPeak = 0, bodyPeak = 0;
        for (int i = 0; i < fade / 2; i++) rampPeak = Math.max(rampPeak, Math.abs(pcm[i]));
        for (int i = fade; i < pcm.length - fade; i++) bodyPeak = Math.max(bodyPeak, Math.abs(pcm[i]));
        assertTrue(rampPeak < bodyPeak);
    }

    @Test
    public void theGainIsSoft() {
        short[] pcm = VoiceTone.start();
        int peak = 0;
        for (short sample : pcm) peak = Math.max(peak, Math.abs(sample));
        assertTrue(peak <= Math.round(VoiceTone.GAIN * Short.MAX_VALUE));
        assertTrue(peak > Math.round(VoiceTone.GAIN * Short.MAX_VALUE * 0.95));
    }

    @Test
    public void theFrequencyIsWhatWasAsked() {
        int rate = 16_000;
        short[] pcm = VoiceTone.sine(rate, 400.0, 250, 0.5, 0);
        int crossings = 0;
        for (int i = 1; i < pcm.length; i++) {
            if ((pcm[i - 1] < 0) != (pcm[i] < 0)) crossings++;
        }
        // Two zero crossings per cycle, 100 cycles in 250 ms.
        assertTrue(Math.abs(crossings - 200) <= 2);
    }

    @Test
    public void aFadeLongerThanTheToneIsClampedToHalfOfIt() {
        short[] pcm = VoiceTone.sine(16_000, 300.0, 10, 0.5, 100);
        assertEquals(160, pcm.length);
        assertEquals(0, pcm[0]);
        assertEquals(0, pcm[pcm.length - 1]);
    }
}
