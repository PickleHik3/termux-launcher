package com.termux.ai;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** The remote client's pure rules: host rule, body sanitiser, retries, parsing, SSE, redaction. */
public class TaiRemoteClientTest {

    // ---------------------------------------------------------------- host rule

    @Test
    public void https_isAllowedToAnyHost() {
        assertEquals(TaiRemoteClient.UrlVerdict.OK_ENCRYPTED, TaiRemoteClient.checkUrl("https://api.openai.com/v1"));
        assertEquals(TaiRemoteClient.UrlVerdict.OK_ENCRYPTED, TaiRemoteClient.checkUrl("https://openrouter.ai/api/v1/"));
        assertFalse(TaiRemoteClient.isUnencrypted("https://api.openai.com/v1"));
    }

    @Test
    public void http_isAllowedToThisPhone() {
        for (String url : Arrays.asList("http://localhost:11434/v1", "http://127.0.0.1:11434/v1",
            "http://127.1.2.3/v1", "http://[::1]:8080/v1")) {
            assertEquals(url, TaiRemoteClient.UrlVerdict.OK_UNENCRYPTED, TaiRemoteClient.checkUrl(url));
            assertTrue(url, TaiRemoteClient.isUnencrypted(url));
        }
    }

    @Test
    public void http_isAllowedToEachPrivateRange() {
        for (String url : Arrays.asList(
            "http://10.0.0.5:11434/v1", "http://10.255.255.255/v1",
            "http://172.16.0.1/v1", "http://172.31.255.254/v1",
            "http://192.168.1.20:11434/v1",
            "http://100.64.0.1/v1", "http://100.127.255.254/v1",
            "http://desktop.local:1234/v1")) {
            assertEquals(url, TaiRemoteClient.UrlVerdict.OK_UNENCRYPTED, TaiRemoteClient.checkUrl(url));
        }
    }

    @Test
    public void http_toPublicHosts_isRefused() {
        for (String url : Arrays.asList(
            "http://api.openai.com/v1", "http://8.8.8.8/v1",
            "http://172.15.0.1/v1", "http://172.32.0.1/v1",
            "http://192.169.1.1/v1", "http://100.63.255.255/v1", "http://100.128.0.1/v1",
            "http://11.0.0.1/v1", "http://local/v1", "http://evil.local.example.com/v1",
            "http://10.0.0.1.example.com/v1")) {
            assertEquals(url, TaiRemoteClient.UrlVerdict.PUBLIC_HTTP, TaiRemoteClient.checkUrl(url));
            assertFalse(url, TaiRemoteClient.isUnencrypted(url));
        }
    }

    @Test
    public void otherSchemes_areRefused() {
        assertEquals(TaiRemoteClient.UrlVerdict.BAD_SCHEME, TaiRemoteClient.checkUrl("file:///sdcard/models"));
        assertEquals(TaiRemoteClient.UrlVerdict.BAD_SCHEME, TaiRemoteClient.checkUrl("ftp://192.168.1.2/v1"));
        assertEquals(TaiRemoteClient.UrlVerdict.BAD_SCHEME, TaiRemoteClient.checkUrl("content://x/y"));
        assertFalse(TaiRemoteClient.checkUrl("file:///etc/hosts").allowed());
    }

    @Test
    public void malformedAddresses_areRefused() {
        assertEquals(TaiRemoteClient.UrlVerdict.MALFORMED, TaiRemoteClient.checkUrl(""));
        assertEquals(TaiRemoteClient.UrlVerdict.MALFORMED, TaiRemoteClient.checkUrl(null));
        assertEquals(TaiRemoteClient.UrlVerdict.MALFORMED, TaiRemoteClient.checkUrl("api.openai.com/v1"));
        assertEquals(TaiRemoteClient.UrlVerdict.MALFORMED, TaiRemoteClient.checkUrl("https://"));
    }

    @Test
    public void baseUrl_losesTrailingSlashes() {
        assertEquals("https://x.example/v1", TaiRemoteClient.normalizeBaseUrl("  https://x.example/v1//  "));
        assertEquals("https://x.example/v1/models", TaiRemoteClient.endpoint("https://x.example/v1/", "models"));
    }

