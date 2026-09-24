package com.termux.ai;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Memory manager phase 3 around the budget: what history keeps of a measured load, which residents
 * a load may evict and in what order, how the router carries an eviction out, and the window a
 * GPU->CPU fallback runs with.
 */
@RunWith(RobolectricTestRunner.class)
public class TaiLoadMeasurementTest {
    private static final long E4B = 3_659_530_240L;

    private Context context;
    private TaiDeviceCapabilities device;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences(TaiSettings.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit();
        device = TaiDeviceCapabilities.createForTest("pong", "nothing", "sm8475", 35,
            Collections.singletonList("arm64-v8a"), 12L * 1024L * 1024L * 1024L, "test", false);
    }

    // --- History ---

    /** Identical GPU loads spread over ±0.9 GB on pong; the budget plans against the worst one seen. */
    @Test
    public void historyKeepsTheLargestMeasuredDropPerKey() {
        TaiModelSpec spec = chatSpec("gemma-4-e4b");
        assertEquals(0L, TaiRuntimeHistory.measuredLoadBytes(context, spec, device, TaiModelSpec.BACKEND_LITERT_LM, "gpu", 4096));

        TaiRuntimeHistory.recordMeasuredLoad(context, spec, device, TaiModelSpec.BACKEND_LITERT_LM, "gpu", 4096, 3_000_000_000L);
        TaiRuntimeHistory.recordMeasuredLoad(context, spec, device, TaiModelSpec.BACKEND_LITERT_LM, "gpu", 4096, 2_400_000_000L);
        assertEquals(3_000_000_000L, TaiRuntimeHistory.measuredLoadBytes(context, spec, device, TaiModelSpec.BACKEND_LITERT_LM, "gpu", 4096));
        TaiRuntimeHistory.recordMeasuredLoad(context, spec, device, TaiModelSpec.BACKEND_LITERT_LM, "gpu", 4096, 4_200_000_000L);
        assertEquals(4_200_000_000L, TaiRuntimeHistory.measuredLoadBytes(context, spec, device, TaiModelSpec.BACKEND_LITERT_LM, "gpu", 4096));

        // Another accelerator, backend or window bucket is another key; a non-positive drop is not a record.
        assertEquals(0L, TaiRuntimeHistory.measuredLoadBytes(context, spec, device, TaiModelSpec.BACKEND_LITERT_LM, "cpu", 4096));
        assertEquals(0L, TaiRuntimeHistory.measuredLoadBytes(context, spec, device, TaiModelSpec.BACKEND_MNN_LLM, "gpu", 4096));
        assertEquals(0L, TaiRuntimeHistory.measuredLoadBytes(context, spec, device, TaiModelSpec.BACKEND_LITERT_LM, "gpu", 8192));
        TaiRuntimeHistory.recordMeasuredLoad(context, spec, device, TaiModelSpec.BACKEND_LITERT_LM, "cpu", 4096, 0L);
        assertEquals(0L, TaiRuntimeHistory.measuredLoadBytes(context, spec, device, TaiModelSpec.BACKEND_LITERT_LM, "cpu", 4096));
    }

    /** Windows share a record by power-of-two bucket, and the -vision variant shares the base model's. */
    @Test
    public void historyBucketsWindowsAndKeysByTheBaseModel() {
        assertEquals(0, TaiRuntimeHistory.contextBucket(0));
        assertEquals(4096, TaiRuntimeHistory.contextBucket(4000));
        assertEquals(4096, TaiRuntimeHistory.contextBucket(4096));
        assertEquals(8192, TaiRuntimeHistory.contextBucket(6000));

        TaiModelSpec base = chatSpec("gemma-4-e4b");
        TaiRuntimeHistory.recordMeasuredLoad(context, base, device, TaiModelSpec.BACKEND_LITERT_LM, "gpu", 4000, 3_100_000_000L);
        assertEquals(3_100_000_000L, TaiRuntimeHistory.measuredLoadBytes(context, base, device, TaiModelSpec.BACKEND_LITERT_LM, "gpu", 4096));
        assertEquals(3_100_000_000L, TaiRuntimeHistory.measuredLoadBytes(context, chatSpec("gemma-4-e4b-vision"), device,
            TaiModelSpec.BACKEND_LITERT_LM, "gpu", 4096));
        // opencl is MNN's spelling of the GPU.
        assertEquals(3_100_000_000L, TaiRuntimeHistory.measuredLoadBytes(context, base, device, TaiModelSpec.BACKEND_LITERT_LM, "opencl", 4096));
    }

