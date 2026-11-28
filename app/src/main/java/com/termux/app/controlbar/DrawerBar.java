package com.termux.app.controlbar;

/**
 * The control bar and the open app drawer ({@code terl.bar_in_drawer}), pure. Shown in the
 * drawer, the bar stays where it stands and the drawer's sheet ends at its top. Not shown (the
 * default), the sheet reaches the screen's bottom edge as every bottom glass does (ADR 0029
 * section 6), the bar keeps its layout but draws nothing while the drawer is open, and it is back
 * once the drawer has closed. The keyboard's rules under the drawer are the same either way.
 */
public final class DrawerBar {

    private DrawerBar() {}

    /**
     * The band the drawer's sheet ends above at rest: the bar's top ({@code barTopAbovePx}) when
     * the bar shows in the drawer, else only the band under Android's pill ({@code bottomBandPx}).
     */
    public static int sheetsBandPx(boolean barInDrawer, int bottomBandPx, int barTopAbovePx) {
        return barInDrawer ? Math.max(bottomBandPx, barTopAbovePx) : bottomBandPx;
    }

    /**
     * Whether the bar stands under the drawer's sheet, drawing nothing: the bar is not shown in the
     * drawer and the drawer is on screen at all ({@code progress} above 0, its pull included, so
     * the sheet growing from the bar's place takes it over on the first frame).
     */
    public static boolean underSheet(boolean barInDrawer, float progress) {
        return !barInDrawer && progress > 0f;
    }
}
