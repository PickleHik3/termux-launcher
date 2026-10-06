package com.termux.app.launcher.widget.builtin;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * The battery as the Battery and System widgets read it: one sticky {@code ACTION_BATTERY_CHANGED}
 * receiver for the whole process, registered while at least one widget holds the source and
 * dropped with the last. Each broadcast becomes an immutable {@link State} and lands in the hourly
 * {@link BatteryHistoryStore}. Main thread only.
 */
public final class BatterySource {
    /** One reading of the battery. Unknown values are -1, {@link Integer#MIN_VALUE} or NaN. */
    public static final class State {
        /** 0..100, or -1 when the device reports no level. */
        public final int level;
        /** Power is going in. */
        public final boolean charging;
        /** On a charger and full. */
        public final boolean full;
        /** On any charger, charging or not. */
        public final boolean plugged;
        /** Tenths of a degree Celsius; {@link Integer#MIN_VALUE} when unknown. */
        public final int temperatureTenths;
        /** Millivolts; -1 when unknown. */
        public final int voltageMv;
        /** Watts in or out; NaN when the device does not say. */
        public final double powerWatts;
        /** Milliseconds to full while charging; -1 when unknown. */
        public final long chargeTimeMs;

        public State(int level, boolean charging, boolean full, boolean plugged,
                     int temperatureTenths, int voltageMv, double powerWatts, long chargeTimeMs) {
            this.level = level; this.charging = charging; this.full = full; this.plugged = plugged;
            this.temperatureTenths = temperatureTenths; this.voltageMv = voltageMv;
            this.powerWatts = powerWatts; this.chargeTimeMs = chargeTimeMs;
        }

        public boolean known() { return level >= 0; }
    }

    public interface Listener { void onBatteryChanged(@NonNull State state); }

    @Nullable private static BatterySource instance;

    /** The process's one source. */
    @MainThread @NonNull public static BatterySource get(@NonNull Context context) {
        if (instance == null) instance = new BatterySource(context.getApplicationContext());
        return instance;
    }

    @NonNull private final Context context;
    @NonNull private final BatteryHistoryStore history;
    private final List<Listener> listeners = new ArrayList<>();
    private boolean registered;
    @Nullable private State latest;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { accept(intent); }
    };

    private BatterySource(@NonNull Context context) {
        this.context = context;
        history = new BatteryHistoryStore(context);
    }

    /** Hears every reading while held; told the latest one at once when there is one. */
    @MainThread public void acquire(@NonNull Listener listener) {
        if (!listeners.contains(listener)) listeners.add(listener);
        if (!registered) {
            try {
                Intent sticky = context.registerReceiver(receiver,
                    new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
                registered = true;
                if (sticky != null) accept(sticky);
            } catch (RuntimeException ignored) {
                // Without the broadcast the widgets keep their unknown state.
            }
        } else if (latest != null) {
            listener.onBatteryChanged(latest);
        }
    }

    @MainThread public void release(@NonNull Listener listener) {
        listeners.remove(listener);
        if (listeners.isEmpty() && registered) {
            try { context.unregisterReceiver(receiver); } catch (RuntimeException ignored) { }
            registered = false;
        }
    }

    /** The last reading, or null before the first broadcast. */
    @Nullable public State latest() { return latest; }

    @NonNull public BatteryHistoryStore history() { return history; }

    private void accept(@Nullable Intent intent) {
        if (intent == null) return;
        State state = read(intent);
        latest = state;
        if (state.known()) history.record(state.level, System.currentTimeMillis());
        for (Listener listener : new ArrayList<>(listeners)) listener.onBatteryChanged(state);
    }

    @NonNull private State read(@NonNull Intent intent) {
        int rawLevel = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
        int level = rawLevel < 0 ? -1
            : scale > 0 ? Math.round(rawLevel * 100f / scale) : rawLevel;
        level = level < 0 ? -1 : Math.min(100, level);
        int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, BatteryManager.BATTERY_STATUS_UNKNOWN);
        boolean plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0;
        boolean full = status == BatteryManager.BATTERY_STATUS_FULL || (plugged && level >= 100);
        boolean charging = !full && status == BatteryManager.BATTERY_STATUS_CHARGING;
        int temperature = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Integer.MIN_VALUE);
        int voltage = DeviceWidgetFormats.voltageMillivolts(
            intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, -1));

        double watts = Double.NaN;
        long chargeTime = -1;
        try {
            BatteryManager manager = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
            if (manager != null) {
                watts = DeviceWidgetFormats.powerWatts(
                    manager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW), voltage);
                if (charging && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    chargeTime = manager.computeChargeTimeRemaining();
                }
            }
        } catch (RuntimeException ignored) {
            // Some makers throw from the property calls; the level and state still stand.
        }
        return new State(level, charging, full, plugged, temperature, voltage, watts,
            chargeTime > 0 ? chargeTime : -1);
    }
}
