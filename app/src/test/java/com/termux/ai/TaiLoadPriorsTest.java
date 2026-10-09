package com.termux.ai;

import static org.junit.Assert.assertEquals;

import java.util.Collections;
import java.util.LinkedHashSet;

import org.junit.Test;

/** The shipped Gemma 4 load priors: what the budget plans on before this phone has measured a load. */
public class TaiLoadPriorsTest {

    private static final long MIB = 1024L * 1024L;
    private static final long SLOPE = 136_000L;

    @Test
    public void e2bOnTheGpuReadsPongsWorstMeasuredDrop() {
        TaiModelSpec e2b = spec(TaiModelRegistry.MODEL_GEMMA_4_E2B_IT, TaiModelSpec.BACKEND_LITERT_LM);
        assertEquals(1477L * MIB, TaiLoadPriors.bytes(e2b, "gpu", 4096, SLOPE));
        assertEquals(1319L * MIB, TaiLoadPriors.bytes(e2b, "gpu", 2048, SLOPE));
        // A window between two measured ones takes the one above it: an upper bound.
        assertEquals(1319L * MIB, TaiLoadPriors.bytes(e2b, "gpu", 1500, SLOPE));
        assertEquals(1235L * MIB, TaiLoadPriors.bytes(e2b, "gpu", 1024, SLOPE));
    }

    @Test
    public void aboveTheLargestWindowTheSlopeCarriesItUp() {
        TaiModelSpec e4b = spec(TaiModelRegistry.MODEL_GEMMA_4_E4B_IT, TaiModelSpec.BACKEND_LITERT_LM);
        assertEquals(3292L * MIB + SLOPE * 4096L, TaiLoadPriors.bytes(e4b, "gpu", 8192, SLOPE));
    }

    @Test
    public void theVisionVariantSharesTheFilesRow() {
        TaiModelSpec vision = spec(TaiModelRegistry.MODEL_GEMMA_4_E4B_IT + "-vision", TaiModelSpec.BACKEND_LITERT_LM);
        assertEquals(3166L * MIB, TaiLoadPriors.bytes(vision, "GPU", 2048, SLOPE));
    }

    @Test
    public void nothingIsKnownForTheCpuOtherModelsOrOtherBackends() {
        TaiModelSpec e2b = spec(TaiModelRegistry.MODEL_GEMMA_4_E2B_IT, TaiModelSpec.BACKEND_LITERT_LM);
        assertEquals(0L, TaiLoadPriors.bytes(e2b, "cpu", 4096, SLOPE));
        assertEquals(0L, TaiLoadPriors.bytes(spec("some-other-model", TaiModelSpec.BACKEND_LITERT_LM), "gpu", 4096, SLOPE));
        assertEquals(0L, TaiLoadPriors.bytes(spec(TaiModelRegistry.MODEL_GEMMA_4_E2B_IT, TaiModelSpec.BACKEND_MNN_LLM), "gpu", 4096, SLOPE));
    }

    private static TaiModelSpec spec(String id, String backend) {
        String format = TaiModelSpec.BACKEND_MNN_LLM.equals(backend) ? TaiModelSpec.FORMAT_MNN : TaiModelSpec.FORMAT_LITERTLM;
        return new TaiModelSpec(id, id, "Test model", "test", "/models/" + id + "/model.litertlm", "test",
            2_588_147_712L, new LinkedHashSet<>(Collections.singleton(TaiModelSpec.CAPABILITY_TEXT_CHAT)),
            false, null, backend, format, null, null, 4096, 0, null);
    }
}
