package com.termux.ai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/** One Feature request over the fake runtime link, a fake remote transport and a fake backend (ADR 0027 section 5). */
public class FeatureRequestTest {
    private static final long WAIT_MS = 10_000L;

    private final FakeTaiRuntimeLink link = new FakeTaiRuntimeLink();
    private final FakeBackend backend = new FakeBackend(link);
    private final FeatureRequest requests = new FeatureRequest(backend);
    private ExecutorService pool;

    @Before
    public void startPool() {
        pool = Executors.newCachedThreadPool();
    }

    @After
    public void stopPool() {
        link.gates.open("hold");
        link.gates.open("load");
        pool.shutdownNow();
    }

    // ------------------------------------------------------------------------------ events

    @Test
    public void aStreamIsLoadingThenOneTextPerChunkThenDoneWithEachChunkUnchanged() throws Exception {
        Recorder sink = new Recorder();
        FeatureRequest.Handle handle = requests.start(FeatureRequest.Call.http(FeatureRequest.Kind.CHAT,
            streamBody(Arrays.asList("a", "b"), null, null)), sink);
        assertNull(handle.await());

        assertEquals(Arrays.asList("LOADING", "TEXT", "TEXT", "DONE"), sink.types());
        assertEquals(FakeTaiRuntimeLink.chunkEvent("a").toString(), sink.events.get(1).openAi.toString());
        assertEquals("a", sink.events.get(1).text);
        assertEquals(FakeTaiRuntimeLink.chunkEvent("b").toString(), sink.events.get(2).openAi.toString());
        assertNull(sink.events.get(3).openAi);
    }

    @Test
    public void aResidentModelIsNotLoading() throws Exception {
        backend.loaded.add("default-model");
        Recorder sink = new Recorder();
        requests.start(FeatureRequest.Call.http(FeatureRequest.Kind.CHAT, streamBody(Arrays.asList("a"), null, null)), sink).await();
        assertEquals(Arrays.asList("TEXT", "DONE"), sink.types());
    }

    @Test
    public void aStreamsChunkIsReadForTextReasoningFinishAndUsage() throws Exception {
        JSONObject chunk = new JSONObject("{\"choices\":[{\"index\":0,\"delta\":{\"content\":\"hi\","
            + "\"reasoning_content\":\"hm\"},\"finish_reason\":\"stop\"}],\"usage\":{\"total_tokens\":7}}");
        FeatureRequest.Event event = FeatureRequest.Event.of(chunk);
        assertEquals(FeatureRequest.Event.Type.TEXT, event.type);
        assertEquals("hi", event.text);
        assertEquals("hm", event.reasoning);
        assertEquals("stop", event.finishReason);
        assertEquals(7, event.usage.getInt("total_tokens"));

        FeatureRequest.Event completion = FeatureRequest.Event.of(new JSONObject(
            "{\"choices\":[{\"text\":\"abc\",\"finish_reason\":null}]}"));
        assertEquals("abc", completion.text);
        assertNull(completion.finishReason);

        FeatureRequest.Event error = FeatureRequest.Event.of(new JSONObject(
            "{\"ok\":false,\"error\":{\"code\":\"model_not_found\",\"message\":\"no\"}}"));
        assertEquals(FeatureRequest.Event.Type.ERROR, error.type);
        assertEquals("model_not_found", error.errorCode);
        assertEquals("no", error.errorMessage);
        assertEquals("flat_code", FeatureRequest.Event.of(new JSONObject("{\"error\":\"flat_code\"}")).errorCode);
    }

    @Test
    public void collectGathersAStream() throws Exception {
        FeatureRequest.Result result = requests.collect(chat(Arrays.asList("he", "llo"), null, null));
        assertTrue(result.ok());
        assertEquals("hello", result.text);
        assertEquals("", result.reasoning);
        assertNull(result.errorCode);
    }

