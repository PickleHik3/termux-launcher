package com.termux.launcherctl;

import android.app.ActivityManager;
import android.content.Context;
import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.BatteryManager;
import android.os.StatFs;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.jakewharton.processphoenix.ProcessPhoenix;
import com.termux.ai.TaiApiCompatibility;
import com.termux.ai.TaiCliFormatter;
import com.termux.ai.TaiDeviceCapabilities;
import com.termux.ai.TaiManager;
import com.termux.ai.TaiSettings;
import com.termux.app.launcher.LauncherAppLauncher;
import com.termux.app.launcher.data.LauncherAppDataProvider;
import com.termux.app.launcher.model.LauncherAppEntry;
import com.termux.app.launcher.notifications.LauncherNotificationAccess;
import com.termux.privileged.PrivilegedBackendManager;
import com.termux.privileged.PrivilegedPolicyStore;
import com.termux.shared.logger.Logger;
import com.termux.shared.termux.TermuxConstants;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * Local LauncherCtl API server exposed on localhost for shell integrations.
 */
public class LauncherCtlApiServer {
    private static final String LOG_TAG = "LauncherCtlApiServer";
    private static final String API_VERSION = "v1";
    private static final String LAUNCHERCTL_DIR_PATH = TermuxConstants.TERMUX_HOME_DIR_PATH + "/.launcherctl";
    private static final String TOKEN_FILE_PATH = LAUNCHERCTL_DIR_PATH + "/token";
    private static final String ENDPOINT_FILE_PATH = LAUNCHERCTL_DIR_PATH + "/endpoint";
    private static final String LAUNCHERCTL_BIN_PATH = TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + "/launcherctl";
    private static final String LAUNCHERCTL_MCP_BIN_PATH = TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + "/launcherctl-mcp";
    private static final String LAUNCHER_RESTART_BIN_PATH = TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + "/launcher-restart";
    private static final String TAI_BIN_PATH = TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + "/tai";
    static final String LAN_WARNING = "LAN exposure allows any device on your network to reach this endpoint when the token is known.";

    private static final int MAX_REQUEST_LINE_BYTES = 4096;
    private static final int MAX_HEADER_LINE_BYTES = 4096;
    private static final int MAX_HEADER_LINES = 64;
    private static final int MAX_BODY_BYTES = 32 * 1024 * 1024;
    private static final int CLIENT_SOCKET_TIMEOUT_MS = 10_000;

    private static LauncherCtlApiServer instance;

    private final ThreadPoolExecutor clientExecutor = new ThreadPoolExecutor(
        2, 4, 30L, TimeUnit.SECONDS, new LinkedBlockingQueue<>(64));
    private final SecureRandom random = new SecureRandom();
    private final Map<String, SimpleRateLimiter> rateLimiters = new HashMap<>();

    private volatile boolean running;
    private volatile boolean starting;
    private volatile String token;
    private volatile int port;
    private volatile JSONObject cachedAppsResponse;
    private ServerSocket serverSocket;
    private Thread acceptThread;
    private long lastCpuTotalTicks = -1L;
    private long lastCpuIdleTicks = -1L;
    private long lastCpuSampleMs = 0L;
    private Context appContext;
    private final Map<String, byte[]> cachedAppIconPngs = new HashMap<>();

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
            LauncherCtlMcpBridge.getInstance().setContext(appContext);
            if (!LauncherCtlMcpPreferences.PROVIDER_OFF.equals(LauncherCtlMcpPreferences.getWebProvider(appContext))) {
                LauncherCtlMcpPreferences.writePresetConfig(appContext);
            }
            TaiSettings settings = new TaiSettings(appContext);
            token = settings.getOrCreateApiToken();
            String bindMode = settings.getApiBindMode();
            serverSocket = createLoopbackServerSocket(settings.getApiPort(), bindMode);
            port = serverSocket.getLocalPort();
            running = true;
            writeClientConfig();
            installLauncherCtlCliScript();
            installLauncherCtlMcpScript();
            installLauncherRestartScript();
            installTaiCliScripts();
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

    public synchronized void ensureStartedAsync(Context context) {
        if (running || starting) {
            return;
        }

        starting = true;
        Context appContext = context.getApplicationContext();
        Thread startThread = new Thread(() -> start(appContext), "launcherctl-start");
        startThread.setDaemon(true);
        startThread.start();
    }

    /**
     * Re-attempt CLI script installation after bootstrap setup is complete.
     */
    public synchronized void ensureCliScriptsInstalled() {
        try {
            installLauncherCtlCliScript();
            installLauncherCtlMcpScript();
            installLauncherRestartScript();
            installTaiCliScripts();
        } catch (Throwable t) {
            Logger.logErrorExtended(LOG_TAG, "Failed to ensure launcher CLI scripts are installed: " + t.getMessage());
        }
    }

    public synchronized void stop() {
        running = false;
        starting = false;
        cleanupSocket();
        if (acceptThread != null) {
            acceptThread.interrupt();
            acceptThread = null;
        }
        clientExecutor.shutdownNow();
    }

