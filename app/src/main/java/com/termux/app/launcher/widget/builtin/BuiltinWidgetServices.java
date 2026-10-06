package com.termux.app.launcher.widget.builtin;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.app.statusbar.SystemStatsController;
import com.termux.app.statusbar.WeatherController;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * What the built-in widgets share: one weather controller, one stats sampler, one minute tick,
 * one background executor, and the few things only the activity can do for them. One instance
 * lives with the widgets page; every source starts on the first subscriber and stops on the last,
 * so a page with no clock on it pays for no clock.
 */
public final class BuiltinWidgetServices {
    /** What a widget needs the activity for. */
    public interface Host {
        /** Ask the user for calendar access; the result arrives through {@link #onCalendarPermissionChanged}. */
        void requestCalendarPermission();
        /** A new terminal window running {@code command} through the login shell. */
        boolean openCommandWindow(@NonNull List<String> command, @Nullable String title);
        /** The terminal's monospace face, when one is loaded. */
        @Nullable android.graphics.Typeface monoTypeface();
    }

    /** A once-a-minute heartbeat, aligned to the system's minute tick. */
    public interface TickListener { void onTick(); }

    /** Told when calendar permission was granted or refused. */
    public interface PermissionListener { void onCalendarPermissionChanged(); }

    @NonNull private final Context context;
    @NonNull private final Host host;
    @NonNull private final Handler main = new Handler(Looper.getMainLooper());
    @NonNull private final ExecutorService io = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "builtin-widgets");
        thread.setDaemon(true);
        return thread;
    });

    private final List<TickListener> tickListeners = new ArrayList<>();
    private final List<PermissionListener> permissionListeners = new ArrayList<>();
    private boolean tickRegistered;
    private final BroadcastReceiver tickReceiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) { tick(); }
    };

    @Nullable private WeatherController weather;
    private int weatherUsers;
    private final List<WeatherController.Listener> weatherListeners = new ArrayList<>();

    @Nullable private SystemStatsController stats;
    private int statsUsers;
    private final List<SystemStatsController.Listener> statsListeners = new ArrayList<>();

    public BuiltinWidgetServices(@NonNull Context context, @NonNull Host host) {
        this.context = context.getApplicationContext();
        this.host = host;
    }

    @NonNull public Context context() { return context; }
    @NonNull public Host host() { return host; }
    @NonNull public Handler main() { return main; }
    /** One background thread for file reads, calendar queries and shell runs. */
    @NonNull public ExecutorService io() { return io; }

    // ----- minute tick ----------------------------------------------------------------------

    public void addTickListener(@NonNull TickListener listener) {
        if (!tickListeners.contains(listener)) tickListeners.add(listener);
        if (!tickRegistered) {
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIME_TICK);
            filter.addAction(Intent.ACTION_TIME_CHANGED);
            filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
            try {
                context.registerReceiver(tickReceiver, filter);
                tickRegistered = true;
            } catch (RuntimeException ignored) {
                // Without the tick the clock still shows the time it was built with.
            }
        }
    }

    public void removeTickListener(@NonNull TickListener listener) {
        tickListeners.remove(listener);
        if (tickListeners.isEmpty() && tickRegistered) {
            try { context.unregisterReceiver(tickReceiver); } catch (RuntimeException ignored) { }
            tickRegistered = false;
        }
    }

    private void tick() {
        for (TickListener listener : new ArrayList<>(tickListeners)) listener.onTick();
    }

    // ----- weather --------------------------------------------------------------------------

    /** The shared weather, refreshed if stale; the listener hears every update while held. */
    @NonNull public WeatherController acquireWeather(@NonNull WeatherController.Listener listener) {
        if (weather == null) {
            weather = new WeatherController(context, this::fanOutWeather);
        }
        if (!weatherListeners.contains(listener)) weatherListeners.add(listener);
        weatherUsers++;
        weather.refreshIfStale();
        return weather;
    }

    public void releaseWeather(@NonNull WeatherController.Listener listener) {
        weatherListeners.remove(listener);
        weatherUsers = Math.max(0, weatherUsers - 1);
        if (weatherUsers == 0 && weather != null) {
            weather.stop();
            weather = null;
        }
    }

    private void fanOutWeather(@NonNull WeatherController.Weather value) {
        for (WeatherController.Listener listener : new ArrayList<>(weatherListeners)) {
            listener.onWeatherUpdated(value);
        }
    }

    // ----- system stats ---------------------------------------------------------------------

    /** The shared sampler, running every {@code intervalMs} while anyone holds it. */
    @NonNull public SystemStatsController acquireStats(@NonNull SystemStatsController.Listener listener,
                                                       long intervalMs) {
        if (stats == null) {
            stats = new SystemStatsController(context, this::fanOutStats);
        }
        if (!statsListeners.contains(listener)) statsListeners.add(listener);
        statsUsers++;
        stats.start(intervalMs, false);
        return stats;
    }

    public void releaseStats(@NonNull SystemStatsController.Listener listener) {
        statsListeners.remove(listener);
        statsUsers = Math.max(0, statsUsers - 1);
        if (statsUsers == 0 && stats != null) {
            stats.stop();
            stats = null;
        }
    }

    private void fanOutStats(@NonNull SystemStatsController.Stats value) {
        for (SystemStatsController.Listener listener : new ArrayList<>(statsListeners)) {
            listener.onStatsUpdated(value);
        }
    }

    // ----- permissions ----------------------------------------------------------------------

    public void addPermissionListener(@NonNull PermissionListener listener) {
        if (!permissionListeners.contains(listener)) permissionListeners.add(listener);
    }

    public void removePermissionListener(@NonNull PermissionListener listener) {
        permissionListeners.remove(listener);
    }

    /** The activity's answer to {@link Host#requestCalendarPermission()}. */
    public void onCalendarPermissionChanged() {
        for (PermissionListener listener : new ArrayList<>(permissionListeners)) {
            listener.onCalendarPermissionChanged();
        }
    }

    /** The page is gone: every source stops whatever it still holds. */
    public void destroy() {
        tickListeners.clear();
        if (tickRegistered) {
            try { context.unregisterReceiver(tickReceiver); } catch (RuntimeException ignored) { }
            tickRegistered = false;
        }
        weatherListeners.clear();
        if (weather != null) { weather.stop(); weather = null; }
        weatherUsers = 0;
        statsListeners.clear();
        if (stats != null) { stats.stop(); stats = null; }
        statsUsers = 0;
        io.shutdownNow();
    }
}
