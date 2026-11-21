package com.termux.app.launcher.animation;

import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * A view on screen that stands for an app's launch icon (a Home icon, a Home folder holding the
 * app, a drawer cell). {@link LaunchIconLocator} finds the one an app is leaving to when the
 * system asks where its window should shrink to.
 */
public interface LaunchIconSource {
    /** Whether this view is the icon of {@code packageName}, or holds it. */
    boolean launchesPackage(@NonNull String packageName);

    /** True for a drawer cell, false for anything on a Home page. */
    boolean isDrawerCell();

    /** The view whose screen bounds the window shrinks into, or null while it has none. */
    @Nullable View launchIconView();
}
