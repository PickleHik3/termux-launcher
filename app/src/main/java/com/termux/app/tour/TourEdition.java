package com.termux.app.tour;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Which of the three shipped editions is running, as far as the closing card has to care.
 *
 * <p>The applicationId is the only thing that differs between the edition branches at runtime —
 * they carry no build flag of their own, and a {@code BuildConfig} field added here would have to
 * be added to each branch's {@code app/build.gradle} by hand and would silently be wrong the first
 * time a merge took the other side. The package name cannot be wrong.
 */
public enum TourEdition {

    /** {@code com.termux}: the Termux repositories and {@code pkg}. */
    TERMUX,
    /** {@code com.termux.launcher.nix}: nixpkgs and the nix profile. */
    NIX,
    /** {@code io.vaj.tl}: the demo edition, on its own apt repository but still {@code pkg}. */
    VAJ;

    public static final String NIX_PACKAGE_NAME = "com.termux.launcher.nix";
    public static final String VAJ_PACKAGE_NAME = "io.vaj.tl";

    /** The edition that ships under {@code packageName}; anything unknown is treated as Termux. */
    @NonNull
    public static TourEdition of(@Nullable String packageName) {
        if (NIX_PACKAGE_NAME.equals(packageName)) return NIX;
        if (VAJ_PACKAGE_NAME.equals(packageName)) return VAJ;
        return TERMUX;
    }

    /** Whether this edition installs with nix rather than {@code pkg}. */
    public boolean usesNixPackages() {
        return this == NIX;
    }
}