    @Test
    public void aSingleReplyComesBackByteIdenticalAsOneTextThenDone() throws Exception {
        Recorder sink = new Recorder();
        JSONObject reply = requests.start(FeatureRequest.Call.http(FeatureRequest.Kind.CHAT,
            scriptBody(Arrays.asList("ab"), null, null, false)), sink).await();

        JSONObject expected = FakeTaiRuntimeLink.completionResult(new JSONObject().put("ok", true).put("response", "ab"));
        assertEquals(expected.toString(), reply.toString());
        assertEquals(Arrays.asList("LOADING", "TEXT", "DONE"), sink.types());
        assertEquals("ab", sink.events.get(1).text);
        assertEquals(reply.toString(), sink.events.get(1).openAi.toString());
    }

    @Test
    public void aSingleErrorIsOneErrorThenDone() throws Exception {
        Recorder sink = new Recorder();
        JSONObject reply = requests.start(FeatureRequest.Call.http(FeatureRequest.Kind.CHAT,
            scriptBody(Collections.emptyList(), null, "boom", false)), sink).await();
        assertEquals(reply.toString(), sink.events.get(sink.events.size() - 2).openAi.toString());
        assertEquals("ERROR", sink.types().get(sink.types().size() - 2));
        FeatureRequest.Event done = sink.events.get(sink.events.size() - 1);
        assertEquals("boom", done.errorCode);
    }

    @Test
    public void aStreamThatErrorsEndsWithDoneCarryingTheCode() throws Exception {
        Recorder sink = new Recorder();
        requests.start(FeatureRequest.Call.http(FeatureRequest.Kind.CHAT,
            scriptBody(Arrays.asList("x"), null, "boom", true)), sink).await();
        List<String> types = sink.types();
        assertEquals("ERROR", types.get(types.size() - 2));
        assertEquals("DONE", types.get(types.size() - 1));
        assertEquals("boom", sink.events.get(sink.events.size() - 1).errorCode);
    }

    // ------------------------------------------------------------------------------ the model rule

    @Test
    public void aRemoteFeatureNeverTouchesTheLink() throws Exception {
        backend.plans.put(TaiFunction.TIDY_DICTATION, plan(TaiFunction.TIDY_DICTATION, "remote", "remote/gpt"));
        Recorder sink = new Recorder();
        JSONObject reply = requests.start(FeatureRequest.Call.chat(TaiFunction.TIDY_DICTATION,
            new JSONObject("{\"messages\":[]}")).stream(false), sink).await();

        assertEquals(1, backend.remote.chatBodies.size());
        assertEquals("remote/gpt", new JSONObject(backend.remote.chatBodies.get(0)).getString("model"));
        assertFalse(new JSONObject(backend.remote.chatBodies.get(0)).has(TaiCallerRequests.FUNCTION));
        assertEquals(0, link.gates.generateCalls.get());
        assertTrue(backend.localBodies.isEmpty());
        assertEquals("remote ok", reply.getString("marker"));
        assertEquals(Arrays.asList("TEXT", "DONE"), sink.types());
    }

    @Test
    public void anExplicitRemoteModelGoesRemoteAndACompletionNever() throws Exception {
        requests.start(FeatureRequest.Call.http(FeatureRequest.Kind.CHAT, "{\"model\":\"remote/m\",\"messages\":[]}")
            .stream(false), new Recorder()).await();
        assertEquals(1, backend.remote.chatBodies.size());

        Recorder sink = new Recorder();
        requests.start(FeatureRequest.Call.http(FeatureRequest.Kind.COMPLETION,
            new JSONObject(scriptBody(Arrays.asList("c"), null, null, false)).put("model", "remote/m").toString()), sink).await();
        assertEquals(1, backend.remote.chatBodies.size());
        assertEquals(1, link.gates.generateCalls.get());
    }

