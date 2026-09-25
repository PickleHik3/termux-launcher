package com.termux.ai;

import androidx.annotation.NonNull;

import java.util.Random;

/**
 * NeMo's {@code AudioToMelSpectrogramPreprocessor} as the Parakeet TDT graphs were trained with it
 * — the port of {@code features()} in {@code scripts/parakeet_replay_server.py}: pre-emphasis
 * 0.97, a 25 ms symmetric Hann window (400 samples) centred in a 512-point FFT, 10 ms hop, centred
 * with zero padding, power spectrum, 128 Slaney mels, {@code ln(x + 2^-24)}, then per-bin
 * mean/std normalisation over the window ({@code N − 1} in the std, {@code + 1e-5}).
 *
 * <p>The graph takes exactly {@link #FRAMES} frames ({@link #WINDOW_SECONDS} s). A shorter clip is
 * padded with near-silent <em>audio</em> ({@link #PAD_NOISE}, about −80 dBFS) before the features
 * are taken and normalised over the whole window. Padding the features with zeros instead reads
 * as "average sound" after per-bin normalisation, and with no length input to mask it the model
 * repeated itself on short phrases ("Enter key. Enter key"). The padding is seeded by the clip's
 * length, so a replay is deterministic; it is noise, not a constant, so it never sits on the log
 * guard floor. No dither.
 */
final class ParakeetFeatures {
    static final int SAMPLE_RATE = 16_000;
    static final int N_FFT = 512;
    static final int WIN = 400;
    static final int HOP = 160;
    static final int N_MELS = 128;
    static final int N_FREQS = N_FFT / 2 + 1;
    static final int FRAMES = 500;
    static final int WINDOW_SECONDS = 5;
    static final int WINDOW_SAMPLES = SAMPLE_RATE * WINDOW_SECONDS;
    static final double PREEMPHASIS = 0.97;
    static final double LOG_GUARD = Math.pow(2.0, -24);
    static final double NORMALISE_GUARD = 1e-5;
    static final float PAD_NOISE = 1e-4f;
    /** Where the 400-sample window sits inside the 512-point frame. */
    private static final int WINDOW_OFFSET = (N_FFT - WIN) / 2;

    private static final double[] WINDOW = hann();
    private static final float[][] FILTERS = filters();
    private static final Fft512 FFT = new Fft512();

    private ParakeetFeatures() {
    }

    /**
     * {@code audio} (16 kHz mono in [−1, 1]) cut or padded to the 5 s window, see the class note.
     * Never returns the input array itself.
     */
    @NonNull
    static float[] windowed(@NonNull float[] audio) {
        float[] out = new float[WINDOW_SAMPLES];
        int copied = Math.min(audio.length, WINDOW_SAMPLES);
        System.arraycopy(audio, 0, out, 0, copied);
        if (copied < WINDOW_SAMPLES) {
            Random random = new Random(copied);
            for (int i = copied; i < WINDOW_SAMPLES; i++) out[i] = (float) (random.nextGaussian() * PAD_NOISE);
        }
        return out;
    }

    /** The normalised log-mel features {@code [N_MELS][FRAMES]} of {@code audio}, windowed first. */
    @NonNull
    static float[][] features(@NonNull float[] audio) {
        float[] window = windowed(audio);
        // Pre-emphasis, then centre padding by N_FFT / 2 on both sides (np.pad "constant").
        double[] padded = new double[WINDOW_SAMPLES + N_FFT];
        padded[N_FFT / 2] = window[0];
        for (int i = 1; i < WINDOW_SAMPLES; i++) padded[N_FFT / 2 + i] = window[i] - PREEMPHASIS * window[i - 1];
        double[] frame = new double[N_FFT];
        double[] power = new double[N_FREQS];
        double[][] mel = new double[N_MELS][FRAMES];
        for (int t = 0; t < FRAMES; t++) {
            int base = t * HOP;
            for (int j = 0; j < N_FFT; j++) frame[j] = padded[base + j] * WINDOW[j];
            FFT.powerSpectrum(frame, power);
            for (int m = 0; m < N_MELS; m++) {
                float[] filter = FILTERS[m];
                double sum = 0.0;
                for (int k = 0; k < N_FREQS; k++) sum += power[k] * filter[k];
                mel[m][t] = Math.log(sum + LOG_GUARD);
            }
        }
        float[][] out = new float[N_MELS][FRAMES];
        for (int m = 0; m < N_MELS; m++) {
            double mean = 0.0;
            for (int t = 0; t < FRAMES; t++) mean += mel[m][t];
            mean /= FRAMES;
            double variance = 0.0;
            for (int t = 0; t < FRAMES; t++) {
                double d = mel[m][t] - mean;
                variance += d * d;
            }
            double std = Math.sqrt(variance / (FRAMES - 1));
            for (int t = 0; t < FRAMES; t++) out[m][t] = (float) ((mel[m][t] - mean) / (std + NORMALISE_GUARD));
        }
        return out;
    }

