package com.termux.app.surfaces.rows;

import androidx.annotation.NonNull;

/**
 * Who a touch on a vertical strip belongs to, inside the card's vertical scroll (editor-redesign
 * §9: the sliders scroll the card by mistake). The strip's axis is the scroll's, so a finger that
 * leaves the touch slop before it has rested {@link #HOLD_TO_CLAIM_MS} is scrolling the card and
 * the strip lets it go untouched; a finger that rests that long inside the slop means the strip,
 * and a tap inside the slop sets the value where it lands.
 *
 * <p>Today's glass strips ({@code LegendSlider} with its handle grabbing) also take a touch on the
 * handle at once. The new strips ({@link StripRow}) do not: there, a swipe that starts anywhere on
 * the strip, its handle included, scrolls the card.</p>
 *
 * <p>Pure: no views, held by JVM tests.</p>
 */
public final class StripTouchPolicy {

    private StripTouchPolicy() {}

    /**
     * How long a finger has to rest inside the slop before the strip takes the gesture from the
     * card's scroll.
     */
    public static final long HOLD_TO_CLAIM_MS = 150L;

    /** What a touch on the strip has decided so far. */
    public enum Decision {
        /** The strip takes the gesture: it tracks the finger and the scroll is held off. */
        CLAIM,
        /** The gesture is the card's scroll; the strip lets go of it untouched. */
        SCROLL,
        /** Not decided yet: the finger is inside the slop and the hold has not elapsed. */
        WAIT
    }

    /**
     * @param onHandle    the touch went down on the handle's touch target
     * @param handleGrabs a touch on the handle claims at once (today's glass strips)
     * @param inScroller  an ancestor scrolls; outside one every touch claims at once
     * @param movedPx     how far the finger has travelled from where it went down, any direction
     * @param slopPx      the touch slop
     * @param heldMs      how long since it went down
     */
    @NonNull
    public static Decision decide(boolean onHandle, boolean handleGrabs, boolean inScroller,
                                  float movedPx, float slopPx, long heldMs) {
        if (!inScroller || (onHandle && handleGrabs))
            return Decision.CLAIM;
        if (movedPx > slopPx && heldMs < HOLD_TO_CLAIM_MS)
            return Decision.SCROLL;
        if (heldMs >= HOLD_TO_CLAIM_MS)
            return Decision.CLAIM;
        return Decision.WAIT;
    }
}
