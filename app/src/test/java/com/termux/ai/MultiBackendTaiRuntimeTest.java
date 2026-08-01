package com.termux.ai;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
public class MultiBackendTaiRuntimeTest {
    private Context context;

    @Before
    public void setUp() throws Exception {
        context = ApplicationProvider.getApplicationContext();
        context.getSharedPreferences(TaiSettings.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit();
        context.getSharedPreferences(TaiModelStore.PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit();
        Field instance = TaiManager.class.getDeclaredField("instance");
        instance.setAccessible(true);
        instance.set(null, null);
    }

    @Test
    public void runtimeForModel_routesLiteRtAndMnnBackends() throws Exception {
        MultiBackendTaiRuntime runtime = new MultiBackendTaiRuntime(context);

        assertSame(field(runtime, "liteRt"), runtimeForModel(runtime, model("litert", TaiModelSpec.BACKEND_LITERT_LM, TaiModelSpec.FORMAT_LITERTLM)));
        assertSame(field(runtime, "mnn"), runtimeForModel(runtime, model("mnn", TaiModelSpec.BACKEND_MNN_LLM, TaiModelSpec.FORMAT_MNN)));
    }

    @Test
    public void runtimeForId_routesFunctionGemmaAsNormalLiteRtModel() throws Exception {
        MultiBackendTaiRuntime runtime = new MultiBackendTaiRuntime(context);

        assertSame(field(runtime, "liteRt"), runtimeForId(runtime, TaiModelRegistry.MODEL_MOBILE_ACTIONS_270M));
    }

    @Test
    public void loadMnnModel_validatesFilesBeforeNativeRuntime() throws Exception {
        MultiBackendTaiRuntime runtime = new MultiBackendTaiRuntime(context);

        JSONObject result = runtime.load(model("mnn-load", TaiModelSpec.BACKEND_MNN_LLM, TaiModelSpec.FORMAT_MNN), options());

        assertFalse(result.getBoolean("ok"));
        assertEquals("model_file_not_readable", result.getString("error"));
        assertEquals(404, result.getInt("_statusCode"));
        assertSame(field(runtime, "mnn"), field(runtime, "activeAssistant"));
    }

    @Test
    public void backendMismatch_returnsConflictBeforeRuntimeLoad() throws Exception {
        TaiModelStore store = new TaiModelStore(context);
        store.upsertUserModel(model("user-mnn", TaiModelSpec.BACKEND_MNN_LLM, TaiModelSpec.FORMAT_MNN));
        TaiManager manager = TaiManager.getInstance(context);

        JSONObject result = manager.loadModel(new JSONObject()
            .put("model", "user-mnn")
            .put("backend", TaiModelSpec.BACKEND_LITERT_LM)
            .toString());

        assertFalse(result.getBoolean("ok"));
        assertEquals("backend_mismatch", result.getString("error"));
        assertEquals(409, result.getInt("_statusCode"));
    }

    @Test
    public void loadDifferentBackendDuringActiveGeneration_returnsConflictAndDoesNotSwitch() throws Exception {
        MultiBackendTaiRuntime runtime = new MultiBackendTaiRuntime(context);
        FakeRuntime activeGeneration = new FakeRuntime(new TaiRuntimeState(
            true,
            "litert-active",
            "fake-litert",
            "generating",
            "Generating.",
            TaiModelSpec.BACKEND_LITERT_LM,
            null,
            null,
            true,
            "generation-1",
            123L,
            0L,
            0L,
            0L,
            0L
        ));
        setField(runtime, "activeAssistant", activeGeneration);

        JSONObject result = runtime.load(model("mnn-switch", TaiModelSpec.BACKEND_MNN_LLM, TaiModelSpec.FORMAT_MNN), options());

        assertFalse(result.getBoolean("ok"));
        assertEquals("generation_active", result.getString("error"));
        assertEquals(409, result.getInt("_statusCode"));
        assertFalse(activeGeneration.unloadCalled);
        assertSame(activeGeneration, field(runtime, "activeAssistant"));
    }

    @Test
    public void unload_canReachBackendWhileGenerationIsRunning() throws Exception {
        MultiBackendTaiRuntime runtime = new MultiBackendTaiRuntime(context);
        BlockingRuntime blocking = new BlockingRuntime();
        setField(runtime, "activeAssistant", blocking);
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread generation = new Thread(() -> {
            try {
                runtime.chat("blocking", "", "hi", options());
            } catch (Throwable throwable) {
                failure.set(throwable);
            }
        });
        generation.start();
        assertTrue(blocking.entered.await(2, TimeUnit.SECONDS));

        JSONObject unloaded = runtime.unload();

        assertTrue(unloaded.getBoolean("ok"));
        assertTrue(blocking.unloadCalled);
        generation.join(2000L);
        assertFalse(generation.isAlive());
        assertTrue(failure.get() == null);
    }

    private static TaiModelSpec model(String id, String backend, String format) {
        return new TaiModelSpec(
            id,
            id,
            "Test model",
            "test",
            TaiModelSpec.FORMAT_MNN.equals(format) ? "/models/" + id + "/config.json" : "/models/" + id + "/model." + format,
            "test",
            123L,
            new java.util.LinkedHashSet<>(java.util.Collections.singleton(TaiModelSpec.CAPABILITY_TEXT_CHAT)),
            false,
            null,
            backend,
            format,
            null,
            null,
            4096,
            0,
            null
        );
    }

    private static TaiRuntimeOptions options() {
        return new TaiRuntimeOptions(null, null, null, null, null, null, null, null);
    }

    private static Object runtimeForModel(MultiBackendTaiRuntime runtime, TaiModelSpec model) throws Exception {
        Method method = MultiBackendTaiRuntime.class.getDeclaredMethod("runtimeForModel", TaiModelSpec.class);
        method.setAccessible(true);
        return method.invoke(runtime, model);
    }

    private static Object runtimeForId(MultiBackendTaiRuntime runtime, String id) throws Exception {
        Method method = MultiBackendTaiRuntime.class.getDeclaredMethod("runtimeForId", String.class);
        method.setAccessible(true);
        return method.invoke(runtime, id);
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static final class FakeRuntime implements TaiRuntime {
        private final TaiRuntimeState state;
        private boolean unloadCalled;

        private FakeRuntime(TaiRuntimeState state) {
            this.state = state;
        }

        @Override public TaiRuntimeState getState() { return state; }
        @Override public boolean isModelLoaded(String modelId) { return modelId.equals(state.loadedModelId); }
        @Override public JSONObject load(TaiModelSpec modelSpec, TaiRuntimeOptions options) throws JSONException { return ok(); }
        @Override public JSONObject unload() throws JSONException { unloadCalled = true; return ok(); }
        @Override public JSONObject keepWarm(TaiModelSpec modelSpec, TaiRuntimeOptions options, int minutes) throws JSONException { return ok(); }
        @Override public JSONObject cancel() throws JSONException { return ok(); }
        @Override public JSONObject chat(String modelId, String systemPrompt, String userPrompt, TaiRuntimeOptions options) throws JSONException { return ok(); }
        @Override public JSONObject chat(String modelId, String systemPrompt, String userPrompt, TaiRuntimeOptions options, TaiGenerationCallback callback) throws JSONException { return ok(); }
        @Override public JSONObject chat(String modelId, TaiChatRequest request, TaiRuntimeOptions options) throws JSONException { return ok(); }
        @Override public JSONObject chat(String modelId, TaiChatRequest request, TaiRuntimeOptions options, TaiGenerationCallback callback) throws JSONException { return ok(); }
        @Override public JSONObject complete(String modelId, String prompt, TaiRuntimeOptions options) throws JSONException { return ok(); }
        @Override public JSONObject complete(String modelId, String prompt, TaiRuntimeOptions options, TaiGenerationCallback callback) throws JSONException { return ok(); }

        private JSONObject ok() throws JSONException {
            return new JSONObject().put("ok", true);
        }
    }

    private static final class BlockingRuntime implements TaiRuntime {
        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch released = new CountDownLatch(1);
        private volatile boolean unloadCalled;

        @Override public TaiRuntimeState getState() { return TaiRuntimeState.fromJson(null); }
        @Override public boolean isModelLoaded(String modelId) { return true; }
        @Override public JSONObject load(TaiModelSpec modelSpec, TaiRuntimeOptions options) throws JSONException { return ok(); }
        @Override public JSONObject unload() throws JSONException { unloadCalled = true; released.countDown(); return ok(); }
        @Override public JSONObject keepWarm(TaiModelSpec modelSpec, TaiRuntimeOptions options, int minutes) throws JSONException { return ok(); }
        @Override public JSONObject cancel() throws JSONException { released.countDown(); return ok(); }
        @Override public JSONObject chat(String modelId, String systemPrompt, String userPrompt, TaiRuntimeOptions options) throws JSONException {
            entered.countDown();
            try { released.await(2, TimeUnit.SECONDS); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            return ok();
        }
        @Override public JSONObject chat(String modelId, String systemPrompt, String userPrompt, TaiRuntimeOptions options, TaiGenerationCallback callback) throws JSONException { return chat(modelId, systemPrompt, userPrompt, options); }
        @Override public JSONObject chat(String modelId, TaiChatRequest request, TaiRuntimeOptions options) throws JSONException { return chat(modelId, "", "", options); }
        @Override public JSONObject chat(String modelId, TaiChatRequest request, TaiRuntimeOptions options, TaiGenerationCallback callback) throws JSONException { return chat(modelId, "", "", options); }
        @Override public JSONObject complete(String modelId, String prompt, TaiRuntimeOptions options) throws JSONException { return chat(modelId, "", prompt, options); }
        @Override public JSONObject complete(String modelId, String prompt, TaiRuntimeOptions options, TaiGenerationCallback callback) throws JSONException { return complete(modelId, prompt, options); }

        private JSONObject ok() throws JSONException { return new JSONObject().put("ok", true); }
    }
}
