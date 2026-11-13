package com.termux.app.assistant;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.test.core.app.ApplicationProvider;

import com.termux.ai.FeatureRequest;

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
import android.app.Application;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(application = Application.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
public class AssistantEngineTest {
    private static final class FakeTransport implements ChatTransport {
        interface Script {
            void run(String body, FeatureRequest.Sink sink) throws Exception;
        }

        final class FakeCall implements Call {
            int cancels;
            FeatureRequest.Sink sink;

            @Override
            public void cancel() {
                cancels++;
                totalCancels++;
            }

            @Override
            public void await() throws Exception {
                script.run(bodies.get(bodies.size() - 1), sink);
            }
        }

        Script script;
        final List<String> bodies = new ArrayList<>();
        final List<FakeCall> calls = new ArrayList<>();
        int totalCancels;

        @NonNull
        @Override
        public Call start(@NonNull String body, @NonNull FeatureRequest.Sink sink) {
            bodies.add(body);
            FakeCall call = new FakeCall();
            call.sink = sink;
            calls.add(call);
            return call;
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

    private static FeatureRequest.Event delta(String text) {
        return FeatureRequest.Event.text(text);
    }

    private static FeatureRequest.Event finish() {
        return FeatureRequest.Event.text("", "stop");
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
            sink.onEvent(FeatureRequest.Event.done());
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
            sink.onEvent(FeatureRequest.Event.error("tai_runtime_crashed", "gone"));
            sink.onEvent(FeatureRequest.Event.done());
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
            sink.onEvent(FeatureRequest.Event.error("model_not_installed", null));
            sink.onEvent(FeatureRequest.Event.done());
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
            sink.onEvent(FeatureRequest.Event.done());
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
        assertEquals(1, transport.totalCancels);
    }

    @Test
    public void stopCancelsOnlyItsOwnCall() throws Exception {
        transport.script = (body, sink) -> {
            engine.stop();
            assertEquals(1, transport.calls.get(0).cancels);
            // A text event that arrives late ends the read; the cancel is not sent twice.
            try {
                sink.onEvent(delta("x"));
            } catch (IOException e) {
                assertEquals(1, transport.calls.get(0).cancels);
                throw e;
            }
        };
        long chat = store.createChat(1);
        engine.send(chat, "q");
        assertEquals(AssistantMessage.STATE_STOPPED, last(chat).state);
        assertEquals("", last(chat).text);
        assertEquals(1, transport.totalCancels);
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
            sink.onEvent(FeatureRequest.Event.error("tai_runtime_timeout", null));
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

    @Test
    public void stopWhileWaitingCancelsTheCall() throws Exception {
        transport.script = (body, sink) -> {
            sink.onEvent(FeatureRequest.Event.waiting());
            assertEquals(AssistantEngine.AnswerPhase.WAITING, engine.answerPhase());
            engine.stop();
            assertEquals(AssistantEngine.AnswerPhase.NONE, engine.answerPhase());
            assertEquals(1, transport.calls.get(0).cancels);
        };
        long chat = store.createChat(1);
        engine.send(chat, "q");
        assertEquals(AssistantMessage.STATE_STOPPED, last(chat).state);
        assertEquals(1, transport.totalCancels);
    }

    @Test
    public void phaseFollowsTheEvents() throws Exception {
        final List<AssistantEngine.AnswerPhase> seen = new ArrayList<>();
        transport.script = (body, sink) -> {
            seen.add(engine.answerPhase());
            sink.onEvent(FeatureRequest.Event.waiting());
            seen.add(engine.answerPhase());
            sink.onEvent(FeatureRequest.Event.loading());
            seen.add(engine.answerPhase());
            sink.onEvent(delta(""));
            seen.add(engine.answerPhase());
            sink.onEvent(delta("text"));
            seen.add(engine.answerPhase());
        };
        long chat = store.createChat(1);
        engine.send(chat, "q");
        List<AssistantEngine.AnswerPhase> expected = new ArrayList<>();
        expected.add(AssistantEngine.AnswerPhase.NONE);
        expected.add(AssistantEngine.AnswerPhase.WAITING);
        expected.add(AssistantEngine.AnswerPhase.LOADING);
        expected.add(AssistantEngine.AnswerPhase.STARTED);
        expected.add(AssistantEngine.AnswerPhase.STARTED);
        assertEquals(expected, seen);
        assertEquals(AssistantEngine.AnswerPhase.NONE, engine.answerPhase());
        assertEquals("text", last(chat).text);
    }

    private static FeatureRequest.Event usage(int completionTokens) throws Exception {
        java.lang.reflect.Method of = FeatureRequest.Event.class.getDeclaredMethod("of", JSONObject.class);
        of.setAccessible(true);
        return (FeatureRequest.Event) of.invoke(null, new JSONObject()
            .put("choices", new JSONArray())
            .put("usage", new JSONObject().put("completion_tokens", completionTokens)));
    }

    @Test
    public void bodyAsksForUsage() throws Exception {
        transport.script = (body, sink) -> sink.onEvent(delta("ok"));
        long chat = store.createChat(1);
        engine.send(chat, "q");
        JSONObject options = new JSONObject(transport.bodies.get(0)).getJSONObject("stream_options");
        assertTrue(options.getBoolean("include_usage"));
    }

    @Test
    public void completeAnswerStampsDurationFirstTokenAndTokens() throws Exception {
        transport.script = (body, sink) -> {
            clock.addAndGet(500);
            sink.onEvent(delta("a"));
            clock.addAndGet(3000);
            sink.onEvent(delta("b"));
            sink.onEvent(finish());
            sink.onEvent(usage(51));
            clock.addAndGet(600);
            sink.onEvent(FeatureRequest.Event.done());
        };
        long chat = store.createChat(1);
        engine.send(chat, "q");
        AssistantMessage m = last(chat);
        assertEquals(Long.valueOf(4100), m.durationMs);
        assertEquals(Long.valueOf(500), m.ttftMs);
        assertEquals(Integer.valueOf(51), m.outputTokens);
    }

    @Test
    public void modelLoadTimeIsNotCounted() throws Exception {
        transport.script = (body, sink) -> {
            sink.onEvent(FeatureRequest.Event.waiting());
            sink.onEvent(FeatureRequest.Event.loading());
            clock.addAndGet(20000);
            sink.onEvent(FeatureRequest.Event.loading());
            clock.addAndGet(10000);
            sink.onEvent(delta("a"));
            clock.addAndGet(1000);
            sink.onEvent(finish());
        };
        long chat = store.createChat(1);
        engine.send(chat, "q");
        AssistantMessage m = last(chat);
        assertEquals(Long.valueOf(1000), m.durationMs);
        assertEquals(Long.valueOf(0), m.ttftMs);
        assertNull(m.outputTokens);
    }

    @Test
    public void stoppedWithTextKeepsTimeAndNoTokens() throws Exception {
        transport.script = (body, sink) -> {
            clock.addAndGet(200);
            sink.onEvent(delta("part"));
            clock.addAndGet(800);
            engine.stop();
            sink.onEvent(delta("more"));
        };
        long chat = store.createChat(1);
        engine.send(chat, "q");
        AssistantMessage m = last(chat);
        assertEquals(AssistantMessage.STATE_STOPPED, m.state);
        assertEquals(Long.valueOf(1000), m.durationMs);
        assertNull(m.outputTokens);
    }

    @Test
    public void errorAndEmptyAnswersCarryNothing() throws Exception {
        transport.script = (body, sink) -> {
            sink.onEvent(delta("bad"));
            sink.onEvent(FeatureRequest.Event.error("tai_runtime_timeout", null));
        };
        long chat = store.createChat(1);
        engine.send(chat, "q");
        assertNull(last(chat).durationMs);
        assertNull(last(chat).ttftMs);
        transport.script = (body, sink) -> sink.onEvent(finish());
        long chat2 = store.createChat(1);
        engine.send(chat2, "q");
        assertEquals(AssistantMessage.ERROR_EMPTY_ANSWER, last(chat2).errorCode);
        assertNull(last(chat2).durationMs);
    }

    @Test
    public void retryReplacesTheOldNumbers() throws Exception {
        transport.script = (body, sink) -> {
            clock.addAndGet(100);
            sink.onEvent(delta("a"));
            clock.addAndGet(900);
            sink.onEvent(usage(10));
        };
        long chat = store.createChat(1);
        engine.send(chat, "q");
        AssistantMessage first = last(chat);
        assertEquals(Long.valueOf(1000), first.durationMs);
        transport.script = (body, sink) -> {
            clock.addAndGet(50);
            sink.onEvent(delta("b"));
            clock.addAndGet(250);
        };
        assertTrue(engine.retry(first.id));
        AssistantMessage second = last(chat);
        assertEquals(Long.valueOf(300), second.durationMs);
        assertEquals(Long.valueOf(50), second.ttftMs);
        assertNull(second.outputTokens);
    }

    private static FeatureRequest.Event thought(String reasoning) throws Exception {
        java.lang.reflect.Method of = FeatureRequest.Event.class.getDeclaredMethod("of", JSONObject.class);
        of.setAccessible(true);
        return (FeatureRequest.Event) of.invoke(null, new JSONObject().put("choices", new JSONArray()
            .put(new JSONObject().put("delta", new JSONObject().put("reasoning_content", reasoning)))));
    }

    @Test
    public void thinkingAccumulatesAndIsStampedAtTheFirstText() throws Exception {
        transport.script = (body, sink) -> {
            clock.addAndGet(100);
            sink.onEvent(thought("hm "));
            clock.addAndGet(2000);
            sink.onEvent(thought("ok"));
            clock.addAndGet(1000);
            sink.onEvent(delta("answer"));
            clock.addAndGet(500);
            sink.onEvent(thought(" late"));
            sink.onEvent(finish());
        };
        long chat = store.createChat(1);
        engine.send(chat, "q");
        AssistantMessage m = last(chat);
        assertEquals("answer", m.text);
        assertEquals("hm ok late", m.thinking);
        assertEquals(Long.valueOf(3000), m.thinkingMs);
        // The first thinking delta is the first token.
        assertEquals(Long.valueOf(100), m.ttftMs);
    }

    @Test
    public void thinkingIsSavedWhileItStreams() throws Exception {
        long chat = store.createChat(1);
        transport.script = (body, sink) -> {
            sink.onEvent(thought("a"));
            clock.addAndGet(AssistantEngine.PERSIST_INTERVAL_MS);
            sink.onEvent(thought("b"));
            AssistantMessage mid = last(chat);
            assertEquals("ab", mid.thinking);
            assertNull(mid.thinkingMs);
            assertEquals(AssistantMessage.STATE_STREAMING, mid.state);
            sink.onEvent(delta("x"));
        };
        engine.send(chat, "q");
        assertEquals(AssistantMessage.STATE_COMPLETE, last(chat).state);
    }

    @Test
    public void aReasoningOnlyAnswerKeepsItsThinkingAndTimeAtTheEnd() throws Exception {
        transport.script = (body, sink) -> {
            sink.onEvent(thought("only"));
            clock.addAndGet(700);
            sink.onEvent(finish());
        };
        long chat = store.createChat(1);
        engine.send(chat, "q");
        AssistantMessage m = last(chat);
        assertEquals(AssistantMessage.ERROR_EMPTY_ANSWER, m.errorCode);
        assertEquals("only", m.thinking);
        assertEquals(Long.valueOf(700), m.thinkingMs);
    }

    @Test
    public void anAnswerWithoutThinkingStoresNone() throws Exception {
        transport.script = (body, sink) -> sink.onEvent(delta("a"));
        long chat = store.createChat(1);
        engine.send(chat, "q");
        assertNull(last(chat).thinking);
        assertNull(last(chat).thinkingMs);
    }

    @Test
    public void retryClearsTheOldThinking() throws Exception {
        transport.script = (body, sink) -> {
            sink.onEvent(thought("first"));
            sink.onEvent(delta("a"));
        };
        long chat = store.createChat(1);
        engine.send(chat, "q");
        AssistantMessage first = last(chat);
        assertEquals("first", first.thinking);
        transport.script = (body, sink) -> {
            AssistantMessage mid = last(chat);
            assertNull(mid.thinking);
            assertNull(mid.thinkingMs);
            sink.onEvent(delta("b"));
        };
        assertTrue(engine.retry(first.id));
        assertNull(last(chat).thinking);
        assertNull(last(chat).thinkingMs);
    }
}
