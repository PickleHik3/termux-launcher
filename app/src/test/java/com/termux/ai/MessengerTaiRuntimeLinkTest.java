package com.termux.ai;

import android.app.Application;
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
import org.robolectric.annotation.Config;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * The Messenger adapter over a fake runtime on its own thread, in the style of
 * {@link TaiRuntimeServiceClientReplyThreadTest}: a generation's id and priority travel with its
 * message, the runtime's waiting and started reach the sink in order with the chunks, a cancel by
 * id carries that id, and the cancel-all carries none.
 */
@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class)
public class MessengerTaiRuntimeLinkTest {
    private static final long PROMPT_MS = 5_000L;

    private final Context context = ApplicationProvider.getApplicationContext();
    private HandlerThread runtimeThread;
    private FakeRuntime runtime;
    private MessengerTaiRuntimeLink link;

    @Before
    public void connectToAFakeRuntimeOnItsOwnThread() throws Exception {
        runtimeThread = new HandlerThread("fake-tai-runtime");
        runtimeThread.start();
        runtime = new FakeRuntime(runtimeThread.getLooper());
        TaiRuntimeServiceClient client = new TaiRuntimeServiceClient(context);
        ServiceConnection connection = (ServiceConnection) field(client, "connection");
        connection.onServiceConnected(new ComponentName(context, TaiRuntimeService.class),
            new Messenger(runtime).getBinder());
        link = new MessengerTaiRuntimeLink(client, body -> body);
    }

    @After
    public void stopTheFakeRuntime() {
        runtimeThread.quitSafely();
    }

    @Test
    public void aStreamCarriesItsIdAndPriorityAndHearsWaitingAndStartedInOrder() throws Exception {
        Recorder sink = new Recorder();
        TaiRuntimeLink.Generation generation = link.generate(new TaiRuntimeLink.GenerationRequest(
            TaiRuntimeLink.Kind.CHAT, true, "{\"messages\":[]}", TaiRuntimeLink.Priority.INTERACTIVE, "gen-1", 0L), sink);

        assertNull(generation.await());

        Received received = runtime.received.poll(PROMPT_MS, TimeUnit.MILLISECONDS);
        assertNotNull(received);
        assertEquals(TaiRuntimeIpc.OP_OPENAI_CHAT_STREAM, received.operation);
        assertEquals("gen-1", received.generationId);
        assertEquals("interactive", received.priority);
        assertEquals("{\"messages\":[]}", received.body);
        assertEquals(Arrays.asList("waiting", "started", "event:0", "event:1", "done"), sink.log);
    }

    @Test
    public void aNonStreamingGenerationSendsNormalPriorityAndReturnsTheAnswer() throws Exception {
        Recorder sink = new Recorder();
        TaiRuntimeLink.Generation generation = link.generate(new TaiRuntimeLink.GenerationRequest(
            TaiRuntimeLink.Kind.COMPLETION, false, "{\"prompt\":\"x\"}", TaiRuntimeLink.Priority.NORMAL, "gen-2", PROMPT_MS), sink);

        JSONObject answer = generation.await();

        assertTrue(answer.toString(), answer.optBoolean("ok", false));
        assertEquals(TaiRuntimeIpc.OP_OPENAI_COMPLETION, answer.optString("echo"));
        Received received = runtime.received.poll(PROMPT_MS, TimeUnit.MILLISECONDS);
        assertEquals("gen-2", received.generationId);
        assertEquals("normal", received.priority);
        assertEquals(Arrays.asList("waiting", "started"), sink.log);
    }

    @Test
    public void cancelByIdCarriesTheIdAndEndsThatGeneration() throws Exception {
        Recorder sink = new Recorder();
        TaiRuntimeLink.Generation generation = link.generate(new TaiRuntimeLink.GenerationRequest(
            TaiRuntimeLink.Kind.CHAT, true, FakeRuntime.HOLD, TaiRuntimeLink.Priority.NORMAL, "gen-held", 0L), sink);
        AtomicReference<Exception> failure = new AtomicReference<>();
        Thread consumer = new Thread(() -> {
            try {
                generation.await();
            } catch (Exception e) {
                failure.set(e);
            }
        });
        consumer.start();
        assertTrue(runtime.holding.await(PROMPT_MS, TimeUnit.MILLISECONDS));

        generation.cancel();
        consumer.join(PROMPT_MS);

        assertFalse(consumer.isAlive());
        assertNull(failure.get());
        runtime.received.poll(PROMPT_MS, TimeUnit.MILLISECONDS);
        Received cancel = runtime.received.poll(PROMPT_MS, TimeUnit.MILLISECONDS);
        assertNotNull(cancel);
        assertEquals(TaiRuntimeIpc.OP_CANCEL, cancel.operation);
        assertEquals("gen-held", cancel.generationId);
        assertEquals("{}", cancel.body);
        assertEquals(Arrays.asList("started", "error:generation_cancelled", "done"), sink.log);
    }

    @Test
    public void cancelAllCarriesNoId() throws Exception {
        JSONObject result = link.cancelAll(TaiBenchHarness.STOP_CANCELLED);

        assertTrue(result.toString(), result.optBoolean("ok", false));
        Received cancel = runtime.received.poll(PROMPT_MS, TimeUnit.MILLISECONDS);
        assertEquals(TaiRuntimeIpc.OP_CANCEL, cancel.operation);
        assertFalse(cancel.hasGenerationId);
        assertEquals("{}", cancel.body);
    }

