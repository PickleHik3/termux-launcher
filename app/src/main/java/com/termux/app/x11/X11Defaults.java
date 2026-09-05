package com.termux.app.x11;

import android.content.Context;

import androidx.annotation.NonNull;

import com.termux.shared.termux.settings.preferences.TermuxAppSharedPreferences;
import com.termux.x11.Prefs;

/**
 * The display's phone-sized defaults, written once when the display is first switched on and
 * only into settings the user has never touched: text and icons at a size a thumb can use, and
 * touch read as a touchscreen rather than a trackpad. Upstream's defaults assume a desktop.
 */
public final class X11Defaults {

    /** Roughly the fraction of the Android density that makes desktop apps readable on a phone. */
    private static final float DPI_FRACTION = 0.56f;

    private X11Defaults() {}

    public static void applyOnce(@NonNull Context context) {
        TermuxAppSharedPreferences prefs = TermuxAppSharedPreferences.build(context);
        if (prefs == null || prefs.areX11DefaultsApplied()) return;
        if (!prefs.hasX11DisplayDpi()) {
            prefs.setX11DisplayDpi(defaultDpi(context.getResources().getDisplayMetrics().densityDpi));
        }
        try {
            Prefs display = new Prefs(context.getApplicationContext());
            if (!display.get().contains("touchMode")) display.touchMode.put("2");
        } catch (RuntimeException ignored) {
            // No display store yet; the server's own default stands.
        }
        prefs.setX11DefaultsApplied(true);
    }

    /** {@code densityDpi} scaled down and rounded to a multiple of eight, kept between 160 and 320. */
    static int defaultDpi(int densityDpi) {
        int dpi = Math.round(densityDpi * DPI_FRACTION / 8f) * 8;
        return Math.max(160, Math.min(320, dpi));
    }
}
