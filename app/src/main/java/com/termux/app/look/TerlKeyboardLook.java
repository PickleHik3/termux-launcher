package com.termux.app.look;

import androidx.annotation.NonNull;

/**
 * The in-app keyboard's Opacity, Blur and Tint colour while the Terl desktop is switched on: the keyboard wears
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

    /** The accent the keyboard's glass tint mixes in: Global's Tint colour under Terl. */
    @NonNull
    public String tintHue(@NonNull String v1) {
        return worn() ? mStore.tint() : v1;
    }
}