    @Test
    public void cleanupWithTheAssistantsModelResidentDoesNotLoad() throws Exception {
        backend.plans.put(TaiFunction.TIDY_DICTATION, plan(TaiFunction.TIDY_DICTATION, "on_device", "m-assist"));
        backend.loaded.add("m-assist");
        Recorder sink = new Recorder();
        requests.start(FeatureRequest.Call.chat(TaiFunction.TIDY_DICTATION, new JSONObject(
            FakeTaiRuntimeLink.script(Arrays.asList("ok"), 0L, null, null))), sink).await();

        assertEquals(Arrays.asList("TEXT", "DONE"), sink.types());
        JSONObject body = new JSONObject(backend.localBodies.get(0));
        assertEquals("m-assist", body.getString("model"));
        assertEquals(TaiFunction.TIDY_DICTATION.id(), body.getString(TaiCallerRequests.FUNCTION));
    }

    @Test
    public void anExplicitHttpModelBeatsThePlan() throws Exception {
        backend.plans.put(TaiFunction.ASSISTANT, plan(TaiFunction.ASSISTANT, "on_device", "m-plan"));
        requests.start(FeatureRequest.Call.http(FeatureRequest.Kind.CHAT, new JSONObject(
            streamBody(Arrays.asList("a"), null, null)).put("model", "m-named").toString()), new Recorder()).await();
        assertEquals("m-named", new JSONObject(backend.localBodies.get(0)).getString("model"));
    }

    @Test
    public void anUntaggedHttpRequestUsesTheAssistantsPlanAndElseTheDefaultModel() throws Exception {
        backend.plans.put(TaiFunction.ASSISTANT, plan(TaiFunction.ASSISTANT, "on_device", "m-plan"));
        requests.start(FeatureRequest.Call.http(FeatureRequest.Kind.CHAT, streamBody(Arrays.asList("a"), null, null)),
            new Recorder()).await();
        assertEquals(Collections.singletonList(TaiFunction.ASSISTANT), backend.planned);
        assertEquals("m-plan", new JSONObject(backend.localBodies.get(0)).getString("model"));

        backend.plans.clear();
        backend.planned.clear();
        requests.start(FeatureRequest.Call.http(FeatureRequest.Kind.CHAT, streamBody(Arrays.asList("a"), null, null)),
            new Recorder()).await();
        assertEquals(Collections.singletonList(TaiFunction.ASSISTANT), backend.planned);
        assertFalse(new JSONObject(backend.localBodies.get(1)).has("model"));
        assertEquals("default-model", backend.askedLoaded.get(backend.askedLoaded.size() - 1));
    }

    @Test
    public void anInAppRequestWithoutAFeatureHasNoPlan() throws Exception {
        requests.start(FeatureRequest.Call.chat(null, new JSONObject(
            FakeTaiRuntimeLink.script(Arrays.asList("a"), 0L, null, null))), new Recorder()).await();
        assertTrue(backend.planned.isEmpty());
        assertFalse(new JSONObject(backend.localBodies.get(0)).has(TaiCallerRequests.FUNCTION));
    }

    @Test
    public void aClientCannotSupplyTheRuntimesInternalKeys() throws Exception {
        String body = new JSONObject(streamBody(Arrays.asList("a"), null, null))
            .put("_taiModelSpec", new JSONObject().put("id", "evil"))
            .put("_taiRuntimeOptions", new JSONObject())
            .put("_taiFeaturePlan", new JSONObject()).toString();
        requests.start(FeatureRequest.Call.http(FeatureRequest.Kind.CHAT, body), new Recorder()).await();
        assertEquals(1, backend.localBodies.size());
        assertEquals(1, backend.requestsSeen.size());
        for (String key : new String[]{"_taiModelSpec", "_taiRuntimeOptions", "_taiFeaturePlan"}) {
            assertFalse(key, backend.requestsSeen.get(0).has(key));
        }
    }

    @Test
    public void aBodyThatIsNotJsonIsABadBody() {
        try {
            requests.start(FeatureRequest.Call.http(FeatureRequest.Kind.CHAT, "not json"), new Recorder());
            fail();
        } catch (JSONException expected) {
            // As today: the route reports it.
        }
    }

    // ------------------------------------------------------------------------------ priority

