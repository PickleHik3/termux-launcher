package com.termux.ai;

import org.junit.Test;

import java.util.Collections;
import java.util.LinkedHashSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public class TaiContextWindowPolicyTest {
    private static final long GIB = 1L << 30;

    @Test
    public void unknownMemory_keepsTheCatalogFloor() {
        TaiModelSpec gemma = liteRt("gemma-4-e2b-it-litert-lm", 4096, 32_768);
        assertEquals(4096, TaiContextWindowPolicy.effectiveEndpointContextWindow(gemma, 0L, null));
        assertSame(gemma, TaiContextWindowPolicy.apply(gemma, 0L, null));
    }

    /** Auto: 2048 up to 6 GB, 4096 from 8 GB; the catalog floor of 4096 still holds, so a 4096-floor model never drops below it. */
    @Test
    public void ramClasses_capTheAutomaticWindow() {
        assertEquals(0, TaiContextWindowPolicy.tierCap(0L));
        assertEquals(2048, TaiContextWindowPolicy.tierCap(4L * GIB));
        assertEquals(2048, TaiContextWindowPolicy.tierCap(6L * GIB));
        assertEquals(4096, TaiContextWindowPolicy.tierCap(8L * GIB));
        assertEquals(4096, TaiContextWindowPolicy.tierCap(10L * GIB));
        assertEquals(4096, TaiContextWindowPolicy.tierCap(12L * GIB));
        assertEquals(4096, TaiContextWindowPolicy.tierCap(16L * GIB));
        assertEquals(4096, TaiContextWindowPolicy.tierCap(24L * GIB));

        TaiModelSpec gemma = liteRt("gemma-4-e2b-it-litert-lm", 4096, 32_768);
        for (long gib : new long[] {4, 6, 8, 12, 24}) {
            assertEquals("gib=" + gib, 4096, TaiContextWindowPolicy.effectiveEndpointContextWindow(gemma, gib * GIB, null));
        }
        // A model whose catalog floor is lower is capped to the class: 2048 up to 6 GB.
        TaiModelSpec small = liteRt("small-model", 1024, 32_768);
        assertEquals(2048, TaiContextWindowPolicy.effectiveEndpointContextWindow(small, 4L * GIB, null));
        assertEquals(2048, TaiContextWindowPolicy.effectiveEndpointContextWindow(small, 6L * GIB, null));
        assertEquals(4096, TaiContextWindowPolicy.effectiveEndpointContextWindow(small, 12L * GIB, null));
    }

    /** "Most": the largest window a setting may select, by RAM class. */
    @Test
    public void ramClasses_capWhatASettingMayChoose() {
        assertEquals(0, TaiContextWindowPolicy.mostCap(0L));
        assertEquals(4096, TaiContextWindowPolicy.mostCap(4L * GIB));
        assertEquals(4096, TaiContextWindowPolicy.mostCap(6L * GIB));
        assertEquals(4096, TaiContextWindowPolicy.mostCap(8L * GIB));
        assertEquals(8192, TaiContextWindowPolicy.mostCap(10L * GIB));
        assertEquals(8192, TaiContextWindowPolicy.mostCap(12L * GIB));
        assertEquals(16_384, TaiContextWindowPolicy.mostCap(16L * GIB));
        assertEquals(16_384, TaiContextWindowPolicy.mostCap(32L * GIB));

        TaiModelSpec gemma = liteRt("gemma-4-e2b-it-litert-lm", 4096, 131_072);
        assertEquals(4096, TaiContextWindowPolicy.effectiveEndpointContextWindow(gemma, 8L * GIB, 32_768));
        assertEquals(8192, TaiContextWindowPolicy.effectiveEndpointContextWindow(gemma, 12L * GIB, 32_768));
        assertEquals(16_384, TaiContextWindowPolicy.effectiveEndpointContextWindow(gemma, 16L * GIB, 32_768));
        assertEquals(8192, TaiContextWindowPolicy.effectiveEndpointContextWindow(gemma, 12L * GIB, 8192));
        // A setting below the cap is kept; the model's own limit still applies.
        assertEquals(2048, TaiContextWindowPolicy.effectiveEndpointContextWindow(gemma, 12L * GIB, 2048));
        TaiModelSpec limited = liteRt("limited", 4096, 4096);
        assertEquals(4096, TaiContextWindowPolicy.effectiveEndpointContextWindow(limited, 16L * GIB, 16_384));
    }

    /** Today MNN is uncapped: Qwen3 0.6B was planned at 32k on pong. It gets the same caps as LiteRT. */
    @Test
    public void mnnModelsGetTheSameCaps() {
        TaiModelSpec qwen = mnn("qwen3-0.6b-mnn", 4096, 40_960);
        assertEquals(4096, TaiContextWindowPolicy.effectiveEndpointContextWindow(qwen, 12L * GIB, null));
        assertEquals(4096, TaiContextWindowPolicy.effectiveEndpointContextWindow(qwen, 24L * GIB, null));
        assertEquals(8192, TaiContextWindowPolicy.effectiveEndpointContextWindow(qwen, 12L * GIB, 32_768));
        assertEquals(16_384, TaiContextWindowPolicy.effectiveEndpointContextWindow(qwen, 16L * GIB, 32_768));
        assertEquals(4096, TaiContextWindowPolicy.effectiveEndpointContextWindow(qwen, 8L * GIB, 32_768));
        TaiModelSpec small = mnn("small-mnn", 1024, 40_960);
        assertEquals(2048, TaiContextWindowPolicy.effectiveEndpointContextWindow(small, 6L * GIB, null));
    }

    @Test
    public void advertisedMemoryJustUnderAMarketingSize_landsInThatClass() {
        // An "8 GB" phone reports ~7.6 GiB; a "12 GB" phone ~11.6 GiB. Neither should drop a class.
        assertEquals(4096, TaiContextWindowPolicy.tierCap(7_782L * GIB / 1024L));
        assertEquals(8192, TaiContextWindowPolicy.mostCap(11_878L * GIB / 1024L));
        assertEquals(2048, TaiContextWindowPolicy.tierCap(5_700L * GIB / 1024L));
        assertEquals(4096, TaiContextWindowPolicy.mostCap(7_782L * GIB / 1024L));
    }

    @Test
    public void neverBelowTheCatalogFloor_andNeverAboveTheSourceWindow() {
        TaiModelSpec coder = mnn("qwen2.5-coder-1.5b-instruct-mnn", 16_384, 32_768);
        assertEquals(16_384, TaiContextWindowPolicy.effectiveEndpointContextWindow(coder, 4L * GIB, null));
        assertEquals(16_384, TaiContextWindowPolicy.effectiveEndpointContextWindow(coder, 16L * GIB, null));

        TaiModelSpec tiny = liteRt("functiongemma-270m-mobile-actions-litert-lm", 1024, 1024);
        assertEquals(1024, TaiContextWindowPolicy.effectiveEndpointContextWindow(tiny, 16L * GIB, null));
    }

    @Test
    public void userSetting_winsOverTheTierEvenWhenSmaller() {
        TaiModelSpec gemma = liteRt("gemma-4-e2b-it-litert-lm", 4096, 32_768);
        assertEquals(2048, TaiContextWindowPolicy.effectiveEndpointContextWindow(gemma, 16L * GIB, 2048));
        assertEquals(1024, TaiContextWindowPolicy.effectiveEndpointContextWindow(gemma, 16L * GIB, 512));
        // Unknown RAM leaves a setting as chosen.
        assertEquals(32_768, TaiContextWindowPolicy.effectiveEndpointContextWindow(gemma, 0L, 32_768));
    }

    @Test
    public void apply_copiesEverythingElseAndKeepsTheSourceWindow() {
        TaiModelSpec gemma = liteRt("gemma-4-e2b-it-litert-lm", 4096, 32_768);
        TaiModelSpec sized = TaiContextWindowPolicy.apply(gemma, 8L * GIB, 4096);
        assertEquals(4096, sized.endpointContextWindow);
        assertEquals(4096, sized.contextWindow);
        assertEquals(32_768, sized.sourceContextWindow);
        assertEquals(gemma.id, sized.id);
        assertEquals(gemma.backend, sized.backend);
        assertEquals(gemma.capabilities, sized.capabilities);
        assertEquals(gemma.defaultMaxOutputTokens, sized.defaultMaxOutputTokens);
    }

    @Test
    public void exportedMedGemmaCacheCapsAutoAndExplicitRequests() {
        TaiModelSpec model = spec("medgemma", TaiModelSpec.BACKEND_LITERT_LM, TaiModelSpec.FORMAT_LITERTLM,
            "/models/medgemma-1.5-4b-it_q4_block32_ekv2048.litertlm", 4096, 131072);
        assertEquals(2048, model.endpointContextWindow);
        assertEquals(2048, TaiContextWindowPolicy.effectiveEndpointContextWindow(model, 16L * GIB, null));
        assertEquals(2048, TaiContextWindowPolicy.effectiveEndpointContextWindow(model, 16L * GIB, 32768));
        assertEquals(0, TaiContextWindowPolicy.artifactContextLimit("/models/other_ekv2048.litertlm"));
    }

    @Test
    public void customArtifactLimitAndMarkersSurviveProfileRoundTrip() throws Exception {
        TaiModelProfile profile = new TaiModelProfile(Collections.singletonList("cpu"), 1024, 64, .95, 1,
            null, "user-artifact-profile", "none", "<start>", "<end>", 2048);
        TaiModelProfile restored = TaiModelProfile.fromJson(profile.toJson());
        assertEquals(2048, restored.maxContextTokens);
        assertEquals("<start>", restored.thinkingChannelStart);
        assertEquals("none", restored.thinkingMode);
    }

    private static TaiModelSpec liteRt(String id, int endpoint, int source) {
        return spec(id, TaiModelSpec.BACKEND_LITERT_LM, TaiModelSpec.FORMAT_LITERTLM, "/models/" + id + ".litertlm", endpoint, source);
    }

    private static TaiModelSpec mnn(String id, int endpoint, int source) {
        return spec(id, TaiModelSpec.BACKEND_MNN_LLM, TaiModelSpec.FORMAT_MNN, "/models/" + id + "/config.json", endpoint, source);
    }

    private static TaiModelSpec spec(String id, String backend, String format, String path, int endpoint, int source) {
        return new TaiModelSpec(id, id, "test", "test", path, "test", 1L,
            new LinkedHashSet<>(Collections.singleton(TaiModelSpec.CAPABILITY_TEXT_CHAT)), true, null,
            backend, format, null, null, endpoint, source, 0, 0, null, null, null);
    }
}
