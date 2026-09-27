package com.termux.ai;

import androidx.annotation.NonNull;

/**
 * The two audio shapes speech output hands out: raw PCM16 little-endian mono (OpenAI's
 * {@code response_format: "pcm"}) and the same samples behind a 44-byte RIFF header ({@code wav}).
 * Float samples from the vocoder are clipped to [-1, 1] and rounded, as the prototype's
 * {@code write_wav} does (NumPy rounds halves to even, Java away from zero, so the two can differ
 * by one step on an exact half).
 */
final class TaiWav {
    static final int HEADER_BYTES = 44;

    private TaiWav() {}

    /** {@code count} samples from {@code samples[offset]} as PCM16 LE into {@code out[outOffset]}. */
    static void floatToPcm16(@NonNull float[] samples, int offset, int count, @NonNull byte[] out, int outOffset) {
        for (int i = 0; i < count; i++) {
            float value = samples[offset + i];
            if (value > 1f) value = 1f;
            else if (value < -1f) value = -1f;
            else if (Float.isNaN(value)) value = 0f;
            int pcm = Math.round(value * 32767f);
            out[outOffset + 2 * i] = (byte) pcm;
            out[outOffset + 2 * i + 1] = (byte) (pcm >> 8);
        }
    }

    @NonNull
    static byte[] pcm16(@NonNull float[] samples) {
        byte[] out = new byte[samples.length * 2];
        floatToPcm16(samples, 0, samples.length, out, 0);
        return out;
    }

    /** The RIFF/WAVE header for {@code dataBytes} of mono PCM16 at {@code sampleRate}. */
    @NonNull
    static byte[] header(int dataBytes, int sampleRate) {
        byte[] h = new byte[HEADER_BYTES];
        ascii(h, 0, "RIFF");
        le32(h, 4, 36 + dataBytes);
        ascii(h, 8, "WAVE");
        ascii(h, 12, "fmt ");
        le32(h, 16, 16);
        le16(h, 20, 1);              // PCM
        le16(h, 22, 1);              // mono
        le32(h, 24, sampleRate);
        le32(h, 28, sampleRate * 2); // byte rate
        le16(h, 32, 2);              // block align
        le16(h, 34, 16);             // bits per sample
        ascii(h, 36, "data");
        le32(h, 40, dataBytes);
        return h;
    }

    private static void ascii(@NonNull byte[] out, int offset, @NonNull String text) {
        for (int i = 0; i < text.length(); i++) out[offset + i] = (byte) text.charAt(i);
    }

    private static void le16(@NonNull byte[] out, int offset, int value) {
        out[offset] = (byte) value;
        out[offset + 1] = (byte) (value >> 8);
    }

    private static void le32(@NonNull byte[] out, int offset, int value) {
        out[offset] = (byte) value;
        out[offset + 1] = (byte) (value >> 8);
        out[offset + 2] = (byte) (value >> 16);
        out[offset + 3] = (byte) (value >> 24);
    }
}
