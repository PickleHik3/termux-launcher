package com.termux.ai;

import android.os.SystemClock;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import com.termux.ai.TaiManager.OpenAiStreamSink;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.json.JSONException;
import org.json.JSONObject;

/** Speech in and out: transcription, the STT warm-up, speaking, synthesis and the speech load budget. Moved out of TaiManager. */
public final class TaiSpeech {
    private final TaiManager manager;

    TaiSpeech(@NonNull TaiManager manager) {
        this.manager = manager;
    }

    /**
     * In the runtime process: how long an idle Whisper model is kept, as the app process's settings
     * last said ({@link TaiSettings#getSttIdleUnloadMinutes}); the plan's default until told.
     */
    private volatile long sttIdleLimitMs = TaiPressureWatch.STT_IDLE_MS;

    /**
     * OpenAI-style speech-to-text: {@code file} (a path — the API server writes uploads to
     * {@code cacheDir/tai-ipc}, and a path under Termux home or shared storage is accepted from
     * scripts), {@code model} (default: the installed speech model from settings), {@code language}
     * (ISO 639-1, multilingual Whisper graphs only; Parakeet detects it) and {@code prompt} (a
     * vocabulary line Whisper biases towards; Parakeet has no prompt). The app process resolves the
     * model and forwards to the runtime process, whose STT lane runs the engine's interpreter.
     * Answers {@code {text, language, duration, segments, ...}}.
     */
    @NonNull
    public JSONObject transcribe(@NonNull String body) throws JSONException {
        return transcribe(body, TaiManager.DEFAULT_TRANSCRIBE_TIMEOUT_MS);
    }

    /**
     * As {@link #transcribe(String)}, with the IPC deadline the caller wants instead of the flat
     * default — a voice-input segment gives itself a much shorter one, so a hung runtime fails
     * that one segment instead of stalling for the full timeout.
     */
    @NonNull
    public JSONObject transcribe(@NonNull String body, long timeoutMs) throws JSONException {
        JSONObject request = manager.parseBody(body);
        TaiModelSpec spec = resolveSpeechModel(request);
        if (spec == null) return speechModelError(request);
        String path = request.optString("file", "").trim();
        if (path.isEmpty()) return manager.openAiRequestError(400, "stt_audio_missing", "Provide the audio as multipart 'file' or a 'file' path.", "file");
        if (manager.shouldDelegateRuntime()) {
            File audio = new File(path);
            if (!isSttIpcFile(audio)) {
                // A path from a script: the same roots /v1/chat/completions accepts local media from.
                try {
                    path = TaiMediaAccess.resolveLocalPath(path);
                } catch (JSONException e) {
                    return manager.openAiRequestError(400, "stt_audio_unreadable", mediaAccessMessage(e), "file");
                }
            }
            if (!new File(path).isFile()) return manager.openAiRequestError(400, "stt_audio_missing", "Audio file not found: " + path, "file");
            request.put("file", path);
            return manager.runtimeRequest(TaiRuntimeIpc.OP_TRANSCRIBE, delegatedSpeechBody(request, spec), timeoutMs);
        }
        rememberSttIdleLimit(request);
        File audio = new File(path);
        if (!audio.isFile()) return manager.openAiRequestError(400, "stt_audio_missing", "Audio file not found: " + path, "file");
        try {
            TaiRuntime local = manager.localRuntime();
            if (!(local instanceof MultiBackendTaiRuntime)) {
                return manager.openAiRequestError(501, "capability_not_supported", "Speech-to-text needs the multi-backend runtime.", "model");
            }
            MultiBackendTaiRuntime router = (MultiBackendTaiRuntime) local;
            if (!router.residency().isResident(TaiResidency.Kind.STT, spec.id)) {
                JSONObject refusal = decideSttLoad(spec);
                if (refusal != null) return refusal;
            }
            String language = manager.stringOverride(request, "language");
            String languageHint = manager.stringOverride(request, WhisperLanguageDetection.REQUEST_HINT);
            return router.transcribe(spec, audio, language, languageHint, biasPromptFor(request));
        } finally {
            // The upload was written for this one request; scripts' own files are left alone.
            if (isSttIpcFile(audio)) {
                //noinspection ResultOfMethodCallIgnored
                audio.delete();
            }
        }
    }

