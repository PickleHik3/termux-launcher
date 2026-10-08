package com.termux.ai;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.List;

import static org.junit.Assert.assertEquals;

/** The evidence as {@link TaiEvidenceFiles} reads it from this phone's files. */
@RunWith(RobolectricTestRunner.class)
public class TaiEvidenceFilesTest {
    private static final String MODEL = "user-chat";
    private static final String MNN = TaiModelSpec.BACKEND_MNN_LLM;

    private Context context;
    private int nextId;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
    }

    private static JSONObject series(double median) throws Exception {
        return new JSONObject().put("med", median).put("min", median).put("max", median).put("runs", 1);
    }

    /** A complete, passing bench record of {@link #MODEL} on MNN, measured on {@code runtimeVersion}. */
    private JSONObject benchRecord(String accelerator, double decodeTps, String runtimeVersion) throws Exception {
        JSONObject phases = new JSONObject()
            .put("load", new JSONObject().put("ms", 3200L).put("memBytes", 1_600_000_000L))
            .put("chat", new JSONObject().put("ttftMs", series(300.0)).put("decodeTps", series(decodeTps))
                .put("tokens", 231).put("reply", "An alias is a shortcut."))
            .put("longInput", new JSONObject().put("readMs", series(4_000.0)).put("promptTokens", 2600)
                .put("truncated", false));
        return new JSONObject()
            .put("id", "r" + (nextId++))
            .put("benchVersion", TaiBenchSuite.BENCH_VERSION)
            .put("preset", "standard")
            .put("timestamp", 1_700_000_000_000L + nextId)
            .put("modelId", MODEL)
            .put("displayName", MODEL)
            .put("backend", MNN)
            .put("accelerator", accelerator)
            .put("speculative", false)
            .put("runtimeVersion", runtimeVersion)
            .put("appVersion", "1.0.0")
            .put("phases", phases)
            .put("check", new JSONObject().put("passed", 3).put("total", 3))
            .put("status", TaiBenchStore.STATUS_COMPLETE);
    }

    // ------------------------------------------------------------------------------ chat bench

    @Test
    public void aBenchOfAnotherRuntimeVersionNoLongerCounts() throws Exception {
        TaiBenchStore bench = TaiBenchStore.in(context.getFilesDir());
        bench.append(benchRecord("gpu", 20.0, MnnTaiRuntime.RUNTIME_VERSION));
        bench.append(benchRecord("cpu", 30.0, "0.0.1"));
        List<TaiEvidence.ChatResult> rows = new TaiEvidenceFiles(context).chatBench(MODEL, MNN);
        assertEquals(1, rows.size());
        assertEquals("gpu", rows.get(0).accelerator);
        // A row that recorded no runtime version is unknown, and unknown is stale.
        bench.append(benchRecord("gpu", 25.0, ""));
        assertEquals(0, new TaiEvidenceFiles(context).chatBench(MODEL, MNN).size());
    }
}
