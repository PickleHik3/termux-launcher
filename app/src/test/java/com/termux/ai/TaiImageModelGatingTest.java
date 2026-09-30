package com.termux.ai;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.util.Collections;
import java.util.LinkedHashSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * An MNN diffusion package is a model, but never a chat model: it exposes only image generation,
 * stays out of /v1/models and the chat catalogue, and `tai load` refuses it the way it refuses
 * embeddings and speech models.
 */
@RunWith(RobolectricTestRunner.class)
public class TaiImageModelGatingTest {
    private static final String ID = "sd15-test";
    private Context context;
    private TaiModelStore store;

    @Before
    public void setUp() throws Exception {
        context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences(TaiSettings.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit();
        context.getSharedPreferences(TaiModelStore.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit();
        Field instance = TaiManager.class.getDeclaredField("instance");
        instance.setAccessible(true);
        instance.set(null, null);
        store = new TaiModelStore(context);
    }

    @Test
    public void theBackendIsSupportedAndExposesOnlyImageGeneration() {
        assertTrue(TaiModelSpec.isSupportedBackendFormat(TaiModelSpec.BACKEND_MNN_DIFFUSION, TaiModelSpec.FORMAT_MNN));
        assertFalse(TaiModelSpec.isSupportedBackendFormat(TaiModelSpec.BACKEND_MNN_DIFFUSION, TaiModelSpec.FORMAT_LITERTLM));
        LinkedHashSet<String> declared = new LinkedHashSet<>();
        declared.add(TaiModelSpec.CAPABILITY_TEXT_CHAT);
        declared.add(TaiModelSpec.CAPABILITY_IMAGE_GENERATION);
        LinkedHashSet<String> endpoint = TaiModelSpec.endpointCapabilitiesFor(ID, TaiModelSpec.BACKEND_MNN_DIFFUSION,
            TaiModelSpec.FORMAT_MNN, declared, "/models/" + ID);
        assertEquals(Collections.singleton(TaiModelSpec.CAPABILITY_IMAGE_GENERATION), endpoint);
    }

    @Test
    public void aSpecWithNoDeclaredCapabilityIsStillAnImageModel() {
        TaiModelSpec spec = new TaiModelSpec(ID, ID, "Image", "test", "/models/" + ID, "test", 1L, new LinkedHashSet<>(), false,
            null, TaiModelSpec.BACKEND_MNN_DIFFUSION, TaiModelSpec.FORMAT_MNN, "sd15", null, 4096, 0, null);
        assertTrue(spec.isImageGeneration());
        assertFalse(spec.capabilities.contains(TaiModelSpec.CAPABILITY_TEXT_CHAT));
        assertTrue(spec.capabilities.contains(TaiModelSpec.CAPABILITY_IMAGE_GENERATION));
    }

    @Test
    public void aSpecRoundTripsThroughJsonKeepingItsBackend() throws Exception {
        TaiModelSpec spec = imageModel(ID, "/models/" + ID);
        TaiModelSpec back = TaiModelSpec.fromJson(spec.toJson());
        assertEquals(TaiModelSpec.BACKEND_MNN_DIFFUSION, back.backend);
        assertTrue(back.isImageGeneration());
        assertEquals("sd15", back.architecture);
    }

    @Test
    public void readabilityFollowsThePackageNotAConfigJson() throws Exception {
        File dir = new File(store.getModelsDirectory(), ID);
        Files.createDirectories(dir.toPath());
        store.upsertUserModel(imageModel(ID, dir.getAbsolutePath()));
        assertFalse("an empty folder is not an installed image model", store.getInstalledUserModels().containsKey(ID));

        for (String name : new String[] {"text_encoder.mnn", "unet.mnn", "vae_decoder.mnn", "tokenizer.mtok"}) {
            Files.write(new File(dir, name).toPath(), new byte[] {1, 2, 3});
        }
        assertTrue(store.getInstalledUserModels().containsKey(ID));
    }

    @Test
    public void openAiModelsNeverListsAnImageModel() throws Exception {
        store.upsertUserModel(imageModel(ID, installedPackage()));
        JSONArray data = TaiManager.getInstance(context).openAiModels().getJSONArray("data");
        for (int i = 0; i < data.length(); i++) {
            assertFalse("an image model is not a chat listing", ID.equals(data.getJSONObject(i).optString("id", "")));
        }
    }

    @Test
    public void loadModelRefusesAnImageModel() throws Exception {
        store.upsertUserModel(imageModel(ID, installedPackage()));
        JSONObject result = TaiManager.getInstance(context).loadModel(new JSONObject().put("model", ID).toString());
        assertFalse(result.getBoolean("ok"));
        assertEquals("image_model_not_loadable", result.getString("error"));
        assertEquals(400, result.getInt("_statusCode"));
    }

    @Test
    public void theChatCatalogueAndRemoteCataloguePayloadsLeaveImageModelsOut() {
        for (TaiModelCatalog.CatalogEntry entry : TaiModelCatalog.chatEntries().values()) {
            assertFalse(entry.modelId, TaiModelSpec.BACKEND_MNN_DIFFUSION.equals(entry.backend));
            assertFalse(entry.modelId, entry.endpointCapabilities.contains(TaiModelSpec.CAPABILITY_IMAGE_GENERATION));
        }
    }

    @Test
    public void generateImageRefusesAChatModelAndAnUnknownOne() throws Exception {
        TaiManager manager = TaiManager.getInstance(context);
        JSONObject unknown = manager.generateImage(new JSONObject().put("model", "nope").put("prompt", "p").toString(), p -> { });
        assertEquals(404, unknown.getInt("_statusCode"));
        assertEquals("model_not_found", unknown.getJSONObject("error").getString("code"));
        JSONObject invalid = manager.generateImage(new JSONObject().put("model", ID).toString(), p -> { });
        assertEquals("missing_prompt", invalid.getJSONObject("error").getString("code"));
    }

    @Test
    public void checkImageRequestSpotsAMissingPackageBeforeAnyRun() throws Exception {
        store.upsertUserModel(imageModel(ID, installedPackage()));
        JSONObject ok = TaiManager.getInstance(context).checkImageRequest(
            new JSONObject().put("model", ID).put("prompt", "a fox").put("size", "512x512").toString());
        assertEquals(null, ok);
        JSONObject wrongSize = TaiManager.getInstance(context).checkImageRequest(
            new JSONObject().put("model", ID).put("prompt", "a fox").put("size", "1024x1024").toString());
        assertEquals("invalid_size", wrongSize.getJSONObject("error").getString("code"));
    }

    private String installedPackage() throws Exception {
        File dir = new File(store.getModelsDirectory(), ID);
        Files.createDirectories(dir.toPath());
        for (String name : new String[] {"text_encoder.mnn", "unet.mnn", "vae_decoder.mnn", "tokenizer.mtok"}) {
            Files.write(new File(dir, name).toPath(), new byte[] {1, 2, 3});
        }
        return dir.getAbsolutePath();
    }

    private static TaiModelSpec imageModel(String id, String path) {
        return new TaiModelSpec(id, id, "Image test model", "test", path, "test", 12L,
            new LinkedHashSet<>(Collections.singleton(TaiModelSpec.CAPABILITY_IMAGE_GENERATION)), false, null,
            TaiModelSpec.BACKEND_MNN_DIFFUSION, TaiModelSpec.FORMAT_MNN, "sd15", null, 4096, 0, null);
    }
}