    @Test
    public void interactiveGoesAheadOfWaitingNormalAndATagNeverRaisesPriority() throws Exception {
        List<String> finished = Collections.synchronizedList(new ArrayList<>());
        Recorder holder = new Recorder("holder", finished);
        FeatureRequest.Handle held = requests.start(FeatureRequest.Call.http(FeatureRequest.Kind.CHAT,
            streamBody(Collections.emptyList(), "hold", null)), holder);
        Future<?> heldDone = pool.submit(() -> held.await());
        assertTrue(link.gates.awaitReached("hold", WAIT_MS));

        Recorder untagged = new Recorder("untagged", finished);
        Recorder tagged = new Recorder("tagged", finished);
        Recorder interactive = new Recorder("interactive", finished);
        FeatureRequest.Handle a = requests.start(FeatureRequest.Call.http(FeatureRequest.Kind.CHAT,
            streamBody(Collections.emptyList(), null, null)), untagged);
        FeatureRequest.Handle b = requests.start(FeatureRequest.Call.http(FeatureRequest.Kind.CHAT,
            new JSONObject(streamBody(Collections.emptyList(), null, null)).put("function", "cleanup").toString()), tagged);
        FeatureRequest.Handle c = requests.start(FeatureRequest.Call.chat(TaiFunction.ASSISTANT,
            new JSONObject(FakeTaiRuntimeLink.script(Collections.emptyList(), 0L, null, null)))
            .priority(FeatureRequest.Priority.INTERACTIVE), interactive);
        List<Future<?>> all = new ArrayList<>();
        for (FeatureRequest.Handle handle : Arrays.asList(a, b, c)) all.add(pool.submit(() -> handle.await()));

        link.gates.open("hold");
        heldDone.get(WAIT_MS, TimeUnit.MILLISECONDS);
        for (Future<?> future : all) future.get(WAIT_MS, TimeUnit.MILLISECONDS);

        assertEquals(Arrays.asList("holder", "interactive", "untagged", "tagged"), finished);
        assertTrue(untagged.types().contains("WAITING"));
    }

    // ------------------------------------------------------------------------------ cancel

    @Test
    public void cancelWhileRunningEndsWithGenerationCancelled() throws Exception {
        Recorder sink = new Recorder();
        FeatureRequest.Handle handle = requests.start(FeatureRequest.Call.http(FeatureRequest.Kind.CHAT,
            streamBody(Arrays.asList("a"), "hold", null)), sink);
        Future<?> done = pool.submit(() -> handle.await());
        assertTrue(link.gates.awaitReached("hold", WAIT_MS));

        handle.cancel();
        done.get(WAIT_MS, TimeUnit.MILLISECONDS);

        List<String> types = sink.types();
        assertEquals("ERROR", types.get(types.size() - 2));
        assertEquals("generation_cancelled", sink.events.get(types.size() - 2).errorCode);
        assertEquals("DONE", types.get(types.size() - 1));
    }

    @Test
    public void cancelWhileWaitingLeavesTheRunningOneAlone() throws Exception {
        Recorder holder = new Recorder();
        FeatureRequest.Handle held = requests.start(FeatureRequest.Call.http(FeatureRequest.Kind.CHAT,
            streamBody(Arrays.asList("h"), "hold", null)), holder);
        Future<?> heldDone = pool.submit(() -> held.await());
        assertTrue(link.gates.awaitReached("hold", WAIT_MS));

        Recorder waiting = new Recorder();
        FeatureRequest.Handle second = requests.start(FeatureRequest.Call.http(FeatureRequest.Kind.CHAT,
            streamBody(Arrays.asList("w"), null, null)), waiting);
        Future<?> secondDone = pool.submit(() -> second.await());
        second.cancel();
        secondDone.get(WAIT_MS, TimeUnit.MILLISECONDS);

        assertEquals(Arrays.asList("WAITING", "ERROR", "DONE"), waiting.types());
        assertEquals("generation_cancelled", waiting.events.get(1).errorCode);

        link.gates.open("hold");
        heldDone.get(WAIT_MS, TimeUnit.MILLISECONDS);
        assertFalse(holder.types().contains("ERROR"));
        assertEquals(1, link.gates.generateCalls.get());
    }

