package com.termux.privileged.lane;

import androidx.annotation.NonNull;

import com.termux.privileged.PrivilegedChoice;

/** Which way the lane runs right now; pure so the choice is tested. */
final class LaneChoice {

    private LaneChoice() {
    }

    /**
     * The lane follows the user's backend choice: Shizuku only under Shizuku, root only under
     * Root, Shizuku else root under Automatic, nothing under Off.
     */
    @NonNull
    static PrivilegedLaneBinding.State choose(@NonNull PrivilegedChoice choice, boolean policyOn,
                                              boolean shizukuAlive, boolean shizukuPermitted,
                                              boolean rootActive) {
        if (choice == PrivilegedChoice.OFF) return PrivilegedLaneBinding.State.OFF;
        if (choice == PrivilegedChoice.ROOT) {
            if (!policyOn) return PrivilegedLaneBinding.State.OFF;
            return rootActive ? PrivilegedLaneBinding.State.ROOT : PrivilegedLaneBinding.State.NO_ROOT;
        }
        return choose(policyOn, shizukuAlive, shizukuPermitted,
            rootActive && choice.allowsRoot());
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
