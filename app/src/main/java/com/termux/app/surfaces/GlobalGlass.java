package com.termux.app.surfaces;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceProperty;
import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences.SurfaceSlot;
import com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP;

/**
 * The shared glass (layout editor v2, DECISIONS items 13 and 14; the Appearance card's All
 * surfaces strip): Blur, Opacity, Grain and Tint written as Global's values, Frost writing blur
 * and grain together. The terminal's Background (its panes' Opacity and Blur, its Wallpaper dim)
 * is never Global's and no write here reaches it (ADR 0031 §4). Own stays (appearance-one-look spec §1): the status bar's, the dock's and
 * the keyboard's own values are left as they are, and only their reset arrows put them back on
 * Global.
 *
 * <p>No views and no {@code Context}: the rule is held by a test against real preferences.</p>
 */
final class GlobalGlass {

    private GlobalGlass() {}

    /**
     * The surfaces whose own Opacity and Blur (with its Grain) a Global write leaves (ADR 0031
     * §2). Their Tint is no longer theirs to own, so a Global Tint still puts them back on it.
     */
    static boolean ownsStay(@NonNull SurfaceSlot slot) {
        return slot == SurfaceSlot.STATUS || slot == SurfaceSlot.DOCK
            || slot == SurfaceSlot.KEYBOARD;
    }

    /**
     * Writes {@code value} as Global's value for {@code property} and puts every surface back on it.
     * Only Blur, Opacity, Grain and Tint are glass; Corners and Margin are Layout's and are refused.
     *
     * @return the value written, clamped to the property's range
     */
    static int write(@NonNull TermuxAppSharedPreferences prefs, @NonNull SurfaceProperty property,
                     int value) {
        return write(prefs, property, TermuxAppSharedPreferences.PAGE_ALL, value);
    }

    /**
     * {@link #write(TermuxAppSharedPreferences, SurfaceProperty, int)} for one page (ADR 0015): the
     * page's Global, and only the surfaces standing on that page put back on it. PAGE_ALL writes the
     * wall and every page.
     */
    static int write(@NonNull TermuxAppSharedPreferences prefs, @NonNull SurfaceProperty property,
                     int pageId, int value) {
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
        for (SurfaceSlot slot : SurfaceSlot.values()) {
            // The terminal's Opacity and Blur are its Background's own keys, never Global's
            // (ADR 0031 §4); its Grain and Tint are Global's like everything else's.
            if (slot == SurfaceSlot.CANVAS && (property == SurfaceProperty.OPACITY
                    || property == SurfaceProperty.BLUR))
                continue;
            if (ownsStay(slot) && property != SurfaceProperty.TINT)
                continue;
            prefs.setSurfaceInheriting(slot, property, pageId, true);
        }
        prefs.setSurfaceGlobalValue(property, pageId, clamped);
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
        int tint = prefs.getSurfaceGlobalValue(SurfaceProperty.TINT);
        if (tint > 0)
            return tint;
        return write(prefs, SurfaceProperty.TINT, TINT_ON_HUE_PICK);
    }
}
