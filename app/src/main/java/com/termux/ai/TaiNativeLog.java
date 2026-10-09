package com.termux.ai;

import android.util.Log;

import androidx.annotation.Nullable;

/**
 * Keeps native debug logging out of the AI runtime process. Upstream MNN prints user prompts at
 * DEBUG (its {@code MNN_DEBUG} strings, e.g. {@code generateNative: prompt=%s}), so the process
 * that loads it raises its log floor to INFO through {@code libtai_xnnpack.so} before any model
 * library is loaded. A debug build can keep the chatter with
 * {@code adb shell setprop debug.termux.tai.verbose 1}.
 */
final class TaiNativeLog {
    private static final String TAG = "TaiNativeLog";
    static final String VERBOSE_PROPERTY = "debug.termux.tai.verbose";
    private static boolean applied;

    private TaiNativeLog() {
    }

    /** Idempotent and never throws; a no-op below API 30 (the native side cannot find the symbol). */
    static synchronized void silenceDebugOnce() {
        if (applied) return;
        applied = true;
        if ("1".equals(systemProperty(VERBOSE_PROPERTY))) {
            Log.i(TAG, "native debug logging left on by " + VERBOSE_PROPERTY);
            return;
        }
        try {
            System.loadLibrary("tai_xnnpack");
            nativeSetMinimumPriority(Log.INFO);
        } catch (Throwable t) {
            Log.w(TAG, "could not raise the native log floor", t);
        }
    }

    @Nullable
    private static String systemProperty(String name) {
        try {
            Class<?> properties = Class.forName("android.os.SystemProperties");
            return (String) properties.getMethod("get", String.class).invoke(null, name);
        } catch (Throwable t) {
            return null;
        }
    }

    private static native boolean nativeSetMinimumPriority(int priority);
}
