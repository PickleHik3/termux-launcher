package com.termux.ai;

import android.app.ActivityManager;
import android.content.Context;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Measures what a native load costs the phone: free memory ({@link TaiMemInfo}: MemAvailable)
 * sampled every 100 ms on a helper thread from just before native initialization, with
 * {@code before − minimum} as the cost.
 *
 * <p>Two readings come from one meter. {@link #endLoad} is the {@code load} phase: the drop up to
 * the end of {@code initialize()}. The meter then keeps running until
 * {@link #firstTokenDrop}, the {@code first_prefill} phase: the drop up to the first token of the
 * first request after the load, capped at {@link #FIRST_PREFILL_CAP_MS} (a load nobody sends a
 * request to just stops being watched). That is where a LiteRT CPU load allocates its KV cache and
 * a vision load its encoder activations; a meter that stopped at the end of {@code initialize()}
 * read 371-422 MB for gemma-4-e2b on the CPU while the first generation went on to 6.9 GB.
 * {@link TaiRuntimeHistory} keeps both, and the budget plans on the first-prefill figure when it
 * has one. {@link #stop} remains the one-shot reading for loads that have no first request.
 *
 * <p>System-side on purpose. GPU buffers are not in the process's PSS and the kgsl per-process
 * counters are not readable by the app, so PSS cannot measure a GPU load; MemAvailable measures
 * what matters, how close the phone came to the floor. It is noisy (±0.9 GB across identical E4B
 * GPU loads on pong, because the kernel reclaims cache while the load runs), which is why
 * {@link TaiRuntimeHistory} keeps a ring of samples and plans on the typical one at the nearest
 * window.
 *
 * <p>Loads are serialized by the runtimes, so one meter runs at a time, apart from the tail of a
 * previous one still waiting for its first token. {@link #activeCount} tells the pressure watch
 * when a load or first prefill is in progress, so it polls faster; {@link #nativeInitRunning} tells
 * it when a chat model's native init is, which nothing can cancel. A {@code null} context (the
 * router's test seam) yields a meter that measures nothing and reports {@code -1}.
 */
final class TaiLoadMeter {
    static final long SAMPLE_INTERVAL_MS = 100L;
    /** The longest a meter keeps watching after the load ends, waiting for the first token. */
    static final long FIRST_PREFILL_CAP_MS = 60_000L;

    /** Meters whose sampler thread is running: a load or a first prefill is in progress. */
    private static final AtomicInteger ACTIVE = new AtomicInteger();
    /** Meters started by {@link #startNativeInit} whose load phase has not ended. */
    private static final AtomicInteger NATIVE_INITS = new AtomicInteger();
    /** The latest meter started by {@link #startNativeInit} while its load phase runs; loads are serialized. */
    @Nullable private static volatile TaiLoadMeter currentNativeInit;

    /** Set while this meter counts in {@link #NATIVE_INITS}; cleared once, by {@link #endLoad} or {@link #stop}. */
    private final AtomicBoolean inNativeInit = new AtomicBoolean();
    /** What this meter's native init loads; {@code null} for every other meter. */
    @Nullable private volatile NativeInit nativeInit;

    /** What a chat model's native init is loading: what a memory stop records against ({@link TaiRuntimeHistory#recordPressureStop}). */
    static final class NativeInit {
        @NonNull final TaiModelSpec spec;
        @NonNull final TaiDeviceCapabilities device;
        @NonNull final String backend;
        @NonNull final String accelerator;
        final int window;

        NativeInit(@NonNull TaiModelSpec spec, @NonNull TaiDeviceCapabilities device, @NonNull String backend,
                   @NonNull String accelerator, int window) {
            this.spec = spec;
            this.device = device;
            this.backend = backend;
            this.accelerator = accelerator;
            this.window = window;
        }
    }

    @Nullable private final ActivityManager activityManager;
    private final long beforeBytes;
    private volatile long minBytes;
    private volatile boolean running;
    /** When {@link #endLoad} ran, on the elapsed-realtime clock; {@code 0} while the load is in progress. */
    private volatile long loadEndedAtMs;
    @Nullable private Thread thread;

    private TaiLoadMeter(@Nullable ActivityManager activityManager) {
        this.activityManager = activityManager;
        beforeBytes = sample();
        minBytes = beforeBytes;
    }

    /** Takes the "before" sample and starts sampling; call immediately before native init. */
    @NonNull
    static TaiLoadMeter start(@Nullable Context context) {
        ActivityManager activityManager = context == null ? null : context.getSystemService(ActivityManager.class);
        TaiLoadMeter meter = new TaiLoadMeter(activityManager);
        if (meter.beforeBytes > 0L) meter.startSampling();
        return meter;
    }

    /**
     * {@link #start} for a chat model's native init (LiteRT-LM {@code initialize()}, MNN
     * {@code load}) of {@code init}: counted by {@link #nativeInitRunning} and named by
     * {@link #currentNativeInit} until {@link #endLoad} or {@link #stop}, whether or not free
     * memory can be read.
     */
    @NonNull
    static TaiLoadMeter startNativeInit(@Nullable Context context, @NonNull NativeInit init) {
        TaiLoadMeter meter = start(context);
        meter.nativeInit = init;
        meter.inNativeInit.set(true);
        NATIVE_INITS.incrementAndGet();
        currentNativeInit = meter;
        return meter;
    }

    /** The meter of the native init in progress, or {@code null}: what the watch records when it ends one. */
    @Nullable
    static TaiLoadMeter currentNativeInit() {
        TaiLoadMeter meter = currentNativeInit;
        return meter != null && meter.inNativeInit.get() ? meter : null;
    }

    /** What this meter's native init loads; {@code null} for a meter not started by {@link #startNativeInit}. */
    @Nullable
    NativeInit nativeInit() {
        return nativeInit;
    }

    /** The drop so far, a fresh sample included, without stopping: {@code -1} when free memory cannot be read. */
    long dropSoFar() {
        if (beforeBytes <= 0L) return -1L;
        observe(sample());
        return dropBytes(beforeBytes, minBytes);
    }

    /** Whether a chat model's native init is running: a load the runtime cannot cancel, only end with its process. */
    static boolean nativeInitRunning() {
        return NATIVE_INITS.get() > 0;
    }

    private void leaveNativeInit() {
        if (!inNativeInit.compareAndSet(true, false)) return;
        NATIVE_INITS.decrementAndGet();
        if (currentNativeInit == this) currentNativeInit = null;
    }

    /** Whether any meter is sampling now: a load, or the wait for a first token, is in progress. */
    static boolean anyActive() {
        return ACTIVE.get() > 0;
    }

    /** Whether the wait for a first token is still open: {@code false} once the cap has passed. */
    static boolean firstPrefillWindowOpen(long nowMs, long loadEndedAtMs, long capMs) {
        return loadEndedAtMs <= 0L || nowMs - loadEndedAtMs <= capMs;
    }

    /** {@code before − minimum}, never negative. */
    static long dropBytes(long beforeBytes, long minBytes) {
        return Math.max(0L, beforeBytes - minBytes);
    }

    private void startSampling() {
        running = true;
        ACTIVE.incrementAndGet();
        Thread sampler = new Thread(() -> {
            try {
                while (running) {
                    observe(sample());
                    if (!firstPrefillWindowOpen(SystemClock.elapsedRealtime(), loadEndedAtMs, FIRST_PREFILL_CAP_MS)) {
                        running = false;
                        return;
                    }
                    try {
                        Thread.sleep(SAMPLE_INTERVAL_MS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return;
                    }
                }
            } finally {
                ACTIVE.decrementAndGet();
            }
        }, "tai-load-meter");
        sampler.setDaemon(true);
        sampler.start();
        thread = sampler;
    }

    private void observe(long availBytes) {
        if (availBytes > 0L && availBytes < minBytes) minBytes = availBytes;
    }

    private long sample() {
        if (activityManager == null) return 0L;
        return TaiMemInfo.read(activityManager).availBytes;
    }

    /**
     * The end of {@code initialize()}: takes a last sample and returns the {@code load} phase drop,
     * {@code -1} when free memory could not be read, and leaves the sampler running for
     * {@link #firstTokenDrop}.
     */
    long endLoad() {
        leaveNativeInit();
        loadEndedAtMs = SystemClock.elapsedRealtime();
        if (beforeBytes <= 0L) return -1L;
        observe(sample());
        return dropBytes(beforeBytes, minBytes);
    }

    /**
     * The first token of the first request: stops sampling and returns the {@code first_prefill}
     * drop, {@code -1} when free memory could not be read.
     */
    long firstTokenDrop() {
        return stop();
    }

    /**
     * Stops sampling, takes a last sample so a load shorter than one interval still counts, and
     * returns {@code before − minimum} in bytes; {@code -1} when free memory could not be read.
     */
    long stop() {
        leaveNativeInit();
        running = false;
        Thread toJoin = thread;
        if (toJoin != null) {
            toJoin.interrupt();
            try {
                toJoin.join(500L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        if (beforeBytes <= 0L) return -1L;
        observe(sample());
        return dropBytes(beforeBytes, minBytes);
    }

    /** Free memory when the meter started; {@code 0} when it could not be read. */
    long beforeBytes() {
        return beforeBytes;
    }

    /** The lowest free memory seen so far. */
    long minBytes() {
        return minBytes;
    }

    /**
     * A load's meter waiting for the first token of the first request after it, with what the
     * record needs: the runtimes keep one of these after a successful load, take it at the start
     * of the next generation, call {@link #firstToken} from the first token callback and
     * {@link #finish} when the generation has finished and its prompt length is known.
     */
    static final class Pending {
        @NonNull private final TaiLoadMeter meter;
        @NonNull private final TaiModelSpec spec;
        @NonNull private final TaiDeviceCapabilities device;
        @NonNull private final String backend;
        @NonNull private final String accelerator;
        private final int window;
        private volatile long firstPrefillDrop = Long.MIN_VALUE;

        Pending(@NonNull TaiLoadMeter meter, @NonNull TaiModelSpec spec, @NonNull TaiDeviceCapabilities device,
                @NonNull String backend, @NonNull String accelerator, int window) {
            this.meter = meter;
            this.spec = spec;
            this.device = device;
            this.backend = backend;
            this.accelerator = accelerator;
            this.window = window;
        }

        /** Stops the meter at the first token; only the first call reads it. */
        void firstToken() {
            if (firstPrefillDrop == Long.MIN_VALUE) firstPrefillDrop = meter.firstTokenDrop();
        }

        /**
         * Ends the first request: stops the meter if no token arrived and, when the generation went
         * through ({@code ok}) and a token did arrive, books the first-prefill drop with the prompt
         * length. A failed or cancelled first request books nothing.
         */
        void finish(@NonNull Context context, int promptTokens, boolean ok) {
            long drop = firstPrefillDrop;
            if (drop == Long.MIN_VALUE) {
                meter.stop();
                return;
            }
            if (ok && drop > 0L) {
                TaiRuntimeHistory.recordMeasuredLoad(context, spec, device, backend, accelerator, window, drop,
                    TaiRuntimeHistory.PHASE_FIRST_PREFILL, promptTokens);
            }
        }

        /** Gives the meter up without a reading: the model was unloaded or replaced before a request came. */
        void abandon() {
            if (firstPrefillDrop == Long.MIN_VALUE) {
                meter.stop();
                firstPrefillDrop = -1L;
            }
        }
    }
}
