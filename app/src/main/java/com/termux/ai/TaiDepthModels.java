package com.termux.ai;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Which depth model makes a photo wallpaper's depth image. Depth Anything V2 Small (28 MB, int8)
 * on Tier 1 and the 8 GB class, where the smaller graph keeps the peak low; Depth Anything 3 Small
 * (55 MB, sharper) everywhere else.
 */
public final class TaiDepthModels {
    private TaiDepthModels() {}

    /** The catalogue id to use on a device whose RAM class is {@code ramClassBytes}. */
    @NonNull
    public static String choose(long ramClassBytes) {
        if (TaiDeviceTier.from(ramClassBytes) == TaiDeviceTier.TIER_1 || TaiDeviceTier.isEightGb(ramClassBytes)) {
            return TaiModelCatalog.DEPTH_ANYTHING_V2_SMALL_ID;
        }
        return TaiModelCatalog.DEPTH_ANYTHING_3_SMALL_ID;
    }

    /** True for a depth model id the maker runs. */
    public static boolean isDepthModel(@Nullable String modelId) {
        return TaiModelCatalog.DEPTH_ANYTHING_3_SMALL_ID.equals(modelId)
            || TaiModelCatalog.DEPTH_ANYTHING_V2_SMALL_ID.equals(modelId);
    }

    /**
     * True when the model's output is distance (Depth Anything 3: far is high) rather than
     * disparity (Depth Anything V2: near is high). {@code architecture} may be null.
     */
    public static boolean isDistance(@NonNull String modelId, @Nullable String architecture) {
        return "depth-anything-3".equals(architecture)
            || TaiModelCatalog.DEPTH_ANYTHING_3_SMALL_ID.equals(modelId);
    }
}
