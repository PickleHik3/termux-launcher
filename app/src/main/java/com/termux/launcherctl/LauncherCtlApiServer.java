package com.termux.launcherctl;

import android.content.Context;

import androidx.annotation.NonNull;

import com.termux.ai.TaiApiCompatibility;
import com.termux.ai.TaiCliFormatter;
import com.termux.ai.TaiManager;
import com.termux.ai.TaiSettings;
import com.termux.app.launcher.LauncherAppLauncher;
import com.termux.app.launcher.data.LauncherAppDataProvider;
import com.termux.app.launcher.model.LauncherAppEntry;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxConstants;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Local OpenAI/Ollama-compatible inference API server exposed on localhost (or optionally LAN)
 * so any OpenAI-compatible client can talk to models hosted by TAI.
 */
public class LauncherCtlApiServer {
    private static final String LOG_TAG = "LauncherCtlApiServer";
    private static final String API_VERSION = "v1";
    private static final String LAUNCHERCTL_DIR_PATH = TermuxConstants.TERMUX_HOME_DIR_PATH + "/.launcherctl";
    private static final String TOKEN_FILE_PATH = LAUNCHERCTL_DIR_PATH + "/token";
    private static final String ENDPOINT_FILE_PATH = LAUNCHERCTL_DIR_PATH + "/endpoint";
    private static final String TAI_BIN_PATH = TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + "/tai";
    private static final String LAUNCHERCTL_BIN_PATH = TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + "/launcherctl";
    static final String LAN_WARNING = TaiSettings.LAN_WARNING;
    static final String HEALTH_BODY = "Ollama is running";

    private static final int MAX_REQUEST_LINE_BYTES = 4096;
    private static final int MAX_HEADER_LINE_BYTES = 4096;
    private static final int MAX_HEADER_LINES = 64;
    private static final int MAX_BODY_BYTES = 32 * 1024 * 1024;
    private static final int CLIENT_SOCKET_TIMEOUT_MS = 10_000;

    private static LauncherCtlApiServer instance;

