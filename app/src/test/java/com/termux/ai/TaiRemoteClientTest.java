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
import java.util.Collections;
import java.util.List;

/** The remote client's pure rules: host rule, body sanitiser, retries, parsing, SSE, redaction. */
public class TaiRemoteClientTest {

    @Test
    public void modelSearchMatchesAnywhereInTheIdIgnoringCase() {
        List<String> ids = Arrays.asList("anthropic/claude-sonnet-5-5", "openai/gpt-5", "Qwen/Qwen3-32B-Instruct", "qwen/qwen3-8b");
        assertEquals(Collections.singletonList("anthropic/claude-sonnet-5-5"), TaiRemoteClient.matchingModelIds(ids, "sonnet"));
        assertEquals(Arrays.asList("Qwen/Qwen3-32B-Instruct", "qwen/qwen3-8b"), TaiRemoteClient.matchingModelIds(ids, "WEN3"));
        assertEquals(Collections.singletonList("Qwen/Qwen3-32B-Instruct"), TaiRemoteClient.matchingModelIds(ids, " qwen  32b "));
        assertEquals(ids, TaiRemoteClient.matchingModelIds(ids, ""));
        assertEquals(ids, TaiRemoteClient.matchingModelIds(ids, null));
        assertTrue(TaiRemoteClient.matchingModelIds(ids, "llama").isEmpty());
    }

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

    @Test
    public void presetAddresses_buildTheirEndpointsWithoutDoubleSlashOrAddedVersion() {
        assertEquals("https://generativelanguage.googleapis.com/v1beta/openai/chat/completions",
            TaiRemoteClient.endpoint(TaiRemotePresets.GOOGLE.baseUrl, "chat/completions"));
        assertEquals("https://generativelanguage.googleapis.com/v1beta/openai/models",
            TaiRemoteClient.endpoint(TaiRemotePresets.GOOGLE.baseUrl + "/", "models"));
        assertEquals("https://openrouter.ai/api/v1/chat/completions",
            TaiRemoteClient.endpoint(TaiRemotePresets.OPENROUTER.baseUrl, "chat/completions"));
        assertEquals("https://api.groq.com/openai/v1/models",
            TaiRemoteClient.endpoint(TaiRemotePresets.GROQ.baseUrl, "models"));
        assertEquals("https://api.mistral.ai/v1/chat/completions",
            TaiRemoteClient.endpoint(TaiRemotePresets.MISTRAL.baseUrl, "chat/completions"));
        for (TaiRemotePresets.Preset preset : TaiRemotePresets.ALL) {
            if (preset.isCustom()) continue;
            for (String path : Arrays.asList("chat/completions", "models")) {
                String url = TaiRemoteClient.endpoint(preset.baseUrl, path);
                assertFalse(url, url.substring("https://".length()).contains("//"));
                assertTrue(url, url.endsWith(preset.baseUrl + "/" + path));
            }
        }
    }

    @Test
    public void presetHosts_areAllowedOverHttpsOnly() {
        for (TaiRemotePresets.Preset preset : TaiRemotePresets.ALL) {
            if (preset.isCustom()) continue;
            assertEquals(preset.id, TaiRemoteClient.UrlVerdict.OK_ENCRYPTED, TaiRemoteClient.checkUrl(preset.baseUrl));
            String http = "http://" + preset.baseUrl.substring("https://".length());
            assertEquals(preset.id, TaiRemoteClient.UrlVerdict.PUBLIC_HTTP, TaiRemoteClient.checkUrl(http));
        }
    }

    // ---------------------------------------------------------------- 429

    @Test
    public void rateLimit_honoursRetryAfterSecondsUpToTheCap() {
        assertEquals(7_000L, TaiRemoteClient.rateLimitWaitMs(429, "7", null, 0, 120_000L, 0L));
        assertEquals(1_500L, TaiRemoteClient.rateLimitWaitMs(429, " 1.5 ", null, 0, 120_000L, 0L));
        assertEquals(60_000L, TaiRemoteClient.rateLimitWaitMs(429, "60", null, 0, 120_000L, 0L));
        // A daily cap names hours: fail now rather than hold the caller.
        assertEquals(-1L, TaiRemoteClient.rateLimitWaitMs(429, "3600", null, 0, 600_000L, 0L));
    }

