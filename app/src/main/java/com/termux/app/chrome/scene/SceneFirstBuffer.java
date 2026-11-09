package com.termux.app.chrome.scene;

import androidx.annotation.Nullable;

/**
 * The colour of the scene surface's first buffer, swapped before any shader compile or video
 * prepare so the window's first-layout sync never waits on them. Pure.
 *
 * <p>Before this buffer existed the layer showed nothing and the window's hole showed the system
 * wallpaper; its primary colour is the closest an opaque buffer gets to that look. Unknown colours
 * give opaque black.</p>
 */
final class SceneFirstBuffer {

    static final int BLACK = 0xff000000;

    private SceneFirstBuffer() {}

    /** {@code wallpaperPrimary} made opaque, or {@link #BLACK} when it is null. */
    static int colour(@Nullable Integer wallpaperPrimary) {
        return wallpaperPrimary == null ? BLACK : (wallpaperPrimary | 0xff000000);
    }
}
