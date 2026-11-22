package com.termux.ai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.json.JSONObject;
import org.junit.Test;

public class TaiDepthRunsTest {
    private static TaiResidency.Entry entry(TaiResidency.Kind kind, boolean busy) {
        return new TaiResidency.Entry("m", kind, "litert-lm", "cpu", 0, 1L, null, 0L, busy);
    }

    @Test
    public void aBusyChatOrImageModelBlocksTheGraph() {
        assertEquals("chat", TaiDepthRuns.busyReason(Arrays.asList(entry(TaiResidency.Kind.CHAT, true)), false));
        assertEquals("image", TaiDepthRuns.busyReason(Arrays.asList(entry(TaiResidency.Kind.IMAGE, true)), false));
        assertEquals("image", TaiDepthRuns.busyReason(Collections.<TaiResidency.Entry>emptyList(), true));
    }

    @Test
    public void idleResidentsAndBusyEmbeddingsDoNotBlock() {
        List<TaiResidency.Entry> residents = Arrays.asList(entry(TaiResidency.Kind.CHAT, false),
            entry(TaiResidency.Kind.IMAGE, false), entry(TaiResidency.Kind.EMBEDDING, true),
            entry(TaiResidency.Kind.RUNTIME, true));
        assertNull(TaiDepthRuns.busyReason(residents, false));
    }

    @Test
    public void outcomeSurvivesTheWire() throws Exception {
        TaiDepthRuns.Outcome failed = TaiDepthRuns.Outcome.failure(DepthMaker.CODE_CANCELLED, "Cancelled.");
        TaiDepthRuns.Outcome back = TaiDepthRuns.Outcome.fromJson(new JSONObject(failed.toJson().toString()));
        assertFalse(back.ok);
        assertTrue(back.cancelled);
        assertEquals("cancelled", back.code);
        assertEquals(TaiDepthRuns.CODE_INCOMPLETE, TaiDepthRuns.Outcome.fromJson(null).code);
        assertTrue(TaiDepthRuns.Outcome.fromJson(new JSONObject().put("ok", true)).ok);
    }
}
