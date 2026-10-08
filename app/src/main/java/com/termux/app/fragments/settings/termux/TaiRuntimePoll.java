package com.termux.app.fragments.settings.termux;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.ai.TaiManager;

import org.json.JSONObject;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

/**
 * Polls the runtime status for an On-device AI page. The status is a blocking IPC that can wait on
 * the runtime process, so it is fetched on a background thread (a main-thread fetch once ANR'd the
 * settings page) and delivered on the main thread only while the page is attached. Polling repeats
 * every two seconds while {@link TaiRuntimeStatusText#keepPolling} says something will change.
 */
final class TaiRuntimePoll {
    private static final long INTERVAL_MS = 2000L;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "tai-settings-runtime");
        thread.setDaemon(true);
        return thread;
    });
    @NonNull private final BooleanSupplier isAttached;
    @Nullable private Context context;
    @Nullable private Consumer<JSONObject> onStatus;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            fetch();
        }
    };

    TaiRuntimePoll(@NonNull BooleanSupplier isAttached) {
        this.isAttached = isAttached;
    }

    /** Fetches now and keeps polling while the runtime is busy; replaces any earlier start. */
    void start(@NonNull Context context, @NonNull Consumer<JSONObject> onStatus) {
        this.context = context.getApplicationContext();
        this.onStatus = onStatus;
        handler.removeCallbacks(tick);
        fetch();
    }

    /** One fetch outside the polling cadence, after an action changed the runtime. */
    void refreshNow() {
        handler.removeCallbacks(tick);
        fetch();
    }

    /** Stops polling; a fetch already in flight is dropped when it lands. */
    void stop() {
        handler.removeCallbacks(tick);
        onStatus = null;
    }

    void shutdown() {
        stop();
        executor.shutdownNow();
    }

    /** Runs one-off work off the main thread (runtime actions, diagnostics, summaries). */
    void run(@NonNull Runnable backgroundWork) {
        if (executor.isShutdown()) return;
        executor.execute(backgroundWork);
    }

    private void fetch() {
        final Context ctx = context;
        if (ctx == null || onStatus == null || executor.isShutdown()) return;
        executor.execute(() -> {
            JSONObject status;
            try {
                status = TaiManager.getInstance(ctx).runtimeStatus();
            } catch (Exception e) {
                status = null;
            }
            final JSONObject result = status;
            handler.post(() -> {
                Consumer<JSONObject> listener = onStatus;
                if (listener == null || !isAttached.getAsBoolean()) return;
                listener.accept(result);
                handler.removeCallbacks(tick);
                if (TaiRuntimeStatusText.keepPolling(result)) {
                    handler.postDelayed(tick, INTERVAL_MS);
                }
            });
        });
    }
}
