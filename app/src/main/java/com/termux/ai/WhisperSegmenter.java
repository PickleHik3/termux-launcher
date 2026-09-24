package com.termux.ai;

import androidx.annotation.NonNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Cuts one audio buffer into the pieces the Whisper graph decodes, by energy alone:
 * <ul>
 *   <li>audio longer than the window is split at the quietest 30 ms frame within the last second
 *       before each window boundary, so a fixed-window cut lands in a pause rather than mid-word
 *       (the far-field check: window cuts split phrases);</li>
 *   <li>a piece with less than {@link #MIN_VOICED_SECONDS} of voiced audio is dropped, never
 *       decoded — a 0.1 s remainder produced invented text on both models;</li>
 *   <li>a piece whose speech starts or ends within {@link #PAD_SECONDS} of its edge gets silence
 *       added to make up the difference — tightly cut single words hallucinate without it.</li>
 * </ul>
 * A frame is voiced when its RMS is {@link #VOICE_OVER_FLOOR} (9 dB) above the piece's noise floor —
 * the 20th-percentile frame RMS, capped at {@link #NOISE_FLOOR_CAP} so a tightly cut clip that is
 * all speech does not take its own quietest syllables for noise — and above {@link #ABSOLUTE_FLOOR}
 * (about −54 dBFS) in any case.
 */
final class WhisperSegmenter {
    static final int SAMPLE_RATE = WhisperMel.SAMPLE_RATE;
    static final int FRAME_SAMPLES = SAMPLE_RATE * 30 / 1000;
    /** How far back from a window boundary the split point is searched for. */
    static final int SPLIT_SEARCH_SAMPLES = SAMPLE_RATE;
    static final double MIN_VOICED_SECONDS = 0.3;
    static final double PAD_SECONDS = 0.3;
    static final float VOICE_OVER_FLOOR = 2.8f;
    static final float ABSOLUTE_FLOOR = 0.002f;
    /** About −34 dBFS; a noise floor above this is speech being mistaken for noise. */
    static final float NOISE_FLOOR_CAP = 0.02f;

    private WhisperSegmenter() {
    }

    /** One piece of the input and where it came from, in samples of the source. */
    static final class Piece {
        @NonNull final float[] samples;
        final int start;
        final int end;

        Piece(@NonNull float[] samples, int start, int end) {
            this.samples = samples;
            this.start = start;
            this.end = end;
        }
    }

    /**
     * The pieces of {@code audio} to decode with a graph of {@code windowSamples}: split at pauses,
     * voiceless pieces dropped, each padded to at least {@link #PAD_SECONDS} of quiet at both ends.
     * Empty when nothing in the buffer is voiced.
     */
    @NonNull
    static List<Piece> pieces(@NonNull float[] audio, int windowSamples) {
        ArrayList<Piece> out = new ArrayList<>();
        int padSamples = (int) (PAD_SECONDS * SAMPLE_RATE);
        // Room is kept for the padding, so a padded piece never runs past the window and loses its tail.
        int effectiveWindow = Math.max(2 * FRAME_SAMPLES, windowSamples - 2 * padSamples);
        for (int[] bounds : splitPoints(audio, effectiveWindow)) {
            float[] piece = Arrays.copyOfRange(audio, bounds[0], bounds[1]);
            if (!hasVoice(piece)) continue;
            out.add(new Piece(padded(piece, padSamples), bounds[0], bounds[1]));
        }
        return out;
    }

    /** {@code [start, end)} sample ranges, each at most {@code windowSamples} long. */
    @NonNull
    static List<int[]> splitPoints(@NonNull float[] audio, int windowSamples) {
        ArrayList<int[]> ranges = new ArrayList<>();
        int length = audio.length;
        int start = 0;
        while (length - start > windowSamples) {
            int boundary = start + windowSamples;
            int cut = quietestFrameStart(audio, Math.max(start + FRAME_SAMPLES, boundary - SPLIT_SEARCH_SAMPLES), boundary);
            ranges.add(new int[] {start, cut});
            start = cut;
        }
        ranges.add(new int[] {start, length});
        return ranges;
    }

    /** The start of the 30 ms frame with the least energy in {@code [from, to)}; {@code to} when the range holds no whole frame. */
    static int quietestFrameStart(@NonNull float[] audio, int from, int to) {
        int best = to;
        double bestEnergy = Double.MAX_VALUE;
        for (int frameStart = from; frameStart + FRAME_SAMPLES <= to; frameStart += FRAME_SAMPLES) {
            double energy = 0.0;
            for (int i = frameStart; i < frameStart + FRAME_SAMPLES; i++) energy += (double) audio[i] * audio[i];
            if (energy < bestEnergy) {
                bestEnergy = energy;
                best = frameStart;
            }
        }
        return best;
    }

    /** Whether at least {@link #MIN_VOICED_SECONDS} of {@code piece} is voiced. */
    static boolean hasVoice(@NonNull float[] piece) {
        float[] rms = frameRms(piece);
        float threshold = voiceThreshold(rms);
        int voiced = 0;
        for (float value : rms) {
            if (value > threshold) voiced++;
        }
        return voiced * FRAME_SAMPLES >= MIN_VOICED_SECONDS * SAMPLE_RATE;
    }

    /**
     * {@code piece} with silence added so the first and last voiced frames sit at least
     * {@code padSamples} from the edges; unchanged when the recording already has that much quiet.
     */
    @NonNull
    static float[] padded(@NonNull float[] piece, int padSamples) {
        float[] rms = frameRms(piece);
        float threshold = voiceThreshold(rms);
        int firstVoiced = -1, lastVoiced = -1;
        for (int i = 0; i < rms.length; i++) {
            if (rms[i] > threshold) {
                if (firstVoiced < 0) firstVoiced = i;
                lastVoiced = i;
            }
        }
        if (firstVoiced < 0) return piece;
        int leadIn = firstVoiced * FRAME_SAMPLES;
        int tail = piece.length - (lastVoiced + 1) * FRAME_SAMPLES;
        // The partial frame after a voiced last whole frame is speech running to the edge, not quiet.
        if (lastVoiced == rms.length - 1) tail = 0;
        int prepend = Math.max(0, padSamples - leadIn);
        int append = Math.max(0, padSamples - tail);
        if (prepend == 0 && append == 0) return piece;
        float[] out = new float[prepend + piece.length + append];
        System.arraycopy(piece, 0, out, prepend, piece.length);
        return out;
    }

    /** RMS of each whole 30 ms frame of {@code piece}. */
    @NonNull
    static float[] frameRms(@NonNull float[] piece) {
        int frames = piece.length / FRAME_SAMPLES;
        float[] rms = new float[frames];
        for (int f = 0; f < frames; f++) {
            double energy = 0.0;
            int base = f * FRAME_SAMPLES;
            for (int i = 0; i < FRAME_SAMPLES; i++) energy += (double) piece[base + i] * piece[base + i];
            rms[f] = (float) Math.sqrt(energy / FRAME_SAMPLES);
        }
        return rms;
    }

    /** 9 dB over the piece's noise floor (its 20th-percentile frame, capped), never below the absolute floor. */
    static float voiceThreshold(@NonNull float[] rms) {
        if (rms.length == 0) return ABSOLUTE_FLOOR;
        float[] sorted = rms.clone();
        Arrays.sort(sorted);
        float noiseFloor = Math.min(NOISE_FLOOR_CAP, sorted[Math.min(sorted.length - 1, sorted.length / 5)]);
        return Math.max(noiseFloor * VOICE_OVER_FLOOR, ABSOLUTE_FLOOR);
    }
}
