package com.termux.ai;

import android.app.ActivityManager;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * The tier a phone's RAM puts it in (tai-device-tiers spec §1). A tier decides what the launcher
 * suggests and preselects; it never forces a load (the live gate still decides that) and never
 * blocks a model the user added themselves.
 *
 * <p>The input is the RAM class from {@link TaiLoadBudget#ramClassBytes} (the size the phone is sold
 * with, rounded up from {@code totalMem}), never {@code advertisedMem}, which counts swap and sizes
 * pong's 12 GB as 16 GB. Swap and vendor "virtual RAM" do not count.
 */
public enum TaiDeviceTier {
    /** 6 GB and under: nothing by default. */
    TIER_1,
    /** 8, 10 and 12 GB: LLMs on, E2B the default assistant. */
    TIER_2,
    /** 16 GB and more: everything local, E4B the default assistant. */
    TIER_3;

    private static final long GIB = 1024L * 1024L * 1024L;

    /** The developer override as of the last read or write; {@code null} is automatic. */
    @Nullable private static volatile TaiDeviceTier sOverride;

    /** 1, 2 or 3, as the welcome card and the Model Centre header print it ("Tier 2 · 12 GB"). */
    public int number() {
        return ordinal() + 1;
    }

    /**
     * The tier for a RAM class in bytes. An unknown RAM ({@code <= 0}) is Tier 1: the cautious
     * answer, since nothing is preselected there.
     */
    @NonNull
    public static TaiDeviceTier from(long ramClassBytes) {
        if (ramClassBytes <= 6L * GIB) return TIER_1;
        if (ramClassBytes <= 12L * GIB) return TIER_2;
        return TIER_3;
    }

    /**
     * True for the 8 GB class, the one Tier 2 phone that gets its own policy rows (E2B wherever
     * 10 and 12 GB use E4B).
     */
    public static boolean isEightGb(long ramClassBytes) {
        return ramClassBytes > 6L * GIB && ramClassBytes <= 8L * GIB;
    }

    /** The override a setting value names ({@code "1"}, {@code "2"}, {@code "3"}), else {@code null} for automatic. */
    @Nullable
    public static TaiDeviceTier parseOverride(@Nullable String value) {
        if (value == null) return null;
        switch (value.trim()) {
            case "1": return TIER_1;
            case "2": return TIER_2;
            case "3": return TIER_3;
            default: return null;
        }
    }

    /** The override when there is one, else the tier of {@code ramClassBytes}. */
    @NonNull
    public static TaiDeviceTier effective(@Nullable TaiDeviceTier override, long ramClassBytes) {
        return override != null ? override : from(ramClassBytes);
    }

    /** {@link #effective} with the override as last read, for callers that hold no context. */
    @NonNull
    public static TaiDeviceTier effectiveCached(long ramClassBytes) {
        return effective(sOverride, ramClassBytes);
    }

    /** Called by {@link TaiSettings#setTierOverride}, so the cached read follows a change at once. */
    static void rememberOverride(@Nullable TaiDeviceTier override) {
        sOverride = override;
    }

    /** The RAM class of this phone, 0 when the system will not say. */
    public static long deviceRamClassBytes(@NonNull Context context) {
        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (manager == null) return 0L;
        ActivityManager.MemoryInfo info = new ActivityManager.MemoryInfo();
        manager.getMemoryInfo(info);
        return TaiLoadBudget.ramClassBytes(info.totalMem);
    }

    /** This phone's tier, honouring the developer override. Cheap, but reads preferences. */
    @NonNull
    public static TaiDeviceTier forDevice(@NonNull Context context) {
        TaiDeviceTier override = parseOverride(new TaiSettings(context).getTierOverrideValue());
        sOverride = override;
        return effective(override, deviceRamClassBytes(context));
    }
}
