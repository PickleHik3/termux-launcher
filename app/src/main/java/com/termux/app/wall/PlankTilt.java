package com.termux.app.wall;

/**
 * The planks: how far the pages a finger is dragging along the wall tip, each about its own
 * vertical centre line (Fancier Glass). The pages are sheets of glass the finger presses its
 * weight into as it drags them, and both the page it holds and the page arriving beside it give
 * the way a plank on a centre pivot gives: the side nearer the finger dips away from the viewer,
 * the far side comes forward. Held by its side border and pulled, the two pages fold into a
 * shallow valley at the seam under the finger; then the slide carries them off and they lie flat
 * again on the place that arrives.
 *
 * <p>How far each page tips is a function of its own position alone, never of a clock of its own:
 * it rises from flat at rest to {@link #MAX_TILT_DEG} half a width out and falls back to flat a
 * whole width out, where the page is off screen. The page leaving and the page arriving always
 * stand a width apart, so they tip by the same amount at every moment and read as one motion; a
 * drag tips them as far as the finger takes them, and the release's spring carries the same
 * curve on through the settle and lays them flat as the wall lands — nothing is handed over
 * between the drag and the slide.
 *
 * <p>Which way each page tips is its {@link #lean}: toward the point the finger pressed, which
 * stays where it pressed on the page it held and travels with that page after the finger lifts.
 * A page the finger is well to one side of leans all the way toward it; the page under a finger
 * held near its middle still leans, by {@link #CENTRE_LEAN}, toward the page it shares the screen
 * with, so both pages tip whichever border the hold was on.
 *
 * <p>On top of it, the press: a finger held on the left or right border pushes that side in by
 * up to {@link #HOLD_TILT_DEG} as the page sinks under it ({@link PageSink}), so the hold itself
 * reads as the page being pushed back. The press gives way to the travel's own tip over the
 * first {@link #HOLD_FADE_FRACTION} of a width; held on a side, both lean the same way.
 *
 * <p>Pure, so the mapping is testable without a view; the layout writes the answer to
 * {@code rotationY} on the frames it already moves the pages on.
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
     * How quickly a page leans all the way toward the finger: the finger a quarter width off the
     * page's centre line — half of the half width — is already all the way.
     */
    public static final float LEAN_GAIN = 2f;

    /**
     * How far the page under a finger held on its centre line leans toward the other page on
     * screen: pressed dead centre a plank only sinks, and the drag would read as one page tipping
     * beside one that does not.
     */
    public static final float CENTRE_LEAN = 0.6f;

    /**
     * The house camera distance ({@code DockPlankController}), in dp: far enough that a
     * {@link #MAX_TILT_DEG} tip reads as depth — the near edge grows by about two hundredths —
     * rather than as a keystone.
     */
    public static final float CAMERA_DISTANCE_DP = 2600f;

    private PlankTilt() {}

    /**
     * Which way, and how fully, a page standing {@code translationPx} from its rest leans toward
     * the finger's weight at {@code weightPx} — both measured the same way, from where a page's
     * centre stands at rest, so a weight equal to the translation is dead on the page's centre
     * line. -1 dips the left edge, +1 the right.
     */
    public static float lean(float translationPx, float weightPx, int widthPx) {
        if (widthPx <= 0 || Float.isNaN(translationPx) || Float.isNaN(weightPx)) return 0f;
        float toward = clamp(LEAN_GAIN * (weightPx - translationPx) / (widthPx / 2f), -1f, 1f);
        // The share the finger's side leaves undecided goes toward the page it shares the screen
        // with: the one on its right for a page moved left, and the other way round. At rest the
        // tip is flat whichever way that is.
        float partner = translationPx < 0f ? 1f : translationPx > 0f ? -1f : 0f;
        return toward + (1f - Math.abs(toward)) * CENTRE_LEAN * partner;
    }

    /**
     * The page's {@code rotationY} for standing {@code translationPx} from its rest on a wall
     * {@code widthPx} wide, leaning {@code lean} ({@link #lean}), with no press. Positive
     * {@code rotationY} takes the right edge away from the viewer, so a positive lean is a
     * positive angle.
     */
    public static float angleDeg(float translationPx, int widthPx, float lean) {
        return angleDeg(translationPx, widthPx, lean, 0, 0f);
    }

    /**
     * The same, with a finger held on a side border: {@code heldSide} is -1 for the left border,
     * +1 for the right and 0 for the top, the bottom or no press; {@code sink} is how far the page
     * has sunk ({@link PageSink}, 0 at rest and 1 fully down), clamped to that range here.
     */
    public static float angleDeg(float translationPx, int widthPx, float lean, int heldSide,
                                 float sink) {
        if (widthPx <= 0 || Float.isNaN(translationPx)) return 0f;
        float t = clamp(translationPx / widthPx, -1f, 1f);
        float leaning = Float.isNaN(lean) ? 0f : clamp(lean, -1f, 1f);
        float travel = (float) (MAX_TILT_DEG * leaning * Math.sin(Math.PI * Math.abs(t)));
        if (heldSide != 0 && !Float.isNaN(sink)) {
            float pressed = clamp(sink, 0f, 1f);
            float handOver = Math.max(0f, 1f - Math.abs(t) / HOLD_FADE_FRACTION);
            travel += Math.signum(heldSide) * HOLD_TILT_DEG * pressed * handOver;
        }
        return clamp(travel, -MAX_TILT_DEG, MAX_TILT_DEG);
    }

    private static float clamp(float value, float min, float max) {
        return value < min ? min : value > max ? max : value;
    }
}
