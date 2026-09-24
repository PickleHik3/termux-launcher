package com.termux.ai;

import android.app.ActivityManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.Process;
import android.os.RemoteException;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import com.termux.R;
import com.termux.app.activities.SettingsActivity;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public final class TaiRuntimeService extends Service {
    private static final String CHANNEL_ID = "termux_ai_runtime";
    private static final int NOTIFICATION_ID = 24110;
    /** Every pressure, idle and exit action logs under this tag: {@code adb logcat -s TaiMemory}. */
    static final String LOG_TAG = "TaiMemory";
    /**
     * Sent to the last client that spoke to this service when the process holds nothing but its
     * baseline (see {@link #checkIdleExit}): the client unbinds, the service is destroyed and the
     * process exits. No payload. The service's own request to its client, not a request/response
     * code, which is why it lives here and not in {@link TaiRuntimeIpc}.
     */
    static final int MSG_IDLE_EXIT = 5;
    private static final long MIB = 1024L * 1024L;

    private final ExecutorService executor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "tai-runtime-service");
        thread.setDaemon(true);
        return thread;
    });
    private final ExecutorService controlExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "tai-runtime-control");
        thread.setDaemon(true);
        return thread;
    });
    /**
     * Speech-to-text has a lane of its own: on the serial executor a transcription would queue
     * behind a chat generation, which is exactly the "talk to an agent while it answers" case.
     * One thread, so transcriptions still run one at a time and the Whisper runtime's monitor is
     * never contended by two requests.
     */
    private final ExecutorService sttExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "tai-runtime-stt");
        thread.setDaemon(true);
        return thread;
    });
    /**
     * Carries out what the watch decides. Evictions take the router's load lock, so a load in
     * progress holds them up; that must stall neither the watch tick (which also publishes
     * presence) nor the cancel/unload lane, hence a lane of their own. One action is queued at a
     * time ({@link #pressureActionQueued}); the next tick decides again from fresh numbers.
     */
    private final ExecutorService pressureExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "tai-runtime-pressure");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicBoolean pressureActionQueued = new AtomicBoolean();
    private final Messenger messenger = new Messenger(new IncomingHandler());
    private volatile boolean foreground;
    /** Slow enough to be invisible in battery stats, fast enough for a per-second countdown. */
    private static final long WATCH_INTERVAL_MS = 2_000L;
    private final ScheduledExecutorService watchScheduler =
        Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "tai-runtime-watch");
            thread.setDaemon(true);
            return thread;
        });
    @Nullable private ScheduledFuture<?> watch;
    @Nullable private ScheduledFuture<?> idleExitCheck;
    /** Whether the watch has published the runtime's idle state since chat last went quiet. */
    private volatile boolean publishedIdle;

    /** Requests being served right now, status reads included; an idle exit waits for zero. */
    private final AtomicInteger inFlight = new AtomicInteger();
    /** When the last request that was not a status read finished; status reads keep nothing alive. */
    private volatile long lastActivityMs;
    /** When the watch last saw the registry go down to the RUNTIME baseline; 0 while models are resident. */
    private volatile long modelsGoneAtMs;
    /** The reply Messenger of the most recent request: the one client an idle-exit notice can reach. */
    @Nullable private volatile Messenger lastClient;
    /** Set between an idle-exit notice and the next request; {@link #onDestroy} exits only on this path. */
    private volatile boolean idleExitAnnounced;

    @Override
    public void onCreate() {
        super.onCreate();
        // Self-heal for builds where the category sort posted under this same id: its last frame
        // could outlive both services and sit in the shade forever, ongoing and unswipeable. This
        // id is ours, and nothing of ours is posted under it until ensureForeground() runs.
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager != null) manager.cancel(NOTIFICATION_ID);
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        ensureChannel();
        return messenger.getBinder();
    }

    @Override
    public void onDestroy() {
        // The process is going away with a model still resident only when it was killed; publishing
        // the last known state keeps the UI glyph from counting down against a runtime that is gone.
        stopRuntimeWatch();
        cancelIdleExitCheck();
        watchScheduler.shutdownNow();
        executor.shutdownNow();
        controlExecutor.shutdownNow();
        sttExecutor.shutdownNow();
        pressureExecutor.shutdownNow();
        super.onDestroy();
        if (idleExitAnnounced) {
            // The client unbound on the notice, so nothing binds this process any more and Android
            // would keep it around as an empty cached process, baseline and all, until it needed the
            // memory. A destroyed service in a process of its own has nothing left to do; exiting
            // here returns the LiteRT-LM / driver baseline now. A death after the bindings are gone
            // is not a crash and starts nothing. Any other destroy leaves the process to Android, so
            // a client that comes back finds a model that survived it.
            Log.i(LOG_TAG, "idle exit: unbound, process exiting");
            Process.killProcess(Process.myPid());
        }
    }

    /**
     * The running levels are the system saying it is low while this process runs; they map to the
     * eviction tiers the poll would reach on its own and stop being delivered from Android 14. The
     * background levels and {@code UI_HIDDEN} are lifecycle, not pressure — backgrounding the
     * launcher must not drop a warm model — and {@link TaiPressureWatch#tierForTrimLevel} ignores them.
     */
    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        TaiPressureWatch.Tier tier = TaiPressureWatch.tierForTrimLevel(level);
        if (tier == TaiPressureWatch.Tier.NONE) return;
        MultiBackendTaiRuntime router = MultiBackendTaiRuntime.processInstance();
        if (router == null) return;
        ActivityManager.MemoryInfo info = memoryInfo();
        long availMem = info == null ? 0L : info.availMem;
        long threshold = info == null ? 0L : info.threshold;
        long floor = info == null ? 0L : TaiLoadBudget.floorBytes(info.threshold, info.totalMem);
        applyTier(tier, router.residency().snapshot(), availMem, floor, threshold, "trim " + level);
    }

    private final class IncomingHandler extends Handler {
        IncomingHandler() {
            super(Looper.getMainLooper());
        }

        @Override
        public void handleMessage(@NonNull Message message) {
            if (message.what != TaiRuntimeIpc.MSG_REQUEST || message.replyTo == null) {
                super.handleMessage(message);
                return;
            }
            Bundle data = message.getData();
            String requestId = data.getString(TaiRuntimeIpc.KEY_REQUEST_ID, "");
            String operation = data.getString(TaiRuntimeIpc.KEY_OPERATION, "");
            String body = data.getString(TaiRuntimeIpc.KEY_BODY, null);
            String bodyFile = data.getString(TaiRuntimeIpc.KEY_BODY_FILE, null);
            Messenger replyTo = message.replyTo;
            // A request means the client kept the binding; whatever exit was announced is off.
            lastClient = replyTo;
            idleExitAnnounced = false;
            inFlight.incrementAndGet();
            if (TaiRuntimeIpc.OP_STATUS.equals(operation) || TaiRuntimeIpc.OP_RUNTIME_STATUS.equals(operation)) {
                runRequest(replyTo, requestId, operation, body, bodyFile);
                return;
            }
            if (isConcurrentControlOperation(operation)) {
                // Generation occupies the serial native-work executor. Control operations need an
                // independent lane so they can signal it instead of queuing behind it.
                controlExecutor.execute(() -> runRequest(replyTo, requestId, operation, body, bodyFile));
                return;
            }
            if (isSttOperation(operation)) {
                sttExecutor.execute(() -> runRequest(replyTo, requestId, operation, body, bodyFile));
                return;
            }
            executor.execute(() -> runRequest(replyTo, requestId, operation, body, bodyFile));
        }
    }

    static boolean isConcurrentControlOperation(@NonNull String operation) {
        return TaiRuntimeIpc.OP_CANCEL.equals(operation) || TaiRuntimeIpc.OP_UNLOAD_MODEL.equals(operation);
    }

    /** Speech-to-text runs on {@link #sttExecutor}, never behind a generation on the serial lane. */
    static boolean isSttOperation(@NonNull String operation) {
        return TaiRuntimeIpc.OP_TRANSCRIBE.equals(operation) || TaiRuntimeIpc.OP_STT_WARM.equals(operation);
    }

    /** Status reads report; they do not count as the client using the runtime. */
    static boolean isStatusOperation(@NonNull String operation) {
        return TaiRuntimeIpc.OP_STATUS.equals(operation) || TaiRuntimeIpc.OP_RUNTIME_STATUS.equals(operation);
    }

    private void runRequest(
        @NonNull Messenger replyTo,
        @NonNull String requestId,
        @NonNull String operation,
        @Nullable String body,
        @Nullable String bodyFile
    ) {
        try {
            String payload = body != null ? body : readBodyFile(bodyFile);
            if (isForegroundOperation(operation)) {
                ensureForeground("TAI runtime", "Preparing " + operation);
            }
            if (TaiRuntimeIpc.OP_OPENAI_CHAT_STREAM.equals(operation)
                || TaiRuntimeIpc.OP_OPENAI_COMPLETION_STREAM.equals(operation)) {
                runStreamRequest(replyTo, requestId, operation, payload);
                return;
            }
            JSONObject result = runJsonRequest(operation, payload);
            sendResponse(replyTo, requestId, result);
        } catch (Throwable throwable) {
            sendResponse(replyTo, requestId, error(500, "tai_runtime_service_error", message(throwable)));
        } finally {
            deleteBodyFile(bodyFile);
            if (!isStatusOperation(operation)) lastActivityMs = System.currentTimeMillis();
            inFlight.decrementAndGet();
            updateForegroundAfterOperation();
        }
    }

    @NonNull
    private JSONObject runJsonRequest(@NonNull String operation, @NonNull String body) throws JSONException {
        TaiManager manager = TaiManager.getRuntimeProcessInstance(this);
        switch (operation) {
            case TaiRuntimeIpc.OP_STATUS:
                return manager.status();
            case TaiRuntimeIpc.OP_RUNTIME_STATUS:
                return manager.runtimeStatus();
            case TaiRuntimeIpc.OP_LOAD_MODEL:
                return manager.loadModel(body);
            case TaiRuntimeIpc.OP_UNLOAD_MODEL:
                return manager.unloadModel();
            case TaiRuntimeIpc.OP_KEEP_WARM:
                return manager.keepWarmRuntime(body);
            case TaiRuntimeIpc.OP_CANCEL:
                return manager.cancelRuntime();
            case TaiRuntimeIpc.OP_OPENAI_CHAT:
                return manager.openAiChatCompletions(body);
            case TaiRuntimeIpc.OP_OPENAI_COMPLETION:
                return manager.openAiCompletions(body);
            case TaiRuntimeIpc.OP_EMBEDDINGS:
                return manager.embeddings(body);
            case TaiRuntimeIpc.OP_PREFLIGHT:
                return manager.preflight(body);
            case TaiRuntimeIpc.OP_BENCHMARK:
                return manager.benchmark(body);
            case TaiRuntimeIpc.OP_TRANSCRIBE:
                return manager.transcribe(body);
            case TaiRuntimeIpc.OP_STT_WARM:
                return manager.sttWarm(body);
            default:
                return error(400, "bad_runtime_operation", "Unknown TAI runtime operation: " + operation);
        }
    }

    private void runStreamRequest(
        @NonNull Messenger replyTo,
        @NonNull String requestId,
        @NonNull String operation,
        @NonNull String body
    ) throws JSONException, IOException {
        TaiManager manager = TaiManager.getRuntimeProcessInstance(this);
        TaiManager.OpenAiStreamSink sink = new TaiManager.OpenAiStreamSink() {
            @Override
            public void onEvent(@NonNull JSONObject event) throws IOException {
                sendStreamEvent(replyTo, requestId, event);
            }

            @Override
            public void onDone() throws IOException {
                sendStreamDone(replyTo, requestId);
            }
        };
        if (TaiRuntimeIpc.OP_OPENAI_CHAT_STREAM.equals(operation)) {
            manager.openAiChatCompletionsStream(body, sink);
        } else {
            manager.openAiCompletionsStream(body, sink);
        }
    }

    private boolean isForegroundOperation(@NonNull String operation) {
        return TaiRuntimeIpc.OP_LOAD_MODEL.equals(operation)
            || TaiRuntimeIpc.OP_KEEP_WARM.equals(operation)
            || TaiRuntimeIpc.OP_OPENAI_CHAT.equals(operation)
            || TaiRuntimeIpc.OP_OPENAI_CHAT_STREAM.equals(operation)
            || TaiRuntimeIpc.OP_OPENAI_COMPLETION.equals(operation)
            || TaiRuntimeIpc.OP_OPENAI_COMPLETION_STREAM.equals(operation)
            || TaiRuntimeIpc.OP_BENCHMARK.equals(operation);
    }

    /** A chat model is held, coming up, warm or generating: the foreground and presence cases. */
    private static boolean chatActive(@NonNull TaiRuntimeState state) {
        return state.loaded || state.activeGeneration || "loading".equals(state.state) || "idle-warm".equals(state.state);
    }

    /** Whether any model — chat, embedding, STT — is in the registry; the RUNTIME baseline alone is not. */
    private static boolean modelsResident() {
        MultiBackendTaiRuntime router = MultiBackendTaiRuntime.processInstance();
        return router != null && router.residency().hasModels();
    }

    private void updateForegroundAfterOperation() {
        try {
            TaiManager manager = TaiManager.getRuntimeProcessInstance(this);
            TaiRuntimeState state = manager.getRuntimeState();
            TaiRuntimePresence.publish(this, state, manager.residentChatBytes());
            boolean chat = chatActive(state);
            if (chat) {
                ensureForeground("TAI runtime", state.status);
            } else if (foreground) {
                stopForeground(true);
                foreground = false;
            }
            // The watch runs for any resident, not only chat: an embedding interpreter left behind
            // by /v1/embeddings has an idle timer and a place in the pressure tiers too.
            if (chat || modelsResident()) startRuntimeWatch();
            else noteModelsGone();
        } catch (Exception ignored) {
        }
    }

    /**
     * The runtime's slow tick while anything is resident. It publishes the runtime state for the UI
     * glyph (the idle unload fires on a backend's own scheduler, with no operation to hang a publish
     * off — without this the glyph would keep counting down past a model that is already gone),
     * reads the phone's memory once and applies the pressure tier it is in, and closes residents
     * that have sat idle past their kind's limit. Decisions are {@link TaiPressureWatch}'s; the
     * tick holds no lock — the registry is read lock-free and the actions go to
     * {@link #pressureExecutor}. It stops itself once nothing but the baseline is left and arms
     * the idle exit.
     */
    private synchronized void startRuntimeWatch() {
        if (watch != null && !watch.isCancelled()) return;
        modelsGoneAtMs = 0L;
        watch = watchScheduler.scheduleWithFixedDelay(() -> {
            try {
                TaiManager manager = TaiManager.getRuntimeProcessInstance(this);
                TaiRuntimeState state = manager.getRuntimeState();
                boolean chat = chatActive(state);
                // Every tick while chat is held (the countdown), once more when it goes quiet, and
                // not at all for an embedding-only residency — the glyph does not show those.
                if (chat || !publishedIdle) TaiRuntimePresence.publish(this, state, manager.residentChatBytes());
                publishedIdle = !chat;
                MultiBackendTaiRuntime router = MultiBackendTaiRuntime.processInstance();
                List<TaiResidency.Entry> residents = router == null
                    ? Collections.<TaiResidency.Entry>emptyList() : router.residency().snapshot();
                if (!chat && (router == null || !router.residency().hasModels())) {
                    stopRuntimeWatch();
                    noteModelsGone();
                    return;
                }
                modelsGoneAtMs = 0L;
                evaluatePressure(residents);
                evaluateIdle(residents);
            } catch (Exception ignored) {
            }
        }, WATCH_INTERVAL_MS, WATCH_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    private synchronized void stopRuntimeWatch() {
        if (watch == null) return;
        watch.cancel(false);
        watch = null;
    }

    /** One {@code MemoryInfo} reading, or {@code null} when the activity manager is not there. */
    @Nullable
    private ActivityManager.MemoryInfo memoryInfo() {
        ActivityManager activityManager = getSystemService(ActivityManager.class);
        if (activityManager == null) return null;
        ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
        activityManager.getMemoryInfo(info);
        return info;
    }

    /**
     * Reads the phone once and acts on the tier it is in. From Android 14 the running trim levels
     * are no longer delivered, so the watch asks instead of waiting to be told. The floor is the
     * budget's ({@link TaiLoadBudget#floorBytes}: twice {@code MemoryInfo.threshold}, at least
     * 512 MiB), so the watch starts reclaiming at the same line a load stops being admitted.
     */
    private void evaluatePressure(@NonNull List<TaiResidency.Entry> residents) {
        ActivityManager.MemoryInfo info = memoryInfo();
        if (info == null) return;
        long floor = TaiLoadBudget.floorBytes(info.threshold, info.totalMem);
        TaiPressureWatch.Tier tier = TaiPressureWatch.tier(info.availMem, floor, info.threshold, info.lowMemory);
        applyTier(tier, residents, info.availMem, floor, info.threshold, "poll");
    }

    /**
     * Carries out one tier: tier 3 cancels and unloads everything; tiers 1 and 2 give up the one
     * resident {@link TaiPressureWatch#nextVictim} names, and the next tick looks again. A tier
     * with nothing idle to give (a busy chat model alone) does nothing and logs nothing — it would
     * log every two seconds otherwise.
     */
    private void applyTier(@NonNull TaiPressureWatch.Tier tier, @NonNull List<TaiResidency.Entry> residents,
                           long availMem, long floor, long threshold, @NonNull String source) {
        if (tier == TaiPressureWatch.Tier.NONE) return;
        String reading = String.format(Locale.US, "availMem=%d MB floor=%d MB threshold=%d MB",
            availMem / MIB, floor / MIB, threshold / MIB);
        if (tier == TaiPressureWatch.Tier.RELEASE_ALL) {
            queuePressureAction(() -> releaseAll("pressure tier 3 (" + source + "): lowMemory, " + reading));
            return;
        }
        TaiResidency.Entry victim = TaiPressureWatch.nextVictim(residents, tier);
        if (victim == null) return;
        int number = tier == TaiPressureWatch.Tier.CHAT ? 2 : 1;
        queuePressureAction(() -> evict(Collections.singletonList(victim),
            "pressure tier " + number + " (" + source + "): " + reading));
    }

    /**
     * Closes the residents that have outlived their kind's idle limit; see {@link TaiPressureWatch#idleExpired}.
     * The STT limit is the settings value the app process sent with its last speech request.
     */
    private void evaluateIdle(@NonNull List<TaiResidency.Entry> residents) {
        long sttIdleMs = TaiManager.getRuntimeProcessInstance(this).sttIdleLimitMs();
        List<TaiResidency.Entry> expired = TaiPressureWatch.idleExpired(residents, System.currentTimeMillis(), sttIdleMs);
        if (expired.isEmpty()) return;
        queuePressureAction(() -> evict(expired, "idle"));
    }

    private interface MemoryAction {
        void run() throws Exception;
    }

    private void queuePressureAction(@NonNull MemoryAction action) {
        if (!pressureActionQueued.compareAndSet(false, true)) return;
        pressureExecutor.execute(() -> {
            try {
                action.run();
            } catch (Exception e) {
                Log.w(LOG_TAG, "memory action failed: " + message(e));
            } finally {
                pressureActionQueued.set(false);
                updateForegroundAfterOperation();
            }
        });
    }

    /**
     * Closes {@code victims} through the router, which re-reads each one and skips any that has
     * become busy or has gone since the watch chose it, and logs what actually happened to each.
     */
    private void evict(@NonNull List<TaiResidency.Entry> victims, @NonNull String reason) throws JSONException {
        MultiBackendTaiRuntime router = MultiBackendTaiRuntime.processInstance();
        if (router == null) return;
        List<String> evicted = router.evict(victims);
        long now = System.currentTimeMillis();
        for (TaiResidency.Entry victim : victims) {
            String outcome = evicted.contains(victim.modelId) ? "evicted" : "kept (busy or already gone)";
            Log.i(LOG_TAG, String.format(Locale.US, "%s: %s %s %s (%d MB, last used %d s ago)", reason, outcome,
                victim.kind.name().toLowerCase(Locale.ROOT), victim.modelId, victim.bytes() / MIB,
                Math.max(0L, now - victim.lastUsedMs) / 1000L));
        }
    }

    /**
     * Gives everything back when the phone runs out: the home screen and whatever the user is
     * doing matter more than a resident model, which reloads on the next request. The budget keeps
     * loads from getting here and the lower tiers give up idle residents first; this is for the
     * phone filling up around a model that is busy.
     */
    private void releaseAll(@NonNull String reason) throws JSONException {
        Log.i(LOG_TAG, reason + ": cancelling in-flight work and unloading everything");
        TaiManager manager = TaiManager.getRuntimeProcessInstance(this);
        manager.cancelRuntime();
        manager.unloadModel();
    }

    /** Nothing but the baseline is resident: stamp when, and arm the idle exit. */
    private void noteModelsGone() {
        if (modelsGoneAtMs == 0L) modelsGoneAtMs = System.currentTimeMillis();
        armIdleExitCheck();
    }

    /** The later of the last real request and the last model leaving; what the exit timer counts from. */
    private long idleSinceMs() {
        return Math.max(lastActivityMs, modelsGoneAtMs);
    }

    /**
     * Schedules {@link #checkIdleExit} for when the baseline will have been alone for
     * {@link TaiPressureWatch#IDLE_EXIT_MS}. Nothing is armed for a process that never loaded a chat
     * model (an empty registry: there is no baseline to give back) and nothing while models are
     * resident (the watch is running and calls back here when they go).
     */
    private synchronized void armIdleExitCheck() {
        if (idleExitCheck != null && !idleExitCheck.isDone()) return;
        MultiBackendTaiRuntime router = MultiBackendTaiRuntime.processInstance();
        if (router == null || router.residency().snapshot().isEmpty() || router.residency().hasModels()) return;
        long delay = Math.max(1_000L, idleSinceMs() + TaiPressureWatch.IDLE_EXIT_MS - System.currentTimeMillis());
        idleExitCheck = watchScheduler.schedule(this::checkIdleExit, delay, TimeUnit.MILLISECONDS);
    }

    private synchronized void cancelIdleExitCheck() {
        if (idleExitCheck == null) return;
        idleExitCheck.cancel(false);
        idleExitCheck = null;
    }

    /**
     * Decides the exit from fresh numbers. A request in flight is left to re-arm the check when it
     * finishes (every request ends in {@link #updateForegroundAfterOperation}); a chat load or
     * generation in progress likewise. A crash marker is only ever set inside a load, so an exit
     * that refuses to overlap one never leaves a marker behind.
     */
    private void checkIdleExit() {
        try {
            MultiBackendTaiRuntime router = MultiBackendTaiRuntime.processInstance();
            if (router == null || router.residency().hasModels()) return;
            TaiManager manager = TaiManager.getRuntimeProcessInstance(this);
            if (chatActive(manager.getRuntimeState())) return;
            List<TaiResidency.Entry> residents = router.residency().snapshot();
            if (!TaiPressureWatch.idleExitDue(residents, inFlight.get(), idleSinceMs(), System.currentTimeMillis())) {
                if (inFlight.get() == 0) armIdleExitCheck();
                return;
            }
            announceIdleExit();
        } catch (Exception e) {
            Log.w(LOG_TAG, "idle exit check failed: " + message(e));
        }
    }

    /**
     * Asks the client to unbind. A bound service cannot end its own process — {@code stopSelf} does
     * nothing while the UI process holds a {@code BIND_AUTO_CREATE} binding — so the client that
     * spoke to us last is told, it unbinds when it has nothing pending, and {@link #onDestroy}
     * exits. A client that ignores the notice (a request of its own was in flight) sends that
     * request, which clears {@link #idleExitAnnounced}, and the timer starts over from it.
     */
    private void announceIdleExit() {
        Messenger client = lastClient;
        if (client == null) {
            Log.i(LOG_TAG, "idle exit due, but no client has spoken to this service; staying");
            return;
        }
        Log.i(LOG_TAG, String.format(Locale.US,
            "idle exit: nothing but the runtime baseline (%d MB) resident for %d min; asking the client to unbind",
            TaiResidency.RUNTIME_BASELINE_BYTES / MIB, TaiPressureWatch.IDLE_EXIT_MS / 60_000L));
        idleExitAnnounced = true;
        try {
            client.send(Message.obtain(null, MSG_IDLE_EXIT));
        } catch (RemoteException e) {
            // The client's process is gone; its binding went with it, and Android decides the rest.
            idleExitAnnounced = false;
            lastClient = null;
            Log.i(LOG_TAG, "idle exit: the last client is gone (" + message(e) + "); staying");
        }
    }

    private void ensureForeground(@NonNull String title, @NonNull String text) {
        ensureChannel();
        try {
            startForeground(NOTIFICATION_ID, buildNotification(title, text));
            foreground = true;
        } catch (RuntimeException e) {
            foreground = false;
        }
    }

    private Notification buildNotification(@NonNull String title, @NonNull String text) {
        Intent settingsIntent = new Intent(this, SettingsActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
            this,
            0,
            settingsIntent,
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0
        );
        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_service_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(pendingIntent)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build();
    }

    private void ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return;
        NotificationManager manager = getSystemService(NotificationManager.class);
        if (manager == null || manager.getNotificationChannel(CHANNEL_ID) != null) return;
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "TAI runtime", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Termux AI model runtime process");
        manager.createNotificationChannel(channel);
    }

    private void sendResponse(@NonNull Messenger replyTo, @NonNull String requestId, @NonNull JSONObject result) {
        Bundle data = new Bundle();
        data.putString(TaiRuntimeIpc.KEY_REQUEST_ID, requestId);
        data.putString(TaiRuntimeIpc.KEY_RESULT, result.toString());
        send(replyTo, TaiRuntimeIpc.MSG_RESPONSE, data);
    }

    private void sendStreamEvent(@NonNull Messenger replyTo, @NonNull String requestId, @NonNull JSONObject event) throws IOException {
        Bundle data = new Bundle();
        data.putString(TaiRuntimeIpc.KEY_REQUEST_ID, requestId);
        data.putString(TaiRuntimeIpc.KEY_EVENT, event.toString());
        sendOrThrow(replyTo, TaiRuntimeIpc.MSG_STREAM_EVENT, data);
    }

    private void sendStreamDone(@NonNull Messenger replyTo, @NonNull String requestId) throws IOException {
        Bundle data = new Bundle();
        data.putString(TaiRuntimeIpc.KEY_REQUEST_ID, requestId);
        sendOrThrow(replyTo, TaiRuntimeIpc.MSG_STREAM_DONE, data);
    }

    private void send(@NonNull Messenger replyTo, int what, @NonNull Bundle data) {
        try {
            sendOrThrow(replyTo, what, data);
        } catch (IOException ignored) {
        }
    }

    private void sendOrThrow(@NonNull Messenger replyTo, int what, @NonNull Bundle data) throws IOException {
        Message message = Message.obtain(null, what);
        message.setData(data);
        try {
            replyTo.send(message);
        } catch (RemoteException e) {
            throw new IOException(e);
        }
    }

    @NonNull
    private String readBodyFile(@Nullable String path) throws IOException {
        if (path == null || path.trim().isEmpty()) return "";
        File file = new File(path);
        try (InputStream stream = new FileInputStream(file);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = stream.read(buffer)) != -1) output.write(buffer, 0, read);
            return output.toString(StandardCharsets.UTF_8.name());
        }
    }

    private void deleteBodyFile(@Nullable String path) {
        if (path == null || path.trim().isEmpty()) return;
        try {
            //noinspection ResultOfMethodCallIgnored
            new File(path).delete();
        } catch (Exception ignored) {
        }
    }

    @NonNull
    private JSONObject error(int statusCode, @NonNull String code, @NonNull String message) {
        JSONObject data = new JSONObject();
        try {
            data.put("ok", false);
            data.put("error", code);
            data.put("message", message);
            data.put("_statusCode", statusCode);
        } catch (JSONException ignored) {
        }
        return data;
    }

    @NonNull
    private String message(@NonNull Throwable throwable) {
        String message = throwable.getMessage();
        return message == null || message.trim().isEmpty() ? throwable.getClass().getSimpleName() : message;
    }
}
