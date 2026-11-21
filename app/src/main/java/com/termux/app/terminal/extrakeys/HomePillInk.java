package com.termux.app.terminal.extrakeys;

import androidx.annotation.ColorInt;

import com.termux.app.chrome.GlassInk;

/**
 * The ink of the Home pill's keys. The pill is a near-opaque plate of its own, so its keys are
 * inked against that plate, never against the dock's glass or the wallpaper the docked bar reads.
 */
public final class HomePillInk {

    private HomePillInk() {
    }

    /**
     * {@code themeInk} where it clears {@code target} on {@code plate}, else its nearest tone that
     * does (dark on a pale plate, pale on a dark one): the docked bar's rule, on the pill's plate.
     */
    @ColorInt
    public static int on(@ColorInt int plate, @ColorInt int themeInk, double target) {
        return GlassInk.faceInk(plate, themeInk, target);
    }
}
