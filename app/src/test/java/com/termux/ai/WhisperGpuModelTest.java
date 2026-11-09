package com.termux.ai;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.io.File;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** Whisper Base float32 on the GPU: its catalog entry, the FP32 flag, the chooser and the recommendation. */
public class WhisperGpuModelTest {
    private static final File MODEL = new File("model.tflite");
    private static final String ID = TaiModelCatalog.WHISPER_BASE_GPU_ID;

    // ---- catalog ----------------------------------------------------------------------------

    @Test
    public void theEntryPinsTheFloatGraphAndTheMultilingualTokenizer() {
        TaiModelCatalog.CatalogEntry entry = TaiModelCatalog.get(ID);
        assertNotNull(entry);
        assertEquals("litert-community/whisper-base", entry.repositoryId);
        assertEquals("ba2c613646edc2b72f5a51fa0b5b0b322e436101", entry.revision);
        assertEquals("whisper_base_30s_f32.tflite", entry.artifactPath);
        assertEquals(290_082_636L, entry.sizeBytes);
        assertEquals("83f90febae74f6db2b671d792ce844f93de5dfa6d6743cdba8c7a208c8dd708a", entry.sha256);
        assertTrue(entry.capabilities.contains(TaiModelSpec.CAPABILITY_SPEECH_TO_TEXT));
        assertTrue(entry.downloadAvailable);
        assertEquals(1, entry.sidecars.size());
        TaiModelCatalog.CatalogEntry.Sidecar tokenizer = entry.sidecars.get(0);
        assertEquals("tokenizer.json", tokenizer.localName);
        assertTrue(tokenizer.url, tokenizer.url.startsWith("https://huggingface.co/openai/whisper-base/resolve/"));
        TaiModelCatalog.CatalogEntry acft = TaiModelCatalog.get("whisper-acft-base");
        assertEquals(acft.sidecars.get(0).url, tokenizer.url);
        assertEquals(acft.sidecars.get(0).sha256, tokenizer.sha256);
    }

    @Test
    public void theEntryHasOneThirtySecondWindowAndNoWindowSwitch() {
        TaiModelCatalog.CatalogEntry entry = TaiModelCatalog.get(ID);
        assertEquals(1, entry.speechWindows.size());
        assertTrue(entry.speechWindows.containsKey(30));
        // A stored 5 or 10 second preference never swaps the graph out.
        assertSame(entry, entry.withWindow(10));
        assertSame(entry, entry.withWindow(5));
        assertEquals(30, TaiSpeechModels.windowSeconds("/models/whisper_base_30s_f32.tflite"));
        assertTrue(entry.withWindow(30).gpuPrecisionFp32);
    }

    @Test
    public void onlyThisModelAsksForFp32AndItDoesNotRouteAsParakeet() {
        for (TaiModelCatalog.CatalogEntry entry : TaiModelCatalog.entries().values()) {
            assertEquals(entry.modelId, ID.equals(entry.modelId), entry.gpuPrecisionFp32);
            assertEquals(entry.modelId, ID.equals(entry.modelId), TaiSpeechModels.needsGpuFp32(entry.modelId));
        }
        assertFalse(TaiSpeechModels.isParakeet(ID, "whisper_base_30s_f32.tflite"));
        assertFalse(TaiSpeechModels.isEnglishOnly(ID, "whisper_base_30s_f32.tflite"));
        assertFalse(TaiSpeechModels.needsGpuFp32(null));
        assertEquals("Base GPU · Many languages", TaiSpeechModels.plainName(ID, "x", null));
    }

    @Test
    public void thePolicyListsItAndNeverHidesOrPreselectsIt() {
        for (int gb : new int[] {4, 8, 12, 16}) {
            long bytes = gb * (1L << 30);
            TaiTierPolicy.Env env = new TaiTierPolicy.Env(TaiDeviceTier.from(bytes), bytes, 34, true, true,
                TaiPlatformCaps.GpuPath.YES, true);
            assertEquals(TaiTierPolicy.Offer.LISTED, TaiTierPolicy.offer(env, ID));
        }
    }

    // ---- the device check -------------------------------------------------------------------

    @Test
    public void theOpenClCheckLooksForTheLibraryInTheThreeVendorPlaces() {
        assertFalse(TaiDeviceCapabilities.openClLibraryPresent(file -> false));
        for (String path : TaiDeviceCapabilities.OPENCL_LIBRARY_PATHS) {
            assertTrue(path, TaiDeviceCapabilities.openClLibraryPresent(file -> file.getPath().equals(path)));
        }
        assertEquals(Arrays.asList("/vendor/lib64/libOpenCL.so", "/system/vendor/lib64/libOpenCL.so",
            "/system/lib64/libOpenCL.so"), Arrays.asList(TaiDeviceCapabilities.OPENCL_LIBRARY_PATHS));
    }

    // ---- the chooser ------------------------------------------------------------------------

    private final FakeLiteRtGraph cpuGraph = new FakeLiteRtGraph();
    private final FakeLiteRtGraph gpuGraph = new FakeLiteRtGraph();
    private final FakeLiteRtGraph fp32Graph = new FakeLiteRtGraph();
    private final SttGraphChooserTest.FakeMemory memory = new SttGraphChooserTest.FakeMemory();

