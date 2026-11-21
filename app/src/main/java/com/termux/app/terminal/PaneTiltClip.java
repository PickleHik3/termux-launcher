package com.termux.app.terminal;

/**
 * Where a pane frame's rounded clip is applied while its press rig tips it.
 *
 * <p>The GPU clips a round rect for free only while the matrix it is drawn under keeps rects
 * rects. A pressed glass pane is turned a degree out of the screen's plane on its own frame, so
 * its outline clip and the band clip inside it (the rim's inner edge, {@link PaneContentFrame})
 * were each rasterized on the CPU into a pane-sized mask and uploaded, every frame of the press
 * and of any swipe that started on the pane.
 *
 * <p>So while it is tipped the frame draws into its own layer, flat, and clips to its outline in
 * its own draw (inside the layer) instead of through the view's outline, which would apply it
 * under the tilt when the layer is composited. The layer is then drawn tipped with no clip at
 * all: its corners are already transparent. At rest the frame clips through its outline as it
 * always has, with no layer. A frame that clips to no shape takes no layer either: nothing of it
 * is cut to a round rect, and a layer would cut off whatever it draws past its bounds.
 */
final class PaneTiltClip {

    private PaneTiltClip() {}

    /** Whether the frame draws into its own layer: it clips to its shape and is turned off flat. */
    static boolean layered(boolean clipToShape, float rotation, float rotationX, float rotationY) {
        return clipToShape && (rotation != 0f || rotationX != 0f || rotationY != 0f);
    }

    /** Whether the view's outline clips the frame: its shape, while it is not layered. */
    static boolean clipsByOutline(boolean clipToShape, boolean layered) {
        return clipToShape && !layered;
    }

    /** Whether the frame clips its own drawing to its outline: its shape, while layered. */
    static boolean clipsInDraw(boolean clipToShape, boolean layered) {
        return clipToShape && layered;
    }
}
