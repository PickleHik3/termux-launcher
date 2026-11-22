package com.termux.app.chrome;

import android.view.SurfaceView;
import android.view.View;

/**
 * The one rule for what a glass backdrop or page-layer recording may hold. A SurfaceView's
 * RenderNode carries a position listener, so whichever renderer draws it moves the real surface to
 * where that renderer draws it (the capture's quarter scale puts the wallpaper at the window's
 * top-left for a frame). A recording therefore never draws one by reference; the skipped view
 * was a transparent hole in it, and its look comes from a stand-in (the scene's live frame, the
 * pane's glass slab). Main thread only; no allocation.
 */
public final class BackdropRecording {

    private static int sDepth;

    private BackdropRecording() {}

    /** Whether a recording may draw {@code view} by reference. */
    public static boolean mayRecord(View view) {
        return !(view instanceof SurfaceView);
    }

    /** Opens a stretch in which a recording draws a view subtree; pair with {@link #end}. */
    public static void begin() {
        sDepth++;
    }

    public static void end() {
        if (sDepth > 0) sDepth--;
    }

    /** Whether {@code child} must be left out of the draw now going on: inside a recording and not recordable. */
    public static boolean skips(View child) {
        return sDepth > 0 && !mayRecord(child);
    }
}
