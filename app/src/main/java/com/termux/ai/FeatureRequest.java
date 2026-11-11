package com.termux.ai;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * A Feature request: the one way a Feature (or an HTTP caller) asks for an answer (ADR 0027 section 5).
 * It parses the body once, resolves the Feature load plan once, picks the model by one rule, sends the
 * request to the remote provider or through the {@link TaiRuntimeLink}, and turns what comes back into
 * {@link Event}s. A stream and a single reply are the same flow; the reply is one event.
 *
 * <p>The model rule: an explicit {@code model} (or {@code modelId}) in the body wins; else the plan's
 * model when the plan is remote or on-device; else the Default model. A chat whose model is
 * {@code remote/...} goes to the remote provider and never touches the runtime; a completion is always local.
 *
 * <p>Events reach the {@link Sink} in order. {@code WAITING} and {@code LOADING} come from the runtime's
 * queue thread, before the answer; the rest come from the thread that calls {@link Handle#await}. Every
 * stream chunk is exactly one {@code TEXT} or {@code ERROR} event carrying the chunk unchanged, and the
 * end is one {@code DONE}, so an edge that writes {@link Event#openAi} per chunk and {@code [DONE]} per
 * {@code DONE} reproduces what the OpenAI routes wrote before. A sink that throws {@link IOException}
 * cancels that request, and the exception comes out of {@link Handle#await}.
 */
public final class FeatureRequest {
    /** Where a waiting request goes in the runtime's queue: interactive ones ahead of normal ones. */
    public enum Priority {
        NORMAL, INTERACTIVE;

        @NonNull
        TaiRuntimeLink.Priority link() {
            return this == INTERACTIVE ? TaiRuntimeLink.Priority.INTERACTIVE : TaiRuntimeLink.Priority.NORMAL;
        }

        @NonNull
        static Priority of(@NonNull TaiRuntimeLink.Priority priority) {
            return priority == TaiRuntimeLink.Priority.INTERACTIVE ? INTERACTIVE : NORMAL;
        }
    }

    public enum Kind { CHAT, COMPLETION }

    /** One step of a request. Immutable. */
    public static final class Event {
        public enum Type { WAITING, LOADING, TEXT, ERROR, DONE }

        @NonNull public final Type type;
        /** The content delta; empty when none. A completion's {@code choices[0].text}. */
        @NonNull public final String text;
        /** The {@code reasoning_content} delta; empty when none. */
        @NonNull public final String reasoning;
        /** TEXT: the chunk's {@code finish_reason}; DONE: the last one seen. */
        @Nullable public final String finishReason;
        /** TEXT: the chunk's usage; DONE: the last one seen. */
        @Nullable public final JSONObject usage;
        /** ERROR, and DONE after an ERROR: the error's code. */
        @Nullable public final String errorCode;
        @Nullable public final String errorMessage;
        /** TEXT and ERROR: the chunk (a stream) or reply (a single answer) this event came from, unchanged. */
        @Nullable public final JSONObject openAi;

        private Event(@NonNull Type type, @NonNull String text, @NonNull String reasoning, @Nullable String finishReason,
                      @Nullable JSONObject usage, @Nullable String errorCode, @Nullable String errorMessage,
                      @Nullable JSONObject openAi) {
            this.type = type;
            this.text = text;
            this.reasoning = reasoning;
            this.finishReason = finishReason;
            this.usage = usage;
            this.errorCode = errorCode;
            this.errorMessage = errorMessage;
            this.openAi = openAi;
        }

        @NonNull public static Event waiting() { return signal(Type.WAITING); }

        @NonNull public static Event loading() { return signal(Type.LOADING); }

        @NonNull public static Event done() { return signal(Type.DONE); }

        @NonNull
        public static Event text(@NonNull String text) {
            return new Event(Type.TEXT, text, "", null, null, null, null, null);
        }

        /** A TEXT with a finish reason. */
        @NonNull
        public static Event text(@NonNull String text, @Nullable String finishReason) {
            return new Event(Type.TEXT, text, "", finishReason, null, null, null, null);
        }

        @NonNull
        public static Event error(@Nullable String code, @Nullable String message) {
            return new Event(Type.ERROR, "", "", null, null, code, message, null);
        }

        @NonNull
        static Event signal(@NonNull Type type) {
            return new Event(type, "", "", null, null, null, null, null);
        }

        @NonNull
        static Event done(@Nullable String finishReason, @Nullable JSONObject usage, @Nullable String errorCode,
                          @Nullable String errorMessage) {
            return new Event(Type.DONE, "", "", finishReason, usage, errorCode, errorMessage, null);
        }

        /** Reads one chunk or reply: an ERROR the way {@code StreamEvent.parse} detects one, else a TEXT. */
        @NonNull
        static Event of(@NonNull JSONObject chunk) {
            JSONObject error = chunk.optJSONObject("error");
            boolean failed = chunk.has("ok") && !chunk.optBoolean("ok", true);
            if (error != null || failed || (chunk.has("error") && !chunk.isNull("error"))) {
                String raw = error != null ? error.optString("code", "") : "";
                if (raw.isEmpty()) raw = chunk.optString("error_code", chunk.optString("code", ""));
                if (raw.isEmpty() && error == null) raw = chunk.optString("error", "");
                String message = error != null ? error.optString("message", "") : "";
                if (message.isEmpty()) message = chunk.optString("message", "");
                return new Event(Type.ERROR, "", "", null, null, raw.isEmpty() ? null : raw,
                    message.isEmpty() ? null : message, chunk);
            }
            String text = "";
            String reasoning = "";
            String finish = null;
            JSONArray choices = chunk.optJSONArray("choices");
            JSONObject choice = choices == null || choices.length() == 0 ? null : choices.optJSONObject(0);
            if (choice != null) {
                JSONObject part = choice.optJSONObject("delta");
                if (part == null) part = choice.optJSONObject("message");
                if (part != null) {
                    text = string(part, "content");
                    reasoning = string(part, "reasoning_content");
                }
                if (text.isEmpty()) text = string(choice, "text");
                if (!choice.isNull("finish_reason") && choice.has("finish_reason")) {
                    finish = choice.optString("finish_reason", "");
                }
            }
            JSONObject usage = chunk.optJSONObject("usage");
            return new Event(Type.TEXT, text, reasoning, finish, usage, null, null, chunk);
        }

        @NonNull
        private static String string(@NonNull JSONObject object, @NonNull String key) {
            return object.has(key) && !object.isNull(key) ? object.optString(key, "") : "";
        }
    }

    public interface Sink {
        void onEvent(@NonNull Event event) throws IOException;
    }

    /** Judges a finished answer; {@code true} makes {@link #collect} report {@code refusal_or_answer}. */
    public interface AnswerGuard {
        boolean rejects(@NonNull String answer);
    }

    /** A request in flight. */
    public interface Handle {
        @NonNull String id();

        /** Stops this request only: out of the queue if it waits, stopped if it runs, disconnected if remote. */
        void cancel();

        /**
         * Waits for the end; the events have all reached the sink when this returns. A single reply
         * (not a stream) comes back as the transport returned it; a stream returns {@code null}.
         */
        @Nullable
        JSONObject await() throws JSONException, IOException;
    }

    /** What {@link #collect} gathered. */
    public static final class Result {
        @NonNull public final String text;
        @NonNull public final String reasoning;
        @Nullable public final String finishReason;
        @Nullable public final JSONObject usage;
        @Nullable public final String errorCode;
        @Nullable public final String errorMessage;
        /** The ERROR event's {@link Event#openAi}, when the answer ended in one. */
        @Nullable public final JSONObject error;

        Result(@NonNull String text, @NonNull String reasoning, @Nullable String finishReason, @Nullable JSONObject usage,
               @Nullable String errorCode, @Nullable String errorMessage, @Nullable JSONObject error) {
            this.text = text;
            this.reasoning = reasoning;
            this.finishReason = finishReason;
            this.usage = usage;
            this.errorCode = errorCode;
            this.errorMessage = errorMessage;
            this.error = error;
        }

        public boolean ok() {
            return errorCode == null && error == null;
        }
    }

    /** One request: build with {@link #chat} or {@link #http}. */
    public static final class Call {
        @Nullable private final TaiFunction feature;
        @NonNull private final Kind kind;
        @NonNull private final String body;
        private final boolean http;
        private boolean stream;
        @NonNull private Priority priority = Priority.NORMAL;
        private long timeoutMs;
        @Nullable private AnswerGuard guard;
        @Nullable private String id;

        private Call(@Nullable TaiFunction feature, @NonNull Kind kind, @NonNull String body, boolean http) {
            this.feature = feature;
            this.kind = kind;
            this.body = body;
            this.http = http;
        }

        /**
         * An in-app chat: messages and sampling keys, an optional explicit {@code model}. Streams, normal
         * priority, no deadline unless set. A {@code null} feature names none: no plan, the Default model.
         */
        @NonNull
        public static Call chat(@Nullable TaiFunction feature, @NonNull JSONObject body) {
            Call call = new Call(feature, Kind.CHAT, body.toString(), false);
            call.stream = true;
            return call;
        }

        /**
         * An HTTP request as the client sent it. The Feature is the body's {@code function}, none being
         * the Assistant; a tag never raises the priority; the deadline is the chat default.
         */
        @NonNull
        public static Call http(@NonNull Kind kind, @NonNull String rawBody) {
            Call call = new Call(null, kind, rawBody, true);
            call.timeoutMs = TaiManager.DEFAULT_CHAT_TIMEOUT_MS;
            try {
                call.stream = !rawBody.trim().isEmpty() && new JSONObject(rawBody).optBoolean("stream", false);
            } catch (JSONException e) {
                call.stream = false; // start() reports the body.
            }
            return call;
        }

        @NonNull
        public Call priority(@NonNull Priority priority) {
            this.priority = priority;
            return this;
        }

        @NonNull
        public Call timeoutMs(long timeoutMs) {
            this.timeoutMs = timeoutMs;
            return this;
        }

        @NonNull
        public Call answerGuard(@Nullable AnswerGuard guard) {
            this.guard = guard;
            return this;
        }

        @NonNull
        public Call stream(boolean stream) {
            this.stream = stream;
            return this;
        }

        @NonNull
        Call id(@Nullable String id) {
            this.id = id;
            return this;
        }
    }

    // ------------------------------------------------------------------------------------ backend

    /** What a Feature request needs of the app: the link, the plan, the Default model, the body, what is resident, the remote. */
    interface Backend {
        @NonNull TaiRuntimeLink link();

        @Nullable TaiFeaturePlan plan(@NonNull TaiFunction feature);

        @NonNull String defaultModelId();

        /** The body the runtime receives: the model's spec and options and an on-device plan attached. */
        @NonNull String localBody(@NonNull JSONObject request, @Nullable TaiFeaturePlan plan) throws JSONException;

        boolean isLoaded(@NonNull String modelId);

        @NonNull RemoteTransport remote();

        /** False in {@code :tai_runtime}, which never reaches the remote provider. */
        default boolean remoteAllowed() {
            return true;
        }
    }

    /** The remote provider's two calls, stoppable. */
    interface RemoteTransport {
        @NonNull
        JSONObject chat(@NonNull String body, long timeoutMs, @NonNull TaiRemoteClient.Cancel cancel) throws JSONException;

        void stream(@NonNull String body, long timeoutMs, @NonNull TaiManager.OpenAiStreamSink sink,
                    @NonNull TaiRemoteClient.Cancel cancel) throws JSONException, IOException;
    }

    private static final ScheduledThreadPoolExecutor DEADLINES = new ScheduledThreadPoolExecutor(1, runnable -> {
        Thread thread = new Thread(runnable, "feature-request-deadline");
        thread.setDaemon(true);
        return thread;
    });

    static {
        DEADLINES.setRemoveOnCancelPolicy(true);
    }

    @NonNull private final Backend backend;

    FeatureRequest(@NonNull Backend backend) {
        this.backend = backend;
    }

    /** The one for this app, backed by {@link TaiManager}. */
    @NonNull
    public static FeatureRequest get(@NonNull Context context) {
        return TaiManager.getInstance(context).featureRequest();
    }

    @NonNull
    static Backend productionBackend(@NonNull TaiManager manager, @NonNull Context context) {
        return new Backend() {
            @NonNull
            @Override
            public TaiRuntimeLink link() {
                return manager.runtimeLink();
            }

            @Nullable
            @Override
            public TaiFeaturePlan plan(@NonNull TaiFunction feature) {
                return manager.featurePlanFor(feature);
            }

            @NonNull
            @Override
            public String defaultModelId() {
                return manager.defaultAssistantModelId();
            }

            @NonNull
            @Override
            public String localBody(@NonNull JSONObject request, @Nullable TaiFeaturePlan plan) throws JSONException {
                return manager.runtimeBodyFor(request, plan);
            }

            @Override
            public boolean isLoaded(@NonNull String modelId) {
                try {
                    return ModelAvailability.get(context).stateOf(modelId) == ModelAvailability.State.LOADED;
                } catch (RuntimeException e) {
                    return false;
                }
            }

            @NonNull
            @Override
            public RemoteTransport remote() {
                TaiRemoteProvider provider = new TaiRemoteProvider(context);
                return new RemoteTransport() {
                    @NonNull
                    @Override
                    public JSONObject chat(@NonNull String body, long timeoutMs, @NonNull TaiRemoteClient.Cancel cancel)
                        throws JSONException {
                        return provider.chatCompletions(body, timeoutMs, cancel);
                    }

                    @Override
                    public void stream(@NonNull String body, long timeoutMs, @NonNull TaiManager.OpenAiStreamSink sink,
                                       @NonNull TaiRemoteClient.Cancel cancel) throws JSONException, IOException {
                        provider.stream(body, timeoutMs, sink, cancel);
                    }
                };
            }

            @Override
            public boolean remoteAllowed() {
                return !manager.isRuntimeProcess();
            }
        };
    }

    // ----------------------------------------------------------------------------------- the flow

    @NonNull
    public Handle start(@NonNull Call call, @NonNull Sink sink) throws JSONException {
        JSONObject request = parse(call.body);
        TaiManager.stripInternalKeys(request);
        TaiFunction feature = call.http ? TaiCallerRequests.featureFor(request, true) : call.feature;
        TaiFeaturePlan plan = feature == null ? null : backend.plan(feature);
        pickModel(request, plan);
        if (!call.http) {
            request.put("stream", call.stream);
            if (feature != null) request.put(TaiCallerRequests.FUNCTION, feature.id());
        }
        String id = call.id == null || call.id.trim().isEmpty() ? TaiRuntimeLink.GenerationRequest.newId() : call.id;
        boolean remote = call.kind == Kind.CHAT && backend.remoteAllowed()
            && TaiCallerRequests.isRemoteModel(request.optString("model", ""));
        if (remote) return startRemote(call, request, id, sink);
        return startLocal(call, request, plan, id, sink);
    }

    /**
     * A single reply as the transport returned it, for the callers that still take today's JSON (the
     * OpenAI routes). The runtime's own failure to answer is the {@code tai_runtime_unavailable} error.
     */
    @NonNull
    public JSONObject reply(@NonNull Call call) throws JSONException {
        try {
            JSONObject reply = start(call.stream(false), event -> { }).await();
            return reply != null ? reply : Run.unavailable();
        } catch (IOException e) {
            // A single reply writes to no sink of the caller's; nothing here does I/O of theirs.
            throw new IllegalStateException(e);
        }
    }

    /** Collects an answer; never throws. */
    @NonNull
    public Result collect(@NonNull Call call) {
        Collector collector = new Collector();
        Handle handle;
        try {
            handle = start(call, collector);
        } catch (JSONException e) {
            return collector.finish(call.guard, "bad_request", message(e));
        } catch (RuntimeException e) {
            return collector.finish(call.guard, "tai_runtime_stream_failed", message(e));
        }
        ScheduledFuture<?> deadline = null;
        if (call.timeoutMs > 0L) {
            deadline = DEADLINES.schedule(() -> {
                collector.timedOut = true;
                handle.cancel();
            }, call.timeoutMs, TimeUnit.MILLISECONDS);
        }
        String failureCode = null;
        String failureMessage = null;
        try {
            handle.await();
        } catch (JSONException e) {
            failureCode = "bad_request";
            failureMessage = message(e);
        } catch (IOException | RuntimeException e) {
            failureCode = "tai_runtime_stream_failed";
            failureMessage = message(e);
        } finally {
            if (deadline != null) deadline.cancel(false);
        }
        if (collector.timedOut) {
            failureCode = "tai_runtime_timeout";
            failureMessage = "On-device AI runtime service timed out.";
        }
        return collector.finish(call.guard, failureCode, failureMessage);
    }

    /**
     * Loads the Feature's model once, by its plan: the same model rule and runtime body as a request.
     * A remote model needs no load: {@code {"ok":true,"remote":true}}.
     */
    @NonNull
    public JSONObject warm(@NonNull TaiFunction feature, @Nullable String model) throws JSONException {
        JSONObject request = new JSONObject();
        if (model != null && !model.trim().isEmpty()) request.put("model", model.trim());
        TaiFeaturePlan plan = backend.plan(feature);
        pickModel(request, plan);
        if (backend.remoteAllowed() && TaiCallerRequests.isRemoteModel(request.optString("model", ""))) {
            return new JSONObject().put("ok", true).put("remote", true);
        }
        request.put(TaiCallerRequests.FUNCTION, feature.id());
        return backend.link().load(backend.localBody(request, plan));
    }

    // --------------------------------------------------------------------------------- internals

    @NonNull
    private static JSONObject parse(@NonNull String body) throws JSONException {
        if (body.trim().isEmpty()) return new JSONObject();
        return new JSONObject(body);
    }

    /** The one model rule: explicit, else the plan's when remote or on-device; else left to the Default model. */
    private static void pickModel(@NonNull JSONObject request, @Nullable TaiFeaturePlan plan) throws JSONException {
        String named = request.optString("model", request.optString("modelId", "")).trim();
        if (!named.isEmpty() || plan == null) return;
        String planned = plan.requestModel();
        boolean usable = plan.isRemote() || plan.where == TaiFeaturePlan.Where.ON_DEVICE;
        if (usable && planned != null && !planned.trim().isEmpty()) request.put("model", planned);
    }

    @NonNull
    private Handle startLocal(@NonNull Call call, @NonNull JSONObject request, @Nullable TaiFeaturePlan plan,
                              @NonNull String id, @NonNull Sink sink) throws JSONException {
        String body = backend.localBody(request, plan);
        String modelId = new JSONObject(body).optString("model", "").trim();
        if (modelId.isEmpty()) modelId = backend.defaultModelId();
        Run run = new Run(sink, backend, modelId);
        TaiRuntimeLink.GenerationRequest generationRequest = new TaiRuntimeLink.GenerationRequest(
            call.kind == Kind.CHAT ? TaiRuntimeLink.Kind.CHAT : TaiRuntimeLink.Kind.COMPLETION, call.stream, body,
            call.priority.link(), id, call.stream ? 0L : call.timeoutMs);
        TaiRuntimeLink.Generation generation = backend.link().generate(generationRequest, run);
        run.generation = generation;
        if (run.failure != null) generation.cancel();
        return new Handle() {
            @NonNull
            @Override
            public String id() {
                return generation.id();
            }

            @Override
            public void cancel() {
                generation.cancel();
            }

            @Nullable
            @Override
            public JSONObject await() throws JSONException, IOException {
                JSONObject reply;
                try {
                    reply = generation.await();
                } catch (IOException e) {
                    generation.cancel();
                    throw e;
                }
                IOException failed = run.failure;
                if (failed != null) throw failed;
                if (call.stream) return null;
                return run.reply(reply);
            }
        };
    }

    @NonNull
    private Handle startRemote(@NonNull Call call, @NonNull JSONObject request, @NonNull String id, @NonNull Sink sink)
        throws JSONException {
        String body = TaiCallerRequests.remoteBody(request);
        TaiRemoteClient.Cancel cancel = new TaiRemoteClient.Cancel();
        Run run = new Run(sink, backend, request.optString("model", ""));
        run.remoteCancel = cancel;
        RemoteTransport transport = backend.remote();
        long timeoutMs = call.timeoutMs > 0L ? call.timeoutMs : TaiManager.DEFAULT_CHAT_TIMEOUT_MS;
        return new Handle() {
            @NonNull
            @Override
            public String id() {
                return id;
            }

            @Override
            public void cancel() {
                cancel.cancel();
            }

            @Nullable
            @Override
            public JSONObject await() throws JSONException, IOException {
                if (call.stream) {
                    if (cancel.isCancelled()) {
                        run.onEvent(TaiManager.openAiError(TaiRemoteClient.cancelledSource()));
                        run.onDone();
                    } else {
                        transport.stream(body, timeoutMs, run, cancel);
                    }
                    IOException failed = run.failure;
                    if (failed != null) throw failed;
                    return null;
                }
                JSONObject reply = cancel.isCancelled()
                    ? TaiManager.openAiError(TaiRemoteClient.cancelledSource())
                    : transport.chat(body, timeoutMs, cancel);
                return run.reply(reply);
            }
        };
    }

    @NonNull
    private static String message(@NonNull Throwable t) {
        String message = t.getMessage();
        return message == null || message.isEmpty() ? t.getClass().getSimpleName() : message;
    }

    /** Turns what the transport reports into events for one sink; remembers the end for DONE. */
    private static final class Run implements TaiRuntimeLink.GenerationSink {
        private final Sink sink;
        private final Backend backend;
        private final String modelId;
        @Nullable volatile TaiRuntimeLink.Generation generation;
        @Nullable volatile TaiRemoteClient.Cancel remoteCancel;
        @Nullable volatile IOException failure;
        @Nullable private String lastFinish;
        @Nullable private JSONObject lastUsage;
        @Nullable private String errorCode;
        @Nullable private String errorMessage;

        Run(@NonNull Sink sink, @NonNull Backend backend, @NonNull String modelId) {
            this.sink = sink;
            this.backend = backend;
            this.modelId = modelId;
        }

        @Override
        public void onWaiting() {
            signal(Event.signal(Event.Type.WAITING));
        }

        @Override
        public void onStarted() {
            if (!backend.isLoaded(modelId)) signal(Event.signal(Event.Type.LOADING));
        }

        @Override
        public void onEvent(@NonNull JSONObject chunk) throws IOException {
            emit(Event.of(chunk));
        }

        @Override
        public void onDone() throws IOException {
            emit(Event.done(lastFinish, lastUsage, errorCode, errorMessage));
        }

        /** A single reply: one TEXT or ERROR, then DONE; the reply itself, or the unavailable error for none. */
        @NonNull
        JSONObject reply(@Nullable JSONObject reply) throws JSONException, IOException {
            JSONObject answer = reply != null ? reply : unavailable();
            onEvent(answer);
            onDone();
            return answer;
        }

        @NonNull
        private static JSONObject unavailable() throws JSONException {
            JSONObject data = new JSONObject();
            data.put("ok", false);
            data.put("error", "tai_runtime_unavailable");
            data.put("message", "On-device AI runtime did not return a result.");
            data.put("_statusCode", 500);
            return data;
        }

        /** A queue signal cannot throw; a sink that fails here ends the request when it can. */
        private void signal(@NonNull Event event) {
            try {
                emit(event);
            } catch (IOException ignored) {
                // emit() has recorded and cancelled.
            }
        }

        private void emit(@NonNull Event event) throws IOException {
            if (event.type == Event.Type.TEXT || event.type == Event.Type.ERROR) {
                if (event.finishReason != null) lastFinish = event.finishReason;
                if (event.usage != null) lastUsage = event.usage;
                if (event.type == Event.Type.ERROR) {
                    errorCode = event.errorCode;
                    errorMessage = event.errorMessage;
                }
            }
            try {
                sink.onEvent(event);
            } catch (IOException e) {
                failure = e;
                TaiRuntimeLink.Generation running = generation;
                if (running != null) running.cancel();
                TaiRemoteClient.Cancel remote = remoteCancel;
                if (remote != null) remote.cancel();
                throw e;
            }
        }
    }

    /** Gathers a request's events into a {@link Result}. */
    private static final class Collector implements Sink {
        private final StringBuilder text = new StringBuilder();
        private final StringBuilder reasoning = new StringBuilder();
        @Nullable private String finish;
        @Nullable private JSONObject usage;
        @Nullable private String errorCode;
        @Nullable private String errorMessage;
        @Nullable private JSONObject error;
        volatile boolean timedOut;

        @Override
        public synchronized void onEvent(@NonNull Event event) {
            if (event.type == Event.Type.TEXT) {
                text.append(event.text);
                reasoning.append(event.reasoning);
                if (event.finishReason != null) finish = event.finishReason;
                if (event.usage != null) usage = event.usage;
            } else if (event.type == Event.Type.ERROR) {
                error = event.openAi;
                errorCode = event.errorCode != null ? event.errorCode : "tai_error";
                errorMessage = event.errorMessage;
            }
        }

        @NonNull
        synchronized Result finish(@Nullable AnswerGuard guard, @Nullable String failureCode, @Nullable String failureMessage) {
            String code = errorCode;
            String message = errorMessage;
            if (failureCode != null) {
                code = failureCode;
                message = failureMessage;
            } else if (code == null && guard != null && text.length() > 0 && guard.rejects(text.toString())) {
                code = "refusal_or_answer";
                message = "The answer was rejected.";
            }
            return new Result(text.toString(), reasoning.toString(), finish, usage, code, message, error);
        }
    }
}
