package com.termux.app.dock;

/**
 * A {@link DockLayout} for tests that need one without an activity behind it.
 *
 * <p>Lives in the dock package because {@link DockLayout.Builder} is package-private: the real one
 * is only ever built by {@code TermuxActivity#buildDockLayout}, which needs a laid-out dock.
 */
public final class TestDockLayouts {

    private TestDockLayouts() {}

    /** A capsule dock at the given density, inset from the screen edge like the shipped one. */
    public static DockLayout capsule(float density, int horizontalInsetPx) {
        DockLayout.Builder builder = new DockLayout.Builder();
        builder.capsule = true;
        builder.density = density;
        builder.horizontalInsetPx = horizontalInsetPx;
        builder.configuredCornerRadiusDp = -1;
        return builder.build();
    }
}