    @Test
    public void rateLimit_readsAnHttpDate() {
        long now = 1_760_000_000_000L; // 2025-10-09T08:53:20Z
        assertEquals(10_000L, TaiRemoteClient.parseRetryAfterMs("Thu, 09 Oct 2025 08:53:30 GMT", now));
        assertEquals(0L, TaiRemoteClient.parseRetryAfterMs("Thu, 09 Oct 2025 08:00:00 GMT", now));
        assertEquals(-1L, TaiRemoteClient.parseRetryAfterMs("soon", now));
        assertEquals(-1L, TaiRemoteClient.parseRetryAfterMs(null, now));
    }

    @Test
    public void rateLimit_readsGeminisRetryDelayWhenNoHeader() {
        String body = "[{\"error\":{\"code\":429,\"details\":[{\"@type\":\"type.googleapis.com/google.rpc.RetryInfo\","
            + "\"retryDelay\": \"17s\"}]}}]";
        assertEquals(17_000L, TaiRemoteClient.rateLimitWaitMs(429, null, body, 0, 120_000L, 0L));
        // The header wins over the body.
        assertEquals(2_000L, TaiRemoteClient.rateLimitWaitMs(429, "2", body, 0, 120_000L, 0L));
    }

    @Test
    public void rateLimit_backsOffWithoutAHintAndStopsAfterAFewTries() {
        assertEquals(2_000L, TaiRemoteClient.rateLimitWaitMs(429, null, "{}", 0, 120_000L, 0L));
        assertEquals(4_000L, TaiRemoteClient.rateLimitWaitMs(429, null, "{}", 1, 120_000L, 0L));
        assertEquals(8_000L, TaiRemoteClient.rateLimitWaitMs(429, null, "{}", 2, 120_000L, 0L));
        assertEquals(-1L, TaiRemoteClient.rateLimitWaitMs(429, null, "{}", TaiRemoteClient.MAX_RATE_LIMIT_RETRIES,
            120_000L, 0L));
    }

    @Test
    public void rateLimit_onlyFor429AndWithinTheCallersBudget() {
        assertEquals(-1L, TaiRemoteClient.rateLimitWaitMs(503, "5", null, 0, 120_000L, 0L));
        assertEquals(-1L, TaiRemoteClient.rateLimitWaitMs(400, "5", null, 0, 120_000L, 0L));
        // A voice rewrite gives itself seconds: a longer wait is not taken.
        assertEquals(-1L, TaiRemoteClient.rateLimitWaitMs(429, "5", null, 0, 4_000L, 0L));
    }

    @Test
    public void exchange_waitsOutA429ThenSucceeds() throws Exception {
        FakeTransport transport = new FakeTransport(
            new TaiRemoteClient.Reply(429, "{}", "3"),
            new TaiRemoteClient.Reply(200, chatReply("ok"), null));
        List<Long> slept = new ArrayList<>();
        TaiRemoteClient.Reply reply = TaiRemoteClient.exchange(new JSONObject().put("model", "m"), 120_000L,
            transport, slept::add);
        assertEquals(200, reply.status);
        assertEquals(2, transport.sent.size());
        assertEquals(Collections.singletonList(3_000L), slept);
    }

    @Test
    public void exchange_givesUpAfterTheLastRetryAndReturnsThe429() throws Exception {
        TaiRemoteClient.Reply limited = new TaiRemoteClient.Reply(429, "{}", null);
        FakeTransport transport = new FakeTransport(limited, limited, limited, limited, limited);
        List<Long> slept = new ArrayList<>();
        TaiRemoteClient.Reply reply = TaiRemoteClient.exchange(new JSONObject().put("model", "m"), 120_000L,
            transport, slept::add);
        assertEquals(429, reply.status);
        assertEquals(1 + TaiRemoteClient.MAX_RATE_LIMIT_RETRIES, transport.sent.size());
        assertEquals(Arrays.asList(2_000L, 4_000L, 8_000L), slept);
    }

