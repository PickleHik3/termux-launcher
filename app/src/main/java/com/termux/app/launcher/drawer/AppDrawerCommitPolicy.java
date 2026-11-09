package com.termux.app.launcher.drawer;

import androidx.annotation.NonNull;

import com.termux.app.wall.PagePolicy;

/**
 * Pure release policy: what a lifted finger means for the drawer transition.
 *
 * <p>Both directions are the same rule read against the fraction of the gesture actually
 * travelled. Opening, that fraction is {@code progress}; closing, it is {@code 1 - progress}.
 * The finger direction does not flip between them — the drawer is pulled down out of the dock and
 * pushed back down to dismiss — so downward always means "carry on" and upward always means
 * "put it back", whichever way the transition is running.
 *
 * <p>The rule is the one page rule ({@link PagePolicy}), read with the whole travel as the page:
 * past {@link #COMMIT_PROGRESS} a plain release commits, a flick at the pager's speed commits from
 * a barely started drag, and a finger turning back puts the drawer back however far it got. A
 * fling still needs {@link #FLING_MIN_PROGRESS} behind it, the drawer's own input to the rule:
 * 15% of travel thrown hard is an instruction, a stray flick off the dock is not.
 */
public final class AppDrawerCommitPolicy {

    public enum Decision { COMMIT_OPEN, COMMIT_CLOSE, CANCEL }

    /** Which way the transition was running when the finger lifted. */
    public enum Direction { OPENING, CLOSING }

    /** Past this fraction of travel a plain release commits: {@link PagePolicy}'s. */
    public static final float COMMIT_PROGRESS = PagePolicy.COMMIT_FRACTION;
    /** A fling still needs this much travel behind it, so a stray flick cannot open the drawer. */
    public static final float FLING_MIN_PROGRESS = 0.12f;

    private AppDrawerCommitPolicy() {}

    /**
     * @param progress            0 = dock, 1 = full drawer, in both directions
     * @param velocityPxPerSec    vertical release velocity, positive downwards
     * @param direction           the transition the finger was driving
     * @param density             px per dp, which the rule's speeds are read in
     */
    @NonNull
    public static Decision decide(float progress, float velocityPxPerSec,
                                  @NonNull Direction direction, float density) {
        float p = AppDrawerTransitionGeometry.clamp01(progress);
        float travelled = direction == Direction.OPENING ? p : 1f - p;
        Decision commit = direction == Direction.OPENING
            ? Decision.COMMIT_OPEN : Decision.COMMIT_CLOSE;
        // Downward is "carry on" both ways, so it is the travel's own sign; a turn the other way
        // (a fling back from a barely started drag) has no page there and puts the drawer back.
        return PagePolicy.turn(travelled, velocityPxPerSec, 1f, density, FLING_MIN_PROGRESS) > 0
            ? commit : Decision.CANCEL;
    }
}
