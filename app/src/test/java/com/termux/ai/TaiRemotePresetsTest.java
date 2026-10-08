package com.termux.ai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/** The provider table: ids, addresses, what an old address reads as, and the model-list match. */
public class TaiRemotePresetsTest {

    @Test
    public void fourPresetsThenCustom_withDistinctIds() {
        assertEquals(5, TaiRemotePresets.ALL.size());
        assertTrue(TaiRemotePresets.ALL.get(4).isCustom());
        Set<String> ids = new HashSet<>();
        for (TaiRemotePresets.Preset preset : TaiRemotePresets.ALL) {
            assertTrue(preset.id, ids.add(preset.id));
            assertEquals(preset, TaiRemotePresets.byId(preset.id));
        }
        assertNull(TaiRemotePresets.byId("openai"));
        assertNull(TaiRemotePresets.byId(null));
    }

    @Test
    public void everyPresetAddressIsAllowedAndNormalized() {
        for (TaiRemotePresets.Preset preset : TaiRemotePresets.ALL) {
            if (preset.isCustom()) continue;
            assertEquals(preset.id, TaiRemoteClient.UrlVerdict.OK_ENCRYPTED, TaiRemoteClient.checkUrl(preset.baseUrl));
            assertEquals(preset.id, TaiRemoteClient.normalizeBaseUrl(preset.baseUrl), preset.baseUrl);
            assertFalse(preset.id, preset.model.isEmpty());
            assertTrue(preset.id, preset.keyPageUrl.startsWith("https://"));
            assertFalse(preset.id, preset.displayName.isEmpty());
        }
    }

    @Test
    public void privacyLine_onlyForGoogleAndMistral() {
        assertTrue(TaiRemotePresets.GOOGLE.freePlanTrains);
        assertTrue(TaiRemotePresets.MISTRAL.freePlanTrains);
        assertFalse(TaiRemotePresets.OPENROUTER.freePlanTrains);
        assertFalse(TaiRemotePresets.GROQ.freePlanTrains);
        assertFalse(TaiRemotePresets.CUSTOM.freePlanTrains);
    }

    @Test
    public void savedAddress_readsAsItsPresetIgnoringTrailingSlashAndCase() {
        assertEquals(TaiRemotePresets.GOOGLE,
            TaiRemotePresets.forBaseUrl("https://generativelanguage.googleapis.com/v1beta/openai/"));
        assertEquals(TaiRemotePresets.OPENROUTER, TaiRemotePresets.forBaseUrl(" https://OpenRouter.ai/api/v1 "));
        assertEquals(TaiRemotePresets.GROQ, TaiRemotePresets.forBaseUrl("https://api.groq.com/openai/v1"));
        assertEquals(TaiRemotePresets.MISTRAL, TaiRemotePresets.forBaseUrl("https://api.mistral.ai/v1//"));
    }

    @Test
    public void savedAddress_otherwiseCustom_orNothingWhenEmpty() {
        assertEquals(TaiRemotePresets.CUSTOM, TaiRemotePresets.forBaseUrl("https://api.openai.com/v1"));
        assertEquals(TaiRemotePresets.CUSTOM, TaiRemotePresets.forBaseUrl("https://openrouter.ai/api/v2"));
        assertEquals(TaiRemotePresets.CUSTOM, TaiRemotePresets.forBaseUrl("http://127.0.0.1:11434/v1"));
        assertNull(TaiRemotePresets.forBaseUrl(""));
        assertNull(TaiRemotePresets.forBaseUrl(null));
    }

    @Test
    public void listedModel_acceptsTheBareIdOrGeminisModelsPrefix() {
        assertEquals("llama-3.3-70b-versatile", TaiRemotePresets.listedModel(
            Arrays.asList("gemma2-9b-it", "llama-3.3-70b-versatile"), "llama-3.3-70b-versatile"));
        assertEquals("gemini-flash-lite-latest", TaiRemotePresets.listedModel(
            Arrays.asList("models/gemini-2.5-pro", "models/gemini-flash-lite-latest"), "gemini-flash-lite-latest"));
        assertNull(TaiRemotePresets.listedModel(Collections.singletonList("mistral-large-latest"), "mistral-small-latest"));
        assertNull(TaiRemotePresets.listedModel(Collections.singletonList("mistral-small-latest-2"), "mistral-small-latest"));
        assertNull(TaiRemotePresets.listedModel(Collections.emptyList(), "openrouter/free"));
        assertNull(TaiRemotePresets.listedModel(Collections.singletonList(""), ""));
    }
}
