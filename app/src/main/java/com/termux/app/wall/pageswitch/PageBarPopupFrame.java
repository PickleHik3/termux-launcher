package com.termux.app.wall.pageswitch;

/**
 * Where the bar the page switcher key pops up stands, in its layer's px: one more card of the
 * stack, raised over the extra-keys card. Its sides are the card's, its drawn bar
 * ({@link PageBarLayout#raisedSlabHeightDp}) stands the stack's row gap above the card (the gap
 * the card keeps from the keyboard: Floating's air, Docked's none), and its corners follow the
 * card's rule at the bar's height (Corners capped at half the bar under Floating, square under
 * Docked), capped at a capsule; the host answers the gap and the rule
 * ({@link PageBarController.Host}). With no card to read it falls back
 * to the layer less {@link PageBarLayout#SIDE_INSET_DP} above the key's band. Pure; filled once
 * per opening, never per frame.
 */
public final class PageBarPopupFrame {

    /** The drawn bar's left and width in the layer: the touch row's too. */
    public float slabLeftPx;
    public float slabWidthPx;
    /** The bar's corner radius. */
    public float radiusPx;
    /** The air between the drawn bar's bottom and the card's top. */
    public int gapPx;
    /** The pop-up view's top in the layer: the touch row's top. */
    public int viewTopPx;
    /** The pop-up view's height: the touch row's. */
    public int viewHeightPx;

    /**
     * @param layerWidthPx the layer's width
     * @param card         whether the extra-keys card was read; else {@code anchorTopPx} is the band's top
     * @param cardLeftPx   the card's left in the layer
     * @param cardRightPx  the card's right in the layer
     * @param anchorTopPx  the card's top (or the band's) in the layer
     * @param rowGapPx     the stack's row gap: the air the card keeps from the keyboard's card
     * @param cardRadiusPx the card's corner rule answered for the bar's height
     *                     ({@link #slabHeightPx})
     */
    public PageBarPopupFrame set(float density, int layerWidthPx, boolean card, int cardLeftPx,
                                 int cardRightPx, int anchorTopPx, int rowGapPx,
                                 float cardRadiusPx) {
        if (card && cardRightPx > cardLeftPx) {
            slabLeftPx = cardLeftPx;
            slabWidthPx = cardRightPx - cardLeftPx;
        } else {
            float inset = PageBarLayout.SIDE_INSET_DP * density;
            slabLeftPx = inset;
            slabWidthPx = Math.max(0f, layerWidthPx - 2f * inset);
        }
        radiusPx = Math.max(0f, Math.min(cardRadiusPx, slabHeightPx(density) / 2f));
        gapPx = Math.max(0, rowGapPx);
        viewHeightPx = Math.round(PageBarLayout.bandHeightDp() * density);
        viewTopPx = Math.max(0, Math.round(anchorTopPx - gapPx
            - PageBarLayout.raisedSlabBottomDp() * density));
        return this;
    }

    /** The drawn bar's height, whole px: what the card's corner rule is asked about. */
    public static int slabHeightPx(float density) {
        return Math.round(PageBarLayout.raisedSlabHeightDp() * density);
    }

    /** The drawn bar's top in the layer. */
    public float slabTopPx(float density) {
        return viewTopPx + (PageBarLayout.raisedSlabBottomDp() - PageBarLayout.raisedSlabHeightDp())
            * density;
    }

    /** The drawn bar's bottom in the layer. */
    public float slabBottomPx(float density) {
        return viewTopPx + PageBarLayout.raisedSlabBottomDp() * density;
    }
}
