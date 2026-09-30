package com.termux.ai;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** The request shape and the per-type size rules; paths go through a stand-in for the allowed-roots policy. */
public class TaiImageRequestTest {
    private static final TaiImageRequest.PathResolver ROOTED = raw -> {
        if (!raw.startsWith("/home/")) throw new JSONException("media_access_denied:Local media must live under /home");
        return raw;
    };

    private static TaiImageRequest parse(JSONObject body) {
        return TaiImageRequest.parse(body, ROOTED);
    }

    private static JSONObject body() throws JSONException {
        return new JSONObject().put("prompt", "a red fox").put("model", "sd15-local");
    }

    @Test
    public void aMinimalRequestParsesWithEveryDefaultOpen() throws Exception {
        TaiImageRequest r = parse(body());
        assertTrue(r.isValid());
        assertEquals("a red fox", r.prompt);
        assertEquals("sd15-local", r.modelId);
        assertNull(r.modelPath);
        assertEquals(TaiImageRequest.BACKEND_OPENCL, r.backend);
        assertEquals(TaiImageRequest.MEMORY_MODE_AUTO, r.memoryMode);
        assertEquals(-1, r.seed);
        assertFalse(r.stream);
        assertEquals(512, r.widthFor(TaiDiffusionPackage.TYPE_SD15));
        assertEquals(20, r.stepsFor(TaiDiffusionPackage.TYPE_SD15));
        assertEquals(0f, r.cfgScaleFor(TaiDiffusionPackage.TYPE_SD15), 0f);
        assertEquals(TaiImageRequest.DEFAULT_SANA_CFG, r.cfgScaleFor(TaiDiffusionPackage.TYPE_SANA), 0f);
    }

    @Test
    public void aPromptIsRequiredAndBounded() throws Exception {
        assertEquals("missing_prompt", parse(new JSONObject().put("model", "m")).errorCode);
        assertEquals("missing_prompt", parse(body().put("prompt", "   ")).errorCode);
        StringBuilder longPrompt = new StringBuilder();
        for (int i = 0; i < TaiImageRequest.MAX_PROMPT_CHARS + 1; i++) longPrompt.append('a');
        assertEquals("prompt_too_long", parse(body().put("prompt", longPrompt.toString())).errorCode);
    }

    @Test
    public void onlyOneImagePerRequest() throws Exception {
        assertTrue(parse(body().put("n", 1)).isValid());
        TaiImageRequest r = parse(body().put("n", 2));
        assertEquals("unsupported_n", r.errorCode);
        assertEquals(400, r.statusCode);
    }

    @Test
    public void exactlyOneOfModelAndModelPath() throws Exception {
        JSONObject both = body().put("model_path", "/home/x/model");
        assertEquals("ambiguous_model", parse(both).errorCode);
        JSONObject neither = new JSONObject().put("prompt", "p");
        assertEquals("missing_model", parse(neither).errorCode);
        TaiImageRequest byPath = parse(new JSONObject().put("prompt", "p").put("model_path", "/home/x/model"));
        assertTrue(byPath.isValid());
        assertEquals("/home/x/model", byPath.modelPath);
        assertNull(byPath.modelId);
    }

    @Test
    public void pathsOutsideTheAllowedRootsAreRefusedAsForbidden() throws Exception {
        TaiImageRequest model = parse(new JSONObject().put("prompt", "p").put("model_path", "/data/data/x"));
        assertEquals("media_access_denied", model.errorCode);
        assertEquals(403, model.statusCode);
        assertEquals("model_path", model.errorParam);
        assertEquals("image", parse(body().put("image", "/etc/passwd")).errorParam);
        assertEquals("output", parse(body().put("output", "/tmp/a.png")).errorParam);
    }

    @Test
    public void outputMustBeAPng() throws Exception {
        assertEquals("invalid_output", parse(body().put("output", "/home/x/a.jpg")).errorCode);
        TaiImageRequest ok = parse(body().put("output", "/home/x/a.PNG"));
        assertTrue(ok.isValid());
        assertEquals("/home/x/a.PNG", ok.outputPath);
    }

