package com.termux.ai;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** WAV and raw PCM16 reading, downmix and the linear resampler. */
public class WhisperAudioTest {

    @Test
    public void readsTheFixtureWav() throws IOException {
        float[] audio = WhisperAudio.decodeWav(WhisperMelTest.readBytes("whisper/git_status_16k.wav"));
        assertEquals(23_718, audio.length);
        float peak = 0f;
        for (float sample : audio) peak = Math.max(peak, Math.abs(sample));
        assertTrue(peak > 0.3f && peak <= 1f);
    }

    @Test
    public void stereoAtAnotherRateIsDownmixedAndResampled() throws IOException {
        // 8 kHz stereo, 800 frames: left 16384 (0.5), right −16384 (−0.5) → mono 0.
        byte[] wav = wav(8000, 2, 16, pcm16Frames(800, (short) 16384, (short) -16384));
        float[] audio = WhisperAudio.decodeWav(wav);
        assertEquals(1600, audio.length);
        for (float sample : audio) assertEquals(0f, sample, 0f);

        byte[] mono = wav(8000, 1, 16, pcm16Frames(800, (short) 16384));
        float[] up = WhisperAudio.decodeWav(mono);
        assertEquals(1600, up.length);
        assertEquals(0.5f, up[0], 1e-6f);
        assertEquals(0.5f, up[1599], 1e-6f);
    }

    @Test
    public void rawPcm16IsSixteenKilohertzMono() throws IOException {
        File file = Files.createTempFile("stt", ".pcm").toFile();
        try {
            Files.write(file.toPath(), new byte[] {0, 0x40, 0, (byte) 0xC0});
            assertArrayEquals(new float[] {0.5f, -0.5f}, WhisperAudio.read(file), 0f);
        } finally {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
        }
    }

    @Test
    public void unsupportedEncodingsAreRefusedWithAReason() {
        try {
            WhisperAudio.decodeWav(wav(16000, 1, 8, new byte[] {1, 2, 3, 4}));
            fail();
        } catch (IOException expected) {
            assertTrue(expected.getMessage().contains("16-bit PCM"));
        }
    }

    @Test
    public void linearResamplingScalesTheLengthAndInterpolates() {
        float[] input = {0f, 1f, 2f, 3f, 4f, 5f};
        float[] down = WhisperAudio.resampleLinear(input, 48000, 16000);
        assertArrayEquals(new float[] {0f, 3f}, down, 0f);
        float[] up = WhisperAudio.resampleLinear(new float[] {0f, 1f}, 8000, 16000);
        assertArrayEquals(new float[] {0f, 0.5f, 1f, 1f}, up, 1e-6f);
    }

    static byte[] pcm16Frames(int frames, short... channels) {
        ByteBuffer buffer = ByteBuffer.allocate(frames * channels.length * 2).order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < frames; i++) {
            for (short channel : channels) buffer.putShort(channel);
        }
        return buffer.array();
    }

    static byte[] wav(int rate, int channels, int bits, byte[] data) {
        ByteBuffer buffer = ByteBuffer.allocate(44 + data.length).order(ByteOrder.LITTLE_ENDIAN);
        buffer.put("RIFF".getBytes()).putInt(36 + data.length).put("WAVE".getBytes());
        buffer.put("fmt ".getBytes()).putInt(16).putShort((short) 1).putShort((short) channels).putInt(rate)
            .putInt(rate * channels * bits / 8).putShort((short) (channels * bits / 8)).putShort((short) bits);
        buffer.put("data".getBytes()).putInt(data.length).put(data);
        return buffer.array();
    }
}
