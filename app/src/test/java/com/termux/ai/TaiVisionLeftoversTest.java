package com.termux.ai;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * The retired wallpaper vision models: which leftovers the one-time cleanup deletes, and that
 * a leftover is still a tool model, never chat, and still never evicts or idles in the resident set.
 */
public class TaiVisionLeftoversTest {
    private static final long MB = 1024L * 1024L;
    private static final long NOW = 10_000_000L;

    private static TaiModelSpec spec(String id, String capability) {
        return new TaiModelSpec(id, id, "role", "downloaded", "/models/" + id + "/graph.tflite", "Apache-2.0",
            55L * MB, new LinkedHashSet<>(Collections.singletonList(capability)));
    }

    @Test
    public void visionToolsAndLegacyIdsAreDeletedAndNothingElse() {
        TaiModelSpec depth = spec("depth-anything-3-small", TaiModelSpec.CAPABILITY_DEPTH_ESTIMATION);
        TaiModelSpec other = spec("some-depth-import", TaiModelSpec.CAPABILITY_SCENE_SEGMENTATION);
        TaiModelSpec chat = spec("gemma-4-e2b-it", TaiModelSpec.CAPABILITY_TEXT_CHAT);
        Set<String> ids = TaiVisionLeftovers.idsToDelete(Arrays.asList(depth, other, chat),
            Arrays.asList("u2net", "gemma-4-e2b-it", ""));
        assertEquals(new LinkedHashSet<>(Arrays.asList("depth-anything-3-small", "some-depth-import", "u2net")), ids);
    }

    @Test
    public void aLegacySpecWithoutCapabilitiesIsStillDeleted() {
        TaiModelSpec bare = spec("segformer-b0-ade20k", TaiModelSpec.CAPABILITY_TEXT_CHAT);
        assertEquals(Collections.singleton("segformer-b0-ade20k"),
            TaiVisionLeftovers.idsToDelete(Collections.singletonList(bare), Collections.<String>emptyList()));
    }

    @Test
    public void aVisionModelIsToolOnlyAndNeverChat() {
        String[][] cases = {
            {"depth-anything-3-small", TaiModelSpec.CAPABILITY_DEPTH_ESTIMATION},
            {"depth-anything-v2-small", TaiModelSpec.CAPABILITY_DEPTH_ESTIMATION},
            {"segformer-b0-ade20k", TaiModelSpec.CAPABILITY_SCENE_SEGMENTATION},
            {"u2net", TaiModelSpec.CAPABILITY_SUBJECT_SEGMENTATION},
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
        assertTrue(spec("u2net", TaiModelSpec.CAPABILITY_SUBJECT_SEGMENTATION).isVisionTool());
    }

    @Test
    public void aVisionResidentIsNeverAnEvictionCandidateNorIdle() {
        TaiResidency.Entry graph = new TaiResidency.Entry("u2net", TaiResidency.Kind.VISION,
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
    }
}
