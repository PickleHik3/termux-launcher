package com.termux.ai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.UUID;

/**
 * The app ↔ {@code :tai_runtime} seam for generating answers and holding models (ADR 0027 §4).
 * {@link TaiManager} talks to the runtime only through this for chat, completion, load, unload,
 * keep warm, cancel and the runtime's state; speech, image, embeddings, tokenize, preflight and
 * the benchmarks keep their own operations for now.
 *
 * <p>Two adapters: {@link InProcessTaiRuntimeLink} runs the work in this process on a
 * {@link GenerationQueue}, and {@link MessengerTaiRuntimeLink} sends it to {@link TaiRuntimeService},
 * which runs it on a queue of the same kind. Bodies and results are today's JSON, so what
 * Launcherctl prints does not change.
 *
 * <p>Every generation has an id and a priority. {@link Generation#cancel} stops that generation
 * if it is running, takes it out of the queue if it is waiting, and does nothing otherwise.
 * {@link #cancelAll} is {@code /v1/ai/runtime/cancel}: it stops whatever is running, as it always did.
 */
interface TaiRuntimeLink {
    enum Kind { CHAT, COMPLETION }

    /**
     * Where a waiting generation goes in the queue: interactive ones ahead of normal ones, never
     * ahead of the running one.
     */
    enum Priority {
        NORMAL, INTERACTIVE;

        /** The wire name: {@code normal} or {@code interactive}. */
        @NonNull
        String wireName() {
            return this == INTERACTIVE ? "interactive" : "normal";
        }

        /** {@link #NORMAL} for anything that is not {@code interactive}, a missing value included. */
        @NonNull
        static Priority fromWire(@Nullable String name) {
            return "interactive".equals(name) ? INTERACTIVE : NORMAL;
        }
    }

    /**
     * Receives a generation: today's OpenAI chunks (a stream) through {@link #onEvent} and
     * {@link #onDone}, plus where it stands in the queue. A non-streaming generation gets only the
     * two queue signals; its answer is what {@link Generation#await} returns.
     */
    interface GenerationSink extends TaiManager.OpenAiStreamSink {
        /** Queued behind another generation or lane job. */
        default void onWaiting() {
        }

        /** Its turn has come; the model may still be loading. */
        default void onStarted() {
        }

        @Override
        default void onEvent(@NonNull JSONObject event) throws IOException {
        }

        @Override
        default void onDone() throws IOException {
        }
    }

    /** One generation's request: today's chat or completion body, its id and its priority. */
    final class GenerationRequest {
        @NonNull final Kind kind;
        final boolean stream;
        @NonNull final String body;
        @NonNull final Priority priority;
        @NonNull final String id;
        /** How long a non-streaming caller waits across the process boundary; a stream does not time out. */
        final long timeoutMs;

        GenerationRequest(@NonNull Kind kind, boolean stream, @NonNull String body, @NonNull Priority priority,
                          @Nullable String id, long timeoutMs) {
            this.kind = kind;
            this.stream = stream;
            this.body = body;
            this.priority = priority;
            this.id = id == null || id.trim().isEmpty() ? newId() : id;
            this.timeoutMs = timeoutMs;
        }

        @NonNull
        static String newId() {
            return "gen-" + UUID.randomUUID();
        }
    }

    /** A generation in flight. */
    interface Generation {
        @NonNull String id();

        /**
         * Stops it if it is running, takes it out of the queue if it is waiting, and otherwise does
         * nothing. A generation cancelled while waiting ends with {@code generation_cancelled}.
         */
        void cancel();

        /**
         * Waits for the end. A stream's chunks have reached the sink, its {@code onDone} included,
         * when this returns, and the result is {@code null}; a non-streaming generation returns its
         * answer or its error as today's JSON. A sink's write failure is rethrown here.
         */
        @Nullable
        JSONObject await() throws JSONException, IOException;
    }

    /** Starts a generation; the work happens in {@link Generation#await} or on the runtime's lane. */
    @NonNull
    Generation generate(@NonNull GenerationRequest request, @NonNull GenerationSink sink) throws JSONException;

    /** Cancels the generation with this id; see {@link Generation#cancel}. */
    void cancel(@NonNull String generationId);

    /** {@code /v1/ai/runtime/cancel}: stops the running generation and a running benchmark. */
    @NonNull
    JSONObject cancelAll(@NonNull String stopReason) throws JSONException;

    @NonNull
    JSONObject load(@NonNull String body) throws JSONException;

    @NonNull
    JSONObject unload() throws JSONException;

    @NonNull
    JSONObject unloadChat(@NonNull String modelId) throws JSONException;

    @NonNull
    JSONObject keepWarm(@NonNull String body) throws JSONException;

    /** The runtime's {@code status} reply. */
    @NonNull
    JSONObject status() throws JSONException;

    /** The runtime's state and its resident table, read together (one round trip over IPC). */
    final class RuntimeSnapshot {
        @NonNull final TaiRuntimeState state;
        @NonNull final JSONArray residents;

        RuntimeSnapshot(@NonNull TaiRuntimeState state, @NonNull JSONArray residents) {
            this.state = state;
            this.residents = residents;
        }
    }

    /** The runtime's state and resident table; an unreachable runtime reads as nothing loaded and no residents. */
    @NonNull
    RuntimeSnapshot runtimeStatus();

    /** The runtime's state alone; an unreachable runtime reads as nothing loaded. */
    @NonNull
    TaiRuntimeState runtimeState();
}
