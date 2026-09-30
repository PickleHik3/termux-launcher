package com.termux.ai;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * {@link TaiManager#generateImage} end to end with the native engine stood in: validation, admission,
 * progress, the PNG handed back inline or moved to the requested path, and the discard on a dropped client.
 */
@RunWith(RobolectricTestRunner.class)
public class TaiImageGenerationFlowTest {
    private static final byte[] PNG = "fake-png-bytes".getBytes(StandardCharsets.UTF_8);
    @Rule public TemporaryFolder temp = new TemporaryFolder();
    private Context context;
    private TaiModelStore store;
    private TaiManager manager;
    private FakeImageRuntime runtime;

    @Before
    public void setUp() throws Exception {
        context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences(TaiSettings.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit();
        context.getSharedPreferences(TaiModelStore.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit();
        Field instance = TaiManager.class.getDeclaredField("instance");
        instance.setAccessible(true);
        instance.set(null, null);
        store = new TaiModelStore(context);
        manager = TaiManager.getInstance(context);
        Field runtimeField = TaiManager.class.getDeclaredField("runtime");
        runtimeField.setAccessible(true);
        runtime = new FakeImageRuntime(context);
        runtimeField.set(manager, runtime);
        TaiManager.setImportPathAllowedRootForTesting(temp.getRoot().getCanonicalPath());
    }

    @After
    public void tearDown() {
        TaiManager.setImportPathAllowedRootForTesting(null);
    }

    @Test
    public void aRegisteredModelComesBackAsBase64AndTheIpcFileIsGone() throws Exception {
        store.upsertUserModel(imageModel("sd15-test", sdPackage("sd15-test").getAbsolutePath()));
        List<Integer> progress = new ArrayList<>();
        JSONObject response = manager.generateImage(new JSONObject().put("model", "sd15-test").put("prompt", "a fox").toString(),
            progress::add);
        String b64 = response.getJSONArray("data").getJSONObject(0).getString("b64_json");
        assertEquals("fake-png-bytes", new String(Base64.getDecoder().decode(b64), StandardCharsets.UTF_8));
        assertTrue(response.getLong("created") > 0L);
        JSONObject tai = response.getJSONObject("tai");
        assertEquals(1, tai.getInt("memoryMode"));
        assertEquals(512, tai.getInt("width"));
        assertEquals(20, tai.getInt("steps"));
        assertEquals("sd15-test", tai.getString("model"));
        assertEquals(java.util.Arrays.asList(0, 50), progress);
        assertFalse("the runtime's PNG is removed once read", runtime.lastOutput.exists());
        assertEquals(TaiDiffusionPackage.TYPE_SD15, runtime.lastParams.type);
        assertEquals(0f, runtime.lastParams.cfgScale, 0f);
    }

    @Test
    public void anOutputPathReceivesTheFileAndNoBase64Comes() throws Exception {
        File out = new File(temp.getRoot(), "shots/fox.png");
        store.upsertUserModel(imageModel("sd15-test", sdPackage("sd15-test").getAbsolutePath()));
        JSONObject response = manager.generateImage(new JSONObject().put("model", "sd15-test").put("prompt", "a fox")
            .put("output", out.getAbsolutePath()).toString(), p -> { });
        JSONObject item = response.getJSONArray("data").getJSONObject(0);
        assertFalse(item.has("b64_json"));
        assertEquals(out.getCanonicalPath(), item.getString("path"));
        assertEquals("fake-png-bytes", new String(Files.readAllBytes(out.toPath()), StandardCharsets.UTF_8));
    }

    @Test
    public void aModelPathRunsWithoutRegistration() throws Exception {
        File dir = sdPackage("loose");
        JSONObject response = manager.generateImage(new JSONObject().put("model_path", dir.getAbsolutePath())
            .put("prompt", "a fox").put("model_type", "taiyi").toString(), p -> { });
        assertTrue(response.has("data"));
        assertEquals(TaiDiffusionPackage.TYPE_TAIYI, runtime.lastParams.type);
        assertEquals(dir.getCanonicalPath(), runtime.lastParams.modelDir);
        assertEquals("local-image-loose", response.getJSONObject("tai").getString("model"));
    }

    @Test
    public void aModelPathOutsideTheAllowedRootsIsForbidden() throws Exception {
        JSONObject response = manager.generateImage(new JSONObject().put("model_path", "/proc/self").put("prompt", "p").toString(), p -> { });
        assertEquals(403, response.getInt("_statusCode"));
        assertEquals("media_access_denied", response.getJSONObject("error").getString("code"));
        assertEquals(0, runtime.runs);
    }

    @Test
    public void aRawTokenizerPackageIsRefusedBeforeAnyRun() throws Exception {
        File dir = sdPackage("raw");
        assertTrue(new File(dir, "tokenizer.mtok").delete());
        Files.write(new File(dir, "vocab.json").toPath(), new byte[] {1});
        Files.write(new File(dir, "merges.txt").toPath(), new byte[] {1});
        JSONObject response = manager.generateImage(new JSONObject().put("model_path", dir.getAbsolutePath()).put("prompt", "p").toString(), p -> { });
        assertEquals(400, response.getInt("_statusCode"));
        assertEquals("tokenizer_mtok_missing", response.getJSONObject("error").getString("code"));
        assertEquals(0, runtime.runs);
    }

    @Test
    public void aChatOrSpeechSizedRequestForAnSdModelIsRefusedOnSize() throws Exception {
        store.upsertUserModel(imageModel("sd15-test", sdPackage("sd15-test").getAbsolutePath()));
        JSONObject response = manager.generateImage(new JSONObject().put("model", "sd15-test").put("prompt", "p")
            .put("size", "1024x1024").toString(), p -> { });
        assertEquals("invalid_size", response.getJSONObject("error").getString("code"));
        assertEquals(0, runtime.runs);
    }

    @Test
    public void aForcedMemoryModeIsPassedThrough() throws Exception {
        File dir = sdPackage("loose");
        manager.generateImage(new JSONObject().put("model_path", dir.getAbsolutePath()).put("prompt", "p")
            .put("memory_mode", 0).put("backend", "cpu").put("seed", 7).put("steps", 5).toString(), p -> { });
        assertEquals(0, runtime.lastParams.memoryMode);
        assertEquals("cpu", runtime.lastParams.backend);
        assertEquals(7, runtime.lastParams.seed);
        assertEquals(5, runtime.lastParams.steps);
    }

    @Test
    public void sanaGetsItsDefaultGuidanceAndSizeRules() throws Exception {
        File dir = sanaPackage("sana-loose");
        manager.generateImage(new JSONObject().put("model_path", dir.getAbsolutePath()).put("prompt", "p")
            .put("size", "768x512").toString(), p -> { });
        assertEquals(TaiDiffusionPackage.TYPE_SANA, runtime.lastParams.type);
        assertEquals(768, runtime.lastParams.width);
        assertEquals(TaiImageRequest.DEFAULT_SANA_CFG, runtime.lastParams.cfgScale, 0f);
        JSONObject bad = manager.generateImage(new JSONObject().put("model_path", dir.getAbsolutePath()).put("prompt", "p")
            .put("size", "700x512").toString(), p -> { });
        assertEquals("invalid_size", bad.getJSONObject("error").getString("code"));
    }

    @Test
    public void aClientThatGoesAwayCancelsAndTheImageIsDiscarded() throws Exception {
        File dir = sdPackage("loose");
        try {
            manager.generateImage(new JSONObject().put("model_path", dir.getAbsolutePath()).put("prompt", "p").toString(), percent -> {
                throw new IOException("client gone");
            });
            fail("the IOException must surface");
        } catch (IOException expected) {
            assertEquals("client gone", expected.getMessage());
        }
        assertTrue(runtime.cancelRequested);
        assertFalse(runtime.lastOutput.exists());
    }

    @Test
    public void aRuntimeErrorPassesThroughAsTheResponse() throws Exception {
        runtime.fail = true;
        File dir = sdPackage("loose");
        JSONObject response = manager.generateImage(new JSONObject().put("model_path", dir.getAbsolutePath()).put("prompt", "p").toString(), p -> { });
        assertEquals(409, response.getInt("_statusCode"));
        assertEquals("image_generation_active", response.getJSONObject("error").getString("code"));
    }

    @Test
    public void cancelReportsWhetherAnythingWasRunning() throws Exception {
        assertFalse(manager.cancelImage().getBoolean("cancelled"));
        assertTrue(manager.cancelImage().getBoolean("ok"));
    }

    private File sdPackage(String name) throws Exception {
        File dir = new File(temp.getRoot(), name);
        Files.createDirectories(dir.toPath());
        for (String file : new String[] {"text_encoder.mnn", "unet.mnn", "vae_decoder.mnn", "tokenizer.mtok"}) {
            Files.write(new File(dir, file).toPath(), new byte[] {1, 2, 3});
        }
        return dir;
    }

    private File sanaPackage(String name) throws Exception {
        File dir = new File(temp.getRoot(), name);
        File llm = new File(dir, "llm");
        Files.createDirectories(llm.toPath());
        for (String file : new String[] {"connector.mnn", "projector.mnn", "transformer.mnn", "vae_decoder.mnn",
                "llm/meta_queries.mnn", "llm/llm.mnn", "llm/llm.mnn.weight", "llm/tokenizer.txt"}) {
            Files.write(new File(dir, file).toPath(), new byte[] {1, 2, 3});
        }
        Files.write(new File(llm, "config.json").toPath(), "{}".getBytes(StandardCharsets.UTF_8));
        return dir;
    }

    private static TaiModelSpec imageModel(String id, String path) {
        return new TaiModelSpec(id, id, "Image test model", "test", path, "test", 12L,
            new LinkedHashSet<>(Collections.singleton(TaiModelSpec.CAPABILITY_IMAGE_GENERATION)), false, null,
            TaiModelSpec.BACKEND_MNN_DIFFUSION, TaiModelSpec.FORMAT_MNN, "sd15", null, 4096, 0, null);
    }

    /** The router with the native diffusion engine replaced: it records the run and writes a fake PNG. */
    private static final class FakeImageRuntime extends MultiBackendTaiRuntime {
        MnnDiffusionRuntime.Params lastParams;
        File lastOutput;
        int runs;
        boolean cancelRequested;
        boolean fail;

        FakeImageRuntime(Context context) {
            super(context);
        }

        @Override
        public JSONObject generateImage(MnnDiffusionRuntime.Params params, MnnDiffusionRuntime.Progress progress) throws JSONException {
            runs++;
            lastParams = params;
            lastOutput = params.output;
            if (fail) return MnnDiffusionRuntime.error(409, "image_generation_active", "An image is already being generated.");
            progress.onProgress(50);
            try {
                Files.write(params.output.toPath(), PNG);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
            JSONObject out = new JSONObject();
            out.put("file", params.output.getAbsolutePath());
            out.put("width", params.width);
            out.put("height", params.height);
            out.put("steps", params.steps);
            out.put("seed", params.seed >= 0 ? params.seed : 1234);
            out.put("backend", params.backend);
            out.put("memoryMode", params.memoryMode);
            out.put("modelType", TaiDiffusionPackage.typeName(params.type));
            out.put("loadMs", 10);
            out.put("generateMs", 20);
            return out;
        }

        @Override
        public boolean cancelImage() {
            cancelRequested = true;
            return super.cancelImage();
        }
    }
}
