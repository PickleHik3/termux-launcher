package com.termux.ai;

import android.app.ActivityManager;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Measures what a native load costs the phone: MemAvailable ({@code MemoryInfo.availMem}) sampled
 * every 100 ms on a helper thread from just before native initialization until it returns, with
 * {@code before − minimum} as the load's cost.
 *
 * <p>System-side on purpose. GPU buffers are not in the process's PSS and the kgsl per-process
 * counters are not readable by the app, so PSS cannot measure a GPU load; MemAvailable measures
 * what matters, how close the phone came to the floor. It is noisy (±0.9 GB across identical E4B
 * GPU loads on pong, because the kernel reclaims cache while the load runs), which is why
 * {@link TaiRuntimeHistory#recordMeasuredLoad} keeps the largest value per key.
 *
 * <p>Loads are serialized by the runtimes, so one meter runs at a time. A {@code null} context
 * (the router's test seam) yields a meter that measures nothing and reports {@code -1}.
 */
final class TaiLoadMeter {
    static final long SAMPLE_INTERVAL_MS = 100L;

    @Nullable private final ActivityManager activityManager;
    private final long beforeBytes;
    private volatile long minBytes;
    private volatile boolean running;
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

    private void startSampling() {
        running = true;
        Thread sampler = new Thread(() -> {
            while (running) {
                observe(sample());
                try {
                    Thread.sleep(SAMPLE_INTERVAL_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
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
        ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
        activityManager.getMemoryInfo(info);
        return info.availMem;
    }

    /**
     * Stops sampling, takes a last sample so a load shorter than one interval still counts, and
     * returns {@code before − minimum} in bytes; {@code -1} when MemAvailable could not be read.
     */
    long stop() {
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
        return Math.max(0L, beforeBytes - minBytes);
    }

    /** MemAvailable when the meter started; {@code 0} when it could not be read. */
    long beforeBytes() {
        return beforeBytes;
    }

    /** The lowest MemAvailable seen so far. */
    long minBytes() {
        return minBytes;
    }
}
