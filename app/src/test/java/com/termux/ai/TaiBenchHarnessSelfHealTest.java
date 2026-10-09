package com.termux.ai;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The benchmark self-heal's degenerate-reply detector and the check on a finished record that
 * decides whether an MNN entry looks like it hit a stale mmap weight cache (see
 * {@link TaiBenchHarness#runEntry}).
 */
public class TaiBenchHarnessSelfHealTest {

    @Test
    public void isDegenerateReply_trueForARepeatedCharacter() {
        assertTrue(TaiBenchHarness.isDegenerateReply("!!!!!!!!"));
        assertTrue(TaiBenchHarness.isDegenerateReply("!"));
    }

    @Test
    public void isDegenerateReply_trueForWhitespaceOnlyOrEmptyOrNull() {
        assertTrue(TaiBenchHarness.isDegenerateReply(""));
        assertTrue(TaiBenchHarness.isDegenerateReply("   \n\t "));
        assertTrue(TaiBenchHarness.isDegenerateReply(null));
    }

    @Test
    public void isDegenerateReply_falseForNormalText() {
        assertFalse(TaiBenchHarness.isDegenerateReply("Paris is the capital of France."));
        assertFalse(TaiBenchHarness.isDegenerateReply("42"));
    }

    @Test
    public void looksLikeStaleMmapCache_trueWhenEveryMnnCheckReplyIsDegenerate() throws JSONException {
        TaiBenchSuite.EntryPlan entry = new TaiBenchSuite.EntryPlan("qwen3-vl-2b", TaiModelSpec.BACKEND_MNN_LLM, "cpu", false);
        JSONObject record = recordWithCheck(2, 0, "!!!!", "");

        assertTrue(TaiBenchHarness.looksLikeStaleMmapCache(entry, record));
    }

    @Test
    public void looksLikeStaleMmapCache_falseWhenAnyReplyIsNotDegenerate() throws JSONException {
        TaiBenchSuite.EntryPlan entry = new TaiBenchSuite.EntryPlan("qwen3-vl-2b", TaiModelSpec.BACKEND_MNN_LLM, "cpu", false);
        JSONObject record = recordWithCheck(2, 0, "!!!!", "a wrong but real answer");

        assertFalse(TaiBenchHarness.looksLikeStaleMmapCache(entry, record));
    }

    @Test
    public void looksLikeStaleMmapCache_falseWhenTheChecksAllPassed() throws JSONException {
        TaiBenchSuite.EntryPlan entry = new TaiBenchSuite.EntryPlan("qwen3-vl-2b", TaiModelSpec.BACKEND_MNN_LLM, "cpu", false);
        JSONObject record = recordWithCheck(2, 2, "right", "right");

        assertFalse(TaiBenchHarness.looksLikeStaleMmapCache(entry, record));
    }

    @Test
    public void looksLikeStaleMmapCache_falseForNonMnnBackends() throws JSONException {
        TaiBenchSuite.EntryPlan entry = new TaiBenchSuite.EntryPlan("gemma-e2b", TaiModelSpec.BACKEND_LITERT_LM, "cpu", false);
        JSONObject record = recordWithCheck(2, 0, "!!!!", "");

        assertFalse(TaiBenchHarness.looksLikeStaleMmapCache(entry, record));
    }

    private static JSONObject recordWithCheck(int total, int passed, String... replies) throws JSONException {
        JSONArray details = new JSONArray();
        for (String reply : replies) {
            details.put(new JSONObject().put("reply", reply));
        }
        JSONObject check = new JSONObject().put("total", total).put("passed", passed).put("details", details);
        return new JSONObject().put("check", check);
    }
}
