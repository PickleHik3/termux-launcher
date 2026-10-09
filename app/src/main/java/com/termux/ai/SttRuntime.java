package com.termux.ai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;

/**
 * One on-device speech-to-text engine as {@link MultiBackendTaiRuntime} drives it: {@link WhisperSttRuntime}
 * for the Whisper ACFT graphs, {@link ParakeetSttRuntime} for Parakeet TDT. Each holds at most one
 * graph, serialises its work and {@link #close} on its own monitor (so an eviction waits for a
 * running transcription and never interrupts it), registers itself with {@link TaiResidency} as
 * {@link TaiResidency.Kind#STT} after a load and deregisters on close. Every answer has the same
 * shape: {@code ok, text, model, language, duration, segments[], windowSeconds, biased, timings{loadMs,
 * melMs, encodeMs, decodeMs, decodeSteps, pieces}, _backend, _runtime}, or {@code ok: false} with an
 * {@code error} and {@code _statusCode}.
 */
interface SttRuntime extends AutoCloseable {
    /** Whether {@code modelId} is the graph held right now. */
    boolean isLoaded(@NonNull String modelId);

    /** Loads the graph and its sidecars without transcribing, so a load overlaps the first words spoken. */
    @NonNull
    JSONObject warm(@NonNull TaiModelSpec spec) throws JSONException;

    /**
     * Transcribes {@code audioFile} (WAV or raw PCM16 16 kHz mono, see {@link WhisperAudio}).
     * {@code language} is an ISO 639-1 hint an engine may ignore; {@code biasText} a vocabulary
     * line an engine may ignore ({@code biased} in the answer says whether it was used).
     */
    @NonNull
    JSONObject transcribe(@NonNull TaiModelSpec spec, @NonNull File audioFile,
                          @Nullable String language, @Nullable String biasText) throws JSONException;

    /** Releases the graph; waits for a running transcription. A no-op when nothing is loaded. */
    @Override
    void close();
}
