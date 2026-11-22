package com.termux.app.glass;

/**
 * Which glass surfaces a sheet covers (the app drawer's card at rest over the page), pure: a
 * surface on a layer beneath the sheet's that is not on screen, or stands wholly inside the
 * sheet's rect, cannot be seen through, so it gives its Live slot up while the sheet stands
 * ({@link GlassRegistry#setCovered}). A surface on the sheet's layer or above, or one that reaches
 * past the sheet's edge, is still seen and keeps its kind.
 */
public final class GlassCover {

    private GlassCover() {}

    /**
     * Whether a surface on layer {@code layer}, shown or not, standing at {@code left},
     * {@code top}, {@code right}, {@code bottom} in the window, is covered by a sheet on layer
     * {@code coverLayer} over the window rect {@code coverLeft} .. {@code coverBottom}.
     */
    public static boolean covered(int layer, boolean shown, int left, int top, int right,
                                  int bottom, int coverLayer, int coverLeft, int coverTop,
                                  int coverRight, int coverBottom) {
        if (layer >= coverLayer) return false;
        if (coverRight <= coverLeft || coverBottom <= coverTop) return false;
        if (!shown) return true;
        return left >= coverLeft && top >= coverTop && right <= coverRight
            && bottom <= coverBottom;
    }
}
