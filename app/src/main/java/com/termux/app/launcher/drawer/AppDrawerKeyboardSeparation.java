package com.termux.app.launcher.drawer;

/**
 * How the open drawer stands apart from the built-in keyboard its search raised: the same way the
 * terminal pane stands apart from the keyboard on the Workspace.
 *
 * <p>Three numbers, all pure so they can be tested without a view tree:
 * <ul>
 *   <li>the gap between the plane's bottom and the keyboard's top — the pane gap, the air the
 *       terminal pane leaves above the keyboard, rather than the 4dp the dock happened to stand
 *       off the extra-keys row at the pull;</li>
 *   <li>the plane's corner radius — under Docked the plane runs off the bezel and its only corners
 *       that show are the two the reveal brings up above the keyboard, so they take the terminal
 *       pane's corners; under Floating the panel keeps its own all round;</li>
 *   <li>the under-pill strip's opacity — the drawer fades the strip out as its glass arrives under
 *       it, but once the keyboard is revealed the plane stops above the keyboard and the strip
 *       under the pill is the keyboard's glass run on, exactly as on the Workspace.</li>
 * </ul>
 */
final class AppDrawerKeyboardSeparation {

    private AppDrawerKeyboardSeparation() {}

    /**
     * The gap between the plane and the keyboard top at full reveal.
     *
     * @param paneGapPx the terminal pane's gap; a negative value means the host gave none
     * @param capturedGapPx the gap measured at the pull, the fallback
     */
    static float revealGapPx(float paneGapPx, float capturedGapPx) {
        return paneGapPx >= 0f ? paneGapPx : Math.max(0f, capturedGapPx);
    }

    /** The plane's bottom edge with the keyboard fully revealed, in host coordinates. */
    static float revealedPlaneBottomPx(float keyboardTopPx, float paneGapPx, float capturedGapPx) {
        return keyboardTopPx - revealGapPx(paneGapPx, capturedGapPx);
    }

    /**
     * The plane's corner radius at reveal {@code k}. Floating keeps the drawer's own radius; Docked
     * moves to the terminal pane's as the keyboard comes up. A negative pane radius means none was
     * given and the drawer's own is kept.
     */
    static float revealRadiusPx(boolean rounded, float drawerRadiusPx, float paneRadiusPx,
                                float reveal) {
        if (rounded || paneRadiusPx < 0f) return drawerRadiusPx;
        return AppDrawerTransitionGeometry.lerp(drawerRadiusPx, Math.max(0f, paneRadiusPx),
            AppDrawerTransitionGeometry.clamp01(reveal));
    }

    /**
     * Docked's plane top is pushed one radius past the bezel so its top corners never show. When the
     * reveal changes the radius, the push follows it, or a pane radius larger than the drawer's
     * would bring part of a top corner on screen. A top that was not pushed (Floating, or a host
     * whose insets never arrived) is left alone.
     */
    static float revealPlaneTopPx(boolean rounded, float frameTopPx, float radiusPx) {
        if (rounded || frameTopPx >= 0f) return frameTopPx;
        return Math.min(frameTopPx, -radiusPx);
    }

    /**
     * The under-pill strip's opacity: the drawer's cross-fade {@code 1 - p}, given back as the
     * keyboard is revealed below the plane. At {@code reveal 0} this is the old fade exactly.
     */
    static float navStripAlpha(float progress, float reveal) {
        float p = AppDrawerTransitionGeometry.clamp01(progress);
        float k = AppDrawerTransitionGeometry.clamp01(reveal);
        return 1f - p * (1f - k);
    }
}
