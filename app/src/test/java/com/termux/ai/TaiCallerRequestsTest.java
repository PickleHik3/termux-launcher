package com.termux.ai;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/** The rules the feature callers share: remote routing, the private flag, the category body, the load decision. */
public class TaiCallerRequestsTest {

    @Test
    public void onlyTheRemotePrefixRoutesToTheProvider() throws Exception {
        assertTrue(TaiCallerRequests.isRemoteRequest("{\"model\":\"remote/gpt-x\"}"));
        assertTrue(TaiCallerRequests.isRemoteRequest(new JSONObject().put("model", "remote/a/b")));
        assertFalse(TaiCallerRequests.isRemoteRequest("{\"model\":\"gemma-4-e2b-it-litert-lm\"}"));
        assertFalse(TaiCallerRequests.isRemoteRequest("{\"model\":\"my-remote/x\"}"));
        assertFalse(TaiCallerRequests.isRemoteRequest("{\"model\":\"remote\"}"));
        assertFalse(TaiCallerRequests.isRemoteRequest("{\"messages\":[]}"));
        assertFalse(TaiCallerRequests.isRemoteRequest("not json"));
        assertFalse(TaiCallerRequests.isRemoteRequest((String) null));
        assertTrue(TaiCallerRequests.isRemoteModel("remote/x"));
        assertFalse(TaiCallerRequests.isRemoteModel(null));
    }

    @Test
    public void thePrivateFlagIsReadAndStripped() throws Exception {
        JSONObject request = new JSONObject().put("model", "m").put(TaiCallerRequests.NO_SYSTEM_PROMPT, true);
        assertTrue(TaiCallerRequests.wantsNoSystemPrompt(request));
        assertFalse(TaiCallerRequests.wantsNoSystemPrompt(new JSONObject().put("model", "m")));
        assertFalse(TaiCallerRequests.wantsNoSystemPrompt(new JSONObject().put(TaiCallerRequests.NO_SYSTEM_PROMPT, false)));
        TaiCallerRequests.stripPrivateFlags(request);
        assertFalse(request.has(TaiCallerRequests.NO_SYSTEM_PROMPT));
        assertEquals("m", request.getString("model"));
    }

    @Test
    public void theRemoteBodyLosesTheFlagAndTheLocalOnlyFieldsButKeepsTheRest() throws Exception {
        JSONObject request = new JSONObject().put("model", "remote/big").put("temperature", 0).put("max_tokens", 24)
            .put(TaiCallerRequests.NO_SYSTEM_PROMPT, true).put("thinking", false).put("speculative_decoding", true)
            .put("accelerator", "gpu").put("load_class", "momentary");
        JSONObject body = new JSONObject(TaiCallerRequests.remoteBody(request));
        assertFalse(body.has(TaiCallerRequests.NO_SYSTEM_PROMPT));
        assertFalse(body.has("thinking"));
        assertFalse(body.has("speculative_decoding"));
        assertFalse(body.has("accelerator"));
        assertFalse(body.has("load_class"));
        assertEquals("remote/big", body.getString("model"));
        assertEquals(24, body.getInt("max_tokens"));
        // The original is untouched.
        assertTrue(request.has(TaiCallerRequests.NO_SYSTEM_PROMPT));
    }

    @Test
    public void theCategoryBodyTurnsThinkingOffNamesTheFeatureAndKeepsTheUserPromptOut() throws Exception {
        JSONObject body = TaiCallerRequests.categoryBody("gemma-4-e2b-it-litert-lm", "Assign this app", 24);
        assertEquals("gemma-4-e2b-it-litert-lm", body.getString("model"));
        assertFalse(body.getBoolean("thinking"));
        assertFalse(body.has("speculative_decoding"));
        assertFalse(body.has("context_window"));
        assertTrue(body.getBoolean(TaiCallerRequests.NO_SYSTEM_PROMPT));
        assertFalse(body.has("accelerator"));
        assertEquals("app_categories", body.getString(TaiCallerRequests.FUNCTION));
        assertSame(TaiFunction.APP_CATEGORIES, TaiCallerRequests.featureOf(body));
        assertEquals(24, body.getInt("max_tokens"));
        assertEquals(0, body.getInt("temperature"));
        assertFalse(body.getBoolean("stream"));
        assertFalse(body.has("context_window"));
        assertEquals("Assign this app", body.getJSONArray("messages").getJSONObject(0).getString("content"));
    }

