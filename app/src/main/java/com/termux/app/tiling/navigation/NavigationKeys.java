package com.termux.app.tiling.navigation;

/**
 * Where a hardware key goes while navigation mode is on (#433; "windows take no input"): no window
 * takes it. Leader chords are asked before this and still run. A system key (Back, volume, media)
 * goes on as ever, so Back leaves the mode; a layer open over the page (the bar's search, the
 * palette) keeps its keys.
 *
 * <p>Pure: the activity asks it for each key.
 */
public final class NavigationKeys {

    private NavigationKeys() {}

    /** Whether a key is taken and dropped: {@code navigating}, the key {@code system}, a {@code layerOpen}. */
    public static boolean swallows(boolean navigating, boolean system, boolean layerOpen) {
        return navigating && !system && !layerOpen;
    }
}
