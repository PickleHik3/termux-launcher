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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

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
        TaiRuntimeCrashMarker.clear(context);
    }

    private static TaiModelSpec spec(String id, String backend, String localPath) {
        String format = TaiModelSpec.BACKEND_LITERT_LM.equals(backend) ? TaiModelSpec.FORMAT_LITERTLM : TaiModelSpec.FORMAT_MNN;
        return new TaiModelSpec(id, id, "Test model", "test", localPath, "test", 123L,
            new java.util.LinkedHashSet<>(java.util.Collections.singleton(TaiModelSpec.CAPABILITY_TEXT_CHAT)),
            false, null, backend, format, null, null, 4096, 0, null);
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

    // ----------------------------------------------------------------------------- crash marker

    @Test
    public void aLoadTheRuntimeDiedInCountsAsAFailureOfThatSetupOnly() throws Exception {
        assertFalse(new TaiEvidenceFiles(context).failed(MODEL, MNN, "gpu"));
        // Written after that reader was made, as the runtime process writes it: the next reader (one per screen build) sees it.
        TaiRuntimeCrashMarker.markLoad(context, spec(MODEL, MNN, "/models/" + MODEL + "/config.json"),
            TaiRuntimeOptions.fromJson(new JSONObject().put("accelerator", "OpenCL")), MNN);
        TaiEvidenceFiles evidence = new TaiEvidenceFiles(context);
        assertTrue(evidence.failed(MODEL, MNN, "gpu"));
        assertFalse(evidence.failed(MODEL, MNN, "cpu"));
        assertFalse(evidence.failed(MODEL, TaiModelSpec.BACKEND_LITERT_LM, "gpu"));
        assertFalse(evidence.failed("another-model", MNN, "gpu"));
        // Cleared once the load returned: the next reader no longer sees it, this one keeps its snapshot.
        TaiRuntimeCrashMarker.clear(context);
        assertTrue(evidence.failed(MODEL, MNN, "gpu"));
        assertFalse(new TaiEvidenceFiles(context).failed(MODEL, MNN, "gpu"));
        assertNull(TaiRuntimeCrashMarker.read(context));
    }

    @Test
    public void theMarkerIsAFileAnyProcessReads() throws Exception {
        java.io.File file = TaiRuntimeCrashMarker.file(context);
        TaiBenchStore.writeAtomically(file, new JSONObject().put("modelId", MODEL).put("backend", MNN)
            .put("accelerator", "cpu").toString());
        JSONObject marker = TaiRuntimeCrashMarker.read(context);
        assertEquals(MODEL, marker.getString("modelId"));
        assertTrue(new TaiEvidenceFiles(context).failed(MODEL, MNN, "cpu"));
        assertTrue(file.delete());
        assertNull(TaiRuntimeCrashMarker.read(context));
    }

    // --------------------------------------------------------------------------- feature checks

    private static final String LM = "user-chat-lm";
    private static final String LITERT = TaiModelSpec.BACKEND_LITERT_LM;

    /** A completed cleanup check of {@code modelId} on LiteRT-LM, stored with {@code staleKey}. */
    private static JSONObject checkRecord(String modelId, String accelerator, String staleKey) throws Exception {
        TaiFeatureCheck.Measurement m = TaiFeatureCheckTest.measurement(accelerator, false, 10.0);
        m.modelId = modelId;
        m.backend = LITERT;
        m.staleKey = staleKey;
        return TaiFeatureCheck.record(m);
    }

    /** Installs {@link #LM} as a user model with a real file, and returns its spec. */
    private TaiModelSpec installLm() throws Exception {
        java.io.File file = new java.io.File(context.getFilesDir(), "tai/models/" + LM + "/model.litertlm");
        assertTrue(file.getParentFile().isDirectory() || file.getParentFile().mkdirs());
        java.nio.file.Files.write(file.toPath(), new byte[] {1, 2, 3});
        TaiModelSpec spec = spec(LM, LITERT, file.getAbsolutePath());
        new TaiModelStore(context).upsertUserModel(spec);
        return spec;
    }

    @Test
    public void aCheckIsJudgedAgainstTheFileInstalledNow() throws Exception {
        TaiModelSpec installed = installLm();
        TaiFeatureCheckStore checks = TaiFeatureCheckStore.in(context.getFilesDir());
        checks.append(checkRecord(LM, "gpu", TaiFeatureCheckStore.stalenessKey(installed)));
        checks.append(checkRecord(LM, "cpu", "size:9:mtime:9|litert-lm 0.0.1"));
        checks.append(checkRecord("not-installed", "gpu", TaiFeatureCheckStore.stalenessKey(installed)));
        TaiEvidenceFiles evidence = new TaiEvidenceFiles(context);

        List<TaiEvidence.FeatureResult> results = evidence.featureChecks(TaiFunction.TIDY_DICTATION, LM, LITERT);
        assertEquals(2, results.size());
        for (TaiEvidence.FeatureResult result : results) {
            assertEquals(result.accelerator, "cpu".equals(result.accelerator), result.stale);
        }
        // A model that is not installed has no file to match: every check of it is stale.
        List<TaiEvidence.FeatureResult> gone = evidence.featureChecks(TaiFunction.TIDY_DICTATION, "not-installed", LITERT);
        assertEquals(1, gone.size());
        assertTrue(gone.get(0).stale);
        // Matched on the feature and the backend as well.
        assertTrue(evidence.featureChecks(TaiFunction.APP_CATEGORIES, LM, LITERT).isEmpty());
        assertTrue(evidence.featureChecks(TaiFunction.TIDY_DICTATION, LM, MNN).isEmpty());
    }

    @Test
    public void aRewrittenResultsFileIsReadAgain() throws Exception {
        TaiEvidenceFiles evidence = new TaiEvidenceFiles(context);
        TaiBenchStore bench = TaiBenchStore.in(context.getFilesDir());
        bench.append(benchRecord("gpu", 20.0, MnnTaiRuntime.RUNTIME_VERSION));
        assertEquals(1, evidence.chatBench(MODEL, MNN).size());
        bench.append(benchRecord("cpu", 30.0, MnnTaiRuntime.RUNTIME_VERSION));
        assertEquals(2, evidence.chatBench(MODEL, MNN).size());

        TaiFeatureCheckStore checks = TaiFeatureCheckStore.in(context.getFilesDir());
        checks.append(checkRecord(LM, "gpu", "k"));
        assertEquals(1, evidence.featureChecks(TaiFunction.TIDY_DICTATION, LM, LITERT).size());
        checks.append(checkRecord(LM, "cpu", "k"));
        assertEquals(2, evidence.featureChecks(TaiFunction.TIDY_DICTATION, LM, LITERT).size());
    }

    // ------------------------------------------------------------------------ one read per reader

    @Test
    public void aReaderReadsTheHistoryAndTheMarkerOnce() throws Exception {
        TaiDeviceCapabilities device = TaiDeviceCapabilities.detect(context);
        TaiRuntimeHistory.recordFailure(context, spec(MODEL, MNN, "/m/config.json"), device, MNN, "gpu", "boom");
        TaiEvidenceFiles evidence = new TaiEvidenceFiles(context);
        assertTrue(evidence.failed(MODEL, MNN, "gpu"));
        assertFalse(evidence.failed(MODEL, MNN, "cpu"));
        // The files change under the reader: its first answers stand, a fresh reader sees the change.
        TaiRuntimeHistory.clear(context);
        TaiBenchStore.writeAtomically(TaiRuntimeCrashMarker.file(context), new JSONObject()
            .put("modelId", MODEL).put("backend", MNN).put("accelerator", "cpu").toString());
        assertTrue(evidence.failed(MODEL, MNN, "gpu"));
        assertFalse(evidence.failed(MODEL, MNN, "cpu"));
        TaiEvidenceFiles fresh = new TaiEvidenceFiles(context);
        assertFalse(fresh.failed(MODEL, MNN, "gpu"));
        assertTrue(fresh.failed(MODEL, MNN, "cpu"));
    }

    @Test
    public void theHistoryMatcherAgreesWithTheContextOne() throws Exception {
        TaiDeviceCapabilities device = TaiDeviceCapabilities.detect(context);
        TaiRuntimeHistory.recordFailure(context, spec(MODEL, MNN, "/m/config.json"), device, MNN, "gpu", "boom");
        TaiRuntimeHistory.recordSuccess(context, spec("ok-model", MNN, "/m/config.json"), device, MNN, "gpu");
        TaiRuntimeHistory.recordFailure(context, spec("gone-model", MNN, "/m/config.json"), device, MNN, "gpu",
            "Download or import this model first");
        JSONObject history = TaiRuntimeHistory.snapshot(context);
        long now = System.currentTimeMillis();
        long version = TaiRuntimeHistory.appVersionCode(context);
        String[][] cases = {{MODEL, MNN, "gpu"}, {MODEL, MNN, "cpu"}, {MODEL, TaiModelSpec.BACKEND_LITERT_LM, "gpu"},
            {"ok-model", MNN, "gpu"}, {"gone-model", MNN, "gpu"}};
        for (String[] c : cases) {
            assertEquals(String.join("/", c), TaiRuntimeHistory.hasFailure(context, device, c[0], c[1], c[2]),
                TaiRuntimeHistory.hasFailure(history, device, c[0], c[1], c[2], now, version));
        }
        assertTrue(TaiRuntimeHistory.hasFailure(history, device, MODEL, MNN, "gpu", now, version));
        assertFalse(TaiRuntimeHistory.hasFailure(history, device, MODEL, TaiModelSpec.BACKEND_LITERT_LM, "gpu", now, version));
        assertFalse(TaiRuntimeHistory.hasFailure(history, device, "ok-model", MNN, "gpu", now, version));
        assertFalse(TaiRuntimeHistory.hasFailure(history, device, "gone-model", MNN, "gpu", now, version));
        // Expired by the clock.
        assertFalse(TaiRuntimeHistory.hasFailure(history, device, MODEL, MNN, "gpu",
            now + TaiRuntimeHistory.FAILURE_TTL_MS + 60_000L, version));
    }
}
