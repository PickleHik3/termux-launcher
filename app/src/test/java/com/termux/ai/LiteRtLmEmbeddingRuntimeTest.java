package com.termux.ai;

import org.json.JSONObject;
import org.junit.Test;

import java.io.File;
import java.util.Collections;
import java.util.LinkedHashSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** The checks {@link LiteRtLmEmbeddingRuntime} makes before it ever builds the native engine. */
public class LiteRtLmEmbeddingRuntimeTest {
    @Test
    public void embed_dimensionsWiderThanTheModel_isRefusedBeforeTheEngineLoads() throws Exception {
        File model = File.createTempFile("embeddinggemma-2-text-270m", ".litertlm");
        model.deleteOnExit();
        LiteRtLmEmbeddingRuntime runtime = new LiteRtLmEmbeddingRuntime(new TaiResidency());

        JSONObject result = runtime.embed(spec(model.getAbsolutePath()), Collections.singletonList("hello"), 1024,
            LiteRtEmbeddingRuntime.INPUT_TYPE_DOCUMENT, null, false);

        assertEquals(400, result.getInt("_statusCode"));
        assertEquals("invalid_dimensions", result.getJSONObject("error").getString("code"));
        assertNull("no engine was built", runtime.loadedModelId());
    }

    @Test
    public void embed_missingModelFile_is404ModelFileMissing() throws Exception {
        LiteRtLmEmbeddingRuntime runtime = new LiteRtLmEmbeddingRuntime(new TaiResidency());
        String missing = new File(System.getProperty("java.io.tmpdir"),
            "no-such-dir-" + System.nanoTime() + "/embeddinggemma-2-text-270m.litertlm").getAbsolutePath();

        JSONObject result = runtime.embed(spec(missing), Collections.singletonList("hello"), 0,
            LiteRtEmbeddingRuntime.INPUT_TYPE_QUERY, null, false);

        assertEquals(404, result.getInt("_statusCode"));
        assertEquals("model_file_missing", result.getJSONObject("error").getString("code"));
        assertNull(runtime.loadedModelId());
    }

    @Test
    public void shapeVector_truncatesToTheRequestedSizeAndNormalises() {
        float[] shaped = LiteRtLmEmbeddingRuntime.shapeVector(new float[] {3f, 4f, 12f}, 2);

        assertEquals(2, shaped.length);
        assertEquals(0.6f, shaped[0], 1e-6f);
        assertEquals(0.8f, shaped[1], 1e-6f);
    }

    @Test
    public void namesOutputSize_recognisesTheEnginesOutputSizeComplaintOnly() {
        assertTrue(LiteRtLmEmbeddingRuntime.namesOutputSize(new IllegalArgumentException("Invalid output_size: 1000")));
        assertTrue(LiteRtLmEmbeddingRuntime.namesOutputSize(
            new RuntimeException("wrapped", new IllegalStateException("Output size must be one of 768, 512"))));
        assertFalse(LiteRtLmEmbeddingRuntime.namesOutputSize(new RuntimeException("input too long")));
        assertFalse(LiteRtLmEmbeddingRuntime.namesOutputSize(new RuntimeException()));
    }

    private static TaiModelSpec spec(String path) {
        return new TaiModelSpec(
            "embeddinggemma-2-text-270m",
            "EmbeddingGemma 2 Text 270M",
            "Test embedder",
            "test",
            path,
            "Apache-2.0",
            0L,
            new LinkedHashSet<>(Collections.singleton(TaiModelSpec.CAPABILITY_TEXT_EMBEDDINGS)),
            false,
            null,
            TaiModelSpec.BACKEND_LITERT_LM,
            TaiModelSpec.FORMAT_LITERTLM,
            null,
            null,
            LiteRtLmEmbeddingRuntime.MAX_INPUT_TOKENS,
            0,
            null
        );
    }
}