    /** Loads the speech model ahead of its first request, so the load overlaps the first words spoken. */
    @NonNull
    public JSONObject sttWarm(@NonNull String body) throws JSONException {
        JSONObject request = manager.parseBody(body);
        TaiModelSpec spec = resolveSpeechModel(request);
        if (spec == null) return speechModelError(request);
        if (manager.shouldDelegateRuntime()) return manager.runtimeRequest(TaiRuntimeIpc.OP_STT_WARM, delegatedSpeechBody(request, spec));
        rememberSttIdleLimit(request);
        TaiRuntime local = manager.localRuntime();
        if (!(local instanceof MultiBackendTaiRuntime)) {
            return manager.openAiRequestError(501, "capability_not_supported", "Speech-to-text needs the multi-backend runtime.", "model");
        }
        MultiBackendTaiRuntime router = (MultiBackendTaiRuntime) local;
        if (request.optBoolean(TaiManager.STT_COLD_LOAD, false)) router.closeStt();
        if (!router.residency().isResident(TaiResidency.Kind.STT, spec.id)) {
            JSONObject refusal = decideSttLoad(spec);
            if (refusal != null) return refusal;
        }
        return router.sttWarm(spec);
    }

    /** The idle limit for a resident speech model, for the runtime process's memory watch. */
    public long sttIdleLimitMs() {
        return sttIdleLimitMs;
    }

    /** One sentence of speech for {@link #synthesizeSpeech}: PCM16 little-endian mono at {@code sampleRate}. */
    public interface SpeechAudioSink {
        void onAudio(@NonNull byte[] pcm16, int sampleRate) throws IOException;
    }

    /**
     * The IPC deadline for {@link #speak}: the call returns only once the text has been heard, so
     * the deadline covers synthesis and playback of the whole text, generously (a slow phone
     * synthesising slower than it speaks, and the model load on first use). Time the runtime spent
     * paused ({@link #pauseSpeaking}) does not count against it; see {@link #speechPauses}.
     */
    static long speakTimeoutMs(int chars) {
        return Math.min(60L * 60_000L, 60_000L + chars * 200L);
    }

    /**
     * The app process's note of how long the phone's speech has been paused, opened when the
     * runtime confirms a pause and closed on resume or stop, so a {@link #speak} waiting on the
     * runtime moves its deadline out for as long as the user keeps it paused. The runtime keeps its
     * own ({@link TaiTtsPlayer#awaitDone}).
     */
    private final TaiPauseClock speechPauses = new TaiPauseClock();

    /**
     * Speaks {@code input} through the phone's speaker with the speech-output model and returns
     * when it has been heard or stopped: {@code tai speak}, Read aloud and the settings sample.
     * Body: {@code input} (or {@code text}), {@code voice}, {@code speed}, {@code model}; voice and
     * speed default to the settings'. Answers {@code ok, model, voice, speed, sentences,
     * audioSeconds, firstSoundMs, played, stopped, timings}.
     */
    @NonNull
    public JSONObject speak(@NonNull String body) throws JSONException {
        JSONObject request = manager.parseBody(body);
        TaiSpeechRequest parsed = parseSpeechRequest(request, TaiSpeechRequest.MAX_SPEAK_CHARS);
        if (!parsed.isValid()) return speechRefusal(parsed);
        TaiModelSpec spec = resolveTtsModel(request, parsed);
        if (spec == null) return ttsModelError(request, parsed);
        if (manager.shouldDelegateRuntime()) {
            if (manager.runtimeClient == null) return manager.error(500, "runtime_client_unavailable", "On-device AI runtime service client is unavailable.");
            return manager.runtimeClient.request(TaiRuntimeIpc.OP_TTS_SPEAK, delegatedTtsBody(parsed, spec),
                speakTimeoutMs(parsed.text.length()), speechPauses);
        }
        MultiBackendTaiRuntime router = ttsRouter();
        if (router == null) return noTtsRuntime();
        JSONObject refusal = decideTtsLoad(router, spec);
        if (refusal != null) return refusal;
        JSONObject spoken = TaiSpeechOutput.speak(manager.appContext, router, spec, parsed);
        closeVoiceIfItsGroupIsTight(router, spec);
        return spoken;
    }

