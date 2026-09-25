package com.termux.ai;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The NeMo log-mel front end against the fixture {@code app/src/test/resources/parakeet/gen_fixture.py}
 * produced with {@code scripts/parakeet_replay_server.py}'s {@code features()}: the same synthetic
 * 5 s signal built here, every fourth frame of its {@code [128, 500]} features, and the stats of
 * the whole array. Plus the audio padding a short clip gets before the features are taken.
 */
public class ParakeetFeaturesTest {
    private static final int STRIDE = 4;
    private static final int STRIDED_FRAMES = ParakeetFeatures.FRAMES / STRIDE;

    /** gen_fixture.py's {@code signal()}: tone, chirp, LCG noise burst, near-silent tail, int16-quantised. */
    static float[] fixtureSignal() {
        float[] out = new float[ParakeetFeatures.WINDOW_SAMPLES];
        long state = 12345L;
        for (int i = 0; i < out.length; i++) {
            double t = i / (double) ParakeetFeatures.SAMPLE_RATE;
            state = (state * 1103515245L + 12345L) % (1L << 31);
            double noise = state / (double) (1L << 31) - 0.5;
            double x = 0.0;
            if (t < 2.5) x += 0.3 * Math.sin(2.0 * Math.PI * 220.0 * t);
            if (t >= 1.0 && t < 3.5) x += 0.2 * Math.sin(2.0 * Math.PI * (500.0 * t + 200.0 * t * t));
            if (t >= 3.0 && t < 4.0) x += 0.05 * noise;
            if (t >= 4.0) x += 0.001 * Math.sin(2.0 * Math.PI * 1000.0 * t);
            double pcm = Math.max(-32768.0, Math.min(32767.0, Math.floor(x * 32767.0 + 0.5)));
            out[i] = (float) (pcm / 32768.0);
        }
        return out;
    }

    @Test
    public void theFixtureSignalIsTheOneThePythonReferenceSaw() throws Exception {
        JSONObject stats = stats();
        float[] audio = fixtureSignal();
        assertEquals(stats.getInt("audio_samples"), audio.length);
        double sum = 0.0;
        for (float sample : audio) sum += sample;
        assertEquals(stats.getDouble("audio_sum"), sum, 1e-3);
    }

    @Test
    public void featuresOfTheFixtureSignalMatchTheReferenceWithinTwoThousandths() throws Exception {
        float[] expected = WhisperMelTest.readFloats("parakeet/features_128x125_stride4.f32", ParakeetFeatures.N_MELS * STRIDED_FRAMES);
        float[][] features = ParakeetFeatures.features(fixtureSignal());
        assertEquals(ParakeetFeatures.N_MELS, features.length);
        assertEquals(ParakeetFeatures.FRAMES, features[0].length);
        double maxError = 0.0;
        for (int m = 0; m < ParakeetFeatures.N_MELS; m++) {
            for (int f = 0; f < STRIDED_FRAMES; f++) {
                maxError = Math.max(maxError, Math.abs(features[m][f * STRIDE] - expected[m * STRIDED_FRAMES + f]));
            }
        }
        assertTrue("features max abs error " + maxError, maxError <= 2e-3);

        JSONObject stats = stats();
        double min = Double.MAX_VALUE, max = -Double.MAX_VALUE, sum = 0.0;
        for (float[] row : features) {
            for (float value : row) {
                min = Math.min(min, value);
                max = Math.max(max, value);
                sum += value;
            }
        }
        assertEquals(stats.getDouble("min"), min, 2e-3);
        assertEquals(stats.getDouble("max"), max, 2e-3);
        assertEquals(stats.getDouble("mean"), sum / (ParakeetFeatures.N_MELS * ParakeetFeatures.FRAMES), 1e-4);
        assertEquals(stats.getDouble("frame0_bin0"), features[0][0], 2e-3);
        assertEquals(stats.getDouble("frame250_bin64"), features[64][250], 2e-3);
    }

    @Test
    public void everyBinIsNormalisedToZeroMeanAndUnitSpreadOverTheWindow() {
        float[][] features = ParakeetFeatures.features(fixtureSignal());
        for (int m = 0; m < ParakeetFeatures.N_MELS; m++) {
            double mean = 0.0;
            for (float value : features[m]) mean += value;
            mean /= ParakeetFeatures.FRAMES;
            double variance = 0.0;
            for (float value : features[m]) variance += (value - mean) * (value - mean);
            double std = Math.sqrt(variance / (ParakeetFeatures.FRAMES - 1));
            assertEquals("bin " + m + " mean", 0.0, mean, 1e-4);
            assertEquals("bin " + m + " std", 1.0, std, 1e-3);
        }
    }

