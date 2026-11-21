package com.termux.ai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

/**
 * An in-memory {@link TaiRuntimeLink} for tests of the layers above the seam. Each generation plays
 * the script its body describes ({@link #script}): chunks with a delay between them, then an
 * optional hold on a named {@link Gates gate}, then an optional error. Generations queue one at a
 * time on a {@link GenerationQueue}, so a sink hears waiting and started as it would through either
 * production adapter. Residency calls answer {@code ok} and are recorded in {@link #calls}.
 */
final class FakeTaiRuntimeLink implements TaiRuntimeLink {
    /** Named holds a scripted generation waits on until a test opens them. */
    static final class Gates {
        private final Map<String, CountDownLatch> gates = new ConcurrentHashMap<>();
        private final Map<String, CountDownLatch> reached = new ConcurrentHashMap<>();
        /** How many scripted generations reached the runtime's generate step. */
        final AtomicInteger generateCalls = new AtomicInteger();

        private CountDownLatch gate(@NonNull String name) {
            return gates.computeIfAbsent(name, key -> new CountDownLatch(1));
        }

        private CountDownLatch reachedLatch(@NonNull String name) {
            return reached.computeIfAbsent(name, key -> new CountDownLatch(1));
        }

        void open(@NonNull String name) {
            gate(name).countDown();
        }

        /** Waits until a generation is holding at {@code name}. */
        boolean awaitReached(@NonNull String name, long timeoutMs) throws InterruptedException {
            return reachedLatch(name).await(timeoutMs, TimeUnit.MILLISECONDS);
        }

