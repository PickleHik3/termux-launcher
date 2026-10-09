package com.termux.app.statusbar;

import androidx.annotation.NonNull;

/**
 * The pinned card's gesture arithmetic: which axis a drag belongs to, and whether a sideways
 * release dismisses the card or springs it back. Pure, so it tests on the JVM.
 *
 * <p>The axis is decided once, when the finger first leaves the touch slop, and held for the
 * rest of the stream. Sideways is the card's swipe-to-dismiss; up or down is the run's scroll
 * (only while there is something to scroll) or, failing that, nobody's — the bar's fold takes it.
 */
public final class PinnedSwipe {

    public enum Axis { UNDECIDED, HORIZONTAL, VERTICAL }

    /** How far across the card a release must be to dismiss it without a fling. */
    public static final float COMMIT_FRACTION = .35f;

    /**
     * A fling this many times the platform's minimum fling velocity, sideways and faster sideways
     * than up or down, dismisses from any distance, as long as it points the way the card moved.
     */
    public static final float FLING_FACTOR = 6f;

    private PinnedSwipe() {}

    /** The axis once the drag is past the slop; {@link Axis#UNDECIDED} until then. */
    @NonNull
    public static Axis decide(float dx, float dy, float touchSlop) {
        float ax = Math.abs(dx);
        float ay = Math.abs(dy);
        if (ax <= touchSlop && ay <= touchSlop) return Axis.UNDECIDED;
        return ax > ay ? Axis.HORIZONTAL : Axis.VERTICAL;
    }

    /** Whether a sideways release at {@code offsetPx} and {@code velocityX} dismisses the card. */
    public static boolean commits(float offsetPx, float widthPx, float velocityX, float velocityY,
                                  float minFlingVelocity) {
        if (widthPx <= 0f) return false;
        if (Math.abs(offsetPx) >= widthPx * COMMIT_FRACTION) return true;
        float fling = minFlingVelocity * FLING_FACTOR;
        return Math.abs(velocityX) >= fling && Math.abs(velocityX) > Math.abs(velocityY)
            && offsetPx != 0f && Math.signum(velocityX) == Math.signum(offsetPx);
    }

    /** The card's opacity while it follows the finger: fading out towards a full width. */
    public static float alphaFor(float offsetPx, float widthPx) {
        if (widthPx <= 0f) return 1f;
        return Math.max(0f, Math.min(1f, 1f - Math.abs(offsetPx) / widthPx));
    }
}
