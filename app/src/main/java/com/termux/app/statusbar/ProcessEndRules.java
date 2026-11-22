package com.termux.app.statusbar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/** Which processes the stats card may offer to stop or end with root, as a pure answer. */
final class ProcessEndRules {

    /** System processes whose death takes the device's session or services with it. */
    private static final Set<String> PROTECTED = new HashSet<>(Arrays.asList(
        "system_server", "zygote", "zygote64", "surfaceflinger", "servicemanager", "init",
        "lmkd", "vold", "magiskd"));

    private ProcessEndRules() {
    }

    /** The package a process name stands for: the name without any {@code :suffix}. */
    @NonNull
    static String packageOf(@NonNull String processName) {
        int colon = processName.indexOf(':');
        return colon > 0 ? processName.substring(0, colon) : processName;
    }

    /**
     * Whether a row for {@code process} is tappable: not a kernel thread, pid above 1, not the
     * launcher's own pid or package, and not one of the protected system processes.
     */
    static boolean canEnd(@NonNull SystemStatsController.Proc process, int ownPid,
                          @Nullable String ownPackage) {
        if (process.kernel || process.pid <= 1 || process.pid == ownPid) return false;
        String pkg = packageOf(process.name);
        if (PROTECTED.contains(process.name) || PROTECTED.contains(pkg)) return false;
        return ownPackage == null || !ownPackage.equals(pkg);
    }
}
