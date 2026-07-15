package com.termux.ai;

import android.content.Context;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.ai.edge.litertlm.Backend;
import com.google.ai.edge.litertlm.BenchmarkInfo;
import com.google.ai.edge.litertlm.Capabilities;
import com.google.ai.edge.litertlm.Content;
import com.google.ai.edge.litertlm.Contents;
import com.google.ai.edge.litertlm.Conversation;
import com.google.ai.edge.litertlm.ConversationConfig;
import com.google.ai.edge.litertlm.Engine;
import com.google.ai.edge.litertlm.EngineConfig;
import com.google.ai.edge.litertlm.ExperimentalFlags;
import com.google.ai.edge.litertlm.Message;
import com.google.ai.edge.litertlm.MessageCallback;
import com.google.ai.edge.litertlm.SamplerConfig;
import com.google.ai.edge.litertlm.ToolCall;

import org.json.JSONException;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public final class LiteRtTaiRuntime implements TaiRuntime {
    private static final Object EXPERIMENTAL_FLAGS_LOCK = new Object();
    private static final int DEFAULT_TOP_K = 64;
    private static final double DEFAULT_TOP_P = 0.95d;
    private static final double DEFAULT_TEMPERATURE = 1.0d;
    private static final int DEFAULT_KEEP_WARM_MINUTES = 30;
    private static final String AUTO_GPU_REASON =
        "Auto selected GPU because this model/device has a successful GPU load history.";
    private static final String AUTO_MODEL_CPU_REASON =
        "Auto selected CPU because the model's Edge Gallery compatibility profile requires CPU.";

    private final Context appContext;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "tai-runtime-idle");
        thread.setDaemon(true);
        return thread;
    });

    private Engine engine;
    private Conversation conversation;
    private String conversationKey = "";
    private String loadedModelId;
    private String loadedModelPath;
    private String backendName = "none";
    private String backendFallbackReason = "";
    private String runtimeState = "unloaded";
    private String statusMessage = "LiteRT-LM runtime is unloaded.";
    private TaiRuntimeOptions loadedOptions;
    private TaiModelProfile loadedProfile;
    private TaiDeviceCapabilities loadedDeviceCapabilities;
    private ScheduledFuture<?> idleUnloadFuture;
    private boolean generating;
    private boolean unloadAfterGeneration;
    private String activeGenerationId;
    private long activeGenerationStartedAtMs;
    private long loadedAtMs;
    private long lastUsedAtMs;
    private long keepWarmUntilMs;
    private long idleUnloadAtMs;
    private boolean loading;
    private boolean loadCancellationRequested;
    private String loadingModelId;

    public LiteRtTaiRuntime(@NonNull Context context) {
        appContext = context.getApplicationContext();
    }

    @NonNull
    @Override
    public synchronized TaiRuntimeState getState() {
        maybeRefreshStateLocked();
        return new TaiRuntimeState(
            engine != null,
            loadedModelId,
            "litert-lm",
            runtimeState,
            statusMessage,
            backendName,
            backendFallbackReason,
            loadedModelPath,
            generating,
            activeGenerationId,
            activeGenerationStartedAtMs,
            keepWarmUntilMs,
            idleUnloadAtMs,
            loadedAtMs,
            lastUsedAtMs
        );
    }

    @Override
    public synchronized boolean isModelLoaded(@NonNull String modelId) {
        return engine != null && loadedModelId != null && loadedModelId.equals(modelId);
    }

    @NonNull
    @Override
    public JSONObject load(@NonNull TaiModelSpec modelSpec, @NonNull TaiRuntimeOptions options) throws JSONException {
        return loadInternal(modelSpec, options, 0);
    }

    @NonNull
    @Override
    public JSONObject keepWarm(@NonNull TaiModelSpec modelSpec, @NonNull TaiRuntimeOptions options, int minutes) throws JSONException {
        int keepWarmMinutes = minutes > 0 ? minutes : DEFAULT_KEEP_WARM_MINUTES;
        synchronized (this) {
            if (!loading && engine != null && modelSpec.id.equals(loadedModelId)) {
                keepWarmUntilMs = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(keepWarmMinutes);
                statusMessage = "Model is warm.";
                maybeRefreshStateLocked();
                scheduleIdleUnloadLocked();

                JSONObject data = stateEnvelopeLocked(true);
                data.put("keepWarm", true);
                data.put("keepWarmMinutes", keepWarmMinutes);
                data.put("keepWarmUntilMs", keepWarmUntilMs);
                return data;
            }
        }

        return loadInternal(modelSpec, options, keepWarmMinutes);
    }

    @NonNull
    @Override
    public synchronized JSONObject unload() throws JSONException {
        if (generating) {
            unloadAfterGeneration = true;
            runtimeState = "stopping";
            statusMessage = "Cancelling generation; LiteRT-LM will unload when generation stops.";
            try {
                if (conversation != null) conversation.cancelProcess();
            } catch (Exception e) {
                return error(500, "cancel_failed", "Failed to cancel active LiteRT-LM generation: " + e.getMessage());
            }
            JSONObject data = stateEnvelopeLocked(true);
            data.put("cancelled", true);
            data.put("unloadPending", true);
            data.put("message", statusMessage);
            return data;
        }
        if (loading) {
            loadCancellationRequested = true;
            runtimeState = "stopping";
            statusMessage = "Cancelling model load. Native initialization will be discarded when it returns.";
            JSONObject data = stateEnvelopeLocked(true);
            data.put("unloadedModelId", JSONObject.NULL);
            data.put("loadingModelId", loadingModelId == null ? JSONObject.NULL : loadingModelId);
            data.put("loadCancellationRequested", true);
            data.put("message", statusMessage);
            return data;
        }
        String previous = loadedModelId;
        closeEngineLocked("LiteRT-LM runtime is unloaded.", "unloaded");
        JSONObject data = new JSONObject();
        data.put("ok", true);
        data.put("unloadedModelId", previous == null ? JSONObject.NULL : previous);
        data.put("runtime", "litert-lm");
        data.put("state", getState().toJson());
        return data;
    }

    @NonNull
    @Override
    public synchronized JSONObject cancel() throws JSONException {
        if (loading) {
            loadCancellationRequested = true;
            runtimeState = "stopping";
            statusMessage = "Cancelling model load. Native initialization will be discarded when it returns.";
            JSONObject data = stateEnvelopeLocked(true);
            data.put("cancelled", true);
            data.put("loadCancellationRequested", true);
            data.put("message", "Model load cancellation requested.");
            return data;
        }
        if (!generating || conversation == null) {
            JSONObject data = stateEnvelopeLocked(true);
            data.put("cancelled", false);
            data.put("message", "No active generation.");
            return data;
        }
        runtimeState = "stopping";
        statusMessage = "Cancelling active generation.";
        try {
            conversation.cancelProcess();
        } catch (Exception e) {
            return error(500, "cancel_failed", "Failed to cancel active LiteRT-LM generation: " + e.getMessage());
        } finally {
            // cancelProcess() does not roll back the conversation's KV state. Never reuse it.
            closeConversationLocked();
        }
        JSONObject data = stateEnvelopeLocked(true);
        data.put("cancelled", true);
        data.put("message", "Cancel requested.");
        return data;
    }

    @NonNull
    @Override
    public JSONObject chat(
        @NonNull String modelId,
        @NonNull String systemPrompt,
        @NonNull String userPrompt,
        @NonNull TaiRuntimeOptions options
    ) throws JSONException {
        return generate(modelId, "chat", TaiChatRequest.simple(systemPrompt, userPrompt), options, null);
    }

    @NonNull
    @Override
    public JSONObject chat(
        @NonNull String modelId,
        @NonNull String systemPrompt,
        @NonNull String userPrompt,
        @NonNull TaiRuntimeOptions options,
        @NonNull TaiGenerationCallback callback
    ) throws JSONException {
        return generate(modelId, "chat", TaiChatRequest.simple(systemPrompt, userPrompt), options, callback);
    }

    @NonNull
    @Override
    public JSONObject chat(
        @NonNull String modelId,
        @NonNull TaiChatRequest request,
        @NonNull TaiRuntimeOptions options
    ) throws JSONException {
        return generate(modelId, "chat", request, options, null);
    }

    @NonNull
    @Override
    public JSONObject chat(
        @NonNull String modelId,
        @NonNull TaiChatRequest request,
        @NonNull TaiRuntimeOptions options,
        @NonNull TaiGenerationCallback callback
    ) throws JSONException {
        return generate(modelId, "chat", request, options, callback);
    }

    @NonNull
    @Override
    public JSONObject complete(@NonNull String modelId, @NonNull String prompt, @NonNull TaiRuntimeOptions options) throws JSONException {
        return generate(modelId, "completion", TaiChatRequest.oneShot("", prompt), options, null);
    }

    @NonNull
    @Override
    public JSONObject complete(
        @NonNull String modelId,
        @NonNull String prompt,
        @NonNull TaiRuntimeOptions options,
        @NonNull TaiGenerationCallback callback
    ) throws JSONException {
        return generate(modelId, "completion", TaiChatRequest.oneShot("", prompt), options, callback);
    }

    @NonNull
    private JSONObject generate(
        @NonNull String modelId,
        @NonNull String mode,
        @NonNull TaiChatRequest request,
        @NonNull TaiRuntimeOptions options,
        @Nullable TaiGenerationCallback callback
    ) throws JSONException {
        Conversation activeConversation;
        String generationId;
        long startedAt;
        synchronized (this) {
            JSONObject availabilityError = ensureLoadedForGenerationLocked(modelId);
            if (availabilityError != null) return availabilityError;
            if (generating) {
                return error(409, "generation_active", "A LiteRT-LM generation is already running. Cancel it or wait for it to finish.");
            }
            activeConversation = ensureConversationLocked(mode, request, options);
            generationId = beginGenerationLocked();
            startedAt = activeGenerationStartedAtMs;
        }

        CountDownLatch done = new CountDownLatch(1);
        StringBuilder responseBuilder = new StringBuilder();
        JSONArray toolCalls = new JSONArray();
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        AtomicBoolean lengthLimited = new AtomicBoolean(false);
        AtomicBoolean callbackCancelled = new AtomicBoolean(false);
        AtomicBoolean callbackFinished = new AtomicBoolean(false);
        AtomicInteger lastPrefillTokens = new AtomicInteger(-1);
        AtomicInteger lastDecodeTokens = new AtomicInteger(-1);
        AtomicInteger conversationTokenCount = new AtomicInteger(-1);
        // Do not call activeConversation.getTokenCount() from inside onMessage: the native
        // session lock is held by the decode loop during callbacks and the call deadlocks the
        // whole generation (observed on-device). Each onMessage delivers at least one decoded
        // token, so the callback count is a safe lower-bound proxy for output tokens.
        AtomicInteger decodedCallbacks = new AtomicInteger(0);
        int maxOutputTokens = options.maxTokens != null
            ? options.maxTokens
            : loadedProfile.defaultMaxTokens;
        boolean unloadRequested;
        try {
            activeConversation.sendMessageAsync(request.message, new MessageCallback() {
                    @Override
                    public void onMessage(@NonNull Message message) {
                        String text = textFromMessage(message);
                        synchronized (responseBuilder) {
                            responseBuilder.append(text);
                        }
                        if (callback != null) callback.onToken(text);
                        JSONArray messageToolCalls = new JSONArray();
                        appendToolCalls(messageToolCalls, message.getToolCalls(), generationId);
                        if (messageToolCalls.length() > 0) {
                            synchronized (toolCalls) {
                                for (int i = 0; i < messageToolCalls.length(); i++) {
                                    toolCalls.put(messageToolCalls.opt(i));
                                }
                            }
                            if (callback != null) callback.onToolCalls(messageToolCalls);
                        }

                        int generatedTokens = decodedCallbacks.incrementAndGet();
                        boolean stopRequested = callback != null && callback.shouldCancelGeneration();
                        boolean newStopRequest = stopRequested && callbackCancelled.compareAndSet(false, true);
                        if ((generatedTokens >= maxOutputTokens && lengthLimited.compareAndSet(false, true))
                            || newStopRequest) {
                            // Cancel from the scheduler thread, never from the decode callback,
                            // so the native session lock is free when cancelProcess() runs.
                            scheduler.execute(() -> {
                                try {
                                    activeConversation.cancelProcess();
                                } catch (Throwable throwable) {
                                    errorRef.compareAndSet(null, throwable);
                                }
                            });
                        }
                    }

                    @Override
                    public void onDone() {
                        if (!callbackFinished.compareAndSet(false, true)) return;
                        String fullText;
                        synchronized (responseBuilder) {
                            fullText = responseBuilder.toString();
                        }
                        if (callback != null) callback.onComplete(fullText);
                        done.countDown();
                    }

                    @Override
                    public void onError(@NonNull Throwable throwable) {
                        if (!callbackFinished.compareAndSet(false, true)) return;
                        if ((lengthLimited.get() || callbackCancelled.get()) && isCancellation(throwable)) {
                            String fullText;
                            synchronized (responseBuilder) {
                                fullText = responseBuilder.toString();
                            }
                            if (callback != null) callback.onComplete(fullText);
                            done.countDown();
                            return;
                        }
                        errorRef.set(throwable);
                        if (callback != null) callback.onError(throwable);
                        done.countDown();
                    }
                }, Collections.emptyMap());
            done.await();
            try {
                BenchmarkInfo benchmark = activeConversation.getBenchmarkInfo();
                if (benchmark != null) {
                    lastPrefillTokens.set(benchmark.getLastPrefillTokenCount());
                    lastDecodeTokens.set(benchmark.getLastDecodeTokenCount());
                }
            } catch (Throwable ignored) {
            }
            try {
                conversationTokenCount.set(activeConversation.getTokenCount());
            } catch (Throwable ignored) {
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            errorRef.set(e);
        } catch (Throwable t) {
            errorRef.set(t);
        } finally {
            synchronized (this) {
                unloadRequested = unloadAfterGeneration;
                finishGenerationLocked(errorRef.get());
                // cancelProcess() leaves the KV cache dirty, so a length-limited conversation
                // must never be reused even though its partial response is a normal completion.
                if (lengthLimited.get() || callbackCancelled.get() || !request.reusableConversation) closeConversationLocked();
            }
        }

        Throwable throwable = errorRef.get();
        if (throwable != null) {
            if (isCancellation(throwable)) {
                return error(499, "generation_cancelled", "LiteRT-LM generation was cancelled.");
            }
            return error(500, "litert_lm_generation_failed", "LiteRT-LM generation failed: " + throwable.getMessage());
        }
        if (unloadRequested) {
            return error(499, "generation_cancelled", "LiteRT-LM generation was cancelled and the model was unloaded.");
        }

        JSONObject data = new JSONObject();
        data.put("ok", true);
        data.put("model", modelId);
        data.put("runtime", "litert-lm");
        data.put("backend", backendName);
        data.put("backendFallbackReason", backendFallbackReason.isEmpty() ? JSONObject.NULL : backendFallbackReason);
        data.put("loaded", true);
        data.put("generationId", generationId);
        data.put("mode", mode);
        data.put("response", responseBuilder.toString());
        data.put("toolCalls", toolCalls);
        data.put("finishReason", callbackCancelled.get() ? "stop" : (lengthLimited.get()
            ? "length" : (toolCalls.length() > 0 ? "tool_calls" : "stop")));
        appendUsage(data, request, responseBuilder.toString(), decodedCallbacks.get(),
            lastPrefillTokens.get(), lastDecodeTokens.get(), conversationTokenCount.get());
        data.put("elapsedMs", System.currentTimeMillis() - startedAt);
        data.put("options", options.toJson());
        if (loadedProfile != null) {
            data.put("effectiveOptions", effectiveOptionsJson(options, loadedProfile));
            data.put("modelProfile", loadedProfile.toJson());
        }
        data.put("state", getState().toJson());
        return data;
    }

    private void appendUsage(
        @NonNull JSONObject data,
        @NonNull TaiChatRequest request,
        @NonNull String response,
        int decodedCallbacks,
        int lastPrefillTokens,
        int lastDecodeTokens,
        int conversationTokenCount
    ) throws JSONException {
        int promptTokens = lastPrefillTokens;
        int completionTokens = lastDecodeTokens;

        boolean estimated = false;
        if (completionTokens < 0 || (completionTokens == 0 && !response.isEmpty())) {
            completionTokens = decodedCallbacks > 0
                ? decodedCallbacks : approximateTokenCountFromCharacters(response);
            estimated = true;
        }
        if (promptTokens <= 0) {
            if (conversationTokenCount >= completionTokens && conversationTokenCount > 0) {
                promptTokens = conversationTokenCount - completionTokens;
            } else {
                promptTokens = approximatePromptTokens(request);
                estimated = true;
            }
        }

        JSONObject usage = new JSONObject();
        usage.put("prompt_tokens", Math.max(0, promptTokens));
        usage.put("completion_tokens", Math.max(0, completionTokens));
        usage.put("total_tokens", Math.max(0, promptTokens) + Math.max(0, completionTokens));
        data.put("usage", usage);
        data.put("usageEstimated", estimated);
        data.put("usageSource", estimated
            ? "litert_callback_or_characters_divided_by_4" : "litert_benchmark_info");
    }

    private int approximatePromptTokens(@NonNull TaiChatRequest request) {
        String prompt = request.systemPrompt + request.messagesJson + request.initialMessages + request.message;
        return approximateTokenCountFromCharacters(prompt);
    }

    private int approximateTokenCountFromCharacters(@Nullable String text) {
        if (text == null || text.isEmpty()) return 0;
        return Math.max(1, (text.length() + 3) / 4);
    }

    @NonNull
    private JSONObject loadInternal(@NonNull TaiModelSpec modelSpec, @NonNull TaiRuntimeOptions options, int keepWarmMinutes) throws JSONException {
        TaiModelProfile profile = TaiModelProfile.forModel(modelSpec);
        TaiDeviceCapabilities deviceCapabilities = TaiDeviceCapabilities.detect(appContext);
        if (!deviceCapabilities.liteRtLmAbiSupported) {
            return error(501, "litert_lm_unsupported_abi", "LiteRT-LM 0.14.0 ships native libraries for arm64-v8a and x86_64 only.");
        }
        if (!deviceCapabilities.liteRtLmNativeLibrariesAvailable) {
            return error(501, "litert_lm_native_unavailable", "LiteRT-LM native libraries are not available in this APK.");
        }
        if (modelSpec.localPath == null || modelSpec.localPath.trim().isEmpty()) {
            return error(404, "model_file_missing", "Download or import this model before loading it.");
        }
        File modelFile = new File(modelSpec.localPath);
        if (!modelFile.isFile() || !modelFile.canRead()) {
            JSONObject error = error(404, "model_file_not_readable", "Model file is missing or not readable by the runtime process.");
            error.put("path", modelSpec.localPath);
            return error;
        }

        String requestedAccelerator = requestedAccelerator(options);
        List<String> autoAccelerators = deviceCapabilities.compatibleAccelerators(profile);
        if (requestedAccelerator == null) {
            autoAccelerators = safeAutoAccelerators(modelSpec, deviceCapabilities, profile);
        }
        if (requestedAccelerator != null) {
            if (!profile.supports(requestedAccelerator)) {
                JSONObject error = error(400, "accelerator_not_supported_by_model",
                    "The model's Edge Gallery compatibility profile does not support " + requestedAccelerator.toUpperCase(Locale.ROOT) + ".");
                error.put("modelProfile", profile.toJson());
                return error;
            }
            if (!deviceCapabilities.supportsAccelerator(requestedAccelerator)) {
                JSONObject error = error(400, "accelerator_not_supported_by_device",
                    requestedAccelerator.toUpperCase(Locale.ROOT) + " is disabled or unsupported on this device.");
                error.put("device", deviceCapabilities.toJson());
                return error;
            }
        } else if (autoAccelerators.isEmpty()) {
            JSONObject error = error(400, "no_compatible_accelerator",
                "No accelerator is compatible with both this model and device.");
            error.put("modelProfile", profile.toJson());
            error.put("device", deviceCapabilities.toJson());
            return error;
        }

        synchronized (this) {
            if (generating) {
                return error(409, "generation_active", "Cancel the active generation before loading another LiteRT-LM model.");
            }
            if (loading) {
                return error(409, "load_in_progress", "A LiteRT-LM model is already loading.");
            }
            closeEngineLocked("Loading model.", "loading");
            loading = true;
            loadCancellationRequested = false;
            loadingModelId = modelSpec.id;
            runtimeState = "loading";
            statusMessage = "Loading " + modelSpec.id + ".";
        }

        Engine initializedEngine = null;
        String initializedBackendName = "none";
        String initializedFallbackReason = "";
        try {
            if (requestedAccelerator == null) {
                String selectedAccelerator = autoAccelerators.get(0);
                if ("gpu".equals(selectedAccelerator)) {
                    try {
                        throwIfLoadCancellationRequested();
                        Backend backend = new Backend.GPU();
                        initializedBackendName = backend.getName();
                        initializedFallbackReason = AUTO_GPU_REASON;
                        initializedEngine = createAndInitializeEngineWithCrashMarker(modelSpec, modelFile.getAbsolutePath(), options,
                            profile, deviceCapabilities, backend, "gpu");
                    } catch (Exception gpuException) {
                        TaiRuntimeCrashMarker.clear(appContext);
                        TaiRuntimeHistory.recordFailure(appContext, modelSpec, deviceCapabilities,
                            TaiModelSpec.BACKEND_LITERT_LM, "gpu", gpuException.getMessage() == null ? "GPU initialization failed." : gpuException.getMessage());
                        if (!autoAccelerators.contains("cpu")) throw gpuException;
                        throwIfLoadCancellationRequested();
                        Backend backend = new Backend.CPU();
                        initializedBackendName = backend.getName();
                        initializedFallbackReason = "Auto GPU initialization failed; selected the model's CPU fallback. GPU error: " + gpuException.getMessage();
                        initializedEngine = createAndInitializeEngineWithCrashMarker(modelSpec, modelFile.getAbsolutePath(), options,
                            profile, deviceCapabilities, backend, "cpu");
                    }
                } else {
                    throwIfLoadCancellationRequested();
                    Backend backend = new Backend.CPU();
                    initializedBackendName = backend.getName();
                    initializedFallbackReason = deviceCapabilities.pixel10 && profile.supports("gpu")
                        ? "Auto selected CPU because Google AI Edge Gallery disables GPU on Pixel 10 devices."
                        : AUTO_MODEL_CPU_REASON;
                    initializedEngine = createAndInitializeEngineWithCrashMarker(modelSpec, modelFile.getAbsolutePath(), options,
                        profile, deviceCapabilities, backend, "cpu");
                }
            } else {
                throwIfLoadCancellationRequested();
                Backend backend = "gpu".equals(requestedAccelerator) ? new Backend.GPU() : new Backend.CPU();
                initializedBackendName = backend.getName();
                initializedEngine = createAndInitializeEngineWithCrashMarker(modelSpec, modelFile.getAbsolutePath(), options,
                    profile, deviceCapabilities, backend, requestedAccelerator);
            }
        } catch (Exception e) {
            TaiRuntimeCrashMarker.clear(appContext);
            TaiRuntimeHistory.recordFailure(appContext, modelSpec, deviceCapabilities,
                TaiModelSpec.BACKEND_LITERT_LM, acceleratorFromBackendName(initializedBackendName, requestedAccelerator),
                e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage());
            if (modelSpec.sourceCapabilities.contains(TaiModelSpec.CAPABILITY_AUDIO_INPUT)) {
                TaiRuntimeHistory.recordAudioInputOutcome(appContext, modelSpec.id, deviceCapabilities, false);
            }
            synchronized (this) {
                boolean cancelled = loadCancellationRequested;
                finishLoadingLocked();
                runtimeState = cancelled ? "unloaded" : "failed";
                statusMessage = cancelled
                    ? "Model load cancelled."
                    : "LiteRT-LM load failed: " + e.getMessage();
                return error(cancelled ? 499 : 500,
                    cancelled ? "model_load_cancelled" : "litert_lm_load_failed", statusMessage);
            }
        }

        synchronized (this) {
            if (!loadCancellationRequested) {
                engine = initializedEngine;
                backendName = initializedBackendName;
                backendFallbackReason = initializedFallbackReason;
                loadedModelId = modelSpec.id;
                loadedModelPath = modelFile.getAbsolutePath();
                loadedOptions = options;
                loadedProfile = profile;
                loadedDeviceCapabilities = deviceCapabilities;
                loadedAtMs = System.currentTimeMillis();
                lastUsedAtMs = loadedAtMs;
                keepWarmUntilMs = keepWarmMinutes > 0 ? loadedAtMs + TimeUnit.MINUTES.toMillis(keepWarmMinutes) : 0L;
                TaiRuntimeCrashMarker.clear(appContext);
                TaiRuntimeHistory.recordSuccess(appContext, modelSpec, deviceCapabilities,
                    TaiModelSpec.BACKEND_LITERT_LM, acceleratorFromBackendName(backendName, requestedAccelerator));
                if (modelSpec.sourceCapabilities.contains(TaiModelSpec.CAPABILITY_AUDIO_INPUT)) {
                    TaiRuntimeHistory.recordAudioInputOutcome(appContext, modelSpec.id, deviceCapabilities, true);
                }
                finishLoadingLocked();
                statusMessage = keepWarmUntilMs > 0L ? "Model loaded and warm." : "Model loaded.";
                maybeRefreshStateLocked();
                scheduleIdleUnloadLocked();

                JSONObject data = stateEnvelopeLocked(true);
                data.put("loadedModelId", loadedModelId);
                data.put("backend", backendName);
                data.put("backendFallbackReason", backendFallbackReason.isEmpty() ? JSONObject.NULL : backendFallbackReason);
                data.put("modelPath", loadedModelPath);
                data.put("options", options.toJson());
                data.put("effectiveOptions", effectiveOptionsJson(options, profile));
                data.put("modelProfile", profile.toJson());
                data.put("device", deviceCapabilities.toJson());
                JSONArray compatibilityWarnings = new JSONArray();
                String memoryWarning = deviceCapabilities.memoryWarning(profile);
                if (memoryWarning != null) compatibilityWarnings.put(memoryWarning);
                data.put("compatibilityWarnings", compatibilityWarnings);
                if (keepWarmUntilMs > 0L) {
                    data.put("keepWarm", true);
                    data.put("keepWarmMinutes", keepWarmMinutes);
                    data.put("keepWarmUntilMs", keepWarmUntilMs);
                }
                return data;
            }
        }

        closeEngine(initializedEngine);
        TaiRuntimeCrashMarker.clear(appContext);
        synchronized (this) {
            finishLoadingLocked();
            runtimeState = "unloaded";
            statusMessage = "Model load cancelled.";
            return error(499, "model_load_cancelled", statusMessage);
        }
    }

    @Nullable
    private JSONObject ensureLoadedForGenerationLocked(@NonNull String modelId) throws JSONException {
        if (engine == null || loadedModelId == null || !loadedModelId.equals(modelId)) {
            return error(409, "model_not_loaded", "Load the downloaded model first with tai load " + modelId + " or from the TAI settings UI.");
        }
        return null;
    }

    @NonNull
    private Conversation ensureConversationLocked(@NonNull String mode, @NonNull TaiChatRequest request, @NonNull TaiRuntimeOptions options) {
        // Tools are baked into the Conversation at creation, so they must be part of the reuse
        // key — otherwise a cached tool-less conversation silently serves tool requests.
        String key = mode + "|" + normalized(request.systemPrompt) + "|" + optionsKey(options)
            + "|tools:" + request.toolDefinitions.toString().hashCode();
        if (request.reusableConversation && conversation != null && conversation.isAlive() && key.equals(conversationKey)) {
            return conversation;
        }
        closeConversationLocked();
        Contents systemContents = contents(request.systemPrompt);
        ConversationConfig conversationConfig = conversationConfig(systemContents, request, options);
        synchronized (EXPERIMENTAL_FLAGS_LOCK) {
            ExperimentalFlags.INSTANCE.setConvertCamelToSnakeCaseInToolDescription(false);
            boolean constrainedDecoding = !request.tools.isEmpty();
            if (constrainedDecoding) {
                ExperimentalFlags.INSTANCE.setEnableConversationConstrainedDecoding(true);
            }
            try {
                conversation = engine.createConversation(conversationConfig);
            } finally {
                if (constrainedDecoding) {
                    ExperimentalFlags.INSTANCE.setEnableConversationConstrainedDecoding(false);
                }
            }
        }
        conversationKey = request.reusableConversation ? key : "";
        return conversation;
    }

    @NonNull
    private String beginGenerationLocked() {
        long now = System.currentTimeMillis();
        generating = true;
        unloadAfterGeneration = false;
        activeGenerationStartedAtMs = now;
        activeGenerationId = "tai-gen-" + now;
        lastUsedAtMs = now;
        runtimeState = "generating";
        statusMessage = "Generating.";
        cancelIdleUnloadLocked();
        return activeGenerationId;
    }

    private void finishGenerationLocked(@Nullable Throwable throwable) {
        generating = false;
        activeGenerationId = null;
        activeGenerationStartedAtMs = 0L;
        lastUsedAtMs = System.currentTimeMillis();
        if (unloadAfterGeneration) {
            unloadAfterGeneration = false;
            closeEngineLocked("LiteRT-LM runtime is unloaded.", "unloaded");
            return;
        } else if (isCancellation(throwable)) {
            statusMessage = "Generation cancelled.";
            runtimeState = "loaded";
        } else if (throwable != null) {
            statusMessage = "Generation failed: " + throwable.getMessage();
            runtimeState = "loaded";
        } else {
            statusMessage = "Model loaded.";
            runtimeState = "loaded";
        }
        maybeRefreshStateLocked();
        scheduleIdleUnloadLocked();
    }

    private Engine createAndInitializeEngine(
        @NonNull TaiModelSpec modelSpec,
        @NonNull String modelPath,
        @NonNull TaiRuntimeOptions options,
        @NonNull TaiModelProfile profile,
        @NonNull TaiDeviceCapabilities deviceCapabilities,
        @NonNull Backend backend
    ) {
        String cacheDir = modelPath.startsWith("/data/local/tmp")
            ? appContext.getCacheDir().getAbsolutePath() : null;
        boolean imageInput = modelSpec.sourceCapabilities.contains(TaiModelSpec.CAPABILITY_IMAGE_INPUT)
            || modelSpec.capabilities.contains(TaiModelSpec.CAPABILITY_IMAGE_INPUT);
        boolean audioInput = modelSpec.sourceCapabilities.contains(TaiModelSpec.CAPABILITY_AUDIO_INPUT)
            || modelSpec.capabilities.contains(TaiModelSpec.CAPABILITY_AUDIO_INPUT);
        // The 5th EngineConfig arg is the engine's TOTAL token budget (KV-cache / max sequence),
        // not the per-request output cap. Size it to the model's context window (or an explicit
        // context_window override) so the input prompt always fits. Using the request's max_tokens
        // here made short max_tokens (e.g. 12) reject any longer prompt with "Input token ids are
        // too long" and reloaded the engine on every distinct max_tokens value.
        int engineMaxTokens = options.contextWindow != null
            ? options.contextWindow
            : Math.max(profile.defaultMaxTokens, modelSpec.endpointContextWindow);
        EngineConfig config = new EngineConfig(
            modelPath,
            backend,
            imageInput ? visionBackend(modelSpec, options, profile, deviceCapabilities) : null,
            // LiteRT-LM 0.14 also supports GPU/NPU audio acceleration; keep CPU as the default.
            audioInput ? new Backend.CPU() : null,
            engineMaxTokens,
            imageInput ? 8 : null,
            cacheDir
        );
        Boolean speculativeDecoding = speculativeDecodingFlag(options, modelPath);
        synchronized (EXPERIMENTAL_FLAGS_LOCK) {
            ExperimentalFlags.INSTANCE.setConvertCamelToSnakeCaseInToolDescription(false);
            Engine loadedEngine = new Engine(config);
            try {
                ExperimentalFlags.INSTANCE.setEnableSpeculativeDecoding(speculativeDecoding);
                loadedEngine.initialize();
                return loadedEngine;
            } catch (RuntimeException e) {
                try {
                    loadedEngine.close();
                } catch (Exception ignored) {
                }
                throw e;
            } finally {
                ExperimentalFlags.INSTANCE.setEnableSpeculativeDecoding(null);
            }
        }
    }

    private Engine createAndInitializeEngineWithCrashMarker(
        @NonNull TaiModelSpec modelSpec,
        @NonNull String modelPath,
        @NonNull TaiRuntimeOptions options,
        @NonNull TaiModelProfile profile,
        @NonNull TaiDeviceCapabilities deviceCapabilities,
        @NonNull Backend backend,
        @NonNull String accelerator
    ) {
        TaiRuntimeCrashMarker.markLoad(appContext, modelSpec, options.withAccelerator(accelerator), TaiModelSpec.BACKEND_LITERT_LM);
        return createAndInitializeEngine(modelSpec, modelPath, options, profile, deviceCapabilities, backend);
    }

    @NonNull
    private Backend visionBackend(
        @NonNull TaiModelSpec modelSpec,
        @NonNull TaiRuntimeOptions options,
        @NonNull TaiModelProfile profile,
        @NonNull TaiDeviceCapabilities deviceCapabilities
    ) {
        String requested = requestedAccelerator(options);
        boolean gpuKnownFailed = TaiRuntimeHistory.failedEntry(
            appContext, modelSpec, deviceCapabilities, "gpu") != null;
        return useGpuVision(requested, profile.supports("gpu"),
            deviceCapabilities.supportsAccelerator("gpu"), gpuKnownFailed)
            ? new Backend.GPU()
            : new Backend.CPU();
    }

    static boolean useGpuVision(
        @Nullable String requestedAccelerator,
        boolean modelSupportsGpu,
        boolean deviceSupportsGpu,
        boolean gpuKnownFailed
    ) {
        if (requestedAccelerator != null) return "gpu".equals(requestedAccelerator);
        return modelSupportsGpu && deviceSupportsGpu && !gpuKnownFailed;
    }

    @Nullable
    private String requestedAccelerator(@NonNull TaiRuntimeOptions options) {
        if (options.accelerator == null || options.accelerator.trim().isEmpty()
            || "auto".equalsIgnoreCase(options.accelerator)) return null;
        return options.accelerator.trim().toLowerCase(Locale.ROOT);
    }

    @NonNull
    private List<String> safeAutoAccelerators(
        @NonNull TaiModelSpec modelSpec,
        @NonNull TaiDeviceCapabilities deviceCapabilities,
        @NonNull TaiModelProfile profile
    ) {
        if (profile.supports("gpu") && deviceCapabilities.supportsAccelerator("gpu")
            && TaiRuntimeHistory.hasSuccessfulGpu(appContext, modelSpec, deviceCapabilities)) {
            return deviceCapabilities.compatibleAccelerators(profile);
        }
        if (profile.supports("cpu") && deviceCapabilities.supportsAccelerator("cpu")) {
            return Collections.singletonList("cpu");
        }
        return Collections.emptyList();
    }

    @NonNull
    private String acceleratorFromBackendName(@NonNull String backend, @Nullable String fallback) {
        String value = backend.toLowerCase(Locale.ROOT);
        if (value.contains("gpu")) return "gpu";
        if (value.contains("cpu")) return "cpu";
        return fallback == null ? "auto" : fallback;
    }

    @Nullable
    private Boolean speculativeDecodingFlag(@NonNull TaiRuntimeOptions options, @NonNull String modelPath) {
        if (!Boolean.TRUE.equals(options.speculativeDecodingEnabled)) return null;
        try (Capabilities capabilities = new Capabilities(modelPath)) {
            return capabilities.hasSpeculativeDecodingSupport() ? Boolean.TRUE : null;
        } catch (Exception ignored) {
            return null;
        }
    }

    @NonNull
    private ConversationConfig conversationConfig(
        @NonNull Contents systemPrompt,
        @NonNull TaiChatRequest request,
        @NonNull TaiRuntimeOptions options
    ) {
        TaiModelProfile profile = loadedProfile;
        SamplerConfig samplerConfig = new SamplerConfig(
            options.topK == null ? (profile == null ? DEFAULT_TOP_K : profile.defaultTopK) : options.topK,
            options.topP == null ? (profile == null ? DEFAULT_TOP_P : profile.defaultTopP) : options.topP,
            options.temperature == null ? (profile == null ? DEFAULT_TEMPERATURE : profile.defaultTemperature) : options.temperature,
            0
        );
        return new ConversationConfig(
            systemPrompt,
            request.initialMessages,
            request.tools,
            samplerConfig,
            false,
            null,
            Collections.emptyMap()
        );
    }

    private void appendToolCalls(@NonNull JSONArray output, @NonNull List<ToolCall> calls, @NonNull String generationId) {
        for (int i = 0; i < calls.size(); i++) {
            ToolCall call = calls.get(i);
            JSONObject function = new JSONObject();
            JSONObject item = new JSONObject();
            try {
                function.put("name", call.getName());
                function.put("arguments", new JSONObject(call.getArguments()).toString());
                item.put("id", generationId + "-call-" + (output.length() + 1));
                item.put("type", "function");
                item.put("function", function);
                output.put(item);
            } catch (JSONException ignored) {
            }
        }
    }

    @NonNull
    private Contents contents(@NonNull String text) {
        return Contents.Companion.of(text);
    }

    @NonNull
    private String textFromMessage(@Nullable Message message) {
        if (message == null || message.getContents() == null) return "";
        StringBuilder builder = new StringBuilder();
        for (Content content : message.getContents().getContents()) {
            if (content instanceof Content.Text) {
                builder.append(((Content.Text) content).getText());
            } else if (content != null) {
                builder.append(content.toString());
            }
        }
        return builder.toString();
    }

    private void scheduleIdleUnloadLocked() {
        cancelIdleUnloadLocked();
        if (engine == null) {
            idleUnloadAtMs = 0L;
            return;
        }
        long target = calculateUnloadAtMsLocked();
        idleUnloadAtMs = target;
        if (target <= 0L) return;
        long delayMs = Math.max(1000L, target - System.currentTimeMillis());
        idleUnloadFuture = scheduler.schedule(this::maybeUnloadAfterIdle, delayMs, TimeUnit.MILLISECONDS);
    }

    private void maybeUnloadAfterIdle() {
        synchronized (this) {
            if (engine == null) return;
            if (generating) {
                scheduleIdleUnloadLocked();
                return;
            }
            long target = calculateUnloadAtMsLocked();
            idleUnloadAtMs = target;
            long now = System.currentTimeMillis();
            if (target > now) {
                idleUnloadFuture = scheduler.schedule(this::maybeUnloadAfterIdle, target - now, TimeUnit.MILLISECONDS);
                return;
            }
            if (target > 0L) {
                closeEngineLocked("Model unloaded after idle or keep-warm timeout.", "unloaded");
            }
        }
    }

    private long calculateUnloadAtMsLocked() {
        long target = 0L;
        long now = System.currentTimeMillis();
        if (keepWarmUntilMs > now) target = keepWarmUntilMs;
        int idleMinutes = loadedOptions != null && loadedOptions.idleUnloadMinutes != null ? loadedOptions.idleUnloadMinutes : 0;
        if (idleMinutes > 0) {
            long idleTarget = lastUsedAtMs + TimeUnit.MINUTES.toMillis(idleMinutes);
            target = Math.max(target, idleTarget);
        }
        return target;
    }

    private void cancelIdleUnloadLocked() {
        if (idleUnloadFuture != null) {
            idleUnloadFuture.cancel(false);
            idleUnloadFuture = null;
        }
    }

    private void maybeRefreshStateLocked() {
        if (loading) {
            runtimeState = loadCancellationRequested ? "stopping" : "loading";
            return;
        }
        if (engine == null) {
            if (!"failed".equals(runtimeState)) runtimeState = "unloaded";
            return;
        }
        if (generating) {
            if ("stopping".equals(runtimeState)) return;
            runtimeState = "generating";
        } else if (keepWarmUntilMs > System.currentTimeMillis()) {
            runtimeState = "idle-warm";
        } else {
            runtimeState = "loaded";
        }
    }

    private void closeEngineLocked(@NonNull String nextStatus, @NonNull String nextState) {
        cancelIdleUnloadLocked();
        closeEngineResourcesLocked();
        loadedModelId = null;
        loadedModelPath = null;
        loadedOptions = null;
        loadedProfile = null;
        loadedDeviceCapabilities = null;
        backendName = "none";
        backendFallbackReason = "";
        runtimeState = nextState;
        statusMessage = nextStatus;
        generating = false;
        activeGenerationId = null;
        activeGenerationStartedAtMs = 0L;
        loadedAtMs = 0L;
        lastUsedAtMs = 0L;
        keepWarmUntilMs = 0L;
        idleUnloadAtMs = 0L;
    }

    private void closeEngineResourcesLocked() {
        closeConversationLocked();
        if (engine != null) {
            try {
                engine.close();
            } catch (Exception ignored) {
            }
        }
        engine = null;
    }

    private void finishLoadingLocked() {
        loading = false;
        loadCancellationRequested = false;
        loadingModelId = null;
    }

    private synchronized void throwIfLoadCancellationRequested() {
        if (loadCancellationRequested) {
            throw new CancellationException("Model load cancelled.");
        }
    }

    static boolean isCancellation(@Nullable Throwable throwable) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (current instanceof CancellationException) return true;
            String message = current.getMessage();
            if (message != null && message.toUpperCase(Locale.ROOT).contains("CANCELLED")) return true;
        }
        return false;
    }

    private void closeEngine(@Nullable Engine engineToClose) {
        if (engineToClose == null) return;
        try {
            engineToClose.close();
        } catch (Exception ignored) {
        }
    }

    private void closeConversationLocked() {
        if (conversation != null) {
            try {
                conversation.close();
            } catch (Exception ignored) {
            }
        }
        conversation = null;
        conversationKey = "";
    }

    @NonNull
    private JSONObject stateEnvelopeLocked(boolean ok) throws JSONException {
        JSONObject data = new JSONObject();
        data.put("ok", ok);
        data.put("runtime", "litert-lm");
        data.put("state", getState().toJson());
        if (loadedProfile != null) data.put("modelProfile", loadedProfile.toJson());
        if (loadedDeviceCapabilities != null) {
            data.put("device", loadedDeviceCapabilities.toJson());
            JSONArray warnings = new JSONArray();
            String warning = loadedDeviceCapabilities.memoryWarning(loadedProfile);
            if (warning != null) warnings.put(warning);
            data.put("compatibilityWarnings", warnings);
        }
        return data;
    }

    @NonNull
    private JSONObject effectiveOptionsJson(@NonNull TaiRuntimeOptions options, @NonNull TaiModelProfile profile) throws JSONException {
        JSONObject json = options.toJson();
        json.put("maxTokens", options.maxTokens == null ? profile.defaultMaxTokens : options.maxTokens);
        json.put("topK", options.topK == null ? profile.defaultTopK : options.topK);
        json.put("topP", options.topP == null ? profile.defaultTopP : options.topP);
        json.put("temperature", options.temperature == null ? profile.defaultTemperature : options.temperature);
        return json;
    }

    @NonNull
    private JSONObject error(int statusCode, String code, String message) throws JSONException {
        JSONObject data = new JSONObject();
        data.put("ok", false);
        data.put("error", code);
        data.put("message", message);
        data.put("runtime", "litert-lm");
        data.put("state", getState().toJson());
        data.put("_statusCode", statusCode);
        return data;
    }

    @NonNull
    private String optionsKey(@NonNull TaiRuntimeOptions options) {
        // contextWindow (not maxTokens) sizes the engine, so reload only when it changes.
        return String.valueOf(options.contextWindow) + "|"
            + options.topK + "|"
            + options.topP + "|"
            + options.temperature + "|"
            + options.thinkingEnabled + "|"
            + options.speculativeDecodingEnabled;
    }

    @NonNull
    private String normalized(@NonNull String value) {
        return value.trim();
    }
}
