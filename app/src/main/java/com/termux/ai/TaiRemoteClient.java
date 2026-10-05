package com.termux.ai;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Talks to one OpenAI-compatible server (OpenAI, OpenRouter, Ollama, LM Studio, llama.cpp) on
 * behalf of {@link TaiRemoteProvider}. Plain {@link HttpURLConnection}, no new dependencies.
 *
 * <p>What it guards, all as pure static functions so they are unit-tested:
 * <ul>
 *   <li>{@link #checkUrl}: only http and https; {@code http://} only to this phone or a private
 *   LAN / Tailscale address (decision 2 of the remote-provider spec). The manifest allows
 *   cleartext so that this rule, not the platform, is the guard.</li>
 *   <li>{@link #sanitize}: the outgoing body loses {@code context_window} and every {@code _tai*}
 *   key; {@link #retryBody} decides the one retry after a 400 ({@code max_tokens} →
 *   {@code max_completion_tokens}, or without {@code response_format}).</li>
 *   <li>{@link #redact}: the key and any {@code Authorization} value never reach a log line.</li>
 * </ul>
 * Errors come back in {@link TaiManager#openAiError}'s shape, so callers handle a remote failure
 * exactly like a local one.
 */
public final class TaiRemoteClient {
    /** Private body flag: the caller wants JSON mode. Stripped like every other {@code _tai} key. */
    public static final String FLAG_JSON_OBJECT = "_tai_json_object";

    static final String REDACTED = "[redacted]";
    private static final String LOG_TAG = "TaiRemote";
    private static final int CONNECT_TIMEOUT_CAP_MS = 15_000;
    private static final int MAX_ERROR_BODY_CHARS = 400;
    private static final long PROBE_TIMEOUT_MS = 60_000L;
    /** A 2×2 solid red PNG, for the "does this model see images" probe. */
    static final String PROBE_IMAGE_DATA_URL = "data:image/png;base64,"
        + "iVBORw0KGgoAAAANSUhEUgAAAAIAAAACCAIAAAD91JpzAAAAEElEQVR4nGP4z8AARAwQCgAf7gP9i18U1AAAAABJRU5ErkJggg==";

    /** The outcome of {@link #checkUrl}. */
    public enum UrlVerdict {
        /** https to any host. */
        OK_ENCRYPTED,
        /** http to this phone or a private address: allowed, shown with an "unencrypted" note. */
        OK_UNENCRYPTED,
        /** Empty, unparsable, or no host. */
        MALFORMED,
        /** Not http or https ({@code file:}, {@code ftp:}, …). */
        BAD_SCHEME,
        /** http to a public host: refused, the key would cross the internet in the clear. */
        PUBLIC_HTTP;

        public boolean allowed() {
            return this == OK_ENCRYPTED || this == OK_UNENCRYPTED;
        }
    }

    /** The answer to "does this model understand images". */
    public enum ImageVerdict { IMAGES, TEXT_ONLY, UNKNOWN }

    /** {@link #listModels}'s answer. */
    public static final class ModelList {
        @NonNull public final List<String> ids;
        /** The server has no usable model list; the settings show a free-text model field. */
        public final boolean freeText;
        /** Why the list is empty, for the settings row; {@code null} when it is not. */
        @Nullable public final String error;

        ModelList(@NonNull List<String> ids, boolean freeText, @Nullable String error) {
            this.ids = ids;
            this.freeText = freeText;
            this.error = error;
        }
    }

    /** {@link #testConnection}'s answer: the round trip in ms, or the error message. */
    public static final class TestResult {
        public final boolean ok;
        public final long latencyMs;
        @Nullable public final String error;

        TestResult(boolean ok, long latencyMs, @Nullable String error) {
            this.ok = ok;
            this.latencyMs = latencyMs;
            this.error = error;
        }
    }

    @Nullable private final Context context;
    @NonNull private final String baseUrl;
    @Nullable private final String apiKey;

    /** {@code context} only feeds the TAI event log; {@code null} keeps the client silent. */
    public TaiRemoteClient(@Nullable Context context, @NonNull String baseUrl, @Nullable String apiKey) {
        this.context = context == null ? null : context.getApplicationContext();
        this.baseUrl = normalizeBaseUrl(baseUrl);
        this.apiKey = apiKey == null || apiKey.trim().isEmpty() ? null : apiKey.trim();
    }

    // ---------------------------------------------------------------- requests

    /** {@code GET {base}/models}; never throws. */
    @NonNull
    public ModelList listModels() {
        UrlVerdict verdict = checkUrl(baseUrl);
        if (!verdict.allowed()) return new ModelList(Collections.emptyList(), false, urlRefusal(baseUrl, verdict));
        long started = System.currentTimeMillis();
        try {
            Reply reply = send("GET", endpoint(baseUrl, "models"), null, 20_000L);
            logExchange("GET", "models", reply.status, System.currentTimeMillis() - started);
            if (reply.status < 200 || reply.status >= 300) {
                return new ModelList(Collections.emptyList(), modelListMissing(reply.status),
                    errorMessage(reply.status, reply.body));
            }
            List<String> ids = parseModelIds(reply.body);
            return new ModelList(ids, ids.isEmpty(), null);
        } catch (IOException e) {
            return new ModelList(Collections.emptyList(), false, transportMessage(e));
        }
    }

    /**
     * One chat completion. Returns the server's JSON on success, or an {@link TaiManager#openAiError}
     * envelope (with {@code _statusCode}) on any failure; never throws for network trouble.
     */
    @NonNull
    public JSONObject chatCompletions(@NonNull String body, long timeoutMs) throws JSONException {
        UrlVerdict verdict = checkUrl(baseUrl);
        if (!verdict.allowed()) return TaiManager.openAiError(source(400, "remote_url_refused", urlRefusal(baseUrl, verdict)));
        JSONObject outgoing;
        try {
            outgoing = sanitize(new JSONObject(body));
        } catch (JSONException e) {
            return TaiManager.openAiError(source(400, "bad_request", "The request body is not JSON."));
        }
        outgoing.remove("stream");
        long started = System.currentTimeMillis();
        try {
            Reply reply = sendWithRetries(outgoing, timeoutMs);
            logExchange("POST", "chat/completions", reply.status, System.currentTimeMillis() - started);
            if (reply.status >= 200 && reply.status < 300) {
                try {
                    return new JSONObject(reply.body);
                } catch (JSONException e) {
                    return TaiManager.openAiError(source(502, "remote_bad_reply", "The server's answer was not JSON."));
                }
            }
            return TaiManager.openAiError(source(reply.status, errorCode(reply.status, reply.body),
                errorMessage(reply.status, reply.body)));
        } catch (IOException e) {
            logExchange("POST", "chat/completions", 0, System.currentTimeMillis() - started);
            return TaiManager.openAiError(transportError(e));
        }
    }

    /**
     * A streamed chat completion: each server-sent {@code data:} chunk goes to {@code sink} as it
     * arrives, then {@link TaiManager.OpenAiStreamSink#onDone}. A failure before or during the
     * stream is one {@link TaiManager#openAiError} event followed by {@code onDone}.
     */
    public void chatCompletionsStream(@NonNull String body, long timeoutMs, @NonNull TaiManager.OpenAiStreamSink sink)
        throws JSONException, IOException {
        UrlVerdict verdict = checkUrl(baseUrl);
        if (!verdict.allowed()) {
            emitError(sink, source(400, "remote_url_refused", urlRefusal(baseUrl, verdict)));
            return;
        }
        JSONObject outgoing;
        try {
            outgoing = sanitize(new JSONObject(body));
        } catch (JSONException e) {
            emitError(sink, source(400, "bad_request", "The request body is not JSON."));
            return;
        }
        outgoing.put("stream", true);
        long started = System.currentTimeMillis();
        HttpURLConnection connection = null;
        try {
            for (int attempt = 0; ; attempt++) {
                connection = open("POST", endpoint(baseUrl, "chat/completions"), timeoutMs);
                write(connection, outgoing.toString());
                int status = connection.getResponseCode();
                if (status >= 200 && status < 300) break;
                String errorBody = readAll(connection.getErrorStream());
                connection.disconnect();
                connection = null;
                JSONObject retry = attempt < 2 ? retryBody(outgoing, status, errorBody) : null;
                if (retry == null) {
                    logExchange("POST", "chat/completions (stream)", status, System.currentTimeMillis() - started);
                    emitError(sink, source(status, errorCode(status, errorBody), errorMessage(status, errorBody)));
                    return;
                }
                outgoing = retry;
            }
            SseReader reader = new SseReader();
            try (BufferedReader lines = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = lines.readLine()) != null) {
                    String data = reader.accept(line);
                    if (data != null && deliver(data, sink)) return;
                }
                String rest = reader.finish();
                if (rest != null && deliver(rest, sink)) return;
            }
            logExchange("POST", "chat/completions (stream)", 200, System.currentTimeMillis() - started);
            sink.onDone();
        } catch (IOException e) {
            logExchange("POST", "chat/completions (stream)", 0, System.currentTimeMillis() - started);
            emitError(sink, transportError(e));
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    /** Sends a 2×2 red PNG with "Name the colour." and judges the answer; never throws. */
    @NonNull
    public ImageVerdict probeImages(@NonNull String model) {
        if (!checkUrl(baseUrl).allowed()) return ImageVerdict.UNKNOWN;
        try {
            JSONObject image = new JSONObject().put("url", PROBE_IMAGE_DATA_URL);
            JSONArray content = new JSONArray()
                .put(new JSONObject().put("type", "text").put("text", "Name the colour of this image in one word."))
                .put(new JSONObject().put("type", "image_url").put("image_url", image));
            JSONObject body = new JSONObject()
                .put("model", model)
                .put("max_tokens", 32)
                .put("messages", new JSONArray().put(new JSONObject().put("role", "user").put("content", content)));
            Reply reply = sendWithRetries(body, PROBE_TIMEOUT_MS);
            logExchange("POST", "chat/completions (image probe)", reply.status, 0L);
            return judgeProbe(reply.status, reply.body);
        } catch (IOException | JSONException e) {
            return ImageVerdict.UNKNOWN;
        }
    }

    /** One tiny chat call; reports the round trip or the error. Never throws. */
    @NonNull
    public TestResult testConnection(@NonNull String model) {
        long started = System.currentTimeMillis();
        try {
            JSONObject body = new JSONObject()
                .put("model", model)
                .put("max_tokens", 8)
                .put("messages", new JSONArray().put(new JSONObject().put("role", "user").put("content", "Reply with OK.")));
            JSONObject reply = chatCompletions(body.toString(), 30_000L);
            long latency = System.currentTimeMillis() - started;
            if (reply.has("choices")) return new TestResult(true, latency, null);
            return new TestResult(false, latency, reply.optString("message", "The server did not answer."));
        } catch (JSONException e) {
            return new TestResult(false, System.currentTimeMillis() - started, e.getMessage());
        }
    }

    // ---------------------------------------------------------------- pure rules

    /** Trims and drops trailing slashes: {@code https://x/v1/} → {@code https://x/v1}. */
    @NonNull
    public static String normalizeBaseUrl(@Nullable String url) {
        String value = url == null ? "" : url.trim();
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        return value;
    }

    @NonNull
    static String endpoint(@NonNull String baseUrl, @NonNull String path) {
        return normalizeBaseUrl(baseUrl) + "/" + path;
    }

    /** The scheme/host rule (decision 2); see {@link UrlVerdict}. */
    @NonNull
    public static UrlVerdict checkUrl(@Nullable String url) {
        String value = normalizeBaseUrl(url);
        if (value.isEmpty()) return UrlVerdict.MALFORMED;
        URI uri;
        try {
            uri = new URI(value);
        } catch (Exception e) {
            return UrlVerdict.MALFORMED;
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (scheme.isEmpty()) return UrlVerdict.MALFORMED;
        if (!"http".equals(scheme) && !"https".equals(scheme)) return UrlVerdict.BAD_SCHEME;
        String host = uri.getHost();
        if (host == null || host.isEmpty()) return UrlVerdict.MALFORMED;
        if ("https".equals(scheme)) return UrlVerdict.OK_ENCRYPTED;
        return isPrivateHost(host) ? UrlVerdict.OK_UNENCRYPTED : UrlVerdict.PUBLIC_HTTP;
    }

    /** True for an allowed {@code http://} address, which the settings mark "unencrypted". */
    public static boolean isUnencrypted(@Nullable String url) {
        return checkUrl(url) == UrlVerdict.OK_UNENCRYPTED;
    }

    /**
     * This phone (localhost, 127/8, ::1), a private LAN range (10/8, 172.16/12, 192.168/16),
     * Tailscale's CGNAT range (100.64/10), or an mDNS {@code *.local} name. Hostnames are not
     * resolved: a LAN machine by plain name needs its IP or https.
     */
    static boolean isPrivateHost(@NonNull String rawHost) {
        String host = rawHost.toLowerCase(Locale.ROOT);
        if (host.startsWith("[") && host.endsWith("]")) host = host.substring(1, host.length() - 1);
        while (host.endsWith(".")) host = host.substring(0, host.length() - 1);
        if (host.equals("localhost") || host.endsWith(".localhost")) return true;
        if (host.equals("::1") || host.equals("0:0:0:0:0:0:0:1")) return true;
        if (host.endsWith(".local") && host.length() > ".local".length()) return true;
        int[] octets = ipv4(host);
        if (octets == null) return false;
        int a = octets[0], b = octets[1];
        if (a == 127 || a == 10) return true;
        if (a == 172 && b >= 16 && b <= 31) return true;
        if (a == 192 && b == 168) return true;
        return a == 100 && b >= 64 && b <= 127;
    }

    @Nullable
    private static int[] ipv4(@NonNull String host) {
        String[] parts = host.split("\\.", -1);
        if (parts.length != 4) return null;
        int[] octets = new int[4];
        for (int i = 0; i < 4; i++) {
            String part = parts[i];
            if (part.isEmpty() || part.length() > 3) return null;
            for (int c = 0; c < part.length(); c++) if (!Character.isDigit(part.charAt(c))) return null;
            octets[i] = Integer.parseInt(part);
            if (octets[i] > 255) return null;
        }
        return octets;
    }

    /**
     * The body that goes on the wire: a copy without {@code context_window} or any {@code _tai*}
     * key (at any depth), and with {@code response_format: {type: json_object}} added only when
     * {@link #FLAG_JSON_OBJECT} asked for it. A {@code remote/} prefix on the model is dropped.
     */
    @NonNull
    static JSONObject sanitize(@NonNull JSONObject request) throws JSONException {
        boolean jsonMode = request.optBoolean(FLAG_JSON_OBJECT, false);
        JSONObject outgoing = (JSONObject) strip(request);
        outgoing.remove("context_window");
        String model = outgoing.optString("model", "");
        if (model.startsWith(TaiRemoteProvider.MODEL_PREFIX)) {
            outgoing.put("model", model.substring(TaiRemoteProvider.MODEL_PREFIX.length()));
        }
        if (jsonMode) outgoing.put("response_format", new JSONObject().put("type", "json_object"));
        return outgoing;
    }

    @NonNull
    private static Object strip(@NonNull Object value) throws JSONException {
        if (value instanceof JSONObject) {
            JSONObject source = (JSONObject) value;
            JSONObject copy = new JSONObject();
            Iterator<String> keys = source.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                if (key.startsWith("_tai")) continue;
                copy.put(key, strip(source.get(key)));
            }
            return copy;
        }
        if (value instanceof JSONArray) {
            JSONArray source = (JSONArray) value;
            JSONArray copy = new JSONArray();
            for (int i = 0; i < source.length(); i++) copy.put(strip(source.get(i)));
            return copy;
        }
        return value;
    }

    /**
     * The one retry a 400 may earn, or {@code null}: a server that names {@code max_tokens} gets
     * the same value as {@code max_completion_tokens} (OpenAI's reasoning models); one that
     * objects to {@code response_format} gets the body without it.
     */
    @Nullable
    static JSONObject retryBody(@NonNull JSONObject sent, int status, @Nullable String errorBody) throws JSONException {
        if (status != 400) return null;
        String error = errorBody == null ? "" : errorBody.toLowerCase(Locale.ROOT);
        if (sent.has("max_tokens") && error.contains("max_tokens")) {
            JSONObject retry = new JSONObject(sent.toString());
            retry.put("max_completion_tokens", retry.remove("max_tokens"));
            return retry;
        }
        if (sent.has("response_format")
            && (error.contains("response_format") || error.contains("json_object") || error.contains("json mode"))) {
            JSONObject retry = new JSONObject(sent.toString());
            retry.remove("response_format");
            return retry;
        }
        return null;
    }

    /**
     * {@code data[].id} from an OpenAI {@code /models} reply; {@code models[].id|name} is also
     * read for servers that answer in Ollama's shape. Order kept, duplicates and blanks dropped.
     */
    @NonNull
    static List<String> parseModelIds(@Nullable String body) {
        Set<String> ids = new LinkedHashSet<>();
        if (body == null) return new ArrayList<>();
        try {
            Object parsed = new org.json.JSONTokener(body.trim()).nextValue();
            JSONArray entries = null;
            if (parsed instanceof JSONObject) {
                JSONObject root = (JSONObject) parsed;
                entries = root.optJSONArray("data");
                if (entries == null) entries = root.optJSONArray("models");
            } else if (parsed instanceof JSONArray) {
                entries = (JSONArray) parsed;
            }
            if (entries == null) return new ArrayList<>();
            for (int i = 0; i < entries.length(); i++) {
                Object entry = entries.opt(i);
                String id = null;
                if (entry instanceof JSONObject) {
                    JSONObject model = (JSONObject) entry;
                    id = model.optString("id", model.optString("name", ""));
                } else if (entry instanceof String) {
                    id = (String) entry;
                }
                if (id != null && !id.trim().isEmpty()) ids.add(id.trim());
            }
        } catch (JSONException | ClassCastException e) {
            return new ArrayList<>();
        }
        return new ArrayList<>(ids);
    }

    /** A missing or locked model list means "type the model id" rather than "broken". */
    static boolean modelListMissing(int status) {
        return status == 401 || status == 403 || status == 404 || status == 405 || status == 501;
    }

    /** Judges the image probe's reply; see {@link #probeImages}. */
    @NonNull
    static ImageVerdict judgeProbe(int status, @Nullable String body) {
        String text = body == null ? "" : body.toLowerCase(Locale.ROOT);
        boolean mentionsImages = text.contains("image") || text.contains("vision") || text.contains("multimodal")
            || text.contains("multi-modal");
        if (status < 200 || status >= 300) {
            if (status == 400 || status == 415 || status == 422 || mentionsImages) return ImageVerdict.TEXT_ONLY;
            return ImageVerdict.UNKNOWN;
        }
        String answer = replyText(body).toLowerCase(Locale.ROOT).replace('\u2019', '\'');
        if (answer.contains("can't see") || answer.contains("cannot see") || answer.contains("unable to see")
            || answer.contains("can't view") || answer.contains("cannot view") || answer.contains("no image")
            || answer.contains("don't see") || answer.contains("do not see")) {
            return ImageVerdict.TEXT_ONLY;
        }
        if (RED.matcher(answer).find()) return ImageVerdict.IMAGES;
        // Accepted the image and named some colour: it reads images, perhaps poorly.
        return answer.trim().isEmpty() ? ImageVerdict.UNKNOWN : ImageVerdict.IMAGES;
    }

    private static final Pattern RED = Pattern.compile("\\bred\\b");

    /** {@code choices[0].message.content}, as a string or an array of text parts. */
    @NonNull
    static String replyText(@Nullable String body) {
        if (body == null) return "";
        try {
            JSONObject root = new JSONObject(body);
            JSONArray choices = root.optJSONArray("choices");
            if (choices == null || choices.length() == 0) return "";
            JSONObject message = choices.getJSONObject(0).optJSONObject("message");
            if (message == null) return "";
            Object content = message.opt("content");
            if (content instanceof String) return (String) content;
            if (content instanceof JSONArray) {
                StringBuilder text = new StringBuilder();
                JSONArray parts = (JSONArray) content;
                for (int i = 0; i < parts.length(); i++) {
                    JSONObject part = parts.optJSONObject(i);
                    if (part != null) text.append(part.optString("text", ""));
                }
                return text.toString();
            }
        } catch (JSONException ignored) {
        }
        return "";
    }

    /**
     * Server-sent events, line by line: {@code data:} lines collect until a blank line ends the
     * event, which {@link #accept} then returns. Comments ({@code :}) and other fields are skipped.
     */
    static final class SseReader {
        static final String DONE = "[DONE]";
        private final StringBuilder data = new StringBuilder();
        private boolean hasData;

        /** Feeds one line; returns a complete event's data, or {@code null} while one is open. */
        @Nullable
        String accept(@NonNull String line) {
            if (line.isEmpty()) return finish();
            if (line.startsWith(":")) return null;
            if (!line.startsWith("data:")) return null;
            String value = line.substring(5);
            if (value.startsWith(" ")) value = value.substring(1);
            if (hasData) data.append('\n');
            data.append(value);
            hasData = true;
            return null;
        }

        /** The open event's data at end of stream, if any. */
        @Nullable
        String finish() {
            if (!hasData) return null;
            String event = data.toString();
            data.setLength(0);
            hasData = false;
            return event;
        }
    }

    /**
     * Hands one SSE event to the sink. Returns true when the stream is over: {@code [DONE]} (after
     * {@code onDone}) or an error chunk (after the error event and {@code onDone}).
     */
    static boolean deliver(@NonNull String data, @NonNull TaiManager.OpenAiStreamSink sink) throws IOException, JSONException {
        String trimmed = data.trim();
        if (trimmed.isEmpty()) return false;
        if (SseReader.DONE.equals(trimmed)) {
            sink.onDone();
            return true;
        }
        JSONObject chunk;
        try {
            chunk = new JSONObject(trimmed);
        } catch (JSONException e) {
            return false; // A keep-alive or a server's own debug line; not ours to forward.
        }
        if (chunk.has("error") && !chunk.has("choices")) {
            JSONObject error = chunk.optJSONObject("error");
            String message = error == null ? chunk.optString("error") : error.optString("message", "Remote model error");
            String code = error == null ? "remote_error" : error.optString("code", "remote_error");
            emitError(sink, source(502, code.isEmpty() || "null".equals(code) ? "remote_error" : code, message));
            return true;
        }
        sink.onEvent(chunk);
        return false;
    }

    /**
     * Removes {@code secrets} and every {@code Authorization}/{@code Bearer} value from a line
     * bound for a log, the event log or the diagnostics file.
     */
    @NonNull
    public static String redact(@Nullable String text, @Nullable String... secrets) {
        if (text == null) return "";
        String redacted = text;
        if (secrets != null) {
            for (String secret : secrets) {
                if (secret != null && secret.trim().length() >= 4) redacted = redacted.replace(secret.trim(), REDACTED);
            }
        }
        redacted = AUTHORIZATION.matcher(redacted).replaceAll("$1" + REDACTED);
        redacted = BEARER.matcher(redacted).replaceAll("Bearer " + REDACTED);
        return redacted;
    }

    private static final Pattern AUTHORIZATION =
        Pattern.compile("(?i)(\"?authorization\"?\\s*[:=]\\s*\"?)(?:bearer\\s+)?[^\\s\",}]+");
    private static final Pattern BEARER = Pattern.compile("(?i)bearer\\s+(?!\\[redacted\\])[A-Za-z0-9._~+/=:-]+");

    /** The line {@link #logExchange} writes: method, host and path, status, time; never a header. */
    @NonNull
    static String formatLogLine(@NonNull String method, @NonNull String baseUrl, @NonNull String path, int status,
                                long ms, @Nullable String apiKey) {
        String host;
        try {
            host = new URI(normalizeBaseUrl(baseUrl)).getHost();
        } catch (Exception e) {
            host = null;
        }
        String line = method + " " + (host == null ? "?" : host) + " " + path + " -> "
            + (status <= 0 ? "no answer" : String.valueOf(status)) + (ms > 0 ? " in " + ms + " ms" : "");
        return redact(line, apiKey);
    }

    // ---------------------------------------------------------------- transport

    private static final class Reply {
        final int status;
        @NonNull final String body;

        Reply(int status, @NonNull String body) {
            this.status = status;
            this.body = body;
        }
    }

    @NonNull
    private Reply sendWithRetries(@NonNull JSONObject outgoing, long timeoutMs) throws IOException, JSONException {
        JSONObject body = outgoing;
        Reply reply = send("POST", endpoint(baseUrl, "chat/completions"), body.toString(), timeoutMs);
        // At most two retries: one for max_tokens, one for response_format; each removes its key.
        for (int attempt = 0; attempt < 2; attempt++) {
            JSONObject retry = retryBody(body, reply.status, reply.body);
            if (retry == null) break;
            body = retry;
            reply = send("POST", endpoint(baseUrl, "chat/completions"), body.toString(), timeoutMs);
        }
        return reply;
    }

    @NonNull
    private Reply send(@NonNull String method, @NonNull String url, @Nullable String body, long timeoutMs) throws IOException {
        HttpURLConnection connection = open(method, url, timeoutMs);
        try {
            if (body != null) write(connection, body);
            int status = connection.getResponseCode();
            InputStream stream = status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream();
            return new Reply(status, readAll(stream));
        } finally {
            connection.disconnect();
        }
    }

    @NonNull
    private HttpURLConnection open(@NonNull String method, @NonNull String url, long timeoutMs) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        int timeout = (int) Math.max(1_000L, Math.min(Integer.MAX_VALUE, timeoutMs));
        connection.setConnectTimeout(Math.min(timeout, CONNECT_TIMEOUT_CAP_MS));
        connection.setReadTimeout(timeout);
        connection.setRequestMethod(method);
        connection.setInstanceFollowRedirects(false); // A redirect could carry the key elsewhere.
        connection.setRequestProperty("Accept", "application/json, text/event-stream");
        if (apiKey != null) connection.setRequestProperty("Authorization", "Bearer " + apiKey);
        return connection;
    }

    private static void write(@NonNull HttpURLConnection connection, @NonNull String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        connection.setDoOutput(true);
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setFixedLengthStreamingMode(bytes.length);
        try (OutputStream output = connection.getOutputStream()) {
            output.write(bytes);
        }
    }

    @NonNull
    private static String readAll(@Nullable InputStream stream) throws IOException {
        if (stream == null) return "";
        try (InputStream input = stream) {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[8192];
            int read;
            while ((read = input.read(chunk)) != -1) buffer.write(chunk, 0, read);
            return buffer.toString(StandardCharsets.UTF_8.name());
        }
    }

    // ---------------------------------------------------------------- errors and logs

    private static void emitError(@NonNull TaiManager.OpenAiStreamSink sink, @NonNull JSONObject source)
        throws JSONException, IOException {
        sink.onEvent(TaiManager.openAiError(source));
        sink.onDone();
    }

    @NonNull
    static JSONObject source(int status, @NonNull String code, @NonNull String message) throws JSONException {
        JSONObject data = new JSONObject();
        data.put("ok", false);
        data.put("error", code);
        data.put("message", message);
        data.put("_statusCode", status);
        return data;
    }

    @NonNull
    private JSONObject transportError(@NonNull IOException e) throws JSONException {
        boolean timeout = e instanceof SocketTimeoutException;
        return source(timeout ? 504 : 502, timeout ? "remote_timeout" : "remote_unreachable", transportMessage(e));
    }

    @NonNull
    private String transportMessage(@NonNull IOException e) {
        String host;
        try {
            host = new URI(baseUrl).getHost();
        } catch (Exception ignored) {
            host = baseUrl;
        }
        if (e instanceof SocketTimeoutException) return redact(host + " did not answer in time.", apiKey);
        String detail = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
        return redact("Could not reach " + host + ": " + detail, apiKey);
    }

    /** The server's own error message when it sent one, else a short generic one per status. */
    @NonNull
    String errorMessage(int status, @Nullable String body) {
        String message = null;
        try {
            JSONObject root = new JSONObject(body == null ? "" : body);
            JSONObject error = root.optJSONObject("error");
            if (error != null) message = error.optString("message", null);
            else if (root.has("error")) message = root.optString("error");
            else if (root.has("message")) message = root.optString("message");
        } catch (JSONException ignored) {
        }
        if (message == null || message.trim().isEmpty()) {
            if (status == 401 || status == 403) message = "The server refused the API key.";
            else if (status == 404) message = "The server has no such address or model.";
            else if (status == 429) message = "The server is rate limiting requests; try again shortly.";
            else if (body != null && !body.trim().isEmpty()) message = body.trim();
            else message = "The server answered " + status + ".";
        }
        if (message.length() > MAX_ERROR_BODY_CHARS) message = message.substring(0, MAX_ERROR_BODY_CHARS) + "…";
        return redact(message, apiKey);
    }

    @NonNull
    static String errorCode(int status, @Nullable String body) {
        try {
            JSONObject error = new JSONObject(body == null ? "" : body).optJSONObject("error");
            if (error != null) {
                Object code = error.opt("code");
                if (code != null && code != JSONObject.NULL && !String.valueOf(code).isEmpty()) return String.valueOf(code);
                String type = error.optString("type", "");
                if (!type.isEmpty()) return type;
            }
        } catch (JSONException ignored) {
        }
        return "remote_http_" + status;
    }

    @NonNull
    static String urlRefusal(@NonNull String url, @NonNull UrlVerdict verdict) {
        switch (verdict) {
            case BAD_SCHEME:
                return "Only http and https addresses are allowed.";
            case PUBLIC_HTTP:
                return "Unencrypted http is only allowed for this phone and private network addresses; use https.";
            case MALFORMED:
                return url.isEmpty() ? "No server address is set." : "The server address is not a valid URL.";
            default:
                return "";
        }
    }

    private void logExchange(@NonNull String method, @NonNull String path, int status, long ms) {
        String line = formatLogLine(method, baseUrl, path, status, ms, apiKey);
        try {
            android.util.Log.i(LOG_TAG, line);
        } catch (RuntimeException ignored) {
            // JVM unit tests: android.util.Log is a stub.
        }
        if (context != null && (status <= 0 || status >= 400)) {
            TaiEventLog.log(context, TaiEventLog.API_ERROR, null, "remote", null, 0, ms, 0L, line);
        }
    }
}
