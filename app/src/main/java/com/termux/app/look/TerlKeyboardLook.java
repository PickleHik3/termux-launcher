package com.termux.app.look;

import androidx.annotation.NonNull;

/**
 * The in-app keyboard's Opacity, Blur, Tint colour and key corners while the Terl desktop is switched on: the keyboard wears
 * Terl's Keyboard look ({@link TerlLookStore#value}), its own value or Global's, painted as its own
 * material. Switched off, v1's numbers pass through untouched, so v1's look plumbing stays as it is.
 *
 * <p>The keyboard's glass asks here once per number it reads from v1's KEYBOARD slot, handing v1's
 * answer in.
 */
public final class TerlKeyboardLook {

    /** Whether the Terl desktop is on; read each time, so a switch takes effect at the next dress. */
    public interface Switch {
        boolean isOn();
    }

    @NonNull private final Switch mSwitch;
    @NonNull private final TerlLookStore mStore;

    public TerlKeyboardLook(@NonNull Switch terl, @NonNull TerlLookStore store) {
        mSwitch = terl;
        mStore = store;
    }

    /** Whether the keyboard wears Terl's look now. */
    public boolean worn() {
        return mSwitch.isOn();
    }

    /** The keyboard's blur radius in dp. */
    public int blurDp(int v1) {
        return worn() ? mStore.value(TerlSurface.KEYBOARD, TerlProperty.BLUR) : v1;
    }

    /** The keyboard's tint opacity in percent. */
    public int opacityPercent(int v1) {
        return worn() ? mStore.value(TerlSurface.KEYBOARD, TerlProperty.OPACITY) : v1;
    }

    /**
     * Whether the keyboard's opacity is the dock's material. Under Terl it never is: there is no
     * dock, and the keyboard's Opacity (own or Global's) is painted as its own.
     */
    public boolean opacityFollowsDock(boolean v1) {
        return !worn() && v1;
    }

    /** The one alpha over the keyboard's finished stack, in percent: Terl fades the tint, not the stack. */
    public int stackAlphaPercent(int v1) {
        return worn() ? 100 : v1;
    }

    /**
     * The keys' corner radius in dp. Under Terl the keys follow Global's Corners: v1's key radius
     * at Terl's default corners, in the same proportion as the corners move, held to the keys' own
     * ceiling (a square look gives square keys, the roundest look keys as round as keys go).
     */
    public float keyCornerRadiusDp(float v1) {
        if (!worn()) return v1;
        float share = com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP
            .DEFAULT_IN_APP_KEYBOARD_KEY_CORNER_RADIUS_DP / StoredTerlLookStore.DEFAULT_CORNER_DP;
        return Math.min(com.termux.shared.termux.settings.preferences.TermuxPreferenceConstants.TERMUX_APP
            .MAX_IN_APP_KEYBOARD_KEY_CORNER_RADIUS_DP, Math.max(0, mStore.cornerRadiusDp()) * share);
    }

    /**
     * The accent the keyboard's glass tint mixes in: Global's Tint colour under Terl (Auto mixes
     * none, {@link #tintAmountPercent}, so v1's hue stands in for it unseen).
     */
    @NonNull
    public String tintHue(@NonNull String v1) {
        if (!worn()) return v1;
        String tint = mStore.tint();
        return TerlLookStore.TINT_AUTO.equals(tint) ? v1 : tint;
    }

    /** How much of that accent the keyboard's glass mixes in, in percent: Global's Tint under Terl. */
    public int tintAmountPercent(int v1) {
        return worn() ? TerlLookStore.tintAmountPercent(mStore.tint()) : v1;
    }
}
