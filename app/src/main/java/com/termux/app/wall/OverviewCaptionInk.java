package com.termux.app.wall;

import androidx.annotation.ColorInt;

import com.termux.app.chrome.WallpaperInk;

/**
 * The ink of Edit pages' captions (a card's name, the Terminal block's grip), written straight
 * on the wallpaper: the launcher's one rule for bare text ({@link WallpaperInk#bareInk}, the
 * Home labels' inks), with its soft shadow on the far side ({@link WallpaperInk#labelShadow}).
 * Before a wallpaper is known the caption keeps white on its dark shadow. Pure.
 */
public final class OverviewCaptionInk {

    private OverviewCaptionInk() {}

    /** Home's label inks: dark on a light wallpaper, white on a dark one. */
    @ColorInt public static final int DARK_INK = 0xFF1F1F1F;
    @ColorInt public static final int PALE_INK = 0xFFFFFFFF;
    /** The shadow a caption keeps while no wallpaper is known. */
    @ColorInt public static final int UNKNOWN_SHADOW = 0x99000000;

    /** The caption's ink over {@code wallpaper} (opaque; transparent when unknown). */
    @ColorInt
    public static int ink(@ColorInt int wallpaper) {
        if ((wallpaper >>> 24) == 0) return PALE_INK;
        return WallpaperInk.bareInk(wallpaper, DARK_INK, PALE_INK);
    }

    /** The caption's shadow under {@link #ink} over {@code wallpaper}. */
    @ColorInt
    public static int shadow(@ColorInt int wallpaper) {
        if ((wallpaper >>> 24) == 0) return UNKNOWN_SHADOW;
        return WallpaperInk.labelShadow(ink(wallpaper), wallpaper);
    }

    /**
     * The cell a caption at {@code px} stands in, {@code cellPx} wide: it is sampled again only
     * when it moves to another cell, so a strip being scrolled reads the wallpaper a few times,
     * not every frame.
     */
    public static int cell(float px, float cellPx) {
        return (int) Math.floor(px / Math.max(1f, cellPx));
    }
}
