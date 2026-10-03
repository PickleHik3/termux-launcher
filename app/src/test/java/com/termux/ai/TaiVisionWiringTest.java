package com.termux.ai;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Where the wallpaper vision graphs sit: their capabilities keeping them out of every chat list,
 * their own lane in the runtime process with cancel on the control lane, their residency kind, and
 * which models the analysis still needs.
 */
public class TaiVisionWiringTest {
    private static final long MB = 1024L * 1024L;
    private static final long NOW = 10_000_000L;

    @Test
    public void analyzeRunsOnTheVisionLaneAndCancelOnTheControlLane() {
        assertTrue(TaiRuntimeService.isVisionOperation(TaiRuntimeIpc.OP_VISION_ANALYZE));
        assertFalse(TaiRuntimeService.isVisionOperation(TaiRuntimeIpc.OP_VISION_CANCEL));
        assertTrue(TaiRuntimeService.isConcurrentControlOperation(TaiRuntimeIpc.OP_VISION_CANCEL));
        assertFalse(TaiRuntimeService.isConcurrentControlOperation(TaiRuntimeIpc.OP_VISION_ANALYZE));
        assertFalse(TaiRuntimeService.isImageOperation(TaiRuntimeIpc.OP_VISION_ANALYZE));
        assertFalse(TaiRuntimeService.isTtsOperation(TaiRuntimeIpc.OP_VISION_ANALYZE));
        assertFalse(TaiRuntimeService.isSttOperation(TaiRuntimeIpc.OP_VISION_ANALYZE));
        assertFalse(TaiRuntimeService.isStatusOperation(TaiRuntimeIpc.OP_VISION_ANALYZE));
        assertTrue(TaiRuntimeService.isForegroundOperation(TaiRuntimeIpc.OP_VISION_ANALYZE));
        assertFalse(TaiRuntimeService.isForegroundOperation(TaiRuntimeIpc.OP_VISION_CANCEL));
        // A benchmark must not share the CPU with an analysis; the cancel still passes.
        assertTrue(TaiRuntimeService.isRefusedDuringBench(TaiRuntimeIpc.OP_VISION_ANALYZE));
        assertFalse(TaiRuntimeService.isRefusedDuringBench(TaiRuntimeIpc.OP_VISION_CANCEL));
    }

    @Test
    public void aVisionModelIsToolOnlyAndNeverChat() {
        String[][] cases = {
            {TaiModelCatalog.DEPTH_ANYTHING_3_SMALL_ID, TaiModelSpec.CAPABILITY_DEPTH_ESTIMATION},
            {TaiModelCatalog.DEPTH_ANYTHING_V2_SMALL_ID, TaiModelSpec.CAPABILITY_DEPTH_ESTIMATION},
            {TaiModelCatalog.SEGFORMER_B0_ADE20K_ID, TaiModelSpec.CAPABILITY_SCENE_SEGMENTATION},
            {TaiModelCatalog.U2NET_ID, TaiModelSpec.CAPABILITY_SUBJECT_SEGMENTATION},
        };
        for (String[] c : cases) {
            LinkedHashSet<String> source = new LinkedHashSet<>(Arrays.asList(c[1], TaiModelSpec.CAPABILITY_TEXT_CHAT));
            LinkedHashSet<String> endpoint = TaiModelSpec.endpointCapabilitiesFor(c[0],
                TaiModelSpec.BACKEND_LITERT_LM, TaiModelSpec.FORMAT_LITERTLM, source, "/models/" + c[0] + "/graph.tflite");
            assertEquals(c[0], Collections.singleton(c[1]), endpoint);
            assertTrue(TaiModelSpec.isVisionTool(endpoint));
        }
        assertFalse(TaiModelSpec.isVisionTool(Collections.singleton(TaiModelSpec.CAPABILITY_TEXT_CHAT)));
        assertFalse(TaiModelSpec.isVisionTool(Collections.singleton(TaiModelSpec.CAPABILITY_TEXT_TO_SPEECH)));
    }

    @Test
    public void aVisionSpecKnowsItIsOne() {
        TaiModelSpec spec = new TaiModelSpec(TaiModelCatalog.U2NET_ID, "U-2-Net", "Subject cut-out", "downloaded",
            "/models/u2net/u2net_fp16.tflite", "Apache-2.0", 88_230_272L,
            new LinkedHashSet<>(Collections.singletonList(TaiModelSpec.CAPABILITY_SUBJECT_SEGMENTATION)));
        assertTrue(spec.isVisionTool());
        assertTrue(TaiVisionModels.isVisionModel(spec));
        assertFalse(spec.isImageGeneration());
    }

    @Test
    public void aVisionResidentIsNeverAnEvictionCandidateNorIdle() {
        TaiResidency.Entry graph = new TaiResidency.Entry(TaiModelCatalog.U2NET_ID, TaiResidency.Kind.VISION,
            TaiModelSpec.BACKEND_LITERT_LM, "cpu", 0, 180L * MB, null, NOW - 3_600_000L, false);
        TaiResidency.Entry embedding = new TaiResidency.Entry("embeddinggemma", TaiResidency.Kind.EMBEDDING,
            TaiModelSpec.BACKEND_LITERT_LM, "cpu", 0, 200L * MB, null, NOW - 1_000L, false);
        List<TaiResidency.Entry> residents = Arrays.asList(graph, embedding);
        assertEquals(Collections.singletonList(embedding),
            TaiResidency.evictionCandidates(residents, TaiResidency.Kind.VISION, TaiModelSpec.BACKEND_LITERT_LM));
        assertEquals(Collections.singletonList(embedding), TaiPressureWatch.idleInEvictionOrder(residents, true));
        assertEquals(0L, TaiPressureWatch.idleLimitMs(TaiResidency.Kind.VISION));
        assertTrue(TaiPressureWatch.idleExpired(residents, NOW + 10L * 3_600_000L).stream()
            .noneMatch(e -> e.kind == TaiResidency.Kind.VISION));
        // Nothing a vision graph holds is credited against another load: the next graph loads after it closes.
        assertEquals(1_000L, TaiResidency.creditedAvailable(1_000L, residents, TaiResidency.Kind.VISION,
            TaiModelSpec.BACKEND_LITERT_LM));
    }

