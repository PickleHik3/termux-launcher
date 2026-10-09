package com.termux.app.terminal.inappkeyboard.voice;

import androidx.annotation.NonNull;

/**
 * The two blips the voice key plays, as PCM16 mono: a short soft sine with a linear fade at both
 * ends so it starts and stops without a click. Pure so the shape can be pinned in a JVM test;
 * {@link VoiceFeedback} plays it. The pitches follow Freestyle's cues (F4 on start, C4 on stop),
 * which read as "up" and "down" without being a system sound.
 */
public final class VoiceTone {

    public static final int SAMPLE_RATE = 44_100;
    public static final double START_HZ = 347.0;
    public static final double STOP_HZ = 255.0;
    public static final int DURATION_MS = 125;
    /** Soft: about −16 dBFS, so it never competes with music on the same stream. */
    public static final double GAIN = 0.16;
    public static final int FADE_MS = 10;

    private VoiceTone() {
    }

    @NonNull
    public static short[] start() {
        return sine(SAMPLE_RATE, START_HZ, DURATION_MS, GAIN, FADE_MS);
    }

    @NonNull
    public static short[] stop() {
        return sine(SAMPLE_RATE, STOP_HZ, DURATION_MS, GAIN, FADE_MS);
    }

    /**
     * The error cue: the stop pitch twice, short, with a gap — distinct from either blip without
     * being a third pitch to learn.
     */
    @NonNull
    public static short[] error() {
        short[] blip = sine(SAMPLE_RATE, STOP_HZ, 60, GAIN, FADE_MS);
        int gap = SAMPLE_RATE * 50 / 1000;
        short[] out = new short[blip.length * 2 + gap];
        System.arraycopy(blip, 0, out, 0, blip.length);
        System.arraycopy(blip, 0, out, blip.length + gap, blip.length);
        return out;
    }

    /**
     * {@code durationMs} of a {@code frequencyHz} sine at {@code gain} (0–1 of full scale), the
     * first and last {@code fadeMs} ramped linearly from and to silence.
     */
    @NonNull
    public static short[] sine(int sampleRate, double frequencyHz, int durationMs, double gain, int fadeMs) {
        int samples = sampleRate * durationMs / 1000;
        int fade = Math.min(sampleRate * fadeMs / 1000, samples / 2);
        short[] out = new short[samples];
        double step = 2.0 * Math.PI * frequencyHz / sampleRate;
        for (int i = 0; i < samples; i++) {
            double envelope = 1.0;
            if (fade > 0) {
                if (i < fade) envelope = i / (double) fade;
                else if (i >= samples - fade) envelope = (samples - 1 - i) / (double) fade;
            }
            out[i] = (short) Math.round(Math.sin(step * i) * gain * envelope * Short.MAX_VALUE);
        }
        return out;
    }
}