    /**
     * Decision 8's second yield: while a group read aloud belongs to is in use and free memory is under
     * the hold floor, the voice model closes after each reply ({@link TaiFeaturePlan#readAloudResidency})
     * instead of staying for its idle limit. The chat window, which yields first, is the budget's own ladder.
     */
    private void closeVoiceIfItsGroupIsTight(@NonNull MultiBackendTaiRuntime router, @NonNull TaiModelSpec spec) {
        try {
            long hold = TaiLoadBudget.floorBytes(false, manager.gateConditions(),
                TaiDeviceCapabilities.detect(manager.appContext).physicalMemoryBytes);
            long available = TaiMemInfo.read(manager.appContext).availBytes;
            boolean tight = hold > 0L && available > 0L && available < hold;
            List<TaiResidency.Entry> residents = router.residency().snapshot();
            if (TaiFeaturePlan.readAloudResidency(residents, System.currentTimeMillis(), tight)
                    != TaiFeaturePlan.Residency.PER_REPLY) return;
            TaiResidency.Entry voice = router.residency().find(TaiResidency.Kind.TTS, spec.id);
            if (voice != null && !voice.busy) router.evict(Collections.singletonList(voice));
        } catch (JSONException | RuntimeException ignored) {
            // The idle limit closes it later in any case.
        }
    }

    /** Stops whatever the phone is saying; answers {@code {ok, stopped}}. Never waits for synthesis. */
    @NonNull
    public JSONObject stopSpeaking() throws JSONException {
        if (manager.shouldDelegateRuntime()) {
            speechPauses.resume(SystemClock.elapsedRealtime());
            return manager.runtimeRequest(TaiRuntimeIpc.OP_TTS_STOP, "{}", TaiManager.RUNTIME_STATUS_TIMEOUT_MS);
        }
        return TaiSpeechOutput.stop(ttsRouter());
    }

    /**
     * Pauses the phone's speech where it is, mid-word; the speak call stays open until it is
     * resumed or stopped. Answers {@code {ok, paused}}, {@code paused} false when nothing was
     * playing (between two of Read aloud's sentences, say). Never waits for synthesis.
     */
    @NonNull
    public JSONObject pauseSpeaking() throws JSONException {
        if (manager.shouldDelegateRuntime()) {
            JSONObject result = manager.runtimeRequest(TaiRuntimeIpc.OP_TTS_PAUSE, "{}", TaiManager.RUNTIME_STATUS_TIMEOUT_MS);
            if (result.optBoolean("paused", false)) speechPauses.pause(SystemClock.elapsedRealtime());
            return result;
        }
        return TaiSpeechOutput.pause();
    }

    /** Carries paused speech on from where it stopped; answers {@code {ok, resumed}}. */
    @NonNull
    public JSONObject resumeSpeaking() throws JSONException {
        if (manager.shouldDelegateRuntime()) {
            speechPauses.resume(SystemClock.elapsedRealtime());
            return manager.runtimeRequest(TaiRuntimeIpc.OP_TTS_RESUME, "{}", TaiManager.RUNTIME_STATUS_TIMEOUT_MS);
        }
        return TaiSpeechOutput.resume();
    }

    /** Answers {@code {ok, speaking, sounding, paused}} for the phone's speech; see {@link TaiSpeechOutput#state}. */
    @NonNull
    public JSONObject speechState() throws JSONException {
        if (manager.shouldDelegateRuntime()) return manager.runtimeRequest(TaiRuntimeIpc.OP_TTS_STATE, "{}", TaiManager.RUNTIME_STATUS_TIMEOUT_MS);
        return TaiSpeechOutput.state();
    }

