package com.termux.ai;

import com.google.ai.edge.litertlm.Content;
import com.google.ai.edge.litertlm.Contents;
import com.google.ai.edge.litertlm.ToolCall;
import com.google.ai.edge.litertlm.ToolProvider;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Test;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TaiOpenAiCompatibilityTest {

    @Test
    public void toolProviders_acceptOpenAiFunctionSchema() throws Exception {
        JSONObject function = new JSONObject();
        function.put("name", "show_map");
        function.put("description", "Shows a location on the map.");
        function.put("parameters", new JSONObject()
            .put("type", "object")
            .put("properties", new JSONObject().put("query", new JSONObject().put("type", "string")))
            .put("required", new JSONArray().put("query")));
        JSONArray tools = new JSONArray().put(new JSONObject()
            .put("type", "function")
            .put("function", function));

        List<ToolProvider> providers = TaiManager.toolProviders(tools, "auto");

        assertEquals(1, providers.size());
    }

    @Test
    public void assistantToolCalls_roundTripIntoLiteRtHistory() throws Exception {
        JSONObject assistant = new JSONObject();
        assistant.put("role", "assistant");
        assistant.put("tool_calls", new JSONArray().put(new JSONObject()
            .put("id", "call_123")
            .put("type", "function")
            .put("function", new JSONObject()
                .put("name", "create_calendar_event")
                .put("arguments", "{\"title\":\"Lunch\",\"details\":{\"hour\":13}}"))));
        Map<String, String> names = new LinkedHashMap<>();

        List<ToolCall> calls = TaiManager.toolCallsFromAssistant(assistant, names);

        assertEquals(1, calls.size());
        assertEquals("create_calendar_event", calls.get(0).getName());
        assertEquals("Lunch", calls.get(0).getArguments().get("title"));
        assertTrue(calls.get(0).getArguments().get("details") instanceof Map);
        assertEquals("create_calendar_event", names.get("call_123"));
    }

    @Test
    public void oneShotRequests_doNotReuseConversationHistory() {
        TaiChatRequest request = TaiChatRequest.oneShot("system", "hello");

        assertFalse(request.reusableConversation);
        assertTrue(request.initialMessages.isEmpty());
        assertEquals("hello", request.message.toString());
    }

    @Test
    public void openAiModels_returnsStrictListShape() throws Exception {
        JSONObject taiModels = new JSONObject()
            .put("ok", true)
            .put("models", new JSONArray()
                .put(new JSONObject().put("id", "gemma").put("backend", "litert-lm")
                    .put("endpointContextWindow", 16384)
                    .put("capabilities", new JSONArray()
                        .put("text_chat")
                        .put("image_input")
                        .put("audio_input")
                        .put("tool_use")))
                .put(new JSONObject().put("id", "gemma").put("backend", "litert-lm")
                    .put("endpointContextWindow", 16384)
                    .put("capabilities", new JSONArray().put("text_chat").put("image_input")))
                .put(new JSONObject().put("id", "qwen")));

        JSONObject response = TaiManager.openAiModelsFromTaiModels(taiModels);

        assertEquals("list", response.getString("object"));
        assertEquals(2, response.getJSONArray("data").length());
        assertEquals(1, response.getJSONArray("models").length());
        assertEquals("gemma", response.getJSONArray("models").getJSONObject(0).getString("slug"));
        assertEquals("local", response.getJSONArray("models").getJSONObject(0).getString("shell_type"));
        assertEquals(16384, response.getJSONArray("models").getJSONObject(0).getInt("context_window"));
        assertEquals("gemma", response.getJSONArray("data").getJSONObject(0).getString("id"));
        assertEquals("model", response.getJSONArray("data").getJSONObject(0).getString("object"));
        assertTrue(response.getJSONArray("data").getJSONObject(0).getJSONArray("_capabilities").toString().contains("image_input"));
        assertTrue(response.getJSONArray("data").getJSONObject(0).getJSONArray("_capabilities").toString().contains("audio_input"));
        assertFalse(response.has("tai"));
        assertFalse(response.getJSONArray("data").getJSONObject(0).has("tai"));
    }

    @Test
    public void openAiModels_marksMnnToolUseAsPromptFallbackAllowsImageAudioFiltersVideo() throws Exception {
        JSONObject taiModels = new JSONObject()
            .put("ok", true)
            .put("models", new JSONArray().put(new JSONObject()
                .put("id", "mnn-tools")
                .put("backend", TaiModelSpec.BACKEND_MNN_LLM)
                .put("capabilities", new JSONArray()
                    .put("text_chat")
                    .put("tool_use")
                    .put("image_input")
                    .put("audio_input")
                    .put("video_input"))));

        JSONObject response = TaiManager.openAiModelsFromTaiModels(taiModels);

        JSONArray capabilities = response.getJSONArray("data").getJSONObject(0).getJSONArray("_capabilities");
        JSONObject model = response.getJSONArray("data").getJSONObject(0);
        assertTrue(contains(capabilities, TaiModelSpec.CAPABILITY_TEXT_CHAT));
        assertTrue(contains(capabilities, TaiModelSpec.CAPABILITY_TOOL_USE));
        // MNN multimodal models accept image/audio path markup; video remains unsupported.
        assertTrue(contains(capabilities, TaiModelSpec.CAPABILITY_IMAGE_INPUT));
        assertTrue(contains(capabilities, TaiModelSpec.CAPABILITY_AUDIO_INPUT));
        assertFalse(contains(capabilities, TaiModelSpec.CAPABILITY_VIDEO_INPUT));
        assertEquals(TaiModelSpec.TOOL_MODE_PROMPT_FALLBACK, model.getString("_tool_mode"));
    }

    @Test
    public void openAiModels_capabilitiesEqualEndpointCapabilitiesNotSourceClaims() throws Exception {
        JSONObject taiModels = new JSONObject()
            .put("ok", true)
            .put("models", new JSONArray().put(new JSONObject()
                .put("id", "gemma-claimed-thinking")
                .put("backend", TaiModelSpec.BACKEND_LITERT_LM)
                .put("format", TaiModelSpec.FORMAT_LITERTLM)
                .put("sourceCapabilities", new JSONArray()
                    .put(TaiModelSpec.CAPABILITY_TEXT_CHAT)
                    .put(TaiModelSpec.CAPABILITY_IMAGE_INPUT)
                    .put(TaiModelSpec.CAPABILITY_LLM_THINKING))
                .put("endpointCapabilities", new JSONArray()
                    .put(TaiModelSpec.CAPABILITY_TEXT_CHAT)
                    .put(TaiModelSpec.CAPABILITY_IMAGE_INPUT))));

        JSONObject model = TaiManager.openAiModelsFromTaiModels(taiModels)
            .getJSONArray("data").getJSONObject(0);

        assertEquals(model.getJSONArray("_endpoint_capabilities").toString(),
            model.getJSONArray("_capabilities").toString());
        assertTrue(contains(model.getJSONArray("_source_capabilities"), TaiModelSpec.CAPABILITY_LLM_THINKING));
        assertFalse(contains(model.getJSONArray("_capabilities"), TaiModelSpec.CAPABILITY_LLM_THINKING));
    }

    @Test
    public void pruneAudioInputFromResponse_hidesAudioForFailedModelOnly() throws Exception {
        JSONObject response = TaiManager.openAiModelsFromTaiModels(new JSONObject()
            .put("ok", true)
            .put("models", new JSONArray()
                .put(new JSONObject().put("id", "gemma-e4b-failed").put("backend", TaiModelSpec.BACKEND_LITERT_LM)
                    .put("format", TaiModelSpec.FORMAT_LITERTLM)
                    .put("endpointCapabilities", new JSONArray()
                        .put(TaiModelSpec.CAPABILITY_TEXT_CHAT)
                        .put(TaiModelSpec.CAPABILITY_AUDIO_INPUT)
                        .put(TaiModelSpec.CAPABILITY_IMAGE_INPUT)))
                .put(new JSONObject().put("id", "gemma-e2b-ok").put("backend", TaiModelSpec.BACKEND_LITERT_LM)
                    .put("format", TaiModelSpec.FORMAT_LITERTLM)
                    .put("endpointCapabilities", new JSONArray()
                        .put(TaiModelSpec.CAPABILITY_TEXT_CHAT)
                        .put(TaiModelSpec.CAPABILITY_AUDIO_INPUT)))));

        LinkedHashSet<String> failed = new LinkedHashSet<>();
        failed.add("gemma-e4b-failed");
        JSONObject pruned = TaiManager.pruneAudioInputFromResponse(response, failed);

        JSONArray data = pruned.getJSONArray("data");
        JSONObject e4b = data.getJSONObject(0);
        JSONObject e2b = data.getJSONObject(1);
        assertFalse(contains(e4b.getJSONArray("_capabilities"), TaiModelSpec.CAPABILITY_AUDIO_INPUT));
        assertFalse(contains(e4b.getJSONArray("_endpoint_capabilities"), TaiModelSpec.CAPABILITY_AUDIO_INPUT));
        assertTrue(contains(e4b.getJSONArray("_capabilities"), TaiModelSpec.CAPABILITY_IMAGE_INPUT));
        assertTrue(contains(e2b.getJSONArray("_capabilities"), TaiModelSpec.CAPABILITY_AUDIO_INPUT));
        assertEquals(e2b.getJSONArray("_endpoint_capabilities").toString(),
            e2b.getJSONArray("_capabilities").toString());
    }

    @Test
    public void messageContentToContents_acceptsLiteRtOpenAiImageAndAudioParts() throws Exception {
        TaiModelSpec spec = spec("gemma-multimodal", TaiModelSpec.BACKEND_LITERT_LM, "image_input", "audio_input");
        JSONArray parts = new JSONArray()
            .put(new JSONObject().put("type", "text").put("text", "describe this"))
            .put(new JSONObject()
                .put("type", "image_url")
                .put("image_url", new JSONObject().put("url", "data:image/png;base64,AQID")))
            .put(new JSONObject()
                .put("type", "input_audio")
                .put("input_audio", new JSONObject().put("data", "BAUG").put("format", "wav")));

        Contents contents = TaiManager.messageContentToContents(parts, spec);

        assertEquals(3, contents.getContents().size());
        assertTrue(contents.getContents().get(0) instanceof Content.Text);
        assertTrue(contents.getContents().get(1) instanceof Content.ImageBytes);
        assertTrue(contents.getContents().get(2) instanceof Content.AudioBytes);
    }

    @Test
    public void messageContentToContents_rejectsMediaForTextOnlyModel() throws Exception {
        TaiModelSpec spec = spec("text-only", TaiModelSpec.BACKEND_LITERT_LM);
        JSONArray parts = new JSONArray().put(new JSONObject()
            .put("type", "image_url")
            .put("image_url", new JSONObject().put("url", "data:image/png;base64,AQID")));

        try {
            TaiManager.messageContentToContents(parts, spec);
        } catch (JSONException e) {
            assertTrue(e.getMessage().startsWith("capability_not_supported:"));
            return;
        }
        throw new AssertionError("Expected capability_not_supported");
    }

    @Test
    public void cancellationDetection_acceptsLiteRtJniCancellationMessage() {
        RuntimeException error = new RuntimeException(
            "Failed to call nativeSendMessage: CANCELLED: Process cancelled.");

        assertTrue(LiteRtTaiRuntime.isCancellation(error));
        assertFalse(LiteRtTaiRuntime.isCancellation(new RuntimeException("GPU initialization failed")));
    }

    @Test
    public void liteRtGeneratedTokenCount_usesNativeDeltaAfterFirstStreamingToken() {
        assertEquals(1, LiteRtTaiRuntime.generatedTokenCount(120, 120));
        assertEquals(8, LiteRtTaiRuntime.generatedTokenCount(120, 127));
        assertEquals(1, LiteRtTaiRuntime.generatedTokenCount(120, 119));
    }

    @Test
    public void liteRtVisionBackend_autoUsesGpuOnlyWhenEligibleAndNotKnownFailed() {
        assertTrue(LiteRtTaiRuntime.useGpuVision(null, true, true, false));
        assertFalse(LiteRtTaiRuntime.useGpuVision(null, false, true, false));
        assertFalse(LiteRtTaiRuntime.useGpuVision(null, true, false, false));
        assertFalse(LiteRtTaiRuntime.useGpuVision(null, true, true, true));
    }

    @Test
    public void liteRtVisionBackend_honorsExplicitAccelerator() {
        assertTrue(LiteRtTaiRuntime.useGpuVision("gpu", false, false, true));
        assertFalse(LiteRtTaiRuntime.useGpuVision("cpu", true, true, false));
    }

    @Test
    public void messageContentToContents_acceptsImageForMnnVlModel() throws Exception {
        TaiModelSpec spec = spec("qwen-vl-mnn", TaiModelSpec.BACKEND_MNN_LLM, "image_input");
        assertTrue(spec.capabilities.contains(TaiModelSpec.CAPABILITY_IMAGE_INPUT));
        JSONArray parts = new JSONArray()
            .put(new JSONObject().put("type", "text").put("text", "describe"))
            .put(new JSONObject().put("type", "image_url")
                .put("image_url", new JSONObject().put("url", "data:image/png;base64,AQID")));

        Contents contents = TaiManager.messageContentToContents(parts, spec);

        assertEquals(2, contents.getContents().size());
        assertTrue(contents.getContents().get(1) instanceof Content.ImageBytes);
    }

    @Test
    public void endpointCapabilities_videoStaysSourceOnlyForBothBackends() {
        LinkedHashSet<String> declared = new LinkedHashSet<>();
        declared.add(TaiModelSpec.CAPABILITY_TEXT_CHAT);
        declared.add(TaiModelSpec.CAPABILITY_VIDEO_INPUT);
        declared.add(TaiModelSpec.CAPABILITY_IMAGE_INPUT);

        LinkedHashSet<String> liteRt = TaiModelSpec.endpointCapabilitiesFor(
            "v", TaiModelSpec.BACKEND_LITERT_LM, TaiModelSpec.FORMAT_LITERTLM, declared, null);
        LinkedHashSet<String> mnn = TaiModelSpec.endpointCapabilitiesFor(
            "v", TaiModelSpec.BACKEND_MNN_LLM, TaiModelSpec.FORMAT_MNN, declared, null);

        assertFalse(liteRt.contains(TaiModelSpec.CAPABILITY_VIDEO_INPUT));
        assertFalse(mnn.contains(TaiModelSpec.CAPABILITY_VIDEO_INPUT));
        assertTrue(mnn.contains(TaiModelSpec.CAPABILITY_IMAGE_INPUT));
    }

    @Test
    public void endpointCapabilities_embeddingOnlyDoesNotBecomeChatAndMnnDoesNotClaimEmbeddings() {
        LinkedHashSet<String> declared = new LinkedHashSet<>();
        declared.add(TaiModelSpec.CAPABILITY_TEXT_EMBEDDINGS);

        LinkedHashSet<String> liteRt = TaiModelSpec.endpointCapabilitiesFor(
            "embeddinggemma-300m", TaiModelSpec.BACKEND_LITERT_LM, TaiModelSpec.FORMAT_LITERTLM, declared, "/models/model.tflite");
        LinkedHashSet<String> mnn = TaiModelSpec.endpointCapabilitiesFor(
            "qwen3-embedding", TaiModelSpec.BACKEND_MNN_LLM, TaiModelSpec.FORMAT_MNN, declared, "/models/config.json");

        assertTrue(liteRt.contains(TaiModelSpec.CAPABILITY_TEXT_EMBEDDINGS));
        assertFalse(mnn.contains(TaiModelSpec.CAPABILITY_TEXT_EMBEDDINGS));
        assertFalse(liteRt.contains(TaiModelSpec.CAPABILITY_TEXT_CHAT));
        assertFalse(mnn.contains(TaiModelSpec.CAPABILITY_TEXT_CHAT));
    }

    @Test
    public void openAiModels_codexDiscoveryRequiresToolsAndSixteenKContext() throws Exception {
        JSONArray models = new JSONArray()
            .put(new JSONObject().put("id", "short-tools")
                .put("endpointContextWindow", 8192)
                .put("capabilities", new JSONArray().put("text_chat").put("tool_use")))
            .put(new JSONObject().put("id", "long-chat")
                .put("endpointContextWindow", 32768)
                .put("capabilities", new JSONArray().put("text_chat")))
            .put(new JSONObject().put("id", "coder")
                .put("endpointContextWindow", 16384)
                .put("capabilities", new JSONArray().put("text_chat").put("tool_use").put("code")));

        JSONArray codex = TaiManager.openAiModelsFromTaiModels(new JSONObject().put("models", models))
            .getJSONArray("models");

        assertEquals(1, codex.length());
        assertEquals("coder", codex.getJSONObject(0).getString("slug"));
    }

    @Test
    public void openAiModels_doesNotVerifyCapabilitiesFromCatalogIdAlone() throws Exception {
        JSONObject unverified = new JSONObject().put("id", TaiModelRegistry.MODEL_MOBILE_ACTIONS_270M)
            .put("capabilities", new JSONArray().put("text_chat"));
        JSONObject verified = new JSONObject(unverified.toString()).put("id", "verified-copy")
            .put("capabilitiesVerified", true).put("capabilitySource", "catalog");

        JSONArray data = TaiManager.openAiModelsFromTaiModels(new JSONObject()
            .put("models", new JSONArray().put(unverified).put(verified))).getJSONArray("data");

        assertFalse(data.getJSONObject(0).getBoolean("_capabilities_verified"));
        assertTrue(data.getJSONObject(1).getBoolean("_capabilities_verified"));
    }

    @Test
    public void endpointCapabilities_rawTfliteCannotAdvertiseChat() {
        LinkedHashSet<String> declared = new LinkedHashSet<>();
        declared.add(TaiModelSpec.CAPABILITY_TEXT_CHAT);
        declared.add(TaiModelSpec.CAPABILITY_TEXT_EMBEDDINGS);

        LinkedHashSet<String> endpoint = TaiModelSpec.endpointCapabilitiesFor(
            "import", TaiModelSpec.BACKEND_LITERT_LM, TaiModelSpec.FORMAT_LITERTLM,
            declared, "/models/model.tflite");

        assertFalse(endpoint.contains(TaiModelSpec.CAPABILITY_TEXT_CHAT));
        assertTrue(endpoint.contains(TaiModelSpec.CAPABILITY_TEXT_EMBEDDINGS));
    }

    @Test
    public void openAiModels_preservesExplicitlyEmptyEndpointCapabilities() throws Exception {
        JSONObject model = new JSONObject().put("id", "unsupported-mnn-embedder")
            .put("backend", TaiModelSpec.BACKEND_MNN_LLM)
            .put("sourceCapabilities", new JSONArray().put(TaiModelSpec.CAPABILITY_TEXT_EMBEDDINGS))
            .put("endpointCapabilities", new JSONArray());

        JSONObject exposed = TaiManager.openAiModelsFromTaiModels(
            new JSONObject().put("models", new JSONArray().put(model)))
            .getJSONArray("data").getJSONObject(0);

        assertEquals(0, exposed.getJSONArray("_capabilities").length());
    }

    @Test
    public void automaticTools_degradeToTextForUnsupportedShortContextAndPromptFallbackModels() throws Exception {
        JSONObject tools = new JSONObject().put("tools", new JSONArray().put(new JSONObject()
            .put("type", "function").put("function", new JSONObject().put("name", "create"))))
            .put("tool_choice", "auto");

        JSONObject unsupported = new JSONObject(tools.toString());
        assertTrue(TaiManager.omitAutomaticToolsForCompatibility(unsupported,
            toolSpec("phi", 4096, false, null)));
        assertFalse(unsupported.has("tools"));
        assertEquals("none", unsupported.getString("tool_choice"));

        JSONObject shortNative = new JSONObject(tools.toString());
        assertTrue(TaiManager.omitAutomaticToolsForCompatibility(shortNative,
            toolSpec("gemma", 4096, true, TaiModelSpec.TOOL_MODE_NATIVE)));

        JSONObject promptFallback = new JSONObject(tools.toString());
        assertTrue(TaiManager.omitAutomaticToolsForCompatibility(promptFallback,
            toolSpec("qwen", 16384, true, TaiModelSpec.TOOL_MODE_PROMPT_FALLBACK)));

        JSONObject reliableNative = new JSONObject(tools.toString());
        assertFalse(TaiManager.omitAutomaticToolsForCompatibility(reliableNative,
            toolSpec("native-agent", 16384, true, TaiModelSpec.TOOL_MODE_NATIVE)));
        assertTrue(reliableNative.has("tools"));
    }

    @Test
    public void explicitRequiredTools_doNotSilentlyDegrade() throws Exception {
        JSONObject request = new JSONObject().put("tools", new JSONArray().put(new JSONObject()
            .put("type", "function").put("function", new JSONObject().put("name", "create"))))
            .put("tool_choice", "required");

        assertFalse(TaiManager.omitAutomaticToolsForCompatibility(request,
            toolSpec("phi", 4096, false, null)));
        assertTrue(request.has("tools"));
    }

    private static TaiModelSpec toolSpec(String id, int context, boolean tools, String toolMode) {
        LinkedHashSet<String> source = new LinkedHashSet<>();
        source.add(TaiModelSpec.CAPABILITY_TEXT_CHAT);
        if (tools) source.add(TaiModelSpec.CAPABILITY_TOOL_USE);
        return new TaiModelSpec(id, id, "test", "test", "/models/model.litertlm", "test", 0L,
            source, false, null, TaiModelSpec.BACKEND_LITERT_LM, TaiModelSpec.FORMAT_LITERTLM,
            null, null, context, context, 1024, 0, null, source, toolMode);
    }

    private static TaiModelSpec spec(String id, String backend, String... capabilities) {
        LinkedHashSet<String> caps = new LinkedHashSet<>();
        caps.add("text_chat");
        caps.addAll(Arrays.asList(capabilities));
        return new TaiModelSpec(
            id,
            id,
            "test",
            "test",
            backend.equals(TaiModelSpec.BACKEND_MNN_LLM) ? "/models/config.json" : "/models/model.litertlm",
            "test",
            0L,
            caps,
            false,
            null,
            backend,
            backend.equals(TaiModelSpec.BACKEND_MNN_LLM) ? TaiModelSpec.FORMAT_MNN : TaiModelSpec.FORMAT_LITERTLM,
            null,
            null,
            4096,
            0,
            null
        );
    }

    private static boolean contains(JSONArray array, String value) {
        for (int i = 0; i < array.length(); i++) {
            if (value.equals(array.optString(i))) return true;
        }
        return false;
    }
}