    @Test
    public void theCategoryBodyForARemoteModelNamesNoFeatureAndNoModelWhenNoneIsGiven() throws Exception {
        JSONObject remote = TaiCallerRequests.categoryBody("remote/big", "p", 24);
        assertFalse(remote.has(TaiCallerRequests.FUNCTION));
        assertTrue(TaiCallerRequests.isRemoteRequest(remote));
        JSONObject none = TaiCallerRequests.categoryBody(null, "p", 24);
        assertFalse(none.has("model"));
        assertEquals("app_categories", none.getString(TaiCallerRequests.FUNCTION));
        assertFalse(none.getBoolean("thinking"));
        // The feature field never reaches the remote provider.
        assertFalse(new JSONObject(TaiCallerRequests.remoteBody(none)).has(TaiCallerRequests.FUNCTION));
        assertNull(TaiCallerRequests.featureOf(new JSONObject().put(TaiCallerRequests.FUNCTION, "nonsense")));
    }

    /**
     * A chat, completion, load or keep-warm request that names no feature is the assistant's, so the
     * assistant's pick and plan reach {@code /v1}. A momentary load, and every other route, keeps the
     * settings' options; a named feature always wins.
     */
    @Test
    public void noFeatureOnAChatRouteMeansTheAssistant() throws Exception {
        JSONObject plain = new JSONObject().put("messages", new org.json.JSONArray());
        assertSame(TaiFunction.ASSISTANT, TaiCallerRequests.featureFor(plain, true));
        assertSame(TaiFunction.ASSISTANT, TaiCallerRequests.featureFor(new JSONObject().put("model", "other"), true));
        assertNull(TaiCallerRequests.featureFor(plain, false));
        assertNull(TaiCallerRequests.featureFor(new JSONObject().put("load_class", "momentary"), true));
        assertSame(TaiFunction.TIDY_DICTATION,
            TaiCallerRequests.featureFor(new JSONObject().put(TaiCallerRequests.FUNCTION, "cleanup"), true));
        assertSame(TaiFunction.EMBEDDINGS,
            TaiCallerRequests.featureFor(new JSONObject().put(TaiCallerRequests.FUNCTION, "dawn_search"), false));
        // A name nobody knows is not a feature; on a chat route that is the assistant.
        assertSame(TaiFunction.ASSISTANT,
            TaiCallerRequests.featureFor(new JSONObject().put(TaiCallerRequests.FUNCTION, "nonsense"), true));
        assertNull(TaiCallerRequests.featureFor(null, true));
    }

    @Test
    public void aSortUnloadsWhatItLoadedAndNeverReloadsWhatWasThereBefore() {
        String e2b = "gemma-4-e2b-it-litert-lm";
        String e4b = "gemma-4-e4b-it-litert-lm";
        assertEquals(TaiCallerRequests.Restore.UNLOAD, TaiCallerRequests.restoreAfterSort(null, e2b));
        assertEquals(TaiCallerRequests.Restore.UNLOAD, TaiCallerRequests.restoreAfterSort(e4b, e2b));
        assertEquals(TaiCallerRequests.Restore.KEEP, TaiCallerRequests.restoreAfterSort(e2b, e2b));
        assertEquals(TaiCallerRequests.Restore.KEEP, TaiCallerRequests.restoreAfterSort(null, "remote/x"));
        assertEquals(TaiCallerRequests.Restore.KEEP, TaiCallerRequests.restoreAfterSort(e4b, null));
    }
}