    // ---------------------------------------------------------------- body sanitiser

    @Test
    public void sanitize_stripsContextWindowAndTaiKeys() throws Exception {
        JSONObject request = new JSONObject()
            .put("model", "remote/gpt-4o-mini")
            .put("context_window", 8192)
            .put("_tai_feature", "categories")
            .put("_taiTrace", true)
            .put("max_tokens", 100)
            .put("messages", new JSONArray().put(new JSONObject().put("role", "user").put("content", "hi")
                .put("_tai_hidden", 1)));
        JSONObject out = TaiRemoteClient.sanitize(request);
        assertFalse(out.has("context_window"));
        assertFalse(out.has("_tai_feature"));
        assertFalse(out.has("_taiTrace"));
        assertFalse(out.getJSONArray("messages").getJSONObject(0).has("_tai_hidden"));
        assertEquals("gpt-4o-mini", out.getString("model"));
        assertEquals(100, out.getInt("max_tokens"));
        assertFalse(out.has("response_format"));
        // The caller's object is untouched.
        assertTrue(request.has("context_window"));
    }

    @Test
    public void sanitize_addsJsonModeOnlyWhenFlagged() throws Exception {
        JSONObject flagged = new JSONObject().put("model", "m").put(TaiRemoteClient.FLAG_JSON_OBJECT, true);
        JSONObject out = TaiRemoteClient.sanitize(flagged);
        assertEquals("json_object", out.getJSONObject("response_format").getString("type"));
        assertFalse(out.has(TaiRemoteClient.FLAG_JSON_OBJECT));

        JSONObject plain = TaiRemoteClient.sanitize(new JSONObject().put("model", "m"));
        assertFalse(plain.has("response_format"));
    }

    @Test
    public void retry_movesMaxTokensOnA400NamingIt() throws Exception {
        JSONObject sent = new JSONObject().put("model", "o4-mini").put("max_tokens", 64);
        String error = "{\"error\":{\"message\":\"Unsupported parameter: 'max_tokens' is not supported with this model. "
            + "Use 'max_completion_tokens' instead.\",\"code\":\"unsupported_parameter\"}}";
        JSONObject retry = TaiRemoteClient.retryBody(sent, 400, error);
        assertNotNull(retry);
        assertFalse(retry.has("max_tokens"));
        assertEquals(64, retry.getInt("max_completion_tokens"));
        // Once moved, the same error earns no second retry.
        assertNull(TaiRemoteClient.retryBody(retry, 400, error));
    }

    @Test
    public void retry_dropsResponseFormatOnA400NamingIt() throws Exception {
        JSONObject sent = new JSONObject().put("model", "m")
            .put("response_format", new JSONObject().put("type", "json_object"));
        JSONObject retry = TaiRemoteClient.retryBody(sent, 400, "{\"error\":{\"message\":\"response_format is not supported\"}}");
        assertNotNull(retry);
        assertFalse(retry.has("response_format"));
    }

    @Test
    public void retry_isNotOfferedForOtherFailures() throws Exception {
        JSONObject sent = new JSONObject().put("model", "m").put("max_tokens", 64);
        assertNull(TaiRemoteClient.retryBody(sent, 401, "max_tokens"));
        assertNull(TaiRemoteClient.retryBody(sent, 400, "{\"error\":{\"message\":\"Invalid model\"}}"));
        assertNull(TaiRemoteClient.retryBody(new JSONObject().put("model", "m"), 400, "max_tokens"));
        assertNull(TaiRemoteClient.retryBody(sent, 400, null));
    }

    // ---------------------------------------------------------------- models list

    @Test
    public void models_readsOpenAiShape() {
        String body = "{\"object\":\"list\",\"data\":[{\"id\":\"gpt-4o\"},{\"id\":\"gpt-4o-mini\"},{\"id\":\"gpt-4o\"},{\"id\":\"\"}]}";
        assertEquals(Arrays.asList("gpt-4o", "gpt-4o-mini"), TaiRemoteClient.parseModelIds(body));
    }

