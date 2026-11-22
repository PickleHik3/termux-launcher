package com.termux.privileged;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Which privileged backend the user lets the launcher use (Settings › Shizuku and root). Pure, so
 * the migration from the three old switches, the backend order and the status line are tested.
 */
public enum PrivilegedChoice {
    /** Shizuku when it is running, otherwise root (su, then rish). The default. */
    AUTO("auto"),
    /** Shizuku only. */
    SHIZUKU("shizuku"),
    /** Root (su) only; never Shizuku, never rish. */
    ROOT("root"),
    /** No privileged features. */
    OFF("off");

    /** The first backend to try when the backend is chosen. */
    public enum First { NONE, SHIZUKU, SHELL }

    /** What is running now, in the words the settings screen shows. */
    public enum Status {
        CHECKING, USING_SHIZUKU, USING_ROOT, NEEDS_PERMISSION,
        SHIZUKU_NOT_RUNNING, ROOT_DENIED, NOT_AVAILABLE, OFF
    }

    public final String stored;

    PrivilegedChoice(String stored) {
        this.stored = stored;
    }

    /** The stored value, or null when it is missing or unknown (the caller then migrates). */
    @Nullable
    public static PrivilegedChoice fromStored(@Nullable String stored) {
        if (stored == null) return null;
        for (PrivilegedChoice choice : values()) {
            if (choice.stored.equals(stored)) return choice;
        }
        return null;
    }

    /** The one-time move from the old master, prefer-Shizuku and shell-fallback switches. */
    @NonNull
    public static PrivilegedChoice migrate(boolean masterEnabled, boolean preferShizuku, boolean shellFallback) {
        if (!masterEnabled) return OFF;
        if (preferShizuku) return shellFallback ? AUTO : SHIZUKU;
        return shellFallback ? ROOT : OFF;
    }

    public boolean allowsShizuku() {
        return this == AUTO || this == SHIZUKU;
    }

    /** su; rish rides with it only under AUTO, see {@link #allowsRish()}. */
    public boolean allowsRoot() {
        return this == AUTO || this == ROOT;
    }

    public boolean allowsRish() {
        return this == AUTO;
    }

    @NonNull
    public First first() {
        switch (this) {
            case AUTO:
            case SHIZUKU:
                return First.SHIZUKU;
            case ROOT:
                return First.SHELL;
            default:
                return First.NONE;
        }
    }

    /** Whether the shell backend is tried once Shizuku could not be used. */
    public boolean shellAfterShizukuFailed() {
        return this == AUTO;
    }

    /** @param shizukuBackend whether the active backend is Shizuku (else su/rish or none) */
    @NonNull
    public Status status(@NonNull PrivilegedBackendManager.BackendState state, boolean shizukuBackend) {
        if (this == OFF) return Status.OFF;
        switch (state) {
            case READY:
                return shizukuBackend ? Status.USING_SHIZUKU : Status.USING_ROOT;
            case FALLBACK_SHELL:
                return Status.USING_ROOT;
            case UNINITIALIZED:
            case INITIALIZING:
                return Status.CHECKING;
            case PERMISSION_DENIED:
            case PERMISSION_REQUESTING:
                return allowsShizuku() ? Status.NEEDS_PERMISSION : Status.ROOT_DENIED;
            default:
                if (this == ROOT) return Status.ROOT_DENIED;
                if (this == SHIZUKU) return Status.SHIZUKU_NOT_RUNNING;
                return Status.NOT_AVAILABLE;
        }
    }
}
