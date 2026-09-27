package com.termux.ai;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Where speech output sits in the runtime process: its own lane with stop on the control lane,
 * its place in the memory watch (given up after embeddings, before speech-to-text and chat), and
 * its capability keeping it out of chat and speech-to-text lists.
 */
public class TaiTtsWiringTest {
    private static final long MB = 1024L * 1024L;
    private static final long NOW = 10_000_000L;

    @Test
    public void speakAndSynthesizeRunOnTheTtsLaneAndStopOnTheControlLane() {
        assertTrue(TaiRuntimeService.isTtsOperation(TaiRuntimeIpc.OP_TTS_SPEAK));
        assertTrue(TaiRuntimeService.isTtsOperation(TaiRuntimeIpc.OP_TTS_SYNTHESIZE));
        assertTrue(TaiRuntimeService.isTtsOperation(TaiRuntimeIpc.OP_TTS_WARM));
        assertFalse(TaiRuntimeService.isTtsOperation(TaiRuntimeIpc.OP_TTS_STOP));
        assertTrue(TaiRuntimeService.isConcurrentControlOperation(TaiRuntimeIpc.OP_TTS_STOP));
        assertFalse(TaiRuntimeService.isSttOperation(TaiRuntimeIpc.OP_TTS_SPEAK));
        assertFalse(TaiRuntimeService.isTtsOperation(TaiRuntimeIpc.OP_TRANSCRIBE));
    }

    @Test
    public void anIdleVoiceIsGivenUpAfterEmbeddingsAndBeforeSpeechToText() {
        TaiResidency.Entry embedding = entry("embeddinggemma", TaiResidency.Kind.EMBEDDING, NOW - 1_000L);
        TaiResidency.Entry voice = entry(TaiModelCatalog.KITTEN_TTS_NANO_ID, TaiResidency.Kind.TTS, NOW - 60_000L);
        TaiResidency.Entry stt = entry("whisper-acft-base-en", TaiResidency.Kind.STT, NOW - 90_000L);
        List<TaiResidency.Entry> order = TaiPressureWatch.idleInEvictionOrder(Arrays.asList(stt, voice, embedding), false);
        assertEquals(Arrays.asList(embedding, voice, stt), order);
    }

    @Test
    public void anIdleVoiceUnloadsAfterItsOwnLimit() {
        assertEquals(TaiPressureWatch.TTS_IDLE_MS, TaiPressureWatch.idleLimitMs(TaiResidency.Kind.TTS));
        TaiResidency.Entry fresh = entry("fresh", TaiResidency.Kind.TTS, NOW - TaiPressureWatch.TTS_IDLE_MS + 1_000L);
        TaiResidency.Entry stale = entry("stale", TaiResidency.Kind.TTS, NOW - TaiPressureWatch.TTS_IDLE_MS - 1_000L);
        assertEquals(Collections.singletonList(stale), TaiPressureWatch.idleExpired(Arrays.asList(fresh, stale), NOW));
    }

    @Test
    public void aVoiceModelIsVoiceOnly() {
        LinkedHashSet<String> endpoint = TaiModelSpec.endpointCapabilitiesFor(TaiModelCatalog.KITTEN_TTS_NANO_ID,
            TaiModelSpec.BACKEND_LITERT_LM, TaiModelSpec.FORMAT_LITERTLM,
            new LinkedHashSet<>(Collections.singletonList(TaiModelSpec.CAPABILITY_TEXT_TO_SPEECH)),
            "/models/kittentts-nano-0.8/kitten_predictor.tflite");
        assertEquals(Collections.singleton(TaiModelSpec.CAPABILITY_TEXT_TO_SPEECH), endpoint);
    }

    private static TaiResidency.Entry entry(String id, TaiResidency.Kind kind, long lastUsedMs) {
        return new TaiResidency.Entry(id, kind, TaiModelSpec.BACKEND_LITERT_LM, "cpu", 0, 200L * MB, null, lastUsedMs, false);
    }
}
