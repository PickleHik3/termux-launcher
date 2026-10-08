package com.termux.app.fragments.settings.termux;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.termux.R;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

public class TaiRuntimeStatusTextTest {

    private static JSONObject status(JSONObject runtime) throws JSONException {
        return new JSONObject().put("runtime", runtime);
    }

    private static JSONObject loadedOnGpu() throws JSONException {
        return status(new JSONObject()
            .put("state", "loaded").put("loaded", true).put("activeGeneration", false)
            .put("loadedModelId", "gemma-3n-e2b").put("backend", "litert-lm").put("accelerator", "gpu")
            .put("idleUnloadRemainingMs", 125_000L).put("status", "Model loaded."));
    }

    private static JSONObject generating() throws JSONException {
        return status(new JSONObject()
            .put("state", "loaded").put("loaded", true).put("activeGeneration", true)
            .put("loadedModelId", "gemma-3n-e2b").put("backend", "litert-lm"));
    }

    private static JSONObject loading() throws JSONException {
        return status(new JSONObject().put("state", "loading").put("loaded", false));
    }

    private static JSONObject stopping() throws JSONException {
        return status(new JSONObject().put("state", "stopping").put("loaded", true));
    }

    private static JSONObject unloaded() throws JSONException {
        return status(new JSONObject().put("state", "unloaded").put("loaded", false)
            .put("loadedModelId", JSONObject.NULL));
    }

    @Test
    public void theHeadlineNamesWhatTheRuntimeIsDoing() throws JSONException {
        assertHeadline(R.string.termux_ai_status_loaded, true, loadedOnGpu());
        assertHeadline(R.string.termux_ai_status_generating, true, generating());
        assertHeadline(R.string.termux_ai_status_loading, true, loading());
        assertHeadline(R.string.termux_ai_status_stopping, true, stopping());
        assertHeadline(R.string.termux_ai_status_none, false, unloaded());
        assertHeadline(R.string.termux_ai_status_unavailable, false, null);
        assertHeadline(R.string.termux_ai_status_unavailable, false, new JSONObject());
    }

    private static void assertHeadline(int label, boolean active, JSONObject status) {
        TaiRuntimeStatusText.Headline headline = TaiRuntimeStatusText.headline(status);
        assertEquals(label, headline.label);
        assertEquals(active, headline.active);
    }

    @Test
    public void theBriefSaysWhatIsLoadedWhereAndWhenItUnloads() throws JSONException {
        assertEquals("gemma-3n-e2b · litert-lm · gpu\nUnloads in 2m 5s\nModel loaded.",
            TaiRuntimeStatusText.brief(loadedOnGpu()));
    }

    @Test
    public void keepingWarmWinsOverTheIdleCountdown() throws JSONException {
        JSONObject status = loadedOnGpu();
        status.getJSONObject("runtime").put("keepWarmRemainingMs", 30_000L).remove("status");
        assertEquals("gemma-3n-e2b · litert-lm · gpu\nKept warm for 30s", TaiRuntimeStatusText.brief(status));
    }

    @Test
    public void theBriefIsEmptyWhenNothingIsLoaded() throws JSONException {
        assertEquals("", TaiRuntimeStatusText.brief(unloaded()));
        assertEquals("", TaiRuntimeStatusText.brief(null));
    }

    @Test
    public void theBriefCarriesTheLastCrashAndItsFallback() throws JSONException {
        JSONObject status = unloaded().put("lastRuntimeCrash", new JSONObject()
            .put("modelId", "qwen3-4b").put("accelerator", "npu").put("suggestedFallback", "Try GPU."));
        assertEquals("AI runtime crashed while loading qwen3-4b on npu\nTry GPU.",
            TaiRuntimeStatusText.brief(status));
        JSONObject bare = unloaded().put("lastRuntimeCrash", new JSONObject());
        assertEquals("AI runtime crashed while loading a model\nTry CPU or a smaller model.",
            TaiRuntimeStatusText.brief(bare));
    }

    @Test
    public void pollingContinuesOnlyWhileSomethingWillChange() throws JSONException {
        assertTrue(TaiRuntimeStatusText.keepPolling(loadedOnGpu()));
        assertTrue(TaiRuntimeStatusText.keepPolling(generating()));
        assertFalse(TaiRuntimeStatusText.keepPolling(unloaded()));
        assertFalse(TaiRuntimeStatusText.keepPolling(null));
        JSONObject warm = unloaded();
        warm.getJSONObject("runtime").put("keepWarmRemainingMs", 1_000L);
        assertTrue(TaiRuntimeStatusText.keepPolling(warm));
    }

    @Test
    public void stopIsOfferedWhileARunOrALoadIsUnderWay() throws JSONException {
        assertTrue(TaiRuntimeStatusText.stopEnabled(generating()));
        assertTrue(TaiRuntimeStatusText.stopEnabled(loading()));
        assertFalse(TaiRuntimeStatusText.stopEnabled(loadedOnGpu()));
        assertFalse(TaiRuntimeStatusText.stopEnabled(unloaded()));
        assertFalse(TaiRuntimeStatusText.stopEnabled(null));
    }

    @Test
    public void unloadIsOfferedForAnIdleOrLoadingModel() throws JSONException {
        assertTrue(TaiRuntimeStatusText.unloadEnabled(loadedOnGpu()));
        assertTrue(TaiRuntimeStatusText.unloadEnabled(loading()));
        assertFalse("not while a reply is being written", TaiRuntimeStatusText.unloadEnabled(generating()));
        assertFalse("not while stopping", TaiRuntimeStatusText.unloadEnabled(stopping()));
        assertFalse(TaiRuntimeStatusText.unloadEnabled(unloaded()));
        assertFalse(TaiRuntimeStatusText.unloadEnabled(null));
    }
}
