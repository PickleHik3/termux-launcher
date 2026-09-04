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

    @Test
    public void ramTiers_raiseTheWindowUpToTheModelsOwnLimit() {
        TaiModelSpec gemma = liteRt("gemma-4-e2b-it-litert-lm", 4096, 32_768);
        assertEquals(4096, TaiContextWindowPolicy.effectiveEndpointContextWindow(gemma, 4L * GIB, null));
        assertEquals(8192, TaiContextWindowPolicy.effectiveEndpointContextWindow(gemma, 6L * GIB, null));
        assertEquals(16_384, TaiContextWindowPolicy.effectiveEndpointContextWindow(gemma, 8L * GIB, null));
        assertEquals(32_768, TaiContextWindowPolicy.effectiveEndpointContextWindow(gemma, 12L * GIB, null));
        assertEquals(32_768, TaiContextWindowPolicy.effectiveEndpointContextWindow(gemma, 24L * GIB, null));
    }

    @Test
    public void advertisedMemoryJustUnderAMarketingSize_landsInThatTier() {
        // An "8 GB" phone reports ~7.6 GiB; a "12 GB" phone ~11.6 GiB. Neither should drop a tier.
        TaiModelSpec gemma = liteRt("gemma-4-e2b-it-litert-lm", 4096, 32_768);
        assertEquals(16_384, TaiContextWindowPolicy.effectiveEndpointContextWindow(gemma, 7_782L * GIB / 1024L, null));
        assertEquals(32_768, TaiContextWindowPolicy.effectiveEndpointContextWindow(gemma, 11_878L * GIB / 1024L, null));
    }

    @Test
    public void neverBelowTheCatalogFloor_andNeverAboveTheSourceWindow() {
        TaiModelSpec coder = mnn("qwen2.5-coder-1.5b-instruct-mnn", 16_384, 32_768);
        assertEquals(16_384, TaiContextWindowPolicy.effectiveEndpointContextWindow(coder, 4L * GIB, null));
        assertEquals(32_768, TaiContextWindowPolicy.effectiveEndpointContextWindow(coder, 16L * GIB, null));

        TaiModelSpec tiny = liteRt("functiongemma-270m-mobile-actions-litert-lm", 1024, 1024);
        assertEquals(1024, TaiContextWindowPolicy.effectiveEndpointContextWindow(tiny, 16L * GIB, null));
    }

    @Test
    public void userSetting_winsOverTheTierEvenWhenSmaller() {
        TaiModelSpec gemma = liteRt("gemma-4-e2b-it-litert-lm", 4096, 32_768);
        assertEquals(2048, TaiContextWindowPolicy.effectiveEndpointContextWindow(gemma, 16L * GIB, 2048));
        assertEquals(1024, TaiContextWindowPolicy.effectiveEndpointContextWindow(gemma, 16L * GIB, 512));
        assertEquals(32_768, TaiContextWindowPolicy.effectiveEndpointContextWindow(gemma, 0L, 32_768));
    }

    @Test
    public void apply_copiesEverythingElseAndKeepsTheSourceWindow() {
        TaiModelSpec gemma = liteRt("gemma-4-e2b-it-litert-lm", 4096, 32_768);
        TaiModelSpec sized = TaiContextWindowPolicy.apply(gemma, 8L * GIB, null);
        assertEquals(16_384, sized.endpointContextWindow);
        assertEquals(16_384, sized.contextWindow);
        assertEquals(32_768, sized.sourceContextWindow);
        assertEquals(gemma.id, sized.id);
        assertEquals(gemma.backend, sized.backend);
        assertEquals(gemma.capabilities, sized.capabilities);
        assertEquals(gemma.defaultMaxOutputTokens, sized.defaultMaxOutputTokens);
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
