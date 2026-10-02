package com.termux.app.chrome.wallpaper;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** The built-in generated backgrounds, in picker order. */
public final class AnimatedWallpapers {

    private static final List<AnimatedWallpaper> ALL = Collections.unmodifiableList(Arrays.<AnimatedWallpaper>asList(
        new Mesh(), new Aurora(), new Tide(), new Rain(),
        new Contour(), new Drift(), new Lava(), new Silk(), new Caustics(), new Chrome()));

    private AnimatedWallpapers() {}

    public static List<AnimatedWallpaper> all() {
        return ALL;
    }

    /** The background with this id, or null when unknown (a restore from a newer build, say). */
    public static AnimatedWallpaper byId(String id) {
        if (id == null) return null;
        for (AnimatedWallpaper w : ALL) if (w.id().equals(id)) return w;
        return null;
    }
}
