package com.termux.app.launcher.drawer;

import androidx.annotation.NonNull;

/**
 * How the open drawer stands apart from the built-in keyboard its search raised: the same way the
 * terminal pane stands apart from the keyboard on the Terminal.
 *
 * <p>All pure so they can be tested without a view tree:
 * <ul>
 *   <li>the gap between the plane's bottom and the keyboard's top — under Docked, Margin: the
 *       plane becomes a card of its own, Margin in from the screen's sides, Margin below the
 *       status bar and Margin above the keyboard card, with the wallpaper round it; under
 *       Floating the pane gap;</li>
 *   <li>the plane's corner radius — under Docked the card's four corners take the terminal pane's
 *       corners as the keyboard comes up; under Floating the panel keeps its own all round;</li>
 *   <li>the under-pill strip's opacity — the drawer fades the strip out as its glass arrives under
 *       it, but once the keyboard is revealed the plane stops above the keyboard and the strip
 *       under the pill is the keyboard's glass run on, exactly as on the Terminal.</li>
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

    /**
     * Whether the open drawer stands as a card of its own above the built-in keyboard: under
     * Docked, where the keyboard is the frame's bottom slice running edge to edge. Floating's panel
     * is a card already, and a system keyboard is a window of its own the drawer only ends above.
     */
    static boolean dockedCard(boolean rounded, boolean imePinned) {
        return !rounded && !imePinned;
    }

    /**
     * The air left at the plane's bottom above the keyboard. Docked's card takes Margin, the same
     * air it leaves at its sides, so the wallpaper shows round it on three sides and between it
     * and the keyboard card. Floating and a system keyboard keep {@code gapPx}. One owner for the
     * content's bottom ({@code contentRect}) and the plane's ({@code applyFrame}).
     *
     * @param marginPx Margin; a negative value means the host gave none
     */
    static float planeGapPx(boolean rounded, boolean imePinned, float gapPx, float marginPx) {
        return dockedCard(rounded, imePinned) ? Math.max(0f, marginPx) : gapPx;
    }

    /**
     * The top of Docked's card: Margin below the status bar's inset, where a terminal pane's card
     * starts below the bar, so the system's status icons stand on the wallpaper, not on the card.
     * The content starts there too ({@code contentRect}).
     *
     * @param statusInsetPx the status bar's inset the host reaches through (0 when it does not)
     * @param marginPx Margin; a negative value means the host gave none
     */
    static float dockedCardTopPx(float statusInsetPx, float marginPx) {
        return Math.max(0f, statusInsetPx) + Math.max(0f, marginPx);
    }

    /**
     * Docked's plane at reveal {@code k}: the bezel-to-bezel sheet at 0, at 1 a card standing
     * Margin in from the left and right edges, its top at {@code topPx}
     * ({@link #dockedCardTopPx}) and its bottom at {@code bottomPx}. An edge already further in
     * than that (the seed, early in the open) is left where it is, so the card only ever moves an
     * edge inward.
     *
     * @param frame the plane's frame before the reveal
     * @param hostWidthPx the host's width, whose right edge Margin is measured from
     * @param marginPx Margin; a negative value means the host gave none
     * @param topPx the card's top at full reveal
     * @param bottomPx the plane's bottom at this reveal
     */
    static AppDrawerTransitionGeometry.Frame dockedCardFrame(
            @NonNull AppDrawerTransitionGeometry.Frame frame,
            float hostWidthPx, float marginPx, float topPx, float bottomPx, float reveal) {
        float k = AppDrawerTransitionGeometry.clamp01(reveal);
        float m = Math.max(0f, marginPx);
        float left = AppDrawerTransitionGeometry.lerp(frame.left, Math.max(frame.left, m), k);
        float right = AppDrawerTransitionGeometry.lerp(frame.right,
            Math.min(frame.right, hostWidthPx - m), k);
        float top = AppDrawerTransitionGeometry.lerp(frame.top, Math.max(frame.top, topPx), k);
        // A seed that reaches below the card's bottom (the dock beside the keyboard, early in an
        // open that starts revealed) is a card of no height on that bottom, never one turned over.
        return new AppDrawerTransitionGeometry.Frame(left, Math.min(top, bottomPx), right,
            bottomPx);
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
     * Whether the built-in keyboard keeps its place for the drawer's whole open and close: Docked's
     * card, with the keyboard up before the drawer was pulled (the Terminal's keyboard, as when the
     * palette opens the drawer). It is not the drawer's to take down or bring back, so it neither
     * dips under the plane nor goes with the close, and the drawer card closes as one card above it.
     * A keyboard the drawer's own search raised (down at the pull) keeps the reveal.
     */
    static boolean keyboardStaysUp(boolean rounded, boolean imePinned, boolean textFieldSearch,
                                   boolean keyboardUpAtPull) {
        return dockedCard(rounded, imePinned) && !textFieldSearch && keyboardUpAtPull;
    }

    /**
     * The reveal spring's target: none without a keyboard to reveal, all of it while the keyboard
     * stays up ({@link #keyboardStaysUp}), open or closing, else the content's ask while open.
     */
    static float revealTarget(boolean revealable, boolean open, boolean staysUp,
                              float contentFraction) {
        if (!revealable) return 0f;
        if (staysUp) return 1f;
        return open ? AppDrawerTransitionGeometry.clamp01(contentFraction) : 0f;
    }

    /**
     * The reveal an open starts from: already at its target while the keyboard stays up
     * ({@link #keyboardStaysUp}), so the plane is a card above the keyboard from the first frame and
     * never a sheet that rises over the keys and then shortens off them; else where it is.
     */
    static float revealAtOpen(boolean staysUp, float target, float current) {
        return staysUp ? target : current;
    }

    /**
     * The reveal the keyboard's bands are choreographed at: identity (1) while the keyboard stays
     * up, so the plane never pushes it down or fades it, else the spring's {@code reveal}.
     */
    static float bandRevealFraction(boolean staysUp, float reveal) {
        return staysUp ? 1f : reveal;
    }
}
