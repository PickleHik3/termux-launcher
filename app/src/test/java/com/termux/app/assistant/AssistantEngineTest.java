package com.termux.app.assistant;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.GraphicsMode;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class AssistantEngineTest {
    private static final class FakeTransport implements ChatTransport {
        interface Script {
            void run(String body, Sink sink) throws Exception;
        }

        Script script;
        final List<String> bodies = new ArrayList<>();
        int cancels;

        @Override
        public void stream(@NonNull String body, @NonNull Sink sink) throws Exception {
            bodies.add(body);
            script.run(body, sink);
        }

        @Override
        public void cancel() {
            cancels++;
        }
    }

    private File file;
    private AssistantChatStore store;
    private FakeTransport transport;
    private AssistantEngine engine;
    private AtomicLong clock;
    private int window = 4096;
    private final List<String> events = new ArrayList<>();

    @Before
    public void setUp() {
        Context context = ApplicationProvider.getApplicationContext();
        file = new File(context.getCacheDir(), "engine_test_" + System.nanoTime() + ".db");
        store = new AssistantChatStore(file);
        transport = new FakeTransport();
        clock = new AtomicLong(1000);
        engine = new AssistantEngine(store, transport, () -> window, Runnable::run, Runnable::run,
            Runnable::run, clock::get);
        engine.addListener(new AssistantEngine.Listener() {
            @Override
            public void onMessageChanged(long chatId, long messageId) {
                events.add("m" + messageId);
            }

            @Override
            public void onChatsChanged() {
                events.add("c");
            }
        });
    }

    @After
    public void tearDown() {
        store.close();
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }

    private static JSONObject delta(String text) throws Exception {
        return new JSONObject("{\"choices\":[{\"delta\":{\"content\":" + JSONObject.quote(text)
            + "},\"finish_reason\":null}]}");
    }

    private static JSONObject finish() throws Exception {
        return new JSONObject("{\"choices\":[{\"delta\":{},\"finish_reason\":\"stop\"}]}");
    }

    private AssistantMessage last(long chat) {
        List<AssistantMessage> all = store.messages(chat);
        return all.get(all.size() - 1);
    }

    @Test
    public void sendStreamsAndCompletes() throws Exception {
        transport.script = (body, sink) -> {
            sink.onEvent(delta("Hel"));
            sink.onEvent(delta("lo"));
            sink.onEvent(finish());
            sink.onDone();
        };
        long chat = store.createChat(1);
        assertTrue(engine.send(chat, "  hi there  "));
        List<AssistantMessage> messages = store.messages(chat);
        assertEquals(2, messages.size());
        assertEquals("  hi there  ", messages.get(0).text);
        assertEquals(AssistantMessage.STATE_COMPLETE, messages.get(1).state);
        assertEquals("Hello", messages.get(1).text);
        assertNull(messages.get(1).errorCode);
        assertEquals("hi there", store.chat(chat).title);
        assertFalse(engine.isGenerating());
        assertTrue(events.contains("c"));
        assertTrue(events.contains("m" + messages.get(1).id));
    }

    @Test
    public void bodyHasStreamAndNoModel() throws Exception {
        transport.script = (body, sink) -> sink.onEvent(delta("ok"));
        long chat = store.createChat(1);
        engine.send(chat, "question");
        JSONObject body = new JSONObject(transport.bodies.get(0));
        assertTrue(body.getBoolean("stream"));
        assertFalse(body.has("model"));
        JSONArray messages = body.getJSONArray("messages");
        assertEquals(1, messages.length());
        assertEquals("user", messages.getJSONObject(0).getString("role"));
        assertEquals("question", messages.getJSONObject(0).getString("content"));
    }

    @Test
    public void historyIsSentAndTheFreshAnswerIsNot() throws Exception {
        transport.script = (body, sink) -> sink.onEvent(delta("A"));
        long chat = store.createChat(1);
        engine.send(chat, "one");
        engine.send(chat, "two");
        JSONArray messages = new JSONObject(transport.bodies.get(1)).getJSONArray("messages");
        assertEquals(3, messages.length());
        assertEquals("one", messages.getJSONObject(0).getString("content"));
        assertEquals("A", messages.getJSONObject(1).getString("content"));
        assertEquals("two", messages.getJSONObject(2).getString("content"));
    }

    @Test
    public void smallWindowCutsOlderMessages() throws Exception {
        window = 1024; // budget 768 tokens
        transport.script = (body, sink) -> sink.onEvent(delta("short"));
        long chat = store.createChat(1);
        String big = new String(new char[1800]).replace('\0', 'x'); // ~604 tokens
        engine.send(chat, big);
        engine.send(chat, big);
        JSONArray messages = new JSONObject(transport.bodies.get(1)).getJSONArray("messages");
        // The older turn (question + answer) no longer fits as a whole, so only the newest
        // question goes; an answer is never sent without its question.
        assertEquals(1, messages.length());
        assertEquals(big, messages.getJSONObject(0).getString("content"));
        assertEquals("user", messages.getJSONObject(0).getString("role"));
    }

    @Test
    public void persistsAtMostEveryQuarterSecondAndAtTheEnd() throws Exception {
        final long[] seen = new long[1];
        transport.script = (body, sink) -> {
            sink.onEvent(delta("a"));          // t=1000 -> first persist
            clock.addAndGet(100);
            sink.onEvent(delta("b"));          // within the interval: not persisted
            long id = engineMessageId();
            seen[0] = id;
            assertEquals("a", store.message(id).text);
            clock.addAndGet(200);
            sink.onEvent(delta("c"));          // past it: persisted
            assertEquals("abc", store.message(id).text);
            clock.addAndGet(10);
            sink.onEvent(delta("d"));
            assertEquals("abc", store.message(id).text);
            assertEquals("abcd", engine.liveText(id));
            sink.onEvent(finish());
        };
        long chat = store.createChat(1);
        engine.send(chat, "x");
        assertEquals("abcd", store.message(seen[0]).text);
        assertEquals(AssistantMessage.STATE_COMPLETE, store.message(seen[0]).state);
        assertNull(engine.liveText(seen[0]));
    }

    private long engineMessageId() {
        return engine.generatingMessageId();
    }

    @Test
    public void errorEventKeepsPartialTextAndCode() throws Exception {
        transport.script = (body, sink) -> {
            sink.onEvent(delta("part"));
            sink.onEvent(new JSONObject("{\"error\":{\"code\":\"tai_runtime_crashed\",\"message\":\"gone\"}}"));
            sink.onDone();
        };
        long chat = store.createChat(1);
        engine.send(chat, "q");
        AssistantMessage m = last(chat);
        assertEquals(AssistantMessage.STATE_ERROR, m.state);
        assertEquals(AssistantMessage.ERROR_RUNTIME_DIED, m.errorCode);
        assertEquals("part", m.text);
    }

    @Test
    public void modelNotDownloadedError() throws Exception {
        transport.script = (body, sink) -> {
            sink.onEvent(new JSONObject("{\"ok\":false,\"code\":\"model_not_installed\","
                + "\"error\":{\"code\":\"model_not_installed\"}}"));
            sink.onDone();
        };
        long chat = store.createChat(1);
        engine.send(chat, "q");
        assertEquals(AssistantMessage.ERROR_MODEL_NOT_DOWNLOADED, last(chat).errorCode);
    }

    @Test
    public void thrownIoErrorIsRuntimeDiedAndOtherThrowIsError() throws Exception {
        transport.script = (body, sink) -> {
            throw new IOException("pipe");
        };
        long chat = store.createChat(1);
        engine.send(chat, "q");
        assertEquals(AssistantMessage.ERROR_RUNTIME_DIED, last(chat).errorCode);
        transport.script = (body, sink) -> {
            throw new IllegalStateException("boom");
        };
        engine.send(chat, "q2");
        assertEquals(AssistantMessage.ERROR_OTHER, last(chat).errorCode);
        assertFalse(engine.isGenerating());
    }

    @Test
    public void emptyAnswerIsAnError() throws Exception {
        transport.script = (body, sink) -> {
            sink.onEvent(finish());
            sink.onDone();
        };
        long chat = store.createChat(1);
        engine.send(chat, "q");
        assertEquals(AssistantMessage.ERROR_EMPTY_ANSWER, last(chat).errorCode);
    }

    @Test
    public void stopKeepsTextMarksStoppedAndCancelsOnce() throws Exception {
        transport.script = (body, sink) -> {
            sink.onEvent(delta("half "));
            sink.onEvent(delta("way"));
            engine.stop();
            assertFalse(engine.isGenerating());
            try {
                sink.onEvent(delta(" more"));
                throw new AssertionError("sink should have aborted");
            } catch (IOException expected) {
                throw expected;
            }
        };
        long chat = store.createChat(1);
        engine.send(chat, "q");
        AssistantMessage m = last(chat);
        assertEquals(AssistantMessage.STATE_STOPPED, m.state);
        assertEquals("half way", m.text);
        assertNull(m.errorCode);
        assertEquals(1, transport.cancels);
    }

    @Test
    public void stopBeforeTheRuntimeAnswersDoesNotCancelTheRuntime() throws Exception {
        transport.script = (body, sink) -> {
            engine.stop();
            // The runtime's first event arrives late: the cancel goes out then, once.
            try {
                sink.onEvent(delta("x"));
            } catch (IOException e) {
                assertEquals(1, transport.cancels);
                throw e;
            }
        };
        long chat = store.createChat(1);
        engine.send(chat, "q");
        assertEquals(AssistantMessage.STATE_STOPPED, last(chat).state);
        assertEquals("", last(chat).text);
        assertEquals(1, transport.cancels);
    }

    @Test
    public void secondSendWhileRunningIsRefused() throws Exception {
        final boolean[] refused = new boolean[2];
        transport.script = (body, sink) -> {
            refused[0] = !engine.send(1, "again");
            sink.onEvent(delta("ok"));
        };
        long chat = store.createChat(1);
        assertTrue(engine.send(chat, "first"));
        assertTrue(refused[0]);
        assertEquals(2, store.messages(chat).size());
        assertFalse(engine.send(chat, "   "));
    }

    @Test
    public void retryRerunsTheLastAnswerForItsUserMessage() throws Exception {
        transport.script = (body, sink) -> {
            sink.onEvent(delta("bad"));
            sink.onEvent(new JSONObject("{\"error\":{\"code\":\"tai_runtime_timeout\"}}"));
        };
        long chat = store.createChat(1);
        engine.send(chat, "ask");
        AssistantMessage failed = last(chat);
        assertEquals(AssistantMessage.ERROR_TIMEOUT, failed.errorCode);
        transport.script = (body, sink) -> {
            sink.onEvent(delta("good"));
            sink.onEvent(finish());
        };
        assertTrue(engine.retry(failed.id));
        List<AssistantMessage> messages = store.messages(chat);
        assertEquals(2, messages.size());
        assertEquals(failed.id, messages.get(1).id);
        assertEquals("good", messages.get(1).text);
        assertEquals(AssistantMessage.STATE_COMPLETE, messages.get(1).state);
        assertNull(messages.get(1).errorCode);
        // The retry sent the user message only, not the failed answer.
        JSONArray sent = new JSONObject(transport.bodies.get(1)).getJSONArray("messages");
        assertEquals(1, sent.length());
    }

    @Test
    public void retryRefusesAUserMessageAnOlderAnswerAndAMissingOne() throws Exception {
        transport.script = (body, sink) -> sink.onEvent(delta("a"));
        long chat = store.createChat(1);
        engine.send(chat, "one");
        engine.send(chat, "two");
        List<AssistantMessage> all = store.messages(chat);
        assertFalse(engine.retry(all.get(0).id));
        assertFalse(engine.retry(all.get(1).id));
        assertFalse(engine.retry(9999));
        assertEquals(1, transport.bodies.size() - 1);
    }

    @Test
    public void sendToAMissingChatDoesNothingAndFreesTheEngine() throws Exception {
        transport.script = (body, sink) -> sink.onEvent(delta("a"));
        engine.send(777, "x");
        assertFalse(engine.isGenerating());
        assertEquals(0, transport.bodies.size());
    }

    @Test
    public void heldQuestionIsStoredWithWaitingAnswerTitledAndSearchable() {
        long chat = store.createChat(1);
        engine.setModelReady(false);
        assertTrue(engine.hold(chat, "hello world"));
        List<AssistantMessage> all = store.messages(chat);
        assertEquals(2, all.size());
        assertEquals("hello world", all.get(0).text);
        assertEquals(AssistantMessage.STATE_WAITING, all.get(1).state);
        assertEquals("hello world", store.chat(chat).title);
        assertEquals(1, store.listChats("hello").size());
        assertTrue(transport.bodies.isEmpty());
    }

    @Test
    public void readyTurnsWaitingIntoAnAnswer() throws Exception {
        transport.script = (body, sink) -> {
            sink.onEvent(delta("Hi"));
            sink.onEvent(finish());
        };
        long chat = store.createChat(1);
        engine.setModelReady(false);
        engine.hold(chat, "hello");
        engine.setModelReady(true);
        AssistantMessage a = last(chat);
        assertEquals(AssistantMessage.STATE_COMPLETE, a.state);
        assertEquals("Hi", a.text);
        assertEquals(1, transport.bodies.size());
        assertEquals(2, store.messages(chat).size());
    }

    @Test
    public void waitingSurvivesReopen() {
        long chat = store.createChat(1);
        engine.setModelReady(false);
        engine.hold(chat, "later");
        store.close();
        AssistantChatStore reopened = new AssistantChatStore(file);
        assertEquals(AssistantMessage.STATE_WAITING, reopened.messages(chat).get(1).state);
        reopened.close();
    }

    @Test
    public void twoHeldQuestionsAreAnsweredInOrder() throws Exception {
        transport.script = (body, sink) -> {
            sink.onEvent(delta("ok"));
            sink.onEvent(finish());
        };
        long a = store.createChat(1);
        long b = store.createChat(2);
        engine.setModelReady(false);
        engine.hold(a, "first");
        engine.hold(b, "second");
        engine.setModelReady(true);
        assertEquals(2, transport.bodies.size());
        assertTrue(transport.bodies.get(0).contains("first"));
        assertTrue(transport.bodies.get(1).contains("second"));
        assertEquals(AssistantMessage.STATE_COMPLETE, last(a).state);
        assertEquals(AssistantMessage.STATE_COMPLETE, last(b).state);
    }
}
