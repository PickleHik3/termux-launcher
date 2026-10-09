package com.termux.app.surfaces;

import androidx.annotation.NonNull;

import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceProperty;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceSlot;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

/**
 * The Look editor's global glass (layout editor v2, DECISIONS items 13 and 14): Blur, Opacity,
 * Grain and Tint written as the shared base values. Writing one re-attaches every surface to the base,
 * terminal included, so the number is what every surface wears afterwards; an element's own
 * Blur, Grain or Opacity on the Custom row detaches it again for fine-tuning.
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
        for (SurfaceSlot slot : SurfaceSlot.values())
            prefs.setSurfaceInheriting(slot, property, true);
        // The keyboard's blur is the one cell whose raw key holds "follow" as -1 outside the
        // link (the rule Blur has always written); a stale number there must not come back.
        if (property == SurfaceProperty.BLUR)
            prefs.setInAppKeyboardBlurRadiusRaw(TERMUX_APP.DEFAULT_IN_APP_KEYBOARD_BLUR_RADIUS);
        prefs.setSurfaceBaseValue(property, clamped);
        if (property == SurfaceProperty.OPACITY) {
            // The terminal follows the base now; the setter also keeps the copy the wallpaper
            // mode restores from in step, so turning the wallpaper off and on keeps this number.
            prefs.setTerminalBackgroundOpacity(clamped);
        }
        return clamped;
    }
}