    @Test
    public void models_readsOllamaShapeAndBareArrays() {
        assertEquals(Arrays.asList("llama3.2:3b"),
            TaiRemoteClient.parseModelIds("{\"models\":[{\"name\":\"llama3.2:3b\"}]}"));
        assertEquals(Arrays.asList("a", "b"), TaiRemoteClient.parseModelIds("[\"a\",{\"id\":\"b\"}]"));
    }

    @Test
    public void models_garbageIsAnEmptyList() {
        assertTrue(TaiRemoteClient.parseModelIds("<html>Not found</html>").isEmpty());
        assertTrue(TaiRemoteClient.parseModelIds("").isEmpty());
        assertTrue(TaiRemoteClient.parseModelIds(null).isEmpty());
        assertTrue(TaiRemoteClient.parseModelIds("{\"data\":\"nope\"}").isEmpty());
    }

    @Test
    public void models_missingEndpointMeansFreeText() {
        assertTrue(TaiRemoteClient.modelListMissing(404));
        assertTrue(TaiRemoteClient.modelListMissing(401));
        assertFalse(TaiRemoteClient.modelListMissing(500));
        assertFalse(TaiRemoteClient.modelListMissing(200));
    }

    // ---------------------------------------------------------------- SSE

    @Test
    public void sse_emitsEachEventAtItsBlankLine() {
        TaiRemoteClient.SseReader reader = new TaiRemoteClient.SseReader();
        assertNull(reader.accept(": keep-alive"));
        assertNull(reader.accept("event: message"));
        assertNull(reader.accept("data: {\"a\":1}"));
        assertEquals("{\"a\":1}", reader.accept(""));
        assertNull(reader.accept(""));
        assertNull(reader.accept("data:{\"b\":2}"));
        assertEquals("{\"b\":2}", reader.accept(""));
        assertNull(reader.accept("data: [DONE]"));
        assertEquals("[DONE]", reader.finish());
        assertNull(reader.finish());
    }

    @Test
    public void sse_joinsMultiLineData() {
        TaiRemoteClient.SseReader reader = new TaiRemoteClient.SseReader();
        reader.accept("data: {\"a\":");
        reader.accept("data: 1}");
        assertEquals("{\"a\":\n1}", reader.accept(""));
    }

    @Test
    public void deliver_forwardsChunksAndEndsOnDone() throws Exception {
        RecordingSink sink = new RecordingSink();
        assertFalse(TaiRemoteClient.deliver("{\"choices\":[{\"delta\":{\"content\":\"Hi\"}}]}", sink));
        assertFalse(TaiRemoteClient.deliver("not json", sink));
        assertTrue(TaiRemoteClient.deliver("[DONE]", sink));
        assertEquals(1, sink.events.size());
        assertEquals("Hi", sink.events.get(0).getJSONArray("choices").getJSONObject(0)
            .getJSONObject("delta").getString("content"));
        assertEquals(1, sink.done);
    }

    @Test
    public void deliver_mapsAnErrorChunkToTheOpenAiErrorShape() throws Exception {
        RecordingSink sink = new RecordingSink();
        assertTrue(TaiRemoteClient.deliver("{\"error\":{\"message\":\"Rate limited\",\"code\":\"rate_limit\"}}", sink));
        JSONObject event = sink.events.get(0);
        assertFalse(event.getBoolean("ok"));
        assertEquals("Rate limited", event.getJSONObject("error").getString("message"));
        assertEquals("rate_limit", event.getString("code"));
        assertEquals(1, sink.done);
    }

    // ---------------------------------------------------------------- errors, probe

    @Test
    public void errors_useTheServersMessageAndCode() throws Exception {
        TaiRemoteClient client = new TaiRemoteClient(null, "https://api.openai.com/v1", "sk-test-1234567890");
        String body = "{\"error\":{\"message\":\"Incorrect API key provided\",\"type\":\"invalid_request_error\",\"code\":\"invalid_api_key\"}}";
        assertEquals("Incorrect API key provided", client.errorMessage(401, body));
        assertEquals("invalid_api_key", TaiRemoteClient.errorCode(401, body));
        assertEquals("remote_http_502", TaiRemoteClient.errorCode(502, "<html>"));
        JSONObject envelope = TaiManager.openAiError(TaiRemoteClient.source(401, "invalid_api_key", "nope"));
        assertEquals(401, envelope.getInt("_statusCode"));
        assertEquals("invalid_api_key", envelope.getJSONObject("error").getString("code"));
    }

