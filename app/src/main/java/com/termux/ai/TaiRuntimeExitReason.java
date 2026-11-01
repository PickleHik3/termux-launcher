package com.termux.ai;

import android.app.ActivityManager;
import android.app.ApplicationExitInfo;
import android.content.Context;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.List;

/**
 * Why Android ended the {@code :tai_runtime} process, from the system's exit records (API 30+).
 * Tells a low-memory kill from a crash, which the binder death alone cannot.
 */
final class TaiRuntimeExitReason {
    static final String LOW_MEMORY = "low_memory";
    static final String CRASH = "crash";

    private static final int MAX_RECORDS = 5;

    private TaiRuntimeExitReason() {
    }

    /**
     * The reason the runtime process last exited at or after {@code sinceMs}, or null when there is
     * no such record yet, the reason is not one worth naming, or the platform has no exit records.
     */
    @Nullable
    static String lookup(@NonNull Context context, long sinceMs) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null;
        try {
            ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
            if (manager == null) return null;
            String runtimeProcess = context.getPackageName() + TaiRuntimeIpc.RUNTIME_PROCESS_SUFFIX;
            List<ApplicationExitInfo> records =
                manager.getHistoricalProcessExitReasons(context.getPackageName(), 0, MAX_RECORDS);
            ApplicationExitInfo newest = null;
            if (records != null) {
                for (ApplicationExitInfo info : records) {
                    if (!runtimeProcess.equals(info.getProcessName()) || info.getTimestamp() < sinceMs) continue;
                    if (newest == null || info.getTimestamp() > newest.getTimestamp()) newest = info;
                }
            }
            if (newest == null) return null;
            switch (newest.getReason()) {
                case ApplicationExitInfo.REASON_LOW_MEMORY:
                    return LOW_MEMORY;
                case ApplicationExitInfo.REASON_CRASH:
                case ApplicationExitInfo.REASON_CRASH_NATIVE:
                    return CRASH;
                default:
                    return null;
            }
        } catch (Exception e) {
            return null;
        }
    }
}
