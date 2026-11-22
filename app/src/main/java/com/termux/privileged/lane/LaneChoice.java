package com.termux.privileged.lane;

import androidx.annotation.NonNull;

/** Which way the lane runs right now; pure so the choice is tested. */
final class LaneChoice {

    private LaneChoice() {
    }

    /** Shizuku whenever it is usable, else root, else the reason Shizuku is missing. */
    @NonNull
    static PrivilegedLaneBinding.State choose(boolean policyOn, boolean shizukuAlive,
                                              boolean shizukuPermitted, boolean rootActive) {
        if (!policyOn) return PrivilegedLaneBinding.State.OFF;
        if (shizukuAlive && shizukuPermitted) return PrivilegedLaneBinding.State.BINDING;
        if (rootActive) return PrivilegedLaneBinding.State.ROOT;
        return shizukuAlive ? PrivilegedLaneBinding.State.NO_PERMISSION : PrivilegedLaneBinding.State.NO_SHIZUKU;
    }
}
