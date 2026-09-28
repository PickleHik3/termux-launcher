package com.termux.ai;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** The lines {@code tai benchmark} prints live, one per harness event that matters. */
public class TaiCliFormatterBenchTest {

    private static JSONObject event(String name) throws Exception {
        return new JSONObject().put("event", name).put("at", 1L);
    }

    @Test
    public void tokensAndPhaseStartsPrintNothing() throws Exception {
        assertNull(TaiCliFormatter.formatBenchEvent(event("token").put("text", "hi").put("tokens", 3)));
        assertNull(TaiCliFormatter.formatBenchEvent(event("phase_start").put("phase", "writing")));
        assertNull(TaiCliFormatter.formatBenchEvent(event("check_start").put("name", "json")));
    }

    @Test
    public void entryHeaderNamesModelBackendAndProcessor() throws Exception {
        JSONObject entry = new JSONObject().put("modelId", "qwen3-vl-2b-instruct-mnn")
            .put("backend", TaiModelSpec.BACKEND_MNN_LLM).put("accelerator", "cpu").put("speculative", false);
        String line = TaiCliFormatter.formatBenchEvent(event("entry_start").put("index", 0).put("total", 2).put("entry", entry));
        assertEquals("\n[1/2] qwen3-vl-2b-instruct-mnn · MNN · CPU\n", line);
        entry.put("speculative", true).put("accelerator", "gpu");
        assertTrue(TaiCliFormatter.formatBenchEvent(event("entry_start").put("index", 1).put("total", 2).put("entry", entry))
            .contains("GPU+draft"));
    }

    @Test
    public void phaseLinesCarryTheHeadlineFigures() throws Exception {
        assertEquals("  load        3.2 s, 1.5 GB used\n", TaiCliFormatter.formatBenchEvent(event("phase_done")
            .put("phase", "load").put("status", "ok")
            .put("metrics", new JSONObject().put("ms", 3200L).put("memBytes", 1_610_612_736L))));
        assertEquals("  writing     21.3 tok/s (min 19.8, max 22.0), 128 tokens\n", TaiCliFormatter.formatBenchEvent(event("phase_done")
            .put("phase", "writing").put("status", "ok")
            .put("metrics", new JSONObject().put("med", 21.3).put("min", 19.8).put("max", 22.0).put("runs", 3).put("tokens", 128))));
        assertEquals("  first word  210 ms\n", TaiCliFormatter.formatBenchEvent(event("phase_done")
            .put("phase", "firstWord").put("status", "ok")
            .put("metrics", new JSONObject().put("med", 210.4).put("min", 210.4).put("max", 210.4).put("runs", 1))));
        assertEquals("  reading     2560 tok/s  [timed out]\n", TaiCliFormatter.formatBenchEvent(event("phase_done")
            .put("phase", "reading").put("status", "timeout")
            .put("metrics", new JSONObject().put("med", 2560.0).put("min", 2560.0).put("max", 2560.0).put("runs", 1))));
        assertEquals("  check       2/3 (failed: json got \"blue\")\n", TaiCliFormatter.formatBenchEvent(event("phase_done")
            .put("phase", "check").put("status", "ok")
            .put("metrics", new JSONObject().put("passed", 2).put("total", 3).put("details", new JSONArray()
                .put(new JSONObject().put("name", "arithmetic").put("passed", true).put("reply", "42"))
                .put(new JSONObject().put("name", "json").put("passed", false).put("reply", "blue"))
                .put(new JSONObject().put("name", "repeat").put("passed", true).put("reply", "pineapple"))))));
    }

    @Test
    public void verdictSkipAndDoneLines() throws Exception {
        JSONObject record = new JSONObject().put("status", "complete").put("verdict", "smooth")
            .put("phases", new JSONObject().put("writing", new JSONObject().put("med", 21.3)));
        assertEquals("  -> smooth (21.3 tok/s)\n", TaiCliFormatter.formatBenchEvent(event("entry_done").put("record", record)));
        record.put("status", "timeout");
        assertEquals("  -> smooth (21.3 tok/s), timeout\n", TaiCliFormatter.formatBenchEvent(event("entry_done").put("record", record)));
        assertEquals("", TaiCliFormatter.formatBenchEvent(event("entry_done").put("record",
            new JSONObject().put("status", "skipped:insufficient_memory"))));
        assertEquals("  skipped: Not enough free memory.\n", TaiCliFormatter.formatBenchEvent(event("skipped")
            .put("code", "insufficient_memory").put("reason", "Not enough free memory.")));
        assertEquals("\nDone: 3 entries, 1 skipped, stopped: cancelled. Results: tai benchmark --results\n",
            TaiCliFormatter.formatBenchEvent(event("done").put("entries", 3).put("skipped", new JSONArray().put(new JSONObject()))
                .put("stopped", "cancelled")));
        assertEquals("\nDone: 1 entries. Results: tai benchmark --results\n",
            TaiCliFormatter.formatBenchEvent(event("done").put("entries", 1).put("skipped", new JSONArray()).put("stopped", JSONObject.NULL)));
    }
}
