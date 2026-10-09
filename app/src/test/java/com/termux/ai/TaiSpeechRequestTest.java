package com.termux.ai;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** The speech request rules (voice, speed, format, limits), the voice table and the WAV writer. */
public class TaiSpeechRequestTest {

    private static TaiSpeechRequest parse(JSONObject body) {
        return TaiSpeechRequest.parse(body, TaiTtsVoices.ROSIE, 1.0f, 100);
    }

    @Test
    public void defaultsComeFromTheSettings() throws Exception {
        TaiSpeechRequest request = parse(new JSONObject().put("input", "  Hello world.  "));
        assertTrue(request.isValid());
        assertEquals("Hello world.", request.text);
        assertEquals(TaiTtsVoices.ROSIE, request.voice);
        assertEquals(1.0f, request.speed, 0f);
        assertEquals(TaiSpeechRequest.FORMAT_WAV, request.format);
        assertEquals("", request.model);
    }

    @Test
    public void textIsAcceptedAsAnAliasForInput() throws Exception {
        assertEquals("hi", parse(new JSONObject().put("text", "hi")).text);
    }

    @Test
    public void voicesByNameAnyCaseAndByOpenAiAlias() throws Exception {
        assertEquals(TaiTtsVoices.HUGO, parse(new JSONObject().put("input", "x").put("voice", "hugo")).voice);
        assertEquals(TaiTtsVoices.JASPER, parse(new JSONObject().put("input", "x").put("voice", "alloy")).voice);
        assertEquals(TaiTtsVoices.ROSIE, parse(new JSONObject().put("input", "x").put("voice", "nova")).voice);
        TaiSpeechRequest hidden = parse(new JSONObject().put("input", "x").put("voice", "Bella"));
        assertFalse(hidden.isValid());
        assertEquals("tts_voice_not_found", hidden.errorCode);
        assertEquals("voice", hidden.errorParam);
    }

    @Test
    public void speedIsClampedAndGarbageRefused() throws Exception {
        assertEquals(2.0f, parse(new JSONObject().put("input", "x").put("speed", 4.0)).speed, 0f);
        assertEquals(0.5f, parse(new JSONObject().put("input", "x").put("speed", 0.25)).speed, 0f);
        assertEquals(1.25f, parse(new JSONObject().put("input", "x").put("speed", "1.25")).speed, 0f);
        assertEquals(1.0f, parse(new JSONObject().put("input", "x").put("speed", 0)).speed, 0f);
        assertEquals("tts_speed_invalid", parse(new JSONObject().put("input", "x").put("speed", "fast")).errorCode);
    }

    @Test
    public void onlyWavAndPcmAreProduced() throws Exception {
        assertEquals(TaiSpeechRequest.FORMAT_PCM, parse(new JSONObject().put("input", "x").put("response_format", "PCM")).format);
        TaiSpeechRequest mp3 = parse(new JSONObject().put("input", "x").put("response_format", "mp3"));
        assertEquals("unsupported_response_format", mp3.errorCode);
        assertEquals(400, mp3.statusCode);
    }

    @Test
    public void emptyAndOverlongTextAreRefused() throws Exception {
        assertEquals("tts_input_missing", parse(new JSONObject().put("input", "   ")).errorCode);
        assertEquals("tts_input_missing", parse(new JSONObject()).errorCode);
        StringBuilder longText = new StringBuilder();
        for (int i = 0; i < 101; i++) longText.append('a');
        assertEquals("tts_input_too_long", parse(new JSONObject().put("input", longText.toString())).errorCode);
    }

    @Test
    public void openAiSpeechModelNamesMeanTheInstalledVoiceModel() {
        assertTrue(TaiSpeechRequest.isDefaultModelAlias(""));
        assertTrue(TaiSpeechRequest.isDefaultModelAlias("tts-1"));
        assertTrue(TaiSpeechRequest.isDefaultModelAlias("gpt-4o-mini-tts"));
        assertFalse(TaiSpeechRequest.isDefaultModelAlias(TaiModelCatalog.KITTEN_TTS_NANO_ID));
    }

    @Test
    public void voiceTableOffersFourVoicesWithTheirPriors() {
        assertArrayEquals(new String[] {"Bruno", "Hugo", "Jasper", "Rosie"}, TaiTtsVoices.VOICES);
        assertEquals("expr-voice-2-m", TaiTtsVoices.npyKey(TaiTtsVoices.JASPER));
        assertEquals("expr-voice-3-m", TaiTtsVoices.npyKey(TaiTtsVoices.BRUNO));
        assertEquals("expr-voice-4-m", TaiTtsVoices.npyKey(TaiTtsVoices.HUGO));
        assertEquals("expr-voice-4-f", TaiTtsVoices.npyKey(TaiTtsVoices.ROSIE));
        assertEquals(0.9f, TaiTtsVoices.speedPrior(TaiTtsVoices.HUGO), 0f);
        assertEquals(0.8f, TaiTtsVoices.speedPrior(TaiTtsVoices.ROSIE), 0f);
        assertNull(TaiTtsVoices.canonical("Leo"));
        assertEquals(TaiTtsVoices.JASPER, TaiTtsVoices.resolve(null, "not a voice"));
        assertEquals(1.0f, TaiTtsVoices.clampSpeed(Double.NaN), 0f);
    }

    @Test
    public void wavHeaderAndPcm16MatchTheRiffLayout() {
        byte[] header = TaiWav.header(48_000, 24_000);
        assertEquals(44, header.length);
        assertEquals("RIFF", new String(header, 0, 4, java.nio.charset.StandardCharsets.US_ASCII));
        assertEquals("WAVE", new String(header, 8, 4, java.nio.charset.StandardCharsets.US_ASCII));
        assertEquals("data", new String(header, 36, 4, java.nio.charset.StandardCharsets.US_ASCII));
        assertEquals(36 + 48_000, le32(header, 4));
        assertEquals(24_000, le32(header, 24));
        assertEquals(48_000, le32(header, 28));
        assertEquals(48_000, le32(header, 40));
        byte[] pcm = TaiWav.pcm16(new float[] {0f, 1f, -1f, 2f, 0.5f});
        assertArrayEquals(new byte[] {0, 0, (byte) 0xff, 0x7f, 0x01, (byte) 0x80, (byte) 0xff, 0x7f, 0x00, 0x40}, pcm);
    }

    private static int le32(byte[] b, int offset) {
        return (b[offset] & 0xff) | (b[offset + 1] & 0xff) << 8 | (b[offset + 2] & 0xff) << 16 | (b[offset + 3] & 0xff) << 24;
    }
}
