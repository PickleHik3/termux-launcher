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
import android.os.PowerManager;
import android.os.Process;
import android.os.RemoteException;
import android.os.SystemClock;
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
     * Speech output has a lane of its own too: speaking lasts as long as the text takes to say, and
     * neither a chat generation nor a transcription should wait for it, nor it for them. One
     * thread, so utterances play one after another rather than over each other.
     */
    private final ExecutorService ttsExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "tai-runtime-tts");
        thread.setDaemon(true);
        return thread;
    });
    /** Speech-output requests running now; the service stays in the foreground while one plays. */
    private final AtomicInteger ttsInFlight = new AtomicInteger();
    /**
     * Image generation has its own lane: a run lasts from seconds to minutes and holds the GPU, and
     * neither a chat turn nor a transcription should wait for it, nor it for them. One thread, so
     * images are made one at a time (the runtime also refuses a second with image_generation_active).
     */
    private final ExecutorService imageExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "tai-runtime-image");
        thread.setDaemon(true);
        return thread;
    });
    /** Image generations running now; the service stays in the foreground while one runs. */
    private final AtomicInteger imageInFlight = new AtomicInteger();
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
    /** A {@link TaiRuntimeIpc#OP_BENCH_RUN} is on the serial lane; see {@link #isRefusedDuringBench}. */
    private final AtomicBoolean benchRunning = new AtomicBoolean();
    /**
     * Held for a bench run so the CPU keeps running with the screen off; the timeout is a ceiling
     * for a run that never reaches its {@code finally}, not a run length.
     */
    static final long BENCH_WAKE_LOCK_CEILING_MS = 4 * 60 * 60_000L;
    @Nullable private PowerManager.WakeLock benchWakeLock;
    private final Messenger messenger = new Messenger(new IncomingHandler());
    private volatile boolean foreground;
    private final ScheduledExecutorService watchScheduler =
        Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "tai-runtime-watch");
            thread.setDaemon(true);
            return thread;
        });
    @Nullable private ScheduledFuture<?> watch;
    /** Bumped on every start and stop, so a tick that outlives its watch does not schedule another. */
    private int watchEpoch;
    /** When the watch last did its slow work (publish, idle checks); the pressure read runs on every tick. */
    private long lastSlowTickMs;
    /**
     * The deadline armed for a momentary load: the runtime itself unloads that model when it
     * passes, whether or not the caller is still waiting (review T3). Guarded by {@code this}.
     */
    @Nullable private ScheduledFuture<?> momentaryDeadline;
    @Nullable private String momentaryModelId;
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
        // Before any MNN or LiteRT library loads in this process: MNN prints prompts at DEBUG.
        TaiNativeLog.silenceDebugOnce();
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
        clearMomentaryDeadline();
        cancelIdleExitCheck();
        releaseBenchWakeLock();
        watchScheduler.shutdownNow();
        executor.shutdownNow();
        controlExecutor.shutdownNow();
        sttExecutor.shutdownNow();
        ttsExecutor.shutdownNow();
        imageExecutor.shutdownNow();
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
            if (benchRunning.get() && isRefusedDuringBench(operation)) {
                // Answered here rather than queued: the bench holds the serial lane for minutes,
                // and a chat request left waiting behind it would only time out on the client.
                sendResponse(replyTo, requestId, error(409, "benchmark_running",
                    "A benchmark is running; wait for it to finish or stop it with tai cancel."));
                deleteBodyFile(bodyFile);
                inFlight.decrementAndGet();
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
            if (isTtsOperation(operation)) {
                ttsExecutor.execute(() -> runRequest(replyTo, requestId, operation, body, bodyFile));
                return;
            }
            if (isImageOperation(operation)) {
                imageExecutor.execute(() -> runRequest(replyTo, requestId, operation, body, bodyFile));
                return;
            }
            // Raised as the bench is queued, so everything already ahead of it on the serial lane
            // is still served and everything after it is refused at once rather than left to wait.
            if (TaiRuntimeIpc.OP_BENCH_RUN.equals(operation)) benchRunning.set(true);
            executor.execute(() -> runRequest(replyTo, requestId, operation, body, bodyFile));
        }
    }

    static boolean isConcurrentControlOperation(@NonNull String operation) {
        return TaiRuntimeIpc.OP_CANCEL.equals(operation) || TaiRuntimeIpc.OP_UNLOAD_MODEL.equals(operation)
            || TaiRuntimeIpc.OP_TTS_STOP.equals(operation) || TaiRuntimeIpc.OP_IMAGE_CANCEL.equals(operation)
            || TaiRuntimeIpc.OP_BENCH_SKIP_WAIT.equals(operation)
            || TaiRuntimeIpc.OP_BENCH_HOLD.equals(operation);
    }

    /**
     * The busy rule for a running bench: everything that would load, generate or embed on the chat
     * lane is refused with {@code benchmark_running}, a second bench included. Status reads pass;
     * cancel and unload pass on the control lane and end the bench ({@link TaiManager#cancelRuntime},
     * {@link TaiManager#unloadModel} stop the harness first); preflight only reads; speech in and
     * out have lanes and models of their own and are left to the user, who can hear them. Image
     * generation is refused: it holds the GPU and gigabytes of memory, which a measurement must not share.
     */
    static boolean isRefusedDuringBench(@NonNull String operation) {
        return TaiRuntimeIpc.OP_LOAD_MODEL.equals(operation)
            || TaiRuntimeIpc.OP_KEEP_WARM.equals(operation)
            || TaiRuntimeIpc.OP_OPENAI_CHAT.equals(operation)
            || TaiRuntimeIpc.OP_OPENAI_CHAT_STREAM.equals(operation)
            || TaiRuntimeIpc.OP_OPENAI_COMPLETION.equals(operation)
            || TaiRuntimeIpc.OP_OPENAI_COMPLETION_STREAM.equals(operation)
            || TaiRuntimeIpc.OP_EMBEDDINGS.equals(operation)
            || TaiRuntimeIpc.OP_TOKENIZE.equals(operation)
            || TaiRuntimeIpc.OP_BENCHMARK.equals(operation)
            || TaiRuntimeIpc.OP_BENCH_RUN.equals(operation)
            || TaiRuntimeIpc.OP_IMAGE_GENERATE.equals(operation);
    }

    /** Image generation runs on {@link #imageExecutor}; its cancel is a control operation, not one of these. */
    static boolean isImageOperation(@NonNull String operation) {
        return TaiRuntimeIpc.OP_IMAGE_GENERATE.equals(operation);
    }

    /** Speech output runs on {@link #ttsExecutor}; its stop is a control operation, not one of these. */
    static boolean isTtsOperation(@NonNull String operation) {
        return TaiRuntimeIpc.OP_TTS_SPEAK.equals(operation) || TaiRuntimeIpc.OP_TTS_SYNTHESIZE.equals(operation)
            || TaiRuntimeIpc.OP_TTS_WARM.equals(operation);
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
        boolean speech = isTtsOperation(operation);
        boolean image = isImageOperation(operation);
        boolean bench = TaiRuntimeIpc.OP_BENCH_RUN.equals(operation);
        if (speech) ttsInFlight.incrementAndGet();
        if (image) imageInFlight.incrementAndGet();
        try {
            String payload = body != null ? body : readBodyFile(bodyFile);
            // The deadline runs from here, the start of the load, not from the caller's request.
            trackMomentaryLoad(operation, payload);
            if (TaiRuntimeIpc.OP_UNLOAD_MODEL.equals(operation)) clearMomentaryDeadline();
            if (bench) acquireBenchWakeLock();
            if (isForegroundOperation(operation)) {
                // Before the work, so a first load is watched too: nothing else starts the watch until it ends.
                startRuntimeWatch();
                ensureForeground("On-device AI runtime", speech ? "Speaking" : image ? "Generating an image"
                    : bench ? "Benchmarking" : "Preparing " + operation);
            }
            if (TaiRuntimeIpc.OP_OPENAI_CHAT_STREAM.equals(operation)
                || TaiRuntimeIpc.OP_OPENAI_COMPLETION_STREAM.equals(operation)
                || TaiRuntimeIpc.OP_TTS_SYNTHESIZE.equals(operation)
                || TaiRuntimeIpc.OP_IMAGE_GENERATE.equals(operation)
                || bench) {
                runStreamRequest(replyTo, requestId, operation, payload);
                return;
            }
            JSONObject result = runJsonRequest(operation, payload);
            sendResponse(replyTo, requestId, result);
        } catch (Throwable throwable) {
            sendResponse(replyTo, requestId, error(500, "tai_runtime_service_error", message(throwable)));
        } finally {
            if (bench) {
                releaseBenchWakeLock();
                benchRunning.set(false);
            }
            if (speech) ttsInFlight.decrementAndGet();
            if (image) imageInFlight.decrementAndGet();
            deleteBodyFile(bodyFile);
            if (!isStatusOperation(operation)) lastActivityMs = System.currentTimeMillis();
            inFlight.decrementAndGet();
            updateForegroundAfterOperation();
        }
    }

    private synchronized void acquireBenchWakeLock() {
        releaseBenchWakeLock();
        try {
            PowerManager power = (PowerManager) getSystemService(POWER_SERVICE);
            if (power == null) return;
            PowerManager.WakeLock lock = power.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "termux:tai-bench");
            lock.setReferenceCounted(false);
            lock.acquire(BENCH_WAKE_LOCK_CEILING_MS);
            benchWakeLock = lock;
        } catch (RuntimeException e) {
            Log.w(LOG_TAG, "Could not hold a wake lock for the benchmark", e);
        }
    }

    private synchronized void releaseBenchWakeLock() {
        PowerManager.WakeLock lock = benchWakeLock;
        benchWakeLock = null;
        try {
            if (lock != null && lock.isHeld()) lock.release();
        } catch (RuntimeException ignored) {
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
            case TaiRuntimeIpc.OP_BENCH_SKIP_WAIT:
                return manager.skipBenchCooldown();
            case TaiRuntimeIpc.OP_BENCH_HOLD:
                return manager.holdBench(body);
            case TaiRuntimeIpc.OP_OPENAI_CHAT:
                return manager.openAiChatCompletions(body);
            case TaiRuntimeIpc.OP_OPENAI_COMPLETION:
                return manager.openAiCompletions(body);
            case TaiRuntimeIpc.OP_EMBEDDINGS:
                return manager.embeddings(body);
            case TaiRuntimeIpc.OP_TOKENIZE:
                return manager.tokenize(body);
            case TaiRuntimeIpc.OP_PREFLIGHT:
                return manager.preflight(body);
            case TaiRuntimeIpc.OP_BENCHMARK:
                return manager.benchmark(body);
            case TaiRuntimeIpc.OP_TRANSCRIBE:
                return manager.transcribe(body);
            case TaiRuntimeIpc.OP_STT_WARM:
                return manager.sttWarm(body);
            case TaiRuntimeIpc.OP_TTS_SPEAK:
                return manager.speak(body);
            case TaiRuntimeIpc.OP_TTS_WARM:
                return manager.ttsWarm(body);
            case TaiRuntimeIpc.OP_TTS_STOP:
                return manager.stopSpeaking();
            case TaiRuntimeIpc.OP_IMAGE_CANCEL:
                return manager.cancelImage();
            default:
                return error(400, "bad_runtime_operation", "Unknown On-device AI runtime operation: " + operation);
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
        if (TaiRuntimeIpc.OP_TTS_SYNTHESIZE.equals(operation)) {
            manager.synthesizeSpeechToEvents(body, sink);
        } else if (TaiRuntimeIpc.OP_IMAGE_GENERATE.equals(operation)) {
            manager.generateImageToEvents(body, sink);
        } else if (TaiRuntimeIpc.OP_BENCH_RUN.equals(operation)) {
            manager.benchRun(body, sink);
        } else if (TaiRuntimeIpc.OP_OPENAI_CHAT_STREAM.equals(operation)) {
            manager.openAiChatCompletionsStream(body, sink);
        } else {
            manager.openAiCompletionsStream(body, sink);
        }
    }

    static boolean isForegroundOperation(@NonNull String operation) {
        return TaiRuntimeIpc.OP_LOAD_MODEL.equals(operation)
            || TaiRuntimeIpc.OP_KEEP_WARM.equals(operation)
            || TaiRuntimeIpc.OP_OPENAI_CHAT.equals(operation)
            || TaiRuntimeIpc.OP_OPENAI_CHAT_STREAM.equals(operation)
            || TaiRuntimeIpc.OP_OPENAI_COMPLETION.equals(operation)
            || TaiRuntimeIpc.OP_OPENAI_COMPLETION_STREAM.equals(operation)
            || TaiRuntimeIpc.OP_BENCHMARK.equals(operation)
            || TaiRuntimeIpc.OP_BENCH_RUN.equals(operation)
            // Not only for keep-alive: as a plain bound service this process sits in the OEM's
            // little-core cpuset (pong: nt_foreground = CPUs 0-3), which made Whisper 2.5-3x slower.
            || TaiRuntimeIpc.OP_TRANSCRIBE.equals(operation)
            || TaiRuntimeIpc.OP_STT_WARM.equals(operation)
            // Speech output for the same cpuset reason, and so playback is not cut off when the
            // launcher goes to the background mid-sentence.
            || isTtsOperation(operation)
            // Image generation holds the GPU for seconds to minutes; it must not be cut off when
            // the launcher goes to the background, nor run in the little-core cpuset.
            || isImageOperation(operation);
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
            if (ttsInFlight.get() > 0) {
                // Speaking: stay in the foreground whatever else finished just now.
                ensureForeground("On-device AI runtime", chat ? state.status : "Speaking");
            } else if (imageInFlight.get() > 0) {
                ensureForeground("On-device AI runtime", chat ? state.status : "Generating an image");
            } else if (chat) {
                ensureForeground("On-device AI runtime", state.status);
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
     * The runtime's tick while anything is resident. It publishes the runtime state for the UI
     * glyph (the idle unload fires on a backend's own scheduler, with no operation to hang a publish
     * off — without this the glyph would keep counting down past a model that is already gone),
     * reads the phone's memory and applies the pressure tier it is in, and closes residents
     * that have sat idle past their kind's limit. Decisions are {@link TaiPressureWatch}'s; the
     * tick holds no lock — the registry is read lock-free and the actions go to
     * {@link #pressureExecutor}. The pressure read runs every {@link TaiPressureWatch#POLL_BUSY_MS}
     * while a load or first prefill is running and every {@link TaiPressureWatch#POLL_IDLE_MS}
     * otherwise; the publish and idle checks always keep the slow beat. It stops itself once
     * nothing but the baseline is left and arms the idle exit.
     */
    private synchronized void startRuntimeWatch() {
        if (watch != null && !watch.isCancelled()) return;
        modelsGoneAtMs = 0L;
        // The first look comes soon: the watch is usually started at the edge of a load, and the tick
        // after it sets its own pace.
        scheduleWatchTick(++watchEpoch, TaiPressureWatch.POLL_BUSY_MS);
    }

    private synchronized void scheduleWatchTick(int epoch, long delayMs) {
        watch = watchScheduler.schedule(() -> watchTick(epoch), delayMs, TimeUnit.MILLISECONDS);
    }

    private void watchTick(int epoch) {
        try {
            TaiManager manager = TaiManager.getRuntimeProcessInstance(this);
            TaiRuntimeState state = manager.getRuntimeState();
            boolean chat = chatActive(state);
            long now = SystemClock.elapsedRealtime();
            boolean slow = now - lastSlowTickMs >= TaiPressureWatch.POLL_IDLE_MS;
            if (slow) {
                lastSlowTickMs = now;
                // Every slow tick while chat is held (the countdown), once more when it goes quiet,
                // and not at all for an embedding-only residency — the glyph does not show those.
                if (chat || !publishedIdle) TaiRuntimePresence.publish(this, state, manager.residentChatBytes());
                publishedIdle = !chat;
            }
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
            if (slow) evaluateIdle(residents);
        } catch (Exception ignored) {
        }
        synchronized (this) {
            if (epoch != watchEpoch || watch == null) return;
            scheduleWatchTick(epoch, TaiPressureWatch.pollIntervalMs(TaiLoadMeter.anyActive()));
        }
    }

    private synchronized void stopRuntimeWatch() {
        watchEpoch++;
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
     * are no longer delivered, so the watch asks instead of waiting to be told. Free memory is
     * {@link TaiMemInfo}'s (MemAvailable on every release) and the floors are the budget's
     * ({@link TaiLoadBudget#floorBytes}), so the watch starts reclaiming at the line a load stops
     * being admitted: the hold floor for tier 1, the lower peak floor for tier 2.
     */
    private void evaluatePressure(@NonNull List<TaiResidency.Entry> residents) {
        ActivityManager.MemoryInfo info = memoryInfo();
        if (info == null) return;
        TaiMemInfo.Reading memory = TaiMemInfo.read(this);
        TaiLoadBudget.Conditions conditions = TaiMemInfo.conditions(this, memory);
        long ramClass = TaiLoadBudget.ramClassBytes(info.totalMem);
        long hold = TaiLoadBudget.floorBytes(ramClass, false, conditions);
        long peak = TaiLoadBudget.floorBytes(ramClass, true, conditions);
        TaiPressureWatch.Tier tier = TaiPressureWatch.tier(memory.availBytes, hold, peak, info.lowMemory);
        applyTier(tier, residents, memory.availBytes, hold, peak, "poll");
    }

    /**
     * Carries out one tier: tier 3 cancels and unloads everything; tiers 1 and 2 give up the one
     * resident {@link TaiPressureWatch#nextVictim} names, and the next tick looks again. A tier
     * with nothing idle to give (a busy chat model alone) does nothing and logs nothing — it would
     * log every tick otherwise.
     */
    private void applyTier(@NonNull TaiPressureWatch.Tier tier, @NonNull List<TaiResidency.Entry> residents,
                           long availMem, long hold, long peak, @NonNull String source) {
        if (tier == TaiPressureWatch.Tier.NONE) return;
        String reading = String.format(Locale.US, "avail=%d MB hold=%d MB peak=%d MB",
            availMem / MIB, hold / MIB, peak / MIB);
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

    // ---- Momentary loads ------------------------------------------------------------------------

    /** Requests that may load a chat model: an explicit load, or a chat or completion that loads on demand. */
    static boolean isLoadingOperation(@NonNull String operation) {
        return TaiRuntimeIpc.OP_LOAD_MODEL.equals(operation)
            || TaiRuntimeIpc.OP_OPENAI_CHAT.equals(operation)
            || TaiRuntimeIpc.OP_OPENAI_CHAT_STREAM.equals(operation)
            || TaiRuntimeIpc.OP_OPENAI_COMPLETION.equals(operation)
            || TaiRuntimeIpc.OP_OPENAI_COMPLETION_STREAM.equals(operation);
    }

    /**
     * The model a request wants loaded for a moment: its {@code model} when the body declares
     * {@code "load_class": "momentary"} on a load or a chat that may load; {@code null} for anything
     * else, a body without a model included (the default model is the app's, not a momentary one).
     * The cheap substring test keeps a large image body from being parsed twice.
     */
    @Nullable
    static String momentaryModelId(@NonNull String operation, @Nullable String body) {
        if (body == null || !isLoadingOperation(operation) || !body.contains("\"load_class\"")) return null;
        try {
            JSONObject request = new JSONObject(body);
            if (!"momentary".equals(request.optString("load_class", "").trim())) return null;
            String model = request.optString("model", request.optString("modelId", "")).trim();
            return model.isEmpty() ? null : TaiSettings.migrateBuiltInModelId(model);
        } catch (JSONException e) {
            return null;
        }
    }

    /** Whether a momentary load armed at {@code armedAtMs} has outlived {@link TaiLoadBudget#MOMENTARY_DEADLINE_MS}. */
    static boolean momentaryDeadlineDue(long armedAtMs, long nowMs) {
        return nowMs - armedAtMs >= TaiLoadBudget.MOMENTARY_DEADLINE_MS;
    }

    /**
     * Whether the deadline for {@code armedModelId} has anything to unload: that model is the loaded
     * one (idle, or generating, which the unload cancels), or a load is still in progress and no
     * model is loaded yet. A different model, or nothing, means the caller already did its part.
     */
    static boolean unloadAtDeadline(@NonNull String armedModelId, @Nullable String loadedModelId, @Nullable String runtimeState) {
        if (armedModelId.equals(loadedModelId)) return true;
        return loadedModelId == null && "loading".equals(runtimeState);
    }

    /**
     * Arms the three-minute deadline for a momentary load that is about to start, from now: the
     * runtime then unloads the model itself, whether or not the caller's IPC wait has already timed
     * out (review T3: the director's 180 s ran out during a load, the unload was skipped because
     * nothing was loaded yet, and E4B stayed resident for the idle timer). A model that is already
     * resident is not this call's load and is left alone. Any other request for the armed model
     * disarms it, because someone now wants the model kept.
     */
    private synchronized void trackMomentaryLoad(@NonNull String operation, @Nullable String body) {
        if (!isLoadingOperation(operation)) return;
        String momentary = momentaryModelId(operation, body);
        if (momentary == null) {
            if (momentaryModelId != null && body != null && body.contains(momentaryModelId)) clearMomentaryDeadline();
            return;
        }
        MultiBackendTaiRuntime router = MultiBackendTaiRuntime.processInstance();
        if (router != null && router.residency().isResident(TaiResidency.Kind.CHAT, momentary)) return;
        clearMomentaryDeadline();
        momentaryModelId = momentary;
        final String armedModelId = momentary;
        momentaryDeadline = watchScheduler.schedule(() -> momentaryDeadlineExpired(armedModelId),
            TaiLoadBudget.MOMENTARY_DEADLINE_MS, TimeUnit.MILLISECONDS);
    }

    private synchronized void clearMomentaryDeadline() {
        if (momentaryDeadline != null) momentaryDeadline.cancel(false);
        momentaryDeadline = null;
        momentaryModelId = null;
    }

    private void momentaryDeadlineExpired(@NonNull String modelId) {
        synchronized (this) {
            if (!modelId.equals(momentaryModelId)) return;
            momentaryDeadline = null;
            momentaryModelId = null;
        }
        // On the control lane: the serial lane may be the very load or generation being ended.
        controlExecutor.execute(() -> {
            try {
                TaiManager manager = TaiManager.getRuntimeProcessInstance(this);
                TaiRuntimeState state = manager.getRuntimeState();
                if (!unloadAtDeadline(modelId, state.loadedModelId, state.state)) return;
                Log.i(LOG_TAG, "momentary deadline: cancelling and unloading " + modelId);
                manager.cancelRuntime();
                manager.unloadModel();
                updateForegroundAfterOperation();
            } catch (Exception e) {
                Log.w(LOG_TAG, "momentary deadline unload failed: " + message(e));
            }
        });
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
            if (evicted.contains(victim.modelId)) {
                TaiEventLog.log(this, TaiEventLog.EVICT, victim.modelId, victim.backend, victim.accelerator,
                    victim.window, 0L, victim.bytes(), reason + ", " + victim.kind.name().toLowerCase(Locale.ROOT));
            }
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
        TaiEventLog.log(this, TaiEventLog.OOM_GUARD, reason + ": cancelling in-flight work and unloading everything");
        TaiManager manager = TaiManager.getRuntimeProcessInstance(this);
        manager.cancelRuntime(TaiBenchHarness.STOP_MEMORY_PRESSURE);
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
        TaiEventLog.log(this, TaiEventLog.IDLE_EXIT, "nothing but the runtime baseline resident for "
            + TaiPressureWatch.IDLE_EXIT_MS / 60_000L + " min");
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
        NotificationChannel channel = new NotificationChannel(CHANNEL_ID, "On-device AI runtime", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("On-device AI model runtime process");
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