    @Test
    public void cancelWhileLoadingNeverGenerates() throws Exception {
        Recorder sink = new Recorder();
        FeatureRequest.Handle handle = requests.start(FeatureRequest.Call.http(FeatureRequest.Kind.CHAT,
            new JSONObject(streamBody(Arrays.asList("a"), null, null)).put("loadGate", "load").toString()), sink);
        Future<?> done = pool.submit(() -> handle.await());
        assertTrue(link.gates.awaitReached("load", WAIT_MS));

        handle.cancel();
        link.gates.open("load");
        done.get(WAIT_MS, TimeUnit.MILLISECONDS);

        assertEquals(0, link.gates.generateCalls.get());
        List<String> types = sink.types();
        assertEquals(Arrays.asList("LOADING", "ERROR", "DONE"), types);
        assertEquals("generation_cancelled", sink.events.get(1).errorCode);
    }

    @Test
    public void cancelRemoteDisconnectsAndEndsWithGenerationCancelled() throws Exception {
        backend.remote.hold = true;
        Recorder sink = new Recorder();
        FeatureRequest.Handle handle = requests.start(FeatureRequest.Call.http(FeatureRequest.Kind.CHAT,
            "{\"model\":\"remote/m\",\"messages\":[],\"stream\":true}"), sink);
        Future<?> done = pool.submit(() -> handle.await());
        assertTrue(backend.remote.holding.await(WAIT_MS, TimeUnit.MILLISECONDS));

        handle.cancel();
        done.get(WAIT_MS, TimeUnit.MILLISECONDS);

        assertEquals(1, backend.remote.connection.disconnects);
        assertEquals(Arrays.asList("ERROR", "DONE"), sink.types());
        assertEquals("generation_cancelled", sink.events.get(0).errorCode);
        assertEquals(0, link.gates.generateCalls.get());
        assertTrue(handle.id().startsWith("gen-"));
    }

    @Test
    public void cancelRemoteBeforeAwaitSendsNothing() throws Exception {
        Recorder sink = new Recorder();
        FeatureRequest.Handle handle = requests.start(FeatureRequest.Call.http(FeatureRequest.Kind.CHAT,
            "{\"model\":\"remote/m\",\"messages\":[],\"stream\":true}"), sink);
        handle.cancel();
        assertNull(handle.await());
        assertEquals(Arrays.asList("ERROR", "DONE"), sink.types());
        assertEquals("generation_cancelled", sink.events.get(0).errorCode);
        assertEquals(0, backend.remote.streams);
    }

    @Test
    public void aSinkThatFailsCancelsThatRequestAndTheFailureComesOutOfAwait() throws Exception {
        List<FeatureRequest.Event> seen = Collections.synchronizedList(new ArrayList<>());
        FeatureRequest.Handle handle = requests.start(FeatureRequest.Call.http(FeatureRequest.Kind.CHAT,
            new JSONObject(streamBody(Arrays.asList("a", "b", "c", "d"), null, null)).put("delayMs", 30).toString()),
            event -> {
                seen.add(event);
                if (event.type == FeatureRequest.Event.Type.TEXT) throw new IOException("client went away");
            });
        try {
            handle.await();
            fail();
        } catch (IOException expected) {
            assertEquals("client went away", expected.getMessage());
        }
        int texts = 0;
        for (FeatureRequest.Event event : seen) if (event.type == FeatureRequest.Event.Type.TEXT) texts++;
        assertEquals(1, texts);
    }

    // ------------------------------------------------------------------------------ collect

    @Test
    public void collectTimesOutAndCancelsTheGeneration() throws Exception {
        long started = System.nanoTime();
        FeatureRequest.Result result = requests.collect(chat(Arrays.asList("a"), "hold", null).timeoutMs(150L));
        assertEquals("tai_runtime_timeout", result.errorCode);
        assertFalse(result.ok());
        assertTrue((System.nanoTime() - started) / 1_000_000L < WAIT_MS);

        // The lane is free again: the cancelled generation did not stay on it.
        FeatureRequest.Result next = requests.collect(chat(Arrays.asList("z"), null, null));
        assertTrue(next.ok());
        assertEquals("z", next.text);
    }

