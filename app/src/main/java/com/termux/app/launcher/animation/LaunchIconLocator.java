package com.termux.app.launcher.animation;

import android.graphics.Rect;
import android.graphics.RectF;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Finds, host-side, the on-screen icon of an app among the {@link LaunchIconSource} views of a
 * window. Runs once per handoff, never per frame.
 */
public final class LaunchIconLocator {

    private LaunchIconLocator() {}

    /**
     * Screen bounds of the first visible icon of {@code packageName}, in the drawer or on Home
     * as {@code drawer} says, or null. A view scrolled off its page is skipped.
     */
    @Nullable
    public static RectF find(@Nullable View root, @NonNull String packageName, boolean drawer) {
        if (root == null) return null;
        return search(root, packageName, drawer, new Rect(), new int[2]);
    }

    @Nullable
    private static RectF search(@NonNull View view, @NonNull String pkg, boolean drawer,
                                @NonNull Rect visible,
                                @NonNull int[] location) {
        if (view.getVisibility() != View.VISIBLE) return null;
        if (view instanceof LaunchIconSource
            && ((LaunchIconSource) view).isDrawerCell() == drawer
            && ((LaunchIconSource) view).launchesPackage(pkg)) {
            View icon = ((LaunchIconSource) view).launchIconView();
            if (icon != null && icon.isAttachedToWindow() && icon.getWidth() > 0
                && icon.getHeight() > 0 && icon.getGlobalVisibleRect(visible)
                && visible.width() >= icon.getWidth() / 2
                && visible.height() >= icon.getHeight() / 2) {
                icon.getLocationOnScreen(location);
                return new RectF(location[0], location[1],
                    location[0] + icon.getWidth(), location[1] + icon.getHeight());
            }
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                RectF found = search(group.getChildAt(i), pkg, drawer, visible, location);
                if (found != null) return found;
            }
        }
        return null;
    }
}
