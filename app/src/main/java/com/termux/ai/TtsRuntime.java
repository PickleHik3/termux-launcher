package com.termux.ai;

import androidx.annotation.NonNull;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * One on-device text-to-speech engine as {@link MultiBackendTaiRuntime} drives it; today only
 * {@link KittenTtsRuntime}. Like {@link SttRuntime}, an engine holds at most one model, serialises
 * synthesis, warm and {@link #close} on its own monitor (so an eviction waits for a sentence in
 * progress and never interrupts it), registers itself with {@link TaiResidency} as
 * {@link TaiResidency.Kind#TTS} after a load and deregisters on close.
 *
 * <p>Synthesis streams: {@link #synthesize} hands each sentence's audio to the {@link Sink} as soon
 * as it exists, so a player can start on the first sentence while the rest are computed. Answers
 * are {@code ok, model, voice, speed, sampleRate, sentences, samples, audioSeconds, timings{loadMs,
 * firstAudioMs, g2pMs, synthMs}, _runtime}, or {@code ok: false} with an {@code error} and
 * {@code _statusCode}.
 */
interface TtsRuntime extends AutoCloseable {
    /** Receives one sentence of mono float PCM at {@link #sampleRate()}; return false to stop early. */
    interface Sink {
        boolean onAudio(@NonNull float[] samples, int count, int sentenceIndex);
    }

    /** Polled between sentences and between graphs; true stops synthesis at the next check. */
    interface Cancellation {
        boolean isCancelled();
    }

    int sampleRate();

    /** Whether {@code modelId} is the model held right now. */
    boolean isLoaded(@NonNull String modelId);

    /** Loads the graphs, voices and pronunciation data without speaking, so the first sentence starts sooner. */
    @NonNull
    JSONObject warm(@NonNull TaiModelSpec spec) throws JSONException;

    /**
     * Speaks {@code text} with {@code voice} (one of {@link TaiTtsVoices#VOICES}) at {@code speed}
     * (already clamped), sentence by sentence into {@code sink}.
     */
    @NonNull
    JSONObject synthesize(@NonNull TaiModelSpec spec, @NonNull String text, @NonNull String voice, float speed,
                          @NonNull Sink sink, @NonNull Cancellation cancellation) throws JSONException;

    /** Interrupts a graph that is running now, where the interpreter allows it; synthesis then ends with a cancellation. */
    void interrupt();

    /** Releases the model; waits for a sentence in progress. A no-op when nothing is loaded. */
    @Override
    void close();
}