    /** Loads the speech-output model ahead of its first sentence. */
    @NonNull
    public JSONObject ttsWarm(@NonNull String body) throws JSONException {
        JSONObject request = manager.parseBody(body);
        TaiSpeechRequest parsed = TaiSpeechRequest.parse(new JSONObject(request.toString()).put("input", "."),
            manager.settings.getTtsVoice(), manager.settings.getTtsSpeed(), TaiSpeechRequest.MAX_SPEAK_CHARS);
        TaiModelSpec spec = resolveTtsModel(request, parsed);
        if (spec == null) return ttsModelError(request, parsed);
        if (manager.shouldDelegateRuntime()) return manager.runtimeRequest(TaiRuntimeIpc.OP_TTS_WARM, delegatedTtsBody(parsed, spec));
        MultiBackendTaiRuntime router = ttsRouter();
        if (router == null) return noTtsRuntime();
        JSONObject refusal = decideTtsLoad(router, spec);
        if (refusal != null) return refusal;
        return router.ttsWarm(spec);
    }

    /**
     * Checks a speech request without running it: the refusal (OpenAI error shape with
     * {@code _statusCode}) or {@code null} when it would run. The API calls this before it commits
     * to a 200 and a streamed body.
     */
    @Nullable
    public JSONObject checkSpeechRequest(@NonNull String body, int maxChars) throws JSONException {
        JSONObject request = manager.parseBody(body);
        TaiSpeechRequest parsed = parseSpeechRequest(request, maxChars);
        if (!parsed.isValid()) return speechRefusal(parsed);
        return resolveTtsModel(request, parsed) == null ? ttsModelError(request, parsed) : null;
    }

    /** {@code wav} or {@code pcm} for a request that {@link #checkSpeechRequest} accepted. */
    @NonNull
    public static String speechResponseFormat(@NonNull String body) {
        try {
            return TaiSpeechRequest.parse(new JSONObject(body.trim().isEmpty() ? "{}" : body), TaiTtsVoices.DEFAULT_VOICE,
                TaiTtsVoices.DEFAULT_SPEED, Integer.MAX_VALUE).format;
        } catch (JSONException e) {
            return TaiSpeechRequest.FORMAT_WAV;
        }
    }

    /** The 44-byte RIFF header for {@code dataBytes} of mono PCM16, for the API's {@code wav} answer. */
    @NonNull
    public static byte[] wavHeader(int dataBytes, int sampleRate) {
        return TaiWav.header(dataBytes, sampleRate);
    }

    /** The limit {@code /v1/audio/speech} applies, OpenAI's; {@code tai speak} gets {@link #speakCharacterLimit}. */
    public static int apiSpeechCharacterLimit() {
        return TaiSpeechRequest.MAX_API_CHARS;
    }

    public static int speakCharacterLimit() {
        return TaiSpeechRequest.MAX_SPEAK_CHARS;
    }

    /**
     * Synthesises without playing, handing each sentence to {@code sink} as it is ready: the
     * OpenAI {@code /v1/audio/speech} route and {@code tai speak --out}. The runtime process writes
     * each sentence as a PCM16 file under {@code cacheDir/tai-ipc} and says so; this side reads
     * it, deletes it and passes the bytes on, so audio never crosses the Messenger inline. Returns
     * the runtime's summary, or a refusal or failure in the OpenAI error shape. When {@code sink}
     * throws (the HTTP client went away) synthesis is stopped and the exception rethrown.
     */
    @NonNull
    public JSONObject synthesizeSpeech(@NonNull String body, int maxChars, @NonNull SpeechAudioSink sink)
            throws JSONException, IOException {
        JSONObject request = manager.parseBody(body);
        TaiSpeechRequest parsed = parseSpeechRequest(request, maxChars);
        if (!parsed.isValid()) return speechRefusal(parsed);
        TaiModelSpec spec = resolveTtsModel(request, parsed);
        if (spec == null) return ttsModelError(request, parsed);
        if (!manager.shouldDelegateRuntime()) {
            // In-process (a runtime override): synthesise straight into the sink.
            MultiBackendTaiRuntime router = ttsRouter();
            if (router == null) return noTtsRuntime();
            final IOException[] failure = new IOException[1];
            JSONObject result = router.synthesizeSpeech(spec, parsed.text, parsed.voice, parsed.speed, (samples, count, index) -> {
                byte[] pcm = new byte[count * 2];
                TaiWav.floatToPcm16(samples, 0, count, pcm, 0);
                try {
                    sink.onAudio(pcm, router.ttsSampleRate());
                    return true;
                } catch (IOException e) {
                    failure[0] = e;
                    return false;
                }
            }, () -> failure[0] != null);
            if (failure[0] != null) throw failure[0];
            return result;
        }
        if (manager.runtimeClient == null) return manager.error(500, "runtime_client_unavailable", "On-device AI runtime service client is unavailable.");
        final JSONObject[] summary = new JSONObject[1];
        try {
            manager.runtimeClient.stream(TaiRuntimeIpc.OP_TTS_SYNTHESIZE, delegatedTtsBody(parsed, spec), new OpenAiStreamSink() {
                @Override
                public void onEvent(@NonNull JSONObject event) throws IOException {
                    String file = event.optString("file", "");
                    if (!file.isEmpty()) {
                        File pcm = new File(file);
                        try {
                            sink.onAudio(readAllBytes(pcm), event.optInt("sampleRate", KittenTtsRuntime.SAMPLE_RATE));
                        } finally {
                            //noinspection ResultOfMethodCallIgnored
                            pcm.delete();
                        }
                    } else if (event.has("result")) {
                        summary[0] = event.optJSONObject("result");
                    } else if (event.has("error")) {
                        summary[0] = event;
                    }
                }

                @Override
                public void onDone() {
                }
            });
        } catch (IOException | RuntimeException e) {
            // The client went away mid-stream: stop synthesising audio nobody will hear.
            try {
                stopSpeaking();
            } catch (JSONException ignored) {
            }
            throw e;
        }
        if (summary[0] == null) return manager.error(500, "tts_stream_incomplete", "The speech runtime ended without a result.");
        return summary[0];
    }

