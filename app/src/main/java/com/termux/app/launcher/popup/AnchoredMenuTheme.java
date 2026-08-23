package com.termux.app.launcher.popup;

/**
 * The host's live look-and-feel, read by the popup module at build and highlight time.
 *
 * <p>Colours are pulled through this interface rather than snapshotted because a menu row is
 * restyled while it is on screen (drag-to-highlight) and must pick up the same colours the host
 * would resolve at that moment.
 */
public interface AnchoredMenuTheme {

    /** Body/label colour for menu rows and their glyphs. */
    int textColor();

    /** Label colour for the row currently under the finger. */
    int selectedTextColor();

    /** Dock opacity, 0..100; the floor of a menu panel's own opacity. */
    int opacityPercent();

    /** Whether the host is running the realtime-blur material. */
    boolean blurEnabled();

    /** Blur radius in dp; blur is skipped when this is not positive. */
    int blurRadiusDp();
}
