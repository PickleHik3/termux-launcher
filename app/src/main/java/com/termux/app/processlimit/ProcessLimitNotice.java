package com.termux.app.processlimit;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.termux.R;
import com.termux.app.processlimit.ProcessLimitPolicy.Card;
import com.termux.app.processlimit.ProcessLimitPolicy.Limit;
import com.termux.app.processlimit.ProcessLimitPolicy.Phase;
import com.termux.privileged.PrivilegedBackendManager;
import com.termux.shared.logger.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * The process-limit notice's one state, for the three places that draw it (the Settings root,
 * Home's long-press sheet, the Workspace's More sheet). A kill reported here raises it unless the
 * limit reads off; it stays, across restarts, until the limit reads off or the user dismisses it.
 * {@link ProcessLimitPolicy} decides; this class only reads, remembers and tells.
 *
 * <p>Main thread for everything but {@link #onChildKilled}, which posts. The reads (a global
 * setting, and with the privileged backend a shell command) run on a thread of their own.
 */
public final class ProcessLimitNotice {

    /** Told on the main thread whenever {@link #card} or {@link #limit} may have changed. */
    public interface Listener {
        void onProcessLimitChanged();
    }

    private static final String LOG_TAG = "ProcessLimitNotice";
    private static final String PREFS = "process_limit_notice";
    private static final String KEY_PENDING = "pending";
    /** The help topic that explains the ways to lift the limit. */
    public static final String HELP_TOPIC = "fix_process_limit";
    /** Diagnostics' row for the limit, which a link scrolls to. */
    public static final String DIAGNOSTICS_KEY = "process_limit";
    private static final long READ_TIMEOUT_S = 10L;
    private static final long WRITE_TIMEOUT_S = 30L;

    @Nullable private static ProcessLimitNotice sInstance;

    /** The process's notice, built the first time it is asked for. */
    @NonNull
    public static synchronized ProcessLimitNotice get(@NonNull Context context) {
        if (sInstance == null) sInstance = new ProcessLimitNotice(context.getApplicationContext());
        return sInstance;
    }

    @VisibleForTesting
    static synchronized void resetForTest() {
        sInstance = null;
    }

