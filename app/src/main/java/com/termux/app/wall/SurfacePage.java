package com.termux.app.wall;

import androidx.annotation.NonNull;

/**
 * A wall page whose picture is a {@code SurfaceView}: the Display place. The wall's motions — the
 * sink's dim, the planks' tilt — need a hardware layer and a rotation, and a surface takes
 * neither: it is composited outside the view hierarchy. Such a page stands a still copy of its
 * surface in for the length of a motion ({@link SurfaceStandIn}), and while it does it is a page
 * like the others. The wall finds it by its page view implementing this, so nothing outside the
 * wall and the page has to know.
 *
 * <p>Reduced motion never asks: there is no motion to stand in for.
 */
public interface SurfacePage {

    /**
     * A motion that moves this page begins — the hold's sink, a drag, a slide that carries it on
     * or off screen. {@code onReady} runs on the main thread once the page is still
     * ({@link #isStill}): at once when it already is or can be, later when its copy lands, never
     * when there is nothing to stand in. Asked again while still, it only runs {@code onReady}.
     */
    void holdStill(@NonNull Runnable onReady);

    /** Whether the page can take a layer and a tilt now, until {@link #releaseStill}. */
    boolean isStill();

    /**
     * The wall is at rest with this page's sink risen: the live surface comes back, and the
     * stand-in goes once it has drawn. The copy is kept a little while, for the page arriving
     * next time.
     */
    void releaseStill();

    /** The page is leaving the wall: nothing stands in, and nothing is kept. */
    void dropStill();
}
