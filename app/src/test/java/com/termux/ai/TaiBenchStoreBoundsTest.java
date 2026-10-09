package com.termux.ai;

import org.json.JSONObject;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;

/** The results file stays bounded across every key, and a deleted model's records can be removed. */
public class TaiBenchStoreBoundsTest {

    @Rule public TemporaryFolder temp = new TemporaryFolder();

    private TaiBenchStore store() {
        return new TaiBenchStore(new File(new File(temp.getRoot(), "tai"), TaiBenchStore.FILE_NAME));
    }

    private static JSONObject record(String modelId, int seq) throws Exception {
        return new JSONObject().put("id", modelId + "-" + seq).put("modelId", modelId)
            .put("backend", "mnn_llm").put("accelerator", "cpu").put("speculative", false)
            .put("timestamp", 1_000L + seq);
    }

    @Test
    public void trimKeepsTheNewestFourHundredAcrossKeys() throws Exception {
        List<JSONObject> records = new ArrayList<>();
        // 30 keys of 15 records each: under the per-key cap, 450 in all.
        for (int seq = 0; seq < 15; seq++) {
            for (int key = 0; key < 30; key++) records.add(record("model-" + key, seq));
        }
        List<JSONObject> kept = TaiBenchStore.trim(records);
        assertEquals(TaiBenchStore.KEEP_TOTAL, kept.size());
        // Oldest first in the file, so the dropped 50 are the first ones.
        assertEquals(records.get(50).optString("id"), kept.get(0).optString("id"));
        assertEquals(records.get(449).optString("id"), kept.get(399).optString("id"));
    }

    @Test
    public void perKeyCapStillApplies() throws Exception {
        List<JSONObject> records = new ArrayList<>();
        for (int seq = 0; seq < TaiBenchStore.KEEP_PER_KEY + 7; seq++) records.add(record("only", seq));
        assertEquals(TaiBenchStore.KEEP_PER_KEY, TaiBenchStore.trim(records).size());
    }

    @Test
    public void removeModelDeletesItsRecordsFromTheFile() throws Exception {
        TaiBenchStore store = store();
        store.append(record("keep", 1));
        store.append(record("gone", 1));
        store.append(record("gone", 2));
        store.append(record("gone-vision", 3));
        assertEquals(3, store.removeModel("gone"));
        assertEquals(1, store.records().length());
        assertEquals("keep", store.records().getJSONObject(0).getString("modelId"));
        assertEquals(0, store.removeModel("gone"));
    }

    @Test
    public void withoutModelLeavesOtherModelsAlone() throws Exception {
        List<JSONObject> records = new ArrayList<>();
        records.add(record("a", 1));
        records.add(record("b", 1));
        List<JSONObject> kept = TaiBenchStore.withoutModel(records, "a");
        assertEquals(1, kept.size());
        assertEquals("b", kept.get(0).optString("modelId"));
    }
}