    @NonNull private final Context context;
    @NonNull private final Handler main = new Handler(Looper.getMainLooper());
    @NonNull private final ExecutorService reads = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "process-limit");
        thread.setDaemon(true);
        return thread;
    });
    @NonNull private final List<Listener> listeners = new ArrayList<>();
    private boolean pending;
    @NonNull private Limit limit = Limit.UNKNOWN;
    @NonNull private Phase phase = Phase.IDLE;
    private boolean refreshing;
    /** A kill's read of the limit is running; later kills of the same burst wait on it. */
    private boolean raising;

    private ProcessLimitNotice(@NonNull Context context) {
        this.context = context;
        pending = prefs().getBoolean(KEY_PENDING, false);
    }

    @NonNull
    private SharedPreferences prefs() {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public void addListener(@NonNull Listener listener) {
        if (!listeners.contains(listener)) listeners.add(listener);
    }

    public void removeListener(@NonNull Listener listener) {
        listeners.remove(listener);
    }

    /** What the card shows now. */
    @NonNull
    public Card card() {
        return ProcessLimitPolicy.card(Build.VERSION.SDK_INT, pending, limit, phase);
    }

    /** The limit as last read; {@link Limit#UNKNOWN} until a read finishes. */
    @NonNull
    public Limit limit() {
        return limit;
    }

    /** Whether the notice stands (read before a refresh, to skip reads nobody needs). */
    public boolean isPending() {
        return pending;
    }

    /**
     * A child of the launcher ended by a signal or exit the caller describes. Raises the notice
     * when {@link ProcessLimitPolicy#raises} says so once the limit has been read. Any thread.
     */
    public void onChildKilled(boolean killedBySigkill, boolean sentByLauncher) {
        int sdk = Build.VERSION.SDK_INT;
        if (!ProcessLimitPolicy.raises(sdk, killedBySigkill, sentByLauncher, Limit.UNKNOWN)) return;
        main.post(() -> raiseAfterRead(sdk, killedBySigkill, sentByLauncher));
    }

    /** Main thread: one read for a burst of kills, none while the notice already stands. */
    private void raiseAfterRead(int sdk, boolean killedBySigkill, boolean sentByLauncher) {
        if (pending || raising) return;
        raising = true;
        reads.execute(() -> {
            Limit read = readLimit();
            main.post(() -> {
                raising = false;
                limit = read;
                if (ProcessLimitPolicy.raises(sdk, killedBySigkill, sentByLauncher, read)) {
                    Logger.logInfo(LOG_TAG, "A child was killed with the process limit on");
                    setPending(true);
                }
                notifyChanged();
            });
        });
    }

    /** Reads the limit again; a notice whose limit now reads off goes. Main thread. */
    public void refresh() {
        if (refreshing) return;
        refreshing = true;
        reads.execute(() -> {
            Limit read = readLimit();
            main.post(() -> {
                refreshing = false;
                limit = read;
                if (!ProcessLimitPolicy.stillPending(pending, read)) setPending(false);
                notifyChanged();
            });
        });
    }

    /** The user put the notice away; the next kill with the limit on raises it again. */
    public void dismiss() {
        phase = Phase.IDLE;
        setPending(false);
        notifyChanged();
    }

    /** The done card's Done: it goes. */
    public void finishFix() {
        if (phase == Phase.WORKING) return;
        phase = Phase.IDLE;
        notifyChanged();
    }

    /** Lifts the limit through the privileged backend, then reads it back to say how it went. */
    public void fix() {
        if (phase == Phase.WORKING) return;
        phase = Phase.WORKING;
        notifyChanged();
        reads.execute(() -> {
            Limit read = combineWithSetting(run(ProcessLimitPolicy.fixThenRead(), WRITE_TIMEOUT_S));
            main.post(() -> {
                limit = read;
                if (read == Limit.OFF) {
                    phase = Phase.FIXED;
                    setPending(false);
                } else {
                    phase = Phase.FAILED;
                }
                notifyChanged();
            });
        });
    }

    /**
     * Puts Android's default back; {@code done} hears, on the main thread, whether the limit now
     * reads on again.
     */
    public void revert(@Nullable Consumer<Boolean> done) {
        if (phase == Phase.WORKING) return;
        phase = Phase.WORKING;
        notifyChanged();
        reads.execute(() -> {
            Limit read = combineWithSetting(run(ProcessLimitPolicy.revertThenRead(), WRITE_TIMEOUT_S));
            main.post(() -> {
                limit = read;
                phase = Phase.IDLE;
                notifyChanged();
                if (done != null) done.accept(read != Limit.OFF);
            });
        });
    }

    /** The guide on the ways to lift the limit. */
    @NonNull
    public static Intent guideIntent(@NonNull Context context) {
        return com.termux.app.help.HelpActivity.intent(context, null, HELP_TOPIC, null);
    }

    /** Diagnostics, scrolled to the limit's row. */
    @NonNull
    public static Intent diagnosticsIntent(@NonNull Context context) {
        return com.termux.app.activities.SettingsActivity.createFragmentIntent(context,
            com.termux.app.fragments.settings.termux.AdvancedDiagnosticsPreferencesFragment.class,
            R.string.settings_destination_advanced_diagnostics, null, DIAGNOSTICS_KEY);
    }

    private void setPending(boolean value) {
        if (pending == value) return;
        pending = value;
        prefs().edit().putBoolean(KEY_PENDING, value).apply();
    }

    private void notifyChanged() {
        for (Listener listener : new ArrayList<>(listeners)) listener.onProcessLimitChanged();
    }

    /** Off the main thread: the setting first, then the backend's read when it can run one. */
    @NonNull
    private Limit readLimit() {
        Limit setting = readSetting();
        if (setting == Limit.OFF) return setting;
        try {
            if (!PrivilegedBackendManager.getInstance().isPrivilegedAvailable()) return setting;
        } catch (Throwable error) {
            return setting;
        }
        return ProcessLimitPolicy.combine(setting, run(ProcessLimitPolicy.READ_COMMAND, READ_TIMEOUT_S));
    }

    @NonNull
    private Limit combineWithSetting(@NonNull Limit read) {
        return ProcessLimitPolicy.combine(readSetting(), read);
    }

    @NonNull
    private Limit readSetting() {
        try {
            return ProcessLimitPolicy.fromMonitorSetting(ProcessLimitPolicy.effectiveMonitor(
                Settings.Global.getString(context.getContentResolver(), ProcessLimitPolicy.MONITOR_SETTING),
                systemProperty(ProcessLimitPolicy.MONITOR_OVERRIDE_PROPERTY)));
        } catch (Throwable error) {
            return Limit.UNKNOWN;
        }
    }

    /** A system property, or null when it cannot be read. */
    @Nullable
    private static String systemProperty(@NonNull String key) {
        try {
            Class<?> properties = Class.forName("android.os.SystemProperties");
            Object value = properties.getMethod("get", String.class).invoke(null, key);
            return value instanceof String ? (String) value : null;
        } catch (Throwable error) {
            return null;
        }
    }

    /** Runs {@code command} through the privileged backend and parses its read-back. */
    @NonNull
    private static Limit run(@NonNull String command, long timeoutS) {
        try {
            String output = PrivilegedBackendManager.getInstance().executeCommand(command)
                .get(timeoutS, TimeUnit.SECONDS);
            return ProcessLimitPolicy.fromReadBack(output);
        } catch (Throwable error) {
            Logger.logWarn(LOG_TAG, "Could not read the process limit: " + error.getMessage());
            return Limit.UNKNOWN;
        }
    }
}
