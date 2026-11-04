package com.termux.launcherctl;

import androidx.annotation.Nullable;

/**
 * Which processes bind the LauncherCtl API server at application start. The system revives the
 * main process as a bare service host after a package update, so the server cannot wait for the
 * launcher activity; the helper processes (such as {@code :tai_runtime}) must never bind it.
 */
public final class LauncherCtlStartPolicy {

    private LauncherCtlStartPolicy() {
    }

    /** True only in the main process, whose name is the package name. */
    public static boolean startsAtApplicationStart(@Nullable String processName,
                                                   @Nullable String packageName) {
        return processName != null && processName.equals(packageName);
    }
}
