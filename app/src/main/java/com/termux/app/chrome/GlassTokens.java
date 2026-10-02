package com.termux.app.chrome;

import androidx.annotation.ColorInt;

/** The glass palette literals, in one place. Values are unchanged from where they were declared. */
public final class GlassTokens {
    private GlassTokens() {}

    @ColorInt public static final int OBSIDIAN_DARK = 0xFF161822;
    @ColorInt public static final int OBSIDIAN_LIGHT = 0xFFFFFFFF;
    /** White 0.05, out of 255. */
    public static final int WASH_ALPHA = 13;

    @ColorInt public static final int RIM_BASE = 0x3DFFFFFF;
    @ColorInt public static final int RIM_LIGHT_TOP = 0x7DFFFFFF;
    @ColorInt public static final int RIM_SHIMMER = 0xC8FFFFFF;

    /** The glass look's light and dark ends: highlights, specular passes and shadows. */
    @ColorInt public static final int HIGHLIGHT = 0xFFFFFFFF;
    @ColorInt public static final int SHADE = 0xFF000000;

    /** Drawer category tile wash, pressed wash and stroke (white 0.055, 0.11, 0.13). */
    @ColorInt public static final int TILE_FILL = 0x0EFFFFFF;
    @ColorInt public static final int TILE_FILL_PRESSED = 0x1CFFFFFF;
    @ColorInt public static final int TILE_STROKE = 0x21FFFFFF;
}
