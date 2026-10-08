package com.termux.ai;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

/**
 * The phone's free memory as the gate, the load meter and the pressure watch read it.
 *
 * <p>{@code ActivityManager.MemoryInfo.availMem} is MemAvailable only on Android 16 (API 36); from
 * Android 9 up to 15 it is {@code MemFree + Cached}, which counts shared and mapped pages
 * MemAvailable discounts and so reads high: a gate on it admits more than it should. Below API 36
 * this class reads {@code MemAvailable} from {@code /proc/meminfo} instead, together with
 * {@code SwapTotal} and {@code SwapFree}, which {@code MemoryInfo} does not carry at all. When the
 * file cannot be read (whether an untrusted app may read it on every release is unverified) the
 * reading falls back to {@code availMem} and swap is unknown.
 *
 * <p>The parser and the choice between the two sources are pure; only {@link #read} and
 * {@link #conditions} touch Android.
 */
final class TaiMemInfo {
    /** Android 16: the first release whose {@code availMem} is MemAvailable. */
    static final int AVAIL_MEM_IS_AVAILABLE_SDK = 36;
    private static final String PROC_MEMINFO = "/proc/meminfo";

    private TaiMemInfo() {
    }

    /** One reading; a negative swap figure means the swap size is not known. */
    static final class Reading {
        /** Free memory for the gate, in bytes; {@code 0} when nothing could be read. */
        final long availBytes;
        final long swapTotalBytes;
        final long swapFreeBytes;
        /** Whether {@link #availBytes} came from {@code /proc/meminfo}. */
        final boolean fromProc;

        Reading(long availBytes, long swapTotalBytes, long swapFreeBytes, boolean fromProc) {
            this.availBytes = availBytes;
            this.swapTotalBytes = swapTotalBytes;
            this.swapFreeBytes = swapFreeBytes;
            this.fromProc = fromProc;
        }

        boolean swapKnown() {
            return swapTotalBytes >= 0L && swapFreeBytes >= 0L;
        }

    }

    /**
     * Parses the text of {@code /proc/meminfo} (values in kB). {@code null} when it has no usable
     * {@code MemAvailable} line; a missing swap line leaves swap unknown.
     */
    @Nullable
    static Reading parse(@Nullable String meminfo) {
        if (meminfo == null) return null;
        long avail = -1L;
        long swapTotal = -1L;
        long swapFree = -1L;
        for (String line : meminfo.split("\n")) {
            int colon = line.indexOf(':');
            if (colon <= 0) continue;
            String name = line.substring(0, colon).trim();
            if (!"MemAvailable".equals(name) && !"SwapTotal".equals(name) && !"SwapFree".equals(name)) continue;
            long bytes = kbToBytes(line.substring(colon + 1));
            if (bytes < 0L) continue;
            if ("MemAvailable".equals(name)) avail = bytes;
            else if ("SwapTotal".equals(name)) swapTotal = bytes;
            else swapFree = bytes;
        }
        if (avail <= 0L) return null;
        if (swapTotal < 0L || swapFree < 0L) {
            swapTotal = -1L;
            swapFree = -1L;
        }
        return new Reading(avail, swapTotal, swapFree, true);
    }

    private static long kbToBytes(@NonNull String value) {
        String digits = value.trim();
        int space = digits.indexOf(' ');
        if (space > 0) digits = digits.substring(0, space);
        try {
            long kb = Long.parseLong(digits);
            return kb < 0L ? -1L : kb * 1024L;
        } catch (NumberFormatException e) {
            return -1L;
        }
    }

    /**
     * Picks the free-memory figure: the {@code /proc/meminfo} one below API 36, {@code availMem}
     * from API 36 on or when the file could not be read. Swap comes from the file whenever it was
     * read, and is unknown otherwise.
     */
    @NonNull
    static Reading choose(int sdkInt, @Nullable Reading proc, long availMemBytes) {
        if (proc == null) return new Reading(Math.max(0L, availMemBytes), -1L, -1L, false);
        boolean useProc = sdkInt < AVAIL_MEM_IS_AVAILABLE_SDK || availMemBytes <= 0L;
        return new Reading(useProc ? proc.availBytes : availMemBytes, proc.swapTotalBytes, proc.swapFreeBytes, useProc);
    }

    /** One reading of the phone's memory now; never throws. */
    @NonNull
    static Reading read(@Nullable Context context) {
        ActivityManager activityManager = context == null ? null : context.getSystemService(ActivityManager.class);
        return read(activityManager);
    }

    @NonNull
    static Reading read(@Nullable ActivityManager activityManager) {
        long availMem = 0L;
        if (activityManager != null) {
            try {
                ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
                activityManager.getMemoryInfo(info);
                availMem = info.availMem;
            } catch (RuntimeException ignored) {
            }
        }
        return choose(Build.VERSION.SDK_INT, readProc(), availMem);
    }

    @Nullable
    private static Reading readProc() {
        try {
            return parse(new String(Files.readAllBytes(Paths.get(PROC_MEMINFO)), StandardCharsets.UTF_8));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * The conditions the budget's floors depend on, read now: the user's memory limits
     * ({@link TaiSettings#memoryMode}), which both the gate in the main process and the runtime's
     * watch in {@code :tai_runtime} must see the same way.
     */
    @NonNull
    static TaiLoadBudget.Conditions conditions(@Nullable Context context) {
        return TaiLoadBudget.Conditions.of(TaiSettings.memoryMode(context));
    }
}