    /** The runtime-process half of {@link #synthesizeSpeech}: files out, one event per sentence, then the summary. */
    void synthesizeSpeechToEvents(@NonNull String body, @NonNull OpenAiStreamSink sink) throws JSONException, IOException {
        JSONObject request = manager.parseBody(body);
        TaiSpeechRequest parsed = parseSpeechRequest(request, TaiSpeechRequest.MAX_SPEAK_CHARS);
        TaiModelSpec spec = parsed.isValid() ? resolveTtsModel(request, parsed) : null;
        MultiBackendTaiRuntime router = ttsRouter();
        JSONObject result;
        if (!parsed.isValid()) {
            result = speechRefusal(parsed);
        } else if (spec == null) {
            result = ttsModelError(request, parsed);
        } else if (router == null) {
            result = noTtsRuntime();
        } else {
            JSONObject refusal = decideTtsLoad(router, spec);
            if (refusal != null) {
                result = refusal;
            } else {
                final IOException[] failure = new IOException[1];
                result = TaiSpeechOutput.synthesizeToFiles(manager.appContext, router, spec, parsed, (file, samples, sampleRate, index) -> {
                    try {
                        JSONObject event = new JSONObject();
                        event.put("file", file.getAbsolutePath());
                        event.put("samples", samples);
                        event.put("sampleRate", sampleRate);
                        event.put("index", index);
                        sink.onEvent(event);
                        return true;
                    } catch (IOException | JSONException e) {
                        failure[0] = e instanceof IOException ? (IOException) e : new IOException(e.getMessage());
                        //noinspection ResultOfMethodCallIgnored
                        file.delete();
                        return false;
                    }
                });
                if (failure[0] != null) throw failure[0];
            }
        }
        if (result.has("error") && !result.optBoolean("ok", true)) {
            sink.onEvent(result);
        } else {
            sink.onEvent(new JSONObject().put("result", result));
        }
        sink.onDone();
    }

    @NonNull
    private TaiSpeechRequest parseSpeechRequest(@NonNull JSONObject request, int maxChars) {
        return TaiSpeechRequest.parse(request, manager.settings.getTtsVoice(), manager.settings.getTtsSpeed(), maxChars);
    }

    @NonNull
    private JSONObject speechRefusal(@NonNull TaiSpeechRequest parsed) throws JSONException {
        return manager.openAiRequestError(parsed.statusCode, parsed.errorCode == null ? "bad_request" : parsed.errorCode,
            parsed.errorMessage == null ? "Bad speech request." : parsed.errorMessage, parsed.errorParam);
    }

