package com.termux.app.chrome;

import android.app.ActivityManager;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

/**
 * The Live blur switch as it stands: the user's own choice, else the default
 * ({@link LiveBlurPolicy#switchOn}: under Terl, on with more than 6 GiB of RAM). The RAM is read
 * once per process.
 */
public final class LiveBlurSwitch {

    private LiveBlurSwitch() {}

    private static long sTotalMem = -1L;
    private static boolean sLowRam;

    /** Whether the switch is on for {@code preferences}, on this phone. */
    public static boolean on(@NonNull Context context, @Nullable TermuxAppSharedPreferences preferences) {
        if (preferences == null) return false;
        readRam(context);
        return resolve(preferences, sTotalMem, sLowRam);
    }

    /**
     * The one resolution every reader shares (the glass, the registry's adapter): the stored
     * choice, else {@link LiveBlurPolicy#switchOn} for the desktop {@code preferences} hold and a
     * phone of {@code totalMemBytes} ({@code lowRamDevice}).
     */
    public static boolean resolve(@NonNull TermuxAppSharedPreferences preferences, long totalMemBytes,
                                  boolean lowRamDevice) {
        return LiveBlurPolicy.switchOn(preferences.getLiveBlurStored(), preferences.isTerlDesktopEnabled(),
            totalMemBytes, lowRamDevice);
    }

    /** This phone's RAM in bytes ({@code MemoryInfo.totalMem}), read once per process. */
    public static long totalMem(@NonNull Context context) {
        readRam(context);
        return sTotalMem;
    }

    /** Whether this is a low-RAM device, read once per process. */
    public static boolean lowRam(@NonNull Context context) {
        readRam(context);
        return sLowRam;
    }

    /** The RAM this process reads as {@code totalMemBytes} ({@code lowRamDevice}); for tests. */
    @androidx.annotation.VisibleForTesting
    public static synchronized void setRamForTest(long totalMemBytes, boolean lowRamDevice) {
        sTotalMem = totalMemBytes;
        sLowRam = lowRamDevice;
    }

    private static synchronized void readRam(@NonNull Context context) {
        if (sTotalMem >= 0L) return;
        ActivityManager am = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (am == null) {
            sTotalMem = 0L;
            return;
        }
        ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
        am.getMemoryInfo(info);
        sTotalMem = info.totalMem;
        sLowRam = am.isLowRamDevice();
    }

    /** The RAM tier of live window residency on this phone. */
    @NonNull
    public static com.termux.app.glass.GlassResidency.Tier residencyTier(@NonNull Context context) {
        readRam(context);
        return com.termux.app.glass.GlassResidency.tier(sTotalMem, sLowRam);
    }
}
