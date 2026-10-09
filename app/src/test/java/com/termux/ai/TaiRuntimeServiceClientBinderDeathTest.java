package com.termux.ai;

import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;

import androidx.test.core.app.ApplicationProvider;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

/**
 * The runtime process dying under a request (SIGKILL mid-load, plan §3c) is the end of that
 * request: the caller gets {@code tai_runtime_crashed} naming the operation, nothing is sent again,
 * and the binding is released so ActivityManager does not bring the process back on its own. Only
 * a new request binds a fresh runtime.
 */
@RunWith(RobolectricTestRunner.class)
public class TaiRuntimeServiceClientBinderDeathTest {
    private static final long PROMPT_MS = 5_000L;

    private final Context context = ApplicationProvider.getApplicationContext();
    private final ComponentName name = new ComponentName(context, TaiRuntimeService.class);
    private TaiRuntimeServiceClient client;
    private ServiceConnection connection;
    private SilentRuntime runtime;

    @Before
    public void connectToAFakeRuntime() throws Exception {
        client = new TaiRuntimeServiceClient(context, Looper.getMainLooper());
        connection = (ServiceConnection) field(client, "connection");
        runtime = new SilentRuntime();
        // Stands in for the bound service: a Messenger on this process's main looper that takes
        // every request and, like a runtime killed mid-load, never answers one.
        connection.onServiceConnected(name, new Messenger(runtime).getBinder());
    }

    @Test
    public void deathUnderALoad_failsTheLoadOnceAndUnbinds() throws Exception {
        AtomicReference<JSONObject> result = new AtomicReference<>();
        Thread caller = new Thread(() -> {
            try {
                result.set(client.request(TaiRuntimeIpc.OP_LOAD_MODEL, "{\"model\":\"e4b\"}", PROMPT_MS));
            } catch (JSONException ignored) {
            }
        });
        caller.start();
        Map<?, ?> pending = (Map<?, ?>) field(client, "pending");
        long deadline = System.currentTimeMillis() + PROMPT_MS;
        while (pending.isEmpty() && System.currentTimeMillis() < deadline) Thread.sleep(10L);
        assertFalse("the load never registered", pending.isEmpty());
        // The caller thread registers the request, then sends it; the fake runtime takes it on the
        // main looper. Idle until the send has landed rather than racing the caller to it.
        deadline = System.currentTimeMillis() + PROMPT_MS;
        while (runtime.requests.get() == 0 && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle();
            Thread.sleep(10L);
        }
        assertEquals(1, runtime.requests.get());

        connection.onServiceDisconnected(name);
        shadowOf(Looper.getMainLooper()).idle();
        caller.join(PROMPT_MS);

        assertFalse(caller.isAlive());
        JSONObject error = result.get();
        assertNotNull(error);
        assertFalse(error.optBoolean("ok", true));
        assertEquals("tai_runtime_crashed", error.optString("error"));
        assertEquals(TaiRuntimeIpc.OP_LOAD_MODEL, error.optString("operation"));
        assertFalse(error.optBoolean("retried", true));
        assertEquals(503, error.optInt("_statusCode"));
        assertTrue(error.optString("message"), error.optString("message").contains("not retried"));
        // The load went out exactly once; the death re-sent nothing.
        assertEquals(1, runtime.requests.get());
        assertEquals(0, pending.size());
        // No runtime to talk to, and no binding left for ActivityManager to restart it on.
        assertNull(field(client, "service"));
        assertTrue(shadowOf((Application) context).getUnboundServiceConnections().contains(connection));
    }

    @Test
    public void deathWithNothingPending_stillUnbinds() throws Exception {
        connection.onServiceDisconnected(name);
        shadowOf(Looper.getMainLooper()).idle();

        assertNull(field(client, "service"));
        assertTrue(shadowOf((Application) context).getUnboundServiceConnections().contains(connection));
        assertEquals(0, runtime.requests.get());
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    /** Counts requests and answers none of them, the way a runtime killed mid-load answers none. */
    private static final class SilentRuntime extends Handler {
        final AtomicInteger requests = new AtomicInteger();

        SilentRuntime() {
            super(Looper.getMainLooper());
        }

        @Override
        public void handleMessage(Message message) {
            if (message.what == TaiRuntimeIpc.MSG_REQUEST) requests.incrementAndGet();
        }
    }
}
