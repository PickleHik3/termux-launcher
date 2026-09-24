package com.termux.ai;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * The Java log-mel front end against the fixtures {@code scripts/whisper_reference_decoder.py}
 * generated: the Slaney filterbank and the mel of {@code git_status_16k.wav} at the 10 s window.
 */
public class WhisperMelTest {

    @Test
    public void filterbankMatchesTheReference() throws IOException {
        float[] expected = readFloats("whisper/mel_filters_80x201.f32", WhisperMel.N_MELS * WhisperMel.N_FREQS);
        float[][] filters = WhisperMel.filters();
        assertEquals(WhisperMel.N_MELS, filters.length);
        double maxError = 0.0;
        for (int m = 0; m < WhisperMel.N_MELS; m++) {
            assertEquals(WhisperMel.N_FREQS, filters[m].length);
            for (int f = 0; f < WhisperMel.N_FREQS; f++) {
                maxError = Math.max(maxError, Math.abs(filters[m][f] - expected[m * WhisperMel.N_FREQS + f]));
            }
        }
        assertTrue("filterbank max abs error " + maxError, maxError <= 1e-5);
    }

    @Test
    public void logMelOfTheFixtureClipMatchesTheReferenceWithinOneThousandth() throws IOException {
        float[] audio = WhisperAudio.decodeWav(readBytes("whisper/git_status_16k.wav"));
        assertEquals(23_718, audio.length);
        int frames = 1000;
        float[] expected = readFloats("whisper/git_status_mel_80x1000.f32", WhisperMel.N_MELS * frames);

        float[][] mel = WhisperMel.logMel(audio, frames);

        assertEquals(WhisperMel.N_MELS, mel.length);
        assertEquals(frames, mel[0].length);
        double maxError = 0.0;
        double min = Double.MAX_VALUE, max = -Double.MAX_VALUE, sum = 0.0;
        for (int m = 0; m < WhisperMel.N_MELS; m++) {
            for (int t = 0; t < frames; t++) {
                float value = mel[m][t];
                maxError = Math.max(maxError, Math.abs(value - expected[m * frames + t]));
                min = Math.min(min, value);
                max = Math.max(max, value);
                sum += value;
            }
        }
        assertTrue("log-mel max abs error " + maxError, maxError <= 1e-3);
        // expected.json's mel_stats for the same clip.
        assertEquals(-0.5656465, min, 1e-3);
        assertEquals(1.4343535, max, 1e-3);
        assertEquals(-0.4975636, sum / (WhisperMel.N_MELS * frames), 1e-3);
    }

    @Test
    public void theSplitDftAgreesWithADirectDft() {
        double[] frame = new double[WhisperMel.N_FFT];
        java.util.Random random = new java.util.Random(7);
        for (int i = 0; i < frame.length; i++) frame[i] = random.nextGaussian();
        double[] power = new double[WhisperMel.N_FREQS];
        new WhisperMel.Dft400().powerSpectrum(frame, power);
        for (int k = 0; k < WhisperMel.N_FREQS; k++) {
            double re = 0.0, im = 0.0;
            for (int n = 0; n < frame.length; n++) {
                double angle = 2.0 * Math.PI * k * n / frame.length;
                re += frame[n] * Math.cos(angle);
                im -= frame[n] * Math.sin(angle);
            }
            assertEquals("bin " + k, re * re + im * im, power[k], 1e-6 * Math.max(1.0, re * re + im * im));
        }
    }

    @Test
    public void slaneyScaleRoundTrips() {
        for (double hz : new double[] {0.0, 500.0, 1000.0, 1234.5, 4000.0, 8000.0}) {
            assertEquals(hz, WhisperMel.melToHz(WhisperMel.hzToMel(hz)), 1e-9);
        }
        assertEquals(15.0, WhisperMel.hzToMel(1000.0), 1e-12);
    }

    static float[] readFloats(String resource, int count) throws IOException {
        byte[] bytes = readBytes(resource);
        assertEquals(resource + " size", count * 4, bytes.length);
        float[] out = new float[count];
        ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(out);
        return out;
    }

    static byte[] readBytes(String resource) throws IOException {
        try (InputStream in = WhisperMelTest.class.getClassLoader().getResourceAsStream(resource);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            assertNotNull("missing test resource " + resource, in);
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
            return out.toByteArray();
        }
    }
}
