package com.termux.ai;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.test.core.app.ApplicationProvider;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class TaiModelStoreProcessSafetyTest {
    private Context context;

    @Before
    public void setUp() {
        context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences(TaiSettings.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit();
        context.getSharedPreferences(TaiModelStore.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit();
    }

    @Test
    public void migratedModelRegistry_isIsolatedFromStaleRuntimeSettingsWrites() throws Exception {
        TaiModelSpec model = new TaiModelSpec("imported", "Imported", "chat", "imported",
            "/models/imported/model.litertlm", "test", 1L,
            Collections.singleton(TaiModelSpec.CAPABILITY_TEXT_CHAT), false);
        SharedPreferences legacy = context.getSharedPreferences(TaiSettings.PREFS_NAME, Context.MODE_PRIVATE);
        legacy.edit().putString("tai_user_models_json",
            new JSONArray().put(model.toJson()).toString()).commit();

        TaiModelStore migrated = new TaiModelStore(context);
        assertTrue(migrated.getUserModels().containsKey("imported"));

        // Simulates a stale :tai_runtime SharedPreferences snapshot writing the former shared key.
        legacy.edit().putString("tai_user_models_json", "[]").commit();

        assertTrue(new TaiModelStore(context).getUserModels().containsKey("imported"));
    }

    @Test
    public void legacyQwenImport_selfHealsGpuAndThinkingMetadata() throws Exception {
        TaiModelProfile staleCpu = new TaiModelProfile(Collections.singletonList("cpu"),
            1024, 64, 0.95d, 1.0d, null, "edge-gallery-import-default");
        TaiModelSpec stale = new TaiModelSpec("Qwen3-4B-Thinking-2507", "Qwen3 Thinking",
            "chat", "imported", "/models/Qwen3-4B-Thinking-2507/model.litertlm", "test", 1L,
            Collections.singleton(TaiModelSpec.CAPABILITY_TEXT_CHAT), false, staleCpu,
            TaiModelSpec.BACKEND_LITERT_LM, TaiModelSpec.FORMAT_LITERTLM, null, null,
            4096, 4096, 1024, 0, null,
            Collections.singleton(TaiModelSpec.CAPABILITY_TEXT_CHAT), null);
        context.getSharedPreferences(TaiModelStore.PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean("tai_model_store_migrated_v1", true)
            .putString("tai_user_models_json", new JSONArray().put(stale.toJson()).toString())
            .commit();

        TaiModelSpec repaired = new TaiModelStore(context).getUserModel("Qwen3-4B-Thinking-2507");

        assertTrue(repaired.sourceCapabilities.contains(TaiModelSpec.CAPABILITY_LLM_THINKING));
        assertTrue(repaired.endpointCapabilities.contains(TaiModelSpec.CAPABILITY_LLM_THINKING));
        assertEquals(java.util.Arrays.asList("gpu", "cpu"), repaired.runtimeProfile.compatibleAccelerators);
        assertEquals(2048, repaired.defaultMaxOutputTokens);
    }

    @Test
    public void qwenAutoAcceleration_isGpuFirstWithoutPriorSuccessHistory() {
        TaiModelSpec model = new TaiModelSpec("Qwen3-4B-Thinking-2507", "Qwen3 Thinking",
            "chat", "imported", "/models/Qwen3-4B-Thinking-2507/model.litertlm", "test", 1L,
            Collections.singleton(TaiModelSpec.CAPABILITY_TEXT_CHAT), false);
        TaiDeviceCapabilities device = TaiDeviceCapabilities.createForTest("test", "test", "test",
            35, Collections.singletonList("arm64-v8a"), 8L * 1024L * 1024L * 1024L,
            "test", false);

        java.util.List<String> ordered = TaiLoadPreflight.autoAccelerators(context, model, device,
            TaiModelProfile.forModel(model));

        assertEquals(java.util.Arrays.asList("gpu", "cpu"), ordered);
    }

    @Test
    public void runtimeOptions_roundTripAcrossPrivateRuntimeIpc() throws Exception {
        TaiRuntimeOptions options = new TaiRuntimeOptions(2048, 64, 0.95d, 1.0d, "gpu",
            4096, 4, "fp16", "high", true, false, 10);

        TaiRuntimeOptions restored = TaiRuntimeOptions.fromJson(options.toJson());

        assertEquals("gpu", restored.accelerator);
        assertEquals(Integer.valueOf(2048), restored.maxTokens);
        assertEquals(Boolean.TRUE, restored.thinkingEnabled);
        assertEquals(Integer.valueOf(10), restored.idleUnloadMinutes);
    }
}
