package com.termux.ai;

import android.content.Context;
import android.net.Uri;
import android.util.Base64;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.alibaba.mnnllm.android.llm.LlmSession;
import com.google.ai.edge.litertlm.Content;
import com.google.ai.edge.litertlm.Message;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MnnTaiRuntime implements TaiRuntime {
    private final Context appContext;

    private LlmSession session;
    private String runtimeState = "unloaded";
    private String statusMessage = "MNN runtime is unloaded.";
    private String loadedModelId;
    private String loadedModelPath;
    private TaiRuntimeOptions loadedOptions;
    private long loadedAtMs;
    private long lastUsedAtMs;
    private long keepWarmUntilMs;
    private boolean generating;
    private String activeGenerationId;
    private long activeGenerationStartedAtMs;
    private volatile boolean cancelRequested;
    private boolean unloadAfterGeneration;

    public MnnTaiRuntime(@NonNull Context context) {
        appContext = context.getApplicationContext();
    }

    public static boolean isNativeRuntimeAvailable() {
        try {
            Class.forName("com.alibaba.mnnllm.android.llm.LlmSession");
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    @NonNull
    @Override
    public synchronized TaiRuntimeState getState() {
        maybeRefreshStateLocked();
        return new TaiRuntimeState(
            loadedModelId != null,
            loadedModelId,
            TaiModelSpec.BACKEND_MNN_LLM,
            runtimeState,
            statusMessage,
            loadedModelId == null ? "none" : backendName(loadedOptions),
            null,
            loadedModelPath,
            generating,
            activeGenerationId,
            activeGenerationStartedAtMs,
            keepWarmUntilMs,
            keepWarmUntilMs > 0L ? keepWarmUntilMs : 0L,
            loadedAtMs,
            lastUsedAtMs
        );
    }

    @Override
    public synchronized boolean isModelLoaded(@NonNull String modelId) {
        return loadedModelId != null && loadedModelId.equals(modelId) && session != null && session.isLoaded();
    }

    @NonNull
    @Override
    public JSONObject load(@NonNull TaiModelSpec modelSpec, @NonNull TaiRuntimeOptions options) throws JSONException {
        return loadInternal(modelSpec, options, 0);
    }

    @NonNull
    @Override
    public synchronized JSONObject unload() throws JSONException {
        if (generating) {
            unloadAfterGeneration = true;
            cancelRequested = true;
            runtimeState = "stopping";
            statusMessage = "Cancelling generation; MNN will unload when native generation stops.";
            JSONObject data = stateEnvelopeLocked(true);
            data.put("cancelled", true);
            data.put("unloadPending", true);
            data.put("message", statusMessage);
            return data;
        }
        String previous = loadedModelId;
        unloadAfterGeneration = false;
        releaseSessionLocked();
        runtimeState = "unloaded";
        statusMessage = "MNN runtime is unloaded.";
        JSONObject data = stateEnvelopeLocked(true);
        data.put("unloadedModelId", previous == null ? JSONObject.NULL : previous);
        return data;
    }

    @NonNull
    @Override
    public JSONObject keepWarm(@NonNull TaiModelSpec modelSpec, @NonNull TaiRuntimeOptions options, int minutes) throws JSONException {
        return loadInternal(modelSpec, options, minutes);
    }

    @NonNull
    @Override
    public synchronized JSONObject cancel() throws JSONException {
        if (!generating) {
            JSONObject data = stateEnvelopeLocked(true);
            data.put("cancelled", false);
            data.put("message", "No active MNN generation.");
            return data;
        }
        cancelRequested = true;
        runtimeState = "stopping";
        statusMessage = "Cancelling active MNN generation.";
        JSONObject data = stateEnvelopeLocked(true);
        data.put("cancelled", true);
        data.put("message", "Cancel requested.");
        return data;
    }

    @NonNull
    @Override
    public JSONObject chat(@NonNull String modelId, @NonNull String systemPrompt, @NonNull String userPrompt, @NonNull TaiRuntimeOptions options) throws JSONException {
        return generate(modelId, "chat", TaiChatRequest.oneShot(systemPrompt, userPrompt), options, null);
    }

    @NonNull
    @Override
    public JSONObject chat(@NonNull String modelId, @NonNull String systemPrompt, @NonNull String userPrompt, @NonNull TaiRuntimeOptions options, @NonNull TaiGenerationCallback callback) throws JSONException {
        return generate(modelId, "chat", TaiChatRequest.oneShot(systemPrompt, userPrompt), options, callback);
    }

    @NonNull
    @Override
    public JSONObject chat(@NonNull String modelId, @NonNull TaiChatRequest request, @NonNull TaiRuntimeOptions options) throws JSONException {
        return generate(modelId, "chat", request, options, null);
    }

    @NonNull
    @Override
    public JSONObject chat(@NonNull String modelId, @NonNull TaiChatRequest request, @NonNull TaiRuntimeOptions options, @NonNull TaiGenerationCallback callback) throws JSONException {
        return generate(modelId, "chat", request, options, callback);
    }

    @NonNull
    @Override
    public JSONObject complete(@NonNull String modelId, @NonNull String prompt, @NonNull TaiRuntimeOptions options) throws JSONException {
        return generate(modelId, "completion", TaiChatRequest.oneShot("", prompt), options, null);
    }

    @NonNull
    @Override
    public JSONObject complete(@NonNull String modelId, @NonNull String prompt, @NonNull TaiRuntimeOptions options, @NonNull TaiGenerationCallback callback) throws JSONException {
        return generate(modelId, "completion", TaiChatRequest.oneShot("", prompt), options, callback);
    }

    @NonNull
    private JSONObject loadInternal(@NonNull TaiModelSpec modelSpec, @NonNull TaiRuntimeOptions options, int keepWarmMinutes) throws JSONException {
        if (!TaiModelSpec.BACKEND_MNN_LLM.equals(modelSpec.backend)) {
            return error(409, "backend_mismatch", "Model " + modelSpec.id + " is not an MNN model.");
        }
        if (modelSpec.localPath == null || modelSpec.localPath.trim().isEmpty()) {
            return error(404, "model_file_not_readable", "MNN model config path is missing.");
        }
        File config = new File(modelSpec.localPath);
        if (!config.isFile() || !"config.json".equals(config.getName())) {
            return error(404, "model_file_not_readable", "MNN models must point to a readable config.json file.");
        }
        JSONObject validationError = validateMnnPackage(config);
        if (validationError != null) return validationError;
        TaiDeviceCapabilities deviceCapabilities = TaiDeviceCapabilities.detect(appContext);
        if (!deviceCapabilities.mnnSupported) {
            return error(501, "mnn_native_unavailable",
                deviceCapabilities.mnnUnsupportedReason == null
                    ? "Native MNN runtime libraries are not available for this APK/ABI."
                    : deviceCapabilities.mnnUnsupportedReason);
        }
        TaiRuntimeCrashMarker.markLoad(appContext, modelSpec, options, TaiModelSpec.BACKEND_MNN_LLM);
        if (!isNativeRuntimeAvailable()) {
            TaiRuntimeCrashMarker.clear(appContext);
            TaiRuntimeHistory.recordFailure(appContext, modelSpec, deviceCapabilities,
                TaiModelSpec.BACKEND_MNN_LLM, backendName(options), "Native MNN runtime libraries are not available for this APK/ABI.");
            return error(501, "mnn_native_unavailable", "Native MNN runtime libraries are not available for this APK/ABI.");
        }

        synchronized (this) {
            if (generating) return errorLocked(409, "generation_active", "Cancel the active generation before loading another MNN model.");
            runtimeState = "loading";
            statusMessage = "Loading " + modelSpec.id + ".";
            releaseSessionLocked();
        }

        LlmSession initialized;
        try {
            String mergedConfig = mergedConfigJson(config, modelSpec, options);
            String extraConfig = extraConfigJson(modelSpec);
            initialized = new LlmSession();
            initialized.load(config.getAbsolutePath(), null, mergedConfig, extraConfig);
        } catch (Throwable t) {
            TaiRuntimeCrashMarker.clear(appContext);
            TaiRuntimeHistory.recordFailure(appContext, modelSpec, deviceCapabilities,
                TaiModelSpec.BACKEND_MNN_LLM, backendName(options), message(t));
            synchronized (this) {
                runtimeState = "failed";
                statusMessage = "MNN load failed: " + message(t);
                return errorLocked(500, "mnn_load_failed", statusMessage);
            }
        }

        synchronized (this) {
            session = initialized;
            loadedModelId = modelSpec.id;
            loadedModelPath = config.getAbsolutePath();
            loadedOptions = options;
            loadedAtMs = System.currentTimeMillis();
            lastUsedAtMs = loadedAtMs;
            keepWarmUntilMs = keepWarmMinutes > 0 ? loadedAtMs + TimeUnit.MINUTES.toMillis(keepWarmMinutes) : 0L;
            TaiRuntimeCrashMarker.clear(appContext);
            TaiRuntimeHistory.recordSuccess(appContext, modelSpec, deviceCapabilities,
                TaiModelSpec.BACKEND_MNN_LLM, backendName(options));
            runtimeState = "loaded";
            statusMessage = keepWarmUntilMs > 0L ? "MNN model loaded and warm." : "MNN model loaded.";
            JSONObject data = stateEnvelopeLocked(true);
            data.put("loadedModelId", loadedModelId);
            data.put("backend", backendName(options));
            data.put("modelPath", loadedModelPath);
            data.put("options", options.toJson());
            data.put("effectiveConfig", safeJson(initialized.dumpConfig()));
            if (keepWarmUntilMs > 0L) {
                data.put("keepWarm", true);
                data.put("keepWarmMinutes", keepWarmMinutes);
                data.put("keepWarmUntilMs", keepWarmUntilMs);
            }
            return data;
        }
    }

    @NonNull
    private JSONObject generate(
        @NonNull String modelId,
        @NonNull String mode,
        @NonNull TaiChatRequest request,
        @NonNull TaiRuntimeOptions options,
        @Nullable TaiGenerationCallback callback
    ) throws JSONException {
        LlmSession activeSession;
        String generationId;
        long startedAt;
        synchronized (this) {
            JSONObject availabilityError = ensureLoadedForGenerationLocked(modelId);
            if (availabilityError != null) return availabilityError;
            if (generating) return errorLocked(409, "generation_active", "A MNN generation is already running. Cancel it or wait for it to finish.");
            activeSession = session;
            if (activeSession == null) return errorLocked(409, "model_not_loaded", "Load the downloaded MNN model first.");
            applyRuntimeOptionsLocked(activeSession, options);
            generationId = beginGenerationLocked();
            startedAt = activeGenerationStartedAtMs;
        }

        StringBuilder responseBuilder = new StringBuilder();
        AtomicReference<Throwable> errorRef = new AtomicReference<>();
        AtomicBoolean callbackCancelled = new AtomicBoolean(false);
        HashMap<String, Object> metrics = new HashMap<>();
        List<Pair<String, String>> history = mnnHistory(request);
        String debugInfo = "";
        boolean wasCancelled;
        try {
            HashMap<String, Object> nativeResult = activeSession.generateHistory(history, progress -> {
                if (cancelRequested) return true;
                if (progress == null) return false;
                synchronized (responseBuilder) {
                    responseBuilder.append(progress);
                }
                if (callback != null) callback.onToken(progress);
                if (callback != null && callback.shouldCancelGeneration()) {
                    callbackCancelled.set(true);
                    cancelRequested = true;
                    return true;
                }
                return false;
            });
            if (nativeResult != null) metrics.putAll(nativeResult);
            debugInfo = activeSession.debugInfo();
        } catch (Throwable t) {
            errorRef.set(t);
        } finally {
            synchronized (this) {
                wasCancelled = cancelRequested;
                finishGenerationLocked(errorRef.get());
            }
        }

        Throwable throwable = errorRef.get();
        String response;
        synchronized (responseBuilder) {
            response = responseBuilder.toString();
        }
        if (throwable != null) {
            if (callback != null) callback.onError(throwable);
            if (wasCancelled || callbackCancelled.get()) {
                return error(499, "generation_cancelled", "MNN generation was cancelled.");
            }
            return error(500, "mnn_generation_failed", "MNN generation failed: " + message(throwable));
        }
        if (wasCancelled && !callbackCancelled.get()) {
            return error(499, "generation_cancelled", "MNN generation was cancelled.");
        }
        response = OpenAiStopSequences.truncate(response, request.stopSequences).text;
        JSONArray toolCalls = parseToolCalls(response, generationId);
        if (toolCalls.length() == 0) appendRequiredFallbackToolCall(request, response, generationId, toolCalls);
        JSONObject toolChoiceError = validateRequiredToolChoice(request.toolChoice, request.toolDefinitions, toolCalls);
        if (toolChoiceError != null) return toolChoiceError;
        String responseText = toolCalls.length() > 0
            ? (responseLooksLikeToolCallJson(response) ? "" : stripToolCallBlocks(response).trim())
            : response;
        if (callback != null) {
            if (toolCalls.length() > 0) callback.onToolCalls(toolCalls);
            callback.onComplete(responseText);
        }

        JSONObject data = new JSONObject();
        data.put("ok", true);
        data.put("model", modelId);
        data.put("runtime", TaiModelSpec.BACKEND_MNN_LLM);
        data.put("backend", backendName(options));
        data.put("loaded", true);
        data.put("generationId", generationId);
        data.put("mode", mode);
        data.put("response", responseText);
        data.put("toolCalls", toolCalls);
        data.put("finishReason", "stop");
        data.put("elapsedMs", System.currentTimeMillis() - startedAt);
        data.put("options", options.toJson());
        data.put("effectiveConfig", safeJson(activeSession.dumpConfig()));
        data.put("metrics", metricsJson(metrics));
        appendUsage(data, metrics, debugInfo, history, response);
        data.put("stoppedByCallback", callbackCancelled.get());
        data.put("state", getState().toJson());
        return data;
    }

    private void appendUsage(
        @NonNull JSONObject data,
        @NonNull HashMap<String, Object> metrics,
        @Nullable String debugInfo,
        @NonNull List<Pair<String, String>> history,
        @NonNull String response
    ) throws JSONException {
        int promptTokens = metricInt(metrics, "prompt_len", "prompt_tokens", "prefill_tokens", "promptLen");
        int completionTokens = metricInt(metrics, "decode_len", "completion_tokens", "decode_tokens", "decodeLen");
        if (promptTokens < 0) promptTokens = debugMetricInt(debugInfo, "prompt_len");
        if (completionTokens < 0) completionTokens = debugMetricInt(debugInfo, "decode_len");

        boolean estimated = false;
        if (promptTokens < 0) {
            int promptCharacters = 0;
            for (Pair<String, String> item : history) {
                if (item != null && item.second != null) promptCharacters += item.second.length();
            }
            promptTokens = approximateTokenCountFromCharacters(promptCharacters);
            estimated = true;
        }
        if (completionTokens < 0 || (completionTokens == 0 && !response.isEmpty())) {
            completionTokens = approximateTokenCountFromCharacters(response.length());
            estimated = true;
        }

        JSONObject usage = new JSONObject();
        usage.put("prompt_tokens", Math.max(0, promptTokens));
        usage.put("completion_tokens", Math.max(0, completionTokens));
        usage.put("total_tokens", Math.max(0, promptTokens) + Math.max(0, completionTokens));
        data.put("usage", usage);
        data.put("usageEstimated", estimated);
        data.put("usageSource", estimated
            ? "mnn_metrics_or_characters_divided_by_4" : "mnn_native_metrics");
    }

    private int metricInt(@NonNull HashMap<String, Object> metrics, @NonNull String... keys) {
        for (String key : keys) {
            Object value = metrics.get(key);
            if (value instanceof Number) return Math.max(0, ((Number) value).intValue());
            if (value != null) {
                try {
                    return Math.max(0, Integer.parseInt(String.valueOf(value).trim()));
                } catch (NumberFormatException ignored) {
                }
            }
        }
        return -1;
    }

    private int debugMetricInt(@Nullable String debugInfo, @NonNull String key) {
        if (debugInfo == null || debugInfo.isEmpty()) return -1;
        Matcher matcher = Pattern.compile("(?:\\\"?" + Pattern.quote(key)
            + "\\\"?)\\s*[:=]\\s*(\\d+)").matcher(debugInfo);
        if (!matcher.find()) return -1;
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    private int approximateTokenCountFromCharacters(int characters) {
        if (characters <= 0) return 0;
        return Math.max(1, (characters + 3) / 4);
    }

    @Nullable
    private JSONObject ensureLoadedForGenerationLocked(@NonNull String modelId) throws JSONException {
        if (session == null || loadedModelId == null || !loadedModelId.equals(modelId)) {
            return errorLocked(409, "model_not_loaded", "Load the downloaded MNN model first with tai load " + modelId + " or from the TAI settings UI.");
        }
        return null;
    }

    private void applyRuntimeOptionsLocked(@NonNull LlmSession activeSession, @NonNull TaiRuntimeOptions options) {
        if (options.maxTokens != null) activeSession.updateMaxNewTokens(options.maxTokens);
        try {
            JSONObject overrides = overridesJson(options);
            if (overrides.length() > 0) activeSession.updateConfig(overrides.toString());
        } catch (JSONException ignored) {
        }
    }

    @NonNull
    private String beginGenerationLocked() {
        long now = System.currentTimeMillis();
        generating = true;
        cancelRequested = false;
        unloadAfterGeneration = false;
        activeGenerationStartedAtMs = now;
        activeGenerationId = "tai-mnn-gen-" + now;
        lastUsedAtMs = now;
        runtimeState = "generating";
        statusMessage = "Generating.";
        return activeGenerationId;
    }

    private void finishGenerationLocked(@Nullable Throwable throwable) {
        generating = false;
        activeGenerationId = null;
        activeGenerationStartedAtMs = 0L;
        lastUsedAtMs = System.currentTimeMillis();
        if (unloadAfterGeneration) {
            releaseSessionLocked();
            runtimeState = "unloaded";
            statusMessage = "MNN runtime is unloaded.";
            cancelRequested = false;
            unloadAfterGeneration = false;
            return;
        } else if (cancelRequested) {
            statusMessage = "Generation cancelled.";
        } else if (throwable != null) {
            statusMessage = "Generation failed: " + message(throwable);
        } else {
            statusMessage = "MNN model loaded.";
        }
        runtimeState = "loaded";
        cancelRequested = false;
        maybeRefreshStateLocked();
    }

    private void maybeRefreshStateLocked() {
        if (loadedModelId == null || generating || keepWarmUntilMs <= 0L) return;
        if (System.currentTimeMillis() > keepWarmUntilMs) keepWarmUntilMs = 0L;
    }

    private void releaseSessionLocked() {
        if (session != null) {
            try {
                session.release();
            } catch (Throwable ignored) {
            }
        }
        session = null;
        loadedModelId = null;
        loadedModelPath = null;
        loadedOptions = null;
        loadedAtMs = 0L;
        lastUsedAtMs = 0L;
        keepWarmUntilMs = 0L;
    }

    @NonNull
    private List<Pair<String, String>> mnnHistory(@NonNull TaiChatRequest request) {
        ArrayList<Pair<String, String>> history = new ArrayList<>();
        String systemPrompt = mnnSystemPrompt(request);
        if (!systemPrompt.trim().isEmpty()) history.add(new Pair<>("system", systemPrompt));
        if (request.messagesJson.length() > 0) {
            appendOpenAiHistory(history, request.messagesJson);
            return history;
        }
        for (Message message : request.initialMessages) history.add(new Pair<>(mnnRole(message), textFromMessage(message)));
        history.add(new Pair<>(mnnRole(request.message), textFromMessage(request.message)));
        return history;
    }

    private void appendOpenAiHistory(
        @NonNull ArrayList<Pair<String, String>> history,
        @NonNull JSONArray messages
    ) {
        for (int i = 0; i < messages.length(); i++) {
            JSONObject source = messages.optJSONObject(i);
            if (source == null) continue;
            String role = source.optString("role", "user");
            if ("system".equals(role) || "developer".equals(role)) continue;
            String content = openAiContentText(source.opt("content"));
            if ("assistant".equals(role) && source.has("tool_calls")) {
                content = appendAssistantToolCalls(content, source.optJSONArray("tool_calls"));
            } else if ("tool".equals(role)) {
                content = "<tool_response>\n" + content + "\n</tool_response>";
            }
            history.add(new Pair<>(role, content));
        }
    }

    @NonNull
    private String appendAssistantToolCalls(@NonNull String content, @Nullable JSONArray toolCalls) {
        StringBuilder builder = new StringBuilder(content);
        if (toolCalls == null) return builder.toString();
        for (int i = 0; i < toolCalls.length(); i++) {
            JSONObject call = toolCalls.optJSONObject(i);
            JSONObject function = call == null ? null : call.optJSONObject("function");
            if (function == null) continue;
            JSONObject toolCall = new JSONObject();
            try {
                toolCall.put("name", function.optString("name", ""));
                Object arguments = function.opt("arguments");
                if (arguments instanceof JSONObject) {
                    toolCall.put("arguments", arguments);
                } else {
                    String argumentsText = arguments == null ? "{}" : String.valueOf(arguments);
                    toolCall.put("arguments", argumentsText.trim().isEmpty() ? new JSONObject() : new JSONObject(argumentsText));
                }
                builder.append("\n<tool_call>\n").append(toolCall).append("\n</tool_call>");
            } catch (JSONException ignored) {
            }
        }
        return builder.toString();
    }

    @NonNull
    private String mnnSystemPrompt(@NonNull TaiChatRequest request) {
        StringBuilder prompt = new StringBuilder(request.systemPrompt == null ? "" : request.systemPrompt.trim());
        if (request.messagesJson.length() > 0) {
            StringBuilder openAiSystemPrompt = new StringBuilder();
            for (int i = 0; i < request.messagesJson.length(); i++) {
                JSONObject message = request.messagesJson.optJSONObject(i);
                if (message == null) continue;
                String role = message.optString("role", "user");
                if (!"system".equals(role) && !"developer".equals(role)) continue;
                String content = openAiContentText(message.opt("content")).trim();
                if (content.isEmpty()) continue;
                if (openAiSystemPrompt.length() > 0) openAiSystemPrompt.append('\n');
                openAiSystemPrompt.append(content);
            }
            String openAiPrompt = openAiSystemPrompt.toString();
            String requestPrompt = prompt.toString();
            boolean alreadyIncluded = requestPrompt.equals(openAiPrompt)
                || requestPrompt.startsWith(openAiPrompt + "\n");
            if (!openAiPrompt.isEmpty() && !alreadyIncluded) {
                if (prompt.length() > 0) openAiSystemPrompt.append("\n\n").append(prompt);
                prompt = openAiSystemPrompt;
            }
        }
        if (request.toolDefinitions.length() == 0 || "none".equals(String.valueOf(request.toolChoice))) {
            return prompt.toString();
        }
        if (prompt.length() > 0) prompt.append("\n\n");
        prompt.append("# Tools\n\n")
            .append("You may call one or more functions to assist with the user query.\n")
            .append("Function signatures are provided inside <tools></tools> XML tags:\n<tools>");
        for (int i = 0; i < request.toolDefinitions.length(); i++) {
            JSONObject tool = request.toolDefinitions.optJSONObject(i);
            if (tool != null) prompt.append('\n').append(tool);
        }
        prompt.append("\n</tools>\n\n")
            .append("For each function call, return exactly one JSON object with function name and arguments ")
            .append("inside <tool_call></tool_call> XML tags:\n")
            .append("<tool_call>\n")
            .append("{\"name\":\"function_name\",\"arguments\":{\"argument\":\"value\"}}\n")
            .append("</tool_call>\n")
            .append("Do not answer in prose when a tool call is required.");
        Object choice = request.toolChoice;
        if ("required".equals(String.valueOf(choice))) {
            prompt.append("\nYou must call one of the provided tools.");
        } else if (choice instanceof JSONObject) {
            JSONObject function = ((JSONObject) choice).optJSONObject("function");
            String name = function == null ? "" : function.optString("name", "");
            if (!name.isEmpty()) prompt.append("\nYou must call the tool named ").append(name).append('.');
        }
        return prompt.toString();
    }

    @NonNull
    private String openAiContentText(@Nullable Object content) {
        if (content == null || JSONObject.NULL.equals(content)) return "";
        if (content instanceof JSONArray) {
            JSONArray array = (JSONArray) content;
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < array.length(); i++) {
                Object item = array.opt(i);
                if (item instanceof JSONObject) {
                    JSONObject object = (JSONObject) item;
                    String type = object.optString("type", "");
                    if ("text".equals(type)) {
                        builder.append(object.optString("text", ""));
                    } else if ("image_url".equals(type) || "input_image".equals(type)) {
                        builder.append(mnnImageMarkup(object));
                    } else if ("input_audio".equals(type) || "audio".equals(type)) {
                        builder.append(mnnAudioMarkup(object));
                    }
                } else if (item != null && !JSONObject.NULL.equals(item)) {
                    builder.append(String.valueOf(item));
                }
            }
            return builder.toString();
        }
        return String.valueOf(content);
    }

    /**
     * Best-effort image input for MNN VL models (Qwen-VL, SmolVLM, MiniCPM-V): MNN-LLM consumes
     * images as inline {@code <img>path</img>} markup in the prompt text. Data-URL/base64 images are
     * materialised to a cache file; local paths pass straight through.
     * ponytail: the {@code <img>} convention is MNN's documented multimodal prompt format but is
     * unverified against every MNN VL build — a non-VL model just ignores the tag. Upgrade to a
     * native setImages bridge if a build needs a different placeholder. http(s) URLs are skipped to
     * keep this minimal (clients send base64 data URLs in practice).
     */
    @NonNull
    private String mnnImageMarkup(@NonNull JSONObject part) {
        Object value = part.opt("image_url");
        String url = "";
        if (value instanceof JSONObject) url = ((JSONObject) value).optString("url", "");
        else if (value != null && !JSONObject.NULL.equals(value)) url = String.valueOf(value);
        if (url.trim().isEmpty()) url = part.optString("image_url", "");
        url = url.trim();
        if (url.isEmpty()) return "";
        String path = "";
        if (url.startsWith("/")) {
            path = url;
        } else if (url.startsWith("file://")) {
            String parsed = Uri.parse(url).getPath();
            path = parsed == null ? "" : parsed;
        } else if (url.startsWith("data:")) {
            int comma = url.indexOf(',');
            if (comma >= 0) {
                try {
                    path = writeImageCache(Base64.decode(url.substring(comma + 1), Base64.DEFAULT));
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        return path.isEmpty() ? "" : "<img>" + path + "</img>";
    }

    @NonNull
    private String writeImageCache(@NonNull byte[] bytes) {
        try {
            File dir = new File(appContext.getCacheDir(), "tai-mnn-images");
            if (!dir.isDirectory() && !dir.mkdirs()) return "";
            File file = new File(dir, "img-" + System.currentTimeMillis() + "-" + bytes.length + ".bin");
            try (FileOutputStream out = new FileOutputStream(file)) {
                out.write(bytes);
            }
            return file.getAbsolutePath();
        } catch (Exception ignored) {
            return "";
        }
    }

    /**
     * MNN audio/omni models consume audio as inline {@code <audio>path</audio>} prompt markup.
     * OpenAI base64 audio is materialised under the app cache; local paths and file URLs pass
     * straight through.
     */
    @NonNull
    private String mnnAudioMarkup(@NonNull JSONObject part) {
        JSONObject inputAudio = part.optJSONObject("input_audio");
        if (inputAudio == null) inputAudio = part.optJSONObject("audio");
        String path = "";
        if (inputAudio != null) {
            String data = inputAudio.optString("data", "").trim();
            if (!data.isEmpty()) {
                try {
                    int comma = data.startsWith("data:") ? data.indexOf(',') : -1;
                    String encoded = comma >= 0 ? data.substring(comma + 1) : data;
                    path = writeAudioCache(Base64.decode(encoded, Base64.DEFAULT), inputAudio.optString("format", "wav"));
                } catch (IllegalArgumentException ignored) {
                }
            }
            if (path.isEmpty()) path = audioPath(inputAudio.optString("url", ""), inputAudio.optString("format", "wav"));
        }
        if (path.isEmpty()) {
            Object audioUrl = part.opt("audio_url");
            String url = audioUrl instanceof JSONObject
                ? ((JSONObject) audioUrl).optString("url", "")
                : audioUrl == null || JSONObject.NULL.equals(audioUrl) ? "" : String.valueOf(audioUrl);
            path = audioPath(url, part.optString("format", "wav"));
        }
        return path.isEmpty() ? "" : "<audio>" + path + "</audio>";
    }

    @NonNull
    private String audioPath(@Nullable String rawUrl, @Nullable String format) {
        String url = rawUrl == null ? "" : rawUrl.trim();
        if (url.startsWith("/")) return url;
        if (url.startsWith("file://")) {
            String path = Uri.parse(url).getPath();
            return path == null ? "" : path;
        }
        if (url.startsWith("data:")) {
            int comma = url.indexOf(',');
            if (comma >= 0) {
                try {
                    return writeAudioCache(Base64.decode(url.substring(comma + 1), Base64.DEFAULT), format);
                } catch (IllegalArgumentException ignored) {
                }
            }
        }
        return "";
    }

    @NonNull
    private String writeAudioCache(@NonNull byte[] bytes, @Nullable String format) {
        try {
            File dir = new File(appContext.getCacheDir(), "tai-mnn-audio");
            if (!dir.isDirectory() && !dir.mkdirs()) return "";
            String extension = format == null ? "wav" : format.trim().toLowerCase(Locale.ROOT);
            if (!extension.matches("[a-z0-9]{1,8}")) extension = "wav";
            File file = new File(dir, "audio-" + System.currentTimeMillis() + "-" + bytes.length + "." + extension);
            try (FileOutputStream out = new FileOutputStream(file)) {
                out.write(bytes);
            }
            return file.getAbsolutePath();
        } catch (Exception ignored) {
            return "";
        }
    }

    @NonNull
    private JSONArray parseToolCalls(@NonNull String response, @NonNull String generationId) {
        JSONArray calls = new JSONArray();
        int offset = 0;
        while (offset < response.length()) {
            int start = response.indexOf("<tool_call>", offset);
            if (start < 0) break;
            int jsonStart = start + "<tool_call>".length();
            int end = response.indexOf("</tool_call>", jsonStart);
            if (end < 0) break;
            String json = response.substring(jsonStart, end).trim();
            try {
                JSONObject parsed = new JSONObject(json);
                String name = parsed.optString("name", "");
                Object argumentsValue = parsed.opt("arguments");
                JSONObject arguments;
                if (argumentsValue instanceof JSONObject) {
                    arguments = (JSONObject) argumentsValue;
                } else {
                    String argumentsText = argumentsValue == null ? "{}" : String.valueOf(argumentsValue);
                    arguments = argumentsText.trim().isEmpty() ? new JSONObject() : new JSONObject(argumentsText);
                }
                if (!name.isEmpty()) {
                    JSONObject function = new JSONObject();
                    function.put("name", name);
                    function.put("arguments", arguments.toString());
                    JSONObject call = new JSONObject();
                    call.put("id", generationId + "-call-" + (calls.length() + 1));
                    call.put("type", "function");
                    call.put("function", function);
                    calls.put(call);
                }
            } catch (JSONException ignored) {
            }
            offset = end + "</tool_call>".length();
        }
        if (calls.length() == 0) appendJsonToolCall(stripJsonCodeFence(response), generationId, calls);
        return calls;
    }

    /**
     * Qwen and other MNN models often emit a tool call as a fenced ```json {...}``` block rather
     * than inside &lt;tool_call&gt; tags. Unwrap a single leading code fence so the JSON tool-call
     * parser can see the object. Returns the trimmed input unchanged when there is no fence.
     */
    @NonNull
    private String stripJsonCodeFence(@NonNull String response) {
        String trimmed = response.trim();
        if (!trimmed.startsWith("```")) return trimmed;
        int firstNewline = trimmed.indexOf('\n');
        int closingFence = trimmed.lastIndexOf("```");
        if (firstNewline < 0 || closingFence <= firstNewline) return trimmed;
        return trimmed.substring(firstNewline + 1, closingFence).trim();
    }

    private void appendJsonToolCall(
        @NonNull String response,
        @NonNull String generationId,
        @NonNull JSONArray calls
    ) {
        String trimmed = response.trim();
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) return;
        try {
            JSONObject parsed = new JSONObject(trimmed);
            JSONObject function = parsed.optJSONObject("function");
            String name = parsed.optString("name", "");
            Object argumentsValue = parsed.opt("arguments");
            if (function != null) {
                if (name.isEmpty()) name = function.optString("name", "");
                if (argumentsValue == null || JSONObject.NULL.equals(argumentsValue)) {
                    argumentsValue = function.opt("arguments");
                }
            }
            if (name.isEmpty()) return;
            JSONObject arguments;
            if (argumentsValue instanceof JSONObject) {
                arguments = (JSONObject) argumentsValue;
            } else {
                String argumentsText = argumentsValue == null ? "{}" : String.valueOf(argumentsValue);
                arguments = argumentsText.trim().isEmpty() ? new JSONObject() : new JSONObject(argumentsText);
            }
            JSONObject callFunction = new JSONObject();
            callFunction.put("name", name);
            callFunction.put("arguments", arguments.toString());
            JSONObject call = new JSONObject();
            call.put("id", generationId + "-call-" + (calls.length() + 1));
            call.put("type", "function");
            call.put("function", callFunction);
            calls.put(call);
        } catch (JSONException ignored) {
        }
    }

    private boolean responseLooksLikeToolCallJson(@NonNull String response) {
        String trimmed = stripJsonCodeFence(response);
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) return false;
        try {
            JSONObject parsed = new JSONObject(trimmed);
            if (!parsed.optString("name", "").isEmpty() && parsed.has("arguments")) return true;
            JSONObject function = parsed.optJSONObject("function");
            return function != null && !function.optString("name", "").isEmpty()
                && (parsed.has("arguments") || function.has("arguments"));
        } catch (JSONException ignored) {
            return false;
        }
    }

    private void appendRequiredFallbackToolCall(
        @NonNull TaiChatRequest request,
        @NonNull String response,
        @NonNull String generationId,
        @NonNull JSONArray calls
    ) {
        if (request.toolDefinitions.length() == 0 || request.toolChoice == null
            || JSONObject.NULL.equals(request.toolChoice)
            || "auto".equals(String.valueOf(request.toolChoice))
            || "none".equals(String.valueOf(request.toolChoice))) {
            return;
        }
        JSONObject function = requiredFunctionSchema(request);
        if (function == null) return;
        String name = function.optString("name", "");
        if (name.isEmpty()) return;
        try {
            JSONObject callFunction = new JSONObject();
            callFunction.put("name", name);
            callFunction.put("arguments", inferredToolArguments(function, lastUserText(request), response).toString());
            JSONObject call = new JSONObject();
            call.put("id", generationId + "-call-" + (calls.length() + 1));
            call.put("type", "function");
            call.put("function", callFunction);
            calls.put(call);
        } catch (JSONException ignored) {
        }
    }

    @Nullable
    private JSONObject requiredFunctionSchema(@NonNull TaiChatRequest request) {
        String requiredName = "";
        if (request.toolChoice instanceof JSONObject) {
            JSONObject function = ((JSONObject) request.toolChoice).optJSONObject("function");
            requiredName = function == null ? "" : function.optString("name", "");
        }
        for (int i = 0; i < request.toolDefinitions.length(); i++) {
            JSONObject tool = request.toolDefinitions.optJSONObject(i);
            JSONObject function = tool == null ? null : tool.optJSONObject("function");
            if (function == null) continue;
            if (requiredName.isEmpty() || requiredName.equals(function.optString("name", ""))) return function;
        }
        return null;
    }

    @NonNull
    private JSONObject inferredToolArguments(
        @NonNull JSONObject function,
        @NonNull String userText,
        @NonNull String modelResponse
    ) throws JSONException {
        JSONObject arguments = new JSONObject();
        JSONObject parameters = function.optJSONObject("parameters");
        JSONObject properties = parameters == null ? null : parameters.optJSONObject("properties");
        JSONArray required = parameters == null ? null : parameters.optJSONArray("required");
        if (required == null || required.length() == 0 || properties == null) return arguments;
        for (int i = 0; i < required.length(); i++) {
            String key = required.optString(i, "");
            if (key.isEmpty()) continue;
            JSONObject property = properties.optJSONObject(key);
            String type = property == null ? "string" : property.optString("type", "string");
            if ("integer".equals(type)) {
                arguments.put(key, inferredInteger(userText));
            } else if ("number".equals(type)) {
                arguments.put(key, inferredNumber(userText));
            } else if ("boolean".equals(type)) {
                arguments.put(key, inferredBoolean(userText, key));
            } else if ("array".equals(type)) {
                arguments.put(key, new JSONArray());
            } else if ("object".equals(type)) {
                arguments.put(key, new JSONObject());
            } else {
                arguments.put(key, inferredString(userText, modelResponse, key, required.length() == 1));
            }
        }
        return arguments;
    }

    @NonNull
    private String lastUserText(@NonNull TaiChatRequest request) {
        if (request.messagesJson.length() > 0) {
            for (int i = request.messagesJson.length() - 1; i >= 0; i--) {
                JSONObject message = request.messagesJson.optJSONObject(i);
                if (message != null && "user".equals(message.optString("role", "user"))) {
                    return openAiContentText(message.opt("content"));
                }
            }
        }
        return textFromMessage(request.message);
    }

    @NonNull
    private String inferredString(
        @NonNull String userText,
        @NonNull String modelResponse,
        @NonNull String key,
        boolean onlyRequired
    ) {
        String value = extractAfterKey(userText, key);
        if (value.isEmpty() && isLocationKey(key)) value = extractLocation(userText);
        if (value.isEmpty() && onlyRequired) value = cleanupArgumentText(userText);
        if (value.isEmpty()) value = cleanupArgumentText(modelResponse);
        return value;
    }

    @NonNull
    private String extractAfterKey(@NonNull String text, @NonNull String key) {
        String lower = text.toLowerCase(Locale.ROOT);
        String normalizedKey = key.toLowerCase(Locale.ROOT).replace('_', ' ');
        int index = lower.indexOf(normalizedKey);
        if (index < 0) return "";
        return cleanupArgumentText(text.substring(index + normalizedKey.length()));
    }

    @NonNull
    private String extractLocation(@NonNull String text) {
        String lower = text.toLowerCase(Locale.ROOT);
        for (String marker : new String[] {" in ", " for ", " at ", " near "}) {
            int index = lower.lastIndexOf(marker);
            if (index >= 0) return cleanupArgumentText(text.substring(index + marker.length()));
        }
        return "";
    }

    private boolean isLocationKey(@NonNull String key) {
        String normalized = key.toLowerCase(Locale.ROOT);
        return normalized.contains("city") || normalized.contains("location")
            || normalized.contains("place") || normalized.contains("address");
    }

    @NonNull
    private String cleanupArgumentText(@NonNull String text) {
        String value = text.replaceAll("(?i)\\b(use|call|return|only|tool|function|get_weather)\\b", " ")
            .replaceAll("[{}<>\\[\\]\"]", " ")
            .replaceAll("(?i)\\bwith\\b", " ")
            .replaceAll("(?i)\\bthe\\b", " ")
            .replaceAll("\\s+", " ")
            .trim();
        value = value.replaceAll("^[,:;\\-?!.\\s]+", "").replaceAll("[,:;\\-?!.\\s]+$", "");
        return value;
    }

    private int inferredInteger(@NonNull String text) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("-?\\d+").matcher(text);
        return matcher.find() ? Integer.parseInt(matcher.group()) : 0;
    }

    private double inferredNumber(@NonNull String text) {
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("-?\\d+(?:\\.\\d+)?").matcher(text);
        return matcher.find() ? Double.parseDouble(matcher.group()) : 0.0d;
    }

    private boolean inferredBoolean(@NonNull String text, @NonNull String key) {
        String lower = text.toLowerCase(Locale.ROOT);
        return lower.contains(key.toLowerCase(Locale.ROOT)) || lower.contains("true") || lower.contains("yes");
    }

    @NonNull
    private String stripToolCallBlocks(@NonNull String response) {
        return response.replaceAll("(?s)<tool_call>.*?</tool_call>", "");
    }

    @Nullable
    private JSONObject validateRequiredToolChoice(
        @Nullable Object toolChoice,
        @NonNull JSONArray tools,
        @NonNull JSONArray toolCalls
    ) throws JSONException {
        if (tools.length() == 0 || toolChoice == null || JSONObject.NULL.equals(toolChoice)
            || "auto".equals(String.valueOf(toolChoice)) || "none".equals(String.valueOf(toolChoice))) {
            return null;
        }
        if (toolCalls.length() == 0) {
            return error(422, "mnn_required_tool_call_missing",
                "MNN generated no valid tool call for a request that required tool use.");
        }
        if (toolChoice instanceof JSONObject) {
            JSONObject function = ((JSONObject) toolChoice).optJSONObject("function");
            String requiredName = function == null ? "" : function.optString("name", "");
            if (!requiredName.isEmpty()) {
                for (int i = 0; i < toolCalls.length(); i++) {
                    JSONObject call = toolCalls.optJSONObject(i);
                    JSONObject callFunction = call == null ? null : call.optJSONObject("function");
                    if (callFunction != null && requiredName.equals(callFunction.optString("name", ""))) return null;
                }
                return error(422, "mnn_required_tool_call_missing",
                    "MNN generated a tool call, but not the required tool: " + requiredName + ".");
            }
        }
        return null;
    }

    @NonNull
    private String mnnRole(@NonNull Message message) {
        String role = String.valueOf(message.getRole()).toLowerCase(Locale.ROOT);
        if (role.contains("model") || role.contains("assistant")) return "assistant";
        if (role.contains("tool")) return "tool";
        return "user";
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

    @NonNull
    private String mergedConfigJson(@NonNull File config, @NonNull TaiModelSpec modelSpec, @NonNull TaiRuntimeOptions options) throws JSONException {
        JSONObject json = readJsonFile(config);
        File modelDir = config.getParentFile();
        if (modelDir != null && json.optString("tokenizer_file", "").trim().isEmpty()) {
            json.put("tokenizer_file", TaiModelStore.mnnTokenizerFile(modelDir, json));
        }
        if (!json.has("backend_type")) json.put("backend_type", "cpu");
        if (!json.has("thread_num")) json.put("thread_num", 4);
        if (!json.has("precision")) json.put("precision", "low");
        if (!json.has("memory")) json.put("memory", "low");
        if (!json.has("max_all_tokens")) json.put("max_all_tokens", modelSpec.endpointContextWindow);
        else json.put("max_all_tokens", Math.min(json.optInt("max_all_tokens", modelSpec.endpointContextWindow), modelSpec.endpointContextWindow));
        json.remove("max_context_len");
        json.put("prompt_cache", true);
        if (!json.has("max_new_tokens")) json.put("max_new_tokens", modelSpec.defaultMaxOutputTokens);
        if (!json.has("temperature")) json.put("temperature", 0.8d);
        if (!json.has("top_p")) json.put("top_p", 0.9d);
        if (!json.has("top_k")) json.put("top_k", 40);
        if (hasExplicitAccelerator(options)) json.put("backend_type", backendName(options));
        if (options.threadCount != null) json.put("thread_num", Math.max(1, options.threadCount));
        if (options.precision != null) json.put("precision", mnnMode(options.precision, json.optString("precision", "low")));
        if (options.memoryMode != null) json.put("memory", mnnMode(options.memoryMode, json.optString("memory", "low")));
        if (options.contextWindow != null) json.put("max_all_tokens", options.contextWindow);
        if (options.maxTokens != null) json.put("max_new_tokens", options.maxTokens);
        if (options.temperature != null) json.put("temperature", options.temperature);
        if (options.topP != null) json.put("top_p", options.topP);
        if (options.topK != null) json.put("top_k", options.topK);
        applyThinkingOverride(json, options.thinkingEnabled);
        return json.toString();
    }

    @NonNull
    private JSONObject overridesJson(@NonNull TaiRuntimeOptions options) throws JSONException {
        JSONObject json = new JSONObject();
        if (hasExplicitAccelerator(options)) json.put("backend_type", backendName(options));
        if (options.threadCount != null) json.put("thread_num", Math.max(1, options.threadCount));
        if (options.precision != null) json.put("precision", mnnMode(options.precision, "low"));
        if (options.memoryMode != null) json.put("memory", mnnMode(options.memoryMode, "low"));
        if (options.contextWindow != null) json.put("max_all_tokens", options.contextWindow);
        if (options.maxTokens != null) json.put("max_new_tokens", options.maxTokens);
        if (options.temperature != null) json.put("temperature", options.temperature);
        if (options.topP != null) json.put("top_p", options.topP);
        if (options.topK != null) json.put("top_k", options.topK);
        applyThinkingOverride(json, options.thinkingEnabled);
        return json;
    }

    private void applyThinkingOverride(@NonNull JSONObject json, @Nullable Boolean thinkingEnabled) throws JSONException {
        if (thinkingEnabled == null) return;
        JSONObject jinja = json.optJSONObject("jinja");
        if (jinja == null) jinja = new JSONObject();
        JSONObject context = jinja.optJSONObject("context");
        if (context == null) context = new JSONObject();
        context.put("enable_thinking", thinkingEnabled);
        jinja.put("context", context);
        json.put("jinja", jinja);
    }

    @NonNull
    private String cacheKey(@NonNull String value) {
        String key = value.replaceAll("[^A-Za-z0-9._-]", "_");
        return key.isEmpty() ? "model" : key;
    }

    @NonNull
    private String extraConfigJson(@NonNull TaiModelSpec modelSpec) throws JSONException {
        JSONObject json = new JSONObject();
        json.put("is_r1", modelSpec.id.toLowerCase(Locale.ROOT).contains("r1")
            || (modelSpec.architecture != null && modelSpec.architecture.toLowerCase(Locale.ROOT).contains("r1")));
        // The native shim derives use_mmap from whether mmap_dir is non-empty and overwrites
        // any use_mmap/tmp_path in the merged config (llm_session.cpp) — so the directory must
        // be passed here, not in mergedConfigJson.
        File mmapDir = new File(appContext.getCacheDir(), "tai-mnn-mmap/" + cacheKey(modelSpec.id));
        if (!mmapDir.isDirectory()) mmapDir.mkdirs();
        json.put("mmap_dir", mmapDir.getAbsolutePath());
        json.put("keep_history", false);
        json.put("prompt_cache", true);
        return json.toString();
    }

    @NonNull
    private String backendName(@Nullable TaiRuntimeOptions options) {
        String accelerator = options == null ? null : options.accelerator;
        if (accelerator == null || accelerator.trim().isEmpty() || "auto".equalsIgnoreCase(accelerator)) return "cpu";
        if ("opencl".equalsIgnoreCase(accelerator) || "gpu".equalsIgnoreCase(accelerator)) return "opencl";
        return "cpu";
    }

    private boolean hasExplicitAccelerator(@NonNull TaiRuntimeOptions options) {
        return options.accelerator != null
            && !options.accelerator.trim().isEmpty()
            && !"auto".equalsIgnoreCase(options.accelerator);
    }

    private int threadCount(@NonNull TaiRuntimeOptions options) {
        int fallback = Math.max(1, Runtime.getRuntime().availableProcessors() / 2);
        return options.threadCount == null ? fallback : Math.max(1, options.threadCount);
    }

    @NonNull
    private String mnnMode(@Nullable String value, @NonNull String fallback) {
        if (value == null || value.trim().isEmpty() || "auto".equalsIgnoreCase(value)) return fallback;
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if ("normal".equals(normalized) || "high".equals(normalized) || "low".equals(normalized)) return normalized;
        return fallback;
    }

    @NonNull
    private JSONObject metricsJson(@NonNull HashMap<String, Object> metrics) throws JSONException {
        JSONObject json = new JSONObject();
        for (String key : metrics.keySet()) {
            Object value = metrics.get(key);
            json.put(key, value == null ? JSONObject.NULL : value);
        }
        return json;
    }

    @NonNull
    private JSONObject safeJson(@Nullable String value) {
        if (value == null || value.trim().isEmpty()) return new JSONObject();
        try {
            return new JSONObject(value);
        } catch (JSONException e) {
            return new JSONObject();
        }
    }

    @NonNull
    private JSONObject readJsonFile(@NonNull File file) throws JSONException {
        try (BufferedInputStream input = new BufferedInputStream(new FileInputStream(file));
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
            return new JSONObject(output.toString(StandardCharsets.UTF_8.name()));
        } catch (Exception e) {
            throw new JSONException("Could not read MNN config: " + message(e));
        }
    }

    @Nullable
    private JSONObject validateMnnPackage(@NonNull File config) throws JSONException {
        File modelDir = config.getParentFile();
        if (modelDir == null) {
            return error(404, "model_file_not_readable", "MNN model config has no parent directory.");
        }
        JSONObject json;
        try {
            json = readJsonFile(config);
        } catch (JSONException e) {
            JSONObject error = error(404, "model_file_not_readable", e.getMessage());
            error.put("path", config.getAbsolutePath());
            return error;
        }
        JSONObject missing = missingMnnSidecar(modelDir, json, "llm_model", "llm.mnn");
        if (missing != null) return missing;
        missing = missingMnnSidecar(modelDir, json, "llm_weight", "llm.mnn.weight");
        if (missing != null) return missing;
        // Qwen3-VL/eagle packages omit tokenizer_file and ship the conventional tokenizer.txt.
        String tokenizer = TaiModelStore.mnnTokenizerFile(modelDir, json);
        if (tokenizer.trim().isEmpty()) {
            JSONObject error = error(404, "model_file_not_readable", "MNN config is missing tokenizer_file.");
            error.put("missingFilename", "tokenizer_file");
            error.put("path", config.getAbsolutePath());
            return error;
        }
        return missingMnnSidecar(modelDir, json, "tokenizer_file", tokenizer);
    }

    @Nullable
    private JSONObject missingMnnSidecar(
        @NonNull File modelDir,
        @NonNull JSONObject config,
        @NonNull String key,
        @NonNull String fallback
    ) throws JSONException {
        String fileName = config.optString(key, fallback);
        if (fileName == null || fileName.trim().isEmpty()) fileName = fallback;
        File file = new File(modelDir, fileName);
        if (file.isFile() && file.canRead()) return null;
        JSONObject error = error(404, "model_file_not_readable", "MNN package sidecar is missing or unreadable: " + fileName);
        error.put("missingFilename", fileName);
        error.put("path", file.getAbsolutePath());
        return error;
    }

    @NonNull
    private JSONObject stateEnvelopeLocked(boolean ok) throws JSONException {
        JSONObject data = new JSONObject();
        data.put("ok", ok);
        data.put("runtime", TaiModelSpec.BACKEND_MNN_LLM);
        data.put("state", getState().toJson());
        return data;
    }

    @NonNull
    private JSONObject error(int statusCode, @NonNull String code, @NonNull String message) throws JSONException {
        synchronized (this) {
            return errorLocked(statusCode, code, message);
        }
    }

    @NonNull
    private JSONObject errorLocked(int statusCode, @NonNull String code, @NonNull String message) throws JSONException {
        JSONObject data = stateEnvelopeLocked(false);
        data.put("error", code);
        data.put("message", message);
        data.put("_statusCode", statusCode);
        return data;
    }

    @NonNull
    private String message(@NonNull Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.trim().isEmpty() ? throwable.getClass().getSimpleName() : message;
    }
}