    private SttGraphChooser chooser(LiteRtGraph.Loader fp32) {
        return new SttGraphChooser(cpuGraph.loader(), cpuGraph.loader(), gpuGraph.loader(), fp32);
    }

    private static final SttGraphChooser.Prepare OK = (graph, verify) -> { };

    @Test
    public void autoAndGpuMeanTheFp32GpuForAModelThatNeedsIt() throws Exception {
        for (SttAccelerator ask : new SttAccelerator[] {SttAccelerator.AUTO, SttAccelerator.GPU}) {
            SttGraphChooser.Opened opened = chooser(fp32Graph.loader()).open(MODEL, 4, ask, memory, OK, true);
            assertSame(fp32Graph, opened.graph);
            assertEquals("gpu", opened.accelerator);
        }
        assertEquals(0, cpuGraph.loads);
        assertEquals(0, gpuGraph.loads);
    }

    @Test
    public void aModelThatDoesNotNeedFp32KeepsTheOldChoiceAndNeverTouchesTheFp32Loader() throws Exception {
        LiteRtGraph.Loader never = (file, threads) -> {
            throw new AssertionError("the FP32 loader is only for the models that need it");
        };
        SttGraphChooser.Opened auto = chooser(never).open(MODEL, 4, SttAccelerator.AUTO, memory, OK, false);
        assertEquals("cpu", auto.accelerator);
        SttGraphChooser.Opened gpu = chooser(never).open(MODEL, 4, SttAccelerator.GPU, memory, OK);
        assertEquals("gpu", gpu.accelerator);
        assertSame(gpuGraph, gpu.graph);
    }

    @Test
    public void anExplicitCpuRunsTheModelOnTheCpu() throws Exception {
        SttGraphChooser.Opened opened = chooser(fp32Graph.loader()).open(MODEL, 4, SttAccelerator.CPU, memory, OK, true);
        assertSame(cpuGraph, opened.graph);
        assertEquals("cpu", opened.accelerator);
        assertEquals(0, fp32Graph.loads);
        assertTrue(memory.records.isEmpty());
    }

    @Test
    public void aGpuThatFailsGivesTheErrorAndNoCpuGraph() throws Exception {
        LiteRtGraph.Loader broken = (file, threads) -> {
            throw new IllegalStateException("no OpenCL");
        };
        try {
            chooser(broken).open(MODEL, 4, SttAccelerator.AUTO, memory, OK, true);
            fail("must not fall back to the CPU");
        } catch (SttGpuUnavailableException expected) {
            assertTrue(expected.getMessage(), expected.getMessage().contains("no OpenCL"));
        }
        assertEquals(0, cpuGraph.loads);
        assertEquals(1, memory.records.size());
        assertTrue(memory.records.get(0).startsWith("gpu:false:"));
        assertEquals(0, memory.unfinished);
    }

    @Test
    public void aGpuThatProvesInsaneGivesTheErrorToo() throws Exception {
        SttGraphChooser.Prepare insane = (graph, verify) -> {
            if (verify) throw new IllegalStateException("GPU encoder output is not sane: NaN");
        };
        try {
            chooser(fp32Graph.loader()).open(MODEL, 4, SttAccelerator.GPU, memory, insane, true);
            fail("must not fall back to the CPU");
        } catch (SttGpuUnavailableException expected) {
            assertTrue(expected.getMessage().contains("not sane"));
        }
        assertEquals(1, fp32Graph.closes);
        assertEquals(0, cpuGraph.loads);
    }

    @Test
    public void aRecordedCrashStopsTheGpuAskWithoutLoadingAnything() throws Exception {
        memory.failure = SttGraphChooser.CRASH_REASON_PREFIX + " died";
        try {
            chooser(fp32Graph.loader()).open(MODEL, 4, SttAccelerator.AUTO, memory, OK, true);
            fail("a crashing GPU must not be tried again");
        } catch (SttGpuUnavailableException expected) {
            // Choosing CPU is the way out.
        }
        assertEquals(0, fp32Graph.loads);
        assertEquals(0, cpuGraph.loads);
    }

    // ---- the recommendation -----------------------------------------------------------------

    private static JSONObject record(String modelId, String ranOn, double realtime, double wer, String text, long at)
            throws Exception {
        JSONArray clips = new JSONArray().put(new JSONObject().put("id", "jfk-3s").put("text", text));
        return new JSONObject().put("benchVersion", TaiVoiceBench.BENCH_VERSION).put("kind", TaiVoiceBench.KIND)
            .put("modelId", modelId).put("backend", TaiVoiceBench.BACKEND).put("accelerator", ranOn).put("ranOn", ranOn)
            .put("status", TaiBenchStore.STATUS_COMPLETE).put("timestamp", at)
            .put("phases", new JSONObject().put("clips", clips)
                .put("voice", new JSONObject().put("realtime", realtime).put("wer", wer)));
    }

