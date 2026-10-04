package com.termux.ai;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Only a verdict on the accelerator is written down as its failure, and a written failure stops
 * counting after a week or a new app version. A cancelled load once demoted the GPU for good.
 */
public class TaiRuntimeResultVerdictTest {
    private static final long DAY = 24L * 60L * 60L * 1000L;
    private static final long NOW = 100L * DAY;

    @Test
    public void momentaryCodesAreNotVerdicts() {
        assertFalse(TaiRuntimeHistory.isRuntimeVerdict("model_load_cancelled"));
        assertFalse(TaiRuntimeHistory.isRuntimeVerdict("insufficient_memory"));
        assertFalse(TaiRuntimeHistory.isRuntimeVerdict("low_available_memory"));
        assertFalse(TaiRuntimeHistory.isRuntimeVerdict("low_available_memory_for_gpu"));
        assertFalse(TaiRuntimeHistory.isRuntimeVerdict("model_file_missing"));
        assertFalse(TaiRuntimeHistory.isRuntimeVerdict("model_file_not_readable"));
        assertFalse(TaiRuntimeHistory.isRuntimeVerdict("known_failed_accelerator"));
        assertFalse(TaiRuntimeHistory.isRuntimeVerdict("load_timeout"));
        assertFalse(TaiRuntimeHistory.isRuntimeVerdict(""));
        assertFalse(TaiRuntimeHistory.isRuntimeVerdict(null));
    }

    @Test
    public void initialisationFailuresAreVerdicts() {
        assertTrue(TaiRuntimeHistory.isRuntimeVerdict("litert_lm_load_failed"));
        assertTrue(TaiRuntimeHistory.isRuntimeVerdict("litert_lm_native_unavailable"));
        assertTrue(TaiRuntimeHistory.isRuntimeVerdict("gpu_init_failed"));
    }

    @Test
    public void aCancelledLoadResultIsNotRecorded() throws Exception {
        JSONObject cancelled = new JSONObject().put("ok", false).put("error", "model_load_cancelled")
            .put("message", "Model load cancelled.");
        JSONObject failed = new JSONObject().put("ok", false).put("error", "litert_lm_load_failed");
        JSONObject ok = new JSONObject().put("ok", true);
        assertFalse(TaiManager.shouldRecordFailure(cancelled));
        assertTrue(TaiManager.shouldRecordFailure(failed));
        assertFalse(TaiManager.shouldRecordFailure(ok));
    }

    @Test
    public void aFailureExpiresAfterAWeekOrANewVersion() throws Exception {
        JSONObject fresh = new JSONObject().put("updatedAtMs", NOW - DAY).put("appVersionCode", 7L);
        JSONObject old = new JSONObject().put("updatedAtMs", NOW - 8 * DAY).put("appVersionCode", 7L);
        JSONObject unversioned = new JSONObject().put("updatedAtMs", NOW - DAY);
        JSONObject unversionedOld = new JSONObject().put("updatedAtMs", NOW - 8 * DAY);
        assertFalse(TaiRuntimeHistory.isExpired(fresh, NOW, 7L));
        assertTrue(TaiRuntimeHistory.isExpired(old, NOW, 7L));
        assertTrue(TaiRuntimeHistory.isExpired(fresh, NOW, 8L));
        assertFalse(TaiRuntimeHistory.isExpired(unversioned, NOW, 8L));
        assertTrue(TaiRuntimeHistory.isExpired(unversionedOld, NOW, 8L));
        assertFalse(TaiRuntimeHistory.isExpired(fresh, NOW, 0L));
    }
}
