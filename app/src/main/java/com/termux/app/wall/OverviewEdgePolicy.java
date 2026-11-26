package com.termux.app.wall;

/**
 * Overview before the first page (#356): where the first page and Overview stand while a finger
 * swipes between them, and where that swipe lands. Overview is not a page of the wall; it stands
 * one width before the first page, so the page-switch swipe continued past the first page brings
 * it in under the finger, and a swipe the other way from Overview brings the first page back.
 *
 * <p>Both stand on one number, the <em>reveal</em>: how far Overview has come in from the left
 * edge, 0 to the wall's width. The first page stands that far right of its rest and Overview's
 * right edge touches the page's left edge, so the two move as one wall. The drag (predicted) and
 * the settle (landed) both read this class; the wall applies the answer. Pure and tested.
 */
public final class OverviewEdgePolicy {

    /** The finger pulls Overview in past the first page. */
    public static final int PULL = 1;
    /** The finger swipes the other way from Overview, back to the first page. */
    public static final int RETURN = -1;

    private OverviewEdgePolicy() {}

    /** The reveal the swipe starts from: none for a pull, all of it for a return. */
    public static float startReveal(int swipe, int widthPx) {
        return swipe == RETURN ? Math.max(0, widthPx) : 0f;
    }

    /**
     * The reveal for a finger that has travelled {@code dxPx} (positive to the right) since the
     * swipe began: one to one with the finger, the wall's own pace, and never past either rest.
     */
    public static float reveal(int swipe, float dxPx, int widthPx) {
        if (widthPx <= 0 || Float.isNaN(dxPx)) return startReveal(swipe, widthPx);
        float at = startReveal(swipe, widthPx) + dxPx;
        return Math.max(0f, Math.min(widthPx, at));
    }

    /** The reveal a settled swipe rests at: the whole width with Overview open, none without. */
    public static float restReveal(boolean open, int widthPx) {
        return open ? Math.max(0, widthPx) : 0f;
    }

    /** How far right of its rest the first page stands at {@code revealPx}. */
    public static float pageOffset(float revealPx) {
        return revealPx;
    }

    /**
     * Overview's translation at {@code revealPx}: one width left of the first page, so its right
     * edge is the page's left edge. 0 with Overview open, which is also where every other way in
     * puts it, with no slide.
     */
    public static float overviewTranslationX(float revealPx, int widthPx) {
        return pageOffset(revealPx) - Math.max(0, widthPx);
    }

    /**
     * How much of the chrome shows while Overview comes in over it, 1 to 0: it goes as Overview
     * covers the screen, so nothing under the sheet shows through it and then blinks out when
     * Overview opens.
     */
    public static float chromeShown(float revealPx, int widthPx) {
        if (widthPx <= 0 || Float.isNaN(revealPx)) return 1f;
        return 1f - Math.max(0f, Math.min(1f, revealPx / widthPx));
    }

    /**
     * Whether the released swipe lands with Overview open: the wall's own rule ({@link
     * PaneWallPolicy#settle}), read as if Overview were the page on the left of the first page, so
     * distance or a fling commit exactly as they would between two pages and a swipe short of
     * either springs back.
     *
     * @param revealPx          the reveal at the release, as {@link #reveal} gave it
     * @param velocityPxPerSec  release velocity, positive to the right
     * @param leadPx            finger travel before the drag was claimed (the border's slop)
     */
    public static boolean settlesOpen(int swipe, float revealPx, float velocityPxPerSec,
                                      int widthPx, float density, float leadPx) {
        // The wall's offset from the rest the swipe started at: positive shows the left page.
        float offset = revealPx - startReveal(swipe, widthPx);
        int steps = PaneWallPolicy.settle(offset, velocityPxPerSec, widthPx, density, true, true,
            leadPx);
        if (swipe == RETURN) return steps != 1;
        return steps == -1;
    }
}
