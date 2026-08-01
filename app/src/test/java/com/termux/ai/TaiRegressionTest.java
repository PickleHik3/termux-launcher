package com.termux.ai;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.termux.launcherctl.LauncherCtlApiServer;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

/**
 * Regression tests proving LiteRT preservation after MNN backend introduction.
 *
 * <p>All tests are deterministic, use fakes/mocks, and do not require a real
 * MNN native runtime or network access.
 */
@RunWith(RobolectricTestRunner.class)
public class TaiRegressionTest {
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
    public void litertCatalogModels_loadAndSerializeUnchanged() {
        Map<String, TaiModelCatalog.CatalogEntry> entries = TaiModelCatalog.entries();

        TaiModelCatalog.CatalogEntry gemma4e2b = entries.get(TaiModelRegistry.MODEL_GEMMA_4_E2B_IT);
        assertNotNull(gemma4e2b);
        assertEquals(TaiModelSpec.BACKEND_LITERT_LM, gemma4e2b.backend);
        assertEquals(TaiModelSpec.FORMAT_LITERTLM, gemma4e2b.format);
        assertTrue(gemma4e2b.capabilities.contains(TaiModelSpec.CAPABILITY_TEXT_CHAT));

        TaiModelCatalog.CatalogEntry mobileActions = entries.get(TaiModelRegistry.MODEL_MOBILE_ACTIONS_270M);
        assertNotNull(mobileActions);
        assertEquals(TaiModelSpec.BACKEND_LITERT_LM, mobileActions.backend);
        assertEquals(TaiModelSpec.FORMAT_LITERTLM, mobileActions.format);
        assertTrue(mobileActions.capabilities.contains("mobile_actions"));
    }

    @Test
    public void litertUserModels_persistAfterMnnLogic() throws Exception {
        TaiModelStore store = new TaiModelStore(context);
        TaiModelSpec litert = new TaiModelSpec(
            "my-litert",
            "My LiteRT",
            "chat",
            "imported",
            "/models/my-litert/model.litertlm",
            "test",
            0L,
            new LinkedHashSet<>(Collections.singletonList(TaiModelSpec.CAPABILITY_TEXT_CHAT)),
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

        store.upsertUserModel(litert);

        Map<String, TaiModelSpec> reloaded = new TaiModelStore(context).getUserModels();
        assertTrue("LiteRT user model should survive MNN filtering", reloaded.containsKey("my-litert"));
        assertEquals(TaiModelSpec.BACKEND_LITERT_LM, reloaded.get("my-litert").backend);
        assertEquals(TaiModelSpec.FORMAT_LITERTLM, reloaded.get("my-litert").format);
    }

    @Test
    public void litertChatCompletions_endpointStillResponds() throws Exception {
        Field serverInstance = LauncherCtlApiServer.class.getDeclaredField("instance");
        serverInstance.setAccessible(true);
        serverInstance.set(null, null);
        LauncherCtlApiServer server = LauncherCtlApiServer.getInstance();
        server.start(context);
        Thread.sleep(150);

        try {
            JSONObject endpoint = server.endpointSettings(context);
            int port = endpoint.getInt("activePort");
            String token = endpoint.getString("token");

            HttpURLConnection conn = (HttpURLConnection) new URL("http://127.0.0.1:" + port + "/v1/chat/completions").openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", "Bearer " + token);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.getOutputStream().write(new JSONObject()
                .put("model", "nonexistent")
                .put("messages", new JSONArray().put(new JSONObject().put("role", "user").put("content", "hi")))
                .toString().getBytes(StandardCharsets.UTF_8));

            assertTrue("Expected non-404 for /v1/chat/completions", conn.getResponseCode() != 404);
        } finally {
            server.stop();
            serverInstance.set(null, null);
        }
    }

    @Test
    public void litertCompletions_endpointStillResponds() throws Exception {
        Field serverInstance = LauncherCtlApiServer.class.getDeclaredField("instance");
        serverInstance.setAccessible(true);
        serverInstance.set(null, null);
        LauncherCtlApiServer server = LauncherCtlApiServer.getInstance();
        server.start(context);
        Thread.sleep(150);

        try {
            JSONObject endpoint = server.endpointSettings(context);
            int port = endpoint.getInt("activePort");
            String token = endpoint.getString("token");

            HttpURLConnection conn = (HttpURLConnection) new URL("http://127.0.0.1:" + port + "/v1/completions").openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Authorization", "Bearer " + token);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);
            conn.getOutputStream().write(new JSONObject()
                .put("model", "nonexistent")
                .put("prompt", "hi")
                .toString().getBytes(StandardCharsets.UTF_8));

            assertTrue("Expected non-404 for /v1/completions", conn.getResponseCode() != 404);
        } finally {
            server.stop();
            serverInstance.set(null, null);
        }
    }

    @Test
    public void litertModelLoad_routesToLiteRtRuntime() throws Exception {
        MultiBackendTaiRuntime runtime = new MultiBackendTaiRuntime(context);
        TaiModelSpec litert = model("litert-model", TaiModelSpec.BACKEND_LITERT_LM, TaiModelSpec.FORMAT_LITERTLM);

        Object target = runtimeForModel(runtime, litert);
        Object liteRtField = field(runtime, "liteRt");

        assertSame(liteRtField, target);
    }

    private static TaiModelSpec model(String id, String backend, String format) {
        return new TaiModelSpec(
            id,
            id,
            "Test model",
            "test",
            "/models/" + id + "/model." + format,
            "test",
            123L,
            new LinkedHashSet<>(Collections.singleton(TaiModelSpec.CAPABILITY_TEXT_CHAT)),
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

    private static Object runtimeForModel(MultiBackendTaiRuntime runtime, TaiModelSpec model) throws Exception {
        Method method = MultiBackendTaiRuntime.class.getDeclaredMethod("runtimeForModel", TaiModelSpec.class);
        method.setAccessible(true);
        return method.invoke(runtime, model);
    }

    private static Object field(Object target, String name) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        return f.get(target);
    }
}