    @Test
    public void sizeMustLookLikeWidthByHeight() throws Exception {
        assertEquals("invalid_size", parse(body().put("size", "big")).errorCode);
        assertEquals("invalid_size", parse(body().put("size", "512x")).errorCode);
        assertEquals("invalid_size", parse(body().put("size", "0x512")).errorCode);
        TaiImageRequest r = parse(body().put("size", "768X512"));
        assertTrue(r.isValid());
        assertEquals(768, r.width);
        assertEquals(512, r.height);
    }

    @Test
    public void stableDiffusionAndTaiyiAreFixedAt512() throws Exception {
        for (int type : new int[] {TaiDiffusionPackage.TYPE_SD15, TaiDiffusionPackage.TYPE_TAIYI}) {
            assertNull(TaiImageRequest.validateForPackage(parse(body()), type, false));
            assertNull(TaiImageRequest.validateForPackage(parse(body().put("size", "512x512")), type, false));
            TaiImageRequest.Refusal wide = TaiImageRequest.validateForPackage(parse(body().put("size", "768x512")), type, false);
            assertNotNull(wide);
            assertEquals("invalid_size", wide.code);
        }
    }

    @Test
    public void sanaTakesMultiplesOf32BetweenTheBounds() throws Exception {
        int sana = TaiDiffusionPackage.TYPE_SANA;
        assertNull(TaiImageRequest.validateForPackage(parse(body().put("size", "1024x768")), sana, true));
        assertNull(TaiImageRequest.validateForPackage(parse(body().put("size", "256x2048")), sana, true));
        assertEquals("invalid_size", TaiImageRequest.validateForPackage(parse(body().put("size", "1000x768")), sana, true).code);
        assertEquals("invalid_size", TaiImageRequest.validateForPackage(parse(body().put("size", "224x512")), sana, true).code);
        assertEquals("invalid_size", TaiImageRequest.validateForPackage(parse(body().put("size", "2080x512")), sana, true).code);
    }

    @Test
    public void imageInputIsSanaOnlyAndNeedsItsEncoder() throws Exception {
        TaiImageRequest edit = parse(body().put("image", "/home/x/in.png"));
        assertEquals("/home/x/in.png", edit.inputImage);
        assertNull(TaiImageRequest.validateForPackage(edit, TaiDiffusionPackage.TYPE_SANA, true));
        assertEquals("image_input_unavailable", TaiImageRequest.validateForPackage(edit, TaiDiffusionPackage.TYPE_SANA, false).code);
        assertEquals("image_input_unavailable", TaiImageRequest.validateForPackage(edit, TaiDiffusionPackage.TYPE_SD15, false).code);
    }

    @Test
    public void stepsSeedCfgBackendAndMemoryModeAreChecked() throws Exception {
        TaiImageRequest r = parse(body().put("steps", 8).put("seed", 42).put("cfg_scale", 0).put("backend", "CPU").put("memory_mode", 2));
        assertTrue(r.isValid());
        assertEquals(8, r.stepsFor(TaiDiffusionPackage.TYPE_SANA));
        assertEquals(42, r.seed);
        assertEquals(0f, r.cfgScaleFor(TaiDiffusionPackage.TYPE_SANA), 0f);
        assertEquals(TaiImageRequest.BACKEND_CPU, r.backend);
        assertEquals(2, r.memoryMode);
        assertEquals("invalid_steps", parse(body().put("steps", 0)).errorCode);
        assertEquals("invalid_steps", parse(body().put("steps", 101)).errorCode);
        assertEquals("invalid_cfg_scale", parse(body().put("cfg_scale", -1)).errorCode);
        assertEquals("invalid_backend", parse(body().put("backend", "vulkan")).errorCode);
        assertEquals("invalid_memory_mode", parse(body().put("memory_mode", 3)).errorCode);
        assertEquals("invalid_model_type", parse(body().put("model_type", "sd35")).errorCode);
        assertEquals(TaiDiffusionPackage.TYPE_TAIYI, parse(body().put("model_type", "taiyi")).typeHint);
    }

    @Test
    public void onlyBase64ResponsesAreOffered() throws Exception {
        assertTrue(parse(body().put("response_format", "b64_json")).isValid());
        assertEquals("unsupported_response_format", parse(body().put("response_format", "url")).errorCode);
    }

    @Test
    public void streamIsRead() throws Exception {
        assertTrue(parse(body().put("stream", true)).stream);
    }
}
