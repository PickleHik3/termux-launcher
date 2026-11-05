package com.termux.ai;

import android.content.Context;
import android.os.BatteryManager;
import android.os.Looper;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

/**
 * Battery current for {@link SttBench}: {@link BatteryManager#BATTERY_PROPERTY_CURRENT_NOW} (microamps)
 * through the system context when one can be had from a shell-launched {@code app_process}, else the
 * fuel gauge's sysfs file. The sign is the phone's own convention (most report discharge as negative);
 * the bench prints it as read. A phone on a charger gives a meaningless number, so every sampling
 * window also records whether any sample saw it charging.
 */
final class SttBenchBattery {
    private static final String SYSFS = "/sys/class/power_supply/battery/";

    private final BatteryManager manager;
    private final String source;

    private SttBenchBattery(BatteryManager manager, String source) {
        this.manager = manager;
        this.source = source;
    }

    static SttBenchBattery open() {
        try {
            if (Looper.myLooper() == null) Looper.prepare();
            Class<?> thread = Class.forName("android.app.ActivityThread");
            Object activityThread = thread.getMethod("systemMain").invoke(null);
            Context context = (Context) thread.getMethod("getSystemContext").invoke(activityThread);
            BatteryManager manager = context.getSystemService(BatteryManager.class);
            if (manager != null && manager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) != Long.MIN_VALUE) {
                return new SttBenchBattery(manager, "BatteryManager");
            }
        } catch (Throwable ignored) {
            // No system context from this launcher; the sysfs file below is the fallback.
        }
        if (readSysfsLong("current_now") != Long.MIN_VALUE) return new SttBenchBattery(null, "sysfs");
        return new SttBenchBattery(null, "none");
    }

    String source() {
        return source;
    }

    /** Microamps now, or {@link Long#MIN_VALUE} when there is no reading. */
    long currentMicroamps() {
        if (manager != null) {
            long value = manager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
            return value == 0L ? Long.MIN_VALUE : value;
        }
        return readSysfsLong("current_now");
    }

    /** True or false when the phone says, null when it does not. */
    Boolean charging() {
        if (manager != null) {
            try {
                return manager.isCharging();
            } catch (Throwable ignored) {
                // fall through to sysfs
            }
        }
        String status = readSysfsLine("status");
        if (status == null) return null;
        return status.equalsIgnoreCase("Charging") || status.equalsIgnoreCase("Full");
    }

    private static long readSysfsLong(String name) {
        String line = readSysfsLine(name);
        if (line == null) return Long.MIN_VALUE;
        try {
            return Long.parseLong(line.trim());
        } catch (NumberFormatException e) {
            return Long.MIN_VALUE;
        }
    }

    private static String readSysfsLine(String name) {
        File file = new File(SYSFS + name);
        if (!file.canRead()) return null;
        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            return reader.readLine();
        } catch (IOException e) {
            return null;
        }
    }

    /** Samples the current on a thread between {@link #start} and {@link #stop}. */
    static final class Sampler {
        /** One window's result: mean microamps (NaN when nothing was read) and whether charging was ever seen. */
        static final class Result {
            final double meanMicroamps;
            final int samples;
            final Boolean charging;

            Result(double meanMicroamps, int samples, Boolean charging) {
                this.meanMicroamps = meanMicroamps;
                this.samples = samples;
                this.charging = charging;
            }
        }

        private final SttBenchBattery battery;
        private final long intervalMs;
        private Thread thread;
        private volatile boolean running;
        private double sum;
        private int count;
        private boolean sawCharging;
        private boolean sawKnown;

        Sampler(SttBenchBattery battery, long intervalMs) {
            this.battery = battery;
            this.intervalMs = Math.max(20L, intervalMs);
        }

        void start() {
            sum = 0.0;
            count = 0;
            sawCharging = false;
            sawKnown = false;
            running = true;
            sample();
            thread = new Thread(() -> {
                while (running) {
                    try {
                        Thread.sleep(intervalMs);
                    } catch (InterruptedException e) {
                        return;
                    }
                    if (running) sample();
                }
            }, "stt-bench-battery");
            thread.setDaemon(true);
            thread.start();
        }

        Result stop() {
            running = false;
            if (thread != null) {
                thread.interrupt();
                try {
                    thread.join(1000L);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            }
            sample();
            synchronized (this) {
                return new Result(count == 0 ? Double.NaN : sum / count, count, sawKnown ? sawCharging : null);
            }
        }

        private synchronized void sample() {
            long ua = battery.currentMicroamps();
            if (ua != Long.MIN_VALUE) {
                sum += ua;
                count++;
            }
            Boolean charging = battery.charging();
            if (charging != null) {
                sawKnown = true;
                sawCharging |= charging;
            }
        }
    }
}