    @NonNull
    private JSONObject noTtsRuntime() throws JSONException {
        return manager.openAiRequestError(501, "capability_not_supported", "Speech output needs the multi-backend runtime.", "model");
    }

    /**
     * The speech-output model a request names, or the installed one when it names none, one of
     * OpenAI's speech model names, or an id this phone has never heard of (OpenAI-shaped clients
     * send a model field they may not know how to leave out); {@code null} when that does not
     * resolve to a {@code text_to_speech} model. In the runtime process the app process's
     * resolved spec rides along in the body.
     */
    @Nullable
    private TaiModelSpec resolveTtsModel(@NonNull JSONObject request, @NonNull TaiSpeechRequest parsed) {
        String modelId = parsed.model;
        if (manager.runtimeProcess) {
            TaiModelSpec supplied = manager.resolveModel(request, modelId);
            return supplied != null && TaiTtsModels.isTtsModel(supplied) ? supplied : null;
        }
        if (TaiSpeechRequest.isDefaultModelAlias(modelId)) return TaiTtsModels.resolveActive(manager.appContext, manager.modelStore);
        TaiModelSpec spec = manager.resolveModel(request, modelId);
        if (spec == null && TaiModelCatalog.get(modelId) == null) return TaiTtsModels.resolveActive(manager.appContext, manager.modelStore);
        return spec != null && TaiTtsModels.isTtsModel(spec) ? spec : null;
    }

    @NonNull
    private JSONObject ttsModelError(@NonNull JSONObject request, @NonNull TaiSpeechRequest parsed) throws JSONException {
        String modelId = parsed.model;
        TaiModelSpec spec = TaiSpeechRequest.isDefaultModelAlias(modelId) ? null : manager.resolveModel(request, modelId);
        if (spec != null && !TaiTtsModels.isTtsModel(spec)) {
            return manager.openAiRequestError(400, "not_a_tts_model", "Model '" + modelId + "' is not a voice model.", "model");
        }
        if (!TaiSpeechRequest.isDefaultModelAlias(modelId) && TaiModelCatalog.get(modelId) != null) {
            return manager.openAiRequestError(400, "tts_model_not_installed", "Model '" + modelId + "' is not installed.", "model");
        }
        return manager.openAiRequestError(400, "tts_model_not_installed",
            "No voice model is installed. Get one in On-device AI settings > Model centre > Speech > Voice output.", "model");
    }

    /** The runtime-process body: the parsed request with the resolved spec riding along. */
    @NonNull
    private String delegatedTtsBody(@NonNull TaiSpeechRequest parsed, @NonNull TaiModelSpec spec) throws JSONException {
        JSONObject body = new JSONObject();
        body.put("input", parsed.text);
        body.put("voice", parsed.voice);
        body.put("speed", (double) parsed.speed);
        body.put("response_format", parsed.format);
        body.put("model", spec.id);
        body.put(TaiManager.INTERNAL_MODEL_SPEC, spec.toJson());
        return body.toString();
    }

    @Nullable
    MultiBackendTaiRuntime ttsRouter() {
        TaiRuntime local = manager.runtime;
        return local instanceof MultiBackendTaiRuntime ? (MultiBackendTaiRuntime) local : null;
    }

