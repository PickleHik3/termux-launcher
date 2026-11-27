package com.termux.app.terminal.inappkeyboard;

import android.content.Context;

import androidx.annotation.Nullable;

import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;

/**
 * Terl's default launcher layout (ADR 0042) stands in for the bundled launcher layout while the
 * Terl desktop switch is on: the same keys with the Terl key on Ctrl's north-east corner, the
 * clipboard key on Alt's and the switch-keyboard key on Alt's south-west. With the switch off the
 * keyboard is today's. Every place that loads a bundled layout asks {@link #forTerl} as it loads,
 * so the switch is read then; a layout file the user wrote is never swapped.
 */
public final class TerlKeyboardLayout {

    /** Today's launcher layout. */
    public static final int LAUNCHER = juloo.keyboard2.R.xml.termux_launcher_qwerty;
    /** The launcher layout under Terl. */
    public static final int TERL = juloo.keyboard2.R.xml.terl_launcher_qwerty;

    private TerlKeyboardLayout() {}

    /** {@code resId}, or Terl's layout in place of the launcher layout when {@code terl}. */
    public static int forTerl(int resId, boolean terl) {
        return terl && resId == LAUNCHER ? TERL : resId;
    }

    /** {@link #forTerl} with the switch as stored now; off when the preferences cannot be read. */
    public static int forTerl(int resId, @Nullable Context context) {
        return forTerl(resId, isOn(context));
    }

    /** Whether the Terl desktop switch is on. */
    public static boolean isOn(@Nullable Context context) {
        if (context == null) return false;
        TermuxAppSharedPreferences preferences = TermuxAppSharedPreferences.build(context, false);
        return preferences != null && preferences.isTerlDesktopEnabled();
    }
}
