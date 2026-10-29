package com.termux.app.setup;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Build;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.termux.ai.TaiLoadBudget;
import com.termux.app.AnimationSpeed;
import com.termux.app.fragments.settings.termux.TaiGpuName;
import com.termux.app.x11.X11GpuProbe;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * How capable this phone is, as the setup wizard pre-selects its Power, AI and Panes choices.
 * {@link #decide} is pure; {@link #read} is the thin Android reader.
 *
 * <p>Thresholds (the spec's "Device detection and tiers"):
 * <ul>
 * <li>LOW: RAM under 4.5 GB; or a GPU / SoC on the virgl freeze tables of
 *     {@link X11GpuProbe} (Mali-G52, MT6768); or a SoC on the short low list below.</li>
 * <li>HIGH: RAM of 8 GB or more and not LOW.</li>
 * <li>MID: everything else.</li>
 * </ul>
 * The RAM is the phone's advertised class (an "8 GB" phone reports about 7.4 GB of total memory),
 * which is what {@link TaiLoadBudget#ramClassBytes} rounds to.
 */
final class DeviceTier {

    enum Tier { LOW, MID, HIGH }

    static final long GIB = 1024L * 1024L * 1024L;
    /** Under this is LOW. */
    static final long LOW_RAM_BYTES = 4L * GIB + GIB / 2;
    /** At or above this, and not LOW, is HIGH. */
    static final long HIGH_RAM_BYTES = 8L * GIB;

    /** The low list: these Helio names, matched on the SoC and board strings. */
    private static final String[] LOW_SOCS = {"mt6765", "mt6768", "mt6762"};
    /** Snapdragon SM4xxx and below: SM4350 and under. */
    private static final int LOW_SM_MAX = 4350;
    private static final Pattern SM = Pattern.compile("sm(\\d{4})");
    /** Exynos 850, and the 1xxx line up to the 1280. */
    private static final Pattern EXYNOS = Pattern.compile("exynos\\s*-?(\\d{3,4})");

    private DeviceTier() {}

    /** What the reader found. */
    static final class Detected {
        @NonNull final String model;
        final long ramBytes;
        @Nullable final String gpu;
        @NonNull final Tier tier;

        Detected(@NonNull String model, long ramBytes, @Nullable String gpu, @NonNull Tier tier) {
            this.model = model;
            this.ramBytes = ramBytes;
            this.gpu = gpu;
            this.tier = tier;
        }
    }

    /** What a tier pre-selects. Updaters keep their stored values instead. */
    static final class Defaults {
        final boolean ai;
        final boolean fancierGlass;
        final boolean motionLight;
        final boolean parallax;
        final boolean lazy;
        final boolean lowMemory;
        @NonNull final String animationSpeed;
        final boolean autotiling;

        Defaults(boolean ai, boolean fancierGlass, boolean motionLight, boolean parallax,
                 boolean lazy, boolean lowMemory, @NonNull String animationSpeed,
                 boolean autotiling) {
            this.ai = ai;
            this.fancierGlass = fancierGlass;
            this.motionLight = motionLight;
            this.parallax = parallax;
            this.lazy = lazy;
            this.lowMemory = lowMemory;
            this.animationSpeed = animationSpeed;
            this.autotiling = autotiling;
        }
    }

    @NonNull
    static Defaults defaults(@NonNull Tier tier) {
        switch (tier) {
            case LOW:
                return new Defaults(false, false, false, false, true, true,
                    AnimationSpeed.SLOWER, true);
            case MID:
                return new Defaults(true, true, false, true, false, false,
                    AnimationSpeed.NORMAL, true);
            case HIGH:
            default:
                return new Defaults(true, true, true, true, false, false,
                    AnimationSpeed.NORMAL, true);
        }
    }

    /**
     * @param ramBytes the phone's RAM class in bytes, 0 when unknown
     * @param soc      {@code Build.SOC_MODEL}, may be empty
     * @param hardware {@code Build.HARDWARE}, may be empty
     * @param gpu      the GL renderer, null when no GL context could name it
     */
    @NonNull
    static Tier decide(long ramBytes, @Nullable String soc, @Nullable String hardware,
                       @Nullable String gpu) {
        if (ramBytes > 0 && ramBytes < LOW_RAM_BYTES) return Tier.LOW;
        String platform = ((soc == null ? "" : soc) + " " + (hardware == null ? "" : hardware))
            .toLowerCase(Locale.ROOT);
        String renderer = gpu == null ? "" : gpu.toLowerCase(Locale.ROOT);
        for (String bad : X11GpuProbe.knownFreezeRenderers())
            if (renderer.contains(bad)) return Tier.LOW;
        for (String bad : X11GpuProbe.knownFreezePlatforms())
            if (platform.contains(bad)) return Tier.LOW;
        if (onLowList(platform)) return Tier.LOW;
        return ramBytes >= HIGH_RAM_BYTES ? Tier.HIGH : Tier.MID;
    }

    private static boolean onLowList(@NonNull String platform) {
        for (String low : LOW_SOCS) if (platform.contains(low)) return true;
        Matcher sm = SM.matcher(platform);
        while (sm.find()) if (Integer.parseInt(sm.group(1)) <= LOW_SM_MAX) return true;
        Matcher exynos = EXYNOS.matcher(platform);
        while (exynos.find()) {
            int number = Integer.parseInt(exynos.group(1));
            if (number == 850 || (number >= 1000 && number <= 1280)) return true;
        }
        return false;
    }

    /**
     * Reads the phone. {@code withGpu} names the GL renderer, which makes a pbuffer context: only
     * from a worker thread. Without it the tier rests on RAM and SoC alone.
     */
    @NonNull
    @WorkerThread
    static Detected read(@NonNull Context context, boolean withGpu) {
        long total = 0L;
        ActivityManager manager = context.getSystemService(ActivityManager.class);
        if (manager != null) {
            ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
            manager.getMemoryInfo(info);
            total = TaiLoadBudget.ramClassBytes(info.totalMem);
        }
        String soc = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && Build.SOC_MODEL != null
            ? Build.SOC_MODEL : "";
        String gpu = withGpu ? TaiGpuName.get() : null;
        String model = Build.MODEL == null ? "" : Build.MODEL;
        return new Detected(model, total, gpu, decide(total, soc, Build.HARDWARE, gpu));
    }
}
