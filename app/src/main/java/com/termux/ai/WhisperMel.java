package com.termux.ai;

import androidx.annotation.NonNull;

/**
 * Whisper's {@code log_mel_spectrogram}, in Java: n_fft 400, hop 160, periodic Hann, reflect
 * padding of n_fft/2, power spectrum, 80 Slaney mel filters over 0–8 kHz, {@code log10(max(x,
 * 1e-10))}, clamped to {@code max − 8}, then {@code (x + 4) / 4}. The audio is zero-padded (or cut)
 * to the graph's window first and the last STFT frame is dropped, exactly as the reference
 * ({@code scripts/whisper_reference_decoder.py}) does; {@code WhisperMelTest} pins the output to
 * the fixture it generated.
 *
 * <p>The 400-point DFT is a 16 × 25 Cooley–Tukey split (a radix-2 FFT over 16, a direct DFT over
 * 25) so a 10 s window costs a few million multiplies, not the 160 million a direct DFT would; all
 * arithmetic is double and cast once at the end.
 */
final class WhisperMel {
    static final int SAMPLE_RATE = 16_000;
    static final int N_FFT = 400;
    static final int HOP = 160;
    static final int N_MELS = 80;
    static final int N_FREQS = N_FFT / 2 + 1;
    /** Frames per second of audio: the graph's window in seconds is {@code frames / 100}. */
    static final int FRAMES_PER_SECOND = SAMPLE_RATE / HOP;

    private static final double LOG_FLOOR = 1e-10;
    private static final double DYNAMIC_RANGE_DB = 8.0;

    private static volatile float[][] filters;

    private WhisperMel() {
    }

    /**
     * The log-mel features for {@code audio} (16 kHz mono, samples in [-1, 1]) as {@code [80][frames]},
     * the encoder's input layout. {@code frames} is the graph's window (500 for 5 s, 1000 for 10 s).
     */
    @NonNull
    static float[][] logMel(@NonNull float[] audio, int frames) {
        if (frames < 2) throw new IllegalArgumentException("Whisper window must be at least two frames: " + frames);
        int samples = frames * HOP;
        int half = N_FFT / 2;
        // Zero-pad or cut to the window, then reflect-pad n_fft/2 on both sides (numpy "reflect":
        // the edge sample itself is not repeated).
        double[] padded = new double[samples + N_FFT];
        for (int i = 0; i < samples; i++) padded[half + i] = i < audio.length ? audio[i] : 0.0;
        for (int i = 0; i < half; i++) {
            padded[half - 1 - i] = padded[half + 1 + i];
            padded[half + samples + i] = padded[half + samples - 2 - i];
        }
        double[] window = new double[N_FFT];
        for (int k = 0; k < N_FFT; k++) window[k] = 0.5 - 0.5 * Math.cos(2.0 * Math.PI * k / N_FFT);
        float[][] filterbank = filters();
        // 1 + (len − n_fft) / hop STFT frames minus the last one is exactly {@code frames}.
        double[][] logMel = new double[N_MELS][frames];
        double[] frame = new double[N_FFT];
        double[] power = new double[N_FREQS];
        Dft400 dft = new Dft400();
        double max = Double.NEGATIVE_INFINITY;
        for (int t = 0; t < frames; t++) {
            int offset = t * HOP;
            for (int k = 0; k < N_FFT; k++) frame[k] = padded[offset + k] * window[k];
            dft.powerSpectrum(frame, power);
            for (int m = 0; m < N_MELS; m++) {
                float[] row = filterbank[m];
                double sum = 0.0;
                for (int f = 0; f < N_FREQS; f++) sum += row[f] * power[f];
                double value = Math.log10(Math.max(sum, LOG_FLOOR));
                logMel[m][t] = value;
                if (value > max) max = value;
            }
        }
        double floor = max - DYNAMIC_RANGE_DB;
        float[][] out = new float[N_MELS][frames];
        for (int m = 0; m < N_MELS; m++) {
            for (int t = 0; t < frames; t++) out[m][t] = (float) ((Math.max(logMel[m][t], floor) + 4.0) / 4.0);
        }
        return out;
    }

    /** The 80 × 201 Slaney mel filterbank (librosa's {@code mel(sr=16000, n_fft=400, n_mels=80)}), computed once. */
    @NonNull
    static float[][] filters() {
        float[][] cached = filters;
        if (cached != null) return cached;
        double[] fftFreqs = new double[N_FREQS];
        for (int f = 0; f < N_FREQS; f++) fftFreqs[f] = (SAMPLE_RATE / 2.0) * f / (N_FREQS - 1);
        double melMax = hzToMel(SAMPLE_RATE / 2.0);
        double[] melHz = new double[N_MELS + 2];
        for (int i = 0; i < melHz.length; i++) melHz[i] = melToHz(melMax * i / (N_MELS + 1));
        float[][] bank = new float[N_MELS][N_FREQS];
        for (int m = 0; m < N_MELS; m++) {
            double lowerWidth = melHz[m + 1] - melHz[m];
            double upperWidth = melHz[m + 2] - melHz[m + 1];
            double norm = 2.0 / (melHz[m + 2] - melHz[m]);
            for (int f = 0; f < N_FREQS; f++) {
                double lower = (fftFreqs[f] - melHz[m]) / lowerWidth;
                double upper = (melHz[m + 2] - fftFreqs[f]) / upperWidth;
                bank[m][f] = (float) (Math.max(0.0, Math.min(lower, upper)) * norm);
            }
        }
        filters = bank;
        return bank;
    }

