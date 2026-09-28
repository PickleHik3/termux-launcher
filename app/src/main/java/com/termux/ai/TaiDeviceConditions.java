package com.termux.ai;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import android.os.PowerManager;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Reads the phone's battery and thermal state for {@link TaiBenchConditionsGuard}. Every call is
 * guarded by an SDK check and a try/catch: a failure or an unavailable API reads back as unknown
 * ({@code -1}/{@code NaN}) rather than throwing, the same as {@link TaiBenchGuardRules.Snapshot}
 * treats a phone that has never reported one.
 */
final class TaiDeviceConditions {
    /**
     * {@link PowerManager#getThermalHeadroom} returns {@code NaN} when polled faster than about a
     * second; the last real reading is reused for that long instead of reporting unknown.
     */
    private static final long HEADROOM_CACHE_MS = 1_000L;

    @NonNull private final Context appContext;
    @Nullable private final PowerManager powerManager;
    @Nullable private final ExecutorService listenerExecutor;
    private float cachedHeadroom = Float.NaN;
    private long cachedHeadroomAtMs;
    @Nullable private PowerManager.OnThermalStatusChangedListener thermalListener;

    TaiDeviceConditions(@NonNull Context context) {
        this.appContext = context.getApplicationContext();
        PowerManager pm = null;
        try {
            pm = (PowerManager) appContext.getSystemService(Context.POWER_SERVICE);
        } catch (RuntimeException ignored) {
        }
        this.powerManager = pm;
        this.listenerExecutor = pm != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
            ? Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "tai-bench-thermal");
                thread.setDaemon(true);
                return thread;
            }) : null;
    }

    /** One reading of battery level/charging and thermal status/headroom, each independently guarded. */
    @NonNull
    TaiBenchGuardRules.Snapshot snapshot() {
        int battery = -1;
        boolean charging = false;
        try {
            Intent status = appContext.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (status != null) {
                int level = status.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = status.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                if (level >= 0 && scale > 0) battery = Math.round(level * 100f / scale);
                int chargeStatus = status.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                int plugged = status.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
                charging = chargeStatus == BatteryManager.BATTERY_STATUS_CHARGING
                    || chargeStatus == BatteryManager.BATTERY_STATUS_FULL
                    || plugged != 0;
            }
        } catch (RuntimeException ignored) {
        }
        int thermal = -1;
        float headroom = Float.NaN;
        if (powerManager != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                thermal = powerManager.getCurrentThermalStatus();
            } catch (RuntimeException ignored) {
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                headroom = cachedHeadroom();
            }
        }
        return new TaiBenchGuardRules.Snapshot(battery, charging, thermal, headroom);
    }

    private float cachedHeadroom() {
        long now = System.currentTimeMillis();
        if (!Float.isNaN(cachedHeadroom) && now - cachedHeadroomAtMs < HEADROOM_CACHE_MS) return cachedHeadroom;
        try {
            float value = powerManager.getThermalHeadroom(0);
            if (!Float.isNaN(value)) {
                cachedHeadroom = value;
                cachedHeadroomAtMs = now;
                return value;
            }
        } catch (RuntimeException ignored) {
        }
        // Polled again too soon, or the call failed: the last good reading is still the best guess.
        return cachedHeadroom;
    }

    /**
     * Registers a listener for the run's duration; {@code onSevereOrWorse} runs (on its own
     * thread) the moment thermal status reaches SEVERE or worse, so the harness can stop the
     * generation in progress instead of waiting for the next {@code beforePhase}. A no-op below
     * API 29 or if the platform refuses the listener.
     */
    void startThermalListener(@NonNull Runnable onSevereOrWorse) {
        if (powerManager == null || listenerExecutor == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return;
        try {
            PowerManager.OnThermalStatusChangedListener listener = status -> {
                if (status >= TaiBenchGuardRules.THERMAL_STATUS_SEVERE) onSevereOrWorse.run();
            };
            powerManager.addThermalStatusListener(listenerExecutor, listener);
            thermalListener = listener;
        } catch (RuntimeException ignored) {
        }
    }

    /** Unregisters the listener {@link #startThermalListener} added; safe to call more than once. */
    void stopThermalListener() {
        PowerManager.OnThermalStatusChangedListener listener = thermalListener;
        thermalListener = null;
        if (powerManager == null || listener == null) return;
        try {
            powerManager.removeThermalStatusListener(listener);
        } catch (RuntimeException ignored) {
        }
    }
}
