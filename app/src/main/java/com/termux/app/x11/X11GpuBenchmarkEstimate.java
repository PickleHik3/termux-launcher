package com.termux.app.x11;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * What a graphics benchmark run will cost, as {@code termux-x11-gpu-setup --estimate} prints it:
 * one {@code key=value} line each for the bytes it downloads, the bytes it takes on the phone while
 * it runs, the bytes it gives back at the end, and how many installed packages it brings up to date.
 * A key that is missing, negative or not a number is {@link #UNKNOWN}; anything else on the output
 * (a package manager's own chatter) is ignored. Pure, so the contract with the script is tested.
 */
public final class X11GpuBenchmarkEstimate {
    /** A figure the script did not give, or gave in a form that is not a count. */
    public static final long UNKNOWN = -1L;

    /** Nothing known: what a run that failed or timed out leaves. */
    public static final X11GpuBenchmarkEstimate NONE =
        new X11GpuBenchmarkEstimate(UNKNOWN, UNKNOWN, UNKNOWN, UNKNOWN);

    public final long downloadBytes;
    public final long installBytes;
    public final long removableBytes;
    public final long upgradeCount;

    X11GpuBenchmarkEstimate(long downloadBytes, long installBytes, long removableBytes, long upgradeCount) {
        this.downloadBytes = downloadBytes;
        this.installBytes = installBytes;
        this.removableBytes = removableBytes;
        this.upgradeCount = upgradeCount;
    }

    /** Whether all three sizes are known. */
    public boolean sizesKnown() {
        return downloadBytes != UNKNOWN && installBytes != UNKNOWN && removableBytes != UNKNOWN;
    }

    /** Whether none of the three sizes is known. */
    public boolean noSizesKnown() {
        return downloadBytes == UNKNOWN && installBytes == UNKNOWN && removableBytes == UNKNOWN;
    }

    @NonNull
    public static X11GpuBenchmarkEstimate parse(@Nullable String output) {
        if (output == null || output.isEmpty()) return NONE;
        long download = UNKNOWN;
        long install = UNKNOWN;
        long removable = UNKNOWN;
        long upgrades = UNKNOWN;
        for (String raw : output.split("\r?\n|\r")) {
            String line = raw.trim();
            int eq = line.indexOf('=');
            if (eq <= 0) continue;
            String key = line.substring(0, eq).trim();
            long value = count(line.substring(eq + 1).trim());
            switch (key) {
                case "download_bytes": download = value; break;
                case "install_bytes": install = value; break;
                case "removable_bytes": removable = value; break;
                case "upgrade_count": upgrades = value; break;
                default: break;
            }
        }
        return new X11GpuBenchmarkEstimate(download, install, removable, upgrades);
    }

    private static long count(@NonNull String text) {
        if (text.isEmpty()) return UNKNOWN;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c < '0' || c > '9') return UNKNOWN;
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException e) {
            return UNKNOWN;
        }
    }
}
