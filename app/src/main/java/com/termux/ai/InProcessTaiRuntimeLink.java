package com.termux.ai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;

/**
 * The {@link TaiRuntimeLink} that runs the work in this process: in {@code :tai_runtime}, and in
 * tests that give {@link TaiManager} a runtime of their own. Generations go through a
 * {@link GenerationQueue} of the same kind {@link TaiRuntimeService} serves the Messenger adapter
 * from, so both adapters queue, prioritise and cancel alike. Loads, unloads, keep warm, cancel and
 * status run on the caller's thread, as they did before the seam.
 */
final class InProcessTaiRuntimeLink implements TaiRuntimeLink {
    /**
     * The runtime half of {@link TaiManager}: the work each operation does once it is in the
     * process that holds the models. Bodies are the ones the runtime process receives.
     */
    interface Host {
        @NonNull JSONObject generateHere(@NonNull Kind kind, @NonNull String body) throws JSONException;

        void streamHere(@NonNull Kind kind, @NonNull String body, @NonNull TaiManager.OpenAiStreamSink sink)
            throws JSONException, IOException;

        /** Stops the generation that is running now and nothing else ({@code TaiRuntime.cancel()}). */
        void cancelActiveGeneration();

        @NonNull JSONObject cancelAllHere(@NonNull String stopReason) throws JSONException;

        @NonNull JSONObject loadHere(@NonNull String body) throws JSONException;

        @NonNull JSONObject unloadHere() throws JSONException;

        @NonNull JSONObject unloadChatHere(@NonNull String modelId) throws JSONException;

        @NonNull JSONObject keepWarmHere(@NonNull String body) throws JSONException;

        @NonNull JSONObject statusHere() throws JSONException;

        /** The resident table, {@link TaiResidency#toJson}; empty for a runtime that keeps none. */
        @NonNull JSONArray residentsHere() throws JSONException;

        @NonNull TaiRuntimeState stateHere();
    }

    private final Host host;
    private final GenerationQueue queue;

    InProcessTaiRuntimeLink(@NonNull Host host) {
        this.host = host;
        this.queue = new GenerationQueue("tai-runtime-service", host::cancelActiveGeneration);
    }

    /** The lane this link queues on; in {@code :tai_runtime} the service queues on it too. */
    @NonNull
    GenerationQueue queue() {
        return queue;
    }

    /**
     * The answer a generation cancelled before it generated ends with (while it waited, or while
     * its model loaded), as an OpenAI error; the same shape the runtime's own errors take on the
     * chat routes.
     */
    @NonNull
    static JSONObject cancelledWhileWaiting() {
        try {
            JSONObject source = new JSONObject();
            source.put("ok", false);
            source.put("error", "generation_cancelled");
            source.put("message", "The generation was cancelled before it started.");
            source.put("_statusCode", 499);
            return TaiManager.openAiError(source);
        } catch (JSONException e) {
            return new JSONObject();
        }
    }

    @NonNull
    @Override
    public Generation generate(@NonNull GenerationRequest request, @NonNull GenerationSink sink) {
        InProcessGeneration generation = new InProcessGeneration(request, sink);
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
        return host.cancelAllHere(stopReason);
    }

    @NonNull
    @Override
    public JSONObject load(@NonNull String body) throws JSONException {
        return host.loadHere(body);
    }

    @NonNull
    @Override
    public JSONObject unload() throws JSONException {
        return host.unloadHere();
    }

    @NonNull
    @Override
    public JSONObject unloadChat(@NonNull String modelId) throws JSONException {
        return host.unloadChatHere(modelId);
    }

    @NonNull
    @Override
    public JSONObject keepWarm(@NonNull String body) throws JSONException {
        return host.keepWarmHere(body);
    }

    @NonNull
    @Override
    public JSONObject status() throws JSONException {
        return host.statusHere();
    }

    @NonNull
    @Override
    public RuntimeSnapshot runtimeStatus() {
        JSONArray residents;
        try {
            residents = host.residentsHere();
        } catch (JSONException e) {
            residents = new JSONArray();
        }
        return new RuntimeSnapshot(host.stateHere(), residents);
    }

    @NonNull
    @Override
    public TaiRuntimeState runtimeState() {
        return host.stateHere();
    }

    private final class InProcessGeneration implements Generation, GenerationQueue.Job {
        private final GenerationRequest request;
        private final GenerationSink sink;
        private final CountDownLatch done = new CountDownLatch(1);
        @Nullable private volatile JSONObject result;
        @Nullable private volatile Throwable failure;
        private volatile boolean cancelledWhileWaiting;

        InProcessGeneration(@NonNull GenerationRequest request, @NonNull GenerationSink sink) {
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
            try {
                if (request.stream) {
                    host.streamHere(request.kind, request.body, sink);
                } else {
                    result = host.generateHere(request.kind, request.body);
                }
            } catch (Throwable throwable) {
                failure = throwable;
            } finally {
                done.countDown();
            }
        }

        @Nullable
        @Override
        public JSONObject await() throws JSONException, IOException {
            boolean interrupted = false;
            while (true) {
                try {
                    done.await();
                    break;
                } catch (InterruptedException e) {
                    interrupted = true;
                }
            }
            if (interrupted) Thread.currentThread().interrupt();
            if (cancelledWhileWaiting) {
                JSONObject error = cancelledWhileWaiting();
                if (!request.stream) return error;
                sink.onEvent(error);
                sink.onDone();
                return null;
            }
            Throwable thrown = failure;
            if (thrown instanceof JSONException) throw (JSONException) thrown;
            if (thrown instanceof IOException) throw (IOException) thrown;
            if (thrown instanceof RuntimeException) throw (RuntimeException) thrown;
            if (thrown instanceof Error) throw (Error) thrown;
            if (thrown != null) throw new IOException(thrown);
            return request.stream ? null : result;
        }
    }
}