    @Test
    public void aShortClipIsPaddedWithNearSilentNoiseNotZerosAndDeterministically() {
        float[] clip = new float[ParakeetFeatures.SAMPLE_RATE]; // 1 s
        for (int i = 0; i < clip.length; i++) clip[i] = (float) (0.2 * Math.sin(2.0 * Math.PI * 300.0 * i / ParakeetFeatures.SAMPLE_RATE));
        float[] window = ParakeetFeatures.windowed(clip);
        assertEquals(ParakeetFeatures.WINDOW_SAMPLES, window.length);
        for (int i = 0; i < clip.length; i++) assertEquals(clip[i], window[i], 0f);
        double energy = 0.0;
        int nonZero = 0;
        for (int i = clip.length; i < window.length; i++) {
            energy += (double) window[i] * window[i];
            if (window[i] != 0f) nonZero++;
        }
        double rms = Math.sqrt(energy / (window.length - clip.length));
        assertTrue("padding is noise, not zeros: " + nonZero, nonZero > (window.length - clip.length) * 9 / 10);
        assertTrue("padding rms " + rms + " is about PAD_NOISE", rms > ParakeetFeatures.PAD_NOISE * 0.8 && rms < ParakeetFeatures.PAD_NOISE * 1.2);
        // Seeded by the clip's length: the same clip pads the same way every time.
        assertArrayEquals(window, ParakeetFeatures.windowed(clip), 0f);
        assertTrue("a different length seeds different padding",
            window[clip.length] != ParakeetFeatures.windowed(new float[clip.length + 1])[clip.length + 1]);
        // The features of the padded window carry no NaN and the padded frames are not a constant.
        float[][] features = ParakeetFeatures.features(clip);
        boolean constantTail = true;
        for (int t = 200; t < ParakeetFeatures.FRAMES; t++) {
            assertFalse(Float.isNaN(features[10][t]));
            if (Math.abs(features[10][t] - features[10][200]) > 1e-3) constantTail = false;
        }
        assertFalse("the padded frames vary like noise, not a flat floor", constantTail);
    }

    @Test
    public void aLongClipIsCutToTheWindow() {
        float[] clip = new float[ParakeetFeatures.WINDOW_SAMPLES + 5000];
        for (int i = 0; i < clip.length; i++) clip[i] = i;
        float[] window = ParakeetFeatures.windowed(clip);
        assertEquals(ParakeetFeatures.WINDOW_SAMPLES, window.length);
        assertEquals(ParakeetFeatures.WINDOW_SAMPLES - 1, window[window.length - 1], 0f);
    }

    @Test
    public void theSlaneyFilterbankHasOrderedNonNegativeTriangles() {
        float[][] filters = ParakeetFeatures.filters();
        assertEquals(ParakeetFeatures.N_MELS, filters.length);
        int previousPeak = -1;
        for (int m = 0; m < ParakeetFeatures.N_MELS; m++) {
            assertEquals(ParakeetFeatures.N_FREQS, filters[m].length);
            int peak = 0;
            float area = 0f;
            for (int k = 0; k < ParakeetFeatures.N_FREQS; k++) {
                assertTrue(filters[m][k] >= 0f);
                area += filters[m][k];
                if (filters[m][k] > filters[m][peak]) peak = k;
            }
            assertTrue("filter " + m + " is not empty", area > 0f);
            assertTrue("filter peaks move up in frequency", peak >= previousPeak);
            previousPeak = peak;
        }
    }

    @Test
    public void theFftAgreesWithADirectDft() {
        double[] frame = new double[ParakeetFeatures.N_FFT];
        java.util.Random random = new java.util.Random(11);
        for (int i = 0; i < frame.length; i++) frame[i] = random.nextGaussian();
        double[] power = new double[ParakeetFeatures.N_FREQS];
        new ParakeetFeatures.Fft512().powerSpectrum(frame, power);
        for (int k = 0; k < ParakeetFeatures.N_FREQS; k++) {
            double re = 0.0, im = 0.0;
            for (int n = 0; n < frame.length; n++) {
                double angle = 2.0 * Math.PI * k * n / frame.length;
                re += frame[n] * Math.cos(angle);
                im -= frame[n] * Math.sin(angle);
            }
            assertEquals("bin " + k, re * re + im * im, power[k], 1e-6 * Math.max(1.0, re * re + im * im));
        }
    }

    private static JSONObject stats() throws IOException, JSONException {
        return new JSONObject(new String(WhisperMelTest.readBytes("parakeet/features_stats.json"), StandardCharsets.UTF_8));
    }
}
