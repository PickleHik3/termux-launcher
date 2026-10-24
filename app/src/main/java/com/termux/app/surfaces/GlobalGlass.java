package com.termux.app.surfaces;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceProperty;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceSlot;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.WallHalf;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

/**
 * The shared glass (layout editor v2, DECISIONS items 13 and 14; the Appearance card's All
 * surfaces strip): Blur, Opacity, Grain and Tint written as the base values, Frost writing blur
 * and grain together. Writing one re-attaches every surface to the base, so the number is what
 * every surface wears afterwards; a surface's own value on its card detaches it again.
 *
 * <p>No views and no {@code Context}: the rule is held by a test against real preferences.</p>
 */
final class GlobalGlass {

    private GlobalGlass() {}

    /**
     * Writes {@code value} as the base for {@code property} and puts every surface back on it.
     * Only Blur, Opacity, Grain and Tint are glass; Corners and Margin are Layout's and are refused.
     *
     * @return the value written, clamped to the property's range
     */
    static int write(@NonNull TermuxAppSharedPreferences prefs, @NonNull SurfaceProperty property,
                     int value) {
        return write(prefs, property, null, value);
    }

    /**
     * {@link #write(TermuxAppSharedPreferences, SurfaceProperty, int)} for one half (ADR 0013): the
     * half's base, and only the surfaces standing on that half put back on it. The terminal stands
     * on the Workspace alone, so Home's shared glass leaves it as it is. A null half writes both.
     */
    static int write(@NonNull TermuxAppSharedPreferences prefs, @NonNull SurfaceProperty property,
                     @Nullable WallHalf half, int value) {
        int clamped;
        switch (property) {
            case BLUR:
                clamped = AppearanceLooks.blurDp(value);
                break;
            case OPACITY:
                clamped = AppearanceLooks.opacityPercent(value);
                break;
            case GRAIN:
                clamped = AppearanceLooks.grainPercent(value);
                break;
            case TINT:
                clamped = AppearanceLooks.tintPercent(value);
                break;
            default:
                throw new IllegalArgumentException("not a glass property: " + property);
        }
        boolean workspace = half != WallHalf.HOME;
        for (SurfaceSlot slot : SurfaceSlot.values()) {
            if (slot == SurfaceSlot.CANVAS && !workspace)
                continue;
            prefs.setSurfaceInheriting(slot, property, half, true);
        }
        // The keyboard's blur is the one cell whose raw key holds "follow" as -1 outside the
        // link (the rule Blur has always written); a stale number there must not come back. A
        // one-half write leaves it: the other half may still read its own number there.
        if (property == SurfaceProperty.BLUR && half == null)
            prefs.setInAppKeyboardBlurRadiusRaw(TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_BLUR_RADIUS);
        prefs.setSurfaceBaseValue(property, half, clamped);
        if (property == SurfaceProperty.OPACITY && half == null) {
            // The terminal follows the base now; the setter also keeps the copy the wallpaper
            // mode restores from in step, so turning the wallpaper off and on keeps this number.
            prefs.setTerminalBackgroundOpacity(clamped);
        } else if (property == SurfaceProperty.OPACITY && workspace
            && prefs.isUseSystemWallpaperEnabled()) {
            // The terminal follows the Workspace's base already; only the wallpaper mode's copy
            // is kept. The terminal's own setter writes the shared base, which would drop both
            // halves' own values and land this number on Home too.
            prefs.setWallpaperEnabledTerminalBackgroundOpacity(clamped);
        }
        return clamped;
    }

    /**
     * The Tint a Hue pick raises an untinted glass to: a hue chosen at Tint 0 would show nothing,
     * so the pick also gives the glass enough accent to see it. On the stretched scale (glass
     * controls format 2) 40 sits just past the Tint Look's 35: on Clear the frame tint and the
     * face move a mid-grey wallpaper's face about 8 CIE76 units (calc, M3 baseline purple), and
     * at the Tint Look's opacity the face alone about 13 on dark glass and 7 on light.
     */
    static final int TINT_ON_HUE_PICK = 40;

    /**
     * The Hue pill picked {@code hueId} for the shared glass: the hue is written, and where the
     * shared Tint stands at 0 it is raised to {@link #TINT_ON_HUE_PICK} in the same edit, as the
     * global Tint slider writes it. A Tint above 0 is left as it is.
     *
     * @return the shared Tint after the pick
     */
    static int writeTintHue(@NonNull TermuxAppSharedPreferences prefs, @NonNull String hueId) {
        prefs.setSurfaceGlassTintHue(hueId);
        int tint = prefs.getSurfaceBaseValue(SurfaceProperty.TINT);
        if (tint > 0)
            return tint;
        return write(prefs, SurfaceProperty.TINT, TINT_ON_HUE_PICK);
    }
}
