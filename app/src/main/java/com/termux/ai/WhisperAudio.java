package com.termux.ai;

import androidx.annotation.NonNull;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;

/**
 * Reads the audio a transcription request points at into 16 kHz mono float samples: a RIFF/WAVE
 * file (16-bit PCM or 32-bit float, any channel count, any rate — other rates are resampled
 * linearly, which is plenty for speech going into an 8 kHz mel bank) or, without a RIFF header,
 * raw little-endian PCM16 at 16 kHz mono, the form the keyboard's capture will write.
 */
final class WhisperAudio {
    static final int SAMPLE_RATE = WhisperMel.SAMPLE_RATE;
    /** A 32 MiB cap keeps a stray upload from turning into a gigabyte of floats. */
    static final long MAX_FILE_BYTES = 32L * 1024L * 1024L;

    private static final int WAVE_FORMAT_PCM = 1;
    private static final int WAVE_FORMAT_IEEE_FLOAT = 3;
    private static final int WAVE_FORMAT_EXTENSIBLE = 0xFFFE;

    private WhisperAudio() {
    }

    /** {@code file} as 16 kHz mono samples in [-1, 1]. */
    @NonNull
    static float[] read(@NonNull File file) throws IOException {
        if (file.length() > MAX_FILE_BYTES) throw new IOException("Audio file is larger than " + (MAX_FILE_BYTES / (1024 * 1024)) + " MiB.");
        byte[] bytes = Files.readAllBytes(file.toPath());
        if (bytes.length >= 12 && bytes[0] == 'R' && bytes[1] == 'I' && bytes[2] == 'F' && bytes[3] == 'F'
            && bytes[8] == 'W' && bytes[9] == 'A' && bytes[10] == 'V' && bytes[11] == 'E') {
            return decodeWav(bytes);
        }
        return decodePcm16(bytes, 0, bytes.length, 1, SAMPLE_RATE);
    }

    @NonNull
    static float[] decodeWav(@NonNull byte[] bytes) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        int format = -1, channels = 0, rate = 0, bitsPerSample = 0;
        int dataOffset = -1, dataLength = 0;
        int offset = 12;
        while (offset + 8 <= bytes.length) {
            String id = new String(bytes, offset, 4, java.nio.charset.StandardCharsets.US_ASCII);
            int size = buffer.getInt(offset + 4);
            int body = offset + 8;
            if (size < 0 || body > bytes.length) break;
            if ("fmt ".equals(id) && size >= 16) {
                format = buffer.getShort(body) & 0xFFFF;
                channels = buffer.getShort(body + 2) & 0xFFFF;
                rate = buffer.getInt(body + 4);
                bitsPerSample = buffer.getShort(body + 14) & 0xFFFF;
                // WAVE_FORMAT_EXTENSIBLE carries the real format in the first two bytes of its GUID.
                if (format == WAVE_FORMAT_EXTENSIBLE && size >= 26) format = buffer.getShort(body + 24) & 0xFFFF;
            } else if ("data".equals(id)) {
                dataOffset = body;
                dataLength = Math.min(size, bytes.length - body);
                break;
            }
            // Chunks are word-aligned; an odd size is followed by one pad byte.
            offset = body + size + (size & 1);
        }
        if (format < 0 || dataOffset < 0) throw new IOException("WAV file has no fmt/data chunks.");
        if (channels <= 0 || rate <= 0) throw new IOException("WAV file declares no channels or sample rate.");
        float[] mono;
        if (format == WAVE_FORMAT_PCM && bitsPerSample == 16) {
            mono = decodePcm16(bytes, dataOffset, dataLength, channels, rate);
        } else if (format == WAVE_FORMAT_IEEE_FLOAT && bitsPerSample == 32) {
            int frames = dataLength / (4 * channels);
            mono = new float[frames];
            for (int i = 0; i < frames; i++) {
                float sum = 0f;
                for (int c = 0; c < channels; c++) sum += buffer.getFloat(dataOffset + (i * channels + c) * 4);
                mono[i] = sum / channels;
            }
        } else {
            throw new IOException("Unsupported WAV encoding (format " + format + ", " + bitsPerSample
                + "-bit); use 16-bit PCM or 32-bit float.");
        }
        return rate == SAMPLE_RATE ? mono : resampleLinear(mono, rate, SAMPLE_RATE);
    }

    /** Little-endian PCM16 frames of {@code channels}, downmixed to mono and resampled to 16 kHz. */
    @NonNull
    static float[] decodePcm16(@NonNull byte[] bytes, int offset, int length, int channels, int rate) {
        int frames = length / (2 * channels);
        float[] mono = new float[frames];
        for (int i = 0; i < frames; i++) {
            int sum = 0;
            for (int c = 0; c < channels; c++) {
                int index = offset + (i * channels + c) * 2;
                sum += (short) ((bytes[index] & 0xFF) | (bytes[index + 1] << 8));
            }
            mono[i] = sum / (32768f * channels);
        }
        return rate == SAMPLE_RATE ? mono : resampleLinear(mono, rate, SAMPLE_RATE);
    }

    /** Linear interpolation from {@code fromRate} to {@code toRate}; the length scales by the ratio. */
    @NonNull
    static float[] resampleLinear(@NonNull float[] input, int fromRate, int toRate) {
        if (fromRate == toRate || input.length == 0) return input;
        int outLength = (int) ((long) input.length * toRate / fromRate);
        float[] out = new float[outLength];
        double step = (double) fromRate / toRate;
        for (int i = 0; i < outLength; i++) {
            double position = i * step;
            int index = (int) position;
            double fraction = position - index;
            float a = input[Math.min(index, input.length - 1)];
            float b = input[Math.min(index + 1, input.length - 1)];
            out[i] = (float) (a + (b - a) * fraction);
        }
        return out;
    }
}
