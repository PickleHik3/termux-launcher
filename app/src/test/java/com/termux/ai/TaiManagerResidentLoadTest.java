package com.termux.ai;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.lang.reflect.Field;
import java.util.Collections;
import java.util.LinkedHashSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * An explicit load the runtime would refuse does not refuse a model that is already resident: the
 * caller gets the resident model as it is, marked {@code reused}, as a generation's own load does. With
 * nothing resident the refusal still comes back. Runs as the runtime process would, so the preflight
 * and the memory budget are consulted rather than skipped by the injected-runtime shortcut.
 */
@RunWith(RobolectricTestRunner.class)
public class TaiManagerResidentLoadTest {
    private static final String MODEL = "resident-chat";

    private TaiManager manager;
    private StubRuntime runtime;

    @Before
    public void setUp() throws Exception {
        Context context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences(TaiSettings.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit();
        context.getSharedPreferences(TaiModelStore.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit();
        Field instance = TaiManager.class.getDeclaredField("instance");
        instance.setAccessible(true);
        instance.set(null, null);
        // No file behind the registered path, so the preflight always blocks the load.
        new TaiModelStore(context).upsertUserModel(chatModel(MODEL, "/nonexistent/tai-test/" + MODEL + ".litertlm"));
        manager = TaiManager.getInstance(context);
        runtime = new StubRuntime();
        setField(manager, "runtimeProcess", true);
        setField(manager, "runtime", runtime);
    }

    @Test
    public void aBlockedLoadOfTheResidentModelReturnsItReused() throws Exception {
        runtime.loadedModelId = MODEL;

        JSONObject result = manager.loadModel(new JSONObject().put("model", MODEL).toString());

        assertTrue(result.toString(), result.getBoolean("ok"));
        assertTrue(result.getBoolean("reused"));
        assertEquals(MODEL, result.getString("modelId"));
        assertEquals(0, runtime.loads);
    }

    @Test
    public void aBlockedLoadWithNothingResidentIsStillRefused() throws Exception {
        JSONObject result = manager.loadModel(new JSONObject().put("model", MODEL).toString());

        assertFalse(result.toString(), result.optBoolean("ok", false));
        assertFalse(result.optBoolean("reused", false));
        assertEquals(0, runtime.loads);
    }

    @Test
    public void aFreshLoadOfTheResidentModelIsStillRefused() throws Exception {
        runtime.loadedModelId = MODEL;

        JSONObject result = manager.loadModel(new JSONObject().put("model", MODEL).put("clearCache", true).toString());

        assertFalse(result.toString(), result.optBoolean("ok", false));
        assertEquals(0, runtime.loads);
    }

    private static TaiModelSpec chatModel(String id, String path) {
        return new TaiModelSpec(
            id,
            id,
            "Test chat model",
            "test",
            path,
            "test",
            123L,
            new LinkedHashSet<>(Collections.singleton(TaiModelSpec.CAPABILITY_TEXT_CHAT)),
            false,
            null,
            TaiModelSpec.BACKEND_LITERT_LM,
            TaiModelSpec.FORMAT_LITERTLM,
            null,
            null,
            4096,
            0,
            null
        );
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static final class StubRuntime implements TaiRuntime {
        volatile String loadedModelId;
        int loads;

        @Override public TaiRuntimeState getState() {
            return new TaiRuntimeState(loadedModelId != null, loadedModelId, "stub", "Stub.");
        }
        @Override public boolean isModelLoaded(String modelId) { return modelId.equals(loadedModelId); }
        @Override public JSONObject load(TaiModelSpec modelSpec, TaiRuntimeOptions options) throws JSONException {
            loads++;
            loadedModelId = modelSpec.id;
            return ok();
        }
        @Override public JSONObject unload() throws JSONException { loadedModelId = null; return ok(); }
        @Override public JSONObject keepWarm(TaiModelSpec modelSpec, TaiRuntimeOptions options, int minutes) throws JSONException { return ok(); }
        @Override public JSONObject cancel() throws JSONException { return ok(); }
        @Override public JSONObject chat(String modelId, String systemPrompt, String userPrompt, TaiRuntimeOptions options) throws JSONException { return ok(); }
        @Override public JSONObject chat(String modelId, String systemPrompt, String userPrompt, TaiRuntimeOptions options, TaiGenerationCallback callback) throws JSONException { return ok(); }
        @Override public JSONObject chat(String modelId, TaiChatRequest request, TaiRuntimeOptions options) throws JSONException { return ok(); }
        @Override public JSONObject chat(String modelId, TaiChatRequest request, TaiRuntimeOptions options, TaiGenerationCallback callback) throws JSONException { return ok(); }
        @Override public JSONObject complete(String modelId, String prompt, TaiRuntimeOptions options) throws JSONException { return ok(); }
        @Override public JSONObject complete(String modelId, String prompt, TaiRuntimeOptions options, TaiGenerationCallback callback) throws JSONException { return ok(); }

        private static JSONObject ok() throws JSONException {
            return new JSONObject().put("ok", true);
        }
    }
}
