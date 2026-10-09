package com.termux.ai;

import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;

import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * The runtime's replies are handled on the client's own reply thread, never the main looper: the
 * main looper is never idled here, so a reply that needed it would leave every request to time
 * out. Each stream's events still arrive in the order the runtime sent them.
 */
@RunWith(RobolectricTestRunner.class)
public class TaiRuntimeServiceClientReplyThreadTest {
    private static final long PROMPT_MS = 5_000L;
    private static final int STREAM_EVENTS = 20;

    private final Context context = ApplicationProvider.getApplicationContext();
    private HandlerThread runtimeThread;
    private TaiRuntimeServiceClient client;

    @Before
    public void connectToAFakeRuntimeOnItsOwnThread() throws Exception {
        runtimeThread = new HandlerThread("fake-tai-runtime");
        runtimeThread.start();
        client = new TaiRuntimeServiceClient(context);
        ServiceConnection connection = (ServiceConnection) field(client, "connection");
        connection.onServiceConnected(new ComponentName(context, TaiRuntimeService.class),
            new Messenger(new FakeRuntime(runtimeThread.getLooper())).getBinder());
    }

    @After
    public void stopTheFakeRuntime() {
        runtimeThread.quitSafely();
    }

    @Test
    public void aRequestFromABackgroundThreadCompletesWithoutTheMainLooper() throws Exception {
        AtomicReference<JSONObject> result = new AtomicReference<>();
        Thread caller = new Thread(() -> {
            try {
                result.set(client.request(TaiRuntimeIpc.OP_STATUS, "{}", PROMPT_MS));
            } catch (Exception ignored) {
            }
        });
        caller.start();
        caller.join(PROMPT_MS * 2);

        assertFalse(caller.isAlive());
        assertNotNull(result.get());
        assertTrue(result.get().toString(), result.get().optBoolean("ok"));
        assertEquals(TaiRuntimeIpc.OP_STATUS, result.get().optString("echo"));
    }

    @Test
    public void aRequestFromTheMainThreadGetsItsReplyInsteadOfTimingOut() throws Exception {
        assertEquals(Looper.getMainLooper(), Looper.myLooper());
        JSONObject result = client.request(TaiRuntimeIpc.OP_STATUS, "{}", PROMPT_MS);
        assertTrue(result.toString(), result.optBoolean("ok"));
    }

    @Test
    public void aStreamsEventsArriveInOrderAndThenItEnds() throws Exception {
        List<Integer> seen = Collections.synchronizedList(new ArrayList<>());
        AtomicBoolean done = new AtomicBoolean();
        client.stream("stream", "{}", new TaiManager.OpenAiStreamSink() {
            @Override
            public void onEvent(@NonNull JSONObject event) {
                seen.add(event.optInt("n", -1));
            }

            @Override
            public void onDone() {
                done.set(true);
            }
        });

        assertTrue(done.get());
        assertEquals(STREAM_EVENTS, seen.size());
        for (int i = 0; i < STREAM_EVENTS; i++) assertEquals(Integer.valueOf(i), seen.get(i));
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    /**
     * Answers {@code stream} with numbered events then a done, and anything else with
     * {@code {"ok":true,"echo":<operation>}}, from its own thread.
     */
    private static final class FakeRuntime extends Handler {
        FakeRuntime(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message message) {
            if (message.what != TaiRuntimeIpc.MSG_REQUEST || message.replyTo == null) return;
            String requestId = message.getData().getString(TaiRuntimeIpc.KEY_REQUEST_ID, "");
            String operation = message.getData().getString(TaiRuntimeIpc.KEY_OPERATION, "");
            try {
                if ("stream".equals(operation)) {
                    for (int i = 0; i < STREAM_EVENTS; i++) {
                        Bundle data = idOnly(requestId);
                        data.putString(TaiRuntimeIpc.KEY_EVENT, "{\"n\":" + i + "}");
                        reply(message, TaiRuntimeIpc.MSG_STREAM_EVENT, data);
                    }
                    reply(message, TaiRuntimeIpc.MSG_STREAM_DONE, idOnly(requestId));
                    return;
                }
                Bundle data = idOnly(requestId);
                data.putString(TaiRuntimeIpc.KEY_RESULT, "{\"ok\":true,\"echo\":\"" + operation + "\"}");
                reply(message, TaiRuntimeIpc.MSG_RESPONSE, data);
            } catch (RemoteException ignored) {
            }
        }

        private static Bundle idOnly(String requestId) {
            Bundle data = new Bundle();
            data.putString(TaiRuntimeIpc.KEY_REQUEST_ID, requestId);
            return data;
        }

        private static void reply(Message request, int what, Bundle data) throws RemoteException {
            Message reply = Message.obtain(null, what);
            reply.setData(data);
            request.replyTo.send(reply);
        }
    }
}
