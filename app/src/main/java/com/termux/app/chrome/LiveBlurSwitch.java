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
        Boolean stored = preferences.getLiveBlurStored();
        if (stored != null) return stored;
        if (!preferences.isTerlDesktopEnabled()) return false;
        readRam(context);
        return LiveBlurPolicy.switchOn(null, true, sTotalMem, sLowRam);
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
