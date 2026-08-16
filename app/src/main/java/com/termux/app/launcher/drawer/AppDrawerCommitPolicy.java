package com.termux.app.launcher.drawer;

import androidx.annotation.NonNull;

/**
 * Pure release policy: what a lifted finger means for the drawer transition.
 *
 * <p>Both directions are the same rule read against the fraction of the gesture actually
 * travelled. Opening, that fraction is {@code progress}; closing, it is {@code 1 - progress}.
 * The finger direction does not flip between them — the drawer is pulled down out of the dock and
 * pushed back down to dismiss — so downward always means "carry on" and upward always means
 * "put it back", whichever way the transition is running.
 *
 * <p>The fling window exists because a flick is an instruction, not a measurement: 15% of travel
 * thrown hard is unambiguous, while the same 15% released slowly is someone who changed their
 * mind. An upward fling wins over every other rule, including a progress past the halfway mark,
 * since it is the only gesture that can mean "abort" once the plane is most of the way there.
 */
public final class AppDrawerCommitPolicy {

    public enum Decision { COMMIT_OPEN, COMMIT_CLOSE, CANCEL }

    /** Which way the transition was running when the finger lifted. */
    public enum Direction { OPENING, CLOSING }

    /** Past this fraction of travel a plain release commits. */
    public static final float COMMIT_PROGRESS = 0.5f;
    /** Speed at which a release counts as a fling rather than a measurement, px/s. */
    public static final float FLING_VELOCITY_PX_PER_SEC = 900f;
    /** A fling still needs this much travel behind it, so a stray flick cannot open the drawer. */
    public static final float FLING_MIN_PROGRESS = 0.12f;

    private AppDrawerCommitPolicy() {}

    /**
     * @param progress            0 = dock, 1 = full drawer, in both directions
     * @param velocityPxPerSec    vertical release velocity, positive downwards
     * @param direction           the transition the finger was driving
     */
    @NonNull
    public static Decision decide(float progress, float velocityPxPerSec,
                                  @NonNull Direction direction) {
        float p = AppDrawerTransitionGeometry.clamp01(progress);
        float travelled = direction == Direction.OPENING ? p : 1f - p;
        Decision commit = direction == Direction.OPENING
            ? Decision.COMMIT_OPEN : Decision.COMMIT_CLOSE;

        // Thrown back the way it came: abort no matter how far it got.
        if (velocityPxPerSec <= -FLING_VELOCITY_PX_PER_SEC) return Decision.CANCEL;
        if (velocityPxPerSec >= FLING_VELOCITY_PX_PER_SEC && travelled >= FLING_MIN_PROGRESS)
            return commit;
        if (travelled >= COMMIT_PROGRESS) return commit;
        return Decision.CANCEL;
    }
}
