package com.termux.app.terminal.inappkeyboard.voice;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import com.termux.ai.TaiModelSpec;

import org.junit.Test;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;

/**
 * The pure half of {@link LocalTaiVoiceTextPolisher#resolveModelId(String, Map, TaiModelSpec,
 * TaiModelSpec, boolean)}: a named "Cleanup model" preference wins when it is still installed;
 * otherwise the automatic Gemma rule (E2B when installed, else E4B), whatever the RAM check says.
 */
public class LocalTaiVoiceTextPolisherResolutionTest {

    private static TaiModelSpec chatModel(String id) {
        return new TaiModelSpec(id, id, "role", "test", null, "test", 100L,
            new LinkedHashSet<>(Collections.singleton(TaiModelSpec.CAPABILITY_TEXT_CHAT)), false);
    }

    private static Map<String, TaiModelSpec> installed(TaiModelSpec... specs) {
        Map<String, TaiModelSpec> map = new LinkedHashMap<>();
        for (TaiModelSpec spec : specs) map.put(spec.id, spec);
        return map;
    }

    @Test
    public void aPreferredModelStillInstalledWins() {
        TaiModelSpec preferred = chatModel("some-chat-model");
        TaiModelSpec e2b = chatModel("gemma-4-e2b-it-litert-lm");
        assertEquals("some-chat-model", LocalTaiVoiceTextPolisher.resolveModelId(
            "some-chat-model", installed(preferred, e2b), null, e2b, true));
    }

    @Test
    public void e4bPickedByNameStillWinsOverE2b() {
        TaiModelSpec e4b = chatModel("gemma-4-e4b-it-litert-lm");
        TaiModelSpec e2b = chatModel("gemma-4-e2b-it-litert-lm");
        assertEquals("gemma-4-e4b-it-litert-lm", LocalTaiVoiceTextPolisher.resolveModelId(
            "gemma-4-e4b-it-litert-lm", installed(e4b, e2b), e4b, e2b, false));
    }

    @Test
    public void aPreferredModelNoLongerInstalledFallsBackToAutomatic() {
        TaiModelSpec e2b = chatModel("gemma-4-e2b-it-litert-lm");
        assertEquals("gemma-4-e2b-it-litert-lm", LocalTaiVoiceTextPolisher.resolveModelId(
            "gone-model", installed(e2b), null, e2b, true));
    }

    @Test
    public void emptyOrNullPreferredIsAutomatic() {
        TaiModelSpec e2b = chatModel("gemma-4-e2b-it-litert-lm");
        assertEquals("gemma-4-e2b-it-litert-lm",
            LocalTaiVoiceTextPolisher.resolveModelId("", installed(e2b), null, e2b, false));
        assertEquals("gemma-4-e2b-it-litert-lm",
            LocalTaiVoiceTextPolisher.resolveModelId(null, installed(e2b), null, e2b, false));
    }

    @Test
    public void automaticPrefersE2bEvenWhenE4bWouldFit() {
        TaiModelSpec e4b = chatModel("gemma-4-e4b-it-litert-lm");
        TaiModelSpec e2b = chatModel("gemma-4-e2b-it-litert-lm");
        assertEquals("gemma-4-e2b-it-litert-lm",
            LocalTaiVoiceTextPolisher.resolveModelId("", installed(e4b, e2b), e4b, e2b, true));
        assertEquals("gemma-4-e2b-it-litert-lm",
            LocalTaiVoiceTextPolisher.resolveModelId("", installed(e4b, e2b), e4b, e2b, false));
    }

    @Test
    public void automaticFallsBackToE4bWhenE2bIsAbsent() {
        TaiModelSpec e4b = chatModel("gemma-4-e4b-it-litert-lm");
        assertEquals("gemma-4-e4b-it-litert-lm",
            LocalTaiVoiceTextPolisher.resolveModelId("", installed(e4b), e4b, null, false));
    }

    @Test
    public void nothingInstalledResolvesToNull() {
        assertNull(LocalTaiVoiceTextPolisher.resolveModelId("", installed(), null, null, false));
    }
}