    /**
     * The budget for a speech-output model that is not resident yet: the measured or estimated
     * cost against what is free, with idle embeddings evicted if that is what it takes. Speech
     * output never evicts speech-to-text or chat: dictating and chatting matter more than a voice
     * that reloads in a second. {@code null} means go ahead.
     */
    @Nullable
    private JSONObject decideTtsLoad(@NonNull MultiBackendTaiRuntime router, @NonNull TaiModelSpec spec) throws JSONException {
        if (router.residency().isResident(TaiResidency.Kind.TTS, spec.id)) return null;
        TaiDeviceCapabilities device = TaiDeviceCapabilities.detect(manager.appContext);
        List<TaiResidency.Entry> residents = router.residency().snapshot();
        long available = TaiResidency.creditedAvailable(manager.availableMemory(device), residents, TaiResidency.Kind.TTS, spec.backend);
        long typical = TaiRuntimeHistory.measuredLoadBytes(manager.appContext, spec, device, TaiModelSpec.BACKEND_LITERT_LM, "cpu", 0);
        TaiLoadBudget.Estimate estimate = typical > 0L ? TaiLoadBudget.Estimate.measured(typical)
            : TaiLoadBudget.Estimate.ratio(TaiResidency.ttsEstimateBytes(spec), 0L);
        List<TaiResidency.Entry> candidates = new ArrayList<>();
        for (TaiResidency.Entry entry : TaiResidency.evictionCandidates(residents, TaiResidency.Kind.TTS, spec.backend,
                TaiFunction.READ_ALOUD, System.currentTimeMillis())) {
            if (entry.kind == TaiResidency.Kind.EMBEDDING) candidates.add(entry);
        }
        TaiLoadBudget.Plan plan = TaiLoadBudget.planFixed(estimate, "cpu", device.physicalMemoryBytes, available,
            candidates, manager.gateConditions());
        if (!plan.fits) return TaiManager.openAiError(TaiManager.insufficientMemory(spec.displayName, plan));
        manager.evict(plan);
        return null;
    }

    @NonNull
    static byte[] readAllBytes(@NonNull File file) throws IOException {
        long length = file.length();
        if (length > Integer.MAX_VALUE) throw new IOException("Audio file too large");
        byte[] out = new byte[(int) length];
        try (java.io.FileInputStream in = new java.io.FileInputStream(file)) {
            int done = 0;
            while (done < out.length) {
                int read = in.read(out, done, out.length - done);
                if (read < 0) break;
                done += read;
            }
            return done == out.length ? out : java.util.Arrays.copyOf(out, done);
        }
    }

    /**
     * The vocabulary line behind {@code <|startofprev|>}: the request's OpenAI {@code prompt},
     * trimmed, or none (plain dictation). There is no built-in vocabulary any more: the shell
     * bias hurt prose (dropped words, noise read as "ok"), and voice input is dictation only.
     */
    @Nullable
    static String biasPromptFor(@NonNull JSONObject request) {
        String prompt = request.optString("prompt", "").trim();
        return prompt.isEmpty() ? null : prompt;
    }

    /** The speech model a request names, or the one voice input uses; {@code null} when neither resolves. */
    @Nullable
    private TaiModelSpec resolveSpeechModel(@NonNull JSONObject request) {
        String modelId = speechModelIdFor(request);
        if (modelId.isEmpty()) return null;
        TaiModelSpec spec = manager.resolveModel(request, modelId);
        return spec != null && spec.capabilities.contains(TaiModelSpec.CAPABILITY_SPEECH_TO_TEXT) ? spec : null;
    }

    /** The request's model, else the installed speech model the settings choose — a stored id whose
     *  files are gone counts as unset and another installed speech model stands in for it. */
    @NonNull
    private String speechModelIdFor(@NonNull JSONObject request) {
        String requested = manager.requestedModelId(request, "");
        return requested.isEmpty() ? TaiSpeechModels.activeModelId(manager.appContext) : requested;
    }

    @NonNull
    private JSONObject speechModelError(@NonNull JSONObject request) throws JSONException {
        String modelId = speechModelIdFor(request);
        if (modelId.isEmpty()) {
            return manager.openAiRequestError(400, "stt_model_not_configured",
                "No speech-to-text model is installed. Get one in On-device AI settings > Model centre > Speech.", "model");
        }
        TaiModelSpec spec = manager.resolveModel(request, modelId);
        if (spec == null) return manager.openAiRequestError(404, "model_not_found", "Unknown model: " + modelId, "model");
        return manager.openAiRequestError(400, "not_a_speech_model", "Model '" + modelId + "' is not a speech-to-text model.", "model");
    }

    /** The runtime-process body for a speech request: the resolved spec and the STT idle setting ride along. */
    @NonNull
    private String delegatedSpeechBody(@NonNull JSONObject request, @NonNull TaiModelSpec spec) throws JSONException {
        request.remove(TaiManager.INTERNAL_RUNTIME_OPTIONS);
        request.put("model", spec.id);
        request.put(TaiManager.INTERNAL_MODEL_SPEC, spec.toJson());
        request.put(TaiManager.INTERNAL_STT_IDLE_MINUTES, manager.settings.getSttIdleUnloadMinutes());
        request.put(TaiManager.INTERNAL_STT_ACCELERATOR, sttAcceleratorFor(request, manager.settings.getSttAccelerator(spec.id)));
        return request.toString();
    }

