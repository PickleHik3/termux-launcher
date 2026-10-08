package com.termux.ai;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/** The user's stored Parameters values as the feature load plan reads them. */
public class TaiFeaturePlansTest {
    private static final String MODEL = "user-chat";
    private static final String BACKEND = TaiModelSpec.BACKEND_MNN_LLM;

    private final Map<String, Object> stored = new HashMap<>();

    private TaiFeaturePlan.Parameters parameters() {
        return TaiFeaturePlans.parametersOf((backend, modelId, field) ->
            BACKEND.equals(backend) && MODEL.equals(modelId) ? stored.get(field) : null);
    }

    private String accelerator(Object value) {
        stored.put(TaiSettings.FIELD_ACCELERATOR, value);
        return parameters().accelerator(MODEL, BACKEND);
    }

    private Boolean speculative(Object value) {
        stored.put(TaiSettings.FIELD_ENABLE_SPECULATIVE_DECODING, value);
        return parameters().speculative(MODEL, BACKEND);
    }

    @Test
    public void theStoredAcceleratorReadsAsGpuOrCpu() {
        // MNN stores OpenCL, which is its GPU.
        assertEquals(TaiTierPolicy.ACCEL_GPU, accelerator("OpenCL"));
        assertEquals(TaiTierPolicy.ACCEL_GPU, accelerator("GPU"));
        assertEquals(TaiTierPolicy.ACCEL_CPU, accelerator("CPU"));
        // Automatic, nothing stored, or anything else is no pick.
        assertNull(accelerator("Auto"));
        assertNull(accelerator(null));
        assertNull(accelerator("NPU"));
    }

    @Test
    public void onlyAStoredBooleanIsASpeculativePick() {
        assertEquals(Boolean.TRUE, speculative(Boolean.TRUE));
        assertEquals(Boolean.FALSE, speculative(Boolean.FALSE));
        assertNull(speculative("true"));
        assertNull(speculative(1));
        assertNull(speculative(null));
    }

    @Test
    public void valuesAreReadForTheModelFileAsked() {
        stored.put(TaiSettings.FIELD_ACCELERATOR, "CPU");
        stored.put(TaiSettings.FIELD_ENABLE_SPECULATIVE_DECODING, Boolean.TRUE);
        assertNull(parameters().accelerator("another-model", BACKEND));
        assertNull(parameters().speculative(MODEL, TaiModelSpec.BACKEND_LITERT_LM));
    }
}
