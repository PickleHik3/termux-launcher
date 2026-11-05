package com.termux.app.chrome;

/**
 * What a live-glass recording draws for one view beneath a surface while a wallpaper scene is on.
 * The scene is drawn on its own SurfaceView, whose pixels a recording cannot hold, so the recording
 * draws the scene's live frame in the SurfaceView's place and leaves out the photo wallpaper under
 * it: one ground, never both. Without a scene every view is drawn as it always was. Pure.
 */
final class LiveGround {

    enum Draw {
        /** The view draws itself, as always. */
        CHILD,
        /** The view is left out of the recording. */
        SKIP,
        /** The scene's live frame is drawn over the view's bounds in its place. */
        SCENE_FRAME
    }

    private LiveGround() {}

    /**
     * @param sceneLive      a live scene frame is current
     * @param sceneLayer     the view is the scene's layer
     * @param photoBackdrop  the view is the launcher-drawn photo wallpaper
     */
    static Draw choose(boolean sceneLive, boolean sceneLayer, boolean photoBackdrop) {
        if (!sceneLive) return Draw.CHILD;
        if (sceneLayer) return Draw.SCENE_FRAME;
        if (photoBackdrop) return Draw.SKIP;
        return Draw.CHILD;
    }
}