    /**
     * The processor a speech request runs on: its own {@code accelerator} ({@code auto}, {@code cpu}
     * or {@code gpu}; the voice benchmark names one per entry), else the setting.
     */
    @NonNull
    static String sttAcceleratorFor(@NonNull JSONObject request, @NonNull String setting) {
        String asked = request.optString("accelerator", "").trim().toLowerCase(java.util.Locale.ROOT);
        if (SttAccelerator.AUTO.id.equals(asked) || SttAccelerator.CPU.id.equals(asked) || SttAccelerator.GPU.id.equals(asked)) {
            return asked;
        }
        return setting;
    }

    /** In the runtime process: the STT idle limit and accelerator the app process sent with this request. */
    private void rememberSttIdleLimit(@NonNull JSONObject request) {
        if (!manager.runtimeProcess) return;
        // The speech runtimes read it at their next load; a different ask reloads the graph.
        if (request.has(TaiManager.INTERNAL_STT_ACCELERATOR)) {
            SttAccelerator.setRequested(SttAccelerator.parse(request.optString(TaiManager.INTERNAL_STT_ACCELERATOR, null)));
        }
        if (!request.has(TaiManager.INTERNAL_STT_IDLE_MINUTES)) return;
        sttIdleLimitMs = java.util.concurrent.TimeUnit.MINUTES.toMillis(Math.max(0, request.optInt(TaiManager.INTERNAL_STT_IDLE_MINUTES, 0)));
    }

    /** Whether {@code file} is one of the uploads the API server wrote under {@code cacheDir/tai-ipc}. */
    private boolean isSttIpcFile(@NonNull File file) {
        try {
            File dir = new File(manager.appContext.getCacheDir(), TaiManager.STT_IPC_DIR).getCanonicalFile();
            return file.getCanonicalFile().getParentFile() != null && dir.equals(file.getCanonicalFile().getParentFile());
        } catch (IOException e) {
            return false;
        }
    }

    @NonNull
    private static String mediaAccessMessage(@NonNull JSONException e) {
        String message = e.getMessage() == null ? "" : e.getMessage();
        int colon = message.indexOf(':');
        return colon > 0 ? message.substring(colon + 1) : message;
    }

    /**
     * The budget for a speech model that is not resident yet, the embedding decision's twin: the
     * measured or file-size estimate (× 1.9, see {@link TaiResidency#STT_FACTOR_TENTHS}) against
     * what is free plus the STT resident it replaces, idle embeddings — and, for STT alone, idle
     * chat — evicted if that is what it takes. {@code null} means go ahead.
     */
    @Nullable
    private JSONObject decideSttLoad(@NonNull TaiModelSpec spec) throws JSONException {
        TaiDeviceCapabilities device = TaiDeviceCapabilities.detect(manager.appContext);
        List<TaiResidency.Entry> residents = manager.residency().snapshot();
        long available = TaiResidency.creditedAvailable(manager.availableMemory(device), residents,
            TaiResidency.Kind.STT, spec.backend);
        long typical = TaiRuntimeHistory.measuredLoadBytes(manager.appContext, spec, device, TaiModelSpec.BACKEND_LITERT_LM, "cpu", 0);
        TaiLoadBudget.Estimate estimate = typical > 0L ? TaiLoadBudget.Estimate.measured(typical)
            : TaiLoadBudget.Estimate.ratio(TaiResidency.sttEstimateBytes(spec), 0L);
        TaiLoadBudget.Plan plan = TaiLoadBudget.planFixed(estimate, "cpu", device.physicalMemoryBytes, available,
            TaiResidency.evictionCandidates(residents, TaiResidency.Kind.STT, spec.backend,
                TaiFunction.VOICE_TYPING, System.currentTimeMillis()), manager.gateConditions());
        if (!plan.fits) return TaiManager.openAiError(TaiManager.insufficientMemory(spec.displayName, plan));
        manager.evict(plan);
        return null;
    }
}