    @Test
    public void exchange_waitsStayWithinTheTimeout() throws Exception {
        TaiRemoteClient.Reply limited = new TaiRemoteClient.Reply(429, "{}", null);
        FakeTransport transport = new FakeTransport(limited, limited, limited);
        List<Long> slept = new ArrayList<>();
        // 2 s fits in 5 s; the next 4 s does not fit in the 3 s left.
        long[] now = {0L};
        TaiRemoteClient.Reply reply = TaiRemoteClient.exchange(new JSONObject().put("model", "m"), 5_000L,
            transport, ms -> { slept.add(ms); now[0] += ms; }, () -> now[0]);
        assertEquals(429, reply.status);
        assertEquals(2, transport.sent.size());
        assertEquals(Collections.singletonList(2_000L), slept);
    }

    @Test
    public void exchange_aSlowPostLeavesNoBudgetForALongRetryAfter() throws Exception {
        long[] now = {0L};
        FakeTransport transport = new FakeTransport(
            new TaiRemoteClient.Reply(429, "{}", "60"),
            new TaiRemoteClient.Reply(200, chatReply("ok"), null));
        // Each post takes 100 s of the 120 s.
        TaiRemoteClient.Transport timed = body -> { now[0] += 100_000L; return transport.post(body); };
        List<Long> slept = new ArrayList<>();
        TaiRemoteClient.Reply reply = TaiRemoteClient.exchange(new JSONObject().put("model", "m"), 120_000L,
            timed, slept::add, () -> now[0]);
        assertEquals(429, reply.status);
        assertEquals(1, transport.sent.size());
        assertTrue(slept.isEmpty());
    }

    @Test
    public void exchange_aShortPostWithAShortRetryAfterRetries() throws Exception {
        long[] now = {0L};
        FakeTransport transport = new FakeTransport(
            new TaiRemoteClient.Reply(429, "{}", "5"),
            new TaiRemoteClient.Reply(200, chatReply("ok"), null));
        TaiRemoteClient.Transport timed = body -> { now[0] += 1_000L; return transport.post(body); };
        List<Long> slept = new ArrayList<>();
        TaiRemoteClient.Reply reply = TaiRemoteClient.exchange(new JSONObject().put("model", "m"), 120_000L,
            timed, ms -> { slept.add(ms); now[0] += ms; }, () -> now[0]);
        assertEquals(200, reply.status);
        assertEquals(Collections.singletonList(5_000L), slept);
    }

    @Test
    public void exchange_keepsTheBodyFixAcrossARateLimit() throws Exception {
        FakeTransport transport = new FakeTransport(
            new TaiRemoteClient.Reply(400, "{\"error\":{\"message\":\"Unsupported parameter: 'max_tokens'\"}}", null),
            new TaiRemoteClient.Reply(429, "{}", "1"),
            new TaiRemoteClient.Reply(200, chatReply("ok"), null));
        TaiRemoteClient.Reply reply = TaiRemoteClient.exchange(
            new JSONObject().put("model", "m").put("max_tokens", 64), 120_000L, transport, ms -> { });
        assertEquals(200, reply.status);
        assertEquals(3, transport.sent.size());
        JSONObject last = new JSONObject(transport.sent.get(2));
        assertFalse(last.has("max_tokens"));
        assertEquals(64, last.getInt("max_completion_tokens"));
    }

    @Test
    public void exchange_anInterruptedWaitStopsAsking() throws Exception {
        FakeTransport transport = new FakeTransport(
            new TaiRemoteClient.Reply(429, "{}", "1"),
            new TaiRemoteClient.Reply(200, chatReply("ok"), null));
        TaiRemoteClient.Reply reply = TaiRemoteClient.exchange(new JSONObject().put("model", "m"), 120_000L,
            transport, ms -> { throw new InterruptedException(); });
        assertEquals(429, reply.status);
        assertEquals(1, transport.sent.size());
        assertTrue(Thread.interrupted()); // also clears the flag for the next test
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

    /** Answers each POST with the next scripted reply and records the bodies it was sent. */
    private static final class FakeTransport implements TaiRemoteClient.Transport {
        final List<String> sent = new ArrayList<>();
        private final List<TaiRemoteClient.Reply> replies;

        FakeTransport(TaiRemoteClient.Reply... replies) {
            this.replies = new ArrayList<>(Arrays.asList(replies));
        }

        @NonNull
        @Override
        public TaiRemoteClient.Reply post(@NonNull String body) {
            sent.add(body);
            if (replies.isEmpty()) throw new AssertionError("more requests than scripted replies");
            return replies.remove(0);
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