    /** {@code torch.hann_window(400, periodic=False)}, zero-padded to the 512-point frame, centred. */
    @NonNull
    private static double[] hann() {
        double[] window = new double[N_FFT];
        for (int n = 0; n < WIN; n++) {
            window[WINDOW_OFFSET + n] = 0.5 - 0.5 * Math.cos(2.0 * Math.PI * n / (WIN - 1));
        }
        return window;
    }

    /** {@code librosa.filters.mel(sr=16000, n_fft=512, n_mels=128, norm='slaney')}, as NeMo builds it. */
    @NonNull
    static float[][] filters() {
        double[] fftFreqs = new double[N_FREQS];
        for (int k = 0; k < N_FREQS; k++) fftFreqs[k] = (SAMPLE_RATE / 2.0) * k / (N_FREQS - 1);
        double[] melPoints = new double[N_MELS + 2];
        double melMin = WhisperMel.hzToMel(0.0);
        double melMax = WhisperMel.hzToMel(SAMPLE_RATE / 2.0);
        for (int i = 0; i < melPoints.length; i++) {
            melPoints[i] = WhisperMel.melToHz(melMin + (melMax - melMin) * i / (N_MELS + 1));
        }
        float[][] filters = new float[N_MELS][N_FREQS];
        for (int m = 0; m < N_MELS; m++) {
            double lowerWidth = melPoints[m + 1] - melPoints[m];
            double upperWidth = melPoints[m + 2] - melPoints[m + 1];
            double enorm = 2.0 / (melPoints[m + 2] - melPoints[m]);
            for (int k = 0; k < N_FREQS; k++) {
                double lower = (fftFreqs[k] - melPoints[m]) / lowerWidth;
                double upper = (melPoints[m + 2] - fftFreqs[k]) / upperWidth;
                double weight = Math.max(0.0, Math.min(lower, upper));
                filters[m][k] = (float) (weight * enorm);
            }
        }
        return filters;
    }

    /** An iterative radix-2 real-input FFT of 512 points, returning the one-sided power spectrum. */
    static final class Fft512 {
        private final double[] re = new double[N_FFT];
        private final double[] im = new double[N_FFT];
        private final double[] cos = new double[N_FFT / 2];
        private final double[] sin = new double[N_FFT / 2];
        private final int[] reversed = new int[N_FFT];

        Fft512() {
            for (int i = 0; i < N_FFT / 2; i++) {
                cos[i] = Math.cos(2.0 * Math.PI * i / N_FFT);
                sin[i] = -Math.sin(2.0 * Math.PI * i / N_FFT);
            }
            int bits = Integer.numberOfTrailingZeros(N_FFT);
            for (int i = 0; i < N_FFT; i++) reversed[i] = Integer.reverse(i) >>> (32 - bits);
        }

        /** {@code power[k] = |X[k]|²} for {@code k} in {@code [0, N_FFT / 2]}. Not thread-safe; callers serialise. */
        synchronized void powerSpectrum(@NonNull double[] frame, @NonNull double[] power) {
            for (int i = 0; i < N_FFT; i++) {
                re[reversed[i]] = frame[i];
                im[reversed[i]] = 0.0;
            }
            for (int size = 2; size <= N_FFT; size <<= 1) {
                int half = size >> 1;
                int step = N_FFT / size;
                for (int start = 0; start < N_FFT; start += size) {
                    for (int j = 0; j < half; j++) {
                        int k = j * step;
                        int a = start + j;
                        int b = a + half;
                        double tr = re[b] * cos[k] - im[b] * sin[k];
                        double ti = re[b] * sin[k] + im[b] * cos[k];
                        re[b] = re[a] - tr;
                        im[b] = im[a] - ti;
                        re[a] += tr;
                        im[a] += ti;
                    }
                }
            }
            for (int k = 0; k < N_FREQS; k++) power[k] = re[k] * re[k] + im[k] * im[k];
        }
    }
}