    /** The budget's history hook reads what the runtimes recorded, so the next plan is measured. */
    @Test
    public void aRecordedLoadMakesTheNextPlanMeasured() {
        TaiModelSpec spec = chatSpec("gemma-4-e4b");
        TaiRuntimeHistory.recordMeasuredLoad(context, spec, device, TaiModelSpec.BACKEND_LITERT_LM, "gpu", 4096, 3_200_000_000L);
        TaiLoadBudget.History history = (accelerator, contextTokens) -> TaiRuntimeHistory.measuredLoadBytes(
            context, spec, device, TaiModelSpec.BACKEND_LITERT_LM, accelerator, contextTokens);
        TaiLoadBudget.Plan plan = TaiLoadBudget.plan(new TaiLoadBudget.Request(TaiModelSpec.BACKEND_LITERT_LM, E4B, false,
            11_530_736L * 1024L, 9_000_000_000L, Arrays.asList("gpu", "cpu"), 4096, null, 0, false, 315_000_000L, history,
            Collections.<TaiResidency.Entry>emptyList()));
        assertEquals(TaiLoadBudget.SOURCE_MEASURED, plan.estimateSource);
        assertEquals(3_520_000_000L, plan.estimatedBytes);
    }

    // --- Residents ---

    @Test
    public void aMeasuredEntryCarriesItsDropAndTheBudgetCountsIt() throws JSONException {
        TaiResidency.Entry entry = TaiResidency.Entry.chat(chatSpec("gemma-4-e4b"), TaiModelSpec.BACKEND_LITERT_LM, "gpu", 4096);
        assertNull(entry.measuredBytes);
        TaiResidency.Entry measured = entry.withMeasured(3_000_000_000L);
        assertEquals(Long.valueOf(3_000_000_000L), measured.measuredBytes);
        assertEquals(3_000_000_000L, measured.bytes());
        assertEquals(entry.estimatedBytes, measured.estimatedBytes);
        assertEquals(3_000_000_000L, measured.toJson().getLong("measuredBytes"));
        assertNull(entry.withMeasured(null).measuredBytes);
    }

