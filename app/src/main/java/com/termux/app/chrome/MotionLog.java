package com.termux.app.chrome;

import android.content.Context;
import android.os.SystemClock;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.view.TerminalView;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * The motion log (Settings › Debugging › Motion log): one line per motion event under logcat tag
 * {@link #TAG} and in a ring buffer of the last {@link #CAPACITY} lines, so a bug report can say
 * what a page change did and in what order. Off, an event costs one static boolean read: callers
 * guard on {@link #enabled} before they build the key=value string.
 *
 * <pre>
 * if (MotionLog.enabled) MotionLog.log("crossing-begin", "to=" + id);
 * </pre>
 */
public final class MotionLog {

    public static final String TAG = "TermuxMotion";
    public static final int CAPACITY = 2000;

    /** Read at every event site before any string is built. */
    public static volatile boolean enabled;

    private static final ArrayDeque<String> BUFFER = new ArrayDeque<>();

    private MotionLog() {
    }

    /** Records {@code t=<uptime ms> <event> <kv>}; a no-op while the switch is off. */
    public static void log(@NonNull String event, @Nullable String kv) {
        if (!enabled) return;
        record(SystemClock.uptimeMillis(), event, kv);
    }

    static void record(long uptimeMs, @NonNull String event, @Nullable String kv) {
        String line = "t=" + uptimeMs + " " + event
            + (kv == null || kv.isEmpty() ? "" : " " + kv);
        synchronized (BUFFER) {
            if (BUFFER.size() >= CAPACITY) BUFFER.pollFirst();
            BUFFER.addLast(line);
        }
        Log.d(TAG, line);
    }

    /** The first {@code count} frames above the caller, as {@code Class.method:line>...}. */
    @NonNull
    public static String callers(int count) {
        StackTraceElement[] stack = new Throwable().getStackTrace();
        StringBuilder out = new StringBuilder();
        // 0 is this method, 1 the logging caller; the callers above it are what a line needs.
        for (int i = 2; i < stack.length && i < 2 + count; i++) {
            if (out.length() > 0) out.append('>');
            String cls = stack[i].getClassName();
            out.append(cls.substring(cls.lastIndexOf('.') + 1)).append('.')
                .append(stack[i].getMethodName()).append(':').append(stack[i].getLineNumber());
        }
        return out.toString();
    }

    /** The buffered lines, oldest first. */
    @NonNull
    public static List<String> dump() {
        synchronized (BUFFER) {
            return new ArrayList<>(BUFFER);
        }
    }

    public static void clear() {
        synchronized (BUFFER) {
            BUFFER.clear();
        }
    }

    /** Turns the log on or off, here and in the terminal view's own events. */
    public static void setEnabled(boolean on) {
        enabled = on;
        TerminalView.sMotionLogEnabled = on;
        TerminalView.setMotionSink(on ? MotionLog::log : null);
    }

    /** Applies the stored switch; called once as the activity starts. */
    public static void syncFromPreferences(@NonNull Context context) {
        TermuxAppSharedPreferences prefs = TermuxAppSharedPreferences.build(context, false);
        setEnabled(prefs != null && prefs.isMotionLogEnabled());
    }
}