    public synchronized JSONObject applyEndpointSettings(Context context) throws JSONException {
        Context nextContext = context.getApplicationContext();
        running = false;
        starting = false;
        cleanupSocket();
        if (acceptThread != null) {
            acceptThread.interrupt();
            acceptThread = null;
        }
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

    public synchronized void invalidatePackageCaches() {
        cachedAppsResponse = null;
        cachedAppIconPngs.clear();
    }

    private void startAcceptLoop(Context context) {
        acceptThread = new Thread(() -> {
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
        }, "launcherctl-api-accept");
        acceptThread.setDaemon(true);
        acceptThread.start();
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

            if (!isAuthorized(request.headers)) {
                writeResponse(output, request.path.startsWith("/api/")
                    ? ollamaUnauthorizedResponse() : unauthorizedResponse());
                return;
            }

            if (!allowRequest(request)) {
                JSONObject rateLimitError = jsonError("rate_limited", "Too many requests; retry later");
                rateLimitError.put("_statusCode", 429);
                writeResponse(output, request.path.startsWith("/api/")
                    ? ollamaJsonResponse(rateLimitError) : jsonResponse(rateLimitError));
                return;
            }

            HttpResponse response = routeRequest(context, request);
            writeResponse(output, response);

        } catch (Exception e) {
            Logger.logErrorExtended(LOG_TAG, "Request handling failed: " + e.getMessage());
        }
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
            } else if ("POST".equals(request.method) && ("/api/pull".equals(request.path)
                    || "/api/create".equals(request.path) || "/api/push".equals(request.path)
                    || "/api/copy".equals(request.path) || "/api/delete".equals(request.path))) {
                return ollamaJsonResponse(TaiApiCompatibility.ollamaError(501, "unsupported_registry_operation",
                    "Ollama registry operations do not map to LiteRT-LM/MNN packages; use the Termux:GUI model market or import flow."));
            } else if ("GET".equals(request.method) && "/v1/status".equals(request.path)) {
                return maybeTextResponse(request, "launcher-status", buildStatus());
            } else if ("GET".equals(request.method) && "/v1/apps".equals(request.path)) {
                return jsonResponse(buildApps(context));
            } else if ("GET".equals(request.method) && isAppIconPath(request.path)) {
                return buildAppIconResponse(context, extractPackageNameFromIconPath(request.path));
            } else if ("GET".equals(request.method) && "/v1/system/resources".equals(request.path)) {
                return jsonResponse(buildSystemResources(context));
            } else if ("GET".equals(request.method) && "/v1/media/now-playing".equals(request.path)) {
                return jsonResponse(buildNowPlaying());
            } else if ("GET".equals(request.method) && "/v1/media/art".equals(request.path)) {
                return jsonResponse(buildNowPlayingArt());
            } else if ("GET".equals(request.method) && "/v1/notifications".equals(request.path)) {
                return jsonResponse(buildNotifications());
            } else if ("POST".equals(request.method) && "/v1/notifications/recent".equals(request.path)) {
                return jsonResponse(buildNotificationsRecent(request.body));
            } else if ("POST".equals(request.method) && "/v1/notifications/since".equals(request.path)) {
                return jsonResponse(buildNotificationsSince(request.body));
            } else if ("POST".equals(request.method) && "/v1/notifications/search".equals(request.path)) {
                return jsonResponse(buildNotificationsSearch(request.body));
            } else if ("POST".equals(request.method) && "/v1/notifications/stats".equals(request.path)) {
                return jsonResponse(buildNotificationsStats(request.body));
            } else if ("GET".equals(request.method) && "/v1/launcher/capabilities".equals(request.path)) {
                return jsonResponse(buildLauncherCapabilities(context));
            } else if ("GET".equals(request.method) && "/v1/agent/tools".equals(request.path)) {
                return jsonResponse(buildAgentTools());
            } else if ("POST".equals(request.method) && "/v1/agent/route".equals(request.path)) {
                return jsonResponse(runAgentRoute(context, request.body));
            } else if ("POST".equals(request.method) && "/v1/agent/execute".equals(request.path)) {
                return jsonResponse(runAgentExecute(context, request.body));
            } else if ("GET".equals(request.method) && "/v1/events".equals(request.path)) {
                return jsonResponse(buildEventsTail("{}"));
            } else if ("GET".equals(request.method) && "/v1/events/stream".equals(request.path)) {
                return sseResponse(output -> writeEventsStream(output));
            } else if ("POST".equals(request.method) && "/v1/events/tail".equals(request.path)) {
                return jsonResponse(buildEventsTail(request.body));
            } else if ("POST".equals(request.method) && "/v1/apps/launch".equals(request.path)) {
                return jsonResponse(runAppLaunch(context, request.body));
            } else if ("POST".equals(request.method) && "/v1/app/restart".equals(request.path)) {
                return jsonResponse(runAppRestart(context));
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

    private HttpResponse buildAppIconResponse(Context context, String packageName) throws JSONException {
        if (packageName == null || packageName.trim().isEmpty()) {
            JSONObject error = jsonError("bad_request", "Missing package name");
            error.put("_statusCode", 400);
            return jsonResponse(error);
        }

        String normalizedPackageName = packageName.trim();
        byte[] pngBytes;
        synchronized (this) {
            pngBytes = cachedAppIconPngs.get(normalizedPackageName);
        }
        if (pngBytes == null || pngBytes.length == 0) {
            pngBytes = findLauncherIconPng(context, normalizedPackageName);
            if (pngBytes != null && pngBytes.length > 0) {
                synchronized (this) {
                    cachedAppIconPngs.put(normalizedPackageName, pngBytes);
                }
            }
        }
        if (pngBytes == null || pngBytes.length == 0) {
            JSONObject error = jsonError("not_found", "No launcher icon found for package");
            error.put("_statusCode", 404);
            error.put("packageName", normalizedPackageName);
            return jsonResponse(error);
        }

        Map<String, String> headers = new HashMap<>();
        headers.put("Cache-Control", "private, max-age=300");
        headers.put("X-Package-Name", normalizedPackageName);
        return new HttpResponse(200, "image/png", pngBytes, headers);
    }

    private JSONObject buildStatus() throws JSONException {
        PrivilegedBackendManager manager = PrivilegedBackendManager.getInstance();
        JSONObject data = new JSONObject();
        data.put("ok", true);
        data.put("apiVersion", API_VERSION);
        data.put("backendType", String.valueOf(manager.getBackendType()));
        data.put("backendState", String.valueOf(manager.getBackendState()));
        data.put("statusReason", String.valueOf(manager.getStatusReason()));
        data.put("statusMessage", manager.getStatusMessage());
        data.put("isPrivilegedAvailable", manager.isPrivilegedAvailable());
        data.put("notificationListenerConnected", LauncherCtlNotificationListener.isListenerConnected());
        data.put("notificationListener", buildNotificationListenerStatus());
        data.put("privilegedPolicy", describePrivilegedPolicy());
        data.put("endpoint", buildEndpointSettings(appContext, false));
        return data;
    }

    private JSONObject buildApps(Context context) throws JSONException {
        JSONObject cached = cachedAppsResponse;
        if (cached != null) {
            return new JSONObject(cached.toString());
        }
        PackageManager packageManager = context.getPackageManager();
        List<LauncherAppEntry> apps = LauncherAppDataProvider.getInstance(context).getAllAppsBlocking();
        JSONObject data = buildLaunchableAppsPayload(apps, packageManager);
        synchronized (this) {
            cachedAppsResponse = new JSONObject(data.toString());
        }
        return data;
    }

    static JSONObject buildLaunchableAppsPayload(List<LauncherAppEntry> apps, PackageManager packageManager) throws JSONException {
        JSONArray payloadApps = new JSONArray();
        LinkedHashSet<String> uniquePackages = new LinkedHashSet<>();
        for (LauncherAppEntry entry : apps) {
            if (entry == null || entry.appRef == null || entry.appRef.packageName == null || entry.appRef.packageName.isEmpty()) {
                continue;
            }
            JSONObject item = new JSONObject();
            item.put("label", entry.label == null ? entry.appRef.packageName : entry.label);
            item.put("packageName", entry.appRef.packageName);
            item.put("activityName", entry.appRef.activityName == null ? "" : entry.appRef.activityName);
            item.put("stableId", entry.appRef.stableId());
            item.put("userId", entry.appRef.userId);
            item.put("userSerialNumber", entry.appRef.userSerialNumber);
            item.put("clonedProfile", entry.appRef.clonedProfile);
            if (entry.appRef.profileLabel != null && !entry.appRef.profileLabel.isEmpty()) {
                item.put("profileLabel", entry.appRef.profileLabel);
            }
            item.put("launchable", true);
            item.put("systemApp", isSystemApp(packageManager, entry.appRef.packageName));
            payloadApps.put(item);
            uniquePackages.add(entry.appRef.packageName);
        }

        JSONObject data = new JSONObject();
        data.put("ok", true);
        data.put("count", payloadApps.length());
        data.put("packageCount", uniquePackages.size());
        data.put("apps", payloadApps);
        return data;
    }

    private JSONObject buildSystemResources(Context context) throws JSONException {
        JSONObject data = new JSONObject();
        data.put("ok", true);
        data.put("apiVersion", API_VERSION);
        data.put("timestampMs", System.currentTimeMillis());
        data.put("cpuCores", Runtime.getRuntime().availableProcessors());

        double[] loadAverage = readLoadAverage();
        if (loadAverage != null) {
            data.put("loadAvg1m", loadAverage[0]);
            data.put("loadAvg5m", loadAverage[1]);
            data.put("loadAvg15m", loadAverage[2]);
        }
        double cpuPercent = readCPUPercent(loadAverage, Runtime.getRuntime().availableProcessors());
        if (cpuPercent >= 0) {
            data.put("cpuPercent", cpuPercent);
        }

        Map<String, Long> memInfoKb = readMemInfoKb();
        if (!memInfoKb.isEmpty()) {
            long memTotalKb = memInfoKb.get("MemTotal") != null ? memInfoKb.get("MemTotal") : 0L;
            long memAvailableKb = memInfoKb.get("MemAvailable") != null ? memInfoKb.get("MemAvailable") : 0L;
            long memFreeKb = memInfoKb.get("MemFree") != null ? memInfoKb.get("MemFree") : 0L;
            long memUsedKb = memTotalKb > 0 && memAvailableKb > 0 ? (memTotalKb - memAvailableKb) : 0L;
            data.put("memTotalBytes", memTotalKb * 1024L);
            data.put("memAvailableBytes", memAvailableKb * 1024L);
            data.put("memFreeBytes", memFreeKb * 1024L);
            data.put("memUsedBytes", memUsedKb * 1024L);

            JSONObject memory = new JSONObject();
            memory.put("totalBytes", memTotalKb * 1024L);
            memory.put("availableBytes", memAvailableKb * 1024L);
            memory.put("freeBytes", memFreeKb * 1024L);
            memory.put("usedBytes", memUsedKb * 1024L);
            putMemInfoBytes(memory, "buffersBytes", memInfoKb, "Buffers");
            putMemInfoBytes(memory, "cachedBytes", memInfoKb, "Cached");
            putMemInfoBytes(memory, "swapCachedBytes", memInfoKb, "SwapCached");
            putMemInfoBytes(memory, "activeBytes", memInfoKb, "Active");
            putMemInfoBytes(memory, "inactiveBytes", memInfoKb, "Inactive");
            putMemInfoBytes(memory, "shmemBytes", memInfoKb, "Shmem");
            putMemInfoBytes(memory, "slabBytes", memInfoKb, "Slab");
            putMemInfoBytes(memory, "swapTotalBytes", memInfoKb, "SwapTotal");
            putMemInfoBytes(memory, "swapFreeBytes", memInfoKb, "SwapFree");
            data.put("memory", memory);
        }

        Runtime runtime = Runtime.getRuntime();
        long javaHeapUsedBytes = runtime.totalMemory() - runtime.freeMemory();
        data.put("javaHeapUsedBytes", javaHeapUsedBytes);
        data.put("javaHeapMaxBytes", runtime.maxMemory());
        data.put("javaHeapFreeBytes", runtime.freeMemory());
        data.put("javaHeapTotalBytes", runtime.totalMemory());

        JSONObject runtimeInfo = new JSONObject();
        runtimeInfo.put("availableProcessors", runtime.availableProcessors());
        runtimeInfo.put("javaHeapUsedBytes", javaHeapUsedBytes);
        runtimeInfo.put("javaHeapFreeBytes", runtime.freeMemory());
        runtimeInfo.put("javaHeapTotalBytes", runtime.totalMemory());
        runtimeInfo.put("javaHeapMaxBytes", runtime.maxMemory());
        data.put("runtime", runtimeInfo);

        ActivityManager activityManager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (activityManager != null) {
            ActivityManager.MemoryInfo memoryInfo = new ActivityManager.MemoryInfo();
            activityManager.getMemoryInfo(memoryInfo);
            data.put("lowMemory", memoryInfo.lowMemory);
            data.put("memoryThresholdBytes", memoryInfo.threshold);
            data.put("memoryClassMb", activityManager.getMemoryClass());
            data.put("largeMemoryClassMb", activityManager.getLargeMemoryClass());
        }

        JSONObject uptime = readUptimeInfo();
        if (uptime.length() > 0) {
            data.put("uptime", uptime);
        }

        JSONArray storage = readStorageStats(context);
        if (storage.length() > 0) {
            data.put("storage", storage);
        }

        JSONObject battery = readBatteryInfo(context);
        if (battery.length() > 0) {
            data.put("battery", battery);
        }

        JSONArray network = readNetworkStats();
        if (network.length() > 0) {
            data.put("network", network);
        }

        JSONArray thermal = readThermalZones();
        if (thermal.length() > 0) {
            data.put("thermal", thermal);
        }

        PrivilegedBackendManager manager = PrivilegedBackendManager.getInstance();
        data.put("backendType", String.valueOf(manager.getBackendType()));
        data.put("backendState", String.valueOf(manager.getBackendState()));
        data.put("statusReason", String.valueOf(manager.getStatusReason()));
        data.put("statusMessage", manager.getStatusMessage());
        data.put("isPrivilegedAvailable", manager.isPrivilegedAvailable());
        data.put("privilegedPolicy", describePrivilegedPolicy());
        return data;
    }

    private JSONObject buildNowPlaying() throws JSONException {
        JSONObject snapshot = LauncherCtlNotificationListener.getNowPlayingSnapshot();
        snapshot.put("ok", true);
        return snapshot;
    }

    private JSONObject buildNowPlayingArt() throws JSONException {
        JSONObject snapshot = LauncherCtlNotificationListener.getNowPlayingArtSnapshot();
        snapshot.put("ok", true);
        return snapshot;
    }

    private JSONObject buildNotifications() throws JSONException {
        JSONObject snapshot = LauncherCtlNotificationListener.getNotificationsSnapshot();
        snapshot.put("ok", true);
        return snapshot;
    }

    private JSONObject buildNotificationsRecent(String body) throws JSONException {
        JSONObject request = parseJsonBody(body);
        int limit = clampInt(request.optInt("limit", 50), 1, 200);
        List<LauncherCtlNotificationEvent> events = LauncherCtlNotificationStore.getInstance().queryRecent(limit);
        return notificationsResponse(events);
    }

    private JSONObject buildNotificationsSince(String body) throws JSONException {
        JSONObject request = parseJsonBody(body);
        if (!request.has("since")) {
            JSONObject error = jsonError("bad_request", "Missing since timestamp");
            error.put("_statusCode", 400);
            return error;
        }
        long since = request.optLong("since", 0);
        int limit = clampInt(request.optInt("limit", 200), 1, 1000);
        List<LauncherCtlNotificationEvent> events = LauncherCtlNotificationStore.getInstance().querySince(since, limit);
        return notificationsResponse(events);
    }

    private JSONObject buildNotificationsSearch(String body) throws JSONException {
        JSONObject request = parseJsonBody(body);
        String query = request.optString("query", "").trim();
        if (query.isEmpty()) {
            JSONObject error = jsonError("bad_request", "Missing search query");
            error.put("_statusCode", 400);
            return error;
        }
        int limit = clampInt(request.optInt("limit", 50), 1, 200);
        List<LauncherCtlNotificationEvent> events = LauncherCtlNotificationStore.getInstance().querySearch(query, limit);
        return notificationsResponse(events);
    }

    private JSONObject buildNotificationsStats(String body) throws JSONException {
        JSONObject request = parseJsonBody(body);
        Long since = request.has("since") ? request.optLong("since", 0) : null;
        JSONObject stats = LauncherCtlNotificationStore.getInstance().queryStats(since);
        stats.put("ok", true);
        return stats;
    }

    private JSONObject buildLauncherCapabilities(Context context) throws JSONException {
        JSONObject data = new JSONObject();
        data.put("ok", true);
        data.put("apiVersion", API_VERSION);
        data.put("timestampMs", System.currentTimeMillis());
        JSONObject integrations = new JSONObject();
        integrations.put("openAiCompatible", true);
        integrations.put("mcpStdio", true);
        integrations.put("mcpCommand", "launcherctl mcp");
        integrations.put("cliStateDir", LAUNCHERCTL_DIR_PATH);
        data.put("integrations", integrations);

        TaiDeviceCapabilities device = TaiDeviceCapabilities.detect(context);
        data.put("device", device.toJson());

        JSONObject notifications = new JSONObject();
        boolean listenerConnected = LauncherCtlNotificationListener.isListenerConnected();
        boolean accessEnabled = LauncherNotificationAccess.isEnabled(context);
        notifications.put("listenerConnected", listenerConnected);
        notifications.put("accessEnabled", accessEnabled);
        notifications.put("settingsAction", LauncherCtlNotificationListener.getListenerSettingsAction());
        if (!listenerConnected) {
            notifications.put("hint", LauncherCtlNotificationListener.getListenerHint());
        }
        data.put("notifications", notifications);

        JSONObject tai = new JSONObject();
        try {
            JSONObject runtimeStatus = TaiManager.getInstance(context).runtimeStatus();
            tai.put("runtime", runtimeStatus.optJSONObject("runtime"));
            JSONObject runtimeState = runtimeStatus.optJSONObject("runtime");
            tai.put("loadedModelId", runtimeState != null && !runtimeState.isNull("loadedModelId")
                ? runtimeState.optString("loadedModelId", null) : JSONObject.NULL);
        } catch (Exception e) {
            tai.put("runtime", JSONObject.NULL);
            tai.put("loadedModelId", JSONObject.NULL);
        }
        data.put("tai", tai);

        LauncherToolRegistry registry = LauncherToolRegistry.getInstance();
        JSONArray toolNames = new JSONArray();
        for (LauncherToolRegistry.ToolMetadata tool : registry.getTools()) {
            toolNames.put(tool.name);
        }
        for (LauncherToolRegistry.ToolMetadata tool : LauncherCtlMcpBridge.getInstance().getToolMetadata()) {
            toolNames.put(tool.name);
        }
        data.put("availableTools", toolNames);

        JSONArray warnings = new JSONArray();
        JSONArray blockingReasons = new JSONArray();
        if (!accessEnabled) {
            warnings.put("Notification access is disabled; notification and media tools are unavailable.");
            blockingReasons.put("notification_access_disabled");
        }
        if (!device.liteRtLmAbiSupported || !device.liteRtLmNativeLibrariesAvailable) {
            warnings.put("LiteRT-LM backend is not available for this device ABI/APK.");
            blockingReasons.put(device.liteRtLmAbiSupported ? "litert_lm_native_unavailable" : "litert_lm_abi_unsupported");
        }
        if (device.mnnUnsupportedReason != null && !device.mnnUnsupportedReason.isEmpty()) {
            warnings.put(device.mnnUnsupportedReason);
        }
        data.put("warnings", warnings);
        data.put("blockingReasons", blockingReasons);

        writeDebugSnapshot(registry, data);
        return data;
    }

    private JSONObject buildAgentTools() throws JSONException {
        if (appContext != null) {
            LauncherCtlMcpBridge.getInstance().setContext(appContext);
        }
        LauncherToolRegistry registry = LauncherToolRegistry.getInstance();
        registry.writeDebugToolsJson();
        List<LauncherToolRegistry.ToolMetadata> tools = new ArrayList<>(registry.getTools());
        tools.addAll(LauncherCtlMcpBridge.getInstance().getToolMetadata());
        JSONArray internal = new JSONArray();
        JSONArray openAi = new JSONArray();
        for (LauncherToolRegistry.ToolMetadata tool : tools) {
            internal.put(tool.toInternalJson());
            openAi.put(tool.toOpenAiTool());
        }
        JSONObject data = new JSONObject();
        data.put("ok", true);
        data.put("count", tools.size());
        data.put("tools", internal);
        data.put("openAiTools", openAi);
        data.put("mcpConfigPath", LauncherCtlStorage.getMcpConfigJsonFile().getAbsolutePath());
        try {
            JSONObject snapshot = new JSONObject(data.toString());
            snapshot.put("generatedAtMs", System.currentTimeMillis());
            writeTextFile(LauncherCtlStorage.getToolsJsonFile().getAbsolutePath(), snapshot.toString(2));
        } catch (Exception ignored) {
        }
        return data;
    }

    private JSONObject runAgentRoute(Context context, String body) throws JSONException {
        JSONObject result = new LauncherCtlAgentHandler(context, new LauncherToolExecutionHandler(context)).route(body);
        appendAgentAudit("agent.route", body, result);
        return result;
    }

    private JSONObject runAgentExecute(Context context, String body) throws JSONException {
        LauncherCtlMcpBridge.getInstance().setContext(context);
        JSONObject result = new LauncherCtlAgentHandler(context, new LauncherToolExecutionHandler(context),
            LauncherCtlMcpBridge.getInstance()).execute(body);
        appendAgentAudit("agent.execute", body, result);
        return result;
    }

    private JSONObject buildEventsTail(String body) throws JSONException {
        JSONObject request = parseJsonBody(body);
        int limit = clampInt(request.optInt("limit", 100), 1, 1000);
        Long since = request.has("since") ? request.optLong("since", 0) : null;
        List<JSONObject> events = LauncherCtlEventStore.getInstance().tailEvents(limit, since);
        JSONObject data = new JSONObject();
        data.put("ok", true);
        data.put("count", events.size());
        data.put("limit", limit);
        if (since != null) data.put("since", since.longValue());
        JSONArray array = new JSONArray();
        for (JSONObject event : events) {
            array.put(event);
        }
        data.put("events", array);
        return data;
    }

    private void writeEventsStream(OutputStream output) throws IOException {
        List<JSONObject> events = LauncherCtlEventStore.getInstance().tailEvents(100, null);
        for (JSONObject event : events) {
            writeSseEvent(output, event.toString());
        }
        writeSseEvent(output, "[DONE]");
    }

    private void appendAgentAudit(String type, String requestBody, JSONObject result) {
        try {
            JSONObject payload = new JSONObject();
            payload.put("request", requestBody == null ? "" : requestBody);
            payload.put("resultOk", result.optBoolean("ok", false));
            if (result.has("error") && !result.isNull("error")) {
                payload.put("error", result.optString("error", null));
            }
            if (result.has("tool") && !result.isNull("tool")) {
                payload.put("tool", result.optString("tool", null));
            } else {
                String requestTool = agentToolFromRequest(requestBody);
                if (requestTool != null && !requestTool.isEmpty()) {
                    payload.put("tool", requestTool);
                }
            }
            LauncherCtlEventStore.getInstance().appendEvent(type, payload);
            LauncherCtlEventStore.getInstance().appendAgentRun(type, payload);
        } catch (Exception e) {
            Logger.logWarn(LOG_TAG, "Failed to append agent audit event: " + e.getMessage());
        }
    }

    @Nullable
    private String agentToolFromRequest(String requestBody) {
        if (requestBody == null || requestBody.trim().isEmpty()) {
            return null;
        }
        JSONObject request = parseJsonBody(requestBody);
        String tool = request.optString("tool", "").trim();
        if (!tool.isEmpty()) return tool;
        String name = request.optString("name", "").trim();
        return name.isEmpty() ? null : LauncherToolRegistry.openAiNameToInternalName(name);
    }

    private void writeDebugSnapshot(LauncherToolRegistry registry, JSONObject capabilities) {
        try {
            registry.writeDebugToolsJson();
            JSONObject payload = new JSONObject();
            payload.put("generatedAtMs", System.currentTimeMillis());
            payload.put("capabilities", capabilities);
            writeTextFile(LauncherCtlStorage.getCapabilitiesJsonFile().getAbsolutePath(), payload.toString(2));
        } catch (Exception e) {
            Logger.logWarn(LOG_TAG, "Failed to write capability debug snapshot: " + e.getMessage());
        }
    }

    private JSONObject notificationsResponse(List<LauncherCtlNotificationEvent> events) throws JSONException {
        JSONObject data = new JSONObject();
        data.put("ok", true);
        data.put("count", events.size());
        JSONArray array = new JSONArray();
        for (LauncherCtlNotificationEvent event : events) {
            array.put(event.toJson());
        }
        data.put("events", array);
        return data;
    }

    private JSONObject parseJsonBody(String body) {
        if (body == null || body.trim().isEmpty()) {
            return new JSONObject();
        }
        try {
            return new JSONObject(body);
        } catch (JSONException e) {
            return new JSONObject();
        }
    }

    private int clampInt(int value, int min, int max) {
        if (value < min) return min;
        if (value > max) return max;
        return value;
    }

    private JSONObject buildNotificationListenerStatus() throws JSONException {
        JSONObject data = new JSONObject();
        boolean connected = LauncherCtlNotificationListener.isListenerConnected();
        data.put("connected", connected);
        data.put("settingsAction", LauncherCtlNotificationListener.getListenerSettingsAction());
        if (!connected) {
            data.put("hint", LauncherCtlNotificationListener.getListenerHint());
        }
        return data;
    }

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

    private JSONObject runAppRestart(Context context) throws JSONException {
        Intent restartIntent = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
        if (restartIntent == null) {
            JSONObject error = jsonError("restart_unavailable", "No launch intent available for app restart");
            error.put("_statusCode", 500);
            return error;
        }

        restartIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ProcessPhoenix.triggerRebirth(context, restartIntent);

        JSONObject data = new JSONObject();
        data.put("ok", true);
        data.put("restarting", true);
        return data;
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

    private JSONObject describePrivilegedPolicy() throws JSONException {
        Context context = appContext;
        JSONObject info = new JSONObject();
        if (context == null) {
            info.put("available", false);
            return info;
        }

        info.put("available", true);
        info.put("masterEnabled", PrivilegedPolicyStore.isMasterEnabled(context));
        info.put("preferShizuku", PrivilegedPolicyStore.isPreferShizuku(context));
        info.put("allowShellFallback", PrivilegedPolicyStore.isShellFallbackEnabled(context));

        return info;
    }
    private String executePrivileged(String command) {
        try {
            return PrivilegedBackendManager.getInstance().executeCommand(command).get(20, TimeUnit.SECONDS);
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    private boolean isAuthorized(Map<String, String> headers) {
        return isAuthorized(token, headers);
    }

    static boolean isAuthorized(String expectedToken, Map<String, String> headers) {
        if (expectedToken == null || expectedToken.isEmpty()) return false;
        if (headers == null) return false;
        String value = headers.get("authorization");
        if (value == null) return false;
        String prefix = "Bearer ";
        if (!value.startsWith(prefix)) return false;
        return secureEquals(expectedToken, value.substring(prefix.length()).trim());
    }

    private boolean allowRequest(HttpRequest request) {
        SimpleRateLimiter limiter = rateLimiters.get(request.method + ":" + request.path);
        if (limiter == null && "GET".equals(request.method) && isAppIconPath(request.path)) {
            limiter = rateLimiters.get("GET:/v1/apps/icon/*");
        }
        return limiter == null || limiter.allow();
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
        BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(output, StandardCharsets.UTF_8));
        byte[] bytes = response.body;
        writer.write("HTTP/1.1 " + response.statusCode + " " + statusMessage(response.statusCode) + "\r\n");
        writer.write("Content-Type: " + response.contentType + "\r\n");
        writer.write("Connection: close\r\n");
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
        rateLimiters.put("GET:/v1/status", new SimpleRateLimiter(120, 60_000));
        rateLimiters.put("GET:/v1/apps", new SimpleRateLimiter(60, 60_000));
        rateLimiters.put("GET:/v1/apps/icon/*", new SimpleRateLimiter(120, 60_000));
        rateLimiters.put("GET:/v1/system/resources", new SimpleRateLimiter(120, 60_000));
        rateLimiters.put("GET:/v1/media/now-playing", new SimpleRateLimiter(120, 60_000));
        rateLimiters.put("GET:/v1/media/art", new SimpleRateLimiter(60, 60_000));
        rateLimiters.put("GET:/v1/notifications", new SimpleRateLimiter(120, 60_000));
        rateLimiters.put("POST:/v1/notifications/recent", new SimpleRateLimiter(120, 60_000));
        rateLimiters.put("POST:/v1/notifications/since", new SimpleRateLimiter(120, 60_000));
        rateLimiters.put("POST:/v1/notifications/search", new SimpleRateLimiter(60, 60_000));
        rateLimiters.put("POST:/v1/notifications/stats", new SimpleRateLimiter(120, 60_000));
        rateLimiters.put("GET:/v1/launcher/capabilities", new SimpleRateLimiter(60, 60_000));
        rateLimiters.put("GET:/v1/agent/tools", new SimpleRateLimiter(60, 60_000));
        rateLimiters.put("POST:/v1/agent/route", new SimpleRateLimiter(60, 60_000));
        rateLimiters.put("POST:/v1/agent/execute", new SimpleRateLimiter(60, 60_000));
        rateLimiters.put("GET:/v1/events", new SimpleRateLimiter(120, 60_000));
        rateLimiters.put("GET:/v1/events/stream", new SimpleRateLimiter(12, 60_000));
        rateLimiters.put("POST:/v1/events/tail", new SimpleRateLimiter(120, 60_000));
        rateLimiters.put("POST:/v1/apps/launch", new SimpleRateLimiter(30, 60_000));
        rateLimiters.put("POST:/v1/app/restart", new SimpleRateLimiter(5, 60_000));
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
        data.put("mcpCommand", "launcherctl mcp");
        data.put("tokenRequired", true);
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
        supportedEndpoints.put("/v1/chat/completions");
        supportedEndpoints.put("/v1/responses");
        supportedEndpoints.put("/v1/completions");
        supportedEndpoints.put("/v1/embeddings");
        supportedEndpoints.put("/v1/audio/speech");
        supportedEndpoints.put("/api/version");
        supportedEndpoints.put("/api/tags");
        supportedEndpoints.put("/api/show");
        supportedEndpoints.put("/api/chat");
        supportedEndpoints.put("/api/generate");
        supportedEndpoints.put("/api/ps");
        supportedEndpoints.put("/api/embed");
        supportedEndpoints.put("/v1/launcher/capabilities");
        supportedEndpoints.put("/v1/agent/tools");
        supportedEndpoints.put("/v1/agent/route");
        supportedEndpoints.put("/v1/agent/execute");
        supportedEndpoints.put("/v1/events");
        supportedEndpoints.put("/v1/events/tail");
        supportedEndpoints.put("/v1/events/stream");
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

    private void installLauncherCtlCliScript() {
        File loginBinary = new File(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + "/login");
        if (!loginBinary.exists()) {
            Logger.logInfo(LOG_TAG, "Skipping LauncherCtl CLI install until bootstrap is initialized.");
            return;
        }

        String script =
            "#!/data/data/com.termux/files/usr/bin/sh\n" +
            "set -eu\n" +
            "cmd=\"${1:-status}\"\n" +
            "update_scripts_help=0\n" +
            "if [ \"$cmd\" = \"help\" ] && [ \"${2:-}\" = \"update-scripts\" ]; then\n" +
            "  update_scripts_help=1\n" +
            "  shift 2 || true\n" +
            "fi\n" +
            "if [ \"$cmd\" = \"update-scripts\" ] || [ \"$update_scripts_help\" = \"1\" ]; then\n" +
            "  [ \"$update_scripts_help\" = \"1\" ] || shift || true\n" +
            "  command -v curl >/dev/null 2>&1 || { echo \"launcherctl update-scripts: missing required command: curl\" >&2; echo \"install it with: pkg install curl\" >&2; exit 1; }\n" +
            "  raw_root=\"${TERMUX_LAUNCHER_RAW_ROOT:-https://raw.githubusercontent.com/PickleHik3/termux-launcher/main}\"\n" +
            "  tmp_dir=\"${TMPDIR:-$HOME/.tmp}/launcherctl-update-scripts.$$\"\n" +
            "  mkdir -p \"$tmp_dir\" || exit 1\n" +
            "  trap 'rm -rf \"$tmp_dir\"' EXIT HUP INT TERM\n" +
            "  curl -fsSL \"$raw_root/resources/bin/launcherctl\" -o \"$tmp_dir/launcherctl\" || exit 1\n" +
            "  sh -n \"$tmp_dir/launcherctl\" || exit 1\n" +
            "  if [ \"$update_scripts_help\" = \"1\" ]; then\n" +
            "    sh \"$tmp_dir/launcherctl\" update-scripts --help \"$@\"\n" +
            "  else\n" +
            "    sh \"$tmp_dir/launcherctl\" update-scripts \"$@\"\n" +
            "  fi\n" +
            "  exit $?\n" +
            "fi\n" +
            "if [ \"$cmd\" = \"mcp\" ]; then\n" +
            "  shift || true\n" +
            "  if ! command -v python3 >/dev/null 2>&1; then\n" +
            "    echo \"launcherctl mcp: missing required command: python3\" >&2\n" +
            "    echo \"install it with: pkg install python\" >&2\n" +
            "    exit 1\n" +
            "  fi\n" +
            "  if command -v launcherctl-mcp >/dev/null 2>&1; then\n" +
            "    exec launcherctl-mcp \"$@\"\n" +
            "  fi\n" +
            "  echo \"launcherctl mcp: missing launcherctl-mcp helper\" >&2\n" +
            "  exit 1\n" +
            "fi\n" +
            "LAUNCHERCTL_DIR=\"$HOME/.launcherctl\"\n" +
            "TOKEN_FILE=\"$LAUNCHERCTL_DIR/token\"\n" +
            "ENDPOINT_FILE=\"$LAUNCHERCTL_DIR/endpoint\"\n" +
            "if [ ! -r \"$TOKEN_FILE\" ] || [ ! -r \"$ENDPOINT_FILE\" ]; then\n" +
            "  echo \"launcherctl: missing $TOKEN_FILE or $ENDPOINT_FILE\" >&2\n" +
            "  exit 1\n" +
            "fi\n" +
            "TOKEN=$(cat \"$TOKEN_FILE\")\n" +
            "BASE=$(sed -n '1p' \"$ENDPOINT_FILE\")\n" +
            "CURL_COMMON=\"-fsS --connect-timeout 2 --max-time 10\"\n" +
            "shift || true\n" +
            "json_escape() { printf '%s' \"$1\" | sed 's/\\\\/\\\\\\\\/g; s/\"/\\\\\"/g'; }\n" +
            "post_json() {\n" +
            "  path=\"$1\"\n" +
            "  data=\"$2\"\n" +
            "  curl $CURL_COMMON -X POST -H \"Authorization: Bearer $TOKEN\" -H \"Content-Type: application/json\" --data \"$data\" \"$BASE$path\"\n" +
            "}\n" +
            "RISH_DIR=\"$HOME/.rish\"\n" +
            "RISH_BIN=\"$RISH_DIR/rish\"\n" +
            "RISH_DEX=\"$RISH_DIR/rish_shizuku.dex\"\n" +
            "sdk_int() { getprop ro.build.version.sdk 2>/dev/null || echo 0; }\n" +
            "tty_doctor() {\n" +
            "  ok=1\n" +
            "  echo \"LauncherCtl tty-doctor\"\n" +
            "  echo \"  rish dir: $RISH_DIR\"\n" +
            "  if [ -x \"$RISH_BIN\" ]; then\n" +
            "    echo \"  rish: ok ($RISH_BIN)\"\n" +
            "  else\n" +
            "    echo \"  rish: missing or not executable ($RISH_BIN)\"\n" +
            "    ok=0\n" +
            "  fi\n" +
            "  if [ -f \"$RISH_DEX\" ]; then\n" +
            "    echo \"  dex: ok ($RISH_DEX)\"\n" +
            "  else\n" +
            "    echo \"  dex: missing ($RISH_DEX)\"\n" +
            "    ok=0\n" +
            "  fi\n" +
            "  sdk=$(sdk_int)\n" +
            "  if [ \"$sdk\" -ge 34 ] && [ -f \"$RISH_DEX\" ] && [ -w \"$RISH_DEX\" ]; then\n" +
            "    echo \"  dex perms: not compatible on Android 14+ (dex is writable)\"\n" +
            "    ok=0\n" +
            "  fi\n" +
            "  if [ \"$ok\" -eq 1 ]; then\n" +
            "    echo \"  status: healthy\"\n" +
            "    return 0\n" +
            "  fi\n" +
            "  cat <<'EOF'\n" +
            "\n" +
            "Suggested fix commands:\n" +
            "  mkdir -p ~/.rish\n" +
            "  cp ~/files/.rish/rish ~/.rish/\n" +
            "  cp ~/files/.rish/rish_shizuku.dex ~/.rish/\n" +
            "  chmod 700 ~/.rish/rish\n" +
            "  chmod 400 ~/.rish/rish_shizuku.dex\n" +
            "\n" +
            "Then run:\n" +
            "  launcherctl tty-doctor\n" +
            "EOF\n" +
            "  return 1\n" +
            "}\n" +
            "case \"$cmd\" in\n" +
            "  status)\n" +
            "    curl $CURL_COMMON -H \"Authorization: Bearer $TOKEN\" \"$BASE/v1/status\"\n" +
            "    ;;\n" +
            "  capabilities)\n" +
            "    curl $CURL_COMMON -H \"Authorization: Bearer $TOKEN\" \"$BASE/v1/launcher/capabilities\"\n" +
            "    ;;\n" +
            "  tools)\n" +
            "    curl $CURL_COMMON -H \"Authorization: Bearer $TOKEN\" \"$BASE/v1/agent/tools\"\n" +
            "    ;;\n" +
            "  apps)\n" +
            "    curl $CURL_COMMON -H \"Authorization: Bearer $TOKEN\" \"$BASE/v1/apps\"\n" +
            "    ;;\n" +
            "  launch)\n" +
            "    [ \"$#\" -gt 0 ] || { echo \"usage: launcherctl launch <app name or package>\" >&2; exit 2; }\n" +
            "    CMD_ESCAPED=$(json_escape \"$*\")\n" +
            "    curl $CURL_COMMON -X POST -H \"Authorization: Bearer $TOKEN\" -H \"Content-Type: application/json\" \\\n" +
            "      --data \"{\\\"query\\\":\\\"$CMD_ESCAPED\\\"}\" \"$BASE/v1/apps/launch\"\n" +
            "    ;;\n" +
            "  resources)\n" +
            "    curl $CURL_COMMON -H \"Authorization: Bearer $TOKEN\" \"$BASE/v1/system/resources\"\n" +
            "    ;;\n" +
            "  media)\n" +
            "    curl $CURL_COMMON -H \"Authorization: Bearer $TOKEN\" \"$BASE/v1/media/now-playing\"\n" +
            "    ;;\n" +
            "  art)\n" +
            "    curl $CURL_COMMON -H \"Authorization: Bearer $TOKEN\" \"$BASE/v1/media/art\"\n" +
            "    ;;\n" +
            "  notifications)\n" +
            "    sub=\"${1:-}\"\n" +
            "    [ -n \"$sub\" ] && shift || true\n" +
            "    case \"$sub\" in\n" +
            "      \"\"|active)\n" +
            "        curl $CURL_COMMON -H \"Authorization: Bearer $TOKEN\" \"$BASE/v1/notifications\"\n" +
            "        ;;\n" +
            "      recent)\n" +
            "        limit=\"${1:-50}\"\n" +
            "        [ \"$limit\" -ge 1 ] 2>/dev/null || { echo \"usage: launcherctl notifications recent [limit]\" >&2; exit 2; }\n" +
            "        post_json /v1/notifications/recent \"{\\\"limit\\\":$limit}\"\n" +
            "        ;;\n" +
"      since)\n" +
"        [ \"$#\" -gt 0 ] || { echo \"usage: launcherctl notifications since <epoch-ms> [limit]\" >&2; exit 2; }\n" +
"        since=\"$1\"; shift || true\n" +
"        limit=\"${1:-200}\"\n" +
"        [ \"$since\" -ge 0 ] 2>/dev/null || { echo \"usage: launcherctl notifications since <epoch-ms> [limit]\" >&2; exit 2; }\n" +
"        [ \"$limit\" -ge 1 ] 2>/dev/null || { echo \"usage: launcherctl notifications since <epoch-ms> [limit]\" >&2; exit 2; }\n" +
"        post_json /v1/notifications/since \"{\\\"since\\\":$since,\\\"limit\\\":$limit}\"\n" +
"        ;;\n" +
            "      search)\n" +
            "        [ \"$#\" -gt 0 ] || { echo \"usage: launcherctl notifications search <text>\" >&2; exit 2; }\n" +
            "        query=\"$*\"\n" +
            "        post_json /v1/notifications/search \"{\\\"query\\\":\\\"$(json_escape \"$query\")\\\"}\"\n" +
            "        ;;\n" +
            "      stats)\n" +
            "        post_json /v1/notifications/stats \"{}\"\n" +
            "        ;;\n" +
            "      *)\n" +
            "        echo \"launcherctl: unknown notifications subcommand: $sub\" >&2\n" +
            "        echo \"usage: launcherctl notifications {active|recent|since|search|stats}\" >&2\n" +
            "        exit 2\n" +
            "        ;;\n" +
            "    esac\n" +
            "    ;;\n" +
            "  agent)\n" +
            "    dry_run=\"false\"\n" +
            "    if [ \"$#\" -gt 0 ] && [ \"$1\" = \"--dry-run\" ]; then\n" +
            "      dry_run=\"true\"\n" +
            "      shift || true\n" +
            "    fi\n" +
            "    [ \"$#\" -gt 0 ] || { echo \"usage: launcherctl agent [--dry-run] <request>\" >&2; exit 2; }\n" +
            "    REQUEST_ESCAPED=$(json_escape \"$*\")\n" +
            "    route_json=$(post_json /v1/agent/route \"{\\\"request\\\":\\\"$REQUEST_ESCAPED\\\"}\")\n" +
            "    printf '%s\\n' \"$route_json\"\n" +
            "    if [ \"$dry_run\" = \"true\" ]; then\n" +
            "      exit 0\n" +
            "    fi\n" +
            "    if ! command -v jq >/dev/null 2>&1; then\n" +
            "      echo\n" +
            "      echo \"launcherctl agent: non-dry-run requires jq to parse route output\" >&2\n" +
            "      echo \"install it with: pkg install jq\" >&2\n" +
            "      exit 1\n" +
            "    fi\n" +
            "    tool=$(printf '%s' \"$route_json\" | jq -r '.tool // empty')\n" +
            "    arguments=$(printf '%s' \"$route_json\" | jq -c '.arguments // {}')\n" +
            "    if [ -z \"$tool\" ]; then\n" +
            "      echo \"launcherctl agent: route did not return a tool\" >&2\n" +
            "      exit 1\n" +
            "    fi\n" +
            "    echo\n" +
            "    post_json /v1/agent/execute \"{\\\"tool\\\":\\\"$tool\\\",\\\"arguments\\\":$arguments,\\\"confirm\\\":true}\"\n" +
            "    ;;\n" +
            "  mcp)\n" +
            "    echo \"launcherctl mcp: internal dispatch error\" >&2\n" +
            "    exit 1\n" +
            "    ;;\n" +
            "  events)\n" +
            "    sub=\"${1:-}\"\n" +
            "    [ -n \"$sub\" ] && shift || true\n" +
            "    case \"$sub\" in\n" +
            "      tail)\n" +
            "        limit=\"${1:-100}\"\n" +
            "        [ \"$limit\" -ge 1 ] 2>/dev/null || { echo \"usage: launcherctl events tail [limit]\" >&2; exit 2; }\n" +
            "        post_json /v1/events/tail \"{\\\"limit\\\":$limit}\"\n" +
            "        ;;\n" +
            "      *)\n" +
            "        echo \"launcherctl: unknown events subcommand: $sub\" >&2\n" +
            "        echo \"usage: launcherctl events tail\" >&2\n" +
            "        exit 2\n" +
            "        ;;\n" +
            "    esac\n" +
            "    ;;\n" +
            "  restart)\n" +
            "    curl $CURL_COMMON -X POST -H \"Authorization: Bearer $TOKEN\" \"$BASE/v1/app/restart\"\n" +
            "    ;;\n" +
            "  update-scripts)\n" +
            "    echo \"launcherctl update-scripts must be run before API command dispatch\" >&2\n" +
            "    exit 1\n" +
            "    ;;\n" +
            "  tty-doctor)\n" +
            "    tty_doctor\n" +
            "    ;;\n" +
            "  token)\n" +
            "    sub=\"${1:-}\"; shift || true\n" +
            "    [ \"$sub\" = \"rotate\" ] || { echo \"usage: launcherctl token rotate\" >&2; exit 2; }\n" +
            "    curl $CURL_COMMON -X POST -H \"Authorization: Bearer $TOKEN\" \"$BASE/v1/auth/rotate\"\n" +
            "    ;;\n" +
            "  *)\n" +
            "    echo \"usage: launcherctl {status|capabilities|tools|apps|launch|resources|media|art|notifications|notifications recent|notifications since|notifications search|notifications stats|agent|mcp|events tail|restart|update-scripts|tty-doctor|token rotate}\" >&2\n" +
            "    exit 2\n" +
            "    ;;\n" +
            "esac\n";

        try {
            writeTextFile(LAUNCHERCTL_BIN_PATH, script);
            File launcherctlBin = new File(LAUNCHERCTL_BIN_PATH);
            if (launcherctlBin.exists()) {
                launcherctlBin.setExecutable(true, false);
                launcherctlBin.setReadable(true, false);
            }
        } catch (Exception e) {
            Logger.logErrorExtended(LOG_TAG, "Failed to install launcherctl cli: " + e.getMessage());
        }
    }

    private void installLauncherCtlMcpScript() {
        File loginBinary = new File(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + "/login");
        if (!loginBinary.exists()) {
            Logger.logInfo(LOG_TAG, "Skipping launcherctl-mcp install until bootstrap is initialized.");
            return;
        }

        try {
            installExecutableAsset("launcherctl-mcp", LAUNCHERCTL_MCP_BIN_PATH);
        } catch (Exception e) {
            Logger.logErrorExtended(LOG_TAG, "Failed to install launcherctl-mcp: " + e.getMessage());
        }
    }

    private void installLauncherRestartScript() {
        File loginBinary = new File(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + "/login");
        if (!loginBinary.exists()) {
            Logger.logInfo(LOG_TAG, "Skipping launcher-restart install until bootstrap is initialized.");
            return;
        }

        String script =
            "#!/data/data/com.termux/files/usr/bin/sh\n" +
            "set -eu\n" +
            "\n" +
            "if [ \"$#\" != \"0\" ]; then\n" +
            "  echo \"usage: launcher-restart\" >&2\n" +
            "  exit 2\n" +
            "fi\n" +
            "\n" +
            "# Keep behavior aligned with tel-restart for launcher state cleanup when present.\n" +
            "if command -v tel-delete-status >/dev/null 2>&1; then\n" +
            "  tel-delete-status -1 || true\n" +
            "fi\n" +
            "\n" +
            "# Preferred path: authenticated LauncherCtl restart.\n" +
            "if command -v launcherctl >/dev/null 2>&1; then\n" +
            "  if launcherctl restart >/dev/null 2>&1; then\n" +
            "    exit 0\n" +
            "  fi\n" +
            "fi\n" +
            "\n" +
            "# Fallbacks for older app builds or pre-init LauncherCtl state.\n" +
            "RESTART_CMD='am start -S -n com.termux/.app.TermuxActivity'\n" +
            "if $RESTART_CMD >/dev/null 2>&1; then\n" +
            "  exit 0\n" +
            "fi\n" +
            "\n" +
            "if [ -x \"$HOME/.rish/rish\" ]; then\n" +
            "  exec \"$HOME/.rish/rish\" -c \"$RESTART_CMD\"\n" +
            "fi\n" +
            "\n" +
            "echo \"launcher-restart: failed (authenticated restart and fallback launch paths failed)\" >&2\n" +
            "exit 1\n";

        try {
            writeTextFile(LAUNCHER_RESTART_BIN_PATH, script);
            File launcherRestartBin = new File(LAUNCHER_RESTART_BIN_PATH);
            if (launcherRestartBin.exists()) {
                launcherRestartBin.setExecutable(true, false);
                launcherRestartBin.setReadable(true, false);
            }
        } catch (Exception e) {
            Logger.logErrorExtended(LOG_TAG, "Failed to install launcher-restart cli: " + e.getMessage());
        }
    }

    private void installTaiCliScripts() {
        File loginBinary = new File(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + "/login");
        if (!loginBinary.exists()) {
            Logger.logInfo(LOG_TAG, "Skipping TAI CLI install until bootstrap is initialized.");
            return;
        }

        String taiScript =
            "#!/data/data/com.termux/files/usr/bin/sh\n" +
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
            "\n" +
            "Point OpenAI-compatible terminal tools at this host, e.g.:\n" +
            "  export OPENAI_BASE_URL=http://127.0.0.1:<port>/v1\n" +
            "  export OPENAI_API_KEY=<your-token>\n" +
            "The actual token is stored at ~/.launcherctl/token (do not echo it into shell history).\n" +
            "\n" +
            "Security notes:\n" +
            "  LAN mode (opt-in via settings) exposes the API to your local network. Keep your token secure.\n" +
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
            "    printf '\\n'\n" +
            "    get_json /v1/status\n" +
            "    ;;\n" +
            "  *)\n" +
            "    echo \"tai: unknown command: $cmd\" >&2\n" +
            "    print_help >&2\n" +
            "    exit 2\n" +
            "    ;;\n" +
            "esac\n";

        try {
            writeExecutableTextFile(TAI_BIN_PATH, taiScript);
            deleteLegacyAtTaiScript();
        } catch (Exception e) {
            Logger.logErrorExtended(LOG_TAG, "Failed to install TAI cli: " + e.getMessage());
        }
    }

    private void deleteLegacyAtTaiScript() {
        File file = new File(TermuxConstants.TERMUX_BIN_PREFIX_DIR_PATH + "/@tai");
        if (file.exists() && !file.delete()) {
            Logger.logWarn(LOG_TAG, "Failed to remove legacy @tai helper at " + file.getAbsolutePath());
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

    private void installExecutableAsset(String assetName, String path) throws IOException {
        Context context = appContext;
        if (context == null) {
            throw new IOException("Application context is not available");
        }
        try (InputStream input = context.getAssets().open(assetName)) {
            writeBytesFile(path, readAllBytes(input));
        }
        File file = new File(path);
        if (file.exists()) {
            file.setExecutable(true, false);
            file.setReadable(true, false);
        }
    }

    private void writeBytesFile(String path, byte[] content) throws IOException {
        File file = new File(path);
        File parent = file.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("Failed to create dir for " + path);
        }
        try (FileOutputStream stream = new FileOutputStream(file, false)) {
            stream.write(content);
        }
        file.setReadable(false, false);
        file.setWritable(false, false);
        file.setReadable(true, true);
        file.setWritable(true, true);
    }

    private byte[] readAllBytes(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        byte[] buffer = new byte[8192];
        int read;
        while ((read = input.read(buffer)) != -1) {
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private byte[] readAllBytes(File file) throws IOException {
        try (InputStream stream = new FileInputStream(file)) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = stream.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return output.toByteArray();
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
                            .put("created_at", java.time.Instant.now().toString()).put("done", false);
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
                            .put("created_at", java.time.Instant.now().toString()).put("done", true)
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

    private boolean isAppIconPath(String path) {
        if (path == null || path.isEmpty()) return false;
        if (path.startsWith("/v1/apps/icon/")) {
            return path.length() > "/v1/apps/icon/".length();
        }
        if (!path.startsWith("/v1/apps/") || !path.endsWith("/icon")) {
            return false;
        }
        return path.length() > "/v1/apps/".length() + "/icon".length();
    }

    private String extractPackageNameFromIconPath(String path) {
        if (path == null) return "";
        if (path.startsWith("/v1/apps/icon/")) {
            return path.substring("/v1/apps/icon/".length());
        }
        if (path.startsWith("/v1/apps/") && path.endsWith("/icon")) {
            return path.substring("/v1/apps/".length(), path.length() - "/icon".length());
        }
        return "";
    }

    private byte[] findLauncherIconPng(Context context, String packageName) {
        if (context == null || packageName == null || packageName.isEmpty()) {
            return null;
        }

        PackageManager packageManager = context.getPackageManager();
        Intent launcherIntent = new Intent(Intent.ACTION_MAIN, null);
        launcherIntent.addCategory(Intent.CATEGORY_LAUNCHER);
        List<ResolveInfo> launchables = packageManager.queryIntentActivities(launcherIntent, 0);

        for (ResolveInfo resolveInfo : launchables) {
            ActivityInfo activityInfo = resolveInfo.activityInfo;
            if (activityInfo == null || activityInfo.packageName == null) continue;
            if (!packageName.equals(activityInfo.packageName)) continue;
            Drawable icon = activityInfo.loadIcon(packageManager);
            if (icon == null) continue;
            return drawableToPngBytes(icon);
        }

        try {
            Drawable icon = packageManager.getApplicationIcon(packageName);
            return drawableToPngBytes(icon);
        } catch (PackageManager.NameNotFoundException ignored) {
            return null;
        }
    }

    private byte[] drawableToPngBytes(Drawable drawable) {
        if (drawable == null) return null;

        Bitmap bitmap;
        if (drawable instanceof BitmapDrawable && ((BitmapDrawable) drawable).getBitmap() != null) {
            bitmap = ((BitmapDrawable) drawable).getBitmap();
        } else {
            int width = Math.max(1, drawable.getIntrinsicWidth());
            int height = Math.max(1, drawable.getIntrinsicHeight());
            bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(bitmap);
            drawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
            drawable.draw(canvas);
        }

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        if (!bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)) {
            return null;
        }
        return output.toByteArray();
    }

    private Map<String, Long> readMemInfoKb() {
        Map<String, Long> values = new HashMap<>();
        try {
            String content = new String(readAllBytes(new File("/proc/meminfo")), StandardCharsets.UTF_8);
            String[] lines = content.split("\n");
            for (String line : lines) {
                int colon = line.indexOf(':');
                if (colon <= 0) continue;
                String key = line.substring(0, colon).trim();
                String valuePart = line.substring(colon + 1).trim();
                if (valuePart.isEmpty()) continue;
                String[] parts = valuePart.split("\\s+");
                if (parts.length == 0) continue;
                try {
                    values.put(key, Long.parseLong(parts[0]));
                } catch (NumberFormatException ignored) {
                }
            }
        } catch (IOException ignored) {
        }
        return values;
    }

    private double[] readLoadAverage() {
        try {
            String content = new String(readAllBytes(new File("/proc/loadavg")), StandardCharsets.UTF_8).trim();
            if (content.isEmpty()) return null;
            String[] parts = content.split("\\s+");
            if (parts.length < 3) return null;
            return new double[] {
                Double.parseDouble(parts[0]),
                Double.parseDouble(parts[1]),
                Double.parseDouble(parts[2]),
            };
        } catch (Exception ignored) {
            return null;
        }
    }

    private double readCPUPercent(double[] loadAverage, int cpuCores) {
        long[] sample = readProcStatCpuTicks();
        if (sample != null) {
            long total = sample[0];
            long idle = sample[1];
            long now = System.currentTimeMillis();
            synchronized (this) {
                if (lastCpuTotalTicks > 0 && total > lastCpuTotalTicks && now > lastCpuSampleMs) {
                    long totalDelta = total - lastCpuTotalTicks;
                    long idleDelta = idle - lastCpuIdleTicks;
                    if (totalDelta > 0) {
                        double percent = 100.0 * (1.0 - ((double) idleDelta / (double) totalDelta));
                        lastCpuTotalTicks = total;
                        lastCpuIdleTicks = idle;
                        lastCpuSampleMs = now;
                        return clampPercent(percent);
                    }
                }
                lastCpuTotalTicks = total;
                lastCpuIdleTicks = idle;
                lastCpuSampleMs = now;
            }

            // First sample has no delta yet. Take a short second sample to compute cpuPercent.
            try {
                Thread.sleep(120);
            } catch (InterruptedException ignored) {
            }
            long[] second = readProcStatCpuTicks();
            if (second != null) {
                long total2 = second[0];
                long idle2 = second[1];
                long totalDelta = total2 - total;
                long idleDelta = idle2 - idle;
                if (totalDelta > 0) {
                    synchronized (this) {
                        lastCpuTotalTicks = total2;
                        lastCpuIdleTicks = idle2;
                        lastCpuSampleMs = System.currentTimeMillis();
                    }
                    double percent = 100.0 * (1.0 - ((double) idleDelta / (double) totalDelta));
                    return clampPercent(percent);
                }
            }
        }

        // Fallback to load average based approximation.
        if (loadAverage != null && loadAverage.length >= 1 && cpuCores > 0) {
            return clampPercent((loadAverage[0] / cpuCores) * 100.0);
        }
        return -1;
    }

    private long[] readProcStatCpuTicks() {
        long[] direct = readProcStatCpuTicksFromContent(readFileAsString("/proc/stat"));
        if (direct != null) {
            return direct;
        }

        // Fallback through privileged backend if direct procfs read is unavailable.
        String privileged = executePrivileged("cat /proc/stat");
        return readProcStatCpuTicksFromContent(privileged);
    }

    private long[] readProcStatCpuTicksFromContent(String content) {
        if (content == null || content.isEmpty()) return null;
        try {
            String[] lines = content.split("\n");
            for (String line : lines) {
                if (!line.startsWith("cpu ")) continue;
                String[] fields = line.trim().split("\\s+");
                if (fields.length < 5) return null;
                long total = 0L;
                for (int i = 1; i < fields.length; i++) {
                    try {
                        total += Long.parseLong(fields[i]);
                    } catch (NumberFormatException ignored) {
                    }
                }
                long idle = Long.parseLong(fields[4]);
                if (fields.length > 5) {
                    try {
                        idle += Long.parseLong(fields[5]); // iowait as idle-like time
                    } catch (NumberFormatException ignored) {
                    }
                }
                return new long[] {total, idle};
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private String readFileAsString(String path) {
        try {
            return new String(readAllBytes(new File(path)), StandardCharsets.UTF_8);
        } catch (Exception ignored) {
            return null;
        }
    }

    private JSONObject readUptimeInfo() {
        JSONObject uptime = new JSONObject();
        try {
            uptime.put("processUptimeMs", SystemClock.elapsedRealtime());
            uptime.put("processUptimeSec", SystemClock.elapsedRealtime() / 1000.0);
            String content = new String(readAllBytes(new File("/proc/uptime")), StandardCharsets.UTF_8).trim();
            String[] parts = content.split("\\s+");
            if (parts.length >= 1) {
                double uptimeSec = Double.parseDouble(parts[0]);
                uptime.put("systemUptimeSec", uptimeSec);
                uptime.put("systemUptimeMs", (long) (uptimeSec * 1000.0));
            }
        } catch (Exception ignored) {
        }
        return uptime;
    }

    private JSONArray readStorageStats(Context context) {
        JSONArray storage = new JSONArray();
        addStoragePath(storage, "root", "/");
        addStoragePath(storage, "data", "/data");

        File filesDir = context.getFilesDir();
        if (filesDir != null) {
            addStoragePath(storage, "appFiles", filesDir.getAbsolutePath());
        }
        File cacheDir = context.getCacheDir();
        if (cacheDir != null) {
            addStoragePath(storage, "appCache", cacheDir.getAbsolutePath());
        }
        File extDir = context.getExternalFilesDir(null);
        if (extDir != null) {
            addStoragePath(storage, "externalFiles", extDir.getAbsolutePath());
        }
        addStoragePath(storage, "shared", "/storage/emulated/0");
        return storage;
    }

    private void addStoragePath(JSONArray storage, String label, String path) {
        try {
            File file = new File(path);
            if (!file.exists()) return;
            StatFs statFs = new StatFs(path);
            long total = statFs.getTotalBytes();
            long free = statFs.getFreeBytes();
            long available = statFs.getAvailableBytes();
            long used = total > free ? (total - free) : 0L;
            JSONObject item = new JSONObject();
            item.put("label", label);
            item.put("path", path);
            item.put("totalBytes", total);
            item.put("freeBytes", free);
            item.put("availableBytes", available);
            item.put("usedBytes", used);
            storage.put(item);
        } catch (Exception ignored) {
        }
    }

    private JSONObject readBatteryInfo(Context context) {
        JSONObject battery = new JSONObject();
        try {
            Intent intent = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (intent == null) return battery;
            int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
            int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
            int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
            int health = intent.getIntExtra(BatteryManager.EXTRA_HEALTH, -1);
            int plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
            int tempTenthsC = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Integer.MIN_VALUE);
            int voltageMv = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1);

            if (level >= 0 && scale > 0) {
                battery.put("levelPercent", (level * 100.0) / scale);
                battery.put("level", level);
                battery.put("scale", scale);
            }
            battery.put("status", batteryStatusToString(status));
            battery.put("health", batteryHealthToString(health));
            battery.put("charging", status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL);
            battery.put("plugged", plugged != 0);
            battery.put("plugType", batteryPlugToString(plugged));
            if (tempTenthsC != Integer.MIN_VALUE) {
                battery.put("temperatureC", tempTenthsC / 10.0);
            }
            if (voltageMv >= 0) {
                battery.put("voltageMv", voltageMv);
            }
        } catch (Exception ignored) {
        }
        return battery;
    }

    private JSONArray readNetworkStats() {
        JSONArray network = new JSONArray();
        try {
            String content = new String(readAllBytes(new File("/proc/net/dev")), StandardCharsets.UTF_8);
            String[] lines = content.split("\n");
            for (String line : lines) {
                if (!line.contains(":")) continue;
                String[] split = line.split(":");
                if (split.length != 2) continue;
                String iface = split[0].trim();
                if (iface.isEmpty()) continue;
                String[] fields = split[1].trim().split("\\s+");
                if (fields.length < 16) continue;
                JSONObject item = new JSONObject();
                item.put("interface", iface);
                item.put("rxBytes", parseLongSafe(fields[0]));
                item.put("rxPackets", parseLongSafe(fields[1]));
                item.put("rxErrors", parseLongSafe(fields[2]));
                item.put("rxDropped", parseLongSafe(fields[3]));
                item.put("txBytes", parseLongSafe(fields[8]));
                item.put("txPackets", parseLongSafe(fields[9]));
                item.put("txErrors", parseLongSafe(fields[10]));
                item.put("txDropped", parseLongSafe(fields[11]));
                network.put(item);
            }
        } catch (Exception ignored) {
        }
        return network;
    }

    private JSONArray readThermalZones() {
        JSONArray thermal = new JSONArray();
        try {
            File root = new File("/sys/class/thermal");
            File[] zones = root.listFiles((dir, name) -> name != null && name.startsWith("thermal_zone"));
            if (zones == null || zones.length == 0) return thermal;
            Arrays.sort(zones, (a, b) -> a.getName().compareTo(b.getName()));
            int limit = Math.min(zones.length, 24);
            for (int i = 0; i < limit; i++) {
                File zone = zones[i];
                String type = readSingleLine(new File(zone, "type"));
                String tempRaw = readSingleLine(new File(zone, "temp"));
                if (tempRaw == null || tempRaw.isEmpty()) continue;
                long temp = parseLongSafe(tempRaw);
                // Most Android kernels expose millidegree C.
                double tempC = temp > 1000 ? (temp / 1000.0) : (double) temp;
                JSONObject item = new JSONObject();
                item.put("zone", zone.getName());
                item.put("type", type == null ? "" : type);
                item.put("tempC", tempC);
                thermal.put(item);
            }
        } catch (Exception ignored) {
        }
        return thermal;
    }

    private void putMemInfoBytes(JSONObject json, String fieldName, Map<String, Long> memInfoKb, String key) throws JSONException {
        Long valueKb = memInfoKb.get(key);
        if (valueKb != null && valueKb >= 0) {
            json.put(fieldName, valueKb * 1024L);
        }
    }

    private String readSingleLine(File file) {
        try {
            String text = new String(readAllBytes(file), StandardCharsets.UTF_8);
            int newline = text.indexOf('\n');
            if (newline >= 0) {
                text = text.substring(0, newline);
            }
            return text.trim();
        } catch (Exception ignored) {
            return null;
        }
    }

    private long parseLongSafe(String value) {
        try {
            return Long.parseLong(value.trim());
        } catch (Exception ignored) {
            return 0L;
        }
    }

    private double clampPercent(double value) {
        if (value < 0) return 0;
        if (value > 100) return 100;
        return value;
    }

    private String batteryStatusToString(int status) {
        switch (status) {
            case BatteryManager.BATTERY_STATUS_CHARGING:
                return "charging";
            case BatteryManager.BATTERY_STATUS_DISCHARGING:
                return "discharging";
            case BatteryManager.BATTERY_STATUS_FULL:
                return "full";
            case BatteryManager.BATTERY_STATUS_NOT_CHARGING:
                return "not_charging";
            case BatteryManager.BATTERY_STATUS_UNKNOWN:
            default:
                return "unknown";
        }
    }

    private String batteryHealthToString(int health) {
        switch (health) {
            case BatteryManager.BATTERY_HEALTH_GOOD:
                return "good";
            case BatteryManager.BATTERY_HEALTH_OVERHEAT:
                return "overheat";
            case BatteryManager.BATTERY_HEALTH_DEAD:
                return "dead";
            case BatteryManager.BATTERY_HEALTH_OVER_VOLTAGE:
                return "over_voltage";
            case BatteryManager.BATTERY_HEALTH_UNSPECIFIED_FAILURE:
                return "unspecified_failure";
            case BatteryManager.BATTERY_HEALTH_COLD:
                return "cold";
            case BatteryManager.BATTERY_HEALTH_UNKNOWN:
            default:
                return "unknown";
        }
    }

    private String batteryPlugToString(int plugged) {
        switch (plugged) {
            case BatteryManager.BATTERY_PLUGGED_AC:
                return "ac";
            case BatteryManager.BATTERY_PLUGGED_USB:
                return "usb";
            case BatteryManager.BATTERY_PLUGGED_WIRELESS:
                return "wireless";
            default:
                return "none";
        }
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

    private static boolean isSystemApp(PackageManager packageManager, String packageName) {
        if (packageManager == null || packageName == null || packageName.isEmpty()) {
            return false;
        }
        try {
            return (packageManager.getApplicationInfo(packageName, 0).flags & ApplicationInfo.FLAG_SYSTEM) != 0;
        } catch (PackageManager.NameNotFoundException ignored) {
            return false;
        }
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

    /**
     * Executes launcher tools by dispatching to the existing API server helpers.
     * This is the shared execution handler used by {@link LauncherCtlAgentHandler}.
     */
    private class LauncherToolExecutionHandler implements LauncherToolRegistry.ToolExecutionHandler {
        private final Context context;

        LauncherToolExecutionHandler(Context context) {
            this.context = context.getApplicationContext();
        }

        @NonNull
        @Override
        public LauncherToolRegistry.ToolExecutionResult execute(
            @NonNull LauncherToolRegistry.ToolMetadata tool,
            @NonNull JSONObject arguments
        ) throws Exception {
            switch (tool.name) {
                case LauncherToolRegistry.TOOL_CAPABILITIES_GET:
                    return wrapExecutionResult(buildLauncherCapabilities(context));
                case LauncherToolRegistry.TOOL_APPS_SEARCH:
                    return wrapExecutionResult(buildAppsSearchResponse(arguments));
                case LauncherToolRegistry.TOOL_APPS_LAUNCH:
                    return wrapExecutionResult(runAppLaunch(context, arguments.toString()));
                case LauncherToolRegistry.TOOL_NOTIFICATIONS_RECENT:
                    return wrapExecutionResult(buildNotificationsRecent(arguments.toString()));
                case LauncherToolRegistry.TOOL_NOTIFICATIONS_SINCE:
                    return wrapExecutionResult(buildNotificationsSince(arguments.toString()));
                case LauncherToolRegistry.TOOL_NOTIFICATIONS_SEARCH:
                    return wrapExecutionResult(buildNotificationsSearch(arguments.toString()));
                case LauncherToolRegistry.TOOL_NOTIFICATIONS_STATS:
                    return wrapExecutionResult(buildNotificationsStats(arguments.toString()));
                case LauncherToolRegistry.TOOL_MEDIA_NOW_PLAYING:
                    return wrapExecutionResult(buildNowPlaying());
                case LauncherToolRegistry.TOOL_SYSTEM_RESOURCES:
                    return wrapExecutionResult(buildSystemResources(context));
                case LauncherToolRegistry.TOOL_INTENT_OPEN:
                    return wrapExecutionResult(runIntentOpen(context, arguments));
                case LauncherToolRegistry.TOOL_MEMORY_WRITE:
                    return wrapExecutionResult(runMemoryWrite(arguments));
                case LauncherToolRegistry.TOOL_MEMORY_SEARCH:
                    return wrapExecutionResult(runMemorySearch(arguments));
                case LauncherToolRegistry.TOOL_EVENTS_TAIL:
                    return wrapExecutionResult(buildEventsTail(arguments.toString()));
                case LauncherToolRegistry.TOOL_USER_CONFIRM:
                    return wrapExecutionResult(buildUserConfirm(arguments));
                default:
                    return LauncherToolRegistry.ToolExecutionResult.error(501, "not_implemented",
                        "Tool '" + tool.name + "' is registered but not yet executable");
            }
        }

        @NonNull
        private LauncherToolRegistry.ToolExecutionResult wrapExecutionResult(@Nullable JSONObject result) {
            if (result == null) {
                return LauncherToolRegistry.ToolExecutionResult.error(500, "execution_failed", "Tool returned null");
            }
            boolean ok = result.optBoolean("ok", false);
            int statusCode = result.optInt("_statusCode", ok ? 200 : 500);
            if (ok && statusCode >= 200 && statusCode < 300) {
                return LauncherToolRegistry.ToolExecutionResult.success(result);
            }
            String errorCode = result.optString("error", "execution_failed");
            String message = result.optString("message", "Tool execution failed");
            return LauncherToolRegistry.ToolExecutionResult.error(statusCode, errorCode, message);
        }
    }

    private JSONObject buildAppsSearchResponse(JSONObject arguments) throws JSONException {
        String query = arguments.optString("query", "").trim();
        int limit = clampInt(arguments.optInt("limit", 50), 1, 200);
        if (query.isEmpty()) {
            JSONObject error = jsonError("bad_request", "Missing search query");
            error.put("_statusCode", 400);
            return error;
        }
        List<LauncherAppEntry> apps = LauncherAppDataProvider.getInstance(appContext).getAllAppsBlocking();
        JSONObject data = new JSONObject();
        JSONArray results = new JSONArray();
        String lowerQuery = query.toLowerCase(Locale.US);
        int count = 0;
        for (LauncherAppEntry entry : apps) {
            if (count >= limit) break;
            String label = entry.label == null ? "" : entry.label.toLowerCase(Locale.US);
            String pkg = entry.appRef.packageName.toLowerCase(Locale.US);
            String activity = entry.appRef.activityName == null ? "" : entry.appRef.activityName.toLowerCase(Locale.US);
            if (label.contains(lowerQuery) || pkg.contains(lowerQuery) || activity.contains(lowerQuery)) {
                JSONObject item = new JSONObject();
                item.put("label", entry.label);
                item.put("packageName", entry.appRef.packageName);
                item.put("activityName", entry.appRef.activityName == null ? "" : entry.appRef.activityName);
                item.put("stableId", entry.appRef.stableId());
                item.put("userId", entry.appRef.userId);
                item.put("clonedProfile", entry.appRef.clonedProfile);
                results.put(item);
                count++;
            }
        }
        data.put("ok", true);
        data.put("query", query);
        data.put("count", results.length());
        data.put("apps", results);
        return data;
    }

    private JSONObject runIntentOpen(Context context, JSONObject arguments) throws JSONException {
        String action = arguments.optString("action", "android.intent.action.VIEW");
        String data = arguments.optString("data", "").trim();
        if (data.isEmpty()) {
            JSONObject error = jsonError("bad_request", "Missing intent data URI");
            error.put("_statusCode", 400);
            return error;
        }
        Intent intent = new Intent(action, Uri.parse(data));
        String packageName = arguments.optString("package", "").trim();
        String component = arguments.optString("component", "").trim();
        if (!packageName.isEmpty()) {
            intent.setPackage(packageName);
        }
        if (!component.isEmpty()) {
            ComponentName cn = ComponentName.unflattenFromString(component);
            if (cn != null) {
                intent.setComponent(cn);
            }
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        JSONObject extras = arguments.optJSONObject("extras");
        if (extras != null) {
            Iterator<String> keys = extras.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                Object value = extras.opt(key);
                if (value instanceof String) {
                    intent.putExtra(key, (String) value);
                } else if (value instanceof Boolean) {
                    intent.putExtra(key, (Boolean) value);
                } else if (value instanceof Integer) {
                    intent.putExtra(key, (Integer) value);
                } else if (value instanceof Long) {
                    intent.putExtra(key, (Long) value);
                }
            }
        }
        try {
            context.startActivity(intent);
        } catch (android.content.ActivityNotFoundException e) {
            JSONObject error = jsonError("activity_not_found", "No activity found for intent: " + data);
            error.put("_statusCode", 404);
            return error;
        }
        JSONObject result = new JSONObject();
        result.put("ok", true);
        result.put("action", action);
        result.put("data", data);
        return result;
    }

    private JSONObject runMemoryWrite(JSONObject arguments) throws JSONException {
        String namespace = arguments.optString("namespace", "agent").trim();
        String key = arguments.optString("key", "").trim();
        String value = arguments.optString("value", "");
        if (namespace.isEmpty() || key.isEmpty()) {
            JSONObject error = jsonError("bad_request", "Missing namespace or key");
            error.put("_statusCode", 400);
            return error;
        }
        LauncherCtlMemoryStore.getInstance().write(namespace, key, value);
        JSONObject result = new JSONObject();
        result.put("ok", true);
        result.put("namespace", namespace);
        result.put("key", key);
        result.put("value", value);
        return result;
    }

    private JSONObject runMemorySearch(JSONObject arguments) throws JSONException {
        String namespace = arguments.optString("namespace", "agent").trim();
        String query = arguments.optString("query", "").trim();
        int limit = clampInt(arguments.optInt("limit", 10), 1, 100);
        if (namespace.isEmpty() || query.isEmpty()) {
            JSONObject error = jsonError("bad_request", "Missing namespace or query");
            error.put("_statusCode", 400);
            return error;
        }
        List<LauncherCtlMemoryStore.MemoryEntry> entries = LauncherCtlMemoryStore.getInstance().search(namespace, query, limit);
        JSONObject data = new JSONObject();
        data.put("ok", true);
        data.put("namespace", namespace);
        data.put("query", query);
        data.put("count", entries.size());
        JSONArray array = new JSONArray();
        for (LauncherCtlMemoryStore.MemoryEntry entry : entries) {
            array.put(entry.toJson());
        }
        data.put("entries", array);
        return data;
    }

    private JSONObject buildUserConfirm(JSONObject arguments) throws JSONException {
        String message = arguments.optString("message", "Confirm?");
        String risk = arguments.optString("risk", "medium");
        JSONObject result = new JSONObject();
        result.put("ok", true);
        result.put("confirmed", false);
        result.put("message", message);
        result.put("risk", risk);
        result.put("note", "Confirmation must be obtained by the caller before executing the action");
        return result;
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