    @Test
    public void collectReportsAnErrorFromTheAnswer() throws Exception {
        FeatureRequest.Result result = requests.collect(chat(Arrays.asList("a"), null, "boom"));
        assertFalse(result.ok());
        assertEquals("boom", result.errorCode);
        assertNotNull(result.error);
    }

    @Test
    public void anAnswerGuardTurnsARejectedAnswerIntoRefusalOrAnswerKeepingTheText() throws Exception {
        FeatureRequest.Result result = requests.collect(chat(Arrays.asList("I cannot", " help"), null, null)
            .answerGuard(answer -> answer.startsWith("I cannot")));
        assertEquals("refusal_or_answer", result.errorCode);
        assertEquals("I cannot help", result.text);

        FeatureRequest.Result fine = requests.collect(chat(Arrays.asList("ok"), null, null).answerGuard(answer -> false));
        assertTrue(fine.ok());
    }

    @Test
    public void collectNeverThrowsForABadBody() {
        FeatureRequest.Result result = requests.collect(FeatureRequest.Call.http(FeatureRequest.Kind.CHAT, "not json"));
        assertEquals("bad_request", result.errorCode);
    }

    // ------------------------------------------------------------------------------ warm

    @Test
    public void warmLoadsByThePlanAndARemoteModelNeedsNoLoad() throws Exception {
        backend.plans.put(TaiFunction.TIDY_DICTATION, plan(TaiFunction.TIDY_DICTATION, "on_device", "m-clean"));
        JSONObject reply = requests.warm(TaiFunction.TIDY_DICTATION, null);
        assertTrue(reply.getBoolean("ok"));
        assertEquals(1, link.calls.size());
        JSONObject loaded = new JSONObject(link.calls.get(0).substring("load:".length()));
        assertEquals("m-clean", loaded.getString("model"));
        assertEquals(TaiFunction.TIDY_DICTATION.id(), loaded.getString(TaiCallerRequests.FUNCTION));

        backend.plans.put(TaiFunction.TIDY_DICTATION, plan(TaiFunction.TIDY_DICTATION, "remote", "remote/x"));
        JSONObject remote = requests.warm(TaiFunction.TIDY_DICTATION, null);
        assertTrue(remote.getBoolean("remote"));
        assertEquals(1, link.calls.size());

        requests.warm(TaiFunction.TIDY_DICTATION, "m-named");
        assertEquals("m-named", new JSONObject(link.calls.get(1).substring("load:".length())).getString("model"));
    }

    // ------------------------------------------------------------------------------ helpers

    @NonNull
    private static FeatureRequest.Call chat(@NonNull List<String> chunks, @Nullable String gate, @Nullable String error)
        throws JSONException {
        return FeatureRequest.Call.chat(TaiFunction.ASSISTANT, new JSONObject(
            FakeTaiRuntimeLink.script(chunks, 0L, gate, error)));
    }

    @NonNull
    private static String streamBody(@NonNull List<String> chunks, @Nullable String gate, @Nullable String error) {
        return scriptBody(chunks, gate, error, true);
    }