    private static TaiSpeechGpuAdvice.Advice judge(boolean gpu, JSONArray records, String current, boolean currentOnGpu) {
        return TaiSpeechGpuAdvice.judge(gpu, records, ID, current, currentOnGpu);
    }

    @Test
    public void noUsableGpuIsNoGpuWhateverTheBenchSays() throws Exception {
        JSONArray records = new JSONArray().put(record(ID, "gpu", 9, 0.01, "and so my fellow americans", 2))
            .put(record("whisper-acft-base", "cpu", 1, 0.01, "and so my fellow americans", 1));
        assertEquals(TaiSpeechGpuAdvice.Advice.NO_GPU, judge(false, records, "whisper-acft-base", false));
    }

    @Test
    public void withoutResultsItIsSomethingToTry() throws Exception {
        assertEquals(TaiSpeechGpuAdvice.Advice.TRY, judge(true, new JSONArray(), "whisper-acft-base", false));
        // Its own result but none for the model in use to compare with.
        JSONArray ours = new JSONArray().put(record(ID, "gpu", 4, 0.02, "and so my fellow americans", 2));
        assertEquals(TaiSpeechGpuAdvice.Advice.TRY, judge(true, ours, "whisper-acft-base", false));
    }

    @Test
    public void fasterWithSaneTextIsRecommended() throws Exception {
        JSONArray records = new JSONArray()
            .put(record("whisper-acft-small-en", "cpu", 3.4, 0.02, "and so my fellow americans", 1))
            .put(record(ID, "gpu", 4.1, 0.02, "and so my fellow americans", 2));
        assertEquals(TaiSpeechGpuAdvice.Advice.RECOMMENDED, judge(true, records, "whisper-acft-small-en", false));
    }

    @Test
    public void slowerOrGarbageIsSlowerOnThisPhone() throws Exception {
        JSONArray slower = new JSONArray()
            .put(record("whisper-acft-small-en", "cpu", 5, 0.02, "and so my fellow americans", 1))
            .put(record(ID, "gpu", 4, 0.02, "and so my fellow americans", 2));
        assertEquals(TaiSpeechGpuAdvice.Advice.SLOWER, judge(true, slower, "whisper-acft-small-en", false));
        JSONArray wrong = new JSONArray()
            .put(record("whisper-acft-small-en", "cpu", 3, 0.02, "and so my fellow americans", 1))
            .put(record(ID, "gpu", 6, 0.8, "and so my fellow americans", 2));
        assertEquals(TaiSpeechGpuAdvice.Advice.SLOWER, judge(true, wrong, "whisper-acft-small-en", false));
        JSONArray repeated = new JSONArray().put(record("whisper-acft-small-en", "cpu", 3, 0.02, "and so", 1))
            .put(record(ID, "gpu", 6, 0.1, "the the the the the the the the", 2));
        assertEquals(TaiSpeechGpuAdvice.Advice.SLOWER, judge(true, repeated, "whisper-acft-small-en", false));
        JSONArray empty = new JSONArray().put(record("whisper-acft-small-en", "cpu", 3, 0.02, "and so", 1))
            .put(record(ID, "gpu", 6, 0.1, "  ", 2));
        assertEquals(TaiSpeechGpuAdvice.Advice.SLOWER, judge(true, empty, "whisper-acft-small-en", false));
    }

    @Test
    public void theComparisonUsesTheProcessorTheCurrentModelRunsOn() throws Exception {
        JSONArray records = new JSONArray()
            .put(record("whisper-acft-small-en", "cpu", 1, 0.02, "and so my fellow americans", 1))
            .put(record("whisper-acft-small-en", "gpu", 9, 0.02, "and so my fellow americans", 1))
            .put(record(ID, "gpu", 4, 0.02, "and so my fellow americans", 2));
        assertEquals(TaiSpeechGpuAdvice.Advice.RECOMMENDED, judge(true, records, "whisper-acft-small-en", false));
        assertEquals(TaiSpeechGpuAdvice.Advice.SLOWER, judge(true, records, "whisper-acft-small-en", true));
    }

    @Test
    public void theRepeatedWordCheckIgnoresShortAndVariedText() {
        assertFalse(TaiVoiceBench.repeatsOneWord("the the the"));
        assertFalse(TaiVoiceBench.repeatsOneWord("ask not what your country can do for you"));
        assertTrue(TaiVoiceBench.repeatsOneWord("you you you you you you you and"));
    }

    @Test
    public void aGpuOnlyBenchTargetIsPlannedOnTheGpuAlone() {
        TaiVoiceBenchRunner.Target both = new TaiVoiceBenchRunner.Target("a", "A", null, 1L, true);
        TaiVoiceBenchRunner.Target gpuOnly = new TaiVoiceBenchRunner.Target(ID, "B", null, 1L, true, true);
        List<TaiBenchSuite.EntryPlan> plan = TaiVoiceBenchRunner.plan(Arrays.asList(both, gpuOnly));
        assertEquals(3, plan.size());
        assertEquals("gpu", plan.get(2).accelerator);
        assertEquals(ID, plan.get(2).modelId);
    }
}
