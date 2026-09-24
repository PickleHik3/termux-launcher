package com.termux.ai;

import android.app.Application;
import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;

import androidx.test.core.app.ApplicationProvider;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.robolectric.Shadows.shadowOf;

/**
 * The runtime's idle exit is a request to be let go, not a death: the client unbinds when it has
 * nothing pending, and a request that is pending keeps the binding and completes normally. In
 * neither case does anything become {@code tai_runtime_crashed} or leave a crash marker.
 */
@RunWith(RobolectricTestRunner.class)
public class TaiRuntimeServiceClientIdleExitTest {
    private static final long PROMPT_MS = 5_000L;

    private final Context context = ApplicationProvider.getApplicationContext();
    private TaiRuntimeServiceClient client;
    private ServiceConnection connection;
    private Messenger incoming;

    @Before
    public void connectToAFakeRuntime() throws Exception {
        client = new TaiRuntimeServiceClient(context);
        connection = (ServiceConnection) field(client, "connection");
        incoming = (Messenger) field(client, "incoming");
        // Stands in for the bound service: a Messenger on this process's main looper that answers
        // every request ok, so the client is "connected" without Robolectric binding anything.
        connection.onServiceConnected(new ComponentName(context, TaiRuntimeService.class),
            new Messenger(new FakeRuntime()).getBinder());
    }

    @Test
    public void idleExitWithNothingPending_unbindsAndReportsNoCrash() throws Exception {
        incoming.send(Message.obtain(null, TaiRuntimeService.MSG_IDLE_EXIT));
        shadowOf(Looper.getMainLooper()).idle();

        assertTrue(shadowOf((Application) context).getUnboundServiceConnections().contains(connection));
        assertNull(field(client, "service"));
        assertTrue(((Map<?, ?>) field(client, "pending")).isEmpty());
        assertNull(TaiRuntimeCrashMarker.read(context));
    }

    @Test
    public void idleExitWithARequestPending_isIgnoredAndTheRequestCompletes() throws Exception {
        AtomicReference<JSONObject> result = new AtomicReference<>();
        Thread caller = new Thread(() -> {
            try {
                result.set(client.request(TaiRuntimeIpc.OP_STATUS, "{}", PROMPT_MS));
            } catch (JSONException ignored) {
            }
        });
        caller.start();
        Map<?, ?> pending = (Map<?, ?>) field(client, "pending");
        long deadline = System.currentTimeMillis() + PROMPT_MS;
        while (pending.isEmpty() && System.currentTimeMillis() < deadline) Thread.sleep(10L);
        assertFalse("the request never registered", pending.isEmpty());

        incoming.send(Message.obtain(null, TaiRuntimeService.MSG_IDLE_EXIT));
        shadowOf(Looper.getMainLooper()).idle();
        caller.join(PROMPT_MS);

        assertFalse(caller.isAlive());
        assertNotNull(result.get());
        assertTrue(result.get().toString(), result.get().optBoolean("ok"));
        assertFalse("tai_runtime_crashed".equals(result.get().optString("error")));
        assertFalse(shadowOf((Application) context).getUnboundServiceConnections().contains(connection));
        assertNotNull(field(client, "service"));
        assertEquals(0, pending.size());
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    /** Answers every request with {@code {"ok":true}} on the caller's reply Messenger. */
    private static final class FakeRuntime extends Handler {
        FakeRuntime() {
            super(Looper.getMainLooper());
        }

        @Override
        public void handleMessage(Message message) {
            if (message.what != TaiRuntimeIpc.MSG_REQUEST || message.replyTo == null) return;
            Bundle data = new Bundle();
            data.putString(TaiRuntimeIpc.KEY_REQUEST_ID, message.getData().getString(TaiRuntimeIpc.KEY_REQUEST_ID, ""));
            data.putString(TaiRuntimeIpc.KEY_RESULT, "{\"ok\":true}");
            Message reply = Message.obtain(null, TaiRuntimeIpc.MSG_RESPONSE);
            reply.setData(data);
            try {
                message.replyTo.send(reply);
            } catch (RemoteException ignored) {
            }
        }
    }
}