    /** Eviction order: embeddings, then STT, then idle chat; busy residents and what the load replaces are left out. */
    @Test
    public void evictionCandidatesAreOrderedByKindThenAgeAndLeaveOutBusyAndReplacedResidents() {
        TaiResidency.Entry chat = new TaiResidency.Entry("qwen", TaiResidency.Kind.CHAT, TaiModelSpec.BACKEND_MNN_LLM, "cpu", 4096, 900L, null, 10L, false);
        TaiResidency.Entry liteRtEmbedding = new TaiResidency.Entry("emb", TaiResidency.Kind.EMBEDDING, TaiModelSpec.BACKEND_LITERT_LM, "cpu", 1024, 200L, null, 30L, false);
        TaiResidency.Entry mnnEmbedding = new TaiResidency.Entry("qwen-emb", TaiResidency.Kind.EMBEDDING, TaiModelSpec.BACKEND_MNN_LLM, "cpu", 0, 300L, null, 20L, false);
        TaiResidency.Entry busyEmbedding = new TaiResidency.Entry("busy-emb", TaiResidency.Kind.EMBEDDING, TaiModelSpec.BACKEND_LITERT_LM, "cpu", 1024, 200L, null, 5L, true);
        TaiResidency.Entry stt = new TaiResidency.Entry("whisper", TaiResidency.Kind.STT, TaiModelSpec.BACKEND_LITERT_LM, "cpu", 30, 400L, null, 1L, false);
        TaiResidency.Entry runtime = new TaiResidency.Entry(TaiResidency.RUNTIME_ID, TaiResidency.Kind.RUNTIME, "process", "cpu", 0, 330L, null, 1L, false);
        List<TaiResidency.Entry> residents = Arrays.asList(chat, liteRtEmbedding, mnnEmbedding, busyEmbedding, stt, runtime);

        // A chat load: never chat (it is replaced, and credited); embeddings oldest first, then STT.
        assertEquals(Arrays.asList(mnnEmbedding, liteRtEmbedding, stt),
            TaiResidency.evictionCandidates(residents, TaiResidency.Kind.CHAT, TaiModelSpec.BACKEND_LITERT_LM));
        // A LiteRT embedding load: not its own backend's embedding (replaced), but MNN's, then STT, then idle chat.
        assertEquals(Arrays.asList(mnnEmbedding, stt, chat),
            TaiResidency.evictionCandidates(residents, TaiResidency.Kind.EMBEDDING, TaiModelSpec.BACKEND_LITERT_LM));
        // An STT load may take everything idle.
        assertEquals(Arrays.asList(mnnEmbedding, liteRtEmbedding, stt, chat),
            TaiResidency.evictionCandidates(residents, TaiResidency.Kind.STT, null));
        for (TaiResidency.Entry entry : TaiResidency.evictionCandidates(residents, TaiResidency.Kind.STT, null)) {
            assertFalse(entry.busy);
            assertFalse(entry.kind == TaiResidency.Kind.RUNTIME);
        }
    }

    // --- Router ---

    /** The router unloads an idle chat victim through its backend and leaves a generating one alone. */
    @Test
    public void theRouterEvictsIdleChatThroughItsBackendAndNeverABusyOne() throws Exception {
        FakeChatRuntime liteRt = new FakeChatRuntime("e4b");
        FakeChatRuntime mnn = new FakeChatRuntime("qwen");
        MultiBackendTaiRuntime runtime = new MultiBackendTaiRuntime(liteRt, mnn);
        liteRt.residency = runtime.residency();
        mnn.residency = runtime.residency();
        TaiResidency.Entry e4b = TaiResidency.Entry.chat(chatSpec("e4b"), TaiModelSpec.BACKEND_LITERT_LM, "gpu", 4096);
        TaiResidency.Entry qwen = TaiResidency.Entry.chat(chatSpec("qwen"), TaiModelSpec.BACKEND_MNN_LLM, "cpu", 4096);
        runtime.residency().register(e4b);
        runtime.residency().register(qwen);
        mnn.generating = true;

        List<String> evicted = runtime.evict(Arrays.asList(e4b, qwen));

        assertEquals(Collections.singletonList("e4b"), evicted);
        assertTrue(liteRt.unloadCalled);
        assertFalse(mnn.unloadCalled);
        assertFalse(runtime.residency().isResident(TaiResidency.Kind.CHAT, "e4b"));
        assertTrue(runtime.residency().isResident(TaiResidency.Kind.CHAT, "qwen"));
        // A victim that is already gone is skipped, not an error.
        assertTrue(runtime.evict(Collections.singletonList(e4b)).isEmpty());
    }

    // --- CPU fallback ---