    @Test
    public void aVisionLoadIsEstimatedAtTwiceItsFile() {
        TaiModelSpec spec = new TaiModelSpec(TaiModelCatalog.DEPTH_ANYTHING_3_SMALL_ID, "DA3", "Depth", "downloaded",
            "/nonexistent/da3_small_gpu_fp16.tflite", "Apache-2.0", 55_035_456L,
            new LinkedHashSet<>(Collections.singletonList(TaiModelSpec.CAPABILITY_DEPTH_ESTIMATION)));
        assertEquals(110_070_912L, TaiResidency.visionEstimateBytes(spec));
        assertEquals(TaiResidency.Kind.VISION, TaiResidency.Entry.vision(spec).kind);
        assertEquals(110_070_912L, TaiResidency.Entry.vision(spec).estimatedBytes);
    }

    @Test
    public void nothingInstalledNeedsADepthModelSegFormerAndU2Net() {
        List<TaiVisionModels.Missing> missing = TaiVisionModels.missing(Collections.<String>emptyList(), null);
        assertEquals(3, missing.size());
        assertEquals(TaiModelCatalog.DEPTH_ANYTHING_3_SMALL_ID, missing.get(0).id);
        assertEquals(TaiModelCatalog.SEGFORMER_B0_ADE20K_ID, missing.get(1).id);
        assertEquals(TaiModelCatalog.U2NET_ID, missing.get(2).id);
        assertEquals(55_035_456L, missing.get(0).sizeBytes);
        assertEquals("Depth Anything 3 Small", missing.get(0).displayName);
    }

    @Test
    public void eitherDepthModelSatisfiesTheDepthNeed() {
        List<TaiVisionModels.Missing> missing = TaiVisionModels.missing(
            Arrays.asList(TaiModelCatalog.DEPTH_ANYTHING_V2_SMALL_ID, TaiModelCatalog.U2NET_ID), null);
        assertEquals(1, missing.size());
        assertEquals(TaiModelCatalog.SEGFORMER_B0_ADE20K_ID, missing.get(0).id);
        assertTrue(TaiVisionModels.missing(Arrays.asList(TaiModelCatalog.DEPTH_ANYTHING_3_SMALL_ID,
            TaiModelCatalog.SEGFORMER_B0_ADE20K_ID, TaiModelCatalog.U2NET_ID), null).isEmpty());
    }

    @Test
    public void theChosenDepthModelIsOfferedWhenNoneIsInstalled() {
        List<TaiVisionModels.Missing> missing = TaiVisionModels.missing(Collections.<String>emptyList(),
            TaiModelCatalog.DEPTH_ANYTHING_V2_SMALL_ID);
        assertEquals(TaiModelCatalog.DEPTH_ANYTHING_V2_SMALL_ID, missing.get(0).id);
        // A preference naming something that is not a depth model is ignored.
        assertEquals(TaiModelCatalog.DEPTH_ANYTHING_3_SMALL_ID,
            TaiVisionModels.missing(Collections.<String>emptyList(), TaiModelCatalog.U2NET_ID).get(0).id);
    }

    @Test
    public void depthModelPrefersTheChoiceThenDa3ThenDa2() {
        List<String> both = Arrays.asList(TaiModelCatalog.DEPTH_ANYTHING_3_SMALL_ID, TaiModelCatalog.DEPTH_ANYTHING_V2_SMALL_ID);
        assertEquals(TaiModelCatalog.DEPTH_ANYTHING_3_SMALL_ID, TaiVisionModels.chooseDepth(both, null));
        assertEquals(TaiModelCatalog.DEPTH_ANYTHING_V2_SMALL_ID,
            TaiVisionModels.chooseDepth(both, TaiModelCatalog.DEPTH_ANYTHING_V2_SMALL_ID));
        // A choice that is not installed falls back to what is.
        assertEquals(TaiModelCatalog.DEPTH_ANYTHING_V2_SMALL_ID, TaiVisionModels.chooseDepth(
            Collections.singletonList(TaiModelCatalog.DEPTH_ANYTHING_V2_SMALL_ID), TaiModelCatalog.DEPTH_ANYTHING_3_SMALL_ID));
        // Nothing installed: DA3 is the one to fetch.
        assertEquals(TaiModelCatalog.DEPTH_ANYTHING_3_SMALL_ID, TaiVisionModels.chooseDepth(Collections.<String>emptyList(), null));
        // A stored id that is not a depth model never wins.
        assertEquals(TaiModelCatalog.DEPTH_ANYTHING_3_SMALL_ID, TaiVisionModels.chooseDepth(both, TaiModelCatalog.U2NET_ID));
    }
}
