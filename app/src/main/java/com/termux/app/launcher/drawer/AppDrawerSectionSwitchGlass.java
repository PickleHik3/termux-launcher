package com.termux.app.launcher.drawer;

import androidx.annotation.ColorInt;
import androidx.annotation.NonNull;
import androidx.core.graphics.ColorUtils;

/**
 * When the section switch wears refracting glass and how round it is, pure. The glass is the page
 * bar's slab ({@code PaneGlassBackdropView}) laid under the switch with the switch's own rect; it
 * is wanted only while the drawer is open on the Default type, the only type that shows the
 * switch.
 */
public final class AppDrawerSectionSwitchGlass {

    /**
     * White laid over the card's own tint so the pill reads as a slightly raised pane of the same
     * glass, in light and dark alike (about 10%).
     */
    static final int LIFT_ALPHA = 0x1A;

    private AppDrawerSectionSwitchGlass() {
    }

    /** Whether the glass is wanted: the switch is shown (Default) and the drawer is open. */
    static boolean wanted(@NonNull AppDrawerViewType type, boolean drawerOpen) {
        return drawerOpen && type == AppDrawerViewType.DEFAULT;
    }

    /** The glass's corner radius: a capsule, half the switch's height. */
    static float radiusPx(int switchHeightPx) {
        return Math.max(0, switchHeightPx) * 0.5f;
    }

    /**
     * The tint the switch's slab wears: the veil the plane's picture wears, under the plane's tint
     * ({@code base} at {@code opacity}), with the lift over both. Translucent, laid over the frost.
     */
    @ColorInt
    public static int tint(@ColorInt int veil, @ColorInt int base, float opacity) {
        float clamped = Float.isFinite(opacity) ? Math.max(0f, Math.min(1f, opacity)) : 0f;
        int plane = ColorUtils.setAlphaComponent(base, Math.round(255 * clamped));
        int card = ColorUtils.compositeColors(plane, veil);
        return ColorUtils.compositeColors(
            ColorUtils.setAlphaComponent(0xFFFFFFFF, LIFT_ALPHA), card);
    }
}