    @Test
    public void errors_neverEchoTheKey() {
        String key = "sk-proj-SECRETSECRET1234";
        TaiRemoteClient client = new TaiRemoteClient(null, "https://api.openai.com/v1", key);
        String message = client.errorMessage(401, "{\"error\":{\"message\":\"Incorrect API key provided: " + key + "\"}}");
        assertFalse(message.contains(key));
    }

    @Test
    public void probe_judgesTheReply() {
        assertEquals(TaiRemoteClient.ImageVerdict.IMAGES,
            TaiRemoteClient.judgeProbe(200, chatReply("Red.")));
        assertEquals(TaiRemoteClient.ImageVerdict.TEXT_ONLY,
            TaiRemoteClient.judgeProbe(200, chatReply("I can't see images; this is required to answer.")));
        assertEquals(TaiRemoteClient.ImageVerdict.TEXT_ONLY,
            TaiRemoteClient.judgeProbe(400, "{\"error\":{\"message\":\"Invalid content type\"}}"));
        assertEquals(TaiRemoteClient.ImageVerdict.TEXT_ONLY,
            TaiRemoteClient.judgeProbe(500, "{\"error\":{\"message\":\"model does not support vision\"}}"));
        assertEquals(TaiRemoteClient.ImageVerdict.UNKNOWN,
            TaiRemoteClient.judgeProbe(401, "{\"error\":{\"message\":\"bad key\"}}"));
        assertEquals(TaiRemoteClient.ImageVerdict.UNKNOWN, TaiRemoteClient.judgeProbe(200, chatReply("")));
    }

    @Test
    public void probe_readsArrayContent() throws Exception {
        JSONObject reply = new JSONObject().put("choices", new JSONArray().put(new JSONObject()
            .put("message", new JSONObject().put("content", new JSONArray()
                .put(new JSONObject().put("type", "text").put("text", "red"))))));
        assertEquals(TaiRemoteClient.ImageVerdict.IMAGES, TaiRemoteClient.judgeProbe(200, reply.toString()));
    }

    // ---------------------------------------------------------------- redaction

    @Test
    public void redact_removesTheKeyAndAuthorizationValues() {
        String key = "sk-or-v1-abcdef0123456789";
        String text = "POST /chat Authorization: Bearer " + key + " {\"authorization\":\"Bearer " + key + "\"} key=" + key
            + " bearer other-token-xyz";
        String redacted = TaiRemoteClient.redact(text, key);
        assertFalse(redacted, redacted.contains(key));
        assertFalse(redacted, redacted.contains("other-token-xyz"));
        assertTrue(redacted.contains(TaiRemoteClient.REDACTED));
    }

    @Test
    public void logLine_namesHostAndStatusButNeverTheKey() {
        String key = "sk-test-0123456789abcdef";
        String line = TaiRemoteClient.formatLogLine("POST", "https://api.openai.com/v1", "chat/completions", 401, 120L, key);
        assertTrue(line, line.contains("api.openai.com"));
        assertTrue(line, line.contains("401"));
        assertFalse(line, line.contains(key));
        String keyInUrl = TaiRemoteClient.formatLogLine("GET", "https://" + key + "@x.example/v1", "models", 0, 0L, key);
        assertFalse(keyInUrl, keyInUrl.contains(key));
    }

    // ---------------------------------------------------------------- helpers

    @NonNull
    private static String chatReply(@NonNull String content) {
        try {
            return new JSONObject().put("choices", new JSONArray().put(new JSONObject()
                .put("message", new JSONObject().put("role", "assistant").put("content", content)))).toString();
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    private static final class RecordingSink implements TaiManager.OpenAiStreamSink {
        final List<JSONObject> events = new ArrayList<>();
        int done;

        @Override
        public void onEvent(@NonNull JSONObject event) {
            events.add(event);
        }

        @Override
        public void onDone() {
            done++;
        }
    }
}