        /** Holds until the gate opens ({@code true}) or {@code cancelled} turns true ({@code false}). */
        boolean hold(@NonNull String name, @NonNull BooleanSupplier cancelled) {
            reachedLatch(name).countDown();
            CountDownLatch gate = gate(name);
            try {
                while (!gate.await(2, TimeUnit.MILLISECONDS)) {
                    if (cancelled.getAsBoolean()) return false;
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
            return !cancelled.getAsBoolean();
        }
    }

    /** Receives a script's chunks as it plays. */
    interface ChunkSink {
        void onChunk(@NonNull String text) throws IOException, JSONException;
    }

    /**
     * A generation's script as its body: {@code chunks} streamed with {@code delayMs} before each,
     * then a hold on {@code gate} when one is named, then {@code error} when one is named.
     */
    @NonNull
    static String script(@NonNull List<String> chunks, long delayMs, @Nullable String gate, @Nullable String error) {
        return script(chunks, delayMs, gate, error, null);
    }

    /**
     * As above, with a load first that holds on {@code loadGate} and cannot be stopped, as a native
     * model load cannot: only a cancel flag read after it keeps the generation from running.
     */
    @NonNull
    static String script(@NonNull List<String> chunks, long delayMs, @Nullable String gate, @Nullable String error,
                         @Nullable String loadGate) {
        try {
            JSONObject body = new JSONObject();
            if (loadGate != null) body.put("loadGate", loadGate);
            body.put("chunks", new JSONArray(chunks));
            body.put("delayMs", delayMs);
            if (gate != null) body.put("gate", gate);
            if (error != null) body.put("error", error);
            return body.toString();
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    /** The script's load step: holds on its {@code loadGate}, if it names one, until the gate opens. */
    static void load(@NonNull String body, @NonNull Gates gates) throws JSONException {
        String loadGate = new JSONObject(body).optString("loadGate", "");
        if (!loadGate.isEmpty()) gates.hold(loadGate, () -> false);
    }

    /**
     * Plays a script: the runtime half both this fake and the in-process contract test's fake
     * runtime run. Returns the runtime's result, {@code generation_cancelled} when stopped.
     */
    @NonNull
    static JSONObject play(@NonNull String body, @NonNull Gates gates, @NonNull BooleanSupplier cancelled,
                           @NonNull ChunkSink sink) throws JSONException, IOException {
        gates.generateCalls.incrementAndGet();
        JSONObject script = new JSONObject(body);
        JSONArray chunks = script.optJSONArray("chunks");
        long delayMs = script.optLong("delayMs", 0L);
        StringBuilder text = new StringBuilder();
        for (int i = 0; chunks != null && i < chunks.length(); i++) {
            if (delayMs > 0L) sleep(delayMs);
            if (cancelled.getAsBoolean()) return cancelledResult();
            String chunk = chunks.getString(i);
            text.append(chunk);
            sink.onChunk(chunk);
        }
        String gate = script.optString("gate", "");
        if (!gate.isEmpty() && !gates.hold(gate, cancelled)) return cancelledResult();
        if (cancelled.getAsBoolean()) return cancelledResult();
        String error = script.optString("error", "");
        if (!error.isEmpty()) {
            return new JSONObject().put("ok", false).put("error", error).put("message", "Scripted " + error)
                .put("_statusCode", 500);
        }
        return new JSONObject().put("ok", true).put("response", text.toString());
    }

    /** Marks a result as the cancel before generating, which ends exactly as a waiting cancel. */
    @NonNull
    static JSONObject cancelledBeforeGenerating() throws JSONException {
        return new JSONObject().put("_beforeGenerating", true);
    }

    @NonNull
    static JSONObject cancelledResult() throws JSONException {
        return new JSONObject().put("ok", false).put("error", "generation_cancelled")
            .put("message", "Generation was cancelled.").put("_statusCode", 499);
    }

    /** A chat chunk as the stream routes write it, reduced to what the tests read. */
    @NonNull
    static JSONObject chunkEvent(@NonNull String text) throws JSONException {
        JSONObject delta = new JSONObject().put("content", text);
        JSONObject choice = new JSONObject().put("index", 0).put("delta", delta);
        return new JSONObject().put("object", "chat.completion.chunk").put("choices", new JSONArray().put(choice));
    }

    /** Ends a stream as the chat routes do: an OpenAI error event for a failed result, then done. */
    static void finishStream(@NonNull TaiManager.OpenAiStreamSink sink, @NonNull JSONObject result)
        throws JSONException, IOException {
        if (result.optBoolean("_beforeGenerating", false)) {
            sink.onEvent(InProcessTaiRuntimeLink.cancelledWhileWaiting());
            sink.onDone();
            return;
        }
        if (!result.optBoolean("ok", false)) sink.onEvent(TaiManager.openAiError(result));
        sink.onDone();
    }

    /** A non-streaming answer: the text as a chat completion, or the OpenAI error. */
    @NonNull
    static JSONObject completionResult(@NonNull JSONObject result) throws JSONException {
        if (result.optBoolean("_beforeGenerating", false)) return InProcessTaiRuntimeLink.cancelledWhileWaiting();
        if (!result.optBoolean("ok", false)) return TaiManager.openAiError(result);
        JSONObject message = new JSONObject().put("role", "assistant").put("content", result.optString("response", ""));
        JSONObject choice = new JSONObject().put("index", 0).put("message", message);
        return new JSONObject().put("object", "chat.completion").put("choices", new JSONArray().put(choice));
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    final Gates gates;

    FakeTaiRuntimeLink() {
        this(new Gates());
    }

    FakeTaiRuntimeLink(@NonNull Gates gates) {
        this.gates = gates;
    }

    /** Residency and cancel-all calls, in order: {@code load:<body>}, {@code unload}, {@code cancelAll:<reason>}, … */
    final List<String> calls = Collections.synchronizedList(new ArrayList<>());
    private final GenerationQueue queue = new GenerationQueue("fake-tai-link", this::stopRunning);
    @Nullable private volatile AtomicBoolean runningCancelled;

    private void stopRunning() {
        AtomicBoolean flag = runningCancelled;
        if (flag != null) flag.set(true);
    }

    @NonNull
    @Override
    public Generation generate(@NonNull GenerationRequest request, @NonNull GenerationSink sink) {
        FakeGeneration generation = new FakeGeneration(request, sink);
        queue.submit(request.id, request.priority, generation);
        return generation;
    }

    @Override
    public void cancel(@NonNull String generationId) {
        queue.cancel(generationId);
    }

    @NonNull
    @Override
    public JSONObject cancelAll(@NonNull String stopReason) throws JSONException {
        calls.add("cancelAll:" + stopReason);
        boolean active = runningCancelled != null;
        stopRunning();
        return new JSONObject().put("ok", true).put("cancelled", active);
    }

    @NonNull
    @Override
    public JSONObject load(@NonNull String body) throws JSONException {
        calls.add("load:" + body);
        return new JSONObject().put("ok", true);
    }

    @NonNull
    @Override
    public JSONObject unload() throws JSONException {
        calls.add("unload");
        return new JSONObject().put("ok", true);
    }

    @NonNull
    @Override
    public JSONObject unloadChat(@NonNull String modelId) throws JSONException {
        calls.add("unloadChat:" + modelId);
        return new JSONObject().put("ok", true);
    }

    @NonNull
    @Override
    public JSONObject keepWarm(@NonNull String body) throws JSONException {
        calls.add("keepWarm:" + body);
        return new JSONObject().put("ok", true);
    }

    @NonNull
    @Override
    public JSONObject status() throws JSONException {
        return new JSONObject().put("ok", true);
    }

    @NonNull
    @Override
    public RuntimeSnapshot runtimeStatus() {
        return new RuntimeSnapshot(runtimeState(), new JSONArray());
    }

    @NonNull
    @Override
    public TaiRuntimeState runtimeState() {
        return TaiRuntimeState.fromJson(null);
    }

    private final class FakeGeneration implements Generation, GenerationQueue.Job {
        private final GenerationRequest request;
        private final GenerationSink sink;
        private final CountDownLatch done = new CountDownLatch(1);
        @Nullable private volatile JSONObject result;
        @Nullable private volatile Exception failure;
        private volatile boolean cancelledWhileWaiting;

        FakeGeneration(@NonNull GenerationRequest request, @NonNull GenerationSink sink) {
            this.request = request;
            this.sink = sink;
        }

        @NonNull
        @Override
        public String id() {
            return request.id;
        }

        @Override
        public void cancel() {
            queue.cancel(request.id);
        }

        @Override
        public void onWaiting() {
            sink.onWaiting();
        }

        @Override
        public void onStarted() {
            sink.onStarted();
        }

        @Override
        public void onCancelled() {
            cancelledWhileWaiting = true;
            done.countDown();
        }

        @Override
        public void run() {
            AtomicBoolean cancelled = new AtomicBoolean();
            runningCancelled = cancelled;
            try {
                load(request.body, gates);
                JSONObject played = GenerationQueue.currentJobCancelled()
                    ? FakeTaiRuntimeLink.cancelledBeforeGenerating()
                    : play(request.body, gates, cancelled::get, text -> {
                        if (request.stream) sink.onEvent(chunkEvent(text));
                    });
                if (request.stream) finishStream(sink, played);
                else result = completionResult(played);
            } catch (JSONException | IOException e) {
                failure = e;
            } finally {
                runningCancelled = null;
                done.countDown();
            }
        }

        @Nullable
        @Override
        public JSONObject await() throws JSONException, IOException {
            try {
                done.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException(e);
            }
            if (cancelledWhileWaiting) {
                JSONObject error = InProcessTaiRuntimeLink.cancelledWhileWaiting();
                if (!request.stream) return error;
                sink.onEvent(error);
                sink.onDone();
                return null;
            }
            Exception thrown = failure;
            if (thrown instanceof JSONException) throw (JSONException) thrown;
            if (thrown instanceof IOException) throw (IOException) thrown;
            return request.stream ? null : result;
        }
    }
}
