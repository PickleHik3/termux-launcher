package com.termux.app.terminal.inappkeyboard.voice;

/**
 * The pure math behind the level meter's bar height: decibels above the VAD's adaptive noise
 * floor, through a saturating curve, so ordinary speech fills most of the bar instead of the low
 * quarter a fixed −50…−10 dBFS window gave it. {@code AudioSource.VOICE_RECOGNITION} has no AGC,
 * so speech sits around −40…−25 dBFS; measuring from the floor instead of an absolute scale is
 * what makes the meter move. The curve is the one Freestyle's pill uses: floor-subtract, then
 * {@code ceiling · (1 − e^(−gain·x))}, "steep at the bottom so a whisper already reaches roughly
 * half height". 0 dB (at the floor) maps to 0; about 25 dB above it reaches ~90% of the ceiling.
 */
final class VoiceLevelCurve {
    /** {@code ln(10) / 25}: solves {@code 1 − e^(−gain·25) = 0.9}. */
    private static final double GAIN = 0.09210340371976184;

    private VoiceLevelCurve() {
    }

    /**
     * In [0, 1]. 0 when {@code rms} is at or below {@code noiseFloor}, or the floor is not a
     * usable positive value yet (the VAD has not seen a quiet frame).
     */
    static float level(float rms, float noiseFloor) {
        if (rms <= 0f || noiseFloor <= 0f) return 0f;
        double db = 20.0 * Math.log10(rms / (double) noiseFloor);
        if (db <= 0.0) return 0f;
        double raw = 1.0 - Math.exp(-GAIN * db);
        if (raw < 0.0) return 0f;
        if (raw > 1.0) return 1f;
        return (float) raw;
    }
}
