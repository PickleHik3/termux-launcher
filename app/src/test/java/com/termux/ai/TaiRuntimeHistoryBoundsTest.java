package com.termux.ai;

import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Runtime history is bounded: oldest entries go past the cap, and a deleted model takes its entries along. */
public class TaiRuntimeHistoryBoundsTest {

    private static JSONObject entry(String modelId, long updatedAtMs) throws Exception {
        return new JSONObject().put("modelId", modelId).put("updatedAtMs", updatedAtMs);
    }

    @Test
    public void capDropsTheOldestByUpdatedAt() throws Exception {
        JSONObject history = new JSONObject();
        for (int i = 0; i < TaiRuntimeHistory.MAX_ENTRIES + 5; i++) {
            // Older as i grows, so the last five inserted are the oldest.
            history.put("k" + i, entry("m" + i, 100_000L - i));
        }
        int dropped = TaiRuntimeHistory.cap(history, TaiRuntimeHistory.MAX_ENTRIES);
        assertEquals(5, dropped);
        assertEquals(TaiRuntimeHistory.MAX_ENTRIES, history.length());
        int last = TaiRuntimeHistory.MAX_ENTRIES + 4;
        for (int i = last - 4; i <= last; i++) assertFalse(history.has("k" + i));
        assertTrue(history.has("k0"));
    }

    @Test
    public void capLeavesASmallHistoryAlone() throws Exception {
        JSONObject history = new JSONObject().put("a", entry("m", 1L));
        assertEquals(0, TaiRuntimeHistory.cap(history, TaiRuntimeHistory.MAX_ENTRIES));
        assertEquals(1, history.length());
    }

    @Test
    public void removeModelDropsEveryKindOfEntryForThatModelOnly() throws Exception {
        JSONObject history = new JSONObject()
            .put("m|dev|gpu", entry("m", 1L))
            .put("load|m|dev|mnn_llm|gpu|4096", entry("m", 2L))
            .put("audio_input|m-audio|dev", entry("m-audio", 3L))
            .put("system_role|m", entry("m", 4L))
            .put("other|dev|gpu", entry("other", 5L))
            .put("other-text|dev|cpu", entry("other-text", 6L));
        int removed = TaiRuntimeHistory.removeModel(history, "m");
        assertEquals(4, removed);
        assertEquals(2, history.length());
        assertTrue(history.has("other|dev|gpu"));
        assertTrue(history.has("other-text|dev|cpu"));
    }

    @Test
    public void removeModelOfAnUnknownIdChangesNothing() throws Exception {
        JSONObject history = new JSONObject().put("a|dev|gpu", entry("a", 1L));
        assertEquals(0, TaiRuntimeHistory.removeModel(history, "zzz"));
        assertEquals(1, history.length());
    }
}
