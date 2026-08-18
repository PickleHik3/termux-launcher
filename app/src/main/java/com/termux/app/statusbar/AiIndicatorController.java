package com.termux.app.statusbar;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;

import com.termux.R;
import com.termux.ai.TaiRuntimePresence;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The status bar's AI glyph: a robot with the countdown to the idle unload, shown only while a model
 * is actually resident.
 *
 * <p>Presence comes from {@link TaiRuntimePresence} — a file the runtime process publishes — so this
 * never binds the runtime service and therefore never keeps a model alive by watching it. The
 * countdown itself is computed locally between publishes; the runtime's idle deadline is an absolute
 * timestamp, so one tick a second needs no cross-process traffic at all.
 *
 * <p>An unload greys the glyph for a few seconds before it disappears. A widget that vanished the
 * instant a model unloaded would read as a glitch — the grey tail says "it was here, it is gone".
 */
public final class AiIndicatorController {

    /** How long the glyph lingers, greyed, after the model is gone. */
    static final long UNLOAD_GRACE_MS = 6_000L;
    /** A loaded snapshot older than this, with no runtime process alive, is a killed runtime. */
    static final long SNAPSHOT_STALE_MS = 20_000L;
    private static final long TICK_MS = 1_000L;
    private static final long SNAPSHOT_POLL_MS = 5_000L;

    private final StatusBarWidgetView widget;
    private final MaterialDotSeparatorView dot;
    private final Context context;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ExecutorService reader = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "tai-presence-read");
        thread.setDaemon(true);
        return thread;
    });

    @NonNull private TaiRuntimePresence.Snapshot snapshot = TaiRuntimePresence.empty();
    private long lastSnapshotReadMs;
    private long unloadObservedAtMs;
    private boolean started;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            long now = System.currentTimeMillis();
            if (now - lastSnapshotReadMs >= SNAPSHOT_POLL_MS) refreshSnapshot();
            render(now);
            handler.postDelayed(this, TICK_MS);
        }
    };

    private final BroadcastReceiver presenceReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context receiverContext, Intent intent) {
            refreshSnapshot();
        }
    };

    public AiIndicatorController(@NonNull StatusBarWidgetView widget,
                                 @NonNull MaterialDotSeparatorView dot) {
        this.widget = widget;
        this.dot = dot;
        this.context = widget.getContext().getApplicationContext();
        widget.setIconResource(R.drawable.ic_symbol_smart_toy);
        widget.setColorRole(StatusBarWidgetView.ColorRole.TERTIARY);
    }

    /** Starts ticking. Safe to call repeatedly; the activity calls it from every resume. */
    public void start() {
        if (started) return;
        started = true;
        IntentFilter filter = new IntentFilter(TaiRuntimePresence.ACTION_PRESENCE_CHANGED);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(presenceReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            context.registerReceiver(presenceReceiver, filter);
        }
        refreshSnapshot();
        render(System.currentTimeMillis());
        handler.removeCallbacks(tick);
        handler.postDelayed(tick, TICK_MS);
    }

    public void stop() {
        if (!started) return;
        started = false;
        handler.removeCallbacks(tick);
        try {
            context.unregisterReceiver(presenceReceiver);
        } catch (IllegalArgumentException ignored) {
        }
    }

    /** Re-runs the visibility rule, e.g. after the other status widgets were toggled. */
    public void refresh() {
        render(System.currentTimeMillis());
    }

    private void refreshSnapshot() {
        lastSnapshotReadMs = System.currentTimeMillis();
        reader.execute(() -> {
            TaiRuntimePresence.Snapshot next = TaiRuntimePresence.read(context);
            handler.post(() -> {
                snapshot = next;
                render(System.currentTimeMillis());
            });
        });
    }

    private void render(long nowMs) {
        boolean active = snapshot.active() && !isStale(snapshot, nowMs);
        if (active) {
            unloadObservedAtMs = 0L;
            widget.setMuted(false);
            widget.setValue(valueFor(snapshot, nowMs));
            show(true);
            return;
        }
        if (widget.getVisibility() != View.VISIBLE) {
            show(false);
            return;
        }
        if (unloadObservedAtMs == 0L) unloadObservedAtMs = nowMs;
        if (nowMs - unloadObservedAtMs >= UNLOAD_GRACE_MS) {
            unloadObservedAtMs = 0L;
            widget.setMuted(false);
            show(false);
            return;
        }
        widget.setMuted(true);
        widget.setValue(context.getString(R.string.termux_status_widget_ai_unloaded));
        show(true);
    }

    private void show(boolean visible) {
        widget.setVisibility(visible ? View.VISIBLE : View.GONE);
        // The dot only earns its pixels when it separates two visible things.
        dot.setVisibility(visible && hasVisibleSiblingBefore() ? View.VISIBLE : View.GONE);
    }

    private boolean hasVisibleSiblingBefore() {
        ViewGroup parent = dot.getParent() instanceof ViewGroup ? (ViewGroup) dot.getParent() : null;
        if (parent == null) return false;
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child == dot) return false;
            if (child instanceof StatusBarWidgetView && child.getVisibility() == View.VISIBLE)
                return true;
        }
        return false;
    }

    @NonNull
    private String valueFor(@NonNull TaiRuntimePresence.Snapshot snapshot, long nowMs) {
        if (snapshot.loading)
            return context.getString(R.string.termux_status_widget_ai_loading);
        if (snapshot.idleUnloadAtMs <= 0L)
            return context.getString(R.string.termux_status_widget_ai_resident);
        return countdownText(snapshot.idleUnloadAtMs - nowMs);
    }

    /**
     * @return the countdown as it reads in a 34dp-wide glyph: seconds only in the last minute,
     *     m:ss under ten minutes, whole minutes above that.
     */
    @NonNull
    public static String countdownText(long remainingMs) {
        long seconds = Math.max(0L, remainingMs) / 1000L;
        if (seconds < 60L) return seconds + "s";
        long minutes = seconds / 60L;
        if (minutes < 10L) return String.format(Locale.US, "%d:%02d", minutes, seconds % 60L);
        if (minutes < 100L) return minutes + "m";
        return (minutes / 60L) + "h";
    }

    /**
     * @return true when a snapshot claims a resident model but is old and the runtime process is no
     *     longer there — a killed runtime never gets to publish its own unload.
     */
    static boolean isStale(@NonNull TaiRuntimePresence.Snapshot snapshot, long nowMs) {
        if (!snapshot.active()) return false;
        return nowMs - snapshot.publishedAtMs > SNAPSHOT_STALE_MS;
    }
}