    private final ThreadPoolExecutor clientExecutor = new ThreadPoolExecutor(
        2, 4, 30L, TimeUnit.SECONDS, new LinkedBlockingQueue<>(64));
    /**
     * The server's own housekeeping — the accept loop, an async start, a LAN-session expiry. A
     * cached pool rather than a single thread because the accept loop occupies its thread for the
     * whole listening lifetime and the expiry has to be able to tear that loop down.
     */
    private final ExecutorService controlExecutor = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "launcherctl-control");
        thread.setDaemon(true);
        return thread;
    });
    private final SecureRandom random = new SecureRandom();
    private final Map<String, SimpleRateLimiter> rateLimiters = new HashMap<>();

    private volatile boolean running;
    private volatile boolean starting;
    private volatile String token;
    private volatile boolean authRequired = true;
    private volatile String bindMode = TaiSettings.BIND_MODE_LOCALHOST;
    private volatile long lanSessionDeadlineMs;
    private volatile boolean lanSessionExpiring;
    private volatile int port;
    private ServerSocket serverSocket;
    private Future<?> acceptLoop;
    private Context appContext;

    private LauncherCtlApiServer() {
    }

    public static synchronized LauncherCtlApiServer getInstance() {
        if (instance == null) {
            instance = new LauncherCtlApiServer();
        }
        return instance;
    }

    public synchronized void start(Context context) {
        if (running) {
            starting = false;
            return;
        }

        try {
            initializeRateLimiters();
            appContext = context.getApplicationContext();
            TaiSettings settings = new TaiSettings(appContext);
            expireLanSessionIfStale(settings);
            token = settings.getOrCreateApiToken();
            String bindMode = settings.getApiBindMode();
            this.bindMode = bindMode;
            long lanStartedAt = settings.getLanSessionStartedAt();
            lanSessionDeadlineMs = TaiSettings.BIND_MODE_LAN.equals(bindMode) && lanStartedAt > 0
                ? lanStartedAt + TaiSettings.LAN_SESSION_MAX_MS : 0L;
            lanSessionExpiring = false;
            authRequired = effectiveAuthRequired(settings);
            serverSocket = createLoopbackServerSocket(settings.getApiPort(), bindMode);
            port = serverSocket.getLocalPort();
            running = true;
            installClientFiles();
            startAcceptLoop(context.getApplicationContext());
            Logger.logInfo(LOG_TAG, "LauncherCtl API listening on " + bindAddressForMode(bindMode) + ":" + port);
        } catch (Exception e) {
            running = false;
            Logger.logErrorExtended(LOG_TAG, "Failed to start LauncherCtl API server: " + e.getMessage());
            cleanupSocket();
        } finally {
            starting = false;
        }
    }

    /**
     * Drops an expired LAN exposure back to loopback and burns the token it was reachable with.
     *
     * <p>Rotating matters as much as unbinding: the plaintext token may already have been captured
     * off the network, and a loopback listener that still honours it is reachable from every other
     * app on the device.
     *
     * @return true when a LAN session was expired by this call.
     */
    private boolean expireLanSessionIfStale(@NonNull TaiSettings settings) {
        if (!settings.isLanSessionExpired()) return false;
        settings.setApiBindMode(TaiSettings.BIND_MODE_LOCALHOST);
        settings.rotateApiToken(random);
        Logger.logInfo(LOG_TAG, "LAN exposure window elapsed; rebound to loopback and rotated the API token");
        return true;
    }

    /**
     * Tears the LAN listener down off the request path.
     *
     * <p>The expiry is noticed while serving a request, but a restart cannot run on the thread that
     * is answering one: {@link #stop()} joins the accept loop this handler was dispatched from.
     * Hand it to a separate thread and let the in-flight request finish with its 403.
     */
    private void beginLanSessionExpiry() {
        if (lanSessionExpiring) return;
        synchronized (this) {
            if (lanSessionExpiring) return;
            lanSessionExpiring = true;
        }
        final Context context = appContext;
        try {
            controlExecutor.execute(() -> {
                try {
                    if (context == null) return;
                    // applyEndpointSettings rebinds without tearing down the client executor, and
                    // start() runs expireLanSessionIfStale, which is what actually rotates the token.
                    applyEndpointSettings(context);
                } catch (Exception e) {
                    Logger.logErrorExtended(LOG_TAG, "Failed to expire LAN session: " + e.getMessage());
                }
            });
        } catch (RejectedExecutionException stopped) {
            lanSessionExpiring = false;
        }
    }

    /**
     * LAN bind mode always enforces the bearer token; the auth toggle only opens up loopback.
     */
    static boolean effectiveAuthRequired(@NonNull TaiSettings settings) {
        if (TaiSettings.BIND_MODE_LAN.equals(settings.getApiBindMode())) return true;
        return settings.isApiAuthRequired();
    }

    /**
     * Writes the token/endpoint files and installs the shell helpers under the Termux home.
     *
     * None of it is required to serve requests: it is discoverability for a shell that already has
     * the socket. Letting an unwritable home abort {@link #start(Context)} would close a listener
     * that had already bound successfully, so every client sees "connection refused" for a reason
     * that has nothing to do with the socket. Log and keep serving instead.
     */
    private void installClientFiles() {
        try {
            writeClientConfig();
        } catch (Exception e) {
            Logger.logWarn(LOG_TAG, "LauncherCtl API is listening but its client config could not be written: " + e.getMessage());
        }
        try {
            installTaiCliScripts();
        } catch (Exception e) {
            Logger.logWarn(LOG_TAG, "LauncherCtl API is listening but its shell helpers could not be installed: " + e.getMessage());
        }
    }

    public synchronized void ensureStartedAsync(Context context) {
        if (running || starting) {
            return;
        }

        starting = true;
        Context appContext = context.getApplicationContext();
        try {
            controlExecutor.execute(() -> start(appContext));
        } catch (RejectedExecutionException stopped) {
            starting = false;
        }
    }

    /**
     * Re-attempt CLI script installation after bootstrap setup is complete.
     */
    public synchronized void ensureCliScriptsInstalled() {
        try {
            installTaiCliScripts();
        } catch (Throwable t) {
            Logger.logErrorExtended(LOG_TAG, "Failed to ensure launcher CLI scripts are installed: " + t.getMessage());
        }
    }

    public synchronized void stop() {
        stopListening();
        clientExecutor.shutdownNow();
        controlExecutor.shutdownNow();
    }

    /** Closes the socket and ends the accept loop; the executors stay up for a restart. */
    private void stopListening() {
        running = false;
        starting = false;
        cleanupSocket();
        if (acceptLoop != null) {
            acceptLoop.cancel(true);
            acceptLoop = null;
        }
    }

    public synchronized JSONObject applyEndpointSettings(Context context) throws JSONException {
        Context nextContext = context.getApplicationContext();
        stopListening();
        start(nextContext);
        return buildEndpointSettings(nextContext, true);
    }

    public synchronized JSONObject rotateAuthTokenFromSettings(Context context) throws JSONException {
        return rotateAuthToken(context.getApplicationContext(), true);
    }

    public synchronized JSONObject randomizeApiPortFromSettings(Context context) throws JSONException {
        Context appContext = context.getApplicationContext();
        new TaiSettings(appContext).randomizeApiPort(random);
        return applyEndpointSettings(appContext);
    }

    public synchronized JSONObject endpointSettings(Context context) throws JSONException {
        Context resolvedContext = appContext != null ? appContext : context.getApplicationContext();
        if (token == null || token.isEmpty()) {
            token = new TaiSettings(resolvedContext).getOrCreateApiToken();
            if (running) {
                try {
                    writeClientConfig();
                } catch (IOException ignored) {
                }
            }
        }
        return buildEndpointSettings(resolvedContext, true);
    }

    private void startAcceptLoop(Context context) {
        acceptLoop = controlExecutor.submit(() -> {
            while (running && serverSocket != null && !serverSocket.isClosed()) {
                try {
                    Socket client = serverSocket.accept();
                    try {
                        clientExecutor.submit(() -> handleClient(client, context));
                    } catch (RejectedExecutionException rejected) {
                        closeQuietly(client);
                    }
                } catch (IOException e) {
                    if (running) {
                        Logger.logErrorExtended(LOG_TAG, "Accept failed: " + e.getMessage());
                    }
                }
            }
        });
    }

    private void handleClient(Socket socket, Context context) {
        try (Socket client = socket;
             BufferedInputStream input = new BufferedInputStream(client.getInputStream());
             OutputStream output = client.getOutputStream()) {
            client.setSoTimeout(CLIENT_SOCKET_TIMEOUT_MS);

            HttpRequest request;
            try {
                request = parseRequest(input);
            } catch (HttpParseException e) {
                writeJsonResponse(output, e.statusCode, jsonError(e.errorCode, e.getMessage()).toString());
                return;
            }

            if (lanSessionDeadlineMs > 0 && System.currentTimeMillis() > lanSessionDeadlineMs) {
                beginLanSessionExpiry();
                writeResponse(output, forbiddenResponse("lan_session_expired",
                    "The LAN exposure window has ended; re-enable LAN mode and collect the new token"));
                return;
            }

            // A request that names a host we did not bind is a DNS rebinding attempt: the browser
            // resolved an attacker-controlled name to a loopback/LAN address and is now speaking to
            // us with the attacker's origin. Reject before any routing or auth work happens.
            if (!isAllowedHost(request.headers.get("host"), bindMode)) {
                writeResponse(output, forbiddenResponse("forbidden_host",
                    "Request Host is not a bound address for this server"));
                return;
            }

            // Only pages served from loopback may act as browser clients. Anything else is a remote
            // site reaching into the device, so it gets neither a CORS grant nor a route -- the
            // token cannot protect endpoints the browser attaches ambient authority to.
            String origin = request.headers.get("origin");
            if (!isAllowedOrigin(origin)) {
                writeResponse(output, forbiddenResponse("forbidden_origin",
                    "Cross-origin browser requests are not accepted"));
                return;
            }

            // CORS preflight and the health probe stay reachable without a token so local browser
            // clients and stock Ollama tooling can discover the server.
            if ("OPTIONS".equals(request.method)) {
                writeResponse(output, corsPreflightResponse(), origin);
                return;
            }
            if (isHealthPath(request)) {
                writeResponse(output, healthResponse("HEAD".equals(request.method)), origin);
                return;
            }

            if (authRequired && !isAuthorized(request.headers)) {
                writeResponse(output, request.path.startsWith("/api/")
                    ? ollamaUnauthorizedResponse() : unauthorizedResponse(), origin);
                return;
            }

            if (!allowRequest(request)) {
                SimpleRateLimiter limiter = rateLimiterFor(request);
                long retryAfter = limiter != null ? limiter.retryAfterSeconds() : 1;
                JSONObject rateLimitError = jsonError("rate_limited", "Too many requests; retry later");
                rateLimitError.put("_statusCode", 429);
                HttpResponse rateLimitResponse = request.path.startsWith("/api/")
                    ? ollamaJsonResponse(rateLimitError) : jsonResponse(rateLimitError);
                writeResponse(output, withRateLimitHeaders(rateLimitResponse, limiter, retryAfter), origin);
                return;
            }

            HttpResponse response = routeRequest(context, request);
            writeResponse(output, response, origin);

        } catch (Exception e) {
            Logger.logErrorExtended(LOG_TAG, "Request handling failed: " + e.getMessage());
        }
    }

    private static boolean isHealthPath(HttpRequest request) {
        return ("GET".equals(request.method) || "HEAD".equals(request.method)) && "/".equals(request.path);
    }

    private static HttpResponse healthResponse(boolean headOnly) {
        byte[] body = headOnly ? new byte[0] : HEALTH_BODY.getBytes(StandardCharsets.UTF_8);
        return new HttpResponse(200, "text/plain; charset=utf-8", body, null);
    }

    /**
     * Browser clients are accepted only when the page itself was served from loopback.
     *
     * <p>A {@code null} origin means the caller is not a browser (curl, an OpenAI SDK, the shell
     * helpers) and is left alone: the bearer token is the control there. A browser, by contrast,
     * attaches ambient authority to the request, so a remote page must never get a route even if
     * the token happens to be disabled or leaked. The literal {@code "null"} origin (sandboxed
     * iframe, {@code file://} document) is rejected because it cannot be attributed to loopback.
     */
    static boolean isAllowedOrigin(String origin) {
        if (origin == null) return true;
        String value = origin.trim();
        if (value.isEmpty()) return true;
        if ("null".equalsIgnoreCase(value)) return false;
        int schemeEnd = value.indexOf("://");
        if (schemeEnd < 0) return false;
        String scheme = value.substring(0, schemeEnd).toLowerCase(Locale.ROOT);
        if (!"http".equals(scheme) && !"https".equals(scheme)) return false;
        return isLoopbackHost(hostWithoutPort(value.substring(schemeEnd + 3)));
    }

    /**
     * The Host header must name an address this server actually bound.
     *
     * <p>Rebinding attacks work by pointing an attacker-owned hostname at 127.0.0.1 (or the LAN
     * address) after the page has loaded, so the browser considers the attacker's site same-origin
     * with us. Requiring a literal address -- or plain {@code localhost} -- removes the hostname
     * indirection that attack depends on. Requests without a Host header are HTTP/1.0 clients that
     * cannot be browsers, so they pass.
     */
    static boolean isAllowedHost(String host, String bindMode) {
        if (host == null) return true;
        String value = hostWithoutPort(host.trim());
        if (value.isEmpty()) return true;
        if (isLoopbackHost(value)) return true;
        // A LAN listener answers on its own numeric address too; names are still refused.
        return TaiSettings.BIND_MODE_LAN.equals(bindMode) && isIpLiteral(value);
    }

    static String hostWithoutPort(@NonNull String hostHeader) {
        String value = hostHeader.trim();
        int slash = value.indexOf('/');
        if (slash >= 0) value = value.substring(0, slash);
        if (value.startsWith("[")) {
            int close = value.indexOf(']');
            return close < 0 ? value : value.substring(1, close);
        }
        int colon = value.lastIndexOf(':');
        // A bare IPv6 address has several colons and no port suffix.
        if (colon > 0 && value.indexOf(':') == colon) return value.substring(0, colon);
        return value;
    }

    private static boolean isLoopbackHost(String host) {
        if (host == null) return false;
        String value = host.trim().toLowerCase(Locale.ROOT);
        if (value.isEmpty()) return false;
        if ("localhost".equals(value) || "::1".equals(value) || "0:0:0:0:0:0:0:1".equals(value)) return true;
        return value.startsWith("127.") && isIpv4Literal(value);
    }

    private static boolean isIpLiteral(String host) {
        return isIpv4Literal(host) || host.indexOf(':') >= 0;
    }

    private static boolean isIpv4Literal(String host) {
        String[] parts = host.split("\\.", -1);
        if (parts.length != 4) return false;
        for (String part : parts) {
            if (part.isEmpty() || part.length() > 3) return false;
            for (int i = 0; i < part.length(); i++) {
                if (!Character.isDigit(part.charAt(i))) return false;
            }
            if (Integer.parseInt(part) > 255) return false;
        }
        return true;
    }

    static HttpResponse forbiddenResponse(String code, String message) {
        JSONObject error = new JSONObject();
        try {
            error.put("ok", false);
            error.put("error", code);
            error.put("message", message);
            withOpenAiErrorEnvelope(error, 403);
        } catch (JSONException ignored) {
        }
        return new HttpResponse(403, "application/json; charset=utf-8",
            error.toString().getBytes(StandardCharsets.UTF_8), null);
    }

    private static HttpResponse corsPreflightResponse() {
        Map<String, String> headers = new HashMap<>();
        headers.put("Access-Control-Allow-Methods", "GET, POST, HEAD, OPTIONS");
        headers.put("Access-Control-Allow-Headers", "Authorization, Content-Type, X-Api-Key, X-Tai-Output, OpenAI-Beta");
        headers.put("Access-Control-Max-Age", "86400");
        return new HttpResponse(200, "text/plain; charset=utf-8", new byte[0], headers);
    }

    private HttpResponse routeRequest(Context context, HttpRequest request) {
        try {
            if ("GET".equals(request.method) && "/api/version".equals(request.path)) {
                return ollamaJsonResponse(new JSONObject().put("version", TaiApiCompatibility.OLLAMA_VERSION));
            } else if ("GET".equals(request.method) && "/api/tags".equals(request.path)) {
                TaiManager manager = TaiManager.getInstance(context);
                return ollamaJsonResponse(TaiApiCompatibility.ollamaTags(manager.openAiModels()));
            } else if ("POST".equals(request.method) && "/api/show".equals(request.path)) {
                TaiManager manager = TaiManager.getInstance(context);
                return ollamaJsonResponse(TaiApiCompatibility.ollamaShow(manager.openAiModels(), request.body));
            } else if ("GET".equals(request.method) && "/api/ps".equals(request.path)) {
                TaiManager manager = TaiManager.getInstance(context);
                return ollamaJsonResponse(TaiApiCompatibility.ollamaPs(manager.openAiModels(), manager.getRuntimeState()));
            } else if ("POST".equals(request.method) && "/api/chat".equals(request.path)) {
                TaiManager manager = TaiManager.getInstance(context);
                JSONObject chatRequest = TaiApiCompatibility.ollamaChatRequest(request.body);
                if (chatRequest.optBoolean("stream", true)) {
                    return ndjsonResponse(output -> writeOllamaChatStream(context, chatRequest.toString(), output, false));
                }
                return ollamaJsonResponse(TaiApiCompatibility.ollamaChatFromOpenAi(manager.openAiChatCompletions(chatRequest.toString())));
            } else if ("POST".equals(request.method) && "/api/generate".equals(request.path)) {
                TaiManager manager = TaiManager.getInstance(context);
                JSONObject chatRequest = TaiApiCompatibility.ollamaGenerateRequest(request.body);
                if (chatRequest.optBoolean("stream", true)) {
                    return ndjsonResponse(output -> writeOllamaChatStream(context, chatRequest.toString(), output, true));
                }
                return ollamaJsonResponse(TaiApiCompatibility.ollamaGenerateFromOpenAi(manager.openAiChatCompletions(chatRequest.toString())));
            } else if ("POST".equals(request.method) && "/api/embed".equals(request.path)) {
                TaiManager manager = TaiManager.getInstance(context);
                JSONObject embedRequest = TaiApiCompatibility.ollamaEmbedRequest(request.body);
                return ollamaJsonResponse(TaiApiCompatibility.ollamaEmbedFromOpenAi(
                    manager.embeddings(embedRequest.toString()), embedRequest.optString("model", "")));
            } else if ("POST".equals(request.method) && "/api/embeddings".equals(request.path)) {
                return ollamaJsonResponse(legacyOllamaEmbeddings(context, request.body));
            } else if ("POST".equals(request.method) && ("/api/pull".equals(request.path)
                    || "/api/create".equals(request.path) || "/api/push".equals(request.path)
                    || "/api/copy".equals(request.path) || "/api/delete".equals(request.path))) {
                return ollamaJsonResponse(TaiApiCompatibility.ollamaError(501, "unsupported_registry_operation",
                    "Ollama registry operations do not map to LiteRT-LM/MNN packages; use the model import flow in settings."));
            } else if ("POST".equals(request.method) && "/v1/apps/launch".equals(request.path)) {
                return jsonResponse(runAppLaunch(context, request.body));
            } else if ("POST".equals(request.method) && "/v1/auth/rotate".equals(request.path)) {
                return jsonResponse(rotateAuthToken(context, false));
            } else if ("GET".equals(request.method) && "/v1/ai/status".equals(request.path)) {
                return maybeTextResponse(request, "status", TaiManager.getInstance(context).status());
            } else if ("GET".equals(request.method) && "/v1/ai/runtime".equals(request.path)) {
                return maybeTextResponse(request, "runtime", TaiManager.getInstance(context).runtimeStatus());
            } else if ("GET".equals(request.method) && "/v1/ai/models".equals(request.path)) {
                return maybeTextResponse(request, "models", TaiManager.getInstance(context).models());
            } else if ("POST".equals(request.method) && "/v1/ai/models/import".equals(request.path)) {
                return maybeTextResponse(request, "import", TaiManager.getInstance(context).importModel(request.body));
            } else if ("POST".equals(request.method) && "/v1/ai/models/download".equals(request.path)) {
                return maybeTextResponse(request, "download", TaiManager.getInstance(context).downloadModel(request.body));
            } else if ("POST".equals(request.method) && "/v1/ai/models/download-catalog".equals(request.path)) {
                JSONObject body = request.body == null || request.body.trim().isEmpty() ? new JSONObject() : new JSONObject(request.body);
                return maybeTextResponse(request, "download", TaiManager.getInstance(context).downloadCatalogModel(body.optString("modelId", body.optString("model", ""))));
            } else if ("GET".equals(request.method) && "/v1/ai/models/downloads".equals(request.path)) {
                return maybeTextResponse(request, "downloads", TaiManager.getInstance(context).downloads());
            } else if ("POST".equals(request.method) && "/v1/ai/models/downloads/cancel".equals(request.path)) {
                return maybeTextResponse(request, "download", TaiManager.getInstance(context).cancelDownload(request.body));
            } else if ("POST".equals(request.method) && "/v1/ai/models/delete".equals(request.path)) {
                return maybeTextResponse(request, "delete", TaiManager.getInstance(context).deleteModel(request.body));
            } else if ("POST".equals(request.method) && "/v1/ai/models/load".equals(request.path)) {
                return maybeTextResponse(request, "load", TaiManager.getInstance(context).loadModel(request.body));
            } else if ("POST".equals(request.method) && "/v1/ai/runtime/load".equals(request.path)) {
                return maybeTextResponse(request, "load", TaiManager.getInstance(context).loadModel(request.body));
            } else if ("POST".equals(request.method) && "/v1/ai/runtime/preflight".equals(request.path)) {
                return maybeTextResponse(request, "preflight", TaiManager.getInstance(context).preflight(request.body));
            } else if ("POST".equals(request.method) && "/v1/ai/models/unload".equals(request.path)) {
                return maybeTextResponse(request, "unload", TaiManager.getInstance(context).unloadModel());
            } else if ("POST".equals(request.method) && "/v1/ai/runtime/unload".equals(request.path)) {
                return maybeTextResponse(request, "unload", TaiManager.getInstance(context).unloadModel());
            } else if ("POST".equals(request.method) && "/v1/ai/runtime/keep-warm".equals(request.path)) {
                return maybeTextResponse(request, "keep-warm", TaiManager.getInstance(context).keepWarmRuntime(request.body));
            } else if ("POST".equals(request.method) && "/v1/ai/runtime/cancel".equals(request.path)) {
                return maybeTextResponse(request, "cancel", TaiManager.getInstance(context).cancelRuntime());
            } else if ("GET".equals(request.method) && "/v1/models".equals(request.path)) {
                return jsonResponse(TaiManager.getInstance(context).openAiModels());
            } else if ("GET".equals(request.method) && isModelRetrievePath(request.path)) {
                return jsonResponse(retrieveModel(context, modelIdFromRetrievePath(request.path)));
            } else if ("POST".equals(request.method) && "/v1/chat/completions".equals(request.path)) {
                TaiManager manager = TaiManager.getInstance(context);
                if (manager.isStreamRequest(request.body)) {
                    return sseResponse(output -> writeChatCompletionStream(context, request.body, output));
                }
                return jsonResponse(TaiManager.getInstance(context).openAiChatCompletions(request.body));
            } else if ("POST".equals(request.method) && "/v1/responses".equals(request.path)) {
                TaiManager manager = TaiManager.getInstance(context);
                JSONObject chatRequest = TaiApiCompatibility.responsesRequestToChat(request.body);
                if (chatRequest.optBoolean("stream", false)) {
                    return sseResponse(output -> writeResponsesStream(context, chatRequest.toString(), output));
                }
                return jsonResponse(TaiApiCompatibility.responsesFromChat(manager.openAiChatCompletions(chatRequest.toString())));
            } else if ("POST".equals(request.method) && "/v1/completions".equals(request.path)) {
                TaiManager manager = TaiManager.getInstance(context);
                if (manager.isStreamRequest(request.body)) {
                    return sseResponse(output -> writeCompletionStream(context, request.body, output));
                }
                return jsonResponse(TaiManager.getInstance(context).openAiCompletions(request.body));
            } else if ("POST".equals(request.method) && "/v1/embeddings".equals(request.path)) {
                return jsonResponse(TaiManager.getInstance(context).embeddings(request.body));
            } else if ("POST".equals(request.method) && "/v1/audio/speech".equals(request.path)) {
                return jsonResponse(TaiManager.getInstance(context).openAiAudioSpeech(request.body));
            }

            JSONObject notFound = jsonError("not_found", "Unknown endpoint");
            notFound.put("_statusCode", 404);
            return request.path.startsWith("/api/") ? ollamaJsonResponse(notFound) : jsonResponse(notFound);
        } catch (Exception e) {
            JSONObject error = jsonError("internal_error", e.getMessage());
            try {
                error.put("_statusCode", 500);
                if (request.path.startsWith("/api/")) return ollamaJsonResponse(error);
            } catch (JSONException ignored) {
            }
            return jsonResponse(error);
        }
    }

    static boolean isModelRetrievePath(String path) {
        return path != null && path.startsWith("/v1/models/") && path.length() > "/v1/models/".length()
            && path.indexOf('/', "/v1/models/".length()) < 0;
    }

    static String modelIdFromRetrievePath(String path) {
        return path.substring("/v1/models/".length());
    }

    /**
     * OpenAI GET /v1/models/{id}: filter the list response down to the one model object.
     */
    private JSONObject retrieveModel(Context context, String modelId) throws JSONException {
        JSONObject models = TaiManager.getInstance(context).openAiModels();
        JSONArray data = models.optJSONArray("data");
        if (data != null) {
            for (int i = 0; i < data.length(); i++) {
                JSONObject model = data.optJSONObject(i);
                if (model != null && modelId.equals(model.optString("id", ""))) {
                    return model;
                }
            }
        }
        JSONObject error = jsonError("model_not_found", "Model '" + modelId + "' does not exist");
        error.put("_statusCode", 404);
        return error;
    }

    /**
     * Legacy Ollama POST /api/embeddings: {model, prompt} in, {embedding: [...]} out.
     */
    private JSONObject legacyOllamaEmbeddings(Context context, String body) throws JSONException {
        JSONObject request = body == null || body.trim().isEmpty() ? new JSONObject() : new JSONObject(body);
        JSONObject openAiRequest = new JSONObject();
        openAiRequest.put("model", request.optString("model", ""));
        openAiRequest.put("input", request.opt("prompt") == null ? "" : request.opt("prompt"));
        JSONObject openAiResponse = TaiManager.getInstance(context).embeddings(openAiRequest.toString());
        if (openAiResponse.has("error")) {
            return openAiResponse;
        }
        JSONArray data = openAiResponse.optJSONArray("data");
        JSONObject first = data != null && data.length() > 0 ? data.optJSONObject(0) : null;
        JSONObject response = new JSONObject();
        response.put("embedding", first != null && first.has("embedding") ? first.opt("embedding") : new JSONArray());
        return response;
    }

    /**
     * The one non-inference route kept from the pre-strip device bridge: `launcherctl launch`
     * has years of tmux configs and shell binds behind it, so app launching stays addressable
     * from the shell while everything else that was agent/MCP-shaped remains gone.
     */
    private JSONObject runAppLaunch(Context context, String body) throws JSONException {
        JSONObject request = body != null && !body.isEmpty() ? new JSONObject(body) : new JSONObject();
        String query = request.optString("query", "").trim();
        if (query.isEmpty()) {
            JSONObject error = jsonError("bad_request", "Missing app query");
            error.put("_statusCode", 400);
            return error;
        }

        List<LauncherAppEntry> apps = LauncherAppDataProvider.getInstance(context).getAllAppsBlocking();
        AppLaunchMatch match = resolveLaunchMatch(apps, query);
        if (match.entry == null) {
            JSONObject error = jsonError(match.errorCode, match.message);
            error.put("_statusCode", match.statusCode);
            error.put("query", query);
            if (match.candidates.length() > 0) {
                error.put("candidates", match.candidates);
            }
            return error;
        }

        boolean launched = LauncherAppLauncher.launchEntry(context, match.entry);
        if (!launched) {
            JSONObject error = jsonError("launch_failed", "Failed to start matched app");
            error.put("_statusCode", 500);
            error.put("query", query);
            error.put("label", match.entry.label);
            error.put("packageName", match.entry.appRef.packageName);
            error.put("activityName", match.entry.appRef.activityName);
            error.put("stableId", match.entry.appRef.stableId());
            error.put("userId", match.entry.appRef.userId);
            error.put("clonedProfile", match.entry.appRef.clonedProfile);
            return error;
        }

        JSONObject data = new JSONObject();
        data.put("ok", true);
        data.put("query", query);
        data.put("label", match.entry.label);
        data.put("packageName", match.entry.appRef.packageName);
        data.put("activityName", match.entry.appRef.activityName);
        data.put("stableId", match.entry.appRef.stableId());
        data.put("userId", match.entry.appRef.userId);
        data.put("clonedProfile", match.entry.appRef.clonedProfile);
        return data;
    }

    static AppLaunchMatch resolveLaunchMatch(List<LauncherAppEntry> apps, String query) throws JSONException {
        String trimmed = query == null ? "" : query.trim();
        String lowerQuery = trimmed.toLowerCase(Locale.US);
        String normalizedQuery = normalizeLookupValue(trimmed);
        if (lowerQuery.isEmpty()) {
            return AppLaunchMatch.error(400, "bad_request", "Missing app query");
        }

        List<AppSearchCandidate> matches = new ArrayList<>();
        for (LauncherAppEntry entry : apps) {
            int tier = matchTier(entry, lowerQuery, normalizedQuery);
            if (tier >= 0) {
                matches.add(new AppSearchCandidate(entry, tier));
            }
        }

        if (matches.isEmpty()) {
            return AppLaunchMatch.error(404, "not_found", "No launcher app matched query");
        }

        Collections.sort(matches, new Comparator<AppSearchCandidate>() {
            @Override
            public int compare(AppSearchCandidate a, AppSearchCandidate b) {
                if (a.tier != b.tier) return Integer.compare(a.tier, b.tier);
                int labelCompare = a.entry.label.compareToIgnoreCase(b.entry.label);
                if (labelCompare != 0) return labelCompare;
                return a.entry.appRef.packageName.compareToIgnoreCase(b.entry.appRef.packageName);
            }
        });

        AppSearchCandidate best = matches.get(0);
        List<AppSearchCandidate> bestTierMatches = new ArrayList<>();
        for (AppSearchCandidate candidate : matches) {
            if (candidate.tier != best.tier) break;
            bestTierMatches.add(candidate);
        }

        if (bestTierMatches.size() == 1) {
            return AppLaunchMatch.success(best.entry);
        }

        JSONArray candidates = new JSONArray();
        for (int i = 0; i < bestTierMatches.size() && i < 8; i++) {
            LauncherAppEntry entry = bestTierMatches.get(i).entry;
            JSONObject item = new JSONObject();
            item.put("label", entry.label);
            item.put("packageName", entry.appRef.packageName);
            item.put("activityName", entry.appRef.activityName);
            item.put("stableId", entry.appRef.stableId());
            item.put("userId", entry.appRef.userId);
            item.put("clonedProfile", entry.appRef.clonedProfile);
            candidates.put(item);
        }
        return AppLaunchMatch.error(409, "ambiguous", "Multiple launcher apps matched query", candidates);
    }

    private static int matchTier(LauncherAppEntry entry, String lowerQuery, String normalizedQuery) {
        String label = entry.label == null ? "" : entry.label;
        String labelLower = label.toLowerCase(Locale.US);
        String labelNormalized = normalizeLookupValue(label);
        String packageName = entry.appRef.packageName.toLowerCase(Locale.US);
        String activityName = entry.appRef.activityName.toLowerCase(Locale.US);
        String stableId = entry.appRef.stableId().toLowerCase(Locale.US);

        if (packageName.equals(lowerQuery) || activityName.equals(lowerQuery) || stableId.equals(lowerQuery)) {
            return 0;
        }
        if (labelLower.equals(lowerQuery) || (!normalizedQuery.isEmpty() && labelNormalized.equals(normalizedQuery))) {
            return 1;
        }
        if (packageName.startsWith(lowerQuery) || activityName.startsWith(lowerQuery)) {
            return 2;
        }
        if (labelLower.startsWith(lowerQuery) || (!normalizedQuery.isEmpty() && labelNormalized.startsWith(normalizedQuery))) {
            return 3;
        }
        if (!normalizedQuery.isEmpty()) {
            String[] words = labelNormalized.split(" ");
            for (String word : words) {
                if (word.startsWith(normalizedQuery)) {
                    return 4;
                }
            }
        }
        if (packageName.contains(lowerQuery) || activityName.contains(lowerQuery)) {
            return 5;
        }
        if (labelLower.contains(lowerQuery) || (!normalizedQuery.isEmpty() && labelNormalized.contains(normalizedQuery))) {
            return 6;
        }
        return -1;
    }

    private static String normalizeLookupValue(String value) {
        if (value == null || value.isEmpty()) return "";
        StringBuilder normalized = new StringBuilder(value.length());
        boolean previousWasSpace = true;
        for (int i = 0; i < value.length(); i++) {
            char c = Character.toLowerCase(value.charAt(i));
            if (Character.isLetterOrDigit(c)) {
                normalized.append(c);
                previousWasSpace = false;
            } else if (!previousWasSpace) {
                normalized.append(' ');
                previousWasSpace = true;
            }
        }
        int length = normalized.length();
        if (length > 0 && normalized.charAt(length - 1) == ' ') {
            normalized.setLength(length - 1);
        }
        return normalized.toString();
    }

    private JSONObject rotateAuthToken(Context context, boolean includeToken) throws JSONException {
        token = new TaiSettings(context).rotateApiToken(random);
        try {
            writeClientConfig();
        } catch (IOException e) {
            JSONObject error = jsonError("rotate_failed", "Failed to persist rotated token: " + e.getMessage());
            error.put("_statusCode", 500);
            return error;
        }
        JSONObject data = new JSONObject();
        data.put("ok", true);
        data.put("rotated", true);
        data.put("endpoint", buildEndpointSettings(context, includeToken));
        return data;
    }

    private boolean isAuthorized(Map<String, String> headers) {
        return isAuthorized(token, headers);
    }

    static boolean isAuthorized(String expectedToken, Map<String, String> headers) {
        if (expectedToken == null || expectedToken.isEmpty()) return false;
        if (headers == null) return false;
        String value = headers.get("authorization");
        if (value != null) {
            String prefix = "Bearer ";
            if (value.startsWith(prefix)) {
                return secureEquals(expectedToken, value.substring(prefix.length()).trim());
            }
        }
        String apiKey = headers.get("x-api-key");
        if (apiKey != null) {
            return secureEquals(expectedToken, apiKey.trim());
        }
        return false;
    }

    private boolean allowRequest(HttpRequest request) {
        SimpleRateLimiter limiter = rateLimiterFor(request);
        return limiter == null || limiter.allow();
    }

    private SimpleRateLimiter rateLimiterFor(HttpRequest request) {
        return rateLimiters.get(request.method + ":" + request.path);
    }

    private static HttpResponse withRateLimitHeaders(HttpResponse response, SimpleRateLimiter limiter, long retryAfterSeconds) {
        Map<String, String> headers = response.headers != null ? new HashMap<>(response.headers) : new HashMap<>();
        headers.put("Retry-After", Long.toString(retryAfterSeconds));
        if (limiter != null) {
            headers.put("RateLimit-Limit", Integer.toString(limiter.maxRequests));
            headers.put("RateLimit-Remaining", "0");
            headers.put("RateLimit-Reset", Long.toString(retryAfterSeconds));
        }
        return new HttpResponse(response.statusCode, response.contentType, response.body, headers);
    }

    private HttpRequest parseRequest(InputStream input) throws IOException, HttpParseException {
        String requestLine = readLine(input, MAX_REQUEST_LINE_BYTES);
        if (requestLine == null || requestLine.isEmpty()) {
            throw new HttpParseException(400, "bad_request", "Missing request line");
        }

        String[] lineParts = requestLine.split(" ");
        if (lineParts.length < 2) {
            throw new HttpParseException(400, "bad_request", "Malformed request line");
        }

        HttpRequest request = new HttpRequest();
        request.method = lineParts[0].trim();
        request.target = lineParts[1].trim();
        int queryStart = request.target.indexOf('?');
        request.path = requestPathFromTarget(request.target);
        request.query = queryStart < 0 ? "" : request.target.substring(queryStart + 1);
        request.headers = new HashMap<>();

        int headerCount = 0;
        String line;
        while ((line = readLine(input, MAX_HEADER_LINE_BYTES)) != null) {
            if (line.isEmpty()) break;
            headerCount++;
            if (headerCount > MAX_HEADER_LINES) {
                throw new HttpParseException(400, "bad_request", "Too many headers");
            }
            int index = line.indexOf(':');
            if (index <= 0) continue;
            String key = line.substring(0, index).trim().toLowerCase();
            String value = line.substring(index + 1).trim();
            request.headers.put(key, value);
        }

        int contentLength = 0;
        try {
            String value = request.headers.get("content-length");
            if (value != null) contentLength = Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
        }

        if (contentLength < 0) {
            throw new HttpParseException(400, "bad_request", "Invalid content length");
        }
        if (contentLength > MAX_BODY_BYTES) {
            throw new HttpParseException(413, "payload_too_large", "Request body too large");
        }

        if (contentLength > 0) {
            byte[] bodyBytes = readBytes(input, contentLength);
            if (bodyBytes.length != contentLength) {
                throw new HttpParseException(400, "bad_request", "Incomplete request body");
            }
            request.body = new String(bodyBytes, StandardCharsets.UTF_8);
        } else {
            request.body = "";
        }

        return request;
    }

    static String requestPathFromTarget(@NonNull String target) {
        int queryStart = target.indexOf('?');
        return queryStart < 0 ? target : target.substring(0, queryStart);
    }

    private String readLine(InputStream input, int maxBytes) throws IOException, HttpParseException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int prev = -1;
        while (true) {
            int b = input.read();
            if (b == -1) break;
            if (b == '\n') break;
            if (prev == '\r') {
                buffer.write('\r');
            }
            if (b != '\r') {
                buffer.write(b);
            }
            if (buffer.size() > maxBytes) {
                throw new HttpParseException(413, "payload_too_large", "Header line too large");
            }
            prev = b;
        }
        if (buffer.size() == 0 && prev == -1) return null;
        return buffer.toString(StandardCharsets.UTF_8.name()).trim();
    }

    private byte[] readBytes(InputStream input, int length) throws IOException {
        byte[] data = new byte[length];
        int offset = 0;
        while (offset < length) {
            int read = input.read(data, offset, length - offset);
            if (read < 0) break;
            offset += read;
        }
        if (offset == length) return data;
        byte[] trimmed = new byte[offset];
        System.arraycopy(data, 0, trimmed, 0, offset);
        return trimmed;
    }

    private void writeJsonResponse(OutputStream output, int statusCode, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        writeResponse(output, new HttpResponse(statusCode, "application/json; charset=utf-8", bytes, null));
    }

    static void writeResponse(OutputStream output, HttpResponse response) throws IOException {
        writeResponse(output, response, null);
    }

    /**
     * @param allowedOrigin the request Origin when it passed {@link #isAllowedOrigin(String)}, which
     *                      is echoed back as the CORS grant. {@code null} (a non-browser caller)
     *                      emits no CORS header at all, so a wildcard grant never escapes to a
     *                      remote page.
     */
    static void writeResponse(OutputStream output, HttpResponse response, String allowedOrigin) throws IOException {
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8));
        byte[] bytes = response.body;
        writer.write("HTTP/1.1 " + response.statusCode + " " + statusMessage(response.statusCode) + "\r\n");
        writer.write("Content-Type: " + response.contentType + "\r\n");
        writer.write("Connection: close\r\n");
        writer.write("Vary: Origin\r\n");
        if (allowedOrigin != null && !allowedOrigin.trim().isEmpty()) {
            writer.write("Access-Control-Allow-Origin: " + allowedOrigin.trim() + "\r\n");
            writer.write("Access-Control-Allow-Credentials: true\r\n");
        }
        if (response.bodyWriter == null) {
            writer.write("Content-Length: " + bytes.length + "\r\n");
        }
        if (response.headers != null) {
            for (Map.Entry<String, String> entry : response.headers.entrySet()) {
                writer.write(entry.getKey() + ": " + entry.getValue() + "\r\n");
            }
        }
        writer.write("\r\n");
        writer.flush();
        if (response.bodyWriter != null) {
            response.bodyWriter.write(output);
            output.flush();
            return;
        }
        output.write(bytes);
        output.flush();
    }

    private static String statusMessage(int code) {
        switch (code) {
            case 200: return "OK";
            case 409: return "Conflict";
            case 501: return "Not Implemented";
            case 400: return "Bad Request";
            case 401: return "Unauthorized";
            case 403: return "Forbidden";
            case 404: return "Not Found";
            case 413: return "Payload Too Large";
            case 429: return "Too Many Requests";
            case 499: return "Client Closed Request";
            default: return "Internal Server Error";
        }
    }

    private JSONObject jsonError(String code, String message) {
        JSONObject error = new JSONObject();
        try {
            error.put("ok", false);
            error.put("error", code);
            error.put("message", message == null ? "" : message);
            withOpenAiErrorEnvelope(error);
        } catch (JSONException ignored) {
        }
        return error;
    }

    static HttpResponse unauthorizedResponse() {
        JSONObject error = new JSONObject();
        try {
            error.put("ok", false);
            error.put("error", "unauthorized");
            error.put("message", "Missing or invalid token");
            withOpenAiErrorEnvelope(error, 401);
        } catch (JSONException ignored) {
        }
        return new HttpResponse(401, "application/json; charset=utf-8", error.toString().getBytes(StandardCharsets.UTF_8), null);
    }

    static HttpResponse ollamaUnauthorizedResponse() {
        JSONObject error = new JSONObject();
        try {
            error.put("error", "Missing or invalid token");
            error.put("_statusCode", 401);
            return ollamaJsonResponse(error);
        } catch (JSONException e) {
            return new HttpResponse(401, "application/json; charset=utf-8",
                "{\"error\":\"Missing or invalid token\"}".getBytes(StandardCharsets.UTF_8), null);
        }
    }

    /**
     * Adds the OpenAI SDK error object while retaining the legacy flat envelope under {@code tai}
     * and as top-level compatibility aliases. A JSON key cannot simultaneously be a string and an
     * object, so the former flat {@code error} value is also exposed as {@code code/error_code}.
     */
    @NonNull
    static JSONObject withOpenAiErrorEnvelope(@NonNull JSONObject response) throws JSONException {
        return withOpenAiErrorEnvelope(response, response.optInt("_statusCode", 400));
    }

    @NonNull
    static JSONObject withOpenAiErrorEnvelope(@NonNull JSONObject response, int statusCode) throws JSONException {
        Object existing = response.opt("error");
        if (existing instanceof JSONObject) {
            JSONObject nested = (JSONObject) existing;
            String message = response.optString("message", nested.optString("message", "Request failed"));
            String code = nested.optString("code", response.optString("code", "api_error"));
            response.put("ok", response.optBoolean("ok", false));
            response.put("message", message);
            response.put("code", code);
            response.put("error_code", code);
            nested.put("type", openAiErrorType(statusCode));
            return response;
        }

        String code = existing == null || JSONObject.NULL.equals(existing)
            ? response.optString("code", "api_error") : String.valueOf(existing);
        String message = response.optString("message", "Request failed");
        JSONObject legacy = new JSONObject(response.toString());
        JSONObject nested = new JSONObject();
        nested.put("message", message);
        nested.put("type", openAiErrorType(statusCode));
        nested.put("code", code);
        response.put("ok", false);
        response.put("message", message);
        response.put("code", code);
        response.put("error_code", code);
        response.put("error", nested);
        if (!response.has("tai")) response.put("tai", legacy);
        return response;
    }

    private static String openAiErrorType(int statusCode) {
        if (statusCode == 401) return "authentication_error";
        if (statusCode == 403) return "permission_error";
        if (statusCode == 429) return "rate_limit_error";
        if (statusCode >= 500) return "api_error";
        return "invalid_request_error";
    }

    private void initializeRateLimiters() {
        rateLimiters.clear();
        rateLimiters.put("POST:/v1/apps/launch", new SimpleRateLimiter(30, 60_000));
        rateLimiters.put("POST:/v1/auth/rotate", new SimpleRateLimiter(5, 60_000));
        rateLimiters.put("GET:/v1/ai/status", new SimpleRateLimiter(120, 60_000));
        rateLimiters.put("GET:/v1/ai/runtime", new SimpleRateLimiter(120, 60_000));
        rateLimiters.put("GET:/v1/ai/models", new SimpleRateLimiter(120, 60_000));
        rateLimiters.put("POST:/v1/ai/models/import", new SimpleRateLimiter(20, 60_000));
        rateLimiters.put("POST:/v1/ai/models/download", new SimpleRateLimiter(20, 60_000));
        rateLimiters.put("POST:/v1/ai/models/download-catalog", new SimpleRateLimiter(20, 60_000));
        rateLimiters.put("POST:/v1/ai/models/downloads/cancel", new SimpleRateLimiter(30, 60_000));
        rateLimiters.put("GET:/v1/ai/models/downloads", new SimpleRateLimiter(120, 60_000));
        rateLimiters.put("POST:/v1/ai/models/delete", new SimpleRateLimiter(30, 60_000));
        rateLimiters.put("POST:/v1/ai/models/load", new SimpleRateLimiter(20, 60_000));
        rateLimiters.put("POST:/v1/ai/models/unload", new SimpleRateLimiter(60, 60_000));
        rateLimiters.put("POST:/v1/ai/runtime/load", new SimpleRateLimiter(20, 60_000));
        rateLimiters.put("POST:/v1/ai/runtime/preflight", new SimpleRateLimiter(60, 60_000));
        rateLimiters.put("POST:/v1/ai/runtime/unload", new SimpleRateLimiter(60, 60_000));
        rateLimiters.put("POST:/v1/ai/runtime/keep-warm", new SimpleRateLimiter(60, 60_000));
        rateLimiters.put("POST:/v1/ai/runtime/cancel", new SimpleRateLimiter(60, 60_000));
        rateLimiters.put("GET:/v1/models", new SimpleRateLimiter(120, 60_000));
        rateLimiters.put("POST:/v1/chat/completions", new SimpleRateLimiter(60, 60_000));
        rateLimiters.put("POST:/v1/responses", new SimpleRateLimiter(60, 60_000));
        rateLimiters.put("POST:/v1/completions", new SimpleRateLimiter(60, 60_000));
        rateLimiters.put("POST:/v1/embeddings", new SimpleRateLimiter(60, 60_000));
        rateLimiters.put("POST:/v1/audio/speech", new SimpleRateLimiter(60, 60_000));
        rateLimiters.put("GET:/api/version", new SimpleRateLimiter(120, 60_000));
        rateLimiters.put("GET:/api/tags", new SimpleRateLimiter(120, 60_000));
        rateLimiters.put("POST:/api/show", new SimpleRateLimiter(120, 60_000));
        rateLimiters.put("GET:/api/ps", new SimpleRateLimiter(120, 60_000));
        rateLimiters.put("POST:/api/chat", new SimpleRateLimiter(60, 60_000));
        rateLimiters.put("POST:/api/generate", new SimpleRateLimiter(60, 60_000));
        rateLimiters.put("POST:/api/embed", new SimpleRateLimiter(60, 60_000));
        rateLimiters.put("POST:/api/embeddings", new SimpleRateLimiter(60, 60_000));
    }

    private void writeClientConfig() throws IOException {
        if (token == null || token.isEmpty()) {
            Context context = appContext;
            if (context != null) token = new TaiSettings(context).getOrCreateApiToken();
            if (token == null || token.isEmpty()) token = TaiSettings.generateApiToken(random);
        }
        File launcherctlDir = new File(LAUNCHERCTL_DIR_PATH);
        if (!launcherctlDir.exists() && !launcherctlDir.mkdirs()) {
            throw new IOException("Failed to create launcherctl dir: " + LAUNCHERCTL_DIR_PATH);
        }
        writeTextFile(TOKEN_FILE_PATH, token + "\n");
        TaiSettings settings = appContext != null ? new TaiSettings(appContext) : null;
        StringBuilder endpoint = new StringBuilder();
        endpoint.append(localhostBaseUrl(port)).append("\n");
        if (settings != null && TaiSettings.BIND_MODE_LAN.equals(settings.getApiBindMode())) {
            endpoint.append(lanBaseUrl(port)).append("\n");
        }
        writeTextFile(ENDPOINT_FILE_PATH, endpoint.toString());
    }

    static ServerSocket createLoopbackServerSocket(int preferredPort, String bindMode) throws IOException {
        IOException preferredPortFailure = null;
        if (preferredPort > 0) {
            try {
                return bindApiAddress(preferredPort, bindMode);
            } catch (IOException e) {
                preferredPortFailure = e;
                Logger.logWarn(LOG_TAG, "Preferred LauncherCtl API port " + preferredPort + " unavailable; falling back to an ephemeral port: " + e.getMessage());
            }
        }
        try {
            return bindApiAddress(0, bindMode);
        } catch (IOException e) {
            if (preferredPortFailure != null) e.addSuppressed(preferredPortFailure);
            throw e;
        }
    }

    private static ServerSocket bindApiAddress(int requestedPort, String bindMode) throws IOException {
        ServerSocket socket = new ServerSocket();
        socket.setReuseAddress(true);
        socket.bind(new InetSocketAddress(InetAddress.getByName(bindAddressForMode(bindMode)), requestedPort), 16);
        return socket;
    }

    static String bindAddressForMode(String bindMode) {
        return TaiSettings.BIND_MODE_LAN.equals(TaiSettings.normalizeApiBindMode(bindMode)) ? "0.0.0.0" : "127.0.0.1";
    }

    private JSONObject buildEndpointSettings(Context context, boolean includeToken) throws JSONException {
        TaiSettings settings = new TaiSettings(context);
        JSONObject data = new JSONObject();
        int configuredPort = settings.getApiPort();
        int activePort = port > 0 ? port : configuredPort;
        String bindMode = settings.getApiBindMode();
        String baseUrl = localhostBaseUrl(activePort);
        data.put("configuredPort", configuredPort);
        data.put("activePort", activePort);
        data.put("bindMode", bindMode);
        data.put("baseUrl", baseUrl);
        data.put("openAiBaseUrl", baseUrl + "/v1");
        data.put("ollamaBaseUrl", baseUrl);
        data.put("authRequired", effectiveAuthRequired(settings));
        data.put("authRequiredPreference", settings.isApiAuthRequired());
        data.put("tokenRequired", effectiveAuthRequired(settings));
        if (TaiSettings.BIND_MODE_LAN.equals(bindMode)) {
            data.put("baseUrlLan", lanBaseUrl(activePort));
            data.put("lanWarning", LAN_WARNING);
        }
        data.put("endpointFile", ENDPOINT_FILE_PATH);
        data.put("tokenFile", TOKEN_FILE_PATH);
        data.put("running", running);
        data.put("usingConfiguredPort", activePort == configuredPort);
        data.put("tokenConfigured", TaiSettings.isValidApiToken(settings.getOrCreateApiToken()));
        JSONArray supportedEndpoints = new JSONArray();
        supportedEndpoints.put("/v1/models");
        supportedEndpoints.put("/v1/models/{id}");
        supportedEndpoints.put("/v1/chat/completions");
        supportedEndpoints.put("/v1/responses");
        supportedEndpoints.put("/v1/completions");
        supportedEndpoints.put("/v1/embeddings");
        supportedEndpoints.put("/v1/audio/speech");
        supportedEndpoints.put("/v1/apps/launch");
        supportedEndpoints.put("/api/version");
        supportedEndpoints.put("/api/tags");
        supportedEndpoints.put("/api/show");
        supportedEndpoints.put("/api/chat");
        supportedEndpoints.put("/api/generate");
        supportedEndpoints.put("/api/ps");
        supportedEndpoints.put("/api/embed");
        supportedEndpoints.put("/api/embeddings");
        data.put("supportedEndpoints", supportedEndpoints);
        data.put("embeddingsNote", "Embeddings support is model-capability dependent; check /v1/models _capabilities for text_embeddings.");
        data.put("audioOutputNote", "Audio output returns an explicit unsupported_audio_output error until a local runner exposes generated audio.");
        data.put("modelFormatNote", "TAI supports LiteRT-LM and MNN model packages only; GGUF/raw weights are not supported by this APK.");
        if (includeToken) {
            data.put("token", settings.getOrCreateApiToken());
        }
        return data;
    }

    static String localhostBaseUrl(int activePort) {
        return "http://127.0.0.1:" + activePort;
    }

    static String lanBaseUrl(int activePort) {
        return "http://" + lanAddressHost() + ":" + activePort;
    }

    private static String lanAddressHost() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces != null && interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();
                if (!networkInterface.isUp() || networkInterface.isLoopback()) continue;
                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (address.isLoopbackAddress() || address.isAnyLocalAddress()) continue;
                    String host = address.getHostAddress();
                    if (host != null && host.indexOf(':') < 0) {
                        return host;
                    }
                }
            }
        } catch (SocketException ignored) {
        }
        return "0.0.0.0";
    }

    private void installTaiCliScripts() {
        File loginBinary = new File(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + "/login");
        if (!loginBinary.exists()) {
            Logger.logInfo(LOG_TAG, "Skipping TAI CLI install until bootstrap is initialized.");
            return;
        }

        String taiScript =
            "#!" + TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + "/sh\n" +
            "set -eu\n" +
            "print_help() {\n" +
            "  cat <<'EOF'\n" +
            "TAI / Termux AI - local multi-backend model host\n" +
            "\n" +
            "Usage:\n" +
            "  tai --json <command>\n" +
            "  tai status\n" +
            "  tai runtime\n" +
            "  tai models\n" +
            "  tai import <path> [model-id]\n" +
            "  tai download <model-id> <https-url> --accept-terms\n" +
            "  tai downloads\n" +
            "  tai download-cancel <model-id>\n" +
            "  tai delete <model-id>\n" +
            "  tai preflight [model] [--auto|--cpu|--gpu]\n" +
            "  tai load [model] [--auto|--cpu|--gpu]\n" +
            "  tai unload\n" +
            "  tai keep-warm [model] [--minutes N] [--auto|--cpu|--gpu]\n" +
            "  tai cancel\n" +
            "  tai doctor\n" +
            "\n" +
            "TAI is authenticated through ~/.launcherctl and runs native AI in the isolated :tai_runtime process.\n" +
            "LiteRT-LM and MNN load in :tai_runtime after ABI/API/library/model/memory preflight.\n" +
            "MNN models route through the bundled MNN backend when supported by the installed APK.\n" +
            "GGUF/raw weight files are not supported by this APK.\n" +
            "Auto defaults to CPU on unknown devices; GPU is used automatically only after a successful device/model history.\n" +
            "OpenAI-compatible endpoints (default bind mode is localhost):\n" +
            "  /v1/models\n" +
            "  /v1/chat/completions\n" +
            "  /v1/completions\n" +
            "  /v1/embeddings\n" +
            "  /v1/audio/speech\n" +
            "Ollama-compatible endpoints: /api/tags /api/chat /api/generate /api/embed /api/embeddings /api/show /api/ps /api/version\n" +
            "\n" +
            "Point OpenAI-compatible terminal tools at this host, e.g.:\n" +
            "  export OPENAI_BASE_URL=http://127.0.0.1:<port>/v1\n" +
            "  export OPENAI_API_KEY=<your-token>\n" +
            "The actual token is stored at ~/.launcherctl/token (do not echo it into shell history).\n" +
            "If token authentication is disabled in settings, any placeholder API key works on localhost.\n" +
            "\n" +
            "Security notes:\n" +
            "  LAN mode (opt-in via settings) exposes the API to your local network and always requires the token.\n" +
            "  /v1/embeddings is model-capability dependent. Not all models support embeddings.\n" +
            "  /v1/audio/speech returns unsupported_audio_output until a local runner exposes generated audio.\n" +
            "  Check /v1/models for capability metadata (for example, _backend and _capabilities per model).\n" +
            "\n" +
            "Use tai --json <command> for raw API JSON.\n" +
            "EOF\n" +
            "}\n" +
            "LAUNCHERCTL_DIR=\"$HOME/.launcherctl\"\n" +
            "TOKEN_FILE=\"$LAUNCHERCTL_DIR/token\"\n" +
            "ENDPOINT_FILE=\"$LAUNCHERCTL_DIR/endpoint\"\n" +
            "if [ ! -r \"$TOKEN_FILE\" ] || [ ! -r \"$ENDPOINT_FILE\" ]; then\n" +
            "  echo \"tai: missing $TOKEN_FILE or $ENDPOINT_FILE; start Termux Launcher first\" >&2\n" +
            "  exit 1\n" +
            "fi\n" +
            "TOKEN=$(cat \"$TOKEN_FILE\")\n" +
            "BASE=$(sed -n '1p' \"$ENDPOINT_FILE\")\n" +
            "CURL_COMMON=\"--fail-with-body -sS --connect-timeout 2 --max-time 180\"\n" +
            "json_escape() { printf '%s' \"$1\" | sed 's/\\\\/\\\\\\\\/g; s/\"/\\\\\"/g'; }\n" +
            "post_json() {\n" +
            "  path=\"$1\"\n" +
            "  data=\"$2\"\n" +
            "  if [ \"$OUTPUT_MODE\" = \"text\" ]; then\n" +
            "    curl $CURL_COMMON -X POST -H \"Authorization: Bearer $TOKEN\" -H \"Content-Type: application/json\" -H \"X-TAI-Output: text\" --data \"$data\" \"$BASE$path\"\n" +
            "  else\n" +
            "    curl $CURL_COMMON -X POST -H \"Authorization: Bearer $TOKEN\" -H \"Content-Type: application/json\" --data \"$data\" \"$BASE$path\"\n" +
            "  fi\n" +
            "}\n" +
            "get_json() {\n" +
            "  path=\"$1\"\n" +
            "  if [ \"$OUTPUT_MODE\" = \"text\" ]; then\n" +
            "    curl $CURL_COMMON -H \"Authorization: Bearer $TOKEN\" -H \"X-TAI-Output: text\" \"$BASE$path\"\n" +
            "  else\n" +
            "    curl $CURL_COMMON -H \"Authorization: Bearer $TOKEN\" \"$BASE$path\"\n" +
            "  fi\n" +
            "}\n" +
            "OUTPUT_MODE=text\n" +
            "case \"${1:-}\" in\n" +
            "  --json|-j)\n" +
            "    OUTPUT_MODE=json\n" +
            "    shift || true\n" +
            "    ;;\n" +
            "esac\n" +
            "cmd=\"${1:-status}\"\n" +
            "shift || true\n" +
            "case \"$cmd\" in\n" +
            "  -h|--help|help)\n" +
            "    print_help\n" +
            "    ;;\n" +
            "  status)\n" +
            "    get_json /v1/ai/status\n" +
            "    ;;\n" +
            "  runtime)\n" +
            "    get_json /v1/ai/runtime\n" +
            "    ;;\n" +
            "  models)\n" +
            "    get_json /v1/ai/models\n" +
            "    ;;\n" +
            "  import)\n" +
            "    [ \"$#\" -gt 0 ] || { echo \"usage: tai import <path> [model-id]\" >&2; exit 2; }\n" +
            "    path=$(json_escape \"$1\")\n" +
            "    model=\"${2:-}\"\n" +
            "    if [ -n \"$model\" ]; then model=$(json_escape \"$model\"); post_json /v1/ai/models/import \"{\\\"path\\\":\\\"$path\\\",\\\"modelId\\\":\\\"$model\\\"}\"; else post_json /v1/ai/models/import \"{\\\"path\\\":\\\"$path\\\"}\"; fi\n" +
            "    ;;\n" +
            "  download)\n" +
            "    [ \"$#\" -ge 3 ] || { echo \"usage: tai download <model-id> <https-url> --accept-terms\" >&2; exit 2; }\n" +
            "    model=$(json_escape \"$1\")\n" +
            "    url=$(json_escape \"$2\")\n" +
            "    [ \"${3:-}\" = \"--accept-terms\" ] || { echo \"tai download: pass --accept-terms after reviewing the provider license/terms\" >&2; exit 2; }\n" +
            "    post_json /v1/ai/models/download \"{\\\"modelId\\\":\\\"$model\\\",\\\"url\\\":\\\"$url\\\",\\\"acceptedTerms\\\":true}\"\n" +
            "    ;;\n" +
            "  downloads)\n" +
            "    get_json /v1/ai/models/downloads\n" +
            "    ;;\n" +
            "  download-cancel)\n" +
            "    [ \"$#\" -gt 0 ] || { echo \"usage: tai download-cancel <model-id>\" >&2; exit 2; }\n" +
            "    model=$(json_escape \"$1\")\n" +
            "    post_json /v1/ai/models/downloads/cancel \"{\\\"modelId\\\":\\\"$model\\\"}\"\n" +
            "    ;;\n" +
            "  delete)\n" +
            "    [ \"$#\" -gt 0 ] || { echo \"usage: tai delete <model-id>\" >&2; exit 2; }\n" +
            "    model=$(json_escape \"$1\")\n" +
            "    post_json /v1/ai/models/delete \"{\\\"modelId\\\":\\\"$model\\\"}\"\n" +
            "    ;;\n" +
            "  preflight)\n" +
            "    model=\"\"\n" +
            "    accelerator=\"\"\n" +
            "    while [ \"$#\" -gt 0 ]; do\n" +
            "      case \"$1\" in\n" +
            "        --auto) accelerator=auto ;;\n" +
            "        --cpu) accelerator=cpu ;;\n" +
            "        --gpu) accelerator=gpu ;;\n" +
            "        --*) echo \"usage: tai preflight [model] [--auto|--cpu|--gpu]\" >&2; exit 2 ;;\n" +
            "        *) [ -z \"$model\" ] || { echo \"usage: tai preflight [model] [--auto|--cpu|--gpu]\" >&2; exit 2; }; model=\"$1\" ;;\n" +
            "      esac\n" +
            "      shift\n" +
            "    done\n" +
            "    accel_json=\"\"\n" +
            "    if [ -n \"$accelerator\" ]; then accel_json=\",\\\"accelerator\\\":\\\"$accelerator\\\"\"; fi\n" +
            "    if [ -n \"$model\" ]; then model_escaped=$(json_escape \"$model\"); post_json /v1/ai/runtime/preflight \"{\\\"model\\\":\\\"$model_escaped\\\"$accel_json}\"; elif [ -n \"$accelerator\" ]; then post_json /v1/ai/runtime/preflight \"{\\\"accelerator\\\":\\\"$accelerator\\\"}\"; else post_json /v1/ai/runtime/preflight '{}'; fi\n" +
            "    ;;\n" +
            "  load)\n" +
            "    model=\"\"\n" +
            "    accelerator=\"\"\n" +
            "    while [ \"$#\" -gt 0 ]; do\n" +
            "      case \"$1\" in\n" +
            "        --auto) accelerator=auto ;;\n" +
            "        --cpu) accelerator=cpu ;;\n" +
            "        --gpu) accelerator=gpu ;;\n" +
            "        --*) echo \"usage: tai load [model] [--auto|--cpu|--gpu]\" >&2; exit 2 ;;\n" +
            "        *) [ -z \"$model\" ] || { echo \"usage: tai load [model] [--auto|--cpu|--gpu]\" >&2; exit 2; }; model=\"$1\" ;;\n" +
            "      esac\n" +
            "      shift\n" +
            "    done\n" +
            "    accel_json=\"\"\n" +
            "    if [ -n \"$accelerator\" ]; then accel_json=\",\\\"accelerator\\\":\\\"$accelerator\\\"\"; fi\n" +
            "    if [ -n \"$model\" ]; then model_escaped=$(json_escape \"$model\"); post_json /v1/ai/runtime/load \"{\\\"model\\\":\\\"$model_escaped\\\"$accel_json}\"; elif [ -n \"$accelerator\" ]; then post_json /v1/ai/runtime/load \"{\\\"accelerator\\\":\\\"$accelerator\\\"}\"; else post_json /v1/ai/runtime/load '{}'; fi\n" +
            "    ;;\n" +
            "  unload)\n" +
            "    post_json /v1/ai/runtime/unload '{}'\n" +
            "    ;;\n" +
            "  keep-warm)\n" +
            "    model=\"\"\n" +
            "    minutes=\"\"\n" +
            "    accelerator=\"\"\n" +
            "    while [ \"$#\" -gt 0 ]; do\n" +
            "      case \"$1\" in\n" +
            "        --minutes) shift; [ \"$#\" -gt 0 ] || { echo \"usage: tai keep-warm [model] [--minutes N] [--auto|--cpu|--gpu]\" >&2; exit 2; }; minutes=\"$1\" ;;\n" +
            "        --auto) accelerator=auto ;;\n" +
            "        --cpu) accelerator=cpu ;;\n" +
            "        --gpu) accelerator=gpu ;;\n" +
            "        --*) echo \"usage: tai keep-warm [model] [--minutes N] [--auto|--cpu|--gpu]\" >&2; exit 2 ;;\n" +
            "        *) [ -z \"$model\" ] || { echo \"usage: tai keep-warm [model] [--minutes N] [--auto|--cpu|--gpu]\" >&2; exit 2; }; model=\"$1\" ;;\n" +
            "      esac\n" +
            "      shift\n" +
            "    done\n" +
            "    body=\"{}\"\n" +
            "    sep=\"\"\n" +
            "    if [ -n \"$model\" ]; then model_escaped=$(json_escape \"$model\"); body=\"{\\\"model\\\":\\\"$model_escaped\\\"\"; sep=\",\"; fi\n" +
            "    if [ -n \"$minutes\" ]; then [ \"$body\" = \"{}\" ] && { body=\"{\"; sep=\"\"; }; body=\"$body$sep\\\"minutes\\\":$minutes\"; sep=\",\"; fi\n" +
            "    if [ -n \"$accelerator\" ]; then [ \"$body\" = \"{}\" ] && { body=\"{\"; sep=\"\"; }; body=\"$body$sep\\\"accelerator\\\":\\\"$accelerator\\\"\"; sep=\",\"; fi\n" +
            "    [ \"$body\" = \"{}\" ] || body=\"$body}\"\n" +
            "    post_json /v1/ai/runtime/keep-warm \"$body\"\n" +
            "    ;;\n" +
            "  cancel)\n" +
            "    post_json /v1/ai/runtime/cancel '{}'\n" +
            "    ;;\n" +
            "  doctor)\n" +
            "    get_json /v1/ai/runtime\n" +
            "    ;;\n" +
            "  *)\n" +
            "    echo \"tai: unknown command: $cmd\" >&2\n" +
            "    print_help >&2\n" +
            "    exit 2\n" +
            "    ;;\n" +
            "esac\n";

        // launcherctl survives the agent/MCP strip as a launch-only client: `launcherctl launch`
        // is what old tmux configs and shell binds call, and keeping it working costs one route.
        String launcherctlScript =
            "#!" + TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + "/sh\n" +
            "set -eu\n" +
            "print_help() {\n" +
            "  cat <<'EOF'\n" +
            "launcherctl - Termux Launcher shell companion\n" +
            "\n" +
            "Usage:\n" +
            "  launcherctl launch <app name, package, or activity>\n" +
            "\n" +
            "Examples:\n" +
            "  launcherctl launch whatsapp\n" +
            "  launcherctl launch com.termux.api\n" +
            "\n" +
            "The agent, MCP, and device-control commands were removed; app launching is the one\n" +
            "command kept for tmux configs and shell binds. For local AI, use: tai --help\n" +
            "EOF\n" +
            "}\n" +
            "LAUNCHERCTL_DIR=\"$HOME/.launcherctl\"\n" +
            "TOKEN_FILE=\"$LAUNCHERCTL_DIR/token\"\n" +
            "ENDPOINT_FILE=\"$LAUNCHERCTL_DIR/endpoint\"\n" +
            "cmd=\"${1:-help}\"\n" +
            "case \"$cmd\" in\n" +
            "  -h|--help|help)\n" +
            "    print_help\n" +
            "    ;;\n" +
            "  launch)\n" +
            "    shift || true\n" +
            "    [ \"$#\" -gt 0 ] || { echo \"usage: launcherctl launch <app name or package>\" >&2; exit 2; }\n" +
            "    if [ ! -r \"$TOKEN_FILE\" ] || [ ! -r \"$ENDPOINT_FILE\" ]; then\n" +
            "      echo \"launcherctl: missing $TOKEN_FILE or $ENDPOINT_FILE; start Termux Launcher first\" >&2\n" +
            "      exit 1\n" +
            "    fi\n" +
            "    TOKEN=$(cat \"$TOKEN_FILE\")\n" +
            "    BASE=$(sed -n '1p' \"$ENDPOINT_FILE\")\n" +
            "    QUERY=$(printf '%s' \"$*\" | sed 's/\\\\/\\\\\\\\/g; s/\"/\\\\\"/g')\n" +
            "    curl -fsS --connect-timeout 2 --max-time 10 -X POST \\\n" +
            "      -H \"Authorization: Bearer $TOKEN\" -H \"Content-Type: application/json\" \\\n" +
            "      --data \"{\\\"query\\\":\\\"$QUERY\\\"}\" \"$BASE/v1/apps/launch\"\n" +
            "    ;;\n" +
            "  *)\n" +
            "    echo \"launcherctl: unknown command: $cmd\" >&2\n" +
            "    echo \"launcherctl now only supports: launch. For local AI use tai.\" >&2\n" +
            "    exit 2\n" +
            "    ;;\n" +
            "esac\n";

        try {
            writeExecutableTextFile(TAI_BIN_PATH, taiScript);
            writeExecutableTextFile(LAUNCHERCTL_BIN_PATH, launcherctlScript);
            deleteLegacyHelperScripts();
        } catch (Exception e) {
            Logger.logErrorExtended(LOG_TAG, "Failed to install TAI cli: " + e.getMessage());
        }
    }

    /**
     * Removes helpers shipped by older builds: the @tai alias and the launcherctl agent/MCP
     * clients whose server-side endpoints no longer exist. launcherctl itself is not on the
     * list: it is reinstalled above as the launch-only client.
     */
    private void deleteLegacyHelperScripts() {
        String[] legacyPaths = {
            TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + "/@tai",
            TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + "/launcherctl-mcp",
            TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + "/launcher-restart",
        };
        for (String path : legacyPaths) {
            File file = new File(path);
            if (file.exists() && !file.delete()) {
                Logger.logWarn(LOG_TAG, "Failed to remove legacy helper at " + file.getAbsolutePath());
            }
        }
    }

    private void writeTextFile(String path, String content) throws IOException {
        File file = new File(path);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Failed to create dir for " + path);
        }
        try (FileOutputStream stream = new FileOutputStream(file, false)) {
            stream.write(content.getBytes(StandardCharsets.UTF_8));
        }
        file.setReadable(false, false);
        file.setWritable(false, false);
        file.setReadable(true, true);
        file.setWritable(true, true);
    }

    private void writeExecutableTextFile(String path, String content) throws IOException {
        writeTextFile(path, content);
        File file = new File(path);
        if (file.exists()) {
            file.setExecutable(true, false);
            file.setReadable(true, false);
        }
    }

    private HttpResponse jsonResponse(JSONObject response) {
        int statusCode = response.optInt("_statusCode", 200);
        response.remove("_statusCode");
        if (statusCode >= 400 || (!response.optBoolean("ok", true) && response.has("error"))) {
            try {
                withOpenAiErrorEnvelope(response, statusCode);
            } catch (JSONException ignored) {
            }
        }
        return new HttpResponse(statusCode, "application/json; charset=utf-8",
            response.toString().getBytes(StandardCharsets.UTF_8), null);
    }

    static HttpResponse ollamaJsonResponse(JSONObject response) throws JSONException {
        int statusCode = response.optInt("_statusCode", 200);
        response.remove("_statusCode");
        Object error = response.opt("error");
        if (error instanceof JSONObject) {
            JSONObject nested = (JSONObject) error;
            response.put("error", nested.optString("message", "Request failed"));
        }
        return new HttpResponse(statusCode, "application/json; charset=utf-8",
            response.toString().getBytes(StandardCharsets.UTF_8), null);
    }

    private HttpResponse sseResponse(BodyWriter bodyWriter) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Cache-Control", "no-cache");
        headers.put("X-Accel-Buffering", "no");
        return new HttpResponse(200, "text/event-stream; charset=utf-8", bodyWriter, headers);
    }

    private HttpResponse ndjsonResponse(BodyWriter bodyWriter) {
        Map<String, String> headers = new HashMap<>();
        headers.put("Cache-Control", "no-cache");
        headers.put("X-Accel-Buffering", "no");
        return new HttpResponse(200, "application/x-ndjson; charset=utf-8", bodyWriter, headers);
    }

    private void writeResponsesStream(Context context, String chatBody, OutputStream output) throws IOException {
        String responseId = "resp_" + System.currentTimeMillis();
        String messageId = "msg_" + System.currentTimeMillis();
        final String model;
        try {
            model = new JSONObject(chatBody).optString("model", "");
            JSONObject created = TaiApiCompatibility.responseEnvelope(responseId, model, "in_progress");
            writeSseEvent(output, new JSONObject().put("type", "response.created").put("response", created).toString());
            JSONObject item = TaiApiCompatibility.messageOutputItem(messageId, "", "in_progress");
            writeSseEvent(output, new JSONObject().put("type", "response.output_item.added")
                .put("output_index", 0).put("item", item).toString());
            writeSseEvent(output, new JSONObject().put("type", "response.content_part.added")
                .put("item_id", messageId).put("output_index", 0).put("content_index", 0)
                .put("part", new JSONObject().put("type", "output_text").put("text", "").put("annotations", new JSONArray())).toString());
        } catch (JSONException e) {
            throw new IOException(e);
        }
        StringBuilder fullText = new StringBuilder();
        boolean[] failed = new boolean[]{false};
        try {
            TaiManager.getInstance(context).openAiChatCompletionsStream(chatBody, new TaiManager.OpenAiStreamSink() {
                @Override
                public void onEvent(@NonNull JSONObject event) throws IOException {
                    try {
                        if (event.has("error")) {
                            failed[0] = true;
                            JSONObject failure = TaiApiCompatibility.responseEnvelope(responseId, model, "failed");
                            failure.put("error", event.opt("error"));
                            writeSseEvent(output, new JSONObject().put("type", "response.failed").put("response", failure).toString());
                            return;
                        }
                        JSONArray choices = event.optJSONArray("choices");
                        JSONObject choice = choices == null ? null : choices.optJSONObject(0);
                        JSONObject delta = choice == null ? null : choice.optJSONObject("delta");
                        if (delta == null) return;
                        String text = delta.optString("content", "");
                        if (!text.isEmpty()) {
                            fullText.append(text);
                            writeSseEvent(output, new JSONObject().put("type", "response.output_text.delta")
                                .put("item_id", messageId).put("output_index", 0).put("content_index", 0)
                                .put("delta", text).toString());
                        }
                        JSONArray calls = delta.optJSONArray("tool_calls");
                        if (calls != null) for (int i = 0; i < calls.length(); i++) {
                            JSONObject call = calls.optJSONObject(i);
                            if (call == null) continue;
                            JSONObject function = call.optJSONObject("function");
                            String callId = call.optString("id", "call_" + i);
                            String itemId = "fc_" + callId;
                            String name = function == null ? "" : function.optString("name", "");
                            String arguments = function == null ? "{}" : function.optString("arguments", "{}");
                            JSONObject functionItem = new JSONObject().put("type", "function_call").put("id", itemId)
                                .put("call_id", callId).put("name", name).put("arguments", "").put("status", "in_progress");
                            writeSseEvent(output, new JSONObject().put("type", "response.output_item.added")
                                .put("output_index", i + 1).put("item", functionItem).toString());
                            writeSseEvent(output, new JSONObject().put("type", "response.function_call_arguments.delta")
                                .put("item_id", itemId).put("output_index", i + 1).put("delta", arguments).toString());
                            writeSseEvent(output, new JSONObject().put("type", "response.function_call_arguments.done")
                                .put("item_id", itemId).put("output_index", i + 1).put("arguments", arguments).toString());
                            functionItem.put("arguments", arguments).put("status", "completed");
                            writeSseEvent(output, new JSONObject().put("type", "response.output_item.done")
                                .put("output_index", i + 1).put("item", functionItem).toString());
                        }
                    } catch (JSONException e) {
                        throw new IOException(e);
                    }
                }

                @Override
                public void onDone() throws IOException {
                    try {
                        if (failed[0]) {
                            writeSseEvent(output, "[DONE]");
                            return;
                        }
                        writeSseEvent(output, new JSONObject().put("type", "response.output_text.done")
                            .put("item_id", messageId).put("output_index", 0).put("content_index", 0)
                            .put("text", fullText.toString()).toString());
                        JSONObject doneItem = TaiApiCompatibility.messageOutputItem(messageId, fullText.toString(), "completed");
                        writeSseEvent(output, new JSONObject().put("type", "response.output_item.done")
                            .put("output_index", 0).put("item", doneItem).toString());
                        JSONObject completed = TaiApiCompatibility.responseEnvelope(responseId, model, "completed");
                        completed.put("output", new JSONArray().put(doneItem));
                        completed.put("usage", new JSONObject().put("input_tokens", 0).put("output_tokens", 0).put("total_tokens", 0));
                        writeSseEvent(output, new JSONObject().put("type", "response.completed").put("response", completed).toString());
                        writeSseEvent(output, "[DONE]");
                    } catch (JSONException e) {
                        throw new IOException(e);
                    }
                }
            });
        } catch (JSONException e) {
            throw new IOException(e);
        }
    }

    private void writeOllamaChatStream(Context context, String chatBody, OutputStream output, boolean generate) throws IOException {
        final String model;
        try {
            model = new JSONObject(chatBody).optString("model", "");
        } catch (JSONException e) {
            throw new IOException(e);
        }
        try {
            TaiManager.getInstance(context).openAiChatCompletionsStream(chatBody, new TaiManager.OpenAiStreamSink() {
                @Override
                public void onEvent(@NonNull JSONObject event) throws IOException {
                    try {
                        if (event.has("error")) {
                            writeNdjsonEvent(output, new JSONObject().put("error", event.opt("error")));
                            return;
                        }
                        JSONArray choices = event.optJSONArray("choices");
                        JSONObject choice = choices == null ? null : choices.optJSONObject(0);
                        JSONObject delta = choice == null ? null : choice.optJSONObject("delta");
                        if (delta == null || delta.length() == 0) return;
                        JSONObject chunk = new JSONObject().put("model", model)
                            .put("created_at", Instant.now().toString()).put("done", false);
                        if (generate) {
                            chunk.put("response", delta.optString("content", ""));
                        } else {
                            JSONObject message = new JSONObject().put("role", "assistant")
                                .put("content", delta.optString("content", ""));
                            if (delta.has("tool_calls")) message.put("tool_calls", delta.opt("tool_calls"));
                            chunk.put("message", message);
                        }
                        writeNdjsonEvent(output, chunk);
                    } catch (JSONException e) {
                        throw new IOException(e);
                    }
                }

                @Override
                public void onDone() throws IOException {
                    try {
                        JSONObject done = new JSONObject().put("model", model)
                            .put("created_at", Instant.now().toString()).put("done", true)
                            .put("done_reason", "stop").put("total_duration", 0L).put("load_duration", 0L)
                            .put("prompt_eval_count", 0).put("prompt_eval_duration", 0L)
                            .put("eval_count", 0).put("eval_duration", 0L);
                        if (generate) done.put("response", "");
                        else done.put("message", new JSONObject().put("role", "assistant").put("content", ""));
                        writeNdjsonEvent(output, done);
                    } catch (JSONException e) {
                        throw new IOException(e);
                    }
                }
            });
        } catch (JSONException e) {
            throw new IOException(e);
        }
    }

    private void writeNdjsonEvent(OutputStream output, JSONObject event) throws IOException {
        output.write((event.toString() + "\n").getBytes(StandardCharsets.UTF_8));
        output.flush();
    }

    private void writeChatCompletionStream(Context context, String body, OutputStream output) throws IOException {
        try {
            TaiManager.getInstance(context).openAiChatCompletionsStream(body, new TaiManager.OpenAiStreamSink() {
                @Override
                public void onEvent(@NonNull JSONObject event) throws IOException {
                    writeSseEvent(output, event.toString());
                }

                @Override
                public void onDone() throws IOException {
                    writeSseEvent(output, "[DONE]");
                }
            });
        } catch (JSONException e) {
            writeSseJsonError(output, "internal_error", e.getMessage());
            writeSseEvent(output, "[DONE]");
        }
    }

    private void writeCompletionStream(Context context, String body, OutputStream output) throws IOException {
        try {
            TaiManager.getInstance(context).openAiCompletionsStream(body, new TaiManager.OpenAiStreamSink() {
                @Override
                public void onEvent(@NonNull JSONObject event) throws IOException {
                    writeSseEvent(output, event.toString());
                }

                @Override
                public void onDone() throws IOException {
                    writeSseEvent(output, "[DONE]");
                }
            });
        } catch (JSONException e) {
            writeSseJsonError(output, "internal_error", e.getMessage());
            writeSseEvent(output, "[DONE]");
        }
    }

    private void writeSseJsonError(OutputStream output, String code, String message) throws IOException {
        JSONObject error = jsonError(code, message == null ? "" : message);
        writeSseEvent(output, error.toString());
    }

    private void writeSseEvent(OutputStream output, String data) throws IOException {
        output.write(("data: " + data + "\n\n").getBytes(StandardCharsets.UTF_8));
        output.flush();
    }

    private HttpResponse maybeTextResponse(HttpRequest request, String command, JSONObject response) {
        if (!"text".equalsIgnoreCase(request.headers.get("x-tai-output"))) {
            return jsonResponse(response);
        }
        int statusCode = response.optInt("_statusCode", 200);
        response.remove("_statusCode");
        String body = TaiCliFormatter.format(command, response);
        return new HttpResponse(statusCode, "text/plain; charset=utf-8",
            body.getBytes(StandardCharsets.UTF_8), null);
    }

    private static boolean secureEquals(String expected, String actual) {
        byte[] e = expected.getBytes(StandardCharsets.UTF_8);
        byte[] a = actual.getBytes(StandardCharsets.UTF_8);
        if (e.length != a.length) return false;
        int result = 0;
        for (int i = 0; i < e.length; i++) {
            result |= (e[i] ^ a[i]);
        }
        return result == 0;
    }

    private void cleanupSocket() {
        if (serverSocket != null) {
            try {
                serverSocket.close();
            } catch (IOException ignored) {
            } finally {
                serverSocket = null;
            }
        }
    }

    private void closeQuietly(Socket socket) {
        if (socket == null) return;
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }

    private static class HttpRequest {
        String method;
        String target;
        String path;
        String query;
        Map<String, String> headers;
        String body;
    }

    static class HttpResponse {
        final int statusCode;
        final String contentType;
        final byte[] body;
        final BodyWriter bodyWriter;
        final Map<String, String> headers;

        HttpResponse(int statusCode, String contentType, byte[] body, Map<String, String> headers) {
            this.statusCode = statusCode;
            this.contentType = contentType;
            this.body = body != null ? body : new byte[0];
            this.bodyWriter = null;
            this.headers = headers;
        }

        HttpResponse(int statusCode, String contentType, BodyWriter bodyWriter, Map<String, String> headers) {
            this.statusCode = statusCode;
            this.contentType = contentType;
            this.body = new byte[0];
            this.bodyWriter = bodyWriter;
            this.headers = headers;
        }
    }

    private interface BodyWriter {
        void write(OutputStream output) throws IOException;
    }

    private static class HttpParseException extends Exception {
        final int statusCode;
        final String errorCode;

        HttpParseException(int statusCode, String errorCode, String message) {
            super(message);
            this.statusCode = statusCode;
            this.errorCode = errorCode;
        }
    }

    private static class SimpleRateLimiter {
        private final int maxRequests;
        private final long windowMs;
        private final Deque<Long> timestamps = new ArrayDeque<>();

        SimpleRateLimiter(int maxRequests, long windowMs) {
            this.maxRequests = maxRequests;
            this.windowMs = windowMs;
        }

        synchronized boolean allow() {
            long now = System.currentTimeMillis();
            while (!timestamps.isEmpty() && (now - timestamps.peekFirst()) > windowMs) {
                timestamps.removeFirst();
            }
            if (timestamps.size() >= maxRequests) {
                return false;
            }
            timestamps.addLast(now);
            return true;
        }

        synchronized long retryAfterSeconds() {
            if (timestamps.isEmpty()) return 1;
            long waitMs = (timestamps.peekFirst() + windowMs) - System.currentTimeMillis();
            if (waitMs <= 0) return 1;
            return (waitMs + 999) / 1000;
        }
    }

    private static class AppSearchCandidate {
        final LauncherAppEntry entry;
        final int tier;

        AppSearchCandidate(LauncherAppEntry entry, int tier) {
            this.entry = entry;
            this.tier = tier;
        }
    }

    static class AppLaunchMatch {
        final int statusCode;
        final String errorCode;
        final String message;
        final LauncherAppEntry entry;
        final JSONArray candidates;

        AppLaunchMatch(int statusCode, String errorCode, String message, LauncherAppEntry entry, JSONArray candidates) {
            this.statusCode = statusCode;
            this.errorCode = errorCode;
            this.message = message;
            this.entry = entry;
            this.candidates = candidates != null ? candidates : new JSONArray();
        }

        static AppLaunchMatch success(LauncherAppEntry entry) {
            return new AppLaunchMatch(200, null, null, entry, null);
        }

        static AppLaunchMatch error(int statusCode, String errorCode, String message) {
            return new AppLaunchMatch(statusCode, errorCode, message, null, null);
        }

        static AppLaunchMatch error(int statusCode, String errorCode, String message, JSONArray candidates) {
            return new AppLaunchMatch(statusCode, errorCode, message, null, candidates);
        }
    }
}