    /** The plan sized the window for the GPU; the CPU retry after a GPU failure runs at the floor. */
    @Test
    public void theCpuFallbackRunsAtTheFloorWindowOnTheCpu() {
        TaiRuntimeOptions gpu8k = new TaiRuntimeOptions(null, null, null, null, "auto", 8192, null, null, null);
        TaiRuntimeOptions fallback = LiteRtTaiRuntime.cpuFallbackOptions(gpu8k);
        assertEquals(Integer.valueOf(TaiLoadBudget.FLOOR_CONTEXT), fallback.contextWindow);
        assertEquals("cpu", fallback.accelerator);

        TaiRuntimeOptions unset = new TaiRuntimeOptions(null, null, null, null, null, null, null, null);
        assertEquals(Integer.valueOf(TaiLoadBudget.FLOOR_CONTEXT), LiteRtTaiRuntime.cpuFallbackOptions(unset).contextWindow);

        // A window already below the floor is kept: the model's own limit may be smaller.
        TaiRuntimeOptions small = new TaiRuntimeOptions(null, null, null, null, "auto", 2048, null, null, null);
        assertEquals(Integer.valueOf(2048), LiteRtTaiRuntime.cpuFallbackOptions(small).contextWindow);
    }

    private static TaiModelSpec chatSpec(String id) {
        return new TaiModelSpec(
            id,
            id,
            "Test model",
            "test",
            "/models/" + id + "/model.litertlm",
            "test",
            E4B,
            new LinkedHashSet<>(Collections.singleton(TaiModelSpec.CAPABILITY_TEXT_CHAT)),
            false,
            null,
            TaiModelSpec.BACKEND_LITERT_LM,
            TaiModelSpec.FORMAT_LITERTLM,
            null,
            null,
            4096,
            0,
            null
        );
    }

    /** A backend holding one chat model; unload deregisters it the way LiteRT's closeEngineLocked does. */
    private static final class FakeChatRuntime implements TaiRuntime {
        private final String modelId;
        volatile TaiResidency residency;
        volatile boolean generating;
        volatile boolean unloadCalled;
        private volatile boolean loaded = true;

        FakeChatRuntime(String modelId) {
            this.modelId = modelId;
        }

        @Override public TaiRuntimeState getState() {
            return new TaiRuntimeState(loaded, loaded ? modelId : null, "fake", generating ? "generating" : loaded ? "loaded" : "unloaded",
                "", TaiModelSpec.BACKEND_LITERT_LM, null, null, generating, generating ? "gen" : null, 0L, 0L, 0L, 0L, 0L);
        }
        @Override public boolean isModelLoaded(String id) { return loaded && modelId.equals(id); }
        @Override public JSONObject load(TaiModelSpec modelSpec, TaiRuntimeOptions options) throws JSONException { return ok(); }
        @Override public JSONObject unload() throws JSONException {
            unloadCalled = true;
            loaded = false;
            if (residency != null) residency.deregister(TaiResidency.Kind.CHAT, modelId);
            return ok();
        }
        @Override public JSONObject keepWarm(TaiModelSpec modelSpec, TaiRuntimeOptions options, int minutes) throws JSONException { return ok(); }
        @Override public JSONObject cancel() throws JSONException { return ok(); }
        @Override public JSONObject chat(String id, String systemPrompt, String userPrompt, TaiRuntimeOptions options) throws JSONException { return ok(); }
        @Override public JSONObject chat(String id, String systemPrompt, String userPrompt, TaiRuntimeOptions options, TaiGenerationCallback callback) throws JSONException { return ok(); }
        @Override public JSONObject chat(String id, TaiChatRequest request, TaiRuntimeOptions options) throws JSONException { return ok(); }
        @Override public JSONObject chat(String id, TaiChatRequest request, TaiRuntimeOptions options, TaiGenerationCallback callback) throws JSONException { return ok(); }
        @Override public JSONObject complete(String id, String prompt, TaiRuntimeOptions options) throws JSONException { return ok(); }
        @Override public JSONObject complete(String id, String prompt, TaiRuntimeOptions options, TaiGenerationCallback callback) throws JSONException { return ok(); }

        private JSONObject ok() throws JSONException { return new JSONObject().put("ok", true); }
    }
}
