package com.termux.app.wall;

/**
 * The plank: how far the page a finger is dragging along the wall tips about its vertical centre
 * line (Fancier Glass). The pane is a sheet of glass the finger presses sideways, and it gives
 * the way a plank on a centre pivot gives — the side it is pushed toward dips away from the
 * viewer, the other side comes forward — then flows off with the slide and lies flat again on the
 * place that arrives.
 *
 * <p>The travel's angle is a function of the page's own position alone, never of a clock of its
 * own: it rises from flat at rest to {@link #MAX_TILT_DEG} half a width out and falls back to
 * flat a whole width out, where the page is off screen. So a drag tips it as far as the finger
 * takes it, a release short of the commit springs it flat on the wall's own settle curve as the
 * wall comes back, and a committed release tips it on through its peak and flat again as the wall
 * carries it out — one motion, the wall's, with nothing to hand over between the drag and the
 * slide.</p>
 *
 * <p>On top of it, the press: a finger held on the left or right border pushes that side in by
 * up to {@link #HOLD_TILT_DEG} as the page sinks under it ({@link PageSink}), so the hold itself
 * reads as the page being pushed back. The press gives way to the travel's own tip over the
 * first {@link #HOLD_FADE_FRACTION} of a width, so however the finger then goes, the plank ends
 * up leading with the side it is moving toward.</p>
 *
 * <p>Pure, so the mapping is testable without a view; the layout writes the answer to
 * {@code rotationY} on the frames it already moves the page on.</p>
 */
public final class PlankTilt {

    /**
     * The peak, half a width out. Deliberately more than the press rigs' degree or three: those
     * answer a finger resting on a surface, where more reads as the screen keeling over; this is
     * the motion of a page change on a page already pushed back, and the tip is the point.
     */
    public static final float MAX_TILT_DEG = 18f;

    /** How far a held side border pushes its side in, at rest, once the page has fully sunk. */
    public static final float HOLD_TILT_DEG = 6f;

    /** The fraction of a width over which the press hands over to the travel's tip. */
    public static final float HOLD_FADE_FRACTION = 0.25f;

    /**
     * The house camera distance ({@code DockPlankController}), in dp: far enough that a
     * {@link #MAX_TILT_DEG} tip reads as depth — the near edge grows by about two hundredths —
     * rather than as a keystone.
     */
    public static final float CAMERA_DISTANCE_DP = 2600f;

    private PlankTilt() {}

    /**
     * The page's {@code rotationY} for standing {@code translationPx} from its rest on a wall
     * {@code widthPx} wide, with no press. Positive {@code rotationY} takes the right edge away
     * from the viewer, so a page moved left (negative travel, the way to the place on the right)
     * dips its left edge: the leading side goes in.
     */
    public static float angleDeg(float translationPx, int widthPx) {
        return angleDeg(translationPx, widthPx, 0, 0f);
    }

    /**
     * The same, with a finger held on a side border: {@code heldSide} is -1 for the left border,
     * +1 for the right and 0 for the top, the bottom or no press; {@code sink} is how far the page
     * has sunk ({@link PageSink}, 0 at rest and 1 fully down), clamped to that range here.
     */
    public static float angleDeg(float translationPx, int widthPx, int heldSide, float sink) {
        if (widthPx <= 0 || Float.isNaN(translationPx)) return 0f;
        float t = Math.max(-1f, Math.min(1f, translationPx / widthPx));
        float travel = (float) (MAX_TILT_DEG * Math.sin(Math.PI * t));
        if (heldSide == 0 || Float.isNaN(sink)) return travel;
        float pressed = Math.max(0f, Math.min(1f, sink));
        float handOver = Math.max(0f, 1f - Math.abs(t) / HOLD_FADE_FRACTION);
        return travel + Math.signum(heldSide) * HOLD_TILT_DEG * pressed * handOver;
    }
}
