package com.termux.app.launcher.drawer;

/**
 * How much of Home shows while the drawer plane covers it. The plane is translucent, so a Home
 * shortcut's label would otherwise print on top of the drawer's own labels; Home fades out as the
 * plane opens and comes back as it closes or an open is let go of.
 */
public final class AppDrawerHomeCoverPolicy {
    private AppDrawerHomeCoverPolicy() {}

    /** @param progress 0 = dock, 1 = full drawer; out-of-range values are clamped. */
    public static float homeAlpha(float progress) {
        return 1f - AppDrawerTransitionGeometry.clamp01(progress);
    }
}