    // Slaney's scale: linear below 1 kHz (200/3 mel per Hz), logarithmic above.
    private static final double MEL_LINEAR_HZ = 200.0 / 3.0;
    private static final double MIN_LOG_HZ = 1000.0;
    private static final double MIN_LOG_MEL = MIN_LOG_HZ / MEL_LINEAR_HZ;
    private static final double LOG_STEP = Math.log(6.4) / 27.0;

    static double hzToMel(double hz) {
        if (hz >= MIN_LOG_HZ) return MIN_LOG_MEL + Math.log(hz / MIN_LOG_HZ) / LOG_STEP;
        return hz / MEL_LINEAR_HZ;
    }

    static double melToHz(double mel) {
        if (mel >= MIN_LOG_MEL) return MIN_LOG_HZ * Math.exp(LOG_STEP * (mel - MIN_LOG_MEL));
        return MEL_LINEAR_HZ * mel;
    }

    /**
     * A 400-point DFT as 16 × 25: with n = n1·n2, input index n2·i1 + i2 and output index
     * k1 + n1·k2, {@code X[k1 + 16 k2] = Σ_i2 W400^(i2 k1) (Σ_i1 x[25 i1 + i2] W16^(i1 k1)) W25^(i2 k2)}.
     * Only the 201 non-negative-frequency power values are produced.
     */
    static final class Dft400 {
        private static final int N1 = 16;
        private static final int N2 = 25;
        private final double[] cos400 = new double[N_FFT];
        private final double[] sin400 = new double[N_FFT];
        private final double[] cos25 = new double[N2];
        private final double[] sin25 = new double[N2];
        private final double[] cos16 = new double[N1];
        private final double[] sin16 = new double[N1];
        private final double[] innerRe = new double[N1];
        private final double[] innerIm = new double[N1];
        private final double[] twiddledRe = new double[N_FFT];
        private final double[] twiddledIm = new double[N_FFT];

        Dft400() {
            for (int k = 0; k < N_FFT; k++) {
                cos400[k] = Math.cos(2.0 * Math.PI * k / N_FFT);
                sin400[k] = Math.sin(2.0 * Math.PI * k / N_FFT);
            }
            for (int k = 0; k < N2; k++) {
                cos25[k] = Math.cos(2.0 * Math.PI * k / N2);
                sin25[k] = Math.sin(2.0 * Math.PI * k / N2);
            }
            for (int k = 0; k < N1; k++) {
                cos16[k] = Math.cos(2.0 * Math.PI * k / N1);
                sin16[k] = Math.sin(2.0 * Math.PI * k / N1);
            }
        }

        /** {@code |X[k]|²} for k in [0, 201) of the real 400-sample {@code frame}. */
        void powerSpectrum(@NonNull double[] frame, @NonNull double[] power) {
            for (int i2 = 0; i2 < N2; i2++) {
                for (int i1 = 0; i1 < N1; i1++) {
                    innerRe[i1] = frame[N2 * i1 + i2];
                    innerIm[i1] = 0.0;
                }
                fft16(innerRe, innerIm);
                for (int k1 = 0; k1 < N1; k1++) {
                    int tw = (i2 * k1) % N_FFT;
                    double c = cos400[tw], s = sin400[tw];
                    double re = innerRe[k1], im = innerIm[k1];
                    // (re + i·im)(c − i·s)
                    twiddledRe[k1 * N2 + i2] = re * c + im * s;
                    twiddledIm[k1 * N2 + i2] = im * c - re * s;
                }
            }
            for (int k1 = 0; k1 < N1; k1++) {
                for (int k2 = 0; k2 < N2; k2++) {
                    int k = k1 + N1 * k2;
                    if (k >= N_FREQS) continue;
                    double sumRe = 0.0, sumIm = 0.0;
                    for (int i2 = 0; i2 < N2; i2++) {
                        int tw = (i2 * k2) % N2;
                        double c = cos25[tw], s = sin25[tw];
                        double re = twiddledRe[k1 * N2 + i2], im = twiddledIm[k1 * N2 + i2];
                        sumRe += re * c + im * s;
                        sumIm += im * c - re * s;
                    }
                    power[k] = sumRe * sumRe + sumIm * sumIm;
                }
            }
        }

        /** In-place radix-2 decimation-in-time FFT of 16 complex points. */
        private void fft16(@NonNull double[] re, @NonNull double[] im) {
            for (int i = 0; i < N1; i++) {
                int j = Integer.reverse(i) >>> 28;
                if (j > i) {
                    double t = re[i]; re[i] = re[j]; re[j] = t;
                    t = im[i]; im[i] = im[j]; im[j] = t;
                }
            }
            for (int size = 2; size <= N1; size <<= 1) {
                int half = size >> 1;
                int step = N1 / size;
                for (int start = 0; start < N1; start += size) {
                    for (int k = 0; k < half; k++) {
                        double c = cos16[k * step], s = sin16[k * step];
                        int a = start + k, b = a + half;
                        double xr = re[b] * c + im[b] * s;
                        double xi = im[b] * c - re[b] * s;
                        re[b] = re[a] - xr;
                        im[b] = im[a] - xi;
                        re[a] += xr;
                        im[a] += xi;
                    }
                }
            }
        }
    }
}
