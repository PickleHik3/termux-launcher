package com.termux.app.theme;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.Application;
import android.graphics.Color;
import android.os.Build;

import com.google.android.material.color.MaterialColors;
import com.termux.R;
import com.termux.shared.theme.NightMode;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/**
 * The Black theme (decided 2026-11-28): wherever the dark theme applies, the surfaces the launcher
 * draws (sheets, bars, cards: the Material surface roles and the launcher's aliases of them) are
 * true black, and so is the terminal's ground in the dark palette. The accents are untouched.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = Build.VERSION_CODES.P, application = Application.class, qualifiers = "night")
public class BlackThemeTest {

    @After public void restore() {
        NightMode.setAppNightMode(NightMode.SYSTEM.getName());
    }

    private static Activity themed(boolean black) {
        Activity activity = Robolectric.buildActivity(Activity.class).setup().get();
        activity.setTheme(R.style.Theme_TermuxActivity_DayNight_NoActionBar);
        NightMode.setAppNightMode(black ? NightMode.BLACK.getName() : NightMode.TRUE.getName());
        if (TermuxThemeManager.wearsBlack(NightMode.getAppNightMode())) {
            activity.getTheme().applyStyle(R.style.ThemeOverlay_Termux_Black, true);
        }
        return activity;
    }

    @Test public void blackWearsTrueBlackSurfacesAndKeepsTheAccent() {
        Activity dark = themed(false);
        int darkSurface = MaterialColors.getColor(dark, com.google.android.material.R.attr.colorSurface, 0);
        int darkAccent = MaterialColors.getColor(dark, androidx.appcompat.R.attr.colorPrimary, 0);
        assertNotEquals("Dark's surface is not black", Color.BLACK, darkSurface);

        Activity black = themed(true);
        for (int role : new int[] {com.google.android.material.R.attr.colorSurface,
                com.google.android.material.R.attr.colorSurfaceContainerLowest,
                com.google.android.material.R.attr.colorSurfaceContainerLow,
                com.google.android.material.R.attr.colorSurfaceContainer,
                com.termux.shared.R.attr.termuxColorSurfaceBase, com.termux.shared.R.attr.termuxColorSurfacePanel}) {
            assertEquals("a sheet, bar or card's surface is true black", Color.BLACK,
                MaterialColors.getColor(black, role, 0));
        }
        assertEquals("the accent still comes from the wallpaper's theme", darkAccent,
            MaterialColors.getColor(black, androidx.appcompat.R.attr.colorPrimary, 0));
    }

    @Test public void onlyBlackIsWornAsBlack() {
        assertTrue(TermuxThemeManager.wearsBlack(NightMode.BLACK));
        assertFalse(TermuxThemeManager.wearsBlack(NightMode.TRUE));
        assertFalse(TermuxThemeManager.wearsBlack(NightMode.SYSTEM));
        assertTrue(NightMode.BLACK.forcesNight());
        assertTrue(NightMode.TRUE.forcesNight());
        assertFalse(NightMode.FALSE.forcesNight());
    }
}