    @NonNull
    private static String scriptBody(@NonNull List<String> chunks, @Nullable String gate, @Nullable String error,
                                     boolean stream) {
        try {
            return new JSONObject(FakeTaiRuntimeLink.script(chunks, 0L, gate, error)).put("stream", stream).toString();
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    @NonNull
    private static TaiFeaturePlan plan(@NonNull TaiFunction feature, @NonNull String where, @NonNull String model)
        throws JSONException {
        TaiFeaturePlan plan = TaiFeaturePlan.fromJson(new JSONObject().put("feature", feature.id()).put("where", where)
            .put("model", model));
        assertNotNull(plan);
        return plan;
    }

    private static final class Recorder implements FeatureRequest.Sink {
        final List<FeatureRequest.Event> events = Collections.synchronizedList(new ArrayList<>());
        @Nullable private final String name;
        @Nullable private final List<String> finished;

        Recorder() {
            this(null, null);
        }

        Recorder(@Nullable String name, @Nullable List<String> finished) {
            this.name = name;
            this.finished = finished;
        }

        @Override
        public void onEvent(@NonNull FeatureRequest.Event event) {
            events.add(event);
            if (event.type == FeatureRequest.Event.Type.DONE && finished != null) finished.add(name);
        }

        @NonNull
        List<String> types() {
            List<String> types = new ArrayList<>();
            synchronized (events) {
                for (FeatureRequest.Event event : events) types.add(event.type.name());
            }
            return types;
        }
    }

    private static final class FakeBackend implements FeatureRequest.Backend {
        private final TaiRuntimeLink link;
        final Map<TaiFunction, TaiFeaturePlan> plans = new HashMap<>();
        final List<TaiFunction> planned = new ArrayList<>();
        final Set<String> loaded = new HashSet<>();
        final List<String> askedLoaded = new ArrayList<>();
        final List<String> localBodies = new ArrayList<>();
        final List<JSONObject> requestsSeen = new ArrayList<>();
        final FakeRemote remote = new FakeRemote();

        FakeBackend(@NonNull TaiRuntimeLink link) {
            this.link = link;
        }

        @NonNull
        @Override
        public TaiRuntimeLink link() {
            return link;
        }

        @Nullable
        @Override
        public TaiFeaturePlan plan(@NonNull TaiFunction feature) {
            planned.add(feature);
            return plans.get(feature);
        }

        @NonNull
        @Override
        public String defaultModelId() {
            return "default-model";
        }

        @NonNull
        @Override
        public String localBody(@NonNull JSONObject request, @Nullable TaiFeaturePlan plan) {
            requestsSeen.add(request);
            String body = request.toString();
            localBodies.add(body);
            return body;
        }

        @Override
        public boolean isLoaded(@NonNull String modelId) {
            askedLoaded.add(modelId);
            return loaded.contains(modelId);
        }

        @NonNull
        @Override
        public FeatureRequest.RemoteTransport remote() {
            return remote;
        }
    }

    private static final class FakeRemote implements FeatureRequest.RemoteTransport {
        final List<String> chatBodies = new ArrayList<>();
        volatile boolean hold;
        volatile int streams;
        final CountDownLatch holding = new CountDownLatch(1);
        volatile Conn connection;

        @NonNull
        @Override
        public JSONObject chat(@NonNull String body, long timeoutMs, @NonNull TaiRemoteClient.Cancel cancel) throws JSONException {
            chatBodies.add(body);
            return new JSONObject().put("marker", "remote ok");
        }

        @Override
        public void stream(@NonNull String body, long timeoutMs, @NonNull TaiManager.OpenAiStreamSink sink,
                           @NonNull TaiRemoteClient.Cancel cancel) throws JSONException, IOException {
            streams++;
            if (hold) {
                try {
                    connection = new Conn();
                } catch (MalformedURLException e) {
                    throw new IllegalStateException(e);
                }
                cancel.attach(connection);
                holding.countDown();
                long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(WAIT_MS);
                while (!cancel.isCancelled() && System.nanoTime() < deadline) {
                    try {
                        Thread.sleep(2L);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
                sink.onEvent(TaiManager.openAiError(TaiRemoteClient.cancelledSource()));
                sink.onDone();
                return;
            }
            sink.onEvent(FakeTaiRuntimeLink.chunkEvent("r"));
            sink.onDone();
        }
    }

    private static final class Conn extends HttpURLConnection {
        volatile int disconnects;

        Conn() throws MalformedURLException {
            super(new URL("http://127.0.0.1/"));
        }

        @Override public void disconnect() { disconnects++; }
        @Override public boolean usingProxy() { return false; }
        @Override public void connect() { }
    }
}
