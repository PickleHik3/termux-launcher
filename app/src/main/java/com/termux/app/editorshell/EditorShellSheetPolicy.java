package com.termux.app.editorshell;

import androidx.annotation.NonNull;

/**
 * Both editors' cards as sheets a finger can pull: which drags the sheet takes for itself rather
 * than leaving to the scrolling body, what one pull amounts to, and where the card settles when the
 * finger lets go. the editor sheet (retired) is the view that measures the finger and applies it.
 *
 * <p>The card rests at its collapsed height and can be pulled up to the expanded one; the distance
 * between the two is the pull's {@code travel}. A pull is measured as an offset in pixels above the
 * resting height: positive is the card grown by that much, negative is the card pushed down past
 * its resting height, which is how it is dismissed. Nothing here knows about views, springs or the
 * screen.
 */
public final class EditorShellSheetPolicy {

    private EditorShellSheetPolicy() {}

    /** Where the card goes once the finger lets go. */
    public enum Snap {
        /** Back to its resting height. */
        COLLAPSED,
        /** Grown to the top of the screen. */
        EXPANDED,
        /** Off the bottom edge: the editor closes the way Back closes it. */
        DISMISS
    }

    /**
     * How far below the resting height a pull has to go before letting go dismisses the card, as
     * a share of the card's own height. A firm pull, not a nudge: the card is over the thing it
     * is editing, and a finger that slipped must not close the editor.
     */
    public static final float DISMISS_FRACTION = 0.25f;

    /** The least a dismissing pull is, in dp, on a card too short for the fraction to mean much. */
    public static final float DISMISS_MIN_DP = 64f;

    /** A flick faster than this decides the direction on its own, in dp per second. */
    public static final float FLING_DP_PER_S = 900f;

    /** The offset a pull may reach: up to the travel, and down as far as the card is tall. */
    public static float clampOffsetPx(float offsetPx, float travelPx, float overshootPx) {
        float top = Math.max(0f, travelPx);
        float bottom = -Math.max(0f, overshootPx);
        return Math.max(bottom, Math.min(top, offsetPx));
    }

    /** The share of the travel a pull has taken, 0 at rest and 1 at the top; never below 0. */
    public static float expansionOf(float offsetPx, float travelPx) {
        if (travelPx <= 0f || offsetPx <= 0f) return 0f;
        return Math.min(1f, offsetPx / travelPx);
    }

    /** How far a pull has pushed the card below its resting height, in px; 0 while it has not. */
    public static float overshootOf(float offsetPx) {
        return Math.max(0f, -offsetPx);
    }

    /**
     * Where the card settles once the finger lets go.
     *
     * <p>Below the resting height the only question is whether the pull was firm enough to close:
     * past {@code dismissPx}, or flicked downward, and the card is dismissed; anything less
     * springs back to rest. Above it a flick decides the direction on its own, and a slower pull
     * goes to whichever of the two heights is nearer. A card with no travel — a landscape screen,
     * where the card already stands the full height — has only rest and dismissal to choose from.
     *
     * @param offsetPx         the pull's offset above the resting height, negative below it
     * @param travelPx         the distance between the resting and the expanded heights
     * @param velocityUpPxPerS the finger's speed as it let go, positive upward
     * @param flingPxPerS      the speed a flick has to reach to decide on its own
     * @param dismissPx        how far below rest a pull has to go before letting go closes
     */
    @NonNull
    public static Snap settle(float offsetPx, float travelPx, float velocityUpPxPerS,
                              float flingPxPerS, float dismissPx) {
        float fling = Math.max(0f, flingPxPerS);
        if (offsetPx < 0f) {
            boolean firm = -offsetPx >= Math.max(0f, dismissPx);
            boolean flicked = fling > 0f && velocityUpPxPerS <= -fling;
            return firm || flicked ? Snap.DISMISS : Snap.COLLAPSED;
        }
        if (travelPx <= 0f) return Snap.COLLAPSED;
        if (fling > 0f && Math.abs(velocityUpPxPerS) >= fling)
            return velocityUpPxPerS > 0f ? Snap.EXPANDED : Snap.COLLAPSED;
        return offsetPx >= travelPx / 2f ? Snap.EXPANDED : Snap.COLLAPSED;
    }