    private static Object field(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static final class Received {
        String operation;
        String body;
        String generationId;
        String priority;
        boolean hasGenerationId;
    }

    /** Records what a sink hears: waiting, started, event:&lt;n&gt;, error:&lt;code&gt;, done. */
    private static final class Recorder implements TaiRuntimeLink.GenerationSink {
        final List<String> log = Collections.synchronizedList(new ArrayList<>());

        @Override public void onWaiting() { log.add("waiting"); }
        @Override public void onStarted() { log.add("started"); }

        @Override
        public void onEvent(@NonNull JSONObject event) {
            JSONObject error = event.optJSONObject("error");
            log.add(error != null ? "error:" + error.optString("code") : "event:" + event.optInt("n", -1));
        }

        @Override public void onDone() { log.add("done"); }
    }

    /**
     * Records every request. A generation hears waiting and started (a held one only started),
     * then a stream gets two numbered events and a done, a plain one {@code {"ok":true,"echo":op}}.
     * A held stream ends with {@code generation_cancelled} when a cancel names its id.
     */
    private static final class FakeRuntime extends Handler {
        static final String HOLD = "{\"hold\":true}";
        final LinkedBlockingQueue<Received> received = new LinkedBlockingQueue<>();
        final CountDownLatch holding = new CountDownLatch(1);
        private final Map<String, Message> held = new ConcurrentHashMap<>();

        FakeRuntime(Looper looper) {
            super(looper);
        }

        @Override
        public void handleMessage(Message message) {
            if (message.what != TaiRuntimeIpc.MSG_REQUEST || message.replyTo == null) return;
            Bundle data = message.getData();
            Received request = new Received();
            request.operation = data.getString(TaiRuntimeIpc.KEY_OPERATION, "");
            request.body = data.getString(TaiRuntimeIpc.KEY_BODY, null);
            request.hasGenerationId = data.containsKey(TaiRuntimeIpc.KEY_GENERATION_ID);
            request.generationId = data.getString(TaiRuntimeIpc.KEY_GENERATION_ID, null);
            request.priority = data.getString(TaiRuntimeIpc.KEY_PRIORITY, null);
            received.add(request);
            String requestId = data.getString(TaiRuntimeIpc.KEY_REQUEST_ID, "");
            try {
                if (TaiRuntimeIpc.OP_CANCEL.equals(request.operation)) {
                    Message target = request.generationId == null ? null : held.remove(request.generationId);
                    if (target != null) {
                        String targetId = target.getData().getString(TaiRuntimeIpc.KEY_REQUEST_ID, "");
                        Bundle event = idOnly(targetId);
                        event.putString(TaiRuntimeIpc.KEY_EVENT, InProcessTaiRuntimeLink.cancelledWhileWaiting().toString());
                        reply(target, TaiRuntimeIpc.MSG_STREAM_EVENT, event);
                        reply(target, TaiRuntimeIpc.MSG_STREAM_DONE, idOnly(targetId));
                    }
                    respond(message, requestId, "{\"ok\":true}");
                    return;
                }
                boolean stream = TaiRuntimeIpc.OP_OPENAI_CHAT_STREAM.equals(request.operation)
                    || TaiRuntimeIpc.OP_OPENAI_COMPLETION_STREAM.equals(request.operation);
                if (HOLD.equals(request.body)) {
                    reply(message, TaiRuntimeIpc.MSG_GENERATION_STATE, state(requestId, TaiRuntimeIpc.STATE_STARTED));
                    held.put(request.generationId, Message.obtain(message));
                    holding.countDown();
                    return;
                }
                reply(message, TaiRuntimeIpc.MSG_GENERATION_STATE, state(requestId, TaiRuntimeIpc.STATE_WAITING));
                reply(message, TaiRuntimeIpc.MSG_GENERATION_STATE, state(requestId, TaiRuntimeIpc.STATE_STARTED));
                if (stream) {
                    for (int i = 0; i < 2; i++) {
                        Bundle event = idOnly(requestId);
                        event.putString(TaiRuntimeIpc.KEY_EVENT, "{\"n\":" + i + "}");
                        reply(message, TaiRuntimeIpc.MSG_STREAM_EVENT, event);
                    }
                    reply(message, TaiRuntimeIpc.MSG_STREAM_DONE, idOnly(requestId));
                    return;
                }
                respond(message, requestId, "{\"ok\":true,\"echo\":\"" + request.operation + "\"}");
            } catch (RemoteException ignored) {
            }
        }

        private static Bundle state(String requestId, String state) {
            Bundle data = idOnly(requestId);
            data.putString(TaiRuntimeIpc.KEY_STATE, state);
            return data;
        }

        private static void respond(Message to, String requestId, String result) throws RemoteException {
            Bundle data = idOnly(requestId);
            data.putString(TaiRuntimeIpc.KEY_RESULT, result);
            reply(to, TaiRuntimeIpc.MSG_RESPONSE, data);
        }

        private static Bundle idOnly(String requestId) {
            Bundle data = new Bundle();
            data.putString(TaiRuntimeIpc.KEY_REQUEST_ID, requestId);
            return data;
        }

        private static void reply(Message to, int what, Bundle data) throws RemoteException {
            Message reply = Message.obtain(null, what);
            reply.setData(data);
            to.replyTo.send(reply);
        }
    }
}
