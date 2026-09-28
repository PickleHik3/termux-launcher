package com.termux.app.wall;

/**
 * The plank: how far the page a finger is dragging along the wall tips about its vertical centre
 * line (Fancier Glass, minimal mode). The pane is a sheet of glass the finger presses sideways,
 * and it gives the way a plank on a centre pivot gives — the side it is pushed toward dips away
 * from the viewer, the other side comes forward — then flows off with the slide and lies flat
 * again on the place that arrives.
 *
 * <p>The angle is a function of the page's own position alone, never of a clock of its own: it
 * rises from flat at rest to {@link #MAX_TILT_DEG} half a width out and falls back to flat a whole
 * width out, where the page is off screen. So a drag tips it as far as the finger takes it, a
 * release short of the commit springs it flat on the wall's own settle curve as the wall comes
 * back, and a committed release tips it on through its peak and flat again as the wall carries
 * it out — one motion, the wall's, with nothing to hand over between the drag and the slide.</p>
 *
 * <p>Pure, so the mapping is testable without a view; the layout writes the answer to
 * {@code rotationY} on the frames it already moves the page on.</p>
 */
public final class PlankTilt {

    /**
     * The peak, half a width out. Deliberately more than the press rigs' degree or three: those
     * answer a finger resting on a surface, where more reads as the screen keeling over; this is
     * the motion of a page change, and the tip is the point.
     */
    public static final float MAX_TILT_DEG = 12f;

    /**
     * The house camera distance ({@code DockPlankController}), in dp: far enough that a
     * {@link #MAX_TILT_DEG} tip reads as depth — the near edge grows by about a hundredth and a
     * half — rather than as a keystone.
     */
    public static final float CAMERA_DISTANCE_DP = 2600f;

    private PlankTilt() {}

    /**
     * The page's {@code rotationY} for standing {@code translationPx} from its rest on a wall
     * {@code widthPx} wide. Positive {@code rotationY} takes the right edge away from the viewer,
     * so a page moved left (negative travel, the way to the place on the right) dips its left
     * edge: the leading side goes in.
     */
    public static float angleDeg(float translationPx, int widthPx) {
        if (widthPx <= 0 || Float.isNaN(translationPx)) return 0f;
        float t = Math.max(-1f, Math.min(1f, translationPx / widthPx));
        return (float) (MAX_TILT_DEG * Math.sin(Math.PI * t));
    }
}