    /** The far end of a tap on the handle: expanded from rest, and rest from anywhere else. */
    @NonNull
    public static Snap toggled(float expansion, float travelPx) {
        if (travelPx <= 0f) return Snap.COLLAPSED;
        return expansion < 0.5f ? Snap.EXPANDED : Snap.COLLAPSED;
    }

    /**
     * Whether a vertical drag belongs to the sheet rather than to the body scrolling inside it —
     * the rule Material's bottom sheet follows, so the body is one list that the card grows around
     * rather than a window the finger has to find its way into.
     *
     * <p>A drag that starts outside the body (on the handle, the header, the card's own air) always
     * moves the sheet. One that starts on the body moves it in two cases only: pulled up while the
     * card still has room to grow, which it does before the list scrolls; and pulled down while the
     * list is already at its top, which brings the card down, and past rest, closes it. Everything
     * else scrolls the list.
     *
     * @param onBody        whether the finger went down on the scrolling body
     * @param pulledUpPx    how far the finger has moved, positive upward
     * @param expansion     how far the card is grown, 0 at rest and 1 all the way up
     * @param travelPx      how far the card can grow at all
     * @param bodyAtTop     whether the body is scrolled to its top
     */
    public static boolean claimsDrag(boolean onBody, float pulledUpPx, float expansion,
                                     float travelPx, boolean bodyAtTop) {
        if (pulledUpPx == 0f)
            return false;
        if (!onBody)
            return true;
        if (pulledUpPx > 0f)
            return travelPx > 0f && expansion < 1f;
        return bodyAtTop;
    }

    // ------------------------------------------------------------------------------ the heights

    /**
     * How tall the card stands at rest: as tall as what it holds, and no taller than its resting
     * height or the room it has. A card whose whole content fits under the resting height is simply
     * that tall, and has no travel.
     *
     * @param naturalPx  the card's height with nothing cut off: its chrome and its whole body
     * @param restPx     the resting height asked for; 0 or less for "as tall as the room"
     * @param expandedPx the pulled-up height asked for; 0 or less for "as tall as the room"
     * @param ceilingPx  the room the card's parent gives it; 0 or less for unbounded
     */
    public static int restHeightPx(int naturalPx, int restPx, int expandedPx, int ceilingPx) {
        int top = topPx(expandedPx, ceilingPx);
        int rest = restPx > 0 ? Math.min(restPx, top) : top;
        return Math.max(0, Math.min(naturalPx, rest));
    }

    /** How tall the card stands pulled all the way up: never shorter than at rest. */
    public static int expandedHeightPx(int naturalPx, int restPx, int expandedPx, int ceilingPx) {
        return Math.max(restHeightPx(naturalPx, restPx, expandedPx, ceilingPx),
            Math.max(0, Math.min(naturalPx, topPx(expandedPx, ceilingPx))));
    }

    /** The card's height part-way through a pull; {@code expansion} outside 0..1 is clamped. */
    public static int heightPx(int naturalPx, int restPx, int expandedPx, int ceilingPx,
                               float expansion) {
        int rest = restHeightPx(naturalPx, restPx, expandedPx, ceilingPx);
        int expanded = expandedHeightPx(naturalPx, restPx, expandedPx, ceilingPx);
        float at = Float.isNaN(expansion) ? 0f : Math.max(0f, Math.min(1f, expansion));
        return rest + Math.round(at * (expanded - rest));
    }

    private static int topPx(int expandedPx, int ceilingPx) {
        int ceiling = ceilingPx > 0 ? ceilingPx : Integer.MAX_VALUE;
        return expandedPx > 0 ? Math.min(expandedPx, ceiling) : ceiling;
    }
}
