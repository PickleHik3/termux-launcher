package com.termux.app.terminal.inappkeyboard.voice;

import androidx.annotation.NonNull;

/**
 * Brings a quiet segment up to a normal level before it is sent for transcription.
 * {@code VOICE_RECOGNITION} has no AGC, and speech from arm's length reaches pong at a −52 dBFS
 * peak: the runtime's own segmenter drops audio under −54 dBFS as voiceless, and Whisper was
 * trained on levelled audio. Peak normalisation keeps the segment's own signal-to-noise ratio,
 * so nothing is invented, only scaled.
 */
final class VoiceGain {
    /** The peak a segment is scaled to: −6 dBFS. */
    static final float TARGET_PEAK = 0.5f;
    /**
     * At most +40 dB, so a segment of near-silence is not blown up into hiss: pong's −52 dBFS
     * peak then lands at −12 dBFS, well inside what the runtime and Whisper expect.
     */
    static final float MAX_GAIN = 100f;

    private VoiceGain() {
    }

    /** The factor {@link #apply} scales by: 1 for a segment already at or over the target. */
    static float gainFor(@NonNull short[] pcm) {
        int peak = 0;
        for (short sample : pcm) peak = Math.max(peak, Math.abs((int) sample));
        if (peak == 0) return 1f;
        float gain = TARGET_PEAK * 32768f / peak;
        return Math.max(1f, Math.min(MAX_GAIN, gain));
    }

    /** {@code pcm} scaled in place by {@link #gainFor}; returns the gain used. */
    static float apply(@NonNull short[] pcm) {
        float gain = gainFor(pcm);
        if (gain == 1f) return gain;
        for (int i = 0; i < pcm.length; i++) {
            int value = Math.round(pcm[i] * gain);
            pcm[i] = (short) Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, value));
        }
        return gain;
    }
}
